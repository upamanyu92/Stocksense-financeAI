package com.stocksense.app.data.remote.providers

import com.stocksense.app.data.model.HistoryPoint
import com.stocksense.app.data.model.StockData
import com.stocksense.app.data.remote.MarketDataProvider
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

internal abstract class BaseMarketDataProvider : MarketDataProvider {
    protected val json = Json { ignoreUnknownKeys = true }

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    protected suspend fun getJson(
        urlBuilder: HttpUrl.Builder,
        headers: Map<String, String> = emptyMap()
    ): JsonElement? {
        val requestBuilder = Request.Builder().url(urlBuilder.build())
        headers.forEach { (name, value) -> requestBuilder.header(name, value) }
        return client.newCall(requestBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string()?.trim().orEmpty()
            if (body.isBlank()) return null
            runCatching { json.parseToJsonElement(body) }.getOrNull()
        }
    }

    protected fun JsonElement.objectAt(vararg keys: String): JsonObject? {
        var current: JsonElement = this
        for (key in keys) {
            current = (current as? JsonObject)?.get(key) ?: return null
        }
        return current as? JsonObject
    }

    protected fun JsonElement.arrayAt(vararg keys: String): List<JsonObject> {
        val root = if (keys.isEmpty()) this else objectOrArrayAt(*keys) ?: return emptyList()
        return when (root) {
            is JsonArray -> root.mapNotNull { it as? JsonObject }
            is JsonObject -> listOf(root)
            else -> emptyList()
        }
    }

    protected fun JsonElement.objectOrArrayAt(vararg keys: String): JsonElement? {
        var current: JsonElement = this
        for (key in keys) {
            current = (current as? JsonObject)?.get(key) ?: return null
        }
        return current
    }

    protected fun JsonObject.string(vararg keys: String): String? =
        keys.firstNotNullOfOrNull { key ->
            (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        }

    protected fun JsonObject.double(vararg keys: String): Double? =
        keys.firstNotNullOfOrNull { key ->
            (this[key] as? JsonPrimitive)?.doubleOrNull
        }

    protected fun JsonObject.long(vararg keys: String): Long? =
        keys.firstNotNullOfOrNull { key ->
            (this[key] as? JsonPrimitive)?.longOrNull
        }

    protected fun JsonObject.firstObject(vararg keys: String): JsonObject? {
        val element = objectOrArrayAt(*keys) ?: return null
        return when (element) {
            is JsonObject -> element
            is JsonArray -> element.firstNotNullOfOrNull { it as? JsonObject }
            else -> null
        }
    }

    protected fun JsonElement.unwrapCommonPayload(): JsonObject? {
        val candidates = listOf(
            this as? JsonObject,
            firstObject("response"),
            firstObject("data"),
            firstObject("result"),
            firstObject("results"),
            firstObject("quote"),
            firstObject("bars")
        )
        return candidates.firstOrNull()
    }

    protected fun buildStockData(
        symbol: String,
        name: String?,
        price: Double?,
        previousClose: Double?,
        changePercent: Double?,
        marketCap: Long? = null,
        sector: String? = null
    ): StockData? {
        val currentPrice = price ?: return null
        val close = previousClose ?: currentPrice
        val percent = changePercent ?: if (close == 0.0) 0.0 else ((currentPrice - close) / close) * 100.0
        return StockData(
            symbol = symbol,
            name = name ?: symbol,
            currentPrice = currentPrice,
            previousClose = close,
            changePercent = percent,
            marketCap = marketCap ?: 0L,
            sector = sector.orEmpty()
        )
    }

    protected fun buildHistoryPoint(
        timestamp: Long?,
        close: Double?,
        volume: Long?
    ): HistoryPoint? {
        val ts = timestamp ?: return null
        val closeValue = close ?: return null
        return HistoryPoint(timestamp = ts, close = closeValue, volume = volume ?: 0L)
    }
}
