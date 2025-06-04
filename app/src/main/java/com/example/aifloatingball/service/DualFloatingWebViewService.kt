package com.example.aifloatingball.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import com.example.aifloatingball.R
import com.example.aifloatingball.engine.SearchEngineHandler
import com.example.aifloatingball.notification.ServiceNotificationManager
import com.example.aifloatingball.ui.floating.FloatingWindowManager
import com.example.aifloatingball.ui.floating.WindowStateCallback
import com.example.aifloatingball.ui.text.TextSelectionManager
import com.example.aifloatingball.ui.webview.WebViewManager
import com.example.aifloatingball.utils.IntentParser
import com.example.aifloatingball.utils.SearchParams
import com.example.aifloatingball.utils.EngineUtil

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
        
        // 广播动作
        const val ACTION_UPDATE_AI_ENGINES = "com.example.aifloatingball.ACTION_UPDATE_AI_ENGINES"
        const val ACTION_UPDATE_MENU = "com.example.aifloatingball.ACTION_UPDATE_MENU"
    }

    // 依赖模块
    private lateinit var notificationManager: ServiceNotificationManager
    private lateinit var windowManager: FloatingWindowManager
    internal lateinit var webViewManager: WebViewManager
    internal lateinit var searchEngineHandler: SearchEngineHandler
    private lateinit var intentParser: IntentParser
    private lateinit var textSelectionManager: TextSelectionManager
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var sharedPreferences: SharedPreferences
    
    // 广播接收器
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_UPDATE_AI_ENGINES, ACTION_UPDATE_MENU -> {
                    // 重新加载WebView的搜索引擎图标
                    refreshSearchEngineIcons()
                }
            }
        }
    }

    private var currentWindowCount = DEFAULT_WINDOW_COUNT
    private var lastQuery: String? = null
    private var lastEngineKey: String? = null

    internal fun isWebViewManagerInitialized(): Boolean = ::webViewManager.isInitialized
    internal fun isSearchEngineHandlerInitialized(): Boolean = ::searchEngineHandler.isInitialized

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

        // 注册广播接收器
        registerBroadcastReceiver()
        
        // 创建时，使用默认窗口数量加载一次
        // 确保此时 webViewManager 已经初始化
        handleSearchInternal(lastQuery ?: "", lastEngineKey ?: SearchEngineHandler.DEFAULT_ENGINE_KEY, currentWindowCount)
    }

    /**
     * 注册广播接收器
     */
    private fun registerBroadcastReceiver() {
        val intentFilter = IntentFilter().apply {
            addAction(ACTION_UPDATE_AI_ENGINES)
            addAction(ACTION_UPDATE_MENU)
        }
        registerReceiver(broadcastReceiver, intentFilter)
    }
    
    /**
     * 刷新所有WebView的搜索引擎图标
     */
    private fun refreshSearchEngineIcons() {
        try {
            // 调用windowManager的方法刷新搜索引擎图标
            windowManager.refreshEngineIcons()
            Log.d(TAG, "已刷新搜索引擎图标")
        } catch (e: Exception) {
            Log.e(TAG, "刷新搜索引擎图标失败", e)
        }
    }

    /**
     * 初始化各管理器模块
     */
    private fun initializeManagers() {
        Log.d(TAG, "初始化管理器")
        
        notificationManager = ServiceNotificationManager(this, CHANNEL_ID)
        windowManager = FloatingWindowManager(this, this)
        searchEngineHandler = SearchEngineHandler()
        intentParser = IntentParser()
        
        // 初始化WebViewManager
        val xmlWebViews = windowManager.getXmlDefinedWebViews()
        webViewManager = WebViewManager(this, xmlWebViews)
        textSelectionManager = webViewManager.textSelectionManager
        
        // 设置窗口参数
        updateWindowParameters(true)
    }

    private fun updateWindowParameters(enableInput: Boolean) {
        try {
            val floatingView = windowManager.floatingView ?: return
            val params = floatingView.layoutParams as? WindowManager.LayoutParams ?: return
            
            if (enableInput) {
                // 移除所有阻止输入的标志
                params.flags = params.flags and (
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                ).inv()
                
                // 设置输入法模式
                params.softInputMode = (
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_IS_FORWARD_NAVIGATION
                )
            } else {
                // 添加阻止获取焦点的标志
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                // 重置输入法模式
                params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            }
            
            try {
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                wm.updateViewLayout(floatingView, params)
                Log.d(TAG, "已更新窗口参数: enableInput=$enableInput")
            } catch (e: Exception) {
                Log.e(TAG, "更新窗口布局失败", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新窗口参数失败", e)
        }
    }

    /**
     * 处理服务命令
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "处理服务命令")
        
        // 确保WebViewManager已经初始化
        if (!::webViewManager.isInitialized) {
            Log.e(TAG, "WebViewManager在onStartCommand时仍未初始化! 这不应该发生。")
            val xmlWebViews = windowManager.getXmlDefinedWebViews()
            webViewManager = WebViewManager(this, xmlWebViews)
            textSelectionManager = webViewManager.textSelectionManager
        }
        
        // 处理搜索意图
        intent?.let { handleSearchIntent(it) }
        
        // 启用输入
        updateWindowParameters(true)
        
        return START_STICKY
    }

    private fun handleSearchIntent(intent: Intent) {
        val searchParams = intentParser.parseSearchIntent(intent)
        if (searchParams != null) {
            Log.d(TAG, "处理搜索请求: ${searchParams.query}")
            lastQuery = searchParams.query
            lastEngineKey = searchParams.engineKey
            handleSearchInternal(searchParams.query ?: "", searchParams.engineKey ?: SearchEngineHandler.DEFAULT_ENGINE_KEY, currentWindowCount)
        } else {
            if (lastQuery == null) {
                Log.d(TAG, "Intent中无搜索参数，加载默认内容 (当前窗口数: $currentWindowCount)")
                handleSearchInternal("", SearchEngineHandler.DEFAULT_ENGINE_KEY, currentWindowCount)
            }
        }
    }

    private fun handleSearchInternal(query: String, engineKey: String, windowCount: Int) {
        Log.d(TAG, "处理搜索请求: query='$query', engineKey='$engineKey', windowCount=$windowCount")
        
        // 启用输入
        updateWindowParameters(true)
        
        // 获取搜索URL
        val searchUrl = searchEngineHandler.getSearchUrl(query, engineKey)
        
        if (searchUrl.isBlank() || !searchUrl.startsWith("http")) {
            Log.e(TAG, "无法获取搜索URL: engineKey='$engineKey'")
            return
        }
        
        // 确定要使用的窗口数量
        val windowCountToUse = windowCount.coerceIn(1, webViewManager.getWebViews().size)
        currentWindowCount = windowCountToUse
        
        // 加载URL到WebView
        for (i in 0 until windowCountToUse) {
            val webView = webViewManager.getWebViews().getOrNull(i)
            webView?.let {
                // 确保WebView启用了文本选择功能
                if(::textSelectionManager.isInitialized) {
                    it.setTextSelectionManager(textSelectionManager)
                } else {
                    Log.w(TAG, "TextSelectionManager未初始化，无法为WebView ${it.id} 设置")
                }
                
                // 加载URL
                it.loadUrl(searchUrl)
            } ?: Log.e(TAG, "尝试加载URL到索引 $i 的WebView失败，该WebView为null")
        }
        
        // 重置滚动条
        windowManager.resetScrollPosition()
        
        // 设置长按监听器
        handler.postDelayed({
            webViewManager.setupLongPressListeners()
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
     * 在指定WebView中执行搜索
     * @param webViewIndex 要加载的WebView索引
     * @param query 搜索查询字符串
     * @param engineKey 搜索引擎键
     */
    fun performSearchInWebView(webViewIndex: Int, query: String, engineKey: String) {
        Log.d(TAG, "performSearchInWebView: index=$webViewIndex, query='$query', engineKey='$engineKey'")
        if (!::webViewManager.isInitialized || !::searchEngineHandler.isInitialized) {
            Log.e(TAG, "WebViewManager或SearchEngineHandler未初始化，无法执行WebView搜索！")
            return
        }

        val urlToLoad = if (query.isBlank()) {
            // 如果查询为空，加载引擎的首页
            if (isAIEngine(engineKey)) {
                EngineUtil.getAISearchEngineHomeUrl(engineKey)
            } else {
                EngineUtil.getSearchEngineHomeUrl(engineKey)
            }
        } else {
            // 否则，执行搜索
            searchEngineHandler.getSearchUrl(query, engineKey)
        }

        if (urlToLoad.isBlank() || !urlToLoad.startsWith("http")) {
            Log.e(TAG, "生成的URL无效: '$urlToLoad' for engine: $engineKey")
            return
        }
        
        webViewManager.loadUrlInWebView(webViewIndex, urlToLoad)
        webViewManager.getWebViews()[webViewIndex]?.let {
            if(::textSelectionManager.isInitialized) {
                it.setTextSelectionManager(textSelectionManager)
            } else {
                Log.w(TAG, "TextSelectionManager未初始化，无法为WebView ${it.id} 设置")
            }
        }
    }
    
    /**
     * 判断是否是AI搜索引擎
     */
    private fun isAIEngine(engineKey: String): Boolean {
        return engineKey in listOf("chatgpt", "claude", "gemini", "wenxin", "chatglm", 
                                   "qianwen", "xinghuo", "perplexity", "phind", "poe")
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

        // 注销广播接收器
        try {
            unregisterReceiver(broadcastReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "注销广播接收器失败", e)
        }

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