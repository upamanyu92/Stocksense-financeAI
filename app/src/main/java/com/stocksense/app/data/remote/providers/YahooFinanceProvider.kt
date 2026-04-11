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
    private val userAgent: String = "Mozilla/5.0 (Android) QuantSense/1.0"
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
        // Yahoo v7 quote and v10 quoteSummary endpoints require auth (401).
        // All data is fetched from the v8 chart API which still works unauthenticated.
        val chartResult = fetchChartResult(request)
        val stock = chartResult?.let { extractStockFromChart(it, request) }
        val history = if (request.requirementType == MarketDataRequirementType.DAILY_HISTORY) {
            chartResult?.let { extractHistoryFromChart(it, request) } ?: emptyList()
        } else {
            emptyList()
        }
        if (stock == null && history.isEmpty()) return null
        return MarketDataPayload(stock = stock, history = history)
    }

    /**
     * Fetch the v8 chart JSON and return the first result object, or null.
     * Used by both quote and history extraction.
     *
     * For symbols without an exchange suffix:
     * 1. Try the bare symbol first.
     * 2. If it fails, retry with ".NS" (NSE India) as a fallback.
     * 3. If the bare symbol resolved to a USD market, also try ".NS" and
     *    prefer the INR result — this handles dual-listed stocks (e.g. INFY)
     *    that are stored as NSE symbols in the seed data.
     */
    private suspend fun fetchChartResult(request: MarketDataRequest): kotlinx.serialization.json.JsonObject? {
        val sym = request.symbol
        val hasSuffix = sym.contains('.')

        val bareResult = fetchChartJson(sym, request)

        if (hasSuffix) return bareResult

        if (bareResult == null) {
            // Bare symbol failed — try .NS
            return fetchChartJson("$sym.NS", request)
        }

        // Bare symbol succeeded — check if it returned USD for a potential Indian stock.
        // If so, try .NS and prefer the INR version.
        val bareCurrency = bareResult.objectAt("meta")?.string("currency")
        if (bareCurrency == "USD") {
            val nsResult = fetchChartJson("$sym.NS", request)
            val nsCurrency = nsResult?.objectAt("meta")?.string("currency")
            if (nsCurrency == "INR") {
                return nsResult  // prefer Indian market version
            }
        }

        return bareResult
    }

    private suspend fun fetchChartJson(
        yahooSymbol: String,
        request: MarketDataRequest
    ) = getJson(
        "$quoteBaseUrl/v8/finance/chart/$yahooSymbol".toHttpUrl().newBuilder()
            .addQueryParameter("interval", mapInterval(request.normalizedInterval))
            .addQueryParameter("range", mapRange(request)),
        headers = yahooHeaders()
    )?.firstObject("chart", "result", "0")

    /**
     * Extract stock quote data from the v8 chart meta field.
     * Fields available in meta: regularMarketPrice, chartPreviousClose,
     * regularMarketDayHigh/Low, regularMarketVolume, longName, shortName,
     * fiftyTwoWeekHigh/Low, currency.
     */
    private fun extractStockFromChart(
        result: kotlinx.serialization.json.JsonObject,
        request: MarketDataRequest
    ): com.stocksense.app.data.model.StockData? {
        val meta = result.objectAt("meta") ?: return null
        val price = meta.double("regularMarketPrice")
        val previousClose = meta.double("chartPreviousClose")
        val changePercent = if (price != null && previousClose != null && previousClose != 0.0) {
            ((price - previousClose) / previousClose) * 100.0
        } else {
            null
        }
        return buildStockData(
            symbol = request.symbol,   // always use the DB key, not Yahoo's suffixed symbol
            name = meta.string("longName", "shortName") ?: request.displayName,
            price = price,
            previousClose = previousClose,
            changePercent = changePercent,
            marketCap = null,   // not in chart meta; acceptable trade-off
            sector = ""
        )
    }

    /** Extract OHLCV history from v8 chart indicators. */
    private fun extractHistoryFromChart(
        result: kotlinx.serialization.json.JsonObject,
        request: MarketDataRequest
    ): List<com.stocksense.app.data.model.HistoryPoint> {
        val timestamps = result.objectOrArrayAt("timestamp")
        val quote = result.firstObject("indicators", "quote", "0") ?: return emptyList()

        val timestampValues = (timestamps as? JsonArray)
            ?.mapNotNull { it.jsonPrimitive.longOrNull }
            .orEmpty()
        val closes = quote.objectOrArrayAt("close") as? JsonArray
        val volumes = quote.objectOrArrayAt("volume") as? JsonArray

        return timestampValues.mapIndexedNotNull { index, timestamp ->
            val close = closes?.getOrNull(index)?.jsonPrimitive?.doubleOrNull
            val volume = volumes?.getOrNull(index)?.jsonPrimitive?.longOrNull
            buildHistoryPoint(
                timestamp = timestamp * 1000L,
                close = close,
                volume = volume
            )
        }.sortedBy { it.timestamp }.takeLast(request.limit)
    }

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
        // For quote/metadata requests use 1d so chartPreviousClose reflects
        // yesterday's close, not the close N months ago.
        request.requirementType != MarketDataRequirementType.DAILY_HISTORY -> "1d"
        request.startTimeMillis != null && request.endTimeMillis != null -> "max"
        request.limit <= 10 -> "1mo"
        request.limit <= 30 -> "3mo"
        request.limit <= 90 -> "6mo"
        request.limit <= 180 -> "1y"
        else -> "5y"
    }
}
