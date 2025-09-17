package com.example.aifloatingball.service

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.aifloatingball.HomeActivity
import com.example.aifloatingball.R

/**
 * 专门的前台服务，用于保活剪贴板监听功能
 * 解决无障碍服务在跨应用时被系统限制的问题
 */
class ClipboardForegroundService : Service() {
    
    companion object {
        private const val TAG = "ClipboardForegroundService"
        private const val NOTIFICATION_ID = 9999
        private const val CHANNEL_ID = "clipboard_monitor_channel"
        const val ACTION_START_MONITORING = "start_monitoring"
        const val ACTION_STOP_MONITORING = "stop_monitoring"
        const val ACTION_CLIPBOARD_DETECTED = "clipboard_detected"
        const val EXTRA_CLIPBOARD_CONTENT = "clipboard_content"
    }
    
    private lateinit var clipboardManager: ClipboardManager
    private var lastClipboardContent: String? = null
    private var lastClipboardChangeTime = 0L
    private val clipboardChangeDebounceTime = 500L // 防抖时间：0.5秒
    
    // 强化轮询机制 - 突破后台限制
    private val pollingHandler = Handler(Looper.getMainLooper())
    private var pollingRunnable: Runnable? = null
    private val pollingInterval = 100L // 每100ms检查一次（极度频繁）

    // 多重检查机制
    private val secondaryHandler = Handler(Looper.getMainLooper())
    private var secondaryRunnable: Runnable? = null
    private val secondaryInterval = 50L // 每50ms检查一次（超级频繁）

    // 应用状态监控
    private var isAppInBackground = false
    private val backgroundCheckHandler = Handler(Looper.getMainLooper())
    private var backgroundCheckRunnable: Runnable? = null
    
    private var isMonitoring = false
    
    // 剪贴板监听器
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        Log.d(TAG, "🔔 前台服务剪贴板监听器触发")
        handleClipboardChange("foreground_listener")
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "🚀 剪贴板前台服务创建")

        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        createNotificationChannel()

        // 立即启动前台服务以避免ANR
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)
        Log.d(TAG, "✅ 前台服务已启动")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_MONITORING -> {
                startMonitoring()
            }
            ACTION_STOP_MONITORING -> {
                stopMonitoring()
                stopSelf()
            }
            else -> {
                startMonitoring()
            }
        }
        
        return START_STICKY // 服务被杀死后自动重启
    }
    
    private fun startMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "剪贴板监听已在运行")
            return
        }

        Log.d(TAG, "🎯 启动剪贴板前台监听")
        
        // 注册剪贴板监听器
        try {
            clipboardManager.addPrimaryClipChangedListener(clipboardListener)
            Log.d(TAG, "✅ 剪贴板监听器已注册")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 注册剪贴板监听器失败", e)
        }
        
        // 启动多重轮询机制
        startPolling()
        startSecondaryPolling()
        startBackgroundMonitoring()

        // 初始化当前剪贴板内容
        updateLastClipboardContent()

        isMonitoring = true
        Log.d(TAG, "✅ 剪贴板前台监听已启动（多重轮询模式）")
    }
    
    private fun stopMonitoring() {
        if (!isMonitoring) return
        
        Log.d(TAG, "🛑 停止剪贴板前台监听")
        
        // 移除剪贴板监听器
        try {
            clipboardManager.removePrimaryClipChangedListener(clipboardListener)
            Log.d(TAG, "✅ 剪贴板监听器已移除")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 移除剪贴板监听器失败", e)
        }
        
        // 停止所有轮询机制
        stopPolling()
        stopSecondaryPolling()
        stopBackgroundMonitoring()

        isMonitoring = false
        stopForeground(true)
    }
    
    private fun startPolling() {
        stopPolling() // 先停止之前的轮询

        pollingRunnable = object : Runnable {
            override fun run() {
                handleClipboardChange("primary_polling")
                pollingHandler.postDelayed(this, pollingInterval)
            }
        }

        pollingHandler.postDelayed(pollingRunnable!!, pollingInterval)
        Log.d(TAG, "🔄 主轮询已启动，间隔: ${pollingInterval}ms")
    }

    private fun stopPolling() {
        pollingRunnable?.let { runnable ->
            pollingHandler.removeCallbacks(runnable)
            pollingRunnable = null
            Log.d(TAG, "🛑 主轮询已停止")
        }
    }

    private fun startSecondaryPolling() {
        stopSecondaryPolling()

        secondaryRunnable = object : Runnable {
            override fun run() {
                // 在后台时使用更频繁的检查
                val interval = if (isAppInBackground) 25L else secondaryInterval
                handleClipboardChange("secondary_polling")
                secondaryHandler.postDelayed(this, interval)
            }
        }

        secondaryHandler.postDelayed(secondaryRunnable!!, secondaryInterval)
        Log.d(TAG, "🔄 辅助轮询已启动，间隔: ${secondaryInterval}ms")
    }

    private fun stopSecondaryPolling() {
        secondaryRunnable?.let { runnable ->
            secondaryHandler.removeCallbacks(runnable)
            secondaryRunnable = null
            Log.d(TAG, "🛑 辅助轮询已停止")
        }
    }

    private fun startBackgroundMonitoring() {
        stopBackgroundMonitoring()

        backgroundCheckRunnable = object : Runnable {
            override fun run() {
                checkAppBackgroundStatus()
                backgroundCheckHandler.postDelayed(this, 1000L) // 每秒检查应用状态
            }
        }

        backgroundCheckHandler.postDelayed(backgroundCheckRunnable!!, 1000L)
        Log.d(TAG, "🔄 后台状态监控已启动")
    }

    private fun stopBackgroundMonitoring() {
        backgroundCheckRunnable?.let { runnable ->
            backgroundCheckHandler.removeCallbacks(runnable)
            backgroundCheckRunnable = null
            Log.d(TAG, "🛑 后台状态监控已停止")
        }
    }

    private fun checkAppBackgroundStatus() {
        try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningTasks = activityManager.getRunningTasks(1)

            val wasInBackground = isAppInBackground
            isAppInBackground = runningTasks.isNotEmpty() &&
                !runningTasks[0].topActivity?.packageName.equals(packageName)

            if (wasInBackground != isAppInBackground) {
                Log.d(TAG, "应用状态变化: ${if (isAppInBackground) "进入后台" else "回到前台"}")

                if (isAppInBackground) {
                    // 应用进入后台，启动超级频繁检查
                    Log.d(TAG, "🚀 应用进入后台，启动超频监听模式")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "检查应用状态失败: ${e.message}")
        }
    }
    
    private fun handleClipboardChange(source: String) {
        try {
            val currentTime = System.currentTimeMillis()

            // 在后台时减少防抖时间，提高响应速度
            val debounceTime = if (isAppInBackground) 100L else clipboardChangeDebounceTime

            // 防抖处理
            if (currentTime - lastClipboardChangeTime < debounceTime) {
                return
            }

            val currentContent = getCurrentClipboardContent()

            if (currentContent != null &&
                currentContent.isNotEmpty() &&
                currentContent != lastClipboardContent) {

                val statusPrefix = if (isAppInBackground) "🔥[后台模式]" else "✅"
                Log.d(TAG, "$statusPrefix [$source] 检测到剪贴板变化: ${currentContent.take(30)}${if (currentContent.length > 30) "..." else ""}")

                // 更新状态
                lastClipboardContent = currentContent
                lastClipboardChangeTime = currentTime

                // 发送广播通知（后台模式下发送多次确保送达）
                sendClipboardChangeBroadcast(currentContent)
                if (isAppInBackground) {
                    // 后台模式下延迟再发送一次，确保送达
                    pollingHandler.postDelayed({
                        sendClipboardChangeBroadcast(currentContent)
                    }, 50)
                }
                
            } else {
                // 详细的调试信息
                if (source == "foreground_polling") {
                    // 轮询时不输出重复日志
                    return
                }
                
                if (currentContent == null) {
                    Log.v(TAG, "❌ [$source] 剪贴板内容为null")
                } else if (currentContent.isEmpty()) {
                    Log.v(TAG, "❌ [$source] 剪贴板内容为空")
                } else if (currentContent == lastClipboardContent) {
                    Log.v(TAG, "❌ [$source] 剪贴板内容重复")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[$source] 处理剪贴板变化失败", e)
        }
    }
    
    private fun getCurrentClipboardContent(): String? {
        return try {
            val clipData = clipboardManager.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val item = clipData.getItemAt(0)
                item.text?.toString()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取剪贴板内容失败", e)
            null
        }
    }
    
    private fun updateLastClipboardContent() {
        lastClipboardContent = getCurrentClipboardContent()
        Log.d(TAG, "初始化剪贴板内容: ${lastClipboardContent?.take(30) ?: "null"}")
    }
    
    private fun sendClipboardChangeBroadcast(content: String) {
        try {
            val intent = Intent(ACTION_CLIPBOARD_DETECTED).apply {
                putExtra(EXTRA_CLIPBOARD_CONTENT, content)
            }
            
            // 发送本地广播
            LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
            
            // 同时发送给无障碍服务的广播
            val accessibilityIntent = Intent(MyAccessibilityService.ACTION_CLIPBOARD_CHANGED).apply {
                putExtra(MyAccessibilityService.EXTRA_CLIPBOARD_CONTENT, content)
            }
            LocalBroadcastManager.getInstance(this).sendBroadcast(accessibilityIntent)
            
            Log.d(TAG, "📡 已发送剪贴板变化广播")
        } catch (e: Exception) {
            Log.e(TAG, "发送剪贴板变化广播失败", e)
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "剪贴板监听",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持剪贴板监听功能运行"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, HomeActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI悬浮球")
            .setContentText("剪贴板监听运行中...")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setShowWhen(false)
            .build()
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🛑 剪贴板前台服务销毁")
        stopMonitoring()
    }
}
