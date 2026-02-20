package com.shaun.sydia.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.shaun.sydia.data.local.ChatHistoryEntity
import com.shaun.sydia.data.remote.AIConfig
import com.shaun.sydia.data.remote.AIService
import com.shaun.sydia.data.remote.ChatMessage
import com.shaun.sydia.data.repository.ChatRepository
import com.shaun.sydia.data.repository.MemoryRepository
import com.shaun.sydia.data.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
        private val chatRepository: ChatRepository,
        private val memoryRepository: MemoryRepository,
        private val settingsRepository: SettingsRepository,
        private val aiService: AIService
) : ViewModel() {

    val chatStream: Flow<PagingData<ChatHistoryEntity>> =
            chatRepository.getChatStream().cachedIn(viewModelScope)

    val memories =
            memoryRepository.allMemories.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun sendMessage(text: String) {
        viewModelScope.launch {
            // 1. 保存用户消息到本地数据库
            chatRepository.sendMessage(text, "user")

            try {
                // 2. 获取统一配置
                val provider = settingsRepository.chatModelProvider.first()
                val model = settingsRepository.chatModelName.first()
                val apiKey = settingsRepository.chatApiKey.first()
                val baseUrl = settingsRepository.chatBaseUrl.first()
                val contextLimit = settingsRepository.contextLength.first()

                // 3. 构建 System Prompt (包含记忆)
                val memoryList = memoryRepository.allMemories.first()
                val memoryContext = if (memoryList.isNotEmpty()) {
                    "\nRelevant memories:\n" + memoryList.joinToString("\n") { "- ${it.body}" }
                } else ""
                
                val systemPrompt = "You are Sydia, a digital assistant. $memoryContext"

                // 4. 获取上下文消息（最后 N 轮）
                val recentMessages = chatRepository.getRecentMessages(contextLimit)
                    .reversed() // 数据库是 DESC，需要反转回时间正序
                    .map { ChatMessage(it.role, it.content) }

                val messages = mutableListOf<ChatMessage>()
                messages.add(ChatMessage("system", systemPrompt))
                messages.addAll(recentMessages)

                // 5. 调用云端 API
                val config = AIConfig(provider, model, apiKey, baseUrl)
                val response = aiService.getChatResponse(config, messages)

                // 6. 保存 AI 回复到本地数据库
                chatRepository.sendMessage(response, "assistant")

                // 7. 触发背景记忆形成
                
                handleMemoryFormation(text, response)
            } catch (e: Exception) {
                chatRepository.sendMessage("Error: ${e.message}", "system")
            }
        }
    }

    private suspend fun handleMemoryFormation(userMsg: String, aiResponse: String) {
        // TODO：逻辑与 overview.md 中一致
        // 检查提取频率
        val freq = settingsRepository.extractionFrequency.first()
        // 简单逻辑：如果消息长度 > 20 或随机
        if (userMsg.length > 20) {
            // TODO：实际实现将调用云端 API 进行嵌入
            // memoryRepository.addMemory(...)
        }
    }

    fun addMemory(text: String) {
        viewModelScope.launch {
            memoryRepository.addMemory(body = text, weight = 1.0f, category = "Manual")
        }
    }

    fun resetContext() {
        viewModelScope.launch { chatRepository.sendMessage("--- Context Reset ---", "system") }
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
            return ChatViewModel(chatRepository, memoryRepository, settingsRepository, aiService) as
                    T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
