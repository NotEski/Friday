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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

private enum class NavDest { Home, Models, Downloads, Settings }

@Composable
fun FridayApp(fromAssistInitial: Boolean) {
    val vm: FridayViewModel = viewModel()
    val ui by vm.ui.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val appCtx = context.applicationContext

    LaunchedEffect(Unit) { vm.init(appCtx) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf(NavDest.Home) }

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

    LaunchedEffect(fromAssistInitial) {
        if (fromAssistInitial) {
            if (hasMicPermission.value) vm.startListening(appCtx)
            else requestMicPermission.launch(micPermission)
        }
    }
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
                    Text("Friday", style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    NavigationDrawerItem(
                        label = { Text("Home") },
                        selected = current == NavDest.Home,
                        onClick = { current = NavDest.Home; scope.launch { drawerState.close() } }
                    )
                    NavigationDrawerItem(
                        label = { Text("Models Downloader") },
                        selected = current == NavDest.Models,
                        onClick = { current = NavDest.Models; scope.launch { drawerState.close() } }
                    )
                    NavigationDrawerItem(
                        label = { Text("Downloaded Models") },
                        selected = current == NavDest.Downloads,
                        onClick = { current = NavDest.Downloads; scope.launch { drawerState.close() } }
                    )
                    NavigationDrawerItem(
                        label = { Text("Settings") },
                        selected = current == NavDest.Settings,
                        onClick = { current = NavDest.Settings; scope.launch { drawerState.close() } }
                    )
                }
            }
        ) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text(title) },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) { Text("☰") }
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
                        // === Chat home ===
                        MainChatScreen(
                            ui = ui,
                            onSend = { vm.sendInputMessage() },
                            onInputChange = { s -> vm.setInput(s) },
                            onToggleMic = {
                                if (ui.isListening) vm.stopListening()
                                else vm.startListening(appCtx)
                            },
                            onShowError = { vm.showError("Example error from pipeline") },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(inner)
                        )
                    }
                }
            }

            if (ui.showPermissionRationale) {
                val packageName = context.packageName
                AlertDialog(
                    onDismissRequest = { vm.dismissPermissionRationale() },
                    title = { Text("Microphone permission") },
                    text = { Text("Friday needs microphone access to listen. You can grant it in Settings.") },
                    confirmButton = {
                        TextButton(onClick = {
                            vm.dismissPermissionRationale()
                            val uri = Uri.fromParts("package", packageName, null)
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
private fun MainChatScreen(
    ui: FridayUiState,
    onSend: () -> Unit,
    onInputChange: (String) -> Unit,
    onToggleMic: () -> Unit,
    onShowError: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Chat history
        ChatList(
            items = ui.messages,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        // Composer row (no KeyboardOptions needed)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = ui.input,
                onValueChange = { s: String ->
                    val nl = s.indexOf('\n')
                    if (nl >= 0) {
                        onInputChange(s.substring(0, nl))
                        onSend()
                    } else {
                        onInputChange(s)
                    }
                },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Type a message…") }
            )
            Button(onClick = onSend, enabled = ui.input.isNotBlank()) { Text("Send") }
            OutlinedButton(onClick = onToggleMic) { Text(if (ui.isListening) "⏹" else "🎙") }
        }

        // Demo button optional
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onShowError, modifier = Modifier.fillMaxWidth()) {
            Text("Show error")
        }
    }
}

@Composable
private fun ChatList(items: List<ChatItem>, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(items.size) {
        if (items.isNotEmpty()) listState.animateScrollToItem(items.lastIndex)
    }
    LazyColumn(
        modifier = modifier,
        state = listState,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items, key = { it.id }) { msg ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = if (msg.role == ChatRole.User) Arrangement.End else Arrangement.Start
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = if (msg.role == ChatRole.User) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 1.dp
                ) {
                    Text(
                        text = msg.text,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        color = if (msg.role == ChatRole.User) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
