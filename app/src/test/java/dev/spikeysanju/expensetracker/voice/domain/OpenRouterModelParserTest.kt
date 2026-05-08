package dev.spikeysanju.expensetracker.voice.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenRouterModelParserTest {
    @Test
    fun `filters models to strict structured output capable text models`() {
        val response = """
            {
              "data": [
                {
                  "id": "good/model-free",
                  "name": "Good Model",
                  "description": "Compatible model",
                  "supported_parameters": ["structured_outputs", "response_format"],
                  "architecture": {
                    "output_modalities": ["text"]
                  },
                  "pricing": {"prompt": "0", "completion": "0"},
                  "context_length": 128000
                },
                {
                  "id": "paid/model",
                  "name": "Paid Model",
                  "supported_parameters": ["structured_outputs", "response_format"],
                  "architecture": {
                    "output_modalities": ["text"]
                  },
                  "pricing": {"prompt": "0.25", "completion": "0.5"}
                },
                {
                  "id": "bad/no-structured",
                  "name": "Bad Model",
                  "supported_parameters": ["response_format"],
                  "architecture": {
                    "output_modalities": ["text"]
                  }
                },
                {
                  "id": "bad/no-text",
                  "name": "Image Model",
                  "supported_parameters": ["structured_outputs", "response_format"],
                  "architecture": {
                    "output_modalities": ["image"]
                  }
                }
              ]
            }
        """.trimIndent()

        val models = OpenRouterModelParser.parseCompatibleModels(response)

        assertEquals(2, models.size)
        assertEquals("good/model-free", models.first().id)
        assertTrue(models.first().isFree)
        assertEquals(128000, models.first().contextLength)
        assertEquals("paid/model", models.last().id)
    }
}
