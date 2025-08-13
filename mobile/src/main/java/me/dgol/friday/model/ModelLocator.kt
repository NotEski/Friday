package me.dgol.friday.model

import android.content.Context
import me.dgol.friday.shared.stt.ModelManager
import java.io.File

object ModelLocator {
    private const val DEFAULT_LANG = "en-us"

    suspend fun resolveVoskModelDir(appContext: Context, lang: String = DEFAULT_LANG): File? {
        ModelManager.modelRootOrNull(appContext, lang)?.let { return it }
        return try { ModelManager.ensureModelFile(appContext, lang) } catch (_: Throwable) { null }
    }
}
