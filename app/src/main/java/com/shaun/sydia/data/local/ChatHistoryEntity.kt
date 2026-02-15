package com.shaun.sydia.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_history")
data class ChatHistoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val role: String, // "user" or "assistant"
    val content: String,
    val timestamp: Long,
    val isResetPoint: Boolean = false
)
