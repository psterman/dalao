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
    private var screenshotBtn: ImageButton? = null
    private var progressBar: android.widget.SeekBar? = null
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
     */
    fun show(videoUrl: String?) {
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
            createFloatingView()
            val url = videoUrl.trim()
            
            Log.d(TAG, "准备播放视频: $url")
            
            currentVideoUrl = url
            hasAutoMaximized = false // 重置自动最大化标志
            videoView?.setVideoURI(Uri.parse(url))
            videoView?.setOnPreparedListener { mediaPlayer ->
                try {
                    mediaPlayer.isLooping = isLooping
                    
                    // 检测视频尺寸和宽高比
                    try {
                        videoWidth = mediaPlayer.videoWidth
                        videoHeight = mediaPlayer.videoHeight
                        isVideoPortrait = videoHeight > videoWidth
                        Log.d(TAG, "视频尺寸: ${videoWidth}x${videoHeight}, 是否为竖屏: $isVideoPortrait")
                    } catch (e: Exception) {
                        Log.w(TAG, "无法获取视频尺寸", e)
                        videoWidth = 0
                        videoHeight = 0
                        isVideoPortrait = false
                    }
                    
                    // 更新控制条状态
                    val duration = mediaPlayer.duration
                    Log.d(TAG, "视频准备完成，时长: ${duration}ms")
                    
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
                                                    
                                                    // 定期检查屏幕方向变化（每1秒检查一次，减少性能开销）
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
                hide()
                true
            }
            
            floatingView?.visibility = View.VISIBLE
            isShowing = true
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
            videoView?.stopPlayback()
            // 清理进度更新任务
            updateRunnable?.let { updateHandler?.removeCallbacks(it) }
            updateHandler = null
            updateRunnable = null
            // 清理自动隐藏任务
            cancelHideControls()
            hideControlsHandler = null
            hideControlsRunnable = null
            videoView?.setOnPreparedListener(null)
            videoView?.setOnCompletionListener(null)
            floatingView?.visibility = View.GONE
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
        if (floatingView != null) {
            return
        }

        val wm = windowManager ?: return
        
        // 获取屏幕尺寸
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels
        
        // 全屏宽度，高度按16:9比例计算
        val videoWidth = screenWidth
        val videoHeight = (screenWidth * 9 / 16) // 16:9 比例
        
        // 保存屏幕尺寸用于拖动限制
        this.screenWidth = screenWidth
        this.screenHeight = screenHeight
        
        // 保存原始尺寸（用于全屏切换）
        originalWidth = videoWidth
        originalHeight = videoHeight
        
        // 创建视频容器（作为主容器，直接添加到WindowManager）
        val container = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                videoWidth,
                videoHeight
            )
            setBackgroundColor(0xFF000000.toInt())
        }
        
        // 创建 VideoView
        val vv = VideoView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
            )
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
            videoWidth,  // 全屏宽度
            videoHeight, // 按16:9比例的高度
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                    or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            // 初始位置：屏幕居中
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth - videoWidth) / 2 // 水平居中
            y = (screenHeight - videoHeight) / 2 // 垂直居中
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
            // 缩小蒙版背景高度到一半：原来padding是4dp上下，现在改为2dp上下
            setPadding(dpToPx(4), dpToPx(1), dpToPx(4), dpToPx(1))
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
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
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
        
        // 创建全屏按钮（放在时间轴左侧）
        val topFullscreenBtn = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(28),
                dpToPx(28)
            ).apply {
                setMargins(0, 0, dpToPx(4), 0)
            }
            setImageResource(android.R.drawable.ic_menu_crop)
            setBackgroundColor(0x80000000.toInt())
            setPadding(dpToPx(2), dpToPx(2), dpToPx(2), dpToPx(2))
            setColorFilter(0xFFFFFFFF.toInt(), android.graphics.PorterDuff.Mode.SRC_IN)
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
                setMargins(0, dpToPx(8), 0, dpToPx(8))
            }
            max = 100
            progress = 0
            thumb = normalThumbDrawable
            thumbOffset = 0
            // 增加进度条的触摸区域
            setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14))
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
                    
                    // 重新安排自动隐藏
                    scheduleHideControls()
                    
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
            setOnClickListener { hide() }
        }
        
        // 将关闭按钮添加到顶部控制条
        topControlBarContainer.addView(closeBtn)
        
        // 保存关闭按钮引用
        this.closeBtn = closeBtn
        
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
        
        // 创建循环播放按钮
        loopBtn = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(36),
                dpToPx(36)
            ).apply {
                setMargins(dpToPx(4), 0, dpToPx(4), 0)
            }
            setImageResource(android.R.drawable.ic_menu_revert)
            setBackgroundColor(0x80000000.toInt())
            setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
            setOnClickListener {
                isLooping = !isLooping
                try {
                    val mediaPlayerField = VideoView::class.java.getDeclaredField("mMediaPlayer")
                    mediaPlayerField.isAccessible = true
                    val mediaPlayer = mediaPlayerField.get(videoView)
                    if (mediaPlayer != null) {
                        val setIsLoopingMethod = mediaPlayer.javaClass.getDeclaredMethod("setLooping", Boolean::class.java)
                        setIsLoopingMethod.invoke(mediaPlayer, isLooping)
                        
                        // 更新按钮外观
                        if (isLooping) {
                            setBackgroundColor(0xCC008000.toInt()) // 绿色背景表示开启
                        } else {
                            setBackgroundColor(0x80000000.toInt()) // 半透明背景表示关闭
                        }
                        Log.d(TAG, "循环播放已${if (isLooping) "开启" else "关闭"}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "设置循环播放失败", e)
                }
            }
        }
        
        // 创建截图按钮（Material Design风格）
        screenshotBtn = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(40),
                dpToPx(40)
            ).apply {
                setMargins(dpToPx(4), 0, dpToPx(4), 0)
            }
            setImageResource(android.R.drawable.ic_menu_camera)
            applyMaterialStyle(this)
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            setOnClickListener {
                captureScreenshot()
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
        
        // 创建全屏按钮（Material Design风格）
        fullscreenBtn = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(40),
                dpToPx(40)
            ).apply {
                setMargins(dpToPx(4), 0, dpToPx(8), 0)
            }
            setImageResource(android.R.drawable.ic_menu_crop)
            applyMaterialStyle(this)
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            setOnClickListener {
                toggleFullscreen()
            }
        }
        
        // 添加按钮到第二行
        buttonRow.addView(playPauseBtn)
        buttonRow.addView(speedBtn)
        buttonRow.addView(loopBtn)
        buttonRow.addView(screenshotBtn)
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
        buttonRow.addView(fullscreenBtn)
        
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
        
        // 初始化：控制条默认显示，3秒后自动隐藏
        hideControlsHandler = android.os.Handler(android.os.Looper.getMainLooper())
        showControls() // 默认显示控制条
        scheduleHideControls() // 3秒后自动隐藏
        
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
     * 截图当前视频帧
     */
    private fun captureScreenshot() {
        try {
            val vv = videoView ?: return
            
            // 通过反射获取VideoView的Surface或当前帧
            // VideoView没有直接的截图API，需要通过MediaPlayer或View截图
            // 方法1：对整个VideoView截图
            vv.isDrawingCacheEnabled = true
            vv.buildDrawingCache()
            val bitmap = vv.drawingCache
            if (bitmap != null) {
                // 保存截图
                saveScreenshot(bitmap)
                vv.isDrawingCacheEnabled = false
                Toast.makeText(context, "截图已保存", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "截图成功")
            } else {
                Log.w(TAG, "无法获取视频截图")
                Toast.makeText(context, "截图失败", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "截图失败", e)
            Toast.makeText(context, "截图失败: ${e.message}", Toast.LENGTH_SHORT).show()
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
                
                // 检测屏幕方向
                val displayMetrics = context.resources.displayMetrics
                val isLandscape = displayMetrics.widthPixels > displayMetrics.heightPixels
                
                if (isLandscape) {
                    // 横屏全屏：使用屏幕宽高
                    params?.width = screenWidth
                    params?.height = screenHeight
                } else {
                    // 竖屏全屏：使用屏幕宽高
                    params?.width = screenWidth
                    params?.height = screenHeight
                }
                
                params?.x = 0
                params?.y = 0
                
                // 更新容器尺寸
                floatingView?.layoutParams?.width = params?.width ?: screenWidth
                floatingView?.layoutParams?.height = params?.height ?: screenHeight
                
                // 调整控制条位置（全屏时靠近视频底部和顶部）
                controlBar?.layoutParams?.let { layoutParams ->
                    if (layoutParams is FrameLayout.LayoutParams) {
                        layoutParams.bottomMargin = dpToPx(16) // 全屏时控制条距离底部16dp
                    }
                }
                
                // 调整顶部控制条位置
                topControlBarContainer?.layoutParams?.let { layoutParams ->
                    if (layoutParams is FrameLayout.LayoutParams) {
                        layoutParams.topMargin = dpToPx(16) // 全屏时顶部控制条距离顶部16dp
                    }
                }
                
                // 全屏时，确保退出全屏按钮可见
                adjustFullscreenControls()
                
                fullscreenBtn?.setImageResource(android.R.drawable.ic_menu_revert)
                // 更新顶部全屏按钮图标
                topControlBarContainer?.let { container ->
                    if (container.childCount > 0) {
                        val topFullscreenBtn = container.getChildAt(0) as? ImageButton
                        topFullscreenBtn?.setImageResource(android.R.drawable.ic_menu_revert)
                    }
                }
                Toast.makeText(context, "全屏模式", Toast.LENGTH_SHORT).show()
            } else {
                // 退出全屏：恢复原始尺寸和位置
                params?.width = originalWidth
                params?.height = originalHeight
                params?.x = originalX
                params?.y = originalY
                
                // 更新容器尺寸
                floatingView?.layoutParams?.width = originalWidth
                floatingView?.layoutParams?.height = originalHeight
                
                // 恢复控制条位置
                controlBar?.layoutParams?.let { layoutParams ->
                    if (layoutParams is FrameLayout.LayoutParams) {
                        layoutParams.bottomMargin = 0
                    }
                }
                
                // 恢复按钮位置
                restoreNormalControls()
                
                fullscreenBtn?.setImageResource(android.R.drawable.ic_menu_crop)
                // 更新顶部全屏按钮图标
                topControlBarContainer?.let { container ->
                    if (container.childCount > 0) {
                        val topFullscreenBtn = container.getChildAt(0) as? ImageButton
                        topFullscreenBtn?.setImageResource(android.R.drawable.ic_menu_crop)
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
            val fullscreenBtn = this.fullscreenBtn
            
            if (closeBtn != null && fullscreenBtn != null) {
                // 全屏时，确保全屏按钮可见
                fullscreenBtn.visibility = View.VISIBLE
                
                // 尝试将全屏按钮移到关闭按钮旁边（右上角）
                // 但保持按钮在控制条中可见，而不是完全移除
                val buttonParams = fullscreenBtn.layoutParams as? LinearLayout.LayoutParams
                if (buttonParams != null) {
                    // 保持按钮在控制条中，但确保可见
                    fullscreenBtn.visibility = View.VISIBLE
                    Log.d(TAG, "全屏按钮保持 visible，在控制条中")
                } else {
                    // 如果已经是FrameLayout.LayoutParams，移动到右上角
                    val fullscreenParams = fullscreenBtn.layoutParams as? FrameLayout.LayoutParams
                    if (fullscreenParams != null) {
                        fullscreenParams.gravity = Gravity.TOP or Gravity.END
                        fullscreenParams.setMargins(0, dpToPx(4), closeBtn.width + dpToPx(8), 0)
                        fullscreenBtn.layoutParams = fullscreenParams
                        fullscreenBtn.visibility = View.VISIBLE
                    }
                }
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
            // 全屏按钮回到控制条中
            val fullscreenBtn = this.fullscreenBtn
            if (fullscreenBtn != null) {
                val buttonParams = fullscreenBtn.layoutParams as? LinearLayout.LayoutParams
                if (buttonParams != null) {
                    // 恢复为按钮行的布局参数
                    buttonParams.setMargins(dpToPx(4), 0, dpToPx(8), 0)
                    fullscreenBtn.layoutParams = buttonParams
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "恢复普通模式控制按钮失败", e)
        }
    }
    
    /**
     * 分享视频URL
     */
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
                    // 双击暂停/播放
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
                            showControls() // 显示控制条
                            scheduleHideControls() // 3秒后自动隐藏
                        } catch (e: Exception) {
                            Log.e(TAG, "双击控制失败", e)
                        }
                    }
                    return true
                }
                
                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    // 单击显示/隐藏控制条（只有在没有其他手势时才触发）
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
                        
                        // 检查是否点击在控制条或按钮上
                        val x = event.x.toInt()
                        val y = event.y.toInt()
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
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
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
                        
                        // 快速滑动：按住时间短，认为是快速手势
                        if (elapsedTime <= DRAG_DELAY_THRESHOLD) {
                            // 检查是水平滑动还是垂直滑动
                            if (kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) && kotlin.math.abs(deltaX) > MIN_SWIPE_DISTANCE) {
                                // 水平滑动：快进/后退
                                isSeeking = true
                                brightnessControlView?.visibility = View.GONE
                                volumeControlView?.visibility = View.GONE
                                handleSeek(deltaX, containerWidth)
                            } else if (kotlin.math.abs(deltaY) > kotlin.math.abs(deltaX) && kotlin.math.abs(deltaY) > MIN_SWIPE_DISTANCE) {
                                // 垂直滑动：亮度或音量控制
                                isSeeking = false
                                seekControlView?.visibility = View.GONE
                                if (x < leftRegion) {
                                    // 左侧上下滑动：调节亮度
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
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        // 隐藏控制提示
                        brightnessControlView?.visibility = View.GONE
                        volumeControlView?.visibility = View.GONE
                        seekControlView?.visibility = View.GONE
                        isSeeking = false
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
        // 亮度控制提示视图
        brightnessControlView = android.widget.TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xCC000000.toInt())
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
            visibility = View.GONE
            gravity = Gravity.CENTER
        }
        container.addView(brightnessControlView)
        
        // 音量控制提示视图
        volumeControlView = android.widget.TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            textSize = 24f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xCC000000.toInt())
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
            visibility = View.GONE
            gravity = Gravity.CENTER
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
     * 处理亮度调节
     */
    private fun handleBrightnessChange(y: Float, containerHeight: Int) {
        try {
            // 计算亮度变化（向上滑动减少亮度，向下滑动增加亮度）
            val ratio = 1.0f - (y / containerHeight) // 0在顶部，1在底部
            val newBrightness = ratio.coerceIn(0.1f, 1.0f) // 亮度范围10%-100%
            
            // 设置系统亮度
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.System.canWrite(context)) {
                    val brightnessValue = (newBrightness * 255).toInt().coerceIn(10, 255)
                    Settings.System.putInt(
                        context.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS,
                        brightnessValue
                    )
                    currentBrightness = newBrightness
                } else {
                    // 如果没有权限，尝试设置窗口亮度（仅对当前窗口有效）
                    (context as? Activity)?.window?.attributes = (context as? Activity)?.window?.attributes?.apply {
                        screenBrightness = newBrightness
                    }
                    currentBrightness = newBrightness
                }
            } else {
                val brightnessValue = (newBrightness * 255).toInt().coerceIn(10, 255)
                Settings.System.putInt(
                    context.contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    brightnessValue
                )
                currentBrightness = newBrightness
            }
            
            // 显示亮度提示
            brightnessControlView?.text = "${(newBrightness * 100).toInt()}%"
            
            Log.d(TAG, "亮度调节: ${(newBrightness * 100).toInt()}%")
        } catch (e: Exception) {
            Log.e(TAG, "处理亮度调节失败", e)
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
            
            // 显示音量提示
            val volumePercent = (targetVolume * 100 / maxVolume)
            volumeControlView?.text = "${volumePercent}%"
            
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
            
            // 计算新位置（使用原始触摸坐标）
            val dampingFactor = 0.85f // 阻尼系数，0.85表示85%的移动量，产生阻尼感
            val rawNewY = (initialTouchY + rawDeltaY - dY).toInt()
            val deltaYFromInitial = rawNewY - initialY
            val dampedDeltaY = (deltaYFromInitial * dampingFactor).toInt()
            var newY = (initialY + dampedDeltaY).coerceIn(0, screenHeight - p.height)
            
            // 只允许垂直拖动，保持X坐标不变（居中）
            p.x = originalX
            p.y = newY
            
            // 更新窗口位置
            wm.updateViewLayout(floatingView, p)
            
            Log.d(TAG, "窗口拖拽: y=${p.y}")
        } catch (e: Exception) {
            Log.e(TAG, "处理窗口拖拽失败", e)
        }
    }
    
    /**
     * 保存截图到文件
     */
    private fun saveScreenshot(bitmap: android.graphics.Bitmap) {
        try {
            val timestamp = System.currentTimeMillis()
            val fileName = "video_screenshot_$timestamp.png"
            
            val screenshotFile = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Android 10+ 使用应用私有目录
                val dir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
                if (dir != null && !dir.exists()) {
                    dir.mkdirs()
                }
                java.io.File(dir, fileName)
            } else {
                // Android 9及以下使用公共Pictures目录
                val file = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
                if (!file.exists()) {
                    file.mkdirs()
                }
                java.io.File(file, fileName)
            }
            
            val fos = java.io.FileOutputStream(screenshotFile)
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, fos)
            fos.flush()
            fos.close()
            
            // 通知媒体库更新（Android 10+需要MediaStore API）
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
                val mediaScanIntent = android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                val uri = android.net.Uri.fromFile(screenshotFile)
                mediaScanIntent.data = uri
                context.sendBroadcast(mediaScanIntent)
            }
            
            Log.d(TAG, "截图已保存到: ${screenshotFile.absolutePath}")
            Toast.makeText(context, "截图已保存: ${screenshotFile.name}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "保存截图失败", e)
            Toast.makeText(context, "保存截图失败: ${e.message}", Toast.LENGTH_SHORT).show()
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
                        newY = newY.coerceIn(0, screenHeight - currentHeight)
                        
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
            screenshotBtn = null
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
     */
    private fun showControls() {
        try {
            topControlBarContainer?.visibility = View.VISIBLE
            controlBar?.visibility = View.VISIBLE
            scheduleHideControls() // 3秒后自动隐藏
            Log.d(TAG, "控制条已显示")
        } catch (e: Exception) {
            Log.e(TAG, "显示控制条失败", e)
        }
    }
    
    /**
     * 隐藏控制条
     */
    private fun hideControls() {
        try {
            topControlBarContainer?.visibility = View.GONE
            controlBar?.visibility = View.GONE
            cancelHideControls() // 取消自动隐藏任务
            Log.d(TAG, "控制条已隐藏")
        } catch (e: Exception) {
            Log.e(TAG, "隐藏控制条失败", e)
        }
    }
    
    /**
     * 安排自动隐藏控制条
     */
    private fun scheduleHideControls() {
        try {
            // 取消之前的任务
            cancelHideControls()
            
            // 创建新的隐藏任务
            hideControlsRunnable = Runnable {
                hideControls()
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
                // 竖屏视频：自动最大化
                if (!hasAutoMaximized && !isFullscreen) {
                    hasAutoMaximized = true
                    params?.width = screenWidth
                    params?.height = screenHeight
                    params?.x = 0
                    params?.y = 0
                    
                    floatingView?.layoutParams?.width = screenWidth
                    floatingView?.layoutParams?.height = screenHeight
                    
                    windowManager?.updateViewLayout(floatingView, params)
                    isFullscreen = true
                    Log.d(TAG, "竖屏视频已自动最大化")
                }
            } else {
                // 横屏视频：居中播放
                if (!hasAutoMaximized && !isFullscreen) {
                    hasAutoMaximized = true
                    // 保持16:9比例，但居中显示
                    val videoWidth = screenWidth
                    val videoHeight = (screenWidth * 9 / 16)
                    
                    params?.width = videoWidth
                    params?.height = videoHeight
                    params?.x = (screenWidth - videoWidth) / 2 // 水平居中
                    params?.y = (screenHeight - videoHeight) / 2 // 垂直居中
                    
                    floatingView?.layoutParams?.width = videoWidth
                    floatingView?.layoutParams?.height = videoHeight
                    
                    // 更新原始尺寸和位置
                    originalWidth = videoWidth
                    originalHeight = videoHeight
                    originalX = params?.x ?: 0
                    originalY = params?.y ?: 0
                    
                    windowManager?.updateViewLayout(floatingView, params)
                    Log.d(TAG, "横屏视频已居中播放")
                }
                
                // 如果用户横屏，自动最大化
                if (isScreenLandscape && !isFullscreen) {
                    params?.width = screenWidth
                    params?.height = screenHeight
                    params?.x = 0
                    params?.y = 0
                    
                    floatingView?.layoutParams?.width = screenWidth
                    floatingView?.layoutParams?.height = screenHeight
                    
                    windowManager?.updateViewLayout(floatingView, params)
                    isFullscreen = true
                    Log.d(TAG, "用户横屏，横屏视频已自动最大化")
                } else if (!isScreenLandscape && isFullscreen && !isVideoPortrait) {
                    // 用户竖屏回来，恢复窗口模式（仅对横屏视频）
                    val videoWidth = screenWidth
                    val videoHeight = (screenWidth * 9 / 16)
                    
                    params?.width = videoWidth
                    params?.height = videoHeight
                    params?.x = (screenWidth - videoWidth) / 2
                    params?.y = (screenHeight - videoHeight) / 2
                    
                    floatingView?.layoutParams?.width = videoWidth
                    floatingView?.layoutParams?.height = videoHeight
                    
                    windowManager?.updateViewLayout(floatingView, params)
                    isFullscreen = false
                    Log.d(TAG, "用户竖屏回来，横屏视频恢复窗口模式")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "自动调整视频窗口大小失败", e)
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


