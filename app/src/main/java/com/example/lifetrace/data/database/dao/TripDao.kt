package com.example.lifetrace.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.lifetrace.data.database.entity.TripEntity
import com.example.lifetrace.data.database.entity.TripStatus

//DAO层用于直接接触数据库
@Dao
interface TripDao {
    //新建trip
    @Insert
    suspend fun insertTrip(trip: TripEntity): Long

    //获取活跃trip，允许空
    @Query("SELECT * FROM trip WHERE status != 'FINISHED' LIMIT 1")
    suspend fun getActiveTrip(): TripEntity?

    //更新trip
    @Update
    suspend fun updateTrip(trip: TripEntity)

    //获取所有trip
    @Query("SELECT * FROM trip ORDER BY startTime DESC")
    suspend fun getAllTrips(): List<TripEntity>?

    @Query("""
    SELECT tripId FROM trip
    WHERE status = 'RECORDING' OR status = 'PAUSE'
    LIMIT 1
    """)
    suspend fun getCurrentTripId(): Long?

    @Query("DELETE FROM trip WHERE tripId = :tripId")
    suspend fun deleteTripById(tripId: Long)
}