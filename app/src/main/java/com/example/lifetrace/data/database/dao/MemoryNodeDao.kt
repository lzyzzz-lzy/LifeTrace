package com.example.lifetrace.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.lifetrace.data.database.entity.MemoryNodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryNodeDao {
        @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMemoryNode(memoryNode: MemoryNodeEntity): Long

    @Query("SELECT * FROM memory_node WHERE tripId = :tripId ORDER BY updatedAt DESC")
    suspend fun getAllMemoryNodesForTrip(tripId: Long): List<MemoryNodeEntity>

    @Query("SELECT * FROM memory_node WHERE tripId = :tripId ORDER BY updatedAt DESC")
    fun observeAllMemoryNodesForTrip(tripId: Long): Flow<List<MemoryNodeEntity>>

    @Query("SELECT * FROM memory_node WHERE id = :nodeId")
    suspend fun getMemoryNodeById(nodeId: Long): MemoryNodeEntity?

    @Query("DELETE FROM memory_node WHERE tripId = :tripId")
    suspend fun deleteMemoryNodesByTripId(tripId: Long)

    @Query("DELETE FROM memory_node WHERE id = :nodeId")
    suspend fun deleteMemoryNodeById(nodeId: Long)

    @Query("UPDATE memory_node SET text = :text, coverUri = :coverUri, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateMemoryNode(id: Long, text: String?, coverUri: String?, updatedAt: Long)

    @Query("UPDATE memory_node SET coverUri = :coverUri WHERE id = :id")
    suspend fun updateCoverUri(id: Long, coverUri: String?)
}
