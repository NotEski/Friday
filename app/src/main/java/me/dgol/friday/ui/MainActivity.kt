package me.dgol.friday.ui

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import me.dgol.friday.pipeline.VoicePipeline
import me.dgol.friday.ui.theme.FridayTheme

class MainActivity : ComponentActivity() {
    private lateinit var voicePipeline: VoicePipeline

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            // Handle denial - for now logging
            android.util.Log.w("Friday", "Microphone permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        voicePipeline = VoicePipeline(this)
        voicePipeline.load()

        requestPermission.launch(Manifest.permission.RECORD_AUDIO)

        var transcription by mutableStateOf("")
        var isLoading by mutableStateOf(true)
        var isListening by mutableStateOf(false)

        lifecycleScope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                voicePipeline.load()
            }
            isLoading = false
        }

        setContent {
            FridayTheme {
                MainScreen(
                    transcription = transcription,
                    isLoading = isLoading,
                    isListening = isListening,
                    onListenClick = {
                        lifecycleScope.launch {
                            isListening = true
                            transcription = voicePipeline.listen()
                            isListening = false
                        }
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voicePipeline.release()
    }
}



