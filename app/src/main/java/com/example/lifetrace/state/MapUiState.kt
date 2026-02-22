package com.example.lifetrace.state

import com.example.lifetrace.data.database.entity.TripEntity

data class MapUiState(
    val mode: MapMode = MapMode.EXPLORE,

    val focusedTrip: TripEntity? = null,  // 当前查看的旅程
    val followUser: Boolean = false,      // 是否跟随定位
    val zoomLevel: Float = 15f,

    val showAllTrips: Boolean = true,
    val showMemoryNodes: Boolean = false
)

enum class MapMode {
    EXPLORE,           // 浏览态
    MEMORY,            // 回忆态
    RECORDING,         // 记录态
    RECORDING_MEMORY   // 记录中浏览历史
}