package dev.spikeysanju.expensetracker.voice.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.sqrt

class VoiceActivityDetector(
    private val onSpeechDetected: (ByteArray) -> Unit
) {
    private val detectorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var recordingJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private val ringBuffer = RingAudioBuffer(PRE_ROLL_MS, SAMPLE_RATE)
    private val speechFrames = mutableListOf<ByteArray>()

    private var isSpeechInProgress = false
    private var noiseFloorRms = DEFAULT_NOISE_FLOOR_RMS

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    fun initialize() {
        if (audioRecord?.state == AudioRecord.STATE_INITIALIZED) {
            return
        }

        releaseAudioRecord()

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        require(minBufferSize > 0) {
            "Microphone is unavailable on this device."
        }

        val bufferSize = max(minBufferSize * 2, FRAME_SIZE * 4)
        audioRecord = AUDIO_SOURCES.firstNotNullOfOrNull { audioSource ->
            createAudioRecord(audioSource = audioSource, bufferSize = bufferSize)
        }

        require(audioRecord != null) {
            "Audio recorder initialization failed. Check microphone permission and emulator microphone availability."
        }
    }

    @SuppressLint("MissingPermission")
    fun startListening() {
        if (_isListening.value) {
            return
        }

        initialize()
        speechFrames.clear()
        ringBuffer.clear()
        isSpeechInProgress = false
        noiseFloorRms = DEFAULT_NOISE_FLOOR_RMS
        _isListening.value = true
        _isSpeaking.value = false

        try {
            audioRecord?.startRecording()
        } catch (error: IllegalStateException) {
            stopRecorder()
            releaseAudioRecord()
            throw IllegalStateException(
                "Unable to start microphone recording.",
                error
            )
        }

        if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            stopRecorder()
            releaseAudioRecord()
            throw IllegalStateException(
                "Microphone recording could not be started. Check emulator microphone availability."
            )
        }

        recordingJob = detectorScope.launch {
            val frame = ShortArray(FRAME_SIZE)
            var pendingAudio: ByteArray? = null
            var consecutiveSpeechFrames = 0
            var silenceStartTime = 0L

            while (_isListening.value) {
                val read = audioRecord?.read(frame, 0, FRAME_SIZE) ?: break
                if (read <= 0) {
                    continue
                }

                ringBuffer.write(frame, read)
                val isSpeechFrame = isSpeechFrame(frame, read)

                when {
                    isSpeechFrame && !isSpeechInProgress -> {
                        consecutiveSpeechFrames += 1
                        if (consecutiveSpeechFrames >= SPEECH_START_FRAME_COUNT) {
                            isSpeechInProgress = true
                            silenceStartTime = 0L
                            speechFrames.clear()
                            val preRoll = ringBuffer.readAll()
                            if (preRoll.isNotEmpty()) {
                                speechFrames += shortsToBytes(preRoll, preRoll.size)
                            } else {
                                speechFrames += shortsToBytes(frame, read)
                            }
                            _isSpeaking.value = true
                        }
                    }

                    isSpeechFrame && isSpeechInProgress -> {
                        silenceStartTime = 0L
                        speechFrames += shortsToBytes(frame, read)
                        _isSpeaking.value = true
                    }

                    !isSpeechFrame && isSpeechInProgress -> {
                        speechFrames += shortsToBytes(frame, read)
                        _isSpeaking.value = false
                        if (silenceStartTime == 0L) {
                            silenceStartTime = System.currentTimeMillis()
                        } else if (System.currentTimeMillis() - silenceStartTime >= SILENCE_TIMEOUT_MS) {
                            pendingAudio = concatenate(speechFrames)
                            break
                        }
                    }

                    else -> {
                        consecutiveSpeechFrames = 0
                        _isSpeaking.value = false
                    }
                }
            }

            stopRecorder()
            pendingAudio?.let(onSpeechDetected)
        }
    }

    fun stopListening(finalizePendingSpeech: Boolean = false): Boolean {
        if (!_isListening.value) {
            return false
        }
        recordingJob?.cancel()
        val pendingAudio = if (finalizePendingSpeech && speechFrames.isNotEmpty()) {
            concatenate(speechFrames)
        } else {
            null
        }
        stopRecorder()
        speechFrames.clear()
        ringBuffer.clear()
        pendingAudio?.let(onSpeechDetected)
        return pendingAudio != null
    }

    fun destroy() {
        stopListening()
        detectorScope.cancel()
        releaseAudioRecord()
    }

    private fun stopRecorder() {
        _isListening.value = false
        _isSpeaking.value = false
        isSpeechInProgress = false
        recordingJob = null
        try {
            audioRecord?.stop()
        } catch (_: IllegalStateException) {
        }
    }

    private fun createAudioRecord(audioSource: Int, bufferSize: Int): AudioRecord? {
        val record = try {
            AudioRecord(
                audioSource,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        } catch (_: Throwable) {
            return null
        }

        return if (record.state == AudioRecord.STATE_INITIALIZED) {
            record
        } else {
            record.release()
            null
        }
    }

    private fun releaseAudioRecord() {
        audioRecord?.release()
        audioRecord = null
    }

    private fun isSpeechFrame(frame: ShortArray, length: Int): Boolean {
        val rms = computeRms(frame, length)
        if (!isSpeechInProgress) {
            noiseFloorRms = (noiseFloorRms * NOISE_FLOOR_SMOOTHING) +
                (rms * (1 - NOISE_FLOOR_SMOOTHING))
        }
        val threshold = max(MIN_RMS_THRESHOLD, noiseFloorRms * SPEECH_THRESHOLD_MULTIPLIER)
        return rms >= threshold
    }

    private fun computeRms(frame: ShortArray, length: Int): Double {
        if (length == 0) {
            return 0.0
        }
        var sum = 0.0
        for (index in 0 until length) {
            val value = frame[index].toDouble()
            sum += value * value
        }
        return sqrt(sum / length)
    }

    private fun shortsToBytes(samples: ShortArray, length: Int): ByteArray {
        val bytes = ByteArray(length * 2)
        var byteIndex = 0
        for (index in 0 until length) {
            val sample = samples[index].toInt()
            bytes[byteIndex++] = (sample and 0xFF).toByte()
            bytes[byteIndex++] = (sample shr 8 and 0xFF).toByte()
        }
        return bytes
    }

    private fun concatenate(frames: List<ByteArray>): ByteArray {
        val totalSize = frames.sumOf { it.size }
        val result = ByteArray(totalSize)
        var offset = 0
        frames.forEach { frame ->
            System.arraycopy(frame, 0, result, offset, frame.size)
            offset += frame.size
        }
        return result
    }

    companion object {
        const val SAMPLE_RATE = 16000
        const val FRAME_SIZE = 320
        const val SILENCE_TIMEOUT_MS = 1200L
        private val AUDIO_SOURCES = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC,
            MediaRecorder.AudioSource.DEFAULT
        )
        private const val PRE_ROLL_MS = 300
        private const val SPEECH_START_FRAME_COUNT = 3
        private const val DEFAULT_NOISE_FLOOR_RMS = 350.0
        private const val MIN_RMS_THRESHOLD = 900.0
        private const val NOISE_FLOOR_SMOOTHING = 0.92
        private const val SPEECH_THRESHOLD_MULTIPLIER = 2.2
    }
}
