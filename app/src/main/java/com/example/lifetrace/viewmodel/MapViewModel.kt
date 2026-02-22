package com.example.lifetrace.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.test.core.app.ApplicationProvider
import com.example.lifetrace.data.database.entity.TripEntity
import com.example.lifetrace.data.database.repository.TrackPointRepository
import com.example.lifetrace.state.MapMode
import com.example.lifetrace.state.MapUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class MapViewModel(
    private val context: Context = ApplicationProvider.getApplicationContext(), // 全局Context
    private val trackPointRepository: TrackPointRepository = TrackPointRepository.getInstance(ApplicationProvider.getApplicationContext())
) : ViewModel() {
    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState

    fun getContext(): Context {
        return context
    }
    //App启动
    fun onAppStart(hasActiveTrip: Boolean) {
        _uiState.update {
            if(hasActiveTrip) {
                it.copy(
                    mode = MapMode.RECORDING,
                    followUser = true,
                    showAllTrips = false,
                    zoomLevel = 17f
                )
            } else {
                it.copy(
                    mode = MapMode.EXPLORE,
                    followUser = false,
                    showAllTrips = true,
                    zoomLevel = 15f
                )
            }
        }
    }

    //点击某条轨迹
    fun onTripSelected(trip: TripEntity, isRecording: Boolean) {
        _uiState.update {
            it.copy(
                mode = if(isRecording) MapMode.RECORDING_MEMORY else MapMode.MEMORY,
                focusedTrip = trip,
                followUser = false,
                showAllTrips = true,
                showMemoryNodes = true
            )
        }
    }

    //返回记录
    fun returnToRecording() {
        _uiState.update {
            it.copy(
                mode = MapMode.RECORDING,
                followUser = true,
                focusedTrip = null,
                showAllTrips = false,
                showMemoryNodes = true
            )
        }
    }

    //进入浏览态
    fun enterExplore() {
        _uiState.update {
            it.copy(
                mode = MapMode.EXPLORE,
                followUser = false,
                focusedTrip = null,
                showAllTrips = true,
                showMemoryNodes = false
            )
        }
    }

    //地图发生拖动（处于记录中），进入临时回忆态
    fun onMapMovedWhileRecording() {
        _uiState.update {
            if (it.mode == MapMode.RECORDING) {
                it.copy(
                    mode = MapMode.RECORDING_MEMORY,
                    followUser = false
                )
            } else it
        }
    }
}