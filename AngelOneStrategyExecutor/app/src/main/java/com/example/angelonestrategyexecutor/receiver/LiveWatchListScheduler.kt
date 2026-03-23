package com.example.angelonestrategyexecutor.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

/**
 * Schedules (and cancels) a daily exact alarm at 09:00 to start the
 * [LiveWatchListForegroundService] via [LiveWatchListAlarmReceiver].
 *
 * Call [schedule] once at app startup and again from [AlarmBootReceiver] so the
 * alarm is re-registered after device reboot.
 */
object LiveWatchListScheduler {

    private const val TAG = "LiveWLScheduler"
    private const val ALARM_REQUEST_CODE = 10060  // unique; does not clash with other schedulers

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = buildPendingIntent(appContext)

        val triggerAt = nextNineAmSlot()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "Exact alarm permission not granted – using inexact alarm for live watchlist")
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt.timeInMillis,
                pendingIntent,
            )
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt.timeInMillis,
                pendingIntent,
            )
        }

        Log.d(TAG, "Live watchlist alarm scheduled for ${triggerAt.time}")
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildPendingIntent(appContext))
        Log.d(TAG, "Live watchlist alarm cancelled")
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, LiveWatchListAlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Returns the next 09:00 calendar instant.
     * If the current time is already past 09:00 today, schedules for 09:00 tomorrow.
     */
    private fun nextNineAmSlot(nowMillis: Long = System.currentTimeMillis()): Calendar {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= nowMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
    }
}
