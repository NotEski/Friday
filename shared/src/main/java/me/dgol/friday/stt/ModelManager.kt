package me.dgol.friday.shared.stt

import android.content.Context
import kotlinx.coroutines.*
import java.io.File
import java.net.URL
import java.util.zip.ZipInputStream

object ModelManager {

    /** Where we keep models: /data/data/.../files/vosk/en-us */
    private fun modelDir(ctx: Context, lang: String = "en-us"): File =
        File(ctx.filesDir, "vosk/$lang")

    /** Returns true if the chosen model is already un-zipped and ready */
    fun isModelReady(ctx: Context, lang: String = "en-us"): Boolean =
        File(modelDir(ctx, lang), "model.conf").exists()

    /**
     * Download & unzip the official small EN model.
     * Call from a coroutine / WorkManager.
     */
    suspend fun downloadDefaultModel(ctx: Context) {
        val url =
            "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
        val targetDir = modelDir(ctx)

        if (targetDir.exists()) return                    // Already done
        targetDir.mkdirs()

        withContext(Dispatchers.IO) {
            URL(url).openStream().use { input ->
                ZipInputStream(input).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val outFile = File(targetDir, entry.name)
                        if (entry.isDirectory) {
                            outFile.mkdirs()
                        } else {
                            outFile.outputStream().use { zip.copyTo(it) }
                        }
                        entry = zip.nextEntry
                    }
                }
            }
        }
    }

    /** Path to feed into `Model(...)` */
    fun modelPath(ctx: Context, lang: String = "en-us") =
        modelDir(ctx, lang).absolutePath
}
