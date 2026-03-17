package com.example.lifetrace.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lifetrace.data.database.entity.MemoryNodeEntity
import com.example.lifetrace.data.database.entity.TripEntity
import com.example.lifetrace.data.database.repository.MemoryNodeRepository
import com.example.lifetrace.data.database.repository.TrackPointRepository
import com.example.lifetrace.data.database.repository.TripRepository
import com.example.lifetrace.state.CameraAction
import com.example.lifetrace.state.CameraMode
import com.example.lifetrace.state.MapMode
import com.example.lifetrace.state.MapUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MapViewModel(
    private val trackPointRepository: TrackPointRepository,
    private val memoryNodeRepository: MemoryNodeRepository,
    private val tripRepository: TripRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState

    private var currentTripPointsJob: Job? = null
    private var focusedTripDataJob: Job? = null
    // 存储当前正在记录的 Trip，用于 RECORDING_MEMORY 模式
    private var currentRecordingTrip: TripEntity? = null

    fun onAppStart(hasActiveTrip: Boolean) {
        _uiState.update {
            if (hasActiveTrip) {
                it.copy(
                    mode = MapMode.RECORDING,
                    cameraMode = CameraMode.FOLLOWING,
                    pendingCameraAction = CameraAction.FollowUserOnce(17f),
                    showAllTrips = false,
                    zoomLevel = 17f
                )
            } else {
                it.copy(
                    mode = MapMode.EXPLORE,
                    cameraMode = CameraMode.FREE,
                    pendingCameraAction = null,
                    showAllTrips = true,
                    zoomLevel = 15f
                )
            }
        }

        if (!hasActiveTrip) {
            loadAllTripsTrackPoints()
        }
    }

    fun bindCurrentTrip(tripId: Long) {
        // 获取并存储 Trip 实体，供 RECORDING_MEMORY 模式使用
        viewModelScope.launch {
            val trip = tripRepository.getTripById(tripId)
            currentRecordingTrip = trip
        }

        trackPointRepository.setCurrentTripId(tripId)
        currentTripPointsJob?.cancel()
        currentTripPointsJob = viewModelScope.launch {
            trackPointRepository.observeCurrentTrackPoints().collect { points ->
                _uiState.update { it.copy(currentTrackPoints = points) }
            }
        }
    }

    fun clearCurrentTripBinding() {
        currentTripPointsJob?.cancel()
        currentTripPointsJob = null
        focusedTripDataJob?.cancel()
        focusedTripDataJob = null
        trackPointRepository.clearCurrentTripId()
        _uiState.update {
            it.copy(
                currentTrackPoints = emptyList(),
                focusedTrip = null,
                focusedTripTrackPoints = emptyList(),
                focusedTripMemoryNodes = emptyList(),
            )
        }
    }

    fun onTripSelected(trip: TripEntity, isRecording: Boolean) {
        focusedTripDataJob?.cancel()
        focusedTripDataJob = viewModelScope.launch {
            launch {
                trackPointRepository.observeTrackPointsByTripId(trip.tripId).collect { points ->
                    _uiState.update { it.copy(focusedTripTrackPoints = points) }
                }
            }
            launch {
                memoryNodeRepository.observeMemoryNodesForTrip(trip.tripId).collect { nodes ->
                    _uiState.update { it.copy(focusedTripMemoryNodes = nodes) }
                }
            }
        }

        _uiState.update {
            it.copy(
                mode = if (isRecording) MapMode.RECORDING_MEMORY else MapMode.MEMORY,
                focusedTrip = trip,
                cameraMode = CameraMode.FREE,  // 聚焦后允许自由浏览
                pendingCameraAction = CameraAction.MoveToTrip(trip.tripId),  // 一次性聚焦动作
                showAllTrips = true,
                showMemoryNodes = true,
            )
        }
    }

    fun returnToRecording() {
        focusedTripDataJob?.cancel()
        focusedTripDataJob = null
        _uiState.update {
            it.copy(
                mode = MapMode.RECORDING,
                cameraMode = CameraMode.FOLLOWING,  // 恢复跟随
                pendingCameraAction = CameraAction.FollowUserOnce(17f),  // 首次定位
                focusedTrip = null,
                showAllTrips = false,
                showMemoryNodes = true,
                focusedTripTrackPoints = emptyList(),
                focusedTripMemoryNodes = emptyList(),
            )
        }
    }

    fun enterPausedRecording() {
        focusedTripDataJob?.cancel()
        focusedTripDataJob = null
        _uiState.update {
            it.copy(
                mode = MapMode.RECORDING_MEMORY,
                cameraMode = CameraMode.FREE,  // 暂停时不跟随
                pendingCameraAction = null,
                focusedTrip = null,
                showAllTrips = false,
                showMemoryNodes = true,
                focusedTripTrackPoints = emptyList(),
                focusedTripMemoryNodes = emptyList(),
            )
        }
    }

    fun enterExplore() {
        focusedTripDataJob?.cancel()
        focusedTripDataJob = null
        _uiState.update {
            it.copy(
                mode = MapMode.EXPLORE,
                cameraMode = CameraMode.FREE,
                pendingCameraAction = null,  // EXPLORE 的镜头适配由 LifeTraceMap 处理
                focusedTrip = null,
                showAllTrips = true,
                showMemoryNodes = false,
                focusedTripTrackPoints = emptyList(),
                focusedTripMemoryNodes = emptyList(),
            )
        }
        loadAllTripsTrackPoints()
    }

    fun onMapCameraChanged(fromUserGesture: Boolean, zoomLevel: Float) {
        _uiState.update { it.copy(zoomLevel = zoomLevel) }

        if (!fromUserGesture) return

        // 用户手势后，立即停止跟随
        _uiState.update { state ->
            when (state.mode) {
                MapMode.RECORDING -> {
                    // 切换到 RECORDING_MEMORY 模式，停止跟随
                    state.copy(
                        mode = MapMode.RECORDING_MEMORY,
                        cameraMode = CameraMode.FREE,  // 关键：停止跟随
                        focusedTrip = currentRecordingTrip
                    )
                }
                else -> state  // 其他模式保持不变
            }
        }

        // 进入 RECORDING_MEMORY 模式时，加载当前 trip 的回忆节点
        if (_uiState.value.mode == MapMode.RECORDING_MEMORY && currentRecordingTrip != null) {
            loadFocusedTripMemoryNodes(currentRecordingTrip!!)
        }
    }

    /**
     * 用户点击"回到我"按钮
     */
    fun onReturnToMeClicked() {
        _uiState.update {
            it.copy(
                cameraMode = CameraMode.FOLLOWING,
                pendingCameraAction = CameraAction.FollowUserOnce(17f)
            )
        }
    }

    /**
     * 镜头动作执行后清除
     */
    fun onCameraActionExecuted() {
        _uiState.update { it.copy(pendingCameraAction = null) }
    }

    private fun loadFocusedTripMemoryNodes(trip: TripEntity) {
        focusedTripDataJob?.cancel()
        focusedTripDataJob = viewModelScope.launch {
            memoryNodeRepository.observeMemoryNodesForTrip(trip.tripId).collect { nodes ->
                _uiState.update { it.copy(focusedTripMemoryNodes = nodes) }
            }
        }
    }

    // 点击回忆节点
    fun onMemoryNodeClicked(node: MemoryNodeEntity) {
        _uiState.update { it.copy(selectedMemoryNode = node) }
    }

    // 关闭回忆详情
    fun dismissMemoryNodeDetail() {
        _uiState.update { it.copy(selectedMemoryNode = null) }
    }

    fun onExploreTripClicked(tripId: Long) {
        val state = _uiState.value
        // 支持 EXPLORE 和 MEMORY 模式下切换旅程
        if (state.mode != MapMode.EXPLORE && state.mode != MapMode.MEMORY) return

        val trip = state.allTrips.firstOrNull { it.tripId == tripId } ?: return
        onTripSelected(trip, isRecording = false)
    }

    fun deleteFocusedTrip() {
        val focusedTripId = _uiState.value.focusedTrip?.tripId ?: return
        viewModelScope.launch {
            memoryNodeRepository.deleteMemoryNodesByTripId(focusedTripId)
            trackPointRepository.deleteTrackPointsByTripId(focusedTripId)
            tripRepository.deleteTripById(focusedTripId)
            enterExplore()
        }
    }

    private fun loadAllTripsTrackPoints() {
        viewModelScope.launch {
            val allTrips = tripRepository.getAllTrips()
            val tripWithTracks = allTrips.map { trip ->
                trip to trackPointRepository.getTrackPointsByTripId(trip.tripId)
            }.filter { (_, points) -> points.isNotEmpty() }

            _uiState.update {
                it.copy(
                    allTrips = tripWithTracks.map { (trip, _) -> trip },
                    allTripsTrackPoints = tripWithTracks.map { (_, points) -> points },
                )
            }
        }
    }
}
