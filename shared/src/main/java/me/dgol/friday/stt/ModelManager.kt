// shared/src/main/java/me/dgol/friday/shared/stt/ModelManager.kt
package me.dgol.friday.shared.stt

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object ModelManager {

    private const val LANG_DEFAULT = "en-us"
    private const val MODEL_ZIP =
        "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"

    private fun baseDir(ctx: Context) = File(ctx.filesDir, "vosk")
    private fun langDir(ctx: Context, lang: String) = File(baseDir(ctx), lang)

    /** Scan for the actual model root (dir that contains conf/model.conf) */
    private fun findModelRoot(dir: File): File? {
        dir.walkTopDown().maxDepth(4).forEach { f ->
            if (File(f, "conf/model.conf").exists()) return f
        }
        return null
    }

    /** New API: returns true if a valid model is present. */
    fun isModelReady(ctx: Context, lang: String = LANG_DEFAULT): Boolean =
        findModelRoot(langDir(ctx, lang)) != null

    fun clearModel(ctx: Context, lang: String = "en-us") {
        File(ctx.filesDir, "vosk/$lang").deleteRecursively()
    }

    /** New API: download if needed and return the root path for org.vosk.Model(...) */
    suspend fun ensureModel(ctx: Context, lang: String = LANG_DEFAULT): String =
        withContext(Dispatchers.IO) {
            val dest = langDir(ctx, lang)
            if (!isModelReady(ctx, lang)) {
                // clean target then download+unzip
                dest.deleteRecursively()
                dest.mkdirs()
                val tmpZip = File.createTempFile("vosk-$lang", ".zip", ctx.cacheDir)
                try {
                    URL(MODEL_ZIP).openStream().use { input ->
                        FileOutputStream(tmpZip).use { output -> input.copyTo(output) }
                    }
                    unzip(tmpZip, dest)
                } finally {
                    tmpZip.delete()
                }
            }
            val root = findModelRoot(dest)
                ?: error("Vosk model extracted but conf/model.conf not found")
            root.absolutePath
        }

    private fun unzip(zipFile: File, destDir: File) {
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val out = File(destDir, entry.name)
                if (entry.isDirectory) {
                    out.mkdirs()
                } else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { fos -> zis.copyTo(fos) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    // ---- Compatibility shims (so your existing code keeps working) ----

    /** Old name: download the default model (returns its root path) */
    suspend fun downloadDefaultModel(ctx: Context): String = ensureModel(ctx, LANG_DEFAULT)

    /** Old helper: path to the language folder (not necessarily the model root) */
    fun modelDir(ctx: Context, lang: String = LANG_DEFAULT): File = langDir(ctx, lang)

    /** Convenience: returns the actual root path if present, else null */
    fun modelPathOrNull(ctx: Context, lang: String = LANG_DEFAULT): String? =
        findModelRoot(langDir(ctx, lang))?.absolutePath
}
