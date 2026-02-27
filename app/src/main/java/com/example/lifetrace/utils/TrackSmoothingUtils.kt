package com.example.lifetrace.utils

import com.amap.api.maps.model.LatLng
import com.example.lifetrace.data.database.entity.TrackPointEntity
import kotlin.math.*

/**
 * 轨迹平滑/简化工具
 * 目标：更像导航轨迹线（缩放友好、少毛刺、可选圆润）
 */
object TrackSmoothingUtils {

    data class TrackRenderConfig(
        val mainColor: Int,
        val outlineColor: Int,
        val mainWidth: Float,
        val outlineWidth: Float,
        val enableChaikin: Boolean,
        val chaikinIterations: Int,
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

        // 描边颜色建议固定半透明黑，稳定且“像导航”
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