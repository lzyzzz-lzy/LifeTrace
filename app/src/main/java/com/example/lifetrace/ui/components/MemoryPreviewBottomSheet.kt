package com.example.lifetrace.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import coil.compose.AsyncImage
import com.example.lifetrace.data.database.entity.MemoryNodeEntity
import com.example.lifetrace.data.database.entity.MemoryAttachmentEntity
import com.example.lifetrace.data.database.entity.AttachmentType
import com.example.lifetrace.media.player.VideoPlayerManager
import com.example.lifetrace.utils.formatDuration
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

    // Overlay 回调
    onImageClick: (MemoryAttachmentEntity, Int, Int) -> Unit = { _, _, _ -> },
    onVideoClick: (MemoryAttachmentEntity) -> Unit = { _ -> },
    onAudioRecorderClick: () -> Unit = { },
    onAudioPlayerClick: (MemoryAttachmentEntity) -> Unit = { _ -> },
) {
    val context = LocalContext.current
    val photoAttachments = attachments.filter { it.type == AttachmentType.PHOTO }
    val audioAttachments = attachments.filter { it.type == AttachmentType.AUDIO }
    val videoAttachments = attachments.filter { it.type == AttachmentType.VIDEO }

    // 内嵌音频播放器状态
    val audioPlayerManager = remember { VideoPlayerManager(context) }
    var currentPlayingAudioId by remember { mutableStateOf<Long?>(null) }

    // 订阅播放器状态
    val isPlaying by audioPlayerManager.isPlaying.collectAsState()
    val currentPosition by audioPlayerManager.currentPosition.collectAsState()
    val duration by audioPlayerManager.duration.collectAsState()

    // 清理播放器资源
    DisposableEffect(Unit) {
        onDispose {
            audioPlayerManager.release()
        }
    }

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
                Spacer(Modifier.height(16.dp))
            }

            // 视频展示区（缩略图网格）
            if (videoAttachments.isNotEmpty()) {
                Text(
                    text = "视频 (${videoAttachments.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.height(if (videoAttachments.size > 3) 250.dp else 150.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(videoAttachments, key = { it.id }) { video ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onVideoClick(video) }
                        ) {
                            // 显示视频缩略图
                            AsyncImage(
                                model = video.thumbnailUri?.let { File(it) }
                                    ?: File(video.uri),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            // 播放图标覆盖层
                            Icon(
                                imageVector = Icons.Filled.PlayCircle,
                                contentDescription = "播放视频",
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(48.dp),
                                tint = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // 音频展示区（内嵌播放器）
            if (audioAttachments.isNotEmpty()) {
                Text(
                    text = "音频 (${audioAttachments.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    audioAttachments.forEach { audio ->
                        val isCurrentAudio = currentPlayingAudioId == audio.id
                        val progress = if (isCurrentAudio && duration > 0) {
                            currentPosition.toFloat() / duration.toFloat()
                        } else 0f

                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = if (isCurrentAudio)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Audiotrack,
                                        contentDescription = null,
                                        tint = if (isCurrentAudio)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "音频",
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            if (isCurrentAudio && isPlaying)
                                                "${formatDuration(currentPosition)} / ${formatDuration(duration)}"
                                            else
                                                formatDuration(audio.duration),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    // 播放/暂停按钮
                                    IconButton(
                                        onClick = {
                                            if (isCurrentAudio) {
                                                if (isPlaying) {
                                                    audioPlayerManager.pause()
                                                } else {
                                                    audioPlayerManager.resume()
                                                }
                                            } else {
                                                // 播放新音频
                                                audioPlayerManager.play(audio.uri)
                                                currentPlayingAudioId = audio.id
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isCurrentAudio && isPlaying)
                                                Icons.Filled.Pause
                                            else
                                                Icons.Filled.PlayArrow,
                                            contentDescription = if (isCurrentAudio && isPlaying) "暂停" else "播放",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    // 停止按钮（仅在播放时显示）
                                    if (isCurrentAudio) {
                                        IconButton(
                                            onClick = {
                                                audioPlayerManager.stop()
                                                currentPlayingAudioId = null
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Stop,
                                                contentDescription = "停止",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                                // 进度条（仅在播放时显示）
                                if (isCurrentAudio) {
                                    Spacer(Modifier.height(8.dp))
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
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
