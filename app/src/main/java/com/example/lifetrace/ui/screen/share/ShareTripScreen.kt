package com.example.lifetrace.ui.screen.share

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.lifetrace.service.CaptionService
import com.example.lifetrace.share.ShareManager
import com.example.lifetrace.share.model.MediaSection
import com.example.lifetrace.share.model.ShareMediaItem
import com.example.lifetrace.ui.components.ApiKeyConfigDialog
import com.example.lifetrace.ui.components.CaptionBottomSheet
import com.example.lifetrace.ui.components.copyCaptionToClipboard

/**
 * 分享旅程页面
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareTripScreen(
    tripId: Long,
    onDismiss: () -> Unit,
    viewModel: ShareTripViewModel = viewModel(factory = ShareTripViewModelFactory(tripId))
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 保存结果状态
    var saveResult by remember { mutableStateOf<String?>(null) }

    // API Key 配置对话框状态
    var showApiKeyDialog by remember { mutableStateOf(false) }
    val captionService = remember { CaptionService(context) }

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
                .fillMaxHeight(0.9f)
        ) {
            // 标题栏
            TopAppBar(
                title = {
                    Column {
                        Text("分享旅程")
                        if (uiState.hasSelection()) {
                            Text(
                                text = "已选 ${uiState.getSelectedCount()} 项",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (uiState.getTotalMediaCount() > 0) {
                        TextButton(onClick = { viewModel.toggleSelectAll() }) {
                            Text(if (uiState.isAllSelected()) "取消全选" else "全选")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )

            // 内容区域
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.sections.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "暂无可分享的媒体",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    uiState.sections.forEach { section ->
                        // 分组标题
                        item {
                            SectionHeader(
                                title = section.title,
                                selectedCount = section.getSelectedCount(uiState.selectedIds),
                                totalCount = section.items.size
                            )
                        }

                        // 媒体网格
                        mediaGrid(
                            items = section.items,
                            selectedIds = uiState.selectedIds,
                            optimizedMap = uiState.optimizedMap,
                            onItemClick = { viewModel.toggleSelect(it) }
                        )
                    }

                    // 底部间距
                    item {
                        Spacer(Modifier.height(80.dp))
                    }
                }
            }

            // 底部操作栏
            if (uiState.hasSelection()) {
                SelectionBar(
                    selectedCount = uiState.getSelectedCount(),
                    isGeneratingCaption = uiState.isGeneratingCaption,
                    onGenerateCaption = {
                        if (!viewModel.isApiConfigured()) {
                            // 显示 API Key 配置对话框
                            showApiKeyDialog = true
                        } else if (uiState.captionOptions.isNotEmpty()) {
                            // 已有缓存，直接显示
                            viewModel.setShowCaptionDialog(true)
                        } else {
                            // 无缓存，生成
                            viewModel.generateCaptions()
                        }
                    },
                    onSave = {
                        val selectedItems = viewModel.getSelectedItems()
                        val savedCount = ShareManager.saveMediaToGallery(
                            context = context,
                            items = selectedItems.map { item ->
                                val optimizedUri = uiState.optimizedMap[item.id]
                                item.copy(optimizedUri = optimizedUri)
                            },
                            tripName = uiState.trip?.title ?: "LifeTrace"
                        )
                        saveResult = "已保存 $savedCount 项到相册"
                        Toast.makeText(context, saveResult, Toast.LENGTH_SHORT).show()
                    },
                    onShare = {
                        val selectedItems = viewModel.getSelectedItems()
                        ShareManager.shareMedia(
                            context = context,
                            items = selectedItems.map { item ->
                                val optimizedUri = uiState.optimizedMap[item.id]
                                item.copy(optimizedUri = optimizedUri)
                            },
                            title = "分享旅程回忆"
                        )
                    }
                )
            }
        }
    }

    // AI 文案生成弹窗
    CaptionBottomSheet(
        isVisible = uiState.showCaptionDialog,
        isLoading = uiState.isGeneratingCaption,
        captions = uiState.captionOptions,
        selectedCaption = uiState.selectedCaption,
        currentStyle = uiState.currentCaptionStyle,
        error = uiState.captionError,
        onDismiss = { viewModel.setShowCaptionDialog(false) },
        onStyleChange = { viewModel.setCurrentCaptionStyle(it) },
        onCaptionSelect = { viewModel.setSelectedCaption(it) },
        onRegenerate = { viewModel.regenerateCaptions() },
        onCopy = { caption ->
            copyCaptionToClipboard(context, caption)
        },
        // 编辑相关参数
        captionResult = uiState.captionResult,
        isEditing = uiState.isEditing,
        editableTitle = uiState.editableCaptionTitle,
        editableBody = uiState.editableCaptionBody,
        editableTags = uiState.editableCaptionTags,
        onStartEditing = { viewModel.startEditing() },
        onCancelEditing = { viewModel.cancelEditing() },
        onSaveEditing = { viewModel.saveEditing() },
        onRestoreOriginal = { viewModel.restoreOriginal() },
        onUpdateTitle = { viewModel.updateEditableTitle(it) },
        onUpdateBody = { viewModel.updateEditableBody(it) },
        onAddTag = { viewModel.addTag(it) },
        onRemoveTag = { viewModel.removeTag(it) },
        onCopyFullText = {
            copyCaptionToClipboard(context, viewModel.getShareText())
        },
        onCopyBodyOnly = {
            copyCaptionToClipboard(context, viewModel.getBodyText())
        }
    )

    // API Key 配置对话框
    ApiKeyConfigDialog(
        isVisible = showApiKeyDialog,
        currentApiKey = "",
        onSave = { apiKey ->
            captionService.saveApiConfig(apiKey)
            showApiKeyDialog = false
            Toast.makeText(context, "API Key 已保存", Toast.LENGTH_SHORT).show()
            // 保存后自动开始生成
            viewModel.generateCaptions()
        },
        onDismiss = { showApiKeyDialog = false }
    )
}

/**
 * 分组标题
 */
@Composable
private fun SectionHeader(
    title: String,
    selectedCount: Int,
    totalCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        if (selectedCount > 0) {
            Text(
                text = "$selectedCount/$totalCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 媒体网格
 */
private fun LazyListScope.mediaGrid(
    items: List<ShareMediaItem>,
    selectedIds: Set<String>,
    optimizedMap: Map<String, Uri>,
    onItemClick: (String) -> Unit
) {
    val rows = items.chunked(3)
    rows.forEach { row ->
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { item ->
                    MediaItem(
                        item = item,
                        isSelected = item.id in selectedIds,
                        isOptimized = item.id in optimizedMap,
                        onClick = { onItemClick(item.id) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // 填充空白
                repeat(3 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * 单个媒体项
 */
@Composable
private fun MediaItem(
    item: ShareMediaItem,
    isSelected: Boolean,
    isOptimized: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
    ) {
        // 图片/视频缩略图
        AsyncImage(
            model = item.uri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // 视频图标
        if (item.isVideo()) {
            Icon(
                Icons.Filled.PlayCircle,
                contentDescription = "视频",
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp),
                tint = Color.White.copy(alpha = 0.8f)
            )
        }

        // AI 优化标记
        if (isOptimized) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "AI",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                    color = Color.White
                )
            }
        }

        // 选中状态遮罩
        if (isSelected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            )

            // 选中图标
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "已选中",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .zIndex(1f),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * 底部操作栏
 */
@Composable
private fun SelectionBar(
    selectedCount: Int,
    isGeneratingCaption: Boolean,
    onGenerateCaption: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 已选数量
            Text(
                text = "已选 $selectedCount 项",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 操作按钮
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 文案按钮
                OutlinedButton(
                    onClick = onGenerateCaption,
                    enabled = !isGeneratingCaption
                ) {
                    if (isGeneratingCaption) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("文案")
                }

                // 保存按钮
                OutlinedButton(onClick = onSave) {
                    Icon(
                        Icons.Filled.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("保存")
                }

                // 分享按钮
                Button(onClick = onShare) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("分享")
                }
            }
        }
    }
}
