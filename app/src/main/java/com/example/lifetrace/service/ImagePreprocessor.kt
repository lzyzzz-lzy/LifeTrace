package com.example.lifetrace.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream

/**
 * 图片预处理服务
 * 用于压缩图片并转换为 Base64 编码
 */
class ImagePreprocessor(private val context: Context) {

    companion object {
        private const val TAG = "ImagePreprocessor"

        // 压缩配置
        private const val MAX_LONG_EDGE = 1024  // 长边最大像素
        private const val MAX_FILE_SIZE = 500 * 1024  // 最大文件大小 500KB
        private const val INITIAL_QUALITY = 85  // 初始压缩质量
        private const val MIN_QUALITY = 50  // 最小压缩质量
    }

    /**
     * 图片预处理结果
     */
    data class PreprocessResult(
        val success: Boolean,
        val base64Data: String? = null,
        val mimeType: String = "image/jpeg",
        val width: Int = 0,
        val height: Int = 0,
        val fileSize: Int = 0,
        val error: String? = null
    )

    /**
     * 预处理单个图片
     * @param uri 图片 Uri
     * @return 预处理结果
     */
    suspend fun preprocessImage(uri: Uri): PreprocessResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "开始预处理图片: $uri")

            // 1. 读取图片
            val inputStream = openInputStreamSafely(uri)
            if (inputStream == null) {
                return@withContext PreprocessResult(
                    success = false,
                    error = "无法打开图片文件"
                )
            }

            // 2. 解码图片（获取尺寸但不加载全部像素）
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            val originalWidth = options.outWidth
            val originalHeight = options.outHeight

            if (originalWidth <= 0 || originalHeight <= 0) {
                return@withContext PreprocessResult(
                    success = false,
                    error = "无效的图片尺寸"
                )
            }

            Log.d(TAG, "原始尺寸: ${originalWidth}x${originalHeight}")

            // 3. 计算缩放比例
            val longEdge = maxOf(originalWidth, originalHeight)
            val sampleSize = if (longEdge > MAX_LONG_EDGE) {
                (longEdge.toFloat() / MAX_LONG_EDGE).toInt().coerceAtLeast(1)
            } else {
                1
            }

            // 4. 重新读取并缩放图片
            val inputStream2 = openInputStreamSafely(uri)
            val loadOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            var bitmap = BitmapFactory.decodeStream(inputStream2, null, loadOptions)
            inputStream2?.close()

            if (bitmap == null) {
                return@withContext PreprocessResult(
                    success = false,
                    error = "无法解码图片"
                )
            }

            // 5. 如果缩放后仍然太大，进行二次缩放
            val currentLongEdge = maxOf(bitmap.width, bitmap.height)
            if (currentLongEdge > MAX_LONG_EDGE) {
                val scale = MAX_LONG_EDGE.toFloat() / currentLongEdge
                val scaledWidth = (bitmap.width * scale).toInt()
                val scaledHeight = (bitmap.height * scale).toInt()

                val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
                if (scaledBitmap != bitmap) {
                    bitmap.recycle()
                    bitmap = scaledBitmap
                }
            }

            Log.d(TAG, "缩放后尺寸: ${bitmap.width}x${bitmap.height}")

            // 6. 检查并修正图片方向（从 Exif 读取）
            val rotatedBitmap = rotateImageIfRequired(context, uri, bitmap)
            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
                bitmap = rotatedBitmap
            }

            // 7. 压缩为 JPEG 并控制大小
            val outputStream = ByteArrayOutputStream()
            var quality = INITIAL_QUALITY
            var compressedSize: Int
            val finalWidth = bitmap.width
            val finalHeight = bitmap.height

            do {
                outputStream.reset()
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
                compressedSize = outputStream.size()

                if (compressedSize > MAX_FILE_SIZE && quality > MIN_QUALITY) {
                    quality -= 5
                }
            } while (compressedSize > MAX_FILE_SIZE && quality > MIN_QUALITY)

            bitmap.recycle()

            Log.d(TAG, "压缩后大小: ${compressedSize / 1024}KB, 质量: $quality")

            // 8. 转换为 Base64
            val base64Data = Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
            val base64WithPrefix = "data:image/jpeg;base64,$base64Data"

            return@withContext PreprocessResult(
                success = true,
                base64Data = base64WithPrefix,
                mimeType = "image/jpeg",
                width = finalWidth,
                height = finalHeight,
                fileSize = compressedSize
            )

        } catch (e: Exception) {
            Log.e(TAG, "图片预处理失败", e)
            PreprocessResult(
                success = false,
                error = "预处理失败: ${e.message}"
            )
        }
    }

    /**
     * 批量预处理图片
     * @param uris 图片 Uri 列表
     * @return 预处理结果列表
     */
    suspend fun preprocessImages(uris: List<Uri>): List<PreprocessResult> {
        return uris.map { preprocessImage(it) }
    }

    /**
     * 检查并修正图片方向
     */
    private fun rotateImageIfRequired(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        try {
            val inputStream = openInputStreamSafely(uri) ?: return bitmap
            val exif = androidx.exifinterface.media.ExifInterface(inputStream)
            inputStream.close()

            val orientation = exif.getAttributeInt(
                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL
            )

            val rotation = when (orientation) {
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }

            if (rotation == 0f) {
                return bitmap
            }

            val matrix = Matrix().apply { postRotate(rotation) }
            val rotatedBitmap = Bitmap.createBitmap(
                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
            )

            return rotatedBitmap ?: bitmap

        } catch (e: Exception) {
            Log.e(TAG, "读取 Exif 失败", e)
            return bitmap
        }
    }

    /**
     * 优先按 content resolver 打开流，失败时回退到文件路径。
     *
     * 兼容以下格式：
     * 1) content://xxx
     * 2) file:///xxx
     * 3) /data/user/... 这种无 scheme 的绝对路径
     */
    private fun openInputStreamSafely(uri: Uri): InputStream? {
        return try {
            context.contentResolver.openInputStream(uri)
        } catch (e: Exception) {
            val file = when {
                uri.scheme == null || uri.scheme.isNullOrBlank() -> File(uri.toString())
                uri.scheme.equals("file", ignoreCase = true) -> File(uri.path ?: "")
                else -> null
            }

            if (file != null && file.exists() && file.isFile) {
                FileInputStream(file)
            } else {
                null
            }
        }
    }

    /**
     * 检查 Uri 是否有效
     */
    fun isUriValid(uri: Uri): Boolean {
        return try {
            val file = File(uri.path ?: "")
            if (file.exists()) return true

            val inputStream = context.contentResolver.openInputStream(uri)
            inputStream?.close()
            true
        } catch (e: Exception) {
            false
        }
    }
}
