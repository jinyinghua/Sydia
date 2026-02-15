package com.shaun.sydia.data.repository

import com.shaun.sydia.data.local.MemoryDao
import com.shaun.sydia.data.local.MemoryEntity
import kotlinx.coroutines.flow.Flow

class MemoryRepository(private val memoryDao: MemoryDao) {
    val allMemories: Flow<List<MemoryEntity>> = memoryDao.getAllMemories()

    suspend fun addMemory(body: String, weight: Float, category: String? = null) {
        val memory = MemoryEntity(
            body = body,
            weight = weight,
            date = System.currentTimeMillis(),
            category = category
        )
        memoryDao.insertMemory(memory)
    }

    suspend fun deleteMemory(memory: MemoryEntity) {
        memoryDao.deleteMemory(memory)
    }

    fun searchMemories(query: String): Flow<List<MemoryEntity>> {
        return memoryDao.searchMemories(query)
    }
}
