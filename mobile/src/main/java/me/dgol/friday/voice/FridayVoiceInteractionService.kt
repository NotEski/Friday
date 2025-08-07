package me.dgol.friday.voice

import android.service.voice.VoiceInteractionService

class FridayVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
        // Service ready
    }

    override fun onShutdown() {
        super.onShutdown()
        // Cleanup
    }
}