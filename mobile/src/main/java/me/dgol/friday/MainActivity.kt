package me.dgol.friday

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.lifecycleScope
import me.dgol.friday.ui.theme.FridayTheme
import me.dgol.friday.shared.stt.ModelManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import kotlinx.coroutines.launch
import android.util.Log



class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val launchedFromAssist = intent?.getBooleanExtra("from_assist", false) == true
        if (launchedFromAssist) {
            // TODO: replace with your existing start-listening / open-mic routine.
            // For example:
            // viewModel.startVoiceSession(source = "assistant")

        }

        enableEdgeToEdge()
        setContent {
            FridayTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Friday",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
        val ready = ModelManager.isModelReady(this)
        Log.d("Friday", "Model ready? $ready  path=${ModelManager.modelPathOrNull(this)}")
        Toast.makeText(this, "Model ready? $ready", Toast.LENGTH_SHORT).show()

        if (!ready) {
        lifecycleScope.launch {
            try {
                // Either ensureModel(...) OR the shim downloadDefaultModel(...)
                ModelManager.ensureModel(this@MainActivity)
                Toast.makeText(this@MainActivity, "Model ready!", Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                Toast.makeText(
                    this@MainActivity,
                    "Download failed: ${t.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = false)
@Composable
fun GreetingPreview() {
    FridayTheme {
        Greeting("Android")
    }
}