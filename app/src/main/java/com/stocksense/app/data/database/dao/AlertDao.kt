package com.stocksense.app.data.database.dao

import androidx.room.*
import com.stocksense.app.data.database.entities.Alert
import com.stocksense.app.data.database.entities.AlertStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface AlertDao {

    @Query("SELECT * FROM alerts ORDER BY createdAt DESC")
    fun getAllAlerts(): Flow<List<Alert>>

    @Query("SELECT * FROM alerts WHERE status = 'ACTIVE'")
    suspend fun getActiveAlerts(): List<Alert>

    @Query("SELECT * FROM alerts WHERE symbol = :symbol AND status = 'ACTIVE'")
    suspend fun getActiveAlertsForSymbol(symbol: String): List<Alert>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: Alert): Long

    @Update
    suspend fun updateAlert(alert: Alert)

    @Query(
        "UPDATE alerts SET status = :status, triggeredAt = :triggeredAt WHERE id = :id"
    )
    suspend fun updateAlertStatus(id: Long, status: AlertStatus, triggeredAt: Long?)

    @Delete
    suspend fun deleteAlert(alert: Alert)

    @Query("DELETE FROM alerts WHERE id = :id")
    suspend fun deleteById(id: Long)
}
