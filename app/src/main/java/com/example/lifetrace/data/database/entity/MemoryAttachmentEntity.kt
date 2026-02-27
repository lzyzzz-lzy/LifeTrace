package com.example.lifetrace.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "memory_attachment",
    indices = [Index("memoryNodeId")],
    foreignKeys = [
        ForeignKey(
            entity = MemoryNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["memoryNodeId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MemoryAttachmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val memoryNodeId: Long,

    val type: AttachmentType,

    val uri: String,               // 媒体文件 URI

    val duration: Long = 0L,        // 音频/视频时长（毫秒），照片时为 0

    val orderIndex: Int = 0,          // 排序索引

    val thumbnailUri: String? = null,   // 缩略图 URI（可选，用于优化列表加载）
)
