package com.pocketcode.video

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Как соединяются куски в готовом видео. [xfade] — имя перехода ffmpeg (null — без наплыва). */
enum class Transition(val label: String, val about: String, val xfade: String?, val seconds: Double, val zoom: Boolean = false) {
    CUT("Резкие", "Обычная склейка без эффектов", null, 0.0),
    ZOOM("Зум", "Резкая склейка, каждый второй кусок слегка приближен: как в роликах блогеров, скрывает «прыжки»", null, 0.0, true),
    FADE("Плавные", "Короткий наплыв (0,15 с), картинка и звук перетекают", "fade", 0.15),
    FLASH("Вспышка", "Быстрая белая вспышка между кусками", "fadewhite", 0.2),
    SLIDE("Сдвиг", "Следующий кусок вытесняет предыдущий вбок", "slideleft", 0.2),
    BLUR("Размытие", "Горизонтальное размытие на стыке", "hblur", 0.2),
    CIRCLE("Круг", "Следующий кадр раскрывается кругом", "circleopen", 0.25),
    MIX("Микс", "Разные переходы по очереди", "mix", 0.2),
}

fun transitionByName(s: String?): Transition = when (s?.trim()?.lowercase()) {
    "cut", "hard", "резкие", "нет" -> Transition.CUT
    "fade", "плавные", "dissolve" -> Transition.FADE
    "flash", "вспышка" -> Transition.FLASH
    "slide", "сдвиг" -> Transition.SLIDE
    "blur", "размытие" -> Transition.BLUR
    "circle", "круг" -> Transition.CIRCLE
    "mix", "микс" -> Transition.MIX
    else -> Transition.ZOOM
}

private val MIX_LIST = listOf("fade", "slideleft", "fadewhite", "circleopen", "hblur", "wiperight", "smoothleft")

/** Имя перехода ffmpeg для стыка с номером [global] (для «Микса» меняется по кругу). */
fun xfadeName(t: Transition, global: Int): String =
    if (t.xfade == "mix") MIX_LIST[global % MIX_LIST.size] else (t.xfade ?: "fade")

/**
 * План одной порции: из-за наплыва куски перекрываются, и готовое видео становится короче суммы кусков.
 * Длины округляются до целых кадров, иначе стыки «плывут».
 * [offsets] — кадр, с которого кусок начинается в готовом видео; [overlap] — сколько кадров он перекрывает предыдущий.
 */
class Plan(val frames: IntArray, val overlap: IntArray, val offsets: IntArray, val starts: DoubleArray, val partDur: Double, val fps: Double)

fun makePlan(keep: List<TimeRange>, tr: Transition, fps: Double): Plan {
    val n = keep.size
    if (tr.xfade == null) {
        val starts = DoubleArray(n)
        var acc = 0.0
        for (i in 0 until n) { starts[i] = acc; acc += keep[i].length }
        return Plan(IntArray(n), IntArray(n), IntArray(n), starts, acc, fps)
    }
    val frames = IntArray(n) { max(4, (keep[it].length * fps).roundToInt()) }
    val d = max(2, (tr.seconds * fps).roundToInt())
    val overlap = IntArray(n)
    val offsets = IntArray(n)
    var run = frames[0]
    for (k in 1 until n) {
        overlap[k] = min(d, min(frames[k] / 2, frames[k - 1] / 2))
        offsets[k] = run - overlap[k]
        run += frames[k] - overlap[k]
    }
    return Plan(frames, overlap, offsets, DoubleArray(n) { offsets[it] / fps }, run / fps, fps)
}
