package com.example.lifetrace.ui.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.lifetrace.ui.overlay.OverlayState
import java.io.File

/**
 * 全屏视频播放器（最小版）
 * 使用系统播放器，不实现复杂的控制条
 */
@Composable
fun FullScreenVideoPlayerMinimal(
    uri: String,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = onDismiss)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
        ) {
            // 视频缩略图
            AsyncImage(
                model = File(uri),
                contentDescription = null,
                modifier = Modifier.size(200.dp, 150.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(32.dp))

            androidx.compose.material3.Text(
                text = "点击下方按钮播放视频",
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = Color.White
            )

            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(48.dp))

            Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        onDismiss()
                        // 延迟后再启动系统播放器
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            // TODO: 启动系统播放器
                        }, 100)
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
                    )
                ) {
                    androidx.compose.material3.Text("播放", color = Color.White)
                }
            }
        }
    }
}
