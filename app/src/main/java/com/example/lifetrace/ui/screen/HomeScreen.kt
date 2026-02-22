package com.example.lifetrace.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.lifetrace.state.HomeUiState
import com.example.lifetrace.ui.components.BottomControlPanel
import com.example.lifetrace.ui.components.TopBar
import com.example.lifetrace.ui.map.LifeTraceMap
import com.example.lifetrace.viewmodel.HomeViewModel
import com.example.lifetrace.viewmodel.MapViewModel

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    mapViewModel: MapViewModel,
) {
    val context = LocalContext.current.applicationContext
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val mapUiState by mapViewModel.uiState.collectAsStateWithLifecycle()

    // 1. 封装 onStart：传入 Context + 用户输入的 Trip 名称
    val onStartTrip = { tripTitle: String ->
        viewModel.startTrip(context = context, title = tripTitle)
    }

    // 2. 封装其他操作：传入 Context
    val onPauseTrip = { viewModel.pauseTrip(context) }
    val onResumeTrip = { viewModel.resumeTrip(context) }
    val onFinishTrip = { viewModel.finishTrip(context) }

    // 3. 封装 onAddMemory：获取地图定位后调用
    var currentLat by remember { mutableStateOf(0.0) }
    var currentLng by remember { mutableStateOf(0.0) }

    val onAddMemoryNode = {
        if (currentLat != 0.0 && currentLng != 0.0) {
            // viewModel.addMemoryNode(currentLat, currentLng)
        }
    }

    val onMapMoved = { mapViewModel.onMapMovedWhileRecording() }

    Box(modifier = Modifier.fillMaxSize()) {
        // 地图组件：更新当前定位

        LifeTraceMap(
            modifier = Modifier.fillMaxSize(),
            mapUiState = mapUiState,
            onMapMoved = onMapMoved,
            onCurrentLocationChanged = { lat, lng ->
                currentLat = lat
                currentLng = lng
            }
        )

        TopBar(modifier = Modifier.align(Alignment.TopCenter))

        // 你的 BottomControlPanel：仅传封装后的回调，样式完全不变
        BottomControlPanel(
            uiState = uiState,
            onStart = onStartTrip,       // 接收用户输入的 Trip 名称
            onPause = onPauseTrip,
            onResume = onResumeTrip,
            onFinish = onFinishTrip,
            onAddMemory = onAddMemoryNode, // 封装定位的回调
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}