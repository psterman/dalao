package com.example.aifloatingball.video

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
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
    private var playPauseBtn: ImageButton? = null
    private var muteBtn: ImageButton? = null
    private var fullscreenBtn: ImageButton? = null
    private var speedBtn: Button? = null
    private var loopBtn: ImageButton? = null
    private var screenshotBtn: ImageButton? = null
    private var progressBar: android.widget.SeekBar? = null
    private var timeText: android.widget.TextView? = null
    private var updateHandler: android.os.Handler? = null
    private var updateRunnable: Runnable? = null
    private var isMuted = false
    private var isLooping = false
    private var playbackSpeed = 1.0f
    private val speedOptions = listOf(0.5f, 1.0f, 1.5f, 2.0f)
    private var screenWidth = 0
    private var screenHeight = 0

    companion object {
        private const val TAG = "SystemOverlayVideo"
        private const val REQUEST_CODE_OVERLAY_PERMISSION = 1001
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
            
            videoView?.setVideoURI(Uri.parse(url))
            videoView?.setOnPreparedListener { mediaPlayer ->
                try {
                    mediaPlayer.isLooping = isLooping
                    
                    // 更新控制条状态
                    val duration = mediaPlayer.duration
                    Log.d(TAG, "视频准备完成，时长: ${duration}ms")
                    
                    if (duration > 0) {
                        progressBar?.max = 100
                        timeText?.text = formatTime(0) + " / " + formatTime(duration)
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
                                                    timeText?.text = formatTime(current) + " / " + formatTime(total)
                                                }
                                                updateHandler?.postDelayed(this, 500)
                                            } catch (e: Exception) {
                                                Log.e(TAG, "更新播放进度失败", e)
                                            }
                                        }
                                    }
                                    updateHandler?.post(updateRunnable!!)
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
            videoView?.setOnPreparedListener(null)
            videoView?.setOnCompletionListener(null)
            floatingView?.visibility = View.GONE
            isShowing = false
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
        
        // 创建关闭按钮（叠加在视频上方）
        val closeBtn = ImageButton(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(36),
                dpToPx(36),
                Gravity.TOP or Gravity.END
            ).apply {
                setMargins(0, dpToPx(4), dpToPx(4), 0)
            }
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(0xCC000000.toInt()) // 更明显的背景色
            setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
            setOnClickListener { hide() }
            // 确保按钮在视频上方
            elevation = dpToPx(8).toFloat()
        }
        
        container.addView(closeBtn)
        
        floatingView = container
        videoView = vv
        this.closeBtn = closeBtn
        
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
        
        // 启用拖拽
        enableDrag(container)
        
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
     * 创建自定义视频控制条
     */
    private fun createCustomControls(container: FrameLayout, videoView: VideoView) {
        // 创建底部控制条容器（增加高度以容纳更多按钮）
        val controlBarContainer = LinearLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dpToPx(80),
                Gravity.BOTTOM
            ).apply {
                setMargins(0, 0, 0, 0)
            }
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xE6000000.toInt())
            visibility = View.VISIBLE
        }
        
        // 创建第一行：进度条和时间
        val progressRow = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(40)
            )
        }
        
        // 创建第二行：控制按钮
        val buttonRow = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(40)
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        
        // 创建播放/暂停按钮
        playPauseBtn = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(40),
                dpToPx(40)
            ).apply {
                setMargins(dpToPx(8), 0, dpToPx(4), 0)
            }
            setImageResource(android.R.drawable.ic_media_play)
            setBackgroundColor(0x80000000.toInt())
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
                                            timeText?.text = formatTime(current) + " / " + formatTime(total)
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
        
        // 创建可拖拽的进度条（SeekBar）
        // 创建更大的thumb drawable，方便点击
        val thumbDrawable = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(0xFFFFFFFF.toInt())
            setSize(dpToPx(28), dpToPx(28)) // 更大的thumb，28dp
        }
        
        progressBar = android.widget.SeekBar(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dpToPx(6), // 增加进度条高度，方便点击
                Gravity.CENTER_VERTICAL
            ).apply {
                setMargins(dpToPx(8), 0, dpToPx(8), 0)
            }
            max = 100
            progress = 0
            thumb = thumbDrawable
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
                }
                override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {
                    // 恢复进度更新
                    updateRunnable?.let { updateHandler?.post(it) }
                }
            })
        }
        
        // 创建时间显示
        timeText = android.widget.TextView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.START or Gravity.CENTER_VERTICAL
            ).apply {
                setMargins(dpToPx(8), 0, 0, 0)
            }
            text = "00:00 / 00:00"
            textSize = 11f
            setTextColor(0xFFFFFFFF.toInt())
        }
        
        progressRow.addView(timeText)
        progressRow.addView(progressBar)
        
        // 创建播放速度按钮（使用Button显示文本）
        speedBtn = Button(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(40),
                dpToPx(36)
            ).apply {
                setMargins(dpToPx(4), 0, dpToPx(4), 0)
            }
            text = "1.0x"
            textSize = 10f
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0x80000000.toInt())
            setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
            setOnClickListener {
                // 循环切换播放速度
                val currentIndex = speedOptions.indexOf(playbackSpeed)
                val nextIndex = (currentIndex + 1) % speedOptions.size
                playbackSpeed = speedOptions[nextIndex]
                text = "${playbackSpeed}x"
                
                try {
                    // 通过反射设置播放速度
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
        
        // 创建截图按钮
        screenshotBtn = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(36),
                dpToPx(36)
            ).apply {
                setMargins(dpToPx(4), 0, dpToPx(4), 0)
            }
            setImageResource(android.R.drawable.ic_menu_camera)
            setBackgroundColor(0x80000000.toInt())
            setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
            setOnClickListener {
                captureScreenshot()
            }
        }
        
        // 创建静音按钮
        muteBtn = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(36),
                dpToPx(36)
            ).apply {
                setMargins(dpToPx(4), 0, dpToPx(4), 0)
            }
            setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            setBackgroundColor(0x80000000.toInt())
            setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
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
        
        // 创建全屏按钮
        fullscreenBtn = ImageButton(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                dpToPx(36),
                dpToPx(36)
            ).apply {
                setMargins(dpToPx(4), 0, dpToPx(8), 0)
            }
            setImageResource(android.R.drawable.ic_menu_crop)
            setBackgroundColor(0x80000000.toInt())
            setPadding(dpToPx(6), dpToPx(6), dpToPx(6), dpToPx(6))
            setOnClickListener {
                Toast.makeText(context, "已经是全局悬浮播放", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 添加按钮到第二行
        buttonRow.addView(playPauseBtn)
        buttonRow.addView(speedBtn)
        buttonRow.addView(loopBtn)
        buttonRow.addView(screenshotBtn)
        
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
        
        // 添加行到容器
        controlBarContainer.addView(progressRow)
        controlBarContainer.addView(buttonRow)
        container.addView(controlBarContainer)
        
        controlBar = controlBarContainer
        
        // 设置控制条的初始状态
        playPauseBtn?.setImageResource(android.R.drawable.ic_media_play)
        
        // 注意：播放完成的监听器在show()方法中设置，这里不设置，避免被覆盖
        
        // 点击视频区域显示/隐藏控制条
        videoView.setOnClickListener {
            toggleControls()
        }
    }
    
    /**
     * 切换控制条显示/隐藏
     */
    private fun toggleControls() {
        controlBar?.let { bar ->
            if (bar.visibility == View.VISIBLE) {
                bar.visibility = View.GONE
            } else {
                bar.visibility = View.VISIBLE
                // 3秒后自动隐藏
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (bar.visibility == View.VISIBLE) {
                        bar.visibility = View.GONE
                    }
                }, 3000)
            }
        }
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
     * 启用拖拽功能
     * 注意：关闭按钮点击时不应触发拖拽
     */
    private fun enableDrag(view: View) {
        view.setOnTouchListener { v, event ->
            // 如果点击的是关闭按钮，不处理拖拽
            if (event.action == MotionEvent.ACTION_DOWN) {
                val x = event.x.toInt()
                val y = event.y.toInt()
                closeBtn?.let { btn ->
                    val btnX = btn.left
                    val btnY = btn.top
                    val btnRight = btn.right
                    val btnBottom = btn.bottom
                    if (x >= btnX && x <= btnRight && y >= btnY && y <= btnBottom) {
                        // 点击在关闭按钮上，不处理拖拽，让按钮处理点击
                        return@setOnTouchListener false
                    }
                }
            }
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    dX = initialTouchX - initialX
                    dY = initialTouchY - initialY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params?.let {
                        // 计算新位置
                        val newX = (event.rawX - dX).toInt()
                        var newY = (event.rawY - dY).toInt()
                        
                        // 只允许垂直拖动，保持X坐标不变（居中）
                        val currentX = initialX // 保持初始X位置（居中）
                        
                        // 限制Y坐标在屏幕范围内（不能移出屏幕）
                        val currentHeight = it.height
                        newY = newY.coerceIn(0, screenHeight - currentHeight)
                        
                        it.x = currentX
                        it.y = newY
                        windowManager?.updateViewLayout(floatingView, it)
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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
            timeText = null
            updateRunnable?.let { updateHandler?.removeCallbacks(it) }
            updateHandler = null
            updateRunnable = null
            params = null
            Log.d(TAG, "系统级悬浮窗播放器已销毁")
        } catch (e: Exception) {
            Log.e(TAG, "销毁悬浮窗播放器失败", e)
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


