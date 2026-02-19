package com.aiavatar.app.network

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class ChatMessage(val role: String, val content: String)

class OllamaApiClient(private val baseUrl: String = "http://192.168.0.177:11434") {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    suspend fun checkConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$baseUrl/api/tags").get().build()
            val resp = client.newCall(req).execute()
            val ok = resp.isSuccessful
            resp.body?.close()
            ok
        } catch (e: Exception) {
            android.util.Log.e("Ollama", "checkConnection failed: ${e.message}")
            false
        }
    }

    suspend fun fetchModels(): List<String> = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$baseUrl/api/tags").get().build()
            val resp = client.newCall(req).execute()
            if (!resp.isSuccessful) { resp.body?.close(); return@withContext emptyList() }
            val body = resp.body?.string() ?: return@withContext emptyList()
            android.util.Log.d("Ollama", "fetchModels response: $body")
            val json = gson.fromJson(body, Map::class.java)
            @Suppress("UNCHECKED_CAST")
            val models = json["models"] as? List<Map<String, Any>> ?: emptyList()
            models.mapNotNull { it["name"] as? String }
        } catch (e: Exception) {
            android.util.Log.e("Ollama", "fetchModels failed: ${e.message}")
            emptyList()
        }
    }

    fun streamChat(model: String, messages: List<ChatMessage>): Flow<String> = flow {
        val body = gson.toJson(mapOf(
            "model" to model,
            "messages" to messages.map { mapOf("role" to it.role, "content" to it.content) },
            "stream" to true
        ))
        android.util.Log.d("Ollama", "streamChat → $baseUrl/api/chat  model=$model")

        val req = Request.Builder()
            .url("$baseUrl/api/chat")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val resp = client.newCall(req).execute()
        if (!resp.isSuccessful) {
            val err = resp.body?.string() ?: ""
            resp.body?.close()
            throw Exception("HTTP ${resp.code}: $err")
        }

        val reader = BufferedReader(InputStreamReader(resp.body!!.byteStream()))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            val cur = line ?: continue
            if (cur.isBlank()) continue
            try {
                val json = gson.fromJson(cur, Map::class.java)
                val msg = json["message"] as? Map<*, *>
                val content = msg?.get("content") as? String
                if (!content.isNullOrEmpty()) emit(content)
                if (json["done"] as? Boolean == true) break
            } catch (_: Exception) {}
        }
    }.flowOn(Dispatchers.IO)
}
