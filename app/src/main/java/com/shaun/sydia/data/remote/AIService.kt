package com.shaun.sydia.data.remote

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class AIService(private val gson: Gson) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * 流式获取聊天回复
     */
    fun getChatStream(config: AIConfig, messages: List<ChatMessage>): Flow<String> = flow {
        val request = buildRequest(config, messages, stream = true)

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Unexpected code $response. Body: ${response.body?.string()}")
            }

            val source = response.body?.source() ?: throw IOException("Empty body")
            while (!source.exhausted()) {
                val line = source.readUtf8LineStrict()
                if (line.isNotEmpty()) {
                    val content = parseStreamLine(config.provider, line)
                    if (!content.isNullOrEmpty()) {
                        emit(content)
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 获取完整聊天回复 (非流式，保留以兼容旧代码)
     */
    suspend fun getChatResponse(config: AIConfig, messages: List<ChatMessage>): String =
        withContext(Dispatchers.IO) {
            val request = buildRequest(config, messages, stream = false)

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Unexpected code $response. Body: ${response.body?.string()}")
                }
                val body = response.body?.string() ?: throw IOException("Empty body")
                parseNonStreamResponse(config.provider, body)
            }
        }

    suspend fun getEmbedding(config: AIConfig, text: String): List<Float> =
        withContext(Dispatchers.IO) {
            when (config.provider.lowercase()) {
                "openai" -> getOpenAIEmbedding(config, text)
                "gemini" -> getGeminiEmbedding(config, text)
                else -> throw IllegalArgumentException("Embedding unsupported for provider: ${config.provider}")
            }
        }

    // region Request Builders

    private fun buildRequest(config: AIConfig, messages: List<ChatMessage>, stream: Boolean): Request {
        return when (config.provider.lowercase()) {
            "openai" -> buildOpenAIRequest(config, messages, stream)
            "gemini" -> buildGeminiRequest(config, messages, stream)
            "claude" -> buildClaudeRequest(config, messages, stream)
            else -> throw IllegalArgumentException("Unsupported provider: ${config.provider}")
        }
    }

    private fun buildOpenAIRequest(config: AIConfig, messages: List<ChatMessage>, stream: Boolean): Request {
        val url = if (config.baseUrl.endsWith("/")) "${config.baseUrl}chat/completions" else "${config.baseUrl}/chat/completions"
        
        val payload = OpenAIRequest(
            model = config.model,
            messages = messages.map { OpenAIMessage(it.role, it.content) },
            stream = stream
        )

        return Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.apiKey}")
            .post(gson.toJson(payload).toRequestBody(jsonMediaType))
            .build()
    }

    private fun buildGeminiRequest(config: AIConfig, messages: List<ChatMessage>, stream: Boolean): Request {
        val url = "${config.baseUrl}models/${config.model}:generateContent?key=${config.apiKey}"

        val contents = messages.map { msg ->
            // Gemini roles: "user" or "model"
            val role = if (msg.role == "assistant") "model" else "user"
            GeminiContent(role, listOf(GeminiPart(msg.content)))
        }
        val payload = GeminiRequest(contents)

        return Request.Builder()
            .url(url)
            .post(gson.toJson(payload).toRequestBody(jsonMediaType))
            .build()
    }

    private fun buildClaudeRequest(config: AIConfig, messages: List<ChatMessage>, stream: Boolean): Request {
        val url = if (config.baseUrl.endsWith("/")) "${config.baseUrl}messages" else "${config.baseUrl}/messages"

        val systemMsg = messages.find { it.role == "system" }?.content
        val chatMessages = messages.filter { it.role != "system" }.map {
            ClaudeMessage(it.role, it.content)
        }

        val payload = ClaudeRequest(
            model = config.model,
            messages = chatMessages,
            system = systemMsg,
            stream = stream
        )

        return Request.Builder()
            .url(url)
            .header("x-api-key", config.apiKey)
            .header("anthropic-version", "2023-06-01")
            .post(gson.toJson(payload).toRequestBody(jsonMediaType))
            .build()
    }

    // endregion

    // region Response Parsers

    private fun parseStreamLine(provider: String, line: String): String? {
        if (!line.startsWith("data:")) return null
        val data = line.removePrefix("data:").trim()
        if (data == "[DONE]") return null

        return try {
            when (provider.lowercase()) {
                "openai" -> {
                    val response = gson.fromJson(data, OpenAIStreamResponse::class.java)
                    response.choices.firstOrNull()?.delta?.content
                }
                "gemini" -> {
                    val response = gson.fromJson(data, GeminiResponse::class.java)
                    response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
                }
                "claude" -> {
                    // Claude stream data structure
                    val response = gson.fromJson(data, ClaudeStreamResponse::class.java)
                    response.delta?.text
                }
                else -> null
            }
        } catch (e: Exception) {
            null // Ignore parsing errors for keep-alive or other events
        }
    }

    private fun parseNonStreamResponse(provider: String, body: String): String {
        return when (provider.lowercase()) {
            "openai" -> {
                val response = gson.fromJson(body, OpenAIResponse::class.java)
                response.choices.firstOrNull()?.message?.content ?: ""
            }
            "gemini" -> {
                val response = gson.fromJson(body, GeminiResponse::class.java)
                response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: ""
            }
            "claude" -> {
                val response = gson.fromJson(body, ClaudeResponse::class.java)
                response.content.firstOrNull()?.text ?: ""
            }
            else -> ""
        }
    }

    // endregion

    // region Embeddings

    private fun getOpenAIEmbedding(config: AIConfig, text: String): List<Float> {
        val url = if (config.baseUrl.endsWith("/")) "${config.baseUrl}embeddings" else "${config.baseUrl}/embeddings"
        val payload = OpenAIEmbeddingRequest(config.model, text)

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${config.apiKey}")
            .post(gson.toJson(payload).toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("OpenAI Embedding Error: $response")
            val body = response.body?.string() ?: throw IOException("Empty body")
            val jsonResponse = gson.fromJson(body, OpenAIEmbeddingResponse::class.java)
            return jsonResponse.data.firstOrNull()?.embedding ?: emptyList()
        }
    }

    private fun getGeminiEmbedding(config: AIConfig, text: String): List<Float> {
        val url = "${config.baseUrl}models/${config.model}:embedContent?key=${config.apiKey}"

        val payload = GeminiEmbeddingRequest(
            content = GeminiContent("user", listOf(GeminiPart(text)))
        )

        val request = Request.Builder()
            .url(url)
            .post(gson.toJson(payload).toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Gemini Embedding Error: $response")
            val body = response.body?.string() ?: throw IOException("Empty body")
            val jsonResponse = gson.fromJson(body, GeminiEmbeddingResponse::class.java)
            return jsonResponse.embedding.values
        }
    }

    // endregion

    companion object {
        fun getDefaultBaseUrl(provider: String): String {
            return when (provider.lowercase()) {
                "openai" -> "https://api.openai.com/v1/"
                "gemini" -> "https://generativelanguage.googleapis.com/v1beta/"
                "claude" -> "https://api.anthropic.com/v1/"
                else -> "https://api.openai.com/v1/"
            }
        }

        fun getDefaultModel(provider: String): String {
            return when (provider.lowercase()) {
                "openai" -> "gpt-3.5-turbo"
                "gemini" -> "gemini-1.5-flash"
                "claude" -> "claude-3-haiku-20240307"
                else -> "gpt-3.5-turbo"
            }
        }
    }
}

// region Data Models

data class AIConfig(
    val provider: String,
    val model: String,
    val apiKey: String,
    val baseUrl: String
)

data class ChatMessage(val role: String, val content: String)

// OpenAI Models
data class OpenAIRequest(val model: String, val messages: List<OpenAIMessage>, val stream: Boolean)
data class OpenAIMessage(val role: String, val content: String)
data class OpenAIResponse(val choices: List<OpenAIChoice>)
data class OpenAIChoice(val message: OpenAIMessage)
data class OpenAIStreamResponse(val choices: List<OpenAIStreamChoice>)
data class OpenAIStreamChoice(val delta: OpenAIStreamDelta)
data class OpenAIStreamDelta(val content: String?)
data class OpenAIEmbeddingRequest(val model: String, val input: String)
data class OpenAIEmbeddingResponse(val data: List<OpenAIEmbeddingData>)
data class OpenAIEmbeddingData(val embedding: List<Float>)

// Gemini Models
data class GeminiRequest(val contents: List<GeminiContent>)
data class GeminiContent(val role: String, val parts: List<GeminiPart>)
data class GeminiPart(val text: String)
data class GeminiResponse(val candidates: List<GeminiCandidate>)
data class GeminiCandidate(val content: GeminiContent)
data class GeminiEmbeddingRequest(val content: GeminiContent)
data class GeminiEmbeddingResponse(val embedding: GeminiEmbeddingValues)
data class GeminiEmbeddingValues(val values: List<Float>)

// Claude Models
data class ClaudeRequest(
    val model: String,
    val messages: List<ClaudeMessage>,
    val max_tokens: Int = 1024,
    val stream: Boolean,
    val system: String? = null
)
data class ClaudeMessage(val role: String, val content: String)
data class ClaudeResponse(val content: List<ClaudeContent>)
data class ClaudeContent(val text: String)
data class ClaudeStreamResponse(val delta: ClaudeStreamDelta?)
data class ClaudeStreamDelta(val text: String?)

// endregion
