package com.example.lifetrace.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.lifetrace.R

@Composable
fun TopBar(
    subtitle: String? = null,
    showDelete: Boolean = false,
    onDelete: (() -> Unit)? = null,
    showShare: Boolean = false,
    onShare: (() -> Unit)? = null,
    onAppIntro: (() -> Unit)? = null,
    onAiSettings: (() -> Unit)? = null,
    onHelp: (() -> Unit)? = null,
    onPrivacy: (() -> Unit)? = null,
    onAbout: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = "LifeTrace",
                style = MaterialTheme.typography.titleLarge,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row {
            // 分享按钮（MEMORY 模式下显示）
            if (showShare && onShare != null) {
                IconButton(onClick = onShare) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = "生成海报",
                    )
                }
            }

            // 删除按钮
            if (showDelete && onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(id = R.string.top_bar_delete_trip_content_description),
                    )
                }
            } else if (!showShare) {
                // 菜单按钮（仅在没有其他按钮时显示）
                IconButton(
                    onClick = { showMenu = true },
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "菜单")
                }

                // 下拉菜单
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("应用介绍") },
                        onClick = {
                            showMenu = false
                            onAppIntro?.invoke()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("AI 设置") },
                        onClick = {
                            showMenu = false
                            onAiSettings?.invoke()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("使用帮助") },
                        onClick = {
                            showMenu = false
                            onHelp?.invoke()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("隐私与权限说明") },
                        onClick = {
                            showMenu = false
                            onPrivacy?.invoke()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("关于") },
                        onClick = {
                            showMenu = false
                            onAbout?.invoke()
                        }
                    )
                }
            }
        }
    }
}
