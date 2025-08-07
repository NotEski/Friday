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


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        if (!ModelManager.isModelReady(this)) {
            AlertDialog.Builder(this)
                .setTitle("Download voice model?")
                .setMessage("Friday needs ~50 MB to recognise speech offline.")
                .setPositiveButton("Download") { _, _ ->
                    lifecycleScope.launch {
                        ModelManager.downloadDefaultModel(this@MainActivity)
                        Toast.makeText(
                            this@MainActivity,
                            "Model ready!",
                            Toast.LENGTH_SHORT            // ← prefixed constant
                        ).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
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