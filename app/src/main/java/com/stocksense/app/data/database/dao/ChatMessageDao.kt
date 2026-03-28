package com.stocksense.app.data.database.dao

import androidx.room.*
import com.stocksense.app.data.database.entities.ChatMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int = 5): List<ChatMessage>

    @Query("SELECT * FROM chat_history ORDER BY timestamp DESC")
    fun observeMessages(): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: ChatMessage): Long

    @Query("DELETE FROM chat_history WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("DELETE FROM chat_history")
    suspend fun deleteAll()
}
