package com.example.angelonestrategyexecutor.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.angelonestrategyexecutor.data.repository.WatchListRepository

class WatchListScanWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val TAG = "WatchListScanWorker"
        private const val UNIQUE_WORK_NAME = "watchlist_scan_work"

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<WatchListScanWorker>().build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(
                    UNIQUE_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    request,
                )
        }
    }

    override suspend fun doWork(): Result {
        return try {
            val snapshot = WatchListRepository.scanAndUpdate(applicationContext)
            Log.d(
                TAG,
                "WatchList scan completed: scanned=${snapshot.totalScanned}, matched=${snapshot.totalMatched}, msg=${snapshot.message}",
            )
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "WatchList scan failed: ${e.message}", e)
            Result.retry()
        }
    }
}
