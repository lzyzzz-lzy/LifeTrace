package com.example.lifetrace.share.caption

import android.util.Log
import com.example.lifetrace.api.StructuredCaptionResult
import com.example.lifetrace.api.VolcDoubaoApi
import com.example.lifetrace.service.CaptionStyle
import com.example.lifetrace.share.model.*

/**
 * 阶段 D: 最终文案生成服务
 *
 * 职责：
 * 1. 构建结构化摘要
 * 2. 调用文本模型生成
 * 3. 结果清洗和展示
 *
 * 日志 TAG: FinalCaptionGenerator
 */
class FinalCaptionGenerator(
    private val api: VolcDoubaoApi
) {
    companion object {
        private const val TAG = "FinalCaptionGenerator"
    }

    /**
     * 生成最终文案
     * @param visualSummary 视觉摘要（主）
     * @param filteredTexts 筛选后的文字（辅）
     * @param tripTitle Trip 标题
     * @param durationText 时长文本
     * @param style 文案风格
     * @return 最终文案结果
     */
    suspend fun generate(
        visualSummary: VisualTripSummary?,
        filteredTexts: FilteredTextCollection?,
        tripTitle: String?,
        durationText: String?,
        style: CaptionStyle
    ): FinalCaptionResult {
        Log.d(TAG, "=== 阶段 D: 最终文案生成开始 ===")

        try {
            // 1. 构建结构化摘要
            val semanticSummary = buildSemanticSummary(
                visualSummary, filteredTexts, tripTitle, durationText
            )
            Log.d(TAG, "[D1] 结构化摘要构建完成")
            Log.d(TAG, "  视觉摘要: ${visualSummary?.visualThemeSentence}")
            Log.d(TAG, "  高质量文字: ${filteredTexts?.highQuality?.size}")
            Log.d(TAG, "  时长: $durationText")

            // 2. 构建最终 Prompt
            val prompt = buildFinalPrompt(semanticSummary, style)
            Log.d(TAG, "[D2] 最终 Prompt 长度: ${prompt.length}")

            // 3. 调用 API 生成
            val apiResult = api.generateStructuredCaption(
                tripInfo = prompt,
                imageSummaries = emptyList(),  // 图片信息已经在 prompt 中
                stylePrompt = buildStylePrompt(style)
            )
            Log.d(TAG, "[D3] API 返回: success=${apiResult.success}")

            // 4. 处理结果
            return processResult(apiResult)

        } catch (e: Exception) {
            Log.e(TAG, "阶段 D 最终生成失败", e)
            return FinalCaptionResult(
                title = "",
                body = "",
                tags = emptyList(),
                rawContent = "",
                generationSuccess = false,
                errorMessage = "生成失败: ${e.message}"
            )
        }
    }

    /**
     * 构建语义摘要
     */
    private fun buildSemanticSummary(
        visualSummary: VisualTripSummary?,
        filteredTexts: FilteredTextCollection?,
        tripTitle: String?,
        durationText: String?
    ): TripSemanticSummary {
        // 提取高质量文字
        val selectedNotes = filteredTexts?.getRecommended()?.map { it.text } ?: emptyList()

        // 提取关键词
        val keywords = filteredTexts?.getRecommended()?.flatMap { item ->
            extractKeywords(item.text)
        }?.distinct() ?: emptyList()

        return TripSemanticSummary(
            visualSummary = visualSummary,
            selectedTripTitle = tripTitle,
            selectedMemoryNotes = selectedNotes.take(5),
            noteKeywords = keywords.take(10),
            notableExperiences = emptyList(),
            durationText = durationText,
            memoryCount = filteredTexts?.getAllAvailable()?.size ?: 0,
            selectedImageCount = visualSummary?.analyzedImageCount ?: 0
        )
    }

    /**
     * 构建最终 Prompt
     */
    private fun buildFinalPrompt(
        summary: TripSemanticSummary,
        style: CaptionStyle
    ): String {
        val sb = StringBuilder()

        // 图片分析结果（主）
        summary.visualSummary?.let { visual ->
            if (visual.isValid()) {
                sb.append("【图片视觉分析】\n")
                sb.append("场景: ${visual.mainScenes.joinToString("、")}\n")
                sb.append("氛围: ${visual.overallMood}\n")
                sb.append("活动: ${visual.mainActivities.joinToString("、")}\n")
                sb.append("主题: ${visual.visualThemeSentence}\n")
                sb.append("\n")
            }
        }

        // 文字信息（辅）
        if (!summary.selectedTripTitle.isNullOrBlank()) {
            sb.append("【旅行标题】${summary.selectedTripTitle}\n\n")
        }

        if (summary.selectedMemoryNotes.isNotEmpty()) {
            sb.append("【用户备注】\n")
            summary.selectedMemoryNotes.forEachIndexed { index, note ->
                sb.append("${index + 1}. $note\n")
            }
            sb.append("\n")
        }

        // 结构信息（补）
        if (!summary.durationText.isNullOrBlank()) {
            sb.append("【时长】${summary.durationText}\n")
        }

        sb.append("【图片数量】${summary.selectedImageCount}张\n")

        return sb.toString()
    }

    /**
     * 构建风格提示
     */
    private fun buildStylePrompt(style: CaptionStyle): String {
        return when (style) {
            CaptionStyle.DOCUMENTARY -> """
你是一个旅行文案助手，根据用户的旅行信息生成适合社交媒体分享的文案。

请按以下格式返回（包含标题、正文和标签）：
【标题】一个简短的标题
【正文】正文内容（80-180字)
【标签】#标签1 #标签2 #标签3

要求：
- 以图片内容分析结果为主要依据
- 仅参考明确、具体、与旅行经历相关的用户备注
- 忽略空泛、无意义的文字
- 不要虚构图片和文字中都没有体现的经历
- 风格：纪实、真实、有故事感
- 真实记录旅程经历
- 语言朴实自然，像在讲述故事
- 使用适当的emoji点缀
"""

            CaptionStyle.RELAXED -> """
你是一个旅行文案助手,根据用户的旅行信息生成适合社交媒体分享的文案。

请按以下格式返回(包含标题、正文和标签)：
【标题】一个简短的标题
【正文】正文内容(80-180字)
【标签】#标签1 #标签2 #标签3

要求：
- 以图片内容分析结果为主要依据
- 仅参考明确、具体、与旅行经历相关的用户备注
- 风格：轻松、愉快、有度假感
- 轻松自然，像朋友圈分享
- 语气松弛，可以用口语化表达
- 使用emoji增加趣味
"""

            CaptionStyle.PLAYFUL -> """
你是一个旅行文案助手,根据用户的旅行信息生成适合社交媒体分享的文案。

请按以下格式返回(包含标题、正文和标签)：
【标题】一个简短的标题
【正文】正文内容(80-180字)
【标签】#标签1 #标签2 #标签3

要求：
- 以图片内容分析结果为主要依据
- 风格：俏皮、有趣、有个性
- 活泼有趣，可以使用网络流行语
- 使用丰富的emoji
- 但不要太油腻
"""
        }
    }

    /**
     * 夋理 API 返回结果
     */
    private fun processResult(apiResult: StructuredCaptionResult): FinalCaptionResult {
        if (!apiResult.success) {
            return FinalCaptionResult(
                title = "",
                body = "",
                tags = emptyList(),
                rawContent = apiResult.rawContent,
                generationSuccess = false,
                errorMessage = apiResult.error
            )
        }

        val caption = apiResult.caption
        if (caption == null) {
            // 尝试从原始内容中提取
            return extractFromRawContent(apiResult.rawContent)
        }

        // 清洗结果
        val cleanedTitle = cleanText(caption.title)
        val cleanedBody = cleanText(caption.content)
        val cleanedTags = caption.tags.map { cleanTag(it) }

        Log.d(TAG, "=== 阶段 D: 最终文案生成完成 ===")
        Log.d(TAG, "标题: $cleanedTitle")
        Log.d(TAG, "正文: ${cleanedBody.take(50)}...")
        Log.d(TAG, "标签: ${cleanedTags.joinToString(" ")}")

        return FinalCaptionResult(
            title = cleanedTitle,
            body = cleanedBody,
            tags = cleanedTags,
            rawContent = apiResult.rawContent,
            generationSuccess = true
        )
    }

    /**
     * 从原始内容中提取结果（降级策略）
     */
    private fun extractFromRawContent(rawContent: String): FinalCaptionResult {
        if (rawContent.isBlank()) {
            return FinalCaptionResult(
                title = "",
                body = "",
                tags = emptyList(),
                rawContent = "",
                generationSuccess = false,
                errorMessage = "生成内容为空"
            )
        }

        // 尝试解析结构化格式
        val titleMatch = Regex("【标题】[:：]?\\s*(.+?)(?:\\n|$)").find(rawContent)
        val contentMatch = Regex("【正文】[:：]?\\s*([\\s\\S]+?)(?=【标签】|$)").find(rawContent)
        val tagsMatch = Regex("【标签】[:：]?\\s*(.+?)(?:\\n|$)").find(rawContent)

        return FinalCaptionResult(
            title = titleMatch?.groupValues?.get(1)?.trim() ?: "",
            body = contentMatch?.groupValues?.get(1)?.trim() ?: rawContent,
            tags = tagsMatch?.groupValues?.get(1)?.split("[,，、#\\s]+".toRegex())
                ?.filter { it.isNotBlank() }
                ?.map { if (it.startsWith("#")) it else "#$it" }
                ?: emptyList(),
            rawContent = rawContent,
            generationSuccess = true
        )
    }

    /**
     * 清洗文本
     */
    private fun cleanText(text: String): String {
        return text
            .removePrefix("当然可以")
            .removePrefix("以下是一段旅行文案")
            .removePrefix("好的")
            .removeSurrounding("\"")
            .trim()
    }

    /**
     * 清洗标签
     */
    private fun cleanTag(tag: String): String {
        val cleaned = tag.trim()
        return if (cleaned.startsWith("#")) cleaned else "#$cleaned"
    }

    /**
     * 提取关键词（简单实现）
     */
    private fun extractKeywords(text: String): List<String> {
        // TODO: 可以使用 NLP 或 AI 提取关键词
        // 暂时返回空列表
        return emptyList()
    }

    private fun String?.isNullOrBlank(): Boolean = this == null || this.isBlank()
}
