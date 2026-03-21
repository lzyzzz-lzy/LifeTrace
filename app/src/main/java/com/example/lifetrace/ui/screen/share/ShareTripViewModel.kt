package com.example.lifetrace.ui.screen.share

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.lifetrace.data.database.entity.AttachmentType
import com.example.lifetrace.data.database.entity.MemoryAttachmentEntity
import com.example.lifetrace.data.database.entity.MemoryNodeEntity
import com.example.lifetrace.data.database.entity.TripEntity
import com.example.lifetrace.data.database.repository.MemoryAttachmentRepository
import com.example.lifetrace.data.database.repository.MemoryNodeRepository
import com.example.lifetrace.data.database.repository.TripRepository
import com.example.lifetrace.service.CaptionService
import com.example.lifetrace.service.CaptionStyle
import com.example.lifetrace.service.TripCaptionData
import com.example.lifetrace.share.caption.*
import com.example.lifetrace.share.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 分享旅程页面 ViewModel
 *
 * 使用 7 步流水线生成文案：
 * Step 1: 收集 Trip 上下文
 * Step 2: 规则预过滤
 * Step 3: AI 文本筛选（可选）
 * Step 4: 图片视觉分析
 * Step 5: 构建语义摘要
 * Step 6: 生成最终文案
 * Step 7: 结果清洗
 */
class ShareTripViewModel(
    application: Application,
    private val tripId: Long
) : AndroidViewModel(application) {

    private val tripRepository: TripRepository
    private val memoryNodeRepository: MemoryNodeRepository
    private val attachmentRepository: MemoryAttachmentRepository
    private val captionService: CaptionService

    // ========== 7 步流水线服务 ==========
    private val inputCollector: CaptionInputCollector by lazy {
        CaptionInputCollector(getApplication(), tripRepository, memoryNodeRepository, attachmentRepository)
    }
    private val textQualityFilter: TextQualityFilter by lazy { TextQualityFilter() }
    private val textSemanticRanker: TextSemanticRanker by lazy {
        TextSemanticRanker(captionService.getVolcDoubaoApi())
    }
    private val imageExtractor: ImageSemanticExtractor by lazy {
        ImageSemanticExtractor(getApplication(), captionService.getVolcDoubaoApi())
    }
    private val promptInputAssembler: PromptInputAssembler by lazy { PromptInputAssembler() }
    private val finalGenerator: FinalCaptionGenerator by lazy {
        FinalCaptionGenerator(captionService.getVolcDoubaoApi())
    }
    private val captionResultCleaner: CaptionResultCleaner by lazy { CaptionResultCleaner }

    // AI 筛选开关 - 已启用，配合失败降级逻辑
    // 当 API 已配置且候选文本数量 >= 4 时启用
    private val aiFilterEnabled: Boolean = true

    private val _uiState = MutableStateFlow(ShareTripUiState())
    val uiState: StateFlow<ShareTripUiState> = _uiState

    init {
        tripRepository = TripRepository.getInstance(application)
        memoryNodeRepository = MemoryNodeRepository.getInstance(application)
        attachmentRepository = MemoryAttachmentRepository.getInstance(application)
        captionService = CaptionService(application)

        loadTripData()
    }

    /**
     * 加载旅程数据
     */
    private fun loadTripData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            try {
                val trip = tripRepository.getTripById(tripId)
                val nodes = memoryNodeRepository.getMemoryNodesForTrip(tripId)

                // 加载所有附件并分组
                val sections = buildMediaSections(nodes)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        trip = trip,
                        sections = sections
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "加载数据失败: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 构建媒体分组
     */
    private suspend fun buildMediaSections(nodes: List<MemoryNodeEntity>): List<MediaSection> {
        val sections = mutableListOf<MediaSection>()

        nodes.sortedBy { it.timestamp }.forEach { node ->
            val attachments = attachmentRepository.getAttachmentsForNode(node.id)
            val mediaAttachments = attachments.filter {
                it.type == AttachmentType.PHOTO || it.type == AttachmentType.VIDEO
            }

            if (mediaAttachments.isNotEmpty()) {
                val items = mediaAttachments.map { attachment ->
                    attachmentToMediaItem(attachment, node)
                }

                val sectionTitle = buildSectionTitle(node)
                sections.add(
                    MediaSection(
                        title = sectionTitle,
                        timestamp = node.timestamp,
                        items = items
                    )
                )
            }
        }

        return sections
    }

    /**
     * 将附件转换为媒体项
     */
    private fun attachmentToMediaItem(
        attachment: MemoryAttachmentEntity,
        node: MemoryNodeEntity
    ): ShareMediaItem {
        return ShareMediaItem(
            id = "${attachment.id}",
            uri = Uri.parse(attachment.uri),
            type = when (attachment.type) {
                AttachmentType.PHOTO -> MediaType.PHOTO
                AttachmentType.VIDEO -> MediaType.VIDEO
                else -> MediaType.PHOTO
            },
            timestamp = node.timestamp,
            nodeId = node.id,
            nodeTitle = node.text?.take(50)
        )
    }

    /**
     * 构建分组标题
     */
    private fun buildSectionTitle(node: MemoryNodeEntity): String {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeStr = timeFormat.format(Date(node.timestamp))
        val location = node.text?.take(20) ?: "记忆点"
        return "$timeStr $location"
    }

    // ========== 选择操作 ==========

    /**
     * 切换选择状态
     */
    fun toggleSelect(itemId: String) {
        _uiState.update { state ->
            val selectedIds = state.selectedIds.toMutableSet()
            if (itemId in selectedIds) {
                selectedIds.remove(itemId)
            } else {
                selectedIds.add(itemId)
            }
            state.copy(selectedIds = selectedIds)
        }
    }

    /**
     * 全选/取消全选
     */
    fun toggleSelectAll() {
        _uiState.update { state ->
            val allIds = state.sections.flatMap { it.items.map { item -> item.id } }.toSet()
            val newSelectedIds = if (state.selectedIds.size == allIds.size) {
                emptySet()
            } else {
                allIds
            }
            state.copy(selectedIds = newSelectedIds)
        }
    }

    /**
     * 获取选中的媒体项
     */
    fun getSelectedItems(): List<ShareMediaItem> {
        val state = _uiState.value
        return state.sections.flatMap { it.items }
            .filter { it.id in state.selectedIds }
    }

    /**
     * 清除选择
     */
    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet()) }
    }

    // ========== 图片优化 ==========

    /**
     * 设置优化中状态
     */
    fun setOptimizing(isOptimizing: Boolean) {
        _uiState.update { it.copy(isOptimizing = isOptimizing) }
    }

    /**
     * 更新优化后的 URI
     */
    fun updateOptimizedUri(itemId: String, optimizedUri: Uri) {
        _uiState.update { state ->
            val optimizedMap = state.optimizedMap.toMutableMap()
            optimizedMap[itemId] = optimizedUri
            state.copy(optimizedMap = optimizedMap)
        }
    }

    // ========== 文案生成（7 步流水线） ==========

    /**
     * 设置文案选项
     */
    fun setCaptionOptions(options: List<String>) {
        _uiState.update { it.copy(captionOptions = options) }
    }

    /**
     * 显示/隐藏文案对话框
     */
    fun setShowCaptionDialog(show: Boolean) {
        _uiState.update { it.copy(showCaptionDialog = show) }
    }

    /**
     * 设置文案生成风格
     */
    fun setCurrentCaptionStyle(style: CaptionStyle) {
        _uiState.update { it.copy(currentCaptionStyle = style) }
        // 切换风格后自动重新生成
        if (_uiState.value.captionResult != null) {
            generateCaptions()
        }
    }

    /**
     * 设置选中的文案
     */
    fun setSelectedCaption(caption: String) {
        _uiState.update { it.copy(selectedCaption = caption) }
    }

    /**
     * 生成文案（7 步流水线）
     */
    fun generateCaptions() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGeneratingCaption = true,
                    captionError = null,
                    captionOptions = emptyList(),
                    selectedCaption = null,
                    captionResult = null
                )
            }

            try {
                val state = _uiState.value
                Log.d(TAG, "=== 7 步流水线开始 ===")

                // ========== Step 1: 收集 Trip 上下文 ==========
                updateStage(CaptionGenerationStage.PREPARING_INPUT, "正在收集旅行信息...")
                val inputContext = inputCollector.collect(
                    tripId = tripId,
                    selectedItemIds = state.selectedIds,
                    style = state.currentCaptionStyle
                )
                Log.d(TAG, "[Step 1] 输入收集完成: 文字=${inputContext.validTextCount()}, 图片=${inputContext.selectedImageCount}")

                // ========== Step 2: 规则预过滤 ==========
                updateStage(CaptionGenerationStage.PRE_FILTERING_TEXT, "正在预筛选文字...")
                val preFiltered = textQualityFilter.preFilter(inputContext)
                Log.d(TAG, "[Step 2] 规则预筛选完成: 强=${preFiltered.acceptedStrong.size}, 弱=${preFiltered.acceptedWeak.size}")

                // ========== Step 3: AI 文本筛选 ==========
                Log.d(TAG, "[Step 3] aiFilterEnabled=$aiFilterEnabled, API已配置=${captionService.isApiConfigured()}, 候选文本数=${preFiltered.getAllAccepted().size}")

                val filteredTexts: SemanticFilteredTextCollection
                val shouldUseAIFilter = aiFilterEnabled &&
                    captionService.isApiConfigured() &&
                    preFiltered.getAllAccepted().size >= 4

                if (shouldUseAIFilter) {
                    updateStage(CaptionGenerationStage.AI_FILTERING_TEXT, "正在智能评估文字...")
                    Log.d(TAG, "[Step 3] 开始 AI 文本筛选，送入文本数=${preFiltered.getAllAccepted().size}")

                    filteredTexts = textSemanticRanker.rank(preFiltered, inputContext.tripTitle, state.currentCaptionStyle)

                    Log.d(TAG, "[Step 3] AI 筛选完成:")
                    Log.d(TAG, "  - 高质量=${filteredTexts.highQuality.size}, 中等=${filteredTexts.mediumQuality.size}, 低质量=${filteredTexts.lowQuality.size}")
                    Log.d(TAG, "  - AI 启用=${filteredTexts.aiFilterEnabled}, AI 成功=${filteredTexts.aiFilterSucceeded}")
                } else {
                    val skipReason = when {
                        !aiFilterEnabled -> "功能未启用"
                        !captionService.isApiConfigured() -> "API 未配置"
                        preFiltered.getAllAccepted().size < 4 -> "文本数量较少(${preFiltered.getAllAccepted().size}<4)"
                        else -> "未知原因"
                    }
                    Log.d(TAG, "[Step 3] 跳过 AI 筛选: $skipReason")
                    filteredTexts = preFiltered.toSemanticCollection()
                }

                // ========== Step 4: 图片视觉分析 ==========
                updateStage(CaptionGenerationStage.ANALYZING_IMAGES, "正在理解图片内容...")
                val visualSummary = imageExtractor.extract(inputContext.candidateImages)
                Log.d(TAG, "[Step 4] 图片分析完成: 主题=${visualSummary.visualThemeSentence}")

                // ========== Step 5: 构建语义摘要 ==========
                updateStage(CaptionGenerationStage.BUILDING_SUMMARY, "正在整理旅行摘要...")
                val semanticSummary = promptInputAssembler.buildSemanticSummary(
                    visualSummary = visualSummary,
                    filteredTexts = filteredTexts,
                    inputContext = inputContext
                )
                Log.d(TAG, "[Step 5] 语义摘要构建完成: 备注数=${semanticSummary.selectedMemoryNotes.size}")

                // ========== Step 6: 生成最终文案 ==========
                updateStage(CaptionGenerationStage.GENERATING_CAPTION, "正在生成分享文案...")
                val rawResult = finalGenerator.generate(
                    semanticSummary = semanticSummary,
                    style = state.currentCaptionStyle
                )
                Log.d(TAG, "[Step 6] 文案生成完成: success=${rawResult.generationSuccess}")

                // ========== Step 7: 结果清洗 ==========
                updateStage(CaptionGenerationStage.CLEANING_RESULT, "正在优化文案格式...")
                val finalResult = captionResultCleaner.clean(rawResult)
                Log.d(TAG, "[Step 7] 结果清洗完成")

                // ========== 处理结果 ==========
                if (finalResult.generationSuccess) {
                    val displayCaptions = buildDisplayCaptions(finalResult)

                    updateStage(CaptionGenerationStage.SUCCESS, "生成完成!")

                    _uiState.update {
                        it.copy(
                            isGeneratingCaption = false,
                            currentStage = CaptionGenerationStage.SUCCESS,
                            captionResult = finalResult,
                            captionOptions = displayCaptions,
                            selectedCaption = displayCaptions.firstOrNull(),
                            showCaptionDialog = true,
                            // 编辑相关
                            editableCaptionTitle = finalResult.title,
                            editableCaptionBody = finalResult.body,
                            editableCaptionTags = finalResult.tags,
                            originalCaptionResult = finalResult,
                            isEditing = false
                        )
                    }
                } else {
                    updateStage(CaptionGenerationStage.ERROR, finalResult.errorMessage ?: "生成失败")

                    _uiState.update {
                        it.copy(
                            isGeneratingCaption = false,
                            currentStage = CaptionGenerationStage.ERROR,
                            captionError = finalResult.errorMessage ?: "生成失败，请重试"
                        )
                    }
                }

                Log.d(TAG, "=== 7 步流水线完成 ===")

            } catch (e: Exception) {
                Log.e(TAG, "生成文案失败", e)
                updateStage(CaptionGenerationStage.ERROR, "生成失败: ${e.message}")

                _uiState.update {
                    it.copy(
                        isGeneratingCaption = false,
                        currentStage = CaptionGenerationStage.ERROR,
                        captionError = "生成失败: ${e.message}"
                    )
                }
            }
        }
    }

    /**
     * 更新生成阶段
     */
    private fun updateStage(stage: CaptionGenerationStage, message: String) {
        Log.d(TAG, "[阶段] ${stage.name}: $message")
        _uiState.update {
            it.copy(
                currentStage = stage,
                stageMessage = message
            )
        }
    }

    /**
     * 构建展示用的文案列表
     */
    private fun buildDisplayCaptions(result: FinalCaptionResult): List<String> {
        val captions = mutableListOf<String>()

        if (result.title.isNotBlank()) {
            captions.add(result.title)
        }
        if (result.body.isNotBlank()) {
            captions.add(result.body)
        }
        if (result.tags.isNotEmpty()) {
            captions.add(result.tags.joinToString(" "))
        }

        // 如果结构化解析失败，使用原始内容
        if (captions.isEmpty() && result.rawContent.isNotBlank()) {
            captions.add(result.rawContent)
        }

        return captions
    }

    // ========== 编辑功能 ==========

    /**
     * 开始编辑文案
     */
    fun startEditing() {
        _uiState.update { it.copy(isEditing = true) }
    }

    /**
     * 取消编辑
     */
    fun cancelEditing() {
        val original = _uiState.value.originalCaptionResult
        _uiState.update {
            it.copy(
                isEditing = false,
                editableCaptionTitle = original?.title ?: "",
                editableCaptionBody = original?.body ?: "",
                editableCaptionTags = original?.tags ?: emptyList()
            )
        }
    }

    /**
     * 更新编辑中的标题
     */
    fun updateEditableTitle(title: String) {
        _uiState.update { it.copy(editableCaptionTitle = title) }
    }

    /**
     * 更新编辑中的正文
     */
    fun updateEditableBody(body: String) {
        _uiState.update { it.copy(editableCaptionBody = body) }
    }

    /**
     * 更新编辑中的标签
     */
    fun updateEditableTags(tags: List<String>) {
        _uiState.update { it.copy(editableCaptionTags = tags) }
    }

    /**
     * 添加标签
     */
    fun addTag(tag: String) {
        val trimmedTag = tag.trim()
        if (trimmedTag.isBlank()) return

        val currentTags = _uiState.value.editableCaptionTags.toMutableList()
        val normalizedTag = if (trimmedTag.startsWith("#")) trimmedTag else "#$trimmedTag"
        if (normalizedTag !in currentTags) {
            currentTags.add(normalizedTag)
            _uiState.update { it.copy(editableCaptionTags = currentTags) }
        }
    }

    /**
     * 移除标签
     */
    fun removeTag(tag: String) {
        _uiState.update {
            it.copy(editableCaptionTags = it.editableCaptionTags.filter { t -> t != tag })
        }
    }

    /**
     * 保存编辑
     */
    fun saveEditing() {
        val state = _uiState.value
        val editedResult = FinalCaptionResult(
            title = state.editableCaptionTitle ?: "",
            body = state.editableCaptionBody ?: "",
            tags = state.editableCaptionTags,
            rawContent = state.originalCaptionResult?.rawContent ?: "",
            generationSuccess = true
        )

        _uiState.update {
            it.copy(
                isEditing = false,
                captionResult = editedResult,
                captionOptions = buildDisplayCaptions(editedResult),
                selectedCaption = buildDisplayCaptions(editedResult).firstOrNull()
            )
        }
    }

    /**
     * 恢复 AI 原文
     */
    fun restoreOriginal() {
        val original = _uiState.value.originalCaptionResult ?: return
        _uiState.update {
            it.copy(
                editableCaptionTitle = original.title,
                editableCaptionBody = original.body,
                editableCaptionTags = original.tags
            )
        }
    }

    /**
     * 获取完整的分享文案（用于复制）
     */
    fun getShareText(): String {
        val state = _uiState.value
        val result = state.captionResult ?: return ""

        val title = if (state.isEditing) state.editableCaptionTitle else result.title
        val body = if (state.isEditing) state.editableCaptionBody else result.body
        val tags = if (state.isEditing) state.editableCaptionTags else result.tags

        val parts = mutableListOf<String>()
        if (!title.isNullOrBlank()) parts.add(title)
        if (!body.isNullOrBlank()) parts.add(body)
        if (tags.isNotEmpty()) parts.add("\n" + tags.joinToString(" "))

        return parts.joinToString("\n\n")
    }

    /**
     * 获取仅正文（用于复制）
     */
    fun getBodyText(): String {
        val state = _uiState.value
        return if (state.isEditing) {
            state.editableCaptionBody ?: ""
        } else {
            state.captionResult?.body ?: ""
        }
    }

    // ========== 其他功能 ==========

    /**
     * 重新生成文案
     */
    fun regenerateCaptions() {
        generateCaptions()
    }

    /**
     * 检查 API 是否已配置
     */
    fun isApiConfigured(): Boolean {
        return captionService.isApiConfigured()
    }

    /**
     * 清除错误
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        private const val TAG = "ShareTripViewModel"
    }
}

/**
 * 分享旅程页面 UI 状态
 */
data class ShareTripUiState(
    val isLoading: Boolean = true,
    val trip: TripEntity? = null,
    val sections: List<MediaSection> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val optimizedMap: Map<String, Uri> = emptyMap(),
    val isOptimizing: Boolean = false,

    // 文案相关
    val captionOptions: List<String> = emptyList(),
    val showCaptionDialog: Boolean = false,
    val isGeneratingCaption: Boolean = false,
    val currentCaptionStyle: CaptionStyle = CaptionStyle.DOCUMENTARY,
    val selectedCaption: String? = null,
    val captionError: String? = null,
    val captionResult: FinalCaptionResult? = null,  // 新增：完整的结果对象

    // 编辑相关
    val editableCaptionTitle: String? = null,
    val editableCaptionBody: String? = null,
    val editableCaptionTags: List<String> = emptyList(),
    val originalCaptionResult: FinalCaptionResult? = null,  // 保存 AI 原文
    val isEditing: Boolean = false,

    // 状态
    val currentStage: CaptionGenerationStage = CaptionGenerationStage.IDLE,
    val stageMessage: String = "",
    val error: String? = null
) {
    /**
     * 获取选中的数量
     */
    fun getSelectedCount(): Int = selectedIds.size

    /**
     * 是否有选中项
     */
    fun hasSelection(): Boolean = selectedIds.isNotEmpty()

    /**
     * 是否已全选
     */
    fun isAllSelected(): Boolean {
        val totalCount = sections.sumOf { it.items.size }
        return totalCount > 0 && selectedIds.size == totalCount
    }

    /**
     * 获取总媒体数量
     */
    fun getTotalMediaCount(): Int = sections.sumOf { it.items.size }

    /**
     * 获取选中的图片数量
     */
    fun getSelectedPhotoCount(): Int {
        return sections.flatMap { it.items }
            .count { it.id in selectedIds && it.isPhoto() }
    }

    /**
     * 获取选中的视频数量
     */
    fun getSelectedVideoCount(): Int {
        return sections.flatMap { it.items }
            .count { it.id in selectedIds && it.isVideo() }
    }
}

/**
 * ViewModel Factory
 */
class ShareTripViewModelFactory(
    private val tripId: Long
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShareTripViewModel::class.java)) {
            val application = getApplication()
            return ShareTripViewModel(application, tripId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }

    private fun getApplication(): Application {
        return Class.forName("android.app.ActivityThread")
            .getMethod("currentApplication")
            .invoke(null) as Application
    }
}
