package com.shaun.sydia.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.shaun.sydia.data.local.ChatHistoryEntity
import com.shaun.sydia.data.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

import com.shaun.sydia.data.local.MemoryEntity
import com.shaun.sydia.data.repository.MemoryRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import com.shaun.sydia.data.remote.AIService
import com.shaun.sydia.data.remote.ChatMessage
import com.shaun.sydia.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val memoryRepository: MemoryRepository,
    private val settingsRepository: SettingsRepository,
    private val aiService: AIService
) : ViewModel() {

    val chatStream: Flow<PagingData<ChatHistoryEntity>> = chatRepository.getChatStream()
        .cachedIn(viewModelScope)

    val memories = memoryRepository.allMemories
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun sendMessage(text: String) {
        viewModelScope.launch {
            // User message
            chatRepository.sendMessage(text, "user")
            
            try {
                // Get Settings
                val provider = settingsRepository.chatModelProvider.first()
                val model = settingsRepository.chatModelName.first()
                val apiKey = settingsRepository.chatApiKey.first()
                val baseUrl = settingsRepository.chatBaseUrl.first()
                val personality = settingsRepository.personality.first()

                // TODO: FETCH PERSISTED MEMORIES AND INJECT INTO SYSTEM PROMPT
                val systemPrompt = "You are Sydia, a digital assistant. Your personality is $personality. "
                
                // Get context messages (last N)
                // For now, let's just use the current message as a simple implementation
                // Real implementation would pull from DB
                val messages = listOf(
                    ChatMessage("system", systemPrompt),
                    ChatMessage("user", text)
                )

                val response = aiService.getChatResponse(provider, model, apiKey, baseUrl, messages)
                chatRepository.sendMessage(response, "assistant")
                
                // Trigger background memory worker if needed
                handleMemoryFormation(text, response)
            } catch (e: Exception) {
                chatRepository.sendMessage("Error: ${e.message}", "system")
            }
        }
    }
    
    private suspend fun handleMemoryFormation(userMsg: String, aiResponse: String) {
        // Logic for Async Worker as per overview.md
        // In this "real" version, we check the frequency
        val freq = settingsRepository.extractionFrequency.first()
        // Simple logic: if message length > 20 or randomly
        if (userMsg.length > 20) {
             // In a real app, this would be a WorkManager task calling Cloud API for embedding
             // memoryRepository.addMemory(...) 
        }
    }

    fun addMemory(text: String) {
        viewModelScope.launch {
            memoryRepository.addMemory(body = text, weight = 1.0f, category = "Manual")
        }
    }

    fun resetContext() {
        viewModelScope.launch {
             chatRepository.sendMessage("--- Context Reset ---", "system")
        }
    }
}

class ChatViewModelFactory(
    private val chatRepository: ChatRepository,
    private val memoryRepository: MemoryRepository,
    private val settingsRepository: SettingsRepository,
    private val aiService: AIService
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(chatRepository, memoryRepository, settingsRepository, aiService) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
