package me.dgol.friday.assistant

import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.util.Log

private const val TAG = "FridayVIS"

class FridayVoiceInteractionService : VoiceInteractionService() {

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate()")
    }

    override fun onReady() {
        super.onReady()
        // Log.i(TAG, "onReady(); isSessionRunning=${isSessionRunning}")
    }

    /** User long-presses assist while UNLOCKED */


    /** User invokes assistant from the LOCKSCREEN */
    override fun onLaunchVoiceAssistFromKeyguard() {
        Log.i(TAG, "onLaunchVoiceAssistFromKeyguard() → calling showSession()")
        val args = Bundle().apply { putBoolean("from_keyguard", true) }
        showSession(args, VoiceInteractionSession.SHOW_WITH_ASSIST or VoiceInteractionSession.SHOW_WITH_SCREENSHOT)
        //Log.i(TAG, "showSession() returned; isSessionRunning=${isSessionRunning}")
    }
}
