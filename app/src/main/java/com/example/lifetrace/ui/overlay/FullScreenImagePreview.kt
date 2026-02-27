package com.example.lifetrace.ui.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
 * 全屏图片预览（Overlay 版本）
 */
@Composable
fun FullScreenImagePreview(
    uri: String,
    onDismiss: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = onDismiss)
    ) {
        // 图片
        AsyncImage(
            model = File(uri),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit
        )

        // 关闭按钮
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .padding(16.dp)
                .align(Alignment.TopEnd)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "关闭",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}
