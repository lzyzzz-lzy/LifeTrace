package com.example.lifetrace.share.caption

import android.util.Log
import com.example.lifetrace.share.model.FinalCaptionResult

/**
 * 阶段 D (Step 7): 文案结果清洗器
 *
 * 负责：
 * 1. 去除模型套话（如 "当然可以"、"以下是..."）
 * 2. 去除 markdown 噪音
 * 3. 合并多余空行
 * 4. 规范标签格式
 * 5. 处理降级内容
 */
object CaptionResultCleaner {

    private const val TAG = "CaptionResultCleaner"

    // 模型套话前缀
    private val MODEL_PREFIXES = listOf(
        "当然可以",
        "好的，",
        "好的!",
        "好的。",
        "以下是一段",
        "以下是",
        "这是为您生成的",
        "这是根据",
        "我为您准备了"
    )

    /**
     * 清洗文案结果
     */
    fun clean(result: FinalCaptionResult): FinalCaptionResult {
        Log.d(TAG, "=== Step 7: 结果清洗开始 ===")

        try {
            // 1. 清洗各部分
            val cleanedTitle = cleanTitle(result.title)
            val cleanedBody = cleanBody(result.body)
            val cleanedTags = cleanTags(result.tags)

            Log.d(TAG, "[C1] 清洗完成")
            Log.d(TAG, "  标题: $cleanedTitle")
            Log.d(TAG, "  正文长度: ${cleanedBody.length}")
            Log.d(TAG, "  标签数: ${cleanedTags.size}")

            // 2. 返回清洗后的结果
            val finalResult = FinalCaptionResult(
                title = cleanedTitle,
                body = cleanedBody,
                tags = cleanedTags,
                rawContent = result.rawContent,
                generationSuccess = result.generationSuccess,
                errorMessage = result.errorMessage
            )

            Log.d(TAG, "=== Step 7: 结果清洗完成 ===")
            return finalResult

        } catch (e: Exception) {
            Log.e(TAG, "Step 7 结果清洗失败", e)
            // 降级：返回原始结果
            return result
        }
    }

    /**
     * 清洗标题（增强版）
     */
    private fun cleanTitle(title: String): String {
        var cleaned = title.trim()

        // 移除模型套话前缀
        for (prefix in MODEL_PREFIXES) {
            if (cleaned.startsWith(prefix)) {
                cleaned = cleaned.removePrefix(prefix).trim()
            }
        }

        // 移除 markdown 标题符号 ##, ###
        cleaned = cleaned.removePrefix("###").removePrefix("##").removePrefix("#").trim()

        // 移除 【标题】 前缀
        cleaned = cleaned.removePrefix("【标题】").removePrefix("【标题:").removePrefix("【标题：").trim()
        if (cleaned.startsWith("【") && cleaned.contains("】")) {
            val endIndex = cleaned.indexOf("】")
            val potentialTitle = cleaned.substring(endIndex + 1).trim()
            if (potentialTitle.isNotBlank()) {
                cleaned = potentialTitle
            }
        }

        // 移除前导列表号：①②③④⑤ 或 1. 2. 3.
        cleaned = cleaned.replace(Regex("^[①②③④⑤⑥⑦⑧⑨⑩][.、:：]?\\s*"), "")
        cleaned = cleaned.replace(Regex("^[1-9][0-9]?[.、:：]\\s*"), "")

        // 移除引号包裹
        cleaned = cleaned.removeSurrounding("\"")
        cleaned = cleaned.removeSurrounding("\"")
        cleaned = cleaned.removeSurrounding("'")

        // 合并多余空格
        cleaned = cleaned.replace(Regex("\\s+"), " ")

        // 若标题超过 30 字，按标点截断
        if (cleaned.length > 30) {
            val truncateIndex = cleaned.indexOfAny(charArrayOf('。', '！', '？', '，', '、'), 10)
            if (truncateIndex > 10 && truncateIndex < 30) {
                cleaned = cleaned.substring(0, truncateIndex)
            } else {
                cleaned = cleaned.take(30) + "..."
            }
        }

        return cleaned.trim()
    }

    /**
     * 清洗正文（增强版）
     */
    private fun cleanBody(body: String): String {
        var cleaned = body.trim()

        // 移除模型套话前缀
        for (prefix in MODEL_PREFIXES) {
            if (cleaned.startsWith(prefix)) {
                cleaned = cleaned.removePrefix(prefix).trim()
            }
        }

        // 移除 【正文】 前缀
        cleaned = cleaned.removePrefix("【正文】").removePrefix("【正文:").removePrefix("【正文：").trim()
        if (cleaned.startsWith("【") && cleaned.contains("】") && cleaned.indexOf("】") < 10) {
            val endIndex = cleaned.indexOf("】")
            val potentialBody = cleaned.substring(endIndex + 1).trim()
            if (potentialBody.isNotBlank()) {
                cleaned = potentialBody
            }
        }

        // 移除 markdown 代码块标记
        cleaned = cleaned.removePrefix("```").trimStart()
        cleaned = cleaned.removeSuffix("```").trimEnd()

        // 移除 markdown 列表符号 *, -, •
        cleaned = cleaned.lines().joinToString("\n") { line ->
            line.trim()
                .removePrefix("* ").removePrefix("- ").removePrefix("• ")
                .removePrefix("*").removePrefix("-").removePrefix("•")
                .trim()
        }

        // 移除 markdown heading ##
        cleaned = cleaned.replace(Regex("^#+\\s*"), "")
        cleaned = cleaned.replace(Regex("\\n#+\\s*"), "\n")

        // 移除引号包裹
        cleaned = cleaned.removeSurrounding("\"")
        cleaned = cleaned.removeSurrounding("\"")

        // 处理换行：合并连续空行为最多两个换行
        cleaned = cleaned.replace(Regex("\\n{3,}"), "\n\n")

        // 处理行内多余空格
        cleaned = cleaned.lines().joinToString("\n") { line ->
            line.trim()
        }

        return cleaned.trim()
    }

    /**
     * 清洗标签（增强版）
     */
    private fun cleanTags(tags: List<String>): List<String> {
        return tags.map { tag ->
            var cleaned = tag.trim()

            // 移除模型套话前缀
            for (prefix in MODEL_PREFIXES) {
                if (cleaned.startsWith(prefix)) {
                    cleaned = cleaned.removePrefix(prefix).trim()
                }
            }

            // 移除 【标签】 前缀
            cleaned = cleaned.removePrefix("【标签】").removePrefix("【标签:").removePrefix("【标签：").trim()

            // 移除标点符号
            cleaned = cleaned.replace(Regex("[,，、。！!？?]"), "")

            // 清理多个 # 连写，只保留一个
            cleaned = cleaned.replace(Regex("^#+"), "")
            cleaned = if (cleaned.isNotBlank()) "#$cleaned" else ""

            cleaned
        }
        .filter { it.isNotBlank() && it != "#" && it.length > 1 }
        .distinct()  // 去重
        .take(5)     // 限制最多 5 个
    }
}
