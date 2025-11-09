package com.example.aifloatingball.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.TrafficStats
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.SimpleModeActivity
import com.example.aifloatingball.download.EnhancedDownloadManager
import java.text.DecimalFormat

/**
 * 网络监控悬浮窗服务
 * 用于显示网速和下载进度
 */
class NetworkMonitorFloatingService : Service() {
    
    companion object {
        private const val TAG = "NetworkMonitorFloating"
        private const val NOTIFICATION_ID = 1003
        private const val NOTIFICATION_CHANNEL_ID = "network_monitor_channel"
        private const val UPDATE_INTERVAL = 1000L // 1秒更新一次
    }
    
    private val windowManager by lazy { getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    private val settingsManager by lazy { SettingsManager.getInstance(this) }
    private val downloadManager by lazy { EnhancedDownloadManager(this) }
    
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    
    private var networkSpeedTextView: TextView? = null
    private var downloadProgressTextView: TextView? = null
    private var contentContainer: FrameLayout? = null
    
    private var isNetworkSpeedEnabled = false
    private var isDownloadProgressEnabled = false
    
    private var lastRxBytes = 0L
    private var lastTxBytes = 0L
    private var lastTime = 0L
    
    private val updateHandler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateNetworkSpeed()
            updateDownloadProgress()
            updateHandler.postDelayed(this, UPDATE_INTERVAL)
        }
    }
    
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "NetworkMonitorFloatingService onCreate")
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
        
        // 检查设置
        isNetworkSpeedEnabled = settingsManager.getBoolean("network_speed_display_enabled", false)
        isDownloadProgressEnabled = settingsManager.getBoolean("download_progress_display_enabled", false)
        
        if (isNetworkSpeedEnabled || isDownloadProgressEnabled) {
            createFloatingView()
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "NetworkMonitorFloatingService onStartCommand")
        
        // 检查设置状态
        isNetworkSpeedEnabled = settingsManager.getBoolean("network_speed_display_enabled", false)
        isDownloadProgressEnabled = settingsManager.getBoolean("download_progress_display_enabled", false)
        
        if (isNetworkSpeedEnabled || isDownloadProgressEnabled) {
            if (floatingView == null) {
                createFloatingView()
            } else {
                updateViewVisibility()
            }
        } else {
            removeFloatingView()
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "NetworkMonitorFloatingService onDestroy")
        removeFloatingView()
        updateHandler.removeCallbacks(updateRunnable)
    }
    
    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "网络监控悬浮窗",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "显示网速和下载进度的悬浮窗服务"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    /**
     * 创建通知
     */
    private fun createNotification(): Notification {
        val intent = Intent(this, SimpleModeActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("网络监控")
            .setContentText("正在显示网速和下载进度")
            .setSmallIcon(R.drawable.ic_settings)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
    
    /**
     * 创建悬浮窗
     */
    private fun createFloatingView() {
        try {
            val inflater = LayoutInflater.from(this)
            floatingView = inflater.inflate(R.layout.floating_network_monitor, null)
            
            networkSpeedTextView = floatingView?.findViewById(R.id.network_speed_text)
            downloadProgressTextView = floatingView?.findViewById(R.id.download_progress_text)
            contentContainer = floatingView?.findViewById(R.id.content_container)
            
            // 设置点击切换显示内容
            floatingView?.setOnClickListener {
                toggleDisplayMode()
            }
            
            // 设置拖拽
            setupDragListener()
            
            // 更新视图可见性
            updateViewVisibility()
            
            // 设置窗口参数
            params = WindowManager.LayoutParams().apply {
                type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_PHONE
                }
                format = PixelFormat.TRANSLUCENT
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                gravity = Gravity.TOP or Gravity.START
                x = 100
                y = 100
            }
            
            windowManager.addView(floatingView, params)
            
            // 初始化网络统计
            lastRxBytes = TrafficStats.getTotalRxBytes()
            lastTxBytes = TrafficStats.getTotalTxBytes()
            lastTime = System.currentTimeMillis()
            
            // 开始更新
            updateHandler.post(updateRunnable)
            
            Log.d(TAG, "悬浮窗创建成功")
        } catch (e: Exception) {
            Log.e(TAG, "创建悬浮窗失败", e)
        }
    }
    
    /**
     * 移除悬浮窗
     */
    private fun removeFloatingView() {
        try {
            floatingView?.let {
                windowManager.removeView(it)
                floatingView = null
            }
            updateHandler.removeCallbacks(updateRunnable)
            Log.d(TAG, "悬浮窗已移除")
        } catch (e: Exception) {
            Log.e(TAG, "移除悬浮窗失败", e)
        }
    }
    
    /**
     * 更新视图可见性
     */
    private fun updateViewVisibility() {
        networkSpeedTextView?.visibility = if (isNetworkSpeedEnabled) View.VISIBLE else View.GONE
        downloadProgressTextView?.visibility = if (isDownloadProgressEnabled) View.VISIBLE else View.GONE
        
        // 如果两个都关闭，隐藏整个悬浮窗
        if (!isNetworkSpeedEnabled && !isDownloadProgressEnabled) {
            removeFloatingView()
        }
    }
    
    /**
     * 切换显示模式（网速/下载进度）
     */
    private fun toggleDisplayMode() {
        if (isNetworkSpeedEnabled && isDownloadProgressEnabled) {
            // 如果两个都启用，切换显示
            val showSpeed = networkSpeedTextView?.visibility == View.VISIBLE
            networkSpeedTextView?.visibility = if (showSpeed) View.GONE else View.VISIBLE
            downloadProgressTextView?.visibility = if (showSpeed) View.VISIBLE else View.GONE
        }
    }
    
    /**
     * 更新网速
     */
    private fun updateNetworkSpeed() {
        if (!isNetworkSpeedEnabled || networkSpeedTextView == null) return
        
        try {
            val currentRxBytes = TrafficStats.getTotalRxBytes()
            val currentTxBytes = TrafficStats.getTotalTxBytes()
            val currentTime = System.currentTimeMillis()
            
            if (lastTime > 0) {
                val timeDiff = (currentTime - lastTime) / 1000.0 // 秒
                if (timeDiff > 0) {
                    val rxSpeed = ((currentRxBytes - lastRxBytes) / timeDiff).toLong()
                    val txSpeed = ((currentTxBytes - lastTxBytes) / timeDiff).toLong()
                    val totalSpeed = rxSpeed + txSpeed
                    
                    val speedText = "↓${formatSpeed(rxSpeed)} ↑${formatSpeed(txSpeed)}"
                    networkSpeedTextView?.text = speedText
                }
            }
            
            lastRxBytes = currentRxBytes
            lastTxBytes = currentTxBytes
            lastTime = currentTime
        } catch (e: Exception) {
            Log.e(TAG, "更新网速失败", e)
        }
    }
    
    /**
     * 更新下载进度
     */
    private fun updateDownloadProgress() {
        if (!isDownloadProgressEnabled || downloadProgressTextView == null) return
        
        try {
            val downloads = downloadManager.getAllDownloads()
            val activeDownloads = downloads.filter { 
                it.status == android.app.DownloadManager.STATUS_RUNNING 
            }
            
            if (activeDownloads.isNotEmpty()) {
                val download = activeDownloads.first()
                val progress = if (download.bytesTotal > 0) {
                    (download.bytesDownloaded * 100 / download.bytesTotal).toInt()
                } else 0
                
                val fileName = download.localFilename ?: download.title ?: "未知文件"
                val progressText = "下载: $fileName\n$progress%"
                downloadProgressTextView?.text = progressText
            } else {
                downloadProgressTextView?.text = "无下载任务"
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新下载进度失败", e)
        }
    }
    
    /**
     * 格式化网速
     */
    private fun formatSpeed(bytesPerSecond: Long): String {
        val df = DecimalFormat("#.##")
        return when {
            bytesPerSecond >= 1024 * 1024 -> "${df.format(bytesPerSecond / (1024.0 * 1024.0))}MB/s"
            bytesPerSecond >= 1024 -> "${df.format(bytesPerSecond / 1024.0)}KB/s"
            else -> "${bytesPerSecond}B/s"
        }
    }
    
    /**
     * 设置拖拽监听
     */
    private fun setupDragListener() {
        floatingView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params?.let {
                        it.x = initialX + (event.rawX - initialTouchX).toInt()
                        it.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, it)
                    }
                    true
                }
                else -> false
            }
        }
    }
}

