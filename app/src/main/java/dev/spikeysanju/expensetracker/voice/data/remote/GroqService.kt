package dev.spikeysanju.expensetracker.voice.data.remote

import dev.spikeysanju.expensetracker.voice.domain.VoiceExtractionParser
import dev.spikeysanju.expensetracker.voice.domain.VoiceExtractionValidationException
import dev.spikeysanju.expensetracker.voice.domain.VoicePromptFactory
import dev.spikeysanju.expensetracker.voice.domain.optDoubleOrNull
import dev.spikeysanju.expensetracker.voice.domain.optStringOrNull
import dev.spikeysanju.expensetracker.voice.debug.VoiceDebugTraceStore
import dev.spikeysanju.expensetracker.voice.model.SpeechTranscript
import dev.spikeysanju.expensetracker.voice.model.SpeechTranscriptSegment
import dev.spikeysanju.expensetracker.voice.model.VoiceExtractionContext
import dev.spikeysanju.expensetracker.voice.model.VoiceTransactionExtraction
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class GroqService private constructor(
    private val okHttpClient: OkHttpClient,
    private val baseUrl: HttpUrl
) {
    @Inject
    constructor(okHttpClient: OkHttpClient) : this(okHttpClient, DEFAULT_BASE_URL.toHttpUrl())

    internal constructor(okHttpClient: OkHttpClient, baseUrl: String) :
        this(okHttpClient, baseUrl.toHttpUrl())

    suspend fun transcribe(
        apiKey: String,
        audioFile: File,
        requestedLanguageCode: String?,
        prompt: String
    ): SpeechTranscript = withContext(Dispatchers.IO) {
        VoiceDebugTraceStore.append(
            section = "WHISPER REQUEST",
            details = buildString {
                appendLine("model=$TRANSCRIPTION_MODEL_ID")
                appendLine("fileName=${audioFile.name}")
                appendLine("audioByteCount=${audioFile.length()}")
                appendLine("language=${requestedLanguageCode ?: "auto-detect"}")
                appendLine("temperature=0")
                appendLine("responseFormat=verbose_json")
                append("prompt=$prompt")
            }
        )
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", TRANSCRIPTION_MODEL_ID)
            .addFormDataPart(
                "file",
                audioFile.name,
                audioFile.asRequestBody(WAV_MEDIA_TYPE)
            )
            .addFormDataPart("temperature", "0")
            .addFormDataPart("response_format", "verbose_json")
            .addFormDataPart("prompt", prompt)
            .apply {
                requestedLanguageCode?.takeIf { it.isNotBlank() }?.let {
                    addFormDataPart("language", it)
                }
            }
            .build()

        val request = Request.Builder()
            .url(endpoint("audio", "transcriptions"))
            .header("Authorization", bearerToken(apiKey))
            .post(requestBody)
            .build()

        val body = executeForBody(request, fallbackMessage = "Groq transcription failed.")
        VoiceDebugTraceStore.append("WHISPER RAW RESPONSE", body)
        val root = JSONObject(body)
        val segmentsJson = root.optJSONArray("segments")
        val segments = mutableListOf<SpeechTranscriptSegment>()
        if (segmentsJson != null) {
            for (index in 0 until segmentsJson.length()) {
                val segment = segmentsJson.optJSONObject(index) ?: continue
                val text = segment.optStringOrNull("text") ?: continue
                segments += SpeechTranscriptSegment(
                    startSeconds = segment.optDoubleOrNull("start"),
                    endSeconds = segment.optDoubleOrNull("end"),
                    text = text
                )
            }
        }

        val rawText = root.optStringOrNull("text")
            ?: throw VoiceApiException("Groq returned an empty transcript.")

        VoiceDebugTraceStore.append(
            section = "WHISPER PARSED TRANSCRIPT",
            details = buildString {
                appendLine("text=$rawText")
                appendLine("detectedLanguage=${root.optStringOrNull("language") ?: "unknown"}")
                append("durationSeconds=${root.optDoubleOrNull("duration") ?: "unknown"}")
            }
        )

        SpeechTranscript(
            rawText = rawText,
            requestedLanguageCode = requestedLanguageCode,
            detectedLanguageCode = root.optStringOrNull("language"),
            durationSeconds = root.optDoubleOrNull("duration"),
            segments = segments
        )
    }

    suspend fun extractTransaction(
        apiKey: String,
        modelId: String,
        transcript: String,
        context: VoiceExtractionContext
    ): VoiceTransactionExtraction = withContext(Dispatchers.IO) {
        if (transcript.isBlank()) {
            throw VoiceApiException("Groq extraction requires a non-empty transcript.")
        }
        if (modelId.isBlank()) {
            throw VoiceApiException("Groq extraction requires a selected model.")
        }

        val systemPrompt = VoicePromptFactory.buildExtractionSystemPrompt()
        val userPayload = VoicePromptFactory.buildExtractionUserPayload(transcript, context)
        val initialMessages = JSONArray()
            .put(message("system", systemPrompt))
            .put(message("user", userPayload))
        val initialBody = createChatCompletion(
            apiKey = apiKey,
            modelId = modelId,
            messages = initialMessages,
            attemptLabel = "INITIAL"
        )

        try {
            VoiceExtractionParser.parseResponse(initialBody, transcript, context).also { extraction ->
                VoiceDebugTraceStore.append("INITIAL PARSED TRANSACTION", extraction.toString())
            }
        } catch (firstError: VoiceExtractionValidationException) {
            VoiceDebugTraceStore.append(
                section = "INITIAL PARSER ERROR",
                details = firstError.message.orEmpty()
            )
            val correctionMessages = JSONArray()
                .put(message("system", systemPrompt))
                .put(message("user", userPayload))
                .put(
                    message(
                        "assistant",
                        firstError.responseContent
                            ?.takeIf(String::isNotBlank)
                            ?: "No usable extraction content was returned."
                    )
                )
                .put(
                    message(
                        "user",
                        VoicePromptFactory.buildExtractionCorrectionPrompt(firstError.message.orEmpty())
                    )
                )
            val correctedBody = createChatCompletion(
                apiKey = apiKey,
                modelId = modelId,
                messages = correctionMessages,
                attemptLabel = "CORRECTION"
            )
            try {
                VoiceExtractionParser.parseResponse(correctedBody, transcript, context).also { extraction ->
                    VoiceDebugTraceStore.append("CORRECTED PARSED TRANSACTION", extraction.toString())
                }
            } catch (secondError: VoiceExtractionValidationException) {
                VoiceDebugTraceStore.append(
                    section = "CORRECTION PARSER ERROR",
                    details = secondError.message.orEmpty()
                )
                throw VoiceApiException(
                    "Groq returned invalid transaction data after one correction attempt: " +
                        secondError.message.orEmpty()
                )
            }
        }
    }

    suspend fun testModelAccess(apiKey: String, modelId: String): Boolean =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank() || modelId.isBlank()) {
                return@withContext false
            }
            val request = Request.Builder()
                .url(endpoint("models"))
                .header("Authorization", bearerToken(apiKey))
                .get()
                .build()
            val body = executeForBody(request, fallbackMessage = "Groq model test failed.")
            val root = JSONObject(body)
            val models = root.optJSONArray("data") ?: return@withContext false
            (0 until models.length()).any { index ->
                val model = models.optJSONObject(index) ?: return@any false
                model.optStringOrNull("id")?.equals(modelId.trim(), ignoreCase = true) == true &&
                    model.optBoolean("active", true)
            }
        }

    private fun createChatCompletion(
        apiKey: String,
        modelId: String,
        messages: JSONArray,
        attemptLabel: String
    ): String {
        val requestJson = JSONObject()
            .put("model", modelId.trim())
            .put("stream", false)
            .put("temperature", 0)
            .put("messages", messages)
        VoiceDebugTraceStore.append(
            section = "$attemptLabel LLM REQUEST",
            details = requestJson.toString(2)
        )
        val request = Request.Builder()
            .url(endpoint("chat", "completions"))
            .header("Authorization", bearerToken(apiKey))
            .header("Content-Type", "application/json")
            .post(requestJson.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return executeForBody(request, fallbackMessage = "Groq extraction failed.").also { body ->
            VoiceDebugTraceStore.append("$attemptLabel LLM RAW RESPONSE", body)
        }
    }

    private fun message(role: String, content: String): JSONObject {
        return JSONObject().put("role", role).put("content", content)
    }

    private fun endpoint(vararg pathSegments: String): HttpUrl {
        return baseUrl.newBuilder().apply {
            pathSegments.forEach(::addPathSegment)
        }.build()
    }

    private fun bearerToken(apiKey: String) = "Bearer ${apiKey.trim()}"

    private fun executeForBody(request: Request, fallbackMessage: String): String {
        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                VoiceDebugTraceStore.append(
                    section = "GROQ HTTP ERROR",
                    details = buildString {
                        appendLine("method=${request.method}")
                        appendLine("url=${request.url}")
                        appendLine("status=${response.code}")
                        append("body=$body")
                    }
                )
                throw VoiceApiException(
                    parseErrorMessage(
                        responseBody = body,
                        statusCode = response.code,
                        fallbackMessage = fallbackMessage
                    )
                )
            }
            return body
        }
    }

    private fun parseErrorMessage(
        responseBody: String,
        statusCode: Int,
        fallbackMessage: String
    ): String {
        val detail = try {
            val root = JSONObject(responseBody)
            root.optJSONObject("error")?.optStringOrNull("message")
                ?: root.optStringOrNull("message")
        } catch (_: Exception) {
            null
        }
        val prefix = when (statusCode) {
            401 -> "Groq rejected the API key."
            403 -> "The selected Groq model is unavailable or blocked."
            404 -> "The selected Groq model was not found."
            429 -> "Groq rate limit reached."
            else -> fallbackMessage
        }
        return listOfNotNull(prefix, detail?.takeIf { it.isNotBlank() })
            .distinct()
            .joinToString(separator = " ")
    }

    private companion object {
        const val DEFAULT_BASE_URL = "https://api.groq.com/openai/v1/"
        const val TRANSCRIPTION_MODEL_ID = "whisper-large-v3"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        val WAV_MEDIA_TYPE = "audio/wav".toMediaType()
    }
}
