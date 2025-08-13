package me.dgol.friday

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import me.dgol.friday.ui.FridayApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val launchedFromAssist = intent?.getBooleanExtra("from_assist", false) == true

        setContent {
            FridayApp(fromAssistInitial = launchedFromAssist)
        }
    }
}
