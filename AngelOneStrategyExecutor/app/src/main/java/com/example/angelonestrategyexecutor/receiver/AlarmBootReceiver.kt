package com.example.angelonestrategyexecutor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmBootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val appContext = context.applicationContext
        Log.d(TAG, "Boot completed, re-scheduling app alarms")
        LoginAlarmScheduler.schedule(appContext)
        WeeklyInstrumentsRefreshScheduler.schedule(appContext)
        WatchListScanScheduler.schedule(appContext)
        LiveWatchListScheduler.schedule(appContext)
    }
}
