package com.example.lifetrace.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.amap.api.location.AMapLocationListener
import com.example.lifetrace.R
import com.example.lifetrace.data.database.entity.TrackPointEntity
import com.example.lifetrace.data.database.repository.TrackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.app.PendingIntent

// 日志TAG，方便调试
private const val TAG = "TripRecordingService"
// 通知ID
private const val NOTIFICATION_ID = 1
// 通知渠道ID
private const val CHANNEL_ID = "trip_record_channel"

class TripRecordingService : Service(), AMapLocationListener {

    // 改用可空类型，避免lateinit崩溃
    private var locationClient: AMapLocationClient? = null
    private var trackRepository: TrackRepository? = null

    private val scope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "服务创建：初始化Repository和定位")

        // 初始化Repository（空安全）
        trackRepository = TrackRepository.getInstance(this)

        // 初始化定位
        initLocation()

        // 启动前台服务
        startForegroundService()
    }

    private fun initLocation() {
        // 空安全判断：避免重复初始化
        if (locationClient != null) return

        try {
            locationClient = AMapLocationClient(applicationContext).apply {
                val option = AMapLocationClientOption().apply {
                    // 高精度定位（GPS+网络）
                    locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy
                    // 3秒采集一次（平衡精度和耗电）
                    interval = 3000
                    // 不需要地址信息，减少流量/耗时
                    isNeedAddress = false
                    // 禁用模拟定位，防止作弊
                    isMockEnable = false
                    // 开启传感器，定位更精准
                    isSensorEnable = true
                    // 开启缓存，提升弱网/室内定位稳定性
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
        // 1. 创建通知渠道（仅首次创建）
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "轨迹记录",
                NotificationManager.IMPORTANCE_LOW // 低优先级，不弹窗
            ).apply {
                // 不显示角标
                setShowBadge(false)
                // 无震动/声音
                enableVibration(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
            Log.d(TAG, "通知渠道创建成功")
        }

        // 2. 构建通知（可点击返回App）
        val notificationIntent = packageManager.getLaunchIntentForPackage(packageName)
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("LifeTrace 正在记录轨迹")
            .setContentText("点击返回应用")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // 不可手动取消，防止服务被杀死
            .setContentIntent(notificationIntent?.let {
                androidx.core.app.PendingIntent.getActivity(
                    this,
                    0,
                    it,
                    androidx.core.app.PendingIntent.FLAG_UPDATE_CURRENT or androidx.core.app.PendingIntent.FLAG_IMMUTABLE
                )
            })
            .build()

        // 3. 启动前台服务
        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "前台服务启动成功")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "服务启动：开始定位")
        // 空安全：启动定位前检查是否初始化成功
        locationClient?.startLocation() ?: run {
            // 定位客户端未初始化，重新初始化
            initLocation()
            locationClient?.startLocation()
        }
        // START_STICKY：服务被杀死后，系统会尝试重启（但不保留intent）
        return START_STICKY
    }

    override fun onDestroy() {
        Log.d(TAG, "服务销毁：停止定位并释放资源")
        // 停止定位+释放资源
        locationClient?.apply {
            stopLocation()
            onDestroy()
        }
        // 清空当前TripId，避免数据错乱
        trackRepository?.clearCurrentTripId()
        // 取消前台服务
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    // 用户从最近任务栏关闭App时，停止服务
    override fun onTaskRemoved(rootIntent: Intent?) {
        Log.d(TAG, "App被关闭：停止轨迹录制服务")
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onLocationChanged(location: AMapLocation?) {
        // 1. 空判断
        if (location == null) {
            Log.w(TAG, "定位结果为空")
            return
        }

        // 2. 错误处理
        if (location.errorCode != 0) {
            Log.e(TAG, "定位失败：错误码=${location.errorCode}，错误信息=${location.errorInfo}")
            return
        }

        // 3. 解析定位数据
        val lat = location.latitude
        val lng = location.longitude
        val time = System.currentTimeMillis()
        val tripId = trackRepository?.getCurrentTripId() // 获取当前活跃TripId

        // 4. 空判断：TripId为空时不插入（未开始录制）
        if (tripId == null) {
            Log.w(TAG, "当前无活跃Trip，跳过轨迹点插入")
            return
        }

        // 5. 插入数据库（协程+空安全）
        scope.launch {
            try {
                val trackPoint = TrackPointEntity(
                    tripId = tripId,
                    latitude = lat,
                    longitude = lng,
                    timestamp = time
                )
                trackRepository?.insertTrackPoint(trackPoint)
                Log.d(TAG, "轨迹点插入成功：lat=$lat, lng=$lng, tripId=$tripId")
            } catch (e: Exception) {
                Log.e(TAG, "轨迹点插入失败：${e.message}", e)
            }
        }
    }
}