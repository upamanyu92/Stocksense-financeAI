package com.stocksense.app.data.remote.providers

import com.stocksense.app.data.remote.MarketDataPayload
import com.stocksense.app.data.remote.MarketDataRequest
import com.stocksense.app.data.remote.MarketDataRequirementType
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.Instant

class AlpacaApiProvider(
    private val apiKey: String,
    private val apiSecret: String,
    private val baseUrl: String = "https://data.alpaca.markets/v2/stocks"
) : BaseMarketDataProvider() {

    override val providerId: String = "alpaca"

    override val isConfigured: Boolean
        get() = apiKey.isNotBlank() && apiSecret.isNotBlank()

    override fun supports(request: MarketDataRequest): Boolean =
        request.isUsMarket && request.requirementType in setOf(
            MarketDataRequirementType.TRADING_GRADE_QUOTE,
            MarketDataRequirementType.INTRADAY_HISTORY
        )

    override suspend fun fetch(request: MarketDataRequest): MarketDataPayload? {
        val stock = if (request.requirementType == MarketDataRequirementType.TRADING_GRADE_QUOTE) {
            fetchQuote(request)
        } else {
            null
        }
        val history = if (request.requirementType == MarketDataRequirementType.INTRADAY_HISTORY) {
            fetchBars(request)
        } else {
            emptyList()
        }
        if (stock == null && history.isEmpty()) return null
        return MarketDataPayload(stock = stock, history = history)
    }

    private suspend fun fetchQuote(request: MarketDataRequest) =
        getJson(
            "$baseUrl/${request.normalizedSymbol}/quotes/latest".toHttpUrl().newBuilder(),
            headers = authHeaders()
        )?.let { root ->
            val quote = root.objectAt("quote") ?: root.unwrapCommonPayload()
            val askPrice = quote?.double("ap", "ask_price")
            val bidPrice = quote?.double("bp", "bid_price")
            val midPrice = listOfNotNull(askPrice, bidPrice).takeIf { it.isNotEmpty() }?.average()
            buildStockData(
                symbol = request.normalizedSymbol,
                name = request.displayName,
                price = midPrice,
                previousClose = bidPrice ?: askPrice ?: midPrice,
                changePercent = 0.0
            )
        }

    private suspend fun fetchBars(request: MarketDataRequest) =
        getJson(
            "$baseUrl/${request.normalizedSymbol}/bars".toHttpUrl().newBuilder()
                .addQueryParameter("timeframe", mapTimeframe(request.normalizedInterval))
                .addQueryParameter("limit", request.limit.toString())
                .apply {
                    request.startTimeMillis?.let {
                        addQueryParameter("start", Instant.ofEpochMilli(it).toString())
                    }
                    request.endTimeMillis?.let {
                        addQueryParameter("end", Instant.ofEpochMilli(it).toString())
                    }
                },
            headers = authHeaders()
        )?.let { root ->
            val bars = root.arrayAt("bars").ifEmpty {
                root.arrayAt("results")
            }
            bars.mapNotNull { bar ->
                buildHistoryPoint(
                    timestamp = bar.string("t", "timestamp")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() },
                    close = bar.double("c", "close"),
                    volume = bar.long("v", "volume")
                )
            }.sortedBy { it.timestamp }
        } ?: emptyList()

    private fun authHeaders(): Map<String, String> = mapOf(
        "APCA-API-KEY-ID" to apiKey,
        "APCA-API-SECRET-KEY" to apiSecret
    )

    private fun mapTimeframe(interval: String): String = when (interval.lowercase()) {
        "1m" -> "1Min"
        "5m" -> "5Min"
        "15m" -> "15Min"
        "1h" -> "1Hour"
        "1d" -> "1Day"
        else -> "1Min"
    }
}
