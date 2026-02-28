package com.example.lifetrace.share.model

/**
 * 媒体分组
 * 按节点（地点）或时间分组
 */
data class MediaSection(
    val title: String,
    val timestamp: Long,
    val items: List<ShareMediaItem>
) {
    /**
     * 获取选中的项目数量
     */
    fun getSelectedCount(selectedIds: Set<String>): Int {
        return items.count { it.id in selectedIds }
    }

    /**
     * 是否有选中的项目
     */
    fun hasSelection(selectedIds: Set<String>): Boolean {
        return items.any { it.id in selectedIds }
    }
}
