package com.pocketcode.video

import androidx.compose.runtime.mutableStateListOf
import kotlin.math.max
import kotlin.math.min

/** Склеивает пересекающиеся и соприкасающиеся куски, возвращает по порядку. */
fun mergeCuts(cuts: List<TimeRange>): List<TimeRange> {
    val out = ArrayList<TimeRange>()
    for (c in cuts.filter { it.end > it.start }.sortedBy { it.start }) {
        val last = out.lastOrNull()
        if (last != null && c.start <= last.end + 0.001) out[out.lastIndex] = TimeRange(last.start, max(last.end, c.end))
        else out += c
    }
    return out
}

/** Что останется от видео длиной [dur], если вырезать [cuts]. Совсем короткие обрывки (меньше 0,05 с) отбрасываются. */
fun rangesKeep(cuts: List<TimeRange>, dur: Double): List<TimeRange> {
    val merged = mergeCuts(cuts.map { TimeRange(max(it.start, 0.0), min(it.end, dur)) })
    val keep = ArrayList<TimeRange>()
    var cur = 0.0
    for (c in merged) {
        if (c.start - cur >= 0.05) keep += TimeRange(cur, c.start)
        cur = max(cur, c.end)
    }
    if (dur - cur >= 0.05) keep += TimeRange(cur, dur)
    return keep
}

/**
 * Монтаж по тексту: слова распознанной речи можно вычёркивать, вычеркнутое вырезается из видео.
 * [flags] хранит для каждого слова признак «вычеркнуто»; это состояние Compose, поэтому экран обновляется сам.
 */
class TextEdit(val tr: Transcript, val duration: Double) {
    val words: List<Word> = Subtitles.wordsOf(tr)
    val flags = mutableStateListOf<Boolean>().also { l -> repeat(words.size) { l.add(false) } }

    /** Абзацы: подряд идущие слова одной распознанной фразы. */
    val paras: List<IntRange> = run {
        val out = ArrayList<IntRange>()
        val segs = tr.segments
        var j = 0
        var from = 0
        var curSeg = -1
        for (i in words.indices) {
            val mid = (words[i].start + words[i].end) / 2
            while (j < segs.size - 1 && mid > segs[j].end + 0.05) j++
            if (curSeg == -1) curSeg = j
            val tooLong = i - from >= 45
            if (j != curSeg || tooLong) {
                out += from until i
                from = i
                curSeg = j
            }
        }
        if (words.isNotEmpty()) out += from until words.size
        out
    }

    val deletedCount: Int get() = flags.count { it }

    fun toggle(i: Int) { flags[i] = !flags[i] }

    fun setRange(r: IntRange, value: Boolean) { for (i in r) flags[i] = value }

    fun reset() { for (i in flags.indices) flags[i] = false }

    /** Отмечает слова-паразиты. Возвращает, сколько слов отмечено новых. */
    fun markFillers(): Int {
        var n = 0
        for (i in words.indices) if (!flags[i] && norm(words[i].text) in FILLERS) { flags[i] = true; n++ }
        return n
    }

    /** Отмечает первое из двух одинаковых слов подряд («я я пошёл»). */
    fun markRepeats(): Int {
        var n = 0
        for (i in 0 until words.size - 1) {
            val a = norm(words[i].text)
            if (a.isEmpty() || flags[i]) continue
            if (a == norm(words[i + 1].text) && words[i + 1].start - words[i].end < 1.0) { flags[i] = true; n++ }
        }
        return n
    }

    /**
     * Куски исходного видео, которые вырезаются из-за вычеркнутых слов. Подряд вычеркнутые слова дают один кусок.
     * Края подрезаются так, чтобы не задеть соседние невычеркнутые слова.
     */
    fun cuts(): List<TimeRange> {
        val n = words.size
        val out = ArrayList<TimeRange>()
        var i = 0
        while (i < n) {
            if (!flags[i]) { i++; continue }
            var j = i
            while (j + 1 < n && flags[j + 1]) j++
            val prevEnd = if (i > 0) words[i - 1].end else 0.0
            val nextStart = if (j + 1 < n) words[j + 1].start else duration
            val a = max(words[i].start - 0.03, prevEnd).coerceAtLeast(0.0)
            val b = min(words[j].end + 0.03, nextStart).coerceAtMost(duration)
            if (b - a > 0.05) out += TimeRange(a, b)
            i = j + 1
        }
        return out
    }

    /** Номер слова, которое звучит в момент [t] секунд, или -1. */
    fun wordAt(t: Double): Int {
        var lo = 0
        var hi = words.size - 1
        var best = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (words[mid].start <= t) { best = mid; lo = mid + 1 } else hi = mid - 1
        }
        return if (best >= 0 && t <= words[best].end + 0.35) best else -1
    }

    private fun norm(s: String) = s.lowercase().filter { it.isLetter() }

    private companion object {
        val FILLERS = setOf(
            "э", "эм", "ээ", "эээ", "эмм", "мм", "ммм", "гм", "хм", "аа", "ааа", "ну", "типа", "короче",
            "um", "uh", "uhm", "er", "erm", "hmm", "mm",
        )
    }
}
