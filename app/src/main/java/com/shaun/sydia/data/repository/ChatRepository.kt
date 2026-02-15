package com.shaun.sydia.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.shaun.sydia.data.local.ChatHistoryDao
import com.shaun.sydia.data.local.ChatHistoryEntity
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val chatHistoryDao: ChatHistoryDao) {
    fun getChatStream(): Flow<PagingData<ChatHistoryEntity>> {
        return Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
            pagingSourceFactory = { chatHistoryDao.getAllMessages() }
        ).flow
    }

    suspend fun sendMessage(content: String, role: String) {
        val message = ChatHistoryEntity(
            role = role,
            content = content,
            timestamp = System.currentTimeMillis()
        )
        chatHistoryDao.insertMessage(message)
    }

    suspend fun clearHistory() {
        chatHistoryDao.clearHistory()
    }
}
