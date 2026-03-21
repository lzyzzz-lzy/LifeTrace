package com.example.lifetrace.share.caption

import android.util.Log
import com.example.lifetrace.share.model.*

/**
 * 阶段 C (Step 5): Prompt 输入组装器
 *
 * 职责：
 * 1. 将多源输入（视觉摘要 + 文字筛选 + Trip 统计）整合成结构化摘要
 * 2. 构建 notableExperiences（从高价值文本中抽取）
 * 3. 提取关键词和亮点
 * 4. 输出 TripSemanticSummary 供 FinalCaptionGenerator 使用
 */
class PromptInputAssembler {

    companion object {
        private const val TAG = "PromptInputAssembler"
        private const val MAX_NOTES = 5
        private const val MAX_KEYWORDS = 10
        private const val MAX_EXPERIENCES = 3
    }

    /**
     * 构建语义摘要
     * @param visualSummary 视觉摘要（Step 4 输出）
     * @param filteredTexts 文字筛选结果（Step 3 输出）
     * @param inputContext 输入上下文（Step 1 输出）
     * @return 语义摘要（Step 5 输出）
     */
    fun buildSemanticSummary(
        visualSummary: VisualTripSummary?,
        filteredTexts: SemanticFilteredTextCollection?,
        inputContext: TripCaptionInputContext
    ): TripSemanticSummary {
        Log.d(TAG, "=== Step 5: 构建语义摘要开始 ===")

        try {
            // 1. 提取文字信息
            val selectedNotes = extractNotes(filteredTexts)
            Log.d(TAG, "[S1] 提取备注: ${selectedNotes.size} 条")

            // 2. 提取关键词
            val keywords = extractKeywords(filteredTexts, visualSummary)
            Log.d(TAG, "[S2] 提取关键词: ${keywords.take(5)}")

            // 3. 构建 notableExperiences
            val experiences = buildNotableExperiences(filteredTexts, visualSummary)
            Log.d(TAG, "[S3] 精彩体验: ${experiences.size} 条")

            // 4. 构建最终摘要
            val summary = TripSemanticSummary(
                visualSummary = visualSummary,
                selectedTripTitle = inputContext.tripTitle,
                selectedMemoryNotes = selectedNotes,
                noteKeywords = keywords,
                notableExperiences = experiences,
                durationText = inputContext.tripDurationText,
                dateText = inputContext.tripDateText,
                timeRangeText = inputContext.tripTimeRangeText,
                memoryCount = inputContext.memoryNodeCount,
                selectedImageCount = inputContext.selectedImageCount
            )

            Log.d(TAG, "=== Step 5: 构建语义摘要完成 ===")
            Log.d(TAG, "  备注数: ${summary.selectedMemoryNotes.size}")
            Log.d(TAG, "  关键词数: ${summary.noteKeywords.size}")
            Log.d(TAG, "  精彩体验: ${summary.notableExperiences.size}")

            return summary

        } catch (e: Exception) {
            Log.e(TAG, "Step 5 构建语义摘要失败", e)
            // 降级：返回基本摘要
            return TripSemanticSummary(
                visualSummary = visualSummary,
                selectedTripTitle = inputContext.tripTitle,
                selectedMemoryNotes = emptyList(),
                noteKeywords = emptyList(),
                notableExperiences = emptyList(),
                durationText = inputContext.tripDurationText,
                dateText = inputContext.tripDateText,
                timeRangeText = inputContext.tripTimeRangeText,
                memoryCount = inputContext.memoryNodeCount,
                selectedImageCount = inputContext.selectedImageCount
            )
        }
    }

    /**
     * 兼容旧接口：从 FilteredTextCollection 构建
     */
    fun buildSemanticSummary(
        visualSummary: VisualTripSummary?,
        filteredTexts: FilteredTextCollection?,
        tripTitle: String?,
        tripStartTime: Long,
        tripEndTime: Long?,
        memoryNodeCount: Int,
        selectedImageCount: Int,
        durationText: String?,
        dateText: String? = null,
        timeRangeText: String? = null
    ): TripSemanticSummary {
        Log.d(TAG, "=== Step 5: 构建语义摘要开始（兼容接口） ===")

        // 转换为 SemanticFilteredTextCollection
        val semanticTexts = if (filteredTexts != null) {
            SemanticFilteredTextCollection(
                highQuality = filteredTexts.highQuality,
                mediumQuality = filteredTexts.mediumQuality,
                lowQuality = filteredTexts.lowQuality,
                dropped = filteredTexts.dropped,
                filterReasonsById = emptyMap(),
                keywordsById = emptyMap(),
                aiFilterEnabled = filteredTexts.aiFilterEnabled,
                aiFilterSucceeded = false
            )
        } else {
            null
        }

        // 提取文字信息
        val selectedNotes = filteredTexts?.getRecommended()?.map { it.text }?.take(MAX_NOTES) ?: emptyList()

        // 提取关键词
        val keywords = extractKeywordsFromTexts(
            filteredTexts?.getRecommended()?.map { it.text } ?: emptyList()
        )

        // 构建精彩体验
        val experiences = buildExperiencesFromTexts(selectedNotes)

        return TripSemanticSummary(
            visualSummary = visualSummary,
            selectedTripTitle = tripTitle,
            selectedMemoryNotes = selectedNotes,
            noteKeywords = keywords.take(MAX_KEYWORDS),
            notableExperiences = experiences,
            durationText = durationText,
            dateText = dateText,
            timeRangeText = timeRangeText,
            memoryCount = memoryNodeCount,
            selectedImageCount = selectedImageCount
        )
    }

    // ========== 私有方法 ==========

    /**
     * 从筛选结果中提取备注
     */
    private fun extractNotes(filteredTexts: SemanticFilteredTextCollection?): List<String> {
        if (filteredTexts == null) return emptyList()

        // 优先使用高质量文字
        val recommended = filteredTexts.getRecommended()

        return recommended
            .sortedByDescending { it.sortWeight }
            .take(MAX_NOTES)
            .map { it.text }
    }

    /**
     * 提取关键词（从文字 + 视觉摘要）
     */
    private fun extractKeywords(
        filteredTexts: SemanticFilteredTextCollection?,
        visualSummary: VisualTripSummary?
    ): List<String> {
        val keywords = mutableSetOf<String>()

        // 1. 从 AI 筛选结果中提取关键词
        filteredTexts?.keywordsById?.values?.forEach { keywordList ->
            keywords.addAll(keywordList)
        }

        // 2. 从高质量文字中提取关键词
        filteredTexts?.highQuality?.forEach { item ->
            val extractedKeywords = extractKeywordsFromText(item.text)
            keywords.addAll(extractedKeywords)
        }

        // 3. 从视觉摘要中提取关键词
        visualSummary?.let { visual ->
            keywords.addAll(visual.mainScenes)
            keywords.addAll(visual.mainSubjects)
            keywords.addAll(visual.mainActivities)
        }

        return keywords.toList().take(MAX_KEYWORDS)
    }

    /**
     * 从单条文本中提取关键词
     */
    private fun extractKeywordsFromText(text: String): List<String> {
        // 简单实现：提取可能的地点、活动等关键词
        // TODO: 可以使用 NLP 或 AI 提取更精确的关键词
        val keywords = mutableListOf<String>()

        // 常见地点关键词模式
        val locationPatterns = listOf(
            Regex("(\\w+(?:市|县|镇|村|岛|山|湖|河|海|湾|港|园|寺|庙|宫|殿|塔|桥))"),
            Regex("(\\w+(?:餐厅|酒店|民宿|景点|公园|广场|街道))")
        )

        locationPatterns.forEach { pattern ->
            pattern.findAll(text).forEach { match ->
                keywords.add(match.groupValues[1])
            }
        }

        return keywords
    }

    /**
     * 从多条文本中提取关键词
     */
    private fun extractKeywordsFromTexts(texts: List<String>): List<String> {
        val keywords = mutableSetOf<String>()
        texts.forEach { text ->
            keywords.addAll(extractKeywordsFromText(text))
        }
        return keywords.toList()
    }

    /**
     * 构建精彩体验列表
     */
    private fun buildNotableExperiences(
        filteredTexts: SemanticFilteredTextCollection?,
        visualSummary: VisualTripSummary?
    ): List<String> {
        val experiences = mutableListOf<String>()

        // 1. 从高质量文字中提取体验描述
        filteredTexts?.highQuality?.forEach { item ->
            val experience = extractExperience(item.text)
            if (experience != null) {
                experiences.add(experience)
            }
        }

        // 2. 从视觉摘要中补充
        visualSummary?.let { visual ->
            if (visual.mainActivities.isNotEmpty() && experiences.size < MAX_EXPERIENCES) {
                val activityDesc = visual.mainActivities.take(2).joinToString("、")
                if (activityDesc.isNotBlank()) {
                    experiences.add(activityDesc)
                }
            }
        }

        return experiences.take(MAX_EXPERIENCES)
    }

    /**
     * 从文本中提取体验描述
     */
    private fun extractExperience(text: String): String? {
        // 简单实现：如果文本包含动词且长度适中，则视为体验描述
        if (text.length < 5 || text.length > 50) return null

        // 常见动词
        val actionVerbs = listOf("去了", "到了", "吃了", "看了", "玩了", "买了", "住了", "拍了", "走了", "坐了")

        val hasAction = actionVerbs.any { text.contains(it) }
        if (hasAction) {
            return text
        }

        return null
    }

    /**
     * 从文本列表构建体验描述
     */
    private fun buildExperiencesFromTexts(texts: List<String>): List<String> {
        return texts.mapNotNull { text -> extractExperience(text) }.take(MAX_EXPERIENCES)
    }
}
