package com.example.lifetrace.share.model

import android.net.Uri
import com.example.lifetrace.service.CaptionStyle

/**
 * AI 文案生成四阶段流水线数据模型
 */

// ========== 阶段 A: 输入收集 ==========

/**
 * 文字来源类型
 */
enum class TextSourceType {
    TRIP_TITLE,    // 旅行标题
    MEMORY_NOTE    // 记忆点备注
}

/**
 * 候选文字项
 */
data class CandidateTextItem(
    val id: String,                          // 唯一标识
    val sourceType: TextSourceType,           // 来源类型
    val sourceMemoryId: Long? = null,         // 来源记忆点ID（标题则为空）
    val text: String,                         // 文字内容
    val normalizedText: String = text,        // 标准化后的文本
    val timeHint: Long? = null,               // 时间提示
    val relatedImageCount: Int = 0,           // 关联图片数量
    val charCount: Int = text.length,         // 字符数
    val isFromSelectedMediaMemory: Boolean = false,  // 是否来自选中图片所在的记忆点
    val sortWeight: Float = 0f               // 排序权重
) {
    /**
     * 从文字创建短预览
     */
    fun preview(): String = if (text.length > 30) "${text.take(30)}..." else text
}

/**
 * 候选图片项
 */
data class CandidateImageItem(
    val id: String,                          // 唯一标识
    val uri: Uri,                             // 图片 Uri
    val memoryId: Long? = null,               // 所属记忆点ID
    val sortTime: Long,                       // 排序时间
    val mimeType: String = "image/jpeg",      // MIME 类型
    val isSelected: Boolean = true            // 是否被选中用于分析
)

/**
 * 生成上下文（阶段 A 输出）
 */
data class TripCaptionInputContext(
    val tripId: Long,
    val tripTitle: String?,
    val tripStartTime: Long,
    val tripEndTime: Long?,
    val candidateTexts: List<CandidateTextItem>,
    val candidateImages: List<CandidateImageItem>,
    val style: CaptionStyle,
    val userExtraNote: String? = null,      // 用户手动补充说明

    // 新增：Trip 统计信息
    val tripDurationText: String? = null,       // 时长文本（如"3天2夜"）
    val tripDateText: String? = null,           // 日期文本（如"2024年3月15日"）
    val tripTimeRangeText: String? = null,      // 时间范围文本（如"上午9:00 - 下午5:00"）
    val memoryNodeCount: Int = 0,               // 记忆点数量
    val selectedImageCount: Int = 0,             // 选中图片数量
    val selectedVideoCount: Int = 0,            // 选中视频数量

    // 新增：图片-记忆点关系
    val imageToMemoryMap: Map<String, Long> = emptyMap(),      // 图片ID -> 记忆点ID
    val memoryToImageIdsMap: Map<Long, List<String>> = emptyMap(),  // 记忆点ID -> 图片ID列表

    // 新增：调试信息
    val collectorDebugSummary: String? = null
) {
    /**
     * 获取有效文字数量
     */
    fun validTextCount(): Int = candidateTexts.count { it.text.isNotBlank() }

    /**
     * 计算选中图片数量（动态计算，基于 candidateImages）
     */
    fun calculatedSelectedImageCount(): Int = candidateImages.count { it.isSelected }
}

// ========== 阶段 B: 文字筛选 ==========

/**
 * 规则筛选等级
 */
enum class TextQualityLevel {
    RULE_STRONG,     // 规则判断为高质量
    RULE_WEAK,       // 规则判断为弱质量
    RULE_DROPPED     // 规则判断应丢弃
}

/**
 * 规则筛选结果
 */
data class RuleFilterResult(
    val item: CandidateTextItem,
    val level: TextQualityLevel,
    val reason: String? = null
)

/**
 * AI 筛选决策
 */
enum class FilterDecision {
    KEEP,   // 保留
    DROP    // 丢弃
}

/**
 * AI 筛选质量等级
 */
enum class ScoreLevel {
    HIGH,       // 高质量
    MEDIUM,     // 中等质量
    LOW         // 低质量
}

/**
 * AI 筛选结果
 */
data class AIFilterResult(
    val id: String,
    val decision: FilterDecision,
    val scoreLevel: ScoreLevel,
    val reason: String?,
    val keywords: List<String> = emptyList()
)

/**
 * 筛选后的文字集合（阶段 B 输出）
 */
data class FilteredTextCollection(
    val highQuality: List<CandidateTextItem>,
    val mediumQuality: List<CandidateTextItem>,
    val lowQuality: List<CandidateTextItem>,
    val dropped: List<CandidateTextItem>,
    val aiFilterEnabled: Boolean = false    // 是否启用了 AI 筛选
) {
    /**
     * 获取所有可用文字（高 + 中 + 低）
     */
    fun getAllAvailable(): List<CandidateTextItem> =
        highQuality + mediumQuality + lowQuality

    /**
     * 获取推荐使用的文字（高 + 中）
     */
    fun getRecommended(): List<CandidateTextItem> =
        highQuality + mediumQuality

    /**
     * 是否有可用文字
     */
    fun hasAvailableText(): Boolean =
        highQuality.isNotEmpty() || mediumQuality.isNotEmpty() || lowQuality.isNotEmpty()
}

// ========== 新增：规则预筛选中间产物 (Step 2 输出) ==========

/**
 * 被规则丢弃的文本记录
 */
data class RuleDroppedItem(
    val item: CandidateTextItem,
    val dropReason: String,
    val stage: String  // 如 "GARBAGE", "BLACKLIST", "DUPLICATE"
)

/**
 * 重复合并追踪记录
 */
data class DuplicateMergeTrace(
    val keptId: String,
    val mergedIds: List<String>,
    val reason: String
)

/**
 * 规则预筛选结果（Step 2 输出）
 */
data class PreFilteredTextCollection(
    val acceptedStrong: List<CandidateTextItem>,   // 规则判断为高质量
    val acceptedWeak: List<CandidateTextItem>,     // 规则判断为弱质量
    val dropped: List<RuleDroppedItem>,            // 被丢弃的项
    val duplicatesMerged: List<DuplicateMergeTrace> // 重复合并记录
) {
    /**
     * 获取所有接受的文字
     */
    fun getAllAccepted(): List<CandidateTextItem> = acceptedStrong + acceptedWeak

    /**
     * 转换为旧的 FilteredTextCollection 格式（兼容）
     */
    fun toFilteredTextCollection(): FilteredTextCollection = FilteredTextCollection(
        highQuality = acceptedStrong,
        mediumQuality = acceptedWeak,
        lowQuality = emptyList(),
        dropped = dropped.map { it.item },
        aiFilterEnabled = false
    )

    /**
     * 转换为语义筛选集合格式（作为 AI 筛选的输入）
     */
    fun toSemanticCollection(): SemanticFilteredTextCollection = SemanticFilteredTextCollection(
        highQuality = acceptedStrong,
        mediumQuality = acceptedWeak,
        lowQuality = emptyList(),
        dropped = dropped.map { it.item },
        filterReasonsById = dropped.associate { it.item.id to it.dropReason },
        keywordsById = emptyMap(),
        aiFilterEnabled = false,
        aiFilterSucceeded = false
    )
}

/**
 * AI 语义筛选结果（Step 3 输出）
 */
data class SemanticFilteredTextCollection(
    val highQuality: List<CandidateTextItem>,
    val mediumQuality: List<CandidateTextItem>,
    val lowQuality: List<CandidateTextItem>,
    val dropped: List<CandidateTextItem>,
    val filterReasonsById: Map<String, String>,
    val keywordsById: Map<String, List<String>>,
    val aiFilterEnabled: Boolean,
    val aiFilterSucceeded: Boolean
) {
    /**
     * 获取所有可用文字
     */
    fun getAllAvailable(): List<CandidateTextItem> =
        highQuality + mediumQuality + lowQuality

    /**
     * 获取推荐使用的文字
     */
    fun getRecommended(): List<CandidateTextItem> =
        highQuality + mediumQuality

    /**
     * 转换为旧的 FilteredTextCollection 格式（兼容）
     */
    fun toFilteredTextCollection(): FilteredTextCollection = FilteredTextCollection(
        highQuality = highQuality,
        mediumQuality = mediumQuality,
        lowQuality = lowQuality,
        dropped = dropped,
        aiFilterEnabled = aiFilterEnabled
    )
}

// ========== 阶段 C: 图片分析 ==========

/**
 * 增强版图片分析结果
 */
data class EnhancedImageAnalysisResult(
    val imageId: String,
    val scene: String = "",              // 场景（如：海边步道、城市街道、餐厅内部）
    val subject: String = "",            // 主体（如：海岸线、建筑、食物、人物背影）
    val mood: String = "",               // 氛围（如：轻松、热闹、安静、浪漫）
    val activity: String = "",           // 活动（如：散步、用餐、观景、拍照）
    val shareableSummary: String = "",   // 适合分享的一句话总结
    val keywords: List<String> = emptyList(),
    val analysisSuccess: Boolean = true,
    val errorMessage: String? = null
) {
    /**
     * 转换为用于 Prompt 的文本
     */
    fun toPromptText(): String {
        val parts = mutableListOf<String>()
        if (scene.isNotBlank()) parts.add("场景:$scene")
        if (subject.isNotBlank()) parts.add("主体:$subject")
        if (mood.isNotBlank()) parts.add("氛围:$mood")
        if (activity.isNotBlank()) parts.add("活动:$activity")
        if (shareableSummary.isNotBlank()) parts.add("总结:$shareableSummary")
        return parts.joinToString(";")
    }
}

/**
 * 视觉旅程摘要（阶段 C 输出）
 */
data class VisualTripSummary(
    val mainScenes: List<String>,           // 主要场景列表
    val mainSubjects: List<String>,         // 主要画面关键词
    val overallMood: String,                // 整体氛围
    val mainActivities: List<String>,       // 活动轨迹线索
    val visualThemeSentence: String,        // 核心主题句
    val analyzedImageCount: Int,            // 分析的图片数量
    val imageAnalysisResults: List<EnhancedImageAnalysisResult> = emptyList()  // 详细的图片分析结果
) {
    companion object {
        val EMPTY = VisualTripSummary(
            mainScenes = emptyList(),
            mainSubjects = emptyList(),
            overallMood = "",
            mainActivities = emptyList(),
            visualThemeSentence = "",
            analyzedImageCount = 0
        )
    }

    /**
     * 是否有效
     */
    fun isValid(): Boolean = analyzedImageCount > 0 && mainScenes.isNotEmpty()
}

// ========== 阶段 D: 最终生成 ==========

/**
 * 旅程语义摘要（最终生成的输入）
 */
data class TripSemanticSummary(
    // 视觉摘要（主）
    val visualSummary: VisualTripSummary?,
    // 文字摘要（辅）
    val selectedTripTitle: String?,
    val selectedMemoryNotes: List<String>,
    val noteKeywords: List<String>,
    val notableExperiences: List<String>,
    // 结构信息（补）
    val durationText: String?,
    val dateText: String? = null,
    val timeRangeText: String? = null,
    val memoryCount: Int,
    val selectedImageCount: Int
) {
    /**
     * 生成用于 Prompt 的文本
     */
    fun toPromptText(): String {
        val sb = StringBuilder()

        // 视觉信息
        visualSummary?.let { visual ->
            if (visual.isValid()) {
                sb.append("【图片视觉分析】\n")
                sb.append("场景:${visual.mainScenes.joinToString("、")}\n")
                sb.append("氛围:${visual.overallMood}\n")
                sb.append("活动:${visual.mainActivities.joinToString("、")}\n")
                sb.append("主题:${visual.visualThemeSentence}\n")
            }
        }

        // 文字信息
        if (!selectedTripTitle.isNullOrBlank()) {
            sb.append("\n【旅行标题】${selectedTripTitle}\n")
        }
        if (selectedMemoryNotes.isNotEmpty()) {
            sb.append("\n【用户备注】\n")
            selectedMemoryNotes.forEachIndexed { index, note ->
                sb.append("${index + 1}. $note\n")
            }
        }

        // 结构信息
        if (!durationText.isNullOrBlank()) {
            sb.append("\n【时长】${durationText}\n")
        }
        sb.append("\n【记忆点数量】${memoryCount}个\n")
        sb.append("【选中图片】${selectedImageCount}张\n")

        return sb.toString()
    }
}

/**
 * 最终文案结果
 */
data class FinalCaptionResult(
    val title: String,               // 标题
    val body: String,                // 正文
    val tags: List<String>,           // 标签
    val rawContent: String,           // 原始生成内容
    val generationSuccess: Boolean,   // 生成是否成功
    val errorMessage: String? = null
) {
    /**
     * 获取完整的分享文案
     */
    fun toShareText(): String {
        val parts = mutableListOf<String>()
        if (title.isNotBlank()) parts.add(title)
        if (body.isNotBlank()) parts.add(body)
        if (tags.isNotEmpty()) parts.add("\n" + tags.joinToString(" "))
        return parts.joinToString("\n\n")
    }
}

/**
 * 生成阶段枚举（用于 UI 显示）
 * 扩展为 7 步流水线
 */
enum class CaptionGenerationStage {
    IDLE("等待开始"),
    PREPARING_INPUT("正在收集旅行信息..."),
    PRE_FILTERING_TEXT("正在预筛选文字..."),
    AI_FILTERING_TEXT("正在智能评估文字..."),
    ANALYZING_IMAGES("正在理解图片内容..."),
    BUILDING_SUMMARY("正在整理旅行摘要..."),
    GENERATING_CAPTION("正在生成分享文案..."),
    CLEANING_RESULT("正在优化文案格式..."),
    SUCCESS("生成完成!"),
    ERROR("生成失败");

    val displayText: String

    constructor(displayText: String) {
        this.displayText = displayText
    }
}

/**
 * 四阶段处理结果
 */
data class FourStageResult(
    val stage: CaptionGenerationStage,
    val inputContext: TripCaptionInputContext? = null,
    val filteredTexts: FilteredTextCollection? = null,
    val visualSummary: VisualTripSummary? = null,
    val finalResult: FinalCaptionResult? = null,
    val error: String? = null
) {
    companion object {
        fun idle() = FourStageResult(CaptionGenerationStage.IDLE)
        fun error(message: String) = FourStageResult(CaptionGenerationStage.ERROR, error = message)
    }

    fun isSuccess(): Boolean = stage == CaptionGenerationStage.SUCCESS
}
