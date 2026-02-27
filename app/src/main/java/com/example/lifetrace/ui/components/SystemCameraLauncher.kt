package com.example.lifetrace.ui.components

import android.Manifest
import android.media.MediaMetadataRetriever
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.example.lifetrace.media.MediaStorageManager
import java.io.File

/**
 * 系统相机模式
 */
enum class SystemCameraMode {
    PHOTO,   // 拍照
    VIDEO    // 录像
}

/**
 * 系统相机启动器
 * 用于调用系统相机拍照和录像
 */
@Composable
fun SystemCameraLauncher(
    mode: SystemCameraMode,
    tripId: Long,
    onResult: (String?, Long?) -> Unit,  // (文件路径, 时长毫秒)
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val mediaStorageManager = remember(context) { MediaStorageManager.getInstance(context) }

    // 生成输出文件（mode/tripId 变化时更新）
    val outputFile = remember(mode, tripId) {
        when (mode) {
            SystemCameraMode.PHOTO -> mediaStorageManager.getPhotoFile(tripId)
            SystemCameraMode.VIDEO -> mediaStorageManager.getVideoFile(tripId)
        }
    }

    // 获取文件 URI（outputFile 变化时更新）
    val fileUri = remember(outputFile) {
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outputFile
        )
    }

    // 拍照 Launcher（先声明，后面 permissionLauncher 才能用）
    val photoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && outputFile.exists()) {
            onResult(outputFile.absolutePath, null)
        } else {
            onCancel()
        }
    }

    // 录像 Launcher（先声明）
    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success && outputFile.exists()) {
            val duration = getVideoDuration(outputFile)
            onResult(outputFile.absolutePath, duration)
        } else {
            onCancel()
        }
    }

    // 权限 Launcher（放在 photo/video 之后，避免前向引用报错）
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            when (mode) {
                SystemCameraMode.PHOTO -> photoLauncher.launch(fileUri)
                SystemCameraMode.VIDEO -> videoLauncher.launch(fileUri)
            }
        } else {
            onCancel()
        }
    }

    // 首次启动时请求权限并拉起相机
    LaunchedEffect(mode, tripId) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // 显示加载中界面
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = Color.White)
    }
}

/**
 * 获取视频时长（毫秒）
 * 添加文件存在检查，避免过早读取
 */
private fun getVideoDuration(file: File): Long {
    return try {
        // 检查文件是否存在并可读
        if (!file.exists() || file.length() < 1024) {
            return 0L
        }

        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(file.absolutePath)
        val d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
        retriever.release()
        d
    } catch (_: Exception) {
        0L
    }
}