package com.example.lifetrace.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.lifetrace.data.database.repository.MemoryNodeRepository
import com.example.lifetrace.data.database.repository.TrackPointRepository
import com.example.lifetrace.data.database.repository.TripRepository

class MapViewModelFactory(
    private val trackPointRepository: TrackPointRepository,
    private val memoryNodeRepository: MemoryNodeRepository,
    private val tripRepository: TripRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MapViewModel::class.java)) {
            return MapViewModel(
                trackPointRepository = trackPointRepository,
                memoryNodeRepository = memoryNodeRepository,
                tripRepository = tripRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}