package com.stocksense.app.data.repository

import com.stocksense.app.data.database.dao.StockDao
import com.stocksense.app.data.database.dao.StockHistoryDao
import com.stocksense.app.data.database.entities.Stock
import com.stocksense.app.data.database.entities.StockHistory
import com.stocksense.app.data.model.HistoryPoint
import com.stocksense.app.data.model.StockData
import com.stocksense.app.data.remote.MarketDataRequest
import com.stocksense.app.data.remote.MarketDataRequirementType
import com.stocksense.app.data.remote.MarketDataRouter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * StockRepository – single source of truth for stock data.
 * Reads from local Room database; writes come from DataIngestion or periodic sync.
 */
class StockRepository(
    private val stockDao: StockDao,
    private val historyDao: StockHistoryDao,
    private val marketDataRouter: MarketDataRouter? = null
) {

    /** Observe all stocks as domain models. */
    fun observeAllStocks(): Flow<List<StockData>> =
        stockDao.getAllStocks().map { list ->
            list.map { it.toDomain() }
        }

    /** Search stocks by symbol or name. */
    fun searchStocks(query: String): Flow<List<StockData>> =
        stockDao.searchStocks(query).map { list ->
            list.map { it.toDomain() }
        }

    /** Observe history for a specific symbol as domain models. */
    fun observeHistory(symbol: String): Flow<List<HistoryPoint>> =
        historyDao.getHistory(symbol).map { list ->
            list.map { HistoryPoint(it.timestamp, it.close, it.volume) }
        }

    /** Get a single stock by symbol, or null. */
    suspend fun getStock(
        symbol: String,
        requirementType: MarketDataRequirementType = defaultQuoteRequirement()
    ): StockData? {
        val local = stockDao.getStock(symbol)
        if (shouldRefreshQuote(local?.lastUpdated, requirementType)) {
            refreshStock(symbol, requirementType, local?.name)
        }
        return stockDao.getStock(symbol)?.toDomain()
    }

    suspend fun getStocks(symbols: List<String>): List<StockData> =
        stockDao.getStocks(symbols).map { it.toDomain() }

    /** Fetch recent history (for ML feature extraction). */
    suspend fun getRecentHistory(
        symbol: String,
        limit: Int = 60,
        requirementType: MarketDataRequirementType = defaultHistoryRequirement()
    ): List<HistoryPoint> {
        var localHistory = historyDao.getRecentHistory(symbol, limit).map {
            HistoryPoint(it.timestamp, it.close, it.volume)
        }
        if (shouldRefreshHistory(localHistory)) {
            refreshHistory(symbol, requirementType, limit)
            localHistory = historyDao.getRecentHistory(symbol, limit).map {
                HistoryPoint(it.timestamp, it.close, it.volume)
            }
        }
        return localHistory
    }

    suspend fun refreshStock(
        symbol: String,
        requirementType: MarketDataRequirementType = defaultQuoteRequirement(),
        displayName: String? = null
    ): StockData? {
        val payload = marketDataRouter?.fetch(
            MarketDataRequest(
                symbol = symbol,
                requirementType = requirementType,
                displayName = displayName
            )
        ) ?: return null
        payload.stock?.let { saveStock(it) }
        if (payload.history.isNotEmpty()) {
            saveHistory(symbol, payload.history)
        }
        return payload.stock
    }

    suspend fun refreshHistory(
        symbol: String,
        requirementType: MarketDataRequirementType = defaultHistoryRequirement(),
        limit: Int = 60,
        displayName: String? = null
    ): List<HistoryPoint> {
        val payload = marketDataRouter?.fetch(
            MarketDataRequest(
                symbol = symbol,
                requirementType = requirementType,
                displayName = displayName,
                limit = limit
            )
        ) ?: return emptyList()
        payload.stock?.let { saveStock(it) }
        if (payload.history.isNotEmpty()) {
            saveHistory(symbol, payload.history)
        }
        return payload.history
    }

    suspend fun refreshTrackedStocks(
        requirementType: MarketDataRequirementType = MarketDataRequirementType.QUOTE
    ): Int {
        if (marketDataRouter?.hasConfiguredProviders() != true) return 0
        val trackedStocks = stockDao.getAllStocks().first()
        var refreshCount = 0
        trackedStocks.forEach { stock ->
            if (refreshStock(stock.symbol, requirementType, stock.name) != null) {
                refreshCount += 1
            }
        }
        return refreshCount
    }

    /** Upsert stock record (called by DataIngestion or sync worker). */
    suspend fun saveStock(stock: StockData) {
        stockDao.insertOrUpdateStock(stock.toEntity())
    }

    /** Bulk save stocks. */
    suspend fun saveStocks(stocks: List<StockData>) {
        stockDao.insertOrUpdateStocks(stocks.map { it.toEntity() })
    }

    /** Save historical data points. */
    suspend fun saveHistory(symbol: String, points: List<HistoryPoint>) {
        historyDao.insertHistory(points.map {
            StockHistory(
                symbol = symbol,
                timestamp = it.timestamp,
                open = it.close,
                high = it.close,
                low = it.close,
                close = it.close,
                volume = it.volume
            )
        })
    }

    /** Remove history older than [before] epoch millis. */
    suspend fun pruneHistory(symbol: String, before: Long) {
        historyDao.pruneOldHistory(symbol, before)
    }

    suspend fun stockCount(): Int = stockDao.count()

    private fun shouldRefreshQuote(
        lastUpdated: Long?,
        requirementType: MarketDataRequirementType
    ): Boolean {
        if (marketDataRouter?.hasConfiguredProviders() != true) return false
        if (lastUpdated == null) return true
        val freshnessWindow = when (requirementType) {
            MarketDataRequirementType.MARKET_METADATA,
            MarketDataRequirementType.FUNDAMENTAL_ANALYSIS -> 86_400_000L
            else -> 900_000L
        }
        return System.currentTimeMillis() - lastUpdated > freshnessWindow
    }

    private fun shouldRefreshHistory(localHistory: List<HistoryPoint>): Boolean {
        if (marketDataRouter?.hasConfiguredProviders() != true) return false
        if (localHistory.isEmpty()) return true
        val latestTimestamp = localHistory.maxOf { it.timestamp }
        val freshnessWindow = 86_400_000L
        return System.currentTimeMillis() - latestTimestamp > freshnessWindow
    }

    private fun defaultQuoteRequirement(): MarketDataRequirementType =
        MarketDataRequirementType.QUOTE

    private fun defaultHistoryRequirement(): MarketDataRequirementType =
        MarketDataRequirementType.DAILY_HISTORY

    // ---------- Mapping helpers ----------

    private fun Stock.toDomain() = StockData(
        symbol = symbol,
        name = name,
        currentPrice = currentPrice,
        previousClose = previousClose,
        changePercent = changePercent,
        marketCap = marketCap,
        sector = sector
    )

    private fun StockData.toEntity() = Stock(
        symbol = symbol,
        name = name,
        currentPrice = currentPrice,
        previousClose = previousClose,
        changePercent = changePercent,
        marketCap = marketCap,
        sector = sector
    )
}
