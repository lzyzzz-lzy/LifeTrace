package com.example.lifetrace.ui.screen.share

import android.app.Application
import android.net.Uri
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

    private val _uiState = MutableStateFlow(ShareTripUiState())
    val uiState: StateFlow<ShareTripUiState> = _uiState

    init {
        tripRepository = TripRepository.getInstance(application)
        memoryNodeRepository = MemoryNodeRepository.getInstance(application)
        attachmentRepository = MemoryAttachmentRepository.getInstance(application)

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
