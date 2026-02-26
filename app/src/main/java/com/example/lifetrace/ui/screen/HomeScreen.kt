package com.example.lifetrace.ui.screen

import android.location.Location
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lifetrace.R
import com.example.lifetrace.data.database.entity.TrackPointEntity
import com.example.lifetrace.state.MapMode
import com.example.lifetrace.ui.components.BottomControlPanel
import com.example.lifetrace.ui.components.TopBar
import com.example.lifetrace.ui.map.LifeTraceMap
import com.example.lifetrace.viewmodel.HomeViewModel
import com.example.lifetrace.viewmodel.MapViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    mapViewModel: MapViewModel,
) {
    val context = LocalContext.current.applicationContext
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mapUiState by mapViewModel.uiState.collectAsStateWithLifecycle()

    val onStartTrip = { tripTitle: String ->
        viewModel.startTrip(context = context, title = tripTitle)
    }

    val onPauseTrip = { viewModel.pauseTrip(context) }
    val onResumeTrip = { viewModel.resumeTrip(context) }
    val onFinishTrip = { viewModel.finishTrip(context) }

    var currentLat by remember { mutableStateOf(0.0) }
    var currentLng by remember { mutableStateOf(0.0) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val onAddMemoryNode = {
        if (currentLat != 0.0 && currentLng != 0.0) {
            viewModel.addMemoryNode(currentLat, currentLng)
        }
    }

    val onMapMoved = { fromUserGesture: Boolean, zoomLevel: Float ->
        mapViewModel.onMapCameraChanged(fromUserGesture, zoomLevel)
    }

    val onExploreTripClicked = { tripId: Long ->
        mapViewModel.onExploreTripClicked(tripId)
    }

    val onMapLongPressAddMemory = { lat: Double, lng: Double ->
        if (uiState.isRecording || uiState.isPaused) {
            viewModel.addMemoryNode(lat, lng)
        }
    }

    val topBarSubtitle = when (mapUiState.mode) {
        MapMode.MEMORY -> {
            val title = mapUiState.focusedTrip?.title ?: "旅程"
            val nodes = mapUiState.focusedTripMemoryNodes.size
            val distanceKm = calculateDistanceMeters(mapUiState.focusedTripTrackPoints) / 1000f
            "$title · ${String.format("%.2f", distanceKm)} km · $nodes 个节点"
        }

        MapMode.RECORDING,
        MapMode.RECORDING_MEMORY -> "记录中 · ${uiState.distanceText} · ${uiState.averageSpeedText}"
        else -> null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LifeTraceMap(
            modifier = Modifier.fillMaxSize(),
            mapUiState = mapUiState,
            onMapMoved = onMapMoved,
            onExploreTripClicked = onExploreTripClicked,
            onMapLongPressAddMemory = onMapLongPressAddMemory,
            onCurrentLocationChanged = { lat, lng ->
                currentLat = lat
                currentLng = lng
            },
        )

        TopBar(
            subtitle = topBarSubtitle,
            showDelete = mapUiState.mode == MapMode.MEMORY,
            onDelete = {
                if (mapUiState.mode == MapMode.MEMORY) {
                    showDeleteConfirm = true
                }
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )


        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text(stringResource(id = R.string.delete_trip_dialog_title)) },
                text = { Text(stringResource(id = R.string.delete_trip_dialog_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteConfirm = false
                        mapViewModel.deleteFocusedTrip()
                    }) {
                        Text(stringResource(id = R.string.delete_trip_dialog_confirm))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirm = false }) {
                        Text(stringResource(id = R.string.delete_trip_dialog_cancel))
                    }
                },
            )
        }

        BottomControlPanel(
            uiState = uiState,
            onStart = onStartTrip,
            onPause = onPauseTrip,
            onResume = onResumeTrip,
            onFinish = onFinishTrip,
            onAddMemory = onAddMemoryNode,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

private fun calculateDistanceMeters(points: List<TrackPointEntity>): Float {
    if (points.size < 2) return 0f

    var total = 0f
    for (i in 1 until points.size) {
        val prev = points[i - 1]
        val curr = points[i]
        val result = FloatArray(1)
        Location.distanceBetween(prev.latitude, prev.longitude, curr.latitude, curr.longitude, result)
        total += result.firstOrNull() ?: 0f
    }
    return total
}