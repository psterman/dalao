package com.example.aifloatingball.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.AnimatedVectorDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.preference.PreferenceManager
import android.text.Editable
import android.text.TextWatcher
import android.view.ActionMode
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.DragEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Spinner
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.HomeActivity
import com.example.aifloatingball.R
import com.example.aifloatingball.activity.AIApiConfigActivity
import com.example.aifloatingball.manager.AIApiManager
import com.example.aifloatingball.manager.AIServiceType
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.WindowInsets
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import com.example.aifloatingball.model.AppSearchSettings
import android.net.Uri
import android.content.ActivityNotFoundException
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.utils.EngineUtil
import com.example.aifloatingball.manager.ModeManager
import com.example.aifloatingball.utils.FaviconLoader
import com.google.android.material.tabs.TabLayout
import android.content.res.Configuration
import android.util.TypedValue
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.annotation.AttrRes
import android.content.pm.PackageManager
import com.example.aifloatingball.MasterPromptSettingsActivity
import com.example.aifloatingball.SettingsActivity as MainSettingsActivity
import com.google.android.material.button.MaterialButton
import android.content.ClipData
import android.content.ClipboardManager
import androidx.appcompat.app.AlertDialog
import android.widget.Toast
import androidx.core.content.ContextCompat
import android.content.ClipDescription
import android.widget.Button
import android.util.Log
import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.app.usage.UsageEvents
import com.example.aifloatingball.ChatActivity
import com.example.aifloatingball.AIContactListActivity
import com.example.aifloatingball.SimpleModeActivity
import com.example.aifloatingball.model.AppSearchConfig
import com.example.aifloatingball.VoiceRecognitionActivity
import android.os.VibrationEffect
import com.example.aifloatingball.adapter.AppSearchAdapter
import com.example.aifloatingball.adapter.RecentAppAdapter
import com.example.aifloatingball.manager.AppInfoManager
import com.example.aifloatingball.model.AppInfo
import com.google.android.material.card.MaterialCardView
import com.example.aifloatingball.adapter.AssistantCategoryAdapter
import com.example.aifloatingball.data.AssistantPrompts
import com.example.aifloatingball.model.AssistantPrompt
import com.example.aifloatingball.adapter.AssistantPromptAdapter
import com.example.aifloatingball.model.AssistantCategory
import com.airbnb.lottie.LottieAnimationView
import com.example.aifloatingball.ui.DynamicIslandIndicatorView
import android.widget.ImageButton
import com.example.aifloatingball.adapter.NotificationAdapter
import com.example.aifloatingball.adapter.ProfileSelectorAdapter

// 剪贴板内容类型枚举
enum class ClipboardContentType {
    ADDRESS,           // 地址信息
    URL,              // 普通URL链接
    URL_SCHEME,       // 应用URL Scheme
    WEATHER,          // 天气相关
    FINANCE,          // 金融相关
    FOREIGN_LANGUAGE, // 外文内容
    PHONE_NUMBER,     // 电话号码
    EMAIL,            // 邮箱地址
    GENERAL_TEXT      // 普通文本
}

// 场景分类
enum class SceneCategory {
    MAPS,             // 地图导航
    BROWSER,          // 浏览器
    SOCIAL,           // 社交应用
    SHOPPING,         // 购物应用
    WEATHER,          // 天气应用
    FINANCE,          // 金融应用
    TRANSLATION,      // 翻译应用
    COMMUNICATION,    // 通讯应用
    GENERAL           // 通用应用
}

// 内容分析器
class ContentAnalyzer {
    companion object {
        // 地址识别正则
        private val ADDRESS_PATTERNS = listOf(
            ".*省.*市.*区.*",           // 中国地址格式
            ".*街道.*号.*",             // 街道地址
            ".*路.*号.*",               // 道路地址
            ".*大厦.*",                 // 建筑物
            ".*广场.*",                 // 广场
            ".*公园.*",                 // 公园
            ".*医院.*",                 // 医院
            ".*学校.*",                 // 学校
            ".*商场.*",                 // 商场
            ".*酒店.*",                 // 酒店
            ".*银行.*",                 // 银行
            ".*地铁.*",                 // 地铁站
            ".*火车站.*",               // 火车站
            ".*机场.*"                  // 机场
        )
        
        // URL识别正则
        private val URL_PATTERNS = listOf(
            "https?://.*",              // HTTP/HTTPS链接
            "www\\..*",                 // www开头的链接
            ".*\\.com.*",               // .com域名
            ".*\\.cn.*",                // .cn域名
            ".*\\.org.*",               // .org域名
            ".*\\.net.*"                // .net域名
        )
        
        // URL Scheme识别
        private val URL_SCHEME_PATTERNS = mapOf(
            "weixin://" to "微信",
            "mqqapi://" to "QQ", 
            "taobao://" to "淘宝",
            "alipay://" to "支付宝",
            "snssdk1128://" to "抖音",
            "sinaweibo://" to "微博",
            "bilibili://" to "哔哩哔哩",
            "youtube://" to "YouTube",
            "wework://" to "企业微信",
            "tim://" to "TIM",
            "xhsdiscover://" to "小红书",
            "douban://" to "豆瓣",
            "twitter://" to "Twitter-X",
            "zhihu://" to "知乎"
        )
        
        // 天气关键词
        private val WEATHER_KEYWORDS = listOf(
            "天气", "温度", "下雨", "晴天", "阴天", "多云", 
            "雪", "风", "湿度", "气压", "紫外线", "空气质量",
            "weather", "rain", "sunny", "cloudy", "snow",
            "°C", "°F", "摄氏度", "华氏度", "暴雨", "雷雨"
        )
        
        // 金融关键词
        private val FINANCE_KEYWORDS = listOf(
            "股票", "基金", "理财", "投资", "银行", "贷款", "保险",
            "汇率", "黄金", "原油", "期货", "债券", "信用卡",
            "stock", "fund", "investment", "bank", "loan",
            "￥", "$", "€", "£", "¥", "元", "美元", "欧元", "英镑"
        )
        
        // 外文检测正则
        private val FOREIGN_LANGUAGE_PATTERNS = listOf(
            "[a-zA-Z]{3,}",             // 英文单词
            "[\\u3040-\\u309F\\u30A0-\\u30FF]", // 日文
            "[\\uAC00-\\uD7AF]",        // 韩文
            "[\\u0400-\\u04FF]",        // 俄文
            "[\\u00C0-\\u017F]"         // 拉丁文扩展
        )
        
        // 电话号码正则
        private val PHONE_PATTERN = "1[3-9]\\d{9}|\\d{3,4}-?\\d{7,8}"
        
        // 邮箱正则
        private val EMAIL_PATTERN = "[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}"
    }
    
    fun analyzeContent(content: String): ClipboardContentType {
        val trimmedContent = content.trim()
        
        // 1. 检查URL Scheme
        if (URL_SCHEME_PATTERNS.keys.any { trimmedContent.startsWith(it) }) {
            return ClipboardContentType.URL_SCHEME
        }
        
        // 2. 检查URL链接
        if (URL_PATTERNS.any { trimmedContent.matches(it.toRegex()) }) {
            return ClipboardContentType.URL
        }
        
        // 3. 检查地址
        if (ADDRESS_PATTERNS.any { trimmedContent.matches(it.toRegex()) }) {
            return ClipboardContentType.ADDRESS
        }
        
        // 4. 检查天气关键词
        if (WEATHER_KEYWORDS.any { trimmedContent.contains(it, ignoreCase = true) }) {
            return ClipboardContentType.WEATHER
        }
        
        // 5. 检查金融关键词
        if (FINANCE_KEYWORDS.any { trimmedContent.contains(it, ignoreCase = true) }) {
            return ClipboardContentType.FINANCE
        }
        
        // 6. 检查外文
        if (FOREIGN_LANGUAGE_PATTERNS.any { trimmedContent.matches(it.toRegex()) }) {
            return ClipboardContentType.FOREIGN_LANGUAGE
        }
        
        // 7. 检查电话号码
        if (trimmedContent.matches(PHONE_PATTERN.toRegex())) {
            return ClipboardContentType.PHONE_NUMBER
        }
        
        // 8. 检查邮箱
        if (trimmedContent.matches(EMAIL_PATTERN.toRegex())) {
            return ClipboardContentType.EMAIL
        }
        
        return ClipboardContentType.GENERAL_TEXT
    }
    
    fun getUrlSchemeApp(content: String): String? {
        return URL_SCHEME_PATTERNS.entries.find { content.startsWith(it.key) }?.value
    }
}

// 场景推荐引擎
class SceneRecommendationEngine {
    companion object {
        // 场景推荐规则
        private val SCENE_RULES = mapOf(
            ClipboardContentType.ADDRESS to listOf(SceneCategory.MAPS),
            ClipboardContentType.URL to listOf(SceneCategory.BROWSER),
            ClipboardContentType.URL_SCHEME to listOf(SceneCategory.SOCIAL, SceneCategory.SHOPPING),
            ClipboardContentType.WEATHER to listOf(SceneCategory.WEATHER),
            ClipboardContentType.FINANCE to listOf(SceneCategory.FINANCE),
            ClipboardContentType.FOREIGN_LANGUAGE to listOf(SceneCategory.TRANSLATION),
            ClipboardContentType.PHONE_NUMBER to listOf(SceneCategory.COMMUNICATION),
            ClipboardContentType.EMAIL to listOf(SceneCategory.COMMUNICATION),
            ClipboardContentType.GENERAL_TEXT to listOf(SceneCategory.GENERAL)
        )
        
        // 场景应用映射
        private val SCENE_APPS = mapOf(
            SceneCategory.MAPS to listOf("高德地图", "百度地图", "腾讯地图"),
            SceneCategory.BROWSER to listOf("Chrome", "Firefox", "UC浏览器", "QQ浏览器", "夸克"),
            SceneCategory.SOCIAL to listOf("微信", "QQ", "微博", "抖音", "小红书", "知乎"),
            SceneCategory.SHOPPING to listOf("淘宝", "京东", "拼多多", "天猫", "闲鱼"),
            SceneCategory.WEATHER to listOf("墨迹天气", "天气通", "彩云天气", "中国天气"),
            SceneCategory.FINANCE to listOf("支付宝", "招商银行", "蚂蚁财富", "同花顺", "东方财富"),
            SceneCategory.TRANSLATION to listOf("有道词典", "欧路词典", "百度翻译", "Google翻译"),
            SceneCategory.COMMUNICATION to listOf("微信", "QQ", "电话", "短信"),
            SceneCategory.GENERAL to listOf("微信", "QQ", "微博", "浏览器")
        )
    }
    
    fun getRecommendedScenes(contentType: ClipboardContentType): List<SceneCategory> {
        return SCENE_RULES[contentType] ?: listOf(SceneCategory.GENERAL)
    }
    
    fun getAppsForScene(scene: SceneCategory): List<String> {
        return SCENE_APPS[scene] ?: emptyList()
    }
    
    fun calculateAppPriority(appName: String, contentType: ClipboardContentType, scenes: List<SceneCategory>): Int {
        var priority = 0
        
        // 基础优先级
        priority += 100
        
        // 场景匹配加分
        scenes.forEach { scene ->
            val sceneApps = getAppsForScene(scene)
            if (sceneApps.contains(appName)) {
                priority += 50
            }
        }
        
        // 特殊类型加分
        when (contentType) {
            ClipboardContentType.URL_SCHEME -> {
                if (appName in listOf("微信", "QQ", "微博", "抖音", "淘宝", "支付宝")) {
                    priority += 30
                }
            }
            ClipboardContentType.ADDRESS -> {
                if (appName in listOf("高德地图", "百度地图")) {
                    priority += 30
                }
            }
            ClipboardContentType.URL -> {
                if (appName in listOf("Chrome", "Firefox", "UC浏览器")) {
                    priority += 30
                }
            }
            ClipboardContentType.WEATHER -> {
                if (appName in listOf("墨迹天气", "天气通")) {
                    priority += 30
                }
            }
            ClipboardContentType.FINANCE -> {
                if (appName in listOf("支付宝", "招商银行")) {
                    priority += 30
                }
            }
            ClipboardContentType.FOREIGN_LANGUAGE -> {
                if (appName in listOf("有道词典", "欧路词典")) {
                    priority += 30
                }
            }
            else -> {}
        }
        
        return priority
    }
}

class DynamicIslandService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    // --- Enhanced Data Models ---
    data class SearchEngine(
        val name: String,
        val description: String,
        val iconResId: Int,
        val searchUrl: String // e.g., "https://www.google.com/search?q="
    )

    data class SearchCategory(
        val title: String,
        val engines: List<SearchEngine>
    )

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var windowManager: WindowManager
    private lateinit var appSearchSettings: AppSearchSettings
    private lateinit var settingsManager: SettingsManager
    
    private var windowContainerView: FrameLayout? = null // The stage
    private var animatingIslandView: FrameLayout? = null // The moving/transforming view
    private var islandContentView: View? = null // The content (icons, searchbox)
    private var configPanelView: View? = null
    private var searchEngineSelectorView: View? = null
    private var assistantSelectorView: View? = null
    private var assistantPromptSelectorView: View? = null
    private var selectorScrimView: View? = null
    private var aiAssistantPanelView: View? = null // 新的AI助手面板
    private var isResponseFolded = false // 回复区域是否折叠

    private lateinit var notificationIconContainer: LinearLayout
    private var searchInput: EditText? = null
    private var searchButton: ImageView? = null
    private var selectedAssistantTextView: TextView? = null

    private var isSearchModeActive = false
    private var isEditingModeActive = false // New state for editing
    private var compactWidth: Int = 0
    private var expandedWidth: Int = 0
    private var compactHeight: Int = 0
    private var expandedHeight: Int = 0
    private var statusBarHeight: Int = 0
    
    /**
     * 获取灵动岛的实际高度（自适应）
     */
    private fun getIslandActualHeight(): Int {
        return animatingIslandView?.height ?: expandedHeight
    }
    
    /**
     * 更新面板位置（基于灵动岛实际高度）
     */
    private fun updatePanelPositions() {
        try {
            val actualHeight = getIslandActualHeight()
            val newTopMargin = statusBarHeight + actualHeight + 16.dpToPx()
            
            // 更新助手选择器面板位置
            assistantSelectorView?.let { view ->
                val params = view.layoutParams as? FrameLayout.LayoutParams
                params?.topMargin = newTopMargin
                view.layoutParams = params
            }
            
            // 更新身份选择器面板位置
            assistantPromptSelectorView?.let { view ->
                val params = view.layoutParams as? FrameLayout.LayoutParams
                params?.topMargin = newTopMargin
                view.layoutParams = params
            }
            
            // 更新AI助手面板位置
            aiAssistantPanelView?.let { view ->
                val params = view.layoutParams as? FrameLayout.LayoutParams
                params?.topMargin = newTopMargin
                view.layoutParams = params
            }
            
            Log.d(TAG, "面板位置已更新，灵动岛实际高度: ${actualHeight}px, 面板顶部边距: ${newTopMargin}px")
        } catch (e: Exception) {
            Log.e(TAG, "更新面板位置失败", e)
        }
    }

    private var appSearchIconContainer: LinearLayout? = null
    private var appSearchIconScrollView: HorizontalScrollView? = null

    // 不再需要proxyIndicatorView，因为现在使用按钮
    private var proxyIndicatorAnimator: ValueAnimator? = null

    private var currentKeyboardHeight = 0
    private var editingScrimView: View? = null // New scrim view for background blur/dim

    private val activeNotifications = ConcurrentHashMap<String, ImageView>()
    private val activeSlots = ConcurrentHashMap<Int, SearchEngine>()
    
    // New variables for the triple browser preview UI
    private var pagePreview1: View? = null
    private var pagePreview2: View? = null
    private var pagePreview3: View? = null
    
    private var textActionMenu: PopupWindow? = null

    // 新增：用于处理粘贴按钮的 Handler 和 Runnable
    private var pasteButtonView: View? = null
    private val pasteButtonHandler = Handler(Looper.getMainLooper())
    private var hidePasteButtonRunnable: Runnable? = null
    private var selectedAssistantPrompt: AssistantPrompt? = null

    private val uiHandler = Handler(Looper.getMainLooper())
    private var savePositionRunnable: Runnable? = null
    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            val command = intent.getStringExtra(NotificationListener.EXTRA_COMMAND)
            val key = intent.getStringExtra(NotificationListener.EXTRA_NOTIFICATION_KEY)
            key ?: return

            when (command) {
                NotificationListener.COMMAND_POSTED -> {
                    val title = intent.getStringExtra(NotificationListener.EXTRA_TITLE) ?: ""
                    val text = intent.getStringExtra(NotificationListener.EXTRA_TEXT) ?: ""
                    val iconByteArray = intent.getByteArrayExtra(NotificationListener.EXTRA_ICON)
                    
                    if (iconByteArray != null) {
                        val iconBitmap = BitmapFactory.decodeByteArray(iconByteArray, 0, iconByteArray.size)
                        addNotificationIcon(key, iconBitmap)
                        showNotificationOnIndicator(iconBitmap, title, text)
                    }
                }
                NotificationListener.COMMAND_REMOVED -> {
                    removeNotificationIcon(key)
                    if (activeNotifications.isEmpty()) {
                        clearNotificationOnIndicator()
                    }
                }
            }
        }
    }

    private val appSearchUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.aifloatingball.ACTION_UPDATE_APP_SEARCH") {
                if (isSearchModeActive) {
                    populateAppSearchIcons()
                }
            }
        }
    }

    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val query = intent.getStringExtra("query")
            val content = intent.getStringExtra("content")
            updateExpandedViewContent(query, content, true)
        }
    }

    private val positionUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "com.example.aifloatingball.ACTION_UPDATE_ISLAND_POSITION") {
                val position = intent.getIntExtra("position", 50)
                Log.d(TAG, "收到位置更新广播: position=$position")
                updateIslandPosition(position)
            }
        }
    }

    companion object {
        private const val TAG = "DynamicIslandService"
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "DynamicIslandChannel"

        @Volatile
        var isRunning = false
            private set

        /**
         * 检查DynamicIslandService是否正在运行
         */
        @Suppress("DEPRECATION") // 为了兼容性保留旧API
        fun isRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            return manager.getRunningServices(Integer.MAX_VALUE)
                .any { it.service.className == DynamicIslandService::class.java.name }
        }
    }

    private var isHiding = false
    private val hideHandler = Handler(Looper.getMainLooper())
    private var hideRunnable: Runnable? = null
    
    // 搜索防抖相关
    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null
    
    // App Search Components
    private lateinit var appInfoManager: AppInfoManager
    private var appSearchRecyclerView: RecyclerView? = null
    private var appSearchAdapter: AppSearchAdapter? = null
    private var appSearchResultsContainer: View? = null
    private var closeAppSearchButton: View? = null
    private var ballView: View? = null // 圆球状态视图
    
    // 应用切换监听相关
    private var usageStatsManager: UsageStatsManager? = null
    private var appSwitchHandler: Handler? = null
    private var appSwitchRunnable: Runnable? = null
    private var currentPackageName: String? = null
    private var isAutoMinimizeEnabled = true // 是否启用自动缩小功能
    
    // 剪贴板相关
    private var clipboardManager: ClipboardManager? = null
    private var lastClipboardContent: String? = null
    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var isClipboardAutoExpandEnabled = true // 是否启用复制文字自动展开功能
    private var lastClipboardChangeTime = 0L // 上次剪贴板变化时间
    private val clipboardChangeDebounceTime = 1000L // 防抖时间：1秒
    
    // 最近选中的APP相关
    private var recentAppButton: MaterialButton? = null
    private var recentAppsDropdown: PopupWindow? = null
    private var recentAppAdapter: RecentAppAdapter? = null
    private val recentApps = mutableListOf<AppInfo>()
    private var currentSelectedApp: AppInfo? = null
    private var lastSearchQuery: String = ""
    
    // 智能场景匹配系统
    private val contentAnalyzer = ContentAnalyzer()
    private val sceneRecommendationEngine = SceneRecommendationEngine()
    
    // 状态保存相关
    private val PREFS_NAME = "dynamic_island_prefs"
    private val KEY_CURRENT_APP_PACKAGE = "current_app_package"
    private val KEY_RECENT_APPS = "recent_apps"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        Log.d(TAG, "DynamicIslandService 启动")

        // Create notification channel and start foreground immediately
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        appSearchSettings = AppSearchSettings.getInstance(this)
        
        // 初始化AppInfoManager并加载应用列表
        AppInfoManager.getInstance().loadApps(this)
        
        // 加载最近选中的APP历史
        loadRecentAppsFromPrefs()
        
        // 初始化剪贴板监听器
        initClipboardListener()
        
        // 初始化应用切换监听器
        initAppSwitchListener()
        
        // 强制启用增强版布局（调试用）
        forceEnableEnhancedLayout()
        
        // 测试增强版灵动岛功能
        testEnhancedIslandFeatures()
        settingsManager = SettingsManager.getInstance(this) // Initialize SettingsManager

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        showDynamicIsland()

        LocalBroadcastManager.getInstance(this).registerReceiver(
            notificationReceiver,
            IntentFilter(NotificationListener.ACTION_NOTIFICATION)
        )
        // Register receiver for app search updates
        val filter = IntentFilter("com.example.aifloatingball.ACTION_UPDATE_APP_SEARCH")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(appSearchUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(appSearchUpdateReceiver, filter)
        }
        
        // Register receiver for island position updates
        val positionFilter = IntentFilter("com.example.aifloatingball.ACTION_UPDATE_ISLAND_POSITION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(positionUpdateReceiver, positionFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(positionUpdateReceiver, positionFilter)
        }
        
        setupMessageReceiver()

        settingsManager.registerOnSharedPreferenceChangeListener(this)
        appInfoManager = AppInfoManager.getInstance()
        // App list might be already loaded by FloatingWindowService, but this is safe
        appInfoManager.loadApps(this)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Dynamic Island Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, HomeActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("灵动岛正在运行")
            .setContentText("点击返回应用")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun showDynamicIsland() {
        if (windowContainerView != null) return

        // 使用MaterialComponents主题创建LayoutInflater
        val contextThemeWrapper = ContextThemeWrapper(this, com.google.android.material.R.style.Theme_MaterialComponents_Light)
        val inflater = LayoutInflater.from(contextThemeWrapper)
        statusBarHeight = getStatusBarHeight()

        // Recalculate dimensions based on current configuration and settings
        val displayMetrics = resources.displayMetrics
        val islandWidth = settingsManager.getIslandWidth()
        
        // 强制设置合适的宽度，确保四个按钮能够完整显示
        val minRequiredWidth = 240 // 240dp，确保四个按钮能够完整显示
        val actualWidth = maxOf(islandWidth, minRequiredWidth)
        
        compactWidth = (actualWidth * displayMetrics.density).toInt()
        expandedWidth = (displayMetrics.widthPixels * 0.9).toInt()
        compactHeight = resources.getDimensionPixelSize(R.dimen.dynamic_island_compact_height)
        expandedHeight = resources.getDimensionPixelSize(R.dimen.dynamic_island_expanded_height)
        
        Log.d(TAG, "灵动岛尺寸计算: 原始宽度=${islandWidth}dp, 实际宽度=${actualWidth}dp, 像素宽度=${compactWidth}px, 高度=${compactHeight}px")

        // 1. The Stage
        windowContainerView = FrameLayout(this)
        val stageParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, // Use full width for the stage
            WindowManager.LayoutParams.WRAP_CONTENT, // Adjust height to content
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or 
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or 
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL, // 允许触摸事件穿透到下层
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START // Change gravity to START to avoid horizontal conflicts
            y = 0
        }

        // 2. The Animating View (Island itself, not the proxy bar)
        animatingIslandView = FrameLayout(this).apply {
            background = ColorDrawable(Color.TRANSPARENT)
            layoutParams = FrameLayout.LayoutParams(compactWidth, compactHeight, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = statusBarHeight // 设置在状态栏下方
            }
            visibility = View.VISIBLE // 初始状态就显示，包含按钮
        }
        
        // 3. The Content - 使用包含按钮的布局
        islandContentView = inflater.inflate(R.layout.dynamic_island_layout, animatingIslandView, false)
        islandContentView?.background = ColorDrawable(Color.TRANSPARENT)
        
        // 设置islandContentView的布局参数，确保它使用父容器的完整宽度
        islandContentView?.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        
        notificationIconContainer = islandContentView!!.findViewById(R.id.notification_icon_container)
        // 移除永久隐藏通知图标的代码
        // notificationIconContainer.visibility = View.GONE // 删除这行
        appSearchIconScrollView = islandContentView!!.findViewById(R.id.app_search_icon_scroll_view)
        appSearchIconContainer = islandContentView!!.findViewById(R.id.app_search_icon_container)
        
        // 确保按钮容器始终可见
        val buttonContainer = islandContentView!!.findViewById<LinearLayout>(R.id.button_container)
        buttonContainer?.visibility = View.VISIBLE
        
        // 设置按钮交互
        Log.d(TAG, "设置灵动岛按钮交互")
        setupEnhancedLayoutButtons(islandContentView!!)
        
        animatingIslandView!!.addView(islandContentView)

        // 4. 灵动岛现在使用按钮交互，不需要拖拽功能
        
        // 5. 设置灵动岛的长按监听器
        animatingIslandView?.setOnLongClickListener {
                if (!isSearchModeActive) {
                Log.d(TAG, "灵动岛长按，显示搜索面板")
                showConfigPanel()
                true
                } else {
                    false
                }
            }
        
        windowContainerView!!.addView(animatingIslandView)
        
        updateIslandVisibility()

        try {
            windowManager.addView(windowContainerView, stageParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showActionIcon(action: String) {
        // 不再需要显示动作图标，因为现在有按钮
        return
        
        val iconResId = when (action) {
            "voice_recognize" -> R.drawable.ic_mic
            "floating_menu" -> R.drawable.ic_menu
            "dual_search" -> R.drawable.ic_search
            "island_panel" -> R.drawable.ic_apps
            "settings" -> R.drawable.ic_settings
            else -> R.drawable.ic_apps // 默认图标
        }
        
        // 不再需要显示应用图标，因为现在有按钮
    }

    private fun executeAction(action: String) {
        when (action) {
            "voice_recognize" -> startVoiceRecognition()
            "floating_menu" -> { /* No-op in DynamicIslandService */ }
            "dual_search" -> startDualSearch()
            "island_panel" -> transitionToSearchState()
            "settings" -> openSettings()
            "mode_switch" -> showModeSwitchDialog()
            "none" -> { /* No-op */ }
        }
    }

    /**
     * 显示模式切换对话框
     */
    private fun showModeSwitchDialog() {
        try {
            // 直接切换到下一个模式，而不是显示对话框
            // 因为在Service中显示对话框需要特殊权限
            ModeManager.switchToNextMode(this)

            // 显示Toast提示当前模式
            val currentMode = ModeManager.getCurrentMode(this)
            Toast.makeText(this, "已切换到: ${currentMode.displayName}", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            android.util.Log.e("DynamicIslandService", "模式切换失败", e)
            Toast.makeText(this, "模式切换功能暂时不可用", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startVoiceRecognition() {
        try {
            val intent = Intent(this, VoiceRecognitionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Log.d(TAG, "启动语音识别")
        } catch (e: Exception) {
            Log.e(TAG, "启动语音识别失败", e)
            Toast.makeText(this, "无法启动语音识别", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startDualSearch() {
        try {
            // 启动双搜索服务
            val intent = Intent(this, DualFloatingWebViewService::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startService(intent)
            Log.d(TAG, "启动双搜索服务")
        } catch (e: Exception) {
            Log.e(TAG, "启动双搜索服务失败", e)
            Toast.makeText(this, "无法启动搜索功能", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openSettings() {
        try {
            val intent = Intent(this, MainSettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Log.d(TAG, "打开设置页面")
        } catch (e: Exception) {
            Log.e(TAG, "打开设置页面失败", e)
            Toast.makeText(this, "无法打开设置页面", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showSearchDialog() {
        try {
            val intent = Intent(this, DualFloatingWebViewService::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("source", "灵动岛搜索")
                putExtra("startTime", System.currentTimeMillis())
            }
            startService(intent)
            Log.d(TAG, "启动搜索服务")
        } catch (e: Exception) {
            Log.e(TAG, "启动搜索服务失败", e)
            Toast.makeText(this, "无法启动搜索", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showAppListDialog() {
        try {
            val intent = Intent(this, AIContactListActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Log.d(TAG, "打开应用程序列表")
        } catch (e: Exception) {
            Log.e(TAG, "打开应用程序列表失败", e)
            Toast.makeText(this, "无法打开应用程序列表", Toast.LENGTH_SHORT).show()
        }
    }


    private fun transitionToSearchState(force: Boolean = false) {
        if (isSearchModeActive && !force) return
        isSearchModeActive = true

        // 不再需要检查拖拽状态，因为现在使用按钮
        proxyIndicatorAnimator?.cancel()

        val windowParams = windowContainerView?.layoutParams as? WindowManager.LayoutParams
        windowParams?.let {
            it.width = WindowManager.LayoutParams.MATCH_PARENT
            it.height = WindowManager.LayoutParams.MATCH_PARENT
            it.flags = it.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            windowManager.updateViewLayout(windowContainerView, it)
        }

        // Animate the background scrim
        ValueAnimator.ofArgb(Color.TRANSPARENT, Color.argb(90, 0, 0, 0)).apply {
            duration = 350
            addUpdateListener { animator ->
                windowContainerView?.setBackgroundColor(animator.animatedValue as Int)
            }
            start()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            windowParams?.blurBehindRadius = 60
        }
        
        setupOutsideTouchListener()
        setupInsetsListener()

        // --- Simplified Animation ---
        animatingIslandView?.visibility = View.VISIBLE
        val islandParams = animatingIslandView?.layoutParams as FrameLayout.LayoutParams
        islandParams.width = expandedWidth
        islandParams.height = FrameLayout.LayoutParams.WRAP_CONTENT // 使用自适应高度
        islandParams.topMargin = statusBarHeight
        animatingIslandView?.layoutParams = islandParams
        animatingIslandView?.background = ColorDrawable(Color.TRANSPARENT) // Make the container transparent

        animatingIslandView?.alpha = 0f

        animatingIslandView?.animate()
            ?.withLayer()
            ?.alpha(1f)
            ?.setInterpolator(AccelerateDecelerateInterpolator())
            ?.setDuration(350)
            ?.setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                     showConfigPanel()
                     populateAppSearchIcons()
                     appSearchIconScrollView?.visibility = View.VISIBLE

                     searchInput?.requestFocus()
                     if (settingsManager.isAutoPasteEnabled()) {
                         autoPaste(searchInput)
                     }
                     uiHandler.postDelayed({
                         val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                         imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
                     }, 100)
                }
            })
            ?.start()
    }

    private fun transitionToCompactState() {
        if (!isSearchModeActive) return
        isSearchModeActive = false

        cleanupExpandedViews()

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowContainerView?.windowToken, 0)
        searchInput?.clearFocus()
        searchInput?.setText("")

        // --- Simplified Animation ---
        animatingIslandView?.animate()
            ?.withLayer() // Treat as a single unit for animation
            ?.alpha(0f)
            ?.setInterpolator(AccelerateDecelerateInterpolator())
            ?.setDuration(350)
            ?.withEndAction {
                // All animations are done, now resize the window and clean up.
                animatingIslandView?.visibility = View.GONE
                appSearchIconScrollView?.visibility = View.GONE
                clearAppSearchIcons()

                // Now that animations are over, fully remove the config panel.
                if (configPanelView != null) {
                    try {
                        windowContainerView?.removeView(configPanelView)
                    } catch (e: Exception) { /* ignore */ }
                    configPanelView = null
                }

                val windowParams = windowContainerView?.layoutParams as? WindowManager.LayoutParams
                windowParams?.let {
                    it.width = expandedWidth
                    it.height = statusBarHeight * 2
                    it.flags = it.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        it.blurBehindRadius = 0
                    }
                    windowManager.updateViewLayout(windowContainerView, it)
                }

                // Ensure background is fully transparent after resize
                windowContainerView?.setBackgroundColor(Color.TRANSPARENT)

                // 确保小横条可见并重新设置
                // 不再需要显示代理指示器
                setupProxyIndicator()
                Log.d(TAG, "搜索状态结束，恢复小横条显示")
            }
            ?.start()

        // Also fade out the background scrim simultaneously
        ValueAnimator.ofArgb((windowContainerView?.background as? ColorDrawable)?.color ?: Color.argb(90, 0, 0, 0), Color.TRANSPARENT).apply {
            duration = 350
            addUpdateListener { animator ->
                windowContainerView?.setBackgroundColor(animator.animatedValue as Int)
            }
            start()
        }
    }
    
    private fun setupSearchListeners() {
        val searchAction = {
            performSearch()
            if (isEditingModeActive) {
                exitEditingMode()
            }
        }

        searchInput?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = searchInput?.text.toString().trim()
                if (query.isNotEmpty()) {
                    hideKeyboard(searchInput)
                    performSearch() // 使用统一的搜索逻辑
                }
                true
            } else {
                false
            }
        }

        searchButton?.setOnClickListener {
            searchAction()
        }
    }

    private fun performSearch() {
        val query = searchInput?.text.toString().trim()
        if (query.isEmpty()) return

        Log.d(TAG, "执行搜索: $query")
        lastSearchQuery = query

        // 检查是否有选中的APP
        if (currentSelectedApp != null) {
            // 有选中APP时，执行搜索动作并退出搜索面板
            handleSearchWithSelectedApp(query, currentSelectedApp!!)
            // 清除选中的APP，为下次搜索做准备
            currentSelectedApp = null
            return
        }

        // 没有选中APP时，搜索匹配的APP
        val appInfoManager = AppInfoManager.getInstance()
        val appResults = if (appInfoManager.isLoaded()) {
            appInfoManager.search(query)
        } else {
            emptyList()
        }

        if (appResults.isNotEmpty()) {
            // 找到匹配的APP，显示搜索结果
            Log.d(TAG, "显示应用搜索结果: ${appResults.map { it.label }}")
            showAppSearchResults(appResults)
        } else {
            // 没有找到匹配的APP，显示支持URL scheme的APP图标
            Log.d(TAG, "没有找到匹配的应用，显示URL scheme APP图标")
            showUrlSchemeAppIcons()
        }

        // 复制搜索文本到剪贴板
        copyTextToClipboard(query)
        
        // 显示提示
        Toast.makeText(this, "已复制搜索文本，请在APP中粘贴", Toast.LENGTH_SHORT).show()
        
        // 收起灵动岛
        transitionToCompactState()
    }

    private fun copyTextToClipboard(text: String) {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("搜索文本", text)
            clipboard.setPrimaryClip(clip)
            Log.d(TAG, "已复制文本到剪贴板: $text")
        } catch (e: Exception) {
            Log.e(TAG, "复制文本到剪贴板失败", e)
        }
    }

    private fun showDeepSeekResponse(text: String) {
        // 优先使用AI助手面板中的响应文本视图
        val aiResponseText = aiAssistantPanelView?.findViewById<TextView>(R.id.ai_response_text)
            ?: configPanelView?.findViewById<TextView>(R.id.ai_response_text)
        aiResponseText?.text = text
    }

    private fun callDeepSeekAPI(query: String) {
        // 调用DeepSeek API
        val aiApiManager = AIApiManager(this)
        
        aiApiManager.sendMessage(
            serviceType = AIServiceType.DEEPSEEK,
            message = query,
            conversationHistory = emptyList(),
            callback = object : AIApiManager.StreamingCallback {
                override fun onChunkReceived(chunk: String) {
                    uiHandler.post {
                        val aiResponseText = aiAssistantPanelView?.findViewById<TextView>(R.id.ai_response_text)
                            ?: configPanelView?.findViewById<TextView>(R.id.ai_response_text)
                        val currentText = aiResponseText?.text?.toString() ?: ""
                        aiResponseText?.text = currentText + chunk
                    }
                }
                
                override fun onComplete(fullResponse: String) {
                    uiHandler.post {
                        showDeepSeekResponse(fullResponse)
                    }
                }
                
                override fun onError(error: String) {
                    uiHandler.post {
                        showDeepSeekResponse("错误：$error\n\n请检查DeepSeek API密钥配置是否正确。")
                    }
                }
            }
        )
    }

    private fun updateWindowParamsForInput() {
        // 更新窗口参数以允许焦点和输入法
        windowContainerView?.let { view ->
            val params = view.layoutParams as WindowManager.LayoutParams
            // 移除FLAG_NOT_FOCUSABLE，允许焦点
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            // 添加FLAG_NOT_TOUCH_MODAL，允许输入法
            params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            try {
                windowManager?.updateViewLayout(view, params)
                Log.d(TAG, "窗口参数已更新以支持输入法")
            } catch (e: Exception) {
                Log.e(TAG, "更新窗口参数失败", e)
            }
        }
    }

    private fun setupAIServiceSpinner(spinner: Spinner?) {
        spinner?.let { sp ->
            val aiServices = listOf(
                "DeepSeek",
                "智谱AI", 
                "Kimi",
                "ChatGPT",
                "Claude",
                "Gemini",
                "文心一言",
                "通义千问",
                "讯飞星火"
            )
            
            val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, aiServices)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            sp.adapter = adapter
            
            // 设置默认选择
            sp.setSelection(0)
        }
    }

    private fun sendAIMessage(query: String, responseTextView: TextView?) {
        responseTextView?.text = "正在思考中..."
        
        // 获取当前选择的AI服务，优先使用AI助手面板中的选择器
        val spinner = aiAssistantPanelView?.findViewById<Spinner>(R.id.ai_service_spinner)
            ?: configPanelView?.findViewById<Spinner>(R.id.ai_service_spinner)
        val selectedService = spinner?.selectedItem?.toString() ?: "DeepSeek"
        
        // 将显示名称映射到AIServiceType
        val serviceType = when (selectedService) {
            "DeepSeek" -> AIServiceType.DEEPSEEK
            "智谱AI" -> AIServiceType.ZHIPU_AI
            "Kimi" -> AIServiceType.KIMI
            "ChatGPT" -> AIServiceType.CHATGPT
            "Claude" -> AIServiceType.CLAUDE
            "Gemini" -> AIServiceType.GEMINI
            "文心一言" -> AIServiceType.WENXIN
            "通义千问" -> AIServiceType.QIANWEN
            "讯飞星火" -> AIServiceType.XINGHUO
            else -> AIServiceType.DEEPSEEK
        }
        
        // 创建AI API管理器
        val aiApiManager = AIApiManager(this)
        
        // 调用真实API
        aiApiManager.sendMessage(
            serviceType = serviceType,
            message = query,
            conversationHistory = emptyList(),
            callback = object : AIApiManager.StreamingCallback {
                override fun onChunkReceived(chunk: String) {
                    uiHandler.post {
                        val currentText = responseTextView?.text?.toString() ?: ""
                        responseTextView?.text = currentText + chunk
                    }
                }
                
                override fun onComplete(fullResponse: String) {
                    uiHandler.post {
                        responseTextView?.text = fullResponse
                    }
                }
                
                override fun onError(error: String) {
                    uiHandler.post {
                        responseTextView?.text = "错误：$error\n\n请检查API密钥配置是否正确。"
                    }
                }
            }
        )
    }

    private fun addNotificationIcon(key: String, iconBitmap: android.graphics.Bitmap) {
        if (!activeNotifications.containsKey(key)) {
            val imageView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(48, 48).apply {
                    marginEnd = 8
                }
                setImageBitmap(iconBitmap)
            }
            activeNotifications[key] = imageView
            // UI update will be handled by showNotificationOnIndicator
        }
    }

    private fun removeNotificationIcon(key: String) {
        activeNotifications.remove(key)
        // UI update will be handled by show/clear NotificationOnIndicator
    }

    private fun showNotificationOnIndicator(icon: android.graphics.Bitmap, title: String, text: String) {
        // 不再需要显示通知图标，因为现在有按钮
    }

    private fun clearNotificationOnIndicator() {
        // 不再需要清除通知图标，因为现在有按钮
    }

    private fun updateIslandVisibility() {
        val hasNotifications = activeNotifications.isNotEmpty()
        val hasAppSearchIcons = appSearchIconContainer?.childCount ?: 0 > 0
        
        // 始终显示按钮容器和灵动岛本身
        val buttonContainer = islandContentView?.findViewById<LinearLayout>(R.id.button_container)
        buttonContainer?.visibility = View.VISIBLE
        animatingIslandView?.visibility = View.VISIBLE
        
        if (hasNotifications && !isSearchModeActive) {
            // 显示通知图标，隐藏应用搜索图标
            notificationIconContainer.visibility = View.VISIBLE
            appSearchIconScrollView?.visibility = View.GONE
            
            // 动画展开小横条以适应通知图标
            animateIslandExpansion(calculateNotificationWidth())
        } else if (hasAppSearchIcons && isSearchModeActive) {
            // 搜索模式下显示应用图标
            notificationIconContainer.visibility = View.GONE
            appSearchIconScrollView?.visibility = View.VISIBLE
        } else {
            // 都隐藏，保持紧凑状态，但按钮容器和灵动岛始终显示
            notificationIconContainer.visibility = View.GONE
            appSearchIconScrollView?.visibility = View.GONE
        }
        
        windowContainerView?.visibility = View.VISIBLE
    }

    private fun animateIslandExpansion(targetWidth: Int) {
        val currentWidth = animatingIslandView?.width ?: compactWidth
        if (currentWidth == targetWidth) return
        
        ValueAnimator.ofInt(currentWidth, targetWidth).apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val width = animator.animatedValue as Int
                animatingIslandView?.layoutParams?.width = width
                animatingIslandView?.requestLayout()
            }
            start()
        }
    }

    private fun calculateNotificationWidth(): Int {
        val iconSize = getStatusBarHeight() - 16.dpToPx()
        val iconMargin = 8.dpToPx()
        val padding = 32.dpToPx()
        val notificationCount = activeNotifications.size.coerceAtMost(5) // 最多显示5个图标
        return (notificationCount * (iconSize + iconMargin)) + padding
    }

    private fun showNotificationExpandedView() {
        if (configPanelView != null) return // 如果已经有面板显示，不重复显示
        
        val themedContext = ContextThemeWrapper(getThemedContext(), R.style.Theme_FloatingWindow)
        val inflater = LayoutInflater.from(themedContext)
        configPanelView = inflater.inflate(R.layout.notification_expanded_panel, null)
        
        setupNotificationExpandedView()
        
        val panelParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP
        ).apply {
            topMargin = statusBarHeight + 60.dpToPx()
            leftMargin = 16.dpToPx()
            rightMargin = 16.dpToPx()
        }

        // Add the panel to the window
        windowContainerView?.addView(configPanelView, panelParams)
        configPanelView?.apply {
            alpha = 0f
            translationY = -100f
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(300)
                .start()
        }
    }

    private fun setupNotificationExpandedView() {
        val notificationList = configPanelView?.findViewById<RecyclerView>(R.id.notification_list)
        val closeButton = configPanelView?.findViewById<ImageButton>(R.id.close_notification_panel)
        
        // 设置关闭按钮
        closeButton?.setOnClickListener {
            hideNotificationExpandedView()
        }
        
        // 设置通知列表
        val notifications = getRecentNotifications()
        val adapter = NotificationAdapter(notifications) { notification, selectedText ->
            onNotificationTextSelected(notification, selectedText)
        }
        
        notificationList?.layoutManager = LinearLayoutManager(this)
        notificationList?.adapter = adapter
    }

    private fun hideNotificationExpandedView() {
        configPanelView?.animate()
            ?.alpha(0f)
            ?.translationY(-100f)
            ?.setDuration(200)
            ?.withEndAction {
                windowContainerView?.removeView(configPanelView)
                configPanelView = null
            }
            ?.start()
    }

    private fun getRecentNotifications(): List<NotificationInfo> {
        val notifications = mutableListOf<NotificationInfo>()
        
        // 从NotificationListener的存储中获取真实通知数据
        val storedNotifications = NotificationListener.getAllNotifications()
        
        storedNotifications.forEach { notificationData ->
            notifications.add(NotificationInfo(
                key = notificationData.key,
                packageName = notificationData.packageName,
                title = notificationData.title,
                text = notificationData.text,
                subText = notificationData.subText,
                bigText = notificationData.bigText,
                icon = notificationData.icon,
                largeIcon = null,
                actions = emptyList(),
                timestamp = notificationData.timestamp
            ))
        }
        
        // 如果没有真实通知，添加一些示例数据用于测试
        if (notifications.isEmpty() && activeNotifications.isNotEmpty()) {
            notifications.add(NotificationInfo(
                key = "test_notification_1",
                packageName = "com.example.test",
                title = "测试通知",
                text = "这是一个测试通知内容，可以点击提取关键词进行搜索",
                subText = "",
                bigText = "",
                icon = null,
                largeIcon = null,
                actions = emptyList(),
                timestamp = System.currentTimeMillis()
            ))
        }
        
        return notifications.take(10) // 最多显示10个最近的通知
    }

    private fun onNotificationTextSelected(notification: NotificationInfo, selectedText: String) {
        // 将选中的文本填入搜索框
        transitionToSearchState()
        
        // 延迟设置文本，确保搜索界面已经创建
        uiHandler.postDelayed({
            searchInput?.setText(selectedText)
            searchInput?.setSelection(selectedText.length)
        }, 100)
        
        // 隐藏通知展开面板
        hideNotificationExpandedView()
    }

    // 数据类定义
    data class NotificationInfo(
        val key: String,
        val packageName: String,
        val title: String,
        val text: String,
        val subText: String,
        val bigText: String,
        val icon: android.graphics.Bitmap?,
        val largeIcon: android.graphics.Bitmap?,
        val actions: List<NotificationAction>,
        val timestamp: Long
    )

    data class NotificationAction(
        val title: String,
        val actionIntent: android.app.PendingIntent?
    )

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId)
        else (24 * resources.displayMetrics.density).toInt()
    }

    private fun getDomainForApp(appId: String): String {
        return when (appId) {
            "wechat" -> "weixin.qq.com"
            "taobao" -> "taobao.com"
            "pdd" -> "pinduoduo.com"
            "douyin" -> "douyin.com"
            "xiaohongshu" -> "xiaohongshu.com"
            else -> ""
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density + 0.5f).toInt()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        Log.d(TAG, "DynamicIslandService 停止")
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(notificationReceiver)
        unregisterReceiver(appSearchUpdateReceiver)
        unregisterReceiver(positionUpdateReceiver)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(messageReceiver)
        cleanupViews()
        hideEditingScrim()
        hidePasteButton()
        proxyIndicatorAnimator?.cancel()
        savePositionRunnable?.let { uiHandler.removeCallbacks(it) }
        
        // 清理剪贴板监听器
        cleanupClipboardListener()
        
        // 清理应用切换监听器
        cleanupAppSwitchListener()
    }

    private fun cleanupViews() {
        try {
            // 不再需要清理代理指示器

            if (windowContainerView?.isAttachedToWindow == true) {
                windowManager.removeView(windowContainerView)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing windowContainerView", e)
        }

        // Nullify all view references to allow them to be garbage collected
        // and to ensure showDynamicIsland creates new ones.
        windowContainerView = null
        animatingIslandView = null
        islandContentView = null
        configPanelView = null
        searchEngineSelectorView = null
        assistantSelectorView = null
        // 不再需要代理指示器
        assistantPromptSelectorView = null
        selectorScrimView = null
        aiAssistantPanelView = null
        editingScrimView = null
        searchInput = null
        searchButton = null
        selectedAssistantTextView = null
    }

    private fun showConfigPanel() {
        if (configPanelView != null || isEditingModeActive) return

        val themedContext = ContextThemeWrapper(getThemedContext(), R.style.Theme_FloatingWindow)
        configPanelView = LayoutInflater.from(themedContext).inflate(R.layout.dynamic_island_config_panel, null)

        searchInput = configPanelView?.findViewById(R.id.search_input)
        searchButton = configPanelView?.findViewById(R.id.search_button)
        selectedAssistantTextView = configPanelView?.findViewById(R.id.selected_assistant_text)
        setupSearchListeners()
        initSearchInputListener()

        // Setup App Search Results Views
        appSearchResultsContainer = configPanelView?.findViewById(R.id.app_search_results_container)
        appSearchRecyclerView = configPanelView?.findViewById(R.id.app_search_results_recycler_view)
        closeAppSearchButton = configPanelView?.findViewById(R.id.close_app_search_button)
        closeAppSearchButton?.setOnClickListener {
            hideAppSearchResults()
        }
        
        // 初始化时显示常用APP图标
        showDefaultAppIcons()
        
        // 初始化最近选中的APP按钮
        recentAppButton = configPanelView?.findViewById<MaterialButton>(R.id.recent_app_button)
        recentAppButton?.setOnClickListener {
            showRecentAppsDropdown()
        }
        
        // 恢复当前选中的APP
        restoreCurrentSelectedApp()
        
        // 设置点击其他位置退出功能
        val configPanelRoot = configPanelView?.findViewById<MaterialCardView>(R.id.config_panel_root)
        configPanelRoot?.setOnClickListener { view ->
            // 检查点击的是否是面板本身（而不是子元素）
            if (view.id == R.id.config_panel_root) {
                Log.d(TAG, "点击搜索面板其他位置，关闭面板")
                hideConfigPanel()
            }
        }

        // AI助手功能已移动到独立的AI助手面板中
        // 这里不再需要设置AI相关的UI组件

        // Set initial state and add listener for the send button's alpha
        searchButton?.alpha = 0.5f // Start as semi-transparent
        // 移除重复的TextWatcher，使用统一的initSearchInputListener

        searchInput?.setOnFocusChangeListener { _, hasFocus ->
            // If the input field loses focus, we should hide the paste button.
            // Showing the button is now handled when the panel appears.
            if (!hasFocus) {
                hidePasteButton()
            } else {
                // 当获得焦点时，强制显示输入法
                uiHandler.postDelayed({
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY)
                }, 100)
            }
        }

        // 添加点击监听器，确保点击时显示输入法
        searchInput?.setOnClickListener {
            searchInput?.requestFocus()
            uiHandler.postDelayed({
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY)
            }, 100)
        }

        // We abandon ActionMode as it's unreliable in overlays.
        // Instead, we manually show our popup menu on long click.
        searchInput?.setOnLongClickListener {
            // We post this to the handler to ensure the default text selection
            // has occurred before we try to position our menu.
            uiHandler.post { showCustomTextMenu() }
            true // Consume the event
        }

        // --- New UI Setup for Triple Browser Preview ---
        pagePreview1 = configPanelView?.findViewById(R.id.page_preview_1)
        pagePreview2 = configPanelView?.findViewById(R.id.page_preview_2)
        pagePreview3 = configPanelView?.findViewById(R.id.page_preview_3)

        // Dynamically set the width of each page preview to ensure the container is scrollable
        val screenWidth = resources.displayMetrics.widthPixels
        val previewWidth = (screenWidth * 0.35).toInt() // Each preview is 35% of screen width, total 105%
        pagePreview1?.layoutParams?.width = previewWidth
        pagePreview2?.layoutParams?.width = previewWidth
        pagePreview3?.layoutParams?.width = previewWidth

        pagePreview1?.setOnClickListener { showSearchEngineSelector(it, 1) }
        pagePreview2?.setOnClickListener { showSearchEngineSelector(it, 2) }
        pagePreview3?.setOnClickListener { showSearchEngineSelector(it, 3) }

        pagePreview1?.findViewById<View>(R.id.page_clear_button)?.setOnClickListener { it.cancelPendingInputEvents(); clearSlot(1) }
        pagePreview2?.findViewById<View>(R.id.page_clear_button)?.setOnClickListener { it.cancelPendingInputEvents(); clearSlot(2) }
        pagePreview3?.findViewById<View>(R.id.page_clear_button)?.setOnClickListener { it.cancelPendingInputEvents(); clearSlot(3) }

        setupCustomScrollbar()
        
        updateAllMiniPages()
        // --- End of New UI Setup ---
        
        val animationView = configPanelView?.findViewById<LottieAnimationView>(R.id.config_panel_animation)

        val selectAssistantButton = configPanelView?.findViewById<View>(R.id.btn_select_assistant)
        selectAssistantButton?.setOnClickListener {
            animationView?.apply {
                visibility = View.VISIBLE
                playAnimation()
            }
            showAssistantSelector()
        }

        val generatePromptButton = configPanelView?.findViewById<View>(R.id.btn_generate_prompt)
        generatePromptButton?.setOnClickListener {
            animationView?.apply {
                visibility = View.VISIBLE
                playAnimation()
            }
            showPromptProfileSelector()
        }

        animationView?.addAnimatorListener(object : Animator.AnimatorListener {
            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationEnd(animation: Animator) {
                animationView.visibility = View.GONE
            }
            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })

        val addPromptButton = configPanelView?.findViewById<View>(R.id.btn_add_master_prompt)
        addPromptButton?.setOnClickListener {
            val activeProfile = getActiveProfile()
            activeProfile?.let {
                val currentText = searchInput?.text?.toString() ?: ""
                val masterPrompt = settingsManager.generateMasterPrompt() // This uses the active profile
                searchInput?.setText("$currentText $masterPrompt")
                searchInput?.setSelection(searchInput?.text?.length ?: 0)
                Toast.makeText(this, "已添加提示词", Toast.LENGTH_SHORT).show()
            } ?: run {
                Toast.makeText(this, "请先在AI指令中心设置档案", Toast.LENGTH_SHORT).show()
            }
        }

        // 退出按钮点击处理
        val exitButton = configPanelView?.findViewById<View>(R.id.btn_settings)
        exitButton?.setOnClickListener {
            animationView?.apply {
                visibility = View.VISIBLE
                playAnimation()
            }

            // 添加点击动画效果
            exitButton.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    exitButton.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                }
                .start()

            // 退出搜索面板
            hideConfigPanel()
        }

        // 计算灵动岛的位置，让搜索面板从灵动岛下方展开
        val islandY = statusBarHeight + compactHeight + 16 // 灵动岛下方16dp
        val screenHeight = resources.displayMetrics.heightPixels
        val maxPanelHeight = screenHeight - islandY - 100 // 留出底部空间给输入法

        val panelParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            maxPanelHeight,
            Gravity.TOP
        ).apply {
            topMargin = islandY
            leftMargin = 16.dpToPx()
            rightMargin = 16.dpToPx()
        }

        // Add the panel to the window
        windowContainerView?.addView(configPanelView, panelParams)
        
        // 更新窗口参数以允许焦点和输入法
        updateWindowParamsForInput()
        
        configPanelView?.apply {
            alpha = 0f
            translationY = -200f // 从上方滑入
            scaleX = 0.9f
            scaleY = 0.9f
            animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(300)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .withEndAction {
                    // 动画完成后显示输入法
                    searchInput?.requestFocus()
                    uiHandler.postDelayed({
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        // 使用更强制的方法显示输入法
                        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY)
                    }, 200)
                    
                    // 不再自动粘贴到搜索框，因为已移除输入框
                }
                .start()
        }

        // Automatically show the paste button when the config panel appears.
        showPasteButton()
        
        // 延迟显示输入法，确保面板完全显示后再显示输入法
        uiHandler.postDelayed({
            searchInput?.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, InputMethodManager.HIDE_IMPLICIT_ONLY)
        }, 500)
    }

    private fun showCustomTextMenu() {
        if (textActionMenu?.isShowing == true) {
            // If it's already showing, hide and re-show to update position.
            hideCustomTextMenu()
        }

        val editText = searchInput ?: return
        val context = editText.context

        val inflater = LayoutInflater.from(context)
        val menuView = inflater.inflate(R.layout.custom_text_menu, null)

        val cut = menuView.findViewById<TextView>(R.id.action_cut)
        val copy = menuView.findViewById<TextView>(R.id.action_copy)
        val paste = menuView.findViewById<TextView>(R.id.action_paste)
        val selectAll = menuView.findViewById<TextView>(R.id.action_select_all)

        cut.setOnClickListener {
            editText.onTextContextMenuItem(android.R.id.cut)
            hideCustomTextMenu()
        }
        copy.setOnClickListener {
            editText.onTextContextMenuItem(android.R.id.copy)
            hideCustomTextMenu()
        }
        paste.setOnClickListener {
            editText.onTextContextMenuItem(android.R.id.paste)
            hideCustomTextMenu()
        }
        selectAll.setOnClickListener {
            editText.onTextContextMenuItem(android.R.id.selectAll)
            hideCustomTextMenu()
        }

        // Make the popup focusable and dismissable on outside touch.
        textActionMenu = PopupWindow(menuView, WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, true).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // PopupWindow不需要设置窗口类型，它会自动继承父窗口的类型
        }

        val layout = editText.layout ?: return
        val startOffset = editText.selectionStart
        val endOffset = editText.selectionEnd

        // Only show menu if there is a selection or if the whole text is not empty
        if (startOffset == endOffset && editText.text.isEmpty()) {
            return
        }

        val line = layout.getLineForOffset(startOffset)
        val x = layout.getPrimaryHorizontal(startOffset) + editText.paddingLeft
        val y = layout.getLineTop(line) + editText.paddingTop

        val location = IntArray(2)
        editText.getLocationOnScreen(location)
        val screenX = location[0] + x.toInt()
        val screenY = location[1] + y.toInt()

        menuView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val menuHeight = menuView.measuredHeight
        val finalY = screenY - menuHeight - 16 // Position above text with a small margin

        textActionMenu?.showAtLocation(editText, Gravity.NO_GRAVITY, screenX, finalY)
    }

    private fun hideCustomTextMenu() {
        textActionMenu?.dismiss()
        textActionMenu = null
    }

    private fun cleanupExpandedViews() {
        if (configPanelView != null) {
            configPanelView?.visibility = View.GONE
            // Don't remove the view or null the reference yet. Postpone to after animation.
            pagePreview1 = null
            pagePreview2 = null
            pagePreview3 = null
        }
        dismissSearchEngineSelector()
        dismissAssistantSelectorPanel()
    }

    private fun showSearchEngineSelector(anchorView: View, slotIndex: Int) {
        dismissSearchEngineSelector()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurEffect = RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP)
            animatingIslandView?.setRenderEffect(blurEffect)
            configPanelView?.setRenderEffect(blurEffect)
        }
        configPanelView?.visibility = View.VISIBLE

        val themedContext = ContextThemeWrapper(getThemedContext(), R.style.Theme_FloatingWindow)
        val inflater = LayoutInflater.from(themedContext)
        val panelWrapper = inflater.inflate(R.layout.search_engine_panel_wrapper, null) as com.google.android.material.card.MaterialCardView

        // --- Programmatically create Tabbed UI ---
        val mainLayout = LinearLayout(themedContext).apply {
            orientation = LinearLayout.VERTICAL
        }

        val tabLayout = TabLayout(themedContext).apply {
            val accentColor = getColorFromAttr(themedContext, com.google.android.material.R.attr.colorAccent)
            val textColor = getColorFromAttr(themedContext, com.google.android.material.R.attr.colorOnSurface)
            val mutedTextColor = Color.argb(
                (Color.alpha(textColor) * 0.7f).toInt(),
                Color.red(textColor),
                Color.green(textColor),
                Color.blue(textColor)
            )

            setSelectedTabIndicatorColor(accentColor)
            setTabTextColors(mutedTextColor, textColor)
        }

        val regularEnginesRecyclerView = RecyclerView(themedContext)
        val aiEnginesRecyclerView = RecyclerView(themedContext).apply { visibility = View.GONE }

        val recyclerContainer = FrameLayout(themedContext).apply {
            addView(regularEnginesRecyclerView)
            addView(aiEnginesRecyclerView)
        }

        // Use a fixed height for the container to ensure it's scrollable and doesn't overflow
        val containerHeight = (resources.displayMetrics.heightPixels * 0.4).toInt()
        mainLayout.addView(tabLayout, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        mainLayout.addView(recyclerContainer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, containerHeight))

        panelWrapper.addView(mainLayout)
        searchEngineSelectorView = panelWrapper

        // --- Data Loading and Adapter Setup ---
        val allCategories = loadSearchCategories()
        val regularEngines = allCategories.find { it.title == "普通搜索引擎" }?.engines ?: emptyList()
        val aiEngines = allCategories.find { it.title == "AI 搜索引擎" }?.engines ?: emptyList()

        regularEnginesRecyclerView.layoutManager = LinearLayoutManager(themedContext)
        regularEnginesRecyclerView.adapter = EngineAdapter(regularEngines) { selectedEngine ->
            selectSearchEngineForSlot(selectedEngine, slotIndex)
        }

        aiEnginesRecyclerView.layoutManager = LinearLayoutManager(themedContext)
        aiEnginesRecyclerView.adapter = EngineAdapter(aiEngines) { selectedEngine ->
            selectSearchEngineForSlot(selectedEngine, slotIndex)
        }

        // --- Setup Tabs ---
        tabLayout.addTab(tabLayout.newTab().setText("普通搜索"))
        tabLayout.addTab(tabLayout.newTab().setText("AI 搜索"))

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> {
                        regularEnginesRecyclerView.visibility = View.VISIBLE
                        aiEnginesRecyclerView.visibility = View.GONE
                    }
                    1 -> {
                        regularEnginesRecyclerView.visibility = View.GONE
                        aiEnginesRecyclerView.visibility = View.VISIBLE
                    }
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Default to the first tab that has content
        if (regularEngines.isNotEmpty()) {
            tabLayout.getTabAt(0)?.select()
            regularEnginesRecyclerView.visibility = View.VISIBLE
            aiEnginesRecyclerView.visibility = View.GONE
        } else {
            tabLayout.getTabAt(1)?.select()
            regularEnginesRecyclerView.visibility = View.GONE
            aiEnginesRecyclerView.visibility = View.VISIBLE
        }


        selectorScrimView = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setOnClickListener { dismissSearchEngineSelector() }
        }
        try {
             windowContainerView?.addView(selectorScrimView)
        } catch (e: Exception) { e.printStackTrace() }

        val selectorParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = 16.dpToPx()
        }

        try {
            windowContainerView?.addView(searchEngineSelectorView, selectorParams)
            searchEngineSelectorView?.apply {
                alpha = 0f
                val finalTranslationY = -currentKeyboardHeight.toFloat()
                translationY = finalTranslationY + 100f
                animate()
                    .withLayer()
                    .alpha(1f)
                    .translationY(finalTranslationY)
                    .setDuration(350)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun selectSearchEngineForSlot(engine: SearchEngine, slotIndex: Int) {
        activeSlots[slotIndex] = engine
        updateMiniPage(slotIndex)
        dismissSearchEngineSelector()
    }

    private fun clearSlot(slotIndex: Int) {
        activeSlots.remove(slotIndex)
        updateMiniPage(slotIndex)
    }

    private fun updateAllMiniPages() {
        updateMiniPage(1)
        updateMiniPage(2)
        updateMiniPage(3)
    }

    private fun updateMiniPage(pageIndex: Int) {
        val pageView = when(pageIndex) {
            1 -> pagePreview1
            2 -> pagePreview2
            3 -> pagePreview3
            else -> null
        } ?: return

        val engine = activeSlots[pageIndex]

        val hintState = pageView.findViewById<View>(R.id.page_hint_state)
        val filledContent = pageView.findViewById<View>(R.id.page_filled_content)
        val clearButton = pageView.findViewById<View>(R.id.page_clear_button)
        val aiPromptInfo = pageView.findViewById<TextView>(R.id.page_ai_prompt_info)

        if (engine != null) {
            hintState.visibility = View.GONE
            filledContent.visibility = View.VISIBLE
            clearButton.visibility = View.VISIBLE

            val icon = filledContent.findViewById<ImageView>(R.id.page_icon)
            val title = filledContent.findViewById<TextView>(R.id.page_title)

            FaviconLoader.loadIcon(icon, engine.searchUrl, engine.iconResId)
            title.text = engine.name

            val enabledAIEngineNames = settingsManager.getEnabledAIEngines()
            val isAiEngine = enabledAIEngineNames.contains(engine.name)

            if (isAiEngine) {
                aiPromptInfo.visibility = View.VISIBLE
            } else {
                aiPromptInfo.visibility = View.GONE
            }

        } else {
            hintState.visibility = View.VISIBLE
            filledContent.visibility = View.GONE
            clearButton.visibility = View.GONE
            aiPromptInfo.visibility = View.GONE

            // Programmatically set the "+" icon tint to ensure it respects the theme
            val addIcon = hintState.findViewById<ImageView>(R.id.iv_add_icon)
            val themedColor = getColorFromAttr(pageView.context, com.google.android.material.R.attr.colorOnSurface)
            addIcon.setColorFilter(themedColor)
        }
        updateGlobalHintVisibility()
    }

    private fun updateGlobalHintVisibility() {
        val globalHint = configPanelView?.findViewById<TextView>(R.id.global_hint_text)
        if (activeSlots.isEmpty()) {
            globalHint?.visibility = View.VISIBLE
        } else {
            globalHint?.visibility = View.GONE
        }
    }

    private fun setupCustomScrollbar() {
        val scrollView = configPanelView?.findViewById<HorizontalScrollView>(R.id.triple_browser_scrollview)
        val scrollThumb = configPanelView?.findViewById<View>(R.id.scrollbar_thumb)
        val contentLayout = configPanelView?.findViewById<LinearLayout>(R.id.triple_browser_linear_layout)

        scrollView?.post {
            val contentWidth = contentLayout?.width ?: 0
            val scrollViewWidth = scrollView.width

            if (contentWidth > scrollViewWidth) {
                configPanelView?.findViewById<View>(R.id.scrollbar_track)?.visibility = View.VISIBLE
                scrollThumb?.visibility = View.VISIBLE

                val thumbWidth = (scrollViewWidth.toFloat() / contentWidth * scrollViewWidth).toInt()
                scrollThumb?.layoutParams?.width = thumbWidth
                scrollThumb?.requestLayout()

                scrollView.setOnScrollChangeListener { _, scrollX, _, _, _ ->
                    val scrollRange = contentWidth - scrollViewWidth
                    if (scrollRange > 0) {
                        val thumbRange = scrollViewWidth - thumbWidth
                        val thumbX = (scrollX.toFloat() / scrollRange * thumbRange)
                        scrollThumb?.translationX = thumbX
                    }
                }
            } else {
                // Hide scrollbar if content is not wide enough to scroll
                configPanelView?.findViewById<View>(R.id.scrollbar_track)?.visibility = View.GONE
                scrollThumb?.visibility = View.GONE
            }
        }
    }

    private fun dismissSearchEngineSelector() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            animatingIslandView?.setRenderEffect(null)
            configPanelView?.setRenderEffect(null)
        }
        configPanelView?.visibility = View.VISIBLE

        val viewToRemove = searchEngineSelectorView
        val scrimToRemove = selectorScrimView
        
        searchEngineSelectorView = null
        selectorScrimView = null
        
        if (viewToRemove == null) return

        viewToRemove.animate()
            .withLayer()
            .alpha(0f)
            .translationY(100f)
            .setDuration(250)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                try {
                    windowContainerView?.removeView(viewToRemove)
                    scrimToRemove?.let { windowContainerView?.removeView(it) }
                } catch (e: Exception) { /* ignore */ }
            }
            .start()
    }

    private fun showAssistantSelector() {
        if (assistantSelectorView != null) return

        // Inflate views using a themed context
        val themedContext = ContextThemeWrapper(getThemedContext(), R.style.Theme_FloatingWindow)
        val inflater = LayoutInflater.from(themedContext)
        assistantSelectorView = inflater.inflate(R.layout.assistant_selector_panel, null)

        // Setup RecyclerView
        val recyclerView = assistantSelectorView?.findViewById<RecyclerView>(R.id.assistant_recycler_view)
        val layoutManager = androidx.recyclerview.widget.GridLayoutManager(themedContext, 4)
        val adapter = AssistantCategoryAdapter(
            categories = AssistantPrompts.categories,
            onCategoryClick = { category ->
                showAssistantPromptPanel(category)
            },
            onPromptClick = {
                // This shouldn't be called from the main category adapter anymore,
                // but we keep a dummy implementation for safety.
            }
        )

        layoutManager.spanSizeLookup = object : androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                // This is now simplified as we only show categories.
                return 1 // Always 1 for TYPE_CATEGORY
            }
        }

        recyclerView?.layoutManager = layoutManager
        recyclerView?.adapter = adapter

        // Create and add scrim view
        selectorScrimView = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setOnClickListener { dismissAssistantSelectorPanel() } // Dismiss all panels
        }
        windowContainerView?.addView(selectorScrimView)

        // Define layout params for the panel
        val panelParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            // Position below the main island view with a small margin
            topMargin = statusBarHeight + getIslandActualHeight() + 16.dpToPx()
        }

        // Add panel to the window and animate it in
        windowContainerView?.addView(assistantSelectorView, panelParams)
        assistantSelectorView?.apply {
            alpha = 0f
            translationY = -100f // Start above and slide down
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(350)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
    }

    private fun showAssistantPromptPanel(category: AssistantCategory) {
        // Hide the main selector
        assistantSelectorView?.visibility = View.GONE

        // Inflate the sub-panel view
        val themedContext = ContextThemeWrapper(getThemedContext(), R.style.Theme_FloatingWindow)
        val inflater = LayoutInflater.from(themedContext)
        assistantPromptSelectorView = inflater.inflate(R.layout.assistant_submenu_panel, null)

        // Configure the sub-panel
        val titleView = assistantPromptSelectorView?.findViewById<TextView>(R.id.submenu_title)
        val backButton = assistantPromptSelectorView?.findViewById<ImageView>(R.id.back_button)
        val recyclerView = assistantPromptSelectorView?.findViewById<RecyclerView>(R.id.assistant_prompt_recycler_view)

        titleView?.text = category.name
        backButton?.setOnClickListener {
            dismissAssistantPromptPanel()
        }

        recyclerView?.layoutManager = LinearLayoutManager(themedContext)
        recyclerView?.adapter = AssistantPromptAdapter(category.assistants) { selectedPrompt ->
            this.selectedAssistantPrompt = selectedPrompt
            updateSelectedAssistantUI()
            dismissAssistantSelectorPanel() // Dismiss all panels
        }

        // Use the same params as the main panel
        val panelParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = statusBarHeight + getIslandActualHeight() + 16.dpToPx()
        }

        windowContainerView?.addView(assistantPromptSelectorView, panelParams)
        assistantPromptSelectorView?.apply {
            alpha = 0f
            translationY = -100f
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(350)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .start()
        }
    }

    private fun dismissAssistantPromptPanel() {
        val panelToRemove = assistantPromptSelectorView
        assistantPromptSelectorView = null

        panelToRemove?.let { panel ->
            panel.animate()
                .alpha(0f)
                .translationY(-100f)
                .setDuration(250)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    try {
                        windowContainerView?.removeView(panel)
                    } catch (e: Exception) { /* ignore */ }
                }
                .start()
        }

        // Show the main selector again
        assistantSelectorView?.visibility = View.VISIBLE
        assistantSelectorView?.animate()?.alpha(1f)?.setDuration(150)?.start()
    }

    private fun dismissAssistantSelectorPanel() {
        // This function now dismisses ALL assistant panels and the scrim.
        val mainPanelToRemove = assistantSelectorView
        val subPanelToRemove = assistantPromptSelectorView
        val scrimToRemove = selectorScrimView

        assistantSelectorView = null
        assistantPromptSelectorView = null
        selectorScrimView = null

        val dismissAction = { view: View?, isSubPanel: Boolean ->
            view?.animate()
                ?.alpha(0f)
                ?.translationY(if (isSubPanel) -50f else -100f)
                ?.setDuration(250)
                ?.setInterpolator(AccelerateDecelerateInterpolator())
                ?.withEndAction {
                    try {
                        windowContainerView?.removeView(view)
                    } catch (e: Exception) { /* ignore */ }
                }
                ?.start()
        }

        dismissAction(mainPanelToRemove, false)
        dismissAction(subPanelToRemove, true)

        scrimToRemove?.let { scrim ->
            scrim.animate().alpha(0f).setDuration(250).withEndAction {
                try {
                    windowContainerView?.removeView(scrim)
                } catch (e: Exception) { /* ignore */ }
            }.start()
        }

        // After dismissing all panels, restore the config panel.
        configPanelView?.visibility = View.VISIBLE
    }

    private fun updateSelectedAssistantUI() {
        if (selectedAssistantPrompt != null) {
            selectedAssistantTextView?.text = "当前助手: ${selectedAssistantPrompt!!.name}"
            selectedAssistantTextView?.visibility = View.VISIBLE
        } else {
            selectedAssistantTextView?.visibility = View.GONE
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            "island_width" -> {
                // 重新计算并更新视图大小
                val displayMetrics = resources.displayMetrics
                val islandWidth = settingsManager.getIslandWidth()
                
                // 强制设置合适的宽度，确保四个按钮能够完整显示
                val minRequiredWidth = 240 // 240dp，确保四个按钮能够完整显示
                val actualWidth = maxOf(islandWidth, minRequiredWidth)
                
                compactWidth = (actualWidth * displayMetrics.density).toInt()
                
                Log.d(TAG, "灵动岛宽度设置已变更: 原始宽度=${islandWidth}dp, 实际宽度=${actualWidth}dp, 像素宽度=${compactWidth}px")
                // 强制重启灵动岛以应用新宽度
                forceRestartIsland()
            }
            "island_position" -> {
                updateProxyIndicatorPosition()
            }
            "island_enhanced_layout" -> {
                Log.d(TAG, "灵动岛增强版布局设置已变更，重新创建视图")
                // 重新创建灵动岛视图以应用新的布局
                uiHandler.post {
                    cleanupViews()
                    showDynamicIsland()
                }
            }
        }
    }

    private fun setupOutsideTouchListener() {
        windowContainerView?.setOnTouchListener { _, event ->
            when {
                // 1. 文本操作菜单显示时，处理触摸事件
                textActionMenu?.isShowing == true -> {
                    hideCustomTextMenu()
                    return@setOnTouchListener true
                }
                
                // 2. 圆球状态下的触摸处理
                ballView != null && ballView?.visibility == View.VISIBLE -> {
                    handleBallTouchEvent(event)
                }
                
                // 3. 搜索模式激活时，检查是否在外部区域
                isSearchModeActive && event.action == MotionEvent.ACTION_DOWN -> {
                    if (isTouchOutsideAllViews(event)) {
                        transitionToCompactState()
                        return@setOnTouchListener true // 消费掉外部点击事件
                    } else {
                        return@setOnTouchListener false // 在内部区域，让子视图处理
                    }
                }
                
                // 4. 紧凑模式下的触摸处理
                !isSearchModeActive && event.action == MotionEvent.ACTION_DOWN -> {
                    if (isTouchInIslandArea(event)) {
                        // 在灵动岛区域内，展开搜索模式
                        expandIsland()
                        return@setOnTouchListener true
                    } else {
                        // 在灵动岛区域外，让事件穿透
                        return@setOnTouchListener false
                    }
                }
                
                // 5. 其他情况，让事件穿透到下层
                else -> false
            }
        }
    }

    private fun isTouchOutsideAllViews(event: MotionEvent): Boolean {
        val x = event.rawX.toInt()
        val y = event.rawY.toInt()

        // 检查触摸点是否在灵动岛主体内部
        val islandRect = android.graphics.Rect()
        animatingIslandView?.getGlobalVisibleRect(islandRect)
        if (islandRect.contains(x, y)) return false

        // 检查触摸点是否在配置面板内部
        val configRect = android.graphics.Rect()
        configPanelView?.let {
            if (it.isShown) {
                it.getGlobalVisibleRect(configRect)
                if (configRect.contains(x, y)) return false
            }
        }

        // 检查触摸点是否在搜索引擎选择器内部
        val selectorRect = android.graphics.Rect()
        searchEngineSelectorView?.let {
            if (it.isShown) {
                it.getGlobalVisibleRect(selectorRect)
                if (selectorRect.contains(x, y)) return false
            }
        }

        // 如果触摸点不在任何一个UI视图内，则视为外部点击
        return true
    }
    
    /**
     * 检查触摸是否在灵动岛区域内
     */
    private fun isTouchInIslandArea(event: MotionEvent): Boolean {
        val x = event.rawX.toInt()
        val y = event.rawY.toInt()

        // 检查触摸点是否在灵动岛主体内部
        val islandRect = android.graphics.Rect()
        animatingIslandView?.getGlobalVisibleRect(islandRect)
        if (islandRect.contains(x, y)) return true

        // 检查触摸点是否在配置面板内部
        val configRect = android.graphics.Rect()
        configPanelView?.let {
            if (it.isShown) {
                it.getGlobalVisibleRect(configRect)
                if (configRect.contains(x, y)) return true
            }
        }

        // 检查触摸点是否在搜索引擎选择器内部
        val selectorRect = android.graphics.Rect()
        searchEngineSelectorView?.let {
            if (it.isShown) {
                it.getGlobalVisibleRect(selectorRect)
                if (selectorRect.contains(x, y)) return true
            }
        }

        // 检查触摸点是否在搜索结果面板内部
        val searchResultsRect = android.graphics.Rect()
        appSearchResultsContainer?.let {
            if (it.isShown) {
                it.getGlobalVisibleRect(searchResultsRect)
                if (searchResultsRect.contains(x, y)) return true
            }
        }

        // 如果触摸点不在任何一个UI视图内，则不在灵动岛区域
        return false
    }

    private fun setupInsetsListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowContainerView?.setOnApplyWindowInsetsListener { _, insets ->
                val imeVisible = insets.isVisible(WindowInsets.Type.ime())
                currentKeyboardHeight = if (imeVisible) insets.getInsets(WindowInsets.Type.ime()).bottom else 0
                val targetTranslation = -currentKeyboardHeight.toFloat()

                if (configPanelView?.isShown == true) {
                    configPanelView?.animate()?.translationY(targetTranslation)?.setDuration(250)?.start()
                }
                if (searchEngineSelectorView?.isShown == true) {
                    searchEngineSelectorView?.animate()?.translationY(targetTranslation)?.setDuration(250)?.start()
                }

                insets
            }
        }
    }

    private fun enterEditingMode() {
        if (isEditingModeActive) return

        isEditingModeActive = true

        // 1. Show the blurred scrim behind everything
        showEditingScrim()
        
        // 2. Add prompt text to the existing search input
        val masterPrompt = "请你扮演一个拥有多年经验的资深行业专家，以我提供的主题为核心，草拟一篇详尽的报告大纲。你的回答需要满足以下要求：<br>1. 采用结构化、层级化的方式呈现，确保逻辑清晰，层次分明。<br>2. 涵盖主题的背景、现状、核心问题、解决方案及未来趋势等关键部分。<br>3. 在每个要点下，提出3-5个具有深度和启发性的子问题或探讨方向。<br>4. 语言风格需专业、严谨，符合正式报告要求。<br>5. 你的产出只包含报告大纲本身，不要有其他无关内容。"
        val currentText = searchInput?.text?.toString() ?: ""
        searchInput?.setText("$masterPrompt\n\n$currentText")
        searchInput?.setSelection(searchInput?.text?.length ?: 0)
        searchInput?.requestFocus()

        // 3. Show keyboard
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun exitEditingMode() {
        if (!isEditingModeActive) return

        isEditingModeActive = false
        
        // 1. Hide the scrim
        hideEditingScrim()
        
        // 2. Hide keyboard
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchInput?.windowToken, 0)
    }
    
    private fun showEditingScrim() {
        if (editingScrimView != null) return
        
        editingScrimView = View(this).apply {
            // Clicking the scrim will exit editing mode
            setOnClickListener { exitEditingMode() }
        }

        val scrimParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN, // 允许接收触摸事件
            PixelFormat.TRANSLUCENT
        )
        // Set a high Z-order but lower than the island itself
        scrimParams.gravity = Gravity.CENTER
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            scrimParams.blurBehindRadius = 25
        } else {
            editingScrimView?.setBackgroundColor(Color.parseColor("#99000000"))
        }

        windowManager.addView(editingScrimView, scrimParams)
    }

    private fun hideEditingScrim() {
        editingScrimView?.let {
            windowManager.removeView(it)
        }
        editingScrimView = null
    }

    private fun hideConfigPanel() {
        if (configPanelView == null) return
        val panelToRemove = configPanelView
        configPanelView = null

        val animation = panelToRemove?.animate()
            ?.translationY(-200f) // 向上滑出
            ?.alpha(0f)
            ?.scaleX(0.9f)
            ?.scaleY(0.9f)
            ?.setDuration(250)
            ?.setInterpolator(android.view.animation.AccelerateInterpolator())
        
        animation?.setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                if (panelToRemove.parent is ViewGroup) {
                    (panelToRemove.parent as ViewGroup).removeView(panelToRemove)
                }
            }
        })
        animation?.start()
    }

    private fun populateAppSearchIcons() {
        clearAppSearchIcons()
        val enabledApps = appSearchSettings.getEnabledAppConfigs()
        enabledApps.forEach { config ->
            val iconView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(40.dpToPx(), 40.dpToPx()).also { params ->
                    params.marginEnd = 12.dpToPx()
                }

                // 设置透明背景，增加透明感
                val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                val backgroundColor = if (isDarkMode) {
                    Color.parseColor("#33FFFFFF")  // 深色模式：半透明白色背景
                } else {
                    Color.parseColor("#33000000")  // 浅色模式：半透明黑色背景
                }

                // 创建圆形背景
                val backgroundDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(backgroundColor)
                }
                background = backgroundDrawable

                // 设置内边距，让图标不会贴边
                setPadding(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx())
                scaleType = ImageView.ScaleType.CENTER_CROP

                // 改进的图标加载逻辑
                val icon = loadAppIcon(config)
                setImageDrawable(icon)
                
                setOnClickListener {
                    val query = searchInput?.text.toString().trim()
                    if (query.isNotEmpty()) {
                        openAppWithSearch(config, query)
                        // --- Search History ---
                        val historyItem = mapOf(
                            "keyword" to "${config.appName}: $query",
                            "source" to "灵动岛-应用搜索",
                            "timestamp" to System.currentTimeMillis(),
                            "duration" to 0 // Duration is not applicable
                        )
                        settingsManager.addSearchHistoryItem(historyItem)
                        // --- End Search History ---
                    }
                }
            }
            appSearchIconContainer?.addView(iconView)
        }
    }

    /**
     * 加载应用图标，支持多种fallback策略
     */
    private fun loadAppIcon(config: AppSearchConfig): Drawable? {
        return try {
            // 1. 首先检查应用是否已安装，如果已安装则获取真实图标
            if (isAppInstalled(config.packageName)) {
                Log.d(TAG, "应用已安装，获取真实图标: ${config.appName}")
                val icon = packageManager.getApplicationIcon(config.packageName)
                if (icon != null) {
                    return icon
                }
            }
            
            Log.d(TAG, "应用未安装或图标获取失败，尝试其他图标加载方式: ${config.appName}")
            
            // 2. 尝试使用自定义图标资源（但排除系统默认图标）
            try {
                val customIcon = getDrawable(config.iconResId)
                if (customIcon != null && config.iconResId != android.R.drawable.ic_menu_search && 
                    config.iconResId != android.R.drawable.ic_menu_gallery &&
                    config.iconResId != android.R.drawable.ic_menu_directions &&
                    config.iconResId != android.R.drawable.ic_menu_manage) {
                    return customIcon
                }
            } catch (e: Exception) {
                Log.d(TAG, "自定义图标加载失败: ${config.appName}")
            }
            
            // 3. 生成字母图标作为fallback
            generateLetterIcon(config)
        } catch (e: Exception) {
            Log.e(TAG, "图标加载异常: ${config.appName}", e)
            // 4. 最后使用字母图标
            generateLetterIcon(config)
        }
    }

    /**
     * 生成字母图标
     */
    private fun generateLetterIcon(config: AppSearchConfig): Drawable {
        val size = 40.dpToPx()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        
        // 设置背景
        val paint = Paint().apply {
            isAntiAlias = true
            color = Color.parseColor("#FF6200EA") // 紫色背景
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        
        // 绘制字母
        val textPaint = Paint().apply {
            isAntiAlias = true
            color = Color.WHITE
            textSize = size * 0.4f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        
        val letter = config.appName.take(1).uppercase()
        val textBounds = Rect()
        textPaint.getTextBounds(letter, 0, letter.length, textBounds)
        val y = size / 2f + textBounds.height() / 2f - textBounds.bottom
        
        canvas.drawText(letter, size / 2f, y, textPaint)
        
        return BitmapDrawable(resources, bitmap)
    }

    private fun clearAppSearchIcons() {
        appSearchIconContainer?.removeAllViews()
    }

    private inner class EngineAdapter(
        private val engines: List<SearchEngine>,
        private val onEngineClick: (SearchEngine) -> Unit
    ) : RecyclerView.Adapter<EngineAdapter.EngineViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EngineViewHolder {
            val context = parent.context
            val view = LayoutInflater.from(context).inflate(R.layout.item_dynamic_island_search_engine, parent, false)
            return EngineViewHolder(view)
        }

        override fun onBindViewHolder(holder: EngineViewHolder, position: Int) {
            val engine = engines[position]
            holder.bind(engine)
            holder.itemView.setOnClickListener { onEngineClick(engine) }
        }

        override fun getItemCount(): Int = engines.size

        inner class EngineViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val iconView: ImageView = itemView.findViewById(R.id.engine_icon)
            private val nameView: TextView = itemView.findViewById(R.id.engine_name)
            private val descriptionView: TextView = itemView.findViewById(R.id.engine_description)

            fun bind(engine: SearchEngine) {
                nameView.text = engine.name
                descriptionView.text = engine.description
                FaviconLoader.loadIcon(iconView, engine.searchUrl, engine.iconResId)
            }
        }
    }

    private fun loadSearchCategories(): List<SearchCategory> {
        val categories = mutableListOf<SearchCategory>()

        // Part 1: Load REGULAR search engines using the group logic (the "before" logic)
        val allGroups = settingsManager.getSearchEngineGroups()
        val enabledGroups = allGroups.filter { it.isEnabled }
        val regularEnginesFromGroups = mutableListOf<SearchEngine>()

        enabledGroups.forEach { group ->
            group.engines.forEach { engine ->
                // Ensure we don't add AI engines here to avoid duplication
                val isAI = com.example.aifloatingball.model.AISearchEngine.DEFAULT_AI_ENGINES.any { it.name == engine.name }
                if (!isAI) {
                    regularEnginesFromGroups.add(
                        SearchEngine(
                            name = engine.name,
                            description = engine.name,
                            iconResId = engine.iconResId,
                            searchUrl = engine.searchUrl
                        )
                    )
                }
            }
        }

        if (regularEnginesFromGroups.isNotEmpty()) {
            categories.add(SearchCategory("普通搜索引擎", regularEnginesFromGroups.distinctBy { it.name }))
        }

        // Part 2: Load AI search engines using the enabled list logic (the "new, correct" logic)
        val enabledAIEngineNames = settingsManager.getEnabledAIEngines()
        val aiEngines = com.example.aifloatingball.model.AISearchEngine.DEFAULT_AI_ENGINES
            .filter { enabledAIEngineNames.contains(it.name) }
            .map {
                SearchEngine(
                    name = it.name,
                    description = it.name,
                    iconResId = it.iconResId,
                    searchUrl = it.searchUrl
                )
            }

        if (aiEngines.isNotEmpty()) {
            categories.add(SearchCategory("AI 搜索引擎", aiEngines))
        }

        return categories
    }

    private fun getColorFromAttr(context: Context, @AttrRes attrRes: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attrRes, typedValue, true)
        return typedValue.data
    }

    private fun getThemedContext(): Context {
        // Create a context that respects the user's theme choice, which might be different from the system's
        val themeMode = settingsManager.getThemeMode() // -1 system, 1 light, 2 dark
        val nightModeFlags = when (themeMode) {
            1 -> Configuration.UI_MODE_NIGHT_NO
            2 -> Configuration.UI_MODE_NIGHT_YES
            else -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        }

        val config = Configuration(resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightModeFlags

        return createConfigurationContext(config)
    }

    private fun recreateAllViews() {
        val wasSearchModeActive = isSearchModeActive
        val currentSearchText = searchInput?.text?.toString()

        cleanupViews()
        showDynamicIsland()

        if (wasSearchModeActive) {
            transitionToSearchState(force = true)
            // We need a slight delay to ensure the config panel is laid out before setting text
            uiHandler.post {
                searchInput?.setText(currentSearchText)
                if (currentSearchText != null) {
                    searchInput?.setSelection(currentSearchText.length)
                }
            }
        }
    }

    private fun showPromptDialog(prompt: String) {
        val dialogContext = ContextThemeWrapper(this, R.style.AppTheme_Dialog)

        AlertDialog.Builder(dialogContext)
            .setTitle("生成的 Prompt")
            .setMessage(prompt)
            .setPositiveButton("复制") { dialog, _ ->
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Master Prompt", prompt)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, "Prompt 已复制到剪贴板", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("关闭") { dialog, _ ->
                dialog.dismiss()
            }
            .create()
            .apply {
                window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                show()
            }
    }

    private fun setupMessageReceiver() {
        val filter = IntentFilter("com.example.aifloatingball.UPDATE_CONTENT")
        LocalBroadcastManager.getInstance(this).registerReceiver(messageReceiver, filter)
    }

    private fun updateExpandedViewContent(query: String?, content: String?, isNewSearch: Boolean) {
        // Dummy implementation
    }

    private fun showPasteButton() {
        val input = searchInput ?: return
        val configPanel = configPanelView as? ViewGroup ?: return

        if (!input.isShown) {
            return
        }

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboard.hasPrimaryClip() || !clipboard.primaryClipDescription!!.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)) {
            return
        }

        if (pasteButtonView == null) {
            val context = ContextThemeWrapper(getThemedContext(), R.style.Theme_FloatingWindow)
            val inflater = LayoutInflater.from(context)
            pasteButtonView = inflater.inflate(R.layout.paste_button, configPanel, false)

            pasteButtonView?.setOnClickListener {
                val pasteData = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
                if (!pasteData.isNullOrEmpty()) {
                    val selectionStart = input.selectionStart
                    val selectionEnd = input.selectionEnd
                    input.text.replace(selectionStart.coerceAtMost(selectionEnd), selectionStart.coerceAtLeast(selectionEnd), pasteData, 0, pasteData.length)
                }
                hidePasteButton()
            }
        }

        if (pasteButtonView?.parent == null) {
            configPanel.addView(pasteButtonView)
        }
        pasteButtonView?.visibility = View.VISIBLE
        pasteButtonView?.alpha = 1f


        input.post {
            if (pasteButtonView == null) return@post

            pasteButtonView?.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            val pasteButtonHeight = pasteButtonView?.measuredHeight ?: 0

            var currentView: View = input
            var inputTop = 0f
            while (currentView != configPanel) {
                inputTop += currentView.y
                currentView = currentView.parent as View
            }

            pasteButtonView?.x = input.x
            pasteButtonView?.y = inputTop + input.height + 4.dpToPx()

            hidePasteButtonRunnable = Runnable { hidePasteButton() }
            pasteButtonHandler.postDelayed(hidePasteButtonRunnable!!, 5000)
        }
    }

    private fun hidePasteButton() {
        hidePasteButtonRunnable?.let { pasteButtonHandler.removeCallbacks(it) }
        hidePasteButtonRunnable = null
        pasteButtonView?.let {
            if (it.visibility == View.VISIBLE) {
                it.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction {
                        it.visibility = View.GONE
                        (configPanelView as? ViewGroup)?.removeView(it)
                         pasteButtonView = null
                    }.start()
            }
        }
    }

    private fun autoPaste(editText: EditText?) {
        editText ?: return
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip() && clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true) {
                val pasteData = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                if (!pasteData.isNullOrEmpty()) {
                    editText.setText(pasteData)
                    editText.setSelection(pasteData.length)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to auto paste", e)
        }
    }

    private fun setupProxyIndicator() {
        proxyIndicatorAnimator?.cancel()
        proxyIndicatorAnimator = null
        
        // Update the custom indicator view settings
        // 不再需要更新代理指示器设置
        
        // Apply the current position setting
        updateProxyIndicatorPosition()
    }

    private fun updateProxyIndicatorPosition() {
        // 不再需要更新代理指示器位置，因为现在使用按钮
    }

    private fun updateIslandPosition(position: Int) {
        // 不再需要更新位置，因为现在使用按钮
    }
    
    private fun updateIslandWidth() {
        if (animatingIslandView != null) {
            val displayMetrics = resources.displayMetrics
            val islandWidth = settingsManager.getIslandWidth()
            
            // 强制设置合适的宽度，确保四个按钮能够完整显示
            val minRequiredWidth = 240 // 240dp，确保四个按钮能够完整显示
            val actualWidth = maxOf(islandWidth, minRequiredWidth)
            
            compactWidth = (actualWidth * displayMetrics.density).toInt()
            
            // 更新灵动岛宽度
            animatingIslandView?.layoutParams?.width = compactWidth
            windowManager?.updateViewLayout(windowContainerView, windowContainerView?.layoutParams)
            Log.d(TAG, "更新灵动岛宽度: 原始宽度=${islandWidth}dp, 实际宽度=${actualWidth}dp, 像素宽度=${compactWidth}px")
        }
    }
    
    private fun forceRestartIsland() {
        Log.d(TAG, "强制重启灵动岛以应用新宽度")
        cleanupViews()
        uiHandler.postDelayed({
            showDynamicIsland()
        }, 100)
    }

    private fun updateIslandPositionForView(touchTargetView: View) {
        // 不再需要更新位置，因为现在使用按钮
    }

    private fun expandIsland() {
        if (isSearchModeActive) return
        isSearchModeActive = true
        animateIsland(compactWidth, expandedWidth)
    }

    private fun collapseIsland() {
        if (!isSearchModeActive) return
        isSearchModeActive = false
        hideKeyboard(searchInput)
        animateIsland(expandedWidth, compactWidth)
    }

    private fun animateIsland(fromWidth: Int, toWidth: Int) {
        val animator = ValueAnimator.ofInt(fromWidth, toWidth)
        animator.duration = 350
        animator.interpolator = AccelerateDecelerateInterpolator()
        animator.addUpdateListener {
            val params = animatingIslandView?.layoutParams as? FrameLayout.LayoutParams
            params?.width = it.animatedValue as Int
            // 如果是展开状态，使用自适应高度
            if (toWidth > fromWidth) {
                params?.height = FrameLayout.LayoutParams.WRAP_CONTENT
            }
            animatingIslandView?.layoutParams = params
            animatingIslandView?.requestLayout()
        }
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                if (toWidth > fromWidth) { // Expanding
                    // Remove old content, add new content (search box, etc.)
                    islandContentView = LayoutInflater.from(this@DynamicIslandService)
                        .inflate(R.layout.dynamic_island_search_content, animatingIslandView, false)
                    animatingIslandView?.addView(islandContentView)
                    setupSearchInput(islandContentView!!)
                    populateAppSearchIcons()
                }
            }
            override fun onAnimationEnd(animation: Animator) {
                if (toWidth < fromWidth) { // Collapsing
                    animatingIslandView?.removeAllViews()
                } else { // Expanding
                    searchInput?.requestFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
                }
            }
        })
        animator.start()
    }

    private fun hideKeyboard(view: View?) {
        view?.let {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(it.windowToken, 0)
        }
    }

    private fun openAppWithSearch(config: com.example.aifloatingball.model.AppSearchConfig, query: String) {
        try {
            val url = config.searchUrl.replace("{q}", Uri.encode(query))
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Important for making sure it opens in the correct app
                setPackage(config.packageName)
            }
            startActivity(intent)
            collapseIsland()
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "${config.appName} 未安装或无法处理搜索请求", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "启动 ${config.appName} 失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSearchInput(view: View) {
        searchInput = view.findViewById(R.id.dynamic_island_input)
        searchButton = view.findViewById(R.id.dynamic_island_send_button)

        searchInput?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = searchInput?.text.toString().trim()
                if (query.isNotEmpty()) {
                    hideKeyboard(searchInput)
                    val intent = Intent(this, DualFloatingWebViewService::class.java).apply {
                        putExtra("search_query", query)
                        putExtra("source", "灵动岛")
                        putExtra("startTime", System.currentTimeMillis())
                    }
                    startService(intent)
                    collapseIsland()
                }
                true
            } else {
                false
            }
        }

        searchButton?.setOnClickListener {
            // Trigger the same search action
            searchInput?.onEditorAction(EditorInfo.IME_ACTION_SEARCH)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Log.d(TAG, "Configuration changed. Recreating all views.")
        
        // Save state before cleanup
        val wasSearchModeActive = isSearchModeActive
        val currentSearchText = searchInput?.text?.toString()

        // Cleanly remove all views
        cleanupViews()

        // Re-create all views with new configuration
        showDynamicIsland()

        // Restore state
        if (wasSearchModeActive) {
            transitionToSearchState(force = true)
            // We need a slight delay to ensure the config panel is laid out before setting text
            uiHandler.post {
                searchInput?.setText(currentSearchText)
                if (currentSearchText != null) {
                    searchInput?.setSelection(currentSearchText.length)
                }
            }
        }
        
        // 不再需要更新代理指示器位置，因为现在使用按钮
    }

    private fun getIslandLayoutParams(isCompact: Boolean): WindowManager.LayoutParams {
        // This function is no longer needed with the new architecture
        // but we keep it to avoid breaking other parts of the code for now.
        // A proper refactor would remove it.
        val width = if (isCompact) compactWidth else expandedWidth
        val height = if (isCompact) compactHeight else expandedHeight

        val params = WindowManager.LayoutParams(
            width,
            height,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = statusBarHeight + 20
        return params
    }

    private fun enterSearchMode(force: Boolean) {
        if (isSearchModeActive && !force) return
        isSearchModeActive = true
        transitionToSearchState(force = true)
    }

    private fun initSearchInputListener() {
        searchInput?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                
                // 取消之前的搜索任务
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                
                if (query.isNotEmpty()) {
                    // 清除选中提示
                    if (currentSelectedApp != null) {
                        searchInput?.hint = "搜索应用或输入内容"
                    }
                    
                    // 确保AppInfoManager已加载
                    val appInfoManager = AppInfoManager.getInstance()
                    if (!appInfoManager.isLoaded()) {
                        appInfoManager.loadApps(this@DynamicIslandService)
                        showLoadingIndicator()
                        return@afterTextChanged
                    }
                    
                    // 使用防抖机制，延迟300ms执行搜索
                    searchRunnable = Runnable {
                        performRealTimeSearch(query, appInfoManager)
                    }
                    searchRunnable?.let { searchHandler.postDelayed(it, 300) }
                } else {
                    // 输入框为空时，显示常用APP图标
                    showDefaultAppIcons()
                }
            }
        })
    }

    /**
     * 执行实时搜索
     */
    private fun performRealTimeSearch(query: String, appInfoManager: AppInfoManager) {
        val appResults = appInfoManager.search(query)
        
        if (appResults.isNotEmpty()) {
            showAppSearchResults(appResults)
        } else {
            // 没有匹配的APP时，显示支持URL scheme的APP图标
            showUrlSchemeAppIcons()
        }
    }
    
    /**
     * 显示加载指示器
     */
    private fun showLoadingIndicator() {
        // 隐藏搜索结果
        hideAppSearchResults()
        
        // 显示加载提示
        val loadingText = "正在加载应用列表..."
        searchInput?.hint = loadingText
        Toast.makeText(this, loadingText, Toast.LENGTH_SHORT).show()
        
        // 延迟检查加载状态
        searchInput?.postDelayed({
            val appInfoManager = AppInfoManager.getInstance()
            if (appInfoManager.isLoaded()) {
                val currentQuery = searchInput?.text.toString().trim()
                if (currentQuery.isNotEmpty()) {
                    performRealTimeSearch(currentQuery, appInfoManager)
                }
            } else {
                // 如果仍未加载完成，继续等待
                showLoadingIndicator()
            }
        }, 500)
    }

    private fun showAppSearchResults(results: List<AppInfo>) {
        Log.d(TAG, "showAppSearchResults: 显示 ${results.size} 个应用结果")
        Log.d(TAG, "appSearchResultsContainer: $appSearchResultsContainer")
        Log.d(TAG, "appSearchRecyclerView: $appSearchRecyclerView")
        
        // 每次都重新创建适配器，确保点击监听器正确设置
        appSearchAdapter = AppSearchAdapter(results, isHorizontal = true) { appInfo ->
            // 选中应用，但不执行搜索动作
            selectAppForSearch(appInfo)
        }
        appSearchRecyclerView?.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = appSearchAdapter
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        appSearchResultsContainer?.visibility = View.VISIBLE
    }

    private fun hideAppSearchResults() {
        if (appSearchResultsContainer?.visibility == View.VISIBLE) {
            appSearchResultsContainer?.visibility = View.GONE
            // Cleanup adapter to avoid holding references
            appSearchRecyclerView?.adapter = null
            appSearchAdapter = null
        }
    }
    
    /**
     * 选中应用用于搜索，但不执行搜索动作
     * 用户需要输入文本并执行搜索后才会退出搜索面板
     */
    private fun selectAppForSearch(appInfo: AppInfo) {
        Log.d(TAG, "选中应用: ${appInfo.label}")
        
        // 添加到最近选中的APP列表
        addToRecentApps(appInfo)
        // 更新最近APP按钮图标
        updateRecentAppButton(appInfo)
        // 设置当前选中的APP
        currentSelectedApp = appInfo
        
        // 清理输入框文本，等待用户输入新的搜索关键词
        searchInput?.setText("")
        searchInput?.hint = "已选中 ${appInfo.label}，请输入搜索内容"
        
        // 显示选中提示
        Toast.makeText(this, "已选中 ${appInfo.label}，请输入搜索内容", Toast.LENGTH_SHORT).show()
        
        // 不退出搜索面板，让用户继续输入搜索内容
        // 搜索面板保持打开状态，用户可以继续输入文本
    }
    
    /**
     * 从历史列表选中应用，但不执行搜索动作
     * 用于历史列表选择应用时的处理
     */
    private fun selectAppFromHistory(appInfo: AppInfo) {
        Log.d(TAG, "从历史列表选中应用: ${appInfo.label}")
        
        // 添加到最近选中的APP列表
        addToRecentApps(appInfo)
        // 更新最近APP按钮图标
        updateRecentAppButton(appInfo)
        // 设置当前选中的APP
        currentSelectedApp = appInfo
        
        // 设置提示信息，等待用户输入搜索关键词
        searchInput?.hint = "已选中 ${appInfo.label}，请输入搜索内容"
        
        // 显示选中提示
        Toast.makeText(this, "已选中 ${appInfo.label}，请输入搜索内容", Toast.LENGTH_SHORT).show()
        
        // 不退出搜索面板，让用户继续输入搜索内容
        // 搜索面板保持打开状态，用户可以继续输入文本
    }
    
    private fun showDefaultAppIcons() {
        // 创建常用APP列表，包含URL scheme信息
        val defaultApps = createDefaultAppList()
        if (defaultApps.isNotEmpty()) {
            showAppSearchResults(defaultApps)
        }
    }
    
    private fun showUrlSchemeAppIcons() {
        // 创建支持URL scheme的APP列表
        val urlSchemeApps = createUrlSchemeAppList()
        if (urlSchemeApps.isNotEmpty()) {
            Log.d(TAG, "显示URL scheme APP图标: ${urlSchemeApps.map { it.label }}")
            showAppSearchResults(urlSchemeApps)
        }
    }
    
    private fun createDefaultAppList(): List<AppInfo> {
        val pm = packageManager
        val defaultApps = mutableListOf<AppInfo>()
        
        // 定义常用APP的包名和URL scheme
        val appConfigs = listOf(
            Triple("com.tencent.mm", "weixin", "微信"),
            Triple("com.tencent.mobileqq", "mqqapi", "QQ"),
            Triple("com.taobao.taobao", "taobao", "淘宝"),
            Triple("com.eg.android.AlipayGphone", "alipay", "支付宝"),
            Triple("com.ss.android.ugc.aweme", "snssdk1128", "抖音"),
            Triple("com.sina.weibo", "sinaweibo", "微博"),
            Triple("com.tencent.wework", "wework", "企业微信"),
            Triple("com.tencent.tim", "tim", "TIM"),
            Triple("com.tencent.mtt", "mttbrowser", "QQ浏览器"),
            Triple("com.UCMobile", "ucbrowser", "UC浏览器")
        )
        
        for ((packageName, urlScheme, appName) in appConfigs) {
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val icon = pm.getApplicationIcon(packageName)
                val label = pm.getApplicationLabel(appInfo).toString()
                
                defaultApps.add(AppInfo(
                    label = label,
                    packageName = packageName,
                    icon = icon,
                    urlScheme = urlScheme
                ))
            } catch (e: Exception) {
                // 应用未安装，跳过
                Log.d(TAG, "应用 $appName 未安装: $packageName")
            }
        }
        
        return defaultApps
    }
    
    private fun createUrlSchemeAppList(): List<AppInfo> {
        val pm = packageManager
        val urlSchemeApps = mutableListOf<AppInfo>()
        
        // 根据AppSearchSettings中的配置创建更全面的URL scheme支持列表
        val urlSchemeAppConfigs = listOf(
            // 社交类
            Triple("com.tencent.mm", "weixin", "微信"),
            Triple("com.tencent.mobileqq", "mqqapi", "QQ"),
            Triple("com.tencent.wework", "wework", "企业微信"),
            Triple("com.tencent.tim", "tim", "TIM"),
            Triple("com.sina.weibo", "sinaweibo", "微博"),
            Triple("com.xingin.xhs", "xhsdiscover", "小红书"),
            Triple("com.douban.frodo", "douban", "豆瓣"),
            Triple("com.twitter.android", "twitter", "Twitter-X"),
            Triple("com.zhihu.android", "zhihu", "知乎"),
            
            // 购物类
            Triple("com.taobao.taobao", "taobao", "淘宝"),
            Triple("com.eg.android.AlipayGphone", "alipay", "支付宝"),
            Triple("com.jingdong.app.mall", "openapp.jdmobile", "京东"),
            Triple("com.xunmeng.pinduoduo", "pinduoduo", "拼多多"),
            Triple("com.tmall.wireless", "tmall", "天猫"),
            Triple("com.taobao.idlefish", "fleamarket", "闲鱼"),
            
            // 视频类
            Triple("com.ss.android.ugc.aweme", "snssdk1128", "抖音"),
            Triple("com.zhiliaoapp.musically", "snssdk1128", "TikTok"),
            Triple("tv.danmaku.bili", "bilibili", "哔哩哔哩"),
            Triple("com.tencent.qqlive", "qqlive", "腾讯视频"),
            Triple("com.iqiyi.app", "iqiyi", "爱奇艺"),
            Triple("com.google.android.youtube", "youtube", "YouTube"),
            
            // 音乐类
            Triple("com.tencent.qqmusic", "qqmusic", "QQ音乐"),
            Triple("com.netease.cloudmusic", "orpheus", "网易云音乐"),
            Triple("com.spotify.music", "spotify", "Spotify"),
            
            // 生活服务类
            Triple("com.sankuai.meituan", "imeituan", "美团"),
            Triple("me.ele", "eleme", "饿了么"),
            Triple("com.dianping.v1", "dianping", "大众点评"),
            
            // 地图导航类
            Triple("com.autonavi.minimap", "androidamap", "高德地图"),
            Triple("com.baidu.BaiduMap", "baidumap", "百度地图"),
            
            // 浏览器类
            Triple("com.tencent.mtt", "mttbrowser", "QQ浏览器"),
            Triple("com.UCMobile", "ucbrowser", "UC浏览器"),
            Triple("com.android.chrome", "googlechrome", "Chrome"),
            Triple("org.mozilla.firefox", "firefox", "Firefox"),
            Triple("com.quark.browser", "quark", "夸克"),
            
            // 金融类
            Triple("cmb.pb", "cmbmobilebank", "招商银行"),
            Triple("com.antfortune.wealth", "antfortune", "蚂蚁财富"),
            
            // 出行类
            Triple("com.sdu.didi.psnger", "diditaxi", "滴滴出行"),
            Triple("com.MobileTicket", "cn.12306", "12306"),
            Triple("ctrip.android.view", "ctrip", "携程旅行"),
            Triple("com.Qunar", "qunar", "去哪儿"),
            Triple("com.jingyao.easybike", "hellobike", "哈啰出行"),
            
            // 招聘类
            Triple("com.hpbr.bosszhipin", "bosszhipin", "BOSS直聘"),
            Triple("com.liepin.android", "liepin", "猎聘"),
            Triple("com.job.android", "zhaopin", "前程无忧"),
            
            // 教育类
            Triple("com.youdao.dict", "yddict", "有道词典"),
            Triple("com.eusoft.eudic", "eudic", "欧路词典"),
            Triple("com.jiongji.andriod.card", "baicizhan", "百词斩"),
            Triple("com.baidu.homework", "zuoyebang", "作业帮"),
            Triple("com.fenbi.android.solar", "yuansouti", "小猿搜题"),
            
            // 新闻类
            Triple("com.netease.nr", "newsapp", "网易新闻")
        )
        
        for ((packageName, urlScheme, appName) in urlSchemeAppConfigs) {
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val icon = pm.getApplicationIcon(packageName)
                val label = pm.getApplicationLabel(appInfo).toString()
                
                // 避免重复添加
                if (!urlSchemeApps.any { it.packageName == packageName }) {
                    urlSchemeApps.add(AppInfo(
                        label = label,
                        packageName = packageName,
                        icon = icon,
                        urlScheme = urlScheme
                    ))
                }
            } catch (e: Exception) {
                // 应用未安装，跳过
                Log.d(TAG, "URL scheme应用 $appName 未安装: $packageName")
            }
        }
        
        return urlSchemeApps
    }
    
    private fun launchAppSearchResults(appInfo: AppInfo) {
        try {
            val searchQuery = searchInput?.text?.toString()?.trim() ?: ""
            
            // 优先使用URL scheme跳转到APP搜索结果页面
            if (appInfo.urlScheme != null) {
                val encodedQuery = Uri.encode(searchQuery)
                val intent = when (appInfo.urlScheme) {
                    // 社交类
                    "weixin" -> Intent(Intent.ACTION_VIEW, Uri.parse("weixin://dl/search?query=$encodedQuery"))
                    "mqqapi" -> Intent(Intent.ACTION_VIEW, Uri.parse("mqqapi://search?query=$encodedQuery"))
                    "wework" -> Intent(Intent.ACTION_VIEW, Uri.parse("wework://search?query=$encodedQuery"))
                    "tim" -> Intent(Intent.ACTION_VIEW, Uri.parse("tim://search?query=$encodedQuery"))
                    "sinaweibo" -> Intent(Intent.ACTION_VIEW, Uri.parse("sinaweibo://searchall?q=$encodedQuery"))
                    "xhsdiscover" -> Intent(Intent.ACTION_VIEW, Uri.parse("xhsdiscover://search/result?keyword=$encodedQuery"))
                    "douban" -> Intent(Intent.ACTION_VIEW, Uri.parse("douban://search?q=$encodedQuery"))
                    "twitter" -> Intent(Intent.ACTION_VIEW, Uri.parse("twitter://search?query=$encodedQuery"))
                    "zhihu" -> Intent(Intent.ACTION_VIEW, Uri.parse("zhihu://search?q=$encodedQuery"))
                    
                    // 购物类
                    "taobao" -> Intent(Intent.ACTION_VIEW, Uri.parse("taobao://s.taobao.com?q=$encodedQuery"))
                    "alipay" -> Intent(Intent.ACTION_VIEW, Uri.parse("alipays://platformapi/startapp?appId=20000067&query=$encodedQuery"))
                    "openapp.jdmobile" -> Intent(Intent.ACTION_VIEW, Uri.parse("openapp.jdmobile://virtual?params={\"des\":\"productList\",\"keyWord\":\"$encodedQuery\"}"))
                    "pinduoduo" -> Intent(Intent.ACTION_VIEW, Uri.parse("pinduoduo://com.xunmeng.pinduoduo/search_result.html?search_key=$encodedQuery"))
                    "tmall" -> Intent(Intent.ACTION_VIEW, Uri.parse("tmall://page.tm/search?q=$encodedQuery"))
                    "fleamarket" -> Intent(Intent.ACTION_VIEW, Uri.parse("fleamarket://x_search_items?keyword=$encodedQuery"))
                    
                    // 视频类
                    "snssdk1128" -> Intent(Intent.ACTION_VIEW, Uri.parse("snssdk1128://search/tabs?keyword=$encodedQuery"))
                    "bilibili" -> Intent(Intent.ACTION_VIEW, Uri.parse("bilibili://search?keyword=$encodedQuery"))
                    "qqlive" -> Intent(Intent.ACTION_VIEW, Uri.parse("qqlive://search?query=$encodedQuery"))
                    "iqiyi" -> Intent(Intent.ACTION_VIEW, Uri.parse("iqiyi://search?key=$encodedQuery"))
                    "youtube" -> Intent(Intent.ACTION_VIEW, Uri.parse("youtube://results?search_query=$encodedQuery"))
                    
                    // 音乐类
                    "qqmusic" -> Intent(Intent.ACTION_VIEW, Uri.parse("qqmusic://search?key=$encodedQuery"))
                    "orpheus" -> Intent(Intent.ACTION_VIEW, Uri.parse("orpheus://search?keyword=$encodedQuery"))
                    "spotify" -> Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$encodedQuery"))
                    
                    // 生活服务类
                    "imeituan" -> Intent(Intent.ACTION_VIEW, Uri.parse("imeituan://www.meituan.com/search?q=$encodedQuery"))
                    "eleme" -> Intent(Intent.ACTION_VIEW, Uri.parse("eleme://search?keyword=$encodedQuery"))
                    "dianping" -> Intent(Intent.ACTION_VIEW, Uri.parse("dianping://searchshoplist?keyword=$encodedQuery"))
                    
                    // 地图导航类
                    "androidamap" -> Intent(Intent.ACTION_VIEW, Uri.parse("androidamap://poi?sourceApplication=appname&keywords=$encodedQuery"))
                    "baidumap" -> Intent(Intent.ACTION_VIEW, Uri.parse("baidumap://map/place/search?query=$encodedQuery"))
                    
                    // 浏览器类
                    "mttbrowser" -> Intent(Intent.ACTION_VIEW, Uri.parse("mttbrowser://search?query=$encodedQuery"))
                    "ucbrowser" -> Intent(Intent.ACTION_VIEW, Uri.parse("ucbrowser://search?keyword=$encodedQuery"))
                    "googlechrome" -> Intent(Intent.ACTION_VIEW, Uri.parse("googlechrome://www.google.com/search?q=$encodedQuery"))
                    "firefox" -> Intent(Intent.ACTION_VIEW, Uri.parse("firefox://search?q=$encodedQuery"))
                    "quark" -> Intent(Intent.ACTION_VIEW, Uri.parse("quark://search?q=$encodedQuery"))
                    
                    // 金融类
                    "cmbmobilebank" -> Intent(Intent.ACTION_VIEW, Uri.parse("cmbmobilebank://search?keyword=$encodedQuery"))
                    "antfortune" -> Intent(Intent.ACTION_VIEW, Uri.parse("antfortune://search?keyword=$encodedQuery"))
                    
                    // 出行类
                    "diditaxi" -> Intent(Intent.ACTION_VIEW, Uri.parse("diditaxi://search?keyword=$encodedQuery"))
                    "cn.12306" -> Intent(Intent.ACTION_VIEW, Uri.parse("cn.12306://search?keyword=$encodedQuery"))
                    "ctrip" -> Intent(Intent.ACTION_VIEW, Uri.parse("ctrip://search?keyword=$encodedQuery"))
                    "qunar" -> Intent(Intent.ACTION_VIEW, Uri.parse("qunar://search?keyword=$encodedQuery"))
                    "hellobike" -> Intent(Intent.ACTION_VIEW, Uri.parse("hellobike://search?keyword=$encodedQuery"))
                    
                    // 招聘类
                    "bosszhipin" -> Intent(Intent.ACTION_VIEW, Uri.parse("bosszhipin://search?keyword=$encodedQuery"))
                    "liepin" -> Intent(Intent.ACTION_VIEW, Uri.parse("liepin://search?keyword=$encodedQuery"))
                    "zhaopin" -> Intent(Intent.ACTION_VIEW, Uri.parse("zhaopin://search?keyword=$encodedQuery"))
                    
                    // 教育类
                    "yddict" -> Intent(Intent.ACTION_VIEW, Uri.parse("yddict://search?keyword=$encodedQuery"))
                    "eudic" -> Intent(Intent.ACTION_VIEW, Uri.parse("eudic://dict/$encodedQuery"))
                    "baicizhan" -> Intent(Intent.ACTION_VIEW, Uri.parse("baicizhan://search?keyword=$encodedQuery"))
                    "zuoyebang" -> Intent(Intent.ACTION_VIEW, Uri.parse("zuoyebang://search?keyword=$encodedQuery"))
                    "yuansouti" -> Intent(Intent.ACTION_VIEW, Uri.parse("yuansouti://search?keyword=$encodedQuery"))
                    
                    // 新闻类
                    "newsapp" -> Intent(Intent.ACTION_VIEW, Uri.parse("newsapp://search?keyword=$encodedQuery"))
                    
                    else -> {
                        // 通用URL scheme格式
                        Intent(Intent.ACTION_VIEW, Uri.parse("${appInfo.urlScheme}://search?query=$encodedQuery"))
                    }
                }
                
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                hideContent()
                return
            }
            
            // 如果没有URL scheme，使用包名启动应用
            val launchIntent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                hideContent()
            } else {
                Toast.makeText(this, "无法启动该应用", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "启动应用失败: ${appInfo.label}", e)
            Toast.makeText(this, "启动应用失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun hideContent() {
        // This function is called when an app is launched from the search results.
        // It should collapse the island and hide the keyboard and search results.
        transitionToCompactState()
        hideAppSearchResults()
        hideConfigPanel()
    }
    
    /**
     * 隐藏内容并切换到圆球状态
     * 当用户进入其他应用时，灵动岛应该从横条变成圆球
     */
    private fun hideContentAndSwitchToBall() {
        Log.d(TAG, "隐藏内容并切换到圆球状态")
        
        // 隐藏搜索面板和配置面板
        hideAppSearchResults()
        hideConfigPanel()
        
        // 隐藏键盘
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowContainerView?.windowToken, 0)
        searchInput?.clearFocus()
        searchInput?.setText("")
        
        // 切换到圆球状态
        transitionToBallState()
    }
    
    /**
     * 切换到圆球状态
     * 灵动岛从横条变成圆球，用户点击后可以恢复
     */
    private fun transitionToBallState() {
        Log.d(TAG, "切换到圆球状态")
        
        // 设置搜索模式为非活跃状态
        isSearchModeActive = false
        
        // 清理展开的视图
        cleanupExpandedViews()
        
        // 动画切换到圆球状态，带有缩放效果
        animatingIslandView?.animate()
            ?.withLayer()
            ?.alpha(0f)
            ?.scaleX(0.3f)
            ?.scaleY(0.3f)
            ?.setInterpolator(AccelerateInterpolator())
            ?.setDuration(400)
            ?.withEndAction {
                // 动画完成后，切换到圆球状态
                switchToBallMode()
            }
            ?.start()
    }
    
    /**
     * 切换到圆球模式
     * 显示一个小的圆球，用户点击可以恢复灵动岛状态
     */
    private fun switchToBallMode() {
        Log.d(TAG, "切换到圆球模式")
        
        // 隐藏当前的灵动岛视图
        animatingIslandView?.visibility = View.GONE
        appSearchIconScrollView?.visibility = View.GONE
        clearAppSearchIcons()
        
        // 创建圆球视图
        createBallView()
        
        // 设置点击监听器，点击圆球恢复灵动岛状态
        setupBallClickListener()
    }
    
    /**
     * 创建圆球视图
     * 实现类似iPhone Dynamic Island的最小化效果
     */
    private fun createBallView() {
        // 如果圆球视图已存在，先移除
        if (ballView != null) {
            try {
                windowContainerView?.removeView(ballView)
            } catch (e: Exception) { /* ignore */ }
        }
        
        // 创建圆球视图
        ballView = View(this).apply {
            // 创建圆形背景
            val ballSize = (32 * resources.displayMetrics.density).toInt() // 32dp，更小更精致
            val touchAreaSize = (60 * resources.displayMetrics.density).toInt() // 60dp触摸区域，更大更容易点击
            val cornerRadius = ballSize / 2f
            
            // 创建渐变背景，模拟灵动岛效果
            // 使用LayerDrawable来创建中心小圆球，外围透明触摸区域
            val ballDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#1C1C1E")) // 深色背景
                setStroke((2 * resources.displayMetrics.density).toInt(), Color.parseColor("#3A3A3C")) // 边框
            }
            
            // 创建外层透明背景，用于触摸区域
            val outerDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT) // 透明背景
            }
            
            // 使用LayerDrawable组合两个drawable
            val layerDrawable = android.graphics.drawable.LayerDrawable(arrayOf(outerDrawable, ballDrawable))
            // 设置内层圆球的位置和大小
            layerDrawable.setLayerInset(1, (touchAreaSize - ballSize) / 2, (touchAreaSize - ballSize) / 2, 
                (touchAreaSize - ballSize) / 2, (touchAreaSize - ballSize) / 2)
            
            background = layerDrawable
            
            // 设置阴影效果
            elevation = 8f
            
            // 使用更大的触摸区域，但视觉上仍然是小的圆球
            layoutParams = FrameLayout.LayoutParams(touchAreaSize, touchAreaSize, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = statusBarHeight + 16 // 状态栏下方16dp
            }
            visibility = View.VISIBLE
            alpha = 0f
        }
        
        // 添加到窗口容器
        windowContainerView?.addView(ballView)
        
        // 显示圆球动画，带有缩放效果
        ballView?.animate()
            ?.alpha(1f)
            ?.scaleX(1f)
            ?.scaleY(1f)
            ?.setDuration(400)
            ?.setInterpolator(AccelerateDecelerateInterpolator())
            ?.start()
    }
    
    /**
     * 设置圆球点击监听器
     */
    private fun setupBallClickListener() {
        ballView?.setOnClickListener {
            Log.d(TAG, "圆球被点击，恢复灵动岛状态")
            restoreIslandState()
        }
    }
    
    /**
     * 处理圆球状态下的触摸事件
     */
    private fun handleBallTouchEvent(event: MotionEvent): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 检查触摸点是否在圆球区域内
                val ballRect = android.graphics.Rect()
                ballView?.getGlobalVisibleRect(ballRect)
                val x = event.rawX.toInt()
                val y = event.rawY.toInt()
                
                if (ballRect.contains(x, y)) {
                    // 在圆球区域内，处理点击事件
                    Log.d(TAG, "圆球被点击，恢复灵动岛状态")
                    restoreIslandState()
                    true
                } else {
                    // 在圆球区域外，让事件穿透
                    false
                }
            }
            else -> false
        }
    }
    
    /**
     * 初始化应用切换监听器
     */
    private fun initAppSwitchListener() {
        try {
            usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            appSwitchHandler = Handler(Looper.getMainLooper())
            
            // 获取当前应用包名
            currentPackageName = getCurrentAppPackageName()
            
            // 启动定期检查
            startAppSwitchMonitoring()
            
            Log.d(TAG, "应用切换监听器已初始化，当前应用: $currentPackageName")
        } catch (e: Exception) {
            Log.e(TAG, "初始化应用切换监听器失败", e)
        }
    }
    
    /**
     * 启动应用切换监控
     */
    private fun startAppSwitchMonitoring() {
        appSwitchRunnable = object : Runnable {
            override fun run() {
                checkAppSwitch()
                // 每500ms检查一次
                appSwitchHandler?.postDelayed(this, 500)
            }
        }
        appSwitchHandler?.post(appSwitchRunnable!!)
    }
    
    /**
     * 检查应用切换
     */
    private fun checkAppSwitch() {
        if (!isAutoMinimizeEnabled) return
        
        try {
            val newPackageName = getCurrentAppPackageName()
            
            // 如果包名发生变化，说明用户切换了应用
            if (newPackageName != null && newPackageName != currentPackageName) {
                Log.d(TAG, "检测到应用切换: $currentPackageName -> $newPackageName")
                
                // 如果当前不是圆球状态，则自动缩小
                if (ballView == null || ballView?.visibility != View.VISIBLE) {
                    Log.d(TAG, "自动缩小灵动岛为圆球状态")
                    hideContentAndSwitchToBall()
                }
                
                currentPackageName = newPackageName
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查应用切换失败", e)
        }
    }
    
    /**
     * 获取当前前台应用的包名
     */
    private fun getCurrentAppPackageName(): String? {
        return try {
            // 使用ActivityManager获取当前应用
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            val runningTasks = activityManager.getRunningTasks(1)
            if (runningTasks.isNotEmpty()) {
                runningTasks[0].topActivity?.packageName
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取当前应用包名失败", e)
            null
        }
    }
    
    /**
     * 初始化剪贴板监听器
     */
    private fun initClipboardListener() {
        try {
            clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            
            clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
                if (isClipboardAutoExpandEnabled) {
                    handleClipboardChange()
                }
            }
            
            clipboardManager?.addPrimaryClipChangedListener(clipboardListener)
            
            // 初始化当前剪贴板内容
            updateLastClipboardContent()
            
            Log.d(TAG, "剪贴板监听器已初始化")
        } catch (e: Exception) {
            Log.e(TAG, "初始化剪贴板监听器失败", e)
        }
    }
    
    /**
     * 处理剪贴板变化
     */
    private fun handleClipboardChange() {
        try {
            val currentTime = System.currentTimeMillis()
            
            // 防抖处理：如果距离上次变化时间太短，忽略
            if (currentTime - lastClipboardChangeTime < clipboardChangeDebounceTime) {
                Log.d(TAG, "剪贴板变化过于频繁，忽略此次变化 (${currentTime - lastClipboardChangeTime}ms)")
                return
            }
            
            val currentContent = getCurrentClipboardContent()
            
            // 严格的内容验证
            if (currentContent != null && 
                currentContent.isNotEmpty() && 
                currentContent != lastClipboardContent &&
                isValidClipboardContent(currentContent) &&
                isUserGeneratedClipboard(currentContent)) {
                
                Log.d(TAG, "检测到有效的剪贴板内容变化: ${currentContent.take(50)}${if (currentContent.length > 50) "..." else ""}")
                
                // 更新时间和内容
                lastClipboardChangeTime = currentTime
                lastClipboardContent = currentContent
                
                // 延迟一小段时间再展开，确保不是系统自动复制
                windowContainerView?.postDelayed({
                    // 再次验证内容是否还有效（避免快速变化的剪贴板）
                    val verifyContent = getCurrentClipboardContent()
                    if (verifyContent == currentContent) {
                autoExpandForClipboard(currentContent)
                    } else {
                        Log.d(TAG, "剪贴板内容已变化，取消展开")
                    }
                }, 200) // 200ms延迟验证
            } else {
                Log.d(TAG, "剪贴板内容未通过验证或为重复内容")
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理剪贴板变化失败", e)
        }
    }
    
    /**
     * 为剪贴板内容自动展开灵动岛
     */
    private fun autoExpandForClipboard(content: String) {
        try {
            Log.d(TAG, "为剪贴板内容自动展开灵动岛: $content")
            
            // 如果当前是圆球状态，先恢复灵动岛状态
            if (ballView != null && ballView?.visibility == View.VISIBLE) {
                restoreIslandState()
                // 等待恢复动画完成后再展开搜索
                windowContainerView?.postDelayed({
                    expandIslandForClipboard(content)
                }, 500)
            } else {
                // 直接展开搜索
                expandIslandForClipboard(content)
            }
        } catch (e: Exception) {
            Log.e(TAG, "自动展开灵动岛失败", e)
        }
    }
    
    /**
     * 为剪贴板内容展开搜索模式（显示最近选中app历史的图标列表）
     */
    private fun expandIslandForClipboard(content: String) {
        try {
            Log.d(TAG, "为剪贴板内容展开灵动岛，显示最近app历史: $content")
            
            // 只展开灵动岛动画，显示app历史图标
            if (!isSearchModeActive) {
                // 创建展开动画，显示最近app历史
                animateIslandForClipboardWithApps(content)
            }
            
            Log.d(TAG, "剪贴板内容检测完成: $content")
        } catch (e: Exception) {
            Log.e(TAG, "展开灵动岛动画失败", e)
        }
    }
    
    /**
     * 为剪贴板内容创建展开动画（显示最近app历史图标列表）
     */
    private fun animateIslandForClipboardWithApps(clipboardContent: String) {
        try {
            val animator = ValueAnimator.ofInt(compactWidth, expandedWidth)
            animator.duration = 350
            animator.interpolator = AccelerateDecelerateInterpolator()
            animator.addUpdateListener {
                val params = animatingIslandView?.layoutParams as? FrameLayout.LayoutParams
                params?.width = it.animatedValue as Int
                // 使用自适应高度
                params?.height = FrameLayout.LayoutParams.WRAP_CONTENT
                animatingIslandView?.layoutParams = params
                animatingIslandView?.requestLayout()
            }
            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    // 展开时显示最近app历史图标列表
                    createClipboardAppHistoryView(clipboardContent)
                }
                override fun onAnimationEnd(animation: Animator) {
                    // 动画结束后，重新计算面板位置
                    updatePanelPositions()
                    // 延迟5秒自动缩小到球状（给用户更多时间选择）
                    windowContainerView?.postDelayed({
                        // 缩小到球状，而不是完全收起
                        hideContentAndSwitchToBall()
                    }, 5000)
                }
            })
            animator.start()
        } catch (e: Exception) {
            Log.e(TAG, "创建剪贴板展开动画失败", e)
        }
    }
    
    /**
     * 创建剪贴板app历史视图
     */
    private fun createClipboardAppHistoryView(clipboardContent: String) {
        try {
            // 创建主容器布局（垂直方向）
            val mainContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
                
                // 设置半透明背景，确保内容可见
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#E6000000")) // 半透明黑色背景
                    cornerRadius = 20.dpToPx().toFloat()
                    setStroke(1.dpToPx(), Color.parseColor("#40FFFFFF")) // 白色边框
                }
                
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            
            // 创建应用图标容器（水平方向）
            val appIconsContainer = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            
            // 智能场景匹配：分析内容类型
            val contentType = contentAnalyzer.analyzeContent(clipboardContent)
            val recommendedScenes = sceneRecommendationEngine.getRecommendedScenes(contentType)
            
            Log.d(TAG, "剪贴板内容分析: $clipboardContent -> $contentType, 推荐场景: $recommendedScenes")
            
            // 获取智能推荐的应用列表
            val recommendedApps = getSmartRecommendedApps(clipboardContent, contentType, recommendedScenes)
            
            // 合并最近使用的应用和智能推荐的应用，按优先级排序
            val allApps = (recommendedApps + recentApps).distinctBy { it.packageName }
                .sortedByDescending { appInfo ->
                    sceneRecommendationEngine.calculateAppPriority(appInfo.label, contentType, recommendedScenes)
                }
                .take(6)
            
            if (allApps.isNotEmpty()) {
                Log.d(TAG, "显示 ${allApps.size} 个智能推荐app图标")
                
                allApps.forEachIndexed { index, appInfo ->
                    val iconButton = createAppIconButton(appInfo, clipboardContent)
                    appIconsContainer.addView(iconButton)
                    
                    // 添加间距（除了最后一个）
                    if (index < allApps.size - 1) {
                        val spacer = View(this).apply {
                            layoutParams = LinearLayout.LayoutParams(8.dpToPx(), 1)
                        }
                        appIconsContainer.addView(spacer)
                    }
                }
                
                // 在app图标最后添加退出按钮
                val exitButton = createExitButton()
                appIconsContainer.addView(exitButton)
                Log.d(TAG, "退出按钮已添加到app图标容器")
            } else {
                // 没有推荐的应用，显示提示
                val hintText = TextView(this).apply {
                    text = "暂无推荐的应用"
                    textSize = 12f
                    setTextColor(Color.WHITE)
                    gravity = Gravity.CENTER
                }
                appIconsContainer.addView(hintText)
            }
            
            // 添加应用图标容器到主容器
            mainContainer.addView(appIconsContainer)
            
            // 添加AI预览部分
            Log.d(TAG, "创建AI预览容器")
            val aiPreviewContainer = createAIPreviewContainer(clipboardContent)
            mainContainer.addView(aiPreviewContainer)
            Log.d(TAG, "AI预览容器已添加到主容器")
            
            islandContentView = mainContainer
            animatingIslandView?.addView(islandContentView)
            Log.d(TAG, "剪贴板视图创建完成，包含应用图标、AI预览和退出按钮")
            
        } catch (e: Exception) {
            Log.e(TAG, "创建剪贴板app历史视图失败", e)
        }
    }
    
    /**
     * 创建退出按钮
     */
    private fun createExitButton(): View {
        val exitButton = ImageButton(this).apply {
            id = View.generateViewId()
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(6.dpToPx(), 6.dpToPx(), 6.dpToPx(), 6.dpToPx())
            
            // 设置按钮背景
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#80000000")) // 半透明黑色背景
                cornerRadius = 12.dpToPx().toFloat()
                setStroke(1.dpToPx(), Color.parseColor("#60FFFFFF")) // 白色边框
            }
            
            // 设置按钮大小，与app图标保持一致
            layoutParams = LinearLayout.LayoutParams(
                40.dpToPx(),
                40.dpToPx()
            ).apply {
                gravity = Gravity.CENTER
                leftMargin = 8.dpToPx() // 与app图标保持间距
            }
            
            // 设置点击事件
            setOnClickListener {
                Log.d(TAG, "退出按钮被点击，切换到球状态")
                hideContentAndSwitchToBall()
            }
        }
        
        return exitButton
    }
    
    /**
     * 创建AI预览容器
     */
    private fun createAIPreviewContainer(clipboardContent: String): View {
        Log.d(TAG, "开始创建AI预览容器")
        
        val aiContainer = LinearLayout(this).apply {
            id = View.generateViewId() // 使用动态ID
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
            
            // 设置半透明背景
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#CC000000")) // 更明显的背景
                cornerRadius = 12.dpToPx().toFloat()
                setStroke(1.dpToPx(), Color.parseColor("#60FFFFFF")) // 更明显的边框
            }
            
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT // 自适应高度
            ).apply {
                topMargin = 12.dpToPx()
                leftMargin = 8.dpToPx()
                rightMargin = 8.dpToPx()
            }
        }
        
        // AI图标和名称容器
        val aiHeaderContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 8.dpToPx()
            }
        }
        
        // AI图标
        val aiIcon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(40.dpToPx(), 40.dpToPx())
            setImageResource(R.drawable.ic_chat) // 使用聊天图标
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setColorFilter(Color.parseColor("#4CAF50")) // 绿色AI图标
            
            // 添加圆形背景
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#404CAF50"))
                shape = GradientDrawable.OVAL
            }
            
            // 添加点击切换AI功能
            setOnClickListener {
                Log.d(TAG, "AI图标被点击，显示AI选择对话框")
                showAISelectionDialog(clipboardContent)
            }
        }
        
        // AI提供商标签
        val aiProviderLabel = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 4.dpToPx()
            }
            text = currentAIProvider
            textSize = 11f
            setTextColor(Color.parseColor("#4CAF50"))
            setPadding(8.dpToPx(), 4.dpToPx(), 8.dpToPx(), 4.dpToPx())
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#204CAF50"))
                cornerRadius = 8.dpToPx().toFloat()
            }
            gravity = Gravity.CENTER
        }
        
        // AI回复内容区域（可延展）
        val aiResponseContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        // AI回复预览文本
        val aiPreviewText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = "AI正在分析中..."
            textSize = 12f
            setTextColor(Color.WHITE)
            maxLines = 3 // 增加显示行数
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
            gravity = Gravity.CENTER
            setLineSpacing(2.dpToPx().toFloat(), 1.2f) // 增加行间距
            
            // 设置背景
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#20000000"))
                cornerRadius = 8.dpToPx().toFloat()
            }
            
            // 添加点击查看完整回复功能
            setOnClickListener {
                Log.d(TAG, "AI预览文本被点击，显示完整回复")
                showFullAIResponse(clipboardContent)
            }
        }
        
        // 展开/折叠按钮
        val expandButton = ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                24.dpToPx(),
                24.dpToPx()
            ).apply {
                gravity = Gravity.CENTER
                topMargin = 4.dpToPx()
            }
            setImageResource(R.drawable.ic_expand_more)
            setColorFilter(Color.parseColor("#4CAF50"))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#20000000"))
                shape = GradientDrawable.OVAL
            }
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            
            setOnClickListener {
                Log.d(TAG, "展开按钮被点击，显示完整回复")
                showFullAIResponse(clipboardContent)
            }
        }
        
        // 组装布局
        aiHeaderContainer.addView(aiIcon)
        aiHeaderContainer.addView(aiProviderLabel)
        
        aiResponseContainer.addView(aiPreviewText)
        aiResponseContainer.addView(expandButton)
        
        aiContainer.addView(aiHeaderContainer)
        aiContainer.addView(aiResponseContainer)
        
        // 异步获取AI回复
        Log.d(TAG, "开始获取AI回复预览")
        getAIResponsePreview(clipboardContent, aiPreviewText)
        
        Log.d(TAG, "AI预览容器创建完成")
        return aiContainer
    }
    
    // AI相关变量
    private var currentAIProvider = "DeepSeek" // 默认AI提供商
    private val aiProviders = listOf("DeepSeek", "GPT-4", "Claude", "Gemini")
    
    /**
     * 显示AI选择对话框
     */
    private fun showAISelectionDialog(clipboardContent: String) {
        try {
            val items = aiProviders.toTypedArray()
            val checkedItem = aiProviders.indexOf(currentAIProvider)
            
            // 使用Application Context避免Service context问题
            val context = applicationContext
            AlertDialog.Builder(ContextThemeWrapper(context, android.R.style.Theme_Material_Dialog))
                .setTitle("选择AI助手")
                .setSingleChoiceItems(items, checkedItem) { dialog, which ->
                    currentAIProvider = aiProviders[which]
                    Log.d(TAG, "AI提供商已切换为: $currentAIProvider")
                    dialog.dismiss()
                    
                    // 重新获取AI回复
                    refreshAIResponse(clipboardContent)
                }
                .setNegativeButton("取消", null)
                .create()
                .apply {
                    window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                    show()
                }
        } catch (e: Exception) {
            Log.e(TAG, "显示AI选择对话框失败", e)
            // 降级处理：直接切换到下一个AI
            val currentIndex = aiProviders.indexOf(currentAIProvider)
            val nextIndex = (currentIndex + 1) % aiProviders.size
            currentAIProvider = aiProviders[nextIndex]
            refreshAIResponse(clipboardContent)
            Toast.makeText(this, "已切换到 $currentAIProvider", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 获取AI回复预览
     */
    private fun getAIResponsePreview(content: String, textView: TextView) {
        // 设置加载状态
        Handler(Looper.getMainLooper()).post {
            textView.text = "AI正在分析中..."
        }
        
        // 获取当前选择的AI服务
        val spinner = aiAssistantPanelView?.findViewById<Spinner>(R.id.ai_service_spinner)
            ?: configPanelView?.findViewById<Spinner>(R.id.ai_service_spinner)
        val selectedService = spinner?.selectedItem?.toString() ?: "DeepSeek"
        
        // 将显示名称映射到AIServiceType
        val serviceType = when (selectedService) {
            "DeepSeek" -> AIServiceType.DEEPSEEK
            "智谱AI" -> AIServiceType.ZHIPU_AI
            "Kimi" -> AIServiceType.KIMI
            "ChatGPT" -> AIServiceType.CHATGPT
            "Claude" -> AIServiceType.CLAUDE
            "Gemini" -> AIServiceType.GEMINI
            "文心一言" -> AIServiceType.WENXIN
            "通义千问" -> AIServiceType.QIANWEN
            "讯飞星火" -> AIServiceType.XINGHUO
            else -> AIServiceType.DEEPSEEK
        }
        
        // 创建AI API管理器
        val aiApiManager = AIApiManager(this)
        
        // 调用真实API
        aiApiManager.sendMessage(
            serviceType = serviceType,
            message = "请简要分析以下内容：$content",
            conversationHistory = emptyList(),
            callback = object : AIApiManager.StreamingCallback {
                override fun onChunkReceived(chunk: String) {
                    uiHandler.post {
                        val currentText = textView.text?.toString() ?: ""
                        val newText = currentText + chunk
                        // 限制预览长度
                        textView.text = if (newText.length > 50) {
                            newText.take(50) + "..."
                        } else {
                            newText
                        }
                    }
                }
                
                override fun onComplete(fullResponse: String) {
                    uiHandler.post {
                        // 限制预览长度
                        textView.text = if (fullResponse.length > 50) {
                            fullResponse.take(50) + "..."
                        } else {
                            fullResponse
                        }
                    }
                }
                
                override fun onError(error: String) {
                    uiHandler.post {
                        textView.text = "AI分析失败"
                    }
                }
            }
        )
    }
    
    /**
     * 刷新AI回复
     */
    private fun refreshAIResponse(content: String) {
        // 查找AI预览文本视图并更新
        islandContentView?.let { container ->
            // 查找AI容器（第二个子视图）
            if (container is LinearLayout && container.childCount > 1) {
                val aiContainer = container.getChildAt(1) as? LinearLayout
                if (aiContainer != null && aiContainer.childCount >= 3) {
                    // 更新AI提供商标签（第二个子视图）
                    val aiProviderLabel = aiContainer.getChildAt(1) as? TextView
                    aiProviderLabel?.text = currentAIProvider
                    
                    // 更新AI预览文本（第三个子视图）
                    val aiPreviewText = aiContainer.getChildAt(2) as? TextView
                    aiPreviewText?.let {
                        it.text = "AI正在分析中..."
                        getAIResponsePreview(content, it)
                    }
                }
            }
        }
    }
    
    /**
     * 显示完整AI回复
     */
    private fun showFullAIResponse(content: String) {
        try {
            val context = applicationContext
            val dialog = AlertDialog.Builder(ContextThemeWrapper(context, android.R.style.Theme_Material_Dialog))
                .setTitle("AI回复 - $currentAIProvider")
                .setMessage("正在获取完整回复...")
                .setPositiveButton("关闭", null)
                .create()
            
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            dialog.show()
            
            // 获取当前选择的AI服务
            val spinner = aiAssistantPanelView?.findViewById<Spinner>(R.id.ai_service_spinner)
                ?: configPanelView?.findViewById<Spinner>(R.id.ai_service_spinner)
            val selectedService = spinner?.selectedItem?.toString() ?: "DeepSeek"
            
            // 将显示名称映射到AIServiceType
            val serviceType = when (selectedService) {
                "DeepSeek" -> AIServiceType.DEEPSEEK
                "智谱AI" -> AIServiceType.ZHIPU_AI
                "Kimi" -> AIServiceType.KIMI
                "ChatGPT" -> AIServiceType.CHATGPT
                "Claude" -> AIServiceType.CLAUDE
                "Gemini" -> AIServiceType.GEMINI
                "文心一言" -> AIServiceType.WENXIN
                "通义千问" -> AIServiceType.QIANWEN
                "讯飞星火" -> AIServiceType.XINGHUO
                else -> AIServiceType.DEEPSEEK
            }
            
            // 创建AI API管理器
            val aiApiManager = AIApiManager(this)
            
            // 调用真实API
            aiApiManager.sendMessage(
                serviceType = serviceType,
                message = "请详细分析以下内容：$content",
                conversationHistory = emptyList(),
                callback = object : AIApiManager.StreamingCallback {
                    override fun onChunkReceived(chunk: String) {
                        uiHandler.post {
                            val currentMessage = dialog.findViewById<TextView>(android.R.id.message)?.text?.toString() ?: ""
                            dialog.setMessage(currentMessage + chunk)
                        }
                    }
                    
                    override fun onComplete(fullResponse: String) {
                        uiHandler.post {
                            dialog.setMessage(fullResponse)
                        }
                    }
                    
                    override fun onError(error: String) {
                        uiHandler.post {
                            dialog.setMessage("获取AI回复失败: $error")
                        }
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "显示完整AI回复失败", e)
        }
    }
    
    /**
     * 调用AI API
     */
    private fun callAIAPI(content: String, provider: String): String {
        return when (provider) {
            "DeepSeek" -> callDeepSeekAPIForResponse(content)
            "GPT-4" -> callGPT4API(content)
            "Claude" -> callClaudeAPI(content)
            "Gemini" -> callGeminiAPI(content)
            else -> "不支持的AI提供商: $provider"
        }
    }
    
    /**
     * 调用DeepSeek API获取回复
     */
    private fun callDeepSeekAPIForResponse(content: String): String {
        // 模拟网络延迟
        Thread.sleep(500)
        
        // 智能内容分析
        val contentType = contentAnalyzer.analyzeContent(content)
        
        return when (contentType) {
            ClipboardContentType.ADDRESS -> "🗺️ 检测到地址信息：${content.take(20)}...\n建议使用地图应用进行导航和位置查询。"
            ClipboardContentType.URL -> "🌐 检测到网页链接：${content.take(30)}...\n建议在浏览器中打开查看详细内容。"
            ClipboardContentType.URL_SCHEME -> "📱 检测到应用链接，可直接跳转到对应应用。"
            ClipboardContentType.WEATHER -> "🌤️ 检测到天气相关内容，建议查看天气应用获取实时信息。"
            ClipboardContentType.FINANCE -> "💰 检测到金融相关内容，建议使用金融应用查看详细信息。"
            ClipboardContentType.FOREIGN_LANGUAGE -> "🌍 检测到外文内容，建议使用翻译应用进行翻译。"
            ClipboardContentType.PHONE_NUMBER -> "📞 检测到电话号码，可直接拨打或添加到联系人。"
            ClipboardContentType.EMAIL -> "📧 检测到邮箱地址，可发送邮件或添加到联系人。"
            ClipboardContentType.GENERAL_TEXT -> {
                when {
                    content.length > 100 -> "📄 文本内容较长(${content.length}字符)，建议选择合适的应用进行编辑或分享。"
                    content.contains(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")) -> "📅 检测到日期信息，建议添加到日历应用。"
                    content.contains(Regex("\\d+[元￥$]")) -> "💵 检测到价格信息，建议使用购物或记账应用。"
                    else -> "📝 通用文本内容，根据需要选择相应应用进行处理。"
                }
            }
        }
    }
    
    /**
     * 调用GPT-4 API
     */
    private fun callGPT4API(content: String): String {
        return "GPT-4分析：${content.take(30)}... 建议使用相关应用处理。"
    }
    
    /**
     * 调用Claude API
     */
    private fun callClaudeAPI(content: String): String {
        return "Claude分析：${content.take(30)}... 推荐使用对应应用。"
    }
    
    /**
     * 调用Gemini API
     */
    private fun callGeminiAPI(content: String): String {
        return "Gemini分析：${content.take(30)}... 建议选择合适应用。"
    }
    
    /**
     * 获取智能推荐的应用列表
     */
    private fun getSmartRecommendedApps(content: String, contentType: ClipboardContentType, scenes: List<SceneCategory>): List<AppInfo> {
        val recommendedApps = mutableListOf<AppInfo>()
        
        try {
            // 特殊处理：URL Scheme直接匹配
            if (contentType == ClipboardContentType.URL_SCHEME) {
                val urlSchemeApp = contentAnalyzer.getUrlSchemeApp(content)
                if (urlSchemeApp != null) {
                    // 查找对应的AppInfo
                    val appInfo = findAppByName(urlSchemeApp)
                    if (appInfo != null) {
                        recommendedApps.add(appInfo)
                        Log.d(TAG, "URL Scheme匹配: $urlSchemeApp")
                    }
                }
            }
            
            // 特殊处理：URL链接推荐浏览器
            if (contentType == ClipboardContentType.URL) {
                val browserApps = listOf("Chrome", "Firefox", "UC浏览器", "QQ浏览器", "夸克")
                browserApps.forEach { appName ->
                    val appInfo = findAppByName(appName)
                    if (appInfo != null) {
                        recommendedApps.add(appInfo)
                    }
                }
                Log.d(TAG, "URL链接推荐浏览器应用")
            }
            
            // 根据场景推荐应用
            scenes.forEach { scene ->
                val sceneAppNames = sceneRecommendationEngine.getAppsForScene(scene)
                sceneAppNames.forEach { appName ->
                    val appInfo = findAppByName(appName)
                    if (appInfo != null && !recommendedApps.any { it.packageName == appInfo.packageName }) {
                        recommendedApps.add(appInfo)
                    }
                }
            }
            
            // 特殊场景处理
            when (contentType) {
                ClipboardContentType.ADDRESS -> {
                    // 地址推荐地图应用
                    val mapApps = listOf("高德地图", "百度地图", "腾讯地图")
                    mapApps.forEach { appName ->
                        val appInfo = findAppByName(appName)
                        if (appInfo != null && !recommendedApps.any { it.packageName == appInfo.packageName }) {
                            recommendedApps.add(appInfo)
                        }
                    }
                }
                ClipboardContentType.WEATHER -> {
                    // 天气推荐天气应用
                    val weatherApps = listOf("墨迹天气", "天气通", "彩云天气", "中国天气")
                    weatherApps.forEach { appName ->
                        val appInfo = findAppByName(appName)
                        if (appInfo != null && !recommendedApps.any { it.packageName == appInfo.packageName }) {
                            recommendedApps.add(appInfo)
                        }
                    }
                }
                ClipboardContentType.FINANCE -> {
                    // 金融推荐金融应用
                    val financeApps = listOf("支付宝", "招商银行", "蚂蚁财富", "同花顺", "东方财富")
                    financeApps.forEach { appName ->
                        val appInfo = findAppByName(appName)
                        if (appInfo != null && !recommendedApps.any { it.packageName == appInfo.packageName }) {
                            recommendedApps.add(appInfo)
                        }
                    }
                }
                ClipboardContentType.FOREIGN_LANGUAGE -> {
                    // 外文推荐翻译应用
                    val translationApps = listOf("有道词典", "欧路词典", "百度翻译", "Google翻译")
                    translationApps.forEach { appName ->
                        val appInfo = findAppByName(appName)
                        if (appInfo != null && !recommendedApps.any { it.packageName == appInfo.packageName }) {
                            recommendedApps.add(appInfo)
                        }
                    }
                }
                ClipboardContentType.PHONE_NUMBER, ClipboardContentType.EMAIL -> {
                    // 通讯推荐通讯应用
                    val communicationApps = listOf("微信", "QQ", "电话", "短信")
                    communicationApps.forEach { appName ->
                        val appInfo = findAppByName(appName)
                        if (appInfo != null && !recommendedApps.any { it.packageName == appInfo.packageName }) {
                            recommendedApps.add(appInfo)
                        }
                    }
                }
                else -> {}
            }
            
            Log.d(TAG, "智能推荐应用: ${recommendedApps.map { it.label }}")
            
        } catch (e: Exception) {
            Log.e(TAG, "获取智能推荐应用失败", e)
        }
        
        return recommendedApps
    }
    
    /**
     * 根据应用名称查找AppInfo
     */
    private fun findAppByName(appName: String): AppInfo? {
        return try {
            // 从已安装的应用中查找
            val packageManager = packageManager
            val installedApps = packageManager.getInstalledPackages(0)
            
            for (packageInfo in installedApps) {
                val appLabel = packageManager.getApplicationLabel(packageInfo.applicationInfo).toString()
                if (appLabel == appName) {
                    val appIcon = packageManager.getApplicationIcon(packageInfo.applicationInfo)
                    val urlScheme = getUrlSchemeForPackage(packageInfo.packageName)
                    
                    return AppInfo(
                        label = appLabel,
                        packageName = packageInfo.packageName,
                        icon = appIcon,
                        urlScheme = urlScheme
                    )
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "查找应用失败: $appName", e)
            null
        }
    }
    
    /**
     * 创建app图标按钮
     */
    private fun createAppIconButton(appInfo: AppInfo, clipboardContent: String): View {
        return ImageView(this).apply {
            // 进一步缩小图标大小（从44dp缩小到22dp）
            val iconSize = 22.dpToPx()
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                // 垂直居中对齐
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            // 使用CENTER_INSIDE确保图标完整显示，不被裁剪
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            
            // 减少内边距，适应更小的图标
            val padding = 3.dpToPx()
            setPadding(padding, padding, padding, padding)
            
            // 设置圆形背景
            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#33FFFFFF")) // 半透明白色背景
            }
            background = drawable
            
            // 设置app图标
            val icon = getAppIconDrawable(appInfo)
            if (icon != null) {
                setImageDrawable(icon)
            } else {
                setImageResource(R.drawable.ic_apps)
            }
            
            // 设置点击监听器
            setOnClickListener {
                Log.d(TAG, "点击app图标: ${appInfo.label}")
                handleAppIconClick(appInfo, clipboardContent)
            }
            
            // 添加点击动画效果
            setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    }
                }
                false
            }
        }
    }
    
    /**
     * 处理app图标点击事件
     */
    private fun handleAppIconClick(appInfo: AppInfo, clipboardContent: String) {
        try {
            // 立即缩小到球状态，而不是完全收起
            hideContentAndSwitchToBall()
            
            // 特殊处理：URL链接使用DualFloatingWebViewService
            val contentType = contentAnalyzer.analyzeContent(clipboardContent)
            if (contentType == ClipboardContentType.URL) {
                Log.d(TAG, "URL链接，启动DualFloatingWebViewService")
                startDualFloatingWebViewService(clipboardContent)
                return
            }
            
            // 判断是否支持URL Scheme搜索
            if (!appInfo.urlScheme.isNullOrEmpty()) {
                // 支持URL Scheme，使用正确的格式跳转到app搜索页面
                val encodedContent = Uri.encode(clipboardContent)
                val intent = when (appInfo.urlScheme) {
                    // 社交类
                    "weixin" -> Intent(Intent.ACTION_VIEW, Uri.parse("weixin://dl/search?query=$encodedContent"))
                    "mqqapi" -> Intent(Intent.ACTION_VIEW, Uri.parse("mqqapi://search?query=$encodedContent"))
                    "wework" -> Intent(Intent.ACTION_VIEW, Uri.parse("wework://search?query=$encodedContent"))
                    "tim" -> Intent(Intent.ACTION_VIEW, Uri.parse("tim://search?query=$encodedContent"))
                    "sinaweibo" -> Intent(Intent.ACTION_VIEW, Uri.parse("sinaweibo://searchall?q=$encodedContent"))
                    "xhsdiscover" -> Intent(Intent.ACTION_VIEW, Uri.parse("xhsdiscover://search/result?keyword=$encodedContent"))
                    "douban" -> Intent(Intent.ACTION_VIEW, Uri.parse("douban://search?q=$encodedContent"))
                    "twitter" -> Intent(Intent.ACTION_VIEW, Uri.parse("twitter://search?query=$encodedContent"))
                    "zhihu" -> Intent(Intent.ACTION_VIEW, Uri.parse("zhihu://search?q=$encodedContent"))
                    
                    // 购物类
                    "taobao" -> Intent(Intent.ACTION_VIEW, Uri.parse("taobao://s.taobao.com?q=$encodedContent"))
                    "alipay" -> Intent(Intent.ACTION_VIEW, Uri.parse("alipays://platformapi/startapp?appId=20000067&query=$encodedContent"))
                    "openapp.jdmobile" -> Intent(Intent.ACTION_VIEW, Uri.parse("openapp.jdmobile://virtual?params={\"des\":\"productList\",\"keyWord\":\"$encodedContent\"}"))
                    "pinduoduo" -> Intent(Intent.ACTION_VIEW, Uri.parse("pinduoduo://com.xunmeng.pinduoduo/search_result.html?search_key=$encodedContent"))
                    "tmall" -> Intent(Intent.ACTION_VIEW, Uri.parse("tmall://page.tm/search?q=$encodedContent"))
                    "fleamarket" -> Intent(Intent.ACTION_VIEW, Uri.parse("fleamarket://x_search_items?keyword=$encodedContent"))
                    
                    // 视频类
                    "snssdk1128" -> Intent(Intent.ACTION_VIEW, Uri.parse("snssdk1128://search/tabs?keyword=$encodedContent"))
                    "bilibili" -> Intent(Intent.ACTION_VIEW, Uri.parse("bilibili://search?keyword=$encodedContent"))
                    "qqlive" -> Intent(Intent.ACTION_VIEW, Uri.parse("qqlive://search?query=$encodedContent"))
                    "iqiyi" -> Intent(Intent.ACTION_VIEW, Uri.parse("iqiyi://search?key=$encodedContent"))
                    "youtube" -> Intent(Intent.ACTION_VIEW, Uri.parse("youtube://results?search_query=$encodedContent"))
                    
                    // 音乐类
                    "qqmusic" -> Intent(Intent.ACTION_VIEW, Uri.parse("qqmusic://search?key=$encodedContent"))
                    "orpheus" -> Intent(Intent.ACTION_VIEW, Uri.parse("orpheus://search?keyword=$encodedContent"))
                    "spotify" -> Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$encodedContent"))
                    
                    // 生活服务类
                    "imeituan" -> Intent(Intent.ACTION_VIEW, Uri.parse("imeituan://www.meituan.com/search?q=$encodedContent"))
                    "eleme" -> Intent(Intent.ACTION_VIEW, Uri.parse("eleme://search?keyword=$encodedContent"))
                    "dianping" -> Intent(Intent.ACTION_VIEW, Uri.parse("dianping://searchshoplist?keyword=$encodedContent"))
                    
                    // 地图导航类
                    "androidamap" -> Intent(Intent.ACTION_VIEW, Uri.parse("androidamap://poi?sourceApplication=appname&keywords=$encodedContent"))
                    "baidumap" -> Intent(Intent.ACTION_VIEW, Uri.parse("baidumap://map/place/search?query=$encodedContent"))
                    
                    // 浏览器类
                    "mttbrowser" -> Intent(Intent.ACTION_VIEW, Uri.parse("mttbrowser://search?query=$encodedContent"))
                    "ucbrowser" -> Intent(Intent.ACTION_VIEW, Uri.parse("ucbrowser://search?keyword=$encodedContent"))
                    "googlechrome" -> Intent(Intent.ACTION_VIEW, Uri.parse("googlechrome://www.google.com/search?q=$encodedContent"))
                    "firefox" -> Intent(Intent.ACTION_VIEW, Uri.parse("firefox://search?q=$encodedContent"))
                    "quark" -> Intent(Intent.ACTION_VIEW, Uri.parse("quark://search?q=$encodedContent"))
                    
                    // 金融类
                    "cmbmobilebank" -> Intent(Intent.ACTION_VIEW, Uri.parse("cmbmobilebank://search?keyword=$encodedContent"))
                    "antfortune" -> Intent(Intent.ACTION_VIEW, Uri.parse("antfortune://search?keyword=$encodedContent"))
                    
                    // 出行类
                    "diditaxi" -> Intent(Intent.ACTION_VIEW, Uri.parse("diditaxi://search?keyword=$encodedContent"))
                    "cn.12306" -> Intent(Intent.ACTION_VIEW, Uri.parse("cn.12306://search?keyword=$encodedContent"))
                    "ctrip" -> Intent(Intent.ACTION_VIEW, Uri.parse("ctrip://search?keyword=$encodedContent"))
                    "qunar" -> Intent(Intent.ACTION_VIEW, Uri.parse("qunar://search?keyword=$encodedContent"))
                    "hellobike" -> Intent(Intent.ACTION_VIEW, Uri.parse("hellobike://search?keyword=$encodedContent"))
                    
                    // 招聘类
                    "bosszhipin" -> Intent(Intent.ACTION_VIEW, Uri.parse("bosszhipin://search?keyword=$encodedContent"))
                    "liepin" -> Intent(Intent.ACTION_VIEW, Uri.parse("liepin://search?keyword=$encodedContent"))
                    "zhaopin" -> Intent(Intent.ACTION_VIEW, Uri.parse("zhaopin://search?keyword=$encodedContent"))
                    
                    // 教育类
                    "yddict" -> Intent(Intent.ACTION_VIEW, Uri.parse("yddict://search?keyword=$encodedContent"))
                    "eudic" -> Intent(Intent.ACTION_VIEW, Uri.parse("eudic://dict/$encodedContent"))
                    "baicizhan" -> Intent(Intent.ACTION_VIEW, Uri.parse("baicizhan://search?keyword=$encodedContent"))
                    "zuoyebang" -> Intent(Intent.ACTION_VIEW, Uri.parse("zuoyebang://search?keyword=$encodedContent"))
                    "yuansouti" -> Intent(Intent.ACTION_VIEW, Uri.parse("yuansouti://search?keyword=$encodedContent"))
                    
                    // 新闻类
                    "newsapp" -> Intent(Intent.ACTION_VIEW, Uri.parse("newsapp://search?keyword=$encodedContent"))
                    
                    else -> {
                        // 通用URL scheme格式
                        Intent(Intent.ACTION_VIEW, Uri.parse("${appInfo.urlScheme}://search?query=$encodedContent"))
                    }
                }.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                try {
                    startActivity(intent)
                    Toast.makeText(this, "已在${appInfo.label}中搜索: $clipboardContent", Toast.LENGTH_SHORT).show()
                    Log.d(TAG, "通过URL Scheme跳转到${appInfo.label}搜索: ${intent.data}")
                } catch (e: ActivityNotFoundException) {
                    // URL Scheme不可用，降级到普通启动
                    Log.w(TAG, "URL Scheme失败，降级到普通启动: ${appInfo.urlScheme}")
                    launchAppForManualPaste(appInfo)
                } catch (e: Exception) {
                    Log.e(TAG, "启动URL Scheme失败", e)
                    launchAppForManualPaste(appInfo)
                }
            } else {
                // 不支持URL Scheme，启动app让用户手动粘贴
                launchAppForManualPaste(appInfo)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "处理app图标点击失败", e)
            Toast.makeText(this, "启动应用失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 启动DualFloatingWebViewService用于URL链接
     */
    private fun startDualFloatingWebViewService(url: String) {
        try {
            val intent = Intent(this, DualFloatingWebViewService::class.java).apply {
                putExtra("url", url)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startService(intent)
            Log.d(TAG, "启动DualFloatingWebViewService: $url")
        } catch (e: Exception) {
            Log.e(TAG, "启动DualFloatingWebViewService失败", e)
            // 降级处理：尝试用默认浏览器打开
            try {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(browserIntent)
                Log.d(TAG, "降级处理：使用默认浏览器打开URL")
            } catch (e2: Exception) {
                Log.e(TAG, "降级处理也失败", e2)
            }
        }
    }
    
    /**
     * 启动app让用户手动粘贴
     */
    private fun launchAppForManualPaste(appInfo: AppInfo) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                Toast.makeText(this, "已启动${appInfo.label}，请手动粘贴内容", Toast.LENGTH_LONG).show()
                Log.d(TAG, "启动app让用户手动粘贴: ${appInfo.label}")
            } else {
                Toast.makeText(this, "无法启动${appInfo.label}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动app失败: ${appInfo.label}", e)
            Toast.makeText(this, "启动应用失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    
    /**
     * 收起剪贴板提示的灵动岛
     */
    private fun collapseIslandForClipboard() {
        try {
            val animator = ValueAnimator.ofInt(expandedWidth, compactWidth)
            animator.duration = 350
            animator.interpolator = AccelerateDecelerateInterpolator()
            animator.addUpdateListener {
                animatingIslandView?.layoutParams?.width = it.animatedValue as Int
                animatingIslandView?.requestLayout()
            }
            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // 收起时移除内容视图
                    animatingIslandView?.removeAllViews()
                }
            })
            animator.start()
        } catch (e: Exception) {
            Log.e(TAG, "收起剪贴板提示失败", e)
        }
    }
    
    /**
     * 检查剪贴板内容是否有效
     */
    private fun isValidClipboardContent(content: String): Boolean {
        // 过滤掉太短的内容（少于2个字符）
        if (content.length < 2) {
            Log.d(TAG, "剪贴板内容太短: ${content.length}")
            return false
        }
        
        // 过滤掉纯数字内容（可能是验证码等）
        if (content.matches(Regex("^\\d+$"))) {
            Log.d(TAG, "剪贴板内容为纯数字，可能是验证码")
            return false
        }
        
        // 过滤掉纯符号内容
        if (content.matches(Regex("^[^\\p{L}\\p{N}]+$"))) {
            Log.d(TAG, "剪贴板内容为纯符号")
            return false
        }
        
        // 过滤掉太长的内容（超过500字符）
        if (content.length > 500) {
            Log.d(TAG, "剪贴板内容太长: ${content.length}")
            return false
        }
        
        // 过滤掉空白字符（空格、换行等）
        if (content.trim().isEmpty()) {
            Log.d(TAG, "剪贴板内容为空白字符")
            return false
        }
        
        return true
    }
    
    /**
     * 检查是否为用户主动生成的剪贴板内容
     * 过滤掉系统自动生成、应用自动复制等情况
     */
    private fun isUserGeneratedClipboard(content: String): Boolean {
        // 过滤掉常见的系统自动复制内容
        val systemPatterns = listOf(
            // URL模式（除非是搜索关键词）
            Regex("^https?://.*"),
            // 文件路径模式
            Regex("^[a-zA-Z]:\\\\.*"),
            Regex("^/.*"),
            // 包名模式
            Regex("^[a-z]+\\.[a-z]+\\.[a-z]+.*"),
            // 系统信息模式
            Regex(".*Build.*API.*"),
            Regex(".*Android.*version.*"),
            // 错误日志模式
            Regex(".*Exception.*at.*"),
            Regex(".*Error.*line.*"),
            // UUID模式
            Regex("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$"),
            // Base64模式
            Regex("^[A-Za-z0-9+/=]{20,}$")
        )
        
        for (pattern in systemPatterns) {
            if (pattern.matches(content)) {
                Log.d(TAG, "剪贴板内容疑似系统自动生成: ${content.take(30)}...")
                return false
            }
        }
        
        // 检查是否包含过多的特殊字符（可能是系统生成的token等）
        val specialCharCount = content.count { !it.isLetterOrDigit() && !it.isWhitespace() && it !in ".,!?;:()[]{}\"'-_" }
        val specialCharRatio = specialCharCount.toFloat() / content.length
        if (specialCharRatio > 0.4) {
            Log.d(TAG, "剪贴板内容包含过多特殊字符，疑似系统生成 (${String.format("%.1f", specialCharRatio * 100)}%)")
            return false
        }
        
        return true
    }
    
    /**
     * 获取当前剪贴板内容
     */
    private fun getCurrentClipboardContent(): String? {
        return try {
            val clipData = clipboardManager?.primaryClip
            if (clipData != null && clipData.itemCount > 0) {
                val item = clipData.getItemAt(0)
                item.text?.toString()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取剪贴板内容失败", e)
            null
        }
    }
    
    /**
     * 更新最后记录的剪贴板内容
     */
    private fun updateLastClipboardContent() {
        lastClipboardContent = getCurrentClipboardContent()
    }
    
    /**
     * 自动粘贴剪贴板内容到搜索框（已禁用）
     */
    private fun autoPasteToSearchInput() {
        // 已禁用自动粘贴功能，因为已移除输入框
        Log.d(TAG, "自动粘贴功能已禁用")
    }
    
    /**
     * 自动粘贴剪贴板内容到AI助手输入框（已禁用）
     */
    private fun autoPasteToAIInput() {
        // 已禁用自动粘贴功能，因为已移除输入框
        Log.d(TAG, "自动粘贴到AI助手功能已禁用")
    }
    
    /**
     * 清理应用切换监听器
     */
    private fun cleanupAppSwitchListener() {
        try {
            appSwitchRunnable?.let { runnable ->
                appSwitchHandler?.removeCallbacks(runnable)
            }
            appSwitchRunnable = null
            appSwitchHandler = null
            usageStatsManager = null
            Log.d(TAG, "应用切换监听器已清理")
        } catch (e: Exception) {
            Log.e(TAG, "清理应用切换监听器失败", e)
        }
    }
    
    /**
     * 清理剪贴板监听器
     */
    private fun cleanupClipboardListener() {
        try {
            clipboardListener?.let { listener ->
                clipboardManager?.removePrimaryClipChangedListener(listener)
            }
            clipboardListener = null
            clipboardManager = null
            lastClipboardContent = null
            Log.d(TAG, "剪贴板监听器已清理")
        } catch (e: Exception) {
            Log.e(TAG, "清理剪贴板监听器失败", e)
        }
    }
    
    /**
     * 显示AI助手面板
     */
    private fun showAIAssistantPanel() {
        if (aiAssistantPanelView != null) return
        
        try {
            val themedContext = ContextThemeWrapper(getThemedContext(), R.style.Theme_FloatingWindow)
            aiAssistantPanelView = LayoutInflater.from(themedContext).inflate(R.layout.ai_assistant_panel, null)
            
            // 设置面板参数
            val panelParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = statusBarHeight + getIslandActualHeight() + 16.dpToPx()
            }
            
        // 设置AI助手面板的交互
        setupAIAssistantPanelInteractions()
        
        // 添加到窗口并显示动画
        windowContainerView?.addView(aiAssistantPanelView, panelParams)
        aiAssistantPanelView?.apply {
            alpha = 0f
            translationY = -100f
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(350)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    // 面板显示完成，不再自动粘贴剪贴板内容
                    Log.d(TAG, "AI助手面板显示完成")
                }
                .start()
        }
        
        Log.d(TAG, "AI助手面板已显示")
        } catch (e: Exception) {
            Log.e(TAG, "显示AI助手面板失败", e)
        }
    }
    
    /**
     * 隐藏AI助手面板
     */
    private fun hideAIAssistantPanel() {
        val panelToRemove = aiAssistantPanelView
        aiAssistantPanelView = null
        
        panelToRemove?.let { panel ->
            panel.animate()
                .alpha(0f)
                .translationY(-100f)
                .setDuration(250)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    try {
                        windowContainerView?.removeView(panel)
                    } catch (e: Exception) {
                        Log.e(TAG, "移除AI助手面板失败", e)
                    }
                }
                .start()
        }
        
        Log.d(TAG, "AI助手面板已隐藏")
    }
    
    /**
     * 设置AI助手面板的交互
     */
    private fun setupAIAssistantPanelInteractions() {
        try {
            // 关闭按钮
            val btnClosePanel = aiAssistantPanelView?.findViewById<ImageButton>(R.id.btn_close_ai_panel)
            btnClosePanel?.setOnClickListener {
                hideAIAssistantPanel()
            }
            
            // AI服务选择器
            val aiServiceSpinner = aiAssistantPanelView?.findViewById<Spinner>(R.id.ai_service_spinner)
            setupAIServiceSpinner(aiServiceSpinner)
            
            // AI输入框
            val aiInputText = aiAssistantPanelView?.findViewById<EditText>(R.id.ai_input_text)
            val aiResponseText = aiAssistantPanelView?.findViewById<TextView>(R.id.ai_response_text)
            
            // AI设置按钮
            val btnAiSettings = aiAssistantPanelView?.findViewById<ImageButton>(R.id.btn_ai_settings)
            btnAiSettings?.setOnClickListener {
                val intent = Intent(this, AIApiConfigActivity::class.java)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            }
            
            // 清除回复按钮
            val btnClearAiResponse = aiAssistantPanelView?.findViewById<ImageButton>(R.id.btn_clear_ai_response)
            btnClearAiResponse?.setOnClickListener {
                aiResponseText?.text = "选择AI服务并输入问题获取回复..."
            }
            
            // 发送消息按钮
            val btnSendAiMessage = aiAssistantPanelView?.findViewById<ImageButton>(R.id.btn_send_ai_message)
            btnSendAiMessage?.setOnClickListener {
                val query = aiInputText?.text?.toString()?.trim()
                if (!query.isNullOrEmpty()) {
                    sendAIMessage(query, aiResponseText)
                    aiInputText?.setText("") // 清空输入框
                }
            }
            
            // 复制回复按钮
            val btnCopyResponse = aiAssistantPanelView?.findViewById<MaterialButton>(R.id.btn_copy_response)
            btnCopyResponse?.setOnClickListener {
                val responseText = aiResponseText?.text?.toString()
                if (!responseText.isNullOrEmpty() && responseText != "选择AI服务并输入问题获取回复...") {
                    copyTextToClipboard(responseText)
                    Toast.makeText(this, "回复已复制到剪贴板", Toast.LENGTH_SHORT).show()
                }
            }
            
            // 分享回复按钮
            val btnShareResponse = aiAssistantPanelView?.findViewById<MaterialButton>(R.id.btn_share_response)
            btnShareResponse?.setOnClickListener {
                val responseText = aiResponseText?.text?.toString()
                if (!responseText.isNullOrEmpty() && responseText != "选择AI服务并输入问题获取回复...") {
                    shareText(responseText)
                }
            }
            
            // 助手选择按钮
            val btnSelectAssistant = aiAssistantPanelView?.findViewById<MaterialButton>(R.id.btn_select_assistant)
            btnSelectAssistant?.setOnClickListener {
                showAssistantSelector()
            }
            
            // 身份生成按钮
            val btnGeneratePrompt = aiAssistantPanelView?.findViewById<MaterialButton>(R.id.btn_generate_prompt)
            if (btnGeneratePrompt != null) {
                Log.d(TAG, "找到AI助手面板的身份按钮，设置点击监听器")
                btnGeneratePrompt.setOnClickListener {
                    Log.d(TAG, "AI助手面板身份按钮被点击")
                    
                    // 添加点击动画效果，参考搜索面板的实现
                    btnGeneratePrompt.animate()
                        .scaleX(0.95f)
                        .scaleY(0.95f)
                        .setDuration(100)
                        .withEndAction {
                            btnGeneratePrompt.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(100)
                                .start()
                        }
                        .start()
                    
                    // 调用档案选择器
                    showPromptProfileSelectorForAI()
                }
            } else {
                Log.e(TAG, "找不到AI助手面板的身份按钮 (btn_generate_prompt)")
            }
            
            // 折叠/展开按钮
            val btnFoldResponse = aiAssistantPanelView?.findViewById<ImageButton>(R.id.btn_fold_response)
            btnFoldResponse?.setOnClickListener {
                toggleResponseFold()
            }
            
            Log.d(TAG, "AI助手面板交互设置完成")
        } catch (e: Exception) {
            Log.e(TAG, "设置AI助手面板交互失败", e)
        }
    }
    
    /**
     * 切换AI服务
     */
    private fun switchAIService() {
        try {
            val spinner = aiAssistantPanelView?.findViewById<Spinner>(R.id.ai_service_spinner)
            if (spinner != null) {
                val currentPosition = spinner.selectedItemPosition
                val itemCount = spinner.adapter?.count ?: 0
                
                if (itemCount > 1) {
                    // 切换到下一个AI服务
                    val nextPosition = (currentPosition + 1) % itemCount
                    spinner.setSelection(nextPosition)
                    
                    val selectedService = spinner.selectedItem?.toString() ?: "未知"
                    Log.d(TAG, "AI服务已切换到: $selectedService")
                    
                    // 显示切换提示
                    Toast.makeText(this, "已切换到: $selectedService", Toast.LENGTH_SHORT).show()
                } else {
                    Log.w(TAG, "没有可切换的AI服务")
                    Toast.makeText(this, "没有可切换的AI服务", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.w(TAG, "找不到AI服务选择器")
            }
        } catch (e: Exception) {
            Log.e(TAG, "切换AI服务失败", e)
            Toast.makeText(this, "切换AI服务失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 切换回复区域折叠状态
     */
    private fun toggleResponseFold() {
        try {
            val responseScroll = aiAssistantPanelView?.findViewById<ScrollView>(R.id.ai_response_scroll)
            val foldButton = aiAssistantPanelView?.findViewById<ImageButton>(R.id.btn_fold_response)
            
            if (responseScroll != null && foldButton != null) {
                isResponseFolded = !isResponseFolded
                
                if (isResponseFolded) {
                    // 折叠：隐藏滚动区域
                    responseScroll.visibility = View.GONE
                    foldButton.setImageResource(R.drawable.ic_expand_more)
                    Log.d(TAG, "AI回复区域已折叠")
                } else {
                    // 展开：显示滚动区域
                    responseScroll.visibility = View.VISIBLE
                    foldButton.setImageResource(R.drawable.ic_expand_less)
                    Log.d(TAG, "AI回复区域已展开")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "切换回复区域折叠状态失败", e)
        }
    }
    
    /**
     * 分享文本
     */
    private fun shareText(text: String) {
        try {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            }
            val chooserIntent = Intent.createChooser(shareIntent, "分享AI回复")
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(chooserIntent)
        } catch (e: Exception) {
            Log.e(TAG, "分享文本失败", e)
            Toast.makeText(this, "分享失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 恢复灵动岛状态
     * 用户点击圆球后，恢复横条状态
     */
    private fun restoreIslandState() {
        Log.d(TAG, "恢复灵动岛状态")
        
        // 隐藏圆球，带有缩放动画
        ballView?.animate()
            ?.alpha(0f)
            ?.scaleX(0.5f)
            ?.scaleY(0.5f)
            ?.setDuration(300)
            ?.setInterpolator(AccelerateInterpolator())
            ?.withEndAction {
                try {
                    windowContainerView?.removeView(ballView)
                } catch (e: Exception) { /* ignore */ }
                ballView = null
            }
            ?.start()
        
        // 恢复灵动岛视图，带有缩放动画
        animatingIslandView?.visibility = View.VISIBLE
        animatingIslandView?.alpha = 0f
        animatingIslandView?.scaleX = 0.8f
        animatingIslandView?.scaleY = 0.8f
        animatingIslandView?.animate()
            ?.alpha(1f)
            ?.scaleX(1f)
            ?.scaleY(1f)
            ?.setDuration(400)
            ?.setInterpolator(OvershootInterpolator(0.8f))
            ?.start()
    }
    
    /**
     * 最小化灵动岛
     * 将灵动岛切换到圆球状态，实现真正的灵动岛最小化效果
     */
    private fun minimizeDynamicIsland() {
        try {
            Log.d(TAG, "开始最小化灵动岛为圆球状态")
            
            // 隐藏所有面板和内容
            hideAppSearchResults()
            hideConfigPanel()
            hideNotificationExpandedView()
            
            // 隐藏键盘
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(windowContainerView?.windowToken, 0)
            searchInput?.clearFocus()
            searchInput?.setText("")
            
            // 切换到圆球状态
            transitionToBallState()
            
            // 显示最小化提示
            Toast.makeText(this, "灵动岛已最小化为圆球", Toast.LENGTH_SHORT).show()
            
            Log.d(TAG, "灵动岛最小化为圆球完成")
        } catch (e: Exception) {
            Log.e(TAG, "最小化灵动岛失败", e)
            Toast.makeText(this, "最小化失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun exitDynamicIsland() {
        try {
            // 停止服务
            stopSelf()
            
            // 启动设置页面
            val intent = Intent(this, MainSettingsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            
            Log.d(TAG, "灵动岛已退出，打开设置页面")
        } catch (e: Exception) {
            Log.e(TAG, "退出灵动岛失败", e)
        }
    }
    
    // 最近选中的APP相关方法
    private fun addToRecentApps(appInfo: AppInfo) {
        // 移除已存在的相同APP
        recentApps.removeAll { it.packageName == appInfo.packageName }
        // 添加到列表开头
        recentApps.add(0, appInfo)
        // 限制最多保存10个
        if (recentApps.size > 10) {
            recentApps.removeAt(recentApps.size - 1)
        }
        // 保存到SharedPreferences
        saveRecentAppsToPrefs()
        Log.d(TAG, "添加到最近APP: ${appInfo.label}")
    }
    
    /**
     * 用于序列化的简化AppInfo数据类（不包含Drawable）
     */
    private data class SerializableAppInfo(
        val label: String,
        val packageName: String,
        val urlScheme: String? = null
    )
    
    /**
     * 将AppInfo转换为可序列化的格式
     */
    private fun AppInfo.toSerializable(): SerializableAppInfo {
        return SerializableAppInfo(
            label = this.label,
            packageName = this.packageName,
            urlScheme = this.urlScheme
        )
    }
    
    /**
     * 从可序列化格式重建AppInfo（重新加载图标）
     */
    private fun SerializableAppInfo.toAppInfo(): AppInfo? {
        return try {
            // 验证应用是否仍然安装
            packageManager.getPackageInfo(this.packageName, 0)
            
            // 重新加载图标
            val icon = packageManager.getApplicationIcon(this.packageName)
            
            AppInfo(
                label = this.label,
                packageName = this.packageName,
                icon = icon,
                urlScheme = this.urlScheme
            )
        } catch (e: Exception) {
            Log.d(TAG, "应用已卸载或无法加载: ${this.label}")
            null
        }
    }

    /**
     * 保存最近选中的APP列表到SharedPreferences
     */
    private fun saveRecentAppsToPrefs() {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val gson = com.google.gson.Gson()
            
            // 转换为可序列化的格式
            val serializableApps = recentApps.map { it.toSerializable() }
            val jsonString = gson.toJson(serializableApps)
            
            prefs.edit()
                .putString(KEY_RECENT_APPS, jsonString)
                .apply()
            Log.d(TAG, "已保存最近APP列表: ${recentApps.size}个")
        } catch (e: Exception) {
            Log.e(TAG, "保存最近APP列表失败", e)
        }
    }
    
    /**
     * 从SharedPreferences加载最近选中的APP列表
     */
    private fun loadRecentAppsFromPrefs() {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonString = prefs.getString(KEY_RECENT_APPS, null)
            
            if (jsonString != null) {
                val gson = com.google.gson.Gson()
                val type = object : com.google.gson.reflect.TypeToken<List<SerializableAppInfo>>() {}.type
                val serializableApps: List<SerializableAppInfo> = gson.fromJson(jsonString, type)
                
                // 转换回AppInfo并过滤有效的应用
                recentApps.clear()
                serializableApps.forEach { serializableApp ->
                    val appInfo = serializableApp.toAppInfo()
                    if (appInfo != null) {
                        recentApps.add(appInfo)
                    }
                }
                
                Log.d(TAG, "已加载最近APP列表: ${recentApps.size}个")
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载最近APP列表失败", e)
            recentApps.clear()
        }
    }
    
    private fun updateRecentAppButton(appInfo: AppInfo) {
        currentSelectedApp = appInfo
        try {
            // 使用与简易模式相同的图标加载策略
            val icon = getAppIconDrawable(appInfo)
            if (icon != null) {
                // 优先使用icon属性设置
                recentAppButton?.icon = icon
                // 清除iconTint，避免影响真实图标的颜色
                recentAppButton?.iconTint = null
                Log.d(TAG, "更新最近APP按钮图标: ${appInfo.label}")
            } else {
                Log.w(TAG, "获取的图标为null，使用默认图标: ${appInfo.label}")
                val defaultIcon = ContextCompat.getDrawable(this, R.drawable.ic_apps)
                recentAppButton?.icon = defaultIcon
                // 恢复iconTint
                recentAppButton?.iconTint = ContextCompat.getColorStateList(this, R.color.dynamic_island_button_icon)
            }
        } catch (e: Exception) {
            // 如果设置图标失败，使用默认图标
            val defaultIcon = ContextCompat.getDrawable(this, R.drawable.ic_apps)
            recentAppButton?.icon = defaultIcon
            recentAppButton?.iconTint = ContextCompat.getColorStateList(this, R.color.dynamic_island_button_icon)
            Log.e(TAG, "设置APP按钮图标失败: ${appInfo.label}", e)
        }
        
        // 保存当前选中的APP
        saveCurrentSelectedApp(appInfo)
    }
    
    /**
     * 获取应用图标Drawable - 与简易模式相同的策略
     */
    private fun getAppIconDrawable(appInfo: AppInfo): android.graphics.drawable.Drawable? {
        return try {
            if (isAppInstalled(appInfo.packageName)) {
                Log.d(TAG, "应用已安装，获取真实图标: ${appInfo.label}")
                val icon = packageManager.getApplicationIcon(appInfo.packageName)
                // 确保图标不为null
                if (icon != null) {
                    icon
                } else {
                    Log.w(TAG, "获取的图标为null，使用默认图标: ${appInfo.label}")
                    ContextCompat.getDrawable(this, R.drawable.ic_apps)
                }
            } else {
                Log.d(TAG, "应用未安装，使用默认图标: ${appInfo.label}")
                ContextCompat.getDrawable(this, R.drawable.ic_apps)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取应用图标失败: ${appInfo.label}", e)
            ContextCompat.getDrawable(this, R.drawable.ic_apps)
        }
    }
    
    /**
     * 检查应用是否已安装
     */
    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 保存当前选中的APP
     */
    private fun saveCurrentSelectedApp(appInfo: AppInfo) {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_CURRENT_APP_PACKAGE, appInfo.packageName)
                .apply()
            Log.d(TAG, "已保存当前选中APP: ${appInfo.label}")
        } catch (e: Exception) {
            Log.e(TAG, "保存当前选中APP失败", e)
        }
    }
    
    /**
     * 恢复当前选中的APP
     */
    private fun restoreCurrentSelectedApp() {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val packageName = prefs.getString(KEY_CURRENT_APP_PACKAGE, null)
            
            if (packageName != null) {
                // 检查应用是否仍然安装
                try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    val label = packageManager.getApplicationLabel(appInfo).toString()
                    
                    val restoredApp = AppInfo(
                        label = label,
                        packageName = packageName,
                        icon = packageManager.getApplicationIcon(packageName), // 临时设置图标
                        urlScheme = getUrlSchemeForPackage(packageName)
                    )
                    
                    currentSelectedApp = restoredApp
                    // 使用统一的图标加载方法
                    val icon = getAppIconDrawable(restoredApp)
                    if (icon != null) {
                        recentAppButton?.icon = icon
                        recentAppButton?.iconTint = null
                        Log.d(TAG, "已恢复当前选中APP图标: $label")
                    } else {
                        val defaultIcon = ContextCompat.getDrawable(this, R.drawable.ic_apps)
                        recentAppButton?.icon = defaultIcon
                        recentAppButton?.iconTint = ContextCompat.getColorStateList(this, R.color.dynamic_island_button_icon)
                        Log.w(TAG, "恢复APP图标失败，使用默认图标: $label")
                    }
                    Log.d(TAG, "已恢复当前选中APP: $label")
                } catch (e: Exception) {
                    Log.d(TAG, "恢复的APP已卸载: $packageName")
                    // 清除无效的保存状态
                    clearCurrentSelectedApp()
                    // 设置默认图标
                    setDefaultRecentAppIcon()
                }
            } else {
                Log.d(TAG, "没有保存的APP，设置默认图标")
                // 没有保存的APP时，设置默认图标
                setDefaultRecentAppIcon()
            }
        } catch (e: Exception) {
            Log.e(TAG, "恢复当前选中APP失败", e)
            // 出现异常时也设置默认图标
            setDefaultRecentAppIcon()
        }
    }
    
    /**
     * 设置默认的最近APP图标
     */
    private fun setDefaultRecentAppIcon() {
        try {
            val defaultIcon = ContextCompat.getDrawable(this, R.drawable.ic_apps)
            recentAppButton?.icon = defaultIcon
            recentAppButton?.iconTint = ContextCompat.getColorStateList(this, R.color.dynamic_island_button_icon)
            Log.d(TAG, "已设置默认最近APP图标")
        } catch (e: Exception) {
            Log.e(TAG, "设置默认图标失败", e)
        }
    }
    
    /**
     * 清除当前选中的APP
     */
    private fun clearCurrentSelectedApp() {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .remove(KEY_CURRENT_APP_PACKAGE)
                .apply()
            currentSelectedApp = null
            val defaultIcon = ContextCompat.getDrawable(this, R.drawable.ic_apps)
            recentAppButton?.icon = defaultIcon
            recentAppButton?.iconTint = ContextCompat.getColorStateList(this, R.color.dynamic_island_button_icon)
            Log.d(TAG, "已清除当前选中APP")
        } catch (e: Exception) {
            Log.e(TAG, "清除当前选中APP失败", e)
        }
    }
    
    /**
     * 获取应用的URL scheme
     */
    private fun getUrlSchemeForPackage(packageName: String): String? {
        // 根据AppSearchSettings配置创建完整的包名到URL scheme映射
        val urlSchemeMap = mapOf(
            // 社交类
            "com.tencent.mm" to "weixin",
            "com.tencent.mobileqq" to "mqqapi",
            "com.tencent.wework" to "wework",
            "com.tencent.tim" to "tim",
            "com.sina.weibo" to "sinaweibo",
            "com.xingin.xhs" to "xhsdiscover",
            "com.douban.frodo" to "douban",
            "com.twitter.android" to "twitter",
            "com.zhihu.android" to "zhihu",
            
            // 购物类
            "com.taobao.taobao" to "taobao",
            "com.eg.android.AlipayGphone" to "alipay",
            "com.jingdong.app.mall" to "openapp.jdmobile",
            "com.xunmeng.pinduoduo" to "pinduoduo",
            "com.tmall.wireless" to "tmall",
            "com.taobao.idlefish" to "fleamarket",
            
            // 视频类
            "com.ss.android.ugc.aweme" to "snssdk1128",
            "com.zhiliaoapp.musically" to "snssdk1128",
            "tv.danmaku.bili" to "bilibili",
            "com.tencent.qqlive" to "qqlive",
            "com.iqiyi.app" to "iqiyi",
            "com.google.android.youtube" to "youtube",
            
            // 音乐类
            "com.tencent.qqmusic" to "qqmusic",
            "com.netease.cloudmusic" to "orpheus",
            "com.spotify.music" to "spotify",
            
            // 生活服务类
            "com.sankuai.meituan" to "imeituan",
            "me.ele" to "eleme",
            "com.dianping.v1" to "dianping",
            
            // 地图导航类
            "com.autonavi.minimap" to "androidamap",
            "com.baidu.BaiduMap" to "baidumap",
            
            // 浏览器类
            "com.tencent.mtt" to "mttbrowser",
            "com.UCMobile" to "ucbrowser",
            "com.android.chrome" to "googlechrome",
            "org.mozilla.firefox" to "firefox",
            "com.quark.browser" to "quark",
            
            // 金融类
            "cmb.pb" to "cmbmobilebank",
            "com.antfortune.wealth" to "antfortune",
            
            // 出行类
            "com.sdu.didi.psnger" to "diditaxi",
            "com.MobileTicket" to "cn.12306",
            "ctrip.android.view" to "ctrip",
            "com.Qunar" to "qunar",
            "com.jingyao.easybike" to "hellobike",
            
            // 招聘类
            "com.hpbr.bosszhipin" to "bosszhipin",
            "com.liepin.android" to "liepin",
            "com.job.android" to "zhaopin",
            
            // 教育类
            "com.youdao.dict" to "yddict",
            "com.eusoft.eudic" to "eudic",
            "com.jiongji.andriod.card" to "baicizhan",
            "com.baidu.homework" to "zuoyebang",
            "com.fenbi.android.solar" to "yuansouti",
            
            // 新闻类
            "com.netease.nr" to "newsapp"
        )
        
        return urlSchemeMap[packageName]
    }
    
    private fun showRecentAppsDropdown() {
        if (recentApps.isEmpty()) {
            Toast.makeText(this, "暂无最近选中的APP", Toast.LENGTH_SHORT).show()
            return
        }
        
        val inflater = LayoutInflater.from(this)
        val dropdownView = inflater.inflate(R.layout.recent_apps_dropdown, null)
        
        val recyclerView = dropdownView.findViewById<RecyclerView>(R.id.recent_apps_recycler_view)
        val clearButton = dropdownView.findViewById<Button>(R.id.clear_recent_apps_button)
        
        // 设置适配器
        recentAppAdapter = RecentAppAdapter(
            recentApps,
            onAppClick = { appInfo ->
                // 检查搜索框是否有内容
                val searchText = searchInput?.text.toString().trim()
                if (searchText.isNotEmpty()) {
                    // 搜索框不为空，直接执行搜索
                    Log.d(TAG, "历史列表选择应用，直接执行搜索: ${appInfo.label}, 搜索内容: $searchText")
                    handleSearchWithSelectedApp(searchText, appInfo)
                    // 清除选中的APP，为下次搜索做准备
                    currentSelectedApp = null
                    hideRecentAppsDropdown()
                } else {
                    // 搜索框为空，只选中应用，等待用户输入
                    Log.d(TAG, "历史列表选择应用，等待用户输入: ${appInfo.label}")
                    selectAppFromHistory(appInfo)
                    hideRecentAppsDropdown()
                }
            },
            onAppRemove = { appInfo ->
                // 从最近列表中移除
                recentApps.remove(appInfo)
                saveRecentAppsToPrefs() // 保存移除状态
                recentAppAdapter?.updateApps(recentApps)
                if (currentSelectedApp == appInfo) {
                    currentSelectedApp = null
                    recentAppButton?.icon = ContextCompat.getDrawable(this, R.drawable.ic_apps)
                    recentAppButton?.iconTint = ContextCompat.getColorStateList(this, R.color.dynamic_island_button_icon)
                }
            }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = recentAppAdapter
        
        // 清空按钮
        clearButton.setOnClickListener {
            recentApps.clear()
            saveRecentAppsToPrefs() // 保存清空状态
            recentAppAdapter?.updateApps(recentApps)
            currentSelectedApp = null
            recentAppButton?.icon = ContextCompat.getDrawable(this, R.drawable.ic_apps)
            recentAppButton?.iconTint = ContextCompat.getColorStateList(this, R.color.dynamic_island_button_icon)
            hideRecentAppsDropdown()
        }
        
        // 创建PopupWindow
        recentAppsDropdown = PopupWindow(
            dropdownView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        
        // 设置背景
        recentAppsDropdown?.setBackgroundDrawable(
            ContextCompat.getDrawable(this, android.R.color.transparent)
        )
        
        // 显示在最近APP按钮下方
        recentAppButton?.let { button ->
            recentAppsDropdown?.showAsDropDown(button, 0, 8)
        }
    }
    
    private fun hideRecentAppsDropdown() {
        recentAppsDropdown?.dismiss()
        recentAppsDropdown = null
    }
    
    private fun handleSearchWithSelectedApp(query: String, appInfo: AppInfo) {
        Log.d(TAG, "使用选中的APP进行搜索: ${appInfo.label}, 查询: $query")
        
        if (appInfo.urlScheme != null) {
            // 有URL scheme，直接跳转到APP搜索结果页面
            try {
                val encodedQuery = Uri.encode(query)
                val intent = when (appInfo.urlScheme) {
                    // 社交类
                    "weixin" -> Intent(Intent.ACTION_VIEW, Uri.parse("weixin://dl/search?query=$encodedQuery"))
                    "mqqapi" -> Intent(Intent.ACTION_VIEW, Uri.parse("mqqapi://search?query=$encodedQuery"))
                    "wework" -> Intent(Intent.ACTION_VIEW, Uri.parse("wework://search?query=$encodedQuery"))
                    "tim" -> Intent(Intent.ACTION_VIEW, Uri.parse("tim://search?query=$encodedQuery"))
                    "sinaweibo" -> Intent(Intent.ACTION_VIEW, Uri.parse("sinaweibo://searchall?q=$encodedQuery"))
                    "xhsdiscover" -> Intent(Intent.ACTION_VIEW, Uri.parse("xhsdiscover://search/result?keyword=$encodedQuery"))
                    "douban" -> Intent(Intent.ACTION_VIEW, Uri.parse("douban://search?q=$encodedQuery"))
                    "twitter" -> Intent(Intent.ACTION_VIEW, Uri.parse("twitter://search?query=$encodedQuery"))
                    "zhihu" -> Intent(Intent.ACTION_VIEW, Uri.parse("zhihu://search?q=$encodedQuery"))
                    
                    // 购物类
                    "taobao" -> Intent(Intent.ACTION_VIEW, Uri.parse("taobao://s.taobao.com?q=$encodedQuery"))
                    "alipay" -> Intent(Intent.ACTION_VIEW, Uri.parse("alipays://platformapi/startapp?appId=20000067&query=$encodedQuery"))
                    "openapp.jdmobile" -> Intent(Intent.ACTION_VIEW, Uri.parse("openapp.jdmobile://virtual?params={\"des\":\"productList\",\"keyWord\":\"$encodedQuery\"}"))
                    "pinduoduo" -> Intent(Intent.ACTION_VIEW, Uri.parse("pinduoduo://com.xunmeng.pinduoduo/search_result.html?search_key=$encodedQuery"))
                    "tmall" -> Intent(Intent.ACTION_VIEW, Uri.parse("tmall://page.tm/search?q=$encodedQuery"))
                    "fleamarket" -> Intent(Intent.ACTION_VIEW, Uri.parse("fleamarket://x_search_items?keyword=$encodedQuery"))
                    
                    // 视频类
                    "snssdk1128" -> Intent(Intent.ACTION_VIEW, Uri.parse("snssdk1128://search/tabs?keyword=$encodedQuery"))
                    "bilibili" -> Intent(Intent.ACTION_VIEW, Uri.parse("bilibili://search?keyword=$encodedQuery"))
                    "qqlive" -> Intent(Intent.ACTION_VIEW, Uri.parse("qqlive://search?query=$encodedQuery"))
                    "iqiyi" -> Intent(Intent.ACTION_VIEW, Uri.parse("iqiyi://search?key=$encodedQuery"))
                    "youtube" -> Intent(Intent.ACTION_VIEW, Uri.parse("youtube://results?search_query=$encodedQuery"))
                    
                    // 音乐类
                    "qqmusic" -> Intent(Intent.ACTION_VIEW, Uri.parse("qqmusic://search?key=$encodedQuery"))
                    "orpheus" -> Intent(Intent.ACTION_VIEW, Uri.parse("orpheus://search?keyword=$encodedQuery"))
                    "spotify" -> Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:$encodedQuery"))
                    
                    // 生活服务类
                    "imeituan" -> Intent(Intent.ACTION_VIEW, Uri.parse("imeituan://www.meituan.com/search?q=$encodedQuery"))
                    "eleme" -> Intent(Intent.ACTION_VIEW, Uri.parse("eleme://search?keyword=$encodedQuery"))
                    "dianping" -> Intent(Intent.ACTION_VIEW, Uri.parse("dianping://searchshoplist?keyword=$encodedQuery"))
                    
                    // 地图导航类
                    "androidamap" -> Intent(Intent.ACTION_VIEW, Uri.parse("androidamap://poi?sourceApplication=appname&keywords=$encodedQuery"))
                    "baidumap" -> Intent(Intent.ACTION_VIEW, Uri.parse("baidumap://map/place/search?query=$encodedQuery"))
                    
                    // 浏览器类
                    "mttbrowser" -> Intent(Intent.ACTION_VIEW, Uri.parse("mttbrowser://search?query=$encodedQuery"))
                    "ucbrowser" -> Intent(Intent.ACTION_VIEW, Uri.parse("ucbrowser://search?keyword=$encodedQuery"))
                    "googlechrome" -> Intent(Intent.ACTION_VIEW, Uri.parse("googlechrome://www.google.com/search?q=$encodedQuery"))
                    "firefox" -> Intent(Intent.ACTION_VIEW, Uri.parse("firefox://search?q=$encodedQuery"))
                    "quark" -> Intent(Intent.ACTION_VIEW, Uri.parse("quark://search?q=$encodedQuery"))
                    
                    // 金融类
                    "cmbmobilebank" -> Intent(Intent.ACTION_VIEW, Uri.parse("cmbmobilebank://search?keyword=$encodedQuery"))
                    "antfortune" -> Intent(Intent.ACTION_VIEW, Uri.parse("antfortune://search?keyword=$encodedQuery"))
                    
                    // 出行类
                    "diditaxi" -> Intent(Intent.ACTION_VIEW, Uri.parse("diditaxi://search?keyword=$encodedQuery"))
                    "cn.12306" -> Intent(Intent.ACTION_VIEW, Uri.parse("cn.12306://search?keyword=$encodedQuery"))
                    "ctrip" -> Intent(Intent.ACTION_VIEW, Uri.parse("ctrip://search?keyword=$encodedQuery"))
                    "qunar" -> Intent(Intent.ACTION_VIEW, Uri.parse("qunar://search?keyword=$encodedQuery"))
                    "hellobike" -> Intent(Intent.ACTION_VIEW, Uri.parse("hellobike://search?keyword=$encodedQuery"))
                    
                    // 招聘类
                    "bosszhipin" -> Intent(Intent.ACTION_VIEW, Uri.parse("bosszhipin://search?keyword=$encodedQuery"))
                    "liepin" -> Intent(Intent.ACTION_VIEW, Uri.parse("liepin://search?keyword=$encodedQuery"))
                    "zhaopin" -> Intent(Intent.ACTION_VIEW, Uri.parse("zhaopin://search?keyword=$encodedQuery"))
                    
                    // 教育类
                    "yddict" -> Intent(Intent.ACTION_VIEW, Uri.parse("yddict://search?keyword=$encodedQuery"))
                    "eudic" -> Intent(Intent.ACTION_VIEW, Uri.parse("eudic://dict/$encodedQuery"))
                    "baicizhan" -> Intent(Intent.ACTION_VIEW, Uri.parse("baicizhan://search?keyword=$encodedQuery"))
                    "zuoyebang" -> Intent(Intent.ACTION_VIEW, Uri.parse("zuoyebang://search?keyword=$encodedQuery"))
                    "yuansouti" -> Intent(Intent.ACTION_VIEW, Uri.parse("yuansouti://search?keyword=$encodedQuery"))
                    
                    // 新闻类
                    "newsapp" -> Intent(Intent.ACTION_VIEW, Uri.parse("newsapp://search?keyword=$encodedQuery"))
                    
                    else -> {
                        // 通用URL scheme格式
                        Intent(Intent.ACTION_VIEW, Uri.parse("${appInfo.urlScheme}://search?query=$encodedQuery"))
                    }
                }

                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                // 执行搜索动作后退出搜索面板并切换到圆球状态
                hideContentAndSwitchToBall()
                Toast.makeText(this, "已跳转到${appInfo.label}搜索", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "跳转到APP搜索失败: ${appInfo.label}", e)
                showNoUrlSchemeDialog(query, appInfo)
            }
        } else {
            // 没有URL scheme，直接复制文本并打开应用
            handleAppWithoutUrlScheme(query, appInfo)
        }
    }
    
    /**
     * 处理没有URL scheme的应用
     * 直接复制文本并打开应用
     */
    private fun handleAppWithoutUrlScheme(query: String, appInfo: AppInfo) {
        Log.d(TAG, "处理没有URL scheme的应用: ${appInfo.label}, 查询: $query")
        
        try {
            // 复制搜索文本到剪贴板
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("搜索关键词", query)
            clipboard.setPrimaryClip(clip)
            
            // 启动应用
            val launchIntent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                
                // 显示提示信息
                Toast.makeText(this, "已复制「$query」到剪贴板，请在${appInfo.label}中粘贴搜索", Toast.LENGTH_LONG).show()
                
                // 执行搜索动作后退出搜索面板并切换到圆球状态
                hideContentAndSwitchToBall()
            } else {
                Toast.makeText(this, "无法启动该应用", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动应用失败: ${appInfo.label}", e)
            Toast.makeText(this, "启动应用失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showNoUrlSchemeDialog(query: String, appInfo: AppInfo) {
        AlertDialog.Builder(this)
            .setTitle("搜索提示")
            .setMessage("已复制关键词「$query」，请在${appInfo.label}中粘贴搜索")
            .setPositiveButton("确定") { _, _ ->
                // 复制到剪贴板
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("搜索关键词", query)
                clipboard.setPrimaryClip(clip)
                
                // 启动APP
                try {
                    val launchIntent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(launchIntent)
                        // 执行搜索动作后退出搜索面板
                        hideContent()
                    } else {
                        Toast.makeText(this, "无法启动该应用", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "启动应用失败: ${appInfo.label}", e)
                    Toast.makeText(this, "启动应用失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun getActiveProfile(): com.example.aifloatingball.model.PromptProfile? {
        val activeProfileId = settingsManager.getActivePromptProfileId()
        val profiles = settingsManager.getPromptProfiles()
        return profiles.find { it.id == activeProfileId } ?: profiles.firstOrNull()
    }

    /**
     * 为AI助手面板显示档案选择器
     */
    private fun showPromptProfileSelectorForAI() {
        try {
            Log.d(TAG, "开始显示AI助手档案选择器")
            
            val profiles = settingsManager.getPromptProfiles()
            Log.d(TAG, "获取到的档案列表大小: ${profiles.size}")
            
            if (profiles.isEmpty()) {
                Log.w(TAG, "档案列表为空，显示提示")
                Toast.makeText(this, "没有可用的档案，请先在设置中创建档案", Toast.LENGTH_SHORT).show()
                return
            }
            
            Log.d(TAG, "找到 ${profiles.size} 个档案")
            
            // 使用主题包装的上下文，参考搜索面板的实现
            val context = ContextThemeWrapper(getThemedContext(), R.style.Theme_FloatingWindow)
            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_profile_selector, null)
            
            val currentProfileId = settingsManager.getActivePromptProfileId()
            val currentProfile = profiles.find { it.id == currentProfileId } ?: profiles.firstOrNull()
            
            Log.d(TAG, "当前档案ID: $currentProfileId, 档案名称: ${currentProfile?.name}")
            
            // 更新当前档案显示
            val currentProfileText = dialogView.findViewById<TextView>(R.id.current_profile_text)
            currentProfileText?.text = "当前档案: ${currentProfile?.name ?: "未选择"}"
            
            // 设置RecyclerView
            val recyclerView = dialogView.findViewById<RecyclerView>(R.id.profiles_recycler_view)
            recyclerView?.layoutManager = LinearLayoutManager(context)
            
            var selectedProfile = currentProfile
            val adapter = ProfileSelectorAdapter(profiles, currentProfileId) { profile ->
                selectedProfile = profile
                Log.d(TAG, "选择了档案: ${profile.name}")
            }
            recyclerView?.adapter = adapter
            
            val dialog = AlertDialog.Builder(context)
                .setView(dialogView)
                .create()
            
            // 设置对话框窗口类型，确保可以从服务中显示
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            
            // 设置按钮点击事件
            dialogView.findViewById<View>(R.id.btn_cancel)?.setOnClickListener {
                Log.d(TAG, "取消选择档案")
                dialog.dismiss()
            }
            
            dialogView.findViewById<View>(R.id.btn_confirm)?.setOnClickListener {
                try {
                    selectedProfile?.let { profile ->
                        Log.d(TAG, "确认选择档案: ${profile.name}")
                        
                        // 切换档案
                        settingsManager.setActivePromptProfileId(profile.id)
                        
                        // 生成并插入提示词到AI输入框
                        val generatedPrompt = settingsManager.generateMasterPrompt(profile)
                        val aiInputText = aiAssistantPanelView?.findViewById<EditText>(R.id.ai_input_text)
                        val currentText = aiInputText?.text?.toString() ?: ""
                        val newText = if (currentText.isBlank()) generatedPrompt else "$currentText\n\n$generatedPrompt"
                        
                        aiInputText?.setText(newText)
                        aiInputText?.setSelection(aiInputText?.text?.length ?: 0)
                        Toast.makeText(this, "已切换到档案: ${profile.name} 并生成提示词", Toast.LENGTH_SHORT).show()
                        
                        Log.d(TAG, "档案切换完成")
                    } ?: run {
                        Toast.makeText(this, "请选择一个档案", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "处理档案选择时出错", e)
                    Toast.makeText(this, "处理档案选择时出错: ${e.message}", Toast.LENGTH_SHORT).show()
                }
                dialog.dismiss()
            }
            
            dialogView.findViewById<View>(R.id.btn_manage_profiles)?.setOnClickListener {
                try {
                    Log.d(TAG, "打开档案管理")
                    val intent = Intent(this, MasterPromptSettingsActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    startActivity(intent)
                    dialog.dismiss()
                } catch (e: Exception) {
                    Log.e(TAG, "打开档案管理失败", e)
                    Toast.makeText(this, "打开档案管理失败", Toast.LENGTH_SHORT).show()
                }
            }
            
            dialog.show()
            
            Log.d(TAG, "AI助手档案选择器显示成功")
            
        } catch (e: Exception) {
            Log.e(TAG, "显示AI助手档案选择器失败", e)
            Toast.makeText(this, "显示档案选择器失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPromptProfileSelector() {
        val profiles = settingsManager.getPromptProfiles()
        if (profiles.isEmpty()) {
            Toast.makeText(this, "没有可用的档案", Toast.LENGTH_SHORT).show()
            return
        }
        
        val context = configPanelView?.context ?: this
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_profile_selector, null)
        
        val currentProfileId = settingsManager.getActivePromptProfileId()
        val currentProfile = profiles.find { it.id == currentProfileId } ?: profiles.firstOrNull()
        
        // 更新当前档案显示
        val currentProfileText = dialogView.findViewById<TextView>(R.id.current_profile_text)
        currentProfileText.text = "当前档案: ${currentProfile?.name ?: "未选择"}"
        
        // 设置RecyclerView
        val recyclerView = dialogView.findViewById<RecyclerView>(R.id.profiles_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(context)
        
        var selectedProfile = currentProfile
        val adapter = ProfileSelectorAdapter(profiles, currentProfileId) { profile ->
            selectedProfile = profile
        }
        recyclerView.adapter = adapter
        
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()
        
        // 设置按钮点击事件
        dialogView.findViewById<View>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        
        dialogView.findViewById<View>(R.id.btn_confirm).setOnClickListener {
            selectedProfile?.let { profile ->
                // 切换档案
                settingsManager.setActivePromptProfileId(profile.id)
                
                // 生成并插入提示词
                val generatedPrompt = settingsManager.generateMasterPrompt(profile)
                val currentText = searchInput?.text?.toString() ?: ""
                val newText = if (currentText.isBlank()) generatedPrompt else "$currentText\n\n$generatedPrompt"
                
                searchInput?.setText(newText)
                searchInput?.setSelection(searchInput?.text?.length ?: 0)
                Toast.makeText(this, "已切换到档案: ${profile.name} 并生成提示词", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        
        dialogView.findViewById<View>(R.id.btn_manage_profiles).setOnClickListener {
            val intent = Intent(this, MasterPromptSettingsActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            dialog.dismiss()
        }
        
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
    }

    private fun updateBlurEffect(intensity: Float) {
        // ... existing code ...
    }

    private fun setupWindowLayoutParams(): WindowManager.LayoutParams {
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            resources.getDimensionPixelSize(R.dimen.dynamic_island_height),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        // 设置初始位置
        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = resources.getDimensionPixelSize(R.dimen.dynamic_island_margin_top)

        // 设置最小宽度和最大宽度
        val minWidth = resources.getDimensionPixelSize(R.dimen.dynamic_island_min_width)
        val maxWidth = resources.getDimensionPixelSize(R.dimen.dynamic_island_max_width)
        params.width = minWidth
        
        return params
    }

    private fun createIslandView() {
        val themedContext = getThemedContext()
        val inflater = LayoutInflater.from(ContextThemeWrapper(themedContext, R.style.Theme_DynamicIsland))
        
        // 使用包含按钮的布局
        val view = inflater.inflate(R.layout.dynamic_island_layout, null)

        // 根据主题设置圆角和背景
        view.findViewById<MaterialCardView>(R.id.island_card_view)?.apply {
            radius = resources.getDimension(R.dimen.dynamic_island_corner_radius)

            // 根据主题设置背景颜色
            val isDarkMode = (themedContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            val backgroundColor = if (isDarkMode) {
                Color.parseColor("#CC1C1C1E")  // 深色模式：半透明深色
            } else {
                Color.parseColor("#CCFFFFFF")  // 浅色模式：半透明白色
            }
            setCardBackgroundColor(backgroundColor)
            cardElevation = resources.getDimension(R.dimen.dynamic_island_elevation)
        }

        // 设置内容容器的大小
        view.findViewById<LinearLayout>(R.id.notification_icon_container)?.apply {
            val lp = layoutParams
            lp.height = resources.getDimensionPixelSize(R.dimen.dynamic_island_height)
            layoutParams = lp
        }

        // 设置图标大小
        view.findViewById<ImageView>(R.id.notification_icon)?.apply {
            val size = resources.getDimensionPixelSize(R.dimen.dynamic_island_icon_size)
            val lp = layoutParams
            lp.width = size
            lp.height = size
            layoutParams = lp
        }

        // 设置文字大小
        view.findViewById<TextView>(R.id.notification_text)?.apply {
            textSize = resources.getDimension(R.dimen.dynamic_island_text_size)
        }

        // 确保按钮容器始终可见
        val buttonContainer = view.findViewById<LinearLayout>(R.id.button_container)
        buttonContainer?.visibility = View.VISIBLE
        
        // 设置按钮交互
        Log.d(TAG, "设置灵动岛按钮交互")
        setupEnhancedLayoutButtons(view)

        windowContainerView = view as FrameLayout
        islandContentView = view
    }

    /**
     * 设置增强版布局的按钮交互
     */
    private fun setupEnhancedLayoutButtons(view: View) {
        // 设置紧凑状态按钮
        val btnAiAssistant = view.findViewById<MaterialButton>(R.id.btn_ai_assistant)
        val btnApps = view.findViewById<MaterialButton>(R.id.btn_apps)
        val btnSearch = view.findViewById<MaterialButton>(R.id.btn_search)
        val btnSettings = view.findViewById<MaterialButton>(R.id.btn_settings)
        val btnExit = view.findViewById<MaterialButton>(R.id.btn_exit)

        // AI助手按钮
        btnAiAssistant?.setOnClickListener {
            Log.d(TAG, "AI助手按钮被点击")
            // 如果AI助手面板已经显示，则切换AI服务
            if (aiAssistantPanelView != null) {
                switchAIService()
            } else {
                // 否则显示AI助手面板
                showAIAssistantPanel()
            }
        }

        // 应用程序按钮
        btnApps?.setOnClickListener {
            Log.d(TAG, "应用程序按钮被点击 - 准备打开搜索面板")
            showConfigPanel()
        }

        // 搜索按钮
        btnSearch?.setOnClickListener {
            Log.d(TAG, "搜索按钮被点击")
            showSearchDialog()
        }

        // 设置按钮
        btnSettings?.setOnClickListener {
            Log.d(TAG, "设置按钮被点击")
            showSettingsDialog()
        }

        // 退出按钮（现在改为最小化按钮）
        btnExit?.setOnClickListener {
            Log.d(TAG, "最小化按钮被点击")
            minimizeDynamicIsland()
        }

        // 设置长按监听器
        val buttonContainer = view.findViewById<LinearLayout>(R.id.button_container)
        buttonContainer?.setOnLongClickListener {
            Log.d(TAG, "灵动岛长按，显示扩展菜单")
            showExpandedMenu(view)
            true
        }

        // 设置扩展菜单按钮
        setupExpandedMenuButtons(view)
    }

    /**
     * 设置扩展菜单的按钮交互
     */
    private fun setupExpandedMenuButtons(view: View) {
        val expandedMenuContainer = view.findViewById<MaterialCardView>(R.id.expanded_menu_container)
        
        // 快速聊天
        expandedMenuContainer?.findViewById<MaterialButton>(R.id.menu_btn_chat)?.setOnClickListener {
            Log.d(TAG, "快速聊天按钮被点击")
            hideExpandedMenu()
            showQuickChatDialog()
        }

        // 群聊
        expandedMenuContainer?.findViewById<MaterialButton>(R.id.menu_btn_group_chat)?.setOnClickListener {
            Log.d(TAG, "群聊按钮被点击")
            hideExpandedMenu()
            showGroupChatDialog()
        }

        // 语音助手
        expandedMenuContainer?.findViewById<MaterialButton>(R.id.menu_btn_voice)?.setOnClickListener {
            Log.d(TAG, "语音助手按钮被点击")
            hideExpandedMenu()
            showVoiceAssistant()
        }

        // 翻译
        expandedMenuContainer?.findViewById<MaterialButton>(R.id.menu_btn_translate)?.setOnClickListener {
            Log.d(TAG, "翻译按钮被点击")
            hideExpandedMenu()
            showTranslateDialog()
        }

        // 设置
        expandedMenuContainer?.findViewById<MaterialButton>(R.id.menu_btn_settings)?.setOnClickListener {
            Log.d(TAG, "菜单设置按钮被点击")
            hideExpandedMenu()
            showSettingsDialog()
        }

        // 更多
        expandedMenuContainer?.findViewById<MaterialButton>(R.id.menu_btn_more)?.setOnClickListener {
            Log.d(TAG, "更多按钮被点击")
            hideExpandedMenu()
            showMoreOptionsDialog()
        }
    }

    /**
     * 显示扩展菜单
     */
    private fun showExpandedMenu(view: View) {
        val expandedMenuContainer = view.findViewById<MaterialCardView>(R.id.expanded_menu_container)
        expandedMenuContainer?.let { menu ->
            // 显示动画
            menu.alpha = 0f
            menu.scaleX = 0.8f
            menu.scaleY = 0.8f
            menu.visibility = View.VISIBLE
            
            menu.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        }
    }

    /**
     * 隐藏扩展菜单
     */
    private fun hideExpandedMenu() {
        val expandedMenuContainer = windowContainerView?.findViewById<MaterialCardView>(R.id.expanded_menu_container)
        expandedMenuContainer?.let { menu ->
            menu.animate()
                .alpha(0f)
                .scaleX(0.8f)
                .scaleY(0.8f)
                .setDuration(150)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    menu.visibility = View.GONE
                }
                .start()
        }
    }

    /**
     * 显示快速聊天对话框
     */
    private fun showQuickChatDialog() {
        val intent = Intent(this, ChatActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("quick_chat", true)
        }
        startActivity(intent)
    }

    /**
     * 显示群聊对话框
     */
    private fun showGroupChatDialog() {
        val intent = Intent(this, AIContactListActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("create_group_chat", true)
        }
        startActivity(intent)
    }

    /**
     * 显示语音助手
     */
    private fun showVoiceAssistant() {
        // 启动语音识别
        val intent = Intent(this, ChatActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("voice_mode", true)
        }
        startActivity(intent)
    }

    /**
     * 显示翻译对话框
     */
    private fun showTranslateDialog() {
        val intent = Intent(this, ChatActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("translate_mode", true)
        }
        startActivity(intent)
    }

    /**
     * 显示设置对话框
     */
    private fun showSettingsDialog() {
        val intent = Intent(this, MainSettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    /**
     * 显示更多选项对话框
     */
    private fun showMoreOptionsDialog() {
        val intent = Intent(this, SimpleModeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    /**
     * 强制启用增强版布局（调试用）
     */
    private fun forceEnableEnhancedLayout() {
        val useEnhancedLayout = sharedPreferences.getBoolean("island_enhanced_layout", true)
        if (!useEnhancedLayout) {
            Log.d(TAG, "强制启用增强版布局")
            sharedPreferences.edit().putBoolean("island_enhanced_layout", true).apply()
        }
    }

    /**
     * 测试增强版灵动岛功能（调试用）
     */
    private fun testEnhancedIslandFeatures() {
        Log.d(TAG, "=== 测试增强版灵动岛功能 ===")
        
        val useEnhancedLayout = sharedPreferences.getBoolean("island_enhanced_layout", true)
        Log.d(TAG, "增强版布局状态: $useEnhancedLayout")
        
        if (useEnhancedLayout) {
            Log.d(TAG, "✅ 增强版布局已启用")
            Log.d(TAG, "功能包括:")
            Log.d(TAG, "  - 紧凑状态按钮: AI助手、搜索、设置")
            Log.d(TAG, "  - 长按弹出扩展菜单")
            Log.d(TAG, "  - 扩展菜单包含: 快速聊天、群聊、语音助手、翻译、设置、更多")
        } else {
            Log.d(TAG, "❌ 增强版布局未启用，使用传统布局")
        }
        
        // 测试按钮点击功能
        windowContainerView?.let { view ->
            val btnAiAssistant = view.findViewById<MaterialButton>(R.id.btn_ai_assistant)
            val btnSearch = view.findViewById<MaterialButton>(R.id.btn_search)
            val btnSettings = view.findViewById<MaterialButton>(R.id.btn_settings)
            val buttonContainer = view.findViewById<LinearLayout>(R.id.button_container)
            
            Log.d(TAG, "按钮状态检查:")
            Log.d(TAG, "  - 按钮容器: ${if (buttonContainer != null) "✅ 存在" else "❌ 不存在"}")
            Log.d(TAG, "  - AI助手按钮: ${if (btnAiAssistant != null) "✅ 存在" else "❌ 不存在"}")
            Log.d(TAG, "  - 搜索按钮: ${if (btnSearch != null) "✅ 存在" else "❌ 不存在"}")
            Log.d(TAG, "  - 设置按钮: ${if (btnSettings != null) "✅ 存在" else "❌ 不存在"}")
            
            // 检查按钮容器可见性
            buttonContainer?.let {
                Log.d(TAG, "  - 按钮容器可见性: ${it.visibility}")
                Log.d(TAG, "  - 按钮容器子视图数量: ${it.childCount}")
            }
        }
    }
}