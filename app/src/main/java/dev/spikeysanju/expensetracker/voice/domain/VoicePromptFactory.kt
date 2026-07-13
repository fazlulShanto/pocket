package dev.spikeysanju.expensetracker.voice.domain

import dev.spikeysanju.expensetracker.voice.model.VoiceExtractionContext
import org.json.JSONArray
import org.json.JSONObject

object VoicePromptFactory {
    private const val OUTER_START = "===voice_transaction_extraction_start==="
    private const val OUTER_END = "===voice_transaction_extraction_end==="

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
        return listOf(
            "Convert one personal-finance voice transcript into one transaction draft.",
            "Preserve free-text fields in the speaker's language and normalize only controlled fields.",
            "Do not invent missing facts. Use an empty tagged section for an unknown value and list required unknown values in missing_fields.",
            "If the spoken category does not confidently match an allowed tag, leave tag empty and preserve the original phrase in tag_hint.",
            "If no explicit date is spoken, use currentDateIso from the input.",
            "Return tagged text only, never JSON. Do not add text before or after the outer markers.",
            "Use this exact format:",
            OUTER_START,
            "===title_start===",
            "Transaction title or empty",
            "===title_end===",
            "===amount_start===",
            "Positive number without a currency symbol or empty",
            "===amount_end===",
            "===transaction_type_start===",
            "Exact allowed transaction type or empty",
            "===transaction_type_end===",
            "===tag_start===",
            "Exact allowed tag or empty",
            "===tag_end===",
            "===tag_hint_start===",
            "Unmatched spoken category phrase or empty",
            "===tag_hint_end===",
            "===date_iso_start===",
            "Date in yyyy-MM-dd or empty",
            "===date_iso_end===",
            "===note_start===",
            "Additional spoken context or empty",
            "===note_end===",
            "===missing_fields_start===",
            "===missing_field_start===",
            "One of title, amount, transactionType, tag, dateIso",
            "===missing_field_end===",
            "===missing_fields_end===",
            OUTER_END,
            "Repeat missing_field blocks as needed. If none are missing, leave the missing_fields section empty."
        ).joinToString(separator = "\n")
    }

    fun buildExtractionCorrectionPrompt(validationError: String): String {
        return listOf(
            "Your previous response did not follow the required tagged transaction format.",
            "Validation error: ${validationError.take(500)}",
            "Return the corrected transaction now using exactly one complete outer block and every required field section.",
            "Return tagged text only. Do not return JSON or explanatory text."
        ).joinToString(separator = "\n")
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
