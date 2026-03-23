package com.example.angelonestrategyexecutor.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

object WeeklyInstrumentsRefreshScheduler {

    private const val TAG = "WeeklyRefreshSched"
    private const val ALARM_REQUEST_CODE = 9050
    private const val TARGET_DAY_OF_WEEK = Calendar.FRIDAY
    private const val TARGET_HOUR = 15
    private const val TARGET_MINUTE = 30

    fun schedule(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WeeklyInstrumentsRefreshReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val calendar = nextRunCalendar()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "Exact alarm permission not granted, falling back to inexact alarm")
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent,
            )
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent,
            )
        }

        Log.d(TAG, "Weekly instruments refresh scheduled for ${calendar.time}")
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WeeklyInstrumentsRefreshReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "Weekly instruments refresh cancelled")
    }

    private fun nextRunCalendar(nowMillis: Long = System.currentTimeMillis()): Calendar {
        return Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, TARGET_DAY_OF_WEEK)
            set(Calendar.HOUR_OF_DAY, TARGET_HOUR)
            set(Calendar.MINUTE, TARGET_MINUTE)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= nowMillis) {
                add(Calendar.WEEK_OF_YEAR, 1)
            }
        }
    }
}
