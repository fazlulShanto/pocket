package dev.spikeysanju.expensetracker.voice.domain

import dev.spikeysanju.expensetracker.voice.model.VoiceExtractionContext
import org.json.JSONArray
import org.json.JSONObject

object VoicePromptFactory {
    fun buildTranscriptionPrompt(
        currencyCode: String,
        currencySymbol: String,
        allowedTagsByType: Map<String, List<String>>
    ): String {
        val knownTags = allowedTagsByType.values
            .flatten()
            .distinct()
            .sorted()
            .joinToString(separator = ", ")

        return buildString {
            append("Expense tracker voice entry. Preserve spoken amounts, dates, merchant names, and note wording.")
            if (knownTags.isNotBlank()) {
                append(' ')
                append("Known tags may include ")
                append(knownTags)
                append('.')
            }
            append(' ')
            append("Currency context: ")
            append(currencyCode)
            append(' ')
            append(currencySymbol)
            append('.')
        }
    }

    fun buildExtractionSystemPrompt(): String {
        return "Convert one personal-finance voice transcript into one transaction draft. Preserve free-text fields in the speaker's language. Normalize only controlled fields. Do not invent missing facts. If a field is ambiguous, return null and list it in missingFields. If the spoken category does not confidently match an allowed tag, return tag null and preserve the original phrase in tagHint. If no explicit date is spoken, use the provided current date. Return JSON only."
    }

    fun buildExtractionUserPayload(
        transcript: String,
        context: VoiceExtractionContext
    ): String {
        val tagsByTypeJson = JSONObject()
        context.allowedTagsByType.forEach { (type, tags) ->
            tagsByTypeJson.put(type, JSONArray(tags))
        }

        return JSONObject()
            .put("transcript", transcript)
            .put("speechLanguageCode", context.speechLanguageCode)
            .put("speechLanguageLabel", context.speechLanguageLabel)
            .put("currentDateIso", context.currentDateIso)
            .put("timezoneId", context.timezoneId)
            .put("selectedCurrencyCode", context.selectedCurrencyCode)
            .put("selectedCurrencySymbol", context.selectedCurrencySymbol)
            .put("allowedTransactionTypes", JSONArray(context.allowedTransactionTypes))
            .put("allowedTagsByType", tagsByTypeJson)
            .toString()
    }
}
