package com.example.angelonestrategyexecutor.receiver

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.angelonestrategyexecutor.data.config.AppConfig
import java.util.Calendar

object WatchListScanScheduler {

    private const val TAG = "WatchListScanSched"
    private const val ALARM_REQUEST_CODE = 10050

    fun schedule(context: Context) {
        val appContext = context.applicationContext
        AppConfig.init(appContext)

        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(appContext, WatchListScanReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val scanMode = AppConfig.watchListScanMode.value
        val triggerCalendar = when (scanMode) {
            AppConfig.WATCHLIST_SCAN_MODE_EVERY_15_MIN -> nextFifteenMinuteSlot()
            else -> nextNineThirtyRun()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            Log.w(TAG, "Exact alarm permission missing, using inexact watchlist scan alarm")
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerCalendar.timeInMillis,
                pendingIntent,
            )
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerCalendar.timeInMillis,
                pendingIntent,
            )
        }

        Log.d(TAG, "WatchList scan scheduled for ${triggerCalendar.time}, mode=$scanMode")
    }

    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(appContext, WatchListScanReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.cancel(pendingIntent)
        Log.d(TAG, "WatchList scan alarm cancelled")
    }

    private fun nextNineThirtyRun(nowMillis: Long = System.currentTimeMillis()): Calendar {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= nowMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
    }

    private fun nextFifteenMinuteSlot(nowMillis: Long = System.currentTimeMillis()): Calendar {
        return Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)

            val minute = get(Calendar.MINUTE)
            val delta = 15 - (minute % 15)
            add(Calendar.MINUTE, if (delta == 0) 15 else delta)
        }
    }
}
