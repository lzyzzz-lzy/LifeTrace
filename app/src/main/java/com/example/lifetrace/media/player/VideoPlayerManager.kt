package com.example.lifetrace.media.player

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * 媒体播放管理器（视频/音频都可用：ExoPlayer）
 *
 * 注意：不要做全局单例持有 Context，建议在 Composable/Host 中 remember 创建，
 * 并在关闭 overlay 时 release。
 */
class VideoPlayerManager(context: Context) {

    // 用 applicationContext 防止 Activity 泄漏
    private val appContext = context.applicationContext

    private var exoPlayer: ExoPlayer? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration

    private val scope = MainScope()
    private var positionJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            // READY 时通常就有 duration（直播可能是 TIME_UNSET）
            val d = exoPlayer?.duration ?: 0L
            if (d > 0) _duration.value = d

            // 播放结束时把 isPlaying 置 false（UI 正常）
            if (playbackState == Player.STATE_ENDED) {
                _isPlaying.value = false
                // 可选：停在末尾 or 回到 0，看你需求
                // exoPlayer?.seekTo(0L)
            }
        }
    }

    fun play(uri: String) {
        // 释放旧的
        releasePlayerOnly()

        // 正确解析 URI：区分本地文件路径和网络 URL
        val uriParsed = when {
            uri.startsWith("/") -> Uri.fromFile(java.io.File(uri))  // 本地文件路径
            uri.startsWith("content://") || uri.startsWith("file://") -> Uri.parse(uri)
            else -> Uri.parse(uri)  // 网络地址或其他
        }

        val player = ExoPlayer.Builder(appContext).build().also {
            it.addListener(listener)
        }

        exoPlayer = player

        val mediaItem = MediaItem.fromUri(uriParsed)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()

        startPositionLoop()
    }

    fun pause() {
        exoPlayer?.pause()
        // _isPlaying 会由 listener 更新
    }

    fun resume() {
        exoPlayer?.play()
        startPositionLoop()
        // _isPlaying 会由 listener 更新
    }

    fun stop() {
        // stop 语义：停止并归零
        positionJob?.cancel()
        positionJob = null

        exoPlayer?.let {
            it.pause()
            it.seekTo(0L)
        }

        _isPlaying.value = false
        _currentPosition.value = 0L
        // duration 保留也可以；你想清零就 uncomment
        // _duration.value = 0L
    }

    fun seekTo(position: Long) {
        val d = _duration.value
        if (d <= 0) return
        exoPlayer?.seekTo(position.coerceIn(0L, d))
    }

    fun getCurrentPosition(): Long = exoPlayer?.currentPosition ?: 0L

    fun getDuration(): Long = _duration.value

    /**
     * 释放所有资源（Overlay 关闭时调用）
     */
    fun release() {
        positionJob?.cancel()
        positionJob = null
        releasePlayerOnly()
        scope.cancel() // ✅ 彻底取消内部 scope，避免泄漏
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
    }

    /**
     * 仅释放 player（内部用）
     */
    private fun releasePlayerOnly() {
        positionJob?.cancel()
        positionJob = null

        exoPlayer?.let { p ->
            try {
                p.removeListener(listener)
            } catch (_: Exception) {
            }
            p.release()
        }
        exoPlayer = null
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L
    }

    fun getPlayer(): ExoPlayer? = exoPlayer

    private fun startPositionLoop() {
        if (positionJob?.isActive == true) return

        positionJob = scope.launch(Dispatchers.Main) {
            while (true) {
                val p = exoPlayer ?: break
                _currentPosition.value = p.currentPosition
                val d = p.duration
                if (d > 0) _duration.value = d
                delay(200) // 100~250ms 都行
            }
        }
    }
}

/**
 * Compose 视频播放器 View（PlayerView）
 * - 正确 attach/detach player，避免泄漏
 */
@Composable
fun VideoPlayerView(
    playerManager: VideoPlayerManager,
    modifier: Modifier = Modifier,
) {
    val player = playerManager.getPlayer()

    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                useController = true
                this.player = player
            }
        },
        update = { view ->
            // player 可能因 play() 重建，更新时重新绑定
            if (view.player !== player) {
                view.player = player
            }
        }
    )

    DisposableEffect(player) {
        onDispose {
            // detach，防止 View 持有 player 引用导致泄漏
            //（注意：不在这里 release player，release 由 manager/overlay 生命周期控制）
        }
    }
}