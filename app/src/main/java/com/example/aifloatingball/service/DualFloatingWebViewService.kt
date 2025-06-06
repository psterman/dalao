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
    private lateinit var sharedPreferences: SharedPreferences
    internal lateinit var chatManager: ChatManager
    
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

        // 设置输入法监听
        setupKeyboardListener()

        // 在浮动窗口创建之后，并且XML中的WebView可用之后，初始化WebViewManager
        val xmlWebViews = windowManager.getXmlDefinedWebViews()
        Log.d(TAG, "获取到的XML定义的WebView数量: ${xmlWebViews.count { it != null }}")
        webViewManager = WebViewManager(this, xmlWebViews, windowManager)
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
            val xmlWebViews = windowManager.getXmlDefinedWebViews()
            webViewManager = WebViewManager(this, xmlWebViews, windowManager)
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
            handleSearchInternal(searchParams.query, searchParams.engineKey ?: SearchEngineHandler.DEFAULT_ENGINE_KEY, currentWindowCount)
        } else {
            if (lastQuery == null) {
                Log.d(TAG, "Intent中无搜索参数，加载默认内容 (当前窗口数: $currentWindowCount)")
                handleSearchInternal("", SearchEngineHandler.DEFAULT_ENGINE_KEY, currentWindowCount)
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
        val windowCountToUse = windowCount.coerceIn(1, webViewManager.getWebViews().size)
        currentWindowCount = windowCountToUse

        // 如果是DeepSeek聊天，使用ChatManager处理，并确保输入法能正常工作
        if (engineKey == "deepseek_chat") {
            val webView = webViewManager.getWebViews().firstOrNull()
            if (webView != null) {
                Log.d(TAG, "初始化DeepSeek聊天界面")
                
                // 确保其他网页视图隐藏
                for (i in 1 until webViewManager.getWebViews().size) {
                    webViewManager.getWebViews()[i].visibility = View.GONE
                }
                
                // 确保首个WebView可见
                webView.visibility = View.VISIBLE
                
                // 初始化WebView前进行一些重置
                try {
                    webView.stopLoading()
                    webView.clearHistory()
                    webView.clearCache(true)
                } catch (e: Exception) {
                    Log.e(TAG, "重置WebView状态失败: ${e.message}")
                }
                
                // 初始化WebView
                chatManager.initWebView(webView)
                
                // 确保WebView可聚焦并接收输入
                webView.isFocusable = true
                webView.isFocusableInTouchMode = true
                
                // 允许JavaScript执行
                webView.settings.javaScriptEnabled = true
                
                // 让WebView获取焦点
                webView.requestFocus()
                
                // 通过系统级API显示输入法
                // 延迟显示输入法，确保WebView完全准备好接收输入
                Handler(Looper.getMainLooper()).postDelayed({
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT)
                }, 1000) // 增加延迟时间以确保WebView渲染完成
                
                return // 已经处理了DeepSeek聊天，不需要继续处理其他WebView
            }
        }
        
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
        return engineKey in listOf("chatgpt", "claude", "gemini", "wenxin", "chatglm", 
                                   "qianwen", "xinghuo", "perplexity", "phind", "poe", "deepseek")
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
     * 发送消息到WebView
     */
    fun sendMessageToWebView(message: String, webView: CustomWebView, isDeepSeek: Boolean) {
        // 验证消息不为空
        if (message.trim().isEmpty()) {
            Log.w(TAG, "尝试发送空消息，忽略")
            return
        }
        
        try {
            Log.d(TAG, "准备发送消息到WebView: '$message', isDeepSeek=$isDeepSeek")
            
            // 强制添加JavaScript接口确保可用
            try {
                webView.removeJavascriptInterface("AndroidChatInterface")
            } catch (e: Exception) {
                // 忽略可能的异常
            }
            chatManager.initWebView(webView)
            
            // 确保WebView已就绪
            webView.evaluateJavascript("document.readyState", { readyState ->
                Log.d(TAG, "WebView状态: $readyState")
                if (readyState == "null" || readyState == "\"loading\"") {
                    Log.w(TAG, "WebView未准备好，等待加载完成后再发送")
                    Handler(Looper.getMainLooper()).postDelayed({
                        chatManager.sendMessageToWebView(message, webView, isDeepSeek)
                    }, 1000)
                } else {
                    Log.d(TAG, "WebView已准备好，直接发送消息")
                    // 直接调用JS来添加用户消息并触发发送
                    webView.evaluateJavascript("""
                        (function() {
                            try {
                                // 添加用户消息到UI
                                addMessageToUI('user', '${message.replace("'", "\\'")}');
                                
                                // 添加助手占位符和打字指示器
                                addMessageToUI('assistant', '', false);
                                showTypingIndicator();
                                
                                // 直接调用Android接口发送消息
                                AndroidChatInterface.sendMessage('${message.replace("'", "\\'")}');
                                
                                return true;
                            } catch(e) {
                                console.error("发送消息时出错:", e);
                                return false;
                            }
                        })();
                    """, null)
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "发送消息到WebView失败: ${e.message}", e)
            // 如果直接调用失败，则回退到原始方法
            Handler(Looper.getMainLooper()).postDelayed({
                chatManager.sendMessageToWebView(message, webView, isDeepSeek)
            }, 500)
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

    private fun setupKeyboardListener() {
        val rootView = floatingView?.findViewById<View>(R.id.dual_webview_root)
        rootView?.viewTreeObserver?.addOnGlobalLayoutListener {
            val r = Rect()
            rootView.getWindowVisibleDisplayFrame(r)
            val screenHeight = rootView.height
            
            // 计算输入法高度
            val newKeyboardHeight = screenHeight - r.bottom
            
            if (newKeyboardHeight != keyboardHeight) {
                val isKeyboardNowVisible = newKeyboardHeight > screenHeight * 0.15
                
                if (isKeyboardNowVisible != isKeyboardVisible) {
                    isKeyboardVisible = isKeyboardNowVisible
                    
                    if (isKeyboardVisible) {
                        // 输入法显示时
                        if (originalY == 0) {
                            originalY = windowManager.params?.y ?: 0
                        }
                        // 调整窗口位置，向上移动输入法高度
                        windowManager.params?.y = originalY - newKeyboardHeight.toInt()
                    } else {
                        // 输入法隐藏时，恢复原始位置
                        windowManager.params?.y = originalY
                        originalY = 0
                    }
                    
                    // 更新窗口布局
                    try {
                        windowManager.updateViewLayout(floatingView, windowManager.params)
                    } catch (e: Exception) {
                        Log.e(TAG, "更新窗口布局失败: ${e.message}")
                    }
                }
                
                keyboardHeight = newKeyboardHeight
            }
        }
    }
} 