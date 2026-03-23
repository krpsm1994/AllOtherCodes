package com.example.angelonestrategyexecutor.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.angelonestrategyexecutor.data.repository.InstrumentsRefreshRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WeeklyInstrumentsRefreshReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WeeklyRefreshRcvr"
    }

    override fun onReceive(context: Context, _intent: Intent) {
        val appContext = context.applicationContext

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val summary = InstrumentsRefreshRepository.refreshAndCache(appContext)
                Log.d(
                    TAG,
                    "Weekly refresh complete: equities=${summary.equitiesCount}, derivatives=${summary.derivativesCount}, underlyings=${summary.underlyingsCount}",
                )
            } catch (e: Exception) {
                Log.e(TAG, "Weekly refresh failed: ${e.message}", e)
            } finally {
                // Exact alarms are one-shot, so always schedule the next Friday run.
                WeeklyInstrumentsRefreshScheduler.schedule(appContext)
                pendingResult.finish()
            }
        }
    }
}
