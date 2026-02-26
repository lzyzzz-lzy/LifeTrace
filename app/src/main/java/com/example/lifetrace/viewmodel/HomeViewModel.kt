package com.example.lifetrace.viewmodel

import android.content.Context
import android.content.Intent
import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lifetrace.data.database.entity.MemoryNodeEntity
import com.example.lifetrace.data.database.entity.MemoryType
import com.example.lifetrace.data.database.entity.TripStatus
import com.example.lifetrace.data.database.repository.MemoryNodeRepository
import com.example.lifetrace.data.database.repository.TrackPointRepository
import com.example.lifetrace.data.database.repository.TripRepository
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
    private val mapViewModel: MapViewModel,
    private val trackPointRepository: TrackPointRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    private var timerJob: Job? = null
    private var distanceJob: Job? = null

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

    fun addMemoryNode(latitude: Double, longitude: Double) {
        val trip = _uiState.value.activeTrip ?: return

        viewModelScope.launch {
            val node = MemoryNodeEntity(
                tripId = trip.tripId,
                latitude = latitude,
                longitude = longitude,
                timestamp = System.currentTimeMillis(),
                text = "",
                contentUrl = "",
                type = MemoryType.TEXT
            )
            memoryNodeRepository.insertMemoryNode(node)
        }
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
