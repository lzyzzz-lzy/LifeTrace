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
 * 使用帮助对话框
 */
@Composable
fun HelpDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Help, contentDescription = null) },
        title = { Text("使用帮助") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // FAQ 项目
                FaqItem(
                    question = "如何开始记录旅程？",
                    answer = "在主页底部点击「开始」按钮即可开始记录。系统会自动追踪你的行程轨迹，你可以在旅途中随时添加记忆点。"
                )

                FaqItem(
                    question = "如何添加记忆点？",
                    answer = "在记录旅程时，点击底部的「+」按钮，可以选择拍照、录像或录音来添加记忆点。每个记忆点会自动关联到当前轨迹位置。"
                )

                FaqItem(
                    question = "如何查看和编辑已记录的旅程？",
                    answer = "在主页左侧侧边栏可以查看历史旅程列表。点击旅程可以查看详情，长按可以编辑或删除。"
                )

                FaqItem(
                    question = "如何选择图片生成 AI 文案？",
                    answer = "1. 在旅程详情页点击「分享」按钮\n" +
                            "2. 在分享页面选择想要的图片（最多9张）\n" +
                            "3. 点击底部「文案」按钮\n" +
                            "4. 选择文案风格，等待 AI 生成\n" +
                            "5. 复制喜欢的文案即可"
                )

                FaqItem(
                    question = "为什么 AI 文案生成失败？",
                    answer = "可能的原因：\n" +
                            "• API Key 未配置或无效 - 请在「AI 设置」中检查\n" +
                            "• 网络连接问题 - 请检查网络状态\n" +
                            "• 服务暂时不可用 - 请稍后重试"
                )

                FaqItem(
                    question = "如何分享旅程内容？",
                    answer = "在分享页面选择图片后，可以：\n" +
                            "• 点击「保存」将图片保存到相册\n" +
                            "• 点击「分享」通过系统分享功能分享\n" +
                            "• 点击「去制作长图海报」跳转到外部工具"
                )

                FaqItem(
                    question = "我的数据安全吗？",
                    answer = "所有旅程数据都存储在您的手机本地，不会上传到服务器。AI 文案功能仅会将您选择的图片发送到 AI 服务进行分析，处理完成后数据不会被保留。"
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
private fun FaqItem(
    question: String,
    answer: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.HelpOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = question,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = answer,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
