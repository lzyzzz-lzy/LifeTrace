package com.example.lifetrace.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

//一次旅程
@Entity(tableName = "trip")
data class TripEntity(
    @PrimaryKey(true)
    val tripId: Long = 0,

    val title: String,

    val startTime: Long,

    val endTime: Long?,

    val lastResumeTime: Long?,        // 最近一次恢复时间

    val accumulatedDuration: Long,

    val status: TripStatus

)

enum class TripStatus {
    RECORDING,
    PAUSE,
    FINISHED
}