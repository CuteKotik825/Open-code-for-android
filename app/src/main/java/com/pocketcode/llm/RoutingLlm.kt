package com.pocketcode.llm

/**
 * Автовыбор модели: простые вопросы без правок идут на дешёвую модель-помощник, всё остальное на основную.
 * Решение принимается по последнему сообщению пользователя, без лишних запросов к модели.
 * Любое сомнение ведёт на основную модель.
 */
class RoutingLlm(
    private val main: LlmClient,
    private val cheap: LlmClient,
    /** Включён ли автовыбор сейчас (можно менять на лету). */
    private val enabled: () -> Boolean,
    /** Вызывается один раз на сообщение пользователя, когда ответ отдан дешёвой модели. */
    private val onCheap: () -> Unit = {},
) : LlmClient {
    private var lastNoted: Message? = null

    override suspend fun complete(
        system: String,
        messages: List<Message>,
        tools: List<ToolSpec>,
        onTextDelta: (String) -> Unit,
    ): LlmResponse {
        val useCheap = enabled() && isSimpleQuestion(messages)
        if (useCheap) {
            val msg = lastUserText(messages)
            if (msg !== lastNoted) {
                lastNoted = msg
                onCheap()
            }
        }
        return (if (useCheap) cheap else main).complete(system, messages, tools, onTextDelta)
    }

    companion object {
        private const val MAX_SIMPLE_CHARS = 240

        /** Признаки работы с кодом и файлами: такие задачи всегда идут на основную модель. */
        private val WORK = listOf(
            "напиш", "сдела", "созда", "добав", "исправ", "почин", "измен", "поменя", "удал", "рефактор", "перепиш",
            "реализ", "установ", "запуст", "собер", "закоммит", "коммит", "задеплой", "обнови", "перенес", "оптимиз",
            "write", "make ", "create", "add ", "fix", "change", "update", "delete", "remove", "refactor", "rewrite",
            "implement", "install", "run ", "build", "commit", "deploy", "optimi",
        )

        private val QUESTION_START = listOf(
            "что ", "что такое", "как ", "почему", "зачем", "чем ", "в чём", "в чем", "объясни", "расскажи", "поясни",
            "what", "why", "how", "explain", "tell me",
        )

        private fun lastUserText(messages: List<Message>): Message? =
            messages.lastOrNull { m -> m.role == "user" && m.blocks.any { it is Block.Text } }

        /** true — последнее сообщение пользователя похоже на короткий вопрос без просьбы что-то менять. */
        fun isSimpleQuestion(messages: List<Message>): Boolean {
            val last = lastUserText(messages) ?: return false
            if (last.blocks.any { it is Block.Image }) return false
            val text = last.blocks.filterIsInstance<Block.Text>().joinToString(" ") { it.text }.trim()
            if (text.isEmpty() || text.length > MAX_SIMPLE_CHARS || text.contains("```")) return false
            val low = text.lowercase()
            if (WORK.any { low.contains(it) }) return false
            return QUESTION_START.any { low.startsWith(it) } || low.endsWith("?")
        }
    }
}
