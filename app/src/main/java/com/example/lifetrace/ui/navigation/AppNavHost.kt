package com.example.lifetrace.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.lifetrace.data.database.repository.MemoryNodeRepository
import com.example.lifetrace.data.database.repository.TrackPointRepository
import com.example.lifetrace.data.database.repository.TripRepository
import com.example.lifetrace.ui.screen.HomeScreen
import com.example.lifetrace.viewmodel.HomeViewModel
import com.example.lifetrace.viewmodel.HomeViewModelFactory
import com.example.lifetrace.viewmodel.MapViewModel
import com.example.lifetrace.viewmodel.MapViewModelFactory

//导航控制器
// AppNavHost.kt 改造后
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val context = LocalContext.current

    // 1. 获取单例 Repository（全局唯一，仅创建一次）
    val tripRepository = TripRepository.getInstance(context)
    val memoryNodeRepository = MemoryNodeRepository.getInstance(context)
    val trackPointRepository = TrackPointRepository.getInstance(context)

    NavHost(
        navController = navController,
        startDestination = "homepage"
    ) {
        composable("homepage") {
            // 2. 创建 MapViewModel（生命周期托管）
            val mapViewModelFactory = MapViewModelFactory(
                trackPointRepository = trackPointRepository,
                memoryNodeRepository = memoryNodeRepository,
                tripRepository = tripRepository,
            )
            val mapViewModel = viewModel<MapViewModel>(factory = mapViewModelFactory)

            // 3. 创建 Factory 并传入所有依赖
            val homeViewModelFactory = HomeViewModelFactory(
                tripRepository = tripRepository,
                memoryNodeRepository = memoryNodeRepository,
                mapViewModel = mapViewModel,
                trackPointRepository = trackPointRepository
            )

            // 4. 用 Factory 创建 HomeViewModel（关键：生命周期托管）
            val homeViewModel = viewModel<HomeViewModel>(
                factory = homeViewModelFactory
            )

            // 5. 传递给 UI
            HomeScreen(viewModel = homeViewModel, mapViewModel = mapViewModel)
        }

        composable("detail") { /* 详情页逻辑 */ }
    }
}