package me.dgol.friday.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DownloadedModelsScreen(
    vm: FridayViewModel,
    appContext: Context,
    modifier: Modifier = Modifier
) {
    val ui by vm.ui.collectAsState()

    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Downloaded models", style = MaterialTheme.typography.titleLarge)

        if (ui.downloads.isEmpty()) {
            Text("No models installed yet.")
            return@Column
        }

        ui.downloads.forEach { item ->
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${item.lang} — ${item.name ?: "model"}")
                    Text(vm.sizeLabel(item.sizeBytes))
                    val inUse = vm.inUse(item)
                    if (inUse) {
                        AssistChip(onClick = {}, label = { Text("In use") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.selectModel(appContext, item.lang) }) { Text("Use") }
                        OutlinedButton(onClick = { vm.deleteInstalledModel(appContext, item.lang) }) { Text("Delete") }
                    }
                }
            }
        }
    }
}
