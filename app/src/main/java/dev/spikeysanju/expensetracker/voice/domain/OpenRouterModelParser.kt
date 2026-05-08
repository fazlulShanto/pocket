package dev.spikeysanju.expensetracker.voice.domain

import dev.spikeysanju.expensetracker.voice.model.OpenRouterModelOption
import java.util.Locale
import org.json.JSONObject

object OpenRouterModelParser {
    fun parseCompatibleModels(responseBody: String): List<OpenRouterModelOption> {
        val root = JSONObject(responseBody)
        val models = mutableListOf<OpenRouterModelOption>()
        val items = root.optJSONArray("data") ?: return emptyList()

        for (index in 0 until items.length()) {
            val model = items.optJSONObject(index) ?: continue
            val supportedParameters = model.optJSONArray("supported_parameters")?.toStringList().orEmpty()
            val outputModalities = model.optJSONObject("architecture")
                ?.optJSONArray("output_modalities")
                ?.toStringList()
                .orEmpty()

            val supportsStructuredOutputs = supportedParameters.any {
                it.equals("structured_outputs", ignoreCase = true)
            }
            val supportsResponseFormat = supportedParameters.any {
                it.equals("response_format", ignoreCase = true)
            }
            val supportsTextOutput = outputModalities.any {
                it.equals("text", ignoreCase = true)
            }

            if (!supportsStructuredOutputs || !supportsResponseFormat || !supportsTextOutput) {
                continue
            }

            val pricing = model.optJSONObject("pricing")
            val promptPrice = pricing?.optStringOrNull("prompt") ?: pricing?.optStringOrNull("input")
            val completionPrice = pricing?.optStringOrNull("completion") ?: pricing?.optStringOrNull("output")
            val contextLength = model.optIntOrNull("context_length")
                ?: model.optJSONObject("top_provider")?.optIntOrNull("context_length")
            val id = model.optStringOrNull("id") ?: continue
            val label = model.optStringOrNull("name") ?: id
            val description = model.optStringOrNull("description")
            val isFree = id.contains(":free", ignoreCase = true) ||
                hasZeroCost(promptPrice, completionPrice)

            models += OpenRouterModelOption(
                id = id,
                label = label,
                description = description,
                promptPrice = promptPrice,
                completionPrice = completionPrice,
                contextLength = contextLength,
                supportsStructuredOutputs = true,
                supportsResponseFormat = true,
                isFree = isFree
            )
        }

        return models.sortedWith(
            compareBy<OpenRouterModelOption> { !it.isFree }
                .thenBy { it.label.lowercase(Locale.US) }
        )
    }

    private fun hasZeroCost(promptPrice: String?, completionPrice: String?): Boolean {
        val parsedPrices = listOf(promptPrice, completionPrice)
            .mapNotNull { it?.toBigDecimalOrNull() }

        return parsedPrices.size == 2 && parsedPrices.all { it.signum() == 0 }
    }
}
