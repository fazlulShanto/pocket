package dev.spikeysanju.expensetracker.voice.data.remote

import dev.spikeysanju.expensetracker.voice.domain.optDoubleOrNull
import dev.spikeysanju.expensetracker.voice.domain.optStringOrNull
import dev.spikeysanju.expensetracker.voice.model.SpeechTranscript
import dev.spikeysanju.expensetracker.voice.model.SpeechTranscriptSegment
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject

@Singleton
class GroqWhisperService @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    suspend fun transcribe(
        apiKey: String,
        audioFile: File,
        requestedLanguageCode: String?,
        prompt: String
    ): SpeechTranscript = withContext(Dispatchers.IO) {
        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", MODEL_ID)
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
            .url("$BASE_URL/audio/transcriptions")
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .post(requestBody)
            .build()

        val body = executeForBody(request, fallbackMessage = "Groq transcription failed.")
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

        SpeechTranscript(
            rawText = rawText,
            requestedLanguageCode = requestedLanguageCode,
            detectedLanguageCode = root.optStringOrNull("language"),
            durationSeconds = root.optDoubleOrNull("duration"),
            segments = segments
        )
    }

    suspend fun testConnection(apiKey: String): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$BASE_URL/models")
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
        const val BASE_URL = "https://api.groq.com/openai/v1"
        const val MODEL_ID = "whisper-large-v3"
        val WAV_MEDIA_TYPE = "audio/wav".toMediaType()
    }
}
