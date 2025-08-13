package me.dgol.friday.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.dgol.friday.model.ModelLocator
import me.dgol.friday.prefs.ModelPrefs
import me.dgol.friday.registry.VoskRegistry
import me.dgol.friday.shared.stt.ModelManager
import me.dgol.friday.shared.stt.ModelManager.RegistryEntry
import me.dgol.friday.stt.VoskEngine
import java.io.File
import java.util.Locale

data class FridayUiState(
    val isListening: Boolean = false,
    val isThinking: Boolean = false,
    val transcript: String = "",
    val showPermissionRationale: Boolean = false,
    val errorMessage: String? = null,

    // Selection
    val selectedLang: String = "en-us",
    val selectedName: String? = null,

    // Registry + filters
    val registry: List<RegistryEntry> = emptyList(),
    val minSizeMb: Int = 0,
    val maxSizeMb: Int = 5000,

    // Downloaded models view
    val downloads: List<DownloadedItem> = emptyList(),

    // Busy flags
    val busy: Boolean = false,
    val busyLang: String? = null
)

data class RegistryItem(
    val entry: RegistryEntry,
    val installed: Boolean
)

data class LanguageGroup(
    val lang: String,
    val langText: String,
    val main: RegistryItem,
    val others: List<RegistryItem>
)

data class FamilyGroup(
    val baseLang: String,
    val baseLabel: String,
    val children: List<LanguageGroup>
)

// Row model for the Downloaded screen
data class DownloadedItem(
    val lang: String,
    val name: String,
    val root: File,
    val inUse: Boolean,
    val sizeLabel: String? = null
)

class FridayViewModel : ViewModel() {

    private val _ui = MutableStateFlow(FridayUiState())
    val ui: StateFlow<FridayUiState> = _ui

    private var engine: VoskEngine? = null

    // --- Init / Registry ---

    fun init(context: Context) {
        if (_ui.value.registry.isNotEmpty()) return
        val fallback = ModelManager.defaultLang()
        val savedLang = ModelPrefs.getSelectedLang(context, fallback)
        val savedName = ModelPrefs.getSelectedName(context)
        _ui.update { it.copy(selectedLang = savedLang, selectedName = savedName) }
        loadRegistry(context)
        refreshDownloads(context)
    }

    fun loadRegistry(context: Context) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            runCatching { VoskRegistry.fetch() }
                .onFailure { t ->
                    _ui.update { it.copy(busy = false, errorMessage = t.message ?: "Failed to load model registry") }
                }
                .onSuccess { list ->
                    _ui.update { it.copy(registry = list, busy = false) }
                }
        }
    }

    // --- Filters (size range only) ---

    fun setMinSizeMb(minMb: Int) {
        val clamped = minMb.coerceIn(0, _ui.value.maxSizeMb)
        _ui.update { it.copy(minSizeMb = clamped) }
    }

    fun setMaxSizeMb(maxMb: Int) {
        val clamped = maxMb.coerceAtLeast(_ui.value.minSizeMb).coerceAtMost(5000)
        _ui.update { it.copy(maxSizeMb = clamped) }
    }

    fun setSizeRange(minMb: Int, maxMb: Int) {
        val min = minMb.coerceIn(0, 5000)
        val max = maxMb.coerceIn(min, 5000)
        _ui.update { it.copy(minSizeMb = min, maxSizeMb = max) }
    }

    // --- Selection persistence ---

    fun selectLang(context: Context, lang: String) {
        ModelPrefs.setSelectedLang(context, lang)
        _ui.update { it.copy(selectedLang = lang) }
        refreshDownloads(context)
    }

    fun selectModel(context: Context, lang: String, name: String?) {
        ModelPrefs.setSelectedLang(context, lang)
        ModelPrefs.setSelectedName(context, name)
        _ui.update { it.copy(selectedLang = lang, selectedName = name) }
        refreshDownloads(context)
    }

    // --- Install / Delete (by language + name) ---

    fun downloadRegistryModel(context: Context, item: RegistryItem) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, busyLang = item.entry.lang) }
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { ModelManager.ensureModelFromRegistry(context, item.entry) }
            }.onFailure { t ->
                _ui.update { it.copy(busy = false, busyLang = null, errorMessage = t.message ?: "Download failed") }
            }.onSuccess {
                // auto-select downloaded model
                selectModel(context, item.entry.lang, item.entry.name)
                _ui.update { it.copy(busy = false, busyLang = null) }
            }
        }
    }

    fun deleteInstalledModel(context: Context, lang: String, name: String) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true, busyLang = lang) }
        viewModelScope.launch(Dispatchers.IO) {
            ModelManager.clearModel(context, lang, name)
            withContext(Dispatchers.Main) {
                if (_ui.value.selectedLang == lang && _ui.value.selectedName == name) {
                    selectModel(context, lang, null)
                }
                _ui.update { it.copy(busy = false, busyLang = null) }
                refreshDownloads(context)
            }
        }
    }

    // --- Grouping / Sorting for Model Selector (families) ---

    fun familiesForDisplay(context: Context): List<FamilyGroup> {
        val minBytes = _ui.value.minSizeMb.toLong() * 1024L * 1024L
        val maxBytes = _ui.value.maxSizeMb.toLong() * 1024L * 1024L
        val filtered = _ui.value.registry.filter { e ->
            val size = e.sizeBytes
            val sizeOk = size == null || (size in minBytes..maxBytes)
            !e.obsolete && sizeOk && e.lang.isNotBlank()
        }

        val byLocale = filtered.groupBy { it.lang.lowercase() }
        val localeGroups = mutableListOf<LanguageGroup>()

        for ((locale, entries) in byLocale) {
            val langText = entries.firstOrNull()?.langText ?: locale

            val installedNames = ModelManager.listInstalledModels(context, locale)
                .mapNotNull { it.parentFile?.name }
                .toSet()

            val sorted = entries.sortedWith { a, b ->
                val va = parseVersion(a.version)
                val vb = parseVersion(b.version)
                val versionCmp = compareVersionLists(va, vb)
                if (versionCmp != 0) -versionCmp else a.name.compareTo(b.name, ignoreCase = true)
            }
            if (sorted.isEmpty()) continue

            val mainEntry = sorted.first()
            val main = RegistryItem(mainEntry, installedNames.contains(mainEntry.name))
            val others = sorted.drop(1).map { RegistryItem(it, installedNames.contains(it.name)) }

            localeGroups += LanguageGroup(
                lang = locale,
                langText = langText,
                main = main,
                others = others
            )
        }

        val byFamily = localeGroups.groupBy { baseLangOf(it.lang) }
        val families = byFamily.map { (base, children) ->
            val baseLabel = baseLabelFor(base, children.firstOrNull()?.langText)
            val sortedChildren = children.sortedWith { a, b ->
                val aInstalled = a.main.installed
                val bInstalled = b.main.installed
                if (aInstalled != bInstalled) return@sortedWith if (aInstalled) -1 else 1
                val aPref = localePreferenceRank(a.lang)
                val bPref = localePreferenceRank(b.lang)
                if (aPref != bPref) return@sortedWith aPref.compareTo(bPref)
                a.langText.compareTo(b.langText, ignoreCase = true)
            }
            FamilyGroup(baseLang = base, baseLabel = baseLabel, children = sortedChildren)
        }

        return families.sortedWith { a, b ->
            val ra = familyPopularityRank(a.baseLang)
            val rb = familyPopularityRank(b.baseLang)
            if (ra != rb) ra.compareTo(rb) else a.baseLabel.lowercase().compareTo(b.baseLabel.lowercase())
        }
    }

    // --- Downloaded models listing ---

    fun refreshDownloads(context: Context) {
        val base = File(context.filesDir, "vosk")
        if (!base.isDirectory) {
            _ui.update { it.copy(downloads = emptyList()) }
            return
        }
        val selLang = _ui.value.selectedLang
        val selName = _ui.value.selectedName

        val list = mutableListOf<DownloadedItem>()
        base.listFiles()?.filter { it.isDirectory }?.forEach { langDir ->
            val lang = langDir.name
            langDir.listFiles()?.filter { it.isDirectory }?.forEach { nameDir ->
                val name = nameDir.name
                val root = ModelManager.modelRootByNameOrNull(context, lang, name) ?: return@forEach
                val inUse = (lang == selLang && name == selName)
                val sizeLabel = root.estimateSizeLabel()
                list += DownloadedItem(lang = lang, name = name, root = root, inUse = inUse, sizeLabel = sizeLabel)
            }
        }

        // Sort: in-use first, then popular families, then lang/name
        val sorted = list.sortedWith { a, b ->
            if (a.inUse != b.inUse) return@sortedWith if (a.inUse) -1 else 1
            val ra = familyPopularityRank(baseLangOf(a.lang))
            val rb = familyPopularityRank(baseLangOf(b.lang))
            if (ra != rb) return@sortedWith ra.compareTo(rb)
            val lc = a.lang.compareTo(b.lang, ignoreCase = true)
            if (lc != 0) return@sortedWith lc
            a.name.compareTo(b.name, ignoreCase = true)
        }
        _ui.update { it.copy(downloads = sorted) }
    }

    // --- STT control ---

    private var engineSeq = 0

    fun startListening(appContext: Context) {
        if (_ui.value.isListening) return
        val mySeq = ++engineSeq

        val lang = _ui.value.selectedLang
        viewModelScope.launch {
            val modelDir = withContext(Dispatchers.IO) {
                ModelLocator.resolveVoskModelDir(appContext, lang)
            }

            if (modelDir == null || !isValidVoskModel(modelDir)) {
                showError("Model not set up. Open Models to download/select one.")
                return@launch
            }

            engine = VoskEngine(
                appContext = appContext,
                modelDir = modelDir,
                onPartial = { partial ->
                    if (engineSeq == mySeq) {
                        _ui.update { it.copy(transcript = mergeLines(it.transcript, "• $partial")) }
                    }
                },
                onFinal = { final ->
                    if (engineSeq == mySeq) {
                        _ui.update { it.copy(transcript = mergeLines(it.transcript, final)) }
                    }
                },
                onError = { t ->
                    if (engineSeq == mySeq) {
                        _ui.update { it.copy(isListening = false, isThinking = false, errorMessage = t.message ?: "Unknown error") }
                    }
                }
            ).also { it.start() }

            _ui.update { it.copy(isListening = true, isThinking = false) }
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
        _ui.update { it.copy(isListening = false, isThinking = false) }
    }

    fun appendTranscript(line: String) {
        _ui.update { it.copy(transcript = mergeLines(it.transcript, line)) }
    }

    fun showError(message: String) {
        _ui.update { it.copy(errorMessage = message) }
    }

    fun dismissError() {
        _ui.update { it.copy(errorMessage = null) }
    }

    fun onPermissionDenied() {
        _ui.update { it.copy(showPermissionRationale = true) }
    }

    fun dismissPermissionRationale() {
        _ui.update { it.copy(showPermissionRationale = false) }
    }

    override fun onCleared() {
        super.onCleared()
        try { engine?.release() } catch (_: Throwable) {}
        engine = null
    }

    // --- helpers ---

    private fun mergeLines(existing: String, newLine: String): String =
        if (existing.isBlank()) newLine else "$existing\n$newLine"

    private fun isValidVoskModel(dir: File): Boolean {
        return dir.exists() && dir.isDirectory && (dir.list()?.isNotEmpty() == true)
    }

    private fun parseVersion(v: String?): List<Int> {
        if (v.isNullOrBlank()) return emptyList()
        return Regex("""\d+""").findAll(v).map { it.value.toIntOrNull() ?: 0 }.toList()
    }

    private fun compareVersionLists(a: List<Int>, b: List<Int>): Int {
        val max = maxOf(a.size, b.size)
        for (i in 0 until max) {
            val ai = if (i < a.size) a[i] else 0
            val bi = if (i < b.size) b[i] else 0
            if (ai != bi) return ai.compareTo(bi)
        }
        return 0
    }

    private fun baseLangOf(lang: String): String = lang.lowercase().split('-', '_').first()

    private fun baseLabelFor(base: String, sampleLangText: String?): String {
        val map = mapOf(
            "en" to "English","es" to "Spanish","fr" to "French","de" to "German","pt" to "Portuguese",
            "it" to "Italian","ru" to "Russian","zh" to "Chinese","ja" to "Japanese","ko" to "Korean",
            "hi" to "Hindi","ar" to "Arabic","nl" to "Dutch","sv" to "Swedish","pl" to "Polish",
            "tr" to "Turkish","vi" to "Vietnamese","id" to "Indonesian"
        )
        map[base]?.let { return it }
        val guess = sampleLangText?.substringBefore("(")?.trim()
        if (!guess.isNullOrBlank()) return guess
        return base.uppercase(Locale.ROOT)
    }

    private fun familyPopularityRank(base: String): Int {
        val popular = listOf("en", "es", "fr", "de", "pt", "it", "ru", "zh", "ja", "ko")
        val idx = popular.indexOf(base)
        return if (idx >= 0) idx else popular.size + base.hashCode().absoluteValue % 1000
    }

    private fun localePreferenceRank(locale: String): Int {
        val lc = locale.lowercase()
        val order = listOf("-us", "-gb", "-au", "-ca")
        val i = order.indexOfFirst { lc.endsWith(it) }
        return if (i >= 0) i else 100
    }
}

private val Int.absoluteValue: Int get() = if (this < 0) -this else this

// Estimate folder size into a short label (best effort, may be partial on scoped storage)
private fun File.estimateSizeLabel(): String? = runCatching {
    var total = 0L
    walkTopDown().forEach { f -> if (f.isFile) total += f.length() }
    val mb = (total / (1024L * 1024L)).toInt()
    "$mb MB"
}.getOrNull()
