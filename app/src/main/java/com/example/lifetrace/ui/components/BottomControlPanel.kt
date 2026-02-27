package com.example.lifetrace.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.lifetrace.state.HomeUiState

@Composable
fun BottomControlPanel(
    uiState: HomeUiState,
    onStart: (String) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
    // 2. 修改 onAddMemory：接收经纬度（供添加回忆点）
    onAddMemory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    val onAddMemoryClick = {
        onAddMemory.invoke() // 调用外部传入的、已封装定位的回调
    }

    // 显示你原有设计的新建旅程弹窗
    if (showCreateDialog) {
        CreateTripDialog(
            onConfirm = {
                onStart(it)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false }
        )
    }

    Surface(
        modifier = modifier
            .widthIn(max = 380.dp)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 8.dp,
        shadowElevation = 16.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            // ===== 1. 旅程信息区（左对齐+右对齐时长，保留你的样式）=====
            if (uiState.activeTrip != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // 旅程标题（左）
                    Text(
                        text = uiState.activeTrip.title ?: "未命名旅程",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    // 时长（右，适配多格式字号）
                    Text(
                        text = uiState.durationText,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontSize = when (uiState.durationText.count { it == ':' }) {
                                1 -> 18.sp
                                2 -> 16.sp
                                3 -> 14.sp
                                else -> 18.sp
                            }
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(4.dp))

                // 状态提示（左对齐，弱化）
                Text(
                    text = when {
                        uiState.isRecording -> "正在记录轨迹…"
                        uiState.isPaused -> "记录已暂停"
                        else -> "准备开始记录"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alpha(0.8f)
                )

                Text(
                    text = "距离：${uiState.distanceText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alpha(0.75f)
                )

                // 分割线（分层）
                Divider(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )
            } else {
                // 无旅程时：居中提示
                Text(
                    text = "准备开始记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .align(Alignment.CenterHorizontally)
                )
            }

            // ===== 2. 核心操作区（复用你的 MainCircleButton，调整尺寸/布局）=====
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = when {
                    uiState.activeTrip == null -> Arrangement.Center
                    else -> Arrangement.SpaceBetween
                }
            ) {
                when {
                    // 场景1：无旅程 → 仅核心开始按钮（用你原有组件）
                    uiState.activeTrip == null -> {
                        MainCircleButton(
                            icon = Icons.Filled.PlayArrow,
                            color = MaterialTheme.colorScheme.primary,
                            breathing = false,
                            onClick = { showCreateDialog = true }
                        )
                    }

                    // 场景2：正在录制 → 次要按钮（缩小）+ 核心暂停按钮（原尺寸+呼吸）
                    uiState.isRecording -> {
                        // 左侧：结束按钮（缩小版）
                        Box(modifier = Modifier.size(48.dp)) {
                            MainCircleButton(
                                icon = Icons.Filled.Stop,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                                breathing = false,
                                onClick = onFinish
                            )
                        }

                        // 中间：暂停按钮（你的原生组件，保留64dp+呼吸动效）
                        MainCircleButton(
                            icon = Icons.Filled.Pause,
                            color = MaterialTheme.colorScheme.secondary,
                            breathing = true,
                            onClick = onPause
                        )

                        // 右侧：添加回忆（缩小版）
                        Box(modifier = Modifier.size(48.dp)) {
                            MainCircleButton(
                                icon = Icons.Filled.Place,
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f),
                                breathing = false,
                                onClick = onAddMemoryClick
                            )
                        }
                    }

                    // 场景3：已暂停 → 次要按钮（缩小）+ 核心继续按钮（原尺寸）
                    uiState.isPaused -> {
                        // 左侧：结束按钮（缩小版）
                        Box(modifier = Modifier.size(48.dp)) {
                            MainCircleButton(
                                icon = Icons.Filled.Stop,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                                breathing = false,
                                onClick = onFinish
                            )
                        }

                        // 中间：继续按钮（你的原生组件）
                        MainCircleButton(
                            icon = Icons.Filled.PlayArrow,
                            color = MaterialTheme.colorScheme.primary,
                            breathing = false,
                            onClick = onResume
                        )

                        // 右侧：添加回忆（缩小版）
                        Box(modifier = Modifier.size(48.dp)) {
                            MainCircleButton(
                                icon = Icons.Filled.Place,
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f),
                                breathing = false,
                                onClick = onAddMemory
                            )
                        }
                    }
                }
            }

            // ===== 3. 辅助提示（弱化显示）=====
            if (uiState.activeTrip != null) {
                Text(
                    text = "点击核心按钮暂停/继续，两侧按钮结束/添加回忆",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}