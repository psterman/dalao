package com.example.aifloatingball.video

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.annotation.RequiresApi
import androidx.media3.common.util.UnstableApi
import com.example.aifloatingball.R

/**
 * 画中画 (PiP) 模式 Activity
 * 
 * 使用 Android 8.0+ 原生 PiP API，保留迷你模式的所有功能：
 * - 播放/暂停控制
 * - 进度条拖拽
 * - 时间显示
 * - 关闭按钮
 * - 恢复按钮（退出 PiP）
 * - 拉伸按钮（全屏）
 * 
 * @author AI Floating Ball
 */
@UnstableApi
class PictureInPictureActivity : Activity() {

    companion object {
        private const val TAG = "PictureInPictureActivity"
        
        // Intent 参数
        const val EXTRA_VIDEO_URL = "video_url"
        const val EXTRA_VIDEO_TITLE = "video_title"
        const val EXTRA_VIDEO_POSITION = "video_position"
        
        // PiP 控制按钮 ID（用于 RemoteAction）
        private const val ACTION_PLAY_PAUSE = 1
        private const val ACTION_CLOSE = 2
        private const val ACTION_RESTORE = 3
        private const val ACTION_EXPAND = 4
    }

    private var videoView: VideoView? = null // 保留用于兼容性，实际使用 exoPlayerManager
    private var exoPlayerManager: ExoPlayerManager? = null // ExoPlayer 管理器
    private var container: FrameLayout? = null
    
    // 控制按钮
    private var playPauseBtn: ImageButton? = null
    private var closeBtn: ImageButton? = null
    private var restoreBtn: ImageButton? = null
    private var expandBtn: ImageButton? = null
    
    // 进度条和时间显示
    private var progressBar: SeekBar? = null
    private var currentTimeText: TextView? = null
    private var totalTimeText: TextView? = null
    
    // 控制条容器
    private var controlsContainer: ViewGroup? = null
    private var isControlsVisible = false
    
    // 视频信息
    private var videoUrl: String? = null
    private var videoTitle: String? = null
    private var savedPosition: Int = 0
    
    // 播放状态
    private var isPlaying = false
    private var isInPictureInPictureMode = false
    
    // 更新进度条的 Handler
    private var updateHandler: android.os.Handler? = null
    private var updateRunnable: Runnable? = null
    
    // 自动隐藏控制条的 Handler
    private var hideControlsHandler: android.os.Handler? = null
    private var hideControlsRunnable: Runnable? = null
    private val CONTROLS_AUTO_HIDE_DELAY = 3000L // 3秒后自动隐藏

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "onCreate: 创建 PiP Activity")
        
        // 获取视频信息
        videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
        videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: "视频"
        savedPosition = intent.getIntExtra(EXTRA_VIDEO_POSITION, 0)
        
        if (videoUrl.isNullOrBlank()) {
            Log.e(TAG, "视频URL为空，无法播放")
            Toast.makeText(this, "视频URL为空", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        // 创建 UI
        createUI()
        
        // 设置视频源
        setupVideo()
        
        // 启动更新进度条的任务
        startProgressUpdate()
    }

    /**
     * 创建 UI
     */
    private fun createUI() {
        // 创建根容器
        container = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0xFF000000.toInt())
        }
        
        // 创建 ExoPlayer 管理器
        exoPlayerManager = ExoPlayerManager(this)
        val playerView = exoPlayerManager!!.initialize(container!!)
        // videoView 不再使用，保留为 null
        
        // 创建控制条容器
        controlsContainer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setOnClickListener {
                toggleControls()
            }
        }
        container?.addView(controlsContainer)
        
        // 创建顶部控制条（时间显示和进度条）
        val topControlBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                (this as FrameLayout.LayoutParams).gravity = android.view.Gravity.TOP
                setMargins(dpToPx(8), dpToPx(8), dpToPx(8), 0)
            }
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            setBackgroundColor(0xCC000000.toInt())
        }
        
        // 当前时间
        currentTimeText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            text = "00:00"
        }
        topControlBar.addView(currentTimeText)
        
        // 进度条
        progressBar = SeekBar(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                setMargins(dpToPx(8), 0, dpToPx(8), 0)
            }
            max = 1000
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val vv = videoView ?: return
                        val duration = vv.duration
                        if (duration > 0) {
                            val position = (progress * duration / 1000).toInt()
                            vv.seekTo(position)
                            updateTimeDisplay()
                        }
                    }
                }
                
                override fun onStartTrackingTouch(seekBar: SeekBar?) {
                    pauseProgressUpdate()
                }
                
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    resumeProgressUpdate()
                }
            })
        }
        topControlBar.addView(progressBar)
        
        // 总时长
        totalTimeText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 12f
            text = "00:00"
        }
        topControlBar.addView(totalTimeText)
        
        controlsContainer?.addView(topControlBar)
        
        // 创建控制按钮
        createControlButtons()
        
        setContentView(container)
    }

    /**
     * 创建控制按钮（播放/暂停、关闭、恢复、拉伸）
     */
    private fun createControlButtons() {
        // 播放/暂停按钮（左上角）
        playPauseBtn = ImageButton(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(40),
                dpToPx(40),
                android.view.Gravity.TOP or android.view.Gravity.START
            ).apply {
                (this as FrameLayout.LayoutParams).setMargins(dpToPx(8), dpToPx(8), 0, 0)
            }
            setImageResource(android.R.drawable.ic_media_play)
            setBackgroundColor(0xCC000000.toInt())
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            setOnClickListener {
                togglePlayPause()
            }
        }
        controlsContainer?.addView(playPauseBtn)
        
        // 关闭按钮（右上角）
        closeBtn = ImageButton(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(40),
                dpToPx(40),
                android.view.Gravity.TOP or android.view.Gravity.END
            ).apply {
                (this as FrameLayout.LayoutParams).setMargins(0, dpToPx(8), dpToPx(8), 0)
            }
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setBackgroundColor(0xCC000000.toInt())
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            setOnClickListener {
                finish()
            }
        }
        controlsContainer?.addView(closeBtn)
        
        // 恢复按钮（退出 PiP，左下角）
        restoreBtn = ImageButton(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(40),
                dpToPx(40),
                android.view.Gravity.BOTTOM or android.view.Gravity.START
            ).apply {
                (this as FrameLayout.LayoutParams).setMargins(dpToPx(8), 0, 0, dpToPx(8))
            }
            setImageResource(android.R.drawable.ic_menu_revert)
            setBackgroundColor(0xCC000000.toInt())
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            setOnClickListener {
                exitPictureInPictureMode()
            }
        }
        controlsContainer?.addView(restoreBtn)
        
        // 拉伸按钮（全屏，右下角）
        expandBtn = ImageButton(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                dpToPx(40),
                dpToPx(40),
                android.view.Gravity.BOTTOM or android.view.Gravity.END
            ).apply {
                (this as FrameLayout.LayoutParams).setMargins(0, 0, dpToPx(8), dpToPx(8))
            }
            setImageResource(android.R.drawable.ic_menu_crop)
            setBackgroundColor(0xCC000000.toInt())
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
            setOnClickListener {
                // 退出 PiP 模式，然后可以全屏显示
                exitPictureInPictureMode()
            }
        }
        controlsContainer?.addView(expandBtn)
        
        // 初始隐藏控制条
        hideControls()
    }

    /**
     * 设置视频源
     */
    private fun setupVideo() {
        val exoManager = exoPlayerManager ?: return
        val url = videoUrl ?: return
        
        try {
            exoManager.setVideoPath(url)
            
            // 设置播放监听
            exoManager.setOnPreparedListener {
                Log.d(TAG, "视频准备完成")
                if (savedPosition > 0) {
                    exoManager.seekTo(savedPosition)
                }
                exoManager.start()
                isPlaying = true
                updatePlayPauseButton()
                updateTimeDisplay()
            }
            
            exoManager.setOnCompletionListener {
                Log.d(TAG, "视频播放完成")
                isPlaying = false
                updatePlayPauseButton()
                // 播放完成后可以自动退出 PiP
                if (isInPictureInPictureMode) {
                    finish()
                }
            }
            
            exoManager.setOnErrorListener { errorCode, extra ->
                Log.e(TAG, "视频播放错误: errorCode=$errorCode, extra=$extra")
                Toast.makeText(this, "视频播放错误", Toast.LENGTH_SHORT).show()
                true
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "设置视频源失败", e)
            Toast.makeText(this, "设置视频源失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 切换播放/暂停
     */
    private fun togglePlayPause() {
        val exoManager = exoPlayerManager ?: return
        try {
            if (exoManager.isPlaying()) {
                exoManager.pause()
                isPlaying = false
            } else {
                exoManager.start()
                isPlaying = true
            }
            updatePlayPauseButton()
        } catch (e: Exception) {
            Log.e(TAG, "切换播放/暂停失败", e)
        }
    }

    /**
     * 更新播放/暂停按钮图标
     */
    private fun updatePlayPauseButton() {
        playPauseBtn?.setImageResource(
            if (isPlaying) android.R.drawable.ic_media_pause
            else android.R.drawable.ic_media_play
        )
    }

    /**
     * 更新进度条和时间显示
     */
    private fun updateTimeDisplay() {
        val exoManager = exoPlayerManager ?: return
        val duration = exoManager.getDuration()
        val currentPosition = exoManager.getCurrentPosition()
        
        if (duration > 0) {
            // 更新进度条
            val progress = (currentPosition * 1000 / duration).toInt()
            progressBar?.progress = progress
            
            // 更新时间显示
            currentTimeText?.text = formatTime(currentPosition)
            totalTimeText?.text = formatTime(duration)
        }
    }

    /**
     * 格式化时间（毫秒 -> MM:SS）
     */
    private fun formatTime(milliseconds: Int): String {
        val totalSeconds = milliseconds / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    /**
     * 启动更新进度条的任务
     */
    private fun startProgressUpdate() {
        updateHandler = android.os.Handler(android.os.Looper.getMainLooper())
        updateRunnable = object : Runnable {
            override fun run() {
                if (!isInPictureInPictureMode) {
                    updateTimeDisplay()
                }
                updateHandler?.postDelayed(this, 1000) // 每秒更新一次
            }
        }
        updateHandler?.post(updateRunnable!!)
    }

    /**
     * 暂停更新进度条（拖拽时）
     */
    private fun pauseProgressUpdate() {
        updateHandler?.removeCallbacks(updateRunnable!!)
    }

    /**
     * 恢复更新进度条
     */
    private fun resumeProgressUpdate() {
        updateHandler?.removeCallbacks(updateRunnable!!)
        updateHandler?.post(updateRunnable!!)
    }

    /**
     * 显示控制条
     */
    private fun showControls() {
        if (isControlsVisible) return
        isControlsVisible = true
        
        controlsContainer?.visibility = View.VISIBLE
        controlsContainer?.alpha = 0f
        controlsContainer?.animate()
            ?.alpha(1f)
            ?.setDuration(200)
            ?.start()
        
        // 设置自动隐藏
        scheduleHideControls()
    }

    /**
     * 隐藏控制条
     */
    private fun hideControls() {
        if (!isControlsVisible) return
        isControlsVisible = false
        
        controlsContainer?.animate()
            ?.alpha(0f)
            ?.setDuration(200)
            ?.withEndAction {
                if (!isControlsVisible) {
                    controlsContainer?.visibility = View.GONE
                }
            }
            ?.start()
    }

    /**
     * 切换控制条显示/隐藏
     */
    private fun toggleControls() {
        if (isControlsVisible) {
            hideControls()
        } else {
            showControls()
        }
    }

    /**
     * 安排自动隐藏控制条
     */
    private fun scheduleHideControls() {
        hideControlsHandler?.removeCallbacks(hideControlsRunnable!!)
        hideControlsRunnable = Runnable {
            hideControls()
        }
        hideControlsHandler = android.os.Handler(android.os.Looper.getMainLooper())
        hideControlsHandler?.postDelayed(hideControlsRunnable!!, CONTROLS_AUTO_HIDE_DELAY)
    }

    /**
     * 进入画中画模式（公共方法，供外部调用）
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun startPictureInPicture() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Log.w(TAG, "当前 Android 版本不支持 PiP")
            return
        }
        
        try {
            val exoManager = exoPlayerManager ?: return
            val videoWidth = exoManager.getVideoWidth()
            val videoHeight = exoManager.getVideoHeight()
            
            // 计算宽高比（默认 16:9）
            val aspectRatio = if (videoWidth > 0 && videoHeight > 0) {
                Rational(videoWidth, videoHeight)
            } else {
                Rational(16, 9)
            }
            
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()
            
            // 调用系统的 enterPictureInPictureMode 方法
            val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                enterPictureInPictureMode(params)
            } else {
                false
            }
            
            if (result) {
                isInPictureInPictureMode = true
                Log.d(TAG, "已进入画中画模式")
                hideControls() // PiP 模式下隐藏控制条
            } else {
                Log.w(TAG, "进入画中画模式失败")
            }
        } catch (e: Exception) {
            Log.e(TAG, "进入画中画模式异常", e)
        }
    }

    /**
     * 退出画中画模式
     */
    private fun exitPictureInPictureMode() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                moveTaskToBack(false)
            }
            isInPictureInPictureMode = false
            showControls() // 退出 PiP 后显示控制条
            Log.d(TAG, "已退出画中画模式")
        } catch (e: Exception) {
            Log.e(TAG, "退出画中画模式失败", e)
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // 用户按 Home 键时，如果支持 PiP 且视频正在播放，自动进入 PiP 模式
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && exoPlayerManager != null && isPlaying) {
            // 调用自定义的 startPictureInPicture 方法
            startPictureInPicture()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        this.isInPictureInPictureMode = isInPictureInPictureMode
        
        if (isInPictureInPictureMode) {
            // 进入 PiP 模式
            Log.d(TAG, "进入 PiP 模式")
            hideControls()
        } else {
            // 退出 PiP 模式
            Log.d(TAG, "退出 PiP 模式")
            showControls()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // PiP 模式下配置变化时，更新 PiP 参数
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && isInPictureInPictureMode) {
            val exoManager = exoPlayerManager ?: return
            val videoWidth = exoManager.getVideoWidth()
            val videoHeight = exoManager.getVideoHeight()
            
            val aspectRatio = if (videoWidth > 0 && videoHeight > 0) {
                Rational(videoWidth, videoHeight)
            } else {
                Rational(16, 9)
            }
            
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(aspectRatio)
                .build()
            
            setPictureInPictureParams(params)
        }
    }

    override fun onPause() {
        super.onPause()
        // 暂停时保存播放位置
        val exoManager = exoPlayerManager ?: return
        if (exoManager.isPlaying()) {
            savedPosition = exoManager.getCurrentPosition()
        }
    }

    override fun onResume() {
        super.onResume()
        // 恢复时恢复播放位置
        val exoManager = exoPlayerManager ?: return
        if (savedPosition > 0 && !isInPictureInPictureMode) {
            exoManager.seekTo(savedPosition)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: 销毁 PiP Activity")
        
        // 清理资源
        updateHandler?.removeCallbacks(updateRunnable!!)
        hideControlsHandler?.removeCallbacks(hideControlsRunnable!!)
        
        exoPlayerManager?.stopPlayback()
        exoPlayerManager?.release()
        exoPlayerManager = null
        videoView = null
    }

    /**
     * dp 转 px
     */
    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }
}

