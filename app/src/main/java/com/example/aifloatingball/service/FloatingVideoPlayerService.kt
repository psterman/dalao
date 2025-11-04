package com.example.aifloatingball.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.NotificationCompat
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsActivity

/**
 * 悬浮视频播放器服务
 * 用于在搜索tab中播放视频时自动弹出悬浮播放器窗口
 */
class FloatingVideoPlayerService : Service() {

    companion object {
        private const val TAG = "FloatingVideoPlayer"
        private const val NOTIFICATION_ID = 1002
        private const val CHANNEL_ID = "floating_video_player_channel"
        
        const val ACTION_START_PLAYER = "com.example.aifloatingball.START_VIDEO_PLAYER"
        const val ACTION_STOP_PLAYER = "com.example.aifloatingball.STOP_VIDEO_PLAYER"
        const val EXTRA_VIDEO_URL = "video_url"
        const val EXTRA_PAGE_URL = "page_url"
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    
    private var videoContainer: FrameLayout? = null
    private var menuButton: ImageButton? = null
    private var closeButton: ImageButton? = null
    
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f
    private var isMoving = false

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_PLAYER -> {
                val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)
                val pageUrl = intent.getStringExtra(EXTRA_PAGE_URL)
                showFloatingPlayer(videoUrl, pageUrl)
            }
            ACTION_STOP_PLAYER -> {
                hideFloatingPlayer()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 显示悬浮播放器
     */
    private fun showFloatingPlayer(videoUrl: String?, pageUrl: String?) {
        if (floatingView != null) {
            Log.d(TAG, "悬浮播放器已存在，不重复创建")
            return
        }

        try {
            // 加载布局
            floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_video_player, null)
            
            videoContainer = floatingView?.findViewById(R.id.video_container)
            menuButton = floatingView?.findViewById(R.id.btn_menu)
            closeButton = floatingView?.findViewById(R.id.btn_close)

            // 设置关闭按钮
            closeButton?.setOnClickListener {
                hideFloatingPlayer()
                stopSelf()
            }

            // 设置菜单按钮
            menuButton?.setOnClickListener { view ->
                showMenuPopup(view, pageUrl)
            }

            // 设置拖拽功能
            setupDragHandler()

            // 创建窗口参数
            params = WindowManager.LayoutParams(
                (screenWidth() * 0.85).toInt(),
                (screenHeight() * 0.6).toInt(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                },
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
                x = 0
                y = 0
            }

            // 添加到窗口
            windowManager?.addView(floatingView, params)

            // 创建WebView用于播放视频
            createVideoWebView(videoUrl, pageUrl)

            // 启动前台服务
            startForeground(NOTIFICATION_ID, createNotification())

            Log.d(TAG, "悬浮播放器已创建")
        } catch (e: Exception) {
            Log.e(TAG, "创建悬浮播放器失败", e)
            Toast.makeText(this, "创建悬浮播放器失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 隐藏悬浮播放器
     */
    private fun hideFloatingPlayer() {
        try {
            floatingView?.let { view ->
                windowManager?.removeView(view)
                floatingView = null
                params = null
                Log.d(TAG, "悬浮播放器已移除")
            }
        } catch (e: Exception) {
            Log.e(TAG, "移除悬浮播放器失败", e)
        }
    }

    /**
     * 创建视频WebView
     */
    private fun createVideoWebView(videoUrl: String?, pageUrl: String?) {
        val webView = WebView(this)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            mediaPlaybackRequiresUserGesture = false
            allowFileAccess = true
            allowContentAccess = true
        }

        // 如果提供了视频URL，直接加载视频
        // 否则加载原页面URL
        val urlToLoad = videoUrl ?: pageUrl ?: "about:blank"
        
        if (videoUrl != null && videoUrl.startsWith("http")) {
            // 直接加载视频URL
            webView.loadUrl(videoUrl)
        } else if (pageUrl != null) {
            // 加载原页面，并通过JS注入来提取并播放视频
            webView.loadUrl(pageUrl)
            webView.evaluateJavascript("""
                (function() {
                    // 查找页面中的视频元素
                    var videos = document.querySelectorAll('video');
                    if (videos.length > 0) {
                        var video = videos[0];
                        video.play();
                        video.setAttribute('controls', 'controls');
                    }
                })();
            """.trimIndent(), null)
        }

        videoContainer?.removeAllViews()
        videoContainer?.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
    }

    /**
     * 设置拖拽处理
     * 优化触摸区域，顶部控制栏和窗口边缘都可以拖拽
     */
    private fun setupDragHandler() {
        floatingView?.setOnTouchListener { view, event ->
            // 检查触摸点位置
            val topControlBar = view.findViewById<View>(R.id.top_control_bar)
            val touchY = event.y
            val touchX = event.x
            val topControlBarHeight = topControlBar?.height ?: 48
            val viewWidth = view.width
            val viewHeight = view.height
            val edgeThreshold = 30 // 边缘拖拽区域宽度
            
            // 允许拖拽的区域：
            // 1. 顶部控制栏区域
            // 2. 窗口边缘区域（左右上下边缘）
            val isTopBar = touchY <= topControlBarHeight
            val isLeftEdge = touchX <= edgeThreshold
            val isRightEdge = touchX >= (viewWidth - edgeThreshold)
            val isBottomEdge = touchY >= (viewHeight - edgeThreshold)
            
            if (isTopBar || isLeftEdge || isRightEdge || isBottomEdge) {
                handleDrag(view, event)
            } else {
                // 其他区域不拦截，让WebView处理视频播放
                false
            }
        }
    }
    
    /**
     * 处理拖拽事件
     */
    private fun handleDrag(view: View, event: MotionEvent): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = params?.x ?: 0
                initialY = params?.y ?: 0
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                isMoving = false
                view.alpha = 0.9f // 拖拽时稍微透明，提供视觉反馈
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - initialTouchX
                val deltaY = event.rawY - initialTouchY
                
                // 降低移动阈值，让拖拽更灵敏
                if (Math.abs(deltaX) > 5 || Math.abs(deltaY) > 5) {
                    isMoving = true
                }
                
                if (isMoving) {
                    params?.let {
                        val newX = initialX + deltaX.toInt()
                        val newY = initialY + deltaY.toInt()
                        
                        // 限制在屏幕范围内
                        val screenWidth = screenWidth()
                        val screenHeight = screenHeight()
                        val windowWidth = it.width
                        val windowHeight = it.height
                        
                        it.x = newX.coerceIn(0, screenWidth - windowWidth)
                        it.y = newY.coerceIn(0, screenHeight - windowHeight)
                        
                        windowManager?.updateViewLayout(view, it)
                    }
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                view.alpha = 1.0f // 恢复不透明
                if (!isMoving) {
                    // 如果只是点击，不移动窗口
                    view.performClick()
                }
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                view.alpha = 1.0f
                true
            }
            else -> false
        }
    }

    /**
     * 显示菜单弹窗
     */
    private fun showMenuPopup(anchorView: View, pageUrl: String?) {
        val popupMenu = PopupMenu(this, anchorView)
        popupMenu.menu.apply {
            add("全屏播放").setOnMenuItemClickListener {
                // TODO: 实现全屏播放
                Toast.makeText(this@FloatingVideoPlayerService, "全屏播放功能开发中", Toast.LENGTH_SHORT).show()
                true
            }
            add("分享视频").setOnMenuItemClickListener {
                shareVideo(pageUrl)
                true
            }
            add("在新窗口打开").setOnMenuItemClickListener {
                openInNewWindow(pageUrl)
                true
            }
            add("关闭播放器").setOnMenuItemClickListener {
                hideFloatingPlayer()
                stopSelf()
                true
            }
        }
        popupMenu.show()
    }

    /**
     * 分享视频
     */
    private fun shareVideo(url: String?) {
        if (url == null) {
            Toast.makeText(this, "无法分享：视频URL为空", Toast.LENGTH_SHORT).show()
            return
        }
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
            putExtra(Intent.EXTRA_SUBJECT, "分享视频")
        }
        
        try {
            startActivity(Intent.createChooser(shareIntent, "分享视频"))
        } catch (e: Exception) {
            Log.e(TAG, "分享失败", e)
            Toast.makeText(this, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 在新窗口打开
     */
    private fun openInNewWindow(url: String?) {
        if (url == null) {
            Toast.makeText(this, "无法打开：URL为空", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "打开新窗口失败", e)
            Toast.makeText(this, "打开失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮视频播放器",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "悬浮视频播放器服务通知"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 创建通知
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, SettingsActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("悬浮视频播放器")
            .setContentText("正在播放视频")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    /**
     * 获取屏幕宽度
     */
    private fun screenWidth(): Int {
        val displayMetrics = resources.displayMetrics
        return displayMetrics.widthPixels
    }

    /**
     * 获取屏幕高度
     */
    private fun screenHeight(): Int {
        val displayMetrics = resources.displayMetrics
        return displayMetrics.heightPixels
    }

    override fun onDestroy() {
        super.onDestroy()
        hideFloatingPlayer()
    }
}
