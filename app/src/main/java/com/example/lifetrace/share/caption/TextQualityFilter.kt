package com.example.lifetrace.share.caption

import android.util.Log
import com.example.lifetrace.share.model.*

/**
 * 阶段 B: 文字价值筛选服务
 *
 * 职责：
 * 1. B1: 规则预过滤（本地、快速）
 * 2. B2: AI 语义筛选（可选、远程）
 * 3. 输出: 高/中/低价值文字分类
 *
 * 日志 TAG: TextQualityFilter
 */
class TextQualityFilter(
    private val aiFilterEnabled: Boolean = false  // 是否启用 AI 筛选
) {
    companion object {
        private const val TAG = "TextQualityFilter"

        // 规则黑名单（明显测试/无意义文本）
        private val BLACKLIST = setOf(
            "test", "测试", "123", "111", "aaa", "abc",
            "哈哈", "哈哈哈", "hehe", "he", "hehehe",
            "打卡", "随便", "记录一下", "到此一游",
            "xx", "xxx", "???", "..."
        )

        // 极短无信息词（长度很短且不包含关键信息）
        private val LOW_INFO_SHORT_WORDS = setOf(
            "到了", "好看", "不错", "还行", "记录",
            "嗯", "啊", "哦", "好", "行",
            "可以", "ok", "OK", "Ok"
        )

        // 高价值关键词（包含具体信息）
        private val HIGH_VALUE_KEYWORDS = listOf(
            // 时间相关
            "小时", "分钟", "凌晨", "傍晚", "早上", "晚上", "中午", "下午",
            // 排队/等待
            "排队", "等了", "等待",
            // 情感表达
            "开心", "激动", "兴奋", "感动", "惊喜", "震撼", "难忘",
            // 具体事件
            "吃了", "去了", "到了", "买了", "看了", "拍了", "玩了", "住了",
            // 场景描述
            "风景", "景色", "建筑", "海边", "山顶", "街道", "公园", "餐厅", "景点"
        )
    }

    /**
     * 执行文字筛选
     * @param inputContext 输入上下文
     * @return 筛选后的文字集合
     */
    suspend fun filter(inputContext: TripCaptionInputContext): FilteredTextCollection {
        Log.d(TAG, "=== 阶段 B: 文字价值筛选开始 ===")
        Log.d(TAG, "输入文字数量: ${inputContext.candidateTexts.size}")

        try {
            // B1: 规则预过滤
            val ruleResults = inputContext.candidateTexts.map { item ->
                applyRuleFilter(item)
            }

            logRuleFilterResults(ruleResults)

            // 分组
            val ruleStrong = ruleResults.filter { it.level == TextQualityLevel.RULE_STRONG }.map { it.item }
            val ruleWeak = ruleResults.filter { it.level == TextQualityLevel.RULE_WEAK }.map { it.item }
            val ruleDropped = ruleResults.filter { it.level == TextQualityLevel.RULE_DROPPED }.map { it.item }

            // B2: AI 语义筛选（可选）
            if (aiFilterEnabled && (ruleStrong.isNotEmpty() || ruleWeak.isNotEmpty())) {
                Log.d(TAG, "[B2] 开始 AI 语义筛选...")
                val aiResults = applyAIFilter(ruleStrong + ruleWeak)
                return buildFinalCollection(ruleStrong, ruleWeak, ruleDropped, aiResults)
            }

            // 不使用 AI 筛选，直接使用规则结果
            Log.d(TAG, "[B2] AI 筛选未启用，使用规则结果")

            val result = FilteredTextCollection(
                highQuality = ruleStrong,
                mediumQuality = ruleWeak,
                lowQuality = emptyList(),
                dropped = ruleDropped,
                aiFilterEnabled = false
            )

            Log.d(TAG, "=== 阶段 B: 文字价值筛选完成 ===")
            Log.d(TAG, "高质量: ${result.highQuality.size}, 中等: ${result.mediumQuality.size}, 低质量: ${result.lowQuality.size}, 丢弃: ${result.dropped.size}")

            return result

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

    /**
     * B1: 应用规则过滤
     */
    private fun applyRuleFilter(item: CandidateTextItem): RuleFilterResult {
        val text = item.text.trim()

        // 1. 空值检查
        if (text.isBlank()) {
            return RuleFilterResult(item, TextQualityLevel.RULE_DROPPED, "空文本")
        }

        // 2. 黑名单检查
        if (text.lowercase() in BLACKLIST) {
            return RuleFilterResult(item, TextQualityLevel.RULE_DROPPED, "黑名单词汇")
        }

        // 3. 极短无信息词检查
        if (text.length <= 4 && text.lowercase() in LOW_INFO_SHORT_WORDS) {
            return RuleFilterResult(item, TextQualityLevel.RULE_DROPPED, "极短无信息词")
        }

        // 4. 纯数字检查
        if (text.all { it.isDigit() }) {
            return RuleFilterResult(item, TextQualityLevel.RULE_DROPPED, "纯数字")
        }

        // 5. 高价值关键词检查
        val hasHighValueKeyword = HIGH_VALUE_KEYWORDS.any { keyword ->
            text.contains(keyword, ignoreCase = true)
        }
        if (hasHighValueKeyword) {
            return RuleFilterResult(item, TextQualityLevel.RULE_STRONG, "包含高价值关键词")
        }

        // 6. 长度判断
        if (text.length >= 10) {
            // 较长的文本可能是高质量
            return RuleFilterResult(item, TextQualityLevel.RULE_STRONG, "文本长度足够")
        }

        // 7. 关联图片数量判断
        if (item.relatedImageCount > 0) {
            return RuleFilterResult(item, TextQualityLevel.RULE_WEAK, "关联了图片")
        }

        // 8. 默认为弱质量
        return RuleFilterResult(item, TextQualityLevel.RULE_WEAK, "默认弱质量")
    }

    /**
     * B2: 应用 AI 语义筛选（占位方法）
     * TODO: 实现真正的 AI 筛选
     */
    private suspend fun applyAIFilter(items: List<CandidateTextItem>): List<AIFilterResult> {
        // TODO: 调用 AI API 进行语义筛选
        // 暂时返回空列表
        return emptyList()
    }

    /**
     * 记录规则筛选结果
     */
    private fun logRuleFilterResults(results: List<RuleFilterResult>) {
        Log.d(TAG, "[B1] 规则预筛选结果:")

        val grouped = results.groupBy { it.level }
        grouped.forEach { (level, items) ->
            val levelName = when (level) {
                TextQualityLevel.RULE_STRONG -> "高价值"
                TextQualityLevel.RULE_WEAK -> "弱价值"
                TextQualityLevel.RULE_DROPPED -> "丢弃"
            }
            items.forEach { result ->
                Log.d(TAG, "  [$levelName] ${result.item.id}: \"${result.item.preview()}\" (${result.reason})")
            }
        }
    }

    /**
     * 构建最终集合（结合 AI 结果）
     */
    private fun buildFinalCollection(
        ruleStrong: List<CandidateTextItem>,
        ruleWeak: List<CandidateTextItem>,
        ruleDropped: List<CandidateTextItem>,
        aiResults: List<AIFilterResult>
    ): FilteredTextCollection {
        if (aiResults.isEmpty()) {
            return FilteredTextCollection(
                highQuality = ruleStrong,
                mediumQuality = ruleWeak,
                lowQuality = emptyList(),
                dropped = ruleDropped,
                aiFilterEnabled = true
            )
        }

        // 根据 AI 结果重新分类
        val highQuality = mutableListOf<CandidateTextItem>()
        val mediumQuality = mutableListOf<CandidateTextItem>()
        val lowQuality = mutableListOf<CandidateTextItem>()

        aiResults.forEach { aiResult ->
            val item = (ruleStrong + ruleWeak).find { it.id == aiResult.id }
            if (item != null && aiResult.decision == FilterDecision.KEEP) {
                when (aiResult.scoreLevel) {
                    ScoreLevel.HIGH -> highQuality.add(item)
                    ScoreLevel.MEDIUM -> mediumQuality.add(item)
                    ScoreLevel.LOW -> lowQuality.add(item)
                }
            }
        }

        return FilteredTextCollection(
            highQuality = highQuality,
            mediumQuality = mediumQuality,
            lowQuality = lowQuality,
            dropped = ruleDropped,
            aiFilterEnabled = true
        )
    }
}
