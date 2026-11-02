package com.pocketcode.video

import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class SubStyle(val label: String, val about: String) {
    CLASSIC("Классика", "Белый текст с обводкой внизу, по фразам"),
    POP("Динамичные", "Крупно, по 2–3 слова, произносимое слово подсвечено"),
}

data class PhraseWord(val text: String, val start: Double, val end: Double)

/** Одна надпись на экране. Времена уже в шкале готового (смонтированного) видео. */
data class Phrase(val start: Double, val end: Double, val words: List<PhraseWord>) {
    val text: String get() = words.joinToString(" ") { it.text }
}

object Subtitles {
    /**
     * Раскладывает распознанные слова по готовому видео: слова из вырезанных кусков пропадают,
     * остальные сдвигаются. Надпись никогда не пересекает место склейки.
     * [keep] — оставляемые куски исходного видео по порядку (для видео без монтажа это один кусок на всё видео).
     */
    fun build(tr: Transcript, keep: List<TimeRange>, style: SubStyle): List<Phrase> {
        if (keep.isEmpty()) return emptyList()
        val src = if (tr.words.isNotEmpty()) tr.words else estimateWords(tr.segments)
        val prefix = DoubleArray(keep.size)
        var acc = 0.0
        for (i in keep.indices) { prefix[i] = acc; acc += keep[i].length }

        class W(val idx: Int, val w: PhraseWord)
        val mapped = ArrayList<W>()
        for (w in src.sortedBy { it.start }) {
            val mid = (w.start + w.end) / 2
            val idx = keep.indexOfFirst { mid >= it.start && mid <= it.end }
            if (idx < 0) continue
            val r = keep[idx]
            val s = prefix[idx] + (w.start.coerceIn(r.start, r.end) - r.start)
            val e = prefix[idx] + (w.end.coerceIn(r.start, r.end) - r.start)
            mapped += W(idx, PhraseWord(clean(w.text), s, max(e, s + 0.05)))
        }

        val out = ArrayList<Phrase>()
        var cur = ArrayList<PhraseWord>()
        var curIdx = -1
        fun flush() {
            if (cur.isNotEmpty()) out += Phrase(cur.first().start, cur.last().end, cur.toList())
            cur = ArrayList()
        }
        for (m in mapped) {
            if (m.w.text.isEmpty()) continue
            if (cur.isNotEmpty()) {
                val prev = cur.last()
                val chars = cur.sumOf { it.text.length + 1 } + m.w.text.length
                val span = m.w.end - cur.first().start
                val brk = m.idx != curIdx ||
                    m.w.start - prev.end > 0.7 ||
                    when (style) {
                        SubStyle.CLASSIC -> chars > 70 || span > 5.0 || (endsSentence(prev.text) && chars > 24)
                        SubStyle.POP -> cur.size >= 3 || chars > 26 || endsSentence(prev.text)
                    }
                if (brk) flush()
            }
            curIdx = m.idx
            cur += m.w
        }
        flush()

        // Чтобы классические надписи не мигали между близкими фразами, держим фразу до начала следующей
        return out.mapIndexed { i, p ->
            val next = out.getOrNull(i + 1)
            var end = p.end
            if (style == SubStyle.CLASSIC) {
                end = p.end + 0.25
                if (next != null) end = min(end, max(next.start, p.end))
            } else {
                end = p.end + 0.05
                if (next != null) end = min(end, max(next.start, p.end))
            }
            Phrase(p.start, max(end, p.start + 0.3), p.words)
        }
    }

    private fun clean(s: String) = s.replace('\n', ' ').replace("\\", "").replace("{", "(").replace("}", ")").trim()

    private fun endsSentence(s: String) = s.trimEnd('"', '»', ')').lastOrNull() in setOf('.', '!', '?', '…')

    /** Если сервис не вернул слова: делим время фразы между словами пропорционально их длине. */
    private fun estimateWords(segs: List<Segment>): List<Word> {
        val out = ArrayList<Word>()
        for (s in segs) {
            val toks = s.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (toks.isEmpty()) continue
            val total = toks.sumOf { it.length + 1 }.toDouble()
            var t = s.start
            for (tok in toks) {
                val d = (s.end - s.start) * (tok.length + 1) / total
                out += Word(tok, t, t + d)
                t += d
            }
        }
        return out
    }

    // ---------- форматы ----------

    private fun assTime(t: Double): String {
        val cs = (max(t, 0.0) * 100).roundToInt()
        return String.format(Locale.US, "%d:%02d:%02d.%02d", cs / 360000, cs / 6000 % 60, cs / 100 % 60, cs % 100)
    }

    private fun srtTime(t: Double): String {
        val ms = (max(t, 0.0) * 1000).roundToInt()
        return String.format(Locale.US, "%02d:%02d:%02d,%03d", ms / 3600000, ms / 60000 % 60, ms / 1000 % 60, ms % 1000)
    }

    /** SRT для плееров и соцсетей. */
    fun srt(phrases: List<Phrase>): String {
        val sb = StringBuilder()
        for ((i, p) in phrases.withIndex()) {
            sb.append(i + 1).append('\n')
            sb.append(srtTime(p.start)).append(" --> ").append(srtTime(p.end)).append('\n')
            sb.append(wrap(p.text)).append("\n\n")
        }
        return sb.toString()
    }

    /** Одна строка не длиннее ~34 знаков, иначе ломаем пополам по ближайшему к середине пробелу. */
    private fun wrap(text: String): String {
        if (text.length <= 34) return text
        val mid = text.length / 2
        var best = -1
        for (i in text.indices) if (text[i] == ' ' && (best < 0 || kotlin.math.abs(i - mid) < kotlin.math.abs(best - mid))) best = i
        return if (best < 0) text else text.substring(0, best) + "\n" + text.substring(best + 1)
    }

    /**
     * ASS для вшивания через ffmpeg. [w] и [h] — размер готового кадра, поэтому масштабирования шрифта нет.
     * [shift] вычитается из времён (для частей, которые рендерятся отдельно).
     */
    fun ass(phrases: List<Phrase>, style: SubStyle, w: Int, h: Int, shift: Double): String {
        val base = min(w, h).coerceAtLeast(240)
        val portrait = h > w
        val pop = style == SubStyle.POP
        val fs = (base * if (pop) 0.08 else 0.052).roundToInt()
        val outline = max(2.0, fs * if (pop) 0.09 else 0.07)
        val shadow = if (pop) 2 else 1
        val mx = (w * 0.06).roundToInt()
        val mv = (h * if (pop) (if (portrait) 0.20 else 0.10) else (if (portrait) 0.10 else 0.07)).roundToInt()
        // Цвета в формате &HAABBGGRR: у «динамичных» произнесённое слово жёлтое, остальное белое
        val primary = if (pop) "&H0000F0FF" else "&H00FFFFFF"
        val secondary = "&H00FFFFFF"
        val sb = StringBuilder()
        sb.append("[Script Info]\nScriptType: v4.00+\nPlayResX: $w\nPlayResY: $h\nWrapStyle: 2\nScaledBorderAndShadow: yes\n\n")
        sb.append("[V4+ Styles]\n")
        sb.append("Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding\n")
        sb.append("Style: Default,DejaVu Sans,$fs,$primary,$secondary,&H00000000,&H64000000,-1,0,0,0,100,100,0,0,1,${String.format(Locale.US, "%.1f", outline)},$shadow,2,$mx,$mx,$mv,1\n\n")
        sb.append("[Events]\nFormat: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text\n")
        for (p in phrases) {
            val s = p.start - shift
            val e = p.end - shift
            if (e <= 0.0) continue
            val text = if (pop) {
                val ws = p.words
                ws.withIndex().joinToString(" ") { (i, w0) ->
                    val until = if (i + 1 < ws.size) ws[i + 1].start else p.end
                    val k = ((until - w0.start) * 100).roundToInt().coerceAtLeast(1)
                    "{\\k$k}" + w0.text.trimEnd(',', '.', ';', ':').uppercase()
                }
            } else wrap(p.text).replace("\n", "\\N")
            sb.append("Dialogue: 0,${assTime(s)},${assTime(e)},Default,,0,0,0,,").append(text).append('\n')
        }
        return sb.toString()
    }
}
