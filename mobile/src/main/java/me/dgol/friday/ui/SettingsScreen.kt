package me.dgol.friday.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings (coming soon)", style = MaterialTheme.typography.headlineSmall)
        Text(
            "This will include:\n" +
                    "• STT/LLM backends and API keys\n" +
                    "• Wake word / auto-listen options\n" +
                    "• Permissions and privacy controls\n" +
                    "• Home Assistant / n8n / Ollama integrations"
        )
    }
}
