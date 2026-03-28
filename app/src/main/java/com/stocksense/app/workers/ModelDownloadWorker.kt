package com.stocksense.app.workers

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.stocksense.app.engine.BitNetModelDownloader
import com.stocksense.app.engine.QualityMode
import java.util.concurrent.TimeUnit

private const val TAG = "ModelDownloadWorker"
private const val UNIQUE_WORK_NAME = "bitnet_model_download"

/**
 * WorkManager job that downloads the Microsoft BitNet 1-bit LLM model in the
 * background on first app launch.
 *
 * ### Behaviour
 * - Only runs when the device has a network connection.
 * - Automatically retries with exponential back-off on transient failures.
 * - Uses [ExistingWorkPolicy.KEEP] so it is a no-op once the model is present.
 *
 * ### Scheduling
 * Call [schedule] from [com.stocksense.app.StockSenseApp.onCreate].
 */
class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val downloader = BitNetModelDownloader(applicationContext)

        // Auto-select quality mode based on device RAM (same logic as LLMInsightEngine).
        val mode = autoSelectMode()

        if (downloader.isModelAvailable(mode)) {
            Log.i(TAG, "Model for $mode already on disk – nothing to do")
            return Result.success()
        }

        Log.i(TAG, "Starting BitNet model download for mode=$mode (attempt $runAttemptCount)")
        val success = downloader.download(mode)

        return if (success) {
            Log.i(TAG, "Model download succeeded")
            Result.success()
        } else {
            if (runAttemptCount < 3) {
                Log.w(TAG, "Download failed – will retry")
                Result.retry()
            } else {
                Log.e(TAG, "Download failed after $runAttemptCount attempts")
                Result.failure()
            }
        }
    }

    private fun autoSelectMode(): QualityMode {
        val am = applicationContext.getSystemService(Context.ACTIVITY_SERVICE)
                as android.app.ActivityManager
        val info = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val ramGb = (info.totalMem / (1024L * 1024L * 1024L)).toInt()
        return when {
            ramGb >= 8 -> QualityMode.PRO
            ramGb >= 6 -> QualityMode.BALANCED
            else -> QualityMode.LITE
        }
    }

    companion object {
        /**
         * Schedule a one-time model download.
         * Safe to call repeatedly – uses [ExistingWorkPolicy.KEEP].
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresStorageNotLow(true)
                .build()

            val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    30, TimeUnit.SECONDS
                )
                .addTag("bitnet_download")
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP, request)

            Log.i(TAG, "Model download work enqueued")
        }
    }
}
