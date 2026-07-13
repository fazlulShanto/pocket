package dev.spikeysanju.expensetracker.voice.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroqReasoningModelsTest {
    @Test
    fun `default model is first suggestion and catalog contains requested models`() {
        assertEquals(
            GroqReasoningModels.DEFAULT_MODEL_ID,
            GroqReasoningModels.suggestedModelIds.first()
        )
        assertEquals(4, GroqReasoningModels.suggestedModelIds.size)
        assertTrue(GroqReasoningModels.suggestedModelIds.contains("qwen/qwen3.6-27b"))
        assertTrue(GroqReasoningModels.suggestedModelIds.contains("qwen/qwen3-32b"))
        assertTrue(GroqReasoningModels.suggestedModelIds.contains("llama-3.3-70b-versatile"))
    }
}
