package com.example.angelonestrategyexecutor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.angelonestrategyexecutor.worker.WatchListScanWorker

class WatchListScanReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WatchListScanRcvr"
    }

    override fun onReceive(context: Context, _intent: Intent) {
        val appContext = context.applicationContext
        try {
            // Exact alarms are one-shot; schedule next run on every trigger.
            WatchListScanScheduler.schedule(appContext)
            // Run long scan off the broadcast lifecycle to avoid receiver timeout ANRs.
            WatchListScanWorker.enqueue(appContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue WatchList scan: ${e.message}", e)
        }
    }
}
