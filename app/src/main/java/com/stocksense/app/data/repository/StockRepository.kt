package com.stocksense.app.data.repository

import com.stocksense.app.data.database.dao.StockDao
import com.stocksense.app.data.database.dao.StockHistoryDao
import com.stocksense.app.data.database.entities.Stock
import com.stocksense.app.data.database.entities.StockHistory
import com.stocksense.app.data.model.HistoryPoint
import com.stocksense.app.data.model.StockData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * StockRepository – single source of truth for stock data.
 * Reads from local Room database; writes come from DataIngestion or periodic sync.
 */
class StockRepository(
    private val stockDao: StockDao,
    private val historyDao: StockHistoryDao
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
    suspend fun getStock(symbol: String): StockData? =
        stockDao.getStock(symbol)?.toDomain()

    /** Fetch recent history (for ML feature extraction). */
    suspend fun getRecentHistory(symbol: String, limit: Int = 60): List<HistoryPoint> =
        historyDao.getRecentHistory(symbol, limit).map {
            HistoryPoint(it.timestamp, it.close, it.volume)
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
