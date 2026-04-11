package com.stocksense.app.data.model.credence

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

/**
 * Quantitative financial inputs required for Altman Z-Score, D/E ratio,
 * and P&L forecasting inside the CredenceAI Quant Agent.
 *
 * All monetary values are in the same currency unit (e.g., INR crores or USD millions).
 * Zero or negative `totalAssets` / `shareholdersEquity` will be guarded in [QuantAgent].
 */
@OptIn(InternalSerializationApi::class)
@Serializable
data class FinancialProfile(
    /** Revenue / Net Sales for the most recent fiscal year (Altman X5 numerator). */
    val totalRevenue: Double = 0.0,

    /** Earnings Before Interest and Tax — EBIT (Altman X3 numerator). */
    val ebit: Double = 0.0,

    /** Total Assets — balance sheet (denominator for Altman X1–X3 and X5). */
    val totalAssets: Double = 0.0,

    /** Total Liabilities (Altman X4 denominator). */
    val totalLiabilities: Double = 0.0,

    /** Working Capital = Current Assets − Current Liabilities (Altman X1 numerator). */
    val workingCapital: Double = 0.0,

    /** Retained Earnings — cumulative undistributed profits (Altman X2 numerator). */
    val retainedEarnings: Double = 0.0,

    /** Market Value of Equity or Book Value if not listed (Altman X4 numerator). */
    val marketValueEquity: Double = 0.0,

    /** Total Financial Debt — long-term + short-term borrowings (for D/E ratio). */
    val totalDebt: Double = 0.0,

    /** Shareholders' Equity — book value (D/E denominator). */
    val shareholdersEquity: Double = 0.0,

    /** Previous year revenue for year-over-year growth computation. */
    val priorYearRevenue: Double = 0.0,

    /** Previous year EBIT for trend analysis. */
    val priorYearEbit: Double = 0.0
) {
    /** Whether the profile has sufficient data for Altman Z-Score computation. */
    val isQuantifiable: Boolean
        get() = totalAssets > 0.0 && totalRevenue > 0.0

    /** Revenue YoY growth rate (0.0 if prior year is zero). */
    val revenueGrowthRate: Double
        get() = if (priorYearRevenue > 0.0) (totalRevenue - priorYearRevenue) / priorYearRevenue else 0.0

    /** EBIT margin as a fraction of revenue (0.0 if revenue is zero). */
    val ebitMargin: Double
        get() = if (totalRevenue > 0.0) ebit / totalRevenue else 0.0
}
