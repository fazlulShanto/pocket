package dev.spikeysanju.expensetracker.voice.domain

import dev.spikeysanju.expensetracker.voice.model.VoiceTransactionExtraction
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object VoiceExtractionParser {
    fun parseResponse(
        responseBody: String,
        rawTranscript: String
    ): VoiceTransactionExtraction {
        val root = JSONObject(responseBody)
        val choices = root.optJSONArray("choices")
            ?: throw IllegalStateException("OpenRouter did not return any choices.")
        val message = choices.optJSONObject(0)?.optJSONObject("message")
            ?: throw IllegalStateException("OpenRouter did not return a message payload.")
        val extractionPayload = extractContent(message)

        val extractionJson = try {
            JSONObject(extractionPayload)
        } catch (error: JSONException) {
            throw IllegalStateException("OpenRouter returned a non-JSON extraction payload.", error)
        }

        return VoiceTransactionExtraction(
            title = extractionJson.optStringOrNull("title"),
            amount = extractionJson.optDoubleOrNull("amount"),
            transactionType = extractionJson.optStringOrNull("transactionType"),
            tag = extractionJson.optStringOrNull("tag"),
            tagHint = extractionJson.optStringOrNull("tagHint"),
            dateIso = extractionJson.optStringOrNull("dateIso"),
            note = extractionJson.optStringOrNull("note"),
            missingFields = extractionJson.optJSONArray("missingFields")?.toStringList().orEmpty(),
            rawTranscript = extractionJson.optStringOrNull("rawTranscript") ?: rawTranscript
        )
    }

    private fun extractContent(message: JSONObject): String {
        return when (val content = message.opt("content")) {
            is String -> content.trim()
            is JSONArray -> content.toStringList().joinToString(separator = "\n").trim()
            else -> ""
        }.ifBlank {
            throw IllegalStateException("OpenRouter returned an empty extraction payload.")
        }
    }
}
