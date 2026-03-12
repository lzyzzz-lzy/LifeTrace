package com.example.lifetrace.ui.screen

import android.location.Location
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lifetrace.data.database.entity.MemoryAttachmentEntity
import com.example.lifetrace.data.database.entity.MemoryNodeEntity
import com.example.lifetrace.data.database.entity.TrackPointEntity
import com.example.lifetrace.data.database.repository.MemoryAttachmentRepository
import com.example.lifetrace.state.MapMode
import com.example.lifetrace.ui.components.AboutDialog
import com.example.lifetrace.ui.components.AiSettingsDialog
import com.example.lifetrace.ui.components.AppIntroDialog
import com.example.lifetrace.ui.components.BottomControlPanel
import com.example.lifetrace.ui.components.HelpDialog
import com.example.lifetrace.ui.components.MemoryEditorBottomSheet
import com.example.lifetrace.ui.components.MemoryPreviewBottomSheet
import com.example.lifetrace.ui.components.PrivacyDialog
import com.example.lifetrace.ui.components.SystemCameraLauncher
import com.example.lifetrace.ui.components.SystemCameraMode
import com.example.lifetrace.ui.components.TopBar
import com.example.lifetrace.ui.map.LifeTraceMap
import com.example.lifetrace.ui.overlay.OverlayHost
import com.example.lifetrace.ui.overlay.OverlayState
import com.example.lifetrace.viewmodel.HomeViewModel
import com.example.lifetrace.viewmodel.MapViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    mapViewModel: MapViewModel,
) {
    val context = LocalContext.current
    val attachmentRepository = remember {
        MemoryAttachmentRepository.getInstance(context.applicationContext)
    }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mapUiState by mapViewModel.uiState.collectAsStateWithLifecycle()

    // ✅ 顶层 Overlay 状态（图片/视频/录音/音频播放等都走这里）
    var overlayState by remember { mutableStateOf<OverlayState?>(null) }

    // ✅ 顶层相机状态（走 SystemCameraLauncher）
    var cameraMode by remember { mutableStateOf<SystemCameraMode?>(null) }

    // ✅ 菜单对话框状态
    var showAppIntro by remember { mutableStateOf(false) }
    var showAiSettings by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    var currentLat by remember { mutableStateOf(0.0) }
    var currentLng by remember { mutableStateOf(0.0) }

    // 编辑状态
    val editingNode by viewModel.editingMemoryNode.collectAsStateWithLifecycle()
    val editingAttachments by viewModel.editingAttachments.collectAsStateWithLifecycle()

    // 预览状态
    val selectedMemoryNode = mapUiState.selectedMemoryNode
    var previewAttachments by remember { mutableStateOf<List<MemoryAttachmentEntity>>(emptyList()) }

    LaunchedEffect(selectedMemoryNode?.id) {
        previewAttachments = if (selectedMemoryNode != null) {
            attachmentRepository.getAttachmentsForNode(selectedMemoryNode!!.id)
        } else {
            emptyList()
        }
    }

    // 回调：旅程控制
    val onStartTrip = { title: String ->
        viewModel.startTrip(context = context.applicationContext, title = title)
    }
    val onPauseTrip = { viewModel.pauseTrip(context.applicationContext) }
    val onResumeTrip = { viewModel.resumeTrip(context.applicationContext) }
    val onFinishTrip = { viewModel.finishTrip(context.applicationContext) }

    // 回调：添加节点
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

    // ✅ 回调：附件点击统一走 OverlayState
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

    val topBarSubtitle: String? = when (mapUiState.mode) {
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

        // 1) 地图
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

        // 2) 顶部栏
        TopBar(
            subtitle = topBarSubtitle,
            showDelete = mapUiState.mode == MapMode.MEMORY,
            onDelete = { mapViewModel.deleteFocusedTrip() },
            showShare = mapUiState.mode == MapMode.MEMORY && mapUiState.focusedTrip != null,
            onShare = {
                mapUiState.focusedTrip?.let { trip ->
                    overlayState = OverlayState.ShareTrip(trip.tripId)
                }
            },
            onAppIntro = { showAppIntro = true },
            onAiSettings = { showAiSettings = true },
            onHelp = { showHelp = true },
            onPrivacy = { showPrivacy = true },
            onAbout = { showAbout = true },
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // 3) 底部控制面板
        BottomControlPanel(
            uiState = uiState,
            onStart = onStartTrip,
            onPause = onPauseTrip,
            onResume = onResumeTrip,
            onFinish = onFinishTrip,
            onAddMemory = onAddMemoryNode,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // 4) 记忆预览 BottomSheet
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
                onImageClick = { photo, index, total ->
                    overlayState = OverlayState.ImagePreview(photo.uri) // 或 OverlayState.ImagePreview(photo, index, total)
                },
                onVideoClick = { video ->
                    overlayState = OverlayState.VideoPlayer(video.uri)
                },
                onAudioRecorderClick = {
                    overlayState = OverlayState.AudioRecorder(selectedMemoryNode!!.id)
                },
                onAudioPlayerClick = { audio ->
                    overlayState = OverlayState.AudioPlayer(audio.uri)
                }
            )
        }

        // 5) 记忆编辑 BottomSheet
        if (editingNode != null) {
            MemoryEditorBottomSheet(
                node = editingNode!!,
                attachments = editingAttachments,
                onDismiss = { viewModel.finishEditingMemory() },
                onTextChange = { viewModel.updateMemoryText(it) },

                onAddPhoto = { cameraMode = SystemCameraMode.PHOTO },
                onAddVideo = { cameraMode = SystemCameraMode.VIDEO },
                onAddAudio = { overlayState = OverlayState.AudioRecorder(editingNode!!.id) },

                onDeleteAttachment = { viewModel.deleteAttachment(it) },
                onSetCover = { viewModel.setCoverUri(it) },

                // 如果你的回调参数不是 MemoryAttachmentEntity，就按实际类型改这行
                onVideoPlay = { videoAttachment ->
                    onVideoClick(videoAttachment.uri)
                },

                onFinish = { viewModel.finishEditingMemory() },
            )
        }

        // 6) 顶层相机（SystemCameraLauncher）
        if (cameraMode != null && editingNode != null) {
            val mode = cameraMode!!
            SystemCameraLauncher(
                mode = mode,
                tripId = uiState.activeTrip?.tripId ?: 0L,
                onResult = { path, duration ->
                    if (!path.isNullOrBlank()) {
                        when (mode) {
                            SystemCameraMode.PHOTO -> viewModel.addPhotoAttachment(path)
                            SystemCameraMode.VIDEO -> viewModel.addVideoAttachment(path, duration ?: 0L)
                        }
                    }
                    cameraMode = null
                },
                onCancel = { cameraMode = null }
            )
        }

        // 7) 顶层 Overlay（图片/视频/录音/音频播放等）
        OverlayHost(
            overlayState = overlayState,
            onDismiss = { overlayState = null },
            onAudioRecorded = { uri, duration ->
                viewModel.addAudioAttachment(uri, duration)
            }
        )

        // 8) 菜单对话框
        AppIntroDialog(
            isVisible = showAppIntro,
            onDismiss = { showAppIntro = false }
        )

        AiSettingsDialog(
            isVisible = showAiSettings,
            onDismiss = { showAiSettings = false }
        )

        HelpDialog(
            isVisible = showHelp,
            onDismiss = { showHelp = false }
        )

        PrivacyDialog(
            isVisible = showPrivacy,
            onDismiss = { showPrivacy = false }
        )

        AboutDialog(
            isVisible = showAbout,
            onDismiss = { showAbout = false }
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