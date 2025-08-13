package me.dgol.friday.assistant

import android.app.role.RoleManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import me.dgol.friday.MainActivity

/**
 * Optional: handles ACTION_ASSIST directly (fallback path and for apps that fire the intent).
 * Also offers a helper to request ROLE_ASSISTANT at runtime on first launch.
 */
class AssistActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If opened as ACTION_ASSIST, just forward into Friday and finish.
        if (intent?.action == Intent.ACTION_ASSIST) {
            startActivity(Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("from_assist", true)
            })
            finish()
            return
        }

        // Helper: prompt the user to set Friday as the assistant (one-time UX you can trigger from settings)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_ASSISTANT) && !rm.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
                val intent = rm.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
                startActivity(intent)
            }
        }

        // If launched directly, bounce to main UI.
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
