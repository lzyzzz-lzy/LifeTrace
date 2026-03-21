package com.example.lifetrace.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.lifetrace.service.CaptionStyle
import com.example.lifetrace.share.model.FinalCaptionResult

/**
 * AI 文案生成底部弹窗（支持编辑）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptionBottomSheet(
    isVisible: Boolean,
    isLoading: Boolean,
    captions: List<String>,
    selectedCaption: String?,
    availableStyles: List<CaptionStyle> = CaptionStyle.entries,
    currentStyle: CaptionStyle = CaptionStyle.DOCUMENTARY,
    error: String?,
    onDismiss: () -> Unit,
    onStyleChange: (CaptionStyle) -> Unit,
    onCaptionSelect: (String) -> Unit,
    onRegenerate: () -> Unit,
    onCopy: (String) -> Unit,
    // 新增：编辑相关参数
    captionResult: FinalCaptionResult? = null,
    isEditing: Boolean = false,
    editableTitle: String? = null,
    editableBody: String? = null,
    editableTags: List<String> = emptyList(),
    onStartEditing: () -> Unit = {},
    onCancelEditing: () -> Unit = {},
    onSaveEditing: () -> Unit = {},
    onRestoreOriginal: () -> Unit = {},
    onUpdateTitle: (String) -> Unit = {},
    onUpdateBody: (String) -> Unit = {},
    onAddTag: (String) -> Unit = {},
    onRemoveTag: (String) -> Unit = {},
    onCopyFullText: () -> Unit = {},
    onCopyBodyOnly: () -> Unit = {}
) {
    if (!isVisible) return

    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    LaunchedEffect(isVisible) {
        if (isVisible) {
            sheetState.show()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 400.dp, max = 650.dp)
                .padding(16.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isEditing) "编辑文案" else "AI 文案生成",
                    style = MaterialTheme.typography.titleLarge
                )
                Row {
                    // 编辑/取消按钮
                    if (captionResult != null && !isLoading) {
                        if (isEditing) {
                            TextButton(onClick = onCancelEditing) {
                                Text("取消")
                            }
                        } else {
                            IconButton(onClick = onStartEditing) {
                                Icon(Icons.Filled.Edit, contentDescription = "编辑")
                            }
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 风格选择
            Text(
                text = "选择风格",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableStyles.forEach { style ->
                    StyleChip(
                        style = style,
                        isSelected = style == currentStyle,
                        onClick = { onStyleChange(style) }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // 内容区域
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    isLoading -> {
                        // 加载状态
                        LoadingContent()
                    }

                    error != null -> {
                        // 错误状态
                        ErrorContent(error = error)
                    }

                    captions.isEmpty() && captionResult == null -> {
                        // 空状态
                        EmptyContent()
                    }

                    isEditing && captionResult != null -> {
                        // 编辑模式
                        EditingContent(
                            title = editableTitle ?: "",
                            body = editableBody ?: "",
                            tags = editableTags,
                            onUpdateTitle = onUpdateTitle,
                            onUpdateBody = onUpdateBody,
                            onAddTag = onAddTag,
                            onRemoveTag = onRemoveTag
                        )
                    }

                    else -> {
                        // 查看模式
                        if (captionResult != null) {
                            CaptionResultContent(
                                result = captionResult,
                                captions = captions,
                                selectedCaption = selectedCaption,
                                onSelect = onCaptionSelect,
                                onCopy = onCopy
                            )
                        } else {
                            // 兼容旧接口
                            CaptionListContent(
                                captions = captions,
                                selectedCaption = selectedCaption,
                                onSelect = onCaptionSelect,
                                onCopy = onCopy
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // 底部按钮
            if (isEditing) {
                // 编辑模式按钮
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onRestoreOriginal,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.RestartAlt, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("恢复原文")
                    }
                    Button(
                        onClick = onSaveEditing,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("保存修改")
                    }
                }
            } else {
                // 查看模式按钮
                OutlinedButton(
                    onClick = onRegenerate,
                    enabled = !isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("重新生成")
                }
            }

            // 复制按钮（非编辑模式且有内容时显示）
            if (!isEditing && captionResult != null) {
                Spacer(Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onCopyBodyOnly,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("复制正文")
                    }
                    Button(
                        onClick = onCopyFullText,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.CopyAll, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("复制全部")
                    }
                }
            }

            // 长图海报入口
            if (!isEditing && selectedCaption != null) {
                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://seede.ai"))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("去制作长图海报")
                }
            }
        }
    }
}

/**
 * 加载状态内容
 */
@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            text = "正在生成文案...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 错误状态内容
 */
@Composable
private fun ErrorContent(error: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.ErrorOutline,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * 空状态内容
 */
@Composable
private fun EmptyContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.EditNote,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "点击生成按钮创建文案",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 编辑模式内容
 */
@Composable
private fun EditingContent(
    title: String,
    body: String,
    tags: List<String>,
    onUpdateTitle: (String) -> Unit,
    onUpdateBody: (String) -> Unit,
    onAddTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit
) {
    var newTagText by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 标题编辑
        item {
            Column {
                Text(
                    text = "标题",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = onUpdateTitle,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("输入标题") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
        }

        // 正文编辑
        item {
            Column {
                Text(
                    text = "正文",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = onUpdateBody,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 200.dp),
                    placeholder = { Text("输入正文内容") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
        }

        // 标签编辑
        item {
            Column {
                Text(
                    text = "标签",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                // 现有标签
                if (tags.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(tags) { tag ->
                            InputChip(
                                selected = false,
                                onClick = { },
                                label = { Text(tag) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { onRemoveTag(tag) },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "删除标签",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                },
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }

                // 添加新标签
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newTagText,
                        onValueChange = { newTagText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("添加标签") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                    IconButton(
                        onClick = {
                            if (newTagText.isNotBlank()) {
                                onAddTag(newTagText)
                                newTagText = ""
                            }
                        }
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "添加标签")
                    }
                }
            }
        }
    }
}

/**
 * 文案结果内容（结构化显示）
 */
@Composable
private fun CaptionResultContent(
    result: FinalCaptionResult,
    captions: List<String>,
    selectedCaption: String?,
    onSelect: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 标题
        if (result.title.isNotBlank()) {
            item {
                CaptionSection(
                    label = "标题",
                    content = result.title
                )
            }
        }

        // 正文
        if (result.body.isNotBlank()) {
            item {
                CaptionSection(
                    label = "正文",
                    content = result.body
                )
            }
        }

        // 标签
        if (result.tags.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = "标签",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = result.tags.joinToString(" "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

/**
 * 文案列表内容（兼容旧接口）
 */
@Composable
private fun CaptionListContent(
    captions: List<String>,
    selectedCaption: String?,
    onSelect: (String) -> Unit,
    onCopy: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(captions) { caption ->
            CaptionItem(
                caption = caption,
                isSelected = caption == selectedCaption,
                onSelect = { onSelect(caption) },
                onCopy = { onCopy(caption) }
            )
        }
    }
}

/**
 * 文案分区显示
 */
@Composable
private fun CaptionSection(
    label: String,
    content: String
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 风格选择芯片
 */
@Composable
private fun StyleChip(
    style: CaptionStyle,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            text = style.displayName,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 文案选项卡片
 */
@Composable
private fun CaptionItem(
    caption: String,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onCopy: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isSelected) {
                    Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(12.dp)
                    )
                }
            )
            .clickable(onClick = onSelect),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onCopy,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("复制")
                }
            }
        }
    }
}

/**
 * 复制文案到剪贴板的辅助函数
 */
fun copyCaptionToClipboard(context: Context, caption: String) {
    val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("travel_caption", caption)
    clipboardManager.setPrimaryClip(clip)
    Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
}
