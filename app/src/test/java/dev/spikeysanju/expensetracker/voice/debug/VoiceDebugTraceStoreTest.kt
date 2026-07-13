package dev.spikeysanju.expensetracker.voice.debug

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class VoiceDebugTraceStoreTest {
    @Before
    fun setUp() {
        VoiceDebugTraceStore.clear()
    }

    @After
    fun tearDown() {
        VoiceDebugTraceStore.clear()
    }

    @Test
    fun `captures labeled debug details in memory`() {
        VoiceDebugTraceStore.append("WHISPER RESPONSE", "recognized words")
        VoiceDebugTraceStore.append("LLM RESPONSE", "raw completion")

        val trace = VoiceDebugTraceStore.snapshot()

        assertTrue(trace.contains("WHISPER RESPONSE"))
        assertTrue(trace.contains("recognized words"))
        assertTrue(trace.contains("LLM RESPONSE"))
        assertTrue(trace.contains("raw completion"))
    }

    @Test
    fun `clear removes previous recording details`() {
        VoiceDebugTraceStore.append("OLD", "private transaction")

        VoiceDebugTraceStore.clear()

        assertFalse(VoiceDebugTraceStore.snapshot().contains("private transaction"))
    }

    @Test
    fun `trace size is bounded`() {
        VoiceDebugTraceStore.append("LARGE", "x".repeat(140 * 1024))

        assertTrue(VoiceDebugTraceStore.snapshot().length <= 128 * 1024)
    }
}
