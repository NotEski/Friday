package me.dgol.friday.assistant

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.view.View
import android.widget.FrameLayout
import android.app.assist.AssistStructure
import android.app.assist.AssistContent
import me.dgol.friday.MainActivity
import android.content.Intent

/**
 * Minimal VoiceInteractionSession that routes invocations into Friday's UI.
 * Handles both modern and legacy assist callbacks.
 */
class FridayVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    override fun onCreateContentView(): View {
        // Lightweight placeholder (we immediately bounce to MainActivity)
        val container = FrameLayout(context)
        val padding = (8 * context.resources.displayMetrics.density).toInt()
        container.setPadding(padding, padding, padding, padding)
        return container
    }

    // --- Modern API (preferred as of API 29) ---
    override fun onHandleAssist(state: AssistState) {
        // You can read state.assistStructure / state.assistContent here if needed.
        launchMain(fromAssist = true)
        finish()
    }

    // --- Legacy overloads (deprecated on the platform) ---
    @Deprecated("Platform API is deprecated. Use onHandleAssist(AssistState) instead.")
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onHandleAssist(
        data: Bundle?,
        structure: AssistStructure?,
        content: AssistContent?
    ) {
        launchMain(fromAssist = true)
        finish()
    }

    @Deprecated("Platform API is deprecated. Use onHandleAssist(AssistState) instead.")
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onHandleAssistSecondary(
        data: Bundle?,
        structure: AssistStructure?,
        content: AssistContent?,
        index: Int,
        count: Int
    ) {
        launchMain(fromAssist = true)
        finish()
    }

    private fun launchMain(fromAssist: Boolean) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("from_assist", fromAssist)
        }
        context.startActivity(intent)
    }
}
