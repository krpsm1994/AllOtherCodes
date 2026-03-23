package com.example.angelonestrategyexecutor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.angelonestrategyexecutor.MainActivity
import com.example.angelonestrategyexecutor.R
import com.example.angelonestrategyexecutor.data.auth.AuthState
import com.example.angelonestrategyexecutor.data.websocket.LtpRepository
import com.example.angelonestrategyexecutor.ui.screens.StockEntry
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/**
 * Foreground service that keeps the WebSocket connection alive
 * in the background to stream live LTP prices.
 *
 * Usage:
 *   LtpForegroundService.start(context, stockList)
 *   LtpForegroundService.stop(context)
 */
class LtpForegroundService : Service() {

    companion object {
        private const val TAG = "LtpFgService"
        private const val CHANNEL_ID = "ltp_streaming_channel"
        private const val NOTIFICATION_ID = 1001
        private const val ACTION_START = "com.example.angelonestrategyexecutor.START_LTP"
        private const val ACTION_STOP = "com.example.angelonestrategyexecutor.STOP_LTP"
        private const val SOCKET_OPEN_TIMEOUT_MS = 12_000L

        /**
         * Start the foreground service and connect WebSocket.
         * Reads the stock list from disk to subscribe tokens.
         */
        fun start(context: Context) {
            val intent = Intent(context, LtpForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Stop the foreground service and disconnect WebSocket.
         */
        fun stop(context: Context) {
            val intent = Intent(context, LtpForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val gson = Gson()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var socketOpenCheckRunnable: Runnable? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.d(TAG, "Stopping LTP service")
                cancelSocketOpenCheck()
                LtpRepository.disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                Log.d(TAG, "Starting LTP service")
                startForeground(NOTIFICATION_ID, buildNotification("Connecting…"))
                connectAndSubscribe()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        cancelSocketOpenCheck()
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }

    private fun connectAndSubscribe() {
        // Ensure auth is initialised (for cold starts via START_STICKY restart)
        if (AuthState.credentials.value == null) {
            AuthState.init(applicationContext)
        }

        val creds = AuthState.credentials.value
        if (creds == null || creds.isExpired) {
            Log.w(TAG, "No valid credentials, stopping service")
            cancelSocketOpenCheck()
            updateNotification("Session expired – please login")
            LtpRepository.disconnect()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        // Read stock list from disk and subscribe
        val stocksFile = File(filesDir, "stocks_list.json")
        if (stocksFile.exists()) {
            try {
                val json = stocksFile.readText()
                val type = object : TypeToken<List<StockEntry>>() {}.type
                val stockList: List<StockEntry> = gson.fromJson(json, type) ?: emptyList()

                val tokensByExchange = mutableMapOf<Int, MutableList<String>>()
                stockList.forEach { stock ->
                    // Skip tokens for closed/expired orders — they no longer need live prices
                    if (isGroupedStatus(stock.orderStatus)) return@forEach

                    if (stock.symbolToken.isNotBlank()) {
                        val exchType = stock.symbolExchangeType?.takeIf { it > 0 } ?: 1
                        tokensByExchange.getOrPut(exchType) { mutableListOf() }.add(stock.symbolToken)
                    }
                    if (stock.optionToken.isNotBlank()) {
                        val exchType = stock.optionExchangeType?.takeIf { it > 0 } ?: 2
                        tokensByExchange.getOrPut(exchType) { mutableListOf() }.add(stock.optionToken)
                    }
                }

                val totalTokens = tokensByExchange.values.sumOf { it.distinct().size }
                if (totalTokens <= 0) {
                    Log.d(TAG, "No active tokens to stream, stopping service")
                    cancelSocketOpenCheck()
                    LtpRepository.disconnect()
                    updateNotification("No active stocks to stream")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return
                }

                // Connect WebSocket only when there are active tokens to stream.
                if (!LtpRepository.isConnected) {
                    LtpRepository.connect()
                }
                scheduleSocketOpenCheck()

                tokensByExchange.forEach { (exchType, tokens) ->
                    val distinct = tokens.distinct()
                    Log.d(TAG, "Subscribing ${distinct.size} tokens on exchange=$exchType")
                    LtpRepository.subscribeByExchange(exchType, distinct)
                }
                updateNotification("Streaming $totalTokens tokens")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load stocks: ${e.message}")
                cancelSocketOpenCheck()
                updateNotification("Error loading stocks")
                LtpRepository.disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        } else {
            Log.d(TAG, "stocks_list.json not found, stopping service")
            cancelSocketOpenCheck()
            LtpRepository.disconnect()
            updateNotification("No stocks added yet")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun scheduleSocketOpenCheck() {
        cancelSocketOpenCheck()
        socketOpenCheckRunnable = Runnable {
            if (LtpRepository.connectionState.value != LtpRepository.ConnectionState.CONNECTED) {
                Log.w(TAG, "WebSocket not opened within timeout, stopping foreground process")
                updateNotification("Socket not open - stopped")
                LtpRepository.disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        mainHandler.postDelayed(socketOpenCheckRunnable!!, SOCKET_OPEN_TIMEOUT_MS)
    }

    private fun cancelSocketOpenCheck() {
        val runnable = socketOpenCheckRunnable ?: return
        mainHandler.removeCallbacks(runnable)
        socketOpenCheckRunnable = null
    }

    private fun isGroupedStatus(status: String?): Boolean {
        return when (status?.trim()?.lowercase()) {
            "closed", "not triggered", "complete" -> true
            else -> false
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "LTP Streaming",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Keeps WebSocket alive for live stock prices"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = Intent(this, LtpForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("AngelOne LTP")
            .setContentText(contentText)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(0, "Stop", stopPendingIntent)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(text))
    }
}
