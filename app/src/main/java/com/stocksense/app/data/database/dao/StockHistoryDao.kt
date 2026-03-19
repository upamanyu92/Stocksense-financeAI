package com.stocksense.app.data.database.dao

import androidx.room.*
import com.stocksense.app.data.database.entities.StockHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface StockHistoryDao {

    @Query("SELECT * FROM stock_history WHERE symbol = :symbol ORDER BY timestamp ASC")
    fun getHistory(symbol: String): Flow<List<StockHistory>>

    @Query(
        "SELECT * FROM stock_history WHERE symbol = :symbol ORDER BY timestamp DESC LIMIT :limit"
    )
    suspend fun getRecentHistory(symbol: String, limit: Int = 60): List<StockHistory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entries: List<StockHistory>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryEntry(entry: StockHistory)

    @Query("DELETE FROM stock_history WHERE symbol = :symbol AND timestamp < :before")
    suspend fun pruneOldHistory(symbol: String, before: Long)

    @Query("SELECT COUNT(*) FROM stock_history WHERE symbol = :symbol")
    suspend fun countForSymbol(symbol: String): Int
}
