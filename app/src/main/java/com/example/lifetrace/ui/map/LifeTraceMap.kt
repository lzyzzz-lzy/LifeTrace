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
import com.amap.api.maps.model.CameraPosition
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.MarkerOptions
import com.amap.api.maps.model.PolylineOptions
import com.example.lifetrace.data.database.entity.MemoryNodeEntity
import com.example.lifetrace.data.database.entity.TrackPointEntity
import com.example.lifetrace.data.database.entity.TripEntity
import com.example.lifetrace.state.MapMode
import com.example.lifetrace.state.MapUiState

@Composable
fun LifeTraceMap(
    mapUiState: MapUiState,
    onMapMoved: (Boolean, Float) -> Unit,
    onExploreTripClicked: (Long) -> Unit,
    onMapLongPressAddMemory: (Double, Double) -> Unit,
    onCurrentLocationChanged: (Double, Double) -> Unit,
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

    var lastLat by remember { mutableStateOf(0.0) }
    var lastLng by remember { mutableStateOf(0.0) }
    var lastCameraFitSignature by remember { mutableStateOf("") }

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
    ) {
        setupMap(
            aMap = aMap,
            onMapMoved = onMapMoved,
            onExploreTripClicked = onExploreTripClicked,
            onMapLongPressAddMemory = onMapLongPressAddMemory,
            polylineIdToTripId = polylineIdToTripId,
            clickablePolylineIds = clickablePolylineIds,
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
        renderMapState(
            map = aMap,
            state = mapUiState,
            polylineIdToTripId = polylineIdToTripId,
            clickablePolylineIds = clickablePolylineIds,
        )

        aMap.mapType = if (isNightMode) AMap.MAP_TYPE_NIGHT else AMap.MAP_TYPE_NORMAL

        val cameraFitSignature = buildCameraFitSignature(mapUiState)
        if (cameraFitSignature != lastCameraFitSignature) {
            lastCameraFitSignature = cameraFitSignature
            fitCameraForState(aMap, mapUiState)
        }
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}

private fun setupMap(
    aMap: AMap,
    onMapMoved: (Boolean, Float) -> Unit,
    onExploreTripClicked: (Long) -> Unit,
    onMapLongPressAddMemory: (Double, Double) -> Unit,
    polylineIdToTripId: Map<String, Long>,
    clickablePolylineIds: Set<String>,
) {
    aMap.uiSettings.apply {
        isZoomControlsEnabled = false
        isMyLocationButtonEnabled = false
        isCompassEnabled = false
        isScaleControlsEnabled = false
    }

    aMap.isMyLocationEnabled = true
    // AMap 没有 isMapTextEnable 这种开关；默认会显示底图文字，所以不需要设置

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

private fun buildCameraFitSignature(state: MapUiState): String {
    return when (state.mode) {
        MapMode.EXPLORE -> {
            val shape = state.allTripsTrackPoints.joinToString(separator = "|") { points ->
                val first = points.firstOrNull()
                val last = points.lastOrNull()
                "${points.size}:${first?.latitude},${first?.longitude}:${last?.latitude},${last?.longitude}"
            }
            "EXPLORE:$shape"
        }

        MapMode.MEMORY -> {
            val first = state.focusedTripTrackPoints.firstOrNull()
            val last = state.focusedTripTrackPoints.lastOrNull()
            "MEMORY:${state.focusedTrip?.tripId}:${state.focusedTripTrackPoints.size}:${first?.latitude},${first?.longitude}:${last?.latitude},${last?.longitude}"
        }

        else -> state.mode.name
    }
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

private fun renderMapState(
    map: AMap,
    state: MapUiState,
    polylineIdToTripId: MutableMap<String, Long>,
    clickablePolylineIds: MutableSet<String>,
) {
    map.clear()
    // map.clear() 会把所有 polyline/marker 清掉；对应映射也要清理
    polylineIdToTripId.clear()
    clickablePolylineIds.clear()

    when (state.mode) {
        MapMode.EXPLORE -> renderAllTrips(
            map = map,
            allTrips = state.allTrips,
            allTripsTrackPoints = state.allTripsTrackPoints,
            polylineIdToTripId = polylineIdToTripId,
            clickablePolylineIds = clickablePolylineIds,
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
                    width = 8f,
                    clickable = false,
                )
            }

            renderTrackPoints(
                map = map,
                points = state.focusedTripTrackPoints,
                color = 0xFF2E7D32.toInt(),
                width = 14f,
                clickable = false,
                tripId = null,
                polylineIdToTripId = polylineIdToTripId,
                clickablePolylineIds = clickablePolylineIds,
            )

            renderMemoryNodesByZoom(map, state.focusedTripMemoryNodes, state.zoomLevel)
        }

        MapMode.RECORDING -> {
            renderTrackPoints(
                map = map,
                points = state.currentTrackPoints,
                color = 0xFF2196F3.toInt(),
                width = 14f,
                clickable = false,
                tripId = null,
                polylineIdToTripId = polylineIdToTripId,
                clickablePolylineIds = clickablePolylineIds,
            )
            if (state.followUser) followUser(map)
        }

        MapMode.RECORDING_MEMORY -> {
            renderTrackPoints(
                map = map,
                points = state.currentTrackPoints,
                color = 0xFF2196F3.toInt(),
                width = 14f,
                clickable = false,
                tripId = null,
                polylineIdToTripId = polylineIdToTripId,
                clickablePolylineIds = clickablePolylineIds,
            )
            renderMemoryNodesByZoom(map, state.focusedTripMemoryNodes, state.zoomLevel)
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
) {
    if (points.size < 2) return

    val latLngs = points.map { LatLng(it.latitude, it.longitude) }
    val polyline = map.addPolyline(
        PolylineOptions()
            .addAll(latLngs)
            .width(width)
            .color(color),
    )

    // AMap 没有 polyline.object / polyline.isClickable，自己做映射
    val id = polyline.id
    if (tripId != null) {
        polylineIdToTripId[id] = tripId
    }
    if (clickable) {
        clickablePolylineIds.add(id)
    }
}

private fun renderMemoryNodesByZoom(
    map: AMap,
    nodes: List<MemoryNodeEntity>,
    zoomLevel: Float,
) {
    when {
        zoomLevel < 14f -> return
        zoomLevel > 16f -> renderMemoryNodes(map, nodes)
        else -> renderMemoryNodeClusters(map, nodes)
    }
}

private fun renderMemoryNodes(map: AMap, nodes: List<MemoryNodeEntity>) {
    nodes.forEach {
        map.addMarker(
            MarkerOptions()
                .position(LatLng(it.latitude, it.longitude))
                .title(it.text ?: "回忆"),
        )
    }
}

private fun renderMemoryNodeClusters(
    map: AMap,
    nodes: List<MemoryNodeEntity>,
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

        map.addMarker(
            MarkerOptions()
                .position(LatLng(centerLat, centerLng))
                .title(title),
        )
    }
}

private fun renderAllTrips(
    map: AMap,
    allTrips: List<TripEntity>,
    allTripsTrackPoints: List<List<TrackPointEntity>>,
    polylineIdToTripId: MutableMap<String, Long>,
    clickablePolylineIds: MutableSet<String>,
    color: Int = 0x884CAF50.toInt(),
    width: Float = 10f,
    clickable: Boolean = true,
) {
    allTripsTrackPoints.forEachIndexed { index, points ->
        val tripId = allTrips.getOrNull(index)?.tripId ?: return@forEachIndexed
        renderTrackPoints(
            map = map,
            points = points,
            color = color,
            width = width,
            clickable = clickable,
            tripId = tripId,
            polylineIdToTripId = polylineIdToTripId,
            clickablePolylineIds = clickablePolylineIds,
        )
    }
}