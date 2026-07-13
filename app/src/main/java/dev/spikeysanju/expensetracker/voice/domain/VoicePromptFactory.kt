package dev.spikeysanju.expensetracker.voice.domain

import dev.spikeysanju.expensetracker.voice.model.VoiceExtractionContext
import org.json.JSONArray
import org.json.JSONObject

object VoicePromptFactory {
    private const val OUTER_START = "===voice_transaction_extraction_start==="
    private const val OUTER_END = "===voice_transaction_extraction_end==="
    private const val MAX_TRANSCRIPTION_PROMPT_CHARACTERS = 400
    private const val OUTPUT_ONLY_INSTRUCTION =
        "Return tagged text only: output exactly one complete outer block. " +
            "No JSON, markdown code fences, preamble, or commentary before or after the outer markers."

    fun buildTranscriptionPrompt(
        currencyCode: String,
        currencySymbol: String,
        allowedTagsByType: Map<String, List<String>>
    ): String {
        val knownTags = allowedTagsByType.values
            .flatten()
            .map { tag -> tag.trim().take(48) }
            .filter(String::isNotEmpty)
            .distinct()
            .sorted()

        val prompt = StringBuilder(
            "Personal finance transaction dictation. " +
                "Currency spelling: ${currencyCode.trim().take(12)} ${currencySymbol.trim().take(8)}. " +
                "Expected transaction vocabulary: income, expense, amount, merchant, date, note."
        )
        if (knownTags.isEmpty()) return prompt.toString()

        prompt.append(" Expected category spellings:")
        knownTags.forEach { tag ->
            val separator = if (prompt.last() == ':') " " else ", "
            val addition = separator + tag
            if (prompt.length + addition.length + 1 <= MAX_TRANSCRIPTION_PROMPT_CHARACTERS) {
                prompt.append(addition)
            }
        }
        return prompt.append('.').toString()
    }

    fun buildExtractionSystemPrompt(): String {
        return listOf(
            OUTPUT_ONLY_INSTRUCTION,
            TASK_RULES,
            CORRECTION_RULES,
            CONTENT_RULES,
            OUTPUT_CONTRACT,
            FORMAT_TEMPLATE,
            WORKED_EXAMPLES,
            OUTPUT_ONLY_INSTRUCTION
        ).joinToString(separator = "\n\n")
    }

    fun buildExtractionCorrectionPrompt(validationError: String): String {
        return listOf(
            OUTPUT_ONLY_INSTRUCTION,
            "Your previous response did not follow the required tagged transaction format.",
            "Validation error: ${validationError.take(500)}",
            "Re-check the transcript for likely speech-recognition mistakes and apply corrections only with high confidence.",
            "Return the corrected transaction now using exactly one complete outer block and every required field section.",
            OUTPUT_ONLY_INSTRUCTION
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
            .put("transcriptSource", "automatic_speech_recognition")
            .put("transcriptMayContainSpeechRecognitionErrors", true)
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

    private val TASK_RULES = """
        [TASK]
        Convert one personal-finance voice transcript into one transaction draft.
        The input transcript comes from automatic speech recognition and may contain speech-recognition errors.
        Preserve free-text fields in the speaker's language. Normalize only controlled fields.
    """.trimIndent()

    private val CORRECTION_RULES = """
        [CORRECTION RULES]
        Speech-recognition errors may include homophones, incorrect word boundaries, and misheard merchant or category names.
        Use the full financial context, selected currency, and allowed transaction types and tags as evidence for intended wording.
        Correct only high-confidence mistakes. Use corrected wording in title and note.
        Keep uncertain wording unchanged and expose ambiguity through missing_fields or tag_hint.
        Do not change a spoken amount or date based only on plausibility; correct it only when transcript evidence makes the intended value unambiguous.
        Do not silently normalize an unfamiliar person, place, or merchant name without strong contextual evidence.
    """.trimIndent()

    private val CONTENT_RULES = """
        [CONTENT RULES]
        Only use values present in allowedTransactionTypes and allowedTagsByType from the input payload; never invent a transaction type or tag.
        If a spoken category does not confidently match an allowed tag, leave tag empty and preserve the original phrase in tag_hint.
        Resolve relative date expressions such as "yesterday" or "last Monday" against currentDateIso and timezoneId.
        If no explicit or relative date is spoken, use currentDateIso.
        Output amount as a positive number without a currency symbol, always using . as the decimal separator regardless of spoken language.
        Do not invent missing facts. Use an empty section for an unknown value and list the required unknown in missing_fields.
        If the transcript contains no extractable transaction, leave all fields empty and list every required field in missing_fields: title, amount, transactionType, tag, dateIso. This overrides the default-date rule.
    """.trimIndent()

    private val OUTPUT_CONTRACT = """
        [OUTPUT CONTRACT]
        Return every field section exactly once and in the exact order shown in the template.
        Replace each {{PLACEHOLDER}} with the extracted value or with nothing when the value is empty.
        Never return the placeholder tokens themselves.
        Inside missing_fields, repeat a complete missing_field block for each missing required field.
        Allowed missing_field values are: title, amount, transactionType, tag, dateIso.
        If no required fields are missing, leave missing_fields empty.
    """.trimIndent()

    private val FORMAT_TEMPLATE = """
        [FORMAT TEMPLATE]
        $OUTER_START
        ===title_start===
        {{TITLE_OR_EMPTY}}
        ===title_end===
        ===amount_start===
        {{POSITIVE_AMOUNT_OR_EMPTY}}
        ===amount_end===
        ===transaction_type_start===
        {{ALLOWED_TRANSACTION_TYPE_OR_EMPTY}}
        ===transaction_type_end===
        ===tag_start===
        {{ALLOWED_TAG_OR_EMPTY}}
        ===tag_end===
        ===tag_hint_start===
        {{TAG_HINT_OR_EMPTY}}
        ===tag_hint_end===
        ===date_iso_start===
        {{ISO_DATE_OR_EMPTY}}
        ===date_iso_end===
        ===note_start===
        {{NOTE_OR_EMPTY}}
        ===note_end===
        ===missing_fields_start===
        {{ZERO_OR_MORE_MISSING_FIELD_BLOCKS}}
        ===missing_fields_end===
        $OUTER_END
    """.trimIndent()

    private val WORKED_EXAMPLES = """
        [WORKED EXAMPLES]
        These examples use illustrative allowed values. Never reuse an example value unless it is supported by the current input payload.

        [EXAMPLE A: HIGH-CONFIDENCE ASR CORRECTION]
        Input transcript: "launch at foot corner for four hundred fifty taka yesterday"
        Input context: currentDateIso=2026-07-14, timezoneId=Asia/Dhaka, allowedTransactionTypes=[Income, Expense], allowedTagsByType={Expense:[Food]}
        Output:
        $OUTER_START
        ===title_start===
        Lunch at Food Corner
        ===title_end===
        ===amount_start===
        450
        ===amount_end===
        ===transaction_type_start===
        Expense
        ===transaction_type_end===
        ===tag_start===
        Food
        ===tag_end===
        ===tag_hint_start===
        ===tag_hint_end===
        ===date_iso_start===
        2026-07-13
        ===date_iso_end===
        ===note_start===
        ===note_end===
        ===missing_fields_start===
        ===missing_fields_end===
        $OUTER_END

        [EXAMPLE B: PRESERVE AMBIGUITY]
        Input transcript: "paid fifty at Zorple for blenko"
        Input context: currentDateIso=2026-07-14, timezoneId=Asia/Dhaka, allowedTransactionTypes=[Income, Expense], allowedTagsByType={Expense:[Food, Travel]}
        Output:
        $OUTER_START
        ===title_start===
        Zorple
        ===title_end===
        ===amount_start===
        50
        ===amount_end===
        ===transaction_type_start===
        Expense
        ===transaction_type_end===
        ===tag_start===
        ===tag_end===
        ===tag_hint_start===
        blenko
        ===tag_hint_end===
        ===date_iso_start===
        2026-07-14
        ===date_iso_end===
        ===note_start===
        ===note_end===
        ===missing_fields_start===
        ===missing_field_start===
        tag
        ===missing_field_end===
        ===missing_fields_end===
        $OUTER_END
    """.trimIndent()
}
