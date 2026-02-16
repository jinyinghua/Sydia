package com.shaun.sydia.data.remote

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class AIService(private val gson: Gson) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun getChatResponse(
        provider: String,
        model: String,
        apiKey: String,
        baseUrl: String,
        messages: List<ChatMessage>
    ): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        return@withContext when (provider.lowercase()) {
            "openai" -> callOpenAI(model, apiKey, baseUrl, messages)
            "gemini" -> callGemini(model, apiKey, baseUrl, messages)
            "claude" -> callClaude(model, apiKey, baseUrl, messages)
            else -> throw IllegalArgumentException("Unsupported provider: $provider")
        }
    }

    suspend fun getEmbedding(
        provider: String,
        model: String,
        apiKey: String,
        baseUrl: String,
        text: String
    ): List<Float> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        return@withContext when (provider.lowercase()) {
            "openai" -> getOpenAIEmbedding(model, apiKey, baseUrl, text)
            "gemini" -> getGeminiEmbedding(model, apiKey, baseUrl, text)
            else -> throw IllegalArgumentException("Embedding unsupported for provider: $provider")
        }
    }

    private fun callOpenAI(model: String, apiKey: String, baseUrl: String, messages: List<ChatMessage>): String {
        val url = if (baseUrl.endsWith("/")) "${baseUrl}chat/completions" else "$baseUrl/chat/completions"
        
        val json = JsonObject().apply {
            addProperty("model", model)
            val messagesArray = JsonArray()
            messages.forEach { msg ->
                messagesArray.add(JsonObject().apply {
                    addProperty("role", msg.role)
                    addProperty("content", msg.content)
                })
            }
            add("messages", messagesArray)
        }

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(gson.toJson(json).toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("OpenAI Unexpected code $response. Body: ${response.body?.string()}")
            val body = response.body?.string() ?: throw IOException("Empty body")
            val jsonResponse = gson.fromJson(body, JsonObject::class.java)
            return jsonResponse.getAsJsonArray("choices")
                .get(0).asJsonObject
                .getAsJsonObject("message")
                .get("content").asString
        }
    }

    private fun callGemini(model: String, apiKey: String, baseUrl: String, messages: List<ChatMessage>): String {
        val actualBaseUrl = if (baseUrl.isBlank() || baseUrl.contains("api.openai.com")) "https://generativelanguage.googleapis.com/v1beta/" else baseUrl
        val url = "${actualBaseUrl}models/$model:generateContent?key=$apiKey"

        val contentsArray = JsonArray()
        messages.forEach { msg ->
            val role = if (msg.role == "assistant") "model" else "user"
            contentsArray.add(JsonObject().apply {
                addProperty("role", role)
                val partsArray = JsonArray()
                partsArray.add(JsonObject().apply {
                    addProperty("text", msg.content)
                })
                add("parts", partsArray)
            })
        }
        
        val json = JsonObject().apply {
            add("contents", contentsArray)
        }

        val request = Request.Builder()
            .url(url)
            .post(gson.toJson(json).toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Gemini Unexpected code $response. Body: ${response.body?.string()}")
            val body = response.body?.string() ?: throw IOException("Empty body")
            val jsonResponse = gson.fromJson(body, JsonObject::class.java)
            return jsonResponse.getAsJsonArray("candidates")
                .get(0).asJsonObject
                .getAsJsonObject("content")
                .getAsJsonArray("parts")
                .get(0).asJsonObject
                .get("text").asString
        }
    }

    private fun callClaude(model: String, apiKey: String, baseUrl: String, messages: List<ChatMessage>): String {
        val actualBaseUrl = if (baseUrl.isBlank() || baseUrl.contains("api.openai.com")) "https://api.anthropic.com/v1/" else baseUrl
        val url = if (actualBaseUrl.endsWith("/")) "${actualBaseUrl}messages" else "$actualBaseUrl/messages"

        val json = JsonObject().apply {
            addProperty("model", model)
            addProperty("max_tokens", 1024)
            val messagesArray = JsonArray()
            messages.filter { it.role != "system" }.forEach { msg ->
                messagesArray.add(JsonObject().apply {
                    addProperty("role", msg.role)
                    addProperty("content", msg.content)
                })
            }
            add("messages", messagesArray)
            
            val systemMsg = messages.find { it.role == "system" }
            if (systemMsg != null) {
                addProperty("system", systemMsg.content)
            }
        }

        val request = Request.Builder()
            .url(url)
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(gson.toJson(json).toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Claude Unexpected code $response. Body: ${response.body?.string()}")
            val body = response.body?.string() ?: throw IOException("Empty body")
            val jsonResponse = gson.fromJson(body, JsonObject::class.java)
            return jsonResponse.getAsJsonArray("content")
                .get(0).asJsonObject
                .get("text").asString
        }
    }

    private fun getOpenAIEmbedding(model: String, apiKey: String, baseUrl: String, text: String): List<Float> {
        val url = if (baseUrl.endsWith("/")) "${baseUrl}embeddings" else "$baseUrl/embeddings"
        val json = JsonObject().apply {
            addProperty("model", model)
            addProperty("input", text)
        }
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .post(gson.toJson(json).toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("OpenAI Embedding Unexpected code $response")
            val body = response.body?.string() ?: throw IOException("Empty body")
            val jsonResponse = gson.fromJson(body, JsonObject::class.java)
            val embeddingArray = jsonResponse.getAsJsonArray("data")
                .get(0).asJsonObject
                .getAsJsonArray("embedding")
            return embeddingArray.map { it.asFloat }
        }
    }

    private fun getGeminiEmbedding(model: String, apiKey: String, baseUrl: String, text: String): List<Float> {
        val actualBaseUrl = if (baseUrl.isBlank() || baseUrl.contains("api.openai.com")) "https://generativelanguage.googleapis.com/v1beta/" else baseUrl
        val url = "${actualBaseUrl}models/$model:embedContent?key=$apiKey"
        
        val json = JsonObject().apply {
            val content = JsonObject().apply {
                val partsArray = JsonArray()
                partsArray.add(JsonObject().apply {
                    addProperty("text", text)
                })
                add("parts", partsArray)
            }
            add("content", content)
        }

        val request = Request.Builder()
            .url(url)
            .post(gson.toJson(json).toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Gemini Embedding Unexpected code $response")
            val body = response.body?.string() ?: throw IOException("Empty body")
            val jsonResponse = gson.fromJson(body, JsonObject::class.java)
            val embeddingArray = jsonResponse.getAsJsonObject("embedding")
                .getAsJsonArray("values")
            return embeddingArray.map { it.asFloat }
        }
    }
}

data class ChatMessage(val role: String, val content: String)
