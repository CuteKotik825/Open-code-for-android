package com.pocketcode

import com.pocketcode.llm.Block
import com.pocketcode.llm.ChainLlm
import com.pocketcode.llm.LlmClient
import com.pocketcode.llm.LlmResponse
import com.pocketcode.llm.Message
import com.pocketcode.llm.RateGate
import com.pocketcode.llm.ToolSpec
import com.pocketcode.tools.UndoJournal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class ChainAndUndoTest {
    private class Fake(private val error: String?, private val answer: String) : LlmClient {
        var calls = 0
        override suspend fun complete(
            system: String,
            messages: List<Message>,
            tools: List<ToolSpec>,
            onTextDelta: (String) -> Unit,
        ): LlmResponse {
            calls++
            if (error != null) throw RuntimeException(error)
            return LlmResponse(listOf(Block.Text(answer)), "end_turn")
        }
    }

    private fun ask(c: LlmClient): String = runBlocking {
        c.complete("s", listOf(Message("user", listOf(Block.Text("hi")))), emptyList()) { }
            .blocks.filterIsInstance<Block.Text>().joinToString("") { it.text }
    }

    @Test
    fun chainSkipsExhaustedModels() {
        val a = Fake("HTTP 429: rate limit", "a")
        val b = Fake("HTTP 402: insufficient credit", "b")
        val c = Fake(null, "c")
        val switched = mutableListOf<String>()
        val chain = ChainLlm(listOf("A" to a, "B" to b, "C" to c)) { switched += it }
        assertEquals("c", ask(chain))
        assertEquals(2, switched.size)
        // дальше идём сразу на рабочую
        assertEquals("c", ask(chain))
        assertEquals(1, a.calls)
        assertEquals(1, b.calls)
        assertEquals(2, c.calls)
    }

    @Test
    fun chainDoesNotHideOtherErrors() {
        val chain = ChainLlm(listOf("A" to Fake("синтаксическая ошибка", "a"), "B" to Fake(null, "b")))
        try {
            ask(chain)
            fail("ожидалась ошибка")
        } catch (e: RuntimeException) {
            assertTrue(e.message!!.contains("синтаксическая"))
        }
    }

    @Test
    fun quotaDetection() {
        assertTrue(ChainLlm.looksLikeQuota("HTTP 429: too many requests"))
        assertFalse(ChainLlm.looksLikeQuota("ошибка разбора"))
        assertFalse(ChainLlm.looksLikeQuota(null))
    }

    private fun tmp(): File = File(System.getProperty("java.io.tmpdir"), "undo-" + System.nanoTime()).also { it.mkdirs() }

    @Test
    fun rollbackRestoresAndRemoves() {
        val dir = tmp()
        try {
            val old = File(dir, "a.txt").apply { writeText("old") }
            val fresh = File(dir, "new.txt")
            val j = UndoJournal()
            j.beginTurn()
            j.record(old)
            old.writeText("changed")
            j.record(fresh)
            fresh.writeText("created")
            assertEquals(2, j.pendingFiles())
            j.rollback()
            assertEquals("old", old.readText())
            assertFalse(fresh.exists())
            assertEquals(0, j.pendingFiles())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun onlyFirstRecordPerTurnCounts() {
        val dir = tmp()
        try {
            val f = File(dir, "a.txt").apply { writeText("v1") }
            val j = UndoJournal()
            j.beginTurn()
            j.record(f)
            f.writeText("v2")
            j.record(f)
            f.writeText("v3")
            j.rollback()
            assertEquals("v1", f.readText())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun disabledJournalRecordsNothing() {
        val dir = tmp()
        try {
            val f = File(dir, "a.txt").apply { writeText("v1") }
            val j = UndoJournal()
            j.enabled = false
            j.beginTurn()
            j.record(f)
            assertEquals(0, j.pendingFiles())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun rateStatusReportsUsage() {
        try {
            RateGate.configure(0, 60)
            assertNull(RateGate.status())
            RateGate.configure(1, 60)
            RateGate.reserve(System.currentTimeMillis())
            val st = RateGate.status()
            assertNotNull(st)
            assertEquals(1, st!!.first)
            assertTrue(st.second in 1L..70L)
        } finally {
            RateGate.configure(0, 60)
        }
    }
}
