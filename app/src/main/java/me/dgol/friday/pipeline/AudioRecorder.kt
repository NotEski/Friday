package me.dgol.friday.pipeline

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AudioRecorder(private val context: Context) {

    companion object {
        const val SAMPLE_RATE = 16000 // Whispers required amount
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        const val SILENCE_THRESHOLD = 500 // amplitude threshold for silence detection
        const val SILENCE_DURATION_MS = 1500L // stop after 1.5 seconds of silence
        const val MAX_DURATION_MS = 10000L // 10 seconds max duration
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun recordUntilSilence(): FloatArray = withContext(Dispatchers.IO) {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT
        )

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufferSize
        )

        val audioBuffer = mutableListOf<Short>()
        val readBuffer = ShortArray(bufferSize)
        var silenceStart = -1L
        val startTime = System.currentTimeMillis()

        recorder.startRecording()

        try {
            while (true) {
                val read = recorder.read(readBuffer, 0, readBuffer.size)
                if (read > 0) {
                    audioBuffer.addAll(readBuffer.take(read))

                    // Check amplitude for silence detection
                    val amplitude = readBuffer.take(read).maxOf { Math.abs(it.toInt()) }
                    val now = System.currentTimeMillis()

                    if (amplitude < SILENCE_THRESHOLD) {
                        if (silenceStart == -1L) silenceStart = now
                        else if (now - silenceStart >= SILENCE_DURATION_MS) break
                    } else {
                        silenceStart = -1L
                    }

                    // Hard cap
                    if (now - startTime >= MAX_DURATION_MS) break
                }
            }
        }
        finally {
            recorder.stop()
            recorder.release()
        }

        audioBuffer.map { it.toFloat() / 32767.0f }.toFloatArray()
    }

}