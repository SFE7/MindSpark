package com.mindspark.compose.features.rooms.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindspark.compose.BuildConfig
import com.mindspark.compose.core.auth.AuthUiState
import com.mindspark.compose.core.auth.AuthViewModel
import com.mindspark.compose.features.rooms.model.Room

@Composable
fun HomeScreen(
    onOpenRoom: (String) -> Unit,
    onSignOut: () -> Unit,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
    authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    val navigateToRoom by viewModel.navigateToRoom.collectAsState()

    val currentUserId = (authState as? AuthUiState.SignedIn)?.user?.id ?: 0L
    val username = (authState as? AuthUiState.SignedIn)?.user?.username ?: ""

    val snackbarHostState = remember { SnackbarHostState() }

    // Resume refresh hook
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Transient message snackbar
    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    // Navigation trigger (one-shot)
    LaunchedEffect(navigateToRoom) {
        navigateToRoom?.let { roomId ->
            onOpenRoom(roomId)
            viewModel.navigated()
        }
    }

    HomeScreenContent(
        uiState = uiState,
        currentUserId = currentUserId,
        username = username,
        onRefresh = { viewModel.refresh() },
        onCreate = { viewModel.create(it) },
        onJoin = { viewModel.join(it) },
        onOpenRoom = { room -> viewModel.open(room) },
        onDeleteRoom = { room -> viewModel.delete(room) },
        onSignOut = onSignOut,
        snackbarHostState = snackbarHostState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenContent(
    uiState: HomeUiState,
    currentUserId: Long,
    username: String,
    onRefresh: () -> Unit,
    onCreate: (String) -> Unit,
    onJoin: (String) -> Unit,
    onOpenRoom: (Room) -> Unit,
    onDeleteRoom: (Room) -> Unit,
    onSignOut: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    modifier: Modifier = Modifier
) {
    var showChoiceDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "mindspark",
                            fontFamily = FontFamily.Serif,
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (username.isNotEmpty()) {
                            Text(
                                text = username,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh"
                        )
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Sign out"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showChoiceDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add room"
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (uiState) {
                is HomeUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is HomeUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val errorText = if (uiState.isConnection) {
                            "Cannot connect to server at ${BuildConfig.HOST_IP}:8080.\nCheck network and verify server is running."
                        } else {
                            uiState.message
                        }
                        Text(
                            text = errorText,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onRefresh) {
                            Text("Retry")
                        }
                    }
                }
                is HomeUiState.Success -> {
                    if (uiState.rooms.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No rooms yet.\nTap + to create or join a room.",
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(
                                items = uiState.rooms,
                                key = { it.id }
                            ) { room ->
                                val isOwner = room.owner?.id == currentUserId
                                RoomCard(
                                    room = room,
                                    isOwner = isOwner,
                                    onClick = { onOpenRoom(room) },
                                    onDelete = { onDeleteRoom(room) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showChoiceDialog) {
        AlertDialog(
            onDismissRequest = { showChoiceDialog = false },
            title = { Text("Add Room") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            showChoiceDialog = false
                            showCreateDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Create room")
                    }
                    OutlinedButton(
                        onClick = {
                            showChoiceDialog = false
                            showJoinDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Join with code")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showChoiceDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showCreateDialog) {
        CreateRoomDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                showCreateDialog = false
                onCreate(name)
            }
        )
    }

    if (showJoinDialog) {
        JoinRoomDialog(
            onDismiss = { showJoinDialog = false },
            onConfirm = { code ->
                showJoinDialog = false
                onJoin(code)
            }
        )
    }
}
