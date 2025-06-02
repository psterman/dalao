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
        private const val MAX_WINDOW_COUNT = 3 // 最大窗口数量
        private const val DEFAULT_WINDOW_COUNT = 2 // 默认窗口数量
    }

    // 依赖模块
    private lateinit var notificationManager: ServiceNotificationManager
    private lateinit var windowManager: FloatingWindowManager
    private lateinit var webViewManager: WebViewManager
    private lateinit var searchEngineHandler: SearchEngineHandler
    private lateinit var intentParser: IntentParser
    private lateinit var textSelectionManager: TextSelectionManager
    private val handler = Handler(Looper.getMainLooper())

    private var currentWindowCount = DEFAULT_WINDOW_COUNT
    private var lastQuery: String? = null // 用于在切换窗口数量时重新加载内容
    private var lastEngineKey: String? = null // 用于在切换窗口数量时重新加载内容


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
        // 创建时，使用默认窗口数量加载一次 (例如加载空白页或上次的查询)
        // handleSearch(SearchParams(query = lastQuery ?: "", engineKey = lastEngineKey ?: SearchEngineHandler.DEFAULT_ENGINE_KEY, windowCount = currentWindowCount))
    }

    /**
     * 初始化各管理器模块
     */
    private fun initializeManagers() {
        Log.d(TAG, "初始化管理器")
        
        notificationManager = ServiceNotificationManager(this, CHANNEL_ID)
        // FloatingWindowManager 需要在 service 实例创建后，才能获取到更新后的 windowCountToggleText
        // 因此，其按钮的 UI 更新回调可能需要 service 准备好之后再设置，或者通过接口
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
            textSelectionManager = webViewManager.textSelectionManager
            
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
                // 保存最后一次的查询和引擎，以便在切换窗口数时使用
                lastQuery = searchParams.query
                lastEngineKey = searchParams.engineKey
                // 注意：这里的 windowCount 来自 Intent，如果希望 toggleWindowCount 完全控制，则忽略 searchParams.windowCount
                // 或者，让 Intent 中的 windowCount 优先，并更新 currentWindowCount
                // currentWindowCount = searchParams.windowCount // 如果Intent可以指定初始窗口数
                handleSearchInternal(searchParams.query ?: "", searchParams.engineKey ?: SearchEngineHandler.DEFAULT_ENGINE_KEY, currentWindowCount)
            } else {
                // 如果没有搜索参数，但服务启动了，可能需要加载默认页面或上次的查询
                 if (lastQuery == null) { // 避免重复加载
                    handleSearchInternal("", SearchEngineHandler.DEFAULT_ENGINE_KEY, currentWindowCount) // 加载空白页或默认页
                 }
            }
        }
        return START_NOT_STICKY
    }

    /**
     * 内部处理搜索请求的函数
     */
    private fun handleSearchInternal(query: String, engineKey: String, windowCountToUse: Int) {
        Log.d(TAG, "handleSearchInternal: query='$query', engineKey='$engineKey', windowCount=$windowCountToUse")
        // 清除现有WebView
        if (::webViewManager.isInitialized) {
            webViewManager.clearWebViews()
        } else {
            Log.e(TAG, "WebViewManager 未初始化，无法执行搜索！")
            return
        }
        
        // 创建新WebView并加载URL
        for (i in 0 until windowCountToUse) {
            val searchUrl = searchEngineHandler.getSearchUrl(query, engineKey)
            val webView = webViewManager.addWebView(searchUrl) // WebViewManager应该负责将WebView添加到容器中
            
            // 确保WebView启用了文本选择功能
            if(::textSelectionManager.isInitialized) {
                webView.setTextSelectionManager(textSelectionManager)
            }
        }
        
        // 重置滚动条
        windowManager.resetScrollPosition()
        
        // 延迟设置长按监听器
        handler.postDelayed({
            webViewManager.setupLongPressListeners()
        }, 1000) // 延迟1秒，确保WebView已经加载
    }

    /**
     * 公共方法，供外部 (如FloatingWindowManager) 调用以执行搜索
     */
    fun performSearch(query: String, engineKey: String? = null) {
        lastQuery = query
        lastEngineKey = engineKey ?: SearchEngineHandler.DEFAULT_ENGINE_KEY // 使用默认或指定的引擎
        Log.d(TAG, "performSearch called: query='$lastQuery', engineKey='$lastEngineKey', currentWindowCount=$currentWindowCount")
        handleSearchInternal(lastQuery!!, lastEngineKey!!, currentWindowCount)
    }


    /**
     * 切换 WebView 窗口数量并重新加载内容。
     * @return 返回新的窗口数量。
     */
    fun toggleAndReloadWindowCount(): Int {
        currentWindowCount++
        if (currentWindowCount > MAX_WINDOW_COUNT) {
            currentWindowCount = 1
        }
        Log.d(TAG, "窗口数量切换为: $currentWindowCount")

        // 使用最后一次的查询（或默认查询）和引擎重新加载WebViews
        val queryToUse = lastQuery ?: "" 
        val engineToUse = lastEngineKey ?: SearchEngineHandler.DEFAULT_ENGINE_KEY
        
        handleSearchInternal(queryToUse, engineToUse, currentWindowCount)
        
        return currentWindowCount
    }
    
    /**
     * 获取当前窗口数量
     */
    fun getCurrentWindowCount(): Int {
        return currentWindowCount
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