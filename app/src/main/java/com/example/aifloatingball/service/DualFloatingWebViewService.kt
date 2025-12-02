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
import com.example.aifloatingball.model.SearchEngineCategory

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
        const val ACTION_RESTORE_CARD_VIEW = "com.example.aifloatingball.ACTION_RESTORE_CARD_VIEW"
        
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
    // 为每个WebView存储独立的AndroidChatInterface实例
    private val webViewChatInterfaces = mutableMapOf<CustomWebView, AndroidChatInterface>()

    /**
     * 根据配置获取AI服务类型
     */
    private fun getAIServiceTypeFromConfig(config: AISearchEngine): AIServiceType {
        return when {
            config.name.contains("临时专线", ignoreCase = true) -> AIServiceType.TEMP_SERVICE
            config.name.contains("DeepSeek", ignoreCase = true) -> AIServiceType.DEEPSEEK
            config.name.contains("ChatGPT", ignoreCase = true) -> AIServiceType.CHATGPT
            config.name.contains("Claude", ignoreCase = true) -> AIServiceType.CLAUDE
            config.name.contains("通义千问", ignoreCase = true) || config.name.contains("Qianwen", ignoreCase = true) -> AIServiceType.QIANWEN
            config.name.contains("智谱", ignoreCase = true) || config.name.contains("Zhipu", ignoreCase = true) -> AIServiceType.ZHIPU_AI
            config.name.contains("文心一言", ignoreCase = true) || config.name.contains("Wenxin", ignoreCase = true) -> AIServiceType.WENXIN
            config.name.contains("Gemini", ignoreCase = true) -> AIServiceType.GEMINI
            config.name.contains("Kimi", ignoreCase = true) -> AIServiceType.KIMI
            config.name.contains("讯飞星火", ignoreCase = true) || config.name.contains("Xinghuo", ignoreCase = true) -> AIServiceType.XINGHUO
            config.url.contains("deepseek_chat.html") -> AIServiceType.DEEPSEEK
            config.url.contains("chatgpt_chat.html") -> {
                // 根据配置的API URL判断是ChatGPT、文心一言、Gemini还是Kimi
                when {
                    config.customParams["api_url"]?.contains("openai.com") == true -> AIServiceType.CHATGPT
                    config.customParams["api_url"]?.contains("baidubce.com") == true -> AIServiceType.WENXIN
                    config.customParams["api_url"]?.contains("googleapis.com") == true -> AIServiceType.GEMINI
                    config.customParams["api_url"]?.contains("moonshot.cn") == true -> AIServiceType.KIMI
                    config.customParams["api_url"]?.contains("xf-yun.com") == true -> AIServiceType.XINGHUO
                    config.customParams["api_url"]?.contains("818233.xyz") == true -> AIServiceType.TEMP_SERVICE
                    else -> AIServiceType.CHATGPT // 默认
                }
            }
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
                ACTION_RESTORE_CARD_VIEW -> {
                    Log.d(TAG, "收到恢复卡片视图的广播")
                    // 如果当前是卡片视图模式且窗口被隐藏，则恢复显示
                    if (currentViewMode == ViewMode.CARD_VIEW && isWindowHidden) {
                        showFloatingWindow()
                        Log.d(TAG, "恢复卡片视图界面")
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
                // 创建卡片视图管理器，传入回调用于隐藏悬浮窗
                cardViewModeManager = CardViewModeManager(this, container) {
                    // 隐藏悬浮窗（用户点击app图标时）
                    hideFloatingWindow()
                }
                
                // 设置标签切换回调（用于左右滑动切换标签）
                cardViewModeManager?.setOnTabSwitchCallback { direction ->
                    val currentTabIndex = tabBarView?.getSelectedTabIndex() ?: 0
                    val allTabs = tabBarView?.getAllTabs() ?: emptyList()
                    if (allTabs.isNotEmpty()) {
                        val newIndex = (currentTabIndex + direction).coerceIn(0, allTabs.size - 1)
                        if (newIndex != currentTabIndex) {
                            tabBarView?.selectTab(newIndex)
                            // 触发标签点击事件，加载新标签的搜索结果
                            val newTab = allTabs[newIndex]
                            val searchQuery = floatingWindowManager?.getSearchInputText()?.trim() ?: ""
                            val query = if (searchQuery.isNotEmpty()) {
                                searchQuery
                            } else {
                                newTab.name
                            }
                            loadSearchResultsForTag(query, newTab)
                        }
                    }
                }
                
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
        
        // 根据搜索引擎名称查找对应的搜索引擎对象（包括AI引擎）
        val engines = engineNames.mapNotNull { engineName ->
            // 首先查找普通搜索引擎
            SearchEngine.DEFAULT_ENGINES.find { it.name == engineName }
                ?: run {
                    // 如果找不到，查找AI引擎
                    val aiEngine = AISearchEngine.DEFAULT_AI_ENGINES.find { 
                        it.name == engineName || 
                        it.name.contains(engineName, ignoreCase = true) ||
                        engineName.contains(it.name, ignoreCase = true)
                    }
                    // 如果找到AI引擎，需要获取增强配置（包含API配置）
                    if (aiEngine != null) {
                        val enhancedConfig = aiPageConfigManager.getConfigByKey(aiEngine.name) ?: aiEngine
                        // 创建一个SearchEngine对象以便统一处理
                        SearchEngine(
                            name = enhancedConfig.name,
                            displayName = enhancedConfig.displayName,
                            url = enhancedConfig.url,
                            iconResId = enhancedConfig.iconResId,
                            description = enhancedConfig.description,
                            searchUrl = enhancedConfig.searchUrl,
                            isAI = enhancedConfig.isChatMode,
                            category = SearchEngineCategory.GENERAL
                        )
                    } else {
                        null
                    }
                }
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
    private fun showTabEditDialog(tab: TabBarView.TabItem, @Suppress("UNUSED_PARAMETER") position: Int) {
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
     * 切换时会保留当前搜索内容，并立即加载对应的窗口
     */
    fun toggleViewMode(): ViewMode {
        // 保存当前搜索内容（如果存在）
        val currentSearchText = floatingWindowManager?.getSearchInputText()?.trim() ?: ""
        
        // 切换模式
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
        
        // 恢复搜索内容（如果存在）
        if (currentSearchText.isNotEmpty()) {
            handler.postDelayed({
                floatingWindowManager?.setSearchInputText(currentSearchText)
                // 根据新模式执行搜索
                when (currentViewMode) {
                    ViewMode.HORIZONTAL_SCROLL -> {
                        // 横向模式：使用默认搜索引擎执行搜索
                        webViewManager?.performSearch(currentSearchText, "google")
                    }
                    ViewMode.CARD_VIEW -> {
                        // 卡片模式：为当前标签加载搜索结果
                        val selectedTab = tabBarView?.getSelectedTab()
                        if (selectedTab != null) {
                            loadSearchResultsForTag(currentSearchText, selectedTab)
                        } else {
                            // 如果没有选中标签，使用默认标签
                            val defaultTab = tabBarView?.getAllTabs()?.firstOrNull()
                            if (defaultTab != null) {
                                tabBarView?.selectTab(0)
                                loadSearchResultsForTag(currentSearchText, defaultTab)
                            }
                        }
                    }
                }
            }, 300) // 延迟300ms确保窗口创建完成
        }
        
        Log.d(TAG, "切换视图模式: $currentViewMode, 保留搜索内容: ${if (currentSearchText.isNotEmpty()) "是" else "否"}")
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
     * 切换到横向拖动模式
     */
    private fun switchToHorizontalScrollMode() {
        if (currentViewMode == ViewMode.HORIZONTAL_SCROLL) {
            return // 已经是横向拖动模式
        }
        
        currentViewMode = ViewMode.HORIZONTAL_SCROLL
        
        // 保存模式
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putInt(KEY_VIEW_MODE, currentViewMode.ordinal).apply()
        
        // 切换视图显示
        floatingWindowManager?.switchViewMode(false)
        
        // 如果WebViewManager还未初始化，则初始化它
        if (webViewManager == null && xmlDefinedWebViews.isNotEmpty()) {
            webViewManager = WebViewManager(
                this,
                xmlDefinedWebViews,
                floatingWindowManager!!
            )
        }
        
        Log.d(TAG, "切换到横向拖动模式")
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
            addAction(ACTION_RESTORE_CARD_VIEW)
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
            
            // 如果收到任何启动请求（搜索或URL），且窗口被隐藏，则重新显示
            // 这确保用户通过悬浮球重新激活服务时，窗口都会显示
            val hasSearchQuery = !searchQuery.isNullOrEmpty()
            val hasUrl = !it.getStringExtra("url").isNullOrEmpty()
            if (isWindowHidden && (hasSearchQuery || hasUrl)) {
                showFloatingWindow()
            }
            
            if (hasSearchQuery && searchQuery != null) {
                val query = searchQuery  // 此时已经确认非空
                Log.d(TAG, "收到搜索请求: query='$query', engine='$engineKey', source='$searchSource'")
                
                // 检查是否是语音搜索模式
                val isVoiceSearchMode = it.getBooleanExtra("voice_search_mode", false)
                if (isVoiceSearchMode) {
                    // 语音搜索模式：同时打开多个AI网页
                    val voiceSearchEngines = it.getStringArrayExtra("voice_search_engines")
                    if (!voiceSearchEngines.isNullOrEmpty()) {
                        Log.d(TAG, "语音搜索模式：打开${voiceSearchEngines.size}个AI网页")
                        performVoiceSearch(query, voiceSearchEngines)
                    } else {
                        // 如果没有指定引擎，使用默认的两个：百度AI对话界面（文心一言）和Google AI对话界面（Gemini）
                        Log.d(TAG, "语音搜索模式：使用默认两个AI对话界面")
                        performVoiceSearch(query, arrayOf("百度AI", "Google AI"))
                    }
                } else {
                    // 普通搜索模式：使用原有逻辑
                    // 设置搜索文本到输入框
                    floatingWindowManager?.setSearchInputText(query)
                    
                    // 确保输入框能获得焦点，防止被WebView内容抢夺
                    handler.postDelayed({
                        ensureInputFocus()
                    }, 200)
                    
                    // 执行搜索
                    performSearch(query, engineKey ?: "google")
                    
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
        Log.d(TAG, "performSearch: query='$query', engineKey='$engineKey', viewMode=$currentViewMode")
        when (currentViewMode) {
            ViewMode.HORIZONTAL_SCROLL -> {
                // 横向拖动模式：使用原有逻辑
                val customConfig = aiPageConfigManager.getConfigByKey(engineKey)
                if (customConfig != null) {
                    Log.d(TAG, "找到AI配置: ${customConfig.name}, url=${customConfig.url}, use_custom_html=${customConfig.customParams["use_custom_html"]}")
                    performCustomAISearch(query, customConfig)
                } else {
                    Log.d(TAG, "未找到AI配置，使用标准搜索: $engineKey")
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
        Log.d(TAG, "配置详情: url=${config.url}, searchUrl=${config.searchUrl}, isChatMode=${config.isChatMode}")
        Log.d(TAG, "自定义参数: ${config.customParams}")

        // 验证API配置（如果需要）
        if (config.isChatMode && !aiPageConfigManager.validateApiConfig(config.name)) {
            Log.w(TAG, "AI配置验证失败: ${config.name}")
        }

        // 检查是否使用自定义HTML页面
        val useCustomHtml = config.customParams["use_custom_html"] == "true"
        val isAssetUrl = config.url.startsWith("file:///android_asset/")
        
        Log.d(TAG, "use_custom_html=$useCustomHtml, isAssetUrl=$isAssetUrl, url=${config.url}")

        if (useCustomHtml && isAssetUrl) {
            // 使用自定义HTML页面
            Log.d(TAG, "使用自定义HTML页面: ${config.url}")
            setupCustomDeepSeekPage(query, config)
        } else {
            // 使用常规URL加载
            Log.d(TAG, "使用常规URL加载")
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
     * 只在当前活动的WebView中加载自定义AI页面
     */
    private fun setupCustomDeepSeekPage(query: String, config: com.example.aifloatingball.model.AISearchEngine) {
        Log.d(TAG, "设置自定义AI页面: ${config.name}，将在活动WebView中加载")

        webViewManager?.let { manager ->
            // 获取当前活动的WebView，如果没有则使用第一个WebView
            val targetWebView = manager.getActiveWebView() ?: manager.getFirstWebView()
            
            if (targetWebView != null) {
                Log.d(TAG, "在活动WebView中设置自定义AI页面")
                setupCustomDeepSeekPageInWebView(targetWebView, query, config)
            } else {
                Log.e(TAG, "没有可用的WebView来加载自定义AI页面")
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
     * 执行语音搜索：在多个WebView中同时打开多个AI网页并搜索
     * @param query 搜索查询文本
     * @param engineNames AI引擎名称数组
     */
    private fun performVoiceSearch(query: String, engineNames: Array<String>) {
        Log.d(TAG, "执行语音搜索: query='$query', engines=${engineNames.contentToString()}")
        
        // 设置搜索文本到输入框
        floatingWindowManager?.setSearchInputText(query)
        
        // 确保使用横向拖动模式
        if (currentViewMode != ViewMode.HORIZONTAL_SCROLL) {
            switchToHorizontalScrollMode()
        }
        
        // 确保webViewManager已初始化
        if (webViewManager == null && xmlDefinedWebViews.isNotEmpty()) {
            webViewManager = WebViewManager(
                this,
                xmlDefinedWebViews,
                floatingWindowManager!!
            )
            Log.d(TAG, "初始化webViewManager")
        }
        
        // 确保窗口数量足够
        val requiredWindowCount = engineNames.size
        val currentWindowCount = getCurrentWindowCount()
        if (currentWindowCount < requiredWindowCount) {
            Log.d(TAG, "更新窗口数量: $currentWindowCount -> $requiredWindowCount")
            updateWindowCount(requiredWindowCount)
            // 等待窗口创建完成，增加延迟时间确保WebView已初始化
            handler.postDelayed({
                // 重新初始化webViewManager，因为窗口数量已更新
                if (webViewManager == null && xmlDefinedWebViews.isNotEmpty()) {
                    webViewManager = WebViewManager(
                        this,
                        xmlDefinedWebViews,
                        floatingWindowManager!!
                    )
                }
                loadVoiceSearchEngines(query, engineNames)
            }, 1000) // 增加到1秒，确保WebView已创建
        } else {
            // 即使窗口数量足够，也稍微延迟一下，确保WebView已准备好
            handler.postDelayed({
                loadVoiceSearchEngines(query, engineNames)
            }, 300)
        }
    }
    
    /**
     * 在多个WebView中加载AI引擎并执行搜索
     * @param query 搜索查询文本
     * @param engineNames AI引擎名称数组
     */
    private fun loadVoiceSearchEngines(query: String, engineNames: Array<String>) {
        Log.d(TAG, "加载语音搜索引擎: ${engineNames.contentToString()}")
        
        // 确保使用横向拖动模式
        if (currentViewMode != ViewMode.HORIZONTAL_SCROLL) {
            switchToHorizontalScrollMode()
        }
        
        // 为每个引擎加载对应的网页
        engineNames.forEachIndexed { index, engineName ->
            handler.postDelayed({
                try {
                    Log.d(TAG, "在WebView $index 中加载引擎: $engineName")
                    
                    // 构建搜索URL - 优先处理百度AI和Google AI对话界面
                    // 注意：必须优先匹配，避免被config查找逻辑覆盖
                    val searchUrl = when {
                        // 优先处理百度AI和Google AI对话界面，不通过config查找
                        engineName.equals("百度AI", ignoreCase = true) || 
                        engineName.equals("百度ai", ignoreCase = true) ||
                        (engineName.contains("百度", ignoreCase = true) && engineName.contains("AI", ignoreCase = true)) -> {
                            // 百度AI对话界面（文心一言）- 使用文心一言对话页面
                            Log.d(TAG, "✓ 匹配到百度AI，使用文心一言对话界面: https://yiyan.baidu.com")
                            "https://yiyan.baidu.com"
                        }
                        engineName.equals("Google AI", ignoreCase = true) || 
                        engineName.equals("google ai", ignoreCase = true) ||
                        engineName.equals("GoogleAI", ignoreCase = true) ||
                        (engineName.contains("Google", ignoreCase = true) && engineName.contains("AI", ignoreCase = true)) -> {
                            // Google AI对话界面（Gemini）- 使用Gemini对话页面
                            Log.d(TAG, "✓ 匹配到Google AI，使用Gemini对话界面: https://gemini.google.com/app")
                            "https://gemini.google.com/app"
                        }
                        // 其他引擎通过config查找
                        else -> {
                            // 获取AI配置
                            var config = aiPageConfigManager.getConfigByKey(engineName)
                            
                            // 如果找不到配置，尝试从默认引擎列表中直接查找
                            if (config == null) {
                                config = AISearchEngine.DEFAULT_AI_ENGINES.find { 
                                    it.name.equals(engineName, ignoreCase = true) ||
                                    it.name.contains(engineName, ignoreCase = true) ||
                                    engineName.contains(it.name, ignoreCase = true)
                                }
                                if (config != null) {
                                    Log.d(TAG, "从默认引擎列表中找到配置: ${config.name}")
                                }
                            }
                            
                            // 根据config构建URL
                            when {
                                config != null && config.searchUrl.contains("{query}") -> {
                                    // 如果searchUrl包含{query}占位符，直接替换
                                    config.searchUrl.replace("{query}", java.net.URLEncoder.encode(query, "UTF-8"))
                                }
                                engineName.contains("文心一言", ignoreCase = true) || engineName.contains("wenxin", ignoreCase = true) || engineName.contains("yiyan", ignoreCase = true) -> {
                                    // 文心一言对话页面
                                    "https://yiyan.baidu.com"
                                }
                                engineName.contains("Gemini", ignoreCase = true) || engineName.contains("gemini", ignoreCase = true) -> {
                                    // Gemini对话页面
                                    "https://gemini.google.com/app"
                                }
                                engineName.contains("秘塔", ignoreCase = true) || engineName.contains("metaso", ignoreCase = true) -> {
                                    // 秘塔AI搜索 - 使用桌面版User-Agent以确保页面正常加载
                                    "https://metaso.cn/?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
                                }
                                engineName.contains("豆包", ignoreCase = true) || engineName.contains("doubao", ignoreCase = true) -> {
                                    // 豆包使用chat页面，查询参数通过JavaScript注入
                                    "https://www.doubao.com/chat/"
                                }
                                engineName.contains("Perplexity", ignoreCase = true) || engineName.contains("perplexity", ignoreCase = true) -> {
                                    // Perplexity使用搜索URL格式
                                    "https://www.perplexity.ai/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
                                }
                                config != null -> {
                                    // 其他情况，检查config的URL，如果是对话界面URL，直接使用，不添加查询参数
                                    val baseUrl = config.url.trimEnd('/')
                                    if (baseUrl.contains("yiyan.baidu.com") || baseUrl.contains("gemini.google.com")) {
                                        // 文心一言或Gemini对话界面，直接使用URL，不添加查询参数（通过JavaScript注入）
                                        Log.d(TAG, "使用config中的对话界面URL: $baseUrl")
                                        baseUrl
                                    } else {
                                        // 其他情况，尝试在URL后添加查询参数
                                        "$baseUrl?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
                                    }
                                }
                                else -> {
                                    // 如果都找不到，使用默认URL
                                    Log.w(TAG, "无法确定引擎URL，使用默认处理: $engineName")
                                    null
                                }
                            }
                        }
                    }
                    
                    if (searchUrl != null) {
                        Log.d(TAG, "在WebView $index 中加载URL: $searchUrl")
                        
                        // 确保webViewManager已初始化
                        if (webViewManager == null && xmlDefinedWebViews.isNotEmpty()) {
                            webViewManager = WebViewManager(
                                this,
                                xmlDefinedWebViews,
                                floatingWindowManager!!
                            )
                        }
                        
                        // 在对应的WebView中加载URL
                        webViewManager?.let { manager ->
                            val webView = manager.getAllWebViews().getOrNull(index)
                            if (webView != null) {
                                // 对于 metaso，需要特殊配置WebView以确保页面正常加载
                                if (engineName.contains("秘塔", ignoreCase = true) || engineName.contains("metaso", ignoreCase = true)) {
                                    // 设置桌面版User-Agent
                                    webView.settings.userAgentString = com.example.aifloatingball.utils.WebViewConstants.DESKTOP_USER_AGENT
                                    
                                    // 启用JavaScript（必须）
                                    webView.settings.javaScriptEnabled = true
                                    
                                    // 启用Cookie管理
                                    android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                        android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
                                    }
                                    
                                    // 启用数据库存储
                                    webView.settings.databaseEnabled = true
                                    
                                    // 启用DOM存储（必须）
                                    webView.settings.domStorageEnabled = true
                                    
                                    // 启用混合内容（HTTPS页面加载HTTP资源）
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                                        webView.settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    }
                                    
                                    // 启用文件访问
                                    webView.settings.allowFileAccess = true
                                    webView.settings.allowContentAccess = true
                                    
                                    // 设置缓存模式
                                    webView.settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                                    
                                    // 启用缩放支持
                                    webView.settings.setSupportZoom(true)
                                    webView.settings.builtInZoomControls = false
                                    webView.settings.displayZoomControls = false
                                    
                                    // 设置视口
                                    webView.settings.useWideViewPort = true
                                    webView.settings.loadWithOverviewMode = true
                                    
                                    // 设置默认文本编码
                                    webView.settings.defaultTextEncodingName = "UTF-8"
                                    
                                    // 确保WebView可见且尺寸正确
                                    webView.visibility = View.VISIBLE
                                    webView.setBackgroundColor(android.graphics.Color.WHITE)
                                    
                                    // 设置WebViewClient处理SSL错误和页面错误
                                    val originalClient = webView.webViewClient
                                    webView.webViewClient = object : android.webkit.WebViewClient() {
                                        override fun onReceivedSslError(view: android.webkit.WebView?, handler: android.webkit.SslErrorHandler?, error: android.net.http.SslError?) {
                                            // 对于metaso.cn，允许SSL错误（如果存在）
                                            Log.w(TAG, "metaso SSL错误: ${error?.toString()}")
                                            handler?.proceed()
                                        }
                                        
                                        override fun onReceivedError(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                                            val errorCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                                error?.errorCode ?: -1
                                            } else {
                                                -1
                                            }
                                            val errorDescription = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                                error?.description?.toString() ?: "Unknown error"
                                            } else {
                                                "Unknown error"
                                            }
                                            Log.e(TAG, "metaso 页面加载错误: $errorDescription, Code: $errorCode, URL: ${request?.url}")
                                            
                                            // 调用原始WebViewClient的错误处理
                                            if (originalClient != null) {
                                                originalClient.onReceivedError(view, request, error)
                                            }
                                        }
                                        
                                        @Deprecated("Deprecated in Java")
                                        override fun onReceivedError(view: android.webkit.WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                                            Log.e(TAG, "metaso 页面加载错误 (legacy): $description, Code: $errorCode, URL: $failingUrl")
                                            if (originalClient != null) {
                                                originalClient.onReceivedError(view, errorCode, description, failingUrl)
                                            }
                                        }
                                        
                                        override fun onPageStarted(view: android.webkit.WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                            Log.d(TAG, "metaso 页面开始加载: $url")
                                            if (originalClient != null) {
                                                originalClient.onPageStarted(view, url, favicon)
                                            }
                                        }
                                        
                                        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                            Log.d(TAG, "metaso 页面加载完成: $url")
                                            
                                            // 页面加载完成后，检查页面内容
                                            view?.evaluateJavascript("""
                                                (function() {
                                                    try {
                                                        var body = document.body;
                                                        if (body && body.innerHTML.trim().length > 0) {
                                                            return 'SUCCESS';
                                                        } else {
                                                            return 'EMPTY_BODY';
                                                        }
                                                    } catch(e) {
                                                        return 'ERROR: ' + e.message;
                                                    }
                                                })();
                                            """.trimIndent()) { result ->
                                                Log.d(TAG, "metaso 页面内容检查: $result")
                                                if (result != null && result.contains("EMPTY_BODY")) {
                                                    Log.w(TAG, "metaso 页面内容为空，尝试重新加载")
                                                    handler.postDelayed({
                                                        view?.reload()
                                                    }, 1000)
                                                }
                                            }
                                            
                                            if (originalClient != null) {
                                                originalClient.onPageFinished(view, url)
                                            }
                                        }
                                        
                                        override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                                            if (originalClient != null) {
                                                return originalClient.shouldOverrideUrlLoading(view, request)
                                            }
                                            return false
                                        }
                                        
                                        @Deprecated("Deprecated in Java")
                                        override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, url: String?): Boolean {
                                            if (originalClient != null) {
                                                return originalClient.shouldOverrideUrlLoading(view, url)
                                            }
                                            return false
                                        }
                                    }
                                    
                                    Log.d(TAG, "已为 metaso 配置完整WebView设置")
                                }
                                
                                webView.loadUrl(searchUrl)
                                
                                // 对于百度AI对话界面（文心一言），需要在页面加载后通过JavaScript注入查询文本
                                if (engineName.contains("百度AI", ignoreCase = true) || 
                                    engineName.contains("文心一言", ignoreCase = true) || 
                                    engineName.contains("wenxin", ignoreCase = true) || 
                                    engineName.contains("yiyan", ignoreCase = true)) {
                                    // 转义查询文本中的特殊字符，避免JavaScript注入攻击
                                    val escapedQuery = query
                                        .replace("\\", "\\\\")
                                        .replace("'", "\\'")
                                        .replace("\"", "\\\"")
                                        .replace("\n", "\\n")
                                        .replace("\r", "\\r")
                                    
                                    // 延迟执行JavaScript，等待页面加载完成
                                    handler.postDelayed({
                                        try {
                                            val jsCode = """
                                                (function() {
                                                    // 尝试找到输入框并输入查询文本
                                                    var input = document.querySelector('textarea, input[type="text"], input[type="search"], [contenteditable="true"], .input-area, .chat-input');
                                                    if (input) {
                                                        if (input.tagName === 'TEXTAREA' || input.tagName === 'INPUT') {
                                                            input.value = '$escapedQuery';
                                                            input.dispatchEvent(new Event('input', { bubbles: true }));
                                                            input.dispatchEvent(new Event('change', { bubbles: true }));
                                                        } else if (input.contentEditable === 'true') {
                                                            input.textContent = '$escapedQuery';
                                                            input.dispatchEvent(new Event('input', { bubbles: true }));
                                                        }
                                                        // 尝试触发搜索或发送
                                                        setTimeout(function() {
                                                            var sendButton = document.querySelector('button[type="submit"], button[aria-label*="发送"], button[aria-label*="搜索"], button[aria-label*="Send"], [data-testid*="send"], .send-button, .submit-button');
                                                            if (sendButton) {
                                                                sendButton.click();
                                                            }
                                                        }, 500);
                                                    }
                                                })();
                                            """.trimIndent()
                                            webView.evaluateJavascript(jsCode, null)
                                            Log.d(TAG, "已为文心一言注入查询文本: $query")
                                        } catch (e: Exception) {
                                            Log.e(TAG, "为文心一言注入查询文本失败", e)
                                        }
                                    }, 2000) // 等待2秒让页面加载完成
                                }
                                
                                // 对于Google AI对话界面（Gemini），需要在页面加载后通过JavaScript注入查询文本
                                if (engineName.contains("Google AI", ignoreCase = true) || 
                                    engineName.contains("Gemini", ignoreCase = true) || 
                                    engineName.contains("gemini", ignoreCase = true)) {
                                    // 转义查询文本中的特殊字符，避免JavaScript注入攻击
                                    val escapedQuery = query
                                        .replace("\\", "\\\\")
                                        .replace("'", "\\'")
                                        .replace("\"", "\\\"")
                                        .replace("\n", "\\n")
                                        .replace("\r", "\\r")
                                    
                                    // 延迟执行JavaScript，等待页面加载完成
                                    handler.postDelayed({
                                        try {
                                            val jsCode = """
                                                (function() {
                                                    // 尝试找到输入框并输入查询文本
                                                    var input = document.querySelector('textarea, input[type="text"], input[type="search"], [contenteditable="true"], .ql-editor, .input-box');
                                                    if (input) {
                                                        if (input.tagName === 'TEXTAREA' || input.tagName === 'INPUT') {
                                                            input.value = '$escapedQuery';
                                                            input.dispatchEvent(new Event('input', { bubbles: true }));
                                                            input.dispatchEvent(new Event('change', { bubbles: true }));
                                                        } else if (input.contentEditable === 'true') {
                                                            input.textContent = '$escapedQuery';
                                                            input.dispatchEvent(new Event('input', { bubbles: true }));
                                                        }
                                                        // 尝试触发搜索或发送
                                                        setTimeout(function() {
                                                            var sendButton = document.querySelector('button[type="submit"], button[aria-label*="发送"], button[aria-label*="搜索"], button[aria-label*="Send"], [data-testid*="send"], .send-button');
                                                            if (sendButton) {
                                                                sendButton.click();
                                                            }
                                                        }, 500);
                                                    }
                                                })();
                                            """.trimIndent()
                                            webView.evaluateJavascript(jsCode, null)
                                            Log.d(TAG, "已为Gemini注入查询文本: $query")
                                        } catch (e: Exception) {
                                            Log.e(TAG, "为Gemini注入查询文本失败", e)
                                        }
                                    }, 2000) // 等待2秒让页面加载完成
                                }
                                
                                // 对于豆包，需要在页面加载后通过JavaScript注入查询文本
                                if (engineName.contains("豆包", ignoreCase = true) || engineName.contains("doubao", ignoreCase = true)) {
                                    // 转义查询文本中的特殊字符，避免JavaScript注入攻击
                                    val escapedQuery = query
                                        .replace("\\", "\\\\")
                                        .replace("'", "\\'")
                                        .replace("\"", "\\\"")
                                        .replace("\n", "\\n")
                                        .replace("\r", "\\r")
                                    
                                    // 延迟执行JavaScript，等待页面加载完成
                                    handler.postDelayed({
                                        try {
                                            val jsCode = """
                                                (function() {
                                                    // 尝试找到输入框并输入查询文本
                                                    var input = document.querySelector('textarea, input[type="text"], input[type="search"], [contenteditable="true"]');
                                                    if (input) {
                                                        if (input.tagName === 'TEXTAREA' || input.tagName === 'INPUT') {
                                                            input.value = '$escapedQuery';
                                                            input.dispatchEvent(new Event('input', { bubbles: true }));
                                                            input.dispatchEvent(new Event('change', { bubbles: true }));
                                                        } else if (input.contentEditable === 'true') {
                                                            input.textContent = '$escapedQuery';
                                                            input.dispatchEvent(new Event('input', { bubbles: true }));
                                                        }
                                                        // 尝试触发搜索或发送
                                                        setTimeout(function() {
                                                            var sendButton = document.querySelector('button[type="submit"], button[aria-label*="发送"], button[aria-label*="搜索"], button[aria-label*="Send"], [data-testid*="send"]');
                                                            if (sendButton) {
                                                                sendButton.click();
                                                            }
                                                        }, 500);
                                                    }
                                                })();
                                            """.trimIndent()
                                            webView.evaluateJavascript(jsCode, null)
                                            Log.d(TAG, "已为豆包注入查询文本: $query")
                                        } catch (e: Exception) {
                                            Log.e(TAG, "为豆包注入查询文本失败", e)
                                        }
                                    }, 2000) // 等待2秒让页面加载完成
                                }
                                
                                // 对于 metaso，添加页面加载完成检测和错误处理
                                if (engineName.contains("秘塔", ignoreCase = true) || engineName.contains("metaso", ignoreCase = true)) {
                                    // 延迟检查页面加载状态
                                    handler.postDelayed({
                                        val currentUrl = webView.url
                                        if (currentUrl != null && currentUrl.contains("metaso.cn")) {
                                            // 检查页面是否正常加载
                                            webView.evaluateJavascript("""
                                                (function() {
                                                    try {
                                                        var body = document.body;
                                                        if (body && body.innerHTML.trim().length > 0) {
                                                            return 'SUCCESS: Page loaded, content length: ' + body.innerHTML.length;
                                                        } else {
                                                            return 'WARNING: Page body is empty';
                                                        }
                                                    } catch(e) {
                                                        return 'ERROR: ' + e.message;
                                                    }
                                                })();
                                            """.trimIndent()) { result ->
                                                Log.d(TAG, "metaso 页面加载检查结果: $result")
                                                if (result != null && (result.contains("WARNING") || result.contains("ERROR"))) {
                                                    Log.w(TAG, "metaso 页面可能未正常加载，尝试重新加载")
                                                    // 如果页面未正常加载，尝试重新加载
                                                    handler.postDelayed({
                                                        webView.reload()
                                                    }, 1000)
                                                }
                                            }
                                        }
                                    }, 3000) // 延迟3秒检查，确保页面加载完成
                                }
                                
                                Log.d(TAG, "WebView $index 加载成功: $engineName")
                            } else {
                                Log.w(TAG, "WebView $index 不存在，无法加载: $engineName")
                            }
                        } ?: run {
                            Log.e(TAG, "webViewManager未初始化，无法加载: $engineName")
                        }
                    } else {
                        Log.e(TAG, "无法构建搜索URL: $engineName")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "加载引擎失败: $engineName", e)
                }
            }, (index * 300).toLong()) // 每个WebView延迟300ms加载，避免同时加载造成卡顿
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
     * 为每个WebView创建独立的AndroidChatInterface实例
     */
    private fun setupCustomDeepSeekPageInWebView(webView: com.example.aifloatingball.ui.webview.CustomWebView, query: String, config: com.example.aifloatingball.model.AISearchEngine) {
        Log.d(TAG, "在WebView中设置自定义DeepSeek页面: ${config.name}, url=${config.url}")

        // 根据配置确定AI服务类型
        val aiServiceType = getAIServiceTypeFromConfig(config)
        Log.d(TAG, "AI服务类型: $aiServiceType")

        // 为当前WebView创建独立的AndroidChatInterface实例
        val chatInterface = AndroidChatInterface(
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
                    Log.d(TAG, "WebView消息完成: $fullMessage")
                }

                override fun onNewChatStarted() {
                    Log.d(TAG, "WebView新对话开始")
                    handler.post {
                        isFirstChunk = true
                    }
                }

                override fun onSessionDeleted(sessionId: String) {
                    Log.d(TAG, "WebView会话已删除: $sessionId")
                }
            },
            aiServiceType
        )

        // 存储当前WebView的接口实例
        webViewChatInterfaces[webView] = chatInterface
        
        // 为了向后兼容，也设置第一个WebView的接口为默认接口
        if (webViewManager?.getFirstWebView() == webView) {
            androidChatInterface = chatInterface
        }

        // 添加JavaScript接口
        webView.addJavascriptInterface(chatInterface, "AndroidChatInterface")

        // 确保WebView设置允许加载asset文件
        webView.settings.apply {
            allowFileAccess = true
            allowContentAccess = true
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = true
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN_MR1) {
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = true
            }
        }

        // 加载自定义HTML页面
        Log.d(TAG, "开始加载HTML页面: ${config.url}")
        
        // 使用延迟检查来避免覆盖WebViewClient导致递归问题
        // 在页面加载后检查内容
        handler.postDelayed({
            val currentUrl = webView.url
            if (currentUrl == config.url || currentUrl?.contains("deepseek_chat.html") == true) {
                Log.d(TAG, "页面加载完成，检查内容: $currentUrl")
                webView.evaluateJavascript("""
                    (function() {
                        try {
                            var body = document.body;
                            if (body && body.innerHTML.trim().length > 0) {
                                return 'SUCCESS: Page content loaded, body length: ' + body.innerHTML.length;
                            } else {
                                return 'WARNING: Page body is empty';
                            }
                        } catch(e) {
                            return 'ERROR: ' + e.message;
                        }
                    })();
                """.trimIndent()) { result ->
                    Log.d(TAG, "页面内容检查结果: $result")
                }
            }
        }, 2000) // 延迟2秒检查，确保页面加载完成
        
        webView.loadUrl(config.url)

        // 如果有查询，延迟发送到页面并自动发送
        if (query.isNotBlank()) {
            handler.postDelayed({
                val escapedQuery = query.replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r")
                val jsCode = """
                    (function() {
                        try {
                            var messageInput = document.getElementById('messageInput');
                            if (messageInput) {
                                // 兼容不同的输入框类型（div或textarea）
                                if (messageInput.tagName === 'TEXTAREA' || messageInput.tagName === 'INPUT') {
                                    // textarea或input类型，使用value
                                    messageInput.value = '$escapedQuery';
                                    // 触发input事件，确保页面状态更新
                                    var inputEvent = new Event('input', { bubbles: true });
                                    messageInput.dispatchEvent(inputEvent);
                                } else {
                                    // div类型（如deepseek），使用textContent
                                    messageInput.textContent = '$escapedQuery';
                                }
                                
                                // 更新发送按钮状态
                                if (typeof updateSendButton === 'function') {
                                    updateSendButton();
                                }
                                
                                // 调整textarea高度（如果存在此函数）
                                if (typeof adjustTextareaHeight === 'function') {
                                    adjustTextareaHeight();
                                }
                                
                                // 等待一小段时间确保输入框已更新
                                setTimeout(function() {
                                    // 自动发送消息
                                    if (typeof sendMessage === 'function') {
                                        sendMessage();
                                    } else {
                                        console.log('sendMessage function not found, trying to trigger manually');
                                        // 如果sendMessage函数不存在，尝试手动触发
                                        var sendButton = document.getElementById('send-button');
                                        if (sendButton && !sendButton.disabled) {
                                            sendButton.click();
                                        }
                                    }
                                }, 100);
                            } else {
                                console.error('messageInput element not found');
                            }
                        } catch(e) {
                            console.error('Error auto-sending message: ' + e.message);
                        }
                    })();
                """.trimIndent()
                Log.d(TAG, "自动发送查询消息: $query")
                webView.evaluateJavascript(jsCode, null)
            }, 2000) // 等待页面加载完成，增加延迟确保页面完全初始化
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
        // 清理所有WebView的ChatInterface实例
        webViewChatInterfaces.clear()
        androidChatInterface = null
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
    
    // 用于跟踪悬浮窗隐藏状态
    private var isWindowHidden = false
    
    /**
     * 隐藏悬浮窗（当用户点击app图标时调用）
     */
    private fun hideFloatingWindow() {
        floatingWindowManager?.let { manager ->
            manager.floatingView?.let { view ->
                try {
                    // 隐藏悬浮窗
                    view.visibility = View.GONE
                    isWindowHidden = true
                    android.util.Log.d(TAG, "隐藏悬浮窗（用户点击app图标）")
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "隐藏悬浮窗失败", e)
                }
            }
        }
    }
    
    /**
     * 显示悬浮窗（当用户通过悬浮球重新激活时调用）
     */
    private fun showFloatingWindow() {
        floatingWindowManager?.let { manager ->
            manager.floatingView?.let { view ->
                try {
                    // 显示悬浮窗
                    view.visibility = View.VISIBLE
                    isWindowHidden = false
                    android.util.Log.d(TAG, "显示悬浮窗（用户重新激活）")
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "显示悬浮窗失败", e)
                }
            }
        }
    }
    
    /**
     * 获取卡片视图模式管理器（供外部调用）
     */
    fun getCardViewModeManager(): CardViewModeManager? {
        return cardViewModeManager
    }
} 