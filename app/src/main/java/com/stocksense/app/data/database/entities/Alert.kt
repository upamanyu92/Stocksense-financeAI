package com.stocksense.app.data.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AlertType { PRICE_ABOVE, PRICE_BELOW, CHANGE_PERCENT, PREDICTION_SIGNAL }
enum class AlertStatus { ACTIVE, TRIGGERED, DISMISSED }

/**
 * User-defined alert rule for a stock.
 */
@Entity(
    tableName = "alerts",
    indices = [Index("symbol"), Index("status")]
)
data class Alert(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val symbol: String,
    val type: AlertType,
    val threshold: Double,
    val message: String = "",
    val status: AlertStatus = AlertStatus.ACTIVE,
    val createdAt: Long = System.currentTimeMillis(),
    val triggeredAt: Long? = null
)
