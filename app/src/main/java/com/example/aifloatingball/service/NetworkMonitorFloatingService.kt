package com.example.aifloatingball.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
    private var downloadCompleteHint: TextView? = null
    private var contentContainer: FrameLayout? = null
    
    // 下载完成相关：记录完成的下载数量
    private var completedDownloadCount = 0
    
    private var isNetworkSpeedEnabled = false
    private var isDownloadProgressEnabled = false
    
    private var lastRxBytes = 0L
    private var lastTxBytes = 0L
    private var lastTime = 0L
    
    // 下载完成广播接收器
    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.aifloatingball.DOWNLOAD_COMPLETE") {
                val downloadId = intent.getLongExtra("download_id", -1L)
                val fileName = intent.getStringExtra("file_name") ?: ""
                if (downloadId != -1L && fileName.isNotEmpty()) {
                    notifyDownloadComplete(downloadId, fileName)
                }
            }
        }
    }
    
    private val updateHandler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateNetworkSpeed()
            updateDownloadProgress()
            // 即使下载进度显示未启用，也要更新下载完成数字
            updateDownloadCompleteHint()
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
        
        // 注册下载完成广播接收器
        val filter = IntentFilter("com.example.aifloatingball.DOWNLOAD_COMPLETE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadCompleteReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(downloadCompleteReceiver, filter)
        }
        
        // 检查悬浮窗权限（Android 6.0+需要）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(this)) {
                Log.w(TAG, "没有悬浮窗权限，停止服务")
                stopSelf()
                return
            }
        }
        
        // 检查设置
        isNetworkSpeedEnabled = settingsManager.getBoolean("network_speed_display_enabled", false)
        isDownloadProgressEnabled = settingsManager.getBoolean("download_progress_display_enabled", false)
        
        if (isNetworkSpeedEnabled || isDownloadProgressEnabled) {
            createFloatingView()
            // 初始化已完成的下载数量
            initializeCompletedDownloadCount()
            // 立即开始更新（如果悬浮窗创建成功）
            if (floatingView != null) {
                updateHandler.post(updateRunnable)
            }
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "NetworkMonitorFloatingService onStartCommand")
        
        // 检查悬浮窗权限（Android 6.0+需要）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!android.provider.Settings.canDrawOverlays(this)) {
                Log.w(TAG, "没有悬浮窗权限，停止服务")
                stopSelf()
                return START_NOT_STICKY
            }
        }
        
        // 检查设置状态
        isNetworkSpeedEnabled = settingsManager.getBoolean("network_speed_display_enabled", false)
        isDownloadProgressEnabled = settingsManager.getBoolean("download_progress_display_enabled", false)
        
        if (isNetworkSpeedEnabled || isDownloadProgressEnabled) {
            // 检查悬浮窗是否存在且已添加到窗口管理器
            val viewExists = floatingView != null && floatingView?.parent != null
            if (!viewExists) {
                // 悬浮窗不存在或已被移除，重新创建
                Log.d(TAG, "悬浮窗不存在，重新创建")
                createFloatingView()
                // 立即开始更新
                if (floatingView != null) {
                    updateHandler.post(updateRunnable)
                }
            } else {
                // 悬浮窗存在，更新可见性
                updateViewVisibility()
                // 确保更新任务正在运行
                if (!updateHandler.hasCallbacks(updateRunnable)) {
                    updateHandler.post(updateRunnable)
                }
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
        try {
            unregisterReceiver(downloadCompleteReceiver)
        } catch (e: Exception) {
            Log.w(TAG, "注销广播接收器失败", e)
        }
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
            // 检查悬浮窗权限（Android 6.0+需要）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!android.provider.Settings.canDrawOverlays(this)) {
                    Log.e(TAG, "创建悬浮窗失败：没有悬浮窗权限")
                    // 如果服务正在运行但没有权限，停止服务
                    stopSelf()
                    return
                }
            }
            
            // 如果悬浮窗已经存在，先移除
            if (floatingView != null) {
                try {
                    windowManager.removeView(floatingView)
                } catch (e: Exception) {
                    Log.w(TAG, "移除旧悬浮窗失败", e)
                }
                floatingView = null
            }
            
            val inflater = LayoutInflater.from(this)
            floatingView = inflater.inflate(R.layout.floating_network_monitor, null)
            
            networkSpeedTextView = floatingView?.findViewById(R.id.network_speed_text)
            downloadProgressTextView = floatingView?.findViewById(R.id.download_progress_text)
            downloadCompleteHint = floatingView?.findViewById(R.id.download_complete_hint)
            contentContainer = floatingView?.findViewById(R.id.content_container)
            
            // 设置下载完成提示的点击事件
            downloadCompleteHint?.setOnClickListener {
                openDownloadManager()
            }
            
            // 设置点击切换显示内容（但下载完成提示不参与切换）
            floatingView?.setOnClickListener { view ->
                // 如果点击的是下载完成提示，不切换显示模式
                if (view.id != R.id.download_complete_hint) {
                    toggleDisplayMode()
                }
            }
            
            // 设置拖拽
            setupDragListener()
            
            // 更新视图可见性
            updateViewVisibility()
            
            // 获取状态栏高度，避免遮挡状态栏
            val statusBarHeight = getStatusBarHeight()
            
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
                // 设置y坐标为状态栏高度+16dp，避免遮挡状态栏
                y = statusBarHeight + (16 * resources.displayMetrics.density).toInt()
            }
            
            windowManager.addView(floatingView, params)
            
            // 初始化网络统计
            lastRxBytes = TrafficStats.getTotalRxBytes()
            lastTxBytes = TrafficStats.getTotalTxBytes()
            lastTime = System.currentTimeMillis()
            
            // 开始更新
            updateHandler.post(updateRunnable)
            
            Log.d(TAG, "悬浮窗创建成功")
        } catch (e: SecurityException) {
            Log.e(TAG, "创建悬浮窗失败：缺少权限", e)
            // 缺少权限，停止服务
            stopSelf()
        } catch (e: android.view.WindowManager.BadTokenException) {
            Log.e(TAG, "创建悬浮窗失败：窗口令牌无效", e)
            // 窗口令牌无效，可能是权限问题，停止服务
            stopSelf()
        } catch (e: Exception) {
            Log.e(TAG, "创建悬浮窗失败", e)
            // 其他异常，尝试重新创建
            floatingView = null
            // 延迟重试
            updateHandler.postDelayed({
                if (isNetworkSpeedEnabled || isDownloadProgressEnabled) {
                    createFloatingView()
                }
            }, 1000)
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
        
        // 下载完成提示始终可用（即使下载进度显示未启用）
        // 如果下载完成数量大于0，显示提示
        if (completedDownloadCount > 0) {
            downloadCompleteHint?.text = completedDownloadCount.toString()
            downloadCompleteHint?.visibility = View.VISIBLE
        } else {
            downloadCompleteHint?.visibility = View.GONE
        }
        
        // 如果两个都关闭，隐藏整个悬浮窗
        if (!isNetworkSpeedEnabled && !isDownloadProgressEnabled && completedDownloadCount == 0) {
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
            
            // 检查TrafficStats是否支持（返回-1表示不支持）
            if (currentRxBytes == TrafficStats.UNSUPPORTED.toLong() || 
                currentTxBytes == TrafficStats.UNSUPPORTED.toLong()) {
                networkSpeedTextView?.text = "↓0.0KB/s ↑0.0KB/s"
                return
            }
            
            // 初始化时，设置初始值，不显示网速
            if (lastTime == 0L) {
                lastRxBytes = currentRxBytes
                lastTxBytes = currentTxBytes
                lastTime = currentTime
                // 显示初始占位文本，避免显示0
                networkSpeedTextView?.text = "↓0.0KB/s ↑0.0KB/s"
                return
            }
            
            val timeDiff = (currentTime - lastTime) / 1000.0 // 秒
            // 确保时间差大于0且至少0.1秒，避免计算异常
            if (timeDiff > 0.1) {
                // 计算速度差，处理可能的负数情况（流量统计重置）
                val rxDiff = currentRxBytes - lastRxBytes
                val txDiff = currentTxBytes - lastTxBytes
                
                // 如果流量统计重置（当前值小于上次值），重新初始化
                if (rxDiff < 0 || txDiff < 0) {
                    lastRxBytes = currentRxBytes
                    lastTxBytes = currentTxBytes
                    lastTime = currentTime
                    return
                }
                
                val rxSpeed = (rxDiff / timeDiff).toLong()
                val txSpeed = (txDiff / timeDiff).toLong()
                
                // 确保速度值非负
                val safeRxSpeed = rxSpeed.coerceAtLeast(0)
                val safeTxSpeed = txSpeed.coerceAtLeast(0)
                
                val speedText = "↓${formatSpeed(safeRxSpeed)} ↑${formatSpeed(safeTxSpeed)}"
                networkSpeedTextView?.text = speedText
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
        // 即使下载进度显示未启用，也要检查并显示下载完成数字
        // 先更新下载完成数字的显示
        if (completedDownloadCount > 0) {
            downloadCompleteHint?.text = completedDownloadCount.toString()
            downloadCompleteHint?.visibility = View.VISIBLE
        } else {
            downloadCompleteHint?.visibility = View.GONE
        }
        
        // 如果下载进度显示未启用，只更新完成数字，不更新进度文本
        if (!isDownloadProgressEnabled || downloadProgressTextView == null) return
        
        try {
            val downloads = downloadManager.getAllDownloads()
            val activeDownloads = downloads.filter { 
                it.status == android.app.DownloadManager.STATUS_RUNNING 
            }
            
            // 统计完成的下载数量（最近完成的，用于显示提示）
            val completedDownloads = downloads.filter {
                it.status == android.app.DownloadManager.STATUS_SUCCESSFUL
            }
            
            if (activeDownloads.isNotEmpty()) {
                // 有活动下载，隐藏完成提示，显示下载进度
                downloadCompleteHint?.visibility = View.GONE
                
                val download = activeDownloads.first()
                val progress = if (download.bytesTotal > 0) {
                    (download.bytesDownloaded * 100 / download.bytesTotal).toInt()
                } else 0
                
                val fileName = download.localFilename ?: download.title ?: "未知文件"
                // 截断文件名，避免过长
                val shortFileName = if (fileName.length > 15) {
                    fileName.substring(0, 15) + "..."
                } else {
                    fileName
                }
                val progressText = "下载: $shortFileName\n$progress%"
                downloadProgressTextView?.text = progressText
                Log.d(TAG, "更新下载进度: $shortFileName $progress%")
            } else if (completedDownloadCount > 0) {
                // 没有活动下载，但有完成的下载，显示完成数量
                downloadCompleteHint?.text = completedDownloadCount.toString()
                downloadCompleteHint?.visibility = View.VISIBLE
                downloadProgressTextView?.text = "无下载任务"
                Log.d(TAG, "显示下载完成数量: $completedDownloadCount")
            } else {
                // 没有活动下载，也没有完成的下载
                downloadProgressTextView?.text = "无下载任务"
                downloadCompleteHint?.visibility = View.GONE
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新下载进度失败", e)
            downloadProgressTextView?.text = "下载进度获取失败"
            downloadCompleteHint?.visibility = View.GONE
        }
    }
    
    /**
     * 打开下载管理界面
     */
    private fun openDownloadManager() {
        try {
            val intent = Intent(this, com.example.aifloatingball.download.DownloadManagerActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            Log.d(TAG, "打开下载管理界面")
            
            // 重置完成数量（用户已查看）
            completedDownloadCount = 0
            downloadCompleteHint?.visibility = View.GONE
        } catch (e: Exception) {
            Log.e(TAG, "打开下载管理界面失败", e)
        }
    }
    
    /**
     * 初始化已完成的下载数量
     */
    private fun initializeCompletedDownloadCount() {
        try {
            val downloads = downloadManager.getAllDownloads()
            val completedDownloads = downloads.filter {
                it.status == android.app.DownloadManager.STATUS_SUCCESSFUL
            }
            completedDownloadCount = completedDownloads.size
            Log.d(TAG, "初始化完成下载数量: $completedDownloadCount")
            // 立即更新显示
            updateDownloadCompleteHint()
        } catch (e: Exception) {
            Log.e(TAG, "初始化完成下载数量失败", e)
        }
    }
    
    /**
     * 更新下载完成数字提示
     */
    private fun updateDownloadCompleteHint() {
        if (completedDownloadCount > 0) {
            downloadCompleteHint?.text = completedDownloadCount.toString()
            downloadCompleteHint?.visibility = View.VISIBLE
        } else {
            downloadCompleteHint?.visibility = View.GONE
        }
    }
    
    /**
     * 通知下载完成（由EnhancedDownloadManager调用）
     */
    fun notifyDownloadComplete(downloadId: Long, fileName: String) {
        updateHandler.post {
            // 增加完成数量
            completedDownloadCount++
            // 立即更新显示数字
            updateDownloadCompleteHint()
            // 立即更新显示
            updateDownloadProgress()
            Log.d(TAG, "下载完成，当前完成数量: $completedDownloadCount")
        }
    }
    
    /**
     * 格式化网速
     * 保留一位小数，确保能兼容各种网速数值
     */
    private fun formatSpeed(bytesPerSecond: Long): String {
        val df = DecimalFormat("#.#")
        return when {
            bytesPerSecond >= 1024 * 1024 -> "${df.format(bytesPerSecond / (1024.0 * 1024.0))}MB/s"
            bytesPerSecond >= 1024 -> "${df.format(bytesPerSecond / 1024.0)}KB/s"
            else -> "${bytesPerSecond}B/s"
        }
    }
    
    /**
     * 获取状态栏高度
     */
    private fun getStatusBarHeight(): Int {
        var result = 0
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            result = resources.getDimensionPixelSize(resourceId)
        }
        // 如果获取失败，使用默认值（24dp）
        if (result == 0) {
            result = (24 * resources.displayMetrics.density).toInt()
        }
        return result
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
                        val newX = initialX + (event.rawX - initialTouchX).toInt()
                        var newY = initialY + (event.rawY - initialTouchY).toInt()
                        
                        // 确保悬浮窗不会移动到状态栏上方
                        val statusBarHeight = getStatusBarHeight()
                        val minY = statusBarHeight + (8 * resources.displayMetrics.density).toInt()
                        if (newY < minY) {
                            newY = minY
                        }
                        
                        it.x = newX
                        it.y = newY
                        windowManager.updateViewLayout(floatingView, it)
                    }
                    true
                }
                else -> false
            }
        }
    }
}

