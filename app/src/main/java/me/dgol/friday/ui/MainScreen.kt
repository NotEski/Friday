package me.dgol.friday.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MainScreen(
    transcription: String,
    isLoading: Boolean,
    isListening: Boolean,
    onListenClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = when {
                isLoading -> "Loading model..."
                isListening -> "Listening..."
                else -> "Tap to speak"
            },
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onListenClick,
            enabled = !isLoading && !isListening
        ) {
            Text("Speak")
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (transcription.isNotEmpty()) {
            Text(
                text = transcription,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}