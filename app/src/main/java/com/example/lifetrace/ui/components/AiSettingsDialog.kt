package com.example.lifetrace.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.lifetrace.service.CaptionService

/**
 * AI 设置对话框
 */
@Composable
fun AiSettingsDialog(
    isVisible: Boolean,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    val context = LocalContext.current
    val captionService = remember { CaptionService(context) }

    // 从 SharedPreferences 读取当前配置
    var apiKey by remember { mutableStateOf("") }
    var modelId by remember { mutableStateOf("") }
    var showApiKey by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    // 初始化时读取配置
    LaunchedEffect(isVisible) {
        if (isVisible) {
            // 使用反射读取 SharedPreferences
            val prefs = context.getSharedPreferences("lifetrace_config", android.content.Context.MODE_PRIVATE)
            apiKey = prefs.getString("doubao_api_key", "") ?: ""
            modelId = prefs.getString("doubao_model_id", "doubao-seed-2-0-pro-260215") ?: "doubao-seed-2-0-pro-260215"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
        title = { Text("AI 设置") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 说明文字
                Text(
                    text = "配置火山引擎豆包 API，用于生成旅行分享文案和图片分析。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // API Key 输入框
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = {
                        apiKey = it
                        testResult = null
                    },
                    label = { Text("API Key") },
                    placeholder = { Text("输入您的 API Key") },
                    singleLine = true,
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        TextButton(
                            onClick = { showApiKey = !showApiKey }
                        ) {
                            Text(if (showApiKey) "隐藏" else "显示")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                // 模型 ID 输入框
                OutlinedTextField(
                    value = modelId,
                    onValueChange = {
                        modelId = it
                        testResult = null
                    },
                    label = { Text("模型 ID") },
                    placeholder = { Text("doubao-seed-2-0-pro-260215") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 获取方式说明
                Text(
                    text = "获取方式：火山引擎控制台 → 方舟 → API Key 管理",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider()

                // 测试结果
                if (testResult != null) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (testResult!!.startsWith("成功"))
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = testResult!!,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // 测试按钮
                OutlinedButton(
                    onClick = {
                        if (apiKey.isBlank()) {
                            testResult = "请先输入 API Key"
                            return@OutlinedButton
                        }
                        isTesting = true
                        testResult = null
                        // 简单验证：API Key 格式
                        if (apiKey.length < 20) {
                            testResult = "API Key 格式不正确"
                            isTesting = false
                        } else {
                            testResult = "API Key 格式正确，保存后即可使用"
                            isTesting = false
                        }
                    },
                    enabled = !isTesting && apiKey.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("测试中...")
                    } else {
                        Icon(Icons.Filled.Check, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("验证配置")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (apiKey.isNotBlank()) {
                        captionService.saveApiConfig(apiKey, modelId.ifBlank { "doubao-seed-2-0-pro-260215" })
                        Toast.makeText(context, "设置已保存", Toast.LENGTH_SHORT).show()
                    }
                    onDismiss()
                },
                enabled = apiKey.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
