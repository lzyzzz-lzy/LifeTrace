package com.example.lifetrace.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.lifetrace.ui.overlay.OverlayState
import com.example.lifetrace.media.player.VideoPlayerManager
import com.example.lifetrace.media.player.VideoPlayerView
import com.example.lifetrace.ui.overlay.FullScreenVideoPlayerMinimal
import com.example.lifetrace.utils.formatDuration

/**
 * 音频播放 BottomSheet（Overlay 版本）
 * 在覆盖层中显示，用于预览回忆时的音频播放
 */
@Composable
fun AudioPlayerSheet(
    uri: String,
    onDismiss: () -> Unit,
) {
    val videoPlayerManager = VideoPlayerManager.getInstance()

    val isPlaying by videoPlayerManager.isPlaying.collectAsState()
    val currentPosition by videoPlayerManager.currentPosition.collectAsState()
    val duration by videoPlayerManager.duration.collectAsState()

    // 启动播放
    LaunchedEffect(uri) {
        videoPlayerManager.play(uri)
    }

    // 组件卸载时停止播放
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            videoPlayerManager.stop()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 顶部标题
            Text(
                text = "音频播放",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 播放控制
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (isPlaying) {
                            videoPlayerManager.pause()
                        } else {
                            videoPlayerManager.resume()
                        }
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.size(64.dp)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // 进度条
                androidx.compose.foundation.layout.Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(
                                if (duration > 0) Color.White.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.3f)
                            ),
                        contentAlignment = androidx.compose.ui.Alignment.CenterStart
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(Color.White)
                                .contentAlignment = androidx.compose.ui.Alignment.CenterStart
                        ) {
                            val progress = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(
                                        androidx.compose.material3.MaterialTheme.colorScheme.primary
                                    ),
                                contentAlignment = androidx.compose.ui.Alignment.CenterStart
                            ) {
                                Text(
                                    text = formatDuration(currentPosition),
                                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // 时间显示
                Text(
                    text = "${formatDuration(currentPosition)} / ${formatDuration(duration)}",
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    modifier = Modifier.width(80.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White
                    )
                ) {
                    Text("关闭", color = Color.White)
                }

                androidx.compose.material3.IconButton(
                    onClick = { videoPlayerManager.stop() },
                    modifier = Modifier.size(48.dp)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Filled.Stop,
                        contentDescription = "停止",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
