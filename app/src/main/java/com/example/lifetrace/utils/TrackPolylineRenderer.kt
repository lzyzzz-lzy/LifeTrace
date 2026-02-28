package com.example.lifetrace.utils

import com.amap.api.maps.AMap
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import com.example.lifetrace.data.database.entity.TrackPointEntity

/**
 * 轨迹线渲染器
 * 负责增量更新 polyline，尽量避免录制时闪烁
 *
 * 双层线：outline + main
 */
class TrackPolylineRenderer(
    private val map: AMap,
) {
    private var outlinePolyline: Polyline? = null
    private var mainPolyline: Polyline? = null

    private var lastProcessedSize: Int = 0
    private var lastZoomBucket: Int = -1

    /**
     * 渲染或更新轨迹线
     *
     * @param config 渲染配置（主线/描边颜色与宽度）
     * @param points 轨迹点列表
     * @param zoomLevel 缩放级别
     * @param forceRedraw 是否强制重算
     */
    fun renderOrUpdate(
        config: TrackSmoothingUtils.TrackRenderConfig,
        points: List<TrackPointEntity>,
        zoomLevel: Float,
        forceRedraw: Boolean = false,
    ) {
        if (points.size < 2) {
            clear()
            return
        }

        val currentSize = points.size
        val zoomBucket = (zoomLevel * 2f).toInt() // 0.5 zoom 分桶

        val needsRecompute = forceRedraw ||
                lastProcessedSize != currentSize ||
                lastZoomBucket != zoomBucket

        if (!needsRecompute) return

        // 1) 计算（去抖 + 简化 + 可选平滑）
        val latLngs = TrackSmoothingUtils.processTrack(
            rawPoints = points,
            zoomLevel = zoomLevel,
            enableChaikin = config.enableChaikin
        )

        if (latLngs.size < 2) {
            clear()
            return
        }

        // 2) 更新或创建 polyline（优先 setPoints，避免 remove/add 闪烁）
        if (outlinePolyline == null || mainPolyline == null) {
            createPolylines(config, latLngs)
        } else {
            val updated = tryUpdatePolylines(config, latLngs)
            if (!updated) {
                // 某些版本不支持 setPoints/修改属性，退化为重建
                clear()
                createPolylines(config, latLngs)
            }
        }

        lastProcessedSize = currentSize
        lastZoomBucket = zoomBucket
    }

    private fun createPolylines(config: TrackSmoothingUtils.TrackRenderConfig, latLngs: List<LatLng>) {
        val outlineOpts = PolylineOptions()
            .addAll(latLngs)
            .width(config.outlineWidth)
            .color(config.outlineColor)

        val mainOpts = PolylineOptions()
            .addAll(latLngs)
            .width(config.mainWidth)
            .color(config.mainColor)
            //.clickable(true)

        // 尝试启用”圆角/圆端点”（如果 SDK 支持）
        trySetRoundJoinCap(outlineOpts)
        trySetRoundJoinCap(mainOpts)

        outlinePolyline = map.addPolyline(outlineOpts)
        mainPolyline = map.addPolyline(mainOpts)
    }

    /**
     * 尝试更新已存在的 polyline（不 remove/add）
     * @return true 表示更新成功；false 表示不支持或失败，需要重建
     */
    private fun tryUpdatePolylines(config: TrackSmoothingUtils.TrackRenderConfig, latLngs: List<LatLng>): Boolean {
        val outline = outlinePolyline ?: return false
        val main = mainPolyline ?: return false

        return try {
            // AMap Polyline 通常支持 setPoints(List<LatLng>)
            outline.points = latLngs
            main.points = latLngs

            // 宽度/颜色也同步（如果支持属性 setter）
            outline.width = config.outlineWidth
            main.width = config.mainWidth

            outline.color = config.outlineColor
            main.color = config.mainColor

            true
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * 尝试设置 PolylineOptions 的 join/cap 为 ROUND
     *
     * 说明：高德不同版本 API 不一致，为避免“写了不存在的字段导致编译失败”，这里用反射尝试。
     * 失败就忽略，不影响主功能（靠 RDP/Chaikin + 双层线也很像导航）。
     */
    private fun trySetRoundJoinCap(opts: PolylineOptions) {
        // 下面是“尽力而为”：存在就设，不存在就算
        runCatching {
            // 尝试：setLineJoinType(ROUND)
            val joinTypeClass = Class.forName("com.amap.api.maps.model.PolylineOptions\$LineJoinType")
            val roundJoin = java.lang.Enum.valueOf(joinTypeClass as Class<out Enum<*>>, "ROUND")
            val m = opts.javaClass.methods.firstOrNull { it.name == "setLineJoinType" && it.parameterTypes.size == 1 }
            m?.invoke(opts, roundJoin)
        }

        runCatching {
            // 尝试：setLineCapType(ROUND)
            val capTypeClass = Class.forName("com.amap.api.maps.model.PolylineOptions\$LineCapType")
            val roundCap = java.lang.Enum.valueOf(capTypeClass as Class<out Enum<*>>, "ROUND")
            val m = opts.javaClass.methods.firstOrNull { it.name == "setLineCapType" && it.parameterTypes.size == 1 }
            m?.invoke(opts, roundCap)
        }
    }

    /**
     * 清除当前轨迹线
     */
    fun clear() {
        outlinePolyline?.remove()
        mainPolyline?.remove()
        outlinePolyline = null
        mainPolyline = null
        lastProcessedSize = 0
        lastZoomBucket = -1
    }

    fun destroy() = clear()
}