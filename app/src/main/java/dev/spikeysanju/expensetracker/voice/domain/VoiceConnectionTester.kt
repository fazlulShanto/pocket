package dev.spikeysanju.expensetracker.voice.domain

import dev.spikeysanju.expensetracker.voice.data.remote.GroqWhisperService
import dev.spikeysanju.expensetracker.voice.data.remote.OpenRouterService
import dev.spikeysanju.expensetracker.voice.model.VoiceConnectionTestResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VoiceConnectionTester @Inject constructor(
    private val groqWhisperService: GroqWhisperService,
    private val openRouterService: OpenRouterService
) {
    suspend fun testConnections(
        groqApiKey: String,
        openRouterApiKey: String
    ): VoiceConnectionTestResult {
        val sanitizedGroqKey = groqApiKey.trim()
        val sanitizedOpenRouterKey = openRouterApiKey.trim()

        return VoiceConnectionTestResult(
            groqReachable = sanitizedGroqKey.isNotBlank() && groqWhisperService.testConnection(sanitizedGroqKey),
            openRouterReachable = sanitizedOpenRouterKey.isNotBlank() && openRouterService.testConnection(sanitizedOpenRouterKey)
        )
    }
}
