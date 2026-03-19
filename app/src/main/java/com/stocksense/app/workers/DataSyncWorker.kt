package com.stocksense.app.workers

import android.content.Context
import android.os.BatteryManager
import android.util.Log
import androidx.work.*
import com.stocksense.app.StockSenseApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val TAG = "DataSyncWorker"

/**
 * DataSyncWorker – periodic background task that fetches fresh stock prices
 * and seeds the database (if this is the first run).
 *
 * Scheduled via WorkManager with a 15-minute minimum interval.
 * Skipped automatically when battery is below 15%.
 */
class DataSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as? StockSenseApp
            ?: return@withContext Result.failure()

        try {
            if (isBatteryLow()) {
                Log.i(TAG, "Battery low – skipping sync")
                return@withContext Result.retry()
            }

            // Seed on first launch
            app.dataIngestion.seedIfEmpty()

            // Optional: fetch updates from network here when online
            // (currently offline-first, so only seeding is performed)

            // Evaluate alerts against current stock data
            val stocks = app.stockRepository.observeAllStocks().first()
            app.alertManager.evaluate(stocks)

            Log.i(TAG, "Sync complete")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Sync error: ${e.message}")
            Result.retry()
        }
    }

    private fun isBatteryLow(): Boolean {
        val bm = applicationContext.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return level < 15
    }

    companion object {
        const val WORK_NAME = "DataSyncWork"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)  // offline-first
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<DataSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.i(TAG, "DataSyncWorker scheduled")
        }
    }
}
