package com.example.lifetrace.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.lifetrace.share.model.MediaType
import com.example.lifetrace.share.model.ShareMediaItem
import java.io.File
import java.io.FileInputStream

/**
 * 分享管理器
 * 处理媒体分享和保存到相册
 */
object ShareManager {

    /**
     * 分享图片
     *
     * @param context Android Context
     * @param uris 图片 URI 列表
     * @param title 分享对话框标题
     */
    fun shareImages(
        context: Context,
        uris: List<Uri>,
        title: String = "分享图片"
    ) {
        if (uris.isEmpty()) return

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, uris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "image/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        val chooserIntent = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(chooserIntent)
    }

    /**
     * 分享媒体（图片和视频混合）
     */
    fun shareMedia(
        context: Context,
        items: List<ShareMediaItem>,
        title: String = "分享媒体"
    ) {
        if (items.isEmpty()) return

        val hasVideo = items.any { it.isVideo() }
        val uris = items.map { it.getDisplayUri() }

        val mimeType = when {
            items.all { it.isPhoto() } -> "image/*"
            items.all { it.isVideo() } -> "video/*"
            else -> "*/*"
        }

        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uris.first())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mimeType
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        val chooserIntent = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(chooserIntent)
    }

    /**
     * 保存图片到相册
     *
     * @param context Android Context
     * @param uris 图片 URI 列表
     * @param tripName 旅程名称（用于文件夹）
     * @return 成功保存的数量
     */
    fun saveToGallery(
        context: Context,
        uris: List<Uri>,
        tripName: String = "LifeTrace"
    ): Int {
        if (uris.isEmpty()) return 0

        var savedCount = 0

        uris.forEach { uri ->
            try {
                val values = android.content.ContentValues().apply {
                    val fileName = "${tripName}_${System.currentTimeMillis()}.jpg"
                    put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/LifeTrace/$tripName")
                }

                val contentResolver = context.contentResolver
                val insertUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)

                insertUri?.let { outputUri ->
                    contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                        contentResolver.openInputStream(uri)?.use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    savedCount++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return savedCount
    }

    /**
     * 保存媒体到相册（支持图片和视频）
     */
    fun saveMediaToGallery(
        context: Context,
        items: List<ShareMediaItem>,
        tripName: String = "LifeTrace"
    ): Int {
        if (items.isEmpty()) return 0

        var savedCount = 0

        items.forEach { item ->
            try {
                val uri = item.getDisplayUri()
                val isVideo = item.isVideo()

                val values = android.content.ContentValues().apply {
                    val extension = if (isVideo) "mp4" else "jpg"
                    val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
                    val fileName = "${tripName}_${System.currentTimeMillis()}.${extension}"

                    if (isVideo) {
                        put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                        put(MediaStore.Video.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                        put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/LifeTrace/$tripName")
                    } else {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
                        put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/LifeTrace/$tripName")
                    }
                }

                val collectionUri = if (isVideo) {
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else {
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                }

                val contentResolver = context.contentResolver
                val insertUri = contentResolver.insert(collectionUri, values)

                insertUri?.let { outputUri ->
                    contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                        contentResolver.openInputStream(uri)?.use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    savedCount++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return savedCount
    }

    /**
     * 从文件路径获取 FileProvider URI
     */
    fun getShareableUri(context: Context, file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
