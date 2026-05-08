package dev.spikeysanju.expensetracker.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.spikeysanju.expensetracker.voice.data.local.EncryptedVoiceConfigStore
import dev.spikeysanju.expensetracker.voice.data.local.VoiceConfigStore
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class VoiceModule {
    @Binds
    @Singleton
    abstract fun bindVoiceConfigStore(
        encryptedVoiceConfigStore: EncryptedVoiceConfigStore
    ): VoiceConfigStore

    companion object {
        @Provides
        @Singleton
        fun provideOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .callTimeout(75, TimeUnit.SECONDS)
                .build()
        }
    }
}
