package com.example.lifetrace.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memory_node",
    indices = [Index("tripId")]
)
data class MemoryNodeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val tripId: Long,

    val type: MemoryType,

    val latitude: Double,

    val longitude: Double,

    val contentUrl: String?,

    val text: String?,

    val timestamp: Long
)

enum class MemoryType {
    PHOTO,
    AUDIO,
    TEXT
}