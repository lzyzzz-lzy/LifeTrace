package com.example.lifetrace.share.model

import android.net.Uri

/**
 * 分享媒体项
 */
data class ShareMediaItem(
    val id: String,
    val uri: Uri,
    val type: MediaType,
    val timestamp: Long,
    val nodeId: Long?,
    val nodeTitle: String?,
    val optimizedUri: Uri? = null,
    val isOptimizing: Boolean = false
) {
    /**
     * 是否为视频
     */
    fun isVideo(): Boolean = type == MediaType.VIDEO

    /**
     * 是否为照片
     */
    fun isPhoto(): Boolean = type == MediaType.PHOTO

    /**
     * 是否已被 AI 优化
     */
    fun isOptimized(): Boolean = optimizedUri != null

    /**
     * 获取用于显示/分享的 URI
     */
    fun getDisplayUri(): Uri = optimizedUri ?: uri
}

/**
 * 媒体类型
 */
enum class MediaType {
    PHOTO,
    VIDEO
}
