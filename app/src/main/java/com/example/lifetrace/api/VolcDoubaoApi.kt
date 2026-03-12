package com.example.lifetrace.api

import android.util.Log
import com.example.lifetrace.share.model.ImageAnalysisResult
import com.example.lifetrace.share.model.StructuredCaption
import com.example.lifetrace.service.CaptionStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 火山引擎豆包大模型 API 封装
 * 文档：https://www.volcengine.com/docs/82379/1298454
 */
class VolcDoubaoApi(
    private val apiKey: String,
    private val endpoint: String = "https://ark.cn-beijing.volces.com/api/v3/chat/completions",
    private val modelId: String = "doubao-seed-2-0-pro-260215"
) {
    companion object {
        private const val TAG = "VolcDoubaoApi"
        private const val JSON_MEDIA_TYPE = "application/json; charset=utf-8"

        // 默认系统提示
        const val DEFAULT_SYSTEM_PROMPT = """你是一个旅行文案助手，根据用户的旅行信息生成适合社交媒体分享的文案。
请生成3条不同风格的文案，每条文案用数字序号分隔（如 1. 2. 3.）。
文案要求：
- 简洁有趣，适合微信朋友圈或小红书
- 可以使用适当的emoji增加趣味性
- 风格可以包括：文艺、活泼、幽默等
- 每条文案控制在100字以内"""

        // 图片分析系统提示
        const val IMAGE_ANALYSIS_PROMPT = """你是一个图片分析助手，请分析这张旅行相关的图片。
请用 JSON 格式返回分析结果，包含以下字段：
- scene: 场景描述（如：城市街道、海边、山路、餐厅、公园等）
- subject: 主要拍摄对象（如：建筑、风景、食物、人物背影、路牌等）
- atmosphere: 画面氛围（如：安静、热闹、轻松、旅行感、温馨等）
- activity: 可能的活动（如：步行、骑行、观景、用餐、购物等）
- keywords: 3-5个关键词列表
- summary: 一句话总结（20字以内）

只返回 JSON，不要有其他内容。"""

        // 结构化文案系统提示
        const val STRUCTURED_CAPTION_PROMPT = """你是一个旅行文案助手，根据用户的旅行信息和图片分析结果生成适合社交媒体分享的文案。

请按以下格式返回（包含标题、正文和标签）：
【标题】一个简短的标题
【正文】正文内容（80-150字）
【标签】#标签1 #标签2 #标签3

要求：
- 标题简洁有力，能吸引注意力
- 正文真实自然，有故事感
- 标签3-5个，与内容相关
- 使用适当的emoji增加趣味性"""
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * 生成文案（纯文本）
     */
    suspend fun generateCaption(
        prompt: String,
        systemPrompt: String = DEFAULT_SYSTEM_PROMPT
    ): CaptionResult = withContext(Dispatchers.IO) {
        try {
            val requestBody = buildTextRequestBody(prompt, systemPrompt)
            executeRequest(requestBody)

        } catch (e: Exception) {
            Log.e(TAG, "生成文案异常", e)
            CaptionResult(
                success = false,
                error = "生成失败: ${e.message}"
            )
        }
    }

    /**
     * 分析图片
     * @param imageBase64 图片的 Base64 编码（带前缀 data:image/jpeg;base64,）
     */
    suspend fun analyzeImage(
        imageBase64: String,
        customPrompt: String = "请分析这张图片"
    ): ImageAnalysisResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始分析图片")

            val requestBody = buildMultimodalRequestBody(
                textPrompt = customPrompt,
                imageBase64List = listOf(imageBase64),
                systemPrompt = IMAGE_ANALYSIS_PROMPT
            )

            val response = executeRequestInternal(requestBody)

            if (!response.success) {
                return@withContext ImageAnalysisResult(summary = "分析失败: ${response.error}")
            }

            // 解析 JSON 结果
            val content = response.rawContent
            Log.d(TAG, "图片分析结果: $content")

            // 尝试提取 JSON
            val jsonMatch = Regex("\\{[\\s\\S]*\\}").find(content)
            if (jsonMatch != null) {
                return@withContext ImageAnalysisResult.fromJson(jsonMatch.value)
            }

            // 无法解析为 JSON，使用原始内容作为 summary
            ImageAnalysisResult(summary = content.take(100))

        } catch (e: Exception) {
            Log.e(TAG, "图片分析异常", e)
            ImageAnalysisResult(summary = "分析失败: ${e.message}")
        }
    }

    /**
     * 批量分析图片
     */
    suspend fun analyzeImages(
        imageBase64List: List<String>
    ): List<ImageAnalysisResult> {
        return imageBase64List.map { analyzeImage(it) }
    }

    /**
     * 生成结构化文案（带图片分析）
     * @param tripInfo 旅程信息文本
     * @param imageSummaries 图片分析结果列表
     */
    suspend fun generateStructuredCaption(
        tripInfo: String,
        imageSummaries: List<ImageAnalysisResult>,
        stylePrompt: String = STRUCTURED_CAPTION_PROMPT
    ): StructuredCaptionResult = withContext(Dispatchers.IO) {
        try {
            // 构建包含图片分析的提示
            val promptBuilder = StringBuilder()
            promptBuilder.append(tripInfo)
            promptBuilder.append("\n\n图片分析结果：\n")

            imageSummaries.forEachIndexed { index, summary ->
                promptBuilder.append("图片${index + 1}：${summary.toPromptText()}\n")
            }

            promptBuilder.append("\n请根据以上信息生成一条旅行分享文案。")

            val requestBody = buildTextRequestBody(promptBuilder.toString(), stylePrompt)
            val response = executeRequestInternal(requestBody)

            if (!response.success) {
                return@withContext StructuredCaptionResult(
                    success = false,
                    error = response.error
                )
            }

            // 解析结构化文案
            val structuredCaption = StructuredCaption.fromContent(
                response.rawContent,
                com.example.lifetrace.service.CaptionStyle.DOCUMENTARY
            )

            StructuredCaptionResult(
                success = true,
                caption = structuredCaption,
                rawContent = response.rawContent
            )

        } catch (e: Exception) {
            Log.e(TAG, "生成结构化文案异常", e)
            StructuredCaptionResult(
                success = false,
                error = "生成失败: ${e.message}"
            )
        }
    }

    /**
     * 执行请求
     */
    private suspend fun executeRequest(requestBody: String): CaptionResult {
        val response = executeRequestInternal(requestBody)
        return CaptionResult(
            success = response.success,
            captions = parseCaptions(response.rawContent),
            rawContent = response.rawContent,
            error = response.error
        )
    }

    /**
     * 执行请求（内部方法）
     */
    private fun executeRequestInternal(requestBody: String): CaptionResult {
        try {
            val request = buildRequest(requestBody)

            Log.d(TAG, "发送请求到豆包 API: $endpoint")
            Log.d(TAG, "Model: $modelId")

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "API 请求失败: ${response.code} - $errorBody")
                return CaptionResult(
                    success = false,
                    error = "API 请求失败: ${response.code} - $errorBody"
                )
            }

            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                return CaptionResult(
                    success = false,
                    error = "响应体为空"
                )
            }

            Log.d(TAG, "API 响应: ${responseBody.take(500)}...")
            return parseResponse(responseBody)

        } catch (e: Exception) {
            Log.e(TAG, "请求执行异常", e)
            return CaptionResult(
                success = false,
                error = "请求失败: ${e.message}"
            )
        }
    }

    /**
     * 构建纯文本请求体
     */
    private fun buildTextRequestBody(prompt: String, systemPrompt: String): String {
        return JSONObject().apply {
            put("model", modelId)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("temperature", 0.7)
            put("max_tokens", 1000)
        }.toString()
    }

    /**
     * 构建多模态请求体（支持图片）
     */
    private fun buildMultimodalRequestBody(
        textPrompt: String,
        imageBase64List: List<String>,
        systemPrompt: String
    ): String {
        return JSONObject().apply {
            put("model", modelId)
            put("messages", JSONArray().apply {
                // System message
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemPrompt)
                })

                // User message with images and text
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", JSONArray().apply {
                        // 添加图片
                        imageBase64List.forEach { imageBase64 ->
                            put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().apply {
                                    put("url", imageBase64)
                                })
                            })
                        }
                        // 添加文本
                        put(JSONObject().apply {
                            put("type", "text")
                            put("text", textPrompt)
                        })
                    })
                })
            })
            put("temperature", 0.7)
            put("max_tokens", 1500)
        }.toString()
    }

    /**
     * 构建请求
     */
    private fun buildRequest(requestBody: String): Request {
        val mediaType = JSON_MEDIA_TYPE.toMediaType()
        val body = requestBody.toRequestBody(mediaType)

        return Request.Builder()
            .url(endpoint)
            .post(body)
            .addHeader("Content-Type", JSON_MEDIA_TYPE)
            .addHeader("Authorization", "Bearer $apiKey")
            .build()
    }

    /**
     * 解析响应
     */
    private fun parseResponse(responseJson: String): CaptionResult {
        return try {
            val json = JSONObject(responseJson)

            // 检查错误
            val error = json.optJSONObject("error")
            if (error != null) {
                val errorMsg = error.optString("message", "Unknown error")
                val errorCode = error.optString("code", "unknown")
                return CaptionResult(success = false, error = "$errorCode: $errorMsg")
            }

            // 解析 choices
            val choices = json.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return CaptionResult(success = false, error = "无生成结果")
            }

            val firstChoice = choices.getJSONObject(0)
            val message = firstChoice.optJSONObject("message")
            val content = message?.optString("content") ?: ""

            if (content.isBlank()) {
                return CaptionResult(success = false, error = "生成内容为空")
            }

            // 检查 finish_reason
            val finishReason = firstChoice.optString("finish_reason")
            if (finishReason == "content_filter") {
                return CaptionResult(success = false, error = "内容被审核拦截，请修改提示词")
            }

            CaptionResult(
                success = true,
                captions = parseCaptions(content),
                rawContent = content
            )

        } catch (e: Exception) {
            Log.e(TAG, "解析响应失败", e)
            CaptionResult(success = false, error = "解析失败: ${e.message}")
        }
    }

    /**
     * 解析文案列表
     */
    private fun parseCaptions(content: String): List<String> {
        val captions = mutableListOf<String>()

        // 尝试按数字序号分割
        val numberPattern = Regex("""\d+[.、]\s*""")
        val parts = content.split(numberPattern)
            .map { it.trim() }
            .filter { it.isNotBlank() && it.length > 5 }

        if (parts.size > 1) {
            captions.addAll(parts)
        } else {
            // 按换行分割
            val lines = content.split("\n")
                .map { it.trim() }
                .filter { it.isNotBlank() && it.length > 5 }

            if (lines.size > 1) {
                captions.addAll(lines)
            } else {
                captions.add(content.trim())
            }
        }

        return captions
    }
}

/**
 * 文案生成结果
 */
data class CaptionResult(
    val success: Boolean,
    val captions: List<String> = emptyList(),
    val rawContent: String = "",
    val error: String? = null
)

/**
 * 结构化文案生成结果
 */
data class StructuredCaptionResult(
    val success: Boolean,
    val caption: StructuredCaption? = null,
    val rawContent: String = "",
    val error: String? = null
)
