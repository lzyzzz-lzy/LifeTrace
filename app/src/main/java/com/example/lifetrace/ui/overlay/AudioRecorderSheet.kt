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
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Slider
import com.example.lifetrace.ui.components.AudioRecordingScreen
import com.example.lifetrace.ui.overlay.OverlayState
import com.example.lifetrace.ui.overlay.FullScreenVideoPlayerMinimal
import com.example.lifetrace.utils.formatDuration

/**
 * 音频录制 BottomSheet（Overlay 版本）
 * 在覆盖层中显示，用于预览回忆时的音频播放控制
 */
@Composable
fun AudioRecorderSheet(
    memoryNodeId: Long,
    onDismiss: () -> Unit,
) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentDuration by remember { mutableStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }

    // 模拟录制状态（预览时只显示控制条）
    LaunchedEffect(memoryNodeId) {
        // 这里可以加载已录制的音频数据
        currentDuration = 5000 // 示例：5秒
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f)),
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
                text = "音频控制",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 播放/暂停按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Button(
                    onClick = { isPlaying = !isPlaying },
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

                Slider(
                    value = (currentDuration.toFloat() / 10000f),
                    onValueChange = { currentDuration = (it * 10000).toLong() },
                    modifier = Modifier.weight(1f),
                    valueRange = 0f..100f,
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White.copy(alpha = 0.7f),
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    )
                )

                Text(
                    text = "${currentDuration / 1000}s",
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
            }
        }
    }
}
