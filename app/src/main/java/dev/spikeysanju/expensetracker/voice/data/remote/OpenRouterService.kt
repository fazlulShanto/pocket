package dev.spikeysanju.expensetracker.voice.data.remote

import dev.spikeysanju.expensetracker.voice.domain.OpenRouterModelParser
import dev.spikeysanju.expensetracker.voice.domain.VoiceExtractionParser
import dev.spikeysanju.expensetracker.voice.domain.VoiceJsonSchemaFactory
import dev.spikeysanju.expensetracker.voice.domain.VoicePromptFactory
import dev.spikeysanju.expensetracker.voice.domain.optStringOrNull
import dev.spikeysanju.expensetracker.voice.model.OpenRouterModelOption
import dev.spikeysanju.expensetracker.voice.model.VoiceExtractionContext
import dev.spikeysanju.expensetracker.voice.model.VoiceTransactionExtraction
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class OpenRouterService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun fetchCompatibleModels(apiKey: String): List<OpenRouterModelOption> =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$BASE_URL/models?supported_parameters=structured_outputs")
                .header("Authorization", "Bearer ${apiKey.trim()}")
                .get()
                .build()

            OpenRouterModelParser.parseCompatibleModels(
                executeForBody(request, fallbackMessage = "OpenRouter model refresh failed.")
            )
        }

    suspend fun extractTransaction(
        apiKey: String,
        modelId: String,
        transcript: String,
        context: VoiceExtractionContext
    ): VoiceTransactionExtraction = withContext(Dispatchers.IO) {
        if (transcript.isBlank()) {
            throw VoiceApiException("OpenRouter extraction requires a non-empty transcript.")
        }
        if (modelId.isBlank()) {
            throw VoiceApiException("OpenRouter extraction requires a selected model.")
        }

        val requestJson = JSONObject()
            .put("model", modelId)
            .put("stream", false)
            .put("temperature", 0)
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put("content", VoicePromptFactory.buildExtractionSystemPrompt())
                    )
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", VoicePromptFactory.buildExtractionUserPayload(transcript, context))
                    )
            )
            .put(
                "response_format",
                JSONObject()
                    .put("type", "json_schema")
                    .put(
                        "json_schema",
                        JSONObject()
                            .put("name", "voice_transaction_extraction")
                            .put("strict", true)
                            .put(
                                "schema",
                                VoiceJsonSchemaFactory.createExtractionSchema(
                                    allowedTransactionTypes = context.allowedTransactionTypes,
                                    allowedTags = context.allowedTagsByType.values.flatten().distinct()
                                )
                            )
                    )
            )

        val request = Request.Builder()
            .url("$BASE_URL/chat/completions")
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .header("Content-Type", "application/json")
            .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        VoiceExtractionParser.parseResponse(
            responseBody = executeForBody(request, fallbackMessage = "OpenRouter extraction failed."),
            rawTranscript = transcript
        )
    }

    suspend fun testConnection(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/models?supported_parameters=structured_outputs")
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            response.isSuccessful
        }
    }

    private fun executeForBody(request: Request, fallbackMessage: String): String {
        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw VoiceApiException(parseErrorMessage(body, fallbackMessage))
            }
            return body
        }
    }

    private fun parseErrorMessage(responseBody: String, fallbackMessage: String): String {
        return try {
            val root = JSONObject(responseBody)
            root.optJSONObject("error")?.optStringOrNull("message")
                ?: root.optStringOrNull("message")
                ?: fallbackMessage
        } catch (_: Exception) {
            fallbackMessage
        }
    }

    private companion object {
        const val BASE_URL = "https://openrouter.ai/api/v1"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
