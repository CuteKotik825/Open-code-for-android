package com.pocketcode.video

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pocketcode.ui.AppIcons
import kotlinx.coroutines.launch

/** Режим «Видео»: выбрать ролик, вырезать паузы одной кнопкой, сохранить или отправить. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoScreen(v: VideoPipeline, onBack: () -> Unit, onOpenLinux: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> if (uri != null) v.pick(uri) }

    // Чувствительность: от -50 дБ (режет только явную тишину) до -25 дБ (режет и тихую речь)
    var noise by remember { mutableFloatStateOf(-35f) }
    var minSilence by remember { mutableStateOf(0.6) }
    var quality by remember { mutableStateOf(VideoQuality.BALANCED) }
    var small by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    val info = v.info
    val a = v.analysis
    val opt = VideoOptions(quality, if (small) 720 else 0)

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Видео", maxLines = 1) },
            navigationIcon = { IconButton(onClick = onBack) { androidx.compose.material3.Icon(AppIcons.Back, contentDescription = "Назад") } },
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!v.ready) {
                SoftCard {
                    Text("Нужна среда Linux", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Монтаж идёт через ffmpeg, а он работает внутри мини-Linux приложения. Установите и включите среду один раз, ffmpeg поставится сам при первом монтаже.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(onClick = onOpenLinux) { Text("Открыть «Среда Linux»") }
                }
            }

            // ---------- выбор видео ----------
            SoftCard {
                Text(if (info == null) "1. Выберите видео" else "Видео", style = MaterialTheme.typography.titleMedium)
                if (info != null) {
                    Text(java.io.File(info.rel).name, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        buildString {
                            append(fmtTime(info.duration))
                            if (info.width > 0) append(" · ${info.width}×${info.height}")
                            append(" · ${fmtSize(info.bytes)}")
                            if (!info.hasAudio) append(" · без звука")
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "Из галереи или файлов. Копия ляжет в папку video/ проекта, оригинал не изменится.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(enabled = v.ready && !v.busy, onClick = { note = null; picker.launch("video/*") }) {
                        Text(if (info == null) "Выбрать видео" else "Другое видео")
                    }
                }
                val recent = remember(v.stage, v.resultRel, info?.rel) { v.recent() }.filter { it != info?.rel }
                if (recent.isNotEmpty() && v.ready) {
                    Text("Уже в проекте:", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        recent.forEach { rel ->
                            androidx.compose.material3.AssistChip(
                                enabled = !v.busy,
                                onClick = { note = null; v.select(rel) },
                                label = { Text(java.io.File(rel).name.take(26)) },
                            )
                        }
                    }
                }
            }

            // ---------- настройки и действия ----------
            if (info != null && v.ready) {
                SoftCard {
                    Text("2. Вырезать паузы", style = MaterialTheme.typography.titleMedium)
                    Text("Как сильно резать: ${sensLabel(noise.toInt())} (${noise.toInt()} дБ)", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = noise, onValueChange = { noise = it.toInt().toFloat() }, valueRange = -50f..-25f,
                        enabled = !v.busy,
                    )
                    Text(
                        "Если вырезается лишнее (тихая речь), сдвиньте влево. Если паузы остаются (шумная комната), вправо.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text("Какие паузы считать:", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0.4 to "от 0,4 с", 0.6 to "от 0,6 с", 1.0 to "от 1 с", 1.5 to "от 1,5 с").forEach { (value, label) ->
                            FilterChip(selected = minSilence == value, enabled = !v.busy, onClick = { minSilence = value }, label = { Text(label) })
                        }
                    }
                    Text("Качество и скорость:", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VideoQuality.values().forEach { q ->
                            FilterChip(selected = quality == q, enabled = !v.busy, onClick = { quality = q }, label = { Text(q.label) })
                        }
                        FilterChip(selected = small, enabled = !v.busy, onClick = { small = !small }, label = { Text("Сжать до 720p") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            enabled = !v.busy,
                            onClick = { note = null; v.removePauses(info.rel, minSilence, noise.toInt(), opt, true) },
                        ) { Text("Вырезать паузы") }
                        OutlinedButton(
                            enabled = !v.busy,
                            onClick = { note = null; v.removePauses(info.rel, minSilence, noise.toInt(), opt, false) },
                        ) { Text("Только показать") }
                    }
                }
            }

            // ---------- ход работы ----------
            if (v.busy) {
                SoftCard {
                    Text(
                        when (v.stage) {
                            VideoStage.IMPORT -> "Копирование"
                            VideoStage.TOOLS -> "Подготовка"
                            VideoStage.ANALYZE -> "Анализ звука"
                            else -> "Монтаж"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (v.progress > 0f) LinearProgressIndicator(progress = { v.progress }, modifier = Modifier.fillMaxWidth())
                    else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(v.message, style = MaterialTheme.typography.bodyMedium)
                    if (v.speed.isNotBlank()) Text("Скорость ${v.speed} от реального времени", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "Можно свернуть приложение: работа продолжится, придёт уведомление.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { v.cancel() }) { Text("Остановить") }
                }
            }

            v.error?.let { e ->
                SoftCard(error = true) {
                    Text("Не получилось", style = MaterialTheme.typography.titleMedium)
                    Text(e, style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { v.dismiss() }) { Text("Закрыть") }
                }
            }

            // ---------- что вырежем ----------
            if (a != null && info != null && a.info.rel == info.rel && !v.busy) {
                SoftCard {
                    Text("Что вырезается", style = MaterialTheme.typography.titleMedium)
                    Timeline(a)
                    Text(
                        if (a.cuts.isEmpty()) "Пауз не найдено. Сдвиньте ползунок вправо или выберите более короткие паузы."
                        else "Пауз: ${a.cuts.size}. Уйдёт ${fmtTime(a.cutSeconds)}, останется ${fmtTime(a.resultSeconds)} из ${fmtTime(a.info.duration)}.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (a.cuts.isNotEmpty() && v.resultRel == null) {
                        Text("Красное: вырезается. Серое: остаётся.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { note = null; v.removePauses(info.rel, a.minSilence, a.noiseDb, opt, true) }) { Text("Смонтировать") }
                    }
                }
            }

            // ---------- результат ----------
            val res = v.resultRel
            if (res != null && v.stage == VideoStage.DONE) {
                SoftCard {
                    Text("Готово", style = MaterialTheme.typography.titleMedium)
                    Text(java.io.File(res).name, style = MaterialTheme.typography.bodyLarge)
                    Text(v.resultNote, style = MaterialTheme.typography.bodyMedium)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch {
                                runCatching {
                                    val uri = v.shareUri(res)
                                    val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    ctx.startActivity(Intent.createChooser(view, "Открыть в…").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }.onFailure { note = "Не удалось открыть: ${it.message}" }
                            }
                        }) { Text("Смотреть") }
                        OutlinedButton(onClick = {
                            scope.launch {
                                runCatching { v.saveToGallery(res) }
                                    .onSuccess { note = "Сохранено в галерею: $it" }
                                    .onFailure { note = "Не удалось сохранить: ${it.message}" }
                            }
                        }) { Text("В галерею") }
                        OutlinedButton(onClick = {
                            scope.launch {
                                runCatching {
                                    val uri = v.shareUri(res)
                                    val send = Intent(Intent.ACTION_SEND).setType("video/mp4").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    ctx.startActivity(Intent.createChooser(send, "Отправить видео").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }.onFailure { note = "Не удалось отправить: ${it.message}" }
                            }
                        }) { Text("Поделиться") }
                    }
                    Text("Файл лежит в проекте: $res", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }

            Text(
                "Агент в чате умеет то же самое: напишите, например, «вырежи паузы из video/clip.mp4». Субтитры и монтаж по тексту добавим следующими версиями.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

private fun sensLabel(db: Int) = when {
    db <= -43 -> "осторожно"
    db <= -32 -> "обычно"
    else -> "сильно"
}

@Composable
private fun SoftCard(error: Boolean = false, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

/** Полоса по длине видео: серое остаётся, красное вырезается. */
@Composable
private fun Timeline(a: SilenceAnalysis) {
    val keep = MaterialTheme.colorScheme.outline
    val cut = Color(0xFFE5484D)
    Canvas(Modifier.fillMaxWidth().height(28.dp)) {
        val total = a.info.duration.coerceAtLeast(0.1)
        drawRect(keep, Offset.Zero, size)
        for (c in a.cuts) {
            val x0 = (c.start / total * size.width).toFloat()
            val w = (c.length / total * size.width).toFloat().coerceAtLeast(2f)
            drawRect(cut, Offset(x0, 0f), Size(w, size.height))
        }
    }
}
