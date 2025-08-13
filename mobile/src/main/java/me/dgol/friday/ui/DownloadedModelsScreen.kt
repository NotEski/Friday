@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package me.dgol.friday.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DownloadedModelsScreen(
    vm: FridayViewModel,
    appContext: Context,
    modifier: Modifier = Modifier
) {
    val ui by vm.ui.collectAsState()

    LaunchedEffect(Unit) {
        vm.refreshDownloads(appContext)
    }

    Column(
        modifier = modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Downloaded models", style = MaterialTheme.typography.headlineSmall)

        if (ui.downloads.isEmpty()) {
            Text("No models downloaded yet.")
            return@Column
        }

        ui.downloads.forEach { item ->
            ElevatedCard {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("${item.lang} — ${item.name}", style = MaterialTheme.typography.titleMedium)
                            Text(item.sizeLabel ?: "", style = MaterialTheme.typography.bodySmall)
                        }
                        if (item.inUse) {
                            AssistChip(label = { Text("Using") }, onClick = {})
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!item.inUse) {
                            OutlinedButton(onClick = {
                                vm.selectModel(appContext, item.lang, item.name)
                            }) { Text("Use") }
                        }
                        OutlinedButton(onClick = {
                            vm.deleteInstalledModel(appContext, item.lang, item.name)
                            vm.refreshDownloads(appContext)
                        }) { Text("Delete") }
                    }
                }
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}