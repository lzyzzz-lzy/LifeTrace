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
     * 清洗标题
     */
    private fun cleanTitle(title: String): String {
        var cleaned = title.trim()

        // 移除模型套话前缀
        for (prefix in MODEL_PREFIXES) {
            if (cleaned.startsWith(prefix)) {
                cleaned = cleaned.removePrefix(prefix).trim()
            }
        }

        // 移除引号包裹
        cleaned = cleaned.removeSurrounding("\"")
        cleaned = cleaned.removeSurrounding("\"")
        cleaned = cleaned.removeSurrounding("'")

        // 移除 markdown 标题符号
        cleaned = cleaned.removePrefix("#").trim()

        // 合并多余空格
        cleaned = cleaned.replace(Regex("\\s+"), " ")

        return cleaned.trim()
    }

    /**
     * 清洗正文
     */
    private fun cleanBody(body: String): String {
        var cleaned = body.trim()

        // 移除模型套话前缀
        for (prefix in MODEL_PREFIXES) {
            if (cleaned.startsWith(prefix)) {
                cleaned = cleaned.removePrefix(prefix).trim()
            }
        }

        // 移除 markdown 代码块标记
        cleaned = cleaned.removePrefix("```").trimStart()
        cleaned = cleaned.removeSuffix("```").trimEnd()

        // 移除引号包裹
        cleaned = cleaned.removeSurrounding("\"")
        cleaned = cleaned.removeSurrounding("\"")

        // 处理换行：合并连续空行为单个换行
        cleaned = cleaned.replace(Regex("\\n{3,}"), "\n\n")

        // 处理行内多余空格（但保留中文排版需要的空格）
        cleaned = cleaned.lines().joinToString("\n") { line ->
            line.trim()
        }

        return cleaned.trim()
    }

    /**
     * 清洗标签
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

            // 移除标点符号
            cleaned = cleaned.replace(Regex("[,，、。！!？?]"), "")

            // 规范化：确保以 # 开头
            if (cleaned.isNotEmpty() && !cleaned.startsWith("#")) {
                cleaned = "#$cleaned"
            }

            cleaned
        }.filter { it.isNotBlank() && it != "#" }
    }
}
