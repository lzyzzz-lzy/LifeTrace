package com.example.lifetrace.data.database.repository

import android.content.Context
import com.example.lifetrace.data.database.AppDatabase
import com.example.lifetrace.data.database.dao.TrackPointDao
import com.example.lifetrace.data.database.dao.TripDao
import com.example.lifetrace.data.database.entity.TrackPointEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

class TrackPointRepository private constructor(
    private val trackPointDao: TrackPointDao,
    private val tripDao: TripDao,
) {
    private val currentTripIdFlow = MutableStateFlow<Long?>(null)

    suspend fun insertTrackPoint(trackPointEntity: TrackPointEntity) {
        trackPointDao.insertTrackPoint(trackPointEntity)
    }

    fun setCurrentTripId(tripId: Long) {
        currentTripIdFlow.value = tripId
    }

    fun getCurrentTripId(): Long? = currentTripIdFlow.value

    fun clearCurrentTripId() {
        currentTripIdFlow.value = null
    }

    suspend fun resolveCurrentTripIdFromDb(): Long? {
        val tripId = tripDao.getCurrentTripId()
        if (tripId != null) {
            currentTripIdFlow.value = tripId
        }
        return tripId
    }

    suspend fun getCurrentTrackPoints(): List<TrackPointEntity> {
        val tripId = currentTripIdFlow.value ?: return emptyList()
        return trackPointDao.getTrackPointsByTripId(tripId)
    }

    suspend fun getTrackPointsByTripId(tripId: Long): List<TrackPointEntity> {
        return trackPointDao.getTrackPointsByTripId(tripId)
    }

    fun observeCurrentTrackPoints(): Flow<List<TrackPointEntity>> {
        return currentTripIdFlow.flatMapLatest { tripId ->
            if (tripId == null) flowOf(emptyList()) else trackPointDao.observeTrackPointsByTripId(tripId)
        }
    }

    fun observeTrackPointsByTripId(tripId: Long): Flow<List<TrackPointEntity>> {
        return trackPointDao.observeTrackPointsByTripId(tripId)
    }

    suspend fun deleteTrackPointsByTripId(tripId: Long) {
        trackPointDao.deleteTrackPointsByTripId(tripId)
    }

    companion object {
        @Volatile
        private var INSTANCE: TrackPointRepository? = null

        fun getInstance(context: Context): TrackPointRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getInstance(context)
                TrackPointRepository(
                    trackPointDao = db.trackPointDao(),
                    tripDao = db.tripDao(),
                ).also { INSTANCE = it }
            }
        }
    }
}