package com.stocksense.app.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TradeType { BUY, SELL }

@Entity(tableName = "trades", indices = [Index("symbol")])
data class Trade(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val tradeType: TradeType,
    val quantity: Double,
    val price: Double,
    val totalValue: Double,
    val executedAt: Long = System.currentTimeMillis()
)
