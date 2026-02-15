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

class ChatViewModel(
    private val chatRepository: ChatRepository,
    private val memoryRepository: MemoryRepository
) : ViewModel() {

    val chatStream: Flow<PagingData<ChatHistoryEntity>> = chatRepository.getChatStream()
        .cachedIn(viewModelScope)

    val memories = memoryRepository.allMemories
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun sendMessage(text: String) {
        viewModelScope.launch {
            // User message
            chatRepository.sendMessage(text, "user")
            
            // TODO: Call LLM and get response. For demo, we simulate a response.
            // In real app, this would be an async call to cloud or local LLM.
            simulateResponse()
        }
    }
    
    private suspend fun simulateResponse() {
        // Simulate thinking delay
        kotlinx.coroutines.delay(1000)
        chatRepository.sendMessage("I received your message. This is a demo response.", "assistant")
        
        // Simulate memory formation occasionally
        if (System.currentTimeMillis() % 3 == 0L) { // Random-ish condition
            memoryRepository.addMemory(
                body = "User sent a message at ${System.currentTimeMillis()}",
                weight = 0.5f,
                category = "Interaction"
            )
        }
    }

    fun addMemory(text: String) {
        viewModelScope.launch {
            memoryRepository.addMemory(body = text, weight = 1.0f, category = "Manual")
        }
    }

    fun resetContext() {
        viewModelScope.launch {
             // Mark the current end as reset point or just insert a system message?
             // Overview says: Reset button inserts logical break.
             // We'll insert a special message or handle it in logic.
             // For now, let's just insert a system message saying "Context Reset"
             chatRepository.sendMessage("--- Context Reset ---", "system")
        }
    }
}

class ChatViewModelFactory(
    private val chatRepository: ChatRepository,
    private val memoryRepository: MemoryRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(chatRepository, memoryRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
