package me.dgol.friday.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionService

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
        // Let the framework start our session service and session; nothing else required here.
        super.onLaunchVoiceAssistFromKeyguard()
    }

    override fun onReady() {
        super.onReady()
        // Service is ready to handle sessions.
    }

    override fun showSession(args: Bundle?, flags: Int) {
        // You can trigger showing a session programmatically if needed.
        super.showSession(args, flags)
    }
}
