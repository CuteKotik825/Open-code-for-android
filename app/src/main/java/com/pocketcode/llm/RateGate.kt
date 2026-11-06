package com.pocketcode.llm

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Общий ограничитель частоты запросов к модели: не больше [limit] запросов за любой промежуток [periodMs].
 * 0 = без ограничений. Окно скользящее: запросы идут сразу, пока в последнем окне есть место,
 * а когда оно занято, следующий ждёт, пока освободится самое старое место. Один счётчик на всё приложение:
 * собственный агент, OpenCode и Claude Code (через локальный прокси) делят одну квоту.
 */
object RateGate {
    @Volatile var limit: Int = 0
    @Volatile var periodMs: Long = 60_000L
    private val mutex = Mutex()
    private var nextAt = 0L
    private val stamps = ArrayDeque<Long>()
    @Volatile var waitingMs: Long = 0
        private set

    fun configure(count: Int, periodSec: Int) {
        synchronized(this) {
            limit = count.coerceAtLeast(0)
            periodMs = periodSec.coerceAtLeast(1) * 1000L
            stamps.clear()
        }
    }

    /** Сервис ответил 429: следующие запросы не раньше чем через [ms] мс (Retry-After или минута с запасом). */
    fun penalize(ms: Long) {
        if (limit <= 0) return
        synchronized(this) { nextAt = maxOf(nextAt, System.currentTimeMillis() + ms) }
    }

    /** Секунды из заголовка Retry-After (если есть), иначе null. */
    fun retryAfterMs(header: String?): Long? = header?.trim()?.toDoubleOrNull()?.let { (it * 1000).toLong().coerceIn(1000L, 600_000L) }

    /** Сколько ждать до свободного места в окне; 0 — можно отправлять (место при этом занимается). */
    internal fun reserve(now: Long): Long = synchronized(this) {
        val lim = limit
        if (lim <= 0) return 0L
        val per = periodMs
        // Небольшой запас: сервис считает запросы по своим часам
        val margin = (per / 50).coerceIn(300L, 2000L)
        while (stamps.isNotEmpty() && now - stamps.first() >= per + margin) stamps.removeFirst()
        var w = maxOf(0L, nextAt - now)
        if (stamps.size >= lim) {
            val blocker = stamps.elementAt(stamps.size - lim)
            w = maxOf(w, blocker + per + margin - now)
        }
        if (w <= 0L) stamps.addLast(now)
        w
    }

    suspend fun acquire() {
        if (limit <= 0) return
        mutex.withLock {
            while (true) {
                val w = reserve(System.currentTimeMillis())
                if (w <= 0L) return
                waitingMs = w
                try { delay(w) } finally { waitingMs = 0 }
            }
        }
    }
}
