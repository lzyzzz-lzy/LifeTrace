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
import com.example.lifetrace.share.caption.CaptionInputCollector
import com.example.lifetrace.share.caption.FinalCaptionGenerator
import com.example.lifetrace.share.caption.ImageSemanticExtractor
import com.example.lifetrace.share.caption.TextQualityFilter
import com.example.lifetrace.share.model.CaptionGenerationStage
import com.example.lifetrace.share.model.FinalCaptionResult
import com.example.lifetrace.share.model.MediaSection
import com.example.lifetrace.share.model.MediaType
import com.example.lifetrace.share.model.ShareMediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * 分享旅程页面 ViewModel
 */
class ShareTripViewModel(
    application: Application,
    private val tripId: Long
) : AndroidViewModel(application) {

    private val tripRepository: TripRepository
    private val memoryNodeRepository: MemoryNodeRepository
    private val attachmentRepository: MemoryAttachmentRepository
    private val captionService: CaptionService

    // 四阶段服务
    private val inputCollector: CaptionInputCollector by lazy {
        CaptionInputCollector(getApplication(), tripRepository, memoryNodeRepository, attachmentRepository)
    }
    private val textQualityFilter: TextQualityFilter by lazy { TextQualityFilter() }
    private val imageExtractor: ImageSemanticExtractor by lazy {
        ImageSemanticExtractor(getApplication(), captionService.getVolcDoubaoApi())
    }
    private val finalGenerator: FinalCaptionGenerator by lazy {
        FinalCaptionGenerator(captionService.getVolcDoubaoApi())
    }

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
            nodeTitle = node.text?.take(50) // 使用文字描述作为标题
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
        if (_uiState.value.captionOptions.isNotEmpty()) {
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
     * 生成文案（四阶段流水线）
     */
    fun generateCaptions() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isGeneratingCaption = true,
                    captionError = null,
                    captionOptions = emptyList(),
                    selectedCaption = null
                )
            }

            try {
                val state = _uiState.value

                // ========== 阶段 A: 输入收集 ==========
                updateStage(CaptionGenerationStage.PREPARING, "正在整理旅行信息...")
                Log.d("ShareTripViewModel", "=== [主流程] 阶段 A: 输入收集 ===")

                val inputContext = inputCollector.collect(
                    tripId = tripId,
                    selectedItemIds = state.selectedIds,
                    style = state.currentCaptionStyle
                )

                Log.d("ShareTripViewModel", "输入收集完成: 文字=${inputContext.validTextCount()}, 图片=${inputContext.selectedImageCount()}")

                // ========== 阶段 B: 文字筛选 ==========
                updateStage(CaptionGenerationStage.FILTERING_TEXT, "正在筛选备注内容...")
                Log.d("ShareTripViewModel", "=== [主流程] 阶段 B: 文字筛选 ===")

                val filteredTexts = textQualityFilter.filter(inputContext)

                Log.d("ShareTripViewModel", "文字筛选完成: 高质量=${filteredTexts.highQuality.size}, 中=${filteredTexts.mediumQuality.size}, 低=${filteredTexts.lowQuality.size}")

                // ========== 阶段 C: 图片分析 ==========
                updateStage(CaptionGenerationStage.ANALYZING_IMAGES, "正在理解图片内容...")
                Log.d("ShareTripViewModel", "=== [主流程] 阶段 C: 图片分析 ===")

                val visualSummary = imageExtractor.extract(inputContext.candidateImages)

                Log.d("ShareTripViewModel", "图片分析完成: 主题=${visualSummary.visualThemeSentence}")

                // ========== 阶段 D: 最终生成 ==========
                updateStage(CaptionGenerationStage.GENERATING_CAPTION, "正在生成分享文案...")
                Log.d("ShareTripViewModel", "=== [主流程] 阶段 D: 最终生成 ===")

                val durationText = buildDurationText(state.trip)
                val finalResult = finalGenerator.generate(
                    visualSummary = visualSummary,
                    filteredTexts = filteredTexts,
                    tripTitle = inputContext.tripTitle,
                    durationText = durationText,
                    style = state.currentCaptionStyle
                )

                Log.d("ShareTripViewModel", "最终生成完成: success=${finalResult.generationSuccess}")

                // ========== 处理结果 ==========
                if (finalResult.generationSuccess) {
                    val displayCaptions = buildDisplayCaptions(finalResult)

                    updateStage(CaptionGenerationStage.SUCCESS, "生成完成!")

                    _uiState.update {
                        it.copy(
                            isGeneratingCaption = false,
                            currentStage = CaptionGenerationStage.SUCCESS,
                            captionOptions = displayCaptions,
                            selectedCaption = displayCaptions.firstOrNull(),
                            showCaptionDialog = true
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

            } catch (e: Exception) {
                Log.e("ShareTripViewModel", "生成文案失败", e)
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
        Log.d("ShareTripViewModel", "[阶段] ${stage.name}: $message")
        _uiState.update {
            it.copy(
                currentStage = stage,
                stageMessage = message
            )
        }
    }

    /**
     * 构建时长文本
     */
    private fun buildDurationText(trip: TripEntity?): String {
        if (trip == null || trip.startTime <= 0) return ""

        val effectiveEndTime = trip.endTime ?: System.currentTimeMillis()
        val diff = if (effectiveEndTime > trip.startTime) effectiveEndTime - trip.startTime else 0L
        val hours = diff / (1000 * 60 * 60)
        val days = hours / 24
        val remainHours = hours % 24

        return when {
            days > 0 -> "${days}天${if (remainHours > 0) "${remainHours}小时" else ""}"
            hours > 0 -> "${hours}小时"
            else -> ""
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

    /**
     * 构建旅程信息文本
     */
    private fun buildTripInfoText(trip: TripEntity?, nodes: List<MemoryNodeEntity>): String {
        val sb = StringBuilder()

        if (trip != null) {
            sb.append("旅行名称：${trip.title}\n")

            // 计算时长
            val duration = TripCaptionData.fromTrip(trip).duration
            if (duration.isNotBlank()) {
                sb.append("时长：$duration\n")
            }
        }

        // 收集地点信息
        val locationNames = nodes
            .mapNotNull { it.text?.take(30) }
            .distinct()
        if (locationNames.isNotEmpty()) {
            sb.append("地点：${locationNames.take(5).joinToString("、")}\n")
        }

        // 收集亮点（节点描述）
        val highlights = nodes
            .mapNotNull { it.text }
            .filter { it.isNotBlank() }
            .take(5)
        if (highlights.isNotEmpty()) {
            sb.append("亮点：${highlights.joinToString("、")}\n")
        }

        return sb.toString()
    }

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
    val captionOptions: List<String> = emptyList(),
    val showCaptionDialog: Boolean = false,
    val isGeneratingCaption: Boolean = false,
    val currentCaptionStyle: CaptionStyle = CaptionStyle.DOCUMENTARY,
    val selectedCaption: String? = null,
    val captionError: String? = null,
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
