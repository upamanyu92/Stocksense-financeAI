package com.stocksense.app.data.database.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Historical OHLCV data point for a stock symbol.
 */
@Entity(
    tableName = "stock_history",
    foreignKeys = [
        ForeignKey(
            entity = Stock::class,
            parentColumns = ["symbol"],
            childColumns = ["symbol"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("symbol"), Index("timestamp")]
)
data class StockHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val timestamp: Long,       // epoch millis (day granularity)
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Long
)
