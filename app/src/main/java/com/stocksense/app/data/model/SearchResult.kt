package com.stocksense.app.data.model

/**
 * Represents a single search result from the unified search system.
 */
data class SearchResult(
    val displayName: String,
    val symbol: String,
    val code: String,
    val type: SearchResultType,
    val matchSource: String
)

enum class SearchResultType(val label: String) {
    COMPANY("Company"),
    STOCK_SYMBOL("Stock"),
    ETF("ETF"),
    INDEX("Index"),
    OTHER("Other")
}
