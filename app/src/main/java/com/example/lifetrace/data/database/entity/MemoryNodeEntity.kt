package com.example.lifetrace.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memory_node",
    indices = [Index(value = ["tripId"])]
)
data class MemoryNodeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val tripId: Long,

    val latitude: Double,
    val longitude: Double,

    // 文字描述（可选）
    val text: String? = null,

    // 封面图 URI（可选，用于地图 marker 预览）
    val coverUri: String? = null,

    // 创建时间（用于展示/排序）
    val timestamp: Long = System.currentTimeMillis(),

    // 最后更新时间（用于排序）
    val updatedAt: Long = timestamp
)