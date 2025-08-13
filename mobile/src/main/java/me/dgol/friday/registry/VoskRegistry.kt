package me.dgol.friday.registry

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import me.dgol.friday.shared.stt.ModelManager.RegistryEntry
import java.net.URL

object VoskRegistry {

    private const val REGISTRY_URL = "https://alphacephei.com/vosk/models/model-list.json"

    /**
     * Fetch & parse the registry. Returns non-obsolete entries (ASR models).
     * Notes:
     *  - "type" in the registry is typically "small" or "big".
     *  - "obsolete" can be a boolean or a string "true"/"false".
     */
    suspend fun fetch(): List<RegistryEntry> = withContext(Dispatchers.IO) {
        val text = URL(REGISTRY_URL).openStream().bufferedReader().use { it.readText() }
        val arr = JSONArray(text)
        val out = ArrayList<RegistryEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)

            // type: "small" | "big" (sometimes missing)
            val type = o.optString("type", "").lowercase().ifBlank { null }

            // obsolete: may be boolean or string
            val obsoleteAny = o.opt("obsolete")
            val obsolete = when (obsoleteAny) {
                is Boolean -> obsoleteAny
                is String -> obsoleteAny.equals("true", ignoreCase = true)
                else -> false
            }

            if (obsolete) continue

            out += RegistryEntry(
                lang = o.optString("lang"),
                langText = o.optString("lang_text"),
                name = o.getString("name"),
                url = o.getString("url"),
                version = o.optString("version").takeIf { it.isNotBlank() },
                sizeBytes = runCatching { o.optLong("size", -1).takeIf { it > 0 } }.getOrNull(),
                sizeText = o.optString("size_text").takeIf { it.isNotBlank() },
                type = type,          // "small" or "big"
                obsolete = false
            )
        }
        out
    }
}
