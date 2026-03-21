package com.example.lifetrace.utils

import android.util.Log
import com.amap.api.maps.model.LatLng
import com.example.lifetrace.data.database.entity.TrackPointEntity
import kotlin.math.*

/**
 * 轨迹平滑/简化工具
 * 目标：更像导航轨迹线（缩放友好、少毛刺、可选圆润）
 */
object TrackSmoothingUtils {

    /**
     * 轨迹视觉层级
     */
    enum class TrackVisualLayer {
        RECORDING,          // 正在记录的轨迹（高亮、动态宽度）
        FOCUSED_TRIP,       // 聚焦的历史轨迹（中等强调）
        BACKGROUND_TRIP     // 背景历史轨迹（弱化、低干扰）
    }

    /**
     * 简化的渲染配置（用于 renderAllTrips）
     */
    data class SimpleRenderConfig(
        val mainColor: Int,
        val mainWidth: Float,
        val clickable: Boolean,
    )

    data class TrackRenderConfig(
        val mainColor: Int,
        val outlineColor: Int,
        val mainWidth: Float,
        val outlineWidth: Float,
        val enableChaikin: Boolean,
        val chaikinIterations: Int,
    )

    /**
     * 轨迹平滑策略
     * 统一控制去抖、RDP、Chaikin 参数
     */
    data class TrackSmoothingPolicy(
        val layer: TrackVisualLayer,
        val enableJitterFilter: Boolean = true,
        val jitterMinDistanceMeters: Double = 2.0,
        val rdpEpsilonMeters: Double? = null,
        val rdpEpsilonByZoom: ((Float) -> Double)? = null,
        val enableChaikin: Boolean = false,
        val chaikinIterations: Int = 1,
        val minPointsForChaikin: Int = 6,
        val maxPointsForChaikin: Int = 120,
        val preserveEndpoints: Boolean = true,
        val debugLabel: String = "",
    )

    /**
     * 轨迹平滑调试信息
     */
    data class TrackSmoothingDebugInfo(
        val rawCount: Int,
        val filteredCount: Int,
        val simplifiedCount: Int,
        val smoothedCount: Int,
        val zoomLevel: Float,
        val layer: TrackVisualLayer,
        val chaikinEnabled: Boolean,
        val chaikinIterationsApplied: Int,
        val rdpEpsilonMetersApplied: Double,
    )

    /**
     * 轨迹平滑结果
     */
    data class TrackSmoothingResult(
        val points: List<LatLng>,
        val debugInfo: TrackSmoothingDebugInfo,
    )

    /**
     * 主入口：对轨迹做去抖 + RDP 简化 + 可选 Chaikin 平滑
     */
    fun processTrack(
        rawPoints: List<TrackPointEntity>,
        zoomLevel: Float,
        enableChaikin: Boolean = false,
    ): List<LatLng> {
        if (rawPoints.size <= 1) return rawPoints.map { LatLng(it.latitude, it.longitude) }

        // 1) 去抖（过滤过近点）
        val filtered = filterJitter(rawPoints, minDistanceMeters = 2.0)
        if (filtered.size <= 2) return filtered.map { LatLng(it.latitude, it.longitude) }

        // 2) 转 LatLng
        val latLngs = filtered.map { LatLng(it.latitude, it.longitude) }

        // 3) zoom -> epsilon（米）
        val epsilonMeters = when {
            zoomLevel < 13f -> 30.0
            zoomLevel < 15f -> 15.0
            zoomLevel < 17f -> 6.0
            else -> 2.0
        }

        // 4) RDP 简化（返回点列表）
        val simplified = rdpSimplify(latLngs, epsilonMeters)

        // 5) 可选 Chaikin 平滑（先简化再平滑，避免点数爆炸）
        if (!enableChaikin || simplified.size <= 2) return simplified

        val iterations = if (zoomLevel < 15f) 1 else 2
        return chaikinSmooth(simplified, iterations.coerceIn(1, 3))
    }

    /**
     * 统一处理入口：根据策略处理轨迹
     * 返回包含调试信息的结果
     */
    fun processTrackWithPolicy(
        rawPoints: List<TrackPointEntity>,
        zoomLevel: Float,
        policy: TrackSmoothingPolicy,
    ): TrackSmoothingResult {
        val rawCount = rawPoints.size

        // 1) 去抖（根据策略决定是否启用）
        val filtered = if (policy.enableJitterFilter) {
            filterJitter(rawPoints, policy.jitterMinDistanceMeters)
        } else {
            rawPoints
        }
        val filteredCount = filtered.size

        // 边界检查
        if (filtered.size <= 1) {
            return TrackSmoothingResult(
                points = filtered.map { LatLng(it.latitude, it.longitude) },
                debugInfo = TrackSmoothingDebugInfo(
                    rawCount = rawCount,
                    filteredCount = filteredCount,
                    simplifiedCount = filteredCount,
                    smoothedCount = filteredCount,
                    zoomLevel = zoomLevel,
                    layer = policy.layer,
                    chaikinEnabled = false,
                    chaikinIterationsApplied = 0,
                    rdpEpsilonMetersApplied = 0.0,
                )
            )
        }

        // 2) 转 LatLng
        val latLngs = filtered.map { LatLng(it.latitude, it.longitude) }

        // 3) RDP epsilon：优先使用策略中的 zoom 映射函数，否则使用固定值或默认映射
        val epsilonMeters = policy.rdpEpsilonByZoom?.invoke(zoomLevel)
            ?: policy.rdpEpsilonMeters
            ?: when {
                zoomLevel < 13f -> 30.0
                zoomLevel < 15f -> 15.0
                zoomLevel < 17f -> 6.0
                else -> 2.0
            }

        // 4) RDP 简化
        val simplified = rdpSimplify(latLngs, epsilonMeters)
        val simplifiedCount = simplified.size

        // 5) Chaikin 平滑（根据策略和条件决定是否启用）
        var chaikinEnabled = policy.enableChaikin
        var iterationsApplied = 0
        var smoothed = simplified

        if (chaikinEnabled && simplified.size > 2) {
            // 检查点数是否在允许范围内
            if (simplified.size < policy.minPointsForChaikin) {
                chaikinEnabled = false
                Log.d(TAG, "[${policy.debugLabel}] Chaikin disabled: too few points (${simplified.size} < ${policy.minPointsForChaikin})")
            } else if (simplified.size > policy.maxPointsForChaikin) {
                chaikinEnabled = false
                Log.d(TAG, "[${policy.debugLabel}] Chaikin disabled: too many points (${simplified.size} > ${policy.maxPointsForChaikin})")
            } else {
                iterationsApplied = policy.chaikinIterations.coerceIn(1, 3)
                smoothed = chaikinSmooth(simplified, iterationsApplied)
            }
        }

        val smoothedCount = smoothed.size

        // 6) 输出调试日志
        Log.d(TAG, """
            [${policy.debugLabel}] zoom=$zoomLevel layer=${policy.layer}
            raw=$rawCount -> filtered=$filteredCount -> simplified=$simplifiedCount -> smoothed=$smoothedCount
            Chaikin: $chaikinEnabled (iterations=$iterationsApplied)
            RDP epsilon: $epsilonMeters m
        """.trimIndent().replace("\n", " "))

        return TrackSmoothingResult(
            points = smoothed,
            debugInfo = TrackSmoothingDebugInfo(
                rawCount = rawCount,
                filteredCount = filteredCount,
                simplifiedCount = simplifiedCount,
                smoothedCount = smoothedCount,
                zoomLevel = zoomLevel,
                layer = policy.layer,
                chaikinEnabled = chaikinEnabled,
                chaikinIterationsApplied = iterationsApplied,
                rdpEpsilonMetersApplied = epsilonMeters,
            )
        )
    }

    private const val TAG = "TrackSmoothing"

    /**
     * 过滤 GPS 抖动噪点：相邻点距离小于阈值则丢弃
     */
    fun filterJitter(
        points: List<TrackPointEntity>,
        minDistanceMeters: Double = 2.0
    ): List<TrackPointEntity> {
        if (points.size <= 2) return points

        val result = ArrayList<TrackPointEntity>(points.size)
        var lastKept = points.first()
        result.add(lastKept)

        for (i in 1 until points.size - 1) {
            val cur = points[i]
            val d = haversineDistanceMeters(lastKept.latitude, lastKept.longitude, cur.latitude, cur.longitude)
            if (d >= minDistanceMeters) {
                result.add(cur)
                lastKept = cur
            }
        }

        // 保留最后一个点
        if (result.last() != points.last()) result.add(points.last())
        return result
    }

    /**
     * RDP 简化：返回简化后的点列表
     * epsilonMeters 越大，简化越强
     */
    fun rdpSimplify(points: List<LatLng>, epsilonMeters: Double): List<LatLng> {
        if (points.size <= 2) return points

        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.lastIndex] = true

        rdpMark(points, 0, points.lastIndex, epsilonMeters, keep)

        val out = ArrayList<LatLng>(points.size)
        for (i in points.indices) {
            if (keep[i]) out.add(points[i])
        }
        return out
    }

    private fun rdpMark(
        points: List<LatLng>,
        start: Int,
        end: Int,
        epsilonMeters: Double,
        keep: BooleanArray
    ) {
        if (end <= start + 1) return

        val a = points[start]
        val b = points[end]

        var maxDist = 0.0
        var index = -1

        for (i in start + 1 until end) {
            val d = perpendicularDistanceMeters(points[i], a, b)
            if (d > maxDist) {
                maxDist = d
                index = i
            }
        }

        if (index != -1 && maxDist > epsilonMeters) {
            keep[index] = true
            rdpMark(points, start, index, epsilonMeters, keep)
            rdpMark(points, index, end, epsilonMeters, keep)
        }
    }

    /**
     * 点到线段的近似垂距（米）
     * 用局部平面近似（equirectangular），对短距离轨迹足够稳定。
     */
    private fun perpendicularDistanceMeters(p: LatLng, a: LatLng, b: LatLng): Double {
        // 若线段极短，退化成点距
        val ab = haversineDistanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        if (ab < 0.5) {
            return haversineDistanceMeters(p.latitude, p.longitude, a.latitude, a.longitude)
        }

        // 以 a 为原点做局部投影（米）
        val ax = 0.0
        val ay = 0.0
        val bx = metersEast(a.latitude, a.longitude, b.latitude, b.longitude)
        val by = metersNorth(a.latitude, a.longitude, b.latitude, b.longitude)
        val px = metersEast(a.latitude, a.longitude, p.latitude, p.longitude)
        val py = metersNorth(a.latitude, a.longitude, p.latitude, p.longitude)

        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        if (len2 <= 1e-6) return hypot(px - ax, py - ay)

        val t = ((px - ax) * dx + (py - ay) * dy) / len2
        val tt = t.coerceIn(0.0, 1.0)
        val projX = ax + tt * dx
        val projY = ay + tt * dy

        return hypot(px - projX, py - projY)
    }

    /**
     * Chaikin Corner Cutting：真正的“切角”平滑（会增加点数）
     * iterations 建议 1~2（太多会点爆炸）
     */
    fun chaikinSmooth(points: List<LatLng>, iterations: Int = 2): List<LatLng> {
        if (points.size <= 2) return points
        var cur = points

        repeat(iterations.coerceIn(1, 3)) {
            val out = ArrayList<LatLng>(cur.size * 2)
            out.add(cur.first()) // 保留起点

            for (i in 0 until cur.size - 1) {
                val p0 = cur[i]
                val p1 = cur[i + 1]

                // Q = 0.75*p0 + 0.25*p1
                val q = LatLng(
                    0.75 * p0.latitude + 0.25 * p1.latitude,
                    0.75 * p0.longitude + 0.25 * p1.longitude
                )
                // R = 0.25*p0 + 0.75*p1
                val r = LatLng(
                    0.25 * p0.latitude + 0.75 * p1.latitude,
                    0.25 * p0.longitude + 0.75 * p1.longitude
                )

                out.add(q)
                out.add(r)
            }

            out.add(cur.last()) // 保留终点
            cur = out
        }

        return cur
    }

    /**
     * 渲染参数：双层线 + zoom 友好宽度
     */
    fun getRenderConfig(
        zoomLevel: Float,
        mainColor: Int,
        isRecording: Boolean = false
    ): TrackRenderConfig {
        val mainWidth = calculateMainWidth(zoomLevel)
        val outlineWidth = mainWidth + 6f

        // 描边颜色建议固定半透明黑，稳定且”像导航”
        val outlineColor = 0xAA000000.toInt()

        return TrackRenderConfig(
            mainColor = mainColor,
            outlineColor = outlineColor,
            mainWidth = mainWidth,
            outlineWidth = outlineWidth,
            enableChaikin = false, // 默认先关，等你验证 RDP OK 再开
            chaikinIterations = 2,
        )
    }

    /**
     * 根据视觉层级获取渲染配置（用于 renderAllTrips）
     */
    fun getRenderConfigForLayer(
        zoomLevel: Float,
        layer: TrackVisualLayer
    ): SimpleRenderConfig {
        val baseWidth = calculateMainWidth(zoomLevel)

        return when (layer) {
            TrackVisualLayer.RECORDING -> SimpleRenderConfig(
                mainColor = 0xFF2196F3.toInt(),  // 蓝色，高亮
                mainWidth = baseWidth,
                clickable = true
            )
            TrackVisualLayer.FOCUSED_TRIP -> SimpleRenderConfig(
                mainColor = 0xFF2E7D32.toInt(),  // 深绿
                mainWidth = baseWidth * 0.9f,
                clickable = true
            )
            TrackVisualLayer.BACKGROUND_TRIP -> SimpleRenderConfig(
                mainColor = 0x664CAF50.toInt(),  // 40% 透明度绿色
                mainWidth = (baseWidth * 0.5f).coerceIn(4f, 10f),  // 更细
                clickable = true
            )
        }
    }

    private fun calculateMainWidth(zoomLevel: Float): Float {
        // 8..20：缩放越大越粗一点（你可按审美再调）
        return (2f * zoomLevel - 16f).coerceIn(8f, 20f)
    }

    // --- 距离与投影工具 ---

    private fun haversineDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    // 以 (lat0,lon0) 为基准，计算 (lat,lon) 在东向的米偏移
    private fun metersEast(lat0: Double, lon0: Double, lat: Double, lon: Double): Double {
        val r = 6371000.0
        val dLon = Math.toRadians(lon - lon0)
        val latRad = Math.toRadians((lat0 + lat) / 2.0)
        return r * dLon * cos(latRad)
    }

    // 以 (lat0,lon0) 为基准，计算 (lat,lon) 在北向的米偏移
    private fun metersNorth(lat0: Double, lon0: Double, lat: Double, lon: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat - lat0)
        return r * dLat
    }
}

/**
 * 轨迹平滑策略工厂
 * 根据层级和条件提供默认策略
 */
object TrackSmoothingPolicyFactory {

    private const val TAG = "TrackSmoothing"

    /**
     * RECORDING 轨迹策略
     * - Chaikin 关闭，性能优先
     * - 去抖开启，2米阈值
     * - RDP 正常简化
     */
    fun forRecording(zoomLevel: Float, pointCount: Int): TrackSmoothingUtils.TrackSmoothingPolicy {
        return TrackSmoothingUtils.TrackSmoothingPolicy(
            layer = TrackSmoothingUtils.TrackVisualLayer.RECORDING,
            enableJitterFilter = true,
            jitterMinDistanceMeters = 2.0,
            enableChaikin = false,  // 录制中始终关闭，保证性能
            chaikinIterations = 0,
            minPointsForChaikin = 8,
            maxPointsForChaikin = 80,
            debugLabel = "RECORDING",
        )
    }

    /**
     * 根据视觉层级获取策略
     */
    fun forLayer(
        layer: TrackSmoothingUtils.TrackVisualLayer,
        zoomLevel: Float,
        pointCount: Int
    ): TrackSmoothingUtils.TrackSmoothingPolicy {
        return when (layer) {
            TrackSmoothingUtils.TrackVisualLayer.RECORDING -> forRecording(zoomLevel, pointCount)

            TrackSmoothingUtils.TrackVisualLayer.FOCUSED_TRIP -> TrackSmoothingUtils.TrackSmoothingPolicy(
                layer = layer,
                enableJitterFilter = true,
                jitterMinDistanceMeters = 2.0,
                enableChaikin = shouldEnableChaikinForFocusedTrip(zoomLevel, pointCount),
                chaikinIterations = 1,  // 1次迭代，平衡圆润和性能
                minPointsForChaikin = 6,
                maxPointsForChaikin = 120,
                debugLabel = "FOCUSED_TRIP",
            )

            TrackSmoothingUtils.TrackVisualLayer.BACKGROUND_TRIP -> TrackSmoothingUtils.TrackSmoothingPolicy(
                layer = layer,
                enableJitterFilter = true,
                jitterMinDistanceMeters = 2.0,
                enableChaikin = false,  // 背景轨迹不开启，降低地图负担
                chaikinIterations = 0,
                minPointsForChaikin = 6,
                maxPointsForChaikin = 120,
                debugLabel = "BACKGROUND_TRIP",
            )
        }
    }

    /**
     * 判断 FOCUSED_TRIP 是否应该启用 Chaikin
     * 条件：
     * - zoom >= 14（放大到一定程度）
     * - 点数在 6~120 之间
     */
    private fun shouldEnableChaikinForFocusedTrip(zoomLevel: Float, pointCount: Int): Boolean {
        val zoomOk = zoomLevel >= 14f
        val pointCountOk = pointCount in 6..120

        if (!zoomOk) {
            Log.d(TAG, "FOCUSED_TRIP: Chaikin disabled - zoom too low ($zoomLevel < 14)")
        }
        if (!pointCountOk) {
            Log.d(TAG, "FOCUSED_TRIP: Chaikin disabled - point count out of range ($pointCount)")
        }

        return zoomOk && pointCountOk
    }
}