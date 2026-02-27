package com.example.lifetrace.data.database.repository

import android.content.Context
import com.example.lifetrace.data.database.AppDatabase
import com.example.lifetrace.data.database.dao.MemoryAttachmentDao
import com.example.lifetrace.data.database.entity.MemoryAttachmentEntity
import com.example.lifetrace.media.MediaStorageManager
import kotlinx.coroutines.flow.Flow

class MemoryAttachmentRepository(
    private val attachmentDao: MemoryAttachmentDao,
    private val mediaStorageManager: MediaStorageManager,
) {
    suspend fun insertAttachment(attachment: MemoryAttachmentEntity): Long {
        return attachmentDao.insertAttachment(attachment)
    }

    suspend fun insertAttachments(attachments: List<MemoryAttachmentEntity>) {
        attachmentDao.insertAttachments(attachments)
    }

    fun observeAttachmentsForNode(memoryNodeId: Long): Flow<List<MemoryAttachmentEntity>> {
        return attachmentDao.observeAttachmentsForNode(memoryNodeId)
    }

    suspend fun getAttachmentsForNode(memoryNodeId: Long): List<MemoryAttachmentEntity> {
        return attachmentDao.getAttachmentsForNode(memoryNodeId)
    }

    suspend fun deleteAttachmentWithFile(id: Long, memoryNodeId: Long) {
        val attachments = attachmentDao.getAttachmentsForNode(memoryNodeId)
        val attachment = attachments.firstOrNull { it.id == id }
        if (attachment != null) {
            mediaStorageManager.deleteFile(attachment.uri)
            mediaStorageManager.deleteFile(attachment.thumbnailUri)
        }
        attachmentDao.deleteAttachment(id)
    }

    suspend fun deleteAttachmentsForNode(memoryNodeId: Long) {
        val attachments = attachmentDao.getAttachmentsForNode(memoryNodeId)
        attachments.forEach { attachment ->
            mediaStorageManager.deleteFile(attachment.uri)
            mediaStorageManager.deleteFile(attachment.thumbnailUri)
        }
        attachmentDao.deleteAttachmentsForNode(memoryNodeId)
    }

    suspend fun reorderAttachment(id: Long, newOrderIndex: Int) {
        attachmentDao.updateOrderIndex(id, newOrderIndex)
    }

    companion object {
        @Volatile
        private var INSTANCE: MemoryAttachmentRepository? = null

        fun getInstance(context: Context): MemoryAttachmentRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getInstance(context)
                val storageManager = MediaStorageManager.getInstance(context)
                MemoryAttachmentRepository(db.memoryAttachmentDao(), storageManager).also { INSTANCE = it }
            }
        }
    }
}
