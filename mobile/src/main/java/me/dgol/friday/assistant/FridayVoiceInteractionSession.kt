package me.dgol.friday.assistant

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log

private const val TAG = "FridayAssist"

class FridayVoiceInteractionSession(
    private val ctx: Context
) : VoiceInteractionSession(ctx) {

    // We render UI in an Activity; disable the session window per docs.
    override fun onPrepareShow(args: Bundle?, showFlags: Int) {
        super.onPrepareShow(args, showFlags)
        setUiEnabled(false)
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)

        val intent = Intent(ctx, FridayAssistantActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            addCategory(Intent.CATEGORY_VOICE)
            putExtra("from_assist", true)
            if (args != null) putExtras(args)
        }

        try {
            if (Build.VERSION.SDK_INT >= 34) {
                // API 34+: requires a non-null Bundle options argument
                startAssistantActivity(intent, Bundle())
            } else {
                // Pre-34 fallback
                startVoiceActivity(intent)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to launch assistant activity", t)
        }

        // Our UI is in the Activity; hide this empty session window.
        hide()
    }

    override fun onHide() {
        super.onHide()
        // Activity owns lifecycle
    }
}
