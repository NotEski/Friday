package me.dgol.friday.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession

/**
 * Bound by the system when the user invokes the assistant.
 * It spins up our VoiceInteractionSession via the SessionService declared in XML.
 */
class FridayVoiceInteractionService : VoiceInteractionService() {

    /**
     * Optionally show a session immediately if the system requests it.
     * Most logic lives in FridayVoiceInteractionSession.
     */
    override fun onLaunchVoiceAssistFromKeyguard() {
        // Ask the system to start and show our VoiceInteractionSession UI.
        val args = Bundle().apply { putBoolean("from_keyguard", true) }
        // Include WITH_ASSIST so onHandleAssist(AssistState) can fire if allowed by user.
        showSession(args, VoiceInteractionSession.SHOW_WITH_ASSIST)
    }

    /** User long-presses assistant gesture while UNLOCKED. */


    override fun onReady() {
        super.onReady()
        // Service is ready to handle sessions.
    }

    override fun showSession(args: Bundle?, flags: Int) {
        // You can trigger showing a session programmatically if needed.
        super.showSession(args, flags)
    }
}
