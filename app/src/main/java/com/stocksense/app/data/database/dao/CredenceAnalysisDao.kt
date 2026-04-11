package com.stocksense.app.data.database.dao

import androidx.room.*
import com.stocksense.app.data.database.entities.CredenceAnalysis
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO for persisting and querying CredenceAI Tatva Ank analyses.
 */
@Dao
interface CredenceAnalysisDao {

    /**
     * Insert a new analysis record. Replaces any row with the same primary key
     * (safe for upsert when updating HITL feedback).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(analysis: CredenceAnalysis): Long

    /** Observe all analyses sorted by most recent first. */
    @Query("SELECT * FROM credence_analyses ORDER BY createdAt DESC")
    fun getAll(): Flow<List<CredenceAnalysis>>

    /** Fetch a single analysis by its primary key. */
    @Query("SELECT * FROM credence_analyses WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CredenceAnalysis?

    /** Fetch the most recent N analyses (useful for History tab). */
    @Query("SELECT * FROM credence_analyses ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<CredenceAnalysis>

    /** Delete analyses older than [timestampMs] to control storage usage. */
    @Query("DELETE FROM credence_analyses WHERE createdAt < :timestampMs")
    suspend fun deleteOlderThan(timestampMs: Long)

    /** Delete all analyses (used in settings or tests). */
    @Query("DELETE FROM credence_analyses")
    suspend fun deleteAll()

    /** Count of stored analyses. */
    @Query("SELECT COUNT(*) FROM credence_analyses")
    suspend fun count(): Int
}

