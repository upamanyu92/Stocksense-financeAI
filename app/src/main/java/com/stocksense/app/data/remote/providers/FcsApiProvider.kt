package com.stocksense.app.data.remote.providers

import com.stocksense.app.data.remote.MarketDataPayload
import com.stocksense.app.data.remote.MarketDataRequest
import com.stocksense.app.data.remote.MarketDataRequirementType
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class FcsApiProvider(
    private val apiKey: String,
    private val baseUrl: String = "https://fcsapi.com/api-v3/stock"
) : BaseMarketDataProvider() {

    override val providerId: String = "fcs"

    override val isConfigured: Boolean
        get() = apiKey.isNotBlank()

    override fun supports(request: MarketDataRequest): Boolean =
        request.requirementType in setOf(
            MarketDataRequirementType.DELAYED_GLOBAL_QUOTE,
            MarketDataRequirementType.MARKET_METADATA,
            MarketDataRequirementType.INTRADAY_HISTORY,
            MarketDataRequirementType.DAILY_HISTORY
        )

    override suspend fun fetch(request: MarketDataRequest): MarketDataPayload? {
        val stock = when (request.requirementType) {
            MarketDataRequirementType.DELAYED_GLOBAL_QUOTE,
            MarketDataRequirementType.MARKET_METADATA -> fetchQuote(request)
            else -> null
        }
        val history = if (
            request.requirementType == MarketDataRequirementType.DAILY_HISTORY ||
            request.requirementType == MarketDataRequirementType.INTRADAY_HISTORY
        ) {
            fetchHistory(request)
        } else {
            emptyList()
        }
        if (stock == null && history.isEmpty()) return null
        return MarketDataPayload(stock = stock, history = history)
    }

    private suspend fun fetchQuote(request: MarketDataRequest) =
        getJson(
            "$baseUrl/latest".toHttpUrl().newBuilder()
                .addQueryParameter("symbol", request.normalizedSymbol)
                .addQueryParameter("exchange", request.normalizedExchange)
                .addQueryParameter("access_key", apiKey)
        )?.let { root ->
            val payload = root.unwrapCommonPayload() ?: root.firstObject("response", "0")
            buildStockData(
                symbol = request.normalizedSymbol,
                name = payload?.string("name", "company_name", "short_name") ?: request.displayName,
                price = payload?.double("price", "close", "c", "last"),
                previousClose = payload?.double("previous_close", "prev_close", "open", "pc"),
                changePercent = payload?.double("change_percent", "change_pct", "dp"),
                marketCap = payload?.long("market_cap", "marketCap"),
                sector = payload?.string("sector", "industry")
            )
        }

    private suspend fun fetchHistory(request: MarketDataRequest) =
        getJson(
            "$baseUrl/history".toHttpUrl().newBuilder()
                .addQueryParameter("symbol", request.normalizedSymbol)
                .addQueryParameter("exchange", request.normalizedExchange)
                .addQueryParameter("period", request.normalizedInterval)
                .addQueryParameter("access_key", apiKey)
                .apply {
                    request.startTimeMillis?.let {
                        addQueryParameter("date_from", dateFormatter.format(Instant.ofEpochMilli(it)))
                    }
                    request.endTimeMillis?.let {
                        addQueryParameter("date_to", dateFormatter.format(Instant.ofEpochMilli(it)))
                    }
                }
        )?.let { root ->
            val rows = root.arrayAt("response").ifEmpty {
                root.arrayAt("data")
            }.ifEmpty {
                root.arrayAt("result")
            }.ifEmpty {
                root.arrayAt("history")
            }
            rows.mapNotNull { row ->
                buildHistoryPoint(
                    timestamp = row.long("timestamp", "t")
                        ?: row.string("date")?.let { runCatching { Instant.parse("${it}T00:00:00Z").toEpochMilli() }.getOrNull() },
                    close = row.double("close", "c", "price"),
                    volume = row.long("volume", "v")
                )
            }.sortedBy { it.timestamp }.takeLast(request.limit)
        } ?: emptyList()

    private companion object {
        val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneOffset.UTC)
    }
}
