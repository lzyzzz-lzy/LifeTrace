package com.example.lifetrace.viewmodel

import android.content.Context
import android.content.Intent
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lifetrace.data.database.entity.MemoryNodeEntity
import com.example.lifetrace.data.database.entity.MemoryAttachmentEntity
import com.example.lifetrace.data.database.entity.AttachmentType
import com.example.lifetrace.data.database.entity.TripStatus
import com.example.lifetrace.data.database.repository.MemoryNodeRepository
import com.example.lifetrace.data.database.repository.MemoryAttachmentRepository
import com.example.lifetrace.data.database.repository.TrackPointRepository
import com.example.lifetrace.data.database.repository.TripRepository
import com.example.lifetrace.media.MediaStorageManager
import com.example.lifetrace.service.TripRecordingService
import com.example.lifetrace.state.HomeUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val tripRepository: TripRepository,
    private val memoryNodeRepository: MemoryNodeRepository,
    private val attachmentRepository: MemoryAttachmentRepository,
    private val mapViewModel: MapViewModel,
    private val trackPointRepository: TrackPointRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    private var timerJob: Job? = null
    private var distanceJob: Job? = null

    // === 回忆编辑状态 ===
    private val _editingMemoryNode = MutableStateFlow<MemoryNodeEntity?>(null)
    val editingMemoryNode: StateFlow<MemoryNodeEntity?> = _editingMemoryNode

    private val _editingAttachments = MutableStateFlow<List<MemoryAttachmentEntity>>(emptyList())
    val editingAttachments: StateFlow<List<MemoryAttachmentEntity>> = _editingAttachments

    init {
        restoreTripIfNeed()
    }

    private fun restoreTripIfNeed() {
        viewModelScope.launch {
            val trip = tripRepository.getCurrentTrip()
            if (trip != null) {
                bindTripData(trip.tripId)

                _uiState.value = HomeUiState(
                    activeTrip = trip,
                    isRecording = trip.status == TripStatus.RECORDING,
                    isPaused = trip.status == TripStatus.PAUSE,
                    isLoading = false,
                    timeRefreshTick = 0L
                )

                if (trip.status == TripStatus.RECORDING) {
                    startTimer()
                    mapViewModel.returnToRecording()
                } else {
                    mapViewModel.enterPausedRecording()
                }
            } else {
                _uiState.value = HomeUiState(
                    activeTrip = null,
                    isRecording = false,
                    isPaused = false,
                    isLoading = false,
                    timeRefreshTick = 0L
                )
                mapViewModel.onAppStart(hasActiveTrip = false)
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            var tick = 0L
            while (true) {
                delay(1000)
                tick++
                _uiState.update { it.copy(timeRefreshTick = tick) }
            }
        }
    }

    fun startTrip(context: Context, title: String) {
        viewModelScope.launch {
            val trip = tripRepository.startNewTrip(title)
            bindTripData(trip.tripId)

            _uiState.value = HomeUiState(
                activeTrip = trip,
                isRecording = true,
                isPaused = false,
                isLoading = false,
                timeRefreshTick = 0L
            )

            mapViewModel.returnToRecording()
            startRecordingService(context)
            startTimer()
        }
    }

    fun pauseTrip(context: Context) {
        val trip = _uiState.value.activeTrip ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val pauseTrip = trip.copy(
                accumulatedDuration = trip.accumulatedDuration + (now - (trip.lastResumeTime ?: now)),
                lastResumeTime = null,
                status = TripStatus.PAUSE
            )
            tripRepository.updateTrip(pauseTrip)
            stopRecordingService(context)
            timerJob?.cancel()

            _uiState.value = _uiState.value.copy(
                activeTrip = pauseTrip,
                isRecording = false,
                isPaused = true,
                timeRefreshTick = _uiState.value.timeRefreshTick + 1
            )
            mapViewModel.enterPausedRecording()
        }
    }

    fun resumeTrip(context: Context) {
        val trip = _uiState.value.activeTrip ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val resumeTrip = trip.copy(
                lastResumeTime = now,
                status = TripStatus.RECORDING
            )
            tripRepository.updateTrip(resumeTrip)

            bindTripData(resumeTrip.tripId)
            startRecordingService(context)

            _uiState.value = _uiState.value.copy(
                activeTrip = resumeTrip,
                isRecording = true,
                isPaused = false,
                timeRefreshTick = _uiState.value.timeRefreshTick + 1
            )
            mapViewModel.returnToRecording()
            startTimer()
        }
    }

    fun finishTrip(context: Context) {
        val trip = _uiState.value.activeTrip ?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val finalAccumulatedDuration = if (trip.status == TripStatus.RECORDING) {
                trip.accumulatedDuration + (now - (trip.lastResumeTime ?: now))
            } else {
                trip.accumulatedDuration
            }

            val endTrip = trip.copy(
                status = TripStatus.FINISHED,
                endTime = now,
                accumulatedDuration = finalAccumulatedDuration,
                lastResumeTime = null,
            )
            tripRepository.updateTrip(endTrip)

            timerJob?.cancel()
            stopRecordingService(context)

            clearTripDataBinding()

            _uiState.value = _uiState.value.copy(
                activeTrip = null,
                isRecording = false,
                isPaused = false,
                timeRefreshTick = 0L
            )
            mapViewModel.enterExplore()
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        distanceJob?.cancel()
    }

    fun togglePanel() {
        _uiState.value = _uiState.value.copy(
            isPanelExpanded = !_uiState.value.isPanelExpanded
        )
    }

    // === 回忆功能方法（重构后） ===

    // 长按地图添加回忆节点（快速创建）
    fun addMemoryNode(latitude: Double, longitude: Double) {
        startEditingMemory(latitude, longitude)
    }

    // 开始编辑回忆
    fun startEditingMemory(latitude: Double, longitude: Double) {
        val trip = _uiState.value.activeTrip ?: return
        viewModelScope.launch {
            // 创建新的回忆节点
            val newNode = MemoryNodeEntity(
                tripId = trip.tripId,
                latitude = latitude,
                longitude = longitude,
                text = null,
                coverUri = null,
                timestamp = System.currentTimeMillis()
            )
            val nodeId = memoryNodeRepository.insertMemoryNode(newNode)

            // 获取创建的节点
            val createdNode = memoryNodeRepository.getMemoryNodeById(nodeId)
            _editingMemoryNode.value = createdNode
            _editingAttachments.value = emptyList()

            // 更新 UI 状态
            _uiState.update { it.copy(showMemoryEditor = true) }
        }
    }

    // 继续编辑现有回忆
    fun continueEditingMemory(node: MemoryNodeEntity) {
        viewModelScope.launch {
            _editingMemoryNode.value = node
            val attachments = attachmentRepository.getAttachmentsForNode(node.id)
            _editingAttachments.value = attachments
            _uiState.update { it.copy(showMemoryEditor = true) }
        }
    }

    // 添加照片附件
    fun addPhotoAttachment(photoUri: String) {
        val node = _editingMemoryNode.value ?: return
        viewModelScope.launch {
            val attachments = _editingAttachments.value
            val newOrderIndex = attachments.maxOfOrNull { it.orderIndex }?.plus(1) ?: 0

            val attachment = MemoryAttachmentEntity(
                memoryNodeId = node.id,
                type = AttachmentType.PHOTO,
                uri = photoUri,
                duration = 0L,
                orderIndex = newOrderIndex
            )
            attachmentRepository.insertAttachment(attachment)

            // 如果没有封面，设置为封面
            if (node.coverUri == null) {
                memoryNodeRepository.updateCoverUri(node.id, photoUri)
                _editingMemoryNode.update { it?.copy(coverUri = photoUri) }
            }

            // 重新加载附件列表
            refreshAttachments(node.id)
        }
    }

    // 添加音频附件
    fun addAudioAttachment(audioUri: String, duration: Long) {
        val node = _editingMemoryNode.value ?: return
        viewModelScope.launch {
            val attachments = _editingAttachments.value
            val newOrderIndex = attachments.maxOfOrNull { it.orderIndex }?.plus(1) ?: 0

            val attachment = MemoryAttachmentEntity(
                memoryNodeId = node.id,
                type = AttachmentType.AUDIO,
                uri = audioUri,
                duration = duration,
                orderIndex = newOrderIndex
            )
            attachmentRepository.insertAttachment(attachment)

            refreshAttachments(node.id)
        }
    }

    // 添加视频附件
    fun addVideoAttachment(videoUri: String, duration: Long) {
        val node = _editingMemoryNode.value ?: return
        viewModelScope.launch {
            val attachments = _editingAttachments.value
            val newOrderIndex = attachments.maxOfOrNull { it.orderIndex }?.plus(1) ?: 0

            val attachment = MemoryAttachmentEntity(
                memoryNodeId = node.id,
                type = AttachmentType.VIDEO,
                uri = videoUri,
                duration = duration,
                orderIndex = newOrderIndex
            )
            attachmentRepository.insertAttachment(attachment)

            // 如果没有封面，设置为封面
            if (node.coverUri == null) {
                memoryNodeRepository.updateCoverUri(node.id, videoUri)
                _editingMemoryNode.update { it?.copy(coverUri = videoUri) }
            }

            refreshAttachments(node.id)
        }
    }

    // 更新文字描述
    fun updateMemoryText(text: String) {
        val node = _editingMemoryNode.value ?: return
        viewModelScope.launch {
            memoryNodeRepository.updateMemoryNode(node.id, text, node.coverUri)
            _editingMemoryNode.update { it?.copy(text = text) }
        }
    }

    // 设置封面
    fun setCoverUri(uri: String) {
        val node = _editingMemoryNode.value ?: return
        viewModelScope.launch {
            memoryNodeRepository.updateCoverUri(node.id, uri)
            _editingMemoryNode.update { it?.copy(coverUri = uri) }
        }
    }

    // 删除附件
    fun deleteAttachment(attachment: MemoryAttachmentEntity) {
        viewModelScope.launch {
            attachmentRepository.deleteAttachmentWithFile(attachment.id, attachment.memoryNodeId)

            // 如果删除的是封面，重新选择
            val node = _editingMemoryNode.value
            if (node?.coverUri == attachment.uri) {
                val remaining = attachmentRepository.getAttachmentsForNode(node.id)
                val newCover = remaining.firstOrNull { it.type == AttachmentType.PHOTO }?.uri
                            ?: remaining.firstOrNull()?.uri
                memoryNodeRepository.updateCoverUri(node.id, newCover)
                _editingMemoryNode.update { it?.copy(coverUri = newCover) }
            }

            refreshAttachments(node?.id ?: return@launch)
        }
    }

    // 完成编辑
    fun finishEditingMemory() {
        _editingMemoryNode.value = null
        _editingAttachments.value = emptyList()
        _uiState.update { it.copy(showMemoryEditor = false) }
    }

    // 取消编辑（删除未保存的节点）
    fun cancelEditingMemory() {
        val node = _editingMemoryNode.value ?: return
        viewModelScope.launch {
            // 如果节点没有附件，删除节点
            val attachments = attachmentRepository.getAttachmentsForNode(node.id)
            if (attachments.isEmpty() && node.text.isNullOrBlank()) {
                memoryNodeRepository.deleteMemoryNodeWithFile(node.id)
            }
            finishEditingMemory()
        }
    }

    // 删除回忆节点
    fun deleteMemoryNode(nodeId: Long) {
        viewModelScope.launch {
            memoryNodeRepository.deleteMemoryNodeWithFile(nodeId)
        }
    }

    private suspend fun refreshAttachments(nodeId: Long) {
        val attachments = attachmentRepository.getAttachmentsForNode(nodeId)
        _editingAttachments.value = attachments
    }

    private fun bindTripData(tripId: Long) {
        trackPointRepository.setCurrentTripId(tripId)
        mapViewModel.bindCurrentTrip(tripId)

        distanceJob?.cancel()
        distanceJob = viewModelScope.launch {
            trackPointRepository.observeCurrentTrackPoints().collectLatest { points ->
                val distance = calculateDistanceMeters(points)
                _uiState.update { it.copy(distanceMeters = distance) }
            }
        }
    }

    private fun clearTripDataBinding() {
        distanceJob?.cancel()
        distanceJob = null
        trackPointRepository.clearCurrentTripId()
        mapViewModel.clearCurrentTripBinding()
        _uiState.update { it.copy(distanceMeters = 0f) }
    }

    private fun calculateDistanceMeters(points: List<com.example.lifetrace.data.database.entity.TrackPointEntity>): Float {
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

    private fun startRecordingService(context: Context) {
        val intent = Intent(context, TripRecordingService::class.java)
        context.startForegroundService(intent)
    }

    private fun stopRecordingService(context: Context) {
        val intent = Intent(context, TripRecordingService::class.java)
        context.stopService(intent)
    }
}
