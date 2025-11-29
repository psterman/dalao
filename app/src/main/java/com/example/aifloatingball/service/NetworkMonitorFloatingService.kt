ackage com.example.aifloatingball.service

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
import com.example.aifloatingball.App
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.SimpleModeActivity
import java.text.DecimalFormat

/**
 * 网络监控悬浮窗服务
 * 用于显示网速
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
    
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null
    
    private var networkSpeedTextView: TextView? = null
    private var contentContainer: FrameLayout? = null
    
    private var isNetworkSpeedEnabled = false
    private var isGlobalDisplayEnabled = true // 默认全局显示
    
    private var lastRxBytes = 0L
    private var lastTxBytes = 0L
    private var lastTime = 0L
    
    private val updateHandler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            // 每次更新时重新读取设置，确保开关变化能立即生效
            isNetworkSpeedEnabled = settingsManager.getBoolean("network_speed_display_enabled", false)
            isGlobalDisplayEnabled = settingsManager.getBoolean("network_speed_global_display", true)
            
            // 如果网速显示未启用，停止更新并移除悬浮窗
            if (!isNetworkSpeedEnabled) {
                removeFloatingView()
                return
            }
            
            // 检查是否应该显示悬浮窗
            val shouldShow = shouldShowFloatingView()
            val viewExists = floatingView != null && floatingView?.parent != null
            
            if (shouldShow && !viewExists) {
                // 应该显示但悬浮窗不存在，创建悬浮窗
                Log.d(TAG, "应该显示悬浮窗，创建悬浮窗 (全局显示=$isGlobalDisplayEnabled)")
                createFloatingView()
            } else if (!shouldShow && viewExists) {
                // 不应该显示但悬浮窗存在，移除悬浮窗
                Log.d(TAG, "不应该显示悬浮窗，移除悬浮窗 (全局显示=$isGlobalDisplayEnabled, 应用在前台=${App.isAppInForeground})")
                removeFloatingView()
                // 继续监听，等待条件满足时重新创建
                updateHandler.postDelayed(this, UPDATE_INTERVAL)
                return
            }
            
            // 只有在悬浮窗存在时才更新内容
            if (floatingView != null && floatingView?.parent != null) {
                updateNetworkSpeed()
            }
            
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
        isGlobalDisplayEnabled = settingsManager.getBoolean("network_speed_global_display", true) // 默认全局显示
        
        if (isNetworkSpeedEnabled) {
            // 检查是否应该显示悬浮窗
            if (shouldShowFloatingView()) {
                createFloatingView()
                // 立即开始更新（如果悬浮窗创建成功）
                if (floatingView != null) {
                    updateHandler.post(updateRunnable)
                }
            } else {
                // 即使不应该显示，也要启动更新任务，以便检测应用回到前台
                updateHandler.post(updateRunnable)
            }
        }
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "NetworkMonitorFloatingService onStartCommand, action: ${intent?.action}")
        
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
        isGlobalDisplayEnabled = settingsManager.getBoolean("network_speed_global_display", true) // 默认全局显示
        
        if (isNetworkSpeedEnabled) {
            // 检查是否应该显示悬浮窗
            val shouldShow = shouldShowFloatingView()
            val viewExists = floatingView != null && floatingView?.parent != null
            
            Log.d(TAG, "检查悬浮窗显示状态: shouldShow=$shouldShow, viewExists=$viewExists, isGlobalDisplayEnabled=$isGlobalDisplayEnabled, isAppInForeground=${App.isAppInForeground}")
            
            if (shouldShow) {
                // 应该显示，检查悬浮窗是否存在且已添加到窗口管理器
                if (!viewExists) {
                    // 悬浮窗不存在或已被移除，重新创建
                    Log.d(TAG, "应该显示悬浮窗，创建悬浮窗")
                    createFloatingView()
                    // 立即开始更新
                    if (floatingView != null) {
                        updateHandler.post(updateRunnable)
                    }
                } else {
                    // 悬浮窗存在，确保更新任务正在运行
                    if (!updateHandler.hasCallbacks(updateRunnable)) {
                        updateHandler.post(updateRunnable)
                    }
                }
            } else {
                // 不应该显示，立即移除悬浮窗
                Log.d(TAG, "不应该显示悬浮窗，移除悬浮窗 (全局显示=$isGlobalDisplayEnabled, 应用在前台=${App.isAppInForeground})")
                removeFloatingView()
                // 即使悬浮窗被移除，也要启动更新任务，以便检测应用回到前台
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
                description = "显示网速的悬浮窗服务"
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
            .setContentText("正在显示网速")
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
            contentContainer = floatingView?.findViewById(R.id.content_container)
            
            // 设置拖拽
            setupDragListener()
            
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
                if (isNetworkSpeedEnabled) {
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
    
    /**
     * 检查是否应该显示悬浮窗
     * 如果设置为只在软件内显示，则检查应用是否在前台
     */
    private fun shouldShowFloatingView(): Boolean {
        // 如果网速显示未启用，不显示悬浮窗
        if (!isNetworkSpeedEnabled) {
            return false
        }
        
        // 如果设置为全局显示，始终显示
        if (isGlobalDisplayEnabled) {
            return true
        }
        
        // 如果设置为只在软件内显示，检查应用是否在前台
        // 优先使用ActivityLifecycleCallbacks检测（最准确）
        var isInForeground = App.isAppInForeground
        
        // 如果ActivityLifecycleCallbacks检测结果为false，使用备用检测方法
        // 这可以处理服务启动时ActivityLifecycleCallbacks还未初始化的情况
        if (!isInForeground) {
            isInForeground = checkAppInForegroundFallback()
            if (isInForeground) {
                Log.d(TAG, "ActivityLifecycleCallbacks检测为false，但备用检测显示应用在前台")
            }
        }
        
        Log.d(TAG, "检查悬浮窗显示: 全局显示=$isGlobalDisplayEnabled, 应用在前台=$isInForeground")
        
        return isInForeground
    }
    
    /**
     * 备用检测方法：检查应用是否在前台
     * 当ActivityLifecycleCallbacks还未初始化时使用
     * 主要用于处理服务启动时ActivityLifecycleCallbacks还未正确初始化的情况
     */
    private fun checkAppInForegroundFallback(): Boolean {
        return try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            
            // 使用 getRunningAppProcesses 检查本应用进程是否在前台
            val runningProcesses = activityManager.runningAppProcesses
            runningProcesses?.forEach { processInfo ->
                if (processInfo.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    if (processInfo.pkgList.contains(packageName)) {
                        Log.d(TAG, "备用检测：通过getRunningAppProcesses检测到应用在前台")
                        return true
                    }
                }
            }
            
            false
        } catch (e: Exception) {
            Log.e(TAG, "备用检测失败", e)
            false
        }
    }
    
}

