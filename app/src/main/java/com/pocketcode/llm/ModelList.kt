package com.pocketcode.llm

import com.pocketcode.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Список моделей, который отдаёт сам сервис по адресу из профиля (GET …/models). */
object ModelList {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** Адрес списка моделей для базового URL профиля. */
    fun url(provider: Provider, baseUrl: String): String {
        val base = baseUrl.trim().trimEnd('/')
        return when {
            base.endsWith("/models") -> base
            provider == Provider.ANTHROPIC && !base.endsWith("/v1") -> "$base/v1/models"
            else -> "$base/models"
        }
    }

    /** Достаёт названия моделей из ответа сервиса: {"data":[{"id":..}]} или {"models":[{"name":..}]}; результат без повторов и по алфавиту. */
    fun parse(body: String): List<String> {
        val root = try {
            Json.parseToJsonElement(body)
        } catch (e: Exception) {
            return emptyList()
        }
        val arr: JsonArray = when (root) {
            is JsonArray -> root
            is JsonObject -> (root["data"] as? JsonArray) ?: (root["models"] as? JsonArray) ?: return emptyList()
            else -> return emptyList()
        }
        return arr.mapNotNull { el ->
            when (el) {
                is JsonPrimitive -> el.contentOrNull
                is JsonObject -> ((el["id"] as? JsonPrimitive) ?: (el["name"] as? JsonPrimitive))?.contentOrNull
                else -> null
            }?.removePrefix("models/")?.trim()?.takeIf { it.isNotEmpty() }
        }.distinct().sorted()
    }

    suspend fun fetch(provider: Provider, baseUrl: String, apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url(provider, baseUrl)).apply {
            if (apiKey.isNotBlank()) {
                if (provider == Provider.ANTHROPIC) {
                    header("x-api-key", apiKey)
                    header("anthropic-version", "2023-06-01")
                } else {
                    header("Authorization", "Bearer $apiKey")
                }
            }
        }.build()
        client.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}: ${text.take(200)}")
            parse(text)
        }
    }
}
