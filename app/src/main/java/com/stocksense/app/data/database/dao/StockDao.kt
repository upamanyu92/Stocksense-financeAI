package com.stocksense.app.data.database.dao

import androidx.room.*
import com.stocksense.app.data.database.entities.Stock
import kotlinx.coroutines.flow.Flow

@Dao
interface StockDao {

    @Query("SELECT * FROM stocks ORDER BY symbol ASC")
    fun getAllStocks(): Flow<List<Stock>>

    @Query("SELECT * FROM stocks WHERE symbol = :symbol LIMIT 1")
    suspend fun getStock(symbol: String): Stock?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateStock(stock: Stock)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateStocks(stocks: List<Stock>)

    @Delete
    suspend fun deleteStock(stock: Stock)

    @Query("DELETE FROM stocks WHERE symbol = :symbol")
    suspend fun deleteBySymbol(symbol: String)

    @Query("SELECT COUNT(*) FROM stocks")
    suspend fun count(): Int
}
