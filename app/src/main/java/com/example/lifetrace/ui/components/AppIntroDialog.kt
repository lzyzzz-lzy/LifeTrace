package com.example.lifetrace.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 应用介绍对话框
 */
@Composable
fun AppIntroDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Explore, contentDescription = null) },
        title = { Text("LifeTrace 应用介绍") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 应用简介
                Text(
                    text = "LifeTrace 是一款旅程记录与分享应用，帮助你记录旅途中的精彩瞬间，并用 AI 生成个性化分享文案。",
                    style = MaterialTheme.typography.bodyMedium
                )

                HorizontalDivider()

                // 核心功能
                Text(
                    text = "核心功能",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                FeatureItem(
                    icon = Icons.Filled.Route,
                    title = "旅程记录",
                    description = "实时记录你的旅行轨迹，自动追踪行程路线"
                )

                FeatureItem(
                    icon = Icons.Filled.PhotoCamera,
                    title = "记忆点",
                    description = "在旅途中拍照、录像、录音，记录精彩瞬间"
                )

                FeatureItem(
                    icon = Icons.Filled.AutoAwesome,
                    title = "AI 文案",
                    description = "智能分析旅程内容，生成个性化分享文案"
                )

                FeatureItem(
                    icon = Icons.Filled.Share,
                    title = "一键分享",
                    description = "将旅程回忆分享到社交平台"
                )

                HorizontalDivider()

                // 基本流程
                Text(
                    text = "使用流程",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "1. 点击「开始」按钮开始记录旅程\n" +
                            "2. 旅途中点击「+」添加记忆点（拍照/录像/录音）\n" +
                            "3. 结束旅程后，点击「分享」进入编辑页面\n" +
                            "4. 选择图片，点击「文案」让 AI 生成分享内容\n" +
                            "5. 复制文案或分享到社交平台",
                    style = MaterialTheme.typography.bodyMedium
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
private fun FeatureItem(
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
