package com.stocksense.app.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "watchlist", indices = [Index("symbol")])
data class WatchlistItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val displayOrder: Int,
    val addedAt: Long = System.currentTimeMillis()
)
