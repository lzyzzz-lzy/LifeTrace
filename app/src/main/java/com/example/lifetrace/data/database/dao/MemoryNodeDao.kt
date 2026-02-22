package com.example.lifetrace.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.lifetrace.data.database.entity.MemoryNodeEntity

@Dao
interface MemoryNodeDao {
    //插入新回忆点
    @Insert
    suspend fun insertMemoryNode(memoryNode: MemoryNodeEntity)

    //获取某trip所有回忆点
    @Query("SELECT * FROM memory_node WHERE tripId = :tripId ORDER BY timestamp ASC")
    suspend fun getAllMemoryNodesForTrip(tripId: Long): List<MemoryNodeEntity>
}