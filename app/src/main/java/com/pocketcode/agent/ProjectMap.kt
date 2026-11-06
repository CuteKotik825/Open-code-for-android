package com.pocketcode.agent

import java.io.File

/**
 * Короткая карта проекта для подсказки агенту: дерево папок и файлов с размерами.
 * Агент сразу видит, что где лежит, и не тратит шаги и токены на ls и поиск. Секреты (скрытые файлы) в карту не попадают.
 */
object ProjectMap {
    private val SKIP = setOf(
        "node_modules", "build", "dist", "out", "target", "venv", "__pycache__", "gradle", "Pods", "vendor",
    )
    private const val MAX_DEPTH = 4
    private const val MAX_SCAN = 3000

    private fun size(bytes: Long): String = if (bytes < 1024) "$bytes Б" else "${bytes / 1024} КБ"

    private fun visible(f: File): Boolean = when {
        f.name == ".github" -> true
        f.name.startsWith(".") -> false
        f.isDirectory && f.name in SKIP -> false
        else -> true
    }

    /** Текст карты не длиннее maxChars; пусто, если папки нет. */
    fun build(root: File, maxChars: Int = 3000): String {
        if (!root.isDirectory) return ""
        val sb = StringBuilder()
        var scanned = 0
        var skipped = 0

        fun walk(dir: File, depth: Int, indent: String) {
            val kids = (dir.listFiles() ?: return)
                .filter { visible(it) }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            for (f in kids) {
                scanned++
                if (scanned > MAX_SCAN) {
                    skipped++
                    continue
                }
                val line = if (f.isDirectory) "$indent${f.name}/\n" else "$indent${f.name} (${size(f.length())})\n"
                if (sb.length + line.length > maxChars) {
                    skipped++
                    continue
                }
                sb.append(line)
                if (f.isDirectory && depth < MAX_DEPTH) walk(f, depth + 1, "$indent  ")
            }
        }

        walk(root, 1, "")
        if (skipped > 0) sb.append("… и ещё $skipped элементов (список урезан)\n")
        return sb.toString().trimEnd()
    }
}
