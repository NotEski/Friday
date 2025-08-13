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

/**
 * Vosk model lifecycle helper.
 *
 * Layout on disk (per language):
 *   <filesDir>/vosk/<lang>/  ...unzipped model...
 * The actual recognizer root is the folder that contains conf/model.conf.
 */
object ModelManager {

    // ---- Catalog ----
    // Add more entries as you need. Labels are user-facing; sizes are informational only.
    data class ModelInfo(
        val lang: String,
        val label: String,
        val url: String,
        val approxSizeMb: Int
    )

    private const val LANG_DEFAULT = "en-us"

    private val MODEL_CATALOG: Map<String, ModelInfo> = listOf(
        ModelInfo(
            lang = "en-us",
            label = "English (US) — small",
            url = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            approxSizeMb = 50
        )
        // Add more here, e.g.:
        // ModelInfo("en-au", "English (AU) — small", "https://...", 55)
    ).associateBy { it.lang }

    fun availableModels(): List<ModelInfo> = MODEL_CATALOG.values.toList()
    fun getInfo(lang: String): ModelInfo? = MODEL_CATALOG[lang]
    fun defaultLang(): String = LANG_DEFAULT

    // ---- Paths ----
    private fun baseDir(ctx: Context) = File(ctx.filesDir, "vosk")
    private fun langDir(ctx: Context, lang: String) = File(baseDir(ctx), lang)

    /** Scan for the actual model root (dir that contains conf/model.conf) */
    private fun findModelRoot(dir: File): File? {
        dir.walkTopDown().maxDepth(4).forEach { f ->
            if (File(f, "conf/model.conf").exists()) return f
        }
        return null
    }

    /** Returns true if a valid model is present for the given language. */
    fun isModelReady(ctx: Context, lang: String = LANG_DEFAULT): Boolean =
        findModelRoot(langDir(ctx, lang)) != null

    /** Deletes the language folder (used to reset/corrupt downloads). */
    fun clearModel(ctx: Context, lang: String = LANG_DEFAULT) {
        langDir(ctx, lang).deleteRecursively()
    }

    /**
     * Ensure the model exists locally; download+unzip if needed.
     * Returns the absolute path to the recognizer root (dir containing conf/model.conf).
     */
    suspend fun ensureModel(ctx: Context, lang: String = LANG_DEFAULT): String =
        withContext(Dispatchers.IO) {
            val info = getInfo(lang) ?: error("Unknown model language: $lang")
            val dest = langDir(ctx, lang)

            if (!isModelReady(ctx, lang)) {
                // Clean target then download+unzip
                dest.deleteRecursively()
                dest.mkdirs()
                val tmpZip = File.createTempFile("vosk-$lang", ".zip", ctx.cacheDir)
                try {
                    URL(info.url).openStream().use { input ->
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

    // ---- Convenience helpers for service/UI layers ----

    /** Like ensureModel(...) but returns a File to the recognizer root. */
    suspend fun ensureModelFile(ctx: Context, lang: String = LANG_DEFAULT): File =
        File(ensureModel(ctx, lang))

    /** Returns the recognizer root directory if present, else null (no download). */
    fun modelRootOrNull(ctx: Context, lang: String = LANG_DEFAULT): File? =
        findModelRoot(langDir(ctx, lang))

    // ---- Compatibility shims (existing names kept) ----

    /** Old name: download the default model (returns its root path) */
    suspend fun downloadDefaultModel(ctx: Context): String = ensureModel(ctx, LANG_DEFAULT)

    /** Old helper: path to the language folder (not necessarily the recognizer root) */
    fun modelDir(ctx: Context, lang: String = LANG_DEFAULT): File = langDir(ctx, lang)

    /** Convenience: returns the actual root path if present, else null */
    fun modelPathOrNull(ctx: Context, lang: String = LANG_DEFAULT): String? =
        findModelRoot(langDir(ctx, lang))?.absolutePath
}
