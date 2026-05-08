package dev.spikeysanju.expensetracker.voice.data.local

import dev.spikeysanju.expensetracker.voice.model.VoiceSettingsConfig

interface VoiceConfigStore {
    suspend fun getConfig(): VoiceSettingsConfig

    suspend fun saveConfig(config: VoiceSettingsConfig)
}
