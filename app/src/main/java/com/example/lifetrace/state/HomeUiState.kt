package com.example.lifetrace.state

import com.example.lifetrace.data.database.entity.TripEntity

data class HomeUiState(
    val activeTrip: TripEntity? = null,
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val isLoading: Boolean = true,
    val isPanelExpanded: Boolean = false,
    val timeRefreshTick: Long = 0L,
    val distanceMeters: Float = 0f,
    // 回忆编辑相关
    val showMemoryEditor: Boolean = false,
) {
    val durationMillis: Long
        get() {
            val trip = activeTrip ?: return 0L
            val base = trip.accumulatedDuration
            val running = if (isRecording) {
                val resumeTime = trip.lastResumeTime ?: trip.startTime
                System.currentTimeMillis() - resumeTime
            } else 0L
            return Math.max(0L, base + running)
        }

    val durationText: String
        get() = formatDuration(durationMillis)

    val distanceText: String
        get() {
            return if (distanceMeters < 1000f) {
                String.format("%.0f m", distanceMeters)
            } else {
                String.format("%.2f km", distanceMeters / 1000f)
            }
        }

    val averageSpeedText: String
        get() {
            if (durationMillis <= 0L || distanceMeters <= 0f) return "0.0 km/h"
            val hours = durationMillis / 3_600_000f
            if (hours <= 0f) return "0.0 km/h"
            val speed = (distanceMeters / 1000f) / hours
            return String.format("%.1f km/h", speed)
        }

    companion object {
        /**
         * 进阶版时长格式化：
         * - <1小时 → MM:SS（如 45:30）
         * - 1小时~24小时 → HH:MM:SS（如 01:20:15、12:30:45）
         * - ≥24小时 → DD:HH:MM:SS（如 02:08:30:15 表示2天8小时30分15秒）
         */
        fun formatDuration(ms: Long): String {
            val totalSeconds = ms / 1000
            val days = totalSeconds / 86400 // 1天=86400秒
            val hours = (totalSeconds % 86400) / 3600
            val minutes = (totalSeconds % 3600) / 60
            val seconds = totalSeconds % 60

            return when {
                days > 0 -> {
                    // 有天数，显示 DD:HH:MM:SS
                    String.format("%02d:%02d:%02d:%02d", days, hours, minutes, seconds)
                }
                hours > 0 -> {
                    // 有小时数，显示 HH:MM:SS
                    String.format("%02d:%02d:%02d", hours, minutes, seconds)
                }
                else -> {
                    // 无小时数，显示 MM:SS
                    String.format("%02d:%02d", minutes, seconds)
                }
            }
        }
    }
}