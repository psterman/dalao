package com.example.aifloatingball.video

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Button
import android.widget.MediaController
import android.widget.PopupMenu
import android.widget.VideoView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.example.aifloatingball.download.EnhancedDownloadManager
import com.example.aifloatingball.R

/**
 * 系统级悬浮窗视频播放器管理器
 * 
 * 使用 WindowManager + TYPE_APPLICATION_OVERLAY 显示可拖拽小窗，
 * 支持关闭、播放完成自动隐藏。
 * 
 * @author AI Floating Ball
 */
class SystemOverlayVideoManager(private val context: Context) {

    private var windowManager: WindowManager? = null
    private var floatingView: ViewGroup? = null
    private var videoView: VideoView? = null
    private var closeBtn: ImageButton? = null
    private var params: WindowManager.LayoutParams? = null
    private var isShowing = false
    
    // 原视频位置信息（用于覆盖原视频）
    private var sourceVideoX = -1
    private var sourceVideoY = -1
    private var sourceVideoWidth = -1
    private var sourceVideoHeight = -1
    
    // 下载管理器
    private val downloadManager: EnhancedDownloadManager by lazy {
        EnhancedDownloadManager(context)
    }
    
    // 播放列表管理器
    private val playlistManager: VideoPlaylistManager by lazy {
        VideoPlaylistManager(context)
    }

    // 拖拽相关
    private var dX = 0f
    private var dY = 0f
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    
    // 控制条相关
    private var controlBar: ViewGroup? = null
    private var topControlBarContainer: ViewGroup? = null
    private var playPauseBtn: ImageButton? = null
    private var muteBtn: ImageButton? = null
    private var fullscreenBtn: ImageButton? = null
    private var speedBtn: Button? = null
    private var loopBtn: ImageButton? = null
    private var playlistBtn: ImageButton? = null
    
    // 迷你模式相关（拖拽到屏幕中间以下时启用）
    private var isMiniMode = false
    private var miniModeCloseBtn: ImageButton? = null
    private var miniModePlayPauseBtn: ImageButton? = null
    private var miniModeRestoreBtn: ImageButton? = null
    private var miniModeExpandBtn: ImageButton? = null
    private var progressBar: android.widget.SeekBar? = null
    private var isMiniModeControlsVisible = false // 迷你模式下按钮和进度条的显示状态
    private var currentTimeText: android.widget.TextView? = null // 当前播放时间（左侧）
    private var totalTimeText: android.widget.TextView? = null // 总时长（右侧）
    private val normalThumbSize: Int get() = dpToPx(28) // 正常thumb大小
    private val enlargedThumbSize: Int get() = dpToPx(40) // 放大后的thumb大小
    private var updateHandler: android.os.Handler? = null
    private var updateRunnable: Runnable? = null
    private var isMuted = false
    private var isLooping = false
    private var playbackSpeed = 1.0f
    private val speedOptions = listOf(0.5f, 1.0f, 1.5f, 2.0f, 4.0f, 8.0f)
    private var hideControlsRunnable: Runnable? = null
    private var hideControlsHandler: android.os.Handler? = null
    private val CONTROLS_AUTO_HIDE_DELAY = 3000L // 3秒后自动隐藏
    private var lastScreenOrientationCheck = 0L // 上次检查屏幕方向的时间
    private val SCREEN_ORIENTATION_CHECK_INTERVAL = 1000L // 每1秒检查一次屏幕方向
    private var screenWidth = 0
    private var screenHeight = 0
    private var isFullscreen = false
    private var originalWidth = 0
    private var originalHeight = 0
    private var originalX = 0
    private var originalY = 0
    private var currentVideoUrl: String? = null
    private var videoWidth = 0
    private var videoHeight = 0
    private var isVideoPortrait = false // 视频是否为竖屏
    private var hasAutoMaximized = false // 是否已经自动最大化过
    private var lastScreenOrientation = Configuration.ORIENTATION_UNDEFINED // 上次屏幕方向
    
    // 手势控制相关
    private var gestureDetector: GestureDetector? = null
    private var brightnessControlView: android.widget.TextView? = null // 亮度控制提示视图
    private var volumeControlView: android.widget.TextView? = null // 音量控制提示视图
    private var seekControlView: android.widget.TextView? = null // 快进/后退提示视图
    private var audioManager: AudioManager? = null
    private var maxVolume = 0
    private var currentVolume = 0
    private var currentBrightness = 0f
    private var isSeeking = false // 是否正在快进/后退
    private var seekStartX = 0f // 快进/后退起始X坐标
    private var seekStartY = 0f // 滑动起始Y坐标
    private var seekStartTime = 0 // 快进/后退起始时间
    private var gestureHandled = false // 手势是否已被处理
    private var touchDownTime = 0L // 按下时间，用于区分拖拽和快速滑动
    private var isDraggingWindow = false // 是否正在拖拽窗口
    private val DRAG_DELAY_THRESHOLD = 200L // 拖拽延迟阈值（毫秒），按住超过这个时间才认为是拖拽

    companion object {
        private const val TAG = "SystemOverlayVideo"
        private const val REQUEST_CODE_OVERLAY_PERMISSION = 1001
        private const val MIN_SWIPE_DISTANCE = 50 // 最小滑动距离（像素）
        private const val SEEK_SPEED_FACTOR = 2.0f // 快进/后退速度因子
    }

    init {
        if (context is Activity) {
            windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        } else {
            windowManager = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        }
    }

    /**
     * 检查是否有悬浮窗权限
     */
    fun canDrawOverlays(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * 请求悬浮窗权限
     */
    fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!canDrawOverlays()) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                )
                if (context is Activity) {
                    context.startActivity(intent)
                } else {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
        }
    }

    /**
     * 显示悬浮窗播放器
     * 
     * @param videoUrl 视频URL
     * @param sourceX 原视频在屏幕上的X坐标（可选，用于覆盖原视频位置）
     * @param sourceY 原视频在屏幕上的Y坐标（可选，用于覆盖原视频位置）
     * @param sourceWidth 原视频宽度（可选）
     * @param sourceHeight 原视频高度（可选）
     */
    fun show(videoUrl: String?, sourceX: Int = -1, sourceY: Int = -1, sourceWidth: Int = -1, sourceHeight: Int = -1, pageTitle: String? = null) {
        // 确保在主线程中执行所有UI操作
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                show(videoUrl, sourceX, sourceY, sourceWidth, sourceHeight, pageTitle)
            }
            return
        }
        
        if (videoUrl.isNullOrBlank()) {
            Log.w(TAG, "视频URL为空，无法播放")
            return
        }

        if (!canDrawOverlays()) {
            Log.w(TAG, "无悬浮窗权限，无法显示系统级悬浮窗")
            requestOverlayPermission()
            return
        }

        try {
            val url = videoUrl.trim()
            
            // 如果播放器已存在且是同一个视频URL，则重新显示并继续播放
            if (isShowing && floatingView != null && videoView != null && currentVideoUrl == url) {
                Log.d(TAG, "播放器已存在，重新显示并继续播放: $url")
                floatingView?.visibility = View.VISIBLE
                isShowing = true
                
                // 确保控制条显示（正常模式下）
                if (!isMiniMode) {
                    showControls()
                }
                
                // 如果视频已暂停，继续播放
                videoView?.let { vv ->
                    if (!vv.isPlaying && vv.duration > 0) {
                        vv.start()
                        playPauseBtn?.setImageResource(android.R.drawable.ic_media_pause)
                        miniModePlayPauseBtn?.setImageResource(android.R.drawable.ic_media_pause)
                        Log.d(TAG, "视频继续播放")
                    }
                }
                return
            }
            
            // 如果播放器视图存在但被隐藏了（hide()后重新显示），需要重新显示并设置视频源
            if (!isShowing && floatingView != null && videoView != null) {
                Log.d(TAG, "播放器视图存在但被隐藏，重新显示: $url")
                
                // 重新显示视图
                floatingView?.visibility = View.VISIBLE
                isShowing = true
                
                // 退出迷你模式（如果处于迷你模式）
                if (isMiniMode) {
                    exitMiniMode()
                }
                
                // 确保控制条显示（正常模式下）
                if (!isMiniMode) {
                    showControls()
                }
                
                // 播放器被隐藏后重新显示，无论URL是否相同，都需要重新设置视频源
                // 因为hide()时调用了stopPlayback()，可能导致视频状态丢失，无法正常播放
                var needResetVideoSource = true
                if (currentVideoUrl == url) {
                    Log.d(TAG, "播放器重新显示，重新设置视频源（相同URL）: $url")
                } else {
                    // 不同视频，需要重新设置视频源
                    Log.d(TAG, "检测到新视频URL，切换播放: 从 $currentVideoUrl 切换到 $url")
                }
                
                // 如果需要重新设置视频源，清理旧状态
                if (needResetVideoSource) {
                    // 停止当前播放
                    videoView?.stopPlayback()
                    
                    // 清理旧的监听器和进度更新
                    updateRunnable?.let { updateHandler?.removeCallbacks(it) }
                    updateRunnable = null
                    videoView?.setOnPreparedListener(null)
                    videoView?.setOnCompletionListener(null)
                    videoView?.setOnErrorListener(null)
                    
                    // 重置播放状态
                    playPauseBtn?.setImageResource(android.R.drawable.ic_media_play)
                    miniModePlayPauseBtn?.setImageResource(android.R.drawable.ic_media_play)
                    progressBar?.progress = 0
                    currentTimeText?.text = formatTime(0)
                    totalTimeText?.text = formatTime(0)
                    
                    // 重置视频尺寸相关状态，以便新视频能够正确检测和调整大小
                    videoWidth = 0
                    videoHeight = 0
                    isVideoPortrait = false
                    hasAutoMaximized = false
                    isFullscreen = false
                    
                    // 继续执行后续代码设置新视频源
                }
            }
            
            // 如果播放器已存在但URL不同，需要切换播放新视频
            if (isShowing && floatingView != null && videoView != null && currentVideoUrl != null && currentVideoUrl != url) {
                Log.d(TAG, "检测到新视频URL，切换播放: 从 $currentVideoUrl 切换到 $url")
                
                // 停止当前播放
                videoView?.stopPlayback()
                
                // 清理旧的监听器和进度更新
                updateRunnable?.let { updateHandler?.removeCallbacks(it) }
                updateRunnable = null
                videoView?.setOnPreparedListener(null)
                videoView?.setOnCompletionListener(null)
                videoView?.setOnErrorListener(null)
                
                // 重置播放状态
                playPauseBtn?.setImageResource(android.R.drawable.ic_media_play)
                miniModePlayPauseBtn?.setImageResource(android.R.drawable.ic_media_play)
                progressBar?.progress = 0
                currentTimeText?.text = formatTime(0)
                totalTimeText?.text = formatTime(0)
                
                // 重置视频尺寸相关状态，以便新视频能够正确检测和调整大小
                videoWidth = 0
                videoHeight = 0
                isVideoPortrait = false
                hasAutoMaximized = false
                isFullscreen = false
                
                // 确保播放器可见
                floatingView?.visibility = View.VISIBLE
                isShowing = true
                
                // 确保控制条显示（正常模式下）
                if (!isMiniMode) {
                    showControls()
                }
                
                // 继续执行后续代码设置新视频源
            } else {
                // 播放器不存在，需要创建
                // 保存原视频位置信息
                sourceVideoX = sourceX
                sourceVideoY = sourceY
                sourceVideoWidth = sourceWidth
                sourceVideoHeight = sourceHeight
                
                // 确保在主线程中创建视图
                if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        createFloatingView()
                    }
                } else {
                    createFloatingView()
                }
            }
            
            // 保存原视频位置信息（如果之前没有保存）
            if (sourceVideoX < 0) {
                sourceVideoX = sourceX
                sourceVideoY = sourceY
                sourceVideoWidth = sourceWidth
                sourceVideoHeight = sourceHeight
            }
            
            Log.d(TAG, "准备播放视频: $url, 原视频位置: ($sourceX, $sourceY), 尺寸: ${sourceWidth}x${sourceHeight}")
            
            currentVideoUrl = url
            hasAutoMaximized = false // 重置自动最大化标志
            
            // 添加到播放列表（使用网页标题或从URL提取）
            val title = pageTitle?.takeIf { it.isNotBlank() } ?: extractVideoTitle(url)
            playlistManager.addVideo(url, title, 0L, 0L)
            
            // 设置视频源，对于 HTTP/HTTPS URL 使用 setVideoPath，对于 content:// 使用 setVideoURI
            try {
                val vv = videoView
                if (vv == null) {
                    Log.e(TAG, "videoView为null，无法设置视频源")
                    hide()
                    return
                }
                
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    // HTTP/HTTPS URL：直接使用 setVideoURI，避免 setVideoPath 内部调用 ContentResolver 导致失败
                    // setVideoPath 内部会尝试通过 ContentResolver 打开，对于 HTTP URL 会失败
                    try {
                        vv.setVideoURI(Uri.parse(url))
                        Log.d(TAG, "视频源已设置（setVideoURI）: $url")
                    } catch (e: Exception) {
                        Log.w(TAG, "setVideoURI失败，尝试使用setVideoPath: $url", e)
                        try {
                            vv.setVideoPath(url)
                            Log.d(TAG, "视频源已设置（setVideoPath）: $url")
                        } catch (e2: Exception) {
                            Log.e(TAG, "设置视频源失败: $url", e2)
                            throw e2
                        }
                    }
                } else if (url.startsWith("content://") || url.startsWith("file://")) {
                    // Content URI 或 File URI：使用 setVideoURI
                    vv.setVideoURI(Uri.parse(url))
                    Log.d(TAG, "视频源已设置（setVideoURI）: $url")
                } else {
                    // 其他情况：尝试作为路径处理
                    vv.setVideoPath(url)
                    Log.d(TAG, "视频源已设置（setVideoPath）: $url")
                }
            } catch (e: Exception) {
                Log.e(TAG, "设置视频源失败: $url", e)
                // 确保在主线程显示 Toast
                if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                    Toast.makeText(context, "无法加载视频: ${e.message}", Toast.LENGTH_SHORT).show()
                } else {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        Toast.makeText(context, "无法加载视频: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
                hide()
                return
            }
            
            videoView?.setOnPreparedListener { mediaPlayer ->
                try {
                    mediaPlayer.isLooping = isLooping
                    
                    // 设置视频缩放模式（在视频准备完成后设置，使视频填满屏幕无空白）
                    try {
                        mediaPlayer.setVideoScalingMode(android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                        Log.d(TAG, "已设置视频缩放模式为SCALE_TO_FIT_WITH_CROPPING")
                    } catch (e: Exception) {
                        Log.w(TAG, "设置视频缩放模式失败", e)
                    }
                    
                    // 检测视频尺寸和宽高比
                    try {
                        val newVideoWidth = mediaPlayer.videoWidth
                        val newVideoHeight = mediaPlayer.videoHeight
                        val newIsVideoPortrait = newVideoHeight > newVideoWidth
                        
                        // 如果视频方向发生变化（从竖屏切换到横屏或反之），需要重置状态
                        val videoOrientationChanged = (isVideoPortrait != newIsVideoPortrait) && (videoWidth > 0 && videoHeight > 0)
                        
                        videoWidth = newVideoWidth
                        videoHeight = newVideoHeight
                        isVideoPortrait = newIsVideoPortrait
                        Log.d(TAG, "视频尺寸: ${videoWidth}x${videoHeight}, 是否为竖屏: $isVideoPortrait, 方向是否变化: $videoOrientationChanged")
                        
                        // 如果视频方向发生变化，重置全屏状态和自动最大化标志
                        if (videoOrientationChanged) {
                            isFullscreen = false
                            hasAutoMaximized = false
                            Log.d(TAG, "视频方向已变化，重置全屏状态")
                        }
                        
                        // 根据视频方向调整窗口大小
                        if (params != null && floatingView != null && windowManager != null) {
                            val displayMetrics = context.resources.displayMetrics
                            val currentScreenWidth = displayMetrics.widthPixels
                            val currentScreenHeight = displayMetrics.heightPixels
                            val isScreenLandscape = currentScreenWidth > currentScreenHeight
                            
                            if (isVideoPortrait) {
                                // 竖屏视频：使用屏幕宽度，高度为屏幕高度的70%，靠近顶部
                                val targetWidth = currentScreenWidth
                                val targetHeight = (currentScreenHeight * 0.7f).toInt()
                                
                                params?.width = targetWidth
                                params?.height = targetHeight
                                params?.x = 0
                                params?.y = 0 // 靠近顶部
                                
                                floatingView?.layoutParams?.width = targetWidth
                                floatingView?.layoutParams?.height = targetHeight
                                
                                // 确保VideoView填满整个容器（宽度和高度都填满，避免黑边）
                                videoView?.layoutParams?.width = FrameLayout.LayoutParams.MATCH_PARENT
                                videoView?.layoutParams?.height = FrameLayout.LayoutParams.MATCH_PARENT
                                
                                // 设置视频缩放模式，确保竖屏视频填满宽度（避免右边黑边）
                                try {
                                    val mediaPlayerField = videoView?.javaClass?.getDeclaredField("mMediaPlayer")
                                    mediaPlayerField?.isAccessible = true
                                    val mediaPlayer = mediaPlayerField?.get(videoView) as? android.media.MediaPlayer
                                    // 使用 SCALE_TO_FIT_WITH_CROPPING 确保视频填满容器宽度，避免右边黑边
                                    mediaPlayer?.setVideoScalingMode(android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                                    Log.d(TAG, "竖屏视频已设置缩放模式为SCALE_TO_FIT_WITH_CROPPING，确保宽度最大化，避免右边黑边")
                                } catch (e: Exception) {
                                    Log.w(TAG, "设置视频缩放模式失败", e)
                                }
                                
                                windowManager?.updateViewLayout(floatingView, params)
                                isFullscreen = false
                                hasAutoMaximized = true
                                
                                // 更新原始尺寸和位置（固定位置）
                                originalWidth = targetWidth
                                originalHeight = targetHeight
                                originalX = 0
                                originalY = 0
                                
                                // 确保全屏按钮可见
                                adjustFullscreenControls()
                                
                                Log.d(TAG, "竖屏视频已调整为靠近顶部播放: ${targetWidth}x${targetHeight}, 位置: (0, 0)")
                            } else {
                                // 横屏视频：根据屏幕方向调整
                                if (isScreenLandscape) {
                                    // 屏幕横屏：横屏视频全屏
                                    params?.width = currentScreenWidth
                                    params?.height = currentScreenHeight
                                    params?.x = 0
                                    params?.y = 0
                                    
                                    floatingView?.layoutParams?.width = currentScreenWidth
                                    floatingView?.layoutParams?.height = currentScreenHeight
                                    
                                    videoView?.layoutParams?.width = FrameLayout.LayoutParams.MATCH_PARENT
                                    videoView?.layoutParams?.height = FrameLayout.LayoutParams.MATCH_PARENT
                                    
                                    windowManager?.updateViewLayout(floatingView, params)
                                    isFullscreen = true
                                    hasAutoMaximized = true
                                    
                                    originalWidth = currentScreenWidth
                                    originalHeight = currentScreenHeight
                                    originalX = 0
                                    originalY = 0
                                    
                                    adjustFullscreenControls()
                                    Log.d(TAG, "横屏视频在横屏屏幕上已全屏: ${currentScreenWidth}x${currentScreenHeight}")
                                } else {
                                    // 屏幕竖屏：横屏视频填满宽度，高度按16:9，垂直居中
                                    val targetWidth = currentScreenWidth
                                    val targetHeight = (currentScreenWidth * 9 / 16).coerceAtMost(currentScreenHeight)
                                    val targetX = 0
                                    val targetY = (currentScreenHeight - targetHeight) / 2
                                    
                                    params?.width = targetWidth
                                    params?.height = targetHeight
                                    params?.x = targetX
                                    params?.y = targetY
                                    
                                    floatingView?.layoutParams?.width = targetWidth
                                    floatingView?.layoutParams?.height = targetHeight
                                    
                                    // 确保VideoView填满容器（关键：避免黑色边）
                                    videoView?.layoutParams?.width = FrameLayout.LayoutParams.MATCH_PARENT
                                    videoView?.layoutParams?.height = FrameLayout.LayoutParams.MATCH_PARENT
                                    
                                    // 再次设置视频缩放模式，确保填满窗口（避免黑色边）
                                    try {
                                        val mediaPlayerField = videoView?.javaClass?.getDeclaredField("mMediaPlayer")
                                        mediaPlayerField?.isAccessible = true
                                        val mediaPlayer = mediaPlayerField?.get(videoView) as? android.media.MediaPlayer
                                        mediaPlayer?.setVideoScalingMode(android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                                        Log.d(TAG, "横屏视频已设置缩放模式为SCALE_TO_FIT_WITH_CROPPING")
                                    } catch (e: Exception) {
                                        Log.w(TAG, "设置视频缩放模式失败", e)
                                    }
                                    
                                    windowManager?.updateViewLayout(floatingView, params)
                                    isFullscreen = false
                                    hasAutoMaximized = true
                                    
                                    originalWidth = targetWidth
                                    originalHeight = targetHeight
                                    originalX = targetX
                                    originalY = targetY
                                    
                                    Log.d(TAG, "横屏视频在竖屏屏幕上已调整: ${targetWidth}x${targetHeight}, 位置: ($targetX, $targetY)")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "无法获取视频尺寸", e)
                        videoWidth = 0
                        videoHeight = 0
                        isVideoPortrait = false
                    }
                    
                    // 更新控制条状态
                    val duration = mediaPlayer.duration
                    Log.d(TAG, "视频准备完成，时长: ${duration}ms")
                    
                    // 更新播放列表中的视频信息（保持原有标题）
                    val currentUrl = currentVideoUrl
                    if (currentUrl != null && duration > 0) {
                        val existingItem = playlistManager.getPlaylist().firstOrNull { it.url == currentUrl }
                        val videoTitle = existingItem?.title ?: extractVideoTitle(currentUrl)
                        playlistManager.addVideo(currentUrl, videoTitle, duration.toLong(), 0L)
                    }
                    
                    if (duration > 0) {
                        progressBar?.max = 100
                        currentTimeText?.text = formatTime(0)
                        totalTimeText?.text = formatTime(duration)
                    }
                    
                    // 更新播放按钮状态为暂停图标（因为即将播放）
                    playPauseBtn?.setImageResource(android.R.drawable.ic_media_pause)
                    
                    // 延迟一下再自动播放，确保VideoView已完全准备好
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try {
                            val vv = videoView
                            if (vv != null) {
                                if (!vv.isPlaying) {
                                    vv.start()
                                    Log.d(TAG, "视频开始自动播放")
                                    
                                    // 启动进度更新任务
                                    updateHandler = android.os.Handler(android.os.Looper.getMainLooper())
                                    updateRunnable = object : Runnable {
                                        private var lastProgress = -1
                                        private var stuckCount = 0
                                        
                                        override fun run() {
                                            try {
                                                // 确保在主线程执行
                                                if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
                                                    updateHandler?.post(this)
                                                    return
                                                }
                                                
                                                val vv = videoView
                                                if (vv != null) {
                                                    // 检测假死：如果视频应该播放但进度不更新
                                                    if (vv.isPlaying && vv.duration > 0) {
                                                        val current = vv.currentPosition
                                                        val total = vv.duration
                                                        val progressPercent = (current * 100 / total).coerceIn(0, 100)
                                                        
                                                        // 检测进度是否卡住
                                                        if (current == lastProgress && lastProgress > 0) {
                                                            stuckCount++
                                                            if (stuckCount > 10) { // 5秒没有进度更新（10次 * 500ms）
                                                                Log.w(TAG, "检测到视频假死，尝试重启")
                                                                stuckCount = 0
                                                                // 确保重启也在主线程执行
                                                                android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                                    restartVideoPlayer()
                                                                }
                                                                return
                                                            }
                                                        } else {
                                                            stuckCount = 0
                                                            lastProgress = current
                                                        }
                                                        
                                                        // 安全更新UI（使用View.post确保在视图创建线程中执行）
                                                        try {
                                                            progressBar?.post {
                                                                try {
                                                                    progressBar?.progress = progressPercent
                                                                } catch (e: Exception) {
                                                                    Log.e(TAG, "更新进度条失败", e)
                                                                }
                                                            }
                                                            currentTimeText?.post {
                                                                try {
                                                                    currentTimeText?.text = formatTime(current)
                                                                } catch (e: Exception) {
                                                                    Log.e(TAG, "更新当前时间失败", e)
                                                                }
                                                            }
                                                            totalTimeText?.post {
                                                                try {
                                                                    totalTimeText?.text = formatTime(total)
                                                                } catch (e: Exception) {
                                                                    Log.e(TAG, "更新总时长失败", e)
                                                                }
                                                            }
                                                        } catch (e: Exception) {
                                                            Log.e(TAG, "更新UI失败", e)
                                                        }
                                                        
                                                        // 检查屏幕方向变化（实时检测）- 确保在主线程
                                                        try {
                                                            checkAndHandleOrientationChange()
                                                        } catch (e: Exception) {
                                                            Log.e(TAG, "检查屏幕方向变化失败", e)
                                                        }
                                                    } else if (!vv.isPlaying && vv.duration > 0 && vv.currentPosition > 0) {
                                                        // 视频应该播放但没有播放，可能是假死
                                                        stuckCount++
                                                        if (stuckCount > 6) { // 3秒
                                                            Log.w(TAG, "检测到视频停止播放，尝试重启")
                                                            stuckCount = 0
                                                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                                restartVideoPlayer()
                                                            }
                                                            return
                                                        }
                                                    } else {
                                                        stuckCount = 0
                                                    }
                                                }
                                                updateHandler?.postDelayed(this, 500)
                                            } catch (e: Exception) {
                                                Log.e(TAG, "更新播放进度失败", e)
                                                // 如果更新失败，也尝试重启（确保在主线程）
                                                stuckCount++
                                                if (stuckCount > 6) {
                                                    stuckCount = 0
                                                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                        restartVideoPlayer()
                                                    }
                                                    return
                                                }
                                                updateHandler?.postDelayed(this, 500)
                                            }
                                        }
                                    }
                                    updateHandler?.post(updateRunnable!!)
                                    
                                    // 自动调整视频窗口大小
                                    adjustVideoWindowSize()
                                } else {
                                    Log.d(TAG, "视频已经在播放中")
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "自动播放失败", e)
                        }
                    }, 200) // 延迟200ms确保VideoView完全准备好
                } catch (e: Exception) {
                    Log.e(TAG, "视频准备监听器错误", e)
                }
            }
            
            videoView?.setOnCompletionListener {
                Log.d(TAG, "视频播放完成，自动隐藏")
                updateRunnable?.let { updateHandler?.removeCallbacks(it) }
                playPauseBtn?.setImageResource(android.R.drawable.ic_media_play)
                hide()
            }
            
            videoView?.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "视频播放错误: what=$what, extra=$extra")
                updateRunnable?.let { updateHandler?.removeCallbacks(it) }
                // 不立即隐藏，而是尝试重启播放器（确保在主线程）
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "视频播放错误，尝试重启...", Toast.LENGTH_SHORT).show()
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        restartVideoPlayer()
                    }, 500)
                }
                true
            }
            
            floatingView?.visibility = View.VISIBLE
            isShowing = true
            
            // 确保控制条显示（正常模式下）
            if (!isMiniMode) {
                showControls()
            }
        } catch (e: Exception) {
            Log.e(TAG, "显示悬浮窗播放器失败", e)
            hide()
        }
    }

    /**
     * 隐藏悬浮窗播放器
     */
    fun hide() {
        try {
            // 确保在主线程执行
            if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    hide()
                }
                return
            }
            
            // 停止视频播放
            try {
                videoView?.stopPlayback()
            } catch (e: Exception) {
                Log.w(TAG, "停止视频播放失败", e)
            }
            
            // 清理进度更新任务
            try {
                updateRunnable?.let { updateHandler?.removeCallbacks(it) }
                updateHandler = null
                updateRunnable = null
            } catch (e: Exception) {
                Log.w(TAG, "清理进度更新任务失败", e)
            }
            
            // 清理自动隐藏任务
            try {
                cancelHideControls()
                hideControlsHandler = null
                hideControlsRunnable = null
            } catch (e: Exception) {
                Log.w(TAG, "清理自动隐藏任务失败", e)
            }
            
            // 清理视频监听器
            try {
                videoView?.setOnPreparedListener(null)
                videoView?.setOnCompletionListener(null)
                videoView?.setOnErrorListener(null)
            } catch (e: Exception) {
                Log.w(TAG, "清理视频监听器失败", e)
            }
            
            // 如果处于迷你模式，退出迷你模式
            try {
                if (isMiniMode) {
                    exitMiniMode()
                }
            } catch (e: Exception) {
                Log.w(TAG, "退出迷你模式失败", e)
            }
            
            // 隐藏视图（确保在主线程）
            try {
                val view = floatingView
                if (view != null) {
                    view.visibility = View.GONE
                    // 确保视图真正隐藏
                    view.post {
                        if (view.visibility != View.GONE) {
                            view.visibility = View.GONE
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "隐藏视图失败", e)
            }
            
            isShowing = false
            hasAutoMaximized = false // 重置自动最大化标志
            Log.d(TAG, "悬浮窗播放器已隐藏")
        } catch (e: Exception) {
            Log.e(TAG, "隐藏悬浮窗播放器失败", e)
        }
    }

    /**
     * 检查是否正在显示
     */
    fun isShowing(): Boolean = isShowing

    /**
     * 创建悬浮窗视图
     */
    private fun createFloatingView() {
        // 确保在主线程中创建视图
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                createFloatingView()
            }
            return
        }
        
        if (floatingView != null) {
            return
        }

        val wm = windowManager ?: return
        
        // 获取屏幕尺寸
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        // 初始尺寸：如果是竖屏视频，使用全屏；否则使用16:9比例
        // 注意：此时视频尺寸可能还未检测到，所以先使用16:9，视频准备完成后会调整
        val overlayWidth = screenWidth
        val overlayHeight = (screenWidth * 9 / 16) // 默认16:9 比例，视频准备完成后会根据实际视频方向调整
        
        // 保存屏幕尺寸用于拖动限制
        this.screenWidth = screenWidth
        this.screenHeight = screenHeight
        
        // 保存原始尺寸（用于全屏切换）
        originalWidth = overlayWidth
        originalHeight = overlayHeight
        
        // 创建视频容器（作为主容器，直接添加到WindowManager）
        val container = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                overlayWidth,
                overlayHeight
            )
            setBackgroundColor(0xFF000000.toInt())
        }
        
        // 创建 VideoView
        val vv = VideoView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
            )
            // 设置VideoView的缩放模式（通过反射设置MediaPlayer的缩放）
            try {
                // VideoView内部使用SurfaceView，我们需要确保视频填满容器
                // 通过设置布局参数为MATCH_PARENT，视频会自动缩放
            } catch (e: Exception) {
                Log.w(TAG, "设置VideoView缩放模式失败", e)
            }
        }
        
        container.addView(vv)
        
        // 创建自定义视频控制条（不依赖MediaController，因为系统级悬浮窗不支持）
        createCustomControls(container, vv)
        
        floatingView = container
        videoView = vv
        
        // 创建 WindowManager.LayoutParams
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        
        params = WindowManager.LayoutParams(
            overlayWidth,  // 全屏宽度
            overlayHeight, // 按16:9比例的高度
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, // 允许触摸事件穿透到下层，不遮盖下拉菜单
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            
            // 如果提供了原视频位置，覆盖在原视频上方；否则屏幕居中
            if (sourceVideoX >= 0 && sourceVideoY >= 0) {
                // 计算覆盖原视频的位置（居中覆盖）
                val overlayX = if (sourceVideoWidth > 0) {
                    sourceVideoX + (sourceVideoWidth - overlayWidth) / 2
                } else {
                    sourceVideoX
                }
                val overlayY = if (sourceVideoHeight > 0) {
                    sourceVideoY + (sourceVideoHeight - overlayHeight) / 2
                } else {
                    sourceVideoY
                }
                
                // 计算有效范围，避免范围无效导致的异常
                val maxX = (screenWidth - overlayWidth).coerceAtLeast(0)
                val maxY = (screenHeight - overlayHeight).coerceAtLeast(0)
                x = overlayX.coerceIn(0, maxX)
                y = overlayY.coerceIn(0, maxY)
                Log.d(TAG, "悬浮窗位置设置为覆盖原视频: x=$x, y=$y (原视频位置: x=$sourceVideoX, y=$sourceVideoY)")
            } else {
                // 默认位置：屏幕居中
                x = (screenWidth - overlayWidth) / 2
                y = (screenHeight - overlayHeight) / 2
                Log.d(TAG, "悬浮窗位置设置为屏幕居中: x=$x, y=$y")
            }
        }
        
        // 保存原始位置
        originalX = params?.x ?: 0
        originalY = params?.y ?: 0
        
        // 拖拽功能已整合到手势控制中，不再单独调用enableDrag
        
        // 添加到 WindowManager
        try {
            wm.addView(floatingView, params)
            floatingView?.visibility = View.GONE
            Log.d(TAG, "悬浮窗视图已创建，尺寸: ${videoWidth}x${videoHeight}, 位置: x=${params?.x}, y=${params?.y}")
        } catch (e: Exception) {
            Log.e(TAG, "添加悬浮窗视图失败", e)
            floatingView = null
            videoView = null
            this.closeBtn = null
            params = null
        }
    }

    /**
     * 应用Material Design风格到按钮
     */
    private fun applyMaterialStyle(button: ImageButton, isActive: Boolean = false) {
        try {
            val rippleColor = android.content.res.ColorStateList.valueOf(0x33FFFFFF.toInt())
            val defaultColor = if (isActive) 0xFF4CAF50.toInt() else 0x80000000.toInt()
            
            val backgroundDrawable = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                android.graphics.drawable.RippleDrawable(
                    rippleColor,
                    android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(defaultColor)
                    },
                    null
                )
            } else {
                android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(defaultColor)
                }
            }
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                button.background = backgroundDrawable
                button.elevation = dpToPx(2).toFloat()
        } else {
                @Suppress("DEPRECATION")
                button.setBackgroundDrawable(backgroundDrawable)
            }
            
            button.setColorFilter(0xFFFFFFFF.toInt(), android.graphics.PorterDuff.Mode.SRC_IN)
        } catch (e: Exception) {
            Log.e(TAG, "应用Material风格失败", e)
        }
    }
    
    /**
     * 创建自定义视频控制条
     */
    private fun createCustomControls(container: FrameLayout, videoView: VideoView) {
        // 创建顶部控制条容器（单行：全屏/投屏按钮、左侧时间、中间进度条、右侧关闭按钮）
        val topControlBarContainer = LinearLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            ).apply {
                setMargins(0, 0, 0, 0)
            }
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0x80000000.toInt()) // 提高透明度，减少遮挡
            visibility = View.VISIBLE
            // 降低播放条背景高度：减少上下padding
            setPadding(dpToPx(4), dpToPx(0), dpToPx(4), dpToPx(0))
            // 确保控制条可以接收触摸事件
            isClickable = true
            isFocusable = false
        }
        
        // 创建底部控制条容器（按钮在底部）
        val controlBarContainer = LinearLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply {
                setMargins(0, 0, 0, 0)
            }
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0x80000000.toInt()) // 提高透明度，减少遮挡
            visibility = View.VISIBLE
            // 降低播放条背景高度：减少上下padding
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            // 确保控制条可以接收触摸事件
            isClickable = true
            isFocusable = false
        }
        
        // 进度条行不再单独创建，直接添加到顶部控制条
        
        // 创建第二行：控制按钮
        val buttonRow = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(40)
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        
        // 创建播放/暂停按钮（Material Design风格）
        playPauseBtn = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(40),
                dpToPx(40)
            ).apply {
                setMargins(dpToPx(8), 0, dpToPx(4), 0)
            }
            setImageResource(android.R.drawable.ic_media_play)
            applyMaterialStyle(this)
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            setOnClickListener {
                val vv = videoView ?: return@setOnClickListener
                try {
                    val isCurrentlyPlaying = vv.isPlaying
                    Log.d(TAG, "播放/暂停按钮点击，当前状态: isPlaying=$isCurrentlyPlaying")
                    
                    if (isCurrentlyPlaying) {
                        vv.pause()
                        setImageResource(android.R.drawable.ic_media_play)
                        Log.d(TAG, "视频已暂停")
                    } else {
                vv.start()
                        setImageResource(android.R.drawable.ic_media_pause)
                        Log.d(TAG, "视频开始播放")
                        
                        // 如果进度更新任务没有运行，启动它
                        if (updateRunnable == null) {
                            updateHandler = android.os.Handler(android.os.Looper.getMainLooper())
                            updateRunnable = object : Runnable {
                                override fun run() {
                                    try {
                                        val vv = videoView
                                        if (vv != null && vv.isPlaying && vv.duration > 0) {
                                            val current = vv.currentPosition
                                            val total = vv.duration
                                            val progressPercent = (current * 100 / total).coerceIn(0, 100)
                                            progressBar?.progress = progressPercent
                                            currentTimeText?.text = formatTime(current)
                                            totalTimeText?.text = formatTime(total)
                                            
                                            // 定期检查屏幕方向变化
                                            val currentTime = System.currentTimeMillis()
                                            if (currentTime - lastScreenOrientationCheck > SCREEN_ORIENTATION_CHECK_INTERVAL) {
                                                lastScreenOrientationCheck = currentTime
                                                adjustVideoWindowSize()
                                            }
                                        }
                                        updateHandler?.postDelayed(this, 500)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "更新播放进度失败", e)
                                    }
                                }
                            }
                            updateHandler?.post(updateRunnable!!)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "播放/暂停操作失败", e)
                }
            }
        }
        
        // 创建全屏按钮（放在时间轴左侧，更换样式）
        val topFullscreenBtn = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(28),
                dpToPx(28)
            ).apply {
                setMargins(0, 0, dpToPx(4), 0)
            }
            // 更换样式：使用展开图标
            setImageResource(android.R.drawable.ic_menu_view)
            setBackgroundColor(0x80000000.toInt())
            setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            setColorFilter(0xFFFFFFFF.toInt(), android.graphics.PorterDuff.Mode.SRC_IN)
            // 添加圆角背景
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0x80000000.toInt())
                cornerRadius = dpToPx(4).toFloat()
            }
            setOnClickListener {
                toggleFullscreen()
            }
        }
        
        // 创建当前播放时间显示（左侧）
        currentTimeText = android.widget.TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, dpToPx(6), 0)
            }
            text = "00:00"
            textSize = 10f // 进一步减小字体
            setTextColor(0xFFFFFFFF.toInt())
        }
        
        // 创建可拖拽的进度条（SeekBar）
        // 创建正常大小的thumb drawable
        val normalThumbDrawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(0xFFFFFFFF.toInt())
            setSize(normalThumbSize, normalThumbSize)
        }
        
        // 创建放大后的thumb drawable
        val enlargedThumbDrawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(0xFFFFFFFF.toInt())
            setSize(enlargedThumbSize, enlargedThumbSize)
        }
        
        progressBar = android.widget.SeekBar(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f // 占据剩余空间
            ).apply {
                // 进度条背景高度改成原来的一半
                setMargins(0, dpToPx(4), 0, dpToPx(4))
            }
            max = 100
            progress = 0
            thumb = normalThumbDrawable
            thumbOffset = 0
            // 减少进度条的触摸区域padding（高度减半）
            setPadding(dpToPx(14), dpToPx(7), dpToPx(14), dpToPx(7))
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser && videoView.duration > 0) {
                        val position = (progress * videoView.duration / 100)
                        videoView.seekTo(position)
                        Log.d(TAG, "拖动进度条到: $position / ${videoView.duration}")
                    }
                }
                override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {
                    // 暂停进度更新，避免冲突
                    updateRunnable?.let { updateHandler?.removeCallbacks(it) }
                    
                    // 显示控制条（拖动时保持可见）
                    showControls()
                    
                    // 放大thumb，方便用户操作
                    progressBar?.thumb = enlargedThumbDrawable
                    progressBar?.invalidate() // 强制重绘
                    Log.d(TAG, "开始拖动进度条，thumb已放大")
                }
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                    // 恢复进度更新
                    updateRunnable?.let { updateHandler?.post(it) }
                    
                    // 恢复thumb正常大小
                    progressBar?.thumb = normalThumbDrawable
                    progressBar?.invalidate() // 强制重绘
                    
                    // 不再自动隐藏控制条，让控制条始终可见
                    // scheduleHideControls()
                    
                    Log.d(TAG, "结束拖动进度条，thumb已恢复")
                }
            })
        }
        
        // 创建总时长显示（在进度条右侧）
        totalTimeText = android.widget.TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(dpToPx(6), 0, dpToPx(6), 0)
            }
            text = "00:00"
            textSize = 10f // 进一步减小字体
            setTextColor(0xFFFFFFFF.toInt())
        }
        
        // 将全屏按钮、当前时间、进度条、总时长添加到顶部控制条（同一行）
        topControlBarContainer.addView(topFullscreenBtn)
        topControlBarContainer.addView(currentTimeText)
        topControlBarContainer.addView(progressBar)
        topControlBarContainer.addView(totalTimeText)
        
        // 创建关闭按钮（放在顶部控制条右侧）
        val closeBtn = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(32),
                dpToPx(32)
            ).apply {
                setMargins(dpToPx(8), 0, 0, 0)
            }
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(0xCC000000.toInt()) // 更明显的背景色
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            setOnClickListener { 
                Log.d(TAG, "关闭按钮被点击")
                hide() 
            }
        }
        
        // 将关闭按钮添加到顶部控制条
        topControlBarContainer.addView(closeBtn)
        
        // 保存关闭按钮引用
        this.closeBtn = closeBtn
        
        // 保存全屏按钮引用（用于后续更新图标）
        this.fullscreenBtn = topFullscreenBtn
        
        // 创建播放速度按钮（Material Design风格）
        speedBtn = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(48),
                dpToPx(40)
            ).apply {
                setMargins(dpToPx(4), 0, dpToPx(4), 0)
            }
            text = "1.0x"
            textSize = 11f
            setTextColor(0xFFFFFFFF.toInt())
            
            // Material Design风格
            val rippleColor = android.content.res.ColorStateList.valueOf(0x33FFFFFF.toInt())
            val backgroundDrawable = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                android.graphics.drawable.RippleDrawable(
                    rippleColor,
                    android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        cornerRadius = dpToPx(4).toFloat()
                        setColor(0x80000000.toInt())
                    },
                    null
                )
            } else {
                android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = dpToPx(4).toFloat()
                    setColor(0x80000000.toInt())
                }
            }
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                background = backgroundDrawable
                elevation = dpToPx(2).toFloat()
            } else {
                @Suppress("DEPRECATION")
                setBackgroundDrawable(backgroundDrawable)
            }
            
            setPadding(dpToPx(8), dpToPx(6), dpToPx(8), dpToPx(6))
            setOnClickListener { view ->
                // 显示播放速度选择菜单
                showSpeedMenu(view)
            }
        }
        
        // 创建循环播放按钮（重新设计样式）
        loopBtn = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(40),
                dpToPx(40)
            ).apply {
                setMargins(dpToPx(4), 0, dpToPx(4), 0)
            }
            setImageResource(android.R.drawable.ic_menu_revert)
            applyMaterialStyle(this, false) // 使用 Material Design 风格
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            setOnClickListener {
                isLooping = !isLooping
                try {
                    val mediaPlayerField = VideoView::class.java.getDeclaredField("mMediaPlayer")
                    mediaPlayerField.isAccessible = true
                    val mediaPlayer = mediaPlayerField.get(videoView)
                    if (mediaPlayer != null) {
                        val setIsLoopingMethod = mediaPlayer.javaClass.getDeclaredMethod("setLooping", Boolean::class.java)
                        setIsLoopingMethod.invoke(mediaPlayer, isLooping)
                        
                        // 更新按钮外观（重新设计）
                        if (isLooping) {
                            // 开启状态：使用 Material Design 的激活状态
                            applyMaterialStyle(this, true)
                            setColorFilter(0xFF4CAF50.toInt(), android.graphics.PorterDuff.Mode.SRC_IN) // 绿色图标
                        } else {
                            // 关闭状态：使用默认样式
                            applyMaterialStyle(this, false)
                            setColorFilter(0xFFFFFFFF.toInt(), android.graphics.PorterDuff.Mode.SRC_IN) // 白色图标
                        }
                        Log.d(TAG, "循环播放已${if (isLooping) "开启" else "关闭"}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "设置循环播放失败", e)
                }
            }
        }
        
        // 创建播放列表按钮（Material Design风格）
        playlistBtn = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(40),
                dpToPx(40)
            ).apply {
                setMargins(dpToPx(4), 0, dpToPx(4), 0)
            }
            setImageResource(android.R.drawable.ic_menu_sort_by_size)
            applyMaterialStyle(this)
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            setOnClickListener {
                showPlaylistDialog()
            }
        }
        
        // 创建下载按钮（Material Design风格）
        val downloadBtn = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(40),
                dpToPx(40)
            ).apply {
                setMargins(dpToPx(4), 0, dpToPx(4), 0)
            }
            setImageResource(R.drawable.ic_download)
            applyMaterialStyle(this)
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            setOnClickListener {
                downloadVideo()
            }
        }
        
        // 创建分享按钮（Material Design风格）
        val shareBtn = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(40),
                dpToPx(40)
            ).apply {
                setMargins(dpToPx(4), 0, dpToPx(4), 0)
            }
            setImageResource(android.R.drawable.ic_menu_share)
            applyMaterialStyle(this)
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            setOnClickListener {
                shareVideo()
            }
        }
        
        // 创建静音按钮（Material Design风格）
        muteBtn = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(40),
                dpToPx(40)
            ).apply {
                setMargins(dpToPx(4), 0, dpToPx(4), 0)
            }
            setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            applyMaterialStyle(this)
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            setOnClickListener {
                isMuted = !isMuted
                try {
                    // 通过反射获取MediaPlayer并设置音量
                    val mediaPlayerField = VideoView::class.java.getDeclaredField("mMediaPlayer")
                    mediaPlayerField.isAccessible = true
                    val mediaPlayer = mediaPlayerField.get(videoView)
                    if (mediaPlayer != null) {
                        val setVolumeMethod = mediaPlayer.javaClass.getDeclaredMethod("setVolume", Float::class.java, Float::class.java)
                        if (isMuted) {
                            setVolumeMethod.invoke(mediaPlayer, 0f, 0f)
                            setImageResource(android.R.drawable.ic_lock_silent_mode)
        } else {
                            setVolumeMethod.invoke(mediaPlayer, 1f, 1f)
                            setImageResource(android.R.drawable.ic_lock_silent_mode_off)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "静音功能不可用", e)
                }
            }
        }
        
        // 全屏按钮现在在顶部控制条，不再单独设置
        // fullscreenBtn 已在上面设置为 topFullscreenBtn
        
        // 添加按钮到第二行
        buttonRow.addView(playPauseBtn)
        buttonRow.addView(speedBtn)
        buttonRow.addView(loopBtn)
        // 全屏按钮已移除，不再添加
        buttonRow.addView(downloadBtn)
        buttonRow.addView(shareBtn)
        
        // 添加弹性空间
        val spacer = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
        }
        buttonRow.addView(spacer)
        
        buttonRow.addView(muteBtn)
        // 下方全屏按钮替换成播放列表按钮
        buttonRow.addView(playlistBtn) // 播放列表按钮替换原来的全屏按钮位置
        
        // 添加重启按钮（用于修复假死问题）
        val restartBtn = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(48),
                dpToPx(48)
            ).apply {
                setMargins(dpToPx(4), 0, dpToPx(4), 0)
            }
            setImageResource(android.R.drawable.ic_menu_revert)
            setBackgroundColor(0x80000000.toInt())
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            setOnClickListener {
                restartVideoPlayer()
            }
        }
        buttonRow.addView(restartBtn)
        
        // 添加按钮行到底部控制条
        controlBarContainer.addView(buttonRow)
        
        // 将顶部和底部控制条添加到容器
        container.addView(topControlBarContainer)
        container.addView(controlBarContainer)
        
        controlBar = controlBarContainer
        this.topControlBarContainer = topControlBarContainer
        
        // 初始化手势检测和音亮度控制
        initGestureControls(container, videoView)
        
        // 为视频区域添加点击监听，用于显示/隐藏控制条（已由手势检测处理）
        // videoView.setOnClickListener {
        //     toggleControls()
        // }
        
        // 初始化：控制条默认显示，永久显示（不自动隐藏）
        hideControlsHandler = android.os.Handler(android.os.Looper.getMainLooper())
        showControls() // 默认显示控制条（永久显示）
        // scheduleHideControls() // 已禁用自动隐藏，让控制条始终可见
        
        // 设置控制条的初始状态
        playPauseBtn?.setImageResource(android.R.drawable.ic_media_play)
        
        // 注意：播放完成的监听器在show()方法中设置，这里不设置，避免被覆盖
    }
    
    /**
     * 格式化时间显示
     */
    private fun formatTime(milliseconds: Int): String {
        val totalSeconds = milliseconds / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }
    
    /**
     * 切换GIF录制状态
     */
    /**
     * 进入迷你模式（缩小一倍，隐藏进度条，四个按钮淡化消失）
     */
    private fun enterMiniMode() {
        val currentParams = params ?: return
        val currentFloatingView = floatingView ?: return
        val currentWindowManager = windowManager ?: return
        
        if (isMiniMode) return
        
        try {
            isMiniMode = true
            
            // 保存当前尺寸
            val currentWidth = currentParams.width
            val currentHeight = currentParams.height
            val currentX = currentParams.x
            val currentY = currentParams.y
            
            // 缩小一倍
            val miniWidth = currentWidth / 2
            val miniHeight = currentHeight / 2
            
            currentParams.width = miniWidth
            currentParams.height = miniHeight
            currentParams.x = currentX
            currentParams.y = currentY
            
            currentFloatingView.layoutParams?.width = miniWidth
            currentFloatingView.layoutParams?.height = miniHeight
            
            // 隐藏所有控制条
            topControlBarContainer?.visibility = View.GONE
            controlBar?.visibility = View.GONE
            
            // 创建迷你模式按钮（如果还没有创建）
            if (miniModeCloseBtn == null) {
                createMiniModeButtons()
            }
            
            // 显示四个按钮（初始状态为显示）
            showMiniModeControls()
            
            currentWindowManager.updateViewLayout(currentFloatingView, currentParams)
            
            Log.d(TAG, "已进入迷你模式: ${miniWidth}x${miniHeight}")
        } catch (e: Exception) {
            Log.e(TAG, "进入迷你模式失败", e)
        }
    }
    
    /**
     * 退出迷你模式（恢复原尺寸，显示所有按钮）
     */
    private fun exitMiniMode() {
        val currentParams = params ?: return
        val currentFloatingView = floatingView ?: return
        val currentWindowManager = windowManager ?: return
        
        if (!isMiniMode) return
        
        try {
            isMiniMode = false
            isMiniModeControlsVisible = false
            
            // 恢复原始尺寸
            currentParams.width = originalWidth
            currentParams.height = originalHeight
            currentParams.x = originalX
            currentParams.y = originalY
            
            currentFloatingView.layoutParams?.width = originalWidth
            currentFloatingView.layoutParams?.height = originalHeight
            
            // 隐藏迷你模式按钮
            miniModeCloseBtn?.visibility = View.GONE
            miniModePlayPauseBtn?.visibility = View.GONE
            miniModeRestoreBtn?.visibility = View.GONE
            miniModeExpandBtn?.visibility = View.GONE
            
            // 恢复正常模式的所有控制条和按钮（调用showControls确保所有按钮都显示）
            showControls()
            
            currentWindowManager.updateViewLayout(currentFloatingView, currentParams)
            
            Log.d(TAG, "已退出迷你模式: ${originalWidth}x${originalHeight}")
        } catch (e: Exception) {
            Log.e(TAG, "退出迷你模式失败", e)
        }
    }
    
    /**
     * 显示迷你模式控制按钮（带淡入动画）
     * 迷你模式不显示进度条
     * 必须在主线程调用
     */
    private fun showMiniModeControls() {
        if (!isMiniMode || isMiniModeControlsVisible) return
        
        // 确保在主线程执行
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                showMiniModeControls()
            }
            return
        }
        
        try {
            isMiniModeControlsVisible = true
            
            // 迷你模式不显示进度条，topControlBarContainer 保持隐藏
            
            // 更新暂停/播放按钮图标以反映当前播放状态（使用View.post确保在视图创建线程中执行）
            videoView?.let { vv ->
                miniModePlayPauseBtn?.let { btn ->
                    btn.post {
                        try {
                            if (vv.isPlaying) {
                                btn.setImageResource(android.R.drawable.ic_media_pause)
                            } else {
                                btn.setImageResource(android.R.drawable.ic_media_play)
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "更新迷你模式播放按钮失败", e)
                        }
                    }
                }
            }
            
            // 显示四个按钮（带淡入动画，使用View.post确保在视图创建线程中执行）
            val buttons = listOf(
                miniModeCloseBtn,
                miniModePlayPauseBtn,
                miniModeRestoreBtn,
                miniModeExpandBtn
            )
            
            buttons.forEach { button ->
                button?.let { btn ->
                    btn.post {
                        try {
                            // 确保按钮可以接收点击事件
                            btn.isClickable = true
                            btn.isFocusable = true
                            btn.isEnabled = true
                            // 确保按钮在最上层
                            btn.bringToFront()
                            btn.visibility = View.VISIBLE
                            btn.alpha = 0f
                            btn.animate()
                                .alpha(1f)
                                .setDuration(300)
                                .setInterpolator(FastOutSlowInInterpolator())
                                .start()
                        } catch (e: Exception) {
                            Log.e(TAG, "显示迷你模式按钮失败", e)
                        }
                    }
                }
            }
            
            // 确保容器可以接收触摸事件（使用View.post确保在视图创建线程中执行）
            floatingView?.post {
                try {
                    floatingView?.isClickable = true
                    floatingView?.isFocusable = false
                } catch (e: Exception) {
                    Log.e(TAG, "设置容器触摸事件失败", e)
                }
            }
            
            Log.d(TAG, "迷你模式控制已显示，按钮位置：关闭(右上)、暂停(左上)、恢复(左下)、拉伸(右下)")
        } catch (e: Exception) {
            Log.e(TAG, "显示迷你模式控制失败", e)
        }
    }
    
    /**
     * 隐藏迷你模式控制按钮和进度条（带淡出动画）
     * 必须在主线程调用
     */
    private fun hideMiniModeControls() {
        if (!isMiniMode || !isMiniModeControlsVisible) return
        
        // 确保在主线程执行
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                hideMiniModeControls()
            }
            return
        }
        
        try {
            isMiniModeControlsVisible = false
            
            // 隐藏 topControlBarContainer（包含进度条）- 迷你模式不显示进度条，所以不需要隐藏
            // topControlBarContainer 在进入迷你模式时已经隐藏了
            
            // 隐藏四个按钮（带淡出动画）
            val buttons = listOf(
                miniModeCloseBtn,
                miniModePlayPauseBtn,
                miniModeRestoreBtn,
                miniModeExpandBtn
            )
            
            buttons.forEach { button ->
                button?.let { btn ->
                    animateButtonHide(btn)
                }
            }
            
            Log.d(TAG, "迷你模式控制已隐藏")
        } catch (e: Exception) {
            Log.e(TAG, "隐藏迷你模式控制失败", e)
        }
    }
    
    /**
     * 执行按钮隐藏动画（确保在主线程）
     */
    private fun animateButtonHide(btn: View) {
        try {
            // 确保在主线程
            if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    animateButtonHide(btn)
                }
                return
            }
            
            // 使用主线程Handler确保回调在主线程执行
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            
            btn.animate()
                .alpha(0f)
                .setDuration(300)
                .setInterpolator(FastOutSlowInInterpolator())
                .withEndAction {
                    // 使用Handler确保在主线程执行
                    mainHandler.post {
                        try {
                            if (btn.visibility != View.GONE) {
                                btn.visibility = View.GONE
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "设置按钮visibility失败", e)
                        }
                    }
                }
                .start()
        } catch (e: Exception) {
            Log.e(TAG, "执行按钮隐藏动画失败", e)
            // 如果动画失败，直接隐藏（确保在主线程）
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                try {
                    if (btn.visibility != View.GONE) {
                        btn.visibility = View.GONE
                    }
                } catch (ex: Exception) {
                    Log.e(TAG, "设置按钮visibility失败", ex)
                }
            }
        }
    }
    
    /**
     * 创建迷你模式按钮（关闭、暂停、恢复、拉伸）
     */
    private fun createMiniModeButtons() {
        try {
            val container = floatingView ?: return
            
            // 创建关闭按钮（右上角）
            miniModeCloseBtn = ImageButton(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    dpToPx(32),
                    dpToPx(32),
                    Gravity.TOP or Gravity.END
                ).apply {
                    setMargins(0, dpToPx(4), dpToPx(4), 0)
                }
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setBackgroundColor(0xCC000000.toInt())
                setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
                // 确保按钮可以接收点击事件
                isClickable = true
                isFocusable = true
                isEnabled = true
                elevation = dpToPx(4).toFloat() // 确保按钮在最上层
                setOnClickListener { 
                    Log.d(TAG, "迷你模式关闭按钮被点击")
                    hide() 
                }
                visibility = View.GONE
            }
            container.addView(miniModeCloseBtn)
            miniModeCloseBtn?.bringToFront() // 确保按钮在最上层
            
            // 创建暂停/播放按钮（左上角）
            miniModePlayPauseBtn = ImageButton(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    dpToPx(32),
                    dpToPx(32),
                    Gravity.TOP or Gravity.START
                ).apply {
                    setMargins(dpToPx(4), dpToPx(4), 0, 0)
                }
                // 根据当前播放状态设置图标
                val vv = videoView
                if (vv != null && vv.isPlaying) {
                    setImageResource(android.R.drawable.ic_media_pause)
                } else {
                    setImageResource(android.R.drawable.ic_media_play)
                }
                setBackgroundColor(0xCC000000.toInt())
                setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
                // 确保按钮可以接收点击事件
                isClickable = true
                isFocusable = true
                isEnabled = true
                elevation = dpToPx(4).toFloat() // 确保按钮在最上层
                setOnClickListener {
                    Log.d(TAG, "迷你模式暂停/播放按钮被点击")
                    val vv = videoView ?: return@setOnClickListener
                    try {
                        if (vv.isPlaying) {
                            vv.pause()
                            setImageResource(android.R.drawable.ic_media_play)
                            miniModePlayPauseBtn?.setImageResource(android.R.drawable.ic_media_play)
                            Log.d(TAG, "视频已暂停")
                        } else {
                            vv.start()
                            setImageResource(android.R.drawable.ic_media_pause)
                            miniModePlayPauseBtn?.setImageResource(android.R.drawable.ic_media_pause)
                            Log.d(TAG, "视频开始播放")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "暂停/播放按钮操作失败", e)
                    }
                }
                visibility = View.GONE
            }
            container.addView(miniModePlayPauseBtn)
            miniModePlayPauseBtn?.bringToFront() // 确保按钮在最上层
            
            // 创建恢复按钮（退出迷你模式，左下角）
            miniModeRestoreBtn = ImageButton(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    dpToPx(32),
                    dpToPx(32),
                    Gravity.BOTTOM or Gravity.START
                ).apply {
                    setMargins(dpToPx(4), 0, 0, dpToPx(4))
                }
                setImageResource(android.R.drawable.ic_menu_revert)
                setBackgroundColor(0xCC000000.toInt())
                setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
                // 确保按钮可以接收点击事件
                isClickable = true
                isFocusable = true
                isEnabled = true
                elevation = dpToPx(4).toFloat() // 确保按钮在最上层
                setOnClickListener { 
                    Log.d(TAG, "迷你模式恢复按钮被点击")
                    exitMiniMode() 
                }
                visibility = View.GONE
            }
            container.addView(miniModeRestoreBtn)
            miniModeRestoreBtn?.bringToFront() // 确保按钮在最上层
            
            // 创建拉伸按钮（全屏，右下角）
            miniModeExpandBtn = ImageButton(context).apply {
                layoutParams = FrameLayout.LayoutParams(
                    dpToPx(32),
                    dpToPx(32),
                    Gravity.BOTTOM or Gravity.END
                ).apply {
                    setMargins(0, 0, dpToPx(4), dpToPx(4))
                }
                setImageResource(android.R.drawable.ic_menu_crop)
                setBackgroundColor(0xCC000000.toInt())
                setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
                // 确保按钮可以接收点击事件
                isClickable = true
                isFocusable = true
                isEnabled = true
                elevation = dpToPx(4).toFloat() // 确保按钮在最上层
                setOnClickListener { 
                    Log.d(TAG, "迷你模式拉伸按钮被点击")
                    toggleFullscreen() 
                }
                visibility = View.GONE
            }
            container.addView(miniModeExpandBtn)
            miniModeExpandBtn?.bringToFront() // 确保按钮在最上层
            
            Log.d(TAG, "迷你模式按钮已创建")
        } catch (e: Exception) {
            Log.e(TAG, "创建迷你模式按钮失败", e)
        }
    }
    
    /**
     * GIF录制功能已移除
     */
    
    /**
     * 显示播放列表对话框
     */
    private fun showPlaylistDialog() {
        try {
            val playlist = playlistManager.getRecentVideos(20)
            
            if (playlist.isEmpty()) {
                Toast.makeText(context, "播放列表为空", Toast.LENGTH_SHORT).show()
                return
            }
            
            // 格式化显示：只显示标题 + 文件扩展名（去掉URL）
            val items = playlist.map { item ->
                val title = if (item.title.isNotBlank() && !item.title.startsWith("视频")) {
                    // 如果标题不是默认的"视频"，使用原标题
                    val ext = extractFileExtension(item.url)
                    if (ext.isNotBlank() && !item.title.endsWith(".$ext")) {
                        "${item.title}.$ext"
                    } else {
                        item.title
                    }
                } else {
                    // 使用默认格式：视频.扩展名
                    extractVideoTitle(item.url)
                }
                // 只显示标题，不显示URL
                title
            }.toTypedArray()
            
            val builder = android.app.AlertDialog.Builder(context)
            builder.setTitle("播放列表 (${playlist.size})")
            builder.setItems(items) { _, which ->
                val item = playlist[which]
                show(item.url, -1, -1, -1, -1, item.title)
            }
            builder.setNegativeButton("关闭", null)
            builder.setNeutralButton("清空列表") { _, _ ->
                playlistManager.clearPlaylist()
                Toast.makeText(context, "播放列表已清空", Toast.LENGTH_SHORT).show()
            }
            
            val dialog = builder.create()
            // 确保对话框可以在系统悬浮窗中显示
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            }
            dialog.show()
            
        } catch (e: Exception) {
            Log.e(TAG, "显示播放列表失败", e)
            Toast.makeText(context, "显示播放列表失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 从URL提取文件扩展名
     */
    private fun extractFileExtension(url: String): String {
        return try {
            val fileName = url.substringAfterLast("/").substringBefore("?")
            if (fileName.contains(".")) {
                fileName.substringAfterLast(".").lowercase()
            } else {
                when {
                    url.contains(".mp4") -> "mp4"
                    url.contains(".webm") -> "webm"
                    url.contains(".m3u8") -> "m3u8"
                    url.contains(".flv") -> "flv"
                    else -> ""
                }
            }
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * 从URL提取视频标题（显示网页标题+文件扩展名）
     */
    private fun extractVideoTitle(url: String): String {
        return try {
            // 提取文件扩展名
            val fileName = url.substringAfterLast("/").substringBefore("?")
            val extension = if (fileName.contains(".")) {
                fileName.substringAfterLast(".")
            } else {
                ""
            }
            
            // 如果没有扩展名，尝试从URL路径判断
            val ext = if (extension.isBlank()) {
                when {
                    url.contains(".mp4") -> "mp4"
                    url.contains(".webm") -> "webm"
                    url.contains(".m3u8") -> "m3u8"
                    url.contains(".flv") -> "flv"
                    else -> ""
                }
            } else {
                extension
            }
            
            // 返回格式：视频.扩展名
            if (ext.isNotBlank()) {
                "视频.$ext"
            } else {
                "视频"
            }
        } catch (e: Exception) {
            "视频"
        }
    }
    
    /**
     * 切换全屏模式（支持横屏）
     */
    private fun toggleFullscreen() {
        try {
            if (params == null || floatingView == null || windowManager == null) return
            
            isFullscreen = !isFullscreen
            
            if (isFullscreen) {
                // 进入全屏：保存当前位置，然后扩展到全屏
                originalX = params?.x ?: 0
                originalY = params?.y ?: 0
                originalWidth = params?.width ?: screenWidth
                originalHeight = params?.height ?: (screenWidth * 9 / 16)
                
                // 无论横屏还是竖屏，都使用屏幕宽高全屏
                // 重新获取屏幕尺寸（可能屏幕方向已改变）
                val displayMetrics = context.resources.displayMetrics
                val currentScreenWidth = displayMetrics.widthPixels
                val currentScreenHeight = displayMetrics.heightPixels
                
                params?.width = currentScreenWidth
                params?.height = currentScreenHeight
                
                params?.x = 0
                params?.y = 0
                
                // 更新容器尺寸
                floatingView?.layoutParams?.width = params?.width ?: screenWidth
                floatingView?.layoutParams?.height = params?.height ?: screenHeight
                
                // 调整控制条位置（全屏时紧贴屏幕边缘，无空白）
                controlBar?.layoutParams?.let { layoutParams ->
                    if (layoutParams is FrameLayout.LayoutParams) {
                        layoutParams.bottomMargin = 0 // 全屏时控制条紧贴底部
                    }
                }
                
                // 调整顶部控制条位置（全屏时紧贴屏幕边缘，无空白）
                topControlBarContainer?.layoutParams?.let { layoutParams ->
                    if (layoutParams is FrameLayout.LayoutParams) {
                        layoutParams.topMargin = 0 // 全屏时顶部控制条紧贴顶部
                    }
                }
                
                // 全屏时，确保退出全屏按钮可见
                adjustFullscreenControls()
                
                // 全屏按钮已移除，只更新顶部全屏按钮图标
                topControlBarContainer?.let { container ->
                    if (container.childCount > 0) {
                        val topFullscreenBtn = container.getChildAt(0) as? ImageButton
                        topFullscreenBtn?.setImageResource(android.R.drawable.ic_menu_view)
                    }
                }
                Toast.makeText(context, "全屏模式", Toast.LENGTH_SHORT).show()
            } else {
                // 退出全屏：恢复原始尺寸和位置
                // 如果是竖屏视频，使用80%屏幕尺寸作为窗口模式
                val displayMetrics = context.resources.displayMetrics
                val currentScreenWidth = displayMetrics.widthPixels
                val currentScreenHeight = displayMetrics.heightPixels
                
                if (isVideoPortrait) {
                    // 竖屏视频退出全屏：使用80%屏幕尺寸
                    val windowWidth = (currentScreenWidth * 0.8f).toInt()
                    val windowHeight = (currentScreenHeight * 0.8f).toInt()
                    
                    params?.width = windowWidth
                    params?.height = windowHeight
                    params?.x = (currentScreenWidth - windowWidth) / 2
                    params?.y = (currentScreenHeight - windowHeight) / 2
                    
                    floatingView?.layoutParams?.width = windowWidth
                    floatingView?.layoutParams?.height = windowHeight
                    
                    // 更新原始尺寸
                    originalWidth = windowWidth
                    originalHeight = windowHeight
                    originalX = params?.x ?: 0
                    originalY = params?.y ?: 0
                } else {
                    // 横屏视频退出全屏：恢复原始尺寸
                    params?.width = originalWidth
                    params?.height = originalHeight
                    params?.x = originalX
                    params?.y = originalY
                    
                    floatingView?.layoutParams?.width = originalWidth
                    floatingView?.layoutParams?.height = originalHeight
                }
                
                // 恢复控制条位置
                controlBar?.layoutParams?.let { layoutParams ->
                    if (layoutParams is FrameLayout.LayoutParams) {
                        layoutParams.bottomMargin = 0
                    }
                }
                
                // 恢复按钮位置
                restoreNormalControls()
                
                // 全屏按钮已移除，只更新顶部全屏按钮图标
                topControlBarContainer?.let { container ->
                    if (container.childCount > 0) {
                        val topFullscreenBtn = container.getChildAt(0) as? ImageButton
                        topFullscreenBtn?.setImageResource(android.R.drawable.ic_menu_view)
                    }
                }
                Toast.makeText(context, "窗口模式", Toast.LENGTH_SHORT).show()
            }
            
            windowManager?.updateViewLayout(floatingView, params)
            Log.d(TAG, "全屏模式切换: isFullscreen=$isFullscreen")
        } catch (e: Exception) {
            Log.e(TAG, "切换全屏模式失败", e)
            Toast.makeText(context, "切换全屏失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 调整全屏时的控制按钮布局（退出全屏按钮在关闭按钮旁边，但保持可见）
     */
    private fun adjustFullscreenControls() {
        try {
            // 获取关闭按钮和全屏按钮
            val closeBtn = this.closeBtn
            // 全屏按钮已移除，不再处理
            if (closeBtn != null) {
                // 全屏时，确保关闭按钮可见
                closeBtn.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            Log.e(TAG, "调整全屏控制按钮失败", e)
        }
    }
    
    /**
     * 恢复普通模式的控制按钮布局
     */
    private fun restoreNormalControls() {
        try {
            // 全屏按钮已移除，不再处理
            // 其他按钮的布局参数保持不变
            // 无需额外操作
        } catch (e: Exception) {
            Log.e(TAG, "恢复普通模式控制按钮失败", e)
        }
    }
    
    /**
     * 下载视频
     */
    private fun downloadVideo() {
        val videoUrl = currentVideoUrl
        if (videoUrl.isNullOrBlank()) {
            Toast.makeText(context, "无法获取视频地址", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            Log.d(TAG, "开始下载视频: $videoUrl")
            downloadManager.downloadSmart(videoUrl, object : EnhancedDownloadManager.DownloadCallback {
                override fun onDownloadSuccess(downloadId: Long, localUri: String?, fileName: String?) {
                    Log.d(TAG, "视频下载成功: $fileName")
                    Toast.makeText(context, "视频下载完成: $fileName", Toast.LENGTH_SHORT).show()
                }
                
                override fun onDownloadFailed(downloadId: Long, reason: Int) {
                    val reasonText = when (reason) {
                        android.app.DownloadManager.ERROR_CANNOT_RESUME -> "无法恢复下载"
                        android.app.DownloadManager.ERROR_DEVICE_NOT_FOUND -> "存储设备未找到"
                        android.app.DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "文件已存在"
                        android.app.DownloadManager.ERROR_FILE_ERROR -> "文件错误"
                        android.app.DownloadManager.ERROR_HTTP_DATA_ERROR -> "HTTP数据错误"
                        android.app.DownloadManager.ERROR_INSUFFICIENT_SPACE -> "存储空间不足"
                        android.app.DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "重定向过多"
                        android.app.DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "HTTP错误"
                        android.app.DownloadManager.ERROR_UNKNOWN -> "未知错误"
                        else -> "下载失败 (错误码: $reason)"
                    }
                    Log.e(TAG, "视频下载失败: $reasonText")
                    Toast.makeText(context, "视频下载失败: $reasonText", Toast.LENGTH_SHORT).show()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "下载视频失败", e)
            Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun shareVideo() {
        try {
            val url = currentVideoUrl
            if (url.isNullOrBlank()) {
                Toast.makeText(context, "无法分享：视频URL为空", Toast.LENGTH_SHORT).show()
                return
            }
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
                putExtra(Intent.EXTRA_SUBJECT, "分享视频")
            }
            
            val chooserIntent = Intent.createChooser(shareIntent, "分享视频")
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooserIntent)
            
            Log.d(TAG, "分享视频URL: $url")
        } catch (e: Exception) {
            Log.e(TAG, "分享视频失败", e)
            Toast.makeText(context, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 初始化手势控制
     */
    private fun initGestureControls(container: FrameLayout, videoView: VideoView) {
        try {
            // 初始化音频管理器
            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.let {
                maxVolume = it.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                currentVolume = it.getStreamVolume(AudioManager.STREAM_MUSIC)
            }
            
            // 获取当前屏幕亮度
            try {
                val brightness = Settings.System.getInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS
                )
                currentBrightness = brightness / 255f
            } catch (e: Exception) {
                currentBrightness = 0.5f // 默认亮度
            }
            
            // 创建手势检测器
            gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    // 迷你模式下，屏蔽双击显示按钮的功能
                    if (isMiniMode) {
                        gestureHandled = true
                        return true
                    }
                    
                    // 双击暂停/播放（仅正常模式）
                    gestureHandled = true
                    val vv = videoView
                    if (vv != null) {
                        try {
                            val isCurrentlyPlaying = vv.isPlaying
                            if (isCurrentlyPlaying) {
                                vv.pause()
                                playPauseBtn?.setImageResource(android.R.drawable.ic_media_play)
                                Log.d(TAG, "双击暂停视频")
                            } else {
                                vv.start()
                                playPauseBtn?.setImageResource(android.R.drawable.ic_media_pause)
                                Log.d(TAG, "双击播放视频")
                                
                                // 如果进度更新任务没有运行，启动它
                                if (updateRunnable == null) {
                                    updateHandler = android.os.Handler(android.os.Looper.getMainLooper())
                                    updateRunnable = object : Runnable {
                                        override fun run() {
                                            try {
                                                val vv = videoView
                                                if (vv != null && vv.isPlaying && vv.duration > 0) {
                                                    val current = vv.currentPosition
                                                    val total = vv.duration
                                                    val progressPercent = (current * 100 / total).coerceIn(0, 100)
                                                    progressBar?.progress = progressPercent
                                                    currentTimeText?.text = formatTime(current)
                                                    totalTimeText?.text = formatTime(total)
                                                    
                                                    // 定期检查屏幕方向变化
                                                    val currentTime = System.currentTimeMillis()
                                                    if (currentTime - lastScreenOrientationCheck > SCREEN_ORIENTATION_CHECK_INTERVAL) {
                                                        lastScreenOrientationCheck = currentTime
                                                        adjustVideoWindowSize()
                                                    }
                                                }
                                                updateHandler?.postDelayed(this, 500)
                                            } catch (e: Exception) {
                                                Log.e(TAG, "更新播放进度失败", e)
                                            }
                                        }
                                    }
                                    updateHandler?.post(updateRunnable!!)
                                }
                            }
                            showControls() // 显示控制条（永久显示）
                            // scheduleHideControls() // 已禁用自动隐藏
                        } catch (e: Exception) {
                            Log.e(TAG, "双击控制失败", e)
                        }
                    }
                    return true
                }
                
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    // 迷你模式下，单击切换按钮显示状态（不自动隐藏，保持显示直到用户再次点击）
                    if (isMiniMode) {
                        gestureHandled = true
                        if (isMiniModeControlsVisible) {
                            // 如果按钮已显示，隐藏它们
                            hideMiniModeControls()
                        } else {
                            // 如果按钮未显示，显示它们（保持显示，不自动隐藏）
                            showMiniModeControls()
                        }
                        return true
                    }
                    
                    // 正常模式下，单击显示/隐藏控制条（只有在没有其他手势时才触发）
                    if (!isSeeking && 
                        brightnessControlView?.visibility != View.VISIBLE &&
                        volumeControlView?.visibility != View.VISIBLE &&
                        seekControlView?.visibility != View.VISIBLE) {
                        gestureHandled = true
                        toggleControls()
                        return true
                    }
                    return false
                }
            })
            
            // 创建控制提示视图
            createControlHintViews(container)
            
            // 为视频容器设置触摸监听
            container.setOnTouchListener { v, event ->
                // 先让手势检测器处理双击和单击
                val gestureResult = gestureDetector?.onTouchEvent(event) ?: false
                
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        gestureHandled = false
                        isDraggingWindow = false
                        
                        // 迷你模式下，点击时不自动显示按钮（由单击手势处理）
                        
                        // 检查是否点击在控制条或按钮上
                        val x = event.x.toInt()
                        val y = event.y.toInt()
                        var isOnControl = false
                        
                        // 迷你模式下，不检查控制条（因为控制条已隐藏）
                        if (!isMiniMode) {
                            controlBar?.let { bar ->
                                val barLeft = bar.left
                                val barRight = bar.right
                                val barTop = bar.top
                                val barBottom = bar.bottom
                                if (x >= barLeft && x <= barRight && y >= barTop && y <= barBottom) {
                                    isOnControl = true
                                }
                            }
                            
                            topControlBarContainer?.let { bar ->
                                val barLeft = bar.left
                                val barRight = bar.right
                                val barTop = bar.top
                                val barBottom = bar.bottom
                                if (x >= barLeft && x <= barRight && y >= barTop && y <= barBottom) {
                                    isOnControl = true
                                }
                            }
                        }
                        
                        // 检查是否点击在迷你模式按钮上（无论按钮是否可见，都要检查，因为按钮可能刚显示）
                        if (isMiniMode) {
                            val buttons = listOf(
                                miniModeCloseBtn,
                                miniModePlayPauseBtn,
                                miniModeRestoreBtn,
                                miniModeExpandBtn
                            )
                            buttons.forEach { button ->
                                button?.let { btn ->
                                    // 检查按钮是否可见或正在显示动画
                                    if (btn.visibility == View.VISIBLE || btn.alpha > 0f) {
                                        val btnLeft = btn.left
                                        val btnRight = btn.right
                                        val btnTop = btn.top
                                        val btnBottom = btn.bottom
                                        if (x >= btnLeft && x <= btnRight && y >= btnTop && y <= btnBottom) {
                                            isOnControl = true
                                            Log.d(TAG, "点击在迷你模式按钮上: ${btn.javaClass.simpleName}, visibility=${btn.visibility}, alpha=${btn.alpha}")
                                        }
                                    }
                                }
                            }
                        }
                        
                        if (isOnControl) {
                            // 点击在控制条或按钮上，不处理手势，让按钮正常响应
                            return@setOnTouchListener false
                        }
                        
                        // 记录起始位置和时间
                        seekStartX = event.x
                        seekStartY = event.y
                        seekStartTime = videoView.currentPosition
                        touchDownTime = System.currentTimeMillis()
                        isSeeking = false
                        
                        // 记录窗口初始位置（用于拖拽）
                        params?.let {
                            initialX = it.x
                            initialY = it.y
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            dX = initialTouchX - initialX
                            dY = initialTouchY - initialY
                        }
                        
                        // 隐藏所有控制提示（等待滑动时再显示）
                        brightnessControlView?.visibility = View.GONE
                        volumeControlView?.visibility = View.GONE
                        seekControlView?.visibility = View.GONE
                        
                        // 迷你模式下，返回false让手势检测器处理单击事件
                        if (isMiniMode) {
                            return@setOnTouchListener gestureResult
                        }
                        
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        // 如果处于迷你模式，禁用所有手势控制，只允许拖拽窗口（全屏移动）
                        if (isMiniMode) {
                            // 迷你模式下，移动时不自动显示按钮，只有单击时才显示
                            
                            // 迷你模式下，任何移动都视为拖拽窗口，不触发其他手势
                            isDraggingWindow = true
                            val rawDeltaY = event.rawY - initialTouchY
                            val rawDeltaX = event.rawX - initialTouchX
                            
                            // 允许全屏移动（X和Y都可以移动）
                            val currentParams = params ?: return@setOnTouchListener false
                            val currentWindowManager = windowManager ?: return@setOnTouchListener false
                            
                            // 计算有效范围，避免范围无效导致的异常
                            val maxX = (screenWidth - currentParams.width).coerceAtLeast(0)
                            val maxY = (screenHeight - currentParams.height).coerceAtLeast(0)
                            
                            val newX = (initialX + rawDeltaX.toInt()).coerceIn(0, maxX)
                            val newY = (initialY + rawDeltaY.toInt()).coerceIn(0, maxY)
                            
                            currentParams.x = newX
                            currentParams.y = newY
                            
                            currentWindowManager.updateViewLayout(floatingView, currentParams)
                            
                            return@setOnTouchListener true
                        }
                        
                        // 如果手势检测器已经处理了（双击或单击），不处理滑动
                        if (gestureHandled) {
                            return@setOnTouchListener false
                        }
                        
                        val x = event.x
                        val y = event.y
                        val containerWidth = container.width
                        val containerHeight = container.height
                        val leftRegion = containerWidth / 3
                        val rightRegion = containerWidth * 2 / 3
                        
                        // 计算移动距离
                        val deltaX = event.x - seekStartX
                        val deltaY = event.y - seekStartY
                        val elapsedTime = System.currentTimeMillis() - touchDownTime
                        
                        // 判断是否应该拖拽窗口：按住时间超过阈值，且是垂直滑动，且在中心区域
                        val isCenterRegion = x >= leftRegion && x <= rightRegion
                        val isVerticalSwipe = kotlin.math.abs(deltaY) > kotlin.math.abs(deltaX) && kotlin.math.abs(deltaY) > MIN_SWIPE_DISTANCE
                        
                        if (elapsedTime > DRAG_DELAY_THRESHOLD && isVerticalSwipe && isCenterRegion) {
                            // 拖拽窗口：处理窗口移动
                            isDraggingWindow = true
                            // 使用原始触摸坐标计算窗口移动
                            val rawDeltaY = event.rawY - initialTouchY
                            handleWindowDrag(rawDeltaY)
                            return@setOnTouchListener true
                        }
                        
                        // 检查是水平滑动还是垂直滑动（持续处理，不限制时间）
                        if (kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) && kotlin.math.abs(deltaX) > MIN_SWIPE_DISTANCE) {
                            // 水平滑动：快进/后退
                            isSeeking = true
                            brightnessControlView?.visibility = View.GONE
                            volumeControlView?.visibility = View.GONE
                            handleSeek(deltaX, containerWidth)
                        } else if (kotlin.math.abs(deltaY) > kotlin.math.abs(deltaX) && kotlin.math.abs(deltaY) > MIN_SWIPE_DISTANCE) {
                            // 垂直滑动：透明度或音量控制（持续处理，确保连续性）
                            isSeeking = false
                            seekControlView?.visibility = View.GONE
                            if (x < leftRegion) {
                                // 左侧上下滑动：调节透明度
                                brightnessControlView?.visibility = View.VISIBLE
                                volumeControlView?.visibility = View.GONE
                                handleBrightnessChange(y, containerHeight)
                            } else if (x > rightRegion) {
                                // 右侧上下滑动：调节音量
                                volumeControlView?.visibility = View.VISIBLE
                                brightnessControlView?.visibility = View.GONE
                                handleVolumeChange(y, containerHeight)
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        // 隐藏控制提示
                        brightnessControlView?.visibility = View.GONE
                        volumeControlView?.visibility = View.GONE
                        seekControlView?.visibility = View.GONE
                        isSeeking = false
                        
                        // 检查是否点击在迷你模式按钮上（在ACTION_UP时也要检查，确保按钮点击不被拦截）
                        if (isMiniMode) {
                            val x = event.x.toInt()
                            val y = event.y.toInt()
                            val buttons = listOf(
                                miniModeCloseBtn,
                                miniModePlayPauseBtn,
                                miniModeRestoreBtn,
                                miniModeExpandBtn
                            )
                            buttons.forEach { button ->
                                button?.let { btn ->
                                    // 检查按钮是否可见或正在显示动画
                                    if (btn.visibility == View.VISIBLE || btn.alpha > 0f) {
                                        val btnLeft = btn.left
                                        val btnRight = btn.right
                                        val btnTop = btn.top
                                        val btnBottom = btn.bottom
                                        if (x >= btnLeft && x <= btnRight && y >= btnTop && y <= btnBottom) {
                                            // 点击在按钮上，不处理手势，让按钮正常响应
                                            gestureHandled = false
                                            isDraggingWindow = false
                                            return@setOnTouchListener false
                                        }
                                    }
                                }
                            }
                        }
                        
                        // 迷你模式下，如果手势检测器处理了单击事件，确保按钮显示/隐藏
                        if (isMiniMode && gestureHandled && !isDraggingWindow) {
                            // 手势检测器已经处理了单击，按钮应该已经显示/隐藏
                            // 按钮保持显示，不自动隐藏（由用户点击切换）
                            gestureHandled = false
                            isDraggingWindow = false
                            return@setOnTouchListener true
                        }
                        
                        // 如果手势检测器已经处理了（双击或单击），不处理其他逻辑
                        if (gestureHandled) {
                            gestureHandled = false
                            isDraggingWindow = false
                            return@setOnTouchListener false
                        }
                        
                        gestureHandled = false
                        isDraggingWindow = false
                        true
                    }
                    else -> false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "初始化手势控制失败", e)
        }
    }
    
    /**
     * 创建控制提示视图
     */
    private fun createControlHintViews(container: FrameLayout) {
        // 亮度控制提示视图（带图标和动画）
        brightnessControlView = android.widget.TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
            
            // 使用圆角背景
            val drawable = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xE6000000.toInt())
                cornerRadius = dpToPx(12).toFloat()
            }
            background = drawable
            
            setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(16))
            visibility = View.GONE
            gravity = Gravity.CENTER
            
            // 不添加图标，只显示数值
        }
        container.addView(brightnessControlView)
        
        // 音量控制提示视图（带图标和动画）
        volumeControlView = android.widget.TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
            
            // 使用圆角背景
            val drawable = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xE6000000.toInt())
                cornerRadius = dpToPx(12).toFloat()
            }
            background = drawable
            
            setPadding(dpToPx(24), dpToPx(16), dpToPx(24), dpToPx(16))
            visibility = View.GONE
            gravity = Gravity.CENTER
            
            // 不添加图标，只显示数值
        }
        container.addView(volumeControlView)
        
        // 快进/后退提示视图
        seekControlView = android.widget.TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            textSize = 20f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xCC000000.toInt())
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
            visibility = View.GONE
            gravity = Gravity.CENTER
        }
        container.addView(seekControlView)
    }
    
    /**
     * 处理快进/后退
     */
    private fun handleSeek(deltaX: Float, containerWidth: Int) {
        try {
            val vv = videoView ?: return
            if (vv.duration <= 0) return
            
            // 计算快进/后退的时间（幅度越大，速度越快）
            val seekRatio = deltaX / containerWidth // 滑动距离占屏幕宽度的比例
            val seekTime = (seekRatio * vv.duration * SEEK_SPEED_FACTOR).toInt()
            val targetTime = (seekStartTime + seekTime).coerceIn(0, vv.duration)
            
            // 跳转到目标时间
            vv.seekTo(targetTime)
            
            // 更新进度条
            val progressPercent = (targetTime * 100 / vv.duration).coerceIn(0, 100)
            progressBar?.progress = progressPercent
            currentTimeText?.text = formatTime(targetTime)
            
            // 显示提示信息
            seekControlView?.visibility = View.VISIBLE
            val timeText = if (seekTime > 0) {
                "+${formatTime(seekTime)}"
            } else {
                formatTime(seekTime)
            }
            seekControlView?.text = timeText
            
            Log.d(TAG, "快进/后退: ${formatTime(seekTime)}, 目标时间: ${formatTime(targetTime)}")
        } catch (e: Exception) {
            Log.e(TAG, "处理快进/后退失败", e)
        }
    }
    
    /**
     * 处理透明度调节（原亮度调节改为透明度调节）
     */
    private fun handleBrightnessChange(y: Float, containerHeight: Int) {
        try {
            // 计算透明度变化（向上滑动减少透明度，向下滑动增加透明度）
            val ratio = 1.0f - (y / containerHeight) // 0在顶部，1在底部
            val newAlpha = ratio.coerceIn(0.3f, 1.0f) // 透明度范围30%-100%
            
            // 设置整个悬浮窗的透明度（包括视频和控制条）
            // 使用setAlpha方法确保透明度正确应用到所有子视图
            floatingView?.alpha = newAlpha
            
            // 确保VideoView也应用透明度（VideoView可能需要特殊处理）
            videoView?.let { vv ->
                vv.alpha = newAlpha
                // 如果VideoView内部有SurfaceView，也需要设置透明度
                try {
                    // 通过反射获取VideoView内部的SurfaceView并设置透明度
                    val surfaceViewField = vv.javaClass.getDeclaredField("mSurfaceView")
                    surfaceViewField.isAccessible = true
                    val surfaceView = surfaceViewField.get(vv) as? android.view.SurfaceView
                    surfaceView?.alpha = newAlpha
                } catch (e: Exception) {
                    // 反射失败不影响，使用默认alpha
                    Log.d(TAG, "无法通过反射设置SurfaceView透明度: ${e.message}")
                }
            }
            
            // 确保控制条也应用透明度
            controlBar?.alpha = newAlpha
            topControlBarContainer?.alpha = newAlpha
            
            // 显示透明度提示（只显示数值，实时更新）
            val alphaPercent = (newAlpha * 100).toInt()
            brightnessControlView?.text = "${alphaPercent}%"
            
            // 确保提示视图可见（实时显示，不添加动画延迟）
            if (brightnessControlView?.visibility != View.VISIBLE) {
                brightnessControlView?.alpha = 1f
                brightnessControlView?.visibility = View.VISIBLE
            }
            
            Log.d(TAG, "透明度调节: ${alphaPercent}%")
        } catch (e: Exception) {
            Log.e(TAG, "处理透明度调节失败", e)
        }
    }
    
    /**
     * 处理音量调节
     */
    private fun handleVolumeChange(y: Float, containerHeight: Int) {
        try {
            val am = audioManager ?: return
            
            // 计算音量变化（向上滑动减少音量，向下滑动增加音量）
            val ratio = 1.0f - (y / containerHeight) // 0在顶部，1在底部
            val targetVolume = (ratio * maxVolume).toInt().coerceIn(0, maxVolume)
            
            // 设置音量
            am.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
            currentVolume = targetVolume
            
            // 显示音量提示（只显示数值，实时更新）
            val volumePercent = (targetVolume * 100 / maxVolume)
            volumeControlView?.text = "${volumePercent}%"
            
            // 确保提示视图可见（实时显示，不添加动画延迟）
            if (volumeControlView?.visibility != View.VISIBLE) {
                volumeControlView?.alpha = 1f
                volumeControlView?.visibility = View.VISIBLE
            }
            
            Log.d(TAG, "音量调节: ${volumePercent}%")
        } catch (e: Exception) {
            Log.e(TAG, "处理音量调节失败", e)
        }
    }
    
    /**
     * 处理窗口拖拽
     */
    private fun handleWindowDrag(rawDeltaY: Float) {
        try {
            val p = params ?: return
            val wm = windowManager ?: return
            
            // 计算新位置（直接跟随手指，无阻尼）
            // 计算有效范围，避免范围无效导致的异常
            val maxY = (screenHeight - p.height).coerceAtLeast(0)
            val newY = (initialY + rawDeltaY.toInt()).coerceIn(0, maxY).toInt()
            
            // 只允许垂直拖动，保持X坐标不变（居中）
            p.x = originalX
            p.y = newY
            
            // 检查是否拖拽到屏幕中间以下，如果是则进入迷你模式
            val screenCenterY = screenHeight / 2
            val shouldBeMiniMode = newY > screenCenterY
            
            if (shouldBeMiniMode && !isMiniMode) {
                // 进入迷你模式
                enterMiniMode()
            } else if (!shouldBeMiniMode && isMiniMode) {
                // 退出迷你模式
                exitMiniMode()
            }
            
            // 更新窗口位置
            wm.updateViewLayout(floatingView, p)
            
            Log.d(TAG, "窗口拖拽: y=${p.y}, 迷你模式: $isMiniMode")
        } catch (e: Exception) {
            Log.e(TAG, "处理窗口拖拽失败", e)
        }
    }
    
    
    /**
     * 启用拖拽功能（带阻尼感）
     * 注意：关闭按钮和控制条点击时不应触发拖拽
     * 注意：手势控制优先于拖拽，如果检测到手势，不触发拖拽
     */
    private fun enableDrag(view: View) {
        var isDragging = false
        var lastMoveTime = 0L
        var lastY = 0f
        
        view.setOnTouchListener { v, event ->
            // 如果正在手势控制（快进/后退、亮度、音量），不处理拖拽
            // 但如果正在拖拽窗口，继续处理拖拽
            if (!isDraggingWindow && (isSeeking || 
                brightnessControlView?.visibility == View.VISIBLE ||
                volumeControlView?.visibility == View.VISIBLE ||
                seekControlView?.visibility == View.VISIBLE)) {
                return@setOnTouchListener false
            }
            
            // 如果正在拖拽窗口，处理拖拽
            if (isDraggingWindow) {
                // 继续处理拖拽逻辑
            }
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val x = event.x.toInt()
                    val y = event.y.toInt()
                    
                    // 检查是否点击在控制条或按钮上
                    var isOnControl = false
                    controlBar?.let { bar ->
                        val barLeft = bar.left
                        val barRight = bar.right
                        val barTop = bar.top
                        val barBottom = bar.bottom
                        if (x >= barLeft && x <= barRight && y >= barTop && y <= barBottom) {
                            isOnControl = true
                        }
                    }
                    
                    topControlBarContainer?.let { bar ->
                        val barLeft = bar.left
                        val barRight = bar.right
                        val barTop = bar.top
                        val barBottom = bar.bottom
                        if (x >= barLeft && x <= barRight && y >= barTop && y <= barBottom) {
                            isOnControl = true
                        }
                    }
                    
                    closeBtn?.let { btn ->
                        val btnX = btn.left
                        val btnY = btn.top
                        val btnRight = btn.right
                        val btnBottom = btn.bottom
                        if (x >= btnX && x <= btnRight && y >= btnY && y <= btnBottom) {
                            isOnControl = true
                        }
                    }
                    
                    if (isOnControl) {
                        // 点击在控制条或按钮上，不处理拖拽
                        return@setOnTouchListener false
                    }
                    
                    // 记录初始触摸位置和窗口位置
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    lastY = event.rawY
                    lastMoveTime = System.currentTimeMillis()
                    isDragging = false
                    
                    params?.let {
                        initialX = it.x
                        initialY = it.y
                        dX = initialTouchX - initialX
                        dY = initialTouchY - initialY
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params?.let {
                        val currentTime = System.currentTimeMillis()
                        val deltaTime = currentTime - lastMoveTime
                        lastY = event.rawY
                        lastMoveTime = currentTime
                        
                        // 计算新位置（带阻尼：移动距离 = 实际距离 * 阻尼系数）
                        val dampingFactor = 0.85f // 阻尼系数，0.85表示85%的移动量，产生阻尼感
                        val rawNewY = (event.rawY - dY).toInt()
                        val deltaYFromInitial = rawNewY - initialY
                        val dampedDeltaY = (deltaYFromInitial * dampingFactor).toInt()
                        var newY = initialY + dampedDeltaY
                        
                        // 只允许垂直拖动，保持X坐标不变（居中）
                        val currentX = initialX
                        
                        // 限制Y坐标在屏幕范围内（不能移出屏幕）
                        val currentHeight = it.height
                        // 计算有效范围，避免范围无效导致的异常
                        val maxY = (screenHeight - currentHeight).coerceAtLeast(0)
                        newY = newY.coerceIn(0, maxY)
                        
                        it.x = currentX
                        it.y = newY
                        windowManager?.updateViewLayout(floatingView, it)
                        
                        // 如果移动距离超过阈值，标记为拖拽中
                        if (kotlin.math.abs(dampedDeltaY) > dpToPx(5)) {
                            isDragging = true
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        isDragging = false
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 清理资源
     */
    fun destroy() {
        try {
            hide()
            floatingView?.let { view ->
                windowManager?.removeView(view)
            }
            floatingView = null
            videoView = null
            closeBtn = null
            controlBar = null
            playPauseBtn = null
            muteBtn = null
            fullscreenBtn = null
            speedBtn = null
            loopBtn = null
            playlistBtn = null
            
            // 退出迷你模式
            if (isMiniMode) {
                exitMiniMode()
            }
            
            progressBar = null
            currentTimeText = null
            totalTimeText = null
            updateRunnable?.let { updateHandler?.removeCallbacks(it) }
            updateHandler = null
            updateRunnable = null
            // 清理手势相关资源
            gestureDetector = null
            brightnessControlView = null
            volumeControlView = null
            seekControlView = null
            audioManager = null
            topControlBarContainer = null
            params = null
            Log.d(TAG, "系统级悬浮窗播放器已销毁")
        } catch (e: Exception) {
            Log.e(TAG, "销毁悬浮窗播放器失败", e)
        }
    }

    /**
     * 切换控制条显示/隐藏
     */
    private fun toggleControls() {
        try {
            val topBar = topControlBarContainer
            val bottomBar = controlBar
            
            if (topBar?.visibility == View.VISIBLE) {
                // 隐藏控制条
                hideControls()
            } else {
                // 显示控制条
                showControls()
            }
        } catch (e: Exception) {
            Log.e(TAG, "切换控制条失败", e)
        }
    }
    
    /**
     * 显示控制条
     * 必须在主线程调用
     */
    private fun showControls() {
        try {
            // 确保在主线程执行
            if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    showControls()
                }
                return
            }
            
            // 只在非迷你模式下显示控制条
            if (!isMiniMode) {
                topControlBarContainer?.visibility = View.VISIBLE
                controlBar?.visibility = View.VISIBLE
                // 不再自动隐藏控制条，让进度条、关闭按钮、全屏按钮始终可见
                // scheduleHideControls() // 已禁用自动隐藏
                Log.d(TAG, "控制条已显示（永久显示）")
            } else {
                Log.d(TAG, "迷你模式下不显示控制条")
            }
        } catch (e: Exception) {
            Log.e(TAG, "显示控制条失败", e)
        }
    }
    
    /**
     * 隐藏控制条
     * 必须在主线程调用
     */
    private fun hideControls() {
        try {
            // 确保在主线程执行
            if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    hideControls()
                }
                return
            }
            
            // 再次检查视图是否仍然有效
            val topBar = topControlBarContainer
            val bottomBar = controlBar
            if (topBar != null && bottomBar != null) {
                topBar.visibility = View.GONE
                bottomBar.visibility = View.GONE
            }
            cancelHideControls() // 取消自动隐藏任务
            Log.d(TAG, "控制条已隐藏")
        } catch (e: Exception) {
            Log.e(TAG, "隐藏控制条失败", e)
        }
    }
    
    /**
     * 安排自动隐藏控制条
     * 必须在主线程调用
     */
    private fun scheduleHideControls() {
        try {
            // 确保在主线程执行
            if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    scheduleHideControls()
                }
                return
            }
            
            // 取消之前的任务
            cancelHideControls()
            
            // 确保 Handler 已初始化
            if (hideControlsHandler == null) {
                hideControlsHandler = android.os.Handler(android.os.Looper.getMainLooper())
            }
            
            // 创建新的隐藏任务（确保在主线程执行）
            hideControlsRunnable = Runnable {
                // Runnable 已经在主线程的 Handler 中执行，但为了安全再次检查
                try {
                    if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                        hideControls()
                    } else {
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            hideControls()
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "自动隐藏控制条失败", e)
                }
            }
            
            // 3秒后自动隐藏
            hideControlsHandler?.postDelayed(hideControlsRunnable!!, CONTROLS_AUTO_HIDE_DELAY)
        } catch (e: Exception) {
            Log.e(TAG, "安排自动隐藏失败", e)
        }
    }
    
    /**
     * 取消自动隐藏任务
     */
    private fun cancelHideControls() {
        try {
            hideControlsRunnable?.let { hideControlsHandler?.removeCallbacks(it) }
            hideControlsRunnable = null
        } catch (e: Exception) {
            Log.e(TAG, "取消自动隐藏失败", e)
        }
    }
    
    /**
     * 自动调整视频窗口大小
     * 竖屏视频自动最大化，横屏视频居中播放
     */
    private fun adjustVideoWindowSize() {
        try {
            if (params == null || floatingView == null || windowManager == null) return
            
            val displayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            val isScreenLandscape = screenWidth > screenHeight
            
            // 如果视频尺寸未知，使用默认逻辑
            if (videoWidth <= 0 || videoHeight <= 0) {
                // 视频尺寸未知时，根据屏幕方向决定
                if (isScreenLandscape) {
                    // 横屏时，使用全屏
                    if (!isFullscreen) {
                        params?.width = screenWidth
                        params?.height = screenHeight
                        params?.x = 0
                        params?.y = 0
                        
                        floatingView?.layoutParams?.width = screenWidth
                        floatingView?.layoutParams?.height = screenHeight
                        
                        windowManager?.updateViewLayout(floatingView, params)
                        isFullscreen = true
                        Log.d(TAG, "屏幕横屏，视频已自动最大化")
                    }
                }
                return
            }
            
            if (isVideoPortrait) {
                // 竖屏视频：靠近顶部播放，留出下方操作空间（固定位置）
                if (!hasAutoMaximized || params?.width != screenWidth || params?.height != (screenHeight * 0.7f).toInt() || params?.x != 0 || params?.y != 0) {
                    hasAutoMaximized = true
                    
                    // 竖屏视频：使用屏幕宽度，高度为屏幕高度的70%，靠近顶部
                    val targetWidth = screenWidth
                    val targetHeight = (screenHeight * 0.7f).toInt()
                    
                    params?.width = targetWidth
                    params?.height = targetHeight
                    params?.x = 0
                    params?.y = 0 // 固定位置：靠近顶部
                    
                    floatingView?.layoutParams?.width = targetWidth
                    floatingView?.layoutParams?.height = targetHeight
                    
                    // 确保VideoView填满整个容器（宽度和高度都填满，避免黑边）
                    videoView?.layoutParams?.width = FrameLayout.LayoutParams.MATCH_PARENT
                    videoView?.layoutParams?.height = FrameLayout.LayoutParams.MATCH_PARENT
                    
                    // 设置视频缩放模式，确保竖屏视频填满宽度（避免右边黑边）
                    try {
                        val mediaPlayerField = videoView?.javaClass?.getDeclaredField("mMediaPlayer")
                        mediaPlayerField?.isAccessible = true
                        val mediaPlayer = mediaPlayerField?.get(videoView) as? android.media.MediaPlayer
                        // 使用 SCALE_TO_FIT_WITH_CROPPING 确保视频填满容器宽度，避免右边黑边
                        mediaPlayer?.setVideoScalingMode(android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                        Log.d(TAG, "竖屏视频已设置缩放模式为SCALE_TO_FIT_WITH_CROPPING，确保宽度最大化，避免右边黑边")
                    } catch (e: Exception) {
                        Log.w(TAG, "设置视频缩放模式失败", e)
                    }
                    
                    windowManager?.updateViewLayout(floatingView, params)
                    isFullscreen = false
                    
                    // 更新原始尺寸和位置（固定位置）
                    originalWidth = targetWidth
                    originalHeight = targetHeight
                    originalX = 0
                    originalY = 0
                    
                    // 确保全屏按钮可见
                    adjustFullscreenControls()
                    
                    Log.d(TAG, "竖屏视频已调整为靠近顶部播放（宽度最大化）: ${targetWidth}x${targetHeight}, 位置: (0, 0)")
                }
            } else {
                // 横屏视频：需要确保填满屏幕宽度，不要有空白
                // 如果之前是竖屏视频全屏状态，需要强制调整
                if (!hasAutoMaximized || (isFullscreen && params?.width != screenWidth)) {
                    hasAutoMaximized = true
                    
                    if (isScreenLandscape) {
                        // 屏幕横屏：横屏视频应该全屏
                        params?.width = screenWidth
                        params?.height = screenHeight
                        params?.x = 0
                        params?.y = 0
                        
                        floatingView?.layoutParams?.width = screenWidth
                        floatingView?.layoutParams?.height = screenHeight
                        
                        // 确保VideoView填满容器
                        videoView?.layoutParams?.width = FrameLayout.LayoutParams.MATCH_PARENT
                        videoView?.layoutParams?.height = FrameLayout.LayoutParams.MATCH_PARENT
                        
                        // 设置视频缩放模式，确保填满屏幕
                        try {
                            val mediaPlayerField = videoView?.javaClass?.getDeclaredField("mMediaPlayer")
                            mediaPlayerField?.isAccessible = true
                            val mediaPlayer = mediaPlayerField?.get(videoView) as? android.media.MediaPlayer
                            mediaPlayer?.setVideoScalingMode(android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                        } catch (e: Exception) {
                            Log.w(TAG, "设置视频缩放模式失败", e)
                        }
                        
                        windowManager?.updateViewLayout(floatingView, params)
                        isFullscreen = true
                        
                        // 更新原始尺寸
                        originalWidth = screenWidth
                        originalHeight = screenHeight
                        originalX = 0
                        originalY = 0
                        
                        adjustFullscreenControls()
                        Log.d(TAG, "横屏视频在横屏屏幕上已全屏: ${screenWidth}x${screenHeight}")
                    } else {
                        // 屏幕竖屏：横屏视频应该填满屏幕宽度，高度按比例，固定位置（居中）
                        val targetWidth = screenWidth
                        val targetHeight = (screenWidth * 9 / 16).coerceAtMost(screenHeight)
                        val targetX = 0 // 固定位置：左对齐
                        val targetY = (screenHeight - targetHeight) / 2 // 固定位置：垂直居中
                        
                        params?.width = targetWidth
                        params?.height = targetHeight
                        params?.x = targetX
                        params?.y = targetY
                        
                        floatingView?.layoutParams?.width = targetWidth
                        floatingView?.layoutParams?.height = targetHeight
                        
                        // 确保VideoView填满容器
                        videoView?.layoutParams?.width = FrameLayout.LayoutParams.MATCH_PARENT
                        videoView?.layoutParams?.height = FrameLayout.LayoutParams.MATCH_PARENT
                        
                        // 设置视频缩放模式，确保填满宽度
                        try {
                            val mediaPlayerField = videoView?.javaClass?.getDeclaredField("mMediaPlayer")
                            mediaPlayerField?.isAccessible = true
                            val mediaPlayer = mediaPlayerField?.get(videoView) as? android.media.MediaPlayer
                            mediaPlayer?.setVideoScalingMode(android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                        } catch (e: Exception) {
                            Log.w(TAG, "设置视频缩放模式失败", e)
                        }
                        
                        windowManager?.updateViewLayout(floatingView, params)
                        isFullscreen = false
                        
                        // 更新原始尺寸和位置（固定位置）
                        originalWidth = targetWidth
                        originalHeight = targetHeight
                        originalX = targetX
                        originalY = targetY
                        
                        // 调整控制条位置：菜单靠近屏幕正下方
                        controlBar?.layoutParams?.let { layoutParams ->
                            if (layoutParams is FrameLayout.LayoutParams) {
                                layoutParams.bottomMargin = 0 // 紧贴屏幕底部
                            }
                        }
                        topControlBarContainer?.layoutParams?.let { layoutParams ->
                            if (layoutParams is FrameLayout.LayoutParams) {
                                layoutParams.topMargin = 0 // 紧贴屏幕顶部
                            }
                        }
                        
                        Log.d(TAG, "横屏视频在竖屏屏幕上已调整（固定位置，菜单靠近屏幕正下方）: ${targetWidth}x${targetHeight}, 位置: ($targetX, $targetY)")
                    }
                } else if (isScreenLandscape && !isFullscreen) {
                    // 如果屏幕横屏但视频不是全屏，自动最大化
                    params?.width = screenWidth
                    params?.height = screenHeight
                    params?.x = 0
                    params?.y = 0
                    
                    floatingView?.layoutParams?.width = screenWidth
                    floatingView?.layoutParams?.height = screenHeight
                    
                    videoView?.layoutParams?.width = FrameLayout.LayoutParams.MATCH_PARENT
                    videoView?.layoutParams?.height = FrameLayout.LayoutParams.MATCH_PARENT
                    
                    try {
                        val mediaPlayerField = videoView?.javaClass?.getDeclaredField("mMediaPlayer")
                        mediaPlayerField?.isAccessible = true
                        val mediaPlayer = mediaPlayerField?.get(videoView) as? android.media.MediaPlayer
                        mediaPlayer?.setVideoScalingMode(android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                    } catch (e: Exception) {
                        Log.w(TAG, "设置视频缩放模式失败", e)
                    }
                    
                    windowManager?.updateViewLayout(floatingView, params)
                    isFullscreen = true
                    adjustFullscreenControls()
                    Log.d(TAG, "用户横屏，横屏视频已自动最大化")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "自动调整视频窗口大小失败", e)
        }
    }
    
    /**
     * 检查并处理屏幕方向变化
     * 必须在主线程调用
     */
    private fun checkAndHandleOrientationChange() {
        try {
            // 确保在主线程执行
            if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    checkAndHandleOrientationChange()
                }
                return
            }
            
            val currentOrientation = context.resources.configuration.orientation
            if (currentOrientation != lastScreenOrientation && lastScreenOrientation != Configuration.ORIENTATION_UNDEFINED) {
                // 屏幕方向已改变
                Log.d(TAG, "检测到屏幕方向变化: $lastScreenOrientation -> $currentOrientation")
                
                // 更新记录的屏幕方向
                lastScreenOrientation = currentOrientation
                
                // 如果当前是全屏模式，重新调整窗口尺寸以适应新方向
                if (isFullscreen && params != null && floatingView != null && windowManager != null) {
                    val displayMetrics = context.resources.displayMetrics
                    val newScreenWidth = displayMetrics.widthPixels
                    val newScreenHeight = displayMetrics.heightPixels
                    val isNewScreenLandscape = newScreenWidth > newScreenHeight
                    
                    // 更新屏幕尺寸
                    screenWidth = newScreenWidth
                    screenHeight = newScreenHeight
                    
                    // 如果从竖屏切换到横屏，且视频是横屏视频，需要恢复横屏状态（不要有空白区域）
                    if (isNewScreenLandscape && !isVideoPortrait) {
                        // 横屏视频在横屏屏幕上：使用全屏，但确保视频填满（无空白）
                        params?.width = newScreenWidth
                        params?.height = newScreenHeight
                        params?.x = 0
                        params?.y = 0
                        
                        floatingView?.layoutParams?.width = newScreenWidth
                        floatingView?.layoutParams?.height = newScreenHeight
                        
                        // 确保VideoView填满容器
                        videoView?.layoutParams?.width = FrameLayout.LayoutParams.MATCH_PARENT
                        videoView?.layoutParams?.height = FrameLayout.LayoutParams.MATCH_PARENT
                        
                        windowManager?.updateViewLayout(floatingView, params)
                        
                        Log.d(TAG, "竖屏切换到横屏，横屏视频已调整为全屏: ${newScreenWidth}x${newScreenHeight}")
                    } else {
                        // 其他情况：重新设置全屏尺寸
                        params?.width = newScreenWidth
                        params?.height = newScreenHeight
                        params?.x = 0
                        params?.y = 0
                        
                        floatingView?.layoutParams?.width = newScreenWidth
                        floatingView?.layoutParams?.height = newScreenHeight
                        
                        // 确保VideoView填满容器
                        videoView?.layoutParams?.width = FrameLayout.LayoutParams.MATCH_PARENT
                        videoView?.layoutParams?.height = FrameLayout.LayoutParams.MATCH_PARENT
                        
                        windowManager?.updateViewLayout(floatingView, params)
                        
                        Log.d(TAG, "屏幕方向变化后已调整窗口尺寸: ${newScreenWidth}x${newScreenHeight}")
                    }
                } else {
                    // 非全屏模式，也调整窗口以适应新方向
                    adjustVideoWindowSize()
                }
            } else if (lastScreenOrientation == Configuration.ORIENTATION_UNDEFINED) {
                // 首次记录屏幕方向
                lastScreenOrientation = currentOrientation
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查屏幕方向变化失败", e)
        }
    }
    
    /**
     * 重启视频播放器（修复假死问题）
     */
    private fun restartVideoPlayer() {
        try {
            val url = currentVideoUrl
            if (url.isNullOrBlank()) {
                Toast.makeText(context, "无法重启：视频URL为空", Toast.LENGTH_SHORT).show()
                return
            }
            
            Log.d(TAG, "重启视频播放器: $url")
            Toast.makeText(context, "正在重启播放器...", Toast.LENGTH_SHORT).show()
            
            // 保存当前播放位置
            val currentPosition = videoView?.currentPosition ?: 0
            val wasPlaying = videoView?.isPlaying ?: false
            
            // 停止当前播放
            videoView?.stopPlayback()
            videoView?.setOnPreparedListener(null)
            videoView?.setOnCompletionListener(null)
            videoView?.setOnErrorListener(null)
            
            // 清理进度更新
            updateRunnable?.let { updateHandler?.removeCallbacks(it) }
            updateRunnable = null
            
            // 重新设置视频源，对于 HTTP/HTTPS URL 使用 setVideoPath
            try {
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    videoView?.setVideoPath(url)
                } else if (url.startsWith("content://") || url.startsWith("file://")) {
                    videoView?.setVideoURI(Uri.parse(url))
                } else {
                    videoView?.setVideoPath(url)
                }
                Log.d(TAG, "重启时视频源已设置: $url")
            } catch (e: Exception) {
                Log.e(TAG, "重启时设置视频源失败: $url", e)
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    Toast.makeText(context, "无法加载视频: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                return
            }
            
            // 重新设置监听器
            videoView?.setOnPreparedListener { mediaPlayer ->
                try {
                    mediaPlayer.isLooping = isLooping
                    
                    // 设置视频缩放模式
                    try {
                        mediaPlayer.setVideoScalingMode(android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                    } catch (e: Exception) {
                        Log.w(TAG, "设置视频缩放模式失败", e)
                    }
                    
                    // 恢复播放位置
                    if (currentPosition > 0 && currentPosition < mediaPlayer.duration) {
                        mediaPlayer.seekTo(currentPosition)
                    }
                    
                    // 如果之前正在播放，继续播放
                    if (wasPlaying) {
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            videoView?.start()
                            playPauseBtn?.setImageResource(android.R.drawable.ic_media_pause)
                        }, 100)
                    }
                    
                    // 重新启动进度更新
                    updateHandler = android.os.Handler(android.os.Looper.getMainLooper())
                    updateRunnable = object : Runnable {
                        private var lastProgress = -1
                        private var stuckCount = 0
                        
                        override fun run() {
                            try {
                                // 确保在主线程执行
                                if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
                                    updateHandler?.post(this)
                                    return
                                }
                                
                                val vv = videoView
                                if (vv != null && vv.isPlaying && vv.duration > 0) {
                                    val current = vv.currentPosition
                                    val total = vv.duration
                                    val progressPercent = (current * 100 / total).coerceIn(0, 100)
                                    
                                    // 检测假死
                                    if (current == lastProgress && lastProgress > 0) {
                                        stuckCount++
                                        if (stuckCount > 10) {
                                            Log.w(TAG, "检测到视频假死，尝试重启")
                                            stuckCount = 0
                                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                restartVideoPlayer()
                                            }
                                            return
                                        }
                                    } else {
                                        stuckCount = 0
                                        lastProgress = current
                                    }
                                    
                                    // 安全更新UI（已在主线程）
                                    try {
                                        progressBar?.progress = progressPercent
                                        currentTimeText?.text = formatTime(current)
                                        totalTimeText?.text = formatTime(total)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "更新UI失败", e)
                                    }
                                    
                                    // 检查屏幕方向变化（确保在主线程）
                                    try {
                                        checkAndHandleOrientationChange()
                                    } catch (e: Exception) {
                                        Log.e(TAG, "检查屏幕方向变化失败", e)
                                    }
                                }
                                updateHandler?.postDelayed(this, 500)
                            } catch (e: Exception) {
                                Log.e(TAG, "更新播放进度失败", e)
                                updateHandler?.postDelayed(this, 500)
                            }
                        }
                    }
                    updateHandler?.post(updateRunnable!!)
                    
                    Log.d(TAG, "视频播放器重启成功")
                    Toast.makeText(context, "播放器已重启", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e(TAG, "重启视频播放器失败", e)
                    Toast.makeText(context, "重启失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            
            videoView?.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "重启后视频播放错误: what=$what, extra=$extra")
                Toast.makeText(context, "播放错误，请检查网络或视频源", Toast.LENGTH_LONG).show()
                true
            }
            
            // 开始准备视频
            videoView?.requestFocus()
            
        } catch (e: Exception) {
            Log.e(TAG, "重启视频播放器失败", e)
            Toast.makeText(context, "重启失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 显示播放速度选择菜单
     */
    private fun showSpeedMenu(anchorView: View) {
        try {
            val popupMenu = PopupMenu(context, anchorView)
            
            // 添加所有速度选项
            speedOptions.forEachIndexed { index, speed ->
                popupMenu.menu.add(0, index, 0, "${speed}x")
            }
            
            // 设置当前选中的速度
            val currentIndex = speedOptions.indexOf(playbackSpeed)
            if (currentIndex >= 0) {
                popupMenu.menu.getItem(currentIndex)?.isChecked = true
            }
            
            // 设置菜单项点击监听
            popupMenu.setOnMenuItemClickListener { item ->
                val selectedIndex = item.itemId
                if (selectedIndex >= 0 && selectedIndex < speedOptions.size) {
                    val selectedSpeed = speedOptions[selectedIndex]
                    playbackSpeed = selectedSpeed
                    speedBtn?.text = "${playbackSpeed}x"
                    
                    // 应用播放速度
                    try {
                        val mediaPlayerField = VideoView::class.java.getDeclaredField("mMediaPlayer")
                        mediaPlayerField.isAccessible = true
                        val mediaPlayer = mediaPlayerField.get(videoView)
                        if (mediaPlayer != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            val setPlaybackParamsMethod = mediaPlayer.javaClass.getDeclaredMethod("setPlaybackParams", android.media.PlaybackParams::class.java)
                            val params = android.media.PlaybackParams()
                            params.speed = playbackSpeed
                            setPlaybackParamsMethod.invoke(mediaPlayer, params)
                            Log.d(TAG, "播放速度已设置为: ${playbackSpeed}x")
                            Toast.makeText(context, "播放速度: ${playbackSpeed}x", Toast.LENGTH_SHORT).show()
                        } else {
                            Log.w(TAG, "当前Android版本不支持播放速度调整")
                            Toast.makeText(context, "当前版本不支持播放速度调整", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "设置播放速度失败", e)
                        Toast.makeText(context, "设置播放速度失败", Toast.LENGTH_SHORT).show()
                    }
                }
                true
            }
            
            popupMenu.show()
        } catch (e: Exception) {
            Log.e(TAG, "显示播放速度菜单失败", e)
            Toast.makeText(context, "显示菜单失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * dp转px
     */
    private fun dpToPx(dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }
}


