package me.dgol.friday.model

import android.content.Context
import me.dgol.friday.prefs.ModelPrefs
import me.dgol.friday.shared.stt.ModelManager
import java.io.File

object ModelLocator {
    private const val DEFAULT_LANG = "en-us"

    /**
     * Returns a recognizer root directory for the selected model.
     * Priority:
     *   1) If user selected a specific model name and it is installed, use it.
     *   2) Else, any installed model for the lang.
     *   3) Else, install from static catalog as a last resort.
     */
    suspend fun resolveVoskModelDir(appContext: Context, lang: String = DEFAULT_LANG): File? {
        val name = ModelPrefs.getSelectedName(appContext)
        if (!name.isNullOrBlank()) {
            ModelManager.modelRootByNameOrNull(appContext, lang, name)?.let { return it }
        }
        ModelManager.modelRootOrNull(appContext, lang)?.let { return it }
        return try { ModelManager.ensureModelFile(appContext, lang) } catch (_: Throwable) { null }
    }
}
