package com.stocksense.app.data.database.dao

import androidx.room.*
import com.stocksense.app.data.database.entities.Trade
import kotlinx.coroutines.flow.Flow

@Dao
interface TradeDao {
    @Query("SELECT * FROM trades ORDER BY executedAt DESC")
    fun getAllTrades(): Flow<List<Trade>>

    @Query("SELECT * FROM trades WHERE symbol = :symbol ORDER BY executedAt DESC")
    suspend fun getTradesForSymbol(symbol: String): List<Trade>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrade(trade: Trade): Long

    @Delete
    suspend fun deleteTrade(trade: Trade)
}
