package dev.spikeysanju.expensetracker.voice.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.spikeysanju.expensetracker.voice.model.VoiceSettingsConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedVoiceConfigStore @Inject constructor(
    @ApplicationContext context: Context
) : VoiceConfigStore {

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override suspend fun getConfig(): VoiceSettingsConfig = withContext(Dispatchers.IO) {
        val snapshot = VoiceConfigPreferencesMapper.allKeys.associateWith { key ->
            sharedPreferences.getString(key, null)
        }
        VoiceConfigPreferencesMapper.fromPreferenceSnapshot(snapshot).also { config ->
            if (VoiceConfigPreferencesMapper.requiresMigration(snapshot)) {
                writeConfig(config)
            }
        }
    }

    override suspend fun saveConfig(config: VoiceSettingsConfig) = withContext(Dispatchers.IO) {
        writeConfig(config)
    }

    private fun writeConfig(config: VoiceSettingsConfig) {
        val snapshot = VoiceConfigPreferencesMapper.toPreferenceSnapshot(config)
        sharedPreferences.edit().apply {
            VoiceConfigPreferencesMapper.allKeys.forEach(::remove)
            snapshot.forEach { (key, value) ->
                if (value == null) {
                    remove(key)
                } else {
                    putString(key, value)
                }
            }
        }.apply()
    }

    private companion object {
        const val FILE_NAME = "voice_settings_secure_prefs"
    }
}
