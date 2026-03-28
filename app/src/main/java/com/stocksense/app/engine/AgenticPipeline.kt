package com.stocksense.app.engine

import android.util.Log
import com.stocksense.app.data.model.HistoryPoint
import com.stocksense.app.data.model.PredictionResult
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

private const val TAG = "AgenticPipeline"

// ── Data Classes ────────────────────────────────────────────────────────────

data class EnrichedData(
    val symbol: String,
    val features: Map<String, Double>,
    val dataQualityScore: Double,
    val marketRegime: String
)

data class AdaptiveResult(
    val regime: String,
    val modelPreference: String,
    val confidenceAdjustment: Double,
    val weights: Map<String, Double>
)

enum class ServingAction { PROCEED, PROCEED_WITH_CAUTION, SHADOW_ONLY, BLOCK_PREDICTION }

data class AgenticPrediction(
    val symbol: String,
    val prediction: Double,
    val confidence: Double,
    val baseConfidence: Double,
    val confidenceAdjustment: Double,
    val predictionInterval: Pair<Double, Double>,
    val uncertainty: Double,
    val dataQuality: Double,
    val marketRegime: String,
    val adaptiveWeights: Map<String, Double>,
    val decision: String,
    val servingAction: ServingAction,
    val agentsUsed: List<String>,
    val recommendation: String,
    val processingTimeMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)

// ── Pipeline ────────────────────────────────────────────────────────────────

/**
 * Five-agent agentic prediction pipeline (Technical Report §2.4 / §3.5).
 *
 * Agents run in sequence:
 *  1. DataEnrichmentAgent   – features + data quality
 *  2. AdaptiveLearningAgent – regime detection + strategy mapping
 *  3. EnsembleAgent         – parallel model inference + combination
 *  4. PredictionEvaluatorAgent – pre-serving quality gate
 *  5. OutcomeEvaluatorAgent – post-hoc accuracy evaluation (called separately)
 */
class AgenticPipeline(
    private val predictionEngine: PredictionEngine,
    private val llmEngine: LLMInsightEngine,
    private val learningEngine: LearningEngine
) {

    companion object {
        // Trust-score weights
        private const val TRUST_W_CONFIDENCE = 0.50
        private const val TRUST_W_QUALITY = 0.30
        private const val TRUST_W_UNCERTAINTY = 0.20

        // Trust-score thresholds
        private const val TRUST_ACCEPT = 0.75
        private const val TRUST_CAUTION = 0.60

        // Prediction-interval z-score (95 %)
        private const val Z_95 = 1.96

        // Minimum confidence floor to avoid divide-by-zero
        private const val MIN_CONF = 1e-6

        // Regime → strategy mapping
        private val REGIME_STRATEGY = mapOf(
            "bull"     to RegimeStrategy("transformer", 0.10, mapOf("transformer" to 0.65, "lstm" to 0.35)),
            "bear"     to RegimeStrategy("lstm",        0.05, mapOf("transformer" to 0.35, "lstm" to 0.65)),
            "sideways" to RegimeStrategy("ensemble",    0.00, mapOf("transformer" to 0.50, "lstm" to 0.50)),
            "volatile" to RegimeStrategy("ensemble",   -0.10, mapOf("transformer" to 0.50, "lstm" to 0.50))
        )
        private val DEFAULT_STRATEGY = RegimeStrategy("ensemble", 0.0, mapOf("transformer" to 0.50, "lstm" to 0.50))

        // History window sizes for the two ensemble "perspectives"
        private const val TECHNICAL_WINDOW = 30
        private const val FUNDAMENTAL_WINDOW = 60

        // Gaussian noise factor when ensemble predictions are identical (0.5% of price)
        private const val ENSEMBLE_NOISE_FACTOR = 0.005
    }

    /** Internal helper mapping a market regime to model preferences. */
    private data class RegimeStrategy(
        val modelPreference: String,
        val confidenceBoost: Double,
        val weights: Map<String, Double>
    )

    // ────────────────────────────────────────────────────────────────────────
    // Public API
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Run the full five-agent pipeline and return an [AgenticPrediction].
     * The [OutcomeEvaluatorAgent] is *not* invoked here — call
     * [evaluateOutcome] separately once the actual price is known.
     */
    suspend fun predict(
        symbol: String,
        history: List<HistoryPoint>,
        close: DoubleArray,
        high: DoubleArray,
        low: DoubleArray,
        volume: DoubleArray
    ): AgenticPrediction {
        val startMs = System.currentTimeMillis()
        val agentsUsed = mutableListOf<String>()

        // ── Agent 1: Data Enrichment ────────────────────────────────────
        val enriched = runDataEnrichmentAgent(symbol, close, high, low, volume, history)
        agentsUsed += "DataEnrichmentAgent"
        Log.d(TAG, "[$symbol] Agent 1 done – regime=${enriched.marketRegime}, quality=${"%.3f".format(enriched.dataQualityScore)}")

        // ── Agent 2: Adaptive Learning ──────────────────────────────────
        val adaptive = runAdaptiveLearningAgent(symbol, enriched)
        agentsUsed += "AdaptiveLearningAgent"
        Log.d(TAG, "[$symbol] Agent 2 done – pref=${adaptive.modelPreference}, adj=${adaptive.confidenceAdjustment}")

        // ── Agent 3: Ensemble ───────────────────────────────────────────
        val ensemble = runEnsembleAgent(symbol, history, close)
        agentsUsed += "EnsembleAgent"
        Log.d(TAG, "[$symbol] Agent 3 done – pred=${"%.2f".format(ensemble.price)}, conf=${"%.3f".format(ensemble.confidence)}")

        // Apply adaptive confidence adjustment
        val baseConfidence = ensemble.confidence
        val adjustedConfidence = (baseConfidence + adaptive.confidenceAdjustment).coerceIn(0.0, 1.0)

        // ── Agent 4: Prediction Evaluator ───────────────────────────────
        val evaluation = runPredictionEvaluatorAgent(
            adjustedConfidence, enriched.dataQualityScore, ensemble.uncertainty
        )
        agentsUsed += "PredictionEvaluatorAgent"
        Log.d(TAG, "[$symbol] Agent 4 done – decision=${evaluation.decision}, serving=${evaluation.servingAction}")

        // Combine evaluator action with decision-derived action (take the stricter)
        val decisionAction = decisionToServingAction(evaluation.decision)
        val finalAction = stricterAction(decisionAction, evaluation.servingAction)

        val recommendation = buildRecommendation(
            symbol, ensemble.direction, adjustedConfidence, enriched.marketRegime, evaluation.decision
        )

        val processingTimeMs = System.currentTimeMillis() - startMs
        Log.i(TAG, "[$symbol] Pipeline complete in ${processingTimeMs}ms – action=$finalAction")

        return AgenticPrediction(
            symbol = symbol,
            prediction = ensemble.price,
            confidence = adjustedConfidence,
            baseConfidence = baseConfidence,
            confidenceAdjustment = adaptive.confidenceAdjustment,
            predictionInterval = ensemble.interval,
            uncertainty = ensemble.uncertainty,
            dataQuality = enriched.dataQualityScore,
            marketRegime = enriched.marketRegime,
            adaptiveWeights = adaptive.weights,
            decision = evaluation.decision,
            servingAction = finalAction,
            agentsUsed = agentsUsed,
            recommendation = recommendation,
            processingTimeMs = processingTimeMs
        )
    }

    /**
     * Agent 5 – Outcome Evaluator. Call after the actual price is known.
     * Returns the absolute percentage error.
     */
    suspend fun evaluateOutcome(
        symbol: String,
        predictedPrice: Double,
        actualPrice: Double
    ): Double {
        val errorPct = if (actualPrice != 0.0)
            abs(actualPrice - predictedPrice) / actualPrice * 100.0
        else 0.0

        // Delegate weight update to the LearningEngine
        learningEngine.resolvePredictions(mapOf(symbol to actualPrice))

        Log.i(TAG, "[$symbol] Agent 5 – outcome error=${"%.2f".format(errorPct)}%")
        return errorPct
    }

    // ────────────────────────────────────────────────────────────────────────
    // Agent Implementations (private)
    // ────────────────────────────────────────────────────────────────────────

    /** Agent 1 – computes features, data quality score, and market regime. */
    private fun runDataEnrichmentAgent(
        symbol: String,
        close: DoubleArray,
        high: DoubleArray,
        low: DoubleArray,
        volume: DoubleArray,
        history: List<HistoryPoint>
    ): EnrichedData {
        val features = FeatureEngineering.computeAllFeatures(close, high, low, volume)
        val lastTimestamp = if (history.isNotEmpty()) history.last().timestamp else System.currentTimeMillis()
        val qualityScore = FeatureEngineering.computeDataQualityScore(close, lastTimestamp)
        val regime = FeatureEngineering.detectMarketRegime(close, high, low)

        return EnrichedData(
            symbol = symbol,
            features = features,
            dataQualityScore = qualityScore,
            marketRegime = regime
        )
    }

    /** Agent 2 – maps market regime to strategy and fetches adaptive weights. */
    private suspend fun runAdaptiveLearningAgent(
        symbol: String,
        enriched: EnrichedData
    ): AdaptiveResult {
        val strategy = REGIME_STRATEGY[enriched.marketRegime] ?: DEFAULT_STRATEGY
        val adaptiveWeight = learningEngine.getAdaptiveWeight(symbol)

        // Scale the regime weights by the per-symbol adaptive weight
        val scaledWeights = strategy.weights.mapValues { (_, v) -> v * adaptiveWeight }

        return AdaptiveResult(
            regime = enriched.marketRegime,
            modelPreference = strategy.modelPreference,
            confidenceAdjustment = strategy.confidenceBoost,
            weights = scaledWeights
        )
    }

    /**
     * Agent 3 – runs two parallel predictions (short-window "technical" and
     * longer-window "fundamental") via [PredictionEngine], then combines them.
     */
    private suspend fun runEnsembleAgent(
        symbol: String,
        history: List<HistoryPoint>,
        close: DoubleArray
    ): EnsembleResult = coroutineScope {
        val technicalHistory = history.takeLast(TECHNICAL_WINDOW)
        val fundamentalHistory = history.takeLast(FUNDAMENTAL_WINDOW)

        val (techResult, fundResult) = listOf(
            async { predictionEngine.predict(symbol, technicalHistory) },
            async { predictionEngine.predict(symbol, fundamentalHistory) }
        ).awaitAll()

        combine(techResult, fundResult, close)
    }

    /** Combine two [PredictionResult]s into a single ensemble output. */
    private fun combine(a: PredictionResult, b: PredictionResult, close: DoubleArray): EnsembleResult {
        var predA = a.predictedPrice
        var predB = b.predictedPrice
        var confA = a.confidence.toDouble()
        var confB = b.confidence.toDouble()

        // When both predictions are identical add small Gaussian noise
        if (predA == predB) {
            val lastClose = if (close.isNotEmpty()) close.last() else predA
            val noise = lastClose * ENSEMBLE_NOISE_FACTOR
            predA += noise
            predB -= noise
            // Slightly differentiate confidence as well
            confA = (confA + 0.02).coerceAtMost(1.0)
            confB = (confB - 0.02).coerceAtLeast(0.0)
        }

        // Weighted average by confidence
        val totalConf = confA + confB
        val weightedPrice = if (totalConf > 0)
            (predA * confA + predB * confB) / totalConf
        else (predA + predB) / 2.0

        // Prediction interval: mean ± 1.96 × std
        val mean = (predA + predB) / 2.0
        val std = sqrt(((predA - mean) * (predA - mean) + (predB - mean) * (predB - mean)) / 2.0)
        val lower = mean - Z_95 * std
        val upper = mean + Z_95 * std

        // Ensemble confidence: mean_conf × max(0.5, 1 − std(conf) / mean_conf)
        val meanConf = (confA + confB) / 2.0
        val stdConf = sqrt(((confA - meanConf) * (confA - meanConf) + (confB - meanConf) * (confB - meanConf)) / 2.0)
        val safeConf = max(meanConf, MIN_CONF)
        val ensembleConfidence = meanConf * max(0.5, 1.0 - stdConf / safeConf)

        // Uncertainty as coefficient of variation of the two predictions
        val safeMean = max(abs(mean), MIN_CONF)
        val uncertainty = std / safeMean

        // Direction from the dominant prediction
        val direction = if (confA >= confB) a.direction else b.direction

        return EnsembleResult(
            price = weightedPrice,
            confidence = ensembleConfidence.coerceIn(0.0, 1.0),
            interval = Pair(lower, upper),
            uncertainty = uncertainty,
            direction = direction
        )
    }

    /** Agent 4 – quality gate that decides whether to serve the prediction. */
    private fun runPredictionEvaluatorAgent(
        confidence: Double,
        dataQuality: Double,
        uncertainty: Double
    ): EvaluationResult {
        // Trust score
        val uncertaintyComponent = 1.0 / (1.0 + uncertainty)
        val trustScore = confidence * TRUST_W_CONFIDENCE +
            dataQuality * TRUST_W_QUALITY +
            uncertaintyComponent * TRUST_W_UNCERTAINTY

        val decision = when {
            trustScore >= TRUST_ACCEPT  -> "accept"
            trustScore >= TRUST_CAUTION -> "caution"
            else                        -> "reject"
        }

        // Serving action based on raw confidence and quality
        val servingAction = when {
            confidence >= 0.8 && dataQuality >= 0.8 -> ServingAction.PROCEED
            confidence >= 0.6 && dataQuality >= 0.6 -> ServingAction.PROCEED_WITH_CAUTION
            confidence >= 0.4                        -> ServingAction.SHADOW_ONLY
            else                                     -> ServingAction.BLOCK_PREDICTION
        }

        return EvaluationResult(decision, servingAction, trustScore)
    }

    // ────────────────────────────────────────────────────────────────────────
    // Helpers
    // ────────────────────────────────────────────────────────────────────────

    /** Convert a decision string to its corresponding [ServingAction]. */
    private fun decisionToServingAction(decision: String): ServingAction = when (decision) {
        "accept"  -> ServingAction.PROCEED
        "caution" -> ServingAction.PROCEED_WITH_CAUTION
        else      -> ServingAction.SHADOW_ONLY
    }

    /**
     * Return the stricter (higher-ordinal) of two [ServingAction]s.
     * Ordinal order: PROCEED(0) < PROCEED_WITH_CAUTION(1) < SHADOW_ONLY(2) < BLOCK_PREDICTION(3).
     */
    private fun stricterAction(a: ServingAction, b: ServingAction): ServingAction =
        if (a.ordinal >= b.ordinal) a else b

    /** Build a human-readable recommendation sentence. */
    private fun buildRecommendation(
        symbol: String,
        direction: String,
        confidence: Double,
        regime: String,
        decision: String
    ): String {
        val confPct = "%.0f".format(confidence * 100)
        val dirText = when (direction) {
            "UP"   -> "upward movement"
            "DOWN" -> "downward pressure"
            else   -> "sideways consolidation"
        }
        return when (decision) {
            "accept" ->
                "$symbol shows $dirText with $confPct% confidence in a $regime market. Prediction accepted for serving."
            "caution" ->
                "$symbol signals $dirText ($confPct% confidence, $regime regime). Proceed with caution — consider additional confirmation."
            else ->
                "$symbol prediction ($dirText, $confPct% confidence) rejected due to low trust in $regime conditions. Shadow-mode only."
        }
    }

    // ── Internal result holders ─────────────────────────────────────────────

    private data class EnsembleResult(
        val price: Double,
        val confidence: Double,
        val interval: Pair<Double, Double>,
        val uncertainty: Double,
        val direction: String
    )

    private data class EvaluationResult(
        val decision: String,
        val servingAction: ServingAction,
        val trustScore: Double
    )
}
