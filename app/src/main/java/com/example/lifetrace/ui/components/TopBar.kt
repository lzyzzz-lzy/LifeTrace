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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    modifier: Modifier = Modifier,
) {
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
                    onClick = {/*todo 菜单*/},
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = null)
                }
            }
        }
    }
}