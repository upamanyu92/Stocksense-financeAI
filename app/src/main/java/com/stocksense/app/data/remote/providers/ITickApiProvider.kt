package com.stocksense.app.data.remote.providers

import com.stocksense.app.data.remote.MarketDataPayload
import com.stocksense.app.data.remote.MarketDataRequest
import com.stocksense.app.data.remote.MarketDataRequirementType
import okhttp3.HttpUrl.Companion.toHttpUrl

class ITickApiProvider(
    private val apiKey: String,
    private val baseUrl: String = "https://api.itick.com/rest-api/stocks"
) : BaseMarketDataProvider() {

    override val providerId: String = "itick"

    override val isConfigured: Boolean
        get() = apiKey.isNotBlank()

    override fun supports(request: MarketDataRequest): Boolean =
        request.isAsianMarket && request.requirementType in setOf(
            MarketDataRequirementType.REALTIME_ASIA_QUOTE,
            MarketDataRequirementType.INTRADAY_HISTORY
        )

    override suspend fun fetch(request: MarketDataRequest): MarketDataPayload? {
        val stock = if (request.requirementType == MarketDataRequirementType.REALTIME_ASIA_QUOTE) {
            fetchQuote(request)
        } else {
            null
        }
        val history = if (request.requirementType == MarketDataRequirementType.INTRADAY_HISTORY) {
            fetchHistory(request)
        } else {
            emptyList()
        }
        if (stock == null && history.isEmpty()) return null
        return MarketDataPayload(stock = stock, history = history)
    }

    private suspend fun fetchQuote(request: MarketDataRequest) =
        getJson(
            "$baseUrl/stock-quote".toHttpUrl().newBuilder()
                .addQueryParameter("region", request.normalizedRegion)
                .addQueryParameter("code", request.normalizedSymbol)
                .addQueryParameter("api-key", apiKey)
        )?.let { root ->
            val payload = root.unwrapCommonPayload()
            buildStockData(
                symbol = request.normalizedSymbol,
                name = payload?.string("name", "companyName", "display_name") ?: request.displayName,
                price = payload?.double("price", "last", "latestPrice", "current"),
                previousClose = payload?.double("prevClose", "previousClose", "pc", "preClose"),
                changePercent = payload?.double("changePercent", "changeRatio", "dp"),
                marketCap = payload?.long("marketCap"),
                sector = payload?.string("sector", "industry")
            )
        }

    private suspend fun fetchHistory(request: MarketDataRequest) =
        getJson(
            "$baseUrl/stock-kline".toHttpUrl().newBuilder()
                .addQueryParameter("region", request.normalizedRegion)
                .addQueryParameter("code", request.normalizedSymbol)
                .addQueryParameter("kType", mapIntervalToKType(request.normalizedInterval))
                .addQueryParameter("limit", request.limit.toString())
                .addQueryParameter("api-key", apiKey)
        )?.let { root ->
            val rows = root.arrayAt("data").ifEmpty {
                root.arrayAt("response")
            }.ifEmpty {
                root.arrayAt("result")
            }
            rows.mapNotNull { row ->
                buildHistoryPoint(
                    timestamp = row.long("timestamp", "t", "time")
                        ?: row.string("date")?.toLongOrNull(),
                    close = row.double("close", "c", "price"),
                    volume = row.long("volume", "v", "turnoverVolume")
                )
            }.sortedBy { it.timestamp }
        } ?: emptyList()

    private fun mapIntervalToKType(interval: String): String = when (interval.lowercase()) {
        "1m" -> "1"
        "5m" -> "2"
        "15m" -> "3"
        "30m" -> "4"
        "1h" -> "5"
        "1d" -> "8"
        "1w" -> "9"
        else -> "8"
    }
}
