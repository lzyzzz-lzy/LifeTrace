package com.example.lifetrace.state

import com.example.lifetrace.data.database.entity.MemoryNodeEntity
import com.example.lifetrace.data.database.entity.TrackPointEntity
import com.example.lifetrace.data.database.entity.TripEntity

data class MapUiState(
    val mode: MapMode = MapMode.EXPLORE,
    val focusedTrip: TripEntity? = null,
    val followUser: Boolean = false,
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