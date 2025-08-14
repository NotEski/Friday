package me.dgol.friday.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ModelSelectorScreen(
    vm: FridayViewModel,
    appContext: Context,
    modifier: Modifier = Modifier
) {
    val ui by vm.ui.collectAsState()
    LaunchedEffect(Unit) { vm.loadRegistry(appContext) }

    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Models Downloader", style = MaterialTheme.typography.titleLarge)

        // Collapsible Filters
        var showFilters by remember { mutableStateOf(false) }
        ElevatedCard {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Filters", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { showFilters = !showFilters }) {
                        Text(if (showFilters) "Hide" else "Show")
                    }
                }
                if (showFilters) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = ui.minSizeMb.toString(),
                            onValueChange = { v -> v.toIntOrNull()?.let { vm.setMinSizeMb(it) } },
                            label = { Text("Min MB") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = ui.maxSizeMb.toString(),
                            onValueChange = { v -> v.toIntOrNull()?.let { vm.setMaxSizeMb(it) } },
                            label = { Text("Max MB") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }
                }
            }
        }

        val groups = remember(ui.registry, ui.minSizeMb, ui.maxSizeMb) { vm.familiesForDisplay() }

        if (groups.isEmpty()) {
            Text("No models available. (Registry empty)")
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(groups, key = { it.baseLang }) { group ->
                LanguageGroupCard(
                    group = group,
                    installed = ui.downloads.any { it.lang.equals(group.baseLang, ignoreCase = true) },
                    onUse = { vm.selectModel(appContext, it.lang) },
                    onDownload = { vm.downloadRegistryModel(appContext, it) }
                )
            }
        }
    }
}

@Composable
private fun LanguageGroupCard(
    group: LanguageGroup,
    installed: Boolean,
    onUse: (VoskModelMeta) -> Unit,
    onDownload: (VoskModelMeta) -> Unit
) {
    ElevatedCard {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(group.baseLabel, style = MaterialTheme.typography.titleMedium)

            group.main?.let { m ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${m.name} • ${m.lang} • ${sizeLabel(m.sizeBytes)}")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (installed) {
                        Button(onClick = { onUse(m) }) { Text("Use") }
                    } else {
                        Button(onClick = { onDownload(m) }) { Text("Download") }
                    }
                }
            } ?: Text("No models in this language.")

            if (group.children.isNotEmpty()) {
                Divider()
                Text("More in ${group.baseLang.uppercase()}", style = MaterialTheme.typography.labelLarge)
                group.children.forEach { c ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${c.name} • ${c.lang} • ${sizeLabel(c.sizeBytes)}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (installed) {
                                OutlinedButton(onClick = { onUse(c) }) { Text("Use") }
                            } else {
                                OutlinedButton(onClick = { onDownload(c) }) { Text("Download") }
                            }
                        }
                    }
                }
            }
        }
    }
}

// local formatter for this screen (uses simple decimal MB/GB)
private fun sizeLabel(bytes: Long): String {
    val mb = bytes.toDouble() / 1_000_000.0
    return if (mb >= 1000) String.format("%.1f GB", mb / 1000.0) else String.format("%.0f MB", mb)
}
