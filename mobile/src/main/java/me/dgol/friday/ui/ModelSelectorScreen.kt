package me.dgol.friday.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun ModelSelectorScreen(
    vm: FridayViewModel,
    appContext: Context
) {
    val ui by vm.ui.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        vm.init(appContext)
        vm.refreshModels(appContext)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Models",
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            "Choose which speech model Friday should use. You can download a model to work fully offline.",
            style = MaterialTheme.typography.bodyMedium
        )

        Text(
            "Selected: ${ui.selectedLang}",
            style = MaterialTheme.typography.labelLarge
        )

        Spacer(Modifier.height(8.dp))

        ui.models.forEach { item ->
            ModelRow(
                item = item,
                selected = (item.lang == ui.selectedLang),
                busy = (ui.busyLang == item.lang),
                onUse = { vm.selectModel(appContext, item.lang) },
                onDownload = { vm.downloadModel(appContext, item.lang) },
                onDelete = { vm.deleteModel(appContext, item.lang) }
            )
        }
    }
}

@Composable
private fun ModelRow(
    item: ModelItem,
    selected: Boolean,
    busy: Boolean,
    onUse: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        Text(item.label, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Language: ${item.lang} • Size ~${item.approxSizeMb} MB • " +
                    if (item.installed) "Installed" else "Not installed",
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(Modifier.height(8.dp))

        if (busy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onUse, enabled = !busy) {
                Text(if (selected) "Using" else "Use")
            }

            if (item.installed) {
                OutlinedButton(onClick = onDelete, enabled = !busy) {
                    Text("Delete")
                }
            } else {
                OutlinedButton(onClick = onDownload, enabled = !busy) {
                    Text("Download")
                }
            }
        }
    }
}
