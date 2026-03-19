package com.stocksense.app.workers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.stocksense.app.StockSenseApp
import com.stocksense.app.data.database.entities.Prediction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val TAG = "PredictionWorker"
const val KEY_SYMBOL = "symbol"

/**
 * PredictionWorker – runs ML inference for a stock symbol in the background.
 * Triggered by the UI or on a recurring schedule.
 */
class PredictionWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as? StockSenseApp
            ?: return@withContext Result.failure()

        val symbol = inputData.getString(KEY_SYMBOL)
            ?: return@withContext Result.failure()

        try {
            val history = app.stockRepository.getRecentHistory(symbol, 60)
            if (history.isEmpty()) {
                Log.w(TAG, "No history for $symbol – skipping prediction")
                return@withContext Result.success()
            }

            app.modelManager.ensureLoaded()

            val result = app.predictionEngine.predict(symbol, history)

            // Persist the prediction
            val prediction = Prediction(
                symbol = symbol,
                predictedPrice = result.predictedPrice,
                confidence = result.confidence,
                direction = result.direction
            )
            app.predictionDao.insertPrediction(prediction)

            // Notify user if confidence is high
            if (result.confidence >= 0.7f) {
                app.alertManager.showPredictionNotification(
                    symbol, result.direction, result.confidence
                )
            }

            Log.i(TAG, "$symbol prediction: ${result.direction} @ ${result.predictedPrice}")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Prediction error for $symbol: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME_PREFIX = "PredictionWork_"

        fun schedule(context: Context, symbol: String) {
            val input = workDataOf(KEY_SYMBOL to symbol)

            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = OneTimeWorkRequestBuilder<PredictionWorker>()
                .setInputData(input)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
