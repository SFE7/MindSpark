package com.mindspark.compose.features.whiteboard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mindspark.compose.features.whiteboard.logic.ViewportState
import com.mindspark.compose.features.whiteboard.ui.widgets.CanvasViewport
import com.mindspark.compose.features.whiteboard.ui.widgets.DrawingLayer
import com.mindspark.compose.features.whiteboard.ui.widgets.IdeaCard
import com.mindspark.compose.ui.theme.MindSparkBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomScreen(
    roomId: String,
    onBack: () -> Unit,
    viewModel: RoomViewModel = viewModel(
        key = "room-$roomId",
        factory = RoomViewModel.provideFactory(roomId)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    var viewport by remember { mutableStateOf(ViewportState(scale = 0.8f)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    when (val state = uiState) {
                        is RoomUiState.Ready -> Text(state.room.name)
                        else -> Text("Room")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MindSparkBackground)
        ) {
            when (val state = uiState) {
                is RoomUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is RoomUiState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is RoomUiState.Ready -> {
                    CanvasViewport(
                        viewport = viewport,
                        onViewportChange = { viewport = it }
                    ) {
                        DrawingLayer(elements = state.canvas.elements)

                        state.canvas.ideas.forEachIndexed { index, idea ->
                            key(idea.id) {
                                IdeaCard(
                                    idea = idea,
                                    gesturesEnabled = false,
                                    index = index
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
