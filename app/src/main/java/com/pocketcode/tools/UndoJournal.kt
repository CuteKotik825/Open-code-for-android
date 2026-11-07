package com.pocketcode.tools

import java.io.File

/**
 * Журнал для отката: перед каждой записью файла агентом запоминается его прежнее содержимое.
 * Один «ход» агента — одна запись журнала; откат возвращает файлы, изменённые в последнем ходе.
 * Покрывает write_file и edit_file. Файлы, изменённые командами оболочки или Python, не откатываются.
 */
class UndoJournal(private val maxTurns: Int = 5, private val maxFileBytes: Long = 2_000_000) {
    private class Turn {
        val before = LinkedHashMap<File, ByteArray?>()
        var skipped = 0
    }

    private val turns = ArrayDeque<Turn>()

    @Volatile var enabled: Boolean = true

    /** Начало нового хода: пустые ходы в журнале не копятся. */
    @Synchronized
    fun beginTurn() {
        if (!enabled) return
        if (turns.isNotEmpty() && turns.last().before.isEmpty() && turns.last().skipped == 0) return
        turns.addLast(Turn())
        while (turns.size > maxTurns) turns.removeFirst()
    }

    /** Вызывается перед записью: запоминает содержимое файла (или что его не было). Повторные записи в том же ходе игнорируются. */
    @Synchronized
    fun record(f: File) {
        if (!enabled) return
        if (turns.isEmpty()) turns.addLast(Turn())
        val t = turns.last()
        if (f in t.before) return
        when {
            !f.exists() -> t.before[f] = null
            f.isFile && f.length() <= maxFileBytes -> t.before[f] = runCatching { f.readBytes() }.getOrNull() ?: run { t.skipped++; return }
            else -> t.skipped++
        }
    }

    /** Сколько файлов вернёт откат последнего хода. */
    @Synchronized
    fun pendingFiles(): Int = turns.lastOrNull { it.before.isNotEmpty() }?.before?.size ?: 0

    /** Возвращает файлы последнего хода в прежнее состояние. Результат — отдельные фразы (их переводит вызывающий). */
    @Synchronized
    fun rollbackParts(): List<String> {
        val t = turns.lastOrNull { it.before.isNotEmpty() } ?: return listOf("Откатывать нечего: агент пока не менял файлы.")
        var restored = 0
        var removed = 0
        var failed = 0
        for ((f, bytes) in t.before) {
            try {
                if (bytes == null) {
                    if (f.exists() && f.delete()) removed++
                } else {
                    f.parentFile?.mkdirs()
                    f.writeBytes(bytes)
                    restored++
                }
            } catch (e: Exception) {
                failed++
            }
        }
        turns.remove(t)
        val out = mutableListOf("Откат готов: возвращено файлов $restored, удалено созданных $removed.")
        if (failed > 0) out += "Не удалось вернуть файлов: $failed."
        if (t.skipped > 0) out += "Крупные файлы (${t.skipped}) не сохранялись и не откатились."
        return out
    }

    fun rollback(): String = rollbackParts().joinToString(" ")
}
