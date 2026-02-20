package com.shaun.sydia.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    companion object {
        val CHAT_MODEL_PROVIDER = stringPreferencesKey("chat_model_provider")
        val CHAT_MODEL_NAME = stringPreferencesKey("chat_model_name")
        val CHAT_API_KEY = stringPreferencesKey("chat_api_key")
        val CHAT_BASE_URL = stringPreferencesKey("chat_base_url")
        val CHAT_STREAM_ENABLED = booleanPreferencesKey("chat_stream_enabled")

        val EMBEDDING_MODEL_PROVIDER = stringPreferencesKey("embedding_model_provider")
        val EMBEDDING_MODEL_NAME = stringPreferencesKey("embedding_model_name")
        val EMBEDDING_API_KEY = stringPreferencesKey("embedding_api_key")
        val EMBEDDING_BASE_URL = stringPreferencesKey("embedding_base_url")
        
        val CONTEXT_LENGTH = intPreferencesKey("context_length")
        val EXTRACTION_FREQUENCY = intPreferencesKey("extraction_frequency")
        val PERSONALITY = stringPreferencesKey("personality")
    }

    val chatModelProvider: Flow<String> = context.dataStore.data.map { it[CHAT_MODEL_PROVIDER] ?: "OpenAI" }
    val chatModelName: Flow<String> = context.dataStore.data.map { it[CHAT_MODEL_NAME] ?: "gpt-3.5-turbo" }
    val chatApiKey: Flow<String> = context.dataStore.data.map { it[CHAT_API_KEY] ?: "" }
    val chatBaseUrl: Flow<String> = context.dataStore.data.map { it[CHAT_BASE_URL] ?: "https://api.openai.com/v1/" }
    val chatStreamEnabled: Flow<Boolean> = context.dataStore.data.map { it[CHAT_STREAM_ENABLED] ?: false }

    val embeddingModelProvider: Flow<String> = context.dataStore.data.map { it[EMBEDDING_MODEL_PROVIDER] ?: "OpenAI" }
    val embeddingModelName: Flow<String> = context.dataStore.data.map { it[EMBEDDING_MODEL_NAME] ?: "text-embedding-3-small" }
    val embeddingApiKey: Flow<String> = context.dataStore.data.map { it[EMBEDDING_API_KEY] ?: "" }
    val embeddingBaseUrl: Flow<String> = context.dataStore.data.map { it[EMBEDDING_BASE_URL] ?: "https://api.openai.com/v1/" }

    val contextLength: Flow<Int> = context.dataStore.data.map { it[CONTEXT_LENGTH] ?: 10 }
    val extractionFrequency: Flow<Int> = context.dataStore.data.map { it[EXTRACTION_FREQUENCY] ?: 3 }
    val personality: Flow<String> = context.dataStore.data.map { it[PERSONALITY] ?: "Sharp" }

    suspend fun updateChatSettings(provider: String, model: String, apiKey: String, baseUrl: String) {
        context.dataStore.edit {
            it[CHAT_MODEL_PROVIDER] = provider
            it[CHAT_MODEL_NAME] = model
            it[CHAT_API_KEY] = apiKey
            it[CHAT_BASE_URL] = baseUrl
        }
    }

    suspend fun updateChatStreamEnabled(enabled: Boolean) {
        context.dataStore.edit { it[CHAT_STREAM_ENABLED] = enabled }
    }

    suspend fun updateEmbeddingSettings(provider: String, model: String, apiKey: String, baseUrl: String) {
        context.dataStore.edit {
            it[EMBEDDING_MODEL_PROVIDER] = provider
            it[EMBEDDING_MODEL_NAME] = model
            it[EMBEDDING_API_KEY] = apiKey
            it[EMBEDDING_BASE_URL] = baseUrl
        }
    }
    
    suspend fun updateContextLength(length: Int) {
        context.dataStore.edit { it[CONTEXT_LENGTH] = length }
    }
    
    suspend fun updateExtractionFrequency(frequency: Int) {
        context.dataStore.edit { it[EXTRACTION_FREQUENCY] = frequency }
    }
    
    suspend fun updatePersonality(personality: String) {
        context.dataStore.edit { it[PERSONALITY] = personality }
    }
}
