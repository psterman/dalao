package com.example.aifloatingball.video

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.ui.PlayerView

/**
 * ExoPlayer 管理器
 * 
 * 封装 ExoPlayer 功能，提供与 VideoView 类似的接口
 * 支持所有现有功能：播放速度、循环、静音等
 * 
 * @author AI Floating Ball
 */
@UnstableApi
class ExoPlayerManager(private val context: Context) {

    companion object {
        private const val TAG = "ExoPlayerManager"
    }

    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    
    // 播放状态
    private var isMuted = false
    private var isLooping = false
    private var playbackSpeed = 1.0f
    private var savedPosition = 0L
    
    // 监听器
    private var onPreparedListener: (() -> Unit)? = null
    private var onCompletionListener: (() -> Unit)? = null
    private var onErrorListener: ((Int, Int) -> Boolean)? = null
    private var onVideoSizeChangedListener: ((Int, Int) -> Unit)? = null

    /**
     * 初始化 ExoPlayer
     */
    fun initialize(container: ViewGroup): PlayerView {
        // 创建 PlayerView
        playerView = PlayerView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            useController = false // 使用自定义控制条
            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
        
        container.addView(playerView)
        
        // 创建 ExoPlayer
        player = ExoPlayer.Builder(context)
            .setLoadControl(createLoadControl())
            .build()
            .apply {
                // 设置音频属性
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true // 自动处理音频焦点
                )
                
                // 添加播放监听器
                addListener(PlayerEventListener())
                
                // 设置默认播放速度
                playbackParameters = androidx.media3.common.PlaybackParameters(playbackSpeed)
                
                // 设置循环播放
                repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            }
        
        playerView?.player = player
        
        Log.d(TAG, "ExoPlayer 初始化完成")
        return playerView!!
    }

    /**
     * 创建加载控制（缓冲策略）
     * 优化后的缓冲参数，提供更流畅的播放体验
     */
    private fun createLoadControl(): LoadControl {
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                30000,  // 最小缓冲时间（毫秒）- 增加到30秒，减少卡顿
                60000,  // 最大缓冲时间（毫秒）- 增加到60秒
                5000,   // 播放缓冲时间（毫秒）- 增加到5秒，确保播放前有足够缓冲
                10000   // 重缓冲时间（毫秒）- 增加到10秒
            )
            .setPrioritizeTimeOverSizeThresholds(true) // 优先考虑时间而非大小
            .build()
    }

    /**
     * 设置视频源
     */
    fun setVideoURI(uri: Uri) {
        try {
            val mediaItem = MediaItem.fromUri(uri)
            player?.setMediaItem(mediaItem)
            player?.prepare()
            Log.d(TAG, "视频源已设置: $uri")
        } catch (e: Exception) {
            Log.e(TAG, "设置视频源失败: $uri", e)
            onErrorListener?.invoke(0, 0) ?: false
        }
    }

    /**
     * 设置视频路径
     */
    fun setVideoPath(path: String) {
        try {
            val uri = if (path.startsWith("http://") || path.startsWith("https://") || 
                          path.startsWith("file://") || path.startsWith("content://")) {
                Uri.parse(path)
            } else {
                Uri.parse("file://$path")
            }
            setVideoURI(uri)
        } catch (e: Exception) {
            Log.e(TAG, "设置视频路径失败: $path", e)
            onErrorListener?.invoke(0, 0) ?: false
        }
    }

    /**
     * 开始播放
     */
    fun start() {
        player?.play()
    }

    /**
     * 暂停播放
     */
    fun pause() {
        player?.pause()
    }

    /**
     * 停止播放
     */
    fun stopPlayback() {
        player?.stop()
    }

    /**
     * 跳转到指定位置
     */
    fun seekTo(position: Int) {
        player?.seekTo(position.toLong())
    }

    /**
     * 获取当前播放位置（毫秒）
     */
    fun getCurrentPosition(): Int {
        return player?.currentPosition?.toInt() ?: 0
    }

    /**
     * 获取视频总时长（毫秒）
     */
    fun getDuration(): Int {
        return player?.duration?.toInt() ?: 0
    }

    /**
     * 是否正在播放
     */
    fun isPlaying(): Boolean {
        return player?.isPlaying ?: false
    }

    /**
     * 设置播放速度
     */
    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        player?.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
        Log.d(TAG, "播放速度已设置为: ${speed}x")
    }

    /**
     * 获取播放速度
     */
    fun getPlaybackSpeed(): Float {
        return playbackSpeed
    }

    /**
     * 设置循环播放
     */
    fun setLooping(looping: Boolean) {
        isLooping = looping
        player?.repeatMode = if (looping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        Log.d(TAG, "循环播放已${if (looping) "开启" else "关闭"}")
    }

    /**
     * 是否循环播放
     */
    fun isLooping(): Boolean {
        return isLooping
    }

    /**
     * 设置静音
     */
    fun setMuted(muted: Boolean) {
        isMuted = muted
        player?.volume = if (muted) 0f else 1f
        Log.d(TAG, "静音已${if (muted) "开启" else "关闭"}")
    }

    /**
     * 是否静音
     */
    fun isMuted(): Boolean {
        return isMuted
    }

    /**
     * 设置音量（0.0 - 1.0）
     */
    fun setVolume(volume: Float) {
        player?.volume = volume.coerceIn(0f, 1f)
    }

    /**
     * 获取音量（0.0 - 1.0）
     */
    fun getVolume(): Float {
        return player?.volume ?: 1f
    }

    /**
     * 设置视频缩放模式
     */
    fun setVideoScalingMode(mode: Int) {
        // ExoPlayer 使用 PlayerView 的 resizeMode
        when (mode) {
            android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT -> {
                playerView?.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
            }
            android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING -> {
                playerView?.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
            else -> {
                playerView?.resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        }
    }

    /**
     * 获取视频宽度
     */
    fun getVideoWidth(): Int {
        return player?.videoSize?.width ?: 0
    }

    /**
     * 获取视频高度
     */
    fun getVideoHeight(): Int {
        return player?.videoSize?.height ?: 0
    }

    /**
     * 设置准备完成监听器
     */
    fun setOnPreparedListener(listener: () -> Unit) {
        onPreparedListener = listener
    }

    /**
     * 设置播放完成监听器
     */
    fun setOnCompletionListener(listener: () -> Unit) {
        onCompletionListener = listener
    }

    /**
     * 设置错误监听器
     */
    fun setOnErrorListener(listener: (Int, Int) -> Boolean) {
        onErrorListener = listener
    }

    /**
     * 设置视频尺寸变化监听器
     */
    fun setOnVideoSizeChangedListener(listener: (Int, Int) -> Unit) {
        onVideoSizeChangedListener = listener
    }

    /**
     * 获取 PlayerView（用于添加到布局）
     */
    fun getPlayerView(): PlayerView? {
        return playerView
    }

    /**
     * 获取 ExoPlayer 实例（用于高级功能）
     */
    fun getPlayer(): ExoPlayer? {
        return player
    }

    /**
     * 获取缓冲百分比（0-100）
     */
    fun getBufferedPercentage(): Int {
        return player?.bufferedPercentage ?: 0
    }

    /**
     * 获取缓冲位置（毫秒）
     */
    fun getBufferedPosition(): Long {
        return player?.bufferedPosition ?: 0L
    }


    /**
     * 释放资源
     */
    fun release() {
        try {
            player?.stop()
            player?.release()
            player = null
            playerView?.player = null
            playerView = null
            Log.d(TAG, "ExoPlayer 资源已释放")
        } catch (e: Exception) {
            Log.e(TAG, "释放 ExoPlayer 资源失败", e)
        }
    }

    /**
     * 播放器事件监听器
     */
    private inner class PlayerEventListener : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_READY -> {
                    Log.d(TAG, "播放器准备就绪")
                    onPreparedListener?.invoke()
                    
                    // 通知视频尺寸变化
                    val width = player?.videoSize?.width ?: 0
                    val height = player?.videoSize?.height ?: 0
                    if (width > 0 && height > 0) {
                        onVideoSizeChangedListener?.invoke(width, height)
                    }
                }
                Player.STATE_ENDED -> {
                    Log.d(TAG, "播放完成")
                    onCompletionListener?.invoke()
                }
                Player.STATE_BUFFERING -> {
                    Log.d(TAG, "缓冲中...")
                }
                Player.STATE_IDLE -> {
                    Log.d(TAG, "播放器空闲")
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "播放错误: ${error.message}", error)
            val errorCode = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> 1
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> 2
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> 3
                PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> 4
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> 5
                else -> 0
            }
            onErrorListener?.invoke(errorCode, 0) ?: false
        }

        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            Log.d(TAG, "视频尺寸变化: ${videoSize.width}x${videoSize.height}")
            onVideoSizeChangedListener?.invoke(videoSize.width, videoSize.height)
        }
    }
}


