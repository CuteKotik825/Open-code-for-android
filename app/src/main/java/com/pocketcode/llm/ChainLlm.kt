package com.pocketcode.llm

import kotlin.coroutines.cancellation.CancellationException

/**
 * Цепочка резервных моделей: пока текущая работает, используется она. Если ответила ошибкой квоты, лимита или ключа,
 * переходим к следующей и повторяем запрос. Когда цепочка закончилась, ошибка уходит дальше.
 */
class ChainLlm(
    private val clients: List<Pair<String, LlmClient>>,
    private val onSwitch: (String) -> Unit = {},
) : LlmClient {
    @Volatile private var index = 0

    override suspend fun complete(
        system: String,
        messages: List<Message>,
        tools: List<ToolSpec>,
        onTextDelta: (String) -> Unit,
    ): LlmResponse {
        while (true) {
            val i = index
            try {
                return clients[i].second.complete(system, messages, tools, onTextDelta)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (i >= clients.lastIndex || !looksLikeQuota(e.message)) throw e
                index = i + 1
                onSwitch("«${clients[i].first}» не сработала (${(e.message ?: "").take(80)}), перехожу на «${clients[i + 1].first}»")
            }
        }
    }

    companion object {
        /** Похоже ли сообщение об ошибке на исчерпанную квоту, лимит или плохой ключ. */
        fun looksLikeQuota(msg: String?): Boolean {
            val m = msg?.lowercase() ?: return false
            return listOf(
                "429", "402", "401", "403", "529", "rate limit", "rate_limit", "quota", "insufficient", "too many requests",
                "overloaded", "credit", "billing", "invalid api key", "incorrect api key", "unauthorized",
            ).any { m.contains(it) }
        }
    }
}
