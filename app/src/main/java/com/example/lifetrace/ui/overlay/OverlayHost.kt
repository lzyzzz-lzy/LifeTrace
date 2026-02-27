package com.example.lifetrace.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.lifetrace.ui.components.AudioRecordingScreen
import com.example.lifetrace.ui.components.FullScreenImageViewer
import com.example.lifetrace.ui.overlay.OverlayState

/**
 * 覆盖层主机
 * 根据当前 OverlayState 渲染对应的覆盖组件
 */
@Composable
fun OverlayHost(
    overlayState: OverlayState?,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        when (overlayState) {
            is OverlayState.ImagePreview -> {
                // 图片预览：全屏 Dialog
                FullScreenImagePreview(
                    uri = overlayState.uri,
                    onDismiss = onDismiss
                )
            }

            is OverlayState.VideoPlayer -> {
                // 视频播放：最小版本，使用系统播放器
                FullScreenVideoPlayerMinimal(
                    uri = overlayState.uri,
                    onDismiss = onDismiss
                )
            }

            is OverlayState.AudioRecorder -> {
                // 音频录制：BottomSheet
                AudioRecordingSheet(
                    memoryNodeId = overlayState.memoryNodeId,
                    onDismiss = onDismiss
                )
            }

            is OverlayState.AudioPlayer -> {
                // 音频播放：BottomSheet
                AudioPlayerSheet(
                    uri = overlayState.uri,
                    onDismiss = onDismiss
                )
            }

            null -> Unit
        }
    }
}
