package dev.spikeysanju.expensetracker.voice.domain

import org.json.JSONArray
import org.json.JSONObject

object VoiceJsonSchemaFactory {
    fun createExtractionSchema(
        allowedTransactionTypes: List<String>,
        allowedTags: Collection<String>
    ): JSONObject {
        val normalizedTransactionTypes = allowedTransactionTypes
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
        val normalizedTags = allowedTags
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .sorted()

        return JSONObject()
            .put("type", "object")
            .put("additionalProperties", false)
            .put(
                "required",
                jsonArrayOf(
                    "title",
                    "amount",
                    "transactionType",
                    "tag",
                    "tagHint",
                    "dateIso",
                    "note",
                    "missingFields",
                    "rawTranscript"
                )
            )
            .put(
                "properties",
                JSONObject()
                    .put(
                        "title",
                        nullableStringSchema(
                            "Concise transaction summary in the speaker's language without translation."
                        )
                    )
                    .put(
                        "amount",
                        nullableNumberSchema(
                            "Positive number only with no currency symbol."
                        )
                    )
                    .put(
                        "transactionType",
                        nullableEnumSchema(
                            normalizedTransactionTypes,
                            "Return Income, Expense, or null."
                        )
                    )
                    .put(
                        "tag",
                        nullableEnumSchema(
                            normalizedTags,
                            "Must match one allowed tag exactly or be null."
                        )
                    )
                    .put(
                        "tagHint",
                        nullableStringSchema(
                            "Preserve the unmatched spoken category phrase when tag is null."
                        )
                    )
                    .put(
                        "dateIso",
                        nullableDateSchema(
                            "Date in yyyy-MM-dd using the user's local timezone. Use the provided current date when no explicit date is spoken."
                        )
                    )
                    .put(
                        "note",
                        nullableStringSchema(
                            "Extra spoken context not already captured by title, amount, tag, or date."
                        )
                    )
                    .put(
                        "missingFields",
                        JSONObject()
                            .put("type", "array")
                            .put(
                                "items",
                                JSONObject()
                                    .put("type", "string")
                                    .put(
                                        "enum",
                                        jsonArrayOf("title", "amount", "transactionType", "tag", "dateIso")
                                    )
                            )
                    )
                    .put(
                        "rawTranscript",
                        JSONObject()
                            .put("type", "string")
                            .put("description", "Echo the transcript text exactly.")
                    )
            )
    }

    private fun nullableStringSchema(description: String): JSONObject {
        return JSONObject()
            .put("description", description)
            .put(
                "oneOf",
                JSONArray()
                    .put(JSONObject().put("type", "string"))
                    .put(JSONObject().put("type", "null"))
            )
    }

    private fun nullableNumberSchema(description: String): JSONObject {
        return JSONObject()
            .put("description", description)
            .put(
                "oneOf",
                JSONArray()
                    .put(JSONObject().put("type", "number").put("exclusiveMinimum", 0))
                    .put(JSONObject().put("type", "null"))
            )
    }

    private fun nullableEnumSchema(values: List<String>, description: String): JSONObject {
        if (values.isEmpty()) {
            return nullableStringSchema(description)
        }
        return JSONObject()
            .put("description", description)
            .put(
                "oneOf",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("type", "string")
                            .put("enum", JSONArray(values))
                    )
                    .put(JSONObject().put("type", "null"))
            )
    }

    private fun nullableDateSchema(description: String): JSONObject {
        return JSONObject()
            .put("description", description)
            .put(
                "oneOf",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("type", "string")
                            .put("pattern", "^\\d{4}-\\d{2}-\\d{2}$")
                    )
                    .put(JSONObject().put("type", "null"))
            )
    }

    private fun jsonArrayOf(vararg values: String): JSONArray {
        return JSONArray().apply {
            values.forEach(::put)
        }
    }
}
