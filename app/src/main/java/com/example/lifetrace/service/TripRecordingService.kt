package com.example.lifetrace.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.example.lifetrace.R
import com.example.lifetrace.data.database.entity.TrackPointEntity
import com.example.lifetrace.data.database.repository.TrackPointRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG = "TripRecordingService"
private const val NOTIFICATION_ID = 1
private const val CHANNEL_ID = "trip_record_channel"
private const val MIN_ACCURACY_METERS = 30f
private const val MIN_DISTANCE_METERS = 4f
private const val MAX_WRITE_INTERVAL_MS = 10_000L

class TripRecordingService : Service(), AMapLocationListener {

    private var locationClient: AMapLocationClient? = null
    private var trackPointRepository: TrackPointRepository? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    private var lastAcceptedLat: Double? = null
    private var lastAcceptedLng: Double? = null
    private var lastAcceptedTimestamp: Long? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "服务创建：初始化Repository和定位")

        trackPointRepository = TrackPointRepository.getInstance(this)
        initLocation()
        startForegroundService()
    }

    private fun initLocation() {
        if (locationClient != null) return

        try {
            locationClient = AMapLocationClient(applicationContext).apply {
                val option = AMapLocationClientOption().apply {
                    locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                    interval = 2000
                    isNeedAddress = false
                    isMockEnable = false
                    isSensorEnable = true
                    isLocationCacheEnable = true
                }
                setLocationOption(option)
                setLocationListener(this@TripRecordingService)
            }
            Log.d(TAG, "定位客户端初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "定位客户端初始化失败：${e.message}", e)
        }
    }

    private fun startForegroundService() {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "轨迹记录",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
            Log.d(TAG, "通知渠道创建成功")
        }

        val notificationIntent = packageManager.getLaunchIntentForPackage(packageName)
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LifeTrace 正在记录轨迹")
            .setContentText("点击返回应用")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(notificationIntent?.let {
                PendingIntent.getActivity(
                    this,
                    0,
                    it,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            })
            .build()

        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "前台服务启动成功")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "服务启动：开始定位")
        locationClient?.startLocation() ?: run {
            initLocation()
            locationClient?.startLocation()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "服务销毁：停止定位并释放资源")
        locationClient?.apply {
            stopLocation()
            onDestroy()
        }
        locationClient = null
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "App被关闭：停止轨迹录制服务")
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onLocationChanged(location: AMapLocation?) {
        if (location == null) {
            Log.w(TAG, "定位结果为空")
            return
        }

        if (location.errorCode != 0) {
            Log.e(TAG, "定位失败：错误码=${location.errorCode}，错误信息=${location.errorInfo}")
            return
        }

        if (location.accuracy > MIN_ACCURACY_METERS) {
            Log.d(TAG, "过滤轨迹点：精度${location.accuracy}m 超过阈值")
            return
        }

        val lat = location.latitude
        val lng = location.longitude
        val pointTimestamp = location.time.takeIf { it > 0 } ?: System.currentTimeMillis()

        scope.launch {
            val repository = trackPointRepository ?: return@launch
            writeMutex.withLock {
                if (!shouldAcceptPoint(lat, lng, pointTimestamp)) {
                    return@withLock
                }

                val tripId = repository.getCurrentTripId() ?: repository.resolveCurrentTripIdFromDb()
                if (tripId == null) {
                    Log.w(TAG, "当前无活跃Trip，跳过轨迹点插入")
                    return@withLock
                }

                try {
                    val trackPoint = TrackPointEntity(
                        tripId = tripId,
                        latitude = lat,
                        longitude = lng,
                        timestamp = pointTimestamp,
                    )
                    repository.insertTrackPoint(trackPoint)
                    lastAcceptedLat = lat
                    lastAcceptedLng = lng
                    lastAcceptedTimestamp = pointTimestamp
                    Log.d(TAG, "轨迹点插入成功：lat=$lat, lng=$lng, tripId=$tripId")
                } catch (e: Exception) {
                    Log.e(TAG, "轨迹点插入失败：${e.message}", e)
                }
            }
        }
    }

    private fun shouldAcceptPoint(lat: Double, lng: Double, pointTimestamp: Long): Boolean {
        val lastLat = lastAcceptedLat
        val lastLng = lastAcceptedLng
        val lastTime = lastAcceptedTimestamp
        if (lastLat == null || lastLng == null || lastTime == null) return true

        if (pointTimestamp - lastTime >= MAX_WRITE_INTERVAL_MS) {
            Log.d(TAG, "时间兜底写点：距离不足但已超过 ${MAX_WRITE_INTERVAL_MS}ms")
            return true
        }

        val results = FloatArray(1)
        Location.distanceBetween(lastLat, lastLng, lat, lng, results)
        val distance = results.firstOrNull() ?: 0f
        if (distance < MIN_DISTANCE_METERS) {
            Log.d(TAG, "过滤轨迹点：移动距离 $distance m 小于阈值")
            return false
        }
        return true
    }
}
