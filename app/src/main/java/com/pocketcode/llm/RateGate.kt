package com.pocketcode.llm

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Общий ограничитель частоты запросов к модели. 0 = без ограничений.
 * Запросы выстраиваются в очередь с равными промежутками (60 000 / N мс), поэтому
 * лимит не превышается даже если запросы приходят пачкой. Один счётчик на всё приложение:
 * собственный агент, OpenCode и Claude Code (через локальный прокси) делят одну квоту.
 */
object RateGate {
    @Volatile var perMinute: Int = 0
    private val mutex = Mutex()
    private var nextAt = 0L
    @Volatile var waitingMs: Long = 0
        private set

    /** Сервис ответил 429: следующие запросы не раньше чем через [ms] мс (Retry-After или минута с запасом). */
    fun penalize(ms: Long) {
        if (perMinute <= 0) return
        synchronized(this) { nextAt = maxOf(nextAt, System.currentTimeMillis() + ms) }
    }

    /** Секунды из заголовка Retry-After (если есть), иначе null. */
    fun retryAfterMs(header: String?): Long? = header?.trim()?.toDoubleOrNull()?.let { (it * 1000).toLong().coerceIn(1000L, 600_000L) }

    suspend fun acquire() {
        val n = perMinute
        if (n <= 0) return
        // Небольшой запас: сервис считает запросы по своим часам, и ровно 60 с между запросами при 1 в минуту уже могут не пройти
        val base = 60_000L / n
        val gap = base + (base * 0.08).toLong().coerceIn(500L, 4000L)
        val wait = mutex.withLock {
            val now = System.currentTimeMillis()
            val at = synchronized(this) { maxOf(now, nextAt).also { nextAt = it + gap } }
            at - now
        }
        if (wait > 0) {
            waitingMs = wait
            try { delay(wait) } finally { waitingMs = 0 }
        }
    }
}
