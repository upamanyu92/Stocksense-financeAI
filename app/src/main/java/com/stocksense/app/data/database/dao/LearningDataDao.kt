package com.stocksense.app.data.database.dao

import androidx.room.*
import com.stocksense.app.data.database.entities.LearningData

@Dao
interface LearningDataDao {

    @Query("SELECT * FROM learning_data WHERE symbol = :symbol LIMIT 1")
    suspend fun getLearningData(symbol: String): LearningData?

    @Query("SELECT * FROM learning_data")
    suspend fun getAllLearningData(): List<LearningData>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(data: LearningData)

    @Update
    suspend fun update(data: LearningData)

    @Query("DELETE FROM learning_data WHERE symbol = :symbol")
    suspend fun deleteForSymbol(symbol: String)
}
