package com.stocksense.app.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_levels")
data class UserLevel(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val levelName: String,
    val xpPoints: Int = 0,
    val predictionsMade: Int = 0,
    val correctPredictions: Int = 0,
    val streakDays: Int = 0,
    val badges: String = "[]"
)
