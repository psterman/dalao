package com.example.aifloatingball.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.core.app.NotificationCompat
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager

/**
 * 双窗口浮动WebView服务
 */
class DualFloatingWebViewService : FloatingServiceBase() {

    companion object {
        const val TAG = "DualFloatingWebViewService"
        const val PREFS_NAME = "dual_floating_webview"
        const val KEY_WINDOW_X = "window_x"
        const val KEY_WINDOW_Y = "window_y"
        const val KEY_WINDOW_WIDTH = "window_width"
        const val KEY_WINDOW_HEIGHT = "window_height"
        const val KEY_WINDOW_COUNT = "window_count"
        const val ACTION_UPDATE_AI_ENGINES = "com.example.aifloatingball.ACTION_UPDATE_AI_ENGINES"
        const val ACTION_UPDATE_MENU = "com.example.aifloatingball.ACTION_UPDATE_MENU"
        
        // 通知相关常量
        const val NOTIFICATION_ID = 2
        const val CHANNEL_ID = "DualFloatingWebViewChannel"
        
        // 添加静态isRunning字段
        @JvmStatic
        var isRunning = false
    }

    // 状态变量
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var settingsManager: SettingsManager

    // WebViewManager将在后续实现中添加

    // 广播接收器
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_UPDATE_AI_ENGINES, ACTION_UPDATE_MENU -> {
                    Log.d(TAG, "收到刷新搜索引擎的广播")
                }
                "com.example.aifloatingball.WEBVIEW_EXECUTE_SCRIPT" -> {
                    val script = intent.getStringExtra("script")
                    if (!script.isNullOrEmpty()) {
                        executeScriptInActiveWebView(script)
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "DualFloatingWebViewService: onCreate")
        isRunning = true

        // 初始化SettingsManager
        settingsManager = SettingsManager.getInstance(this)

        // 创建通知渠道和启动前台服务
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // 注册广播接收器
        registerBroadcastReceiver()
    }

    /**
     * 创建通知渠道
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "双窗口浮动WebView服务"
            val descriptionText = "保持多窗口浮动WebView服务运行"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
                setShowBadge(false)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 创建通知
     */
    private fun createNotification(): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_search)
            .setContentTitle("多窗口浮动WebView服务")
            .setContentText("服务正在运行")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
        
        return builder.build()
    }

    /**
     * 注册广播接收器
     */
    private fun registerBroadcastReceiver() {
        val intentFilter = IntentFilter().apply {
            addAction(ACTION_UPDATE_AI_ENGINES)
            addAction(ACTION_UPDATE_MENU)
            addAction("com.example.aifloatingball.WEBVIEW_EXECUTE_SCRIPT")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(broadcastReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(broadcastReceiver, intentFilter)
        }
    }
    
    /**
     * 在当前活动的WebView中执行JavaScript脚本
     */
    private fun executeScriptInActiveWebView(script: String) {
        Log.d(TAG, "执行JavaScript脚本")
    }

    /**
     * 处理服务命令
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "DualFloatingWebViewService: onStartCommand")
        
        intent?.let {
            val searchParams = it.getStringExtra("query")
            
            if (!searchParams.isNullOrEmpty()) {
                // 检查是否需要显示工具栏
                val showToolbar = it.getBooleanExtra("show_toolbar", false)
                if (showToolbar) {
                    // 延迟显示工具栏，等待WebView加载
                    handler.postDelayed({
                        try {
                            val toolbarIntent = Intent()
                            toolbarIntent.setClassName(this, "com.example.aifloatingball.service.WebViewToolbarService")
                            toolbarIntent.action = "com.example.aifloatingball.SHOW_TOOLBAR"
                            toolbarIntent.putExtra("search_text", searchParams)
                            toolbarIntent.putExtra("ai_engine", "Unknown")
                            startService(toolbarIntent)
                            Log.d(TAG, "已启动工具栏服务")
                        } catch (e: Exception) {
                            Log.e(TAG, "启动工具栏服务失败", e)
                        }
                    }, 3000) // 3秒延迟
                }
            }
        }

        return START_NOT_STICKY
    }

    /**
     * 判断是否是AI搜索引擎
     */
    private fun isAIEngine(engineKey: String): Boolean {
        val aiEngineKeys = listOf(
            "chatgpt", "chatgpt_chat", "claude", "gemini", "wenxin", "chatglm",
            "qianwen", "xinghuo", "perplexity", "phind", "poe", "deepseek", "deepseek_chat",
            "kimi", "tiangong", "metaso", "quark", "360ai", "baiduai", "you", "brave",
            "wolfram", "wanzhi", "baixiaoying", "yuewen", "doubao", "cici", "hailuo",
            "groq", "yuanbao"
        )
        return aiEngineKeys.any { it.equals(engineKey, ignoreCase = true) }
    }

    /**
     * 切换 WebView 窗口数量并重新加载内容。
     */
    fun toggleAndReloadWindowCount(): Int {
        return 1
    }
    
    /**
     * 获取当前窗口数量
     */
    fun getCurrentWindowCount(): Int {
        return 1
    }

    /**
     * 在指定WebView中执行搜索
     */
    fun performSearchInWebView(webViewIndex: Int, query: String, engineKey: String) {
        Log.d(TAG, "performSearchInWebView: index=$webViewIndex, query='$query', engineKey='$engineKey'")
    }

    /**
     * 服务绑定
     */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 服务销毁时清理资源
     */
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        
        // 停止前台服务
        stopForeground(true)
        
        // 取消注册广播接收器
        try {
            unregisterReceiver(broadcastReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "广播接收器未注册或已被取消注册。")
        }
        
        // 移除所有 Handler 回调
        handler.removeCallbacksAndMessages(null)
        
        Log.d(TAG, "DualFloatingWebViewService: 服务销毁 onDestroy()")
    }

    fun onWindowStateChanged(x: Int, y: Int, width: Int, height: Int) {
        Log.d(TAG, "保存窗口状态: X=$x, Y=$y, Width=$width, Height=$height")
    }
} 