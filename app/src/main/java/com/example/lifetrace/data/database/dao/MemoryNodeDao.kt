package com.example.lifetrace.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.lifetrace.data.database.entity.MemoryNodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryNodeDao {
    @Insert
    suspend fun insertMemoryNode(memoryNode: MemoryNodeEntity)

    @Query("SELECT * FROM memory_node WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun getAllMemoryNodesForTrip(tripId: Long): List<MemoryNodeEntity>

    @Query("SELECT * FROM memory_node WHERE tripId = :tripId ORDER BY timestamp ASC")
    fun observeAllMemoryNodesForTrip(tripId: Long): Flow<List<MemoryNodeEntity>>

    @Query("DELETE FROM memory_node WHERE tripId = :tripId")
    suspend fun deleteMemoryNodesByTripId(tripId: Long)
}
