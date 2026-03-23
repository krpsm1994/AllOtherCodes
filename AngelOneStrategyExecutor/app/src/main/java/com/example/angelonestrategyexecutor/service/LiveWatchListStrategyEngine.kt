package com.example.angelonestrategyexecutor.service

import android.content.Context
import android.util.Log
import com.example.angelonestrategyexecutor.data.auth.AuthState
import com.example.angelonestrategyexecutor.data.model.CandleDataPoint
import com.example.angelonestrategyexecutor.service.LiveWatchListForegroundService
import com.example.angelonestrategyexecutor.data.model.OhlcvCandle
import com.example.angelonestrategyexecutor.data.repository.HistoricalCandleRepository
import com.example.angelonestrategyexecutor.data.websocket.LiveSmartWebSocketService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Utility engine for the LIVE watchlist strategy.
 *
 * This is completely independent of [com.example.angelonestrategyexecutor.data.repository.WatchListRepository]
 * and implements a different strategy with real-time candle construction from WebSocket ticks.
 *
 * ## Flow
 * 1. [initialize] – pre-loads historical 15-min candles for each stock, computes
 *    [hourly10SMA] (last 10 completed 1-hour bars) and [volume20SMA] (last 20 15-min bars).
 * 2. [processTick] – called for each [LiveSmartWebSocketService.SnapQuoteTick]; updates the
 *    currently-building 15-min candle for that token. On slot boundary, the previous candle
 *    is closed and all 9 strategy rules are evaluated.
 * 3. [triggerCandleClose] – explicit close at each 15-min clock boundary (belt-and-suspenders
 *    alongside the tick-based slot-change detection).
 * 4. [finalClose] – called at ~16:00 to flush any open candle and stop processing.
 *
 * ## Rules evaluated at every 15-min candle close
 *  1. 15 min close > 50
 *  2. 15 min volume > 100 000
 *  3. 15 min close > 15 min open   (bullish body)
 *  4. 15 min open  > hourly10SMA
 *  5. 15 min close > hourly10SMA
 *  6. 15 min low   < hourly10SMA
 *  7. 15 min volume > 2 × volume20SMA
 *  8. (close − open) > (high − low) × 0.6  (strong body relative to range)
 *  9. (high − low) < previous 15 min close × 0.02  (narrow range candle)
 *
 * If any rule fails, remaining rules are **not** checked (short-circuit).
 * Stocks that pass all rules are appended to [watchListEntries] with the candle timestamp
 * and the CE option selected via the same option-selection logic used in WatchListRepository.
 */
object LiveWatchListStrategyEngine {

    private const val TAG = "LiveWLEngine"
    private const val LIVE_WATCHLIST_FILE = "live_watchlist_results.json"

    // ── IST calendar helpers ──────────────────────────────────────────────────

    private val istZone: ZoneId = ZoneId.of("Asia/Kolkata")
    private val MARKET_OPEN = LocalTime.of(9, 15)
    private val MARKET_CLOSE = LocalTime.of(15, 15) // last slot START time

    /** All 15-min slot start times for a full session (25 candles). */
    private val ALL_SLOT_STARTS: List<LocalTime> = buildList {
        var t = MARKET_OPEN
        while (!t.isAfter(MARKET_CLOSE)) { add(t); t = t.plusMinutes(15) }
    }

    /** Slot start times that are the LAST slot of a complete hourly bucket.
     *  The 9:15–14:15 buckets each contain 4 × 15-min candles; 15:15 is a single-candle bucket. */
    private val HOURLY_BUCKET_END_SLOTS: Set<LocalTime> = setOf(
        LocalTime.of(10, 0),
        LocalTime.of(11, 0),
        LocalTime.of(12, 0),
        LocalTime.of(13, 0),
        LocalTime.of(14, 0),
        LocalTime.of(15, 0),
        LocalTime.of(15, 15),  // 3:15 is both start and end of the last hourly bucket
    )

    /** Start times of each hourly bucket (used when building hourly candles). */
    private val HOURLY_BUCKET_STARTS: List<LocalTime> = listOf(
        LocalTime.of(9, 15),
        LocalTime.of(10, 15),
        LocalTime.of(11, 15),
        LocalTime.of(12, 15),
        LocalTime.of(13, 15),
        LocalTime.of(14, 15),
        LocalTime.of(15, 15),  // single-candle bucket
    )

    // ── Public data structures ────────────────────────────────────────────────

    /** Identifies an equity instrument from the user's stock list. */
    data class StockInput(
        val symbol: String,
        val token: String,
        val exchange: String,      // "NSE" or "BSE"
        val exchangeType: Int,     // 1 = NSE_CM, 3 = BSE_CM
    )

    /** Identifies a derivative instrument (from instruments_cache.json derivatives list). */
    data class DerivativeInput(
        val token: String,
        val symbol: String,
        val lotSize: Int = 0,
        val exchSeg: String = "",
    )

    /** An entry appended to the live watchlist when all rules pass at a candle close. */
    data class LiveWatchListEntry(
        val symbol: String,
        val token: String,
        val exchange: String,
        /** Timestamp of the closing 15-min candle (ISO-8601). */
        val candleTimestamp: String,
        val candle: OhlcvCandle,
        val hourly10SMA: Double,
        val volume20SMA: Double,
        val addedAtMillis: Long = System.currentTimeMillis(),
        val optionSymbol: String? = null,
        val optionToken: String? = null,
        val optionLotSize: Int? = null,
        val optionExchSeg: String? = null,
    )

    // ── Observed state ────────────────────────────────────────────────────────

    private val _watchListEntries = MutableStateFlow<List<LiveWatchListEntry>>(emptyList())
    /** Append-only; grows as stocks pass all rules at successive candle closes. */
    val watchListEntries: StateFlow<List<LiveWatchListEntry>> = _watchListEntries.asStateFlow()

    /**
     * True when a network error or WebSocket disconnection occurred that may have caused
     * missing ticks (and therefore incomplete or inaccurate candle data).
     * Call [clearDirty] once the connection is re-established.
     */
    private val _isDirty = MutableStateFlow(false)
    val isDirty: StateFlow<Boolean> = _isDirty.asStateFlow()

    private val _statusMessage = MutableStateFlow("Not initialised")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _isFetchingHistorical = MutableStateFlow(false)
    /** True while a manual historical-data fetch is in progress. */
    val isFetchingHistorical: StateFlow<Boolean> = _isFetchingHistorical.asStateFlow()

    // ── Internal per-stock state ──────────────────────────────────────────────

    private data class TimedCandle(
        val date: LocalDate,
        val time: LocalTime,
        val open: Double,
        val high: Double,
        val low: Double,
        val close: Double,
        val volume: Double,
    )

    /**
     * Mutable accumulator for the currently-forming 15-min candle.
     *
     * The WebSocket SNAP_QUOTE field "Volume traded for the day" (offset 67) is the
     * **cumulative day volume** — i.e. total shares traded since market open.
     * The 15-min *period* volume is derived as:
     *
     *   periodVolume = cumVol at last tick of this slot
     *                − cumVol at first tick of this slot
     *
     * [cumVolAtStart] is anchored to the first tick that has a valid (> 0) cumulative
     * volume.  Subsequent ticks only update [latestCumVol].
     * [hasValidVolume] becomes true once the anchor is set.
     */
    private class MutableCandle(
        val slotStart: LocalTime,
        val date: LocalDate,
        firstPrice: Double,
        firstCumVolume: Long,
    ) {
        var open: Double = firstPrice
        var high: Double = firstPrice
        var low: Double = firstPrice
        var close: Double = firstPrice

        // −1 sentinel means "not yet anchored"
        private var cumVolAtStart: Long = if (firstCumVolume > 0) firstCumVolume else -1L
        private var latestCumVol:  Long = if (firstCumVolume > 0) firstCumVolume else -1L

        val hasValidVolume: Boolean get() = cumVolAtStart > 0

        /** Volume traded during this 15-min slot (= cumVol delta). */
        val periodVolume: Long
            get() = if (cumVolAtStart > 0 && latestCumVol >= cumVolAtStart)
                        latestCumVol - cumVolAtStart
                    else 0L

        fun update(ltp: Double, cumVolume: Long) {
            if (ltp > high) high = ltp
            if (ltp < low)  low  = ltp
            close = ltp
            if (cumVolume > 0) {
                // Anchor the start to the first valid cumVol we see
                if (cumVolAtStart < 0) cumVolAtStart = cumVolume
                latestCumVol = cumVolume
            }
        }

        fun toTimedCandle() = TimedCandle(
            date = date, time = slotStart,
            open = open, high = high, low = low, close = close,
            volume = periodVolume.toDouble(),   // ← 15-min period volume, NOT cumulative day vol
        )
    }

    private class StockState(
        val symbol: String,
        val token: String,
        val exchange: String,
        val exchangeType: Int,
        /** Pre-loaded historical 15-min candles (past 2+ trading days). */
        historicalCandles: List<TimedCandle>,
        /** Completed 1-hour candles built from the historical data (excludes today). */
        initialHourlyCandles: List<TimedCandle>,
        /** Pre-computed hourly-10 SMA from historical data. */
        initialHourly10SMA: Double,
        /** Pre-computed volume-20 SMA from historical data. */
        initialVolume20SMA: Double,
    ) {
        val historical15m: List<TimedCandle> = historicalCandles
        val hourlyCandles: MutableList<TimedCandle> = initialHourlyCandles.toMutableList()
        val completedLive15m: MutableList<TimedCandle> = mutableListOf()
        var currentCandle: MutableCandle? = null

        var hourly10SMA: Double = initialHourly10SMA
        var volume20SMA: Double = initialVolume20SMA
    }

    /** token → state. Guarded by [lock] for thread safety (WS and coroutine threads). */
    private val stockStates = mutableMapOf<String, StockState>()
    private val lock = Any()

    /** True if [initialize] has been called and at least one stock is loaded. */
    val isInitialized: Boolean get() = synchronized(lock) { stockStates.isNotEmpty() }

    private var derivatives: List<DerivativeInput> = emptyList()
    private val gson = Gson()
    private var diskFile: File? = null

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Must be called once before ticks start flowing.
     * Parses [historicalCandles] (keyed by token), builds hourly bars and initial SMAs,
     * then resets any in-progress candles so the engine is ready for live ticks.
     *
     * @param stocks            Equity instruments from the user's stock list.
     * @param derivativeInputs  Derivative instruments for CE option selection.
     * @param historicalCandles Token → list of 15-min [CandleDataPoint] (last ~2 trading days).
     * @param today             Logical trading date (used to filter today's candles from history).
     */
    fun initialize(
        stocks: List<StockInput>,
        derivativeInputs: List<DerivativeInput>,
        historicalCandles: Map<String, List<CandleDataPoint>>,
        today: LocalDate = LocalDate.now(istZone),
    ) {
        derivatives = derivativeInputs
        // Determine the last fully-closed 15-min slot boundary (candles older than this are done).
        val currentTime = LocalTime.now(istZone)
        val lastClosedSlotEnd = run {
            // Walk back to find the most recent :15/:30/:45/:00 boundary that has already passed.
            val openMin = MARKET_OPEN.hour * 60 + MARKET_OPEN.minute
            val nowMin  = currentTime.hour * 60 + currentTime.minute
            val slotsElapsed = (nowMin - openMin) / 15
            if (slotsElapsed <= 0) MARKET_OPEN
            else LocalTime.of((openMin + slotsElapsed * 15) / 60, (openMin + slotsElapsed * 15) % 60)
        }
        synchronized(lock) {
            stockStates.clear()
            stocks.forEach { stock ->
                val rawCandles = historicalCandles[stock.token] ?: emptyList()
                val allTimed = rawCandles
                    .mapNotNull { parseToTimed(it) }
                    .sortedWith(compareBy({ it.date }, { it.time }))

                // Past-days candles seed the stable historical baseline for SMAs.
                val pastCandles = allTimed.filter { it.date < today }

                // Today's candles that belong to already-closed 15-min slots are pre-loaded
                // into completedLive15m so the engine's state reflects real session progress.
                val todayCompleted = allTimed.filter { it.date == today && it.time < lastClosedSlotEnd }

                val allForSma = pastCandles + todayCompleted
                val hourlyBars = buildHourlyCandles(allForSma)
                val hourly10SMA = computeHourly10SMA(hourlyBars)
                val volume20SMA = computeVolume20SMA(allForSma)

                val state = StockState(
                    symbol = stock.symbol,
                    token = stock.token,
                    exchange = stock.exchange,
                    exchangeType = stock.exchangeType,
                    historicalCandles = pastCandles,
                    initialHourlyCandles = hourlyBars,
                    initialHourly10SMA = hourly10SMA,
                    initialVolume20SMA = volume20SMA,
                )
                // Pre-populate today's completed candles so live ticks continue from here.
                state.completedLive15m.addAll(todayCompleted)

                stockStates[stock.token] = state
                Log.d(
                    TAG,
                    "${stock.symbol}: ${pastCandles.size} hist + ${todayCompleted.size} today candles, " +
                        "${hourlyBars.size} hourly bars, " +
                        "hourly10SMA=${hourly10SMA.fmt()}, volume20SMA=${volume20SMA.fmt()}"
                )
            }
        }
        _isDirty.value = false
        val msg = if (LocalTime.now(istZone).isBefore(MARKET_OPEN)) "waiting for 09:15"
                  else "ready – ${stocks.size} stocks loaded"
        _statusMessage.value = "Initialised ${stocks.size} stocks – $msg"
        Log.d(TAG, "Engine initialised with ${stocks.size} stocks, today=$today, lastClosedSlot=$lastClosedSlotEnd")
    }

    /**
     * Process a SNAP_QUOTE tick from the WebSocket.
     * Builds/updates the current 15-min candle; on slot boundary, closes the old candle
     * and validates all 9 strategy rules.
     */
    fun processTick(
        token: String,
        ltp: Double,
        cumulativeDayVolume: Long,
        now: ZonedDateTime = ZonedDateTime.now(istZone),
    ) {
        val currentTime = now.toLocalTime()
        if (currentTime.isBefore(MARKET_OPEN) || currentTime.isAfter(LocalTime.of(15, 30))) return

        val slotStart = getSlotStart(currentTime) ?: return

        synchronized(lock) {
            val state = stockStates[token] ?: return
            val candle = state.currentCandle

            when {
                candle == null -> {
                    // Start first candle for this stock session
                    state.currentCandle = MutableCandle(slotStart, now.toLocalDate(), ltp, cumulativeDayVolume)
                }
                candle.slotStart != slotStart -> {
                    // We've crossed into a new 15-min slot → close the old candle
                    closeCandleAndValidate(state, candle, candle.date)
                    // Start the new candle
                    state.currentCandle = MutableCandle(slotStart, now.toLocalDate(), ltp, cumulativeDayVolume)
                }
                else -> {
                    // Still in the same slot; update the existing candle
                    candle.update(ltp, cumulativeDayVolume)
                }
            }
        }
    }

    /**
     * Explicitly trigger a candle close at a known 15-min boundary time.
     * Called from a scheduled timer in the foreground service as a belt-and-suspenders
     * mechanism alongside the tick-based slot-change detection in [processTick].
     */
    fun triggerCandleClose(now: ZonedDateTime = ZonedDateTime.now(istZone)) {
        synchronized(lock) {
            stockStates.values.forEach { state ->
                val candle = state.currentCandle ?: return@forEach
                // Only close if the candle's slot is in the past relative to `now`
                val candleEndTime = candle.slotStart.plusMinutes(15)
                if (!now.toLocalTime().isBefore(candleEndTime)) {
                    closeCandleAndValidate(state, candle, candle.date)
                    state.currentCandle = null
                }
            }
        }
        Log.d(TAG, "Candle close triggered at ${now.toLocalTime()}")
    }

    /**
     * Flush any open candles when the session ends (~16:00).
     * Validates rules on the partial candle (may fail, which is fine).
     */
    fun finalClose(now: ZonedDateTime = ZonedDateTime.now(istZone)) {
        synchronized(lock) {
            stockStates.values.forEach { state ->
                val candle = state.currentCandle ?: return@forEach
                closeCandleAndValidate(state, candle, candle.date)
                state.currentCandle = null
            }
        }
        _statusMessage.value = "Session closed – ${_watchListEntries.value.size} match(es) today"
        Log.d(TAG, "Final close at ${now.toLocalTime()}")
    }

    /** Mark the engine dirty due to a network issue; sets [isDirty] = true. */
    fun markDirty(reason: String) {
        _isDirty.value = true
        _statusMessage.value = "⚠ Data may be incomplete: $reason"
        Log.w(TAG, "Engine marked dirty: $reason")
    }

    /** Clear the dirty flag once the connection has been re-established. */
    fun clearDirty() {
        _isDirty.value = false
        Log.d(TAG, "Dirty flag cleared")
    }

    /** Full reset of engine state (called when the service starts fresh each day). */
    fun reset() {
        synchronized(lock) { stockStates.clear() }
        _watchListEntries.value = emptyList()
        _isDirty.value = false
        _statusMessage.value = "Not initialised"
        Log.d(TAG, "Engine reset")
    }

    /** Initialise disk file reference and reload any previously saved entries for today. */
    fun init(context: Context) {
        diskFile = File(context.applicationContext.filesDir, LIVE_WATCHLIST_FILE)
        loadFromDisk()
    }

    /** Save [watchListEntries] to disk so they survive process death/restart. */
    fun saveToDisk() {
        try {
            val file = diskFile ?: return
            file.writeText(gson.toJson(_watchListEntries.value))
        } catch (e: Exception) {
            Log.e(TAG, "saveToDisk failed: ${e.message}", e)
        }
    }

    /**
     * Manually fetch historical 15-min candles for all stocks in stocks_list.json
     * and re-initialise the engine. Safe to call from the UI at any time.
     * Progress is reflected in [statusMessage]; loading state in [isFetchingHistorical].
     */
    fun manualFetchAndInit(context: Context) {
        if (_isFetchingHistorical.value) return
        _isFetchingHistorical.value = true
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val appContext = context.applicationContext
                AuthState.init(appContext)

                val creds = AuthState.credentials.value
                if (creds == null || creds.isExpired) {
                    _statusMessage.value = "Cannot fetch – please log in first"
                    return@launch
                }

                val stocks = loadStocksFromDisk(appContext)
                if (stocks.isEmpty()) {
                    _statusMessage.value = "No stocks configured"
                    return@launch
                }
                val derivativeInputs = loadDerivativesFromDisk(appContext)

                _statusMessage.value = "Fetching historical data for ${stocks.size} stocks…"
                val historicalCandles = mutableMapOf<String, List<CandleDataPoint>>()
                val now = ZonedDateTime.now(istZone)
                // Fetch up to the current moment so today's completed candles are included.
                val toMillis = now.toInstant().toEpochMilli()
                val fromMillis = now.minusDays(5)
                    .withHour(9).withMinute(0).withSecond(0).withNano(0)
                    .toInstant().toEpochMilli()

                stocks.forEachIndexed { index, stock ->
                    if (stock.token.isBlank()) return@forEachIndexed
                    if (index > 0) delay(400L)
                    _statusMessage.value = "Fetching ${index + 1}/${stocks.size}: ${stock.symbol}…"
                    try {
                        val candles = HistoricalCandleRepository.getCandlesInRange(
                            exchange = stock.exchange,
                            symbolToken = stock.token,
                            interval = "FIFTEEN_MINUTE",
                            fromMillis = fromMillis,
                            toMillis = toMillis,
                        )
                        if (candles.isNotEmpty()) historicalCandles[stock.token] = candles
                    } catch (e: Exception) {
                        Log.e(TAG, "Fetch failed for ${stock.symbol}: ${e.message}", e)
                    }
                }

                initialize(
                    stocks = stocks,
                    derivativeInputs = derivativeInputs,
                    historicalCandles = historicalCandles,
                    today = LocalDate.now(istZone),
                )
                _statusMessage.value = "Historical data loaded – starting stream…"
                Log.d(TAG, "Manual fetch complete – ${historicalCandles.size}/${stocks.size} stocks fetched")
                // Start the foreground service so the WebSocket connects and live ticks flow.
                // The service sees isInitialized=true and skips its own fetch+reset.
                LiveWatchListForegroundService.start(appContext)
            } finally {
                _isFetchingHistorical.value = false
            }
        }
    }

    // ── Disk helpers (also used by manualFetchAndInit) ────────────────────────

    private fun loadStocksFromDisk(context: Context): List<StockInput> {
        val file = File(context.filesDir, "instruments_cache.json")
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
                StockInput(
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

    private fun loadDerivativesFromDisk(context: Context): List<DerivativeInput> {
        val file = File(context.filesDir, "instruments_cache.json")
        if (!file.exists()) return emptyList()
        return try {
            @Suppress("UNCHECKED_CAST")
            val root = gson.fromJson(file.readText(), Map::class.java) as? Map<String, Any>
            @Suppress("UNCHECKED_CAST")
            val rawList = root?.get("derivatives") as? List<Map<String, Any>> ?: return emptyList()
            rawList.mapNotNull { m ->
                val token = m["token"] as? String ?: return@mapNotNull null
                val symbol = m["symbol"] as? String ?: return@mapNotNull null
                val lotSize = (m["lotSize"] as? Double)?.toInt() ?: 0
                val exchSeg = m["exchSeg"] as? String ?: ""
                DerivativeInput(token, symbol, lotSize, exchSeg)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read instruments_cache.json: ${e.message}", e)
            emptyList()
        }
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun closeCandleAndValidate(state: StockState, mutable: MutableCandle, date: LocalDate) {
        val closed = mutable.toTimedCandle()  // volume = periodVolume (cumVol delta)
        state.completedLive15m.add(closed)

        // volume20SMA: prefer today's 15-min period volumes once we have enough;
        // fall back to mixing in past-days candles only if today has fewer than 20.
        val todayCandles = state.completedLive15m  // already time-ordered
        val candidatesForSma = if (todayCandles.size >= 20) todayCandles
                               else state.historical15m + todayCandles
        state.volume20SMA = computeVolume20SMA(candidatesForSma)

        Log.d(
            TAG,
            "${state.symbol} candle@${closed.time}: " +
                "O=${closed.open} H=${closed.high} L=${closed.low} C=${closed.close} " +
                "15mVol=${closed.volume.toLong()} (period delta) " +
                "v20SMA=${state.volume20SMA.fmt()} " +
                "h10SMA=${state.hourly10SMA.fmt()}"
        )

        // Check if this close finishes a full hourly bucket → rebuild hourly SMA
        if (closed.time in HOURLY_BUCKET_END_SLOTS) {
            tryBuildHourlyCandle(state, date)
        }

        // Validate strategy rules
        val prevCandle = if (state.completedLive15m.size >= 2) {
            state.completedLive15m[state.completedLive15m.size - 2]
        } else {
            state.historical15m.lastOrNull()
        }

        if (validateRules(closed, prevCandle, state.hourly10SMA, state.volume20SMA)) {
            onRulesPassed(state, closed)
        }
    }

    /**
     * Build one hourly candle from completed live 15-min candles.
     * Buckets 9:15–14:15 each need 4 slots; 15:15 is a single-candle bucket.
     */
    private fun tryBuildHourlyCandle(state: StockState, date: LocalDate) {
        val live = state.completedLive15m
        val closedSlot = live.lastOrNull()?.time ?: return

        // Special case: 15:15 is a single-candle hourly bucket
        if (closedSlot == LocalTime.of(15, 15)) {
            val candle = live.last()
            val hourlyBar = TimedCandle(
                date = date,
                time = LocalTime.of(15, 15),
                open = candle.open,
                high = candle.high,
                low = candle.low,
                close = candle.close,
                volume = candle.volume,
            )
            state.hourlyCandles.add(hourlyBar)
            state.hourly10SMA = computeHourly10SMA(state.hourlyCandles)
            Log.d(TAG, "${state.symbol}: new hourly bar 15:15 (single-candle), close=${hourlyBar.close}, hourly10SMA=${state.hourly10SMA.fmt()}")
            return
        }

        // Normal case: 4-candle bucket
        if (live.size < 4) return
        val last4 = live.takeLast(4)
        val bucketStart = HOURLY_BUCKET_STARTS.lastOrNull { bucket ->
            bucket != LocalTime.of(15, 15) && !last4.first().time.isBefore(bucket)
        } ?: return

        val inBucket = last4.filter { !it.time.isBefore(bucketStart) }
        if (inBucket.size < 4) return

        val hourlyBar = TimedCandle(
            date = date,
            time = bucketStart,
            open = inBucket.first().open,
            high = inBucket.maxOf { it.high },
            low = inBucket.minOf { it.low },
            close = inBucket.last().close,
            volume = inBucket.sumOf { it.volume },
        )
        state.hourlyCandles.add(hourlyBar)
        state.hourly10SMA = computeHourly10SMA(state.hourlyCandles)

        Log.d(
            TAG,
            "${state.symbol}: hourly bar ${hourlyBar.time} " +
                "vol=${hourlyBar.volume.toLong()} (sum of ${inBucket.size}×15m: " +
                "${inBucket.map { it.volume.toLong() }.joinToString("+")}) " +
                "close=${hourlyBar.close} h10SMA=${state.hourly10SMA.fmt()}"
        )
    }

    /** Evaluate all 9 rules, short-circuiting on the first failure. */
    private fun validateRules(
        c: TimedCandle,
        prev: TimedCandle?,
        hourly10SMA: Double,
        volume20SMA: Double,
    ): Boolean {
        // Rule 1 – close > 50
        if (c.close <= 50.0) return false

        // Rule 2 – volume > 100 000
        if (c.volume <= 100_000.0) return false

        // Rule 3 – close > open (bullish body)
        if (c.close <= c.open) return false

        // Rules 4–6 require a valid hourly10SMA
        if (hourly10SMA.isNaN() || hourly10SMA <= 0.0) return false

        // Rule 4 – open > hourly10SMA
        if (c.open <= hourly10SMA) return false

        // Rule 5 – close > hourly10SMA
        if (c.close <= hourly10SMA) return false

        // Rule 6 – low < hourly10SMA
        if (c.low >= hourly10SMA) return false

        // Rule 7 – volume > 2 × volume20SMA
        if (volume20SMA.isNaN() || c.volume <= 2.0 * volume20SMA) return false

        val body = c.close - c.open
        val range = c.high - c.low

        // Rule 8 – (close − open) > (high − low) × 0.6  (strong body)
        if (range > 0.0 && body <= range * 0.6) return false

        // Rule 9 – (high − low) < previous close × 0.02  (narrow candle)
        if (prev != null && range >= prev.close * 0.02) return false

        return true
    }

    private fun onRulesPassed(state: StockState, candle: TimedCandle) {
        val candleOhlcv = OhlcvCandle(
            timestamp = "${candle.date}T${candle.time}",
            open = candle.open,
            high = candle.high,
            low = candle.low,
            close = candle.close,
            volume = candle.volume,
        )
        val option = selectCeOption(
            stockSymbol = state.symbol,
            strikeAbove = candle.high,
            scanDate = candle.date,
        )
        val entry = LiveWatchListEntry(
            symbol = state.symbol,
            token = state.token,
            exchange = state.exchange,
            candleTimestamp = "${candle.date}T${candle.time}",
            candle = candleOhlcv,
            hourly10SMA = state.hourly10SMA,
            volume20SMA = state.volume20SMA,
            addedAtMillis = System.currentTimeMillis(),
            optionSymbol = option?.symbol,
            optionToken = option?.token,
            optionLotSize = option?.lotSize,
            optionExchSeg = option?.exchSeg,
        )
        _watchListEntries.value = _watchListEntries.value + entry
        _statusMessage.value = "Live watchlist: ${_watchListEntries.value.size} match(es)"
        saveToDisk()
        Log.i(
            TAG,
            "✓ ${state.symbol} passed all rules at ${candle.date}T${candle.time} | " +
                "close=${candle.close}, vol=${candle.volume}, h10SMA=${state.hourly10SMA.fmt()}, " +
                "v20SMA=${state.volume20SMA.fmt()}" +
                if (option != null) " → option ${option.symbol}" else ""
        )
    }

    // ── SMA helpers ───────────────────────────────────────────────────────────

    private fun computeHourly10SMA(bars: List<TimedCandle>): Double =
        if (bars.isEmpty()) Double.NaN
        else bars.takeLast(10).map { it.close }.average()

    private fun computeVolume20SMA(candles: List<TimedCandle>): Double =
        if (candles.isEmpty()) Double.NaN
        else candles.takeLast(20).map { it.volume }.average()

    // ── Hourly bar builder (from historical 15-min data) ─────────────────────

    private fun buildHourlyCandles(sortedCandles: List<TimedCandle>): List<TimedCandle> {
        val result = mutableListOf<TimedCandle>()
        val byDay = sortedCandles.groupBy { it.date }.toSortedMap()

        byDay.forEach { (date, dayCandles) ->
            val byTime = dayCandles.associateBy { it.time }
            HOURLY_BUCKET_STARTS.forEach { bucketStart ->
                // 15:15 is a single-candle bucket; all others need 4 slots
                if (bucketStart == LocalTime.of(15, 15)) {
                    val candle = byTime[bucketStart] ?: return@forEach
                    result.add(
                        TimedCandle(
                            date = date,
                            time = bucketStart,
                            open = candle.open,
                            high = candle.high,
                            low = candle.low,
                            close = candle.close,
                            volume = candle.volume,
                        )
                    )
                    return@forEach
                }

                val slots = listOf(
                    bucketStart,
                    bucketStart.plusMinutes(15),
                    bucketStart.plusMinutes(30),
                    bucketStart.plusMinutes(45),
                )
                if (!slots.all { byTime.containsKey(it) }) return@forEach
                val s = slots.mapNotNull { byTime[it] }
                result.add(
                    TimedCandle(
                        date = date,
                        time = bucketStart,
                        open = s.first().open,
                        high = s.maxOf { it.high },
                        low = s.minOf { it.low },
                        close = s.last().close,
                        volume = s.sumOf { it.volume },
                    )
                )
            }
        }
        return result.sortedWith(compareBy({ it.date }, { it.time }))
    }

    // ── Slot calculation ──────────────────────────────────────────────────────

    /**
     * Map a clock time to its 15-min slot start.
     * e.g. 09:31 → 09:30, 14:58 → 14:45, 15:30 → null (out of session).
     */
    private fun getSlotStart(time: LocalTime): LocalTime? {
        val openMin = MARKET_OPEN.hour * 60 + MARKET_OPEN.minute           // 555
        val closeMin = MARKET_CLOSE.hour * 60 + MARKET_CLOSE.minute + 15   // 930 (slot end)
        val t = time.hour * 60 + time.minute
        if (t < openMin || t >= closeMin) return null
        val slotIdx = (t - openMin) / 15
        val slotMin = openMin + slotIdx * 15
        return LocalTime.of(slotMin / 60, slotMin % 60)
    }

    // ── Option selection ──────────────────────────────────────────────────────

    private val nfoOptionPattern = Regex("^([A-Z0-9]+)(\\d{2}[A-Z]{3}\\d{2})(\\d+)(CE|PE)$")
    private val expiryFmt: DateTimeFormatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("ddMMMyy")
        .toFormatter(Locale.ENGLISH)

    /**
     * Selects the monthly CE option whose strike is the smallest value strictly above
     * [strikeAbove] (= closing candle's high price). Uses the same expiry-selection logic
     * as WatchListRepository (next month if day > 20, else current month; latest expiry in
     * that month = monthly expiry).
     */
    private fun selectCeOption(
        stockSymbol: String,
        strikeAbove: Double,
        scanDate: LocalDate,
    ): DerivativeInput? {
        val underlying = stockSymbol
            .removeSuffix("-EQ").removeSuffix("-eq")
            .uppercase(Locale.ENGLISH)
            .replace(Regex("[^A-Z0-9]"), "")

        val targetMonth = if (scanDate.dayOfMonth > 20) {
            YearMonth.from(scanDate).plusMonths(1)
        } else {
            YearMonth.from(scanDate)
        }

        data class Parsed(val d: DerivativeInput, val expiry: LocalDate, val strike: Double)

        val parsed = derivatives.mapNotNull { d ->
            val m = nfoOptionPattern.matchEntire(d.symbol.uppercase(Locale.ENGLISH)) ?: return@mapNotNull null
            val parsedUnd = m.groupValues[1].replace(Regex("[^A-Z0-9]"), "")
            if (parsedUnd != underlying) return@mapNotNull null
            if (m.groupValues[4] != "CE") return@mapNotNull null
            val expiry = runCatching { LocalDate.parse(m.groupValues[2], expiryFmt) }.getOrNull() ?: return@mapNotNull null
            if (YearMonth.from(expiry) != targetMonth) return@mapNotNull null
            val strike = m.groupValues[3].toDoubleOrNull() ?: return@mapNotNull null
            Parsed(d, expiry, strike)
        }

        if (parsed.isEmpty()) return null
        val latestExpiry = parsed.maxOf { it.expiry }
        return parsed
            .filter { it.expiry == latestExpiry && it.strike > strikeAbove }
            .minByOrNull { it.strike }
            ?.d
    }

    // ── Timestamp parsing ─────────────────────────────────────────────────────

    private fun parseToTimed(c: CandleDataPoint): TimedCandle? {
        val zdt = parseTimestamp(c.timestamp) ?: return null
        return TimedCandle(
            date = zdt.toLocalDate(),
            time = zdt.toLocalTime().withSecond(0).withNano(0),
            open = c.open, high = c.high, low = c.low, close = c.close, volume = c.volume,
        )
    }

    private fun parseTimestamp(raw: String): ZonedDateTime? {
        val v = raw.trim()
        return runCatching { OffsetDateTime.parse(v).atZoneSameInstant(istZone) }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(v).withZoneSameInstant(istZone) }.getOrNull()
            ?: runCatching {
                LocalDateTime.parse(v, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    .atZone(istZone)
            }.getOrNull()
            ?: runCatching {
                LocalDateTime.parse(v, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    .atZone(istZone)
            }.getOrNull()
            ?: runCatching {
                LocalDateTime.parse(v, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
                    .atZone(istZone)
            }.getOrNull()
    }

    // ── Disk persistence ──────────────────────────────────────────────────────

    private fun loadFromDisk() {
        val file = diskFile ?: return
        if (!file.exists()) return
        try {
            val type = object : TypeToken<List<LiveWatchListEntry>>() {}.type
            val saved: List<LiveWatchListEntry>? = gson.fromJson(file.readText(), type)
            if (!saved.isNullOrEmpty()) {
                _watchListEntries.value = saved
                Log.d(TAG, "Loaded ${saved.size} entries from disk")
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadFromDisk failed: ${e.message}", e)
        }
    }

    // ── Formatting helper ─────────────────────────────────────────────────────

    private fun Double.fmt() = if (isNaN()) "NaN" else "%.2f".format(this)
}
