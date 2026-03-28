package com.stocksense.app.data.database.dao

import androidx.room.*
import com.stocksense.app.data.database.entities.PortfolioHolding
import kotlinx.coroutines.flow.Flow

@Dao
interface PortfolioHoldingDao {
    @Query("SELECT * FROM portfolio_holdings ORDER BY symbol ASC")
    fun getAll(): Flow<List<PortfolioHolding>>

    @Query("SELECT * FROM portfolio_holdings WHERE symbol = :symbol LIMIT 1")
    suspend fun getBySymbol(symbol: String): PortfolioHolding?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(holding: PortfolioHolding)

    @Delete
    suspend fun delete(holding: PortfolioHolding)

    @Query("DELETE FROM portfolio_holdings WHERE symbol = :symbol")
    suspend fun deleteBySymbol(symbol: String)

    @Query("SELECT SUM(currentValue) FROM portfolio_holdings")
    suspend fun getTotalValue(): Double?

    @Query("SELECT SUM(investedValue) FROM portfolio_holdings")
    suspend fun getTotalInvested(): Double?
}
