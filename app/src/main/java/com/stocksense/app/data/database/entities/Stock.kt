package com.stocksense.app.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a tracked stock symbol with its latest known price.
 */
@Entity(tableName = "stocks")
data class Stock(
    @PrimaryKey val symbol: String,
    val name: String,
    val currentPrice: Double,
    val previousClose: Double,
    val changePercent: Double,
    val marketCap: Long = 0L,
    val sector: String = "",
    val lastUpdated: Long = System.currentTimeMillis()
)
