package com.example.angelonestrategyexecutor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.angelonestrategyexecutor.MainActivity
import com.example.angelonestrategyexecutor.R
import com.example.angelonestrategyexecutor.data.auth.AuthState
import com.example.angelonestrategyexecutor.data.model.CandleDataPoint
import com.example.angelonestrategyexecutor.data.repository.HistoricalCandleRepository
import com.example.angelonestrategyexecutor.data.websocket.LiveSmartWebSocketService
import com.google.gson.Gson
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service for the live watchlist strategy.
 *
 * ## Daily lifecycle
 * 1. Starts at **09:00** (triggered by [LiveWatchListScheduler]).
 * 2. Fetches 15-min historical candles for the last 2 trading days for every stock in
 *    `stocks_list.json` (the user's add/edit stock list).
 *    – If auth is not yet available at 09:00, retries every 5 min until 15:00.
 * 3. Initialises [LiveWatchListStrategyEngine] with the historical data.
 * 4. Connects [LiveSmartWebSocketService] in SNAP_QUOTE mode and subscribes all equity tokens.
 * 5. Streams ticks into the engine; a [android.os.Handler] fires candle-close triggers at
 *    each 15-min boundary (09:30, 09:45 … 15:15, 15:30).
 * 6. Stops automatically at **16:00**.
 *
 * The service uses `START_STICKY` so Android restarts it if killed; on restart it
 * re-fetches historical data and re-initialises the engine.
 *
 * Usage:
 *   LiveWatchListForegroundService.start(context)
 *   LiveWatchListForegroundService.stop(context)
 */
class LiveWatchListForegroundService : Service() {

    companion object {
        private const val TAG = "LiveWLService"
        private const val CHANNEL_ID = "live_watchlist_channel"
        private const val NOTIFICATION_ID = 1005
        private const val ACTION_START = "com.example.angelonestrategyexecutor.START_LIVE_WL"
        private const val ACTION_STOP = "com.example.angelonestrategyexecutor.STOP_LIVE_WL"

        /** Retry interval when historical fetch fails (credentials not yet available). */
        private const val FETCH_RETRY_DELAY_MS = 5 * 60 * 1000L   // 5 minutes
        /** API throttle between per-stock candle fetches. */
        private const val FETCH_THROTTLE_MS = 400L
        /** Historical lookback: fetch enough days to cover at least 2 full trading days. */
        private const val HISTORY_DAYS = 5L
        /** Last time to start a new historical-data attempt. */
        private val LATEST_FETCH_TIME = LocalTime.of(15, 0)
        /** Service auto-stop time. */
        private val AUTO_STOP_TIME = LocalTime.of(16, 0)

        private val istZone: ZoneId = ZoneId.of("Asia/Kolkata")

        fun start(context: Context) {
            val intent = Intent(context, LiveWatchListForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LiveWatchListForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val gson = Gson()

    private val wsService = LiveSmartWebSocketService()
    private var candleCloseRunnables = mutableListOf<Runnable>()
    private var autoStopRunnable: Runnable? = null

    // ── Service lifecycle ─────────────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        LiveWatchListStrategyEngine.init(applicationContext)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.d(TAG, "Stop action received")
                shutdownCleanly()
                return START_NOT_STICKY
            }
            else -> {
                // ACTION_START or null (START_STICKY restart)
                Log.d(TAG, "Start action received")
                startForeground(NOTIFICATION_ID, buildNotification("Starting…"))
                launchStrategy()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        cancelAllTimers()
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }

    // ── Strategy orchestration ────────────────────────────────────────────────

    private fun launchStrategy() {
        serviceScope.launch {
            val now = ZonedDateTime.now(istZone)

            // If we're already past 16:00 there's nothing to do today
            if (now.toLocalTime().isAfter(AUTO_STOP_TIME)) {
                Log.d(TAG, "Already past 16:00 – stopping immediately")
                shutdownCleanly()
                return@launch
            }

            // If the engine is already populated (e.g. manual "Fetch Data" button was pressed),
            // skip the reset + re-fetch and go straight to streaming.
            if (LiveWatchListStrategyEngine.isInitialized) {
                Log.d(TAG, "Engine already initialised – skipping fetch, connecting WebSocket…")
                val stocks = loadStocksFromDisk()
                updateNotification("Streaming ${stocks.size} stocks live…")
                mainHandler.post { connectWebSocket(stocks) }
                mainHandler.post { scheduleCandleCloseTimers(); scheduleAutoStop() }
                return@launch
            }

            // -- Reset engine for a fresh day's run --
            LiveWatchListStrategyEngine.reset()

            // Ensure auth is initialised (needed after cold START_STICKY restarts)
            AuthState.init(applicationContext)

            // 1. Load stock list
            val stocks = loadStocksFromDisk()
            if (stocks.isEmpty()) {
                Log.w(TAG, "No stocks found in stocks_list.json – stopping")
                updateNotification("No stocks configured")
                shutdownCleanly()
                return@launch
            }
            val derivatives = loadDerivativesFromDisk()

            updateNotification("Fetching historical data for ${stocks.size} stocks…")

            // 2. Fetch historical candles with retry until 15:00
            var historicalCandles: Map<String, List<CandleDataPoint>> = emptyMap()
            while (isActive) {
                val currentTime = ZonedDateTime.now(istZone).toLocalTime()
                if (currentTime.isAfter(LATEST_FETCH_TIME)) {
                    Log.w(TAG, "Past 15:00 – could not fetch historical data, stopping")
                    updateNotification("Could not fetch data – stopping")
                    shutdownCleanly()
                    return@launch
                }

                val creds = AuthState.credentials.value
                if (creds == null || creds.isExpired) {
                    Log.d(TAG, "Auth not available yet – retrying in 5 min")
                    updateNotification("Waiting for login session…")
                    delay(FETCH_RETRY_DELAY_MS)
                    AuthState.init(applicationContext)
                    continue
                }

                historicalCandles = fetchHistoricalCandles(stocks)
                if (historicalCandles.isNotEmpty()) break

                Log.d(TAG, "Historical fetch returned empty – retrying in 5 min")
                updateNotification("Historical fetch failed – retrying…")
                delay(FETCH_RETRY_DELAY_MS)
            }

            if (!isActive) return@launch

            // 3. Initialise engine
            val today = LocalDate.now(istZone)
            LiveWatchListStrategyEngine.initialize(
                stocks = stocks,
                derivativeInputs = derivatives,
                historicalCandles = historicalCandles,
                today = today,
            )
            Log.d(TAG, "Engine initialised, connecting WebSocket…")
            updateNotification("Streaming ${stocks.size} stocks live…")

            // 4. Connect WebSocket from the main thread (OkHttp requires it)
            mainHandler.post {
                connectWebSocket(stocks)
            }

            // 5. Schedule 15-min candle close triggers & auto-stop on the main handler
            mainHandler.post {
                scheduleCandleCloseTimers()
                scheduleAutoStop()
            }
        }
    }

    // ── WebSocket connection ──────────────────────────────────────────────────

    private fun connectWebSocket(stocks: List<LiveWatchListStrategyEngine.StockInput>) {
        wsService.setCallback(object : LiveSmartWebSocketService.TickCallback {
            override fun onTick(tick: LiveSmartWebSocketService.SnapQuoteTick) {
                LiveWatchListStrategyEngine.processTick(
                    token = tick.token,
                    ltp = tick.ltp,
                    cumulativeDayVolume = tick.cumulativeDayVolume,
                )
            }

            override fun onConnected() {
                Log.d(TAG, "WebSocket connected – subscribing tokens")
                LiveWatchListStrategyEngine.clearDirty()
                updateNotification("Live: streaming ${stocks.size} stocks…")

                val tokensByExchange = mutableMapOf<Int, MutableList<String>>()
                stocks.forEach { stock ->
                    if (stock.token.isNotBlank()) {
                        tokensByExchange.getOrPut(stock.exchangeType) { mutableListOf() }
                            .add(stock.token)
                    }
                }
                tokensByExchange.forEach { (exchType, tokens) ->
                    wsService.subscribe(exchType, tokens.distinct())
                }
            }

            override fun onDisconnected(reason: String) {
                Log.w(TAG, "WebSocket disconnected: $reason")
                LiveWatchListStrategyEngine.markDirty("WebSocket disconnected: $reason")
                updateNotification("⚠ Disconnected – reconnecting…")
            }

            override fun onError(error: String) {
                Log.e(TAG, "WebSocket error: $error")
                LiveWatchListStrategyEngine.markDirty("WebSocket error: $error")
                updateNotification("⚠ Socket error – data may be incomplete")
            }
        })
        wsService.connect()
    }

    // ── Candle close timers ───────────────────────────────────────────────────

    /**
     * Schedules a Runnable at each upcoming 15-min slot boundary from now until 15:30.
     * Each Runnable calls [LiveWatchListStrategyEngine.triggerCandleClose].
     */
    private fun scheduleCandleCloseTimers() {
        val now = ZonedDateTime.now(istZone)
        val currentMs = System.currentTimeMillis()

        // 15-min slot close times: 09:30, 09:45, …, 15:15, 15:30
        val slotCloseTimes = buildList {
            var t = LocalTime.of(9, 30)
            while (!t.isAfter(LocalTime.of(15, 30))) { add(t); t = t.plusMinutes(15) }
        }

        slotCloseTimes.forEach { closeTime ->
            val closeMs = now.toLocalDate()
                .atTime(closeTime)
                .atZone(istZone)
                .toInstant()
                .toEpochMilli()
            val delayMs = closeMs - currentMs
            if (delayMs <= 0) return@forEach  // already past, skip

            val runnable = Runnable {
                LiveWatchListStrategyEngine.triggerCandleClose(ZonedDateTime.now(istZone))
                Log.d(TAG, "Candle close triggered at $closeTime")
            }
            candleCloseRunnables.add(runnable)
            mainHandler.postDelayed(runnable, delayMs)
        }
        Log.d(TAG, "Scheduled ${candleCloseRunnables.size} candle-close timers")
    }

    // ── Auto-stop at 16:00 ────────────────────────────────────────────────────

    private fun scheduleAutoStop() {
        val now = ZonedDateTime.now(istZone)
        val stopMs = now.toLocalDate()
            .atTime(AUTO_STOP_TIME)
            .atZone(istZone)
            .toInstant()
            .toEpochMilli()
        val delayMs = (stopMs - System.currentTimeMillis()).coerceAtLeast(0L)

        val runnable = Runnable {
            Log.d(TAG, "Auto-stop at 16:00 triggered")
            shutdownCleanly()
        }
        autoStopRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
        Log.d(TAG, "Auto-stop scheduled in ${delayMs / 60_000} min")
    }

    // ── Historical candle fetch ───────────────────────────────────────────────

    private suspend fun fetchHistoricalCandles(
        stocks: List<LiveWatchListStrategyEngine.StockInput>,
    ): Map<String, List<CandleDataPoint>> {
        val result = mutableMapOf<String, List<CandleDataPoint>>()
        val now = ZonedDateTime.now(istZone)
        // Fetch up to the current moment so today's completed candles are included.
        val toMillis = now.toInstant().toEpochMilli()
        val fromMillis = now.minusDays(HISTORY_DAYS)
            .withHour(9).withMinute(0).withSecond(0).withNano(0)
            .toInstant().toEpochMilli()

        stocks.forEachIndexed { index, stock ->
            if (stock.token.isBlank()) return@forEachIndexed
            if (index > 0) delay(FETCH_THROTTLE_MS)

            try {
                val candles = HistoricalCandleRepository.getCandlesInRange(
                    exchange = stock.exchange,
                    symbolToken = stock.token,
                    interval = "FIFTEEN_MINUTE",
                    fromMillis = fromMillis,
                    toMillis = toMillis,
                )
                if (candles.isNotEmpty()) {
                    result[stock.token] = candles
                }
                Log.d(TAG, "Fetched ${candles.size} candles for ${stock.symbol}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch candles for ${stock.symbol}: ${e.message}", e)
            }
        }
        return result
    }

    // ── Disk helpers ──────────────────────────────────────────────────────────

    private fun loadStocksFromDisk(): List<LiveWatchListStrategyEngine.StockInput> {
        val file = File(filesDir, "instruments_cache.json")
        if (!file.exists()) return emptyList()
        return try {
            @Suppress("UNCHECKED_CAST")
            val root = gson.fromJson(file.readText(), Map::class.java) as? Map<String, Any>
            @Suppress("UNCHECKED_CAST")
            val rawList = root?.get("equities") as? List<Map<String, Any>> ?: return emptyList()
            rawList.mapNotNull { m ->
                val token = m["token"] as? String ?: return@mapNotNull null
                val symbol = m["symbol"] as? String ?: return@mapNotNull null
                val exchSeg = m["exchSeg"] as? String ?: "NSE"
                val exchangeType = if (exchSeg.equals("BSE", ignoreCase = true)) 3 else 1
                LiveWatchListStrategyEngine.StockInput(
                    symbol = symbol,
                    token = token,
                    exchange = if (exchangeType == 3) "BSE" else "NSE",
                    exchangeType = exchangeType,
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read instruments_cache.json equities: ${e.message}", e)
            emptyList()
        }
    }

    private fun loadDerivativesFromDisk(): List<LiveWatchListStrategyEngine.DerivativeInput> {
        val file = File(filesDir, "instruments_cache.json")
        if (!file.exists()) return emptyList()
        return try {
            // instruments_cache.json has shape: { "equities": [...], "derivatives": [...] }
            @Suppress("UNCHECKED_CAST")
            val root = gson.fromJson(file.readText(), Map::class.java) as? Map<String, Any>
            @Suppress("UNCHECKED_CAST")
            val rawList = root?.get("derivatives") as? List<Map<String, Any>> ?: return emptyList()
            rawList.mapNotNull { m ->
                val token = m["token"] as? String ?: return@mapNotNull null
                val symbol = m["symbol"] as? String ?: return@mapNotNull null
                val lotSize = (m["lotSize"] as? Double)?.toInt() ?: 0
                val exchSeg = m["exchSeg"] as? String ?: ""
                LiveWatchListStrategyEngine.DerivativeInput(token, symbol, lotSize, exchSeg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read instruments_cache.json: ${e.message}", e)
            emptyList()
        }
    }

    private fun resolveExchange(exchangeType: Int?): String = when (exchangeType) {
        3 -> "BSE"
        else -> "NSE"
    }

    // ── Shutdown ──────────────────────────────────────────────────────────────

    private fun shutdownCleanly() {
        cancelAllTimers()
        LiveWatchListStrategyEngine.finalClose(ZonedDateTime.now(istZone))
        LiveWatchListStrategyEngine.saveToDisk()
        wsService.disconnect()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.d(TAG, "Service shut down cleanly")
    }

    private fun cancelAllTimers() {
        candleCloseRunnables.forEach { mainHandler.removeCallbacks(it) }
        candleCloseRunnables.clear()
        autoStopRunnable?.let { mainHandler.removeCallbacks(it) }
        autoStopRunnable = null
    }

    // ── Notification helpers ──────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Watchlist Strategy",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Live 15-min candle strategy running in the background"
            }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Live Watchlist")
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(text: String) {
        try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFICATION_ID, buildNotification(text))
        } catch (_: Exception) {}
    }
}
