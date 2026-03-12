package com.example.lifetrace.share.model

import com.example.lifetrace.service.CaptionStyle
import org.json.JSONObject

/**
 * 图片分析结果
 */
data class ImageAnalysisResult(
    val scene: String = "",       // 场景：城市街道/海边/山路/餐厅
    val subject: String = "",     // 主体：建筑/风景/食物/人物背影
    val atmosphere: String = "",  // 氛围：安静/热闹/轻松/旅行感
    val activity: String = "",    // 行为：步行/骑行/观景/用餐
    val keywords: List<String> = emptyList(),
    val summary: String = ""      // 简短总结
) {
    companion object {
        /**
         * 从 JSON 字符串解析
         */
        fun fromJson(json: String): ImageAnalysisResult {
            return try {
                val jsonObject = JSONObject(json)
                ImageAnalysisResult(
                    scene = jsonObject.optString("scene", ""),
                    subject = jsonObject.optString("subject", ""),
                    atmosphere = jsonObject.optString("atmosphere", ""),
                    activity = jsonObject.optString("activity", ""),
                    keywords = jsonObject.optJSONArray("keywords")?.let { arr ->
                        (0 until arr.length()).map { arr.getString(it) }
                    } ?: emptyList(),
                    summary = jsonObject.optString("summary", "")
                )
            } catch (e: Exception) {
                ImageAnalysisResult(summary = "解析失败")
            }
        }
    }

    /**
     * 转换为用于 Prompt 的文本描述
     */
    fun toPromptText(): String {
        val parts = mutableListOf<String>()
        if (scene.isNotBlank()) parts.add("场景：$scene")
        if (subject.isNotBlank()) parts.add("主体：$subject")
        if (atmosphere.isNotBlank()) parts.add("氛围：$atmosphere")
        if (activity.isNotBlank()) parts.add("活动：$activity")
        if (keywords.isNotEmpty()) parts.add("关键词：${keywords.take(5).joinToString("、")}")
        return parts.joinToString("；")
    }
}

/**
 * 结构化文案
 */
data class StructuredCaption(
    val title: String = "",       // 标题
    val content: String = "",     // 正文
    val tags: List<String> = emptyList(),  // 标签（如 #旅行 #城市探索）
    val style: CaptionStyle = CaptionStyle.DOCUMENTARY
) {
    companion object {
        /**
         * 从 AI 返回的内容解析结构化文案
         */
        fun fromContent(content: String, style: CaptionStyle): StructuredCaption {
            // 尝试解析标记格式（【标题】xxx，【正文】xxx，【标签】xxx）
            val titleMatch = Regex("【标题】[:：]?\\s*(.+?)(?:\n|$)").find(content)
            val contentMatch = Regex("【正文】[:：]?\\s*([\\s\\S]+?)(?=【标签】|$)").find(content)
            val tagsMatch = Regex("【标签】[:：]?\\s*(.+?)(?:\n|$)").find(content)

            if (titleMatch != null || contentMatch != null) {
                return StructuredCaption(
                    title = titleMatch?.groupValues?.get(1)?.trim() ?: "",
                    content = contentMatch?.groupValues?.get(1)?.trim() ?: content,
                    tags = tagsMatch?.groupValues?.get(1)?.split("[,，、#\\s]+".toRegex())
                        ?.filter { it.isNotBlank() }
                        ?.map { if (it.startsWith("#")) it else "#$it" }
                        ?: emptyList(),
                    style = style
                )
            }

            // 无法解析结构，将整个内容作为正文
            return StructuredCaption(
                title = "",
                content = content.trim(),
                tags = extractHashtags(content),
                style = style
            )
        }

        /**
         * 从文本中提取标签
         */
        private fun extractHashtags(text: String): List<String> {
            return Regex("#[^#\\s]+").findAll(text)
                .map { it.value }
                .toList()
        }
    }

    /**
     * 获取完整的分享文案（标题 + 正文 + 标签）
     */
    fun toShareText(): String {
        val parts = mutableListOf<String>()
        if (title.isNotBlank()) parts.add(title)
        if (content.isNotBlank()) parts.add(content)
        if (tags.isNotEmpty()) parts.add("\n" + tags.joinToString(" "))
        return parts.joinToString("\n\n")
    }

    /**
     * 是否有标题
     */
    fun hasTitle(): Boolean = title.isNotBlank()

    /**
     * 是否有标签
     */
    fun hasTags(): Boolean = tags.isNotEmpty()
}
