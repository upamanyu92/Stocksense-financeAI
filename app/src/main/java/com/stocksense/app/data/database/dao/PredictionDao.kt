package com.stocksense.app.data.database.dao

import androidx.room.*
import com.stocksense.app.data.database.entities.Prediction
import kotlinx.coroutines.flow.Flow

@Dao
interface PredictionDao {

    @Query("SELECT * FROM predictions WHERE symbol = :symbol ORDER BY createdAt DESC")
    fun getPredictionsForSymbol(symbol: String): Flow<List<Prediction>>

    @Query("SELECT * FROM predictions WHERE symbol = :symbol ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestPrediction(symbol: String): Prediction?

    @Query("SELECT * FROM predictions WHERE actualPrice IS NULL ORDER BY createdAt ASC")
    suspend fun getUnresolvedPredictions(): List<Prediction>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPrediction(prediction: Prediction): Long

    @Update
    suspend fun updatePrediction(prediction: Prediction)

    @Query(
        "UPDATE predictions SET actualPrice = :actual, errorPercent = :error, resolvedAt = :resolvedAt " +
        "WHERE id = :id"
    )
    suspend fun resolvePrediction(id: Long, actual: Double, error: Double, resolvedAt: Long)

    @Query("DELETE FROM predictions WHERE createdAt < :before")
    suspend fun pruneOldPredictions(before: Long)
}
