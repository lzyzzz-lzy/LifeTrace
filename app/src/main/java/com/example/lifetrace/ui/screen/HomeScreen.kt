package com.example.lifetrace.ui.screen

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
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
import com.example.lifetrace.ui.components.MemoryEditorBottomSheet
import com.example.lifetrace.ui.components.MemoryPreviewBottomSheet
import com.example.lifetrace.ui.components.SystemCameraLauncher
import com.example.lifetrace.ui.components.SystemCameraMode
import com.example.lifetrace.ui.components.AudioRecordingScreen
import com.example.lifetrace.ui.components.FullScreenImageViewer
import com.example.lifetrace.data.database.repository.MemoryAttachmentRepository

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    mapViewModel: MapViewModel,
) {
    val context = LocalContext.current.applicationContext
    val attachmentRepository = remember { MemoryAttachmentRepository.getInstance(context) }
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
    var showCamera by remember { mutableStateOf(false) }
    var showAudioRecorder by remember { mutableStateOf(false) }
    var cameraMode by remember { mutableStateOf(com.example.lifetrace.ui.components.SystemCameraMode.PHOTO) }

    // 编辑状态
    val editingNode by viewModel.editingMemoryNode.collectAsStateWithLifecycle()
    val editingAttachments by viewModel.editingAttachments.collectAsStateWithLifecycle()

    // 预览状态 - 用于地图 marker 点击后的预览
    val selectedMemoryNode by mapViewModel.selectedMemoryNode.collectAsStateWithLifecycle()
    var previewAttachments by remember { mutableStateOf<List<MemoryAttachmentEntity>>(emptyList()) }

    // 当选中的节点变化时，加载附件
    LaunchedEffect(selectedMemoryNode?.id) {
        if (selectedMemoryNode != null) {
            previewAttachments = attachmentRepository.getAttachmentsForNode(selectedMemoryNode!!.id)
        } else {
            previewAttachments = emptyList()
        }
    }

    // 全屏图片查看器状态
    var selectedPhoto by remember { mutableStateOf<MemoryAttachmentEntity?>(null) }
    var photoIndex by remember { mutableStateOf(-1) }
    var photoCount by remember { mutableStateOf(0) }

    val onAddMemoryNode = {
        if (currentLat != 0.0 && currentLng != 0.0) {
            viewModel.startEditingMemory(currentLat, currentLng)
        }
    }

    val onMapLongPressAddMemory = { lat: Double, lng: Double ->
        if (uiState.isRecording || uiState.isPaused) {
            viewModel.startEditingMemory(lat, lng)
        }
    }

    val onMemoryNodeClicked = { node: MemoryNodeEntity ->
        mapViewModel.onMemoryNodeClicked(node)
    }

    val onMapMoved = { fromUserGesture: Boolean, zoomLevel: Float ->
        mapViewModel.onMapCameraChanged(fromUserGesture, zoomLevel)
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
            onExploreTripClicked = { tripId -> mapViewModel.onExploreTripClicked(tripId) },
            onMapLongPressAddMemory = onMapLongPressAddMemory,
            onMemoryNodeClicked = onMemoryNodeClicked,
            onCurrentLocationChanged = { lat, lng ->
                currentLat = lat
                currentLng = lng
            },
        )

        TopBar(
            subtitle = topBarSubtitle,
            showDelete = mapUiState.mode == MapMode.MEMORY,
            onDelete = { mapViewModel.deleteFocusedTrip() },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        BottomControlPanel(
            uiState = uiState,
            onStart = onStartTrip,
            onPause = onPauseTrip,
            onResume = onResumeTrip,
            onFinish = onFinishTrip,
            onAddMemory = onAddMemoryNode,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // 回忆预览
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
                onPhotoClick = { photo, index, total ->
                    selectedPhoto = photo
                    photoIndex = index
                    photoCount = total
                },
            )
        }

        // 相机拍照/录像界面
        if (showCamera && editingNode != null) {
            SystemCameraLauncher(
                mode = cameraMode,
                tripId = uiState.activeTrip?.tripId ?: 0L,
                onResult = { uri, duration ->
                    if (cameraMode == com.example.lifetrace.ui.components.SystemCameraMode.VIDEO) {
                        // 视频模式
                        uri?.let {
                            viewModel.addVideoAttachment(it, duration ?: 0L)
                        }
                    } else {
                        // 照片模式
                        uri?.let {
                            viewModel.addPhotoAttachment(it)
                        }
                    }
                    showCamera = false
                },
                onCancel = {
                    showCamera = false
                },
            )
        }

        // 音频录制界面
        if (showAudioRecorder && editingNode != null) {
            AudioRecordingScreen(
                onRecordingComplete = { audioUri, duration ->
                    viewModel.addAudioAttachment(audioUri, duration)
                    showAudioRecorder = false
                },
                onCancel = {
                    showAudioRecorder = false
                },
            )
        }

        // 全屏图片查看器（放在最外层以确保 z-index 最高）
        if (selectedPhoto != null) {
            FullScreenImageViewer(
                photo = selectedPhoto!!,
                onDismiss = { selectedPhoto = null }
            )
        }

        // 回忆编辑器
        if (editingNode != null) {
            MemoryEditorBottomSheet(
                node = editingNode!!,
                attachments = editingAttachments,
                onDismiss = { viewModel.finishEditingMemory() },
                onTextChange = { viewModel.updateMemoryText(it) },
                onAddPhoto = {
                    cameraMode = com.example.lifetrace.ui.components.SystemCameraMode.PHOTO
                    showCamera = true
                },
                onAddAudio = {
                    showAudioRecorder = true
                },
                onAddVideo = {
                    cameraMode = com.example.lifetrace.ui.components.SystemCameraMode.VIDEO
                    showCamera = true
                },
                onDeleteAttachment = { viewModel.deleteAttachment(it) },
                onSetCover = { viewModel.setCoverUri(it) },
                onFinish = { viewModel.finishEditingMemory() },
            )
        }
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