package com.example.lifetrace.share.caption

import android.util.Log
import com.example.lifetrace.data.database.entity.MemoryNodeEntity
import com.example.lifetrace.data.database.entity.TripEntity
import com.example.lifetrace.share.model.CandidateImageItem
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Trip 统计信息构建器
 * 负责计算 Trip 的各种统计信息
 */
object TripStatsBuilder {

    private const val TAG = "TripStatsBuilder"

    /**
     * Trip 统计结果
     */
    data class TripStats(
        val durationText: String?,           // 时长文本（如"3天2夜"）
        val dateText: String?,               // 日期文本（如"2024年3月15日"）
        val timeRangeText: String?,          // 时间范围文本（如"上午9:00 - 下午5:00"）
        val memoryNodeCount: Int,            // 记忆点数量
        val selectedImageCount: Int,         // 选中图片数量
        val selectedVideoCount: Int,         // 选中视频数量
        val totalAttachmentCount: Int        // 总附件数量
    )

    /**
     * 构建 Trip 统计信息
     *
     * @param trip Trip 实体
     * @param memoryNodes 所有记忆点
     * @param candidateImages 候选图片列表
     * @return Trip 统计结果
     */
    fun build(
        trip: TripEntity,
        memoryNodes: List<MemoryNodeEntity>,
        candidateImages: List<CandidateImageItem>
    ): TripStats {
        val memoryNodeCount = memoryNodes.size
        val selectedImageCount = candidateImages.size

        // 计算视频数量（暂时为0，可根据实际需求调整）
        val selectedVideoCount = 0

        // 总附件数量（暂时等于选中图片数）
        val totalAttachmentCount = selectedImageCount

        // 计算时长文本
        val durationText = buildDurationText(trip.startTime, trip.endTime)

        // 计算日期文本
        val dateText = buildDateText(trip.startTime)

        // 计算时间范围文本
        val timeRangeText = buildTimeRangeText(trip.startTime, trip.endTime)

        Log.d(TAG, "TripStats 构建: memoryNodes=$memoryNodeCount, images=$selectedImageCount, duration=$durationText, date=$dateText, timeRange=$timeRangeText")

        return TripStats(
            durationText = durationText,
            dateText = dateText,
            timeRangeText = timeRangeText,
            memoryNodeCount = memoryNodeCount,
            selectedImageCount = selectedImageCount,
            selectedVideoCount = selectedVideoCount,
            totalAttachmentCount = totalAttachmentCount
        )
    }

    /**
     * 构建时长文本
     * 如："3天2夜"、"5小时"、"进行中"
     */
    private fun buildDurationText(startTime: Long, endTime: Long?): String? {
        if (startTime <= 0) return null

        val effectiveEndTime = endTime ?: System.currentTimeMillis()
        val durationMs = effectiveEndTime - startTime

        if (durationMs <= 0) return null

        val days = TimeUnit.MILLISECONDS.toDays(durationMs)
        val hours = TimeUnit.MILLISECONDS.toHours(durationMs) % 24

        return when {
            days == 0L && hours < 1 -> "不到1小时"
            days == 0L && hours < 24 -> "${hours}小时"
            days > 0 -> {
                val nights = if (hours >= 12) (days + 1).toInt() else days.toInt()
                if (nights > days.toInt()) {
                    "${days}天${nights}夜"
                } else {
                    "${days}天"
                }
            }
            else -> "${hours}小时"
        }
    }

    /**
     * 构建日期文本
     * 如："2024年3月15日"、"3月15日"
     */
    private fun buildDateText(timestamp: Long): String? {
        if (timestamp <= 0) return null

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp

        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH) + 1  // Calendar.MONTH 是 0-based
        val day = calendar.get(Calendar.DAY_OF_MONTH)

        val currentYear = Calendar.getInstance().get(Calendar.YEAR)

        return if (year == currentYear) {
            "${month}月${day}日"
        } else {
            "${year}年${month}月${day}日"
        }
    }

    /**
     * 构建时间范围文本
     * 如："上午9:00 - 下午5:00"
     */
    private fun buildTimeRangeText(startTime: Long, endTime: Long?): String? {
        if (startTime <= 0) return null

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val startStr = timeFormat.format(Date(startTime))

        val startHour = SimpleDateFormat("HH", Locale.getDefault()).format(Date(startTime)).toIntOrNull() ?: 0
        val startPeriod = if (startHour < 12) "上午" else "下午"

        if (endTime == null || endTime <= 0) {
            return "${startPeriod}${startStr}"
        }

        val endStr = timeFormat.format(Date(endTime))
        val endHour = SimpleDateFormat("HH", Locale.getDefault()).format(Date(endTime)).toIntOrNull() ?: 0
        val endPeriod = if (endHour < 12) "上午" else "下午"

        return "${startPeriod}${startStr} - ${endPeriod}${endStr}"
    }
}
