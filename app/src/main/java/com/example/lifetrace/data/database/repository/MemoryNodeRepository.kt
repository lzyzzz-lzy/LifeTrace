package com.example.lifetrace.data.database.repository

import android.content.Context
import com.example.lifetrace.data.database.AppDatabase
import com.example.lifetrace.data.database.dao.MemoryNodeDao
import com.example.lifetrace.data.database.entity.MemoryNodeEntity
import kotlinx.coroutines.flow.Flow

class MemoryNodeRepository(
    private val memoryNodeDao: MemoryNodeDao,
) {
    suspend fun insertMemoryNode(node: MemoryNodeEntity) {
        memoryNodeDao.insertMemoryNode(node)
    }

    fun observeMemoryNodesForTrip(tripId: Long): Flow<List<MemoryNodeEntity>> {
        return memoryNodeDao.observeAllMemoryNodesForTrip(tripId)
    }

    suspend fun deleteMemoryNodesByTripId(tripId: Long) {
        memoryNodeDao.deleteMemoryNodesByTripId(tripId)
    }

    companion object {
        @Volatile
        private var INSTANCE: MemoryNodeRepository? = null

        fun getInstance(context: Context): MemoryNodeRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getInstance(context)
                MemoryNodeRepository(db.memoryNodeDao()).also { INSTANCE = it }
            }
        }
    }
}