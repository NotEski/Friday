package me.dgol.friday.pipeline

import android.Manifest
import android.content.Context
import androidx.annotation.RequiresPermission

class VoicePipeline(private val context: Context) {

    private val audioRecorder = AudioRecorder(context)
    private val whisperEngine = WhisperEngine(context)

    private var isLoaded = false

    fun load() {
        if (!isLoaded) {
            whisperEngine.load()
            isLoaded = true
        }
    }

    fun release() {
        whisperEngine.release()
        isLoaded = false
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    suspend fun listen(): String {
        val audio = audioRecorder.recordUntilSilence()
        return whisperEngine.transcribe(audio)
    }
}