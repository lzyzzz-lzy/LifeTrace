package com.example.lifetrace.viewmodel

import android.content.Context
import android.content.Intent
import android.icu.text.CaseMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lifetrace.data.database.entity.MemoryNodeEntity
import com.example.lifetrace.data.database.entity.MemoryType
import com.example.lifetrace.data.database.entity.TripEntity
import com.example.lifetrace.data.database.entity.TripStatus
import com.example.lifetrace.data.database.repository.MemoryNodeRepository
import com.example.lifetrace.data.database.repository.TripRepository
import com.example.lifetrace.service.TripRecordingService
import com.example.lifetrace.state.HomeUiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val tripRepository: TripRepository,
    private val memoryNodeRepository: MemoryNodeRepository,
    private val mapViewModel: MapViewModel,
): ViewModel() {
    //State机制规范管理，私有可变流，公开不可变流
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    private var timerJob: Job? = null

    //初始化
    init {
        restoreTripIfNeed()
    }

    //检查是否有未结束trip
    private fun restoreTripIfNeed() {
        viewModelScope.launch {
            val trip = tripRepository.getCurrentTrip()
            if(trip != null) {
                _uiState.value = HomeUiState(
                    activeTrip = trip,
                    isRecording = trip.status == TripStatus.RECORDING,
                    isPaused = trip.status == TripStatus.PAUSE,
                    isLoading = false,
                    timeRefreshTick = 0L // 新增
                )
                mapViewModel.onAppStart(hasActiveTrip = true)
            } else {
                _uiState.value = HomeUiState(
                    activeTrip = null,
                    isRecording = false,
                    isPaused = false,
                    isLoading = false,
                    timeRefreshTick = 0L // 新增
                )
                mapViewModel.onAppStart(hasActiveTrip = false)
            }
        }
    }

    //启动计时器
    private fun startTimer() {
        timerJob?.cancel()

        timerJob = viewModelScope.launch {
            var tick = 0L
            while (true) {
                delay(1000)
                tick++
                // 关键：更新 timeRefreshTick，值每次都变，必触发状态通知
                _uiState.update { it.copy(timeRefreshTick = tick) }
            }
        }
    }


    //开始新旅途
    fun startTrip(context: Context, title: String) {
        viewModelScope.launch {
            val trip = tripRepository.startNewTrip(title)
            _uiState.value = HomeUiState(
                activeTrip = trip,
                isRecording = true,
                isPaused = false,
                isLoading = false,
                timeRefreshTick = 0L // 新增
            )
            mapViewModel.returnToRecording()

            startRecordingService(context = context)  //开始定位

            startTimer()
        }
    }

    //暂停旅途
    fun pauseTrip(context: Context) {
        val trip = _uiState.value.activeTrip?: return //当前没有trip则不存在暂停
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val pauseTrip = trip.copy(
                accumulatedDuration = trip.accumulatedDuration +
                        (now - (trip.lastResumeTime ?: now)),
                lastResumeTime = null,
                status = TripStatus.PAUSE
            )
            tripRepository.updateTrip(pauseTrip)

            stopRecordingService(context = context) //暂停定位

            timerJob?.cancel()

            _uiState.value = _uiState.value.copy(
                activeTrip = pauseTrip,
                isRecording = false,
                isPaused = true,
                timeRefreshTick = _uiState.value.timeRefreshTick + 1 // 新增，确保暂停时刷新
            )
        }
    }

    //继续旅程
    // HomeViewModel.kt

    // 1. 恢复旅程时，确保 lastResumeTime 不为空
    fun resumeTrip(context: Context) {
        val trip = _uiState.value.activeTrip?: return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val resumeTrip = trip.copy(
                lastResumeTime = now,
                status = TripStatus.RECORDING
            )
            tripRepository.updateTrip(resumeTrip)

            startRecordingService(context = context)

            _uiState.value = _uiState.value.copy(
                activeTrip = resumeTrip,
                isRecording = true,
                isPaused = false,
                timeRefreshTick = _uiState.value.timeRefreshTick + 1 // 新增，确保继续时刷新
            )

            startTimer() // 重启定时器
        }
    }

    // 2. ViewModel 销毁时停止定时器，避免内存泄漏
    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
    }

    // 3. 结束旅程时停止定时器
    fun finishTrip(context: Context) {
        val trip = _uiState.value.activeTrip?: return
        viewModelScope.launch {
            val endTrip = trip.copy(
                status = TripStatus.FINISHED,
                endTime = System.currentTimeMillis()
            )
            tripRepository.updateTrip(endTrip)

            timerJob?.cancel() // 新增：停止定时器

            stopRecordingService(context = context)

            _uiState.value = _uiState.value.copy(
                activeTrip = null,
                isRecording = false,
                isPaused = false,
                timeRefreshTick = 0L // 新增
            )
            mapViewModel.enterExplore()
        }
    }

    fun togglePanel() {
        _uiState.value = _uiState.value.copy(
            isPanelExpanded = !_uiState.value.isPanelExpanded
        )
    }

    fun addMemoryNode() {
        val trip = _uiState.value.activeTrip ?: return

        viewModelScope.launch {
            val node = MemoryNodeEntity(
                tripId = trip.tripId,
                latitude = 0.0, // TODO 定位
                longitude = 0.0,
                timestamp = System.currentTimeMillis(),
                text = "",
                contentUrl = "",
                type = MemoryType.TEXT
            )
            memoryNodeRepository.insertMemoryNode(node)
        }
    }

    fun startRecordingService(context: Context) {
        val intent = Intent(context, TripRecordingService::class.java)
        context.startForegroundService(intent)
    }

    fun stopRecordingService(context: Context) {
        val intent = Intent(context, TripRecordingService::class.java)
        context.stopService(intent)
    }

    fun onMarkPoint() {

    }
}