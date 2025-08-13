package me.dgol.friday.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * Creates a new VoiceInteractionSession every time the user invokes Friday.
 */
class FridayVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession {
        return FridayVoiceInteractionSession(this)
    }
}
