package com.example.lifetrace.data.database.repository

import android.content.Context
import com.example.lifetrace.data.database.AppDatabase
import com.example.lifetrace.data.database.dao.MemoryNodeDao
import com.example.lifetrace.data.database.entity.MemoryNodeEntity
import com.example.lifetrace.media.MediaStorageManager
import kotlinx.coroutines.flow.Flow

class MemoryNodeRepository(
    private val memoryNodeDao: MemoryNodeDao,
    private val mediaStorageManager: MediaStorageManager,
    private val attachmentRepository: MemoryAttachmentRepository,
) {
    suspend fun insertMemoryNode(node: MemoryNodeEntity): Long {
        return memoryNodeDao.insertMemoryNode(node)
    }

    suspend fun getMemoryNodeById(nodeId: Long): MemoryNodeEntity? {
        return memoryNodeDao.getMemoryNodeById(nodeId)
    }

    fun observeMemoryNodesForTrip(tripId: Long): Flow<List<MemoryNodeEntity>> {
        return memoryNodeDao.observeAllMemoryNodesForTrip(tripId)
    }

    suspend fun deleteMemoryNodesByTripId(tripId: Long) {
        // 先删除所有节点和附件的文件
        memoryNodeDao.getAllMemoryNodesForTrip(tripId).forEach { node ->
            attachmentRepository.deleteAttachmentsForNode(node.id)
            mediaStorageManager.deleteFile(node.coverUri)
        }
        // 再删除记录
        memoryNodeDao.deleteMemoryNodesByTripId(tripId)
    }

    suspend fun deleteMemoryNodeWithFile(nodeId: Long) {
        // 先删除所有附件
        attachmentRepository.deleteAttachmentsForNode(nodeId)
        // 删除封面
        val node = memoryNodeDao.getMemoryNodeById(nodeId)
        if (node != null) {
            mediaStorageManager.deleteFile(node.coverUri)
        }
        // 再删除节点记录
        memoryNodeDao.deleteMemoryNodeById(nodeId)
    }

    suspend fun updateMemoryNode(id: Long, text: String?, coverUri: String?) {
        val now = System.currentTimeMillis()
        memoryNodeDao.updateMemoryNode(id, text, coverUri, now)
    }

    suspend fun updateCoverUri(id: Long, coverUri: String?) {
        memoryNodeDao.updateCoverUri(id, coverUri)
    }

    companion object {
        @Volatile
        private var INSTANCE: MemoryNodeRepository? = null

        fun getInstance(context: Context): MemoryNodeRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getInstance(context)
                val storageManager = MediaStorageManager.getInstance(context)
                val attachmentRepo = MemoryAttachmentRepository.getInstance(context)
                MemoryNodeRepository(db.memoryNodeDao(), storageManager, attachmentRepo).also { INSTANCE = it }
            }
        }
    }
}
