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
import android.content.res.ColorStateList
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
import java.io.File
import org.json.JSONObject
import org.json.JSONArray
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
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.HomeActivity
import com.example.aifloatingball.R
import com.example.aifloatingball.activity.AIApiConfigActivity
import com.example.aifloatingball.manager.AIApiManager
import com.example.aifloatingball.manager.AIServiceSelectionManager
import com.example.aifloatingball.manager.AIServiceType
import com.example.aifloatingball.manager.ScreenTextRecognitionManager
import com.example.aifloatingball.data.ChatDataManager
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.UUID
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.PopupMenu
import android.view.WindowInsets
import android.widget.HorizontalScrollView
import android.widget.ScrollView
import com.example.aifloatingball.model.AppSearchSettings
import android.net.Uri
import android.content.ActivityNotFoundException
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.model.PromptProfile
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
import com.example.aifloatingball.SettingsActivity
import com.google.android.material.button.MaterialButton
import android.content.ClipData
import android.content.ClipboardManager
import androidx.appcompat.app.AlertDialog
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
    private lateinit var aiServiceSelectionManager: AIServiceSelectionManager
    private lateinit var chatDataManager: ChatDataManager
    
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

    // 新增：常用应用图标相关
    private var commonAppsContainer: FrameLayout? = null
    private var commonAppsRecyclerView: RecyclerView? = null
    private var commonAppsAdapter: AppSearchAdapter? = null
    private var ballView: View? = null // 圆球状态视图
    
    // 应用切换监听相关
    private var usageStatsManager: UsageStatsManager? = null
    private var appSwitchHandler: Handler? = null
    private var appSwitchRunnable: Runnable? = null
    private var currentPackageName: String? = null
    private var isAutoMinimizeEnabled = true // 是否启用自动缩小功能
    
    // 剪贴板相关变量已移除，改用无障碍服务监听
    private var clipboardBroadcastReceiver: BroadcastReceiver? = null
    
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

    // 屏幕文字识别管理器
    private var screenTextRecognitionManager: ScreenTextRecognitionManager? = null

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
        
        // 初始化SettingsManager
        settingsManager = SettingsManager.getInstance(this)
        
        // 监听主题模式变化
        settingsManager.registerOnSettingChangeListener<Int>("theme_mode") { _, value ->
            updateAllStatesTheme()
        }
        
        // 注册档案变更监听器
        settingsManager.registerOnSettingChangeListener<List<PromptProfile>>("prompt_profiles") { key, value ->
            Log.d(TAG, "档案列表已更新，重新加载档案选择器")
            // 如果当前有档案选择器显示，需要刷新
            refreshProfileSelectorIfVisible()
        }
        
        // 初始化聊天数据管理器
        chatDataManager = ChatDataManager.getInstance(this)
        appSearchSettings = AppSearchSettings.getInstance(this)
        
        // 初始化AppInfoManager并加载应用列表
        AppInfoManager.getInstance().loadApps(this)
        
        // 加载最近选中的APP历史
        loadRecentAppsFromPrefs()
        
        // 初始化剪贴板广播接收器
        initClipboardBroadcastReceiver()

        // 启动剪贴板前台服务
        startClipboardForegroundService()
        
        // 初始化应用切换监听器
        initAppSwitchListener()
        
        // 强制启用增强版布局（调试用）
        forceEnableEnhancedLayout()
        
        // 测试增强版灵动岛功能
        testEnhancedIslandFeatures()
        aiServiceSelectionManager = AIServiceSelectionManager(this)

        // 初始化屏幕文字识别管理器
        initScreenTextRecognitionManager()

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
        
        // 测试主题切换功能
        testThemeSwitching()
        
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
        
        // 强制设置合适的宽度，确保所有按钮能够完整显示（5个按钮：48dp×5 + 间距 + padding = 约360dp）
        val minRequiredWidth = 360 // 360dp，确保所有按钮能够完整显示
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
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or // 允许触摸事件穿透到下层
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, // 添加此标志以确保穿透性
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START // Change gravity to START to avoid horizontal conflicts
            y = 0
        }

        // 2. The Animating View (Island itself, not the proxy bar)
        animatingIslandView = FrameLayout(this).apply {
            // 检测当前主题模式
            val themedContext = getThemedContext()
            val isDarkMode = (themedContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            
            Log.d(TAG, "原始状态主题检测: isDarkMode=$isDarkMode")
            
            // 根据主题设置背景 - 初始状态使用专用颜色
            background = GradientDrawable().apply {
                if (isDarkMode) {
                    // 暗色模式：使用初始状态专用颜色
                    setColor(resources.getColor(R.color.dynamic_island_compact_background, themedContext.theme))
                    setStroke(1.dpToPx(), Color.parseColor("#40FFFFFF"))
                    Log.d(TAG, "原始状态: 应用暗色主题 - 初始状态颜色")
                } else {
                    // 亮色模式：使用纯白色背景，无阴影
                    setColor(Color.WHITE)
                    setStroke(1.dpToPx(), Color.parseColor("#E0E0E0"))
                    Log.d(TAG, "原始状态: 应用亮色主题 - 纯白色背景")
                }
                cornerRadius = 20.dpToPx().toFloat()
            }
            
            layoutParams = FrameLayout.LayoutParams(compactWidth, compactHeight, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = statusBarHeight // 设置在状态栏下方
            }
            visibility = View.VISIBLE // 初始状态就显示，包含按钮
        }
        
        // 3. The Content - 使用包含按钮的布局
        islandContentView = inflater.inflate(R.layout.dynamic_island_layout, animatingIslandView, false)
        // 不设置背景，让父容器的背景显示
        // islandContentView?.background = ColorDrawable(Color.TRANSPARENT)
        
        // 应用主题到内容视图
        val themedContext = getThemedContext()
        val isDarkMode = (themedContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        applyThemeToContentView(islandContentView, isDarkMode)
        
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
        
        // 5. 设置灵动岛的触摸监听器
        setupIslandTouchListener()
        
        // 6. 设置灵动岛的长按监听器
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
            val intent = Intent(this, SettingsActivity::class.java).apply {
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
        
        // 移除搜索模式的外部点击监听器
        removeSearchModeTouchListener()

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
        
        // 记录搜索历史
        com.example.aifloatingball.manager.SearchHistoryAutoRecorder.recordSearchHistory(
            context = this,
            query = query,
            source = com.example.aifloatingball.manager.SearchHistoryAutoRecorder.SearchSource.DYNAMIC_ISLAND,
            tags = emptyList(),
            searchType = "应用搜索"
        )

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
            // 找到匹配的APP，显示搜索结果供用户选择
            Log.d(TAG, "显示匹配应用搜索结果: ${appResults.map { it.label }}")
            showAppSearchResults(appResults)
        } else {
            // 没有找到匹配的APP，显示支持URL scheme的APP图标
            Log.d(TAG, "没有找到匹配的应用，显示URL scheme APP图标")
            showUrlSchemeAppIcons()
        }

        // 复制搜索文本到剪贴板
        copyTextToClipboard(query)

        // 显示提示
        Toast.makeText(this, "已复制搜索文本，请选择应用进行搜索", Toast.LENGTH_SHORT).show()
    }

    private fun getDefaultSearchApp(): AppInfo? {
        // 优先使用百度作为默认搜索应用
        val defaultSearchApps = listOf(
            "com.baidu.searchbox" to "baiduboxapp", // 百度
            "com.google.android.googlequicksearchbox" to "googlechrome", // Google
            "com.qihoo.browser" to "qihoo", // 360浏览器
            "com.UCMobile" to "ucbrowser", // UC浏览器
            "com.tencent.mtt" to "mttbrowser" // QQ浏览器
        )

        val pm = packageManager
        for ((packageName, urlScheme) in defaultSearchApps) {
            try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                val icon = pm.getApplicationIcon(packageName)
                val label = pm.getApplicationLabel(appInfo).toString()

                return AppInfo(
                    label = label,
                    packageName = packageName,
                    icon = icon,
                    urlScheme = urlScheme
                )
            } catch (e: Exception) {
                // 应用未安装，继续尝试下一个
                continue
            }
        }
        return null
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
        // 显示DeepSeek响应文本
        Log.d(TAG, "DeepSeek响应: $text")
        // 可以通过Toast或其他方式显示响应
        Toast.makeText(this, "DeepSeek: $text", Toast.LENGTH_LONG).show()
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
                        // 处理流式响应数据
                        Log.d(TAG, "收到AI响应片段: $chunk")
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
            // 确保窗口可以接收输入事件
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            
            // 对于悬浮窗，还需要确保窗口类型正确
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                params.type = WindowManager.LayoutParams.TYPE_PHONE
            }
            
            try {
                windowManager?.updateViewLayout(view, params)
                Log.d(TAG, "窗口参数已更新以支持输入法: flags=${params.flags}, type=${params.type}")
            } catch (e: Exception) {
                Log.e(TAG, "更新窗口参数失败", e)
            }
        }
    }

    /**
     * 设置AI服务多选界面
     */
    private fun setupAIServiceMultiSelect() {
        try {
            val container = aiAssistantPanelView?.findViewById<LinearLayout>(R.id.ai_engines_container)
            container?.removeAllViews()
            
            // 创建CheckBox网格布局（3列）
            val rowCount = (aiServiceSelectionManager.availableServices.size + 2) / 3
            for (row in 0 until rowCount) {
                val rowLayout = LinearLayout(this)
                rowLayout.orientation = LinearLayout.HORIZONTAL
                rowLayout.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                
                for (col in 0 until 3) {
                    val serviceIndex = row * 3 + col
                    if (serviceIndex < aiServiceSelectionManager.availableServices.size) {
                        val serviceName = aiServiceSelectionManager.availableServices[serviceIndex]
                        val checkBox = createAIServiceCheckBox(serviceName)
                        rowLayout.addView(checkBox)
                    } else {
                        // 添加空白占位
                        val spacer = View(this)
                        spacer.layoutParams = LinearLayout.LayoutParams(
                            0,
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            1f
                        )
                        rowLayout.addView(spacer)
                    }
                }
                
                container?.addView(rowLayout)
            }
            
            // 设置控制按钮
            setupAIServiceControlButtons()
            
            // 更新状态显示
            updateAIServiceStatus()
            
            Log.d(TAG, "AI服务多选界面设置完成")
        } catch (e: Exception) {
            Log.e(TAG, "设置AI服务多选界面失败", e)
        }
    }
    
    /**
     * 创建AI服务CheckBox
     */
    private fun createAIServiceCheckBox(serviceName: String): CheckBox {
        val checkBox = CheckBox(this)
        val layoutParams = LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        )
        layoutParams.marginEnd = 4.dpToPx()
        checkBox.layoutParams = layoutParams
        
        checkBox.text = serviceName
        checkBox.textSize = 10f
        
        // 检查API密钥是否配置
        val hasApiKey = checkApiKeyConfigured(serviceName)
        if (hasApiKey) {
            checkBox.setTextColor(getColor(R.color.ai_assistant_text_primary_light))
            checkBox.buttonTintList = getColorStateList(R.color.ai_assistant_primary_light)
        } else {
            checkBox.setTextColor(getColor(R.color.ai_assistant_text_secondary_light))
            checkBox.buttonTintList = getColorStateList(R.color.ai_assistant_text_secondary_light)
        }
        
        checkBox.isChecked = aiServiceSelectionManager.isSelected(serviceName) && hasApiKey
        checkBox.isEnabled = hasApiKey
        
        checkBox.setOnCheckedChangeListener { _, isChecked ->
            if (hasApiKey) {
                aiServiceSelectionManager.setServiceSelected(serviceName, isChecked)
                updateAIServiceStatus()
                Log.d(TAG, "AI服务选择变更: $serviceName = $isChecked")
            } else {
                // 如果API密钥未配置，提示用户配置
                Toast.makeText(this, "$serviceName 需要先配置API密钥", Toast.LENGTH_SHORT).show()
                checkBox.isChecked = false
            }
        }
        
        return checkBox
    }
    
    /**
     * 检查API密钥是否已配置
     */
    private fun checkApiKeyConfigured(serviceName: String): Boolean {
        return when (serviceName) {
            "DeepSeek" -> (settingsManager.getString("deepseek_api_key", "") ?: "").isNotBlank()
            "智谱AI" -> (settingsManager.getString("zhipu_ai_api_key", "") ?: "").isNotBlank()
            "Kimi" -> (settingsManager.getString("kimi_api_key", "") ?: "").isNotBlank()
            "ChatGPT" -> (settingsManager.getString("openai_api_key", "") ?: "").isNotBlank()
            "Claude" -> (settingsManager.getString("claude_api_key", "") ?: "").isNotBlank()
            "Gemini" -> (settingsManager.getString("gemini_api_key", "") ?: "").isNotBlank()
            "文心一言" -> (settingsManager.getString("wenxin_api_key", "") ?: "").isNotBlank()
            "通义千问" -> (settingsManager.getString("qianwen_api_key", "") ?: "").isNotBlank()
            "讯飞星火" -> (settingsManager.getString("xinghuo_api_key", "") ?: "").isNotBlank()
            else -> false
        }
    }
    
    /**
     * 打开API密钥配置页面
     */
    private fun openApiKeyConfigPage() {
        try {
            // 隐藏AI助手面板
            hideAIAssistantPanel()
            
            // 启动AI API设置Activity
            val intent = Intent(this, AIApiConfigActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            
            Toast.makeText(this, "请配置AI服务的API密钥", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Log.e(TAG, "打开API配置页面失败", e)
            Toast.makeText(this, "无法打开配置页面", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 设置AI服务控制按钮
     */
    private fun setupAIServiceControlButtons() {
        // 全选按钮
        val btnSelectAll = aiAssistantPanelView?.findViewById<MaterialButton>(R.id.btn_select_all)
        btnSelectAll?.setOnClickListener {
            aiServiceSelectionManager.selectAll()
            refreshAIServiceCheckBoxes()
            updateAIServiceStatus()
            Toast.makeText(this, "已全选所有AI服务", Toast.LENGTH_SHORT).show()
        }
        
        // 清空按钮
        val btnClearAll = aiAssistantPanelView?.findViewById<MaterialButton>(R.id.btn_clear_all_ai)
        btnClearAll?.setOnClickListener {
            aiServiceSelectionManager.clearAll()
            refreshAIServiceCheckBoxes()
            updateAIServiceStatus()
            Toast.makeText(this, "已清空所有选择", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 刷新AI服务CheckBox状态
     */
    private fun refreshAIServiceCheckBoxes() {
        val container = aiAssistantPanelView?.findViewById<LinearLayout>(R.id.ai_engines_container)
        container?.let { containerView ->
            for (i in 0 until containerView.childCount) {
                val rowLayout = containerView.getChildAt(i) as? LinearLayout
                rowLayout?.let { row ->
                    for (j in 0 until row.childCount) {
                        val child = row.getChildAt(j)
                        if (child is CheckBox) {
                            val serviceName = child.text.toString()
                            child.isChecked = aiServiceSelectionManager.isSelected(serviceName)
                        }
                    }
                }
            }
        }
    }
    
    /**
     * 更新AI服务选择状态显示
     */
    private fun updateAIServiceStatus() {
        val statusText = aiAssistantPanelView?.findViewById<TextView>(R.id.ai_status_text)
        statusText?.text = aiServiceSelectionManager.getSelectedServicesText()
    }

    private fun sendAIMessage(query: String, responseTextView: TextView?) {
        responseTextView?.text = "正在思考中..."
        
        // 获取当前选择的AI服务，使用多选管理器
        val selectedServices = aiServiceSelectionManager.getSelectedServices()
        val selectedService = if (selectedServices.isNotEmpty()) selectedServices.first() else "DeepSeek"
        
        // 将显示名称映射到AIServiceType
        val serviceType = when (selectedService) {
            "临时专线" -> AIServiceType.TEMP_SERVICE
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
                        
                        // 保存到聊天历史
                        saveToChatHistory(query, fullResponse, serviceType)
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "SHOW_INPUT_PANEL" -> {
                Log.d(TAG, "收到显示输入面板请求")
                uiHandler.post {
                    showConfigPanel()
                }
            }
        }
        return START_STICKY
    }

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
        
        // 清理剪贴板广播接收器
        cleanupClipboardBroadcastReceiver()

        // 停止剪贴板前台服务
        stopClipboardForegroundService()

        // 清理应用切换监听器
        cleanupAppSwitchListener()

        // 清理屏幕文字识别管理器
        screenTextRecognitionManager?.release()
        screenTextRecognitionManager = null
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

        // 初始化常用应用图标组件
        commonAppsContainer = configPanelView?.findViewById(R.id.common_apps_container)
        commonAppsRecyclerView = configPanelView?.findViewById(R.id.common_apps_recycler_view)

        // 初始化搜索结果组件
        appSearchResultsContainer = configPanelView?.findViewById(R.id.app_search_results_container)
        appSearchRecyclerView = configPanelView?.findViewById(R.id.app_search_results_recycler_view)
        closeAppSearchButton = configPanelView?.findViewById(R.id.close_app_search_button)

        setupSearchListeners()
        initSearchInputListener()

        // 显示常用应用图标
        showCommonAppIcons()

        // Setup App Search Results Views
        appSearchResultsContainer = configPanelView?.findViewById(R.id.app_search_results_container)
        appSearchRecyclerView = configPanelView?.findViewById(R.id.app_search_results_recycler_view)
        closeAppSearchButton = configPanelView?.findViewById(R.id.close_app_search_button)
        closeAppSearchButton?.setOnClickListener {
            hideAppSearchResults()
        }
        
        // 初始化时隐藏搜索结果，只在用户输入时显示
        hideAppSearchResults()
        
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
                
                // 强制设置合适的宽度，确保所有按钮能够完整显示（5个按钮：48dp×5 + 间距 + padding = 约360dp）
                val minRequiredWidth = 360 // 360dp，确保所有按钮能够完整显示
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
            "theme_mode" -> {
                Log.d(TAG, "主题模式设置已变更，立即更新所有状态主题")
                // 立即更新所有状态的主题，不使用延迟
                updateAllStatesTheme()
                
                // 额外确保UI线程上的刷新
                uiHandler.post {
                    forceRefreshAllViews()
                    // 重新创建所有视图以确保主题完全应用
                    recreateAllViews()
                }
            }
        }
    }

    private fun setupOutsideTouchListener() {
        // 移除全局触摸监听器，改为在具体组件上设置监听器
        // 这样可以避免拦截所有触摸事件，让系统手势和输入法正常工作
        Log.d(TAG, "使用精确触摸区域检测，不再拦截所有触摸事件")
    }
    
    /**
     * 为灵动岛设置精确的触摸监听器
     */
    private fun setupIslandTouchListener() {
        animatingIslandView?.setOnTouchListener { _, event ->
            Log.d(TAG, "灵动岛收到触摸事件: action=${event.action}, x=${event.rawX}, y=${event.rawY}")
            
            // 首先检查是否是系统级侧边滑动返回手势
            val isSystemGesture = isSystemBackGesture(event)
            if (isSystemGesture) {
                Log.d(TAG, "检测到系统侧边滑动返回手势，让事件穿透")
                return@setOnTouchListener false
            }
            
            // 如果正在检测系统手势，让事件穿透
            if (isSystemGestureActive) {
                Log.d(TAG, "正在检测系统手势，让事件穿透")
                return@setOnTouchListener false
            }
            
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isSearchModeActive) {
                        // 紧凑模式下，点击灵动岛展开搜索
                        Log.d(TAG, "紧凑模式下点击灵动岛，展开搜索")
                        expandIsland()
                        true
                    } else {
                        // 搜索模式下，让子视图处理
                        Log.d(TAG, "搜索模式下，让子视图处理触摸事件")
                        false
                    }
                }
                else -> false
            }
        }
    }
    
    /**
     * 为搜索模式设置外部点击监听器
     */
    private fun setupSearchModeTouchListener() {
        // 在搜索模式下，设置一个透明的覆盖层来处理外部点击
        val overlayView = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        
        overlayView.setOnTouchListener { _, event ->
            // 首先检查是否是系统级侧边滑动返回手势
            val isSystemGesture = isSystemBackGesture(event)
            if (isSystemGesture) {
                Log.d(TAG, "搜索模式下检测到系统侧边滑动返回手势，让事件穿透")
                return@setOnTouchListener false
            }
            
            // 如果正在检测系统手势，让事件穿透
            if (isSystemGestureActive) {
                Log.d(TAG, "搜索模式下正在检测系统手势，让事件穿透")
                return@setOnTouchListener false
            }
            
            if (isSearchModeActive && event.action == MotionEvent.ACTION_DOWN) {
                Log.d(TAG, "搜索模式外部点击检测")
                    if (isTouchOutsideAllViews(event)) {
                    Log.d(TAG, "确认外部点击，退出搜索模式")
                        transitionToCompactState()
                    true
                    } else {
                        Log.d(TAG, "内部点击，让子视图处理")
                    false
                }
                    } else {
                false
            }
        }
        
        windowContainerView?.addView(overlayView)
        
        // 保存覆盖层引用，以便后续移除
        searchModeOverlay = overlayView
    }
    
    private var searchModeOverlay: View? = null
    
    /**
     * 移除搜索模式的外部点击监听器
     */
    private fun removeSearchModeTouchListener() {
        searchModeOverlay?.let { overlay ->
            try {
                windowContainerView?.removeView(overlay)
                Log.d(TAG, "已移除搜索模式外部点击监听器")
            } catch (e: Exception) {
                Log.e(TAG, "移除搜索模式外部点击监听器失败", e)
            }
        }
        searchModeOverlay = null
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
        val themeMode = settingsManager.getThemeMode() // -1 system, 0 light, 1 dark
        val nightModeFlags = when (themeMode) {
            SettingsManager.THEME_MODE_LIGHT -> Configuration.UI_MODE_NIGHT_NO
            SettingsManager.THEME_MODE_DARK -> Configuration.UI_MODE_NIGHT_YES
            else -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        }

        val config = Configuration(resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightModeFlags

        val isDarkMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        Log.d(TAG, "主题检测: themeMode=$themeMode, nightModeFlags=$nightModeFlags, isDarkMode=$isDarkMode")

        val themedContext = createConfigurationContext(config)
        
        // 验证主题上下文是否正确应用
        val actualDarkMode = (themedContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        Log.d(TAG, "主题上下文验证: 期望深色模式=$isDarkMode, 实际深色模式=$actualDarkMode")
        
        return themedContext
    }
    
    /**
     * 检查当前是否为深色模式
     */
    private fun isDarkMode(): Boolean {
        val themedContext = getThemedContext()
        return (themedContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun recreateAllViews() {
        val wasSearchModeActive = isSearchModeActive
        val currentSearchText = searchInput?.text?.toString()

        cleanupViews()
        showDynamicIsland()

        // 更新圆球主题（如果圆球存在）
        updateBallTheme()

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
            
            // 强制设置合适的宽度，确保所有按钮能够完整显示（5个按钮：48dp×5 + 间距 + padding = 约360dp）
            val minRequiredWidth = 360 // 360dp，确保所有按钮能够完整显示
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
        
        // 更新状态
        updateState(IslandState.EXPANDED)
        
        // 设置搜索模式的外部点击监听器
        setupSearchModeTouchListener()
        
        animateIsland(compactWidth, expandedWidth)
    }

    private fun collapseIsland() {
        if (!isSearchModeActive) return
        isSearchModeActive = false
        hideKeyboard(searchInput)
        
        // 更新状态
        updateState(IslandState.COMPACT)
        
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
                    // 不再使用旧的 dynamic_island_search_content.xml
                    // 展开功能现在由 createClipboardAppHistoryView 处理
                    populateAppSearchIcons()
                }
            }
            override fun onAnimationEnd(animation: Animator) {
                if (toWidth < fromWidth) { // Collapsing
                    animatingIslandView?.removeAllViews()
                } else { // Expanding
                    // 动画结束时不激活输入法，由视图创建完成后的延迟激活处理
                    Log.d(TAG, "动画结束，等待视图创建完成后的自动激活")
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

    // setupSearchInput 方法已移除，因为现在使用新的展开输入框系统

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
                        showLoadingIndicator()
                        appInfoManager.loadApps(this@DynamicIslandService) {
                            // 加载完成后重新执行搜索
                            val currentQuery = searchInput?.text.toString().trim()
                            if (currentQuery.isNotEmpty() && currentQuery == query) {
                                performRealTimeSearch(currentQuery, appInfoManager)
                            }
                        }
                        return@afterTextChanged
                    }
                    
                    // 使用防抖机制，延迟100ms执行搜索（优化响应速度）
                    searchRunnable = Runnable {
                        performRealTimeSearch(query, appInfoManager)
                    }
                    searchRunnable?.let { searchHandler.postDelayed(it, 100) }
                } else {
                    // 输入框为空时，隐藏搜索结果，保持常用APP图标显示
                    hideAppSearchResults()
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
            // 点击图标时，使用当前输入框的文字直接跳转到应用搜索
            val searchQuery = searchInput?.text?.toString()?.trim() ?: ""
            if (searchQuery.isNotEmpty()) {
                Log.d(TAG, "点击应用图标，执行搜索: ${appInfo.label}, 搜索内容: $searchQuery")
                handleSearchWithSelectedApp(searchQuery, appInfo)
            } else {
                // 如果输入框为空，选中应用等待用户输入
                selectAppForSearch(appInfo)
            }
            }
            appSearchRecyclerView?.apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                adapter = appSearchAdapter
                overScrollMode = View.OVER_SCROLL_NEVER
        }

        appSearchResultsContainer?.visibility = View.VISIBLE
    }

    /**
     * 隐藏应用搜索结果
     */
    private fun hideAppSearchResults() {
        if (appSearchResultsContainer?.visibility == View.VISIBLE) {
            appSearchResultsContainer?.visibility = View.GONE
            // Cleanup adapter to avoid holding references
            appSearchRecyclerView?.adapter = null
            appSearchAdapter = null
            Log.d(TAG, "隐藏应用搜索结果")
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

    /**
     * 显示常用应用图标（在输入框上方）
     */
    private fun showCommonAppIcons() {
        Log.d(TAG, "显示历史选择的应用图标")

        // 使用历史选择的应用，如果没有则使用默认应用
        val commonApps = if (recentApps.isNotEmpty()) {
            recentApps.take(6) // 最多显示6个历史应用
        } else {
            createDefaultAppList().take(6) // 如果没有历史记录，显示默认应用
        }

        if (commonApps.isNotEmpty()) {
            // 每次都重新创建适配器
            commonAppsAdapter = AppSearchAdapter(commonApps, isHorizontal = true) { appInfo ->
                // 点击常用应用图标，加载到搜索图标内
                loadAppToSearchIcon(appInfo)
            }

            commonAppsRecyclerView?.apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                adapter = commonAppsAdapter
                overScrollMode = View.OVER_SCROLL_NEVER
            }

            commonAppsContainer?.visibility = View.VISIBLE
            Log.d(TAG, "历史应用图标显示完成，共 ${commonApps.size} 个")
        } else {
            commonAppsContainer?.visibility = View.GONE
            Log.d(TAG, "没有历史应用可显示")
        }
    }

    /**
     * 将应用加载到搜索图标内
     */
    private fun loadAppToSearchIcon(appInfo: AppInfo) {
        Log.d(TAG, "加载应用到搜索图标: ${appInfo.label}")

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
    }

    /**
     * 显示简易模式的AI悬浮窗
     */
    private fun showSimpleAIOverlay(packageName: String, appName: String, forceSimpleMode: Boolean = true) {
        try {
            Log.d(TAG, "显示AI悬浮窗: $appName, 简易模式: $forceSimpleMode")

            // 启动AI悬浮窗服务
            val intent = Intent(this, com.example.aifloatingball.service.AIAppOverlayService::class.java).apply {
                putExtra("package_name", packageName)
                putExtra("app_name", appName)
                if (forceSimpleMode) {
                    putExtra("mode", "simple") // 简易模式
                } else {
                    putExtra("mode", "overlay") // 普通悬浮窗模式，但显示简易样式
                }
            }
            startService(intent)

            Log.d(TAG, "AI悬浮窗服务已启动")
        } catch (e: Exception) {
            Log.e(TAG, "启动AI悬浮窗服务失败", e)
        }
    }
    
    /**
     * 显示AI应用悬浮窗面板（从灵动岛按钮触发）
     * 显示与软件tab搜索后弹出的相同的AI面板
     */
    private fun showAIAppOverlayPanel() {
        try {
            Log.d(TAG, "显示AI应用悬浮窗面板（从灵动岛按钮触发）")
            
            // 获取当前剪贴板内容作为查询文本
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val currentClipboard = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            
            // 启动AI应用悬浮窗服务，使用island模式
            // island模式会直接显示AI应用列表，不需要二级菜单
            val intent = Intent(this, com.example.aifloatingball.service.AIAppOverlayService::class.java).apply {
                action = com.example.aifloatingball.service.AIAppOverlayService.ACTION_SHOW_OVERLAY
                putExtra(com.example.aifloatingball.service.AIAppOverlayService.EXTRA_APP_NAME, "AI助手")
                putExtra(com.example.aifloatingball.service.AIAppOverlayService.EXTRA_QUERY, currentClipboard)
                putExtra(com.example.aifloatingball.service.AIAppOverlayService.EXTRA_PACKAGE_NAME, "")
                putExtra("mode", "island") // 使用island模式，直接显示AI应用列表
            }
            startService(intent)
            
            Log.d(TAG, "AI应用悬浮窗面板服务已启动")
        } catch (e: Exception) {
            Log.e(TAG, "显示AI应用悬浮窗面板失败", e)
            e.printStackTrace()
            Toast.makeText(this, "无法显示AI助手面板: ${e.message}", Toast.LENGTH_SHORT).show()
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
            Triple("com.sina.weibo.lite", "sinaweibo", "微博轻享"),
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
            Triple("com.youku.phone", "youku", "优酷"),
            Triple("com.smile.gifmaker", "kuaishou", "快手"),
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
            Triple("com.tencent.map", "tencentmap", "腾讯地图"),
            
            // 浏览器类
            Triple("com.tencent.mtt", "mttbrowser", "QQ浏览器"),
            Triple("com.UCMobile", "ucbrowser", "UC浏览器"),
            Triple("com.android.chrome", "googlechrome", "Chrome"),
            Triple("org.mozilla.firefox", "firefox", "Firefox"),
            Triple("com.quark.browser", "quark", "夸克"),
            Triple("com.baidu.searchbox", "baiduboxapp", "百度"),
            Triple("com.sohu.inputmethod.sogou", "sogou", "搜狗"),
            Triple("com.qihoo.browser", "qihoo", "360浏览器"),
            
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
            Triple("com.netease.nr", "newsapp", "网易新闻"),
            Triple("com.ss.android.article.news", "toutiao", "今日头条"),
            Triple("com.tencent.news", "tencentnews", "腾讯新闻")
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
                    "weixin" -> {
                        // 微信不支持搜索URL Scheme，降级到普通启动
                        Log.d(TAG, "微信不支持搜索URL Scheme，降级到普通启动")
                        null
                    }
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
                    "youku" -> Intent(Intent.ACTION_VIEW, Uri.parse("youku://search?keyword=$encodedQuery"))
                    "kuaishou" -> Intent(Intent.ACTION_VIEW, Uri.parse("kuaishou://search?keyword=$encodedQuery"))
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
                    "tencentmap" -> Intent(Intent.ACTION_VIEW, Uri.parse("tencentmap://map/place/search?query=$encodedQuery"))
                    
                    // 浏览器类
                    "mttbrowser" -> Intent(Intent.ACTION_VIEW, Uri.parse("mttbrowser://search?query=$encodedQuery"))
                    "ucbrowser" -> Intent(Intent.ACTION_VIEW, Uri.parse("ucbrowser://search?keyword=$encodedQuery"))
                    "googlechrome" -> Intent(Intent.ACTION_VIEW, Uri.parse("googlechrome://www.google.com/search?q=$encodedQuery"))
                    "firefox" -> Intent(Intent.ACTION_VIEW, Uri.parse("firefox://search?q=$encodedQuery"))
                    "quark" -> Intent(Intent.ACTION_VIEW, Uri.parse("quark://search?q=$encodedQuery"))
                    "baiduboxapp" -> Intent(Intent.ACTION_VIEW, Uri.parse("baiduboxapp://searchbox?action=search&query=$encodedQuery"))
                    "sogou" -> Intent(Intent.ACTION_VIEW, Uri.parse("sogou://search?keyword=$encodedQuery"))
                    "qihoo" -> Intent(Intent.ACTION_VIEW, Uri.parse("qihoo://search?keyword=$encodedQuery"))
                    
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
                    "toutiao" -> Intent(Intent.ACTION_VIEW, Uri.parse("toutiao://search?keyword=$encodedQuery"))
                    "tencentnews" -> Intent(Intent.ACTION_VIEW, Uri.parse("tencentnews://search?keyword=$encodedQuery"))
                    
                    else -> {
                        // 通用URL scheme格式
                        Intent(Intent.ACTION_VIEW, Uri.parse("${appInfo.urlScheme}://search?query=$encodedQuery"))
                    }
                }
                
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    hideContent()
                    return
                } else {
                    // Intent为null，降级到普通启动
                    Log.d(TAG, "Intent为null，降级到普通启动: ${appInfo.urlScheme}")
                }
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
        
        // 更新状态
        updateState(IslandState.BALL)
        
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
        
        // 创建圆球视图（完全隐藏，避免闪现）
        createBallView()
        ballView?.visibility = View.INVISIBLE // 使用INVISIBLE而不是设置alpha
        ballView?.alpha = 0f
        ballView?.scaleX = 0.1f
        ballView?.scaleY = 0.1f
        
        // 设置点击监听器
        setupBallClickListener()
        
        // 先启动灵动岛消失动画
        val islandAnimation = animatingIslandView?.animate()
            ?.withLayer()
            ?.alpha(0f)
            ?.scaleX(0.1f)
            ?.scaleY(0.1f)
            ?.setInterpolator(AccelerateInterpolator())
            ?.setDuration(300)
            ?.withEndAction {
                // 隐藏灵动岛视图
                animatingIslandView?.visibility = View.GONE
                appSearchIconScrollView?.visibility = View.GONE
                clearAppSearchIcons()
                
                // 优化窗口参数
                optimizeWindowForBallMode()
                
                // 灵动岛消失后，显示圆球并开始动画
                ballView?.visibility = View.VISIBLE
                Log.d(TAG, "圆球视图已设置为可见，开始动画")
        
        val ballAnimation = ballView?.animate()
            ?.withLayer()
            ?.alpha(0.9f)
            ?.scaleX(1f)
            ?.scaleY(1f)
            ?.setInterpolator(OvershootInterpolator(0.8f))
            ?.setDuration(400)
                    ?.withEndAction {
                        Log.d(TAG, "圆球动画完成，启动透明度渐变")
                        // 动画完成后启动透明度渐变
                        startFadeOutTimer()
                    }
                ballAnimation?.start()
                
                // 添加调试信息
                Log.d(TAG, "圆球状态: visibility=${ballView?.visibility}, alpha=${ballView?.alpha}, scaleX=${ballView?.scaleX}, scaleY=${ballView?.scaleY}")
                Log.d(TAG, "圆球位置: leftMargin=${(ballView?.layoutParams as? FrameLayout.LayoutParams)?.leftMargin}, topMargin=${(ballView?.layoutParams as? FrameLayout.LayoutParams)?.topMargin}")
            }
        
        // 启动灵动岛消失动画
        islandAnimation?.start()
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
        
        // 优化窗口参数，减少遮挡
        optimizeWindowForBallMode()
        
        // 显示圆球动画
        ballView?.visibility = View.VISIBLE
        Log.d(TAG, "switchToBallMode: 圆球视图已设置为可见，开始动画")
        
        ballView?.animate()
            ?.alpha(0.9f)
            ?.scaleX(1f)
            ?.scaleY(1f)
            ?.setDuration(400)
            ?.setInterpolator(AccelerateDecelerateInterpolator())
            ?.withEndAction {
                Log.d(TAG, "switchToBallMode: 圆球动画完成，启动透明度渐变")
                // 动画完成后启动透明度渐变
                startFadeOutTimer()
            }
            ?.start()
        
        // 添加调试信息
        Log.d(TAG, "switchToBallMode: 圆球状态: visibility=${ballView?.visibility}, alpha=${ballView?.alpha}")
        Log.d(TAG, "switchToBallMode: 圆球位置: leftMargin=${(ballView?.layoutParams as? FrameLayout.LayoutParams)?.leftMargin}, topMargin=${(ballView?.layoutParams as? FrameLayout.LayoutParams)?.topMargin}")
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
        
        // 创建完美圆形背景
        val ballSize = (40 * resources.displayMetrics.density).toInt() // 40dp，适中的大小
        
        // 设置布局参数，使用保存的位置或默认位置
        val savedX = settingsManager.getBallX()
        val savedY = settingsManager.getBallY()
        val defaultX = (resources.displayMetrics.widthPixels - ballSize) / 2 // 居中
        val defaultY = statusBarHeight + 20 * resources.displayMetrics.density.toInt()
        
        val ballX = if (savedX == -1) defaultX else savedX.coerceIn(0, resources.displayMetrics.widthPixels - ballSize)
        val ballY = if (savedY == -1) defaultY else savedY.coerceIn(statusBarHeight, resources.displayMetrics.heightPixels - ballSize)
        
        // 创建圆球视图
        ballView = View(this).apply {
            // 检测当前主题模式
            val themedContext = getThemedContext()
            val isDarkMode = (themedContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            
            Log.d(TAG, "圆球状态主题检测: isDarkMode=$isDarkMode")
            
            // 创建渐变背景，根据主题动态调整 - 小圆球状态使用专用颜色
            val ballDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                if (isDarkMode) {
                    // 暗色模式：使用小圆球状态专用颜色
                    setColor(resources.getColor(R.color.dynamic_island_ball_background, themedContext.theme))
                    setStroke((2 * resources.displayMetrics.density).toInt(), Color.parseColor("#60FFFFFF"))
                    Log.d(TAG, "圆球状态: 应用暗色主题 - 小圆球状态颜色")
                } else {
                    // 亮色模式：使用纯白色背景
                    setColor(Color.WHITE)
                    setStroke((2 * resources.displayMetrics.density).toInt(), Color.parseColor("#E0E0E0"))
                    Log.d(TAG, "圆球状态: 应用亮色主题 - 纯白色背景")
                }
            }
            
            background = ballDrawable
            
            // 设置阴影效果
            elevation = 12f
            
            layoutParams = FrameLayout.LayoutParams(ballSize, ballSize, Gravity.TOP or Gravity.START).apply {
                leftMargin = ballX // 使用保存的位置
                topMargin = ballY
            }
            visibility = View.INVISIBLE // 初始状态为不可见，避免闪现
            alpha = 0f // 初始透明度为0
        }
        
        // 添加到窗口容器
        windowContainerView?.addView(ballView)
        
        Log.d(TAG, "圆球视图已创建并添加到窗口容器，位置: (${ballX}, ${ballY}), 大小: ${ballSize}x${ballSize}")
        
        // 注意：不在这里启动动画，动画将在transitionToBallState中控制
    }
    
    // 圆球拖动相关变量
    private var isDragging = false
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var initialBallX = 0
    private var initialBallY = 0
    private var longPressRunnable: Runnable? = null
    private val longPressDelay = 500L // 长按延迟500ms
    
    // 透明度渐变相关变量
    private var fadeOutRunnable: Runnable? = null
    private val fadeOutDelay = 3000L // 3秒后开始渐变
    private val fadeOutDuration = 2000L // 2秒完成渐变
    private var isFadingOut = false

    /**
     * 设置圆球点击和拖动监听器
     */
    private fun setupBallClickListener() {
        ballView?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 记录初始位置
                    dragStartX = event.rawX
                    dragStartY = event.rawY
                    initialBallX = (view.layoutParams as FrameLayout.LayoutParams).leftMargin
                    initialBallY = (view.layoutParams as FrameLayout.LayoutParams).topMargin
                    
                    // 停止透明度渐变
                    stopFadeOutTimer()
                    
                    // 触摸时提供视觉反馈，立即恢复透明度
                    ballView?.animate()?.alpha(1f)?.setDuration(100)?.start()
                    
                    // 启动长按检测
                    longPressRunnable = Runnable {
                        if (!isDragging) {
                            Log.d(TAG, "长按检测触发，启动屏幕文字识别")
                            startScreenTextRecognition()
                        }
                    }
                    uiHandler.postDelayed(longPressRunnable!!, longPressDelay)
                    
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        // 拖动模式 - 实时更新位置
                        val deltaX = event.rawX - dragStartX
                        val deltaY = event.rawY - dragStartY
                        
                        // 计算新位置，确保圆球完全在屏幕内
                        val ballSize = view.width
                        val screenWidth = resources.displayMetrics.widthPixels
                        val screenHeight = resources.displayMetrics.heightPixels
                        
                        val newX = (initialBallX + deltaX.toInt()).coerceIn(0, screenWidth - ballSize)
                        val newY = (initialBallY + deltaY.toInt()).coerceIn(statusBarHeight, screenHeight - ballSize)
                        
                        // 直接更新布局参数，确保跟手
                        val layoutParams = view.layoutParams as FrameLayout.LayoutParams
                        layoutParams.leftMargin = newX
                        layoutParams.topMargin = newY
                        view.layoutParams = layoutParams
                        
                        // 记录拖动位置
                        updateWindowPositionForBall(newX, newY)
                        
                        true
                    } else {
                        // 检查是否应该开始拖动
                        val deltaX = Math.abs(event.rawX - dragStartX)
                        val deltaY = Math.abs(event.rawY - dragStartY)
                        if (deltaX > 15 || deltaY > 15) { // 增加阈值，避免误触发
                            // 移动距离超过阈值，取消长按检测
                            longPressRunnable?.let { uiHandler.removeCallbacks(it) }
                        }
                        false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 取消长按检测
                    longPressRunnable?.let { uiHandler.removeCallbacks(it) }
                    
                    if (isDragging) {
                        // 结束拖动
                        stopDragging()
                        // 拖动结束后重新启动渐变
                        startFadeOutTimer()
                    } else {
                        // 检查是否是有效的点击（没有移动太多）
                        val deltaX = Math.abs(event.rawX - dragStartX)
                        val deltaY = Math.abs(event.rawY - dragStartY)
                        if (deltaX < 10 && deltaY < 10) {
                            // 普通点击 - 恢复灵动岛状态
                            ballView?.animate()?.alpha(0.9f)?.setDuration(200)?.start()
                            if (event.action == MotionEvent.ACTION_UP) {
            Log.d(TAG, "圆球被点击，恢复灵动岛状态")
            restoreIslandState()
                            }
                        } else {
                            // 移动距离过大，不是有效点击，恢复透明度并重新启动渐变
                            ballView?.animate()?.alpha(0.9f)?.setDuration(200)?.withEndAction {
                                startFadeOutTimer()
                            }?.start()
                        }
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 开始拖动模式
     */
    private fun startDragging() {
        isDragging = true
        Log.d(TAG, "开始拖动圆球")
        
        // 停止透明度渐变
        stopFadeOutTimer()
        
        // 拖动时的视觉反馈
        ballView?.animate()
            ?.scaleX(1.2f)
            ?.scaleY(1.2f)
            ?.alpha(1f) // 拖动时完全不透明
            ?.setDuration(200)
            ?.start()
    }

    /**
     * 停止拖动模式
     */
    private fun stopDragging() {
        isDragging = false
        Log.d(TAG, "停止拖动圆球")
        
        // 恢复视觉状态
        ballView?.animate()
            ?.scaleX(1f)
            ?.scaleY(1f)
            ?.alpha(0.9f) // 保持较高的可见性
            ?.setDuration(200)
            ?.start()
        
        // 保存新位置
        val layoutParams = ballView?.layoutParams as? FrameLayout.LayoutParams
        if (layoutParams != null) {
            settingsManager.setBallPosition(layoutParams.leftMargin, layoutParams.topMargin)
            Log.d(TAG, "圆球位置已保存: (${layoutParams.leftMargin}, ${layoutParams.topMargin})")
        }
    }
    
    /**
     * 更新窗口位置以跟随圆球拖动
     * 现在窗口是全屏的，只需要更新圆球的位置
     */
    private fun updateWindowPositionForBall(ballX: Int, ballY: Int) {
        // 窗口现在是全屏的，不需要更新窗口位置
        // 只需要确保圆球位置正确即可
        Log.d(TAG, "圆球拖动到位置: ($ballX, $ballY)")
    }
    
    /**
     * 更新圆球主题
     * 当主题变化时动态更新圆球外观
     */
    private fun updateBallTheme() {
        ballView?.let { ball ->
            try {
                // 检测当前主题模式
                val themedContext = getThemedContext()
                val isDarkMode = (themedContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                
                // 创建新的背景，根据主题动态调整 - 使用专用颜色资源
                val ballDrawable = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    if (isDarkMode) {
                        // 暗色模式：使用小圆球状态专用颜色
                        setColor(resources.getColor(R.color.dynamic_island_ball_background, themedContext.theme))
                        setStroke((2 * resources.displayMetrics.density).toInt(), Color.parseColor("#60FFFFFF"))
                    } else {
                        // 亮色模式：使用纯白色背景
                        setColor(Color.WHITE)
                        setStroke((2 * resources.displayMetrics.density).toInt(), Color.parseColor("#E0E0E0"))
                    }
                }
                
                ball.background = ballDrawable
                Log.d(TAG, "圆球主题已更新: ${if (isDarkMode) "暗色模式" else "亮色模式"}")
            } catch (e: Exception) {
                Log.e(TAG, "更新圆球主题失败", e)
            }
        }
    }
    
    // 圆球触摸状态跟踪
    private var ballTouchDownInside = false
    
    // 系统手势检测相关变量
    private var systemGestureStartX = 0f
    private var systemGestureStartY = 0f
    private var systemGestureStartTime = 0L
    private var isSystemGestureActive = false
    
    /**
     * 检测是否是系统级侧边滑动返回手势
     * 这个方法确保系统手势不被应用拦截
     */
    private fun isSystemBackGesture(event: MotionEvent): Boolean {
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val edgeZoneWidth = (50 * resources.displayMetrics.density).toInt()
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 检查是否在边缘区域
                val isInLeftEdge = event.rawX <= edgeZoneWidth
                val isInRightEdge = event.rawX >= screenWidth - edgeZoneWidth
                val isInTopEdge = event.rawY <= edgeZoneWidth
                val isInBottomEdge = event.rawY >= screenHeight - edgeZoneWidth
                
                val isEdgeGesture = (isInLeftEdge || isInRightEdge) && !isInTopEdge && !isInBottomEdge
                
                if (isEdgeGesture) {
                    systemGestureStartX = event.rawX
                    systemGestureStartY = event.rawY
                    systemGestureStartTime = System.currentTimeMillis()
                    isSystemGestureActive = true
                    Log.d(TAG, "开始检测系统边缘手势: x=${event.rawX}, edge=${if (isInLeftEdge) "左" else "右"}")
                }
                
                return false // 不立即消费DOWN事件
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (!isSystemGestureActive) return false
                
                val deltaX = event.rawX - systemGestureStartX
                val deltaY = event.rawY - systemGestureStartY
                val deltaTime = System.currentTimeMillis() - systemGestureStartTime
                
                // 检查是否是水平滑动（返回手势）
                val isHorizontalSwipe = kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) && kotlin.math.abs(deltaX) > 20
                val isFastEnough = deltaTime > 0 && kotlin.math.abs(deltaX) / deltaTime > 0.5f
                
                if (isHorizontalSwipe && isFastEnough) {
                    Log.d(TAG, "确认系统返回手势: deltaX=${deltaX}, 速度=${kotlin.math.abs(deltaX) / deltaTime}")
                    // 触发系统返回手势
                    triggerSystemBackGesture()
                    return true // 确认是系统返回手势
                }
                
                return false
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isSystemGestureActive = false
                return false
            }
        }
        
        return false
    }
    
    /**
     * 触发系统返回手势
     * 通过临时调整窗口标志来支持系统手势
     */
    private fun triggerSystemBackGesture() {
        try {
            // 临时设置窗口为不可获取焦点，让系统手势穿透
            val windowParams = windowContainerView?.layoutParams as? WindowManager.LayoutParams
            if (windowParams != null) {
                // 备份当前标志
                val originalFlags = windowParams.flags
                
                // 临时添加不可获取焦点标志
                windowParams.flags = originalFlags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                windowManager?.updateViewLayout(windowContainerView, windowParams)
                
                Log.d(TAG, "已临时设置窗口为不可获取焦点，支持系统手势")
                
                // 延迟恢复原始标志
                windowContainerView?.postDelayed({
                    try {
                        windowParams.flags = originalFlags
                        windowManager?.updateViewLayout(windowContainerView, windowParams)
                        Log.d(TAG, "已恢复窗口焦点设置")
                    } catch (e: Exception) {
                        Log.e(TAG, "恢复窗口焦点设置失败", e)
                    }
                }, 1000) // 1秒后恢复
            }
            
            Log.d(TAG, "系统返回手势处理已完成")
        } catch (e: Exception) {
            Log.e(TAG, "触发系统返回手势失败", e)
        }
    }
    
    /**
     * 处理圆球状态下的触摸事件
     * 重新设计的触摸处理逻辑，确保只有圆球区域内的触摸被处理
     */
    private fun handleBallTouchEvent(event: MotionEvent): Boolean {
        // 获取圆球区域
        val ballRect = android.graphics.Rect()
        ballView?.getGlobalVisibleRect(ballRect)
        val x = event.rawX.toInt()
        val y = event.rawY.toInt()
        
        val isInBallArea = ballRect.contains(x, y)
        
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isInBallArea) {
                    // 在圆球区域内按下，记录状态并处理
                    ballTouchDownInside = true
                    Log.d(TAG, "圆球区域内按下，开始处理触摸")
                    true
                } else {
                    // 在圆球区域外按下，不处理，让事件穿透
                    ballTouchDownInside = false
                    Log.d(TAG, "圆球区域外按下，让事件穿透")
                    false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                // 只有在圆球区域内按下的情况下才处理移动事件
                if (ballTouchDownInside) {
                    Log.d(TAG, "圆球拖动中...")
                    true
                } else {
                    // 不是在圆球区域内开始的触摸，让事件穿透
                    false
                }
            }
            MotionEvent.ACTION_UP -> {
                if (ballTouchDownInside) {
                    // 在圆球区域内开始的触摸，处理抬起事件
                    if (isInBallArea) {
                        Log.d(TAG, "圆球被点击，恢复灵动岛状态")
                        restoreIslandState()
                    }
                    ballTouchDownInside = false
                    true
                } else {
                    // 不是在圆球区域内开始的触摸，让事件穿透
                    false
                }
            }
            MotionEvent.ACTION_CANCEL -> {
                // 取消事件，重置状态
                ballTouchDownInside = false
                false
            }
            else -> false
        }
    }

    /**
     * 优化窗口参数以减少遮挡
     * 在圆球模式下，将窗口尺寸缩小到只覆盖圆球区域
     */
    private fun optimizeWindowForBallMode() {
        try {
            val windowParams = windowContainerView?.layoutParams as? WindowManager.LayoutParams
            if (windowParams != null) {
                // 计算圆球区域 - 使用与createBallView相同的逻辑
                val ballSize = (40 * resources.displayMetrics.density).toInt() // 40dp，与圆球视觉大小一致
                val touchAreaSize = (60 * resources.displayMetrics.density).toInt() // 60dp触摸区域
                val screenWidth = resources.displayMetrics.widthPixels
                val screenHeight = resources.displayMetrics.heightPixels
                
                // 使用保存的位置或默认位置 - 与createBallView保持一致
                val savedX = settingsManager.getBallX()
                val savedY = settingsManager.getBallY()
                val defaultX = (screenWidth - ballSize) / 2 // 居中
                val defaultY = statusBarHeight + 20 * resources.displayMetrics.density.toInt()
                
                val ballX = if (savedX == -1) defaultX else savedX.coerceIn(0, screenWidth - ballSize)
                val ballY = if (savedY == -1) defaultY else savedY.coerceIn(statusBarHeight, screenHeight - ballSize)
                
                // 设置窗口参数，与灵动岛模式保持一致
                windowParams.width = WindowManager.LayoutParams.MATCH_PARENT
                windowParams.height = WindowManager.LayoutParams.WRAP_CONTENT  // 关键：使用WRAP_CONTENT而不是MATCH_PARENT
                windowParams.x = 0
                windowParams.y = 0
                
                // 确保窗口可见
                windowParams.alpha = 1.0f
                
                // 重新设置窗口标志，确保触摸穿透正常工作，同时保持剪贴板监听
                windowParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                
                // 更新窗口布局
                windowManager?.updateViewLayout(windowContainerView, windowParams)
                
                Log.d(TAG, "圆球模式窗口优化完成: 全屏模式，圆球位置($ballX, $ballY)，触摸区域${touchAreaSize}x${touchAreaSize}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "优化圆球模式窗口参数失败", e)
        }
    }

    /**
     * 恢复窗口参数到正常状态
     * 当从圆球模式恢复到正常模式时调用
     */
    private fun restoreWindowForNormalMode() {
        try {
            val windowParams = windowContainerView?.layoutParams as? WindowManager.LayoutParams
            if (windowParams != null) {
                // 恢复全屏窗口参数
                windowParams.width = WindowManager.LayoutParams.MATCH_PARENT
                windowParams.height = WindowManager.LayoutParams.WRAP_CONTENT
                windowParams.x = 0
                windowParams.y = 0
                
                // 重新设置窗口标志，确保触摸穿透正常工作，同时保持剪贴板监听
                windowParams.flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                
                // 更新窗口布局
                windowManager?.updateViewLayout(windowContainerView, windowParams)
                
                Log.d(TAG, "窗口参数已恢复到正常状态，保持触摸穿透功能")
            }
        } catch (e: Exception) {
            Log.e(TAG, "恢复窗口参数失败", e)
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
     * 初始化剪贴板广播接收器
     */
    private fun initClipboardBroadcastReceiver() {
        try {
            clipboardBroadcastReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    when (intent?.action) {
                        MyAccessibilityService.ACTION_CLIPBOARD_CHANGED -> {
                            val content = intent.getStringExtra(MyAccessibilityService.EXTRA_CLIPBOARD_CONTENT)
                            if (!content.isNullOrEmpty()) {
                                Log.d(TAG, "收到无障碍服务剪贴板广播: ${content.take(50)}${if (content.length > 50) "..." else ""}")
                                autoExpandForClipboard(content)
                            }
                        }
                        ClipboardForegroundService.ACTION_CLIPBOARD_DETECTED -> {
                            val content = intent.getStringExtra(ClipboardForegroundService.EXTRA_CLIPBOARD_CONTENT)
                            if (!content.isNullOrEmpty()) {
                                Log.d(TAG, "收到前台服务剪贴板广播: ${content.take(50)}${if (content.length > 50) "..." else ""}")
                                autoExpandForClipboard(content)
                            }
                        }
                    }
                }
            }

            val filter = IntentFilter().apply {
                addAction(MyAccessibilityService.ACTION_CLIPBOARD_CHANGED)
                addAction(ClipboardForegroundService.ACTION_CLIPBOARD_DETECTED)
            }
            LocalBroadcastManager.getInstance(this).registerReceiver(clipboardBroadcastReceiver!!, filter)

            Log.d(TAG, "剪贴板广播接收器已初始化（支持双重监听）")
        } catch (e: Exception) {
            Log.e(TAG, "初始化剪贴板广播接收器失败", e)
        }
    }

    /**
     * 启动剪贴板前台服务
     */
    private fun startClipboardForegroundService() {
        try {
            val intent = Intent(this, ClipboardForegroundService::class.java).apply {
                action = ClipboardForegroundService.ACTION_START_MONITORING
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }

            Log.d(TAG, "✅ 剪贴板前台服务已启动")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 启动剪贴板前台服务失败", e)
        }
    }

    /**
     * 停止剪贴板前台服务
     */
    private fun stopClipboardForegroundService() {
        try {
            val intent = Intent(this, ClipboardForegroundService::class.java).apply {
                action = ClipboardForegroundService.ACTION_STOP_MONITORING
            }
            startService(intent)

            Log.d(TAG, "✅ 剪贴板前台服务已停止")
        } catch (e: Exception) {
            Log.e(TAG, "❌ 停止剪贴板前台服务失败", e)
        }
    }
    
    // handleClipboardChange方法已移除，改用无障碍服务处理
    
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
                    // 移除自动缩小功能，让用户手动控制
                    Log.d(TAG, "灵动岛展开完成，等待用户操作")
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
            // 获取主题化的上下文
            val themedContext = getThemedContext()
            val isDarkMode = (themedContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            
            // 获取当前主题模式设置
            val themeMode = settingsManager.getThemeMode()
            val systemDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            
            Log.d(TAG, "展开状态主题检测: themeMode=$themeMode, systemDarkMode=$systemDarkMode, isDarkMode=$isDarkMode")
            
            // 创建主容器布局（垂直方向）
            val mainContainer = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
                
                // 根据主题设置背景 - 展开状态使用专用颜色
                val expandedBackgroundColor = if (isDarkMode) {
                    // 深色模式：直接使用黑色
                    Color.parseColor("#000000")
                } else {
                    // 浅色模式：直接使用白色
                    Color.parseColor("#FFFFFF")
                }
                
                background = GradientDrawable().apply {
                    setColor(expandedBackgroundColor)
                    if (isDarkMode) {
                        setStroke(1.dpToPx(), Color.parseColor("#40FFFFFF")) // 白色边框
                        Log.d(TAG, "展开状态: 应用暗色主题 - 展开状态颜色: ${Integer.toHexString(expandedBackgroundColor)}")
                    } else {
                        setStroke(1.dpToPx(), Color.parseColor("#40000000")) // 黑色边框
                        Log.d(TAG, "展开状态: 应用亮色主题 - 展开状态颜色: ${Integer.toHexString(expandedBackgroundColor)}")
                    }
                    cornerRadius = 20.dpToPx().toFloat()
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
                
                // 添加AI按钮（在退出按钮左边）
                val aiButton = createAIButton(clipboardContent)
                appIconsContainer.addView(aiButton)
                Log.d(TAG, "AI按钮已添加到app图标容器")
                
                // 在app图标最后添加退出按钮
                val exitButton = createExitButton()
                appIconsContainer.addView(exitButton)
                Log.d(TAG, "退出按钮已添加到app图标容器")
            } else {
                // 没有推荐的应用，显示AI按钮和退出按钮
                val aiButton = createAIButton(clipboardContent)
                appIconsContainer.addView(aiButton)
                
                val exitButton = createExitButton()
                appIconsContainer.addView(exitButton)
                Log.d(TAG, "AI按钮和退出按钮已添加到app图标容器")
            }
            
            // 添加应用图标容器到主容器
            mainContainer.addView(appIconsContainer)

            // 添加输入框和发送按钮区域
            val inputContainer = createInputAndSendButtonContainer(clipboardContent)
            mainContainer.addView(inputContainer)

            // 不再自动添加AI预览部分，改为用户点击AI按钮时才显示

            islandContentView = mainContainer
            animatingIslandView?.addView(islandContentView)
            Log.d(TAG, "剪贴板视图创建完成，包含应用图标、输入框、发送按钮和退出按钮")
            
            // 延迟激活输入法，确保视图完全创建和显示后再激活
            uiHandler.postDelayed({
                searchInput?.let { inputField ->
                    Log.d(TAG, "开始自动激活输入法")
                    // 使用更简单但更有效的方法
                    activateInputMethodForFloatingWindow(inputField)
                }
            }, 800) // 延迟800ms确保动画完全完成，避免与动画冲突
            
        } catch (e: Exception) {
            Log.e(TAG, "创建剪贴板app历史视图失败", e)
        }
    }
    
    /**
     * 创建输入框和发送按钮容器
     */
    private fun createInputAndSendButtonContainer(clipboardContent: String): View {
        val inputContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 12.dpToPx()
            }
            setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
        }

        // 检测当前主题模式
        val themedContext = getThemedContext()
        val isDarkMode = (themedContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        // 创建输入框容器（包含输入框和清空按钮）
        val inputFieldContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                40.dpToPx(), // 固定高度，更容易点击
                1f
            ).apply {
                rightMargin = 8.dpToPx()
            }
        }

        // 创建输入框
        var isUserInput = false // 标志：用户是否已经开始输入
        val inputField = EditText(this).apply {
            id = View.generateViewId()
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            hint = "输入搜索内容..."
            textSize = 16f // 增大字体
            maxLines = 1
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = InputType.TYPE_CLASS_TEXT
            
            // 将动态创建的输入框赋值给searchInput变量，确保animateIsland方法能正确访问
            searchInput = this
            
            // 设置输入框样式 - 根据主题调整
            background = GradientDrawable().apply {
                if (isDarkMode) {
                    // 暗色模式：半透明黑色背景
                    setColor(Color.parseColor("#F01C1C1E"))
                    setStroke(2.dpToPx(), Color.parseColor("#4CAF50")) // 绿色边框
                } else {
                    // 亮色模式：半透明白色背景
                    setColor(Color.parseColor("#F0FFFFFF"))
                    setStroke(2.dpToPx(), Color.parseColor("#4CAF50")) // 绿色边框
                }
                cornerRadius = 12.dpToPx().toFloat() // 更大的圆角
            }
            setPadding(16.dpToPx(), 10.dpToPx(), 48.dpToPx(), 10.dpToPx()) // 右边距为清空按钮留空间
            
            // 根据主题设置文字颜色 - 使用主题化颜色
            setTextColor(resources.getColor(R.color.dynamic_island_input_text, themedContext.theme))
            setHintTextColor(resources.getColor(R.color.dynamic_island_input_hint, themedContext.theme))

            // 预填充剪贴板内容，设置为虚灰色
            if (clipboardContent.isNotEmpty()) {
                setText(clipboardContent)
                setTextColor(Color.parseColor("#999999")) // 虚灰色，表示自动粘贴的内容
                isUserInput = false // 初始状态为自动粘贴
            }

            // 设置输入框点击监听 - 只在自动激活失败时手动激活
            setOnClickListener {
                Log.d(TAG, "输入框被点击，检查是否需要手动激活输入法")
                
                // 检查输入法是否已激活，如果没有则激活
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                if (!imm.isActive(this)) {
                    Log.d(TAG, "输入法未激活，手动激活")
                    activateInputMethodForFloatingWindow(this)
                }
            }

            // 设置输入框触摸监听 - 简化处理
            setOnTouchListener { _, event ->
                if (event.action == android.view.MotionEvent.ACTION_DOWN) {
                    Log.d(TAG, "输入框被触摸，请求焦点")
                    requestFocus()
                }
                false // 不消费事件，让其他监听器继续处理
            }

            // 设置输入框焦点监听
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    Log.d(TAG, "输入框获得焦点，更新样式")

                    // 获得焦点时改变边框颜色
                    background = GradientDrawable().apply {
                        if (isDarkMode) {
                            setColor(Color.parseColor("#FF1C1C1E")) // 暗色模式：完全不透明的黑色背景
                        } else {
                            setColor(Color.parseColor("#FFFFFF")) // 亮色模式：完全不透明的白色背景
                        }
                        cornerRadius = 12.dpToPx().toFloat()
                        setStroke(3.dpToPx(), Color.parseColor("#2196F3")) // 蓝色边框表示焦点
                    }

                    // 不在这里激活输入法，避免与自动激活冲突
                } else {
                    // 失去焦点时恢复原样
                    background = GradientDrawable().apply {
                        if (isDarkMode) {
                            setColor(Color.parseColor("#F01C1C1E"))
                        } else {
                            setColor(Color.parseColor("#F0FFFFFF"))
                        }
                        cornerRadius = 12.dpToPx().toFloat()
                        setStroke(2.dpToPx(), Color.parseColor("#4CAF50"))
                    }
                }
            }

            // 添加文本变化监听器 - 用户开始输入时提示文本消失，控制清空按钮显示，区分文字颜色
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    // 用户开始输入时，清除提示文本
                    if (!s.isNullOrEmpty() && hint.isNotEmpty()) {
                        hint = "" // 清除提示文本
                    } else if (s.isNullOrEmpty()) {
                        hint = "输入搜索内容..." // 恢复提示文本
                    }
                    
                    // 控制清空按钮的显示/隐藏
                    val clearButton = inputFieldContainer.findViewById<ImageButton>(R.id.clear_button)
                    if (s.isNullOrEmpty()) {
                        clearButton?.visibility = View.GONE
                    } else {
                        clearButton?.visibility = View.VISIBLE
                    }
                }
                override fun afterTextChanged(s: android.text.Editable?) {
                    // 检测用户是否开始输入（与初始内容不同）
                    if (!s.isNullOrEmpty() && !isUserInput) {
                        val currentText = s.toString()
                        val initialText = clipboardContent
                        if (currentText != initialText) {
                            isUserInput = true
                            setTextColor(resources.getColor(R.color.dynamic_island_input_text, themedContext.theme)) // 使用主题化颜色
                            Log.d(TAG, "检测到用户输入，文字颜色改为主题化颜色")
                        }
                    }
                }
            })

            // 支持文本操作菜单（考虑悬浮窗特性）
            setupFloatingWindowTextOperations(this)
        }

        // 创建清空按钮
        val clearButton = ImageButton(this).apply {
            id = R.id.clear_button
            layoutParams = FrameLayout.LayoutParams(
                32.dpToPx(),
                32.dpToPx()
            ).apply {
                gravity = Gravity.CENTER_VERTICAL or Gravity.END
                rightMargin = 8.dpToPx()
            }
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx())
            
            // 根据主题设置图标颜色 - 使用主题化颜色
            setColorFilter(resources.getColor(R.color.dynamic_island_button_icon, themedContext.theme))
            
            // 设置按钮背景 - 使用主题化颜色
            background = GradientDrawable().apply {
                setColor(resources.getColor(R.color.dynamic_island_button_normal_background, themedContext.theme))
                setStroke(1.dpToPx(), resources.getColor(R.color.dynamic_island_button_normal_stroke, themedContext.theme))
                cornerRadius = 16.dpToPx().toFloat()
            }
            
            // 初始状态隐藏
            visibility = if (clipboardContent.isNotEmpty()) View.VISIBLE else View.GONE
            
            // 设置点击事件
            setOnClickListener {
                Log.d(TAG, "清空按钮被点击")
                inputField.setText("")
                inputField.setTextColor(Color.parseColor("#999999")) // 重置为虚灰色
                isUserInput = false // 重置用户输入标志
                inputField.requestFocus()
                // 激活输入法
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(inputField, InputMethodManager.SHOW_IMPLICIT)
            }
            
            // 设置触摸效果
            setOnTouchListener { view, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        view.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                    }
                }
                false
            }
        }

        // 将输入框和清空按钮添加到容器
        inputFieldContainer.addView(inputField)
        inputFieldContainer.addView(clearButton)

        // 创建发送按钮
        val sendButton = ImageButton(this).apply {
            id = View.generateViewId()
            layoutParams = LinearLayout.LayoutParams(
                36.dpToPx(),
                36.dpToPx()
            )
            setImageResource(R.drawable.ic_send_plane)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(8.dpToPx(), 8.dpToPx(), 8.dpToPx(), 8.dpToPx())
            setColorFilter(resources.getColor(R.color.dynamic_island_send_icon_tint, themedContext.theme)) // 使用主题化颜色

            // 设置按钮背景 - 使用主题化颜色
            background = GradientDrawable().apply {
                setColor(resources.getColor(R.color.dynamic_island_expand_button_normal_background, themedContext.theme))
                setStroke(1.dpToPx(), resources.getColor(R.color.dynamic_island_expand_button_normal_stroke, themedContext.theme))
                cornerRadius = 8.dpToPx().toFloat()
            }

            // 设置点击事件
            setOnClickListener {
                val searchText = inputField.text.toString().trim()
                if (searchText.isNotEmpty()) {
                    Log.d(TAG, "发送按钮被点击，搜索内容: $searchText")
                    handleExpandedSearchInput(searchText)
                } else {
                    Toast.makeText(this@DynamicIslandService, "请输入搜索内容", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // 添加输入框容器和发送按钮到容器
        inputContainer.addView(inputFieldContainer)
        inputContainer.addView(sendButton)

        return inputContainer
    }

    /**
     * 收起到灵动岛初始状态
     * 关闭按钮应该返回到紧凑的灵动岛状态，而不是变成白色小球
     */
    private fun collapseToInitialState() {
        try {
            Log.d(TAG, "收起到灵动岛初始状态")

            // 隐藏输入法
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(windowContainerView?.windowToken, 0)

            // 清理展开的内容
            animatingIslandView?.removeAllViews()

            // 确保搜索模式为非活跃状态
            isSearchModeActive = false

            // 创建紧凑状态的灵动岛内容，而不是重新创建整个视图
            createCompactIslandContent()

            Log.d(TAG, "已返回灵动岛初始状态")
        } catch (e: Exception) {
            Log.e(TAG, "返回灵动岛初始状态失败", e)
        }
    }

    /**
     * 创建紧凑状态的灵动岛内容
     */
    private fun createCompactIslandContent() {
        try {
            // 确保灵动岛视图存在
            if (animatingIslandView == null) {
                showDynamicIsland()
                return
            }

            // 检测当前主题模式
            val themedContext = getThemedContext()
            val isDarkMode = (themedContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

            // 创建紧凑状态的内容容器
            val compactContent = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            }

            // 添加一个简单的指示器（类似iPhone Dynamic Island的紧凑状态）
            val indicator = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    24.dpToPx(),
                    4.dpToPx()
                )
                background = GradientDrawable().apply {
                    if (isDarkMode) {
                        setColor(Color.parseColor("#80FFFFFF")) // 暗色模式：半透明白色指示器
                    } else {
                        setColor(Color.parseColor("#80000000")) // 亮色模式：半透明黑色指示器
                    }
                    cornerRadius = 2.dpToPx().toFloat()
                }
            }

            compactContent.addView(indicator)
            
            // 设置点击监听器，点击紧凑状态可以展开
            compactContent.setOnClickListener {
                Log.d(TAG, "紧凑状态被点击，展开灵动岛")
                expandIsland()
            }
            
            animatingIslandView?.addView(compactContent)

            // 确保灵动岛可见且处于正确状态
            animatingIslandView?.visibility = View.VISIBLE
            animatingIslandView?.alpha = 1f

            Log.d(TAG, "紧凑状态内容创建完成")
        } catch (e: Exception) {
            Log.e(TAG, "创建紧凑状态内容失败", e)
        }
    }

    /**
     * 强制在悬浮窗中显示输入法
     * 悬浮窗环境需要特殊处理才能确保输入法立即弹出
     */
    private fun forceShowInputMethodInFloatingWindow(editText: EditText) {
        try {
            Log.d(TAG, "开始强制激活悬浮窗输入法")

            // 1. 首先更新窗口参数以支持输入法
            updateWindowParamsForInput()

            // 2. 确保EditText可以获取焦点
            editText.isFocusable = true
            editText.isFocusableInTouchMode = true
            editText.isClickable = true
            editText.isEnabled = true

            // 3. 设置光标位置（有助于输入法激活）
            editText.setSelection(editText.text.length)

            // 4. 强制请求焦点
            editText.requestFocus()

            // 5. 获取输入法管理器
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

            // 6. 悬浮窗环境下的输入法激活策略
            // 方法1: 使用SHOW_IMPLICIT（更适合悬浮窗环境）
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)

            // 方法2: 延迟使用SHOW_FORCED作为备用
            uiHandler.postDelayed({
                try {
                    if (!imm.isActive(editText)) {
                        Log.d(TAG, "使用SHOW_FORCED重试激活输入法")
                        imm.showSoftInput(editText, InputMethodManager.SHOW_FORCED)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "SHOW_FORCED重试失败", e)
                }
            }, 100)

            // 方法3: 使用toggleSoftInput作为最后手段
            uiHandler.postDelayed({
                try {
                    if (!imm.isActive(editText)) {
                        Log.d(TAG, "使用toggleSoftInput激活输入法")
                        imm.toggleSoftInput(InputMethodManager.SHOW_IMPLICIT, 0)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "toggleSoftInput激活失败", e)
                }
            }, 200)

            // 方法4: 模拟用户点击事件（悬浮窗环境特殊处理）
            uiHandler.postDelayed({
                try {
                    if (!imm.isActive(editText)) {
                        Log.d(TAG, "模拟点击事件激活输入法")
                        // 模拟点击事件
                        val downTime = System.currentTimeMillis()
                        val eventTime = System.currentTimeMillis()
                        val x = editText.width / 2f
                        val y = editText.height / 2f
                        
                        val downEvent = android.view.MotionEvent.obtain(
                            downTime, eventTime, android.view.MotionEvent.ACTION_DOWN, x, y, 0
                        )
                        val upEvent = android.view.MotionEvent.obtain(
                            downTime, eventTime + 10, android.view.MotionEvent.ACTION_UP, x, y, 0
                        )
                        
                        editText.dispatchTouchEvent(downEvent)
                        editText.dispatchTouchEvent(upEvent)
                        
                        downEvent.recycle()
                        upEvent.recycle()
                        
                        // 再次尝试显示输入法
                        imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "模拟点击事件失败", e)
                }
            }, 300)

            // 方法5: 最后的备用方案 - 使用反射调用隐藏方法
            uiHandler.postDelayed({
                try {
                    if (!imm.isActive(editText)) {
                        Log.d(TAG, "使用反射方法激活输入法")
                        // 使用反射调用隐藏的showSoftInputUnchecked方法
                        val method = InputMethodManager::class.java.getDeclaredMethod(
                            "showSoftInputUnchecked", 
                            Int::class.java, 
                            android.os.ResultReceiver::class.java
                        )
                        method.isAccessible = true
                        method.invoke(imm, InputMethodManager.SHOW_IMPLICIT, null)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "反射方法激活失败", e)
                }
            }, 400)

            Log.d(TAG, "悬浮窗输入法激活策略已启动")
        } catch (e: Exception) {
            Log.e(TAG, "强制激活悬浮窗输入法失败", e)
        }
    }

    // 添加一个标志来防止重复激活
    private var isInputMethodActivating = false
    
    // 状态记忆相关变量
    private enum class IslandState {
        COMPACT,    // 紧凑状态
        EXPANDED,   // 展开状态
        BALL        // 圆球状态
    }
    private var previousState: IslandState = IslandState.COMPACT
    private var currentState: IslandState = IslandState.COMPACT

    /**
     * 更新状态并记录上一个状态
     */
    private fun updateState(newState: IslandState) {
        previousState = currentState
        currentState = newState
        Log.d(TAG, "状态更新: $previousState -> $currentState")
    }

    /**
     * 更新所有状态的主题
     * 当主题模式改变时调用此方法
     */
    private fun updateAllStatesTheme() {
        try {
            Log.d(TAG, "开始更新所有状态的主题")
            
            // 检测当前主题模式
            val isDarkMode = isDarkMode()
            val themedContext = getThemedContext()
            
            Log.d(TAG, "当前主题模式: ${if (isDarkMode) "暗色" else "亮色"}")
            
            // 更新原始状态的主题
            updateOriginalStateTheme(isDarkMode)
            
            // 更新展开状态的主题
            updateExpandedStateTheme(isDarkMode)
            
            // 更新圆球状态的主题
            updateBallStateTheme(isDarkMode)
            
            // 更新按钮图标颜色
            islandContentView?.let { contentView ->
                updateButtonIconColors(contentView, isDarkMode, themedContext)
            }
            
            // 更新搜索输入框主题
            updateSearchInputTheme(isDarkMode, themedContext)
            
            // 强制刷新所有视图
            forceRefreshAllViews()
            
            Log.d(TAG, "所有状态主题更新完成")
        } catch (e: Exception) {
            Log.e(TAG, "更新主题失败", e)
        }
    }
    
    /**
     * 强制刷新所有视图
     */
    private fun forceRefreshAllViews() {
        try {
            // 刷新灵动岛视图
            animatingIslandView?.invalidate()
            animatingIslandView?.requestLayout()
            
            // 刷新内容视图
            islandContentView?.invalidate()
            islandContentView?.requestLayout()
            
            // 刷新圆球视图
            ballView?.invalidate()
            ballView?.requestLayout()
            
            // 刷新配置面板
            configPanelView?.invalidate()
            configPanelView?.requestLayout()
            
            Log.d(TAG, "所有视图已强制刷新")
        } catch (e: Exception) {
            Log.e(TAG, "强制刷新视图失败", e)
        }
    }

    /**
     * 更新原始状态的主题
     */
    private fun updateOriginalStateTheme(isDarkMode: Boolean) {
        animatingIslandView?.let { view ->
            val backgroundColor = if (isDarkMode) {
                Color.parseColor("#000000") // 深色模式：黑色
            } else {
                Color.parseColor("#FFFFFF") // 浅色模式：白色
            }
            
            val backgroundDrawable = GradientDrawable().apply {
                setColor(backgroundColor)
                if (isDarkMode) {
                    setStroke(1.dpToPx(), Color.parseColor("#40FFFFFF"))
                } else {
                    setStroke(1.dpToPx(), Color.parseColor("#40000000"))
                }
                cornerRadius = 20.dpToPx().toFloat()
            }
            
            // 立即设置背景并刷新
            view.background = backgroundDrawable
            view.invalidate()
            
            Log.d(TAG, "原始状态主题已更新: ${if (isDarkMode) "暗色模式" else "亮色模式"}, 颜色: ${Integer.toHexString(backgroundColor)}")
        }
    }

    /**
     * 更新展开状态的主题
     */
    private fun updateExpandedStateTheme(isDarkMode: Boolean) {
        try {
            // 更新展开状态的背景颜色
            animatingIslandView?.let { view ->
                val backgroundColor = if (isDarkMode) {
                    Color.parseColor("#000000") // 深色模式：黑色
                } else {
                    Color.parseColor("#FFFFFF") // 浅色模式：白色
                }
                
                val backgroundDrawable = GradientDrawable().apply {
                    setColor(backgroundColor)
                    if (isDarkMode) {
                        setStroke(1.dpToPx(), Color.parseColor("#40FFFFFF"))
                    } else {
                        setStroke(1.dpToPx(), Color.parseColor("#40000000"))
                    }
                    cornerRadius = 20.dpToPx().toFloat()
                }
                
                // 立即设置背景并刷新
                view.background = backgroundDrawable
                view.invalidate()
                
                Log.d(TAG, "展开状态主题已更新: ${if (isDarkMode) "暗色模式" else "亮色模式"}, 颜色: ${Integer.toHexString(backgroundColor)}")
            }
            
            // 更新内容视图的主题
            applyThemeToContentView(islandContentView, isDarkMode)
        } catch (e: Exception) {
            Log.e(TAG, "更新展开状态主题失败", e)
        }
    }

    /**
     * 更新圆球状态的主题
     */
    private fun updateBallStateTheme(isDarkMode: Boolean) {
        ballView?.let { ball ->
            val backgroundColor = if (isDarkMode) {
                Color.parseColor("#000000") // 深色模式：黑色
            } else {
                Color.parseColor("#FFFFFF") // 浅色模式：白色
            }
            
            val ballDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(backgroundColor)
                if (isDarkMode) {
                    setStroke((2 * resources.displayMetrics.density).toInt(), Color.parseColor("#60FFFFFF"))
                } else {
                    setStroke((2 * resources.displayMetrics.density).toInt(), Color.parseColor("#60000000"))
                }
            }
            ball.background = ballDrawable
            ball.invalidate()
            
            Log.d(TAG, "圆球状态主题已更新: ${if (isDarkMode) "暗色模式" else "亮色模式"}, 颜色: ${Integer.toHexString(backgroundColor)}")
        }
    }
    
    /**
     * 应用主题到内容视图
     */
    private fun applyThemeToContentView(contentView: View?, isDarkMode: Boolean) {
        contentView?.let { view ->
            try {
                val themedContext = getThemedContext()
                
                // 更新文字颜色
                val notificationText = view.findViewById<TextView>(R.id.notification_text)
                val copyToastText = view.findViewById<TextView>(R.id.copy_toast_text)
                
                val textColor = resources.getColor(R.color.dynamic_island_text_color, themedContext.theme)
                
                notificationText?.setTextColor(textColor)
                copyToastText?.setTextColor(textColor)
                
                // 更新按钮图标颜色
                updateButtonIconColors(view, isDarkMode, themedContext)
                
                // 强制刷新视图
                view.invalidate()
                
                Log.d(TAG, "内容视图主题已应用: ${if (isDarkMode) "暗色模式" else "亮色模式"}")
            } catch (e: Exception) {
                Log.e(TAG, "应用内容视图主题失败", e)
            }
        }
    }
    
    /**
     * 更新按钮图标颜色
     */
    private fun updateButtonIconColors(view: View, isDarkMode: Boolean, themedContext: Context) {
        try {
            // 获取按钮图标颜色
            val buttonIconColor = resources.getColor(R.color.dynamic_island_button_icon, themedContext.theme)
            val expandButtonIconColor = resources.getColor(R.color.dynamic_island_expand_button_icon, themedContext.theme)
            
            // 更新所有按钮的图标颜色
            val btnAiAssistant = view.findViewById<MaterialButton>(R.id.btn_ai_assistant)
            val btnApps = view.findViewById<MaterialButton>(R.id.btn_apps)
            val btnSearch = view.findViewById<MaterialButton>(R.id.btn_search)
            val btnSettings = view.findViewById<MaterialButton>(R.id.btn_settings)
            val btnExit = view.findViewById<MaterialButton>(R.id.btn_exit)
            val btnExpand = view.findViewById<MaterialButton>(R.id.btn_expand)
            
            // 设置普通按钮图标颜色
            btnAiAssistant?.iconTint = ColorStateList.valueOf(buttonIconColor)
            btnApps?.iconTint = ColorStateList.valueOf(buttonIconColor)
            btnSearch?.iconTint = ColorStateList.valueOf(buttonIconColor)
            btnSettings?.iconTint = ColorStateList.valueOf(buttonIconColor)
            btnExit?.iconTint = ColorStateList.valueOf(buttonIconColor)
            
            // 设置展开按钮图标颜色（保持绿色）
            btnExpand?.iconTint = ColorStateList.valueOf(expandButtonIconColor)
            
            // 强制刷新所有按钮
            btnAiAssistant?.invalidate()
            btnApps?.invalidate()
            btnSearch?.invalidate()
            btnSettings?.invalidate()
            btnExit?.invalidate()
            btnExpand?.invalidate()
            
            Log.d(TAG, "按钮图标颜色已更新: ${if (isDarkMode) "暗色模式" else "亮色模式"}")
        } catch (e: Exception) {
            Log.e(TAG, "更新按钮图标颜色失败", e)
        }
    }
    
    /**
     * 更新搜索输入框主题
     */
    private fun updateSearchInputTheme(isDarkMode: Boolean, themedContext: Context) {
        try {
            searchInput?.let { input ->
                // 更新输入框背景颜色
                val inputBackgroundColor = resources.getColor(R.color.dynamic_island_input_background, themedContext.theme)
                val inputTextColor = resources.getColor(R.color.dynamic_island_input_text, themedContext.theme)
                val inputHintColor = resources.getColor(R.color.dynamic_island_input_hint, themedContext.theme)
                
                // 设置输入框背景
                input.setBackgroundColor(inputBackgroundColor)
                
                // 设置文字颜色
                input.setTextColor(inputTextColor)
                input.setHintTextColor(inputHintColor)
                
                // 强制刷新
                input.invalidate()
                
                Log.d(TAG, "搜索输入框主题已更新: ${if (isDarkMode) "暗色模式" else "亮色模式"}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新搜索输入框主题失败", e)
        }
    }
    
    /**
     * 测试主题切换功能
     * 用于验证深色模式切换是否正常工作
     */
    private fun testThemeSwitching() {
        try {
            Log.d(TAG, "开始测试主题切换功能")
            
            val currentThemeMode = settingsManager.getThemeMode()
            val isCurrentlyDark = isDarkMode()
            
            Log.d(TAG, "当前主题模式: $currentThemeMode")
            Log.d(TAG, "当前是否为深色模式: $isCurrentlyDark")
            
            // 强制更新所有状态的主题
            updateAllStatesTheme()
            
            Log.d(TAG, "主题切换测试完成")
        } catch (e: Exception) {
            Log.e(TAG, "主题切换测试失败", e)
        }
    }

    /**
     * 简化的悬浮窗输入法激活方法
     * 专门针对悬浮窗环境优化，防止重复激活
     */
    private fun activateInputMethodForFloatingWindow(editText: EditText) {
        try {
            // 防止重复激活
            if (isInputMethodActivating) {
                Log.d(TAG, "输入法正在激活中，跳过重复调用")
                return
            }

            Log.d(TAG, "开始激活悬浮窗输入法（简化版）")
            isInputMethodActivating = true

            // 1. 更新窗口参数
            updateWindowParamsForInput()

            // 2. 确保EditText状态正确
            editText.isFocusable = true
            editText.isFocusableInTouchMode = true
            editText.isClickable = true
            editText.isEnabled = true

            // 3. 设置光标位置
            editText.setSelection(editText.text.length)

            // 4. 请求焦点
            editText.requestFocus()

            // 5. 获取输入法管理器
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

            // 6. 使用最简单的方法：直接调用showSoftInput
            imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)

            // 7. 延迟重试（悬浮窗环境需要）
            uiHandler.postDelayed({
                if (!imm.isActive(editText)) {
                    Log.d(TAG, "重试激活输入法")
                    imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
                }
                // 重置标志
                isInputMethodActivating = false
            }, 300)

            Log.d(TAG, "悬浮窗输入法激活完成")
        } catch (e: Exception) {
            Log.e(TAG, "激活悬浮窗输入法失败", e)
            isInputMethodActivating = false
        }
    }

    /**
     * 为悬浮窗中的EditText设置文本操作功能
     * 考虑悬浮窗的特殊性，需要特别处理文本选择和操作菜单
     */
    private fun setupFloatingWindowTextOperations(editText: EditText) {
        try {
            // 启用文本选择功能
            editText.setTextIsSelectable(true)
            editText.isLongClickable = true

            // 设置长按监听器
            editText.setOnLongClickListener { view ->
                try {
                    Log.d(TAG, "输入框长按，显示文本操作菜单")

                    // 确保输入框有焦点
                    if (!editText.hasFocus()) {
                        editText.requestFocus()
                    }

                    // 在悬浮窗中显示文本操作菜单
                    showFloatingTextMenu(editText)

                    true // 消费长按事件
                } catch (e: Exception) {
                    Log.e(TAG, "显示文本操作菜单失败", e)
                    false
                }
            }

            // 设置自定义文本选择处理（适配悬浮窗）
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                editText.customSelectionActionModeCallback = object : android.view.ActionMode.Callback {
                    override fun onCreateActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean {
                        // 添加自定义菜单项
                        menu?.clear()
                        menu?.add(0, android.R.id.selectAll, 0, "全选")
                        menu?.add(0, android.R.id.copy, 1, "复制")
                        menu?.add(0, android.R.id.cut, 2, "剪切")
                        menu?.add(0, android.R.id.paste, 3, "粘贴")
                        return true
                    }

                    override fun onPrepareActionMode(mode: android.view.ActionMode?, menu: android.view.Menu?): Boolean {
                        return false
                    }

                    override fun onActionItemClicked(mode: android.view.ActionMode?, item: android.view.MenuItem?): Boolean {
                        return handleTextOperation(editText, item?.itemId ?: 0)
                    }

                    override fun onDestroyActionMode(mode: android.view.ActionMode?) {
                        // 清理工作
                    }
                }
            }

            Log.d(TAG, "悬浮窗文本操作功能设置完成")
        } catch (e: Exception) {
            Log.e(TAG, "设置悬浮窗文本操作功能失败", e)
        }
    }

    /**
     * 在悬浮窗中显示文本操作菜单
     */
    private fun showFloatingTextMenu(editText: EditText) {
        try {
            // 创建弹出菜单
            val popupMenu = android.widget.PopupMenu(this, editText)

            // 添加菜单项
            popupMenu.menu.add(0, android.R.id.selectAll, 0, "全选")
            popupMenu.menu.add(0, android.R.id.copy, 1, "复制")
            popupMenu.menu.add(0, android.R.id.cut, 2, "剪切")
            popupMenu.menu.add(0, android.R.id.paste, 3, "粘贴")

            // 设置菜单项点击监听器
            popupMenu.setOnMenuItemClickListener { item ->
                handleTextOperation(editText, item.itemId)
            }

            // 显示菜单
            popupMenu.show()

            Log.d(TAG, "悬浮窗文本菜单已显示")
        } catch (e: Exception) {
            Log.e(TAG, "显示悬浮窗文本菜单失败", e)
            // 如果PopupMenu失败，尝试使用系统默认的文本选择
            try {
                editText.selectAll()
            } catch (e2: Exception) {
                Log.e(TAG, "备用文本选择也失败", e2)
            }
        }
    }

    /**
     * 处理文本操作
     */
    private fun handleTextOperation(editText: EditText, itemId: Int): Boolean {
        try {
            when (itemId) {
                android.R.id.selectAll -> {
                    editText.selectAll()
                    Toast.makeText(this, "已全选", Toast.LENGTH_SHORT).show()
                    return true
                }
                android.R.id.copy -> {
                    val selectedText = if (editText.hasSelection()) {
                        editText.text.subSequence(editText.selectionStart, editText.selectionEnd)
                    } else {
                        editText.text
                    }
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("text", selectedText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, "已复制", Toast.LENGTH_SHORT).show()
                    return true
                }
                android.R.id.cut -> {
                    val selectedText = if (editText.hasSelection()) {
                        val start = editText.selectionStart
                        val end = editText.selectionEnd
                        val text = editText.text.subSequence(start, end)
                        editText.text.delete(start, end)
                        text
                    } else {
                        val text = editText.text.toString()
                        editText.setText("")
                        text
                    }
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("text", selectedText)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, "已剪切", Toast.LENGTH_SHORT).show()
                    return true
                }
                android.R.id.paste -> {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    if (clipboard.hasPrimaryClip()) {
                        val clipData = clipboard.primaryClip?.getItemAt(0)?.text
                        if (clipData != null) {
                            if (editText.hasSelection()) {
                                val start = editText.selectionStart
                                val end = editText.selectionEnd
                                editText.text.replace(start, end, clipData)
                            } else {
                                val cursorPos = editText.selectionStart
                                editText.text.insert(cursorPos, clipData)
                            }
                            Toast.makeText(this, "已粘贴", Toast.LENGTH_SHORT).show()
                        }
                    }
                    return true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "处理文本操作失败", e)
        }
        return false
    }

    /**
     * 处理展开状态下的搜索输入
     */
    private fun handleExpandedSearchInput(searchText: String) {
        try {
            Log.d(TAG, "处理展开状态搜索: $searchText")

            // 隐藏输入法
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(windowContainerView?.windowToken, 0)

            // 显示搜索面板
            showConfigPanel()

            // 将搜索内容填入搜索面板的输入框
            uiHandler.postDelayed({
                searchInput?.setText(searchText)
                searchInput?.setSelection(searchText.length)
            }, 300)

        } catch (e: Exception) {
            Log.e(TAG, "处理展开状态搜索失败", e)
        }
    }

    /**
     * 创建AI按钮
     */
    private fun createAIButton(clipboardContent: String): View {
        // 获取主题化的上下文
        val themedContext = getThemedContext()
        
        val aiButton = ImageButton(this).apply {
            id = View.generateViewId()
            setImageResource(R.drawable.ic_chat) // 使用聊天图标
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(6.dpToPx(), 6.dpToPx(), 6.dpToPx(), 6.dpToPx())
            setColorFilter(resources.getColor(R.color.dynamic_island_send_icon_tint, themedContext.theme)) // 使用主题化颜色
            
            // 设置按钮背景 - 使用主题化颜色
            background = GradientDrawable().apply {
                setColor(resources.getColor(R.color.dynamic_island_expand_button_normal_background, themedContext.theme))
                cornerRadius = 12.dpToPx().toFloat()
                setStroke(1.dpToPx(), resources.getColor(R.color.dynamic_island_expand_button_normal_stroke, themedContext.theme))
            }
            
            // 设置按钮大小，与app图标保持一致
            layoutParams = LinearLayout.LayoutParams(40.dpToPx(), 40.dpToPx()).apply {
                leftMargin = 8.dpToPx()
            }
            
            // 添加点击事件
            setOnClickListener {
                Log.d(TAG, "AI按钮被点击")
                showAIPreviewForClipboard(clipboardContent)
            }
        }
        
        return aiButton
    }
    
    /**
     * 保存AI对话到聊天历史
     */
    private fun saveToChatHistory(userContent: String, aiResponse: String, serviceType: AIServiceType) {
        try {
            // 使用与AI联系人匹配的会话ID格式
            val sessionId = getAIContactId(serviceType)
            
            Log.d(TAG, "灵动岛保存对话 - 会话ID: $sessionId, 服务类型: ${serviceType.name}")
            Log.d(TAG, "灵动岛保存对话 - 用户消息: ${userContent.take(50)}...")
            Log.d(TAG, "灵动岛保存对话 - AI回复: ${aiResponse.take(50)}...")
            
            // 设置当前会话ID
            chatDataManager.setCurrentSessionId(sessionId, serviceType)
            
            // 添加用户消息
            chatDataManager.addMessage(sessionId, "user", userContent, serviceType)
            
            // 添加AI回复
            chatDataManager.addMessage(sessionId, "assistant", aiResponse, serviceType)
            
            Log.d(TAG, "AI对话已保存到聊天历史: $sessionId (${serviceType.name})")
            
            // 验证保存是否成功
            val savedMessages = chatDataManager.getMessages(sessionId, serviceType)
            Log.d(TAG, "验证保存结果 - 会话 $sessionId 中有 ${savedMessages.size} 条消息")
            
            // 显示保存提示
            Toast.makeText(this, "对话已保存到聊天历史", Toast.LENGTH_SHORT).show()
            
            // 发送广播通知简易模式更新数据
            notifySimpleModeUpdate(serviceType, sessionId)
            
        } catch (e: Exception) {
            Log.e(TAG, "保存AI对话到聊天历史失败", e)
        }
    }
    
    /**
     * 通知简易模式更新AI对话数据
     */
    private fun notifySimpleModeUpdate(serviceType: AIServiceType, sessionId: String) {
        try {
            Log.d(TAG, "=== 开始发送AI对话更新广播 ===")
            Log.d(TAG, "服务类型: ${serviceType.name}")
            Log.d(TAG, "会话ID: $sessionId")
            
            val intent = Intent("com.example.aifloatingball.AI_CHAT_UPDATED")
            intent.putExtra("ai_service_type", serviceType.name)
            intent.putExtra("session_id", sessionId)
            intent.putExtra("timestamp", System.currentTimeMillis())
            
            // 获取最新消息数量和内容
            val messages = chatDataManager.getMessages(sessionId, serviceType)
            intent.putExtra("message_count", messages.size)
            
            Log.d(TAG, "从ChatDataManager获取到 ${messages.size} 条消息")
            
            if (messages.isNotEmpty()) {
                val lastMessage = messages.last()
                intent.putExtra("last_message", lastMessage.content.take(100))
                intent.putExtra("last_message_role", lastMessage.role)
                Log.d(TAG, "最后消息: ${lastMessage.content.take(50)}... (${lastMessage.role})")
            }
            
            // 发送广播
            sendBroadcast(intent)
            Log.d(TAG, "广播已发送: ${intent.action}")
            Log.d(TAG, "广播包名: ${intent.`package`}")
            Log.d(TAG, "广播组件: ${intent.component}")
            
            // 额外尝试：发送到特定包名的广播
            val specificIntent = Intent("com.example.aifloatingball.AI_CHAT_UPDATED")
            specificIntent.setPackage("com.example.aifloatingball")
            specificIntent.putExtra("ai_service_type", serviceType.name)
            specificIntent.putExtra("session_id", sessionId)
            specificIntent.putExtra("timestamp", System.currentTimeMillis())
            specificIntent.putExtra("message_count", messages.size)
            
            if (messages.isNotEmpty()) {
                val lastMessage = messages.last()
                specificIntent.putExtra("last_message", lastMessage.content.take(100))
                specificIntent.putExtra("last_message_role", lastMessage.role)
            }
            
            sendBroadcast(specificIntent)
            Log.d(TAG, "特定包名广播已发送")
            
            // 写入文件作为备用同步机制
            writeSyncFile(serviceType, sessionId, messages)
            
            Log.d(TAG, "=== AI对话更新广播发送完成 ===")
            
        } catch (e: Exception) {
            Log.e(TAG, "发送AI对话更新广播失败", e)
        }
    }
    
    /**
     * 写入同步文件作为备用机制
     */
    private fun writeSyncFile(serviceType: AIServiceType, sessionId: String, messages: List<com.example.aifloatingball.data.ChatDataManager.ChatMessage>) {
        try {
            val syncFile = File(filesDir, "ai_sync_${serviceType.name.lowercase()}.json")
            val syncData = JSONObject().apply {
                put("service_type", serviceType.name)
                put("session_id", sessionId)
                put("message_count", messages.size)
                put("timestamp", System.currentTimeMillis())
                
                val messagesArray = JSONArray()
                messages.forEach { message ->
                    val messageObj = JSONObject().apply {
                        put("role", message.role)
                        put("content", message.content)
                        put("timestamp", message.timestamp)
                    }
                    messagesArray.put(messageObj)
                }
                put("messages", messagesArray)
            }
            
            syncFile.writeText(syncData.toString())
            Log.d(TAG, "同步文件已写入: ${syncFile.absolutePath}")
            
        } catch (e: Exception) {
            Log.e(TAG, "写入同步文件失败", e)
        }
    }
    
    /**
     * 获取AI联系人的ID（与简易模式中的格式保持一致）
     */
    private fun getAIContactId(serviceType: AIServiceType): String {
        val aiName = when (serviceType) {
            AIServiceType.DEEPSEEK -> "DeepSeek"
            AIServiceType.CHATGPT -> "ChatGPT"
            AIServiceType.CLAUDE -> "Claude"
            AIServiceType.GEMINI -> "Gemini"
            AIServiceType.ZHIPU_AI -> "智谱AI"
            AIServiceType.WENXIN -> "文心一言"
            AIServiceType.QIANWEN -> "通义千问"
            AIServiceType.XINGHUO -> "讯飞星火"
            AIServiceType.KIMI -> "Kimi"
            else -> serviceType.name
        }
        
        // 使用与简易模式相同的ID生成逻辑
        // 对于中文字符，直接使用原名称，不进行lowercase转换
        val processedName = if (aiName.contains(Regex("[\\u4e00-\\u9fff]"))) {
            // 包含中文字符，直接使用原名称
            aiName
        } else {
            // 英文字符，转换为小写
            aiName.lowercase()
        }
        
        val contactId = "ai_${processedName.replace(" ", "_")}"
        Log.d(TAG, "生成AI联系人ID: $serviceType -> $aiName -> $contactId")
        return contactId
    }
    
    /**
     * 显示文本上下文菜单
     */
    private fun showTextContextMenu(textView: TextView) {
        try {
            val popupMenu = PopupMenu(this, textView)
            popupMenu.menuInflater.inflate(R.menu.text_context_menu, popupMenu.menu)
            
            // 设置菜单项点击事件
            popupMenu.setOnMenuItemClickListener { item: MenuItem ->
                when (item.itemId) {
                    R.id.action_copy_text -> {
                        val text = textView.text.toString()
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("AI回复", text)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        true
                    }
                    R.id.action_share_text -> {
                        val text = textView.text.toString()
                        val shareIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        val chooserIntent = Intent.createChooser(shareIntent, "分享AI回复")
                        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(chooserIntent)
                        true
                    }
                    R.id.action_select_all -> {
                        textView.setSelectAllOnFocus(true)
                        textView.requestFocus()
                        true
                    }
                    R.id.action_clear_text -> {
                        textView.text = ""
                        true
                    }
                    else -> false
                }
            }
            
            popupMenu.show()
        } catch (e: Exception) {
            Log.e(TAG, "显示文本上下文菜单失败", e)
            // 如果菜单资源不存在，使用简单的Toast提示
            Toast.makeText(this, "长按功能：复制、分享、全选", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 显示AI预览（用户点击AI按钮时调用）
     */
    private fun showAIPreviewForClipboard(clipboardContent: String) {
        try {
            Log.d(TAG, "显示AI预览，剪贴板内容: $clipboardContent")
            
            // 获取主容器
            val mainContainer = islandContentView as? LinearLayout
            if (mainContainer == null) {
                Log.e(TAG, "主容器为空，无法显示AI预览")
                return
            }
            
            // 检查是否已经有AI预览容器
            val existingAIPreview = mainContainer.getChildAt(1) // AI预览容器是第二个子视图
            if (existingAIPreview != null) {
                Log.d(TAG, "AI预览已存在，移除旧预览")
                mainContainer.removeView(existingAIPreview)
            }
            
            // 创建新的AI预览容器
            val aiPreviewContainer = createAIPreviewContainer(clipboardContent)
            mainContainer.addView(aiPreviewContainer)
            
            // 显示提示
            Toast.makeText(this, "AI正在分析中...", Toast.LENGTH_SHORT).show()
            
            Log.d(TAG, "AI预览已添加到主容器")
            
        } catch (e: Exception) {
            Log.e(TAG, "显示AI预览失败", e)
            Toast.makeText(this, "显示AI预览失败", Toast.LENGTH_SHORT).show()
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
                Log.d(TAG, "关闭按钮被点击，切换到圆球状态")
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
        
        // 获取主题化的上下文
        val themedContext = getThemedContext()
        val isDarkMode = (themedContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        
        val aiContainer = LinearLayout(this).apply {
            id = View.generateViewId() // 使用动态ID
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
            
            // 根据主题设置背景
            background = GradientDrawable().apply {
                if (isDarkMode) {
                    setColor(Color.parseColor("#CC000000")) // 深色模式：半透明黑色背景
                    setStroke(1.dpToPx(), Color.parseColor("#60FFFFFF")) // 白色边框
                } else {
                    setColor(Color.parseColor("#CCFFFFFF")) // 浅色模式：半透明白色背景
                    setStroke(1.dpToPx(), Color.parseColor("#60000000")) // 黑色边框
                }
                cornerRadius = 12.dpToPx().toFloat()
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
            setColorFilter(resources.getColor(R.color.dynamic_island_send_icon_tint, themedContext.theme)) // 使用主题化颜色
            
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
        
        // AI提供商标签容器（支持多个配置好的AI服务）
        val aiProviderContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 4.dpToPx()
            }
        }
        
        // 获取配置好的AI服务
        val configuredAIServices = getConfiguredAIServices()
        Log.d(TAG, "找到 ${configuredAIServices.size} 个配置好的AI服务: $configuredAIServices")
        
        // 为每个配置好的AI服务创建标签
        configuredAIServices.forEachIndexed { index, aiService ->
            val isCurrentAI = aiService == currentAIProvider
            val aiProviderLabel = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (index > 0) leftMargin = 4.dpToPx()
                }
                text = if (isCurrentAI) "✓ $aiService" else aiService
                textSize = 10f
                setTextColor(if (isCurrentAI) {
                    if (isDarkMode) Color.parseColor("#FFFFFF") else Color.parseColor("#FFFFFF")
                } else {
                    if (isDarkMode) Color.parseColor("#4CAF50") else Color.parseColor("#2E7D32")
                })
                setPadding(6.dpToPx(), 3.dpToPx(), 6.dpToPx(), 3.dpToPx())
                background = GradientDrawable().apply {
                    if (isCurrentAI) {
                        // 当前AI使用更明显的背景色
                        setColor(if (isDarkMode) Color.parseColor("#4CAF50") else Color.parseColor("#4CAF50"))
                        setStroke(1.dpToPx(), if (isDarkMode) Color.parseColor("#FFFFFF") else Color.parseColor("#FFFFFF"))
                    } else {
                        // 其他AI使用淡色背景
                    setColor(if (isDarkMode) Color.parseColor("#204CAF50") else Color.parseColor("#E8F5E8"))
                    }
                    cornerRadius = 6.dpToPx().toFloat()
                }
                gravity = Gravity.CENTER
                
                // 添加点击事件，切换AI服务
                setOnClickListener {
                    Log.d(TAG, "用户点击AI标签: $aiService")
                    switchToAIService(aiService, clipboardContent)
                }
            }
            aiProviderContainer.addView(aiProviderLabel)
        }
        
        // AI回复内容区域（可延展，带滚动条）
        val aiResponseContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        // 创建滚动容器
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            // 设置滚动条样式
            isVerticalScrollBarEnabled = true
            scrollBarStyle = View.SCROLLBARS_OUTSIDE_OVERLAY
        }
        
        // AI回复文本（支持长按菜单）
        var aiPreviewText: TextView? = null
        aiPreviewText = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            text = "AI正在分析中..."
            textSize = 12f
            setTextColor(resources.getColor(R.color.dynamic_island_text_color, themedContext.theme))
            // 移除行数限制，让文本可以完全显示
            maxLines = Integer.MAX_VALUE
            setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 12.dpToPx())
            gravity = Gravity.START // 改为左对齐，更符合阅读习惯
            setLineSpacing(4.dpToPx().toFloat(), 1.3f) // 增加行间距
            
            // 设置背景 - 使用主题化颜色
            background = GradientDrawable().apply {
                setColor(resources.getColor(R.color.dynamic_island_preview_background, themedContext.theme))
                cornerRadius = 8.dpToPx().toFloat()
            }
            
            // 启用文本选择
            setTextIsSelectable(true)
            
            // 添加长按菜单
            setOnLongClickListener {
                Log.d(TAG, "AI回复文本被长按，显示操作菜单")
                showTextContextMenu(this)
                true
            }
            
            // 添加点击查看完整回复功能
            setOnClickListener {
                Log.d(TAG, "AI预览文本被点击，显示完整回复")
                showFullAIResponse(clipboardContent)
            }
        }
        
        // 将文本添加到滚动容器
        scrollView.addView(aiPreviewText)
        
        // 字体大小控制按钮容器
        val fontControlContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 8.dpToPx()
            }
        }
        
        // 字体缩小按钮
        val fontDecreaseButton = ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                32.dpToPx(),
                32.dpToPx()
            ).apply {
                rightMargin = 8.dpToPx()
            }
            setImageResource(android.R.drawable.ic_menu_edit)
            setColorFilter(Color.parseColor("#4CAF50"))
            background = GradientDrawable().apply {
                setColor(resources.getColor(R.color.dynamic_island_button_normal_background, themedContext.theme))
                shape = GradientDrawable.OVAL
            }
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = "缩小字体"
            
            setOnClickListener {
                Log.d(TAG, "字体缩小按钮被点击")
                decreaseFontSize(aiPreviewText)
            }
        }
        
        // 字体放大按钮
        val fontIncreaseButton = ImageButton(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                32.dpToPx(),
                32.dpToPx()
            ).apply {
                leftMargin = 8.dpToPx()
            }
            setImageResource(android.R.drawable.ic_menu_edit)
            setColorFilter(Color.parseColor("#4CAF50"))
            background = GradientDrawable().apply {
                setColor(resources.getColor(R.color.dynamic_island_button_normal_background, themedContext.theme))
                shape = GradientDrawable.OVAL
            }
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = "放大字体"
            
            setOnClickListener {
                Log.d(TAG, "字体放大按钮被点击")
                increaseFontSize(aiPreviewText)
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
                setColor(resources.getColor(R.color.dynamic_island_button_normal_background, themedContext.theme))
                shape = GradientDrawable.OVAL
            }
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            
            setOnClickListener {
                Log.d(TAG, "展开按钮被点击，显示完整回复")
                showFullAIResponse(clipboardContent)
            }
        }
        
        // 添加字体控制按钮到容器
        fontControlContainer.addView(fontDecreaseButton)
        fontControlContainer.addView(fontIncreaseButton)
        
        // 组装布局
        aiHeaderContainer.addView(aiIcon)
        aiHeaderContainer.addView(aiProviderContainer)
        
        aiResponseContainer.addView(scrollView)
        aiResponseContainer.addView(fontControlContainer)
        aiResponseContainer.addView(expandButton)
        
        aiContainer.addView(aiHeaderContainer)
        aiContainer.addView(aiResponseContainer)
        
        // 设置初始字体大小
        val initialFontSize = settingsManager.getAIFontSize()
        aiPreviewText.textSize = initialFontSize
        
        // 异步获取AI回复
        Log.d(TAG, "开始获取AI回复预览")
        getAIResponsePreview(clipboardContent, aiPreviewText!!)
        
        Log.d(TAG, "AI预览容器创建完成")
        return aiContainer
    }
    
    // AI相关变量
    private var currentAIProvider = "DeepSeek" // 默认AI提供商
    private val aiProviders: List<String>
        get() = getConfiguredAIServices() // 使用已配置API的AI服务

    /**
     * 增加AI回复字体大小
     */
    private fun increaseFontSize(textView: TextView) {
        val newSize = settingsManager.increaseAIFontSize()
        textView.textSize = newSize
        Log.d(TAG, "AI回复字体大小已增加到: $newSize")
        Toast.makeText(this, "字体大小: ${newSize.toInt()}sp", Toast.LENGTH_SHORT).show()
    }

    /**
     * 减少AI回复字体大小
     */
    private fun decreaseFontSize(textView: TextView) {
        val newSize = settingsManager.decreaseAIFontSize()
        textView.textSize = newSize
        Log.d(TAG, "AI回复字体大小已减少到: $newSize")
        Toast.makeText(this, "字体大小: ${newSize.toInt()}sp", Toast.LENGTH_SHORT).show()
    }
    
    /**
     * 获取配置好的AI服务列表（只返回已配置API密钥的AI服务）
     */
    private fun getConfiguredAIServices(): List<String> {
        val configuredServices = mutableListOf<String>()
        
        // 强制刷新AI引擎配置，确保Kimi被包含
        settingsManager.forceRefreshAIEngines()
        
        // 获取已启用的AI引擎（移到try块外）
            val enabledAIEngines = settingsManager.getEnabledAIEngines()
            Log.d(TAG, "已启用的AI引擎: $enabledAIEngines")
        
        try {
            // 首先添加临时专线（总是可用，无需API密钥）
            configuredServices.add("临时专线")
            Log.d(TAG, "添加临时专线到配置的AI服务列表")
            
            // 将AI引擎名称映射到显示名称和API密钥检查
            val aiEngineMapping = mapOf(
                "DeepSeek (API)" to Pair("DeepSeek", "deepseek_api_key"),
                "ChatGPT (Custom)" to Pair("ChatGPT", "chatgpt_api_key"),
                "Claude (Custom)" to Pair("Claude", "claude_api_key"),
                "Gemini" to Pair("Gemini", "gemini_api_key"),
                "智谱AI (Custom)" to Pair("智谱AI", "zhipu_ai_api_key"),
                "通义千问 (Custom)" to Pair("通义千问", "qianwen_api_key"),
                "文心一言 (Custom)" to Pair("文心一言", "wenxin_api_key"),
                "讯飞星火 (Custom)" to Pair("讯飞星火", "xinghuo_api_key"),
                "Kimi" to Pair("Kimi", "kimi_api_key")
            )
            
            // 根据已启用的AI引擎添加对应的显示名称，并验证API密钥
            enabledAIEngines.forEach { engineName ->
                Log.d(TAG, "处理已启用的AI引擎: $engineName")
                val mapping = aiEngineMapping[engineName]
                if (mapping != null) {
                    val (displayName, apiKeyName) = mapping
                    Log.d(TAG, "找到映射: $engineName -> $displayName, API密钥名: $apiKeyName")
                    
                    // 使用正确的方法获取API密钥
                    val apiKey = when (displayName) {
                        "DeepSeek" -> settingsManager.getDeepSeekApiKey()
                        "ChatGPT" -> settingsManager.getString("chatgpt_api_key", "") ?: ""
                        "Claude" -> settingsManager.getString("claude_api_key", "") ?: ""
                        "Gemini" -> settingsManager.getGeminiApiKey()
                        "智谱AI" -> settingsManager.getString("zhipu_ai_api_key", "") ?: ""
                        "通义千问" -> settingsManager.getQianwenApiKey()
                        "文心一言" -> settingsManager.getWenxinApiKey()
                        "讯飞星火" -> settingsManager.getString("xinghuo_api_key", "") ?: ""
                        "Kimi" -> {
                            val kimiKey = settingsManager.getKimiApiKey()
                            Log.d(TAG, "Kimi API密钥获取: ${if (kimiKey.isNotBlank()) "成功 (${kimiKey.length}字符)" else "失败"}")
                            kimiKey
                        }
                        else -> settingsManager.getString(apiKeyName, "") ?: ""
                    }
                    
                    Log.d(TAG, "检查 $displayName API密钥: ${if (apiKey.isNotBlank()) "已配置 (${apiKey.length}字符)" else "未配置"}")
                    
                    // 验证API密钥是否有效
                    if (isValidApiKey(apiKey, displayName)) {
                        configuredServices.add(displayName)
                        Log.d(TAG, "✅ $displayName API密钥已配置，添加到可用列表")
                    } else {
                        Log.d(TAG, "❌ $displayName API密钥未配置或无效，跳过")
                    }
                } else {
                    Log.d(TAG, "❌ 未找到AI引擎映射: $engineName")
                }
            }
            
            // 如果没有配置任何AI服务，返回默认的DeepSeek（如果它有API密钥）
            if (configuredServices.isEmpty()) {
                val deepSeekApiKey = settingsManager.getDeepSeekApiKey()
                if (isValidApiKey(deepSeekApiKey, "DeepSeek")) {
                configuredServices.add("DeepSeek")
                    Log.d(TAG, "使用默认DeepSeek（API密钥已配置）")
                } else {
                    Log.w(TAG, "没有可用的AI服务，所有AI都未配置API密钥")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "获取配置好的AI服务失败", e)
            // 尝试添加DeepSeek作为备用
            val deepSeekApiKey = settingsManager.getDeepSeekApiKey()
            if (isValidApiKey(deepSeekApiKey, "DeepSeek")) {
                configuredServices.add("DeepSeek")
            }
        }
        
        Log.d(TAG, "最终配置好的AI服务: $configuredServices")
        
        // 特别检查Kimi的状态
        if (configuredServices.contains("Kimi")) {
            Log.d(TAG, "✅ Kimi已成功添加到配置列表")
        } else {
            Log.w(TAG, "❌ Kimi未添加到配置列表，可能的原因：")
            Log.w(TAG, "1. Kimi未在已启用的AI引擎中")
            Log.w(TAG, "2. Kimi API密钥未配置或无效")
            Log.w(TAG, "3. Kimi映射失败")
            
            // 直接检查Kimi的配置状态
            val kimiEnabled = enabledAIEngines.contains("Kimi")
            val kimiApiKey = settingsManager.getKimiApiKey()
            val kimiValid = isValidApiKey(kimiApiKey, "Kimi")
            Log.w(TAG, "Kimi状态检查: 启用=$kimiEnabled, API密钥=${if (kimiApiKey.isNotBlank()) "已配置" else "未配置"}, 有效=$kimiValid")
        }
        
        return configuredServices
    }
    
    /**
     * 验证API密钥是否有效
     */
    private fun isValidApiKey(apiKey: String, aiName: String): Boolean {
        if (apiKey.isBlank()) {
            return false
        }

        // 根据不同的AI服务验证API密钥格式
        return when (aiName.lowercase()) {
            "deepseek" -> apiKey.startsWith("sk-") && apiKey.length >= 20
            "chatgpt" -> apiKey.startsWith("sk-") && apiKey.length >= 20
            "claude" -> apiKey.startsWith("sk-ant-") && apiKey.length >= 20
            "gemini" -> apiKey.length >= 20 // Google API密钥没有固定前缀
            "智谱ai", "智谱AI" -> apiKey.contains(".") && apiKey.length >= 20 // 智谱AI API密钥格式：xxxxx.xxxxx
            "文心一言" -> apiKey.length >= 10 // 百度API密钥
            "通义千问" -> apiKey.length >= 10 // 阿里云API密钥
            "讯飞星火" -> apiKey.length >= 10 // 讯飞API密钥
            "kimi" -> apiKey.length >= 10 // Kimi API密钥
            else -> apiKey.length >= 10
        }
    }
    
    /**
     * 切换到指定的AI服务并重新获取回复
     */
    private fun switchToAIService(aiService: String, clipboardContent: String) {
        Log.d(TAG, "切换到AI服务: $aiService")
        currentAIProvider = aiService
        
        // 显示切换提示
        Toast.makeText(this, "已切换到 $aiService", Toast.LENGTH_SHORT).show()
        
        // 重新创建AI预览容器
        recreateAIPreviewContainer(clipboardContent)
    }
    
    /**
     * 重新创建AI预览容器
     */
    private fun recreateAIPreviewContainer(clipboardContent: String) {
        try {
            // 找到AI预览容器
            val mainContainer = islandContentView as? LinearLayout
            if (mainContainer != null) {
                // 移除旧的AI预览容器
                val aiPreviewContainer = mainContainer.getChildAt(1) // AI预览容器是第二个子视图
                if (aiPreviewContainer != null) {
                    mainContainer.removeView(aiPreviewContainer)
                }
                
                // 创建新的AI预览容器
                val newAIPreviewContainer = createAIPreviewContainer(clipboardContent)
                mainContainer.addView(newAIPreviewContainer)
                
                Log.d(TAG, "AI预览容器已重新创建")
            }
        } catch (e: Exception) {
            Log.e(TAG, "重新创建AI预览容器失败", e)
        }
    }
    
    /**
     * 显示AI选择对话框
     */
    private fun showAISelectionDialog(clipboardContent: String) {
        try {
            val configuredAIServices = getConfiguredAIServices()
            if (configuredAIServices.isEmpty()) {
                Toast.makeText(this, "没有配置任何AI服务，请先在设置中配置API", Toast.LENGTH_LONG).show()
                return
            }
            
            val items = configuredAIServices.toTypedArray()
            val checkedItem = configuredAIServices.indexOf(currentAIProvider).coerceAtLeast(0)
            
            Log.d(TAG, "显示AI选择对话框，可用AI服务: $configuredAIServices")
            Log.d(TAG, "当前选择的AI: $currentAIProvider, 选中索引: $checkedItem")
            
            // 使用Application Context避免Service context问题
            val context = applicationContext
            AlertDialog.Builder(ContextThemeWrapper(context, android.R.style.Theme_Material_Dialog))
                .setTitle("选择AI助手")
                .setSingleChoiceItems(items, checkedItem) { dialog, which ->
                    currentAIProvider = configuredAIServices[which]
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
            val configuredAIServices = getConfiguredAIServices()
            if (configuredAIServices.isNotEmpty()) {
                val currentIndex = configuredAIServices.indexOf(currentAIProvider)
                val nextIndex = (currentIndex + 1) % configuredAIServices.size
                currentAIProvider = configuredAIServices[nextIndex]
            refreshAIResponse(clipboardContent)
            Toast.makeText(this, "已切换到 $currentAIProvider", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "没有可用的AI服务", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    /**
     * 获取AI回复预览
     */
    private fun getAIResponsePreview(content: String, textView: TextView?) {
        // 如果textView为null，说明是切换AI服务，需要重新创建预览
        if (textView == null) {
            Log.d(TAG, "textView为null，跳过预览更新")
            return
        }
        
        // 设置加载状态
        Handler(Looper.getMainLooper()).post {
            textView.text = "AI正在分析中..."
        }
        
        // 使用当前选择的AI服务
        val selectedService = currentAIProvider
        
        // 将显示名称映射到AIServiceType
        val serviceType = when (selectedService) {
            "临时专线" -> AIServiceType.TEMP_SERVICE
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
                        // 不再限制预览长度，让文本完全显示
                        textView.text = newText
                        
                        // 自动滚动到底部，显示最新内容
                        if (textView.parent is ScrollView) {
                            val scrollView = textView.parent as ScrollView
                            scrollView.post {
                                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
                            }
                        }
                    }
                }
                
                override fun onComplete(fullResponse: String) {
                    uiHandler.post {
                        // 显示完整回复，不限制长度
                        textView.text = fullResponse
                        Log.d(TAG, "AI回复预览完成")
                        
                        // 滚动到底部
                        if (textView.parent is ScrollView) {
                            val scrollView = textView.parent as ScrollView
                            scrollView.post {
                                scrollView.fullScroll(ScrollView.FOCUS_DOWN)
                            }
                        }
                        
                        // 保存到聊天历史
                        saveToChatHistory(content, fullResponse, serviceType)
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
        try {
            Log.d(TAG, "刷新AI回复，当前AI提供商: $currentAIProvider")
            
            // 验证当前AI提供商是否在已配置的AI服务中
            val configuredAIServices = getConfiguredAIServices()
            if (!configuredAIServices.contains(currentAIProvider)) {
                Log.w(TAG, "当前AI提供商 $currentAIProvider 不在已配置的AI服务中，切换到第一个可用的AI")
                currentAIProvider = configuredAIServices.firstOrNull() ?: "DeepSeek"
                Log.d(TAG, "已切换到: $currentAIProvider")
            }
            
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
        } catch (e: Exception) {
            Log.e(TAG, "刷新AI回复失败", e)
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
            val selectedServices = aiServiceSelectionManager.getSelectedServices()
            val selectedService = if (selectedServices.isNotEmpty()) selectedServices.first() else "DeepSeek"
            
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
                            
                            // 保存到聊天历史
                            saveToChatHistory(content, fullResponse, serviceType)
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
     * 🎯 处理app图标点击事件 - 新逻辑：优先使用输入框内容，自动粘贴获取剪贴板数据
     */
    private fun handleAppIconClick(appInfo: AppInfo, clipboardContent: String) {
        try {
            Log.d(TAG, "🎯 应用图标点击: ${appInfo.label} - 开始获取搜索内容...")

            // 立即缩小到球状态，而不是完全收起
            hideContentAndSwitchToBall()

            // 🚨 核心改进：优先获取输入框内容，然后尝试自动粘贴获取剪贴板数据
            val inputText = searchInput?.text?.toString()?.trim()
            val finalContent = when {
                !inputText.isNullOrEmpty() -> {
                    Log.d(TAG, "✅ 使用输入框内容: ${inputText.take(50)}...")
                    // 将输入框内容复制到剪贴板，确保app能获取到
                    copyTextToClipboard(inputText)
                    inputText
                }
                else -> {
                    // 输入框为空，尝试自动粘贴获取剪贴板数据
                    val latestClipboardContent = getClipboardContentByAutoPaste()
                    when {
                        !latestClipboardContent.isNullOrEmpty() -> {
                            Log.d(TAG, "✅ 自动粘贴获取成功: ${latestClipboardContent.take(50)}...")
                            latestClipboardContent
                        }
                        !clipboardContent.isNullOrEmpty() -> {
                            Log.d(TAG, "✅ 使用展开时的剪贴板内容: ${clipboardContent.take(50)}...")
                            clipboardContent
                        }
                        else -> {
                            Log.d(TAG, "⚠️ 无剪贴板内容，使用默认搜索")
                            "搜索内容"
                        }
                    }
                }
            }

            Log.d(TAG, "🔄 最终搜索内容: ${finalContent.take(50)}...")

            // 特殊处理：URL链接使用DualFloatingWebViewService
            val contentType = contentAnalyzer.analyzeContent(finalContent)
            if (contentType == ClipboardContentType.URL) {
                Log.d(TAG, "🌐 检测到URL链接，启动DualFloatingWebViewService")
                startDualFloatingWebViewService(finalContent)
                return
            }

            // 判断是否支持URL Scheme搜索
            if (!appInfo.urlScheme.isNullOrEmpty()) {
                // 支持URL Scheme，使用最新获取的内容构建搜索URL
                val encodedContent = Uri.encode(finalContent)
                Log.d(TAG, "🔗 构建URL Scheme搜索: ${appInfo.urlScheme} - 内容: ${encodedContent.take(50)}...")
                val intent = when (appInfo.urlScheme) {
                    // 社交类
                    "weixin" -> {
                        // 微信不支持搜索URL Scheme，直接降级到普通启动
                        Log.d(TAG, "微信不支持搜索URL Scheme，降级到普通启动")
                        null
                    }
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
                }
                
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    
                    try {
                        startActivity(intent)
                        Toast.makeText(this, "已在${appInfo.label}中搜索: ${finalContent.take(30)}...", Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "🚀 通过URL Scheme跳转到${appInfo.label}搜索: ${intent.data}")
                    } catch (e: ActivityNotFoundException) {
                        // URL Scheme不可用，降级到普通启动
                        Log.w(TAG, "URL Scheme失败，降级到普通启动: ${appInfo.urlScheme}")
                        launchAppForManualPaste(appInfo, finalContent)
                    } catch (e: Exception) {
                        Log.e(TAG, "启动URL Scheme失败", e)
                        launchAppForManualPaste(appInfo, finalContent)
                    }
                } else {
                    // Intent为null，直接降级到普通启动
                    Log.w(TAG, "Intent为null，降级到普通启动: ${appInfo.urlScheme}")
                    launchAppForManualPaste(appInfo, finalContent)
                }
            } else {
                // 不支持URL Scheme，启动app让用户手动粘贴
                launchAppForManualPaste(appInfo, finalContent)
            }

        } catch (e: Exception) {
            Log.e(TAG, "处理app图标点击失败", e)
            Toast.makeText(this, "启动应用失败", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 🎯 核心方法：通过自动粘贴获取剪贴板内容
     * 为应用图标点击专门设计的剪贴板获取方法
     */
    private fun getClipboardContentByAutoPaste(): String? {
        return try {
            Log.d(TAG, "🔄 [自动粘贴] 开始为应用图标点击获取剪贴板内容...")

            // 方法1：直接获取剪贴板内容（最可靠）
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboardManager.hasPrimaryClip()) {
                val clipData = clipboardManager.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val content = clipData.getItemAt(0)?.text?.toString()
                    if (!content.isNullOrEmpty()) {
                        Log.d(TAG, "✅ [自动粘贴] 直接获取剪贴板成功: ${content.take(50)}...")
                        return content
                    }
                }
            }

            // 方法2：通过隐藏EditText模拟粘贴（备选方案）
            Log.d(TAG, "🔄 [自动粘贴] 直接获取失败，尝试模拟粘贴...")
            
            // 创建隐藏的EditText用于接收粘贴内容
            val hiddenEditText = EditText(this).apply {
                layoutParams = ViewGroup.LayoutParams(1, 1)
                alpha = 0f // 完全透明
                isFocusable = true
                isFocusableInTouchMode = true
                setText("") // 确保初始为空
            }

            // 创建窗口参数（隐藏在屏幕外）
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val params = WindowManager.LayoutParams(
                1, 1, // 最小尺寸
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                x = -3000 // 移到屏幕外
                y = -3000
            }

            // 添加到窗口管理器
            windowManager.addView(hiddenEditText, params)
            Log.d(TAG, "✅ [自动粘贴] 隐藏输入框已创建")

            // 等待视图完全加载
            Thread.sleep(100)

            // 🚨 执行自动粘贴操作
            val pastedContent = performAutoPasteOperation(hiddenEditText)

            // 清理资源
            try {
                windowManager.removeView(hiddenEditText)
                Log.d(TAG, "✅ [自动粘贴] 隐藏输入框已清理")
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ [自动粘贴] 清理隐藏输入框失败", e)
            }

            if (!pastedContent.isNullOrEmpty()) {
                Log.d(TAG, "✅ [自动粘贴] 模拟粘贴成功获取内容，长度: ${pastedContent.length}")
                return pastedContent
            } else {
                Log.d(TAG, "❌ [自动粘贴] 所有方法都未获取到内容")
                return null
            }

        } catch (e: Exception) {
            Log.e(TAG, "❌ [自动粘贴] 自动粘贴操作异常", e)
            null
        }
    }

    /**
     * 🎯 执行自动粘贴操作
     */
    private fun performAutoPasteOperation(editText: EditText): String? {
        return try {
            Log.d(TAG, "🔄 [粘贴操作] 开始执行自动粘贴...")

            // 方法1：通过InputConnection模拟粘贴
            val inputConnection = editText.onCreateInputConnection(EditorInfo())
            if (inputConnection != null) {
                Log.d(TAG, "🔄 [粘贴操作] 尝试通过InputConnection自动粘贴...")

                // 模拟粘贴操作
                val pasteResult = inputConnection.performContextMenuAction(android.R.id.paste)
                Log.d(TAG, "🔄 [粘贴操作] 自动粘贴操作结果: $pasteResult")

                // 等待粘贴完成
                Thread.sleep(300)

                val pastedText = editText.text.toString()
                if (pastedText.isNotEmpty()) {
                    Log.d(TAG, "✅ [粘贴操作] InputConnection自动粘贴成功: ${pastedText.take(50)}...")
                    return pastedText
                }
            }

            // 方法2：通过ClipboardManager直接获取（备选）
            Log.d(TAG, "🔄 [粘贴操作] InputConnection失败，尝试直接获取剪贴板...")
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboardManager.hasPrimaryClip()) {
                val clipData = clipboardManager.primaryClip
                if (clipData != null && clipData.itemCount > 0) {
                    val content = clipData.getItemAt(0)?.text?.toString()
                    if (!content.isNullOrEmpty()) {
                        Log.d(TAG, "✅ [粘贴操作] 直接获取剪贴板成功: ${content.take(50)}...")
                        return content
                    }
                }
            }

            Log.d(TAG, "❌ [粘贴操作] 所有自动粘贴方法都失败")
            null

        } catch (e: Exception) {
            Log.e(TAG, "❌ [粘贴操作] 执行自动粘贴操作异常", e)
            null
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
    private fun launchAppForManualPaste(appInfo: AppInfo, searchContent: String? = null) {
        try {
            // 如果有搜索内容，先复制到剪贴板
            if (!searchContent.isNullOrEmpty()) {
                copyTextToClipboard(searchContent)
                Log.d(TAG, "已将搜索内容复制到剪贴板: ${searchContent.take(50)}...")
            }
            
            val intent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                
                val message = if (!searchContent.isNullOrEmpty()) {
                    "已启动${appInfo.label}，内容已复制到剪贴板，可直接粘贴搜索"
                } else {
                    "已启动${appInfo.label}，请手动粘贴内容"
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
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
    
    // isValidClipboardContent方法已移除，改用无障碍服务处理
    
    // isUserGeneratedClipboard方法已移除，改用无障碍服务处理
    
    // getCurrentClipboardContent和updateLastClipboardContent方法已移除，改用无障碍服务处理
    
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
     * 清理剪贴板广播接收器
     */
    private fun cleanupClipboardBroadcastReceiver() {
        try {
            clipboardBroadcastReceiver?.let { receiver ->
                LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
            }
            clipboardBroadcastReceiver = null
            Log.d(TAG, "剪贴板广播接收器已清理")
        } catch (e: Exception) {
            Log.e(TAG, "清理剪贴板广播接收器失败", e)
        }
    }
    
    /**
     * 显示AI助手面板
     */
    private fun showAIAssistantPanel() {
        if (aiAssistantPanelView != null) {
            Log.d(TAG, "AI助手面板已存在，跳过创建")
            return
        }
        
        // 检查windowContainerView是否存在
        if (windowContainerView == null) {
            Log.e(TAG, "windowContainerView为null，无法显示AI助手面板")
            Toast.makeText(this, "窗口容器未初始化，请重启灵动岛服务", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            Log.d(TAG, "开始创建AI助手面板")
            val themedContext = ContextThemeWrapper(getThemedContext(), R.style.Theme_FloatingWindow)
            aiAssistantPanelView = LayoutInflater.from(themedContext).inflate(R.layout.ai_assistant_panel, null)
            
            if (aiAssistantPanelView == null) {
                Log.e(TAG, "无法加载AI助手面板布局")
                Toast.makeText(this, "无法加载AI助手面板布局", Toast.LENGTH_SHORT).show()
                return
            }
            
            Log.d(TAG, "AI助手面板布局加载成功")
            
            // 设置面板参数
            val islandHeight = getIslandActualHeight()
            val topMargin = statusBarHeight + islandHeight + 16.dpToPx()
            val panelParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                this.topMargin = topMargin
            }
            
            Log.d(TAG, "AI助手面板参数设置完成: topMargin=$topMargin, islandHeight=$islandHeight, statusBarHeight=$statusBarHeight")
            
            // 设置AI助手面板的交互
            setupAIAssistantPanelInteractions()
            
            // 更新窗口参数以允许焦点和输入法
            updateWindowParamsForInput()
            
            // 添加到窗口并显示动画
            try {
                windowContainerView?.addView(aiAssistantPanelView, panelParams)
                Log.d(TAG, "AI助手面板已添加到窗口容器")
            } catch (e: Exception) {
                Log.e(TAG, "添加AI助手面板到窗口容器失败", e)
                aiAssistantPanelView = null
                Toast.makeText(this, "无法添加面板到窗口: ${e.message}", Toast.LENGTH_SHORT).show()
                return
            }
            
            aiAssistantPanelView?.apply {
                alpha = 0f
                translationY = -100f
                animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(350)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .withEndAction {
                        // 面板显示完成，自动激活输入法
                        Log.d(TAG, "AI助手面板显示完成，激活输入法")
                        val aiInputText = aiAssistantPanelView?.findViewById<EditText>(R.id.search_input)
                        aiInputText?.let { inputField ->
                            // 使用专门的悬浮窗输入法激活方法
                            activateInputMethodForFloatingWindow(inputField)
                        }
                    }
                    .start()
            }
            
            Log.d(TAG, "AI助手面板已显示并开始动画")
            
            // 额外的延迟激活输入法，确保面板完全显示后激活
            uiHandler.postDelayed({
                val aiInputText = aiAssistantPanelView?.findViewById<EditText>(R.id.search_input)
                aiInputText?.let { inputField ->
                    Log.d(TAG, "延迟激活AI助手面板输入法")
                    activateInputMethodForFloatingWindow(inputField)
                }
            }, 500)
            
        } catch (e: Exception) {
            Log.e(TAG, "显示AI助手面板失败", e)
            e.printStackTrace()
            aiAssistantPanelView = null
            Toast.makeText(this, "显示AI助手面板失败: ${e.message}", Toast.LENGTH_LONG).show()
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
            val btnClosePanel = aiAssistantPanelView?.findViewById<ImageButton>(R.id.close_panel_button)
            btnClosePanel?.setOnClickListener {
                hideAIAssistantPanel()
            }
            
            // AI服务多选界面
            setupAIServiceMultiSelect()
            
            // AI输入框
            val aiInputText = aiAssistantPanelView?.findViewById<EditText>(R.id.search_input)
            val aiResponseText = aiAssistantPanelView?.findViewById<TextView>(R.id.ai_status_text)
            
            // 设置AI输入框的焦点和输入法激活
            aiInputText?.let { inputField ->
                // 设置焦点变化监听器
                inputField.setOnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        // 当获得焦点时，强制显示输入法
                        uiHandler.postDelayed({
                            // 使用专门的悬浮窗输入法激活方法
                            activateInputMethodForFloatingWindow(inputField)
                        }, 100)
                    }
                }
                
                // 设置点击监听器，确保点击时显示输入法
                inputField.setOnClickListener {
                    inputField.requestFocus()
                    uiHandler.postDelayed({
                        // 使用专门的悬浮窗输入法激活方法
                        activateInputMethodForFloatingWindow(inputField)
                    }, 100)
                }
            }
            
            
            // 切换AI选项折叠/展开按钮
            val btnToggleAiOptions = aiAssistantPanelView?.findViewById<ImageButton>(R.id.settings_button)
            val aiOptionsContainer = aiAssistantPanelView?.findViewById<LinearLayout>(R.id.ai_engines_container)
            var isAiOptionsExpanded = false
            
            btnToggleAiOptions?.setOnClickListener {
                isAiOptionsExpanded = !isAiOptionsExpanded
                
                // 添加旋转动画
                val rotation = if (isAiOptionsExpanded) 180f else 0f
                btnToggleAiOptions.animate()
                    .rotation(rotation)
                    .setDuration(300)
                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                    .start()
                
                if (isAiOptionsExpanded) {
                    aiOptionsContainer?.visibility = View.VISIBLE
                    // 添加淡入动画
                    aiOptionsContainer?.alpha = 0f
                    aiOptionsContainer?.animate()
                        ?.alpha(1f)
                        ?.setDuration(300)
                        ?.setInterpolator(android.view.animation.DecelerateInterpolator())
                        ?.start()
                } else {
                    // 添加淡出动画
                    aiOptionsContainer?.animate()
                        ?.alpha(0f)
                        ?.setDuration(200)
                        ?.setInterpolator(android.view.animation.AccelerateInterpolator())
                        ?.withEndAction {
                            aiOptionsContainer?.visibility = View.GONE
                        }
                        ?.start()
                }
            }
            
            // 发送消息按钮
            val btnSendAiMessage = aiAssistantPanelView?.findViewById<MaterialButton>(R.id.voice_input_button)
            btnSendAiMessage?.setOnClickListener {
                val query = aiInputText?.text?.toString()?.trim()
                if (!query.isNullOrEmpty()) {
                    sendAIMessageToMultipleServices(query)
                    aiInputText?.setText("") // 清空输入框
                }
            }
            
            // 发送按钮长按功能
            btnSendAiMessage?.setOnLongClickListener {
                val query = aiInputText?.text?.toString()?.trim()
                if (!query.isNullOrEmpty()) {
                    // 显示确认对话框
                    android.app.AlertDialog.Builder(this)
                        .setTitle("清空输入")
                        .setMessage("确定要清空输入框吗？")
                        .setPositiveButton("确定") { _, _ ->
                            aiInputText?.setText("")
                        }
                        .setNegativeButton("取消", null)
                        .show()
                }
                true // 返回true表示消费了长按事件
            }
            
            // AI配置按钮
            val btnAiConfig = aiAssistantPanelView?.findViewById<MaterialButton>(R.id.settings_button)
            btnAiConfig?.setOnClickListener {
                // 跳转到API密钥配置页面
                openApiKeyConfigPage()
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
            val btnFoldResponse = aiAssistantPanelView?.findViewById<ImageButton>(R.id.history_button)
            btnFoldResponse?.setOnClickListener {
                toggleResponseFold()
            }
            
            
            Log.d(TAG, "AI助手面板交互设置完成")
        } catch (e: Exception) {
            Log.e(TAG, "设置AI助手面板交互失败", e)
        }
    }
    
    
    /**
     * 添加AI回复卡片
     */
    private fun addAIResponseCard(aiName: String, response: String, query: String) {
        try {
            val responseContainer = aiAssistantPanelView?.findViewById<LinearLayout>(R.id.ai_engines_container)
            if (responseContainer != null) {
                // 创建新的AI回复卡片
                val newCard = createAIResponseCard(aiName, response, query)
                responseContainer.addView(newCard)
                
                // 滚动到最新添加的卡片
                val scrollContainer = aiAssistantPanelView?.findViewById<HorizontalScrollView>(R.id.ai_engines_container)
                scrollContainer?.post {
                    scrollContainer.fullScroll(HorizontalScrollView.FOCUS_RIGHT)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "添加AI回复卡片失败", e)
        }
    }
    
    /**
     * 创建AI回复卡片
     */
    private fun createAIResponseCard(aiName: String, response: String, query: String): MaterialCardView {
        // 使用MaterialComponents主题创建MaterialCardView
        val contextThemeWrapper = ContextThemeWrapper(this, com.google.android.material.R.style.Theme_MaterialComponents_Light)
        val context = contextThemeWrapper
        val card = MaterialCardView(context)
        
        // 设置卡片参数
        val layoutParams = LinearLayout.LayoutParams(
            280.dpToPx(), // 280dp宽度
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        layoutParams.marginEnd = 12.dpToPx() // 12dp右边距
        card.layoutParams = layoutParams
        
        // 设置卡片样式
        card.radius = 12.dpToPx().toFloat()
        card.cardElevation = 1f
        card.setCardBackgroundColor(getColor(R.color.ai_assistant_ai_bubble_light))
        card.strokeColor = getColor(R.color.ai_assistant_border_light)
        card.strokeWidth = 1.dpToPx()
        
        // 创建主容器
        val mainContainer = LinearLayout(context)
        mainContainer.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        mainContainer.orientation = LinearLayout.VERTICAL
        
        // 创建副标题区域
        val subtitleContainer = LinearLayout(context)
        subtitleContainer.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        subtitleContainer.orientation = LinearLayout.VERTICAL
        subtitleContainer.setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 8.dpToPx())
        subtitleContainer.setBackgroundColor(getColor(R.color.ai_assistant_ai_bubble_light))
        
        // AI名称
        val aiNameText = TextView(context)
        aiNameText.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        aiNameText.text = aiName
        aiNameText.setTextColor(getColor(R.color.ai_assistant_primary_light))
        aiNameText.textSize = 12f
        aiNameText.typeface = android.graphics.Typeface.DEFAULT_BOLD
        
        // 回复时间
        val timeText = TextView(context)
        timeText.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val currentTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        timeText.text = "回复时间: $currentTime"
        timeText.setTextColor(getColor(R.color.ai_assistant_text_secondary_light))
        timeText.textSize = 10f
        
        // 最后回复的问题
        val queryText = TextView(context)
        queryText.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        queryText.text = "问题: $query"
        queryText.setTextColor(getColor(R.color.ai_assistant_text_secondary_light))
        queryText.textSize = 10f
        queryText.maxLines = 2
        queryText.ellipsize = android.text.TextUtils.TruncateAt.END
        
        subtitleContainer.addView(aiNameText)
        subtitleContainer.addView(timeText)
        subtitleContainer.addView(queryText)
        
        // 创建回复内容区域
        val scrollView = ScrollView(context)
        val scrollViewParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0
        )
        scrollViewParams.weight = 1f
        scrollView.layoutParams = scrollViewParams
        scrollView.isVerticalScrollBarEnabled = true
        scrollView.setPadding(12.dpToPx(), 0, 12.dpToPx(), 12.dpToPx())
        
        val textView = TextView(context)
        textView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        textView.text = response
        textView.setTextColor(getColor(R.color.ai_assistant_ai_text_light))
        textView.textSize = 13f
        textView.setLineSpacing(4.dpToPx().toFloat(), 1f)
        textView.setTextIsSelectable(true)
        
        scrollView.addView(textView)
        
        // 组装卡片
        mainContainer.addView(subtitleContainer)
        mainContainer.addView(scrollView)
        card.addView(mainContainer)
        
        return card
    }
    
    /**
     * 清除所有AI回复卡片（隐藏默认卡片）
     */
    private fun clearAIResponseCards() {
        try {
            val responseContainer = aiAssistantPanelView?.findViewById<LinearLayout>(R.id.ai_engines_container)
            if (responseContainer != null) {
                // 移除所有动态创建的卡片
                val childCount = responseContainer.childCount
                for (i in childCount - 1 downTo 0) {
                    responseContainer.removeViewAt(i)
                }
                
                // 隐藏默认卡片
                val defaultCard = aiAssistantPanelView?.findViewById<MaterialCardView>(R.id.ai_engines_container)
                defaultCard?.visibility = View.GONE
            }
            
            // 清除AI名称标签
            val tabsContainer = aiAssistantPanelView?.findViewById<LinearLayout>(R.id.ai_engines_container)
            val tabsScrollView = aiAssistantPanelView?.findViewById<HorizontalScrollView>(R.id.ai_engines_container)
            if (tabsContainer != null && tabsScrollView != null) {
                tabsContainer.removeAllViews()
                aiNameTabs.clear()
                tabsScrollView.visibility = View.GONE
            }
        } catch (e: Exception) {
            Log.e(TAG, "清除AI回复卡片失败", e)
        }
    }
    
    
    /**
     * 发送消息到多个AI服务
     */
    private fun sendAIMessageToMultipleServices(query: String) {
        try {
            // 获取当前选择的AI服务列表
            val selectedAIServices = getSelectedAIServices()
            
            if (selectedAIServices.isEmpty()) {
                Toast.makeText(this, "请先选择AI服务", Toast.LENGTH_SHORT).show()
                return
            }
            
            // 清除之前的回复
            clearAIResponseCards()
            
            // 更新AI名称标签栏
            updateAINameTabs(selectedAIServices)
            
            // 为每个选中的AI服务创建独立的回复卡片
            selectedAIServices.forEach { aiService ->
                createAILoadingCard(aiService, query)
                sendAIMessageToServiceAsync(query, aiService)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "发送消息到多个AI服务失败", e)
            Toast.makeText(this, "发送失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 获取当前选择的AI服务列表
     */
    private fun getSelectedAIServices(): List<String> {
        return aiServiceSelectionManager.getSelectedServices()
    }
    
    /**
     * 创建AI加载卡片
     */
    private fun createAILoadingCard(aiService: String, query: String): MaterialCardView {
        // 使用MaterialComponents主题创建MaterialCardView
        val contextThemeWrapper = ContextThemeWrapper(this, com.google.android.material.R.style.Theme_MaterialComponents_Light)
        val card = MaterialCardView(contextThemeWrapper)
        
        // 设置卡片参数
        val layoutParams = LinearLayout.LayoutParams(
            280.dpToPx(), // 280dp宽度
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        layoutParams.marginEnd = 12.dpToPx() // 12dp右边距
        card.layoutParams = layoutParams
        
        // 设置卡片样式
        card.radius = 12.dpToPx().toFloat()
        card.cardElevation = 1f
        card.setCardBackgroundColor(getColor(R.color.ai_assistant_ai_bubble_light))
        card.strokeColor = getColor(R.color.ai_assistant_border_light)
        card.strokeWidth = 1.dpToPx()
        
        // 设置唯一ID用于后续更新
        card.id = View.generateViewId()
        
        // 创建主容器
        val mainContainer = LinearLayout(contextThemeWrapper)
        mainContainer.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        mainContainer.orientation = LinearLayout.VERTICAL
        
        // 创建副标题区域
        val subtitleContainer = LinearLayout(contextThemeWrapper)
        subtitleContainer.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        subtitleContainer.orientation = LinearLayout.VERTICAL
        subtitleContainer.setPadding(12.dpToPx(), 12.dpToPx(), 12.dpToPx(), 8.dpToPx())
        subtitleContainer.setBackgroundColor(getColor(R.color.ai_assistant_ai_bubble_light))
        
        // AI名称
        val aiNameText = TextView(contextThemeWrapper)
        aiNameText.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        aiNameText.text = aiService
        aiNameText.setTextColor(getColor(R.color.ai_assistant_primary_light))
        aiNameText.textSize = 12f
        aiNameText.typeface = android.graphics.Typeface.DEFAULT_BOLD
        
        // 回复时间
        val timeText = TextView(contextThemeWrapper)
        timeText.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val currentTime = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        timeText.text = "开始时间: $currentTime"
        timeText.setTextColor(getColor(R.color.ai_assistant_text_secondary_light))
        timeText.textSize = 10f
        
        // 问题
        val queryText = TextView(contextThemeWrapper)
        queryText.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        queryText.text = "问题: $query"
        queryText.setTextColor(getColor(R.color.ai_assistant_text_secondary_light))
        queryText.textSize = 10f
        queryText.maxLines = 2
        queryText.ellipsize = android.text.TextUtils.TruncateAt.END
        
        subtitleContainer.addView(aiNameText)
        subtitleContainer.addView(timeText)
        subtitleContainer.addView(queryText)
        
        // 创建加载状态区域
        val loadingContainer = LinearLayout(contextThemeWrapper)
        val loadingParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0
        )
        loadingParams.weight = 1f
        loadingContainer.layoutParams = loadingParams
        loadingContainer.orientation = LinearLayout.VERTICAL
        loadingContainer.gravity = Gravity.CENTER
        loadingContainer.setPadding(12.dpToPx(), 20.dpToPx(), 12.dpToPx(), 20.dpToPx())
        
        // 加载进度条
        val progressBar = ProgressBar(contextThemeWrapper, null, android.R.attr.progressBarStyle)
        progressBar.isIndeterminate = true
        progressBar.layoutParams = LinearLayout.LayoutParams(
            32.dpToPx(),
            32.dpToPx()
        )
        
        // 加载文本
        val loadingText = TextView(contextThemeWrapper)
        loadingText.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        loadingText.text = "$aiService 正在思考中..."
        loadingText.setTextColor(getColor(R.color.ai_assistant_text_secondary_light))
        loadingText.textSize = 12f
        loadingText.gravity = Gravity.CENTER
        
        loadingContainer.addView(progressBar)
        loadingContainer.addView(loadingText)
        
        // 创建回复内容区域（初始隐藏）
        val scrollView = ScrollView(contextThemeWrapper)
        val scrollViewParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0
        )
        scrollViewParams.weight = 1f
        scrollView.layoutParams = scrollViewParams
        scrollView.isVerticalScrollBarEnabled = true
        scrollView.setPadding(12.dpToPx(), 0, 12.dpToPx(), 12.dpToPx())
        scrollView.visibility = View.GONE
        
        val textView = TextView(contextThemeWrapper)
        textView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        textView.setTextColor(getColor(R.color.ai_assistant_ai_text_light))
        textView.textSize = 13f
        textView.setLineSpacing(4.dpToPx().toFloat(), 1f)
        textView.setTextIsSelectable(true)
        
        scrollView.addView(textView)
        
        // 组装卡片
        mainContainer.addView(subtitleContainer)
        mainContainer.addView(loadingContainer)
        mainContainer.addView(scrollView)
        card.addView(mainContainer)
        
        // 添加到回复容器
        val responseContainer = aiAssistantPanelView?.findViewById<LinearLayout>(R.id.ai_engines_container)
        responseContainer?.addView(card)
        
        // 滚动到最新添加的卡片
        val scrollContainer = aiAssistantPanelView?.findViewById<HorizontalScrollView>(R.id.ai_engines_container)
        scrollContainer?.post {
            scrollContainer.fullScroll(HorizontalScrollView.FOCUS_RIGHT)
        }
        
        return card
    }
    
    /**
     * 异步发送消息到指定AI服务
     */
    // 用于存储每个AI服务的累积回复内容
    private val aiResponseAccumulator = mutableMapOf<String, StringBuilder>()
    
    // 用于存储AI名称标签
    private val aiNameTabs = mutableMapOf<String, MaterialButton>()
    private var currentSelectedAiTab: String? = null
    
    private fun sendAIMessageToServiceAsync(query: String, aiService: String) {
        Log.d(TAG, "开始发送消息到AI服务: $aiService, 问题: $query")
        
        // 初始化累积器
        aiResponseAccumulator[aiService] = StringBuilder()
        
        // 显示连接状态
        uiHandler.post {
            updateAIResponseCard(aiService, "正在连接${aiService}...", false)
        }
        
        Thread {
            try {
                
                // 将显示名称映射到AIServiceType
                val serviceType = when (aiService) {
                    "临时专线" -> AIServiceType.TEMP_SERVICE
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
                
                Log.d(TAG, "AI服务类型映射: $aiService -> $serviceType")
                
                // 创建AI API管理器
                val aiApiManager = AIApiManager(this)
                
                Log.d(TAG, "开始调用AI API: $aiService")
                
                // 调用真实API
                aiApiManager.sendMessage(
                    serviceType = serviceType,
                    message = query,
                    conversationHistory = emptyList(),
                    callback = object : AIApiManager.StreamingCallback {
                        override fun onChunkReceived(chunk: String) {
                            Log.d(TAG, "收到${aiService}的流式回复: ${chunk.length}字符")
                            uiHandler.post {
                                // 累积流式回复内容
                                aiResponseAccumulator[aiService]?.append(chunk)
                                val accumulatedContent = aiResponseAccumulator[aiService]?.toString() ?: ""
                                updateAIResponseCard(aiService, accumulatedContent, false)
                            }
                        }
                        
                        override fun onComplete(fullResponse: String) {
                            Log.d(TAG, "${aiService}回复完成: ${fullResponse.length}字符")
                            uiHandler.post {
                                // 格式化完整回复
                                val formattedResponse = formatMarkdownResponse(fullResponse)
                                updateAIResponseCard(aiService, formattedResponse, true)
                                // 清理累积器
                                aiResponseAccumulator.remove(aiService)
                                // 保存到聊天历史
                                saveToChatHistory(query, fullResponse, serviceType)
                            }
                        }
                        
                        override fun onError(error: String) {
                            Log.e(TAG, "${aiService}回复错误: $error")
                            uiHandler.post {
                                updateAIResponseCard(aiService, "❌ 错误：$error\n\n请检查API密钥配置是否正确。", true)
                                // 清理累积器
                                aiResponseAccumulator.remove(aiService)
                            }
                        }
                    }
                )
                
            } catch (e: Exception) {
                Log.e(TAG, "发送消息到${aiService}失败", e)
                uiHandler.post {
                    updateAIResponseCard(aiService, "❌ 抱歉，${aiService} 暂时无法回复：${e.message}", true)
                    // 清理累积器
                    aiResponseAccumulator.remove(aiService)
                }
            }
        }.start()
    }
    
    /**
     * 格式化Markdown响应
     */
    private fun formatMarkdownResponse(response: String): String {
        return response
            .replace("```", "") // 移除代码块标记
            .replace("**", "") // 移除粗体标记
            .replace("*", "") // 移除斜体标记
            .replace("#", "") // 移除标题标记
            .replace("`", "") // 移除行内代码标记
            .replace("---", "—") // 替换分隔线
            .replace("###", "•") // 替换三级标题为项目符号
            .replace("##", "•") // 替换二级标题为项目符号
            .replace("#", "•") // 替换一级标题为项目符号
            .replace("\n\n\n", "\n\n") // 减少多余空行
            .replace("\\n", "\n") // 处理转义换行符
            .trim()
    }
    
    /**
     * 创建AI名称标签
     */
    private fun createAINameTab(aiService: String): MaterialButton {
        val contextThemeWrapper = ContextThemeWrapper(this, com.google.android.material.R.style.Theme_MaterialComponents_Light)
        val tab = MaterialButton(contextThemeWrapper)
        
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        layoutParams.marginEnd = 8.dpToPx()
        tab.layoutParams = layoutParams
        
        tab.text = aiService
        tab.textSize = 12f
        tab.setPadding(16.dpToPx(), 8.dpToPx(), 16.dpToPx(), 8.dpToPx())
        tab.cornerRadius = 16.dpToPx()
        
        // 设置默认样式（未选中）
        tab.backgroundTintList = getColorStateList(R.color.ai_assistant_button_secondary_light)
        tab.setTextColor(getColor(R.color.ai_assistant_button_text_secondary_light))
        tab.strokeColor = getColorStateList(R.color.ai_assistant_border_light)
        tab.strokeWidth = 1.dpToPx()
        
        tab.setOnClickListener {
            selectAITab(aiService)
            scrollToAICard(aiService)
        }
        
        return tab
    }
    
    /**
     * 选择AI标签
     */
    private fun selectAITab(aiService: String) {
        // 重置所有标签样式
        aiNameTabs.values.forEach { tab ->
            tab.backgroundTintList = getColorStateList(R.color.ai_assistant_button_secondary_light)
            tab.setTextColor(getColor(R.color.ai_assistant_button_text_secondary_light))
        }
        
        // 设置选中标签样式
        aiNameTabs[aiService]?.let { tab ->
            tab.backgroundTintList = getColorStateList(R.color.ai_assistant_primary_light)
            tab.setTextColor(getColor(R.color.ai_assistant_button_text_light))
        }
        
        currentSelectedAiTab = aiService
    }
    
    /**
     * 滚动到指定AI卡片
     */
    private fun scrollToAICard(aiService: String) {
        try {
            val responseContainer = aiAssistantPanelView?.findViewById<LinearLayout>(R.id.ai_engines_container)
            val scrollContainer = aiAssistantPanelView?.findViewById<HorizontalScrollView>(R.id.ai_engines_container)
            
            if (responseContainer != null && scrollContainer != null) {
                // 查找对应的AI卡片
                for (i in 0 until responseContainer.childCount) {
                    val card = responseContainer.getChildAt(i) as? MaterialCardView
                    if (card != null) {
                        val mainContainer = card.getChildAt(0) as? LinearLayout
                        if (mainContainer != null) {
                            val subtitleContainer = mainContainer.getChildAt(0) as? LinearLayout
                            if (subtitleContainer != null) {
                                val aiNameText = subtitleContainer.getChildAt(0) as? TextView
                                if (aiNameText?.text.toString() == aiService) {
                                    // 滚动到该卡片
                                    val cardLeft = card.left
                                    val cardWidth = card.width
                                    val scrollViewWidth = scrollContainer.width
                                    val scrollX = cardLeft - (scrollViewWidth - cardWidth) / 2
                                    
                                    scrollContainer.smoothScrollTo(scrollX, 0)
                                    break
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "滚动到AI卡片失败", e)
        }
    }
    
    /**
     * 更新AI名称标签栏
     */
    private fun updateAINameTabs(aiServices: List<String>) {
        try {
            val tabsContainer = aiAssistantPanelView?.findViewById<LinearLayout>(R.id.ai_engines_container)
            val tabsScrollView = aiAssistantPanelView?.findViewById<HorizontalScrollView>(R.id.ai_engines_container)
            
            if (tabsContainer != null && tabsScrollView != null) {
                // 清除现有标签
                tabsContainer.removeAllViews()
                aiNameTabs.clear()
                
                if (aiServices.isNotEmpty()) {
                    // 显示标签栏
                    tabsScrollView.visibility = View.VISIBLE
                    
                    // 创建新标签
                    aiServices.forEach { aiService ->
                        val tab = createAINameTab(aiService)
                        tabsContainer.addView(tab)
                        aiNameTabs[aiService] = tab
                    }
                    
                    // 默认选择第一个
                    if (aiServices.isNotEmpty()) {
                        selectAITab(aiServices[0])
                    }
                } else {
                    // 隐藏标签栏
                    tabsScrollView.visibility = View.GONE
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新AI名称标签栏失败", e)
        }
    }
    
    /**
     * 更新AI回复卡片
     */
    private fun updateAIResponseCard(aiService: String, content: String, isComplete: Boolean) {
        try {
            val responseContainer = aiAssistantPanelView?.findViewById<LinearLayout>(R.id.ai_engines_container)
            if (responseContainer != null) {
                // 查找对应的AI卡片
                for (i in 0 until responseContainer.childCount) {
                    val card = responseContainer.getChildAt(i) as? MaterialCardView
                    if (card != null) {
                        val mainContainer = card.getChildAt(0) as? LinearLayout
                        if (mainContainer != null) {
                            val subtitleContainer = mainContainer.getChildAt(0) as? LinearLayout
                            if (subtitleContainer != null) {
                                val aiNameText = subtitleContainer.getChildAt(0) as? TextView
                                if (aiNameText?.text.toString() == aiService) {
                                    // 找到对应的卡片，更新内容
                                    updateCardContent(card, content, isComplete)
                                    break
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新AI回复卡片失败", e)
        }
    }
    
    /**
     * 更新卡片内容
     */
    private fun updateCardContent(card: MaterialCardView, content: String, isComplete: Boolean) {
        try {
            val mainContainer = card.getChildAt(0) as? LinearLayout
            if (mainContainer != null) {
                val loadingContainer = mainContainer.getChildAt(1) as? LinearLayout
                val scrollView = mainContainer.getChildAt(2) as? ScrollView
                val textView = scrollView?.getChildAt(0) as? TextView
                
                if (isComplete) {
                    // 完成时隐藏加载状态，显示回复内容
                    loadingContainer?.visibility = View.GONE
                    scrollView?.visibility = View.VISIBLE
                    textView?.text = content
                } else {
                    // 流式更新时，如果还在加载状态，先显示内容区域
                    if (loadingContainer?.visibility == View.VISIBLE) {
                        loadingContainer.visibility = View.GONE
                        scrollView?.visibility = View.VISIBLE
                    }
                    // 流式回复直接设置内容（API已经处理了累积）
                    textView?.text = content
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新卡片内容失败", e)
        }
    }

    /**
     * 切换AI服务
     */
    private fun switchAIService() {
        try {
            val selectedServices = aiServiceSelectionManager.getSelectedServices()
            val availableServices = aiServiceSelectionManager.availableServices
            
            if (selectedServices.isEmpty()) {
                // 如果没有选中的服务，选择第一个
                if (availableServices.isNotEmpty()) {
                    aiServiceSelectionManager.setServiceSelected(availableServices.first(), true)
                    refreshAIServiceCheckBoxes()
                    updateAIServiceStatus()
                    Toast.makeText(this, "已选择: ${availableServices.first()}", Toast.LENGTH_SHORT).show()
                }
            } else if (selectedServices.size == 1) {
                // 如果只选中一个，切换到下一个
                val currentService = selectedServices.first()
                val currentIndex = availableServices.indexOf(currentService)
                val nextIndex = (currentIndex + 1) % availableServices.size
                val nextService = availableServices[nextIndex]
                
                aiServiceSelectionManager.clearAll()
                aiServiceSelectionManager.setServiceSelected(nextService, true)
                refreshAIServiceCheckBoxes()
                updateAIServiceStatus()
                Toast.makeText(this, "已切换到: $nextService", Toast.LENGTH_SHORT).show()
            } else {
                // 如果选中多个，清空后选择第一个
                aiServiceSelectionManager.clearAll()
                if (availableServices.isNotEmpty()) {
                    aiServiceSelectionManager.setServiceSelected(availableServices.first(), true)
                }
                refreshAIServiceCheckBoxes()
                updateAIServiceStatus()
                Toast.makeText(this, "已清空多选，选择: ${availableServices.first()}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "切换AI服务失败", e)
            Toast.makeText(this, "切换失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 切换回复区域折叠状态
     */
    private fun toggleResponseFold() {
        try {
            val responseScroll = aiAssistantPanelView?.findViewById<HorizontalScrollView>(R.id.ai_engines_container)
            val foldButton = aiAssistantPanelView?.findViewById<ImageButton>(R.id.history_button)
            
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
        Log.d(TAG, "恢复灵动岛状态，上一个状态: $previousState")
        
        // 停止透明度渐变定时器
        stopFadeOutTimer()
        
        // 恢复窗口参数到正常状态
        restoreWindowForNormalMode()
        
        // 圆球点击后直接回到初始状态，跳过紧凑状态
        Log.d(TAG, "圆球点击，直接回到初始状态")
        updateState(IslandState.COMPACT)
        isSearchModeActive = false
        
        // 确保灵动岛视图存在
        if (animatingIslandView == null) {
            showDynamicIsland()
        } else {
            // 清理现有内容，重新创建初始状态
            animatingIslandView?.removeAllViews()
            
            // 重新创建初始状态的内容
            val themedContext = getThemedContext()
            val contextThemeWrapper = ContextThemeWrapper(themedContext, R.style.Theme_FloatingWindow)
            val inflater = LayoutInflater.from(contextThemeWrapper)
            islandContentView = inflater.inflate(R.layout.dynamic_island_layout, animatingIslandView, false)
            islandContentView?.background = ColorDrawable(Color.TRANSPARENT)
            
            // 设置islandContentView的布局参数
            islandContentView?.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            
            // 重新设置按钮容器和交互
            val buttonContainer = islandContentView!!.findViewById<LinearLayout>(R.id.button_container)
            buttonContainer?.visibility = View.VISIBLE
            
            // 重新设置按钮交互
            setupEnhancedLayoutButtons(islandContentView!!)
            
            // 重新设置其他组件
            notificationIconContainer = islandContentView!!.findViewById(R.id.notification_icon_container)
            appSearchIconScrollView = islandContentView!!.findViewById(R.id.app_search_icon_scroll_view)
            appSearchIconContainer = islandContentView!!.findViewById(R.id.app_search_icon_container)
            
            // 添加内容到灵动岛视图
            animatingIslandView?.addView(islandContentView)
            
            Log.d(TAG, "重新创建初始状态内容完成")
        }
        
        // 先显示灵动岛视图（隐藏状态）
        animatingIslandView?.visibility = View.VISIBLE
        animatingIslandView?.alpha = 0f
        animatingIslandView?.scaleX = 0.1f
        animatingIslandView?.scaleY = 0.1f
        
        // 同时进行两个动画：圆球缩小消失，灵动岛放大出现
        val ballAnimation = ballView?.animate()
            ?.withLayer()
            ?.alpha(0f)
            ?.scaleX(0.1f)
            ?.scaleY(0.1f)
            ?.setDuration(250)
            ?.setInterpolator(AccelerateInterpolator())
            ?.withEndAction {
                try {
                    windowContainerView?.removeView(ballView)
                } catch (e: Exception) { /* ignore */ }
                ballView = null
            }
        
        val islandAnimation = animatingIslandView?.animate()
            ?.withLayer()
            ?.alpha(1f)
            ?.scaleX(1f)
            ?.scaleY(1f)
            ?.setDuration(350)
            ?.setInterpolator(OvershootInterpolator(0.6f))
            ?.setStartDelay(100) // 稍微延迟，让圆球开始消失后再显示灵动岛
            ?.withEndAction {
                Log.d(TAG, "灵动岛恢复动画完成，回到初始状态")
            }
        
        // 启动动画
        ballAnimation?.start()
        islandAnimation?.start()
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
            val intent = Intent(this, SettingsActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)

            Log.d(TAG, "灵动岛已退出，打开设置页面")
        } catch (e: Exception) {
            Log.e(TAG, "退出灵动岛失败", e)
        }
    }

    /**
     * 初始化屏幕文字识别管理器
     */
    private fun initScreenTextRecognitionManager() {
        try {
            screenTextRecognitionManager = ScreenTextRecognitionManager(this)
            screenTextRecognitionManager?.setCallback(object : ScreenTextRecognitionManager.TextRecognitionCallback {
                override fun onTextExtracted(text: String) {
                    handleExtractedText(text)
                }

                override fun onError(error: String) {
                    Log.e(TAG, "屏幕文字识别错误: $error")
                    showToast("文字识别失败: $error")
                }

                override fun onCancelled() {
                    Log.d(TAG, "屏幕文字识别已取消")
                }
            })
            Log.d(TAG, "屏幕文字识别管理器初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "初始化屏幕文字识别管理器失败", e)
        }
    }

    /**
     * 启动屏幕文字识别
     */
    private fun startScreenTextRecognition() {
        try {
            Log.d(TAG, "启动屏幕文字识别")
            screenTextRecognitionManager?.startTextRecognition()
        } catch (e: Exception) {
            Log.e(TAG, "启动屏幕文字识别失败", e)
            showToast("启动文字识别失败: ${e.message}")
        }
    }

    /**
     * 处理提取的文字
     */
    private fun handleExtractedText(text: String) {
        try {
            Log.d(TAG, "提取到文字: $text")

            // 将文字填入搜索输入框
            searchInput?.setText(text)

            // 恢复到灵动岛输入状态
            restoreIslandState()

            // 显示成功提示
            showToast("已提取文字: ${text.take(20)}${if (text.length > 20) "..." else ""}")

        } catch (e: Exception) {
            Log.e(TAG, "处理提取文字失败", e)
            showToast("处理文字失败: ${e.message}")
        }
    }

    /**
     * 显示Toast提示
     */
    private fun showToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
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
            "com.youku.phone" to "youku",
            "com.smile.gifmaker" to "kuaishou",
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
            "com.tencent.map" to "tencentmap",
            
            // 浏览器类
            "com.tencent.mtt" to "mttbrowser",
            "com.UCMobile" to "ucbrowser",
            "com.android.chrome" to "googlechrome",
            "org.mozilla.firefox" to "firefox",
            "com.quark.browser" to "quark",
            "com.baidu.searchbox" to "baiduboxapp",
            "com.sohu.inputmethod.sogou" to "sogou",
            "com.qihoo.browser" to "qihoo",
            
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
            "com.netease.nr" to "newsapp",
            "com.ss.android.article.news" to "toutiao",
            "com.tencent.news" to "tencentnews"
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
                    "weixin" -> {
                        // 微信不支持搜索URL Scheme，降级到普通启动
                        Log.d(TAG, "微信不支持搜索URL Scheme，降级到普通启动")
                        null
                    }
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
                    "youku" -> Intent(Intent.ACTION_VIEW, Uri.parse("youku://search?keyword=$encodedQuery"))
                    "kuaishou" -> Intent(Intent.ACTION_VIEW, Uri.parse("kuaishou://search?keyword=$encodedQuery"))
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
                    "tencentmap" -> Intent(Intent.ACTION_VIEW, Uri.parse("tencentmap://map/place/search?query=$encodedQuery"))
                    
                    // 浏览器类
                    "mttbrowser" -> Intent(Intent.ACTION_VIEW, Uri.parse("mttbrowser://search?query=$encodedQuery"))
                    "ucbrowser" -> Intent(Intent.ACTION_VIEW, Uri.parse("ucbrowser://search?keyword=$encodedQuery"))
                    "googlechrome" -> Intent(Intent.ACTION_VIEW, Uri.parse("googlechrome://www.google.com/search?q=$encodedQuery"))
                    "firefox" -> Intent(Intent.ACTION_VIEW, Uri.parse("firefox://search?q=$encodedQuery"))
                    "quark" -> Intent(Intent.ACTION_VIEW, Uri.parse("quark://search?q=$encodedQuery"))
                    "baiduboxapp" -> Intent(Intent.ACTION_VIEW, Uri.parse("baiduboxapp://searchbox?action=search&query=$encodedQuery"))
                    "sogou" -> Intent(Intent.ACTION_VIEW, Uri.parse("sogou://search?keyword=$encodedQuery"))
                    "qihoo" -> Intent(Intent.ACTION_VIEW, Uri.parse("qihoo://search?keyword=$encodedQuery"))
                    
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
                    "toutiao" -> Intent(Intent.ACTION_VIEW, Uri.parse("toutiao://search?keyword=$encodedQuery"))
                    "tencentnews" -> Intent(Intent.ACTION_VIEW, Uri.parse("tencentnews://search?keyword=$encodedQuery"))
                    
                    else -> {
                        // 通用URL scheme格式
                        Intent(Intent.ACTION_VIEW, Uri.parse("${appInfo.urlScheme}://search?query=$encodedQuery"))
                    }
                }

                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    // 执行搜索动作后退出搜索面板并切换到圆球状态
                    hideContentAndSwitchToBall()

                    // 延迟显示AI悬浮窗
                    uiHandler.postDelayed({
                        showSimpleAIOverlay(appInfo.packageName, appInfo.label)
                    }, 2000)
                } else {
                    // Intent为null，降级到普通启动
                    Log.d(TAG, "Intent为null，降级到普通启动: ${appInfo.urlScheme}")
                    launchAppForManualPaste(appInfo, query)
                }
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

                // 延迟显示AI悬浮窗
                uiHandler.postDelayed({
                    showSimpleAIOverlay(appInfo.packageName, appInfo.label)
                }, 2000)
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
                        val aiInputText = aiAssistantPanelView?.findViewById<EditText>(R.id.search_input)
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
            
            dialogView.findViewById<View>(R.id.btn_new_profile)?.setOnClickListener {
                try {
                    Log.d(TAG, "创建新档案")
                    showNewProfileDialog(dialog)
                } catch (e: Exception) {
                    Log.e(TAG, "创建新档案失败", e)
                    Toast.makeText(this, "创建新档案失败", Toast.LENGTH_SHORT).show()
                }
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

    /**
     * 刷新档案选择器（如果当前可见）
     */
    private fun refreshProfileSelectorIfVisible() {
        // 这里可以添加逻辑来检查是否有档案选择器正在显示
        // 如果有，则重新显示以刷新数据
        Log.d(TAG, "档案变更通知：检查是否需要刷新档案选择器")
    }

    /**
     * 显示新建档案对话框
     */
    private fun showNewProfileDialog(parentDialog: AlertDialog) {
        try {
            val context = ContextThemeWrapper(getThemedContext(), R.style.Theme_FloatingWindow)
            val input = EditText(context).apply {
                hint = "请输入档案名称"
                setPadding(32, 16, 32, 16)
            }
            
            val dialog = AlertDialog.Builder(context)
                .setTitle("新建AI指令档案")
                .setMessage("请输入新档案的名称：")
                .setView(input)
                .setPositiveButton("创建") { _, _ ->
                    val name = input.text.toString().trim()
                    if (name.isNotEmpty()) {
                        try {
                            val newProfile = PromptProfile(
                                id = UUID.randomUUID().toString(),
                                name = name,
                                persona = "一个乐于助人的通用AI助手",
                                tone = "友好、清晰、简洁",
                                formality = "适中",
                                responseLength = "适中",
                                outputFormat = "使用Markdown格式进行回复",
                                language = "中文",
                                description = "新建的AI助手档案"
                            )
                            
                            // 保存新档案
                            settingsManager.savePromptProfile(newProfile)
                            
                            // 设置为当前活跃档案
                            settingsManager.setActivePromptProfileId(newProfile.id)
                            
                            Toast.makeText(this, "档案「$name」创建成功", Toast.LENGTH_SHORT).show()
                            
                            // 关闭父对话框并重新显示档案选择器
                            parentDialog.dismiss()
                            showPromptProfileSelectorForAI()
                            
                            Log.d(TAG, "新档案创建成功: $name")
                            
                        } catch (e: Exception) {
                            Log.e(TAG, "保存新档案失败", e)
                            Toast.makeText(this, "保存档案失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "请输入档案名称", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("取消", null)
                .create()
            
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            dialog.show()
            
            // 自动聚焦到输入框
            input.requestFocus()
            
        } catch (e: Exception) {
            Log.e(TAG, "显示新建档案对话框失败", e)
            Toast.makeText(this, "无法显示新建档案对话框", Toast.LENGTH_SHORT).show()
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
        val btnExpand = view.findViewById<MaterialButton>(R.id.btn_expand)
        val btnSettings = view.findViewById<MaterialButton>(R.id.btn_settings)
        val btnExit = view.findViewById<MaterialButton>(R.id.btn_exit)
        
        // 调试日志：检查按钮是否找到
        Log.d(TAG, "设置按钮交互 - AI助手按钮: ${btnAiAssistant != null}, 应用程序按钮: ${btnApps != null}, 搜索按钮: ${btnSearch != null}, 展开按钮: ${btnExpand != null}, 设置按钮: ${btnSettings != null}, 退出按钮: ${btnExit != null}")
        
        // 如果AI助手按钮未找到，记录警告
        if (btnAiAssistant == null) {
            Log.w(TAG, "警告：AI助手按钮未找到！请检查布局文件是否正确")
        }

        // AI助手按钮
        btnAiAssistant?.setOnClickListener {
            Log.d(TAG, "AI助手按钮被点击")
            try {
                // 显示AI应用悬浮窗面板（与软件tab搜索后弹出的面板相同）
                showAIAppOverlayPanel()
            } catch (e: Exception) {
                Log.e(TAG, "AI助手按钮点击处理失败", e)
                Toast.makeText(this, "无法打开AI助手面板: ${e.message}", Toast.LENGTH_SHORT).show()
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

        // 展开按钮 - 手动触发展开界面
        btnExpand?.setOnClickListener {
            Log.d(TAG, "展开按钮被点击 - 手动触发展开界面")
            triggerManualExpansion(view)
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
    }

    /**
     * 手动触发展开界面 - 显示最近打开的app和AI查询界面
     */
    private fun triggerManualExpansion(view: View) {
        try {
            Log.d(TAG, "手动触发展开界面")

            // 获取当前剪贴板内容，如果没有则使用默认文本
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val currentClipboard = clipboardManager.primaryClip?.getItemAt(0)?.text?.toString()
            val displayContent = if (!currentClipboard.isNullOrBlank()) {
                currentClipboard
            } else {
                "点击展开查看最近应用和AI助手"
            }

            // 使用与剪贴板触发相同的展开逻辑
            expandIslandForClipboard(displayContent)

            Log.d(TAG, "手动展开界面完成，显示内容: $displayContent")
        } catch (e: Exception) {
            Log.e(TAG, "手动触发展开界面失败", e)
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
        val intent = Intent(this, SettingsActivity::class.java).apply {
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
    
    /**
     * 启动透明度渐变定时器
     * 在指定延迟后开始将圆球逐渐变透明
     */
    private fun startFadeOutTimer() {
        // 先停止之前的定时器
        stopFadeOutTimer()
        
        // 重置渐变状态
        isFadingOut = false
        
        // 创建新的定时器
        fadeOutRunnable = Runnable {
            if (ballView != null && !isDragging) {
                Log.d(TAG, "开始透明度渐变")
                isFadingOut = true
                
                // 执行透明度渐变动画
                ballView?.animate()
                    ?.alpha(0.3f) // 渐变到30%透明度
                    ?.setDuration(fadeOutDuration)
                    ?.setInterpolator(AccelerateDecelerateInterpolator())
                    ?.withEndAction {
                        isFadingOut = false
                        Log.d(TAG, "透明度渐变完成")
                    }
                    ?.start()
            }
        }
        
        // 延迟启动
        uiHandler.postDelayed(fadeOutRunnable!!, fadeOutDelay)
        Log.d(TAG, "透明度渐变定时器已启动，${fadeOutDelay}ms后开始渐变")
    }
    
    /**
     * 停止透明度渐变定时器
     * 在用户触摸圆球时调用
     */
    private fun stopFadeOutTimer() {
        fadeOutRunnable?.let { 
            uiHandler.removeCallbacks(it)
            fadeOutRunnable = null
        }
        
        // 如果正在渐变，停止渐变动画
        if (isFadingOut) {
            ballView?.animate()?.cancel()
            isFadingOut = false
            Log.d(TAG, "透明度渐变已停止")
        }
    }
}