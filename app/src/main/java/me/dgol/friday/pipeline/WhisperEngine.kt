package me.dgol.friday.pipeline

import android.content.Context
import java.io.File

class WhisperEngine(private val context: Context) {

    private var contextPointer: Long = 0

    //Declares the native functions implemented in jni.c
    private external fun initContext(modelPath: String): Long
    private external fun freeContext(contextPtr: Long)
    private external fun transcribe(contextPtr: Long, audioPath: FloatArray): String

    companion object {
        init {
            System.loadLibrary("whisper")
        }
    }

    fun load() {
        val modelFile = copyModelToCache()
        contextPointer = initContext(modelFile.absolutePath)
    }

    fun transcribe(audioData: FloatArray): String {
        if (contextPointer == 0L) error("WhisperEngine not loaded")
        return transcribe(contextPointer, audioData)
    }

    fun release() {
        if (contextPointer != 0L) {
            freeContext(contextPointer)
            contextPointer = 0
        }
    }

    private fun copyModelToCache(): File {
        val modelName = "ggml-base.en.bin"
        val outFile = File(context.cacheDir, modelName)
        if (!outFile.exists()) {
            context.assets.open("models/whisper/$modelName").use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return outFile
    }
}