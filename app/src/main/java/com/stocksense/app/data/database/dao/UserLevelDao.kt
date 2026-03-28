package com.stocksense.app.data.database.dao

import androidx.room.*
import com.stocksense.app.data.database.entities.UserLevel
import kotlinx.coroutines.flow.Flow

@Dao
interface UserLevelDao {
    @Query("SELECT * FROM user_levels LIMIT 1")
    suspend fun getUserLevel(): UserLevel?

    @Query("SELECT * FROM user_levels LIMIT 1")
    fun observeUserLevel(): Flow<UserLevel?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(userLevel: UserLevel)

    @Query("UPDATE user_levels SET xpPoints = xpPoints + :xp")
    suspend fun addXp(xp: Int)

    @Query("UPDATE user_levels SET predictionsMade = predictionsMade + 1")
    suspend fun incrementPredictions()

    @Query("UPDATE user_levels SET correctPredictions = correctPredictions + 1")
    suspend fun incrementCorrectPredictions()

    @Query("UPDATE user_levels SET streakDays = :days")
    suspend fun updateStreak(days: Int)
}
