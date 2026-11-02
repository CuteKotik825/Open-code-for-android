package com.pocketcode.video

import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

const val DEFAULT_STT_URL = "https://api.groq.com/openai/v1"
const val DEFAULT_STT_MODEL = "whisper-large-v3-turbo"

/** Куда отправлять звук на распознавание: любой OpenAI-совместимый адрес с /audio/transcriptions. */
data class SpeechConfig(val baseUrl: String, val model: String, val key: String)

@Serializable
data class Word(val text: String, val start: Double, val end: Double)

@Serializable
data class Segment(val start: Double, val end: Double, val text: String)

/** Распознанная речь по времени исходного видео. */
@Serializable
data class Transcript(val language: String, val segments: List<Segment>, val words: List<Word>) {
    /** Текст с отметками времени: по строке на фразу. */
    fun timedText(): String = segments.joinToString("\n") { "[${fmtTime(it.start)}] ${it.text.trim()}" }

    fun plainText(): String = segments.joinToString(" ") { it.text.trim() }.replace(Regex("\\s+"), " ").trim()

    fun toJson(): String = Json.encodeToString(this)

    companion object {
        fun fromJson(s: String): Transcript = Json { ignoreUnknownKeys = true }.decodeFromString(serializer(), s)
    }
}

fun sameHost(a: String, b: String): Boolean = runCatching { URI(a.trim()).host.equals(URI(b.trim()).host, ignoreCase = true) }.getOrDefault(false)

/** Строка вида http://host:port или socks5://host:port (как в настройках сети приложения) → Proxy. */
fun parseProxy(raw: String): Proxy? {
    val v = raw.trim()
    if (v.isEmpty()) return null
    return runCatching {
        val u = URI(if ("://" in v) v else "http://$v")
        val port = if (u.port > 0) u.port else if (u.scheme.startsWith("socks")) 1080 else 8080
        Proxy(if (u.scheme.startsWith("socks")) Proxy.Type.SOCKS else Proxy.Type.HTTP, InetSocketAddress.createUnresolved(u.host, port))
    }.getOrNull()
}

/** Клиент распознавания речи (формат OpenAI: POST /audio/transcriptions, multipart). */
class SpeechClient(private val cfg: SpeechConfig, proxy: String) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(180, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .apply { parseProxy(proxy)?.let { proxy(it) } }
        .build()

    /** Не все совместимые серверы понимают запрос слов с отметками времени: после отказа работаем только по фразам. */
    private var withWords = true

    private fun url(path: String) = cfg.baseUrl.trim().trimEnd('/') + path

    /** Быстрая проверка адреса и ключа: список моделей. */
    suspend fun ping(): String {
        val req = Request.Builder().url(url("/models")).header("Authorization", "Bearer ${cfg.key}").get().build()
        val (code, body) = call(req)
        return when {
            code in 200..299 -> "Ключ принят"
            code == 401 || code == 403 -> "Ключ не принят (HTTP $code)"
            code == 404 -> "Адрес отвечает, но списка моделей нет (HTTP 404). Распознавание всё равно может работать."
            else -> "Ответ HTTP $code: ${body.take(200)}"
        }
    }

    /** Распознаёт один файл. Времена сдвигаются на [offset] секунд (номер куска × длина куска). */
    suspend fun transcribe(file: File, language: String?, offset: Double, chunkDuration: Double): Transcript {
        var attempt = 0
        while (true) {
            val req = Request.Builder().url(url("/audio/transcriptions"))
                .header("Authorization", "Bearer ${cfg.key}")
                .post(form(file, language, withWords))
                .build()
            val (code, body) = call(req)
            when {
                code in 200..299 -> return parse(body, offset, chunkDuration)
                code == 429 && attempt < 3 -> {
                    attempt++
                    delay(retryAfterMs(body, attempt))
                }
                code == 400 && withWords -> withWords = false
                else -> throw IOException(explain(code, body))
            }
        }
    }

    private fun retryAfterMs(body: String, attempt: Int): Long {
        // Groq пишет «Please try again in 12.5s»
        val m = Regex("try again in\\s+(?:(\\d+)m)?\\s*(\\d+(?:\\.\\d+)?)s", RegexOption.IGNORE_CASE).find(body)
        val sec = m?.let { (it.groupValues[1].toDoubleOrNull() ?: 0.0) * 60 + (it.groupValues[2].toDoubleOrNull() ?: 0.0) }
        return ((sec ?: (15.0 * attempt)) * 1000).toLong().coerceIn(3_000, 120_000) + 500
    }

    private fun explain(code: Int, body: String): String {
        val tail = body.take(300)
        return when (code) {
            401, 403 -> "Ключ распознавания не принят (HTTP $code). Проверьте ключ и адрес в настройках на этом экране."
            404 -> "Адрес не найден (HTTP 404): проверьте адрес (обычно заканчивается на /v1) и название модели. $tail"
            413 -> "Кусок звука слишком большой для сервера (HTTP 413). $tail"
            429 -> "Лимит запросов сервиса распознавания исчерпан (HTTP 429). Подождите или смените ключ. $tail"
            else -> "Сервис распознавания ответил HTTP $code: $tail"
        }
    }

    private fun form(file: File, language: String?, words: Boolean): MultipartBody {
        val b = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody("audio/mpeg".toMediaType()))
            .addFormDataPart("model", cfg.model)
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("temperature", "0")
        if (words) {
            b.addFormDataPart("timestamp_granularities[]", "word")
            b.addFormDataPart("timestamp_granularities[]", "segment")
        }
        if (!language.isNullOrBlank()) b.addFormDataPart("language", language)
        return b.build()
    }

    private fun parse(body: String, offset: Double, chunkDuration: Double): Transcript {
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: throw IOException("Сервис вернул не JSON: ${body.take(200)}")
        fun JsonObject.num(k: String) = (this[k] as? JsonPrimitive)?.doubleOrNull
        fun JsonObject.txt(k: String) = (this[k] as? JsonPrimitive)?.contentOrNull

        val segs = ArrayList<Segment>()
        val silent = ArrayList<Pair<Double, Double>>()
        (root["segments"] as? JsonArray)?.forEach { el ->
            val o = el.jsonObject
            val s = o.num("start") ?: return@forEach
            val e = o.num("end") ?: return@forEach
            val t = o.txt("text")?.trim().orEmpty()
            // Whisper любит «досочинять» текст на тишине и музыке: такие куски отбрасываем
            val ns = o.num("no_speech_prob") ?: 0.0
            val lp = o.num("avg_logprob") ?: 0.0
            if (t.isEmpty() || (ns > 0.75 && lp < -0.8)) {
                silent += s to e
            } else segs += Segment(s + offset, e + offset, t)
        }
        if (segs.isEmpty() && silent.isEmpty()) {
            val t = root.txt("text")?.trim().orEmpty()
            if (t.isNotEmpty()) segs += Segment(offset, offset + chunkDuration, t)
        }
        val words = ArrayList<Word>()
        (root["words"] as? JsonArray)?.forEach { el ->
            val o = el.jsonObject
            val t = (o.txt("word") ?: o.txt("text"))?.trim().orEmpty()
            val s = o.num("start") ?: return@forEach
            val e = o.num("end") ?: return@forEach
            val mid = (s + e) / 2
            if (t.isNotEmpty() && silent.none { mid >= it.first && mid <= it.second }) words += Word(t, s + offset, e + offset)
        }
        return Transcript(root.txt("language").orEmpty(), segs, words)
    }

    private suspend fun call(req: Request): Pair<Int, String> = suspendCancellableCoroutine { cont ->
        val c = client.newCall(req)
        cont.invokeOnCancellation { c.cancel() }
        c.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) {
                    cont.resumeWithException(IOException("Нет связи с сервисом распознавания: ${e.message ?: e.javaClass.simpleName}. Проверьте интернет, VPN или прокси в настройках."))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { r ->
                    val text = try { r.body?.string().orEmpty() } catch (e: IOException) { "" }
                    if (cont.isActive) cont.resume(r.code to text)
                }
            }
        })
    }
}

internal fun mergeTranscripts(parts: List<Transcript>): Transcript =
    Transcript(
        parts.firstOrNull { it.language.isNotBlank() }?.language.orEmpty(),
        parts.flatMap { it.segments }.sortedBy { it.start },
        parts.flatMap { it.words }.sortedBy { it.start },
    )
