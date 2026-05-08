package dev.spikeysanju.expensetracker.voice.domain

import dev.spikeysanju.expensetracker.voice.model.VoiceExtractionContext
import dev.spikeysanju.expensetracker.voice.model.VoiceTransactionDraft
import dev.spikeysanju.expensetracker.voice.model.VoiceTransactionExtraction
import java.text.SimpleDateFormat
import java.util.LinkedHashSet
import java.util.Locale
import javax.inject.Inject

class VoiceDraftMapper @Inject constructor() {
    fun map(
        extraction: VoiceTransactionExtraction,
        context: VoiceExtractionContext
    ): VoiceTransactionDraft {
        val missing = LinkedHashSet<String>()

        val title = extraction.title?.trim().takeIf { !it.isNullOrEmpty() }
        val amount = extraction.amount?.takeIf { it > 0.0 }
        val transactionType = normalizeTransactionType(
            rawValue = extraction.transactionType,
            allowedTransactionTypes = context.allowedTransactionTypes
        )
        val allowedTags = if (transactionType != null) {
            context.allowedTagsByType[transactionType].orEmpty()
        } else {
            context.allowedTagsByType.values.flatten().distinct()
        }
        val tag = normalizeTag(extraction.tag, allowedTags)
        val tagHint = extraction.tagHint?.trim().takeIf { !it.isNullOrEmpty() }
            ?: extraction.tag?.trim()?.takeIf { tag == null }
        val resolvedDateIso = extraction.dateIso?.trim()
            ?.takeIf(::isIsoDate)
            ?: context.currentDateIso
        val date = formatIsoToDisplayDate(resolvedDateIso) ?: context.currentDateDisplay
        val note = extraction.note?.trim().takeIf { !it.isNullOrEmpty() }
        val rawTranscript = extraction.rawTranscript.trim()

        extraction.missingFields
            .map(::normalizeMissingField)
            .forEach(missing::add)

        if (title == null) {
            missing += "title"
        }
        if (amount == null) {
            missing += "amount"
        }
        if (transactionType == null) {
            missing += "transactionType"
        }
        if (tag == null) {
            missing += "tag"
        }

        if (title != null) missing.remove("title")
        if (amount != null) missing.remove("amount")
        if (transactionType != null) missing.remove("transactionType")
        if (tag != null) missing.remove("tag")
        missing.remove("date")

        return VoiceTransactionDraft(
            title = title,
            amount = amount,
            transactionType = transactionType,
            tag = tag,
            date = date,
            note = note,
            tagHint = tagHint,
            rawTranscript = rawTranscript,
            missingFields = missing.toList()
        )
    }

    private fun normalizeTransactionType(
        rawValue: String?,
        allowedTransactionTypes: List<String>
    ): String? {
        val trimmed = rawValue?.trim().takeIf { !it.isNullOrEmpty() } ?: return null
        return allowedTransactionTypes.firstOrNull { allowed ->
            allowed.equals(trimmed, ignoreCase = true)
        }
    }

    private fun normalizeTag(rawValue: String?, allowedTags: List<String>): String? {
        val trimmed = rawValue?.trim().takeIf { !it.isNullOrEmpty() } ?: return null
        return allowedTags.firstOrNull { allowed ->
            allowed.equals(trimmed, ignoreCase = true)
        }
    }

    private fun normalizeMissingField(rawValue: String): String {
        return when (rawValue.trim()) {
            "dateIso", "date" -> "date"
            "transaction_type" -> "transactionType"
            else -> rawValue.trim()
        }
    }

    private fun isIsoDate(value: String): Boolean {
        return try {
            SimpleDateFormat(ISO_DATE_PATTERN, Locale.US).apply {
                isLenient = false
            }.parse(value)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun formatIsoToDisplayDate(value: String): String? {
        return try {
            val parser = SimpleDateFormat(ISO_DATE_PATTERN, Locale.US).apply {
                isLenient = false
            }
            val formatter = SimpleDateFormat(DISPLAY_DATE_PATTERN, Locale.getDefault())
            parser.parse(value)?.let(formatter::format)
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        const val ISO_DATE_PATTERN = "yyyy-MM-dd"
        const val DISPLAY_DATE_PATTERN = "dd/MM/yyyy"
    }
}
