package com.shaun.sydia

import android.app.Application
import com.shaun.sydia.data.local.SydiaDatabase
import com.shaun.sydia.data.repository.ChatRepository
import com.shaun.sydia.data.repository.MemoryRepository

class SydiaApplication : Application() {
    val database by lazy { SydiaDatabase.getDatabase(this) }
    val chatRepository by lazy { ChatRepository(database.chatHistoryDao()) }
    val memoryRepository by lazy { MemoryRepository(database.memoryDao()) }
}
