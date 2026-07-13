package dev.spikeysanju.expensetracker.voice.domain

import dev.spikeysanju.expensetracker.voice.model.VoiceExtractionContext
import dev.spikeysanju.expensetracker.voice.model.VoiceTransactionExtraction
import java.text.SimpleDateFormat
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

class VoiceExtractionValidationException(
    message: String,
    val responseContent: String? = null
) : IllegalArgumentException(message)

object VoiceExtractionParser {
    fun parseResponse(
        responseBody: String,
        rawTranscript: String,
        context: VoiceExtractionContext
    ): VoiceTransactionExtraction {
        val root = try {
            JSONObject(responseBody)
        } catch (_: Exception) {
            throw VoiceExtractionValidationException("Groq returned an invalid completion envelope.")
        }
        val choices = root.optJSONArray("choices")
            ?: throw VoiceExtractionValidationException("Groq did not return any choices.")
        val message = choices.optJSONObject(0)?.optJSONObject("message")
            ?: throw VoiceExtractionValidationException("Groq did not return a message payload.")
        val extractionPayload = extractContent(message)
        val finishReason = choices.optJSONObject(0)?.optStringOrNull("finish_reason")
        if (finishReason != null && finishReason != "stop") {
            throw VoiceExtractionValidationException(
                "Groq did not complete the extraction normally: $finishReason.",
                extractionPayload
            )
        }
        return parseDelimitedContent(extractionPayload, rawTranscript, context)
    }

    fun parseDelimitedContent(
        content: String,
        rawTranscript: String,
        context: VoiceExtractionContext
    ): VoiceTransactionExtraction {
        val outer = extractSingleSection(
            text = content,
            startMarker = OUTER_START,
            endMarker = OUTER_END,
            label = "outer extraction",
            responseContent = content
        )
        validateOuterStructure(outer, content)
        val title = scalar(outer, "title", content)
        val amountText = scalar(outer, "amount", content)
        val transactionTypeText = scalar(outer, "transaction_type", content)
        val tagText = scalar(outer, "tag", content)
        val tagHint = scalar(outer, "tag_hint", content)
        val dateIso = scalar(outer, "date_iso", content)
        val note = scalar(outer, "note", content)
        val missingFieldsText = extractSingleSection(
            text = outer,
            startMarker = "===missing_fields_start===",
            endMarker = "===missing_fields_end===",
            label = "missing_fields",
            responseContent = content
        )
        val missingFields = extractRepeatedSections(
            text = missingFieldsText,
            startMarker = "===missing_field_start===",
            endMarker = "===missing_field_end===",
            label = "missing_field",
            responseContent = content
        ).map { value ->
            value.takeIf(ALLOWED_MISSING_FIELDS::contains)
                ?: invalid("Unsupported missing field: $value.", content)
        }.distinct()

        val amount = amountText?.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0.0 }
        if (amountText != null && amount == null) {
            invalid("amount must be a positive number.", content)
        }

        val transactionType = transactionTypeText?.let { value ->
            context.allowedTransactionTypes.firstOrNull { it.equals(value, ignoreCase = true) }
                ?: invalid("transaction_type must match an allowed transaction type.", content)
        }
        val allowedTags = context.allowedTagsByType.values.flatten().distinct()
        val tag = tagText?.let { value ->
            allowedTags.firstOrNull { it.equals(value, ignoreCase = true) }
                ?: invalid("tag must match an allowed tag.", content)
        }
        if (dateIso != null && !isIsoDate(dateIso)) {
            invalid("date_iso must use yyyy-MM-dd.", content)
        }

        return VoiceTransactionExtraction(
            title = title,
            amount = amount,
            transactionType = transactionType,
            tag = tag,
            tagHint = tagHint,
            dateIso = dateIso,
            note = note,
            missingFields = missingFields,
            rawTranscript = rawTranscript
        )
    }

    private fun extractContent(message: JSONObject): String {
        return when (val content = message.opt("content")) {
            is String -> content.trim()
            is JSONArray -> content.toStringList().joinToString(separator = "\n").trim()
            else -> ""
        }.ifBlank {
            throw VoiceExtractionValidationException("Groq returned an empty extraction payload.")
        }
    }

    private fun scalar(text: String, name: String, responseContent: String): String? {
        return extractSingleSection(
            text = text,
            startMarker = "===${name}_start===",
            endMarker = "===${name}_end===",
            label = name,
            responseContent = responseContent
        ).trim().ifBlank { null }
    }

    private fun extractSingleSection(
        text: String,
        startMarker: String,
        endMarker: String,
        label: String,
        responseContent: String
    ): String {
        if (text.countOccurrences(startMarker) != 1 || text.countOccurrences(endMarker) != 1) {
            invalid("Expected exactly one $label section.", responseContent)
        }
        val start = text.indexOf(startMarker) + startMarker.length
        val end = text.indexOf(endMarker, start)
        if (end < start) {
            invalid("The $label section markers are out of order.", responseContent)
        }
        return text.substring(start, end)
    }

    private fun extractRepeatedSections(
        text: String,
        startMarker: String,
        endMarker: String,
        label: String,
        responseContent: String
    ): List<String> {
        val startCount = text.countOccurrences(startMarker)
        val endCount = text.countOccurrences(endMarker)
        if (startCount != endCount) {
            invalid("The $label section markers are unbalanced.", responseContent)
        }
        val results = mutableListOf<String>()
        var cursor = 0
        repeat(startCount) {
            val startIndex = text.indexOf(startMarker, cursor)
            val contentStart = startIndex + startMarker.length
            val endIndex = text.indexOf(endMarker, contentStart)
            if (startIndex < 0 || endIndex < contentStart) {
                invalid("The $label section markers are out of order.", responseContent)
            }
            if (text.substring(cursor, startIndex).isNotBlank()) {
                invalid("The $label list contains untagged content.", responseContent)
            }
            val value = text.substring(contentStart, endIndex).trim()
            if (value.isBlank()) {
                invalid("A $label value cannot be empty.", responseContent)
            }
            results += value
            cursor = endIndex + endMarker.length
        }
        if (text.substring(cursor).isNotBlank()) {
            invalid("The $label list contains untagged content.", responseContent)
        }
        return results
    }

    private fun validateOuterStructure(outer: String, responseContent: String) {
        val sections = listOf(
            "title",
            "amount",
            "transaction_type",
            "tag",
            "tag_hint",
            "date_iso",
            "note",
            "missing_fields"
        )
        var cursor = 0
        sections.forEach { name ->
            val startMarker = "===${name}_start==="
            val endMarker = "===${name}_end==="
            val startIndex = outer.indexOf(startMarker, cursor)
            if (startIndex < 0 || outer.substring(cursor, startIndex).isNotBlank()) {
                invalid("The $name section is missing or out of order.", responseContent)
            }
            val endIndex = outer.indexOf(endMarker, startIndex + startMarker.length)
            if (endIndex < 0) {
                invalid("The $name section is incomplete.", responseContent)
            }
            cursor = endIndex + endMarker.length
        }
        if (outer.substring(cursor).isNotBlank()) {
            invalid("The extraction contains unexpected tagged or untagged content.", responseContent)
        }
    }

    private fun String.countOccurrences(value: String): Int {
        var count = 0
        var index = indexOf(value)
        while (index >= 0) {
            count += 1
            index = indexOf(value, index + value.length)
        }
        return count
    }

    private fun isIsoDate(value: String): Boolean {
        if (!value.matches(Regex("^\\d{4}-\\d{2}-\\d{2}$"))) return false
        return try {
            val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
            formatter.format(formatter.parse(value)!!) == value
        } catch (_: Exception) {
            false
        }
    }

    private fun invalid(message: String, responseContent: String): Nothing {
        throw VoiceExtractionValidationException(message, responseContent)
    }

    private val ALLOWED_MISSING_FIELDS = setOf(
        "title",
        "amount",
        "transactionType",
        "tag",
        "dateIso"
    )
    private const val OUTER_START = "===voice_transaction_extraction_start==="
    private const val OUTER_END = "===voice_transaction_extraction_end==="
}
