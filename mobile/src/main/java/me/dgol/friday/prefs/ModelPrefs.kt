package me.dgol.friday.prefs

import android.content.Context
import android.content.Context.MODE_PRIVATE

object ModelPrefs {
    private const val PREFS = "friday_prefs"
    private const val KEY_SELECTED_LANG = "selected_lang"
    private const val KEY_SELECTED_NAME = "selected_model_name" // e.g., vosk-model-small-en-us-0.15

    fun getSelectedLang(ctx: Context, fallback: String): String =
        ctx.getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString(KEY_SELECTED_LANG, fallback) ?: fallback

    fun setSelectedLang(ctx: Context, lang: String) {
        ctx.getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putString(KEY_SELECTED_LANG, lang)
            .apply()
    }

    fun getSelectedName(ctx: Context): String? =
        ctx.getSharedPreferences(PREFS, MODE_PRIVATE)
            .getString(KEY_SELECTED_NAME, null)

    fun setSelectedName(ctx: Context, name: String?) {
        ctx.getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .apply {
                if (name == null) remove(KEY_SELECTED_NAME) else putString(KEY_SELECTED_NAME, name)
                apply()
            }
    }
}
