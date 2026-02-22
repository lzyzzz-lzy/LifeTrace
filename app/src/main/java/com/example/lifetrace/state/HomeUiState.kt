package com.example.lifetrace.state

import com.example.lifetrace.data.database.entity.TripEntity

data class HomeUiState(
    val activeTrip: TripEntity? = null,
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val isLoading: Boolean = true,
    val isPanelExpanded: Boolean = false,
    val timeRefreshTick: Long = 0L
) {
    val durationText: String
        get() {
            val trip = activeTrip ?: return "00:00"
            val base = trip.accumulatedDuration ?: 0L
            val running = if (isRecording) {
                val resumeTime = trip.lastResumeTime ?: trip.startTime ?: System.currentTimeMillis()
                System.currentTimeMillis() - resumeTime
            } else 0L
            val totalMs = Math.max(0L, base + running)
            return formatDuration(totalMs)
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