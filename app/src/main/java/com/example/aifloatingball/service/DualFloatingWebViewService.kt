package com.example.aifloatingball.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.example.aifloatingball.engine.SearchEngineHandler
import com.example.aifloatingball.notification.ServiceNotificationManager
import com.example.aifloatingball.ui.floating.FloatingWindowManager
import com.example.aifloatingball.ui.floating.WindowStateCallback
import com.example.aifloatingball.ui.text.TextSelectionManager
import com.example.aifloatingball.ui.webview.WebViewManager
import com.example.aifloatingball.utils.IntentParser
import com.example.aifloatingball.utils.SearchParams

/**
 * 双窗口浮动WebView服务，提供多窗口并行搜索功能
 */
class DualFloatingWebViewService : FloatingServiceBase(), WindowStateCallback {
    companion object {
        private const val TAG = "DualFloatingWebView"
        const val NOTIFICATION_ID = 2
        const val CHANNEL_ID = "DualFloatingWebViewChannel"
        
        @Volatile
        @JvmStatic
        var isRunning = false
        private const val MAX_WINDOW_COUNT = 3 // 最大窗口数量
        private const val DEFAULT_WINDOW_COUNT = 3 // 默认窗口数量

        // SharedPreferences keys
        const val PREFS_NAME = "DualFloatingWebViewPrefs"
        const val KEY_WINDOW_X = "window_x"
        const val KEY_WINDOW_Y = "window_y"
        const val KEY_WINDOW_WIDTH = "window_width"
        const val KEY_WINDOW_HEIGHT = "window_height"
        const val KEY_WINDOW_COUNT = "window_count"
    }

    // 依赖模块
    private lateinit var notificationManager: ServiceNotificationManager
    private lateinit var windowManager: FloatingWindowManager
    private lateinit var webViewManager: WebViewManager
    private lateinit var searchEngineHandler: SearchEngineHandler
    private lateinit var intentParser: IntentParser
    private lateinit var textSelectionManager: TextSelectionManager
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var sharedPreferences: SharedPreferences

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
        
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Load saved window count
        currentWindowCount = sharedPreferences.getInt(KEY_WINDOW_COUNT, DEFAULT_WINDOW_COUNT)

        // 初始化各模块
        initializeManagers()
        
        // 启动前台服务
        notificationManager.createNotificationChannel()
        startForeground(NOTIFICATION_ID, notificationManager.createNotification())
        
        // 创建浮动窗口
        windowManager.createFloatingWindow()

        // 在浮动窗口创建之后，并且XML中的WebView可用之后，初始化WebViewManager
        val xmlWebViews = windowManager.getXmlDefinedWebViews()
        Log.d(TAG, "获取到的XML定义的WebView数量: ${xmlWebViews.count { it != null }}")
        webViewManager = WebViewManager(this, xmlWebViews)
        textSelectionManager = webViewManager.textSelectionManager

        // 创建时，使用默认窗口数量加载一次
        // 确保此时 webViewManager 已经初始化
        handleSearchInternal(lastQuery ?: "", lastEngineKey ?: SearchEngineHandler.DEFAULT_ENGINE_KEY, currentWindowCount)
    }

    /**
     * 初始化各管理器模块
     */
    private fun initializeManagers() {
        Log.d(TAG, "初始化管理器")
        
        notificationManager = ServiceNotificationManager(this, CHANNEL_ID)
        // FloatingWindowManager 需要在 service 实例创建后，才能获取到更新后的 windowCountToggleText
        // 因此，其按钮的 UI 更新回调可能需要 service 准备好之后再设置，或者通过接口
        windowManager = FloatingWindowManager(this, this) 
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
        
        // 确保WebViewManager已经初始化 (现在它在onCreate中初始化)
        if (!::webViewManager.isInitialized) {
            Log.e(TAG, "WebViewManager在onStartCommand时仍未初始化! 这不应该发生。")
            // 尝试再次初始化，以防万一，但这通常表明onCreate的逻辑流程有问题
            val xmlWebViews = windowManager.getXmlDefinedWebViews()
            webViewManager = WebViewManager(this, xmlWebViews)
            textSelectionManager = webViewManager.textSelectionManager
        }
        
        // 设置WebView的长按监听器 (可以在这里进行，或者延迟)
        // handler.postDelayed({
        //     webViewManager.setupLongPressListeners()
        // }, 500) 
        // setupLongPressListeners现在应该在handleSearchInternal中调用，当WebView确实被使用时
        
        // 处理搜索意图
        intent?.let { 
            val searchParams = intentParser.parseSearchIntent(it)
            if (searchParams != null) {
                Log.d(TAG, "处理搜索请求: ${searchParams.query}")
                lastQuery = searchParams.query
                lastEngineKey = searchParams.engineKey
                // Intent中的windowCount可以被忽略，因为我们通过按钮控制
                handleSearchInternal(searchParams.query ?: "", searchParams.engineKey ?: SearchEngineHandler.DEFAULT_ENGINE_KEY, currentWindowCount)
            } else {
                 if (lastQuery == null) { // 避免重复加载
                    // 如果服务被系统重启等，可能没有intent，尝试加载默认/空白内容
                    Log.d(TAG, "Intent中无搜索参数，加载默认内容 (当前窗口数: $currentWindowCount)")
                    handleSearchInternal("", SearchEngineHandler.DEFAULT_ENGINE_KEY, currentWindowCount) 
                 }
            }
        } ?: run {
            // Intent 为 null 的情况 (例如服务被系统杀死后重启且没有 sticky intent)
            if (lastQuery == null) { // 避免重复加载
                Log.d(TAG, "Intent为null，加载默认内容 (当前窗口数: $currentWindowCount)")
                handleSearchInternal("", SearchEngineHandler.DEFAULT_ENGINE_KEY, currentWindowCount) 
            }
        }
        return START_NOT_STICKY
    }

    /**
     * 内部处理搜索请求的函数
     */
    private fun handleSearchInternal(query: String, engineKey: String, windowCountToUse: Int) {
        Log.d(TAG, "handleSearchInternal: query='$query', engineKey='$engineKey', windowCount=$windowCountToUse")
        
        if (!::webViewManager.isInitialized) {
            Log.e(TAG, "WebViewManager 未初始化，无法执行搜索！")
            return
        }
        
        // 根据 windowCountToUse 决定哪些 WebView 是活动的
        // 清空并隐藏多余的WebView
        webViewManager.clearAndHideWebViews(windowCountToUse)
        
        // 获取所有搜索URL
        val searchUrl = searchEngineHandler.getSearchUrl(query, engineKey)
        
        // 如果搜索URL为空或无效，记录日志并返回
        if (searchUrl.isBlank() || !searchUrl.startsWith("http")) {
            Log.e(TAG, "生成的搜索URL无效: '$searchUrl'")
            return
        }
        
        Log.d(TAG, "准备加载URL到 $windowCountToUse 个WebView: $searchUrl")
        
        for (i in 0 until windowCountToUse) {
            // 确保我们不会超出实际拥有的WebView数量 (最多3个)
            if (i < webViewManager.getWebViews().size) {
                Log.d(TAG, "处理WebView索引 $i")
                
                // 确保目标WebView可见
                webViewManager.setWebViewVisibility(i, true)
                
                // 使用不同搜索引擎，避免所有WebView加载相同内容
                val actualSearchUrl = when(i) {
                    0 -> searchUrl // 第一个WebView使用原始搜索URL
                    1 -> { // 第二个WebView使用不同搜索引擎
                        if (engineKey.contains("google")) {
                            searchEngineHandler.getSearchUrl(query, "baidu")
                        } else {
                            searchEngineHandler.getSearchUrl(query, "google")
                        }
                    }
                    2 -> { // 第三个WebView再使用不同搜索引擎
                        if (engineKey.contains("google") || engineKey.contains("baidu")) {
                            searchEngineHandler.getSearchUrl(query, "bing")
                        } else {
                            searchEngineHandler.getSearchUrl(query, "360")
                        }
                    }
                    else -> searchUrl
                }
                
                Log.d(TAG, "WebView索引 $i 将加载URL: $actualSearchUrl")
                
                // 加载URL到WebView
                val webView = webViewManager.loadUrlInWebView(i, actualSearchUrl)
                
                webView?.let {
                    // 确保WebView启用了文本选择功能
                    if(::textSelectionManager.isInitialized) {
                        it.setTextSelectionManager(textSelectionManager)
                    } else {
                        Log.w(TAG, "TextSelectionManager未初始化，无法为WebView ${it.id} 设置")
                    }
                } ?: Log.e(TAG, "尝试加载URL到索引 $i 的WebView失败，该WebView为null")
            } else {
                Log.w(TAG, "请求的窗口数量 ($windowCountToUse) 超过了XML中定义的WebView数量 (${webViewManager.getWebViews().size})，索引 $i 将被跳过。")
            }
        }
        
        // 重置滚动条 (如果适用)
        windowManager.resetScrollPosition()
        
        // 设置长按监听器给当前活动的WebViews
        handler.postDelayed({
            webViewManager.setupLongPressListeners() // 这会给所有当前的 webViews 设置监听
        }, 1000) 
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
        if (currentWindowCount > MAX_WINDOW_COUNT || currentWindowCount > webViewManager.getWebViews().size) { // 也不能超过实际XML定义的数量
            currentWindowCount = 1
        }
        Log.d(TAG, "窗口数量切换为: $currentWindowCount")
        saveWindowCount(currentWindowCount) // Save new window count

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
        
        // Save current window state before destroying
        windowManager.params?.let {
            saveWindowState(it.x, it.y, it.width, it.height)
        }
        saveWindowCount(currentWindowCount)

        isRunning = false
        if (::webViewManager.isInitialized) {
            webViewManager.destroyAll()
        }
        if (::windowManager.isInitialized) {
            windowManager.removeFloatingWindow()
        }
    }

    // WindowStateCallback implementation
    override fun onWindowStateChanged(x: Int, y: Int, width: Int, height: Int) {
        saveWindowState(x, y, width, height)
    }

    private fun saveWindowState(x: Int, y: Int, width: Int, height: Int) {
        Log.d(TAG, "保存窗口状态: X=$x, Y=$y, Width=$width, Height=$height")
        with(sharedPreferences.edit()) {
            putInt(KEY_WINDOW_X, x)
            putInt(KEY_WINDOW_Y, y)
            putInt(KEY_WINDOW_WIDTH, width)
            putInt(KEY_WINDOW_HEIGHT, height)
            apply()
        }
    }

    private fun saveWindowCount(count: Int) {
        Log.d(TAG, "保存窗口数量: $count")
        with(sharedPreferences.edit()) {
            putInt(KEY_WINDOW_COUNT, count)
            apply()
        }
    }
} 