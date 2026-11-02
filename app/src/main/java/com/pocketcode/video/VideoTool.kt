package com.pocketcode.video

import com.pocketcode.tools.Prop
import com.pocketcode.tools.Tool
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
