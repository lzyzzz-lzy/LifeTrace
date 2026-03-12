package com.example.lifetrace.share.caption

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.lifetrace.data.database.entity.AttachmentType
import com.example.lifetrace.data.database.entity.MemoryAttachmentEntity
import com.example.lifetrace.data.database.entity.MemoryNodeEntity
import com.example.lifetrace.data.database.entity.TripEntity
import com.example.lifetrace.data.database.repository.MemoryAttachmentRepository
import com.example.lifetrace.data.database.repository.MemoryNodeRepository
import com.example.lifetrace.data.database.repository.TripRepository
import com.example.lifetrace.service.CaptionStyle
import com.example.lifetrace.share.model.CandidateImageItem
import com.example.lifetrace.share.model.CandidateTextItem
import com.example.lifetrace.share.model.TextSourceType
import com.example.lifetrace.share.model.TripCaptionInputContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 阶段 A: 输入收集与标准化服务
 *
 * 职责：
 * 1. 收集 Trip 基础信息
 * 2. 收集记忆点备注
 * 3. 收集用户选中的图片
 * 4. 数据清洗和标准化
 *
 * 日志 TAG: CaptionInputCollector
 */
class CaptionInputCollector(
    private val context: Context,
    private val tripRepository: TripRepository,
    private val memoryNodeRepository: MemoryNodeRepository,
    private val attachmentRepository: MemoryAttachmentRepository
) {
    companion object {
        private const val TAG = "CaptionInputCollector"
    }

    /**
     * 收集并标准化输入
     * @param tripId 旅行 ID
     * @param selectedItemIds 用户选中的项目 ID 集合
     * @param style 文案风格
     * @return 标准化的输入上下文
     */
    suspend fun collect(
        tripId: Long,
        selectedItemIds: Set<String>,
        style: CaptionStyle
    ): TripCaptionInputContext {
        Log.d(TAG, "=== 阶段 A: 输入收集开始 ===")
        Log.d(TAG, "tripId: $tripId, selectedItems: ${selectedItemIds.size}  style: ${style.displayName}")

        try {
            // 1. 收集 Trip 基础信息
            val trip = tripRepository.getTripById(tripId)
            Log.d(TAG, "[Trip] title=${trip?.title} startTime=${trip?.startTime}")

            // 2. 收集记忆点和附件
            val nodes = memoryNodeRepository.getMemoryNodesForTrip(tripId)
            Log.d(TAG, "[记忆点] 数量: ${nodes.size}")

            // 3. 构建候选文字列表
            val candidateTexts = buildCandidateTexts(trip, nodes)
            Log.d(TAG, "[候选文字] 数量: ${candidateTexts.size}")
            candidateTexts.forEach { item ->
                Log.d(TAG, "  - ${item.sourceType.name}: \"${item.preview()}\"")
            }

            // 4. 构建候选图片列表
            val candidateImages = buildCandidateImages(nodes, selectedItemIds)
            Log.d(TAG, "[候选图片] 数量: ${candidateImages.size}")

            // 5. 构建上下文
            val context = TripCaptionInputContext(
                tripId = tripId,
                tripTitle = trip?.title,
                tripStartTime = trip?.startTime ?: 0L,
                tripEndTime = trip?.endTime,
                candidateTexts = candidateTexts,
                candidateImages = candidateImages,
                style = style
            )

            Log.d(TAG, "=== 阶段 A: 输入收集完成 ===")
            Log.d(TAG, "有效文字: ${context.validTextCount()}, 选中图片: ${context.selectedImageCount()}")

            return context

        } catch (e: Exception) {
            Log.e(TAG, "阶段 A 输入收集失败", e)
            throw e
        }
    }

    /**
     * 构建候选文字列表
     */
    private fun buildCandidateTexts(
        trip: TripEntity?,
        nodes: List<MemoryNodeEntity>
    ): List<CandidateTextItem> {
        val texts = mutableListOf<CandidateTextItem>()

        // 添加 Trip 标题
        if (trip != null && trip.title.isNotBlank()) {
            texts.add(
                CandidateTextItem(
                    id = "trip_title_${trip.tripId}",
                    sourceType = TextSourceType.TRIP_TITLE,
                    text = trip.title.trim(),
                    timeHint = trip.startTime
                )
            )
        }

        // 添加记忆点备注
        nodes.forEach { node ->
            if (!node.text.isNullOrBlank()) {
                // 标准化文本
                val normalizedText = normalizeText(node.text)
                if (normalizedText.isNotBlank()) {
                    texts.add(
                        CandidateTextItem(
                            id = "memory_note_${node.id}",
                            sourceType = TextSourceType.MEMORY_NOTE,
                            sourceMemoryId = node.id,
                            text = normalizedText,
                            timeHint = node.timestamp,
                            relatedImageCount = 0 // 将在后面异步填充
                        )
                    )
                }
            }
        }

        return texts
    }

    /**
     * 构建候选图片列表
     */
    private suspend fun buildCandidateImages(
        nodes: List<MemoryNodeEntity>,
        selectedItemIds: Set<String>
    ): List<CandidateImageItem> {
        val images = mutableListOf<CandidateImageItem>()

        nodes.forEach { node ->
            val attachments = try {
                attachmentRepository.getAttachmentsForNode(node.id)
            } catch (e: Exception) {
                emptyList()
            }

            attachments
                .filter { it.type == AttachmentType.PHOTO }
                .filter { "${it.id}" in selectedItemIds }
                .forEach { attachment ->
                    images.add(
                        CandidateImageItem(
                            id = "${attachment.id}",
                            uri = Uri.parse(attachment.uri),
                            memoryId = node.id,
                            sortTime = node.timestamp,
                            mimeType = "image/jpeg"
                        )
                    )
                }
        }

        // 按时间排序
        return images.sortedBy { it.sortTime }
    }

    /**
     * 标准化文本
     * - trim 首尾空格
     * - 压缩连续空格
     * - 去掉纯换行
     */
    private fun normalizeText(text: String?): String {
        // let 会自动处理空值，且 lambda 内的 text 自动为非空
        return text?.let {
            it.trim()                          // 去首尾空格
                .replace(Regex("\\s+"), " ")   // 压缩连续空白为单空格
                .replace("\n", " ")             // 换行转空格
                .trim()
        } ?: "" // 若 text 为 null/空白，直接返回空字符串
    }

    /**
     * 格式化时间（用于调试）
     */
    private fun formatTime(timestamp: Long?): String {
        if (timestamp == null || timestamp <= 0) return "N/A"
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

/**
 * String 扩展函数
 */
private fun String?.isNullOrBlank(): Boolean = this == null || this.isBlank()
