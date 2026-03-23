package com.example.angelonestrategyexecutor.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

/**
 * Schedules a daily exact alarm using AlarmManager.
 * Uses setExactAndAllowWhileIdle for reliable delivery even during Doze.
 * Since exact alarms don't repeat, the receiver re-schedules after each firing.
 */
object LoginAlarmScheduler {

    private const val TAG = "LoginAlarmScheduler"
    private const val ALARM_REQUEST_CODE = 8050

    /**
     * Schedule (or re-schedule) the next alarm at the configured time.
     * Safe to call multiple times — uses the same request code so it replaces any existing alarm.
     */
    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(context, LoginReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Read configured time from AppConfig
        val hour = com.example.angelonestrategyexecutor.data.config.AppConfig.reminderHour.value
        val minute = com.example.angelonestrategyexecutor.data.config.AppConfig.reminderMinute.value

        // Set target to configured time today (or tomorrow if already past)
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1) // next day
            }
        }

        // Check exact alarm permission on API 31+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "Exact alarm permission not granted — using inexact alarm")
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent,
            )
        } else {
            // Use exact alarm for reliable delivery even during Doze
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent,
            )
        }

        Log.d(TAG, "Login reminder alarm scheduled for ${calendar.time}")
    }

    /**
     * Cancel the alarm if needed.
     */
    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, LoginReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Login reminder alarm cancelled")
    }
}
