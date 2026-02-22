package com.example.lifetrace.data.database.repository

import android.content.Context
import com.example.lifetrace.data.database.AppDatabase
import com.example.lifetrace.data.database.dao.TripDao
import com.example.lifetrace.data.database.entity.TripEntity
import com.example.lifetrace.data.database.entity.TripStatus

class TripRepository(
    private val tripDao: TripDao
) {
    //开始新trip
    suspend fun startNewTrip(title: String): TripEntity {
        val now = System.currentTimeMillis()
        val trip = TripEntity(
            title = title,
            startTime = now,
            lastResumeTime = now,
            accumulatedDuration = 0L,
            endTime = null,
            status = TripStatus.RECORDING
        )
        val id = tripDao.insertTrip(trip)
        return trip.copy(tripId = id) //copy返回，保证原对象不可变
    }

    //得到当前活跃trip
    suspend fun getCurrentTrip(): TripEntity? {
        return tripDao.getActiveTrip()
    }

    //更新trip
    suspend fun updateTrip(trip: TripEntity) {
        return tripDao.updateTrip(trip)
    }

    companion object {
        @Volatile private var INSTANCE: TripRepository? = null

        fun getInstance(context: Context): TripRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getInstance(context)
                TripRepository(db.tripDao()).also { INSTANCE = it }
            }
        }
    }

}