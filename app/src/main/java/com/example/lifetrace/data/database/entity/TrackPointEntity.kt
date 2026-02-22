package com.example.lifetrace.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

//旅程坐标点
@Entity(
    tableName = "track_point",
    indices = [Index("tripId")]
)
data class TrackPointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val tripId: Long,

    val latitude: Double,

    val longitude: Double,

    val timestamp: Long,

    //val accuracy: Float
)