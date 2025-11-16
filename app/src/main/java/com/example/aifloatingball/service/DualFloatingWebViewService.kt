package com.example.aifloatingball.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.manager.AIPageConfigManager
import com.example.aifloatingball.manager.AIServiceType
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.webview.AndroidChatInterface
import com.example.aifloatingball.ui.floating.FloatingWindowManager
import com.example.aifloatingball.ui.floating.WindowStateCallback
import com.example.aifloatingball.ui.text.TextSelectionManager
import com.example.aifloatingball.ui.webview.CustomWebView
import com.example.aifloatingball.ui.webview.WebViewManager
import com.example.aifloatingball.utils.SearchParams
import com.example.aifloatingball.ui.cardview.CardViewModeManager
import com.example.aifloatingball.ui.cardview.TabBarView
import com.example.aifloatingball.model.SearchEngine

/**
 * 双窗口浮动WebView服务
 * 支持两种视图模式：
 * 1. 横向拖动模式（原有模式）：多个WebView横向排列，可拖动查看
 * 2. 卡片视图模式（新模式）：搜索结果以卡片形式展示，左右两列，可向下滚动
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
        const val KEY_VIEW_MODE = "view_mode" // 视图模式：0=横向拖动，1=卡片视图
        const val ACTION_UPDATE_AI_ENGINES = "com.example.aifloatingball.ACTION_UPDATE_AI_ENGINES"
        const val ACTION_UPDATE_MENU = "com.example.aifloatingball.ACTION_UPDATE_MENU"
        const val ACTION_RESET_WINDOW_STATE = "com.example.aifloatingball.ACTION_RESET_WINDOW_STATE"
        
        // 通知相关常量
        const val NOTIFICATION_ID = 2
        const val CHANNEL_ID = "DualFloatingWebViewChannel"
        
        // 添加静态isRunning字段
        @JvmStatic
        var isRunning = false
    }

    /**
     * 视图模式枚举
     */
    enum class ViewMode {
        HORIZONTAL_SCROLL,  // 横向拖动模式
        CARD_VIEW           // 卡片视图模式
    }

    // 状态变量
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var settingsManager: SettingsManager
    private lateinit var aiPageConfigManager: AIPageConfigManager
    private var androidChatInterface: AndroidChatInterface? = null

    /**
     * 根据配置获取AI服务类型
     */
    private fun getAIServiceTypeFromConfig(config: AISearchEngine): AIServiceType {
        return when {
            config.name.contains("DeepSeek", ignoreCase = true) -> AIServiceType.DEEPSEEK
            config.name.contains("ChatGPT", ignoreCase = true) -> AIServiceType.CHATGPT
            config.name.contains("Claude", ignoreCase = true) -> AIServiceType.CLAUDE
            config.name.contains("通义千问", ignoreCase = true) -> AIServiceType.QIANWEN
            config.name.contains("智谱", ignoreCase = true) -> AIServiceType.ZHIPU_AI
            config.url.contains("deepseek_chat.html") -> AIServiceType.DEEPSEEK
            config.url.contains("chatgpt_chat.html") -> AIServiceType.CHATGPT
            config.url.contains("claude_chat.html") -> AIServiceType.CLAUDE
            config.url.contains("qianwen_chat.html") -> AIServiceType.QIANWEN
            config.url.contains("zhipu_chat.html") -> AIServiceType.ZHIPU_AI
            else -> AIServiceType.DEEPSEEK // 默认
        }
    }
    
    // 浮动窗口管理器
    private var floatingWindowManager: FloatingWindowManager? = null
    private var textSelectionManager: TextSelectionManager? = null
    private var webViewManager: WebViewManager? = null
    private var xmlDefinedWebViews: List<CustomWebView?> = emptyList()
    
    // 卡片视图模式管理器
    private var cardViewModeManager: CardViewModeManager? = null
    private var tabBarView: TabBarView? = null
    var currentViewMode: ViewMode = ViewMode.HORIZONTAL_SCROLL
        private set

    // 广播接收器
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_UPDATE_AI_ENGINES, ACTION_UPDATE_MENU -> {
                    Log.d(TAG, "收到刷新搜索引擎的广播")
                    // 刷新搜索引擎
                    refreshSearchEngines()
                }
                ACTION_RESET_WINDOW_STATE -> {
                    Log.d(TAG, "=== 收到重置窗口位置的广播 ===")
                    try {
                        // 重置窗口位置
                        resetWindowPosition()
                        Log.d(TAG, "✓ 重置窗口位置调用完成")
                    } catch (e: Exception) {
                        Log.e(TAG, "重置窗口位置失败", e)
                    }
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
        aiPageConfigManager = AIPageConfigManager(this)

        // 调试：打印所有AI配置
        aiPageConfigManager.debugPrintAllConfigs()

        // 创建通知渠道和启动前台服务
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // 注册广播接收器
        registerBroadcastReceiver()
        
        // 初始化文本选择管理器
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        textSelectionManager = TextSelectionManager(this, windowManager)
        
        // 加载视图模式
        loadViewMode()
        
        // 创建浮动窗口
        createFloatingWindow()
    }

    /**
     * 创建浮动窗口
     */
    private fun createFloatingWindow() {
        try {
            Log.d(TAG, "开始创建浮动窗口，模式: $currentViewMode")
            
            // 初始化浮动窗口管理器
            floatingWindowManager = FloatingWindowManager(
                this,
                this,
                this,
                textSelectionManager!!
            )
            
            // 创建浮动窗口并获取WebView列表
            xmlDefinedWebViews = floatingWindowManager!!.createFloatingWindow()
            
            // 切换视图模式显示
            floatingWindowManager?.switchViewMode(currentViewMode == ViewMode.CARD_VIEW)
            
            // 根据视图模式初始化不同的管理器
            when (currentViewMode) {
                ViewMode.HORIZONTAL_SCROLL -> {
                    // 横向拖动模式：使用WebViewManager
                    webViewManager = WebViewManager(
                        this,
                        xmlDefinedWebViews,
                        floatingWindowManager!!
                    )
                }
                ViewMode.CARD_VIEW -> {
                    // 卡片视图模式：初始化卡片视图管理器和标签栏
                    setupCardViewMode()
                }
            }
            
            Log.d(TAG, "浮动窗口创建成功")
            
        } catch (e: Exception) {
            Log.e(TAG, "创建浮动窗口失败", e)
            stopSelf()
        }
    }

    /**
     * 设置卡片视图模式
     */
    private fun setupCardViewMode() {
        floatingWindowManager?.let { manager ->
            val container = manager.getCardViewContainer()
            if (container != null) {
                // 创建卡片视图管理器
                cardViewModeManager = CardViewModeManager(this, container)
                
                // 设置全屏查看器的父容器（使用浮动窗口的根视图）
                manager.floatingView?.let { floatingView ->
                    val rootView = floatingView.rootView as? ViewGroup
                        ?: (floatingView as? ViewGroup)
                    rootView?.let {
                        cardViewModeManager?.setFullScreenParentContainer(it)
                    }
                }
                
                // 创建标签栏
                val tabBarContainer = manager.getTabBarContainer()
                if (tabBarContainer != null) {
                    tabBarView = TabBarView(this, tabBarContainer)
                    setupTabBarListeners()
                }
                
                // 设置卡片点击监听器
                cardViewModeManager?.setOnCardClickListener(
                    object : CardViewModeManager.OnCardClickListener {
                        override fun onCardClick(cardData: CardViewModeManager.SearchResultCardData) {
                            Log.d(TAG, "卡片点击: ${cardData.title}")
                        }
                        
                        override fun onCardExpand(cardData: CardViewModeManager.SearchResultCardData) {
                            Log.d(TAG, "卡片展开: ${cardData.title}")
                        }
                    }
                )
            }
        }
    }

    /**
     * 设置标签栏监听器
     */
    private fun setupTabBarListeners() {
        tabBarView?.setOnTabClickListener(
            object : TabBarView.OnTabClickListener {
                override fun onTabClick(tab: TabBarView.TabItem, position: Int) {
                    Log.d(TAG, "标签点击: ${tab.name}")
                    // 当标签切换时，清除旧卡片并加载新标签的搜索结果
                    val searchQuery = floatingWindowManager?.getSearchInputText()?.trim() ?: ""
                    // 如果有搜索内容，使用搜索内容；否则使用标签名称作为默认搜索词
                    val query = if (searchQuery.isNotEmpty()) {
                        searchQuery
                    } else {
                        tab.name // 使用标签名称作为默认搜索词
                    }
                    loadSearchResultsForTag(query, tab)
                }
                
                override fun onTabLongClick(tab: TabBarView.TabItem, position: Int) {
                    Log.d(TAG, "标签长按: ${tab.name}")
                    // 显示标签编辑对话框（可以删除、重命名等）
                    showTabEditDialog(tab, position)
                }
            }
        )
    }

    /**
     * 为标签加载搜索结果
     */
    private fun loadSearchResultsForTag(query: String, tab: TabBarView.TabItem) {
        Log.d(TAG, "开始为标签 '${tab.name}' 加载搜索结果，查询: $query")
        
        // 清除所有旧卡片（不仅仅是该标签的，因为切换标签时应该清除所有）
        cardViewModeManager?.clearAllCards()
        
        // 获取该标签对应的搜索引擎名称列表
        val engineNames = tab.getDefaultEngines()
        Log.d(TAG, "标签 '${tab.name}' 对应的搜索引擎: $engineNames")
        
        // 根据搜索引擎名称查找对应的搜索引擎对象
        val engines = engineNames.mapNotNull { engineName ->
            SearchEngine.DEFAULT_ENGINES.find { it.name == engineName }
        }
        
        // 如果找不到对应的搜索引擎，使用默认的11个搜索引擎
        val finalEngines = if (engines.isEmpty()) {
            Log.w(TAG, "未找到标签 '${tab.name}' 对应的搜索引擎，使用默认搜索引擎")
            SearchEngine.DEFAULT_ENGINES.take(11)
        } else {
            engines.take(11) // 确保最多使用11个搜索引擎
        }
        
        Log.d(TAG, "将为标签 '${tab.name}' 创建 ${finalEngines.size} 个搜索结果卡片")
        
        // 为每个搜索引擎创建卡片
        finalEngines.forEachIndexed { index, engine ->
            Log.d(TAG, "创建卡片 ${index + 1}/${finalEngines.size}: ${engine.displayName}")
            cardViewModeManager?.addSearchResultCard(
                query = query,
                engineKey = engine.name,
                engineName = engine.displayName,
                tag = tab.name
            )
        }
        
        Log.d(TAG, "为标签 '${tab.name}' 加载了 ${finalEngines.size} 个搜索结果卡片")
    }

    /**
     * 显示标签编辑对话框
     */
    private fun showTabEditDialog(tab: TabBarView.TabItem, position: Int) {
        // TODO: 实现标签编辑对话框
        Log.d(TAG, "显示标签编辑对话框: ${tab.name}")
    }

    /**
     * 加载视图模式
     */
    private fun loadViewMode() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val modeValue = prefs.getInt(KEY_VIEW_MODE, ViewMode.HORIZONTAL_SCROLL.ordinal)
        currentViewMode = ViewMode.values().getOrElse(modeValue) { ViewMode.HORIZONTAL_SCROLL }
        Log.d(TAG, "加载视图模式: $currentViewMode")
    }

    /**
     * 切换视图模式
     */
    fun toggleViewMode(): ViewMode {
        currentViewMode = when (currentViewMode) {
            ViewMode.HORIZONTAL_SCROLL -> ViewMode.CARD_VIEW
            ViewMode.CARD_VIEW -> ViewMode.HORIZONTAL_SCROLL
        }
        
        // 保存模式
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_VIEW_MODE, currentViewMode.ordinal).apply()
        
        // 重新创建窗口
        floatingWindowManager?.destroyFloatingWindow()
        createFloatingWindow()
        
        Log.d(TAG, "切换视图模式: $currentViewMode")
        return currentViewMode
    }

    /**
     * 切换到卡片视图模式（不重新创建窗口，只切换显示）
     */
    private fun switchToCardViewMode() {
        if (currentViewMode == ViewMode.CARD_VIEW) {
            return // 已经是卡片视图模式
        }
        
        currentViewMode = ViewMode.CARD_VIEW
        
        // 保存模式
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_VIEW_MODE, currentViewMode.ordinal).apply()
        
        // 切换视图显示
        floatingWindowManager?.switchViewMode(true)
        
        // 如果卡片视图管理器还未初始化，则初始化它
        if (cardViewModeManager == null) {
            setupCardViewMode()
        }
        
        Log.d(TAG, "切换到卡片视图模式")
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
            addAction(ACTION_RESET_WINDOW_STATE)
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
     * 重置窗口位置到默认值
     */
    private fun resetWindowPosition() {
        floatingWindowManager?.resetWindowPositionToDefault()
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
            // 检查是否需要切换到卡片视图模式
            val useCardViewMode = it.getBooleanExtra("use_card_view_mode", false)
            if (useCardViewMode && currentViewMode != ViewMode.CARD_VIEW) {
                Log.d(TAG, "切换到卡片视图模式")
                // 切换到卡片视图模式（不重新创建窗口，只切换显示）
                switchToCardViewMode()
            }
            
            // 处理搜索请求
            val searchQuery = it.getStringExtra("search_query")
            val engineKey = it.getStringExtra("engine_key")
            val searchSource = it.getStringExtra("search_source")
            
            if (!searchQuery.isNullOrEmpty()) {
                Log.d(TAG, "收到搜索请求: query='$searchQuery', engine='$engineKey', source='$searchSource'")
                
                // 设置搜索文本到输入框
                floatingWindowManager?.setSearchInputText(searchQuery)
                
                // 确保输入框能获得焦点，防止被WebView内容抢夺
                handler.postDelayed({
                    ensureInputFocus()
                }, 200)
                
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
        when (currentViewMode) {
            ViewMode.HORIZONTAL_SCROLL -> {
                // 横向拖动模式：使用原有逻辑
                val customConfig = aiPageConfigManager.getConfigByKey(engineKey)
                if (customConfig != null) {
                    performCustomAISearch(query, customConfig)
                } else {
                    webViewManager?.performSearch(query, engineKey)
                }
            }
            ViewMode.CARD_VIEW -> {
                // 卡片视图模式：为当前标签添加搜索结果卡片
                val selectedTab = tabBarView?.getSelectedTab()
                if (selectedTab != null) {
                    loadSearchResultsForTag(query, selectedTab)
                } else {
                    // 如果没有选中标签，使用默认标签（第一个标签）
                    val defaultTab = tabBarView?.getAllTabs()?.firstOrNull()
                    if (defaultTab != null) {
                        // 选中默认标签
                        tabBarView?.selectTab(0)
                        loadSearchResultsForTag(query, defaultTab)
                    } else {
                        // 如果连标签都没有，创建一个默认标签并加载搜索结果
                        Log.w(TAG, "没有可用的标签，无法加载搜索结果")
                    }
                }
            }
        }
    }

    /**
     * 执行定制AI搜索
     */
    private fun performCustomAISearch(query: String, config: com.example.aifloatingball.model.AISearchEngine) {
        Log.d(TAG, "执行定制AI搜索: ${config.name}, query: $query")

        // 验证API配置（如果需要）
        if (config.isChatMode && !aiPageConfigManager.validateApiConfig(config.name)) {
            Log.w(TAG, "AI配置验证失败: ${config.name}")
        }

        // 检查是否使用自定义HTML页面
        val useCustomHtml = config.customParams["use_custom_html"] == "true"

        if (useCustomHtml && config.url.startsWith("file:///android_asset/")) {
            // 使用自定义HTML页面
            setupCustomDeepSeekPage(query, config)
        } else {
            // 使用常规URL加载
            val finalUrl = if (config.customParams.isNotEmpty()) {
                aiPageConfigManager.buildUrlWithParams(config.searchUrl, config.customParams)
            } else {
                config.getSearchUrl(query)
            }

            Log.d(TAG, "最终AI搜索URL: $finalUrl")

            webViewManager?.let { manager ->
                manager.getFirstWebView()?.loadUrl(finalUrl)
            }
        }
    }

    /**
     * 设置自定义AI页面
     */
    private fun setupCustomDeepSeekPage(query: String, config: com.example.aifloatingball.model.AISearchEngine) {
        Log.d(TAG, "设置自定义AI页面: ${config.name}")

        webViewManager?.let { manager ->
            val webView = manager.getFirstWebView()
            if (webView != null) {
                // 根据配置确定AI服务类型
                val aiServiceType = getAIServiceTypeFromConfig(config)

                // 创建AndroidChatInterface
                androidChatInterface = AndroidChatInterface(
                    this,
                    object : AndroidChatInterface.WebViewCallback {
                        override fun onMessageReceived(message: String) {
                            // 将AI回复发送到HTML页面
                            handler.post {
                                val jsCode = """
                                    if (typeof window.receiveMessage === 'function') {
                                        window.receiveMessage('$message');
                                    }
                                """.trimIndent()
                                webView.evaluateJavascript(jsCode, null)
                            }
                        }

                        override fun onMessageCompleted(fullMessage: String) {
                            // 消息完成时的处理
                            handler.post {
                                val jsCode = """
                                    if (typeof window.onMessageCompleted === 'function') {
                                        window.onMessageCompleted('$fullMessage');
                                    }
                                """.trimIndent()
                                webView.evaluateJavascript(jsCode, null)
                            }
                            Log.d(TAG, "消息完成: $fullMessage")
                        }

                        override fun onNewChatStarted() {
                            Log.d(TAG, "新对话开始")
                        }

                        override fun onSessionDeleted(sessionId: String) {
                            Log.d(TAG, "会话已删除: $sessionId")
                        }
                    },
                    aiServiceType
                )

                // 添加JavaScript接口
                webView.addJavascriptInterface(androidChatInterface!!, "AndroidChatInterface")

                // 加载自定义HTML页面
                webView.loadUrl(config.url)

                // 如果有查询，延迟发送到页面
                if (query.isNotBlank()) {
                    handler.postDelayed({
                        val jsCode = """
                            if (typeof window.setInitialQuery === 'function') {
                                window.setInitialQuery('$query');
                            }
                        """.trimIndent()
                        webView.evaluateJavascript(jsCode, null)
                    }, 1000) // 等待页面加载完成
                }
            }
        }
    }

    /**
     * 确保输入框焦点稳定
     */
    fun ensureInputFocus() {
        floatingWindowManager?.ensureInputFocus()
    }

    /**
     * 清除输入框焦点
     */
    fun clearInputFocus() {
        floatingWindowManager?.clearInputFocus()
    }

    /**
     * 超强力输入框焦点激活 - 用于对付顽固的页面焦点抢夺
     */
    fun forceInputFocusActivation() {
        floatingWindowManager?.forceInputFocusActivation()
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
            "chatgpt", "chatgpt_chat", "chatgpt (custom)", "claude", "claude (custom)",
            "gemini", "wenxin", "chatglm", "qianwen", "通义千问 (custom)", "xinghuo",
            "perplexity", "phind", "poe", "deepseek", "deepseek_chat", "deepseek (api)",
            "智谱ai (custom)", "zhipu", "kimi", "tiangong", "metaso", "quark",
            "360ai", "baiduai", "you", "brave", "wolfram", "wanzhi", "baixiaoying",
            "yuewen", "doubao", "cici", "hailuo", "groq", "yuanbao"
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

        // 检查是否是定制AI配置
        val customConfig = aiPageConfigManager.getConfigByKey(engineKey)
        if (customConfig != null) {
            Log.d(TAG, "找到定制AI配置: ${customConfig.name}, isChatMode=${customConfig.isChatMode}, url=${customConfig.url}")
            Log.d(TAG, "自定义参数: ${customConfig.customParams}")
            performCustomAISearchInWebView(webViewIndex, query, customConfig)
        } else {
            Log.d(TAG, "未找到定制AI配置，使用标准搜索")
            // 使用标准搜索
            webViewManager?.performSearchInWebView(webViewIndex, query, engineKey)
        }
    }

    /**
     * 在指定WebView中执行定制AI搜索
     */
    private fun performCustomAISearchInWebView(webViewIndex: Int, query: String, config: com.example.aifloatingball.model.AISearchEngine) {
        Log.d(TAG, "在WebView $webViewIndex 中执行定制AI搜索: ${config.name}")

        webViewManager?.let { manager ->
            val webView = manager.getAllWebViews().getOrNull(webViewIndex)
            if (webView != null) {
                // 验证API配置（如果需要）
                if (config.isChatMode && !aiPageConfigManager.validateApiConfig(config.name)) {
                    Log.w(TAG, "AI配置验证失败: ${config.name}")
                }

                // 检查是否使用自定义HTML页面
                val useCustomHtml = config.customParams["use_custom_html"] == "true"

                if (useCustomHtml && config.url.startsWith("file:///android_asset/")) {
                    // 使用自定义HTML页面
                    setupCustomDeepSeekPageInWebView(webView, query, config)
                } else {
                    // 使用常规URL加载
                    val finalUrl = if (config.customParams.isNotEmpty()) {
                        aiPageConfigManager.buildUrlWithParams(config.searchUrl, config.customParams)
                    } else {
                        config.getSearchUrl(query)
                    }

                    Log.d(TAG, "在WebView $webViewIndex 中加载URL: $finalUrl")
                    webView.loadUrl(finalUrl)
                }
            } else {
                Log.e(TAG, "找不到索引为 $webViewIndex 的WebView")
            }
        }
    }

    /**
     * 在指定WebView中设置自定义DeepSeek页面
     */
    private fun setupCustomDeepSeekPageInWebView(webView: com.example.aifloatingball.ui.webview.CustomWebView, query: String, config: com.example.aifloatingball.model.AISearchEngine) {
        Log.d(TAG, "在WebView中设置自定义DeepSeek页面")

        // 根据配置确定AI服务类型
        val aiServiceType = getAIServiceTypeFromConfig(config)

        // 创建AndroidChatInterface
        androidChatInterface = AndroidChatInterface(
            this,
            object : AndroidChatInterface.WebViewCallback {
                private var isFirstChunk = true

                override fun onMessageReceived(message: String) {
                    // 将AI回复发送到HTML页面
                    handler.post {
                        val escapedMessage = message.replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")

                        if (isFirstChunk) {
                            // 第一个chunk，开始新的回复
                            val jsCode = """
                                if (typeof showTypingIndicator === 'function') {
                                    showTypingIndicator();
                                }
                                currentRawText = '';
                                if (typeof appendToResponse === 'function') {
                                    appendToResponse('$escapedMessage');
                                }
                            """.trimIndent()
                            webView.evaluateJavascript(jsCode, null)
                            isFirstChunk = false
                        } else {
                            // 后续chunk，追加到回复
                            val jsCode = """
                                if (typeof appendToResponse === 'function') {
                                    appendToResponse('$escapedMessage');
                                }
                            """.trimIndent()
                            webView.evaluateJavascript(jsCode, null)
                        }
                    }
                }

                override fun onMessageCompleted(fullMessage: String) {
                    // 响应完成，调用completeResponse
                    handler.post {
                        val jsCode = """
                            if (typeof completeResponse === 'function') {
                                completeResponse();
                            }
                        """.trimIndent()
                        webView.evaluateJavascript(jsCode, null)
                        isFirstChunk = true // 重置为下次对话准备
                    }
                }

                override fun onNewChatStarted() {
                    Log.d(TAG, "新对话开始")
                    handler.post {
                        isFirstChunk = true
                    }
                }

                override fun onSessionDeleted(sessionId: String) {
                    Log.d(TAG, "会话已删除: $sessionId")
                }
            },
            aiServiceType
        )

        // 添加JavaScript接口
        webView.addJavascriptInterface(androidChatInterface!!, "AndroidChatInterface")

        // 加载自定义HTML页面
        webView.loadUrl(config.url)

        // 如果有查询，延迟发送到页面
        if (query.isNotBlank()) {
            handler.postDelayed({
                val escapedQuery = query.replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")
                val jsCode = """
                    if (typeof messageInput !== 'undefined') {
                        messageInput.textContent = '$escapedQuery';
                        if (typeof updateSendButton === 'function') {
                            updateSendButton();
                        }
                    }
                """.trimIndent()
                webView.evaluateJavascript(jsCode, null)
            }, 1500) // 等待页面加载完成
        }
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
     * 处理配置变化，特别是屏幕方向变化
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "配置变化: 屏幕方向=${newConfig.orientation}")
        
        // 延迟处理屏幕方向变化，等待系统稳定
        handler.postDelayed({
            try {
                floatingWindowManager?.handleOrientationChangeIfNeeded()
                Log.d(TAG, "✓ 屏幕方向变化处理完成")
            } catch (e: Exception) {
                Log.e(TAG, "处理屏幕方向变化失败", e)
            }
        }, 200) // 200ms延迟确保屏幕旋转动画完成
    }

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
        
        // 销毁卡片视图管理器
        cardViewModeManager?.destroy()
        tabBarView?.destroy()
        
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