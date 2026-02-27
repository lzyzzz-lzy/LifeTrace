package com.example.lifetrace.ui.components

import android.Manifest
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.lifetrace.media.MediaStorageManager
import com.example.lifetrace.utils.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/**
 * 录音界面（简化版 - 直接使用 MediaRecorder）
 */
@Composable
fun AudioRecordingScreen(
    onRecordingComplete: (String, Long) -> Unit,  // (文件路径, 时长毫秒)
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val mediaStorageManager = remember { MediaStorageManager.getInstance(context) }

    var outputFile by remember { mutableStateOf(mediaStorageManager.getAudioFile(0L)) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var isRecording by remember { mutableStateOf(false) }
    var currentDuration by remember { mutableStateOf(0L) }
    var showReview by remember { mutableStateOf(false) }
    var startTime by remember { mutableStateOf(0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var permissionRequested by remember { mutableStateOf(false) }

    // 权限 Launcher
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            errorMessage = "录音权限被拒绝，请在设置中开启"
            scope.launch {
                delay(2000)
                onCancel()
            }
        }
    }

    // 首次启动时请求权限（添加 key 防止重复触发）
    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // 持续更新时长显示
    LaunchedEffect(isRecording) {
        if (isRecording) {
            while (isRecording) {
                kotlinx.coroutines.delay(100)
                currentDuration = System.currentTimeMillis() - startTime
            }
        }
    }

    // 开始录音
    val scope1 = rememberCoroutineScope()

    fun startRecording() {
        try {
            outputFile = mediaStorageManager.getAudioFile(0L)
            errorMessage = null

            val recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            isRecording = true
            startTime = System.currentTimeMillis()
            currentDuration = 0L
        } catch (e: Exception) {
            e.printStackTrace()
            errorMessage = "录音启动失败: ${e.message}"

            scope1.launch {
                delay(2000)
                // 这里写你想做的事，比如清掉错误提示
                // errorMessage = null
                // 或者 onCancel()
            }
        }
    }

    // 停止录音
    fun stopRecording() {
        mediaRecorder?.let {
            try {
                it.stop()
                it.reset()
                it.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        mediaRecorder = null
        isRecording = false
        currentDuration = System.currentTimeMillis() - startTime
        showReview = true
    }

    // 释放资源
    DisposableEffect(Unit) {
        onDispose {
            mediaRecorder?.let {
                try {
                    it.release()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 标题
            Spacer(Modifier.height(40.dp))
            Text(
                text = if (showReview) "预览录音" else "录制音频",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            // 错误提示
            errorMessage?.let { error ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(80.dp))

            // 录音图标动画
            if (!showReview) {
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.2f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    )
                )

                Box(
                    modifier = Modifier.size(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(if (isRecording) 100.dp else 60.dp)
                    )
                }

                // 录音状态指示
                if (isRecording) {
                    Box(
                        modifier = Modifier
                            .size(140.dp, 140.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), CircleShape)
                    ) {}
                }
            }

            // 时长显示
            Text(
                text = formatDuration(currentDuration),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                fontSize = 42.sp
            )

            Spacer(Modifier.weight(1f))

            // 操作按钮
            if (!showReview) {
                // 录音中：停止按钮
                if (isRecording) {
                    Button(
                        onClick = { stopRecording() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.size(80.dp, 80.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Stop,
                            contentDescription = "停止",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                } else {
                    // 未开始：开始录音按钮
                    Button(
                        onClick = { startRecording() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.size(80.dp, 80.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "开始录音",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            } else {
                // 预览模式：重录/确认
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    Button(
                        onClick = {
                            outputFile = mediaStorageManager.getAudioFile(0L)
                            showReview = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("重录")
                    }

                    Button(
                        onClick = {
                            onRecordingComplete(outputFile.absolutePath, currentDuration)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("确认")
                    }
                }
            }

            Spacer(Modifier.height(40.dp))

            // 取消按钮
            if (!isRecording) {
                TextButton(onClick = onCancel) {
                    Text("取消")
                }
            }
        }
    }
}
