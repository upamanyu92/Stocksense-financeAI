package com.stocksense.app.data.model.credence

import kotlinx.serialization.Serializable

/**
 * Represents a credit-scoring request submitted to the CredenceAI multi-agent pipeline.
 *
 * The pipeline validates, enriches, and scores this request to produce a [TatvaAnkReport].
 */
@Serializable
data class CreditAnalysisRequest(
    /** Legal / trading name of the company being assessed. */
    val companyName: String = "",

    /** Primary industry classification (e.g., "Manufacturing", "NBFC", "Retail"). */
    val industry: String = "",

    /** Broad sector (e.g., "Financial Services", "Technology", "Healthcare"). */
    val sector: String = "",

    /**
     * Free-form description used by the NLP Risk Agent for sentiment analysis.
     * Can contain recent news headlines, management commentary, or legal notes.
     */
    val description: String = "",

    /** Quantitative financial inputs for the Quant Agent. */
    val financialProfile: FinancialProfile = FinancialProfile(),

    /** Any additional analyst notes passed as context to the Orchestrator. */
    val analystNotes: String = ""
) {
    /** True when the request carries at least a company name and non-zero assets. */
    val isValid: Boolean
        get() = companyName.isNotBlank() &&
                financialProfile.totalAssets > 0.0 &&
                financialProfile.totalRevenue > 0.0
}

