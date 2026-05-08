package dev.spikeysanju.expensetracker.voice.audio

class RingAudioBuffer(
    maxSizeMs: Int,
    sampleRate: Int
) {
    private val maxSamples = maxSizeMs * sampleRate / 1000
    private val buffer = ShortArray(maxSamples)
    private var writePosition = 0
    private var isFull = false

    fun write(samples: ShortArray, length: Int = samples.size) {
        var written = 0
        while (written < length) {
            val remaining = maxSamples - writePosition
            val copyLength = minOf(remaining, length - written)
            System.arraycopy(samples, written, buffer, writePosition, copyLength)
            writePosition += copyLength
            written += copyLength
            if (writePosition >= maxSamples) {
                writePosition = 0
                isFull = true
            }
        }
    }

    fun readAll(): ShortArray {
        if (!isFull) {
            return if (writePosition == 0) {
                ShortArray(0)
            } else {
                buffer.copyOf(writePosition)
            }
        }

        val result = ShortArray(maxSamples)
        System.arraycopy(buffer, writePosition, result, 0, maxSamples - writePosition)
        System.arraycopy(buffer, 0, result, maxSamples - writePosition, writePosition)
        return result
    }

    fun clear() {
        writePosition = 0
        isFull = false
        buffer.fill(0)
    }
}
