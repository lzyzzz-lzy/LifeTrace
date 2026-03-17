package com.example.lifetrace.state

import com.example.lifetrace.data.database.entity.MemoryNodeEntity
import com.example.lifetrace.data.database.entity.TrackPointEntity
import com.example.lifetrace.data.database.entity.TripEntity

/**
 * 镜头控制模式
 */
enum class CameraMode {
    FREE,           // 用户自由浏览，不跟随
    FOLLOWING,      // 持续跟随用户位置
}

/**
 * 一次性镜头动作（用于首次进入、聚焦 trip 等场景）
 */
sealed class CameraAction {
    data class FitToBounds(val points: List<TrackPointEntity>) : CameraAction()
    data class FollowUserOnce(val zoom: Float = 17f) : CameraAction()
    data class MoveToTrip(val tripId: Long) : CameraAction()
}

data class MapUiState(
    val mode: MapMode = MapMode.EXPLORE,
    val focusedTrip: TripEntity? = null,
    // 镜头控制：用 cameraMode 替代 followUser
    val cameraMode: CameraMode = CameraMode.FREE,
    val pendingCameraAction: CameraAction? = null,  // 待执行的一次性镜头动作
    val zoomLevel: Float = 15f,
    val showAllTrips: Boolean = true,
    val showMemoryNodes: Boolean = false,
    val currentTrackPoints: List<TrackPointEntity> = emptyList(),
    val focusedTripTrackPoints: List<TrackPointEntity> = emptyList(),
    val focusedTripMemoryNodes: List<MemoryNodeEntity> = emptyList(),
    val selectedMemoryNode: MemoryNodeEntity? = null,
    val allTrips: List<TripEntity> = emptyList(),
    val allTripsTrackPoints: List<List<TrackPointEntity>> = emptyList(),
)

enum class MapMode {
    EXPLORE,
    MEMORY,
    RECORDING,
    RECORDING_MEMORY,
}