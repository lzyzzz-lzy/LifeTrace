package com.example.lifetrace.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.lifetrace.data.database.entity.TrackPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackPointDao {
    @Insert
    suspend fun insertTrackPoint(trackPointEntity: TrackPointEntity)

    @Query("SELECT * FROM track_point WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun getTrackPointsByTripId(tripId: Long): List<TrackPointEntity>

    @Query("SELECT * FROM track_point WHERE tripId = :tripId ORDER BY timestamp ASC")
    fun observeTrackPointsByTripId(tripId: Long): Flow<List<TrackPointEntity>>

    @Query("DELETE FROM track_point WHERE tripId = :tripId")
    suspend fun deleteTrackPointsByTripId(tripId: Long)
}