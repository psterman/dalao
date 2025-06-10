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
import android.view.View
import android.graphics.Rect
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
import com.example.aifloatingball.manager.ChatManager
import com.example.aifloatingball.ui.webview.CustomWebView
import android.view.inputmethod.InputMethodManager
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.model.AISearchEngine
import android.widget.ImageView
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator

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
    internal lateinit var windowManager: FloatingWindowManager
    internal lateinit var webViewManager: WebViewManager
    internal lateinit var searchEngineHandler: SearchEngineHandler
    private lateinit var intentParser: IntentParser
    private lateinit var textSelectionManager: TextSelectionManager
    private val handler = Handler(Looper.getMainLooper())
    private val menuAutoHideHandler = Handler(Looper.getMainLooper())
    private lateinit var sharedPreferences: SharedPreferences
    internal lateinit var chatManager: ChatManager
    internal lateinit var settingsManager: SettingsManager
    
    // 添加 floatingView 属性
    val floatingView: View?
        get() = windowManager.floatingView

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
    private var originalY = 0
    private var isKeyboardVisible = false
    private var keyboardHeight = 0

    internal fun isWebViewManagerInitialized(): Boolean = ::webViewManager.isInitialized
    internal fun isSearchEngineHandlerInitialized(): Boolean = ::searchEngineHandler.isInitialized

    /**
     * 服务创建时初始化
     */
    override fun onCreate() {
        super.onCreate()
        isRunning = true
        
        Log.d(TAG, "DualFloatingWebViewService: 服务创建 onCreate()")
        
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

        // 延迟初始化，确保UI已经准备就绪
        handler.postDelayed({
            val xmlWebViews = windowManager.getXmlDefinedWebViews()
            Log.d(TAG, "获取到的XML定义的WebView数量: ${xmlWebViews.count { it != null }}")
            if (xmlWebViews.any { it != null }) {
                webViewManager = WebViewManager(this, xmlWebViews, windowManager)
                textSelectionManager = webViewManager.textSelectionManager

                // 创建时，使用默认窗口数量加载一次
                handleSearchInternal(lastQuery ?: "", lastEngineKey ?: SearchEngineHandler.DEFAULT_ENGINE_KEY, currentWindowCount)
            } else {
                Log.e(TAG, "错误: XML中的WebView未找到，无法初始化WebViewManager。")
                stopSelf() // 如果关键视图找不到，停止服务
            }
        }, 100) // 延迟100毫秒
        
        // 注册设置变更监听器
        settingsManager.registerOnSettingChangeListener<String>("left_window_search_engine") { key, value ->
            Log.d(TAG, "设置变更: $key = $value")
            // 重新加载第一个WebView
            handleSearchInternal(lastQuery ?: "", settingsManager.getSearchEngineForPosition(0), currentWindowCount)
        }
        settingsManager.registerOnSettingChangeListener<String>("center_window_search_engine") { key, value ->
            Log.d(TAG, "设置变更: $key = $value")
            // 重新加载第二个WebView
            handleSearchInternal(lastQuery ?: "", settingsManager.getSearchEngineForPosition(1), currentWindowCount)
        }
        settingsManager.registerOnSettingChangeListener<String>("right_window_search_engine") { key, value ->
            Log.d(TAG, "设置变更: $key = $value")
            // 重新加载第三个WebView
            handleSearchInternal(lastQuery ?: "", settingsManager.getSearchEngineForPosition(2), currentWindowCount)
        }
        
        // 注册广播接收器
        registerBroadcastReceiver()
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
        handler.post {
            try {
                // 调用windowManager的方法刷新搜索引擎图标
                windowManager.refreshEngineIcons()
                Log.d(TAG, "已在主线程刷新搜索引擎图标")
            } catch (e: Exception) {
                Log.e(TAG, "刷新搜索引擎图标失败", e)
            }
        }
    }

    /**
     * 初始化各管理器模块
     */
    private fun initializeManagers() {
        Log.d(TAG, "初始化管理器")
        
        notificationManager = ServiceNotificationManager(
            this,
            CHANNEL_ID,
            "Dual Floating WebView Service",
            "Keeps the floating webview service running"
        )
        windowManager = FloatingWindowManager(this, this)
        searchEngineHandler = SearchEngineHandler()
        intentParser = IntentParser()
        chatManager = ChatManager(this)
        settingsManager = SettingsManager.getInstance(this)
        
        // 初始化WebViewManager
        val xmlWebViews = windowManager.getXmlDefinedWebViews()
        webViewManager = WebViewManager(this, xmlWebViews, windowManager)
        textSelectionManager = webViewManager.textSelectionManager
        
        // 设置窗口参数
        updateWindowParameters(true)
    }

    private fun updateWindowParameters(enableInput: Boolean) {
        try {
            // 将窗口参数的更新委托给 FloatingWindowManager
            windowManager.setFloatingWindowFocusable(enableInput)
            Log.d(TAG, "已通过 FloatingWindowManager 更新窗口参数: enableInput=$enableInput")
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
            // 在这种情况下，我们依赖onCreate中的postDelayed来初始化。
            // 延迟处理意图，以确保UI和管理器已准备就绪。
            handler.postDelayed({
                intent?.let { handleSearchIntent(it) }
            }, 200) // 稍微长一点的延迟，以确保onCreate的延迟已经执行
        } else {
            // 如果已经初始化，则立即处理
            intent?.let { handleSearchIntent(it) }
        }
        
        // 启用输入
        updateWindowParameters(true)
        
        return START_STICKY
    }

    private fun handleSearchIntent(intent: Intent) {
        val searchParams = intentParser.parseSearchIntent(intent)
        if (searchParams != null) {
            Log.d(TAG, "处理搜索请求 from intent: ${searchParams.query}")
            // 委托给 performSearch 处理，它有正确的引擎回退逻辑
            performSearch(searchParams.query, searchParams.engineKey)
        } else {
            if (lastQuery == null) {
                Log.d(TAG, "Intent中无搜索参数，加载默认内容 (当前窗口数: $currentWindowCount)")
                // 使用 performSearch 来确保使用正确的默认引擎加载主页
                performSearch("", null)
            }
        }
    }

    private fun handleSearchInternal(query: String, engineKey: String, windowCount: Int) {
        Log.d(TAG, "处理搜索请求: query='$query', engineKey='$engineKey', windowCount=$windowCount")
        
        // 启用输入 (此处不再每次搜索都调用，由onStartCommand或明确操作控制)
        // updateWindowParameters(true)
        
        // 获取搜索URL
        val searchUrl = searchEngineHandler.getSearchUrl(query, engineKey)
        
        if (searchUrl.isBlank() || !searchUrl.startsWith("http")) {
            Log.e(TAG, "无法获取搜索URL: engineKey='$engineKey'")
            return
        }
        
        // 确定要使用的窗口数量
        if (webViewManager.getWebViews().isEmpty()) {
            Log.e(TAG, "无法确定要使用的窗口数量，因为没有可用的 WebView。")
            // 可以在这里尝试重新初始化或延迟，但现在只返回以防止崩溃
            return 
        }
        val windowCountToUse = windowCount.coerceIn(1, webViewManager.getWebViews().size)
        currentWindowCount = windowCountToUse

        // 加载URL到WebView
        for (i in 0 until windowCountToUse) {
            val webView = webViewManager.getWebViews().getOrNull(i)
            webView?.let {
                // 获取该窗口位置对应的搜索引擎
                val currentWindowEngineKey = if (i == 0) {
                    engineKey // 点击的引擎或左侧默认引擎优先用于第一个窗口
                } else {
                    SettingsManager.getInstance(this@DualFloatingWebViewService).getSearchEngineForPosition(i)
                }
                
                // 根据该搜索引擎和查询，获取新的搜索URL
                val currentWindowSearchUrl = if (query.isBlank()) {
                    if (isAIEngine(currentWindowEngineKey)) {
                        EngineUtil.getAISearchEngineHomeUrl(currentWindowEngineKey)
                    } else {
                        EngineUtil.getSearchEngineHomeUrl(currentWindowEngineKey)
                    }
                } else {
                    searchEngineHandler.getSearchUrl(query, currentWindowEngineKey)
                }

                if (currentWindowSearchUrl.isBlank() || !currentWindowSearchUrl.startsWith("http")) {
                    Log.e(TAG, "无法获取窗口 $i 的搜索URL: engineKey='$currentWindowEngineKey'")
                    return@let // 继续处理下一个WebView
                }

                // 确保WebView启用了文本选择功能
                if(::textSelectionManager.isInitialized) {
                    it.setTextSelectionManager(textSelectionManager)
                } else {
                    Log.w(TAG, "TextSelectionManager未初始化，无法为WebView ${it.id} 设置")
                }
                
                // 如果是DeepSeek聊天，需要进行特殊处理，确保输入法能正常工作
                if (currentWindowEngineKey == "deepseek" || currentWindowEngineKey == "deepseek_chat" || currentWindowEngineKey == "chatgpt" || currentWindowEngineKey == "chatgpt_chat") {
                    Log.d(TAG, "初始化聊天界面 for WebView $i")
                    // 重置WebView状态
                    try {
                        it.stopLoading()
                        it.clearHistory()
                        it.clearCache(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "重置WebView $i 状态失败: ${e.message}")
                    }
                    
                    chatManager.initWebView(it, currentWindowEngineKey, query) // 初始化聊天界面并传递查询
                    
                    // 让WebView获取焦点并显示输入法 (仅对第一个WebView)
                    if (i == 0) { // 仅对第一个窗口处理输入法
                        it.isFocusable = true
                        it.isFocusableInTouchMode = true
                        it.requestFocus()
                        Handler(Looper.getMainLooper()).postDelayed({
                            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                            imm?.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT)
                        }, 1000)
                    }
                } else { // 非聊天模式，直接加载URL
                    it.loadUrl(currentWindowSearchUrl)
                }
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
        // 如果没有指定引擎（例如，通过点击搜索按钮），则使用为第一个窗口位置设置的默认引擎
        val primaryEngine = engineKey ?: settingsManager.getSearchEngineForPosition(0)
        lastEngineKey = primaryEngine
        Log.d(TAG, "performSearch called: query='$query', primaryEngine='$primaryEngine', currentWindowCount=$currentWindowCount")
        handleSearchInternal(query, primaryEngine, currentWindowCount)
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
        webViewManager.getWebViews()[webViewIndex].let {
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
        return engineKey in listOf("chatgpt", "chatgpt_chat", "claude", "gemini", "wenxin", "chatglm", 
                                   "qianwen", "xinghuo", "perplexity", "phind", "poe", "deepseek", "deepseek_chat")
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
        
        // 延迟请求焦点，确保UI更新完成，避免输入法闪烁
        handler.postDelayed({
            try {
                val searchInput = windowManager.floatingView?.findViewById<EditText>(R.id.dual_search_input)
                searchInput?.requestFocus()
                Log.d(TAG, "窗口数量切换后，尝试重新聚焦搜索输入框")
            } catch (e: Exception) {
                Log.e(TAG, "切换窗口数量后聚焦输入框失败: ${e.message}")
            }
        }, 300) // 延迟300毫秒
        
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
        isRunning = false
        
        // 取消注册广播接收器
        try {
            unregisterReceiver(broadcastReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "广播接收器未注册或已被取消注册。")
        }

        // 移除所有窗口
        if (::windowManager.isInitialized) {
            windowManager.removeFloatingWindow()
        }

        // 停止前台服务
        stopForeground(true)
        
        // 移除所有 Handler 回调
        handler.removeCallbacksAndMessages(null)
        
        Log.d(TAG, "DualFloatingWebViewService: 服务销毁 onDestroy()")
    }

    // WindowStateCallback implementation
    override fun onWindowStateChanged(x: Int, y: Int, width: Int, height: Int) {
        saveWindowState(x, y, width, height)
    }

    // 处理WebView焦点变化
    fun onWebViewFocusChanged(focusable: Boolean) {
        Log.d(TAG, "WebView焦点变化: focusable=$focusable")
        windowManager.setFloatingWindowFocusable(focusable)
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

    /**
     * 为指定位置的WebView执行搜索
     */
    fun performSearchForPosition(query: String, engineKey: String, position: Int) {
        if (!::webViewManager.isInitialized) {
            Log.e(TAG, "WebViewManager not initialized, cannot perform search for position.")
            return
        }

        val webViewId = when (position) {
            0 -> R.id.first_floating_webview
            1 -> R.id.second_floating_webview
            2 -> R.id.third_floating_webview
            else -> -1
        }

        if (webViewId != -1) {
            val webView = windowManager.floatingView?.findViewById<CustomWebView>(webViewId)
            webView?.let {
                val url = searchEngineHandler.getSearchUrl(engineKey, query)
                it.loadUrl(url)
                Log.d(TAG, "Position $position search: engine=$engineKey, query=$query, url=$url")
            }
        } else {
            Log.e(TAG, "Invalid position $position, cannot perform search.")
        }
    }
} 