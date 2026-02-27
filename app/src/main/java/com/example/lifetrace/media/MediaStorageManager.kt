package com.example.lifetrace.media

import android.content.Context
import java.io.File

/**
 * 媒体文件存储管理器（单例）
 * 负责管理照片、音频、视频文件的存储
 */
class MediaStorageManager private constructor(private val context: Context) {

    private val baseDir: File
        get() = File(context.filesDir, "media")

    private val photoDir: File
        get() = File(baseDir, "photos")

    private val audioDir: File
        get() = File(baseDir, "audios")

    private val videoDir: File
        get() = File(baseDir, "videos")

    /**
     * 初始化存储目录
     */
    fun initialize() {
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }
        if (!photoDir.exists()) {
            photoDir.mkdirs()
        }
        if (!audioDir.exists()) {
            audioDir.mkdirs()
        }
        if (!videoDir.exists()) {
            videoDir.mkdirs()
        }
    }

    /**
     * 生成照片文件
     * 文件名格式: {tripId}_{timestamp}.jpg
     */
    fun getPhotoFile(tripId: Long): File {
        initialize()
        val timestamp = System.currentTimeMillis()
        return File(photoDir, "${tripId}_$timestamp.jpg")
    }

    /**
     * 生成音频文件
     * 文件名格式: {tripId}_{timestamp}.m4a
     */
    fun getAudioFile(tripId: Long): File {
        initialize()
        val timestamp = System.currentTimeMillis()
        return File(audioDir, "${tripId}_$timestamp.m4a")
    }

    /**
     * 生成视频文件
     * 文件名格式: {tripId}_{timestamp}.mp4
     */
    fun getVideoFile(tripId: Long): File {
        initialize()
        val timestamp = System.currentTimeMillis()
        return File(videoDir, "${tripId}_$timestamp.mp4")
    }

    /**
     * 删除指定文件
     */
    fun deleteFile(contentUrl: String?) {
        if (contentUrl.isNullOrEmpty()) return
        try {
            val file = File(contentUrl)
            if (file.exists() && file.isFile) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 清理指定旅程的所有关联文件
     */
    fun cleanupTripFiles(tripId: Long) {
        try {
            photoDir.listFiles()
                ?.filter { it.name.startsWith("${tripId}_") }
                ?.forEach { it.delete() }

            audioDir.listFiles()
                ?.filter { it.name.startsWith("${tripId}_") }
                ?.forEach { it.delete() }

            videoDir.listFiles()
                ?.filter { it.name.startsWith("${tripId}_") }
                ?.forEach { it.delete() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 获取所有媒体文件的总大小（字节）
     */
    fun getTotalMediaSize(): Long {
        var totalSize = 0L
        try {
            totalSize += photoDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            totalSize += audioDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            totalSize += videoDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return totalSize
    }

    companion object {
        @Volatile
        private var INSTANCE: MediaStorageManager? = null

        fun getInstance(context: Context): MediaStorageManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MediaStorageManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
