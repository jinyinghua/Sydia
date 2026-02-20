package com.shaun.sydia.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.shaun.sydia.data.repository.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val settingsRepository: SettingsRepository) : ViewModel() {

    val chatModelProvider = settingsRepository.chatModelProvider
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "OpenAI")
    val chatModelName = settingsRepository.chatModelName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "gpt-3.5-turbo")
    val chatApiKey = settingsRepository.chatApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val chatBaseUrl = settingsRepository.chatBaseUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "https://api.openai.com/v1/")
    val chatStreamEnabled = settingsRepository.chatStreamEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val embeddingModelProvider = settingsRepository.embeddingModelProvider
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "OpenAI")
    val embeddingModelName = settingsRepository.embeddingModelName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "text-embedding-3-small")
    val embeddingApiKey = settingsRepository.embeddingApiKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    val embeddingBaseUrl = settingsRepository.embeddingBaseUrl
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "https://api.openai.com/v1/")

    val contextLength = settingsRepository.contextLength
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10)
    val extractionFrequency = settingsRepository.extractionFrequency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)
    val personality = settingsRepository.personality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "Sharp")

    fun updateChatSettings(provider: String, model: String, apiKey: String, baseUrl: String) {
        viewModelScope.launch {
            settingsRepository.updateChatSettings(provider, model, apiKey, baseUrl)
        }
    }

    fun updateChatStreamEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.updateChatStreamEnabled(enabled)
        }
    }

    fun updateEmbeddingSettings(provider: String, model: String, apiKey: String, baseUrl: String) {
        viewModelScope.launch {
            settingsRepository.updateEmbeddingSettings(provider, model, apiKey, baseUrl)
        }
    }

    fun updateContextLength(length: Int) {
        viewModelScope.launch {
            settingsRepository.updateContextLength(length)
        }
    }

    fun updateExtractionFrequency(frequency: Int) {
        viewModelScope.launch {
            settingsRepository.updateExtractionFrequency(frequency)
        }
    }

    fun updatePersonality(personality: String) {
        viewModelScope.launch {
            settingsRepository.updatePersonality(personality)
        }
    }
}

class SettingsViewModelFactory(private val settingsRepository: SettingsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(settingsRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
