package com.example.lifetrace.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import com.example.lifetrace.data.database.entity.MemoryAttachmentEntity
import java.io.File

/**
 * 全屏视频播放器（简化版）
 * 使用 AlertDialog 包裹后，这里只需要显示内容和触发播放即可
 */
@Composable
fun FullScreenVideoPlayer(
    attachment: MemoryAttachmentEntity,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 视频缩略图
            AsyncImage(
                model = File(attachment.uri),
                contentDescription = null,
                modifier = Modifier.size(200.dp, 150.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )

            Spacer(Modifier.height(32.dp))

            Text(
                text = "点击下方按钮播放视频",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
        }
    }
}
