package com.stocksense.app.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Stores a ML prediction alongside the eventual actual value for learning.
 */
@Entity(
    tableName = "predictions",
    indices = [Index("symbol"), Index("createdAt")]
)
data class Prediction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val predictedPrice: Double,
    val confidence: Float,           // 0.0 – 1.0
    val direction: String,           // "UP" | "DOWN" | "NEUTRAL"
    val horizon: Int = 1,            // days ahead
    val actualPrice: Double? = null, // filled in by LearningEngine
    val errorPercent: Double? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null
)
