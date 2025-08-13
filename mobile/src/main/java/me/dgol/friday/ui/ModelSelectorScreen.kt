@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package me.dgol.friday.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ModelSelectorScreen(
    vm: FridayViewModel,
    appContext: Context
) {
    val ui by vm.ui.collectAsState()

    LaunchedEffect(Unit) {
        vm.init(appContext)
        vm.loadRegistry(appContext)
    }

    var filtersExpanded by remember { mutableStateOf(false) }
    val expandedFamilies = remember { mutableStateListOf<String>() }
    val expandedLocales = remember { mutableStateListOf<String>() }

    val families = vm.familiesForDisplay(appContext)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Models", style = MaterialTheme.typography.headlineSmall)

        // ---- Collapsible Filters ----
        ElevatedCard {
            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Filters", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { filtersExpanded = !filtersExpanded }) {
                        Text(if (filtersExpanded) "Hide" else "Show")
                    }
                }
                AnimatedVisibility(visible = filtersExpanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                        // Size range numeric fields (digits-only filtering)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            var minText by remember(ui.minSizeMb) { mutableStateOf(ui.minSizeMb.toString()) }
                            var maxText by remember(ui.maxSizeMb) { mutableStateOf(ui.maxSizeMb.toString()) }

                            OutlinedTextField(
                                value = minText,
                                onValueChange = { s ->
                                    minText = s.filter { it.isDigit() }
                                    val v = minText.toIntOrNull() ?: 0
                                    vm.setMinSizeMb(v)
                                },
                                label = { Text("Min MB") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = maxText,
                                onValueChange = { s ->
                                    maxText = s.filter { it.isDigit() }
                                    val v = maxText.toIntOrNull() ?: ui.minSizeMb
                                    vm.setMaxSizeMb(v)
                                },
                                label = { Text("Max MB") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Range slider
                        RangeSlider(
                            value = ui.minSizeMb.toFloat()..ui.maxSizeMb.toFloat(),
                            onValueChange = { range ->
                                val min = range.start.toInt()
                                val max = range.endInclusive.toInt()
                                vm.setSizeRange(min, max)
                            },
                            valueRange = 0f..5000f,
                            steps = 100
                        )

                        Text(
                            "Tip: Wider range shows more models. Sizes are approximate when provided by the registry."
                        )
                    }
                }
            }
        }

        // ---- Family sections ----
        if (ui.busy && families.isEmpty()) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        families.forEach { family ->
            ElevatedCard {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(family.baseLabel, style = MaterialTheme.typography.titleLarge)
                            Text(
                                "${family.children.size} locale${if (family.children.size == 1) "" else "s"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton(onClick = {
                            if (expandedFamilies.contains(family.baseLang)) expandedFamilies.remove(family.baseLang)
                            else expandedFamilies.add(family.baseLang)
                        }) {
                            Text(if (expandedFamilies.contains(family.baseLang)) "Collapse" else "Expand")
                        }
                    }

                    AnimatedVisibility(visible = expandedFamilies.contains(family.baseLang)) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            family.children.forEach { localeGroup ->
                                LanguageSection(
                                    group = localeGroup,
                                    isExpanded = expandedLocales.contains(localeGroup.lang),
                                    selectedLang = ui.selectedLang,
                                    selectedName = ui.selectedName,
                                    busyForLang = (ui.busy && ui.busyLang == localeGroup.lang),
                                    onToggleExpand = {
                                        if (expandedLocales.contains(localeGroup.lang)) expandedLocales.remove(localeGroup.lang)
                                        else expandedLocales.add(localeGroup.lang)
                                    },
                                    onUse = { name -> vm.selectModel(appContext, localeGroup.lang, name) },
                                    onDownload = { item -> vm.downloadRegistryModel(appContext, item) },
                                    onDelete = { name -> vm.deleteInstalledModel(appContext, localeGroup.lang, name) }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun LanguageSection(
    group: LanguageGroup,
    isExpanded: Boolean,
    selectedLang: String,
    selectedName: String?,
    busyForLang: Boolean,
    onToggleExpand: () -> Unit,
    onUse: (String) -> Unit,
    onDownload: (RegistryItem) -> Unit,
    onDelete: (String) -> Unit
) {
    ElevatedCard {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {

            // Header row with main (latest) model for this locale
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(group.langText, style = MaterialTheme.typography.titleMedium)

                    // Show main model meta line (version • size)
                    val e = group.main.entry
                    val meta = buildString {
                        if (!e.version.isNullOrBlank()) append("v${e.version} • ")
                        val size = e.sizeText ?: e.sizeBytes?.let { "${(it / (1024*1024)).toInt()} MB" } ?: ""
                        if (size.isNotBlank()) append(size)
                    }
                    if (meta.isNotBlank()) {
                        Text(meta, style = MaterialTheme.typography.bodySmall)
                    }
                }
                TextButton(onClick = onToggleExpand) {
                    Text(if (isExpanded) "Hide versions" else "Show versions")
                }
            }

            Spacer(Modifier.height(8.dp))

            // "Main" row actions with size next to name
            run {
                val e = group.main.entry
                val size = e.sizeText ?: e.sizeBytes?.let { "${(it / (1024*1024)).toInt()} MB" } ?: null
                val label = if (size != null) "${e.name} — $size" else e.name
                ModelActionRow(
                    label = label,
                    installed = group.main.installed,
                    selected = (selectedLang == group.lang && selectedName == e.name),
                    busy = busyForLang,
                    onUse = { onUse(e.name) },
                    onDownload = { onDownload(group.main) },
                    onDelete = { onDelete(e.name) }
                )
            }

            // Other versions
            AnimatedVisibility(visible = isExpanded && group.others.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Spacer(Modifier.height(8.dp))
                    group.others.forEach { item ->
                        val e = item.entry
                        val size = e.sizeText ?: e.sizeBytes?.let { "${(it / (1024*1024)).toInt()} MB" } ?: null
                        val label = if (size != null) "${e.name} — $size" else e.name
                        ModelActionRow(
                            label = label,
                            installed = item.installed,
                            selected = (selectedLang == group.lang && selectedName == e.name),
                            busy = busyForLang,
                            onUse = { onUse(e.name) },
                            onDownload = { onDownload(item) },
                            onDelete = { onDelete(e.name) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModelActionRow(
    label: String,
    installed: Boolean,
    selected: Boolean,
    busy: Boolean,
    onUse: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selected) {
                AssistChip(label = { Text("Using") }, onClick = {})
            } else {
                OutlinedButton(onClick = onUse, enabled = !busy) { Text("Use") }
            }

            if (installed) {
                OutlinedButton(onClick = onDelete, enabled = !busy) { Text("Delete") }
            } else {
                Button(onClick = onDownload, enabled = !busy) { Text("Download") }
            }
        }
        if (busy) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}
