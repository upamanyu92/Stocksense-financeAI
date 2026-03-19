package com.stocksense.app.workers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.stocksense.app.StockSenseApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val TAG = "LearningWorker"

/**
 * LearningWorker – runs daily to resolve pending predictions and update adaptive weights.
 */
class LearningWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as? StockSenseApp
            ?: return@withContext Result.failure()

        try {
            // Build map of symbol → current price (used as "actual" proxy)
            val stocks = app.stockRepository.observeAllStocks().first()
            val actualPrices = stocks.associate { it.symbol to it.currentPrice }

            app.learningEngine.resolvePredictions(actualPrices)

            // Prune old predictions (older than 90 days)
            val cutoff = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
            app.predictionDao.pruneOldPredictions(cutoff)

            Log.i(TAG, "Learning update complete for ${actualPrices.size} symbols")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Learning error: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "LearningWork"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<LearningWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.i(TAG, "LearningWorker scheduled")
        }
    }
}
