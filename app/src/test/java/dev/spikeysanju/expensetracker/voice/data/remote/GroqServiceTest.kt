package dev.spikeysanju.expensetracker.voice.data.remote

import dev.spikeysanju.expensetracker.voice.model.VoiceExtractionContext
import java.io.File
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GroqServiceTest {
    private lateinit var server: MockWebServer
    private lateinit var service: GroqService

    private val context = VoiceExtractionContext(
        currentDateIso = "2026-07-14",
        currentDateDisplay = "14/07/2026",
        timezoneId = "Asia/Dhaka",
        speechLanguageCode = "en",
        speechLanguageLabel = "English",
        selectedCurrencyCode = "BDT",
        selectedCurrencySymbol = "৳",
        allowedTransactionTypes = listOf("Income", "Expense"),
        allowedTagsByType = mapOf("Expense" to listOf("Food"), "Income" to listOf("Work"))
    )

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        service = GroqService(
            okHttpClient = OkHttpClient(),
            baseUrl = server.url("/openai/v1/").toString()
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `chat completion sends model agnostic delimiter request`() = runBlocking {
        server.enqueue(completionResponse(validContent(title = "Lunch", amount = "12")))

        val extraction = service.extractTransaction(
            apiKey = "gsk_test",
            modelId = "openai/gpt-oss-120b",
            transcript = "lunch 12",
            context = context
        )

        assertEquals("Lunch", extraction.title)
        val request = server.takeRequest()
        assertEquals("/openai/v1/chat/completions", request.path)
        assertEquals("Bearer gsk_test", request.getHeader("Authorization"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals("openai/gpt-oss-120b", body.getString("model"))
        assertEquals(0.0, body.getDouble("temperature"), 0.0)
        assertFalse(body.getBoolean("stream"))
        assertEquals(2, body.getJSONArray("messages").length())
        assertFalse(body.has("response_format"))
        assertFalse(body.has("tools"))
        assertFalse(body.has("reasoning_format"))
        assertFalse(body.has("reasoning_effort"))
    }

    @Test
    fun `invalid output receives one corrective retry`() = runBlocking {
        server.enqueue(completionResponse("not tagged"))
        server.enqueue(completionResponse(validContent(title = "Corrected", amount = "7")))

        val extraction = service.extractTransaction(
            apiKey = "key",
            modelId = "qwen/qwen3-32b",
            transcript = "correct this",
            context = context
        )

        assertEquals("Corrected", extraction.title)
        server.takeRequest()
        val retry = JSONObject(server.takeRequest().body.readUtf8())
        val messages = retry.getJSONArray("messages")
        assertEquals(4, messages.length())
        assertEquals("assistant", messages.getJSONObject(2).getString("role"))
        assertEquals("not tagged", messages.getJSONObject(2).getString("content"))
        assertEquals("user", messages.getJSONObject(3).getString("role"))
        assertTrue(messages.getJSONObject(3).getString("content").contains("Validation error"))
    }

    @Test
    fun `second malformed output fails without a third request`() {
        server.enqueue(completionResponse("bad one"))
        server.enqueue(completionResponse("bad two"))

        val error = assertThrows(VoiceApiException::class.java) {
            runBlocking {
                service.extractTransaction("key", "custom/model", "spoken", context)
            }
        }

        assertTrue(error.message.orEmpty().contains("after one correction attempt"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `model lookup encodes slash and validates returned id`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                JSONObject()
                    .put("id", "openai/gpt-oss-120b")
                    .put("active", true)
                    .toString()
            )
        )

        assertTrue(service.testModelAccess("key", "openai/gpt-oss-120b"))
        assertEquals(
            "/openai/v1/models/openai%2Fgpt-oss-120b",
            server.takeRequest().path
        )
    }

    @Test
    fun `transcription uses multipart verbose json contract`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                JSONObject()
                    .put("text", "Lunch twelve")
                    .put("language", "en")
                    .put("duration", 1.5)
                    .put(
                        "segments",
                        JSONArray().put(
                            JSONObject()
                                .put("start", 0)
                                .put("end", 1.5)
                                .put("text", "Lunch twelve")
                        )
                    )
                    .toString()
            )
        )
        val audioFile = File.createTempFile("voice", ".wav").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }

        val transcript = service.transcribe("key", audioFile, "en", "Finance prompt")

        assertEquals("Lunch twelve", transcript.rawText)
        val request = server.takeRequest()
        assertEquals("/openai/v1/audio/transcriptions", request.path)
        assertTrue(request.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data; boundary="))
        val body = request.body.readUtf8()
        assertTrue(body.contains("name=\"model\""))
        assertTrue(body.contains("whisper-large-v3"))
        assertTrue(body.contains("name=\"response_format\""))
        assertTrue(body.contains("verbose_json"))
        assertTrue(body.contains("name=\"language\""))
    }

    @Test
    fun `groq error envelope maps status without retry`() {
        server.enqueue(
            MockResponse().setResponseCode(403).setBody(
                JSONObject()
                    .put(
                        "error",
                        JSONObject()
                            .put("message", "Model blocked")
                            .put("type", "invalid_request_error")
                    )
                    .toString()
            )
        )

        val error = assertThrows(VoiceApiException::class.java) {
            runBlocking { service.testModelAccess("key", "blocked/model") }
        }

        assertTrue(error.message.orEmpty().contains("unavailable or blocked"))
        assertTrue(error.message.orEmpty().contains("Model blocked"))
        assertEquals(1, server.requestCount)
    }

    private fun completionResponse(content: String): MockResponse {
        val body = JSONObject()
            .put(
                "choices",
                JSONArray().put(
                    JSONObject()
                        .put("finish_reason", "stop")
                        .put(
                            "message",
                            JSONObject()
                                .put("role", "assistant")
                                .put("content", content)
                        )
                )
            )
            .toString()
        return MockResponse().setResponseCode(200).setBody(body)
    }

    private fun validContent(
        title: String = "",
        amount: String = "",
        type: String = "",
        tag: String = "",
        tagHint: String = "",
        date: String = "",
        note: String = ""
    ): String = """
        ===voice_transaction_extraction_start===
        ===title_start===
        $title
        ===title_end===
        ===amount_start===
        $amount
        ===amount_end===
        ===transaction_type_start===
        $type
        ===transaction_type_end===
        ===tag_start===
        $tag
        ===tag_end===
        ===tag_hint_start===
        $tagHint
        ===tag_hint_end===
        ===date_iso_start===
        $date
        ===date_iso_end===
        ===note_start===
        $note
        ===note_end===
        ===missing_fields_start===
        ===missing_fields_end===
        ===voice_transaction_extraction_end===
    """.trimIndent()
}
