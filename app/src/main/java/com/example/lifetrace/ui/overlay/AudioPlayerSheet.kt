package com.example.lifetrace.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.lifetrace.media.player.VideoPlayerManager
import com.example.lifetrace.utils.formatDuration

@Composable
fun AudioPlayerSheet(
    uri: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val playerManager = remember { VideoPlayerManager(context) }

    // ✅ 订阅播放器状态
    val isPlaying by playerManager.isPlaying.collectAsState()
    val currentPosition by playerManager.currentPosition.collectAsState()
    val duration by playerManager.duration.collectAsState()

    var sliderPosition by remember { mutableStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }

    // ✅ 启动播放（只写一次）
    LaunchedEffect(uri) {
        playerManager.play(uri)
    }

    // ✅ 同步 slider（拖动时不抢）
    LaunchedEffect(currentPosition, duration, isSeeking) {
        if (!isSeeking) {
            sliderPosition =
                if (duration > 0) currentPosition.toFloat() / duration.toFloat()
                else 0f
        }
    }

    // ✅ 关闭/卸载时释放资源（只保留一个）
    DisposableEffect(Unit) {
        onDispose {
            playerManager.release()
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
            Text(
                text = "音频播放",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            Slider(
                value = sliderPosition,
                onValueChange = {
                    isSeeking = true
                    sliderPosition = it
                },
                onValueChangeFinished = {
                    val target = (sliderPosition * duration).toLong()
                    playerManager.seekTo(target)
                    isSeeking = false
                },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White.copy(alpha = 0.7f),
                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatDuration(currentPosition),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = formatDuration(duration),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = {
                        if (isPlaying) playerManager.pause() else playerManager.resume()
                    }
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null
                    )
                }

                FilledIconButton(
                    onClick = { playerManager.stop() }
                ) {
                    Icon(Icons.Filled.Stop, contentDescription = null)
                }

                OutlinedButton(
                    onClick = {
                        onDismiss()
                    }
                ) {
                    Text("关闭")
                }
            }
        }
    }
}