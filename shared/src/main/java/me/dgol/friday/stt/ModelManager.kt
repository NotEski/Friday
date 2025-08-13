// shared/src/main/java/me/dgol/friday/shared/stt/ModelManager.kt
package me.dgol.friday.shared.stt

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Vosk model lifecycle helper.
 *
 * On-disk layout:
 *   <filesDir>/vosk/<lang>/<name>/ ...unzipped model...
 * The recognizer root is the folder that contains conf/model.conf.
 *
 * Supports:
 *  - Static catalog (simple)
 *  - Dynamic registry entries (name/url/version/size)
 */
object ModelManager {

    // ---- Minimal static catalog (kept for defaults/fallbacks) ----
    data class CatalogModel(
        val lang: String,
        val label: String,
        val url: String,
        val approxSizeMb: Int
    )

    private const val LANG_DEFAULT = "en-us"
    private val STATIC_CATALOG: Map<String, CatalogModel> = listOf(
        CatalogModel(
            lang = "en-us",
            label = "English (US) — small",
            url = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            approxSizeMb = 50
        )
    ).associateBy { it.lang }

    fun defaultLang(): String = LANG_DEFAULT

    // ---- Registry entry (from Vosk JSON) ----
    data class RegistryEntry(
        val lang: String,
        val langText: String,
        val name: String,        // e.g., vosk-model-small-en-us-0.15
        val url: String,
        val version: String?,
        val sizeBytes: Long?,
        val sizeText: String?,
        val type: String?,       // asr / tts
        val obsolete: Boolean
    )

    // ---- Paths ----
    private fun baseDir(ctx: Context) = File(ctx.filesDir, "vosk")
    private fun langDir(ctx: Context, lang: String) = File(baseDir(ctx), lang)
    private fun nameDir(ctx: Context, lang: String, name: String) = File(langDir(ctx, lang), name)

    /** Scan for the actual model root (dir that contains conf/model.conf) */
    private fun findModelRoot(dir: File): File? {
        if (!dir.exists()) return null
        dir.walkTopDown().maxDepth(5).forEach { f ->
            if (File(f, "conf/model.conf").exists()) return f
        }
        return null
    }

    // ---- Presence / cleanup ----

    /** Returns true if any valid model for the lang exists. */
    fun isModelReady(ctx: Context, lang: String = LANG_DEFAULT): Boolean =
        listInstalledModels(ctx, lang).isNotEmpty()

    /** Return the recognizer root for a specific <lang>/<name> if present, else null. */
    fun modelRootByNameOrNull(ctx: Context, lang: String, name: String): File? =
        findModelRoot(nameDir(ctx, lang, name))

    /** List installed models under a language. */
    fun listInstalledModels(ctx: Context, lang: String): List<File> {
        val ldir = langDir(ctx, lang)
        if (!ldir.isDirectory) return emptyList()
        return ldir.listFiles()?.mapNotNull { findModelRoot(it) } ?: emptyList()
    }

    /** Deletes a specific installed model by <lang>/<name>. */
    fun clearModel(ctx: Context, lang: String, name: String? = null) {
        val dir = if (name == null) langDir(ctx, lang) else nameDir(ctx, lang, name)
        dir.deleteRecursively()
    }

    // ---- Ensure from static catalog (backwards compatible) ----

    suspend fun ensureModel(ctx: Context, lang: String = LANG_DEFAULT): String =
        withContext(Dispatchers.IO) {
            val info = STATIC_CATALOG[lang] ?: error("Unknown static catalog lang: $lang")
            val name = staticNameFromUrl(info.url) // e.g. vosk-model-small-en-us-0.15
            val root = ensureModelFromUrlInternal(ctx, lang, name, info.url, expectedMd5 = null)
            root.absolutePath
        }

    private fun staticNameFromUrl(url: String): String =
        url.substringAfterLast('/').substringBeforeLast(".zip")

    // ---- Ensure from live registry ----

    /**
     * Download & install a registry model into <filesDir>/vosk/<lang>/<name>
     * Returns the recognizer root directory.
     */
    suspend fun ensureModelFromRegistry(ctx: Context, entry: RegistryEntry): File =
        withContext(Dispatchers.IO) {
            require(!entry.obsolete) { "Model '${entry.name}' is marked obsolete" }
            ensureModelFromUrlInternal(ctx, entry.lang, entry.name, entry.url, expectedMd5 = null)
        }

    /** Internal downloader/unzipper. Verifies model root exists after unpack. */
    private fun ensureModelFromUrlInternal(
        ctx: Context,
        lang: String,
        name: String,
        url: String,
        expectedMd5: String?
    ): File {
        val dest = nameDir(ctx, lang, name)
        if (findModelRoot(dest) != null) return findModelRoot(dest)!!

        dest.deleteRecursively()
        dest.mkdirs()

        val tmpZip = File.createTempFile("vosk-$lang-$name", ".zip", ctx.cacheDir)
        try {
            URL(url).openStream().use { input ->
                FileOutputStream(tmpZip).use { output -> input.copyTo(output) }
            }

            if (!expectedMd5.isNullOrBlank()) {
                val md5 = tmpZip.md5()
                check(md5.equals(expectedMd5, ignoreCase = true)) {
                    "MD5 mismatch for $name: expected $expectedMd5, got $md5"
                }
            }

            unzip(tmpZip, dest)
        } finally {
            tmpZip.delete()
        }

        return requireNotNull(findModelRoot(dest)) {
            "Vosk model extracted but conf/model.conf not found in $name"
        }
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

    private fun File.md5(): String {
        val md = MessageDigest.getInstance("MD5")
        inputStream().use { ins ->
            val buf = ByteArray(16 * 1024)
            while (true) {
                val r = ins.read(buf)
                if (r <= 0) break
                md.update(buf, 0, r)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    // ---- Convenience helpers kept from earlier API ----

    suspend fun ensureModelFile(ctx: Context, lang: String = LANG_DEFAULT): File =
        File(ensureModel(ctx, lang))

    fun modelRootOrNull(ctx: Context, lang: String = LANG_DEFAULT): File? {
        // Prefer any installed named model; otherwise null
        val installed = listInstalledModels(ctx, lang)
        return installed.firstOrNull()
    }

    fun modelPathOrNull(ctx: Context, lang: String = LANG_DEFAULT): String? =
        modelRootOrNull(ctx, lang)?.absolutePath
}
