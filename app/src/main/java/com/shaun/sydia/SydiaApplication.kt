package com.shaun.sydia

import android.app.Application
import com.shaun.sydia.data.local.SydiaDatabase
import com.google.gson.Gson
import com.shaun.sydia.data.remote.AIService
import com.shaun.sydia.data.repository.ChatRepository
import com.shaun.sydia.data.repository.MemoryRepository
import com.shaun.sydia.data.repository.SettingsRepository

class SydiaApplication : Application() {
    val database by lazy { SydiaDatabase.getDatabase(this) }
    val chatRepository by lazy { ChatRepository(database.chatHistoryDao()) }
    val memoryRepository by lazy { MemoryRepository(database.memoryDao()) }
    val settingsRepository by lazy { SettingsRepository(this) }
    val gson by lazy { Gson() }
    val aiService by lazy { AIService(gson) }
}
