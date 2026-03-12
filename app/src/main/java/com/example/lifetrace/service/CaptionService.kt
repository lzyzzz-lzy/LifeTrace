package com.example.lifetrace.service

import android.content.Context
import android.util.Log
import com.example.lifetrace.api.CaptionResult
import com.example.lifetrace.api.StructuredCaptionResult
import com.example.lifetrace.api.VolcDoubaoApi
import com.example.lifetrace.data.database.entity.TripEntity
import com.example.lifetrace.share.model.ImageAnalysisResult

/**
 * 文案生成服务
 */
class CaptionService(private val context: Context) {

    companion object {
        private const val TAG = "CaptionService"

        // SharedPreferences 配置
        private const val PREF_NAME = "lifetrace_config"
        private const val KEY_API_KEY = "doubao_api_key"
        private const val KEY_MODEL_ID = "doubao_model_id"

        // 默认模型
        private const val DEFAULT_MODEL_ID = "doubao-seed-2-0-pro-260215"
    }

    private val api: VolcDoubaoApi by lazy {
        VolcDoubaoApi(
            apiKey = getApiKey(),
            modelId = getModelId()
        )
    }

    /**
     * 生成旅行文案
     * @param tripData 旅行数据
     * @return 文案选项列表
     */
    suspend fun generateCaptions(tripData: TripCaptionData): CaptionResult {
        Log.d(TAG, "开始生成文案: tripName=${tripData.tripName}")

        if (!isApiConfigured()) {
            return CaptionResult(
                success = false,
                error = "API 未配置，请先设置 API Key"
            )
        }

        val prompt = buildPrompt(tripData)

        return api.generateCaption(
            prompt = prompt,
            systemPrompt = buildSystemPrompt(tripData.style)
        )
    }

    /**
     * 获取 VolcDoubaoApi 实例（供四阶段服务使用）
     */
    fun getVolcDoubaoApi(): VolcDoubaoApi = api

    /**
     * 生成结构化文案（带图片分析）
     * @param tripInfo 旅程信息文本
     * @param imageSummaries 图片分析结果列表
     * @param style 文案风格
     * @return 结构化文案结果
     */
    suspend fun generateStructuredCaption(
        tripInfo: String,
        imageSummaries: List<ImageAnalysisResult>,
        style: CaptionStyle
    ): StructuredCaptionResult {
        Log.d(TAG, "开始生成结构化文案: imageCount=${imageSummaries.size}")
        return api.generateStructuredCaption(
            tripInfo = tripInfo,
            imageSummaries = imageSummaries,
            stylePrompt = buildStructuredSystemPrompt(style)
        )
    }

    /**
     * 构建结构化系统提示
     */
    private fun buildStructuredSystemPrompt(style: CaptionStyle): String {
        val basePrompt = """你是一个旅行文案助手，根据用户的旅行信息和图片分析结果生成适合社交媒体分享的文案。

请按以下格式返回（包含标题、正文和标签）：
【标题】一个简短的标题
【正文】正文内容（80-150字）
【标签】#标签1 #标签2 #标签3

要求：
- 标题简洁有力，能吸引注意力
- 正文真实自然，有故事感
- 标签3-5个，与内容相关
- 使用适当的emoji增加趣味性"""

        val styleAddition = when (style) {
            CaptionStyle.DOCUMENTARY -> "\n- 风格：纪实、真实、有故事感"
            CaptionStyle.RELAXED -> "\n- 风格：轻松、愉快、有度假感"
            CaptionStyle.PLAYFUL -> "\n- 风格：俏皮、有趣、有个性"
        }

        return basePrompt + styleAddition
    }

    /**
     * 构建用户提示
     */
    private fun buildPrompt(tripData: TripCaptionData): String {
        val sb = StringBuilder()

        sb.append("请为我的旅行生成3条分享文案：\n\n")

        if (tripData.tripName.isNotBlank()) {
            sb.append("旅行名称：${tripData.tripName}\n")
        }

        if (tripData.locations.isNotEmpty()) {
            sb.append("地点：${tripData.locations.joinToString("、")}\n")
        }

        if (tripData.duration.isNotBlank()) {
            sb.append("时长：${tripData.duration}\n")
        }

        if (tripData.highlights.isNotEmpty()) {
            sb.append("亮点：${tripData.highlights.take(5).joinToString("、")}\n")
        }

        if (tripData.mood.isNotBlank()) {
            sb.append("心情：${tripData.mood}\n")
        }

        return sb.toString()
    }

    /**
     * 构建系统提示
     */
    private fun buildSystemPrompt(style: CaptionStyle): String {
        return when (style) {
            CaptionStyle.DOCUMENTARY -> """你是一个纪实风格的旅行文案助手。
请生成3条适合社交媒体分享的文案，要求：
- 真实记录旅程经历，时间线清晰
- 语言朴实自然，像在讲述故事
- 使用适当的emoji点缀
- 每条文案用数字序号分隔（1. 2. 3.）
- 每条控制在100字以内"""

            CaptionStyle.RELAXED -> """你是一个轻松风格的旅行文案助手。
请生成3条适合社交媒体分享的文案，要求：
- 轻松、愉快、有度假感
- 可以用口语化表达
- 使用emoji增加趣味
- 每条文案用数字序号分隔（1. 2. 3.）
- 每条控制在100字以内"""

            CaptionStyle.PLAYFUL -> """你是一个俏皮风格的旅行文案助手。
请生成3条适合社交媒体分享的文案，要求：
- 俏皮、有趣、有个性
- 可以用网络流行语和梗
- 使用丰富的emoji
- 每条文案用数字序号分隔（1. 2. 3.）
- 每条控制在100字以内"""
        }
    }

    /**
     * 检查 API 是否已配置
     */
    fun isApiConfigured(): Boolean {
        return getApiKey().isNotBlank()
    }

    /**
     * 获取 API Key
     */
    private fun getApiKey(): String {
        val sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getString(KEY_API_KEY, "") ?: ""
    }

    /**
     * 获取模型 ID
     */
    private fun getModelId(): String {
        val sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getString(KEY_MODEL_ID, DEFAULT_MODEL_ID) ?: DEFAULT_MODEL_ID
    }

    /**
     * 保存 API 配置
     * @param apiKey 火山引擎 ARK API Key
     * @param modelId 模型 ID（可选）
     */
    fun saveApiConfig(apiKey: String, modelId: String = DEFAULT_MODEL_ID) {
        val sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .putString(KEY_API_KEY, apiKey)
            .putString(KEY_MODEL_ID, modelId)
            .apply()
        Log.d(TAG, "API 配置已保存")
    }

    /**
     * 清除 API 配置
     */
    fun clearApiConfig() {
        val sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sharedPreferences.edit()
            .remove(KEY_API_KEY)
            .remove(KEY_MODEL_ID)
            .apply()
        Log.d(TAG, "API 配置已清除")
    }
}

/**
 * 旅行文案数据
 */
data class TripCaptionData(
    val tripName: String = "",
    val locations: List<String> = emptyList(),
    val duration: String = "",
    val highlights: List<String> = emptyList(),
    val mood: String = "",
    val style: CaptionStyle = CaptionStyle.DOCUMENTARY
) {
    companion object {
        /**
         * 从 TripEntity 创建
         */
        fun fromTrip(
            trip: TripEntity?,
            nodeTitles: List<String> = emptyList(),
            locationNames: List<String> = emptyList()
        ): TripCaptionData {
            if (trip == null) return TripCaptionData()

            val duration = calculateDuration(trip.startTime, trip.endTime)

            return TripCaptionData(
                tripName = trip.title,
                locations = locationNames.ifEmpty { listOf("未知地点") },
                duration = duration,
                highlights = nodeTitles.filter { it.isNotBlank() }.take(5),
                mood = "",
                style = CaptionStyle.DOCUMENTARY
            )
        }

        private fun calculateDuration(startTime: Long, endTime: Long?): String {
            if (startTime <= 0) return ""

            val effectiveEndTime = endTime ?: System.currentTimeMillis()
            val diff = if (effectiveEndTime > startTime) effectiveEndTime - startTime else System.currentTimeMillis() - startTime
            val hours = diff / (1000 * 60 * 60)
            val days = hours / 24
            val remainHours = hours % 24

            return when {
                days > 0 -> "${days}天${if (remainHours > 0) "${remainHours}小时" else ""}"
                hours > 0 -> "${hours}小时"
                else -> ""
            }
        }
    }
}

/**
 * 文案风格
 */
enum class CaptionStyle(val displayName: String) {
    DOCUMENTARY("纪实"),
    RELAXED("轻松"),
    PLAYFUL("俏皮")
}
