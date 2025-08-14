package me.dgol.friday.ui

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import me.dgol.friday.model.ModelLocator
import me.dgol.friday.prefs.ModelPrefs
import me.dgol.friday.shared.stt.ModelManager
import me.dgol.friday.stt.VoskEngine
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

// ---------- Registry / downloads data models ----------
data class VoskModelMeta(
    val name: String,
    val url: String,
    val sizeBytes: Long,
    val lang: String,           // e.g., "en-us"
    val version: String = "0",
    val obsolete: Boolean = false
) {
    val langText: String get() = lang
}

data class DownloadedModel(
    val lang: String,
    val path: String,
    val name: String? = null,
    val sizeBytes: Long? = null
)

// Grouping for selector UI
data class LanguageGroup(
    val baseLang: String,       // e.g., "en"
    val baseLabel: String,      // e.g., "English (en)"
    val main: VoskModelMeta?,   // preferred/latest for this base
    val children: List<VoskModelMeta>
)

// ---------- UI state ----------
data class FridayUiState(
    val isListening: Boolean = false,
    val isThinking: Boolean = false,
    val transcript: String = "",

    // Chat
    val input: String = "",
    val messages: List<ChatItem> = emptyList(),

    // Selection
    val selectedLang: String = ModelManager.defaultLang(),
    val selectedName: String? = null,

    // Registry + filters
    val registry: List<VoskModelMeta> = emptyList(),
    val minSizeMb: Int = 0,
    val maxSizeMb: Int = 5000,

    // Installed models
    val downloads: List<DownloadedModel> = emptyList(),

    // Dialogs
    val showPermissionRationale: Boolean = false,
    val errorMessage: String? = null,

    // Busy flags
    val busy: Boolean = false,
    val busyLang: String? = null
)

class FridayViewModel : ViewModel() {

    private val _ui = MutableStateFlow(FridayUiState())
    val ui: StateFlow<FridayUiState> = _ui.asStateFlow()

    // STT engine
    private var engine: VoskEngine? = null
    private var engineSeq = 0

    // ---------- Lifecycle / init ----------
    fun init(appContext: Context) {
        val lang = ModelPrefs.getSelectedLang(appContext, ModelManager.defaultLang())
        _ui.update { it.copy(selectedLang = lang) }
        if (_ui.value.registry.isEmpty()) {
            loadRegistry(appContext)
        }
        refreshDownloads(appContext)
    }

    // ---------- Dialog helpers ----------
    fun onPermissionDenied() { _ui.update { it.copy(showPermissionRationale = true) } }
    fun dismissPermissionRationale() { _ui.update { it.copy(showPermissionRationale = false) } }
    fun showError(msg: String) { _ui.update { it.copy(errorMessage = msg) } }
    fun dismissError() { _ui.update { it.copy(errorMessage = null) } }

    // ---------- Chat helpers ----------
    fun setInput(text: String) { _ui.update { it.copy(input = text) } }

    fun sendInputMessage() {
        val msg = _ui.value.input.trim()
        if (msg.isEmpty()) return
        _ui.update { it.copy(input = "", messages = it.messages + ChatItem(ChatRole.User, msg)) }
        processUserMessage(msg)
    }

    private fun processUserMessage(text: String) {
        // TODO: plug your AI/tooling here
        val reply = "You said: $text"
        _ui.update { it.copy(messages = it.messages + ChatItem(ChatRole.Assistant, reply)) }
    }

    // ---------- Model registry / filters ----------
    fun loadRegistry(ctx: Context) {
        // If you already had a networked registry, rewire it here.
        // For now we keep empty (screens will handle empty state).
        viewModelScope.launch { /* no-op placeholder */ }
    }

    fun setMinSizeMb(v: Int) { _ui.update { it.copy(minSizeMb = v.coerceAtLeast(0)) } }
    fun setMaxSizeMb(v: Int) { _ui.update { it.copy(maxSizeMb = v.coerceAtLeast(_ui.value.minSizeMb)) } }
    fun setSizeRange(min: Int, max: Int) {
        _ui.update { it.copy(minSizeMb = min.coerceAtLeast(0), maxSizeMb = max.coerceAtLeast(min)) }
    }

    fun familiesForDisplay(): List<LanguageGroup> {
        val minB = _ui.value.minSizeMb * 1_000_000L
        val maxB = _ui.value.maxSizeMb * 1_000_000L
        val filtered = _ui.value.registry.filter { it.sizeBytes in minB..maxB && !it.obsolete }

        // group by base language (part before '-'), e.g., "en" for "en-us"
        val groups = filtered.groupBy { it.lang.lowercase().substringBefore('-') }

        val groupList = groups.map { (base, items) ->
            val sorted = items.sortedWith(
                compareByDescending<VoskModelMeta> { it.version }
                    .thenBy { it.sizeBytes }
                    .thenBy { it.name }
            )
            val main = sorted.firstOrNull()
            val children = sorted.drop(1)
            LanguageGroup(
                baseLang = base,
                baseLabel = "${base.uppercase()} ($base)",
                main = main,
                children = children
            )
        }

        // Popular languages first (English), then alphabetical
        val priority = listOf("en", "es", "de", "fr", "it", "pt", "ru", "zh", "ja", "ko")
        return groupList.sortedWith(
            compareBy<LanguageGroup> { val i = priority.indexOf(it.baseLang); if (i == -1) Int.MAX_VALUE else i }
                .thenBy { it.baseLang }
        )
    }

    // ---------- Installed models ----------
    fun refreshDownloads(ctx: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = scanInstalledModels(ctx)
            _ui.update { it.copy(downloads = list) }
        }
    }

    private fun scanInstalledModels(ctx: Context): List<DownloadedModel> {
        val base = File(ctx.filesDir, "vosk")
        if (!base.exists()) return emptyList()
        val out = mutableListOf<DownloadedModel>()
        base.listFiles()?.forEach { langDir ->
            if (!langDir.isDirectory) return@forEach
            val root = findModelRootCompat(langDir)
            if (root != null) {
                val size = root.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
                out += DownloadedModel(
                    lang = langDir.name,
                    path = root.absolutePath,
                    name = root.name,
                    sizeBytes = size
                )
            }
        }
        return out.sortedBy { it.lang }
    }

    private fun findModelRootCompat(dir: File): File? {
        dir.walkTopDown().maxDepth(4).forEach { f ->
            if (File(f, "conf/model.conf").exists()) return f
        }
        return null
    }

    fun sizeLabel(bytes: Long?): String =
        if (bytes == null) "-" else {
            val mb = bytes.toDouble() / 1_000_000.0
            if (mb >= 1000) String.format("%.1f GB", mb / 1000.0) else String.format("%.0f MB", mb)
        }

    fun inUse(item: DownloadedModel): Boolean =
        item.lang.equals(_ui.value.selectedLang, ignoreCase = true)

    fun selectModel(ctx: Context, lang: String) {
        ModelPrefs.setSelectedLang(ctx, lang)
        _ui.update { it.copy(selectedLang = lang) }
    }

    fun deleteInstalledModel(ctx: Context, lang: String) {
        viewModelScope.launch(Dispatchers.IO) {
            File(ctx.filesDir, "vosk/$lang").deleteRecursively()
            refreshDownloads(ctx)
        }
    }

    fun downloadRegistryModel(ctx: Context, meta: VoskModelMeta) {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(busy = true, busyLang = meta.lang) }
            val target = File(ctx.filesDir, "vosk/${meta.lang}")
            target.deleteRecursively()
            target.mkdirs()

            val tmpZip = File.createTempFile("vosk-${meta.lang}", ".zip", ctx.cacheDir)
            try {
                URL(meta.url).openStream().use { input ->
                    FileOutputStream(tmpZip).use { output -> input.copyTo(output) }
                }
                unzip(tmpZip, target)
                // set as selected & refresh
                ModelPrefs.setSelectedLang(ctx, meta.lang)
                _ui.update { it.copy(selectedLang = meta.lang) }
                refreshDownloads(ctx)
            } catch (t: Throwable) {
                _ui.update { it.copy(errorMessage = t.message ?: "Download failed") }
            } finally {
                tmpZip.delete()
                _ui.update { it.copy(busy = false, busyLang = null) }
            }
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

    // ---------- STT control ----------
    fun startListening(appContext: Context) {
        if (_ui.value.isListening) return

        val lang = _ui.value.selectedLang.ifBlank { ModelManager.defaultLang() }
        _ui.update { it.copy(isListening = true, isThinking = false, errorMessage = null) }

        val mySeq = ++engineSeq

        viewModelScope.launch {
            val modelDir = withContext(Dispatchers.IO) {
                ModelLocator.resolveVoskModelDir(appContext, lang)
            }

            if (modelDir == null || !modelDir.exists()) {
                _ui.update { it.copy(isListening = false, errorMessage = "Model not set up for '$lang'") }
                return@launch
            }

            engine = VoskEngine(
                appContext = appContext,
                modelDir = modelDir,
                onPartial = { partial ->
                    if (engineSeq == mySeq) {
                        _ui.update { it.copy(input = partial) }
                    }
                },
                onFinal = { final ->
                    if (engineSeq == mySeq) {
                        stopListening()
                        _ui.update { it.copy(input = final) }
                        _ui.update { it.copy(messages = it.messages + ChatItem(ChatRole.User, final), input = "") }
                        processUserMessage(final)
                    }
                },
                onError = { t ->
                    if (engineSeq == mySeq) {
                        _ui.update { it.copy(isListening = false, errorMessage = t.message ?: "Unknown error") }
                    }
                }
            ).also { it.start() }
        }
    }

    fun stopListening() {
        val e = engine ?: run {
            _ui.update { it.copy(isListening = false, isThinking = false) }
            return
        }
        try { e.stop() } catch (_: Throwable) {}
        try { e.release() } catch (_: Throwable) {}
        engine = null
        engineSeq++
        _ui.update { it.copy(isListening = false, isThinking = false) }
    }
}
