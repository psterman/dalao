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
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.ui.floating.FloatingWindowManager
import com.example.aifloatingball.ui.floating.WindowStateCallback
import com.example.aifloatingball.ui.text.TextSelectionManager
import com.example.aifloatingball.ui.webview.CustomWebView
import com.example.aifloatingball.ui.webview.WebViewManager
import com.example.aifloatingball.utils.SearchParams

/**
 * 双窗口浮动WebView服务
 */
class DualFloatingWebViewService : FloatingServiceBase(), WindowStateCallback {

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
    
    // 浮动窗口管理器
    private var floatingWindowManager: FloatingWindowManager? = null
    private var textSelectionManager: TextSelectionManager? = null
    private var webViewManager: WebViewManager? = null
    private var xmlDefinedWebViews: List<CustomWebView?> = emptyList()

    // 广播接收器
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_UPDATE_AI_ENGINES, ACTION_UPDATE_MENU -> {
                    Log.d(TAG, "收到刷新搜索引擎的广播")
                    // 刷新搜索引擎
                    refreshSearchEngines()
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
        
        // 初始化文本选择管理器
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        textSelectionManager = TextSelectionManager(this, windowManager)
        
        // 创建浮动窗口
        createFloatingWindow()
    }

    /**
     * 创建浮动窗口
     */
    private fun createFloatingWindow() {
        try {
            Log.d(TAG, "开始创建浮动窗口")
            
            // 初始化浮动窗口管理器
            floatingWindowManager = FloatingWindowManager(
                this,
                this,
                this,
                textSelectionManager!!
            )
            
            // 创建浮动窗口并获取WebView列表
            xmlDefinedWebViews = floatingWindowManager!!.createFloatingWindow()
            
            // 初始化WebView管理器
            webViewManager = WebViewManager(
                this,
                xmlDefinedWebViews,
                floatingWindowManager!!
            )
            
            Log.d(TAG, "浮动窗口创建成功")
            
        } catch (e: Exception) {
            Log.e(TAG, "创建浮动窗口失败", e)
            stopSelf()
        }
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
     * 刷新搜索引擎
     */
    private fun refreshSearchEngines() {
        floatingWindowManager?.refreshSearchEngines()
    }
    
    /**
     * 在当前活动的WebView中执行JavaScript脚本
     */
    private fun executeScriptInActiveWebView(script: String) {
        Log.d(TAG, "执行JavaScript脚本: $script")
        webViewManager?.executeScriptInActiveWebView(script)
    }

    /**
     * 处理服务命令
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "DualFloatingWebViewService: onStartCommand")
        
        intent?.let {
            // 处理搜索请求
            val searchQuery = it.getStringExtra("search_query")
            val engineKey = it.getStringExtra("engine_key")
            val searchSource = it.getStringExtra("search_source")
            
            if (!searchQuery.isNullOrEmpty()) {
                Log.d(TAG, "收到搜索请求: query='$searchQuery', engine='$engineKey', source='$searchSource'")
                
                // 设置搜索文本到输入框
                floatingWindowManager?.setSearchInputText(searchQuery)
                
                // 执行搜索
                performSearch(searchQuery, engineKey ?: "google")
                
                // 检查是否需要显示工具栏
                val showToolbar = it.getBooleanExtra("show_toolbar", false)
                if (showToolbar) {
                    // 延迟显示工具栏，等待WebView加载
                    handler.postDelayed({
                        try {
                            val toolbarIntent = Intent()
                            toolbarIntent.setClassName(this, "com.example.aifloatingball.service.WebViewToolbarService")
                            toolbarIntent.action = "com.example.aifloatingball.SHOW_TOOLBAR"
                            toolbarIntent.putExtra("search_text", searchQuery)
                            toolbarIntent.putExtra("ai_engine", engineKey ?: "Unknown")
                            startService(toolbarIntent)
                            Log.d(TAG, "已启动工具栏服务")
                        } catch (e: Exception) {
                            Log.e(TAG, "启动工具栏服务失败", e)
                        }
                    }, 3000) // 3秒延迟
                }
            }
            
            // 处理URL加载请求
            val url = it.getStringExtra("url")
            if (!url.isNullOrEmpty()) {
                Log.d(TAG, "收到URL加载请求: $url")
                loadUrlInWebView(url)
            }
            
            // 处理窗口数量设置
            val windowCount = it.getIntExtra("window_count", getCurrentWindowCount())
            if (windowCount != getCurrentWindowCount()) {
                Log.d(TAG, "更新窗口数量: $windowCount")
                updateWindowCount(windowCount)
            }
        }

        return START_NOT_STICKY
    }

    /**
     * 执行搜索
     */
    private fun performSearch(query: String, engineKey: String) {
        webViewManager?.performSearch(query, engineKey)
    }
    
    /**
     * 在WebView中加载URL
     */
    private fun loadUrlInWebView(url: String) {
        webViewManager?.loadUrlInActiveWebView(url)
    }
    
    /**
     * 更新窗口数量
     */
    private fun updateWindowCount(count: Int) {
        floatingWindowManager?.updateWindowCount(count)
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
        val currentCount = getCurrentWindowCount()
        val newCount = when (currentCount) {
            1 -> 2
            2 -> 3
            3 -> 1
            else -> 1
        }
        
        // 保存新的窗口数量
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_WINDOW_COUNT, newCount).apply()
        
        // 更新窗口显示
        floatingWindowManager?.updateWindowCount(newCount)
        
        Log.d(TAG, "切换窗口数量: $currentCount -> $newCount")
        return newCount
    }
    
    /**
     * 获取当前窗口数量
     */
    fun getCurrentWindowCount(): Int {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(KEY_WINDOW_COUNT, settingsManager.getDefaultWindowCount())
    }

    /**
     * 在指定WebView中执行搜索
     */
    fun performSearchInWebView(webViewIndex: Int, query: String, engineKey: String) {
        Log.d(TAG, "performSearchInWebView: index=$webViewIndex, query='$query', engineKey='$engineKey'")
        webViewManager?.performSearchInWebView(webViewIndex, query, engineKey)
    }

    /**
     * 窗口状态回调
     */
    override fun onWindowStateChanged(x: Int, y: Int, width: Int, height: Int) {
        Log.d(TAG, "保存窗口状态: X=$x, Y=$y, Width=$width, Height=$height")
        
        // 保存窗口状态到SharedPreferences
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt(KEY_WINDOW_X, x)
            putInt(KEY_WINDOW_Y, y)
            putInt(KEY_WINDOW_WIDTH, width)
            putInt(KEY_WINDOW_HEIGHT, height)
            apply()
        }
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
        
        // 销毁浮动窗口
        floatingWindowManager?.destroyFloatingWindow()
        
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
} 