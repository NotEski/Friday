@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package me.dgol.friday.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

private enum class NavDest { Home, Models, Downloads, Settings }

@Composable
fun FridayApp(fromAssistInitial: Boolean) {
    val vm: FridayViewModel = viewModel()
    val ui by vm.ui.collectAsState()
    val context = LocalContext.current
    val appCtx = context.applicationContext

    // Ensure view model has initial state (selected model + registry)
    LaunchedEffect(Unit) { vm.init(appCtx) }

    // Navigation drawer
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf(NavDest.Home) }

    // Permissions
    val micPermission = Manifest.permission.RECORD_AUDIO
    val hasMicPermission = remember {
        derivedStateOf {
            ContextCompat.checkSelfPermission(context, micPermission) == PackageManager.PERMISSION_GRANTED
        }
    }
    val requestMicPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.startListening(appCtx) else vm.onPermissionDenied()
    }

    // Auto-start if launched from assistant
    LaunchedEffect(fromAssistInitial) {
        if (fromAssistInitial) {
            if (hasMicPermission.value) vm.startListening(appCtx)
            else requestMicPermission.launch(micPermission)
        }
    }

    // Refresh downloads when navigating to Downloads
    LaunchedEffect(current) {
        if (current == NavDest.Downloads) vm.refreshDownloads(appCtx)
    }

    val title = when (current) {
        NavDest.Home -> "Friday"
        NavDest.Models -> "Friday — Models"
        NavDest.Downloads -> "Friday — Downloaded"
        NavDest.Settings -> "Friday — Settings"
    }

    MaterialTheme {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Friday",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    NavigationDrawerItem(
                        label = { Text("Home") },
                        selected = current == NavDest.Home,
                        onClick = {
                            current = NavDest.Home
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        label = { Text("Models Downloader") },
                        selected = current == NavDest.Models,
                        onClick = {
                            current = NavDest.Models
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        label = { Text("Downloaded Models") },
                        selected = current == NavDest.Downloads,
                        onClick = {
                            current = NavDest.Downloads
                            scope.launch { drawerState.close() }
                        }
                    )
                    NavigationDrawerItem(
                        label = { Text("Settings") },
                        selected = current == NavDest.Settings,
                        onClick = {
                            current = NavDest.Settings
                            scope.launch { drawerState.close() }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        ) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text(title) },
                        navigationIcon = {
                            // Minimal hamburger without icon deps
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Text("☰")
                            }
                        }
                    )
                }
            ) { inner ->
                when (current) {
                    NavDest.Models -> {
                        ModelSelectorScreen(
                            vm = vm,
                            appContext = appCtx,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(inner)
                        )
                    }
                    NavDest.Downloads -> {
                        DownloadedModelsScreen(
                            vm = vm,
                            appContext = appCtx,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(inner)
                        )
                    }
                    NavDest.Settings -> {
                        SettingsScreen(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(inner)
                        )
                    }
                    NavDest.Home -> {
                        MainMicScreen(
                            ui = ui,
                            onStart = {
                                if (hasMicPermission.value) vm.startListening(appCtx)
                                else requestMicPermission.launch(micPermission)
                            },
                            onStop = { vm.stopListening() },
                            onAppendSample = { vm.appendTranscript("User: Hello Friday") },
                            onShowError = { vm.showError("Example error from pipeline") },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(inner)
                        )
                    }
                }
            }

            // ===== Compose Dialogs =====

            if (ui.showPermissionRationale) {
                AlertDialog(
                    onDismissRequest = { vm.dismissPermissionRationale() },
                    title = { Text("Microphone permission") },
                    text = { Text("Friday needs microphone access to listen. You can grant it in Settings.") },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.dismissPermissionRationale()
                            val uri = Uri.fromParts("package", context.packageName, null)
                            context.startActivity(
                                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        }) { Text("Open Settings") }
                    },
                    dismissButton = {
                        TextButton(onClick = { vm.dismissPermissionRationale() }) { Text("Cancel") }
                    }
                )
            }

            ui.errorMessage?.let { msg ->
                AlertDialog(
                    onDismissRequest = { vm.dismissError() },
                    title = { Text("Something went wrong") },
                    text = { Text(msg) },
                    confirmButton = {
                        TextButton(onClick = { vm.dismissError() }) { Text("OK") }
                    }
                )
            }
        }
    }
}

@Composable
private fun MainMicScreen(
    ui: FridayUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onAppendSample: () -> Unit,
    onShowError: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Transcript area
        Text(
            text = if (ui.transcript.isBlank()) "Transcript will appear here…" else ui.transcript,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            textAlign = TextAlign.Start
        )

        Spacer(Modifier.height(16.dp))

        // Mic controls
        if (!ui.isListening) {
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("🎙️  Start Listening")
            }
        } else {
            Button(
                onClick = onStop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("⏹  Stop")
            }

            if (ui.isThinking) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        Spacer(Modifier.height(8.dp))

        // Demo buttons (optional)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onAppendSample,
                modifier = Modifier.weight(1f)
            ) { Text("Append sample") }

            OutlinedButton(
                onClick = onShowError,
                modifier = Modifier.weight(1f)
            ) { Text("Show error") }
        }
    }
}
