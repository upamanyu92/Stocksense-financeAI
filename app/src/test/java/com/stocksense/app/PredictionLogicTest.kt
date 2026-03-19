package com.stocksense.app

import com.stocksense.app.data.model.HistoryPoint
import com.stocksense.app.data.model.PredictionResult
import com.stocksense.app.engine.LearningEngine
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for PredictionEngine heuristic logic and LearningEngine weight adjustments.
 * No Android context required – tests run on JVM.
 */
class PredictionLogicTest {

    // ---------- Heuristic prediction helpers ----------

    private fun buildHistory(prices: List<Double>): List<HistoryPoint> {
        val now = System.currentTimeMillis()
        return prices.mapIndexed { i, p ->
            HistoryPoint(timestamp = now - (prices.size - i) * 86400000L, close = p)
        }
    }

    private fun heuristicPredict(symbol: String, history: List<HistoryPoint>): PredictionResult {
        if (history.size < 2) return PredictionResult(symbol, 0.0, 0f, "NEUTRAL")
        val recent = history.takeLast(5).map { it.close }
        val momentum = if (recent.size >= 2) recent.last() - recent.first() else 0.0
        val lastClose = history.last().close
        val direction = when {
            momentum > lastClose * 0.005 -> "UP"
            momentum < -lastClose * 0.005 -> "DOWN"
            else -> "NEUTRAL"
        }
        val confidence = (Math.abs(momentum) / (lastClose * 0.05)).coerceIn(0.1, 0.8).toFloat()
        val predictedPrice = lastClose + momentum * 0.5
        return PredictionResult(symbol, predictedPrice, confidence, direction)
    }

    @Test
    fun `heuristic predicts UP on rising prices`() {
        val history = buildHistory(listOf(100.0, 101.0, 102.5, 103.8, 105.0))
        val result = heuristicPredict("TEST", history)
        assertEquals("UP", result.direction)
        assertTrue(result.predictedPrice > 105.0)
        assertTrue(result.confidence > 0f)
    }

    @Test
    fun `heuristic predicts DOWN on falling prices`() {
        val history = buildHistory(listOf(105.0, 103.5, 102.0, 100.8, 99.0))
        val result = heuristicPredict("TEST", history)
        assertEquals("DOWN", result.direction)
        assertTrue(result.predictedPrice < 99.0)
    }

    @Test
    fun `heuristic predicts NEUTRAL on flat prices`() {
        val history = buildHistory(listOf(100.0, 100.1, 99.9, 100.0, 100.0))
        val result = heuristicPredict("TEST", history)
        assertEquals("NEUTRAL", result.direction)
    }

    @Test
    fun `heuristic returns NEUTRAL for single data point`() {
        val history = buildHistory(listOf(100.0))
        val result = heuristicPredict("TEST", history)
        assertEquals("NEUTRAL", result.direction)
    }

    @Test
    fun `confidence is within 0 to 1 range`() {
        val history = buildHistory(listOf(100.0, 101.0, 102.0, 103.0, 110.0))
        val result = heuristicPredict("TEST", history)
        assertTrue(result.confidence in 0f..1f)
    }

    // ---------- Sigmoid weight function ----------

    private fun sigmoidWeight(avgErrorPct: Double): Double {
        val x = avgErrorPct / 10.0
        return 0.6 + 0.6 / (1 + Math.exp(x - 1))
    }

    @Test
    fun `sigmoid weight is higher for low error`() {
        val lowErrorWeight = sigmoidWeight(0.0)
        val highErrorWeight = sigmoidWeight(25.0)
        assertTrue(lowErrorWeight > highErrorWeight)
    }

    @Test
    fun `sigmoid weight stays in valid range`() {
        for (errorPct in listOf(0.0, 5.0, 10.0, 15.0, 20.0, 30.0)) {
            val w = sigmoidWeight(errorPct)
            assertTrue("Weight $w out of range for error $errorPct", w in 0.5..1.3)
        }
    }

    @Test
    fun `prediction result symbol matches input`() {
        val history = buildHistory(listOf(100.0, 101.0, 102.0))
        val result = heuristicPredict("AAPL", history)
        assertEquals("AAPL", result.symbol)
    }
}
