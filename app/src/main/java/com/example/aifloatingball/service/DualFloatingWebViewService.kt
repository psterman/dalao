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
import com.example.aifloatingball.database.AppDatabase
import com.example.aifloatingball.model.AISearchEngine
import android.widget.ImageView
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import com.example.aifloatingball.model.SearchHistory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

    // 需要自定义UI的聊天引擎列表
    private val CHAT_UI_ENGINES = listOf("deepseek", "deepseek_chat", "chatgpt", "chatgpt_chat")

    // 依赖模块
    private lateinit var notificationManager: ServiceNotificationManager
    internal lateinit var windowManager: FloatingWindowManager
    lateinit var webViewManager: WebViewManager
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

    // --- Search History Fields ---
    private var searchStartTime: Long = 0
    private var searchSource: String? = null
    // --- End Search History Fields ---

    internal fun isWebViewManagerInitialized(): Boolean = ::webViewManager.isInitialized
    internal fun isSearchEngineHandlerInitialized(): Boolean = ::searchEngineHandler.isInitialized

    /**
     * 服务创建时初始化
     */
    override fun onCreate() {
        super.onCreate()
        isRunning = true
        
        // --- Search History ---
        searchStartTime = System.currentTimeMillis()
        // --- End Search History ---
        
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

        windowManager.floatingView?.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_OUTSIDE) {
                Log.d(TAG, "Outside touch detected, stopping service.")
                this.stopSelf()
                true
            } else {
                false
            }
        }

        // 延迟初始化，确保UI已经准备就绪
        handler.postDelayed({
            val xmlWebViews = windowManager.getXmlDefinedWebViews()
            Log.d(TAG, "获取到的XML定义的WebView数量: ${xmlWebViews.count { it != null }}")
            if (xmlWebViews.any { it != null }) {
                webViewManager = WebViewManager(this, xmlWebViews, windowManager)
                textSelectionManager = webViewManager.textSelectionManager

                // 创建时不再主动加载任何内容。
                // 所有加载逻辑都将由 onStartCommand 及其处理的 Intent 触发。
                // 这可以从根本上解决 onCreate 和 onStartCommand 之间的竞态条件。
                Log.d(TAG, "WebViewManager 已初始化，等待 onStartCommand 指令。")
            } else {
                Log.e(TAG, "错误: XML中的WebView未找到，无法初始化WebViewManager。")
                stopSelf() // 如果关键视图找不到，停止服务
            }
        }, 100) // 延迟100毫秒
        
        // 注册设置变更监听器
        settingsManager.registerOnSettingChangeListener<String>("left_window_search_engine") { _, value ->
            Log.d(TAG, "左侧窗口引擎变更为: $value")
            // 仅重新加载第一个WebView
            performSearchInWebView(0, lastQuery ?: "", value)
        }
        settingsManager.registerOnSettingChangeListener<String>("center_window_search_engine") { _, value ->
            Log.d(TAG, "中间窗口引擎变更为: $value")
            // 仅重新加载第二个WebView
            performSearchInWebView(1, lastQuery ?: "", value)
        }
        settingsManager.registerOnSettingChangeListener<String>("right_window_search_engine") { _, value ->
            Log.d(TAG, "右侧窗口引擎变更为: $value")
            // 仅重新加载第三个WebView
            performSearchInWebView(2, lastQuery ?: "", value)
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
        
        // 提前创建 WebViewManager 和 TextSelectionManager
        val tempWebViewFactory = com.example.aifloatingball.ui.webview.WebViewFactory(this)
        textSelectionManager = tempWebViewFactory.textSelectionManager
        
        windowManager = FloatingWindowManager(this, this, textSelectionManager)
        settingsManager = SettingsManager.getInstance(this)
        searchEngineHandler = SearchEngineHandler(settingsManager)
        intentParser = IntentParser()
        chatManager = ChatManager(this)
        
        // 移除此处过早的初始化，这是导致崩溃的根源
        // 正确的初始化在onCreate的postDelayed中进行
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
            // 延迟处理意图，确保UI和管理器已准备就绪。
            handler.postDelayed({
                intent?.let { handleSearchIntent(it) }
            }, 200) // 稍微长一点的延迟，以确保onCreate的延迟已经执行
        } else {
            // 如果已经初始化，则立即处理
            intent?.let { handleSearchIntent(it) }
        }
        
        // 启用输入的逻辑已移至 handleSearchIntent, 以避免焦点问题
        // updateWindowParameters(true)
        
        return START_STICKY
    }

    private fun handleSearchIntent(intent: Intent) {
        val searchParams = intentParser.parseSearchIntent(intent)
        if (searchParams != null) {
            // --- Search History ---
            // 来源信息在第一次启动服务时被设置
            if (searchSource == null) {
                searchSource = intent.getStringExtra("search_source") ?: "浏览器内部"
            }
            // --- End Search History ---

            // 这是一个来自外部的、明确的新搜索请求，执行它。
            Log.d(TAG, "处理来自intent的新搜索: ${searchParams.query}")
            performSearch(searchParams.query, searchParams.engineKey)
        } else {
            // 如果 intent 中没有搜索参数，这可能是服务的首次启动（例如，从快捷方式），
            // 并且还没有执行过任何搜索。
            if (lastQuery == null) {
                Log.d(TAG, "Intent中无搜索参数，执行首次默认加载。")
                // 执行一个空的搜索，这将加载默认搜索引擎的主页
                performSearch("", settingsManager.getSearchEngineForPosition(0))
            } else {
                Log.d(TAG, "Intent中无搜索参数，但已有内容，忽略。")
            }
        }
        // 在处理完所有搜索逻辑、WebView开始加载后再启用输入，避免焦点冲突
        updateWindowParameters(true)
    }

    private fun handleSearchInternal(query: String, engineKey: String, windowCount: Int) {
        Log.d(TAG, "处理搜索请求: query='$query', engineKey='$engineKey', windowCount=$windowCount")
        
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
                    settingsManager.getSearchEngineForPosition(i)
                }
                // 使用统一的加载方法
                loadContentInWebView(it, currentWindowEngineKey, query)
            } ?: Log.e(TAG, "尝试加载URL到索引 $i 的WebView失败，该WebView为null")
        }
        
        // 重置滚动条
        windowManager.resetScrollPosition()
    }

    /**
     * 公共方法，供外部 (如FloatingWindowManager) 调用以执行搜索
     */
    fun performSearch(query: String, engineKey: String? = null) {
        // 新增：将搜索词设置到窗口管理器的输入框中，以更新UI
        windowManager.setSearchInputText(query)

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
        if (!::webViewManager.isInitialized) {
            Log.w(TAG, "performSearchInWebView called before WebViewManager was initialized. Ignoring.")
            return
        }
        Log.d(TAG, "performSearchInWebView: index=$webViewIndex, query='$query', engineKey='$engineKey'")
        val webView = webViewManager.getWebViews().getOrNull(webViewIndex)
        if (webView != null) {
            // 使用统一的加载方法
            loadContentInWebView(webView, engineKey, query)
        } else {
            Log.e(TAG, "在 performSearchInWebView 中找不到索引为 $webViewIndex 的WebView")
        }
    }

    /**
     * 新增：统一的WebView内容加载器，这是所有加载逻辑的核心。
     */
    private fun loadContentInWebView(webView: CustomWebView, engineKey: String, query: String) {
        // 确保WebView启用了文本选择功能
        if (::textSelectionManager.isInitialized) {
            webView.setTextSelectionManager(textSelectionManager)
        } else {
            Log.w(TAG, "TextSelectionManager未初始化，无法为WebView ${webView.id} 设置")
        }

        // 1. 修复：使用更灵活的检查来识别聊天引擎，兼容显示名称和内部键
        val isChatEngine = CHAT_UI_ENGINES.any { engineKey.equals(it, ignoreCase = true) } ||
                engineKey.contains("deepseek", ignoreCase = true) ||
                engineKey.contains("chatgpt", ignoreCase = true)

        if (isChatEngine) {
            Log.d(TAG, "检测到聊天引擎 '$engineKey'，使用ChatManager初始化")
            chatManager.initWebView(webView, engineKey, query)
            return // 由ChatManager处理，直接返回
        }

        // 2. 如果不是聊天引擎，则继续正常的URL加载
        val urlToLoad = if (query.isBlank()) {
            // 如果查询为空，加载引擎的主页
            if (isAIEngine(engineKey)) {
                EngineUtil.getAISearchEngineHomeUrl(engineKey)
            } else {
                EngineUtil.getSearchEngineHomeUrl(engineKey)
            }
        } else {
            // 否则，执行搜索
            searchEngineHandler.getSearchUrl(query, engineKey)
        }

        // 3. 验证并加载URL，如果URL无效，则假定它是一个应由ChatManager处理的引擎
        if (urlToLoad.isNotBlank() && urlToLoad.startsWith("http")) {
            webView.loadUrl(urlToLoad)
            } else {
            Log.w(TAG, "生成的URL无效 ('$urlToLoad') 或引擎未找到 ('$engineKey')，尝试作为聊天引擎加载。")
            chatManager.initWebView(webView, engineKey, query)
        }
    }
    
    /**
     * 判断是否是AI搜索引擎
     */
    private fun isAIEngine(engineKey: String): Boolean {
        val aiEngineKeys = listOf("chatgpt", "chatgpt_chat", "claude", "gemini", "wenxin", "chatglm",
                                   "qianwen", "xinghuo", "perplexity", "phind", "poe", "deepseek", "deepseek_chat")
        return aiEngineKeys.any { it.equals(engineKey, ignoreCase = true) }
    }

    /**
     * 切换 WebView 窗口数量并重新加载内容。
     * @return 返回新的窗口数量。
     */
    fun toggleAndReloadWindowCount(): Int {
        if (!::webViewManager.isInitialized) {
            Log.w(TAG, "toggleAndReloadWindowCount called before WebViewManager was initialized. Ignoring.")
            return currentWindowCount
        }
        currentWindowCount++
        if (currentWindowCount > MAX_WINDOW_COUNT || currentWindowCount > webViewManager.getWebViews().size) { // 也不能超过实际XML定义的数量
            currentWindowCount = 1
        }
        Log.d(TAG, "窗口数量切换为: $currentWindowCount")
        saveWindowCount(currentWindowCount) // Save new window count

        val queryToUse = lastQuery ?: "" 
        val engineToUse = lastEngineKey ?: settingsManager.getSearchEngineForPosition(0)
        
        handleSearchInternal(queryToUse, engineToUse, currentWindowCount)
        
        // 移除延迟请求焦点的逻辑，因为这可能导致输入法闪烁问题
        // handler.postDelayed({
        //     try {
        //         val searchInput = windowManager.floatingView?.findViewById<EditText>(R.id.dual_search_input)
        //         searchInput?.requestFocus()
        //         Log.d(TAG, "窗口数量切换后，尝试重新聚焦搜索输入框")
        //     } catch (e: Exception) {
        //         Log.e(TAG, "切换窗口数量后聚焦输入框失败: ${e.message}")
        //     }
        // }, 300) // 延迟300毫秒
        
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

        // --- Search History ---
        saveSearchHistory()
        // --- End Search History ---

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

    private fun saveSearchHistory() {
        if (lastQuery.isNullOrBlank()) {
            Log.d(TAG, "查询为空，不保存历史记录。")
            return
        }

        val duration = System.currentTimeMillis() - searchStartTime
        
        // 修正：准确记录实际使用的搜索引擎，而不是只记录默认设置。
        // 此逻辑与 handleSearchInternal 中为WebView分配引擎的逻辑保持一致。
        val enginesList = (0 until currentWindowCount).map { i ->
            if (i == 0) {
                // 第一个窗口使用触发搜索的引擎 (lastEngineKey)，如果为空则回退到其默认引擎。
                lastEngineKey ?: settingsManager.getSearchEngineForPosition(0)
            } else {
                // 其他窗口使用它们各自的默认引擎。
                settingsManager.getSearchEngineForPosition(i)
            }
        }
        val engines = enginesList.distinct().joinToString(", ")

        val historyEntry = SearchHistory(
            query = lastQuery!!,
            timestamp = searchStartTime,
            source = searchSource ?: "未知",
            durationInMillis = duration,
            engines = engines
        )

        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppDatabase.getDatabase(applicationContext).searchHistoryDao().insert(historyEntry)
                Log.d(TAG, "搜索历史已保存: $historyEntry")
            } catch (e: Exception) {
                Log.e(TAG, "保存搜索历史失败", e)
            }
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