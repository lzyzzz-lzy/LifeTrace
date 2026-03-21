package com.example.lifetrace.share.caption

import android.util.Log
import com.example.lifetrace.share.model.*

/**
 * 阶段 B: 文字价值筛选服务
 *
 * 职责：
 * B1: 规则预过滤（本地、快速）
 * B2: AI 语义筛选（可选，远程）
 * B3: 输出: 高/中/低价值文字分类
 */
class TextQualityFilter(
    private val textDeduplicator: TextDeduplicator = TextDeduplicator,
    private val aiFilterEnabled: Boolean = false
) {
    companion object {
        private const val TAG = "TextQualityFilter"

        // ========== 规则黑名单 ==========
        private val BLACKLIST = setOf(
            "test", "测试", "123", "111", "aaa", "abc",
            "哈哈", "哈哈哈", "hehe", "he", "hehehe",
            "打卡", "随便", "记录一下", "到此一游",
            "xx", "xxx", "???", "..."
        )

        // ========== 极短无信息词 ==========
        private val LOW_INFO_SHORT_WORDS = setOf(
            "到了", "好看", "不错", "还行", "记录",
            "嗯", "啊", "哦", "好", "行",
            "可以", "ok", "OK", "Ok"
        )

        // ========== 高价值关键词 ==========
        private val HIGH_VALUE_KEYWORDS = listOf(
            // 时间相关
            "小时", "分钟", "凌晨", "傍晚", "早上", "晚上", "中午", "下午",
            // 掃队/等待
            "排队", "等了", "等待",
            // 情感表达
            "开心", "激动", "兴奋", "感动", "惊喜", "震撼", "难忘",
            // 具体事件
            "吃了", "去了", "到了", "买了", "看了", "拍了", "玩了", "住了",
            // 场景描述
            "风景", "景色", "建筑", "海边", "山顶", "街道", "公园", "餐厅", "景点"
        )

        // ========== 垃圾模式 ==========
        private val GARBAGE_PATTERNS = listOf(
            Regex("^[\\d\\s]+$"),                          // 纯数字/空格
            Regex("^[\\p{Punct}\\s]+$"),                     // 纯标点
            Regex("(.)\\1{5,}"),                             // 重复字符过高（5次以上相同字符）
            Regex("^[\\p{So}\\p{Po}\\s]*$")                 // 纯表情符号
        )
    }

    /**
     * 执行文字预筛选（Step 2: 规则预过滤）
     * @param inputContext 输入上下文
     * @return 预筛选结果
     */
    suspend fun preFilter(inputContext: TripCaptionInputContext): PreFilteredTextCollection {
        Log.d(TAG, "=== Step 2: 规则预筛选开始 ===")
        Log.d(TAG, "输入文字数量: ${inputContext.candidateTexts.size}")

        try {
            val items = inputContext.candidateTexts

            // B1-1: 标准化
            val normalizedItems = normalizeAll(items)
            Log.d(TAG, "[B1-1] 标准化完成: ${normalizedItems.size} 项")

            // B1-2: 去重
            val deduplicatedResult = textDeduplicator.deduplicate(normalizedItems)
            Log.d(TAG, "[B1-2] 去重完成: 保留 ${deduplicatedResult.deduplicated.size} 项, 合并 ${deduplicatedResult.mergedTraces.size} 组重复")

            // B1-3: 垃圾过滤
            val garbageFiltered = filterGarbage(deduplicatedResult.deduplicated)
            Log.d(TAG, "[B1-3] 垃圾过滤完成: 保留 ${garbageFiltered.accepted.size} 项, 丢弃 ${garbageFiltered.dropped.size} 项")

            // B1-4: 质量分级
            val graded = gradeByQuality(garbageFiltered.accepted)
            Log.d(TAG, "[B1-4] 质量分级完成: 强 ${graded.strong.size} 项, 弱 ${graded.weak.size} 项")

            val result = PreFilteredTextCollection(
                acceptedStrong = graded.strong,
                acceptedWeak = graded.weak,
                dropped = garbageFiltered.dropped + deduplicatedResult.dropped,
                duplicatesMerged = deduplicatedResult.mergedTraces
            )

            Log.d(TAG, "=== Step 2: 规则预筛选完成 ===")
            Log.d(TAG, "强质量: ${result.acceptedStrong.size}, 弱质量: ${result.acceptedWeak.size}, 丢弃: ${result.dropped.size}")

            return result

        } catch (e: Exception) {
            Log.e(TAG, "Step 2 规则预筛选失败", e)
            // 降级：返回空集合
            return PreFilteredTextCollection(
                acceptedStrong = emptyList(),
                acceptedWeak = emptyList(),
                dropped = inputContext.candidateTexts.map {
                    RuleDroppedItem(it, "处理失败", "ERROR")
                },
                duplicatesMerged = emptyList()
            )
        }
    }

    /**
     * 执行完整的文字筛选（兼容旧接口）
     * @param inputContext 输入上下文
     * @return 筛选后的文字集合
     */
    suspend fun filter(inputContext: TripCaptionInputContext): FilteredTextCollection {
        Log.d(TAG, "=== 阶段 B: 文字价值筛选开始 ===")
        Log.d(TAG, "输入文字数量: ${inputContext.candidateTexts.size}")

        try {
            // 执行预筛选
            val preFiltered = preFilter(inputContext)

            // 转换为旧的 FilteredTextCollection 格式
            return preFiltered.toFilteredTextCollection()
        } catch (e: Exception) {
            Log.e(TAG, "阶段 B 文字筛选失败", e)
            // 降级：返回空集合
            return FilteredTextCollection(
                highQuality = emptyList(),
                mediumQuality = emptyList(),
                lowQuality = emptyList(),
                dropped = inputContext.candidateTexts,
                aiFilterEnabled = false
            )
        }
    }

    // ========== 私有方法 ==========

    /**
     * B1-1: 标准化所有文本
     */
    private fun normalizeAll(items: List<CandidateTextItem>): List<CandidateTextItem> {
        return items.map { item ->
            val normalized = normalizeText(item.text)
            item.copy(
                text = item.text,              // 保留原文
                normalizedText = normalized
            )
        }
    }

    /**
     * 标准化单条文本
     */
    private fun normalizeText(text: String?): String {
        if (text.isNullOrBlank()) return ""
        return text.trim()
            .replace(Regex("\\s+"), " ")
            .replace("\n", " ")
            .trim()
    }

    /**
     * B1-3: 垃圾过滤
     */
    private fun filterGarbage(items: List<CandidateTextItem>): GarbageFilterResult {
        val accepted = mutableListOf<CandidateTextItem>()
        val dropped = mutableListOf<RuleDroppedItem>()

        items.forEach { item ->
            val dropReason = checkGarbageRules(item)
            if (dropReason != null) {
                dropped.add(RuleDroppedItem(item, dropReason, "GARBAGE"))
            } else {
                accepted.add(item)
            }
        }

        return GarbageFilterResult(accepted, dropped)
    }

    /**
     * 检查垃圾规则
     * @return 丢弃原因，null 表示不丢弃
     */
    private fun checkGarbageRules(item: CandidateTextItem): String? {
        val text = item.normalizedText

        // 1. 空值检查
        if (text.isBlank()) return "空文本"

        // 2. 黑名单检查
        if (text.lowercase() in BLACKLIST) return "黑名单词汇"

        // 3. 极短无信息词检查
        if (text.length <= 4 && text.lowercase() in LOW_INFO_SHORT_WORDS) return "极短无信息词"

        // 4. 纯数字检查
        if (text.all { it.isDigit() }) return "纯数字"

        // 5. 垃圾模式检查
        for (pattern in GARBAGE_PATTERNS) {
            if (pattern.matches(text)) {
                return "匹配垃圾模式: ${pattern.pattern}"
            }
        }

        return null
    }

    /**
     * B1-4: 按质量分级
     */
    private fun gradeByQuality(items: List<CandidateTextItem>): QualityGradeResult {
        val strong = mutableListOf<CandidateTextItem>()
        val weak = mutableListOf<CandidateTextItem>()

        items.forEach { item ->
            val level = evaluateQuality(item)
            if (level == TextQualityLevel.RULE_STRONG) {
                strong.add(item)
            } else {
                weak.add(item)
            }
        }

        return QualityGradeResult(strong, weak)
    }

    /**
     * 评估单条文本质量
     */
    private fun evaluateQuality(item: CandidateTextItem): TextQualityLevel {
        val text = item.normalizedText

        // 1. 高价值关键词检查
        val hasHighValueKeyword = HIGH_VALUE_KEYWORDS.any { keyword ->
            text.contains(keyword, ignoreCase = true)
        }
        if (hasHighValueKeyword) return TextQualityLevel.RULE_STRONG

        // 2. 长度判断
        if (text.length >= 10) return TextQualityLevel.RULE_STRONG

        // 3. 关联图片数量判断
        if (item.relatedImageCount > 0) return TextQualityLevel.RULE_WEAK

        // 4. 默认为弱质量
        return TextQualityLevel.RULE_WEAK
    }

    // ========== 内部数据类 ==========

    private data class GarbageFilterResult(
        val accepted: List<CandidateTextItem>,
        val dropped: List<RuleDroppedItem>
    )

    private data class QualityGradeResult(
        val strong: List<CandidateTextItem>,
        val weak: List<CandidateTextItem>
    )
}
