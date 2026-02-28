package com.example.lifetrace.ui.overlay

/**
 * 页面顶层覆盖状态
 * 统一管理图片预览、视频播放、音频录制、音频播放、分享旅程等覆盖层
 */
sealed class OverlayState {
    data class ImagePreview(val uri: String) : OverlayState()
    data class VideoPlayer(val uri: String) : OverlayState()
    data class AudioRecorder(val memoryNodeId: Long) : OverlayState()
    data class AudioPlayer(val uri: String) : OverlayState()
    data class ShareTrip(val tripId: Long) : OverlayState()
}
