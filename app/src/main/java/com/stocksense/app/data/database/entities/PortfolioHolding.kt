package com.stocksense.app.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "portfolio_holdings",
    indices = [Index(value = ["symbol"], unique = true)]
)
data class PortfolioHolding(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val quantity: Double,
    val avgBuyPrice: Double,
    val currentPrice: Double,
    val investedValue: Double,
    val currentValue: Double,
    val pnl: Double,
    val pnlPercent: Double,
    val lastUpdated: Long = System.currentTimeMillis()
)
