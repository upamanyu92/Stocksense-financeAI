package com.stocksense.app.ingestion

import android.content.Context
import android.util.Log
import com.stocksense.app.data.model.HistoryPoint
import com.stocksense.app.data.model.StockData
import com.stocksense.app.data.repository.StockRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader

private const val TAG = "DataIngestion"
private const val STOCKS_ASSET = "stocks_initial.json"
private const val STK_ASSET = "stk.json"
private const val HISTORY_ASSET_PREFIX = "history_"   // e.g. history_AAPL.csv

/**
 * DataIngestion – loads seed stock data from bundled assets on first launch.
 *
 * Assets required (place in app/src/main/assets/):
 *   stk.json             – primary array of company details (with industry, description, etc.)
 *   stocks_initial.json  – fallback array of [SeedStock]
 *   history_AAPL.csv     – CSV with columns: date,open,high,low,close,volume
 *   history_GOOG.csv     – …
 */
class DataIngestion(
    private val context: Context,
    private val repository: StockRepository
) {

    /**
     * Seed the database from bundled assets if it is empty.
     * Prefers stk.json (comprehensive company details); falls back to stocks_initial.json.
     * Safe to call multiple times; will no-op if data already exists.
     */
    suspend fun seedIfEmpty() = withContext(Dispatchers.IO) {
        if (repository.stockCount() > 0) {
            Log.d(TAG, "Database already seeded – skipping")
            return@withContext
        }
        try {
            val stocks = loadStocksFromStkAsset() ?: loadStocksFromAsset()
            repository.saveStocks(stocks)
            for (stock in stocks) {
                loadHistoryFromAsset(stock.symbol)?.let { history ->
                    repository.saveHistory(stock.symbol, history)
                }
            }
            Log.i(TAG, "Database seeded with ${stocks.size} stocks")
        } catch (e: Exception) {
            Log.e(TAG, "Seeding failed: ${e.message}")
        }
    }

    // ---------- Private helpers ----------

    /**
     * Load stocks from stk.json (comprehensive company details).
     * Returns null if the asset is missing.
     */
    private fun loadStocksFromStkAsset(): List<StockData>? {
        return try {
            val json = context.assets.open(STK_ASSET).bufferedReader().readText()
            val seeds = Json { ignoreUnknownKeys = true }.decodeFromString<List<SeedStock>>(json)
            seeds.map { it.toStockData() }
        } catch (e: Exception) {
            Log.d(TAG, "stk.json not found or invalid, falling back to stocks_initial.json")
            null
        }
    }

    private fun loadStocksFromAsset(): List<StockData> {
        val json = context.assets.open(STOCKS_ASSET).bufferedReader().readText()
        val seeds = Json { ignoreUnknownKeys = true }.decodeFromString<List<SeedStock>>(json)
        return seeds.map { it.toStockData() }
    }

    private fun loadHistoryFromAsset(symbol: String): List<HistoryPoint>? {
        val assetName = "$HISTORY_ASSET_PREFIX${symbol}.csv"
        return try {
            val reader = BufferedReader(InputStreamReader(context.assets.open(assetName)))
            val points = mutableListOf<HistoryPoint>()
            reader.useLines { lines ->
                lines.drop(1).forEach { line ->   // skip header
                    parseCsvLine(line)?.let { points.add(it) }
                }
            }
            points
        } catch (e: Exception) {
            Log.d(TAG, "No history asset for $symbol")
            null
        }
    }

    /** Parse a CSV line: timestamp_ms,open,high,low,close,volume */
    private fun parseCsvLine(line: String): HistoryPoint? {
        val parts = line.split(",")
        if (parts.size < 6) return null
        return try {
            HistoryPoint(
                timestamp = parts[0].trim().toLong(),
                close = parts[4].trim().toDouble(),
                volume = parts[5].trim().toLong()
            )
        } catch (e: Exception) {
            null
        }
    }

    @Serializable
    private data class SeedStock(
        val symbol: String,
        val name: String,
        val currentPrice: Double,
        val previousClose: Double,
        val changePercent: Double,
        val marketCap: Long = 0L,
        val sector: String = ""
    ) {
        fun toStockData() = StockData(
            symbol = symbol,
            name = name,
            currentPrice = currentPrice,
            previousClose = previousClose,
            changePercent = changePercent,
            marketCap = marketCap,
            sector = sector
        )
    }
}
