package me.dgol.friday.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession

/**
 * Minimal session that launches FridayAssistantActivity in the "voice layer".
 * This avoids device-specific quirks with the session window/Compose overlay.
 */
class FridayVoiceInteractionSession(
    private val ctx: Context
) : VoiceInteractionSession(ctx) {

    override fun onCreate() {
        super.onCreate()
        // We render UI in an Activity instead of the session window.
        setUiEnabled(false)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)

        val intent = Intent(ctx, FridayAssistantActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra("from_assist", true)
            if (args != null) putExtras(args)
        }

        // Launch with assistant privileges so it appears over apps/keyguard.
        startAssistantActivity(intent)

        // Hide the (empty) session window immediately.
        hide()
    }

    override fun onHide() {
        super.onHide()
        // No-op; the activity handles its own lifecycle.
    }
}
