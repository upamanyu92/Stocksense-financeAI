package com.stocksense.app.data.model

import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable

/** Domain-level representation of a stock used by the UI layer. */
@OptIn(InternalSerializationApi::class)
@Serializable
data class StockData(
    val symbol: String,
    val name: String,
    val currentPrice: Double,
    val previousClose: Double,
    val changePercent: Double,
    val marketCap: Long = 0L,
    val sector: String = "",
    val history: List<HistoryPoint> = emptyList()
)

@Serializable
data class HistoryPoint(
    val timestamp: Long,
    val close: Double,
    val volume: Long = 0L
)
