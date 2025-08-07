package me.dgol.friday.voice

import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

class FridayRecognitionService : RecognitionService() {
    override fun onStartListening(intent: Intent?, listener: Callback) {
        val results = Bundle().apply {
            putStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION,
                arrayListOf("hello", "test")
            )
        }
        listener.results(results)
    }

    override fun onStopListening(listener: Callback) {
        // No-op
    }

    override fun onCancel(listener: Callback) {
        // No-op
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}