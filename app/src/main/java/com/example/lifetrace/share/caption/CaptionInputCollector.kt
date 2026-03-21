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
import com.example.lifetrace.share.model.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * 阶段 A (Step 1): 输入收集服务
 *
 * 职责：
 * 1. 收集 Trip 基础信息
 * 2. 收集记忆点和附件
 * 3. 构建候选文字列表
 * 4. 构建候选图片列表
 * 5. 构建 Trip 统计
 * 6. 输出标准化的 TripCaptionInputContext
 */
class CaptionInputCollector(
    private val context: Context,
    private val tripRepository: TripRepository,
    private val memoryNodeRepository: MemoryNodeRepository,
    private val attachmentRepository: MemoryAttachmentRepository,
    private val tripStatsBuilder: TripStatsBuilder = TripStatsBuilder
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
        Log.d(TAG, "=== Step 1: 输入收集开始 ===")
        Log.d(TAG, "tripId: $tripId, selectedItems: ${selectedItemIds.size}, style: ${style.displayName}")

        try {
            // 1. 收集 Trip 基础信息
            val trip = tripRepository.getTripById(tripId)
                ?: throw IllegalStateException("Trip not found: $tripId")
            Log.d(TAG, "[Trip] title=${trip.title}, startTime=${trip.startTime}")

            // 2. 收集记忆点
            val nodes = memoryNodeRepository.getMemoryNodesForTrip(tripId)
            Log.d(TAG, "[记忆点] 数量: ${nodes.size}")

            // 3. 收集所有附件（用于构建图片-记忆点映射）
            val allAttachments = mutableListOf<MemoryAttachmentEntity>()
            val memoryToAttachmentsMap = mutableMapOf<Long, MutableList<MemoryAttachmentEntity>>()
            nodes.forEach { node ->
                val attachments = try {
                    attachmentRepository.getAttachmentsForNode(node.id)
                } catch (e: Exception) {
                    emptyList()
                }
                if (attachments.isNotEmpty()) {
                    memoryToAttachmentsMap[node.id] = attachments.toMutableList()
                    allAttachments.addAll(attachments)
                }
            }
            Log.d(TAG, "[附件] 总数: ${allAttachments.size}")

            // 4. 构建候选文字列表
            val candidateTexts = buildCandidateTexts(trip, nodes, memoryToAttachmentsMap)
            Log.d(TAG, "[候选文字] 数量: ${candidateTexts.size}")
            candidateTexts.forEach { item ->
                Log.d(TAG, "  - ${item.sourceType.name}: \"${item.preview()}\"")
            }

            // 5. 构建候选图片列表
            val candidateImages = buildCandidateImages(nodes, selectedItemIds, memoryToAttachmentsMap)
            Log.d(TAG, "[候选图片] 数量: ${candidateImages.size}")

            // 6. 构建 Trip 统计
            val stats = tripStatsBuilder.build(trip, nodes, candidateImages)
            Log.d(TAG, "[Trip统计] duration: ${stats.durationText}, 日期: ${stats.dateText}, 时间范围: ${stats.timeRangeText}")
            Log.d(TAG, "[Trip统计] 记忆点: ${stats.memoryNodeCount}, 图片: ${stats.selectedImageCount}")

            // 7. 构建图片-记忆点映射
            val imageToMemoryMap = mutableMapOf<String, Long>()
            val memoryToImageIdsMap = mutableMapOf<Long, MutableList<String>>()
            candidateImages.forEach { image ->
                val memoryId = image.memoryId
                if (memoryId != null) {
                    imageToMemoryMap[image.id] = memoryId
                    memoryToImageIdsMap.getOrPut(memoryId) { mutableListOf() }.add(image.id)
                }
            }
            Log.d(TAG, "[映射] 图片->记忆点: ${imageToMemoryMap.size} 个, 记忆点->图片: ${memoryToImageIdsMap.size} 个")

            // 8. 构建最终上下文
            val resultContext = TripCaptionInputContext(
                tripId = tripId,
                tripTitle = trip.title,
                tripStartTime = trip.startTime,
                tripEndTime = trip.endTime,
                candidateTexts = candidateTexts,
                candidateImages = candidateImages,
                style = style,
                tripDurationText = stats.durationText,
                tripDateText = stats.dateText,
                tripTimeRangeText = stats.timeRangeText,
                memoryNodeCount = stats.memoryNodeCount,
                selectedImageCount = candidateImages.size,
                selectedVideoCount = stats.selectedVideoCount,
                imageToMemoryMap = imageToMemoryMap,
                memoryToImageIdsMap = memoryToImageIdsMap,
                collectorDebugSummary = buildDebugSummary(trip, nodes, candidateTexts, candidateImages, stats)
            )

            Log.d(TAG, "=== Step 1: 输入收集完成 ===")
            Log.d(TAG, "有效文字: ${resultContext.validTextCount()}, 选中图片: ${resultContext.selectedImageCount}")

            return resultContext

        } catch (e: Exception) {
            Log.e(TAG, "Step 1 输入收集失败", e)
            throw e
        }
    }

    /**
     * 构建候选文字列表
     */
    private fun buildCandidateTexts(
        trip: TripEntity,
        nodes: List<MemoryNodeEntity>,
        memoryToAttachmentsMap: Map<Long, List<MemoryAttachmentEntity>>
    ): List<CandidateTextItem> {
        val texts = mutableListOf<CandidateTextItem>()

        // 1. 添加 Trip 标题
        if (trip.title.isNotBlank()) {
            texts.add(
                CandidateTextItem(
                    id = "trip_title_${trip.tripId}",
                    sourceType = TextSourceType.TRIP_TITLE,
                    text = trip.title.trim(),
                    normalizedText = normalizeText(trip.title),
                    timeHint = trip.startTime
                )
            )
        }

        // 2. 添加记忆点备注
        nodes.forEach { node ->
            if (!node.text.isNullOrBlank()) {
                val normalizedText = normalizeText(node.text)
                if (normalizedText.isNotBlank()) {
                    // 计算关联图片数量
                    val relatedImageCount = memoryToAttachmentsMap[node.id]
                        ?.count { it.type == AttachmentType.PHOTO } ?: 0

                    texts.add(
                        CandidateTextItem(
                            id = "memory_note_${node.id}",
                            sourceType = TextSourceType.MEMORY_NOTE,
                            sourceMemoryId = node.id,
                            text = node.text.trim(),
                            normalizedText = normalizedText,
                            timeHint = node.timestamp,
                            relatedImageCount = relatedImageCount,
                            isFromSelectedMediaMemory = true,
                            sortWeight = 0.5f
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
    private fun buildCandidateImages(
        nodes: List<MemoryNodeEntity>,
        selectedItemIds: Set<String>,
        memoryToAttachmentsMap: Map<Long, List<MemoryAttachmentEntity>>
    ): List<CandidateImageItem> {
        val images = mutableListOf<CandidateImageItem>()

        nodes.forEach { node ->
            val attachments = memoryToAttachmentsMap[node.id] ?: emptyList()
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
     */
    private fun normalizeText(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text.trim()
            .replace(Regex("\\s+"), " ")
            .replace("\n", " ")
            .trim()
    }

    /**
     * 构建调试摘要
     */
    private fun buildDebugSummary(
        trip: TripEntity,
        nodes: List<MemoryNodeEntity>,
        texts: List<CandidateTextItem>,
        images: List<CandidateImageItem>,
        stats: TripStatsBuilder.TripStats
    ): String {
        val sb = StringBuilder()
        sb.append("=== 输入收集调试信息 ===\n")
        sb.append("Trip: ${trip.title ?: "无标题"}\n")
        sb.append("记忆点: ${nodes.size} 个\n")
        sb.append("附件: ${stats.totalAttachmentCount} 个\n")
        sb.append("选中图片: ${stats.selectedImageCount} 张\n")
        texts.forEach { text ->
            sb.append("  文字: [${text.sourceType.name}] \"${text.preview()}\" (关联图片: ${text.relatedImageCount})\n")
        }
        sb.append("\n图片: ${images.size} 张\n")
        images.forEach { image ->
            sb.append("  - [${image.memoryId ?: "无"}] ${image.uri}\n")
        }
        return sb.toString()
    }
}
