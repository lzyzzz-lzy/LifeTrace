package com.example.lifetrace.ui.map

import android.os.Bundle
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
import androidx.room.util.appendPlaceholders
import com.amap.api.mapcore.util.it
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.PolylineOptions
import com.example.lifetrace.data.database.entity.TripEntity
import com.example.lifetrace.state.MapMode
import com.example.lifetrace.state.MapUiState

/**
 * 封装高德地图 MapView 的 Compose 组件
 * * @param modifier 布局修饰符（控制大小、边距等）
 */
@Composable
fun LifeTraceMap(
    mapUiState: MapUiState,
    onMapMoved: () -> Unit, // 你原有：地图移动回调
    // 我新增的参数（可合并到 MapUiState 中）
    onCurrentLocationChanged: (Double, Double) -> Unit, // 当前定位回调
    modifier: Modifier = Modifier,
    isNightMode: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val mapView = remember {
        MapView(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            onCreate(Bundle())
        }
    }

    val aMap by remember(mapView) { mutableStateOf(mapView.map) }

    // 新增：缓存当前定位，避免频繁回调
    var lastLat by remember { mutableStateOf(0.0) }
    var lastLng by remember { mutableStateOf(0.0) }

    DisposableEffect(lifecycleOwner, mapView) {
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_DESTROY -> {
                    mapView.onDestroy()
                    mapView.onLowMemory()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            mapView.onPause()
            mapView.onDestroy()
        }
    }

    // 1. 初始化地图 + 监听定位变化
    LaunchedEffect(aMap) {
        setupMap(aMap, onMapMoved)

        // 新增：监听定位变化，传递给外部
        aMap.setOnMyLocationChangeListener { location ->
            location ?: return@setOnMyLocationChangeListener

            val lat = location.latitude
            val lng = location.longitude

            // 防抖：坐标变化超过0.0001才回调（避免频繁刷新）
            if (kotlin.math.abs(lat - lastLat) > 0.0001 || kotlin.math.abs(lng - lastLng) > 0.0001) {
                lastLat = lat
                lastLng = lng
                onCurrentLocationChanged(lat, lng) // 传递定位给HomeScreen
            }
        }
    }

    // 2. 监听状态变化（你原有逻辑）
    LaunchedEffect(mapUiState, isNightMode) {
        renderMapState(aMap, mapUiState)
        aMap.mapType = if (isNightMode) AMap.MAP_TYPE_NIGHT else AMap.MAP_TYPE_NORMAL
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
    )
}

// 修正：setupMap 保留你原有逻辑，仅补全 onCameraChange 空实现
private fun setupMap(
    aMap: AMap,
    onMapMoved: () -> Unit,
) {
    aMap.uiSettings.apply {
        isZoomControlsEnabled = false
        isMyLocationButtonEnabled = false
        isCompassEnabled = false
        isScaleControlsEnabled = false
    }

    aMap.isMyLocationEnabled = true

    aMap.setOnCameraChangeListener(object : AMap.OnCameraChangeListener {
        override fun onCameraChange(p0: CameraPosition?) {
            // 空实现即可，无需TODO
        }
        override fun onCameraChangeFinish(p0: CameraPosition?) {
            onMapMoved() // 触发你预留的“地图移动”回调
        }
    })
}

// 修正：followUser 增加空安全处理
private fun followUser(map: AMap) {
    val location = map.myLocation ?: return
    val latLng = LatLng(location.latitude, location.longitude)

    map.animateCamera(
        CameraUpdateFactory.newLatLngZoom(latLng, 17f)
    )
}
// 根据state渲染地图
private fun renderMapState(
    map: AMap,
    state: MapUiState
) {
    when (state.mode) {
        // 探索模式：清空地图，渲染所有旅程
        MapMode.EXPLORE -> {
            map.clear()
            renderAllTrips(map)
        }

        // 回忆模式：清空地图，渲染指定旅程+回忆点
        MapMode.MEMORY -> {
            map.clear()
            state.focusedTrip?.let {
                renderTrip(map, it)
                renderMemoryNodes(map, it.tripId)
            }
        }

        // 录制模式：清空地图，渲染当前旅程+跟随用户
        MapMode.RECORDING -> {
            map.clear()
            renderCurrentTrip(map)
            if (state.followUser) followUser(map)
        }

        // 录制+回忆模式：清空地图，渲染当前旅程+回忆点
        MapMode.RECORDING_MEMORY -> {
            map.clear()
            renderCurrentTrip(map)
            renderMemoryNodes(map, state.focusedTrip?.tripId)
        }
    }
}

//渲染指定trip
private fun renderTrip(
    map: AMap,
    trip: TripEntity
) {
    val points = loadTrackPoints(trip.tripId)

    if (points.size < 2) return

    val latLngs = points.map {
        LatLng(it.latitude, it.longitude)
    }

    map.addPolyline(
        PolylineOptions()
            .addAll(latLngs)
            .width(12f)
            .color(0xFF4CAF50.toInt())
    )

    // 聚焦起点
    map.animateCamera(
        com.amap.api.maps.CameraUpdateFactory.newLatLngZoom(
            latLngs.first(),
            15f
        )
    )
}

// 渲染当前轨迹（记录中）
private fun renderCurrentTrip(map: AMap) {
    val points = loadCurrentTripPoints()

    if (points.size < 2) return

    val latLngs = points.map {
        LatLng(it.latitude, it.longitude)
    }

    map.addPolyline(
        PolylineOptions()
            .addAll(latLngs)
            .width(14f)
            .color(0xFF2196F3.toInt())
    )
}

// 渲染记录点
private fun renderMemoryNodes(
    map: AMap,
    tripId: Long?
) {
    if (tripId == null) return

    val nodes = loadMemoryNodes(tripId)

    nodes.forEach {
        val latLng = LatLng(it.latitude, it.longitude)

        map.addMarker(
            MarkerOptions()
                .position(latLng)
                .title(it.text ?: "回忆")
        )
    }
}


private fun loadTrackPoints(tripId: Long)
        : List<com.example.lifetrace.data.database.entity.TrackPointEntity> {
    return emptyList()
}

private fun loadCurrentTripPoints()
        : List<com.example.lifetrace.data.database.entity.TrackPointEntity> {
    return emptyList()
}

private fun loadMemoryNodes(tripId: Long)
        : List<com.example.lifetrace.data.database.entity.MemoryNodeEntity> {
    return emptyList()
}

private fun renderAllTrips(map: AMap) {
    // TODO 所有轨迹
}