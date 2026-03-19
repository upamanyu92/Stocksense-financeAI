package com.stocksense.app.engine

import android.util.Log
import com.stocksense.app.data.database.dao.LearningDataDao
import com.stocksense.app.data.database.dao.PredictionDao
import com.stocksense.app.data.database.entities.LearningData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.exp

private const val TAG = "LearningEngine"

/**
 * LearningEngine – tracks prediction accuracy and adjusts per-symbol adaptive weights.
 *
 * Flow:
 *   Prediction made → stored in DB
 *   Next day actual price available → [resolvePredictions] called
 *   Error computed → [LearningData.adaptiveWeight] updated (EMA)
 *   Future predictions use the weight to bias model output
 */
class LearningEngine(
    private val predictionDao: PredictionDao,
    private val learningDataDao: LearningDataDao
) {

    /**
     * Resolve all open predictions whose horizon has passed by comparing
     * with [actualPrices] (symbol → actual close price).
     * Updates prediction records and adjusts adaptive weights.
     */
    suspend fun resolvePredictions(actualPrices: Map<String, Double>) =
        withContext(Dispatchers.IO) {
            val unresolved = predictionDao.getUnresolvedPredictions()
            val now = System.currentTimeMillis()

            for (pred in unresolved) {
                val actual = actualPrices[pred.symbol] ?: continue
                val horizonMs = pred.horizon * 24 * 60 * 60 * 1000L
                if (now - pred.createdAt < horizonMs) continue   // too soon

                val errorPct = abs(actual - pred.predictedPrice) / actual * 100.0
                predictionDao.resolvePrediction(pred.id, actual, errorPct, now)

                updateLearningData(pred.symbol, pred.predictedPrice, actual, errorPct)
                Log.d(TAG, "${pred.symbol} resolved: predicted=${pred.predictedPrice} actual=$actual err=${String.format("%.2f", errorPct)}%")
            }
        }

    /**
     * Get the adaptive weight for [symbol].
     * Returns 1.0 (no adjustment) if no learning data exists yet.
     */
    suspend fun getAdaptiveWeight(symbol: String): Double =
        withContext(Dispatchers.IO) {
            learningDataDao.getLearningData(symbol)?.adaptiveWeight ?: 1.0
        }

    /**
     * Apply the adaptive weight to a raw predicted price.
     * The adjustment moves the prediction slightly toward the historical trend.
     */
    suspend fun applyAdaptiveWeight(symbol: String, rawPrediction: Double, lastActual: Double): Double {
        val weight = getAdaptiveWeight(symbol)
        // Weighted blend: higher weight → trust model more; lower weight → regress to last actual
        return rawPrediction * weight + lastActual * (1.0 - weight)
    }

    // ---------- Private ----------

    private suspend fun updateLearningData(
        symbol: String,
        predicted: Double,
        actual: Double,
        errorPct: Double
    ) {
        val existing = learningDataDao.getLearningData(symbol)
        val count = (existing?.predictionCount ?: 0) + 1
        // Track correct predictions (within 1% error as "correct")
        val isCorrect = errorPct < 1.0
        val correct = (existing?.correctDirectionCount ?: 0) + if (isCorrect) 1 else 0

        // Exponential moving average of error
        val alpha = 0.2  // smoothing factor
        val prevAvgError = existing?.avgError ?: errorPct
        val newAvgError = alpha * errorPct + (1 - alpha) * prevAvgError

        // Adaptive weight: sigmoid-based mapping from error to weight
        // Low error → weight near 1.2 (trust model); high error → weight near 0.6 (regress)
        val newWeight = sigmoidWeight(newAvgError)

        val updated = LearningData(
            id = existing?.id ?: 0,
            symbol = symbol,
            adaptiveWeight = newWeight,
            avgError = newAvgError,
            predictionCount = count,
            correctDirectionCount = correct,
            lastUpdated = System.currentTimeMillis()
        )
        learningDataDao.insertOrUpdate(updated)
    }

    /**
     * Maps average error percentage to an adaptive weight in [0.6, 1.2].
     * Error=0% → 1.2, Error=10% → ~1.0, Error=20%+ → ~0.6
     */
    private fun sigmoidWeight(avgErrorPct: Double): Double {
        val x = avgErrorPct / 10.0   // normalise: 10% error → x=1
        return 0.6 + 0.6 / (1 + exp(x - 1))
    }
}
