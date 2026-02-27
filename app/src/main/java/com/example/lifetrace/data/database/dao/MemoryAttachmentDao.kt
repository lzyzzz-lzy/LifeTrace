package com.example.lifetrace.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.lifetrace.data.database.entity.MemoryAttachmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryAttachmentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachment(attachment: MemoryAttachmentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttachments(attachments: List<MemoryAttachmentEntity>)

    @Query("SELECT * FROM memory_attachment WHERE memoryNodeId = :memoryNodeId ORDER BY orderIndex ASC")
    fun observeAttachmentsForNode(memoryNodeId: Long): Flow<List<MemoryAttachmentEntity>>

    @Query("SELECT * FROM memory_attachment WHERE memoryNodeId = :memoryNodeId ORDER BY orderIndex ASC")
    suspend fun getAttachmentsForNode(memoryNodeId: Long): List<MemoryAttachmentEntity>

    @Query("DELETE FROM memory_attachment WHERE id = :id")
    suspend fun deleteAttachment(id: Long)

    @Query("DELETE FROM memory_attachment WHERE memoryNodeId = :memoryNodeId")
    suspend fun deleteAttachmentsForNode(memoryNodeId: Long)

    @Query("UPDATE memory_attachment SET orderIndex = :orderIndex WHERE id = :id")
    suspend fun updateOrderIndex(id: Long, orderIndex: Int)
}
