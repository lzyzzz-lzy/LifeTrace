package com.example.lifetrace.share.caption

import android.util.Log
import com.example.lifetrace.api.VolcDoubaoApi
import com.example.lifetrace.service.CaptionStyle
import com.example.lifetrace.share.model.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * 阧段 B (Step 3): AI 语义筛选器
 *
 * 职责：
 * 1. 对文本进行语义质量评估
 * 2. 决定保留/丢弃，并给出质量等级
 * 3. 提取关键词
 * 4. 失败时降级到规则筛选结果
 */
class TextSemanticRanker(
    private val api: VolcDoubaoApi
) {
    companion object {
        private const val TAG = "TextSemanticRanker"
        private const val MAX_TEXTS_FOR_AI = 20  // 最多送 AI 的文本数量
    }

    /**
     * 执行 AI 语义筛选
     * @param preFiltered 规则预过滤后的文字集合（Step 2 输出）
     * @param tripTitle Trip 标题
     * @param style 文案风格
     * @return 语义筛选结果（失败时返回规则过滤结果）
     */
    suspend fun rank(
        preFiltered: PreFilteredTextCollection,
        tripTitle: String?,
        style: CaptionStyle
    ): SemanticFilteredTextCollection {
        Log.d(TAG, "=== Step 3: AI 语义筛选开始 ===")

        try {
            val allItems = preFiltered.getAllAccepted()
            Log.d(TAG, "输入文本数: ${allItems.size}")

            if (allItems.isEmpty()) {
                Log.d(TAG, "无文本需要筛选，返回空结果")
                return preFiltered.toSemanticCollection()
            }

            // 如果文本数量较少，直接使用规则结果
            if (allItems.size <= 3) {
                Log.d(TAG, "文本数量较少，跳过 AI 筛选")
                return preFiltered.toSemanticCollection()
            }

            // 限制送 AI 的文本数量
            val itemsForAI = allItems.take(MAX_TEXTS_FOR_AI)

            // 构建 AI 筛选 Prompt
            val prompt = buildAIFilterPrompt(itemsForAI, tripTitle, style)
            Log.d(TAG, "[A1] 构建 AI 筛选 Prompt，长度: ${prompt.length}")

            // 调用 AI API
            val apiResult = api.generateCaption(prompt)
            Log.d(TAG, "[A2] AI 返回，success: ${apiResult.success}")

            if (!apiResult.success) {
                Log.w(TAG, "AI 调用失败: ${apiResult.error}，降级到规则结果")
                return preFiltered.toSemanticCollection()
            }

            val responseText = apiResult.rawContent

            // 解析 AI 结果
            val aiResults = parseAIFilterResult(responseText)
            Log.d(TAG, "[A3] 解析 AI 结果: ${aiResults.size} 条")

            if (aiResults.isEmpty()) {
                Log.w(TAG, "AI 结果解析失败，降级到规则结果")
                return preFiltered.toSemanticCollection()
            }

            // 根据 AI 结果分类
            val result = applyAIFilterResults(preFiltered, aiResults)

            Log.d(TAG, "=== Step 3: AI 语义筛选完成 ===")
            Log.d(TAG, "高质量: ${result.highQuality.size}, 中等: ${result.mediumQuality.size}, 低质量: ${result.lowQuality.size}")

            return result

        } catch (e: Exception) {
            Log.e(TAG, "Step 3 AI 语义筛选失败", e)
            // 降级：返回规则筛选结果
            return preFiltered.toSemanticCollection()
        }
    }

    /**
     * 兼容旧接口：直接对候选文本列表进行筛选
     */
    suspend fun rank(
        items: List<CandidateTextItem>,
        tripTitle: String?,
        style: CaptionStyle
    ): SemanticFilteredTextCollection {
        // 转换为 PreFilteredTextCollection 格式
        val preFiltered = PreFilteredTextCollection(
            acceptedStrong = items,
            acceptedWeak = emptyList(),
            dropped = emptyList(),
            duplicatesMerged = emptyList()
        )
        return rank(preFiltered, tripTitle, style)
    }

    // ========== 私有方法 ==========

    /**
     * 构建 AI 筛选 Prompt
     */
    private fun buildAIFilterPrompt(
        items: List<CandidateTextItem>,
        tripTitle: String?,
        style: CaptionStyle
    ): String {
        val sb = StringBuilder()

        sb.append("你是一个旅行文案助手，请评估以下旅行备注文字的质量。\n\n")

        if (!tripTitle.isNullOrBlank()) {
            sb.append("旅行标题：$tripTitle\n\n")
        }

        sb.append("请对以下文字进行质量评估，判断是否适合用于生成分享文案：\n\n")

        items.forEachIndexed { index, item ->
            sb.append("${index + 1}. \"${item.text}\"\n")
        }

        sb.append("""
请返回 JSON 数组格式，每条文字的评估结果包含：
- id: 文字序号（从1开始）
- decision: "KEEP"（保留）或 "DROP"（丢弃）
- scoreLevel: "HIGH"（高质量）、"MEDIUM"（中等）或 "LOW"（低质量）
- reason: 简短理由
- keywords: 提取的关键词（数组）

评估标准：
- 保留：包含具体的时间、地点、事件、情感描述
- 丢弃：过于笼统、无信息量、重复、与旅行无关
- 高质量：有具体内容，可用于文案主题
- 中等：有一定信息量，可作为补充
- 低质量：信息量较少

只返回 JSON 数组，不要其他文字。
""".trimIndent())

        return sb.toString()
    }

    /**
     * 解析 AI 筛选结果
     */
    private fun parseAIFilterResult(response: String): List<AIFilterResult> {
        try {
            // 尝试提取 JSON 数组
            val jsonContent = extractJsonArray(response)
            if (jsonContent.isBlank()) {
                Log.w(TAG, "无法从响应中提取 JSON 数组")
                return emptyList()
            }

            // 使用 org.json 解析
            val jsonArray = JSONArray(jsonContent)
            val results = mutableListOf<AIFilterResult>()

            for (i in 0 until jsonArray.length()) {
                val jsonItem = jsonArray.getJSONObject(i)
                results.add(
                    AIFilterResult(
                        id = jsonItem.optInt("id", i + 1).toString(),
                        decision = when (jsonItem.optString("decision", "KEEP").uppercase()) {
                            "KEEP" -> FilterDecision.KEEP
                            else -> FilterDecision.DROP
                        },
                        scoreLevel = when (jsonItem.optString("scoreLevel", "MEDIUM").uppercase()) {
                            "HIGH" -> ScoreLevel.HIGH
                            "MEDIUM" -> ScoreLevel.MEDIUM
                            else -> ScoreLevel.LOW
                        },
                        reason = jsonItem.optString("reason", null),
                        keywords = jsonItem.optJSONArray("keywords")?.let { arr ->
                            (0 until arr.length()).map { arr.getString(it) }
                        } ?: emptyList()
                    )
                )
            }

            return results
        } catch (e: Exception) {
            Log.e(TAG, "解析 AI 结果失败: ${e.message}")
            return emptyList()
        }
    }

    /**
     * 从响应中提取 JSON 数组
     */
    private fun extractJsonArray(response: String): String {
        // 尝试直接解析
        if (response.trim().startsWith("[")) {
            return response.trim()
        }

        // 尝试提取代码块中的 JSON
        val codeBlockPattern = Regex("```(?:json)?\\s*\\n?([\\s\\S]*?)\\n?```")
        val codeBlockMatch = codeBlockPattern.find(response)
        if (codeBlockMatch != null) {
            return codeBlockMatch.groupValues[1].trim()
        }

        // 尝试找到第一个 [ 和最后一个 ]
        val startIndex = response.indexOf('[')
        val endIndex = response.lastIndexOf(']')
        if (startIndex >= 0 && endIndex > startIndex) {
            return response.substring(startIndex, endIndex + 1)
        }

        return ""
    }

    /**
     * 应用 AI 筛选结果
     */
    private fun applyAIFilterResults(
        preFiltered: PreFilteredTextCollection,
        aiResults: List<AIFilterResult>
    ): SemanticFilteredTextCollection {
        val allItems = preFiltered.getAllAccepted()
        val resultMap = aiResults.associateBy { it.id }

        val highQuality = mutableListOf<CandidateTextItem>()
        val mediumQuality = mutableListOf<CandidateTextItem>()
        val lowQuality = mutableListOf<CandidateTextItem>()
        val dropped = mutableListOf<CandidateTextItem>()
        val filterReasonsById = mutableMapOf<String, String>()
        val keywordsById = mutableMapOf<String, List<String>>()

        allItems.forEachIndexed { index, item ->
            val aiResult = resultMap[(index + 1).toString()]

            if (aiResult == null) {
                // 没有对应的 AI 结果，保留原分类
                if (item in preFiltered.acceptedStrong) {
                    highQuality.add(item)
                } else {
                    mediumQuality.add(item)
                }
            } else {
                // 根据 AI 结果分类
                filterReasonsById[item.id] = aiResult.reason ?: ""
                keywordsById[item.id] = aiResult.keywords

                when (aiResult.decision) {
                    FilterDecision.DROP -> {
                        dropped.add(item)
                    }
                    FilterDecision.KEEP -> {
                        when (aiResult.scoreLevel) {
                            ScoreLevel.HIGH -> highQuality.add(item)
                            ScoreLevel.MEDIUM -> mediumQuality.add(item)
                            ScoreLevel.LOW -> lowQuality.add(item)
                        }
                    }
                }
            }
        }

        // 添加被规则丢弃的项
        dropped.addAll(preFiltered.dropped.map { it.item })

        return SemanticFilteredTextCollection(
            highQuality = highQuality,
            mediumQuality = mediumQuality,
            lowQuality = lowQuality,
            dropped = dropped,
            filterReasonsById = filterReasonsById,
            keywordsById = keywordsById,
            aiFilterEnabled = true,
            aiFilterSucceeded = true
        )
    }

    // ========== 内部数据类 ==========

    private data class AIFilterResult(
        val id: String,
        val decision: FilterDecision,
        val scoreLevel: ScoreLevel,
        val reason: String?,
        val keywords: List<String>
    )
}
