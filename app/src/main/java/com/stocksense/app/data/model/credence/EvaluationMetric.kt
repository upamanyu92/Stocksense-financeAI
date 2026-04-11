package com.stocksense.app.data.model.credence

import kotlinx.serialization.Serializable

/**
 * One of the 22 evaluation taxonomy metrics defined by the CredenceAI evaluation framework.
 *
 * Taxonomy has 6 categories covering 22 metrics total:
 *  - Factual Accuracy  (4)
 *  - Relevance         (3)
 *  - Safety & Compliance (4)
 *  - System Performance  (4)
 *  - Reasoning Quality   (4)
 *  - UX & Delivery       (3)
 */
@Serializable
data class EvaluationMetric(
    /** Stable integer ID (1–22); do not reorder once deployed. */
    val id: Int,

    /** High-level category this metric belongs to. */
    val category: EvaluationCategory,

    /** Human-readable metric name (matches Credence AI taxonomy). */
    val name: String,

    /** Short explanation of what is being measured. */
    val description: String,

    /** Normalised score in [0.0, 1.0]; higher is better. */
    val score: Double,

    /** Whether the metric passes its threshold. */
    val passed: Boolean,

    /** Contextual detail explaining the score (e.g., "Hallucination rate 3 % — below 10 % threshold"). */
    val detail: String = ""
)

/** The 6 top-level evaluation categories from the Credence AI taxonomy. */
@Serializable
enum class EvaluationCategory(val displayName: String) {
    FACTUAL_ACCURACY("Factual Accuracy"),
    RELEVANCE("Relevance"),
    SAFETY_COMPLIANCE("Safety & Compliance"),
    SYSTEM_PERFORMANCE("System Performance"),
    REASONING_QUALITY("Reasoning Quality"),
    UX_DELIVERY("UX & Delivery")
}

