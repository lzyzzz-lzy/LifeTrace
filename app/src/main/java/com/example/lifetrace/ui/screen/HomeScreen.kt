package com.example.lifetrace.ui.screen

import android.content.Intent
import android.net.Uri
import android.location.Location
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.lifetrace.R
import com.example.lifetrace.data.database.entity.TrackPointEntity
import com.example.lifetrace.data.database.entity.MemoryNodeEntity
import com.example.lifetrace.data.database.entity.MemoryAttachmentEntity
import com.example.lifetrace.data.database.entity.AttachmentType
import com.example.lifetrace.state.MapMode
import com.example.lifetrace.ui.components.BottomControlPanel
import com.example.lifetrace.ui.components.TopBar
import com.example.lifetrace.ui.map.LifeTraceMap
import com.example.lifetrace.viewmodel.HomeViewModel
import com.example.lifetrace.viewmodel.MapViewModel
import com.example.lifetrace.ui.components.MemoryPreviewBottomSheet
import com.example.lifetrace.ui.components.SystemCameraLauncher
import com.example.lifetrace.ui.components.SystemCameraMode
import com.example.lifetrace.ui.components.AudioRecordingScreen
import com.example.lifetrace.ui.components.FullScreenImageViewer
import com.example.lifetrace.data.database.repository.MemoryAttachmentRepository
import com.example.lifetrace.ui.components.MemoryEditorBottomSheet
import com.example.lifetrace.ui.overlay.OverlayHost
import com.example.lifetrace.ui.overlay.OverlayState

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    mapViewModel: MapViewModel,
) {
    val context = LocalContext.current   // ✅ 用 Activity context
    val attachmentRepository = remember { MemoryAttachmentRepository.getInstance(context.applicationContext) }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mapUiState by mapViewModel.uiState.collectAsStateWithLifecycle()

    // ✅ Overlay 状态：统一类型引用
    var overlayState by remember { mutableStateOf<OverlayState?>(null) }

    var currentLat by remember { mutableStateOf(0.0) }
    var currentLng by remember { mutableStateOf(0.0) }

    val editingNode by viewModel.editingMemoryNode.collectAsStateWithLifecycle()
    val editingAttachments by viewModel.editingAttachments.collectAsStateWithLifecycle()

    val selectedMemoryNode by mapViewModel.selectedMemoryNode.collectAsStateWithLifecycle()
    var previewAttachments by remember { mutableStateOf<List<MemoryAttachmentEntity>>(emptyList()) }

    LaunchedEffect(selectedMemoryNode?.id) {
        previewAttachments = if (selectedMemoryNode != null) {
            attachmentRepository.getAttachmentsForNode(selectedMemoryNode!!.id)
        } else emptyList()
    }

    // Overlay 触发回调
    val onImageClick: (String) -> Unit = { uri ->
        overlayState = OverlayState.ImagePreview(uri)
    }
    val onVideoClick: (String) -> Unit = { uri ->
        overlayState = OverlayState.VideoPlayer(uri)
    }
    val onAudioRecorderClick: (Long) -> Unit = { nodeId ->
        overlayState = OverlayState.AudioRecorder(nodeId)
    }
    val onAudioPlayerClick: (String) -> Unit = { uri ->
        overlayState = OverlayState.AudioPlayer(uri)
    }

    val onAddMemoryNode = {
        if (currentLat != 0.0 && currentLng != 0.0) {
            viewModel.startEditingMemory(currentLat, currentLng)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        LifeTraceMap(
            modifier = Modifier.fillMaxSize(),
            mapUiState = mapUiState,
            onMapMoved = { fromUserGesture, zoomLevel ->
                mapViewModel.onMapCameraChanged(fromUserGesture, zoomLevel)
            },
            onExploreTripClicked = { tripId -> mapViewModel.onExploreTripClicked(tripId) },
            onMapLongPressAddMemory = { lat, lng ->
                if (uiState.isRecording || uiState.isPaused) viewModel.startEditingMemory(lat, lng)
            },
            onMemoryNodeClicked = { node ->
                mapViewModel.onMemoryNodeClicked(node)
            },
            onCurrentLocationChanged = { lat, lng ->
                currentLat = lat
                currentLng = lng
            },
        )

        TopBar(
            subtitle = when (mapUiState.mode) {
                MapMode.MEMORY -> {
                    val title = mapUiState.focusedTrip?.title ?: "旅程"
                    val nodes = mapUiState.focusedTripMemoryNodes.size
                    val distanceKm = calculateDistanceMeters(mapUiState.focusedTripTrackPoints) / 1000f
                    "$title · ${String.format("%.2f", distanceKm)} km · $nodes 个节点"
                }
                MapMode.RECORDING, MapMode.RECORDING_MEMORY ->
                    "记录中 · ${uiState.distanceText} · ${uiState.averageSpeedText}"
                else -> null
            },
            showDelete = mapUiState.mode == MapMode.MEMORY,
            onDelete = { mapViewModel.deleteFocusedTrip() },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        BottomControlPanel(
            uiState = uiState,
            onStart = { title -> viewModel.startTrip(context = context.applicationContext, title = title) },
            onPause = { viewModel.pauseTrip(context.applicationContext) },
            onResume = { viewModel.resumeTrip(context.applicationContext) },
            onFinish = { viewModel.finishTrip(context.applicationContext) },
            onAddMemory = onAddMemoryNode,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (selectedMemoryNode != null) {
            MemoryPreviewBottomSheet(
                node = selectedMemoryNode!!,
                attachments = previewAttachments,
                onDismiss = { mapViewModel.dismissMemoryNodeDetail() },
                onEdit = {
                    viewModel.continueEditingMemory(selectedMemoryNode!!)
                    mapViewModel.dismissMemoryNodeDetail()
                },
                onDelete = {
                    viewModel.deleteMemoryNode(selectedMemoryNode!!.id)
                    mapViewModel.dismissMemoryNodeDetail()
                },
                onImageClick = onImageClick,
                onVideoClick = onVideoClick,
                onAudioRecorderClick = onAudioRecorderClick,
                onAudioPlayerClick = onAudioPlayerClick,
            )
        }

        if (editingNode != null) {
            MemoryEditorBottomSheet(
                node = editingNode!!,
                attachments = editingAttachments,
                onDismiss = { viewModel.finishEditingMemory() },
                onTextChange = { viewModel.updateMemoryText(it) },

                // ✅ 不要 startActivity + 空 File，直接走 Overlay 相机
                onAddPhoto = {
                    overlayState = OverlayState.Camera(
                        memoryNodeId = editingNode!!.id,
                        mode = SystemCameraMode.PHOTO
                    )
                },
                onAddVideo = {
                    overlayState = OverlayState.Camera(
                        memoryNodeId = editingNode!!.id,
                        mode = SystemCameraMode.VIDEO
                    )
                },
                onAddAudio = {
                    overlayState = OverlayState.AudioRecorder(editingNode!!.id)
                },

                onDeleteAttachment = { viewModel.deleteAttachment(it) },
                onSetCover = { viewModel.setCoverUri(it) },

                // ✅ 适配：假设参数是 MemoryAttachmentEntity
                onVideoPlay = { videoAttachment ->
                    onVideoClick(videoAttachment.uri)
                },

                onFinish = { viewModel.finishEditingMemory() },
            )
        }
    }

    // ✅ Overlay 主机
    OverlayHost(
        overlayState = overlayState,
        onDismiss = { overlayState = null }
    )
}

// ✅ 一定要在 HomeScreen 外面
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