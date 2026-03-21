package com.example.lifetrace.share.caption

import android.util.Log
import com.example.lifetrace.share.model.CandidateTextItem
import com.example.lifetrace.share.model.DuplicateMergeTrace
import com.example.lifetrace.share.model.RuleDroppedItem
import com.example.lifetrace.share.model.PreFilteredTextCollection

/**
 * 文本去重器
 * 负责对文本进行去重处理
 *
 * 职责：
 * 1. 完全重复去重
 * 2. 忽略空格/标点的归一化去重
 * 3. 短句包含关系去重
 */
object TextDeduplicator {

    private const val TAG = "TextDeduplicator"

    /**
     * 去重结果
     */
    data class DeduplicateResult(
        val deduplicated: List<CandidateTextItem>,
        val dropped: List<RuleDroppedItem>,
        val mergedTraces: List<DuplicateMergeTrace>
    )

    /**
     * 对文本列表进行去重
     *
     * @param items 待去重的文本列表
     * @return 去重结果
     */
    fun deduplicate(items: List<CandidateTextItem>): DeduplicateResult {
        Log.d(TAG, "开始去重，输入数量: ${items.size}")

        val deduplicated = mutableListOf<CandidateTextItem>()
        val dropped = mutableListOf<RuleDroppedItem>()
        val mergedTraces = mutableListOf<DuplicateMergeTrace>()

        // 按标准化文本分组
        val groupedByNormalized = items.groupBy { it.normalizedText.lowercase().trim() }

        groupedByNormalized.forEach { (normalizedText, group) ->
            if (group.size == 1) {
                // 唯一项，直接保留
                deduplicated.add(group.first())
            } else {
                // 有重复，选择最佳的一项保留
                val kept = selectBestItem(group)
                deduplicated.add(kept)

                // 记录被合并的项
                val mergedIds = group.filter { it.id != kept.id }.map { it.id }
                if (mergedIds.isNotEmpty()) {
                    mergedTraces.add(
                        DuplicateMergeTrace(
                            keptId = kept.id,
                            mergedIds = mergedIds,
                            reason = "重复文本合并"
                        )
                    )

                    // 记录被丢弃的项
                    group.filter { it.id != kept.id }.forEach { item ->
                        dropped.add(
                            RuleDroppedItem(
                                item = item,
                                dropReason = "与 \"${kept.preview()}\" 重复",
                                stage = "DUPLICATE"
                            )
                        )
                    }
                }
            }
        }

        // 包含关系去重：如果一个短文本是另一个长文本的子串，丢弃短文本
        val afterInclusionFilter = filterIncludedTexts(deduplicated, dropped, mergedTraces)

        Log.d(TAG, "去重完成: 保留 ${afterInclusionFilter.size} 项, 丢弃 ${dropped.size} 项, 合并 ${mergedTraces.size} 组")

        return DeduplicateResult(
            deduplicated = afterInclusionFilter,
            dropped = dropped,
            mergedTraces = mergedTraces
        )
    }

    /**
     * 选择最佳项（优先选择长度最长、关联图片最多的）
     */
    private fun selectBestItem(items: List<CandidateTextItem>): CandidateTextItem {
        return items.maxWithOrNull(
            compareBy<CandidateTextItem> { it.text.length }
                .thenBy { it.relatedImageCount }
                .thenBy { it.sortWeight }
        ) ?: items.first()
    }

    /**
     * 过滤被包含的短文本
     */
    private fun filterIncludedTexts(
        items: List<CandidateTextItem>,
        dropped: MutableList<RuleDroppedItem>,
        mergedTraces: MutableList<DuplicateMergeTrace>
    ): List<CandidateTextItem> {
        val result = mutableListOf<CandidateTextItem>()
        val processedIds = mutableSetOf<String>()

        items.forEach { item ->
            if (item.id in processedIds) return@forEach

            // 检查是否被其他更长的文本包含
            val containingItem = items.find { other ->
                other.id != item.id &&
                other.id !in processedIds &&
                other.text.length > item.text.length &&
                other.text.contains(item.text, ignoreCase = true)
            }

            if (containingItem != null) {
                // 当前项被包含，丢弃
                dropped.add(
                    RuleDroppedItem(
                        item = item,
                        dropReason = "被 \"${containingItem.preview()}\" 包含",
                        stage = "INCLUSION"
                    )
                )
                processedIds.add(item.id)
            } else {
                result.add(item)
                processedIds.add(item.id)
            }
        }

        return result
    }
}
