package com.example.angelonestrategyexecutor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.angelonestrategyexecutor.service.LiveWatchListForegroundService

/**
 * Receives the daily 09:00 alarm broadcast from [LiveWatchListScheduler] and
 * starts [LiveWatchListForegroundService].
 *
 * The alarm is one-shot (exact alarms), so [LiveWatchListScheduler.schedule] is
 * called here to re-arm the alarm for the following day.
 */
class LiveWatchListAlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "LiveWLAlarmRcvr"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        Log.d(TAG, "09:00 alarm received – starting live watchlist service and re-arming alarm")

        // Re-arm for the next trading day before doing anything else
        LiveWatchListScheduler.schedule(appContext)

        // Start the foreground service
        LiveWatchListForegroundService.start(appContext)
    }
}
