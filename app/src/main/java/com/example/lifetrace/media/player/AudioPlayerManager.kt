package com.example.lifetrace.media.player

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 音频播放管理器
 * 封装 MediaPlayer API
 */
class AudioPlayerManager(private val context: Context) {

    private var mediaPlayer: MediaPlayer? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    private var positionUpdateJob: kotlinx.coroutines.Job? = null

    /**
     * 播放音频
     */
    fun play(uri: String) {
        release()

        try {
            val player = MediaPlayer()
            player.setDataSource(uri)
            player.prepare()
            player.start()

            mediaPlayer = player
            _duration.value = player.duration.toLong()
            _isPlaying.value = true

            startPositionUpdate()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 暂停播放
     */
    fun pause() {
        mediaPlayer?.let {
            if (isPlaying.value) {
                it.pause()
                _isPlaying.value = false
            }
        }
    }

    /**
     * 继续播放
     */
    fun resume() {
        mediaPlayer?.let {
            if (!isPlaying.value) {
                it.start()
                _isPlaying.value = true
                startPositionUpdate()
            }
        }
    }

    /**
     * 停止播放
     */
    fun stop() {
        positionUpdateJob?.cancel()
        mediaPlayer?.let {
            it.stop()
            it.reset()
        }
        _isPlaying.value = false
        _currentPosition.value = 0L
    }

    /**
     * 跳转到指定位置（毫秒）
     */
    fun seekTo(position: Long) {
        mediaPlayer?.let {
            if (position in 0..duration.value) {
                it.seekTo(position.toInt())
            }
        }
    }

    /**
     * 获取当前播放位置（毫秒）
     */
    fun getCurrentPosition(): Long {
        return mediaPlayer?.currentPosition?.toLong() ?: 0L
    }

    /**
     * 获取总时长（毫秒）
     */
    fun getDuration(): Long {
        return _duration.value
    }

    /**
     * 释放资源
     */
    fun release() {
        positionUpdateJob?.cancel()
        mediaPlayer?.let {
            try {
                it.stop()
                it.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        mediaPlayer = null
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
    }

    /**
     * 启动位置更新
     */
    private fun startPositionUpdate() {
        positionUpdateJob?.cancel()
        positionUpdateJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            while (isPlaying.value) {
                kotlinx.coroutines.delay(100)
                _currentPosition.value = mediaPlayer?.currentPosition?.toLong() ?: 0L
            }
        }
    }
}
