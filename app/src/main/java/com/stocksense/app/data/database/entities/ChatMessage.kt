package com.stocksense.app.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_history")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userMessage: String,
    val aiResponse: String,
    val sentiment: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
