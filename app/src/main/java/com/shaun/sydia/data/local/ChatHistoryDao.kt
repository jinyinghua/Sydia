package com.shaun.sydia.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.paging.PagingSource

@Dao
interface ChatHistoryDao {
    @Insert
    suspend fun insertMessage(message: ChatHistoryEntity)

    @Query("SELECT * FROM chat_history ORDER BY timestamp DESC")
    fun getAllMessages(): PagingSource<Int, ChatHistoryEntity>

    @Query("SELECT * FROM chat_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(limit: Int): List<ChatHistoryEntity>

    @Query("DELETE FROM chat_history")
    suspend fun clearHistory()
}
