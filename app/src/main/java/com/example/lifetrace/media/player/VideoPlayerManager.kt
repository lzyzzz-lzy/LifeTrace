package com.example.lifetrace.media.player

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 视频播放管理器
 * 使用 ExoPlayer API
 */
class VideoPlayerManager(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    private var positionUpdateJob: kotlinx.coroutines.Job? = null

    /**
     * 获取当前播放状态
     */
    private val playbackState: Int
        get() = exoPlayer?.playbackState ?: Player.STATE_IDLE

    /**
     * 播放视频
     */
    fun play(uri: String) {
        release()

        try {
            val player = ExoPlayer.Builder(context)
                .build()
                player.setMediaItem(MediaItem.fromUri(android.net.Uri.parse(uri)))
                player.prepare()
                player.play()

            exoPlayer = player
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
        exoPlayer?.let {
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
        exoPlayer?.let {
            if (!isPlaying.value && playbackState == Player.STATE_READY) {
                it.play()
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
        exoPlayer?.let {
            it.pause()
            it.seekTo(0L)
        }
        _isPlaying.value = false
        _currentPosition.value = 0L
    }

    /**
     * 跳转到指定位置（毫秒）
     */
    fun seekTo(position: Long) {
        exoPlayer?.let {
            if (position in 0..duration.value) {
                it.seekTo(position)
            }
        }
    }

    /**
     * 获取当前播放位置（毫秒）
     */
    fun getCurrentPosition(): Long {
        return exoPlayer?.currentPosition ?: 0L
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
        exoPlayer?.let {
            it.release()
        }
        exoPlayer = null
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
    }

    /**
     * 获取播放器实例（用于 Compose 集成）
     */
    fun getPlayer(): ExoPlayer? {
        return exoPlayer
    }

    /**
     * 启动位置更新
     */
    private fun startPositionUpdate() {
        positionUpdateJob?.cancel()
        positionUpdateJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            while (isPlaying.value) {
                kotlinx.coroutines.delay(100)
                _currentPosition.value = exoPlayer?.currentPosition ?: 0L
                _duration.value = exoPlayer?.duration ?: 0L
            }
        }
    }
}

/**
 * Compose 视频播放器视图
 */
@Composable
fun VideoPlayerView(playerManager: VideoPlayerManager, modifier: Modifier = Modifier) {
    val player = playerManager.getPlayer() ?: return

    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                this.player = player
                useController = true
            }
        },
        update = { view ->
            view.onResume()
        }
    )
}
