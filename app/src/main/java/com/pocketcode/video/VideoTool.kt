package com.pocketcode.video

import com.pocketcode.tools.Prop
import com.pocketcode.tools.Tool
import com.pocketcode.tools.bool
import com.pocketcode.tools.int
import com.pocketcode.tools.need
import com.pocketcode.tools.objSchema
import com.pocketcode.tools.str
import kotlinx.serialization.json.JsonObject

/**
 * Монтаж видео для агента: те же операции, что и на экране «Видео».
 * Работает с файлами в папке проекта, ffmpeg ставится сам.
 */
class VideoTool(private val pipeline: VideoPipeline) : Tool {
    override val name = "video_cut_pauses"
    override val description =
        "Найти паузы в видео и вырезать их (монтаж). Файл берётся из папки проекта (например video/clip.mp4). " +
            "Сначала можно вызвать с apply=false, чтобы только узнать, сколько получится. Результат сохраняется рядом с исходником как *_cut.mp4, оригинал не меняется. " +
            "Нужна включённая среда Linux, ffmpeg ставится автоматически."
    override val schema = objSchema(
        Prop("path", "string", "Путь к видео относительно папки проекта"),
        Prop("min_silence", "number", "Минимальная длина паузы в секундах (по умолчанию 0.6)", required = false),
        Prop("noise_db", "integer", "Порог тишины в дБ, от -60 до -20 (по умолчанию -35; ближе к нулю режет больше)", required = false),
        Prop("apply", "boolean", "true — смонтировать файл, false — только показать, что будет вырезано (по умолчанию true)", required = false),
    )

    // Создаёт новый файл в проекте и нагружает процессор: просим подтверждение
    override fun needsApproval(input: JsonObject) = input["apply"]?.toString() != "false"

    override fun describe(input: JsonObject) = "вырезать паузы: ${input.str("path") ?: ""}"

    override suspend fun run(input: JsonObject): String {
        val path = input.need("path").trim().trimStart('/')
        require(!path.contains("..")) { "Путь не должен содержать «..»" }
        val minSilence = (input["min_silence"]?.toString()?.trim('"')?.toDoubleOrNull() ?: 0.6).coerceIn(0.2, 10.0)
        val noise = (input.int("noise_db") ?: -35).coerceIn(-60, -20)
        val apply = input["apply"]?.toString() != "false"
        return pipeline.cutSilence(path, minSilence, noise, VideoOptions(), apply)
    }
}

private fun JsonObject.cleanPath(): String {
    val p = need("path").trim().trimStart('/')
    require(!p.contains("..")) { "Путь не должен содержать «..»" }
    return p
}

private fun JsonObject.number(key: String): Double? = this[key]?.toString()?.trim('"')?.toDoubleOrNull()

/** Распознать речь в видео и вернуть текст с отметками времени (ключ распознавания задаёт пользователь на экране «Видео»). */
class VideoTranscribeTool(private val pipeline: VideoPipeline) : Tool {
    override val name = "video_transcribe"
    override val description =
        "Распознать речь в видео из папки проекта и вернуть текст с отметками времени. Заодно сохраняет рядом файлы .txt и .srt. " +
            "Нужны среда Linux и ключ распознавания речи в разделе «Видео» (например бесплатный Groq)."
    override val schema = objSchema(
        Prop("path", "string", "Путь к видео относительно папки проекта"),
        Prop("language", "string", "Код языка речи (ru, en…); пусто — определить автоматически", required = false),
    )

    override fun needsApproval(input: JsonObject) = false
    override fun describe(input: JsonObject) = "распознать речь: ${input.str("path") ?: ""}"

    override suspend fun run(input: JsonObject): String {
        val path = input.cleanPath()
        val text = pipeline.transcribe(path, input.str("language")?.trim().orEmpty())
        val saved = runCatching { pipeline.saveTranscriptFiles(path) }.getOrNull()
        val body = if (text.length > 20_000) text.take(20_000) + "\n…(текст обрезан, полный лежит в файлах)" else text
        return (if (saved != null) "Сохранено: $saved\n\n" else "") + body
    }
}

/** Вшить субтитры (и при желании вырезать паузы) одним проходом. */
class VideoSubtitlesTool(private val pipeline: VideoPipeline) : Tool {
    override val name = "video_subtitles"
    override val description =
        "Добавить субтитры в видео из папки проекта: распознаёт речь и вшивает текст. Можно сразу вырезать паузы (cut_pauses=true). " +
            "Результат сохраняется рядом как *_sub.mp4 (или *_cut_sub.mp4) и файл .srt, оригинал не меняется. Нужны среда Linux и ключ распознавания в разделе «Видео»."
    override val schema = objSchema(
        Prop("path", "string", "Путь к видео относительно папки проекта"),
        Prop("style", "string", "classic — белый текст внизу фразами; pop — крупно по 2–3 слова с подсветкой слова (по умолчанию classic)", required = false),
        Prop("language", "string", "Код языка речи (ru, en…); пусто — автоматически", required = false),
        Prop("cut_pauses", "boolean", "Заодно вырезать паузы (по умолчанию false)", required = false),
        Prop("min_silence", "number", "Минимальная длина паузы в секундах при cut_pauses (по умолчанию 0.6)", required = false),
        Prop("noise_db", "integer", "Порог тишины в дБ, от -60 до -20 (по умолчанию -35)", required = false),
    )

    override fun needsApproval(input: JsonObject) = true
    override fun describe(input: JsonObject) = "субтитры: ${input.str("path") ?: ""}"

    override suspend fun run(input: JsonObject): String {
        val style = if (input.str("style")?.trim()?.lowercase() == "pop") SubStyle.POP else SubStyle.CLASSIC
        return pipeline.produce(
            input.cleanPath(),
            input.bool("cut_pauses") == true,
            (input.number("min_silence") ?: 0.6).coerceIn(0.2, 10.0),
            (input.int("noise_db") ?: -35).coerceIn(-60, -20),
            VideoOptions(),
            style,
            input.str("language")?.trim().orEmpty(),
        )
    }
}
