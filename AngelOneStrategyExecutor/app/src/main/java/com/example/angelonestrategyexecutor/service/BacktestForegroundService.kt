package com.example.angelonestrategyexecutor.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.angelonestrategyexecutor.MainActivity
import com.example.angelonestrategyexecutor.R
import com.example.angelonestrategyexecutor.data.repository.WatchListRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.launch

/**
 * Foreground service that runs a backtest scan in the background,
 * keeping it alive even if the app is moved to background or closed.
 *
 * Usage:
 *   BacktestForegroundService.start(context, fromDate, toDate)
 *   BacktestForegroundService.stop(context)   // or WatchListRepository.stopBacktest()
 */
class BacktestForegroundService : Service() {

    companion object {
        private const val TAG = "BacktestFgService"
        private const val CHANNEL_ID = "backtest_channel"
        private const val NOTIFICATION_ID = 1004
        private const val ACTION_START = "com.example.angelonestrategyexecutor.START_BACKTEST"
        private const val ACTION_STOP = "com.example.angelonestrategyexecutor.STOP_BACKTEST"
        private const val EXTRA_FROM_DATE = "from_date"
        private const val EXTRA_TO_DATE = "to_date"

        fun start(context: Context, fromDate: LocalDate, toDate: LocalDate) {
            val intent = Intent(context, BacktestForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_FROM_DATE, fromDate.toString())
                putExtra(EXTRA_TO_DATE, toDate.toString())
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, BacktestForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                Log.d(TAG, "Stop requested")
                WatchListRepository.stopBacktest()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                val fromDate = intent?.getStringExtra(EXTRA_FROM_DATE)
                    ?.let { LocalDate.parse(it) } ?: run {
                    Log.e(TAG, "Missing from_date extra, stopping")
                    stopSelf()
                    return START_NOT_STICKY
                }
                val toDate = intent?.getStringExtra(EXTRA_TO_DATE)
                    ?.let { LocalDate.parse(it) } ?: run {
                    Log.e(TAG, "Missing to_date extra, stopping")
                    stopSelf()
                    return START_NOT_STICKY
                }
                Log.d(TAG, "Starting backtest from=$fromDate to=$toDate")
                startForeground(NOTIFICATION_ID, buildNotification("Starting\u2026"))
                runBacktest(fromDate, toDate)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        // Ensure progress state is reset if the service is killed unexpectedly
        if (WatchListRepository.backtestProgress.value.isRunning) {
            WatchListRepository.stopBacktest()
        }
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }

    private fun runBacktest(fromDate: LocalDate, toDate: LocalDate) {
        // Observe progress to update notification while running
        serviceScope.launch {
            WatchListRepository.backtestProgress
                .takeWhile { it.isRunning }
                .collect { progress ->
                    if (progress.total > 0) {
                        val pct = progress.scanned * 100 / progress.total
                        updateNotification("Scanning ${progress.scanned}/${progress.total} ($pct%)")
                    }
                }
        }

        // Run the actual backtest
        serviceScope.launch {
            WatchListRepository.runBacktest(
                context = applicationContext,
                fromDate = fromDate,
                toDate = toDate,
            )
            val finalProgress = WatchListRepository.backtestProgress.value
            Log.d(TAG, "Backtest finished: ${finalProgress.message}")
            showCompletionNotification(finalProgress.message)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Backtest",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows backtest scan progress"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
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
        val stopIntent = Intent(this, BacktestForegroundService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("AngelOne Backtest")
            .setContentText(contentText)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(0, "Stop", stopPending)
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun showCompletionNotification(text: String) {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Backtest Complete")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID + 1, notification)
    }
}
