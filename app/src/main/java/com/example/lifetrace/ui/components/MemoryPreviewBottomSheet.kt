package com.example.lifetrace.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.lifetrace.data.database.entity.MemoryNodeEntity
import com.example.lifetrace.data.database.entity.MemoryAttachmentEntity
import com.example.lifetrace.data.database.entity.AttachmentType
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * 回忆预览 BottomSheet（地图 Marker 点击后显示）
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MemoryPreviewBottomSheet(
    node: MemoryNodeEntity,
    attachments: List<MemoryAttachmentEntity>,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    // 新的 Overlay 回调
    onImageClick: (MemoryAttachmentEntity, Int, Int) -> Unit = { _, _, _ -> },
    onVideoClick: (MemoryAttachmentEntity) -> Unit = { _, _ -> },
    onAudioRecorderClick: () -> Unit = { _, _ -> },
    onAudioPlayerClick: (MemoryAttachmentEntity) -> Unit = { _, _ -> },
) {
    val photoAttachments = attachments.filter { it.type == AttachmentType.PHOTO }
    val audioAttachments = attachments.filter { it.type == AttachmentType.AUDIO }
    val videoAttachments = attachments.filter { it.type == AttachmentType.VIDEO }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(
            skipPartiallyExpanded = true,
        ),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 标题和时间
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "回忆",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = formatTime(node.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))

            // 照片展示区（优先显示照片网格）
            if (photoAttachments.isNotEmpty()) {
                Text(
                    text = "照片 (${photoAttachments.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.height(if (photoAttachments.size > 3) 250.dp else 150.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(photoAttachments, key = { it.id }) { attachment ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    onImageClick(attachment, photoAttachments.indexOf(attachment), photoAttachments.size)
                                }
                        ) {
                            AsyncImage(
                                model = File(attachment.uri),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            } else if (node.coverUri != null) {
                // 如果没有照片但有封面（兼容旧数据）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            onImageClick(
                                MemoryAttachmentEntity(
                                    id = 0,
                                    memoryNodeId = node.id,
                                    type = AttachmentType.PHOTO,
                                    uri = node.coverUri!!
                                ),
                                1,
                                1
                            )
                        }
                ) {
                    AsyncImage(
                        model = File(node.coverUri!!),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 文字描述
            node.text?.let { text ->
                if (text.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Text(
                            text = text,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            // 媒体附件统计
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (photoAttachments.isNotEmpty()) {
                    AttachmentCountItem(
                        icon = Icons.Filled.PhotoLibrary,
                        count = photoAttachments.size,
                        label = "张照片"
                    )
                }
                if (audioAttachments.isNotEmpty()) {
                    AttachmentCountItem(
                        icon = Icons.Filled.Audiotrack,
                        count = audioAttachments.size,
                        label = "段音频"
                    )
                }
                if (videoAttachments.isNotEmpty()) {
                    AttachmentCountItem(
                        icon = Icons.Filled.VideoLibrary,
                        count = videoAttachments.size,
                        label = "段视频"
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("删除")
                }

                Button(
                    onClick = onEdit,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("编辑")
                }
            }
        }
    }
}

@Composable
private fun AttachmentCountItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    count: Int,
    label: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "$count $label",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000L -> "刚刚"
        diff < 3600_000L -> "${diff / 60_000L} 分钟前"
        diff < 86400_000L -> "${diff / 3600_000L} 小时前"
        diff < 604800_000L -> "${diff / 86400_000L} 天前"
        else -> {
            val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(Date(timestamp))
            date
        }
    }
}
