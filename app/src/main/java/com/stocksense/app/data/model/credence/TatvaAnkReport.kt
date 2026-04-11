package com.stocksense.app.data.model.credence

import kotlinx.serialization.Serializable

/**
 * Risk classification produced by the Orchestrator Agent.
 * Mirrors the risk badge colours used in the CredenceAI Streamlit UI.
 */
@Serializable
enum class RiskLabel(val displayName: String, val emoji: String) {
    LOW("Low Risk", "🟢"),
    MEDIUM("Medium Risk", "🟡"),
    HIGH("High Risk", "🔴")
}

/**
 * Result payload returned by a single CredenceAI agent node.
 * Mirrors the node-level return dicts in the Credence Python graph.
 */
@Serializable
data class AgentResult(
    /** Agent name: "Ingestion", "Quant", "NLP Risk", "Orchestrator". */
    val agentName: String,
    /** Human-readable summary of what the agent found / computed. */
    val summary: String,
    /** Confidence in this agent's output (0.0–1.0). */
    val confidence: Double,
    /** True when the agent completed successfully without errors. */
    val passed: Boolean,
    /** Optional structured detail (serialised as string for flexibility). */
    val detail: String = "",
    /** Processing time in milliseconds for this agent. */
    val processingTimeMs: Long = 0L
)

/**
 * Tatva Ank Report — the canonical output of the CredenceAI multi-agent pipeline.
 *
 * Scoring formula (mirrors Credence AI orchestrator.py heuristic):
 *  - Baseline: 50
 *  - Altman Z-Score adjustment:  Z ≥ 3.0 → +20, Z ≥ 2.5 → +12, Z ≥ 1.81 → +5, Z ≥ 1.0 → -5, Z < 1.0 → -15
 *  - D/E ratio adjustment: D/E ≤ 0.5 → +5, D/E ≤ 1.0 → +2, D/E ≤ 2.0 → 0, D/E > 2.0 → -5, D/E > 3.0 → -10
 *  - Sentiment adjustment: sentimentScore ∈ [-1.0, +1.0] → × 10 (contribution: -10 to +10)
 *  - Final: clamped to [0, 100]
 *
 * Risk labels: LOW ≥ 70, MEDIUM 40–69, HIGH < 40.
 */
@Serializable
data class TatvaAnkReport(
    /** Echo of the original request. */
    val request: CreditAnalysisRequest,

    /** Final credit score in [0, 100] (Tatva Ank score). */
    val tatvaAnkScore: Double,

    /** Derived risk classification. */
    val riskLabel: RiskLabel,

    /** Altman Z-Score computed by the Quant Agent. */
    val altmanZScore: Double,

    /** Debt-to-Equity ratio computed by the Quant Agent (Double.MAX_VALUE if equity = 0). */
    val debtToEquity: Double,

    /** Sentiment score in [-1.0, +1.0] from the NLP Risk Agent. */
    val sentimentScore: Double,

    /** Result from the Ingestion Agent. */
    val ingestionResult: AgentResult,

    /** Result from the Quant Agent. */
    val quantResult: AgentResult,

    /** Result from the NLP Risk Agent. */
    val nlpResult: AgentResult,

    /** Result from the Orchestrator Agent. */
    val orchestratorResult: AgentResult,

    /** 22 evaluation taxonomy metrics. */
    val evaluationMetrics: List<EvaluationMetric>,

    /**
     * Human-in-the-loop feedback keyed by agent name.
     * Updated via [CredenceAIViewModel.provideFeedback].
     */
    val hitlFeedback: Map<String, String> = emptyMap(),

    /** Wall-clock time for the full pipeline in milliseconds. */
    val processingTimeMs: Long,

    /** Unix timestamp (ms) when the analysis was completed. */
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Convenience: pass count across all 22 metrics. */
    val evaluationPassCount: Int get() = evaluationMetrics.count { it.passed }

    /** Mean evaluation score across all 22 metrics (0.0–1.0). */
    val meanEvaluationScore: Double
        get() = if (evaluationMetrics.isEmpty()) 0.0
                else evaluationMetrics.sumOf { it.score } / evaluationMetrics.size
}

