package com.example.lifetrace.data.database.repository

import android.content.Context
import com.example.lifetrace.data.database.AppDatabase
import com.example.lifetrace.data.database.dao.TrackPointDao
import com.example.lifetrace.data.database.entity.TrackPointEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay

class TrackPointRepository private constructor(
    private val trackPointDao: TrackPointDao
) {
    // 缓存当前活跃的TripId（录制态用）
    private var currentTripId: Long? = null

    // 1. 插入轨迹点（服务调用）
    suspend fun insertTrackPoint(trackPointEntity: TrackPointEntity) {
        trackPointDao.insertTrackPoint(trackPointEntity)
    }

    // 2. 设置当前活跃TripId（开始录制时调用）
    fun setCurrentTripId(tripId: Long) {
        currentTripId = tripId
    }

    // 新增：公共读取方法（供外部获取当前TripId）
    fun getCurrentTripId(): Long? {
        return currentTripId
    }

    // 3. 清空当前活跃TripId（结束录制时调用）
    fun clearCurrentTripId() {
        currentTripId = null
    }

    // 4. 获取当前录制的轨迹点（UI渲染用）
    suspend fun getCurrentTrackPoints(): List<TrackPointEntity> {
        currentTripId ?: return emptyList()
        return trackPointDao.getTrackPointsByTripId(currentTripId!!)
    }

    // 5. 获取指定Trip的轨迹点（回忆态用）
    suspend fun getTrackPointsByTripId(tripId: Long): List<TrackPointEntity> {
        return trackPointDao.getTrackPointsByTripId(tripId)
    }

    // 6. 实时监听当前轨迹点变化（供UI层实时渲染）
    fun observeCurrentTrackPoints(): Flow<List<TrackPointEntity>> {
        return flow {
            while (true) {
                emit(getCurrentTrackPoints())
                delay(1000) // 1秒刷新一次，匹配定位频率
            }
        }
    }

    // 7. 删除指定Trip的轨迹点（可选：删除旅程时调用）
    suspend fun deleteTrackPointsByTripId(tripId: Long) {
        trackPointDao.deleteTrackPointsByTripId(tripId)
    }

    companion object {
        @Volatile private var INSTANCE: TrackPointRepository? = null

        // 修正：正确获取TrackPointDao
        fun getInstance(context: Context): TrackPointRepository {
            return INSTANCE ?: synchronized(this) {
                val db = AppDatabase.getInstance(context)
                val trackPointDao = db.trackPointDao()
                TrackPointRepository(trackPointDao).also { INSTANCE = it }
            }
        }
    }
}