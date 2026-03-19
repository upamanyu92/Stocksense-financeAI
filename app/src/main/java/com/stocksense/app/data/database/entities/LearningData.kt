package com.stocksense.app.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Per-symbol adaptive learning weights used by LearningEngine.
 * Weights are updated each time a prediction is resolved.
 */
@Entity(
    tableName = "learning_data",
    indices = [Index("symbol", unique = true)]
)
data class LearningData(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    /** Moving-average weight applied to model output (0.5 – 1.5). */
    val adaptiveWeight: Double = 1.0,
    /** Exponential moving average of recent absolute errors (%). */
    val avgError: Double = 0.0,
    val predictionCount: Int = 0,
    val correctDirectionCount: Int = 0,
    val lastUpdated: Long = System.currentTimeMillis()
)
