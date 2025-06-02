package com.example.aifloatingball.service

import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.example.aifloatingball.engine.SearchEngineHandler
import com.example.aifloatingball.notification.ServiceNotificationManager
import com.example.aifloatingball.ui.floating.FloatingWindowManager
import com.example.aifloatingball.ui.text.TextSelectionManager
import com.example.aifloatingball.ui.webview.WebViewManager
import com.example.aifloatingball.utils.IntentParser
import com.example.aifloatingball.utils.SearchParams

/**
 * 双窗口浮动WebView服务，提供多窗口并行搜索功能
 */
class DualFloatingWebViewService : FloatingServiceBase() {
    companion object {
        private const val TAG = "DualFloatingWebView"
        const val NOTIFICATION_ID = 2
        const val CHANNEL_ID = "DualFloatingWebViewChannel"
        
        @Volatile
        @JvmStatic
        var isRunning = false
    }

    // 依赖模块
    private lateinit var notificationManager: ServiceNotificationManager
    private lateinit var windowManager: FloatingWindowManager
    private lateinit var webViewManager: WebViewManager
    private lateinit var searchEngineHandler: SearchEngineHandler
    private lateinit var intentParser: IntentParser
    private lateinit var textSelectionManager: TextSelectionManager
    private val handler = Handler(Looper.getMainLooper())

    /**
     * 服务创建时初始化
     */
    override fun onCreate() {
        super.onCreate()
        isRunning = true
        
        Log.d(TAG, "服务创建")
        
        // 初始化各模块
        initializeManagers()
        
        // 启动前台服务
        notificationManager.createNotificationChannel()
        startForeground(NOTIFICATION_ID, notificationManager.createNotification())
        
        // 创建浮动窗口
        windowManager.createFloatingWindow()
    }

    /**
     * 初始化各管理器模块
     */
    private fun initializeManagers() {
        Log.d(TAG, "初始化管理器")
        
        notificationManager = ServiceNotificationManager(this, CHANNEL_ID)
        windowManager = FloatingWindowManager(this)
        searchEngineHandler = SearchEngineHandler()
        intentParser = IntentParser()
        
        // WebViewManager需要在windowManager创建浮动窗口后初始化
        // 但此时windowManager还未创建窗口，所以在onStartCommand中延迟初始化
    }

    /**
     * 处理服务命令
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "处理服务命令")
        
        // 延迟初始化WebViewManager
        if (!::webViewManager.isInitialized) {
            Log.d(TAG, "初始化WebViewManager")
            webViewManager = WebViewManager(this, windowManager.getWebViewContainer())
            
            // 获取TextSelectionManager
            textSelectionManager = webViewManager.getTextSelectionManager()
            
            // 设置WebView的长按监听器
            handler.postDelayed({
                webViewManager.setupLongPressListeners()
            }, 500) // 延迟一点设置，确保WebView已经初始化
        }
        
        // 处理搜索意图
        intent?.let { 
            val searchParams = intentParser.parseSearchIntent(it)
            if (searchParams != null) {
                Log.d(TAG, "处理搜索请求: ${searchParams.query}")
                handleSearch(searchParams)
            }
        }
        return START_NOT_STICKY
    }

    /**
     * 处理搜索请求
     */
    private fun handleSearch(params: SearchParams) {
        // 清除现有WebView
        webViewManager.clearWebViews()
        
        // 创建新WebView并加载URL
        for (i in 0 until params.windowCount) {
            val searchUrl = searchEngineHandler.getSearchUrl(params.query, params.engineKey)
            val webView = webViewManager.addWebView(searchUrl)
            
            // 确保WebView启用了文本选择功能
            webView.setTextSelectionManager(textSelectionManager)
        }
        
        // 重置滚动条
        windowManager.resetScrollPosition()
        
        // 延迟设置长按监听器
        handler.postDelayed({
            webViewManager.setupLongPressListeners()
        }, 1000) // 延迟1秒，确保WebView已经加载
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
        Log.d(TAG, "服务销毁")
        
        isRunning = false
        if (::webViewManager.isInitialized) {
            webViewManager.destroyAll()
        }
        windowManager.removeFloatingWindow()
    }
} 