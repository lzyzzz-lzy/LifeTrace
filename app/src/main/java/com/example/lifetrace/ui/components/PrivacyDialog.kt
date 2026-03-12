package com.example.lifetrace.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 隐私与权限说明对话框
 */
@Composable
fun PrivacyDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Security, contentDescription = null) },
        title = { Text("隐私与权限说明") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 权限用途说明
                Text(
                    text = "应用权限说明",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                PermissionItem(
                    icon = Icons.Filled.LocationOn,
                    title = "位置权限",
                    description = "用于记录您的旅行轨迹，绘制行程路线图。仅在您主动开始记录旅程时使用。"
                )

                PermissionItem(
                    icon = Icons.Filled.PhotoCamera,
                    title = "相机权限",
                    description = "用于在旅途中拍摄照片和视频，记录精彩瞬间。仅在您主动点击拍照/录像时使用。"
                )

                PermissionItem(
                    icon = Icons.Filled.Mic,
                    title = "麦克风权限",
                    description = "用于在旅途中录制音频，记录声音记忆。仅在您主动点击录音时使用。"
                )

                PermissionItem(
                    icon = Icons.Filled.Photo,
                    title = "相册/存储权限",
                    description = "用于选择照片生成分享内容，以及将生成的图片保存到相册。"
                )

                HorizontalDivider()

                // 数据说明
                Text(
                    text = "数据处理说明",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        DataItem(
                            title = "本地存储",
                            items = listOf(
                                "旅程轨迹数据",
                                "记忆点照片/视频/音频",
                                "应用设置信息"
                            )
                        )

                        HorizontalDivider()

                        DataItem(
                            title = "AI 服务处理（仅在选择使用时）",
                            items = listOf(
                                "您选择要分析的图片",
                                "旅程基本信息（用于生成文案）",
                                "处理完成后数据不保留"
                            )
                        )
                    }
                }

                Text(
                    text = "我们重视您的隐私。所有旅程数据仅存储在您的设备本地，不会上传到任何服务器。使用 AI 功能时，仅会发送您选择的内容进行分析。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("我知道了")
            }
        }
    )
}

@Composable
private fun PermissionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DataItem(
    title: String,
    items: List<String>
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        items.forEach { item ->
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = item,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
