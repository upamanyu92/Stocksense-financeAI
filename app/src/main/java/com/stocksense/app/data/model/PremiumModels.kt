package com.stocksense.app.data.model

/**
 * Data models that support the premium StockSense dashboard experience.
 */
data class PortfolioSnapshot(
    val totalValue: Double,
    val dailyPnl: Double,
    val dailyPercent: Double,
    val level: String,
    val streakDays: Int
)

data class AiPredictionCard(
    val symbol: String,
    val name: String,
    val movement: Movement,
    val confidence: Int,
    val rationale: String
)

enum class Movement { UP, DOWN, NEUTRAL }

data class SentimentSummary(
    val score: Int, // 0-100
    val stance: SentimentStance,
    val headline: String,
    val detail: String
)

enum class SentimentStance { BULLISH, BEARISH, NEUTRAL }

data class DebateMessage(
    val speaker: DebateRole,
    val claim: String,
    val evidenceGrade: EvidenceGrade
)

enum class DebateRole { BULL, BEAR, SKEPTIC }

data class EvidenceGrade(
    val label: String,
    val sources: Int
)

data class IndicatorTranslation(
    val key: String,
    val plainEnglish: String,
    val whyItMatters: String
)

data class KillCriteriaNotice(
    val title: String,
    val body: String,
    val trustSignal: String
)

data class WeightingRule(
    val source: String,
    val weight: Double
)
