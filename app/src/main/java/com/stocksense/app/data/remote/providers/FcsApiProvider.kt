package com.stocksense.app.data.remote.providers

import com.stocksense.app.data.remote.MarketDataPayload
import com.stocksense.app.data.remote.MarketDataRequest
import com.stocksense.app.data.remote.MarketDataRequirementType
import okhttp3.HttpUrl.Companion.toHttpUrl

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
            MarketDataRequirementType.MARKET_METADATA
        )

    override suspend fun fetch(request: MarketDataRequest): MarketDataPayload? {
        val stock = when (request.requirementType) {
            MarketDataRequirementType.DELAYED_GLOBAL_QUOTE,
            MarketDataRequirementType.MARKET_METADATA -> fetchQuote(request)
            else -> null
        }
        if (stock == null) return null
        return MarketDataPayload(stock = stock)
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

}
