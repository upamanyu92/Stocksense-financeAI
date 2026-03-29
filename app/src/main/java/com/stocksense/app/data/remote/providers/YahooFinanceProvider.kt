package com.stocksense.app.data.remote.providers

import com.stocksense.app.data.remote.MarketDataPayload
import com.stocksense.app.data.remote.MarketDataRequest
import com.stocksense.app.data.remote.MarketDataRequirementType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl

class YahooFinanceProvider(
    private val quoteBaseUrl: String = "https://query1.finance.yahoo.com",
    private val userAgent: String = "Mozilla/5.0 (Android) SenseQuant/1.0"
) : BaseMarketDataProvider() {

    override val providerId: String = "yahoo"

    override val isConfigured: Boolean
        get() = true

    override fun supports(request: MarketDataRequest): Boolean =
        request.requirementType in setOf(
            MarketDataRequirementType.QUOTE,
            MarketDataRequirementType.DAILY_HISTORY,
            MarketDataRequirementType.MARKET_METADATA,
            MarketDataRequirementType.FUNDAMENTAL_ANALYSIS
        )

    override suspend fun fetch(request: MarketDataRequest): MarketDataPayload? {
        val stock = when (request.requirementType) {
            MarketDataRequirementType.QUOTE -> fetchQuote(request)
            MarketDataRequirementType.MARKET_METADATA,
            MarketDataRequirementType.FUNDAMENTAL_ANALYSIS -> fetchSummary(request)
            else -> null
        }
        val history = if (request.requirementType == MarketDataRequirementType.DAILY_HISTORY) {
            fetchHistory(request)
        } else {
            emptyList()
        }
        if (stock == null && history.isEmpty()) return null
        return MarketDataPayload(stock = stock, history = history)
    }

    private suspend fun fetchQuote(request: MarketDataRequest) =
        getJson(
            "$quoteBaseUrl/v7/finance/quote".toHttpUrl().newBuilder()
                .addQueryParameter("symbols", request.symbol),
            headers = yahooHeaders()
        )?.let { root ->
            val quote = root.firstObject("quoteResponse", "result", "0") ?: return@let null
            buildStockData(
                symbol = quote.string("symbol") ?: request.symbol,
                name = quote.string("longName", "shortName") ?: request.displayName,
                price = quote.double("regularMarketPrice"),
                previousClose = quote.double("regularMarketPreviousClose"),
                changePercent = quote.double("regularMarketChangePercent"),
                marketCap = quote.long("marketCap"),
                sector = ""
            )
        }

    private suspend fun fetchSummary(request: MarketDataRequest) =
        getJson(
            "$quoteBaseUrl/v10/finance/quoteSummary/${request.symbol}".toHttpUrl().newBuilder()
                .addQueryParameter(
                    "modules",
                    "price,summaryProfile,financialData,defaultKeyStatistics"
                ),
            headers = yahooHeaders()
        )?.let { root ->
            val summary = root.firstObject("quoteSummary", "result", "0") ?: return@let null
            val price = summary.objectAt("price")
            val profile = summary.objectAt("summaryProfile")
            val financial = summary.objectAt("financialData")
            val stats = summary.objectAt("defaultKeyStatistics")

            buildStockData(
                symbol = request.symbol,
                name = price?.string("longName", "shortName") ?: request.displayName,
                price = price?.double("regularMarketPrice")
                    ?: price?.objectAt("regularMarketPrice")?.double("raw")
                    ?: financial?.double("currentPrice")
                    ?: financial?.objectAt("currentPrice")?.double("raw"),
                previousClose = price?.double("regularMarketPreviousClose")
                    ?: price?.objectAt("regularMarketPreviousClose")?.double("raw"),
                changePercent = price?.double("regularMarketChangePercent")
                    ?: price?.objectAt("regularMarketChangePercent")?.double("raw"),
                marketCap = price?.long("marketCap")
                    ?: stats?.long("marketCap")
                    ?: price?.objectAt("marketCap")?.long("raw")
                    ?: stats?.objectAt("marketCap")?.long("raw"),
                sector = profile?.string("sector", "industry")
            )
        }

    private suspend fun fetchHistory(request: MarketDataRequest) =
        getJson(
            "$quoteBaseUrl/v8/finance/chart/${request.symbol}".toHttpUrl().newBuilder()
                .addQueryParameter("interval", mapInterval(request.normalizedInterval))
                .addQueryParameter("range", mapRange(request))
                .apply {
                    request.startTimeMillis?.let {
                        addQueryParameter("period1", (it / 1000L).toString())
                    }
                    request.endTimeMillis?.let {
                        addQueryParameter("period2", (it / 1000L).toString())
                    }
                },
            headers = yahooHeaders()
        )?.let { root ->
            val result = root.firstObject("chart", "result", "0") ?: return@let emptyList()
            val timestamps = result.objectOrArrayAt("timestamp")
            val quote = result.firstObject("indicators", "quote", "0") ?: return@let emptyList()

            val timestampValues = (timestamps as? JsonArray)
                ?.mapNotNull { it.jsonPrimitive.longOrNull }
                .orEmpty()
            val closes = quote.objectOrArrayAt("close") as? JsonArray
            val volumes = quote.objectOrArrayAt("volume") as? JsonArray

            timestampValues.mapIndexedNotNull { index, timestamp ->
                val close = closes?.getOrNull(index)?.jsonPrimitive?.doubleOrNull
                val volume = volumes?.getOrNull(index)?.jsonPrimitive?.longOrNull
                buildHistoryPoint(
                    timestamp = timestamp * 1000L,
                    close = close,
                    volume = volume
                )
            }.sortedBy { it.timestamp }.takeLast(request.limit)
        } ?: emptyList()

    private fun yahooHeaders(): Map<String, String> = mapOf(
        "User-Agent" to userAgent,
        "Accept" to "application/json"
    )

    private fun mapInterval(interval: String): String = when (interval.lowercase()) {
        "1m", "2m", "5m", "15m", "30m", "60m", "90m", "1h", "1d", "1wk", "1mo" -> interval
            .replace("1h", "60m")
        "1w" -> "1wk"
        else -> "1d"
    }

    private fun mapRange(request: MarketDataRequest): String = when {
        request.startTimeMillis != null && request.endTimeMillis != null -> "max"
        request.limit <= 10 -> "1mo"
        request.limit <= 30 -> "3mo"
        request.limit <= 90 -> "6mo"
        request.limit <= 180 -> "1y"
        else -> "5y"
    }
}
