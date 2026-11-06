package com.pocketcode

import com.pocketcode.llm.ModelList
import com.pocketcode.llm.RateGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RateAndModelsTest {
    @Test
    fun slidingWindowAllowsBurstThenWaits() {
        try {
            RateGate.configure(2, 10)
            assertEquals(0L, RateGate.reserve(0L))
            assertEquals(0L, RateGate.reserve(1L))
            val wait = RateGate.reserve(2L)
            assertTrue(wait in 10_000L..11_000L)
            // после окна место снова есть
            assertEquals(0L, RateGate.reserve(20_000L))
        } finally {
            RateGate.configure(0, 60)
        }
    }

    @Test
    fun noLimitNeverWaits() {
        RateGate.configure(0, 60)
        assertEquals(0L, RateGate.reserve(0L))
    }

    @Test
    fun modelListUrls() {
        assertEquals("https://api.openai.com/v1/models", ModelList.url(Provider.OPENAI, "https://api.openai.com/v1/"))
        assertEquals("https://api.anthropic.com/v1/models", ModelList.url(Provider.ANTHROPIC, "https://api.anthropic.com"))
    }

    @Test
    fun modelListParsing() {
        val a = ModelList.parse("""{"data":[{"id":"b-model"},{"id":"a-model"},{"id":"a-model"}]}""")
        assertEquals(listOf("a-model", "b-model"), a)
        val g = ModelList.parse("""{"models":[{"name":"models/gemini-x"}]}""")
        assertEquals(listOf("gemini-x"), g)
        assertTrue(ModelList.parse("not json").isEmpty())
    }
}
