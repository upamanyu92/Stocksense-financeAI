package com.stocksense.app.data.remote

import com.stocksense.app.data.model.HistoryPoint
import com.stocksense.app.data.model.StockData

enum class MarketDataRequirementType {
    DELAYED_GLOBAL_QUOTE,
    MARKET_METADATA,
    FUNDAMENTAL_ANALYSIS,
    REALTIME_ASIA_QUOTE,
    INTRADAY_HISTORY,
    DAILY_HISTORY
}

data class MarketDataRequest(
    val symbol: String,
    val requirementType: MarketDataRequirementType,
    val displayName: String? = null,
    val exchange: String? = null,
    val region: String? = null,
    val interval: String? = null,
    val limit: Int = 60,
    val startTimeMillis: Long? = null,
    val endTimeMillis: Long? = null
) {
    val normalizedSymbol: String
        get() = symbol.substringBefore('.').uppercase()

    val normalizedExchange: String
        get() = exchange?.takeIf { it.isNotBlank() }?.uppercase()
            ?: when {
                symbol.endsWith(".NS", ignoreCase = true) -> "NSE"
                symbol.endsWith(".BO", ignoreCase = true) -> "BSE"
                else -> "NASDAQ"
            }

    val normalizedRegion: String
        get() = region?.takeIf { it.isNotBlank() }?.uppercase()
            ?: when (normalizedExchange) {
                "NSE", "BSE" -> "IN"
                "HKEX" -> "HK"
                "TSE" -> "JP"
                else -> "US"
            }

    val normalizedInterval: String
        get() = interval?.takeIf { it.isNotBlank() } ?: when (requirementType) {
            MarketDataRequirementType.INTRADAY_HISTORY -> "1m"
            MarketDataRequirementType.DAILY_HISTORY -> "1d"
            else -> "1d"
        }

    val isAsianMarket: Boolean
        get() = normalizedRegion in setOf("IN", "HK", "JP", "CN", "SG", "TW")

    val isUsMarket: Boolean
        get() = normalizedRegion == "US"
}

data class MarketDataPayload(
    val stock: StockData? = null,
    val history: List<HistoryPoint> = emptyList()
)

interface MarketDataProvider {
    val providerId: String
    val isConfigured: Boolean

    fun supports(request: MarketDataRequest): Boolean

    suspend fun fetch(request: MarketDataRequest): MarketDataPayload?
}
