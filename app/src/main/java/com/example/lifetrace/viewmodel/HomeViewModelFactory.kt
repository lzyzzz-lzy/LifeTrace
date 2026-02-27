package com.example.lifetrace.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.lifetrace.data.database.repository.MemoryAttachmentRepository
import com.example.lifetrace.data.database.repository.MemoryNodeRepository
import com.example.lifetrace.data.database.repository.TrackPointRepository
import com.example.lifetrace.data.database.repository.TripRepository

class HomeViewModelFactory(
    private val tripRepository: TripRepository,
    private val memoryNodeRepository: MemoryNodeRepository,
    private val mapViewModel: MapViewModel,
    private val trackPointRepository: TrackPointRepository,
    private val attachmentRepository: MemoryAttachmentRepository,
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            return HomeViewModel(
                tripRepository = tripRepository,
                memoryNodeRepository = memoryNodeRepository,
                mapViewModel = mapViewModel,
                trackPointRepository = trackPointRepository,
                attachmentRepository = attachmentRepository,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}