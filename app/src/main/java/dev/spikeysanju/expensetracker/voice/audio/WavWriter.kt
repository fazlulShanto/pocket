package dev.spikeysanju.expensetracker.voice.audio

import java.io.File
import java.io.FileOutputStream

object WavWriter {
    fun write(
        pcmData: ByteArray,
        sampleRate: Int,
        file: File
    ) {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val header = ByteArray(44)

        "RIFF".toByteArray().copyInto(header, destinationOffset = 0)
        putInt(header, offset = 4, value = dataSize + 36)
        "WAVE".toByteArray().copyInto(header, destinationOffset = 8)
        "fmt ".toByteArray().copyInto(header, destinationOffset = 12)
        putInt(header, offset = 16, value = 16)
        putShort(header, offset = 20, value = 1)
        putShort(header, offset = 22, value = channels)
        putInt(header, offset = 24, value = sampleRate)
        putInt(header, offset = 28, value = byteRate)
        putShort(header, offset = 32, value = blockAlign)
        putShort(header, offset = 34, value = bitsPerSample)
        "data".toByteArray().copyInto(header, destinationOffset = 36)
        putInt(header, offset = 40, value = dataSize)

        FileOutputStream(file).use { output ->
            output.write(header)
            output.write(pcmData)
        }
    }

    private fun putInt(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = (value shr 8 and 0xFF).toByte()
        buffer[offset + 2] = (value shr 16 and 0xFF).toByte()
        buffer[offset + 3] = (value shr 24 and 0xFF).toByte()
    }

    private fun putShort(buffer: ByteArray, offset: Int, value: Int) {
        buffer[offset] = (value and 0xFF).toByte()
        buffer[offset + 1] = (value shr 8 and 0xFF).toByte()
    }
}
