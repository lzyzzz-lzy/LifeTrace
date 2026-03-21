package com.example.lifetrace.ui.map

import android.os.Bundle
import android.view.MotionEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.BitmapDescriptorFactory
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.PolylineOptions
import com.example.lifetrace.R
import com.example.lifetrace.data.database.entity.MemoryNodeEntity
import com.example.lifetrace.data.database.entity.TrackPointEntity
import com.example.lifetrace.data.database.entity.TripEntity
import com.example.lifetrace.state.CameraAction
import com.example.lifetrace.state.CameraMode
import com.example.lifetrace.state.MapMode
import com.example.lifetrace.state.MapUiState
import com.example.lifetrace.utils.TrackSmoothingPolicyFactory
import com.example.lifetrace.utils.TrackSmoothingUtils
import com.example.lifetrace.utils.TrackPolylineRenderer

@Composable
fun LifeTraceMap(
    mapUiState: MapUiState,
    onMapMoved: (Boolean, Float) -> Unit,
    onExploreTripClicked: (Long) -> Unit,
    onMapLongPressAddMemory: (Double, Double) -> Unit,
    onCurrentLocationChanged: (Double, Double) -> Unit,
    onMemoryNodeClicked: (MemoryNodeEntity) -> Unit = {},
    onCameraActionExecuted: () -> Unit = {},
    modifier: Modifier = Modifier,
    isNightMode: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        MapView(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )
            onCreate(Bundle())
        }
    }

    val aMap by remember(mapView) { mutableStateOf(mapView.map) }

    // 用于替代 polyline.object / polyline.isClickable 的本地映射
    val polylineIdToTripId = remember { mutableMapOf<String, Long>() }
    val clickablePolylineIds = remember { mutableSetOf<String>() }

    // Marker 到 MemoryNodeEntity 的映射
    val markerIdToMemoryNode = remember { mutableMapOf<String, MemoryNodeEntity>() }

    // 轨迹线渲染器（用于双层线渲染和增量更新）
    // 注意：必须依赖 aMap，确保 map 实例 ready 后再创建
    val polylineRenderer = remember(aMap) { TrackPolylineRenderer(aMap) }

    var lastLat by remember { mutableStateOf(0.0) }
    var lastLng by remember { mutableStateOf(0.0) }
    var lastCameraFitSignature by remember { mutableStateOf("") }
    var lastMapMode by remember { mutableStateOf<MapMode?>(null) }
    var isFirstEnterMode by remember { mutableStateOf<MapMode?>(null) }
    var lastPendingActionSignature by remember { mutableStateOf("") }

    DisposableEffect(lifecycleOwner, mapView) {
        var isDestroyed = false

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> {
                    if (!isDestroyed) {
                        mapView.onDestroy()
                        isDestroyed = true
                    }
                }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            polylineRenderer.destroy()
            if (!isDestroyed) {
                mapView.onPause()
                mapView.onDestroy()
                isDestroyed = true
            }
        }
    }

    LaunchedEffect(
        aMap,
        onMapMoved,
        onExploreTripClicked,
        onMapLongPressAddMemory,
        onCurrentLocationChanged,
        onMemoryNodeClicked,
    ) {
        setupMap(
            aMap = aMap,
            onMapMoved = onMapMoved,
            onExploreTripClicked = onExploreTripClicked,
            onMapLongPressAddMemory = onMapLongPressAddMemory,
            onMemoryNodeClicked = onMemoryNodeClicked,
            polylineIdToTripId = polylineIdToTripId,
            clickablePolylineIds = clickablePolylineIds,
            markerIdToMemoryNode = markerIdToMemoryNode,
        )

        aMap.setOnMyLocationChangeListener { location ->
            location ?: return@setOnMyLocationChangeListener

            val lat = location.latitude
            val lng = location.longitude
            if (kotlin.math.abs(lat - lastLat) > 0.0001 || kotlin.math.abs(lng - lastLng) > 0.0001) {
                lastLat = lat
                lastLng = lng
                onCurrentLocationChanged(lat, lng)
            }
        }
    }

    LaunchedEffect(mapUiState, isNightMode) {
        // 先保存上一个模式，再检查模式切换
        val previousMode = lastMapMode
        val modeChanged = previousMode != mapUiState.mode

        // 检查是否首次进入当前模式
        val currentMode = mapUiState.mode
        val isFirstEnter = isFirstEnterMode != currentMode
        if (isFirstEnter) {
            isFirstEnterMode = currentMode
        }

        // 传递上一个模式，用于判断是否需要清除旧数据
        renderMapState(
            map = aMap,
            state = mapUiState,
            previousMode = previousMode,
            polylineIdToTripId = polylineIdToTripId,
            clickablePolylineIds = clickablePolylineIds,
            markerIdToMemoryNode = markerIdToMemoryNode,
            polylineRenderer = polylineRenderer,
        )

        // 更新最后模式（在渲染之后）
        lastMapMode = mapUiState.mode

        aMap.mapType = if (isNightMode) AMap.MAP_TYPE_NIGHT else AMap.MAP_TYPE_NORMAL

        // 镜头控制：分离为两部分
        // 1. 首次进入模式时的镜头适配（仅 EXPLORE 和 MEMORY 模式）
        if (isFirstEnter && mapUiState.mode in listOf(MapMode.EXPLORE, MapMode.MEMORY)) {
            fitCameraForState(aMap, mapUiState)
        }

        // 2. 处理 pendingCameraAction（一次性动作）
        val actionSignature = mapUiState.pendingCameraAction?.hashCode().toString()
        if (actionSignature != lastPendingActionSignature && mapUiState.pendingCameraAction != null) {
            executeCameraAction(aMap, mapUiState.pendingCameraAction!!, mapUiState)
            lastPendingActionSignature = actionSignature
            // 通知 ViewModel 动作已执行
            onCameraActionExecuted()
        }
    }

    // RECORDING 模式下持续跟随（独立的 LaunchedEffect）
    LaunchedEffect(mapUiState.cameraMode, mapUiState.currentTrackPoints.lastOrNull()) {
        if (mapUiState.cameraMode == CameraMode.FOLLOWING &&
            mapUiState.mode == MapMode.RECORDING) {
            followUser(aMap)
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun setupMap(
    aMap: AMap,
    onMapMoved: (Boolean, Float) -> Unit,
    onExploreTripClicked: (Long) -> Unit,
    onMapLongPressAddMemory: (Double, Double) -> Unit,
    onMemoryNodeClicked: (MemoryNodeEntity) -> Unit,
    polylineIdToTripId: Map<String, Long>,
    clickablePolylineIds: Set<String>,
    markerIdToMemoryNode: MutableMap<String, MemoryNodeEntity>,
) {
    aMap.uiSettings.apply {
        isZoomControlsEnabled = false
        isMyLocationButtonEnabled = false
        isCompassEnabled = false
        isScaleControlsEnabled = false
    }

    aMap.isMyLocationEnabled = true

    var movedByUserGesture = false

    aMap.setOnMapTouchListener { motionEvent ->
        if (motionEvent?.action == MotionEvent.ACTION_DOWN) {
            movedByUserGesture = true
        }
    }

    aMap.setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
        override fun onCameraChange(cameraPosition: CameraPosition?) = Unit

        override fun onCameraChangeFinish(cameraPosition: CameraPosition?) {
            val zoom = cameraPosition?.zoom ?: aMap.cameraPosition?.zoom ?: 15f
            onMapMoved(movedByUserGesture, zoom)
            movedByUserGesture = false
        }
    })

    aMap.setOnPolylineClickListener { polyline ->
        val id = polyline.id
        if (id !in clickablePolylineIds) return@setOnPolylineClickListener
        val tripId = polylineIdToTripId[id] ?: return@setOnPolylineClickListener
        onExploreTripClicked(tripId)
    }

    aMap.setOnMapLongClickListener { latLng ->
        onMapLongPressAddMemory(latLng.latitude, latLng.longitude)
    }

    aMap.setOnMarkerClickListener { marker ->
        val markerId = marker.id
        val node = markerIdToMemoryNode[markerId]
        if (node != null) {
            onMemoryNodeClicked(node)
            true
        } else {
            false
        }
    }
}

private fun fitCameraForState(map: AMap, state: MapUiState) {
    when (state.mode) {
        MapMode.EXPLORE -> {
            val points = state.allTripsTrackPoints.flatten()
            animateToBounds(map, points)
        }

        MapMode.MEMORY -> {
            animateToBounds(map, state.focusedTripTrackPoints)
        }

        else -> Unit
    }
}

private fun animateToBounds(map: AMap, points: List<TrackPointEntity>) {
    if (points.isEmpty()) return

    if (points.size == 1) {
        val point = points.first()
        map.animateCamera(
            CameraUpdateFactory.newLatLngZoom(
                LatLng(point.latitude, point.longitude),
                17f,
            ),
        )
        return
    }

    val builder = LatLngBounds.builder()
    points.forEach { point ->
        builder.include(LatLng(point.latitude, point.longitude))
    }

    map.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 120))
}

private fun followUser(map: AMap) {
    val location = map.myLocation ?: return
    map.animateCamera(
        CameraUpdateFactory.newLatLngZoom(
            LatLng(location.latitude, location.longitude),
            17f,
        ),
    )
}

/**
 * 执行一次性镜头动作
 */
private fun executeCameraAction(map: AMap, action: CameraAction, state: MapUiState) {
    when (action) {
        is CameraAction.FitToBounds -> animateToBounds(map, action.points)
        is CameraAction.FollowUserOnce -> {
            val location = map.myLocation ?: return
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                    LatLng(location.latitude, location.longitude),
                    action.zoom,
                ),
            )
        }
        is CameraAction.MoveToTrip -> {
            animateToBounds(map, state.focusedTripTrackPoints)
        }
    }
}

private fun renderMapState(
    map: AMap,
    state: MapUiState,
    previousMode: MapMode?,
    polylineIdToTripId: MutableMap<String, Long>,
    clickablePolylineIds: MutableSet<String>,
    markerIdToMemoryNode: MutableMap<String, MemoryNodeEntity>,
    polylineRenderer: TrackPolylineRenderer,
) {
    // 判断是否需要清除地图上的旧数据
    // 1. 模式切换时（从其他模式切换到当前模式）
    // 2. 在非录制模式下，每次都需要重新渲染
    val modeChanged = previousMode != state.mode
    val shouldClear = modeChanged ||
                     (state.mode != MapMode.RECORDING && state.mode != MapMode.RECORDING_MEMORY)

    if (shouldClear) {
        map.clear()
        polylineIdToTripId.clear()
        clickablePolylineIds.clear()
        markerIdToMemoryNode.clear()
        polylineRenderer.clear()
    } else {
        // 录制模式下不执行 clear，但需要更新 markers
        markerIdToMemoryNode.clear()
    }

    when (state.mode) {
        MapMode.EXPLORE -> renderAllTrips(
            map = map,
            allTrips = state.allTrips,
            allTripsTrackPoints = state.allTripsTrackPoints,
            polylineIdToTripId = polylineIdToTripId,
            clickablePolylineIds = clickablePolylineIds,
            zoomLevel = state.zoomLevel,
        )

        MapMode.MEMORY -> {
            if (state.showAllTrips) {
                renderAllTrips(
                    map = map,
                    allTrips = state.allTrips,
                    allTripsTrackPoints = state.allTripsTrackPoints,
                    polylineIdToTripId = polylineIdToTripId,
                    clickablePolylineIds = clickablePolylineIds,
                    color = 0x554CAF50,
                    width = 12f,
                    clickable = true,
                    zoomLevel = state.zoomLevel,
                )
            }

            // 聚焦轨迹：使用 renderer（双层线 + 简化）
            val cfg = TrackSmoothingUtils.getRenderConfig(
                zoomLevel = state.zoomLevel,
                mainColor = 0xFF2E7D32.toInt(),
                isRecording = false
            )
            polylineRenderer.renderOrUpdate(
                config = cfg,
                points = state.focusedTripTrackPoints,
                zoomLevel = state.zoomLevel,
            )

            renderMemoryNodesByZoom(map, state.focusedTripMemoryNodes, state.zoomLevel, markerIdToMemoryNode)
        }

        MapMode.RECORDING -> {
            renderRecordingTrack(
                map = map,
                state = state,
                polylineRenderer = polylineRenderer,
            )
        }

        MapMode.RECORDING_MEMORY -> {
            renderRecordingTrack(
                map = map,
                state = state,
                polylineRenderer = polylineRenderer,
            )
            renderMemoryNodesByZoom(map, state.focusedTripMemoryNodes, state.zoomLevel, markerIdToMemoryNode)
        }
    }
}

private fun renderTrackPoints(
    map: AMap,
    points: List<TrackPointEntity>,
    color: Int,
    width: Float,
    clickable: Boolean,
    tripId: Long?,
    polylineIdToTripId: MutableMap<String, Long>,
    clickablePolylineIds: MutableSet<String>,
    zoomLevel: Float = 15f,
    policy: TrackSmoothingUtils.TrackSmoothingPolicy? = null,
) {
    if (points.size < 2) return

    // 使用传入的 policy 或创建默认 policy
    val effectivePolicy = policy ?: TrackSmoothingPolicyFactory.forLayer(
        layer = TrackSmoothingUtils.TrackVisualLayer.BACKGROUND_TRIP,
        zoomLevel = zoomLevel,
        pointCount = points.size
    )

    // ✅ 统一使用 processTrackWithPolicy
    val result = TrackSmoothingUtils.processTrackWithPolicy(
        rawPoints = points,
        zoomLevel = zoomLevel,
        policy = effectivePolicy,
    )
    if (result.points.size < 2) return

    val polyline = map.addPolyline(
        PolylineOptions()
            .addAll(result.points)
            .width(width)
            .color(color)
    )

    //polyline.isClickable = clickable

    val id = polyline.id
    if (tripId != null) polylineIdToTripId[id] = tripId
    if (clickable) clickablePolylineIds.add(id)
}



/**
 * 渲染 RECORDING 模式：当前录制轨迹
 * 注意：镜头跟随已移至独立的 LaunchedEffect，此处仅负责轨迹渲染
 */
private fun renderRecordingTrack(
    map: AMap,
    state: MapUiState,
    polylineRenderer: TrackPolylineRenderer,
) {
    val config = TrackSmoothingUtils.getRenderConfig(
        zoomLevel = state.zoomLevel,
        mainColor = 0xFF2196F3.toInt(),
        isRecording = true,
    )

    polylineRenderer.renderOrUpdate(
        config = config,
        points = state.currentTrackPoints,
        zoomLevel = state.zoomLevel,
    )
    // 镜头跟随逻辑已移至 LifeTraceMap Composable 中的独立 LaunchedEffect
}

/**
 * 根据 zoom level 渲染回忆节点
 * zoom >= 17: 显示所有节点
 * zoom < 17: 聚合显示
 */
private fun renderMemoryNodesByZoom(
    map: AMap,
    nodes: List<MemoryNodeEntity>,
    zoomLevel: Float,
    markerIdToMemoryNode: MutableMap<String, MemoryNodeEntity>,
) {
    if (nodes.isEmpty()) return

    if (zoomLevel >= 17f) {
        renderMemoryNodes(map, nodes, markerIdToMemoryNode)
    } else {
        renderMemoryNodeClusters(map, nodes, markerIdToMemoryNode)
    }
}

/**
 * 渲染 MEMORY 模式：聚焦的旅程轨迹 + 回忆节点
 */

private fun renderMemoryNodes(
    map: AMap,
    nodes: List<MemoryNodeEntity>,
    markerIdToMemoryNode: MutableMap<String, MemoryNodeEntity>,
) {
    nodes.forEach {
        // 新模型：根据是否有封面图决定显示方式
        val title = it.text ?: "回忆"

        // 如果有封面图，使用封面；否则使用默认图标
        val marker = if (it.coverUri != null) {
            // 使用封面图作为 marker
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(it.latitude, it.longitude))
                    .title(title)
                    .draggable(false)
                    .anchor(0.5f, 0.5f)
            )
        } else {
            // 无封面，使用默认图标
            val iconRes = R.drawable.ic_text_marker

            map.addMarker(
                MarkerOptions()
                    .position(LatLng(it.latitude, it.longitude))
                    .title(title)
                    .icon(BitmapDescriptorFactory.fromResource(iconRes))
                    .draggable(false)
                    .anchor(0.5f, 0.5f)
            )
        }

        // 存储标记到节点的映射
        if (marker != null) {
            markerIdToMemoryNode[marker.id] = it
        }
    }
}

private fun renderMemoryNodeClusters(
    map: AMap,
    nodes: List<MemoryNodeEntity>,
    markerIdToMemoryNode: MutableMap<String, MemoryNodeEntity>,
) {
    val grouped = nodes.groupBy {
        val latBucket = (it.latitude * 1000).toInt()
        val lngBucket = (it.longitude * 1000).toInt()
        latBucket to lngBucket
    }

    grouped.values.forEach { cluster ->
        val centerLat = cluster.map { it.latitude }.average()
        val centerLng = cluster.map { it.longitude }.average()
        val title = if (cluster.size == 1) {
            cluster.first().text ?: "回忆"
        } else {
            "${cluster.size} 条回忆"
        }

        // 使用默认图标，增大点击区域
        val marker = map.addMarker(
            MarkerOptions()
                .position(LatLng(centerLat, centerLng))
                .title(title)
                .snippet("memory")
                .draggable(false)
                .anchor(0.5f, 0.5f)  // 设置锚点为中心，增大点击区域
        )
        // 存储标记到第一个节点的映射（点击时可以展示该节点）
        if (marker != null) {
            markerIdToMemoryNode[marker.id] = cluster.first()
        }
    }
}

private fun renderAllTrips(
    map: AMap,
    allTrips: List<TripEntity>,
    allTripsTrackPoints: List<List<TrackPointEntity>>,
    polylineIdToTripId: MutableMap<String, Long>,
    clickablePolylineIds: MutableSet<String>,
    focusedTripId: Long? = null,
    color: Int? = null,
    width: Float? = null,
    clickable: Boolean? = null,
    zoomLevel: Float = 15f,
) {
    allTripsTrackPoints.forEachIndexed { index, points ->
        val tripId = allTrips.getOrNull(index)?.tripId ?: return@forEachIndexed

        // 检查是否聚焦 trip
        val isFocused = tripId == focusedTripId
        val layer = if (isFocused) {
            TrackSmoothingUtils.TrackVisualLayer.FOCUSED_TRIP
        } else {
            TrackSmoothingUtils.TrackVisualLayer.BACKGROUND_TRIP
        }

        // 获取渲染配置
        val renderConfig = TrackSmoothingUtils.getRenderConfigForLayer(
            zoomLevel = zoomLevel,
            layer = layer
        )

        // ✅ 获取平滑策略（统一由工厂决定）
        val smoothingPolicy = TrackSmoothingPolicyFactory.forLayer(
            layer = layer,
            zoomLevel = zoomLevel,
            pointCount = points.size
        )

        // 如果调用时指定了 color/width/clickable，则使用它们，否则使用配置中的值
        val finalColor = color ?: renderConfig.mainColor
        val finalWidth = width ?: renderConfig.mainWidth
        val finalClickable = clickable ?: renderConfig.clickable

        // ✅ 统一调用 renderTrackPoints，传递 policy
        renderTrackPoints(
            map = map,
            points = points,
            color = finalColor,
            width = finalWidth,
            clickable = finalClickable,
            tripId = tripId,
            polylineIdToTripId = polylineIdToTripId,
            clickablePolylineIds = clickablePolylineIds,
            zoomLevel = zoomLevel,
            policy = smoothingPolicy,
        )
    }
}