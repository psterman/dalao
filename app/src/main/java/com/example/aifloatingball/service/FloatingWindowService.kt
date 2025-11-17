package com.example.aifloatingball.service

import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsActivity
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.SimpleModeActivity
import com.example.aifloatingball.VoiceRecognitionActivity
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.model.AppSearchConfig
import com.example.aifloatingball.model.AppSearchSettings
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.model.SearchEngineShortcut
import com.example.aifloatingball.service.NotificationHelper
import com.example.aifloatingball.manager.ModeManager
import com.example.aifloatingball.MultiTabBrowserActivity
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.net.URLEncoder
import com.example.aifloatingball.ui.text.TextSelectionManager
import android.text.Editable
import android.text.TextWatcher
import android.content.ClipDescription
import android.os.Looper
import com.example.aifloatingball.utils.BitmapUtils
import android.graphics.BitmapFactory
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.os.Vibrator
import android.os.VibrationEffect
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.AppSearchAdapter
import com.example.aifloatingball.manager.AppInfoManager
import com.example.aifloatingball.model.AppInfo
import com.example.aifloatingball.AppSelectionHistoryManager
import com.example.aifloatingball.manager.AppSortManager
import com.example.aifloatingball.model.AppCategory
import com.example.aifloatingball.utils.FaviconLoader
import com.example.aifloatingball.SearchHistoryActivity

class FloatingWindowService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    private val windowManager by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    private val settingsManager by lazy { SettingsManager.getInstance(this) }
    private val vibrator: Vibrator by lazy {
        getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }

    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null

    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0.toFloat()
    private var initialTouchY: Float = 0.toFloat()
    private var isMoving: Boolean = false
    private var isLongPress: Boolean = false
    private var isClick: Boolean = false
    private val touchSlop: Int by lazy { ViewConfiguration.get(this).scaledTouchSlop }

    private val idleHandler = Handler(android.os.Looper.getMainLooper())
    private var fadeOutRunnable: Runnable? = null

    // For notification bar
    private var notificationBarView: View? = null
    private var notificationBarParams: WindowManager.LayoutParams? = null
    private val notificationHideHandler = Handler(Looper.getMainLooper())
    private var notificationHideRunnable: Runnable? = null

    // 新增：用于处理AI按钮的 Handler 和 Runnable
    private var aiButtonView: View? = null
    private var aiPopupView: View? = null
    private var aiAssistantPanelView: View? = null
    private val aiButtonHandler = Handler(Looper.getMainLooper())
    private var hideAiButtonRunnable: Runnable? = null

    // 新增: 用于处理长按事件的 Handler 和 Runnable
    private val longPressHandler = Handler(android.os.Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null

    private var floatingBallIcon: ImageView? = null
    private var searchContainer: LinearLayout? = null
    private var aiEnginesContainer: LinearLayout? = null
    private var regularEnginesContainer: LinearLayout? = null
    private var savedCombosContainer: LinearLayout? = null
    private var appSearchContainer: LinearLayout? = null

    // 新增：标题和分割线
    private var comboTitle: TextView? = null
    private var aiTitle: TextView? = null
    private var regularTitle: TextView? = null
    private var appTitle: TextView? = null
    private var comboDivider: View? = null
    private var aiDivider: View? = null
    private var regularDivider: View? = null

    private var searchInput: EditText? = null

    private lateinit var appSearchSettings: AppSearchSettings
    private lateinit var textSelectionManager: TextSelectionManager
    private var isMenuVisible: Boolean = false
    private var themedContext: Context? = null // Make themedContext a member variable

    // App Search Components
    private lateinit var appInfoManager: AppInfoManager
    private var appSearchRecyclerView: RecyclerView? = null
    private var appSearchAdapter: AppSearchAdapter? = null
    private var appSearchResultsContainer: View? = null
    private var closeAppSearchButton: View? = null

    private var aiSearchEngines: List<AISearchEngine> = emptyList()
    private var regularSearchEngines: List<SearchEngine> = emptyList()
    private var searchEngineShortcuts: List<SearchEngineShortcut> = emptyList()

    private val gson = Gson()

    private val settingsChangeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.aifloatingball.SETTINGS_CHANGED") {
                loadAndDisplayAppSearch()
            }
        }
    }

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getStringExtra(NotificationListener.EXTRA_COMMAND)) {
                NotificationListener.COMMAND_POSTED -> {
                    val title = intent.getStringExtra(NotificationListener.EXTRA_TITLE) ?: ""
                    val text = intent.getStringExtra(NotificationListener.EXTRA_TEXT) ?: ""
                    val iconBytes = intent.getByteArrayExtra(NotificationListener.EXTRA_ICON)
                    val icon = iconBytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }

                    // Prefer text content for search, but use title as a fallback.
                    val contentToSearch = if (text.isNotBlank()) text else title
                    val displayText = "From: $title - $text".trim()

                    if (contentToSearch.isNotBlank()) {
                        showNotificationBar(displayText, contentToSearch, icon)
                    }
                }
            }
        }
    }

    private val positionUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_UPDATE_POSITION) {
                updateFloatingBallPosition()
            }
        }
    }

    companion object {
        private const val NOTIFICATION_ID = NotificationHelper.NOTIFICATION_ID
        private const val CHANNEL_ID = NotificationHelper.CHANNEL_ID
        private const val TAG = "FloatingWindowService"
        const val ACTION_SHOW_SEARCH = "com.example.aifloatingball.service.SHOW_SEARCH"
        const val ACTION_UPDATE_POSITION = "com.example.aifloatingball.service.UPDATE_POSITION"

        /**
         * 检查FloatingWindowService是否正在运行
         */
        @Suppress("DEPRECATION") // 为了兼容性保留旧API
        fun isRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            return manager.getRunningServices(Integer.MAX_VALUE)
                .any { it.service.className == FloatingWindowService::class.java.name }
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        appSearchSettings = AppSearchSettings.getInstance(this)
        settingsManager.registerOnSharedPreferenceChangeListener(this)
        appInfoManager = AppInfoManager.getInstance()

        setupView()
        setupFloatingBall()
        setupNotificationBar()
        setupPasteButton()

        // Register broadcast receivers
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(positionUpdateReceiver, IntentFilter(ACTION_UPDATE_POSITION), Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(positionUpdateReceiver, IntentFilter(ACTION_UPDATE_POSITION))
        }
        LocalBroadcastManager.getInstance(this).registerReceiver(notificationReceiver,
            IntentFilter(NotificationListener.ACTION_NOTIFICATION))
        LocalBroadcastManager.getInstance(this).registerReceiver(settingsChangeReceiver,
            IntentFilter("com.example.aifloatingball.SETTINGS_CHANGED"))

        // Create notification channel and start foreground service
        NotificationHelper.createNotificationChannel(this)
        startForeground(NOTIFICATION_ID, NotificationHelper.createNotification(this))
    }

    private fun setupView() {
        // Initialize views and themed context
        initializeViews()
    }

    private fun setupNotificationBar() {
        // Initialize notification bar view
        initializeNotificationBar()
    }

    private fun setupPasteButton() {
        // Initialize paste button view - the actual initialization is done in showPasteButton()
        // when the button is first shown
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHOW_SEARCH) {
            showSearchInterface()
        }
        
        // 检查是否是从SimpleModeActivity关闭过来的
        val isClosingFromSimpleMode = intent?.getBooleanExtra("closing_from_simple_mode", false) ?: false
        if (isClosingFromSimpleMode) {
            Log.d(TAG, "Service started from SimpleModeActivity closing")
        }
        
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingView != null) windowManager.removeView(floatingView)
        if (notificationBarView != null) windowManager.removeView(notificationBarView)
        settingsManager.unregisterOnSharedPreferenceChangeListener(this)
        idleHandler.removeCallbacksAndMessages(null) // Clean up handler
        notificationHideHandler.removeCallbacksAndMessages(null)
        hideAIButton()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(notificationReceiver)
        // Unregister position update receiver
        unregisterReceiver(positionUpdateReceiver)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            "theme_mode" -> recreateViews()
            "search_engine_shortcuts" -> loadSavedCombos()
            "enabled_search_engines", "enabled_ai_engines", "floating_window_display_mode" -> {
                loadSearchEngines()
            }
            "left_handed_mode" -> {
                // No-op. The change will be reflected the next time the menu is opened.
                // This respects the user's ability to place the ball anywhere.
            }
            "ball_alpha" -> {
                updateBallAlpha()
            }
        }
    }

    private fun recreateViews() {
        // Preserve the current position to prevent the ball from resetting its location
        val currentParams = params

        if (floatingView?.isAttachedToWindow == true) {
            windowManager.removeView(floatingView)
        }

        // This will inflate a new view with the correct theme based on the latest settings
        initializeViews()
        setupTouchListener()

        // Restore the previous params (especially position) and add the new view
        params = currentParams
        try {
            windowManager.addView(floatingView, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating view after recreation", e)
        }

        // Restore the visibility of the search menu if it was open
        if (isMenuVisible) {
            // We need to re-show the interface, but without animation to avoid weird jumps
            showSearchInterface(false)
        }
    }

    private fun initializeViews() {
        // 1. Get the app's theme mode from SettingsManager to correctly apply dark/light theme
        val themeMode = settingsManager.getThemeMode()

        // 2. Create a new configuration object based on the service's current configuration
        val config = Configuration(resources.configuration)
        val currentNightMode = config.uiMode and Configuration.UI_MODE_NIGHT_MASK

        // 3. Determine the correct night mode flag based on app settings
        val newNightMode = when (themeMode) {
            SettingsManager.THEME_MODE_DARK -> Configuration.UI_MODE_NIGHT_YES
            SettingsManager.THEME_MODE_LIGHT -> Configuration.UI_MODE_NIGHT_NO
            else -> currentNightMode // Follow system, so use the config's current value
        }
        config.uiMode = newNightMode or (config.uiMode and currentNightMode.inv())

        // 4. Create a new context with the overridden configuration
        val contextWithOverride = createConfigurationContext(config)

        // 5. Create a themed context using the overridden context and assign to member variable
        themedContext = ContextThemeWrapper(contextWithOverride, R.style.Theme_FloatingWindow)
        val inflater = LayoutInflater.from(themedContext)
        floatingView = inflater.inflate(R.layout.floating_ball_layout, null)

        // Add a touch listener to the root view to detect outside touches
        floatingView?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE && isMenuVisible) {
                // 仅当文本选择菜单未显示时，才通过外部触摸隐藏搜索界面
                // 这可以防止在点击文本菜单按钮时意外关闭搜索界面
                if (!textSelectionManager.isShowing()) {
                hideSearchInterface()
                }
                true // Consume the event
            } else {
                false // Don't consume, let other views handle it
            }
        }

        floatingBallIcon = floatingView?.findViewById(R.id.floating_ball_icon)
        searchContainer = floatingView?.findViewById(R.id.search_container)
        aiEnginesContainer = floatingView?.findViewById(R.id.ai_engines_container)
        regularEnginesContainer = floatingView?.findViewById(R.id.regular_engines_container)
        savedCombosContainer = floatingView?.findViewById(R.id.saved_combos_container)
        appSearchContainer = floatingView?.findViewById(R.id.app_search_container)

        comboTitle = floatingView?.findViewById(R.id.combo_title)
        aiTitle = floatingView?.findViewById(R.id.ai_title)
        regularTitle = floatingView?.findViewById(R.id.regular_title)
        appTitle = floatingView?.findViewById(R.id.app_title)
        comboDivider = floatingView?.findViewById(R.id.combo_divider)
        aiDivider = floatingView?.findViewById(R.id.ai_divider)
        regularDivider = floatingView?.findViewById(R.id.regular_divider)

        searchInput = floatingView?.findViewById(R.id.search_input)

        // Initialize App Search Results Views
        appSearchResultsContainer = floatingView?.findViewById(R.id.app_search_results_container)
        appSearchRecyclerView = floatingView?.findViewById(R.id.app_search_results_recycler_view)
        closeAppSearchButton = floatingView?.findViewById(R.id.close_app_search_button)
        closeAppSearchButton?.setOnClickListener {
            hideAppSearchResults()
        }

        // Load content before setting up listeners that might depend on it
        loadSearchEngines()
        loadSavedCombos()
        loadAndDisplayAppSearch()

        setupSearchInput()

        val searchModeButton = floatingView?.findViewById<ImageButton>(R.id.search_mode_button)
        searchModeButton?.setOnClickListener { view ->
            showSearchModeMenu(view)
        }

        val clearButton = floatingView?.findViewById<ImageButton>(R.id.clear_button)
        clearButton?.setOnClickListener {
            searchInput?.text?.clear()
        }

        // Apply theme to all UI elements
        applyThemeToAllElements()

        updateSearchModeVisibility()
        updateBallAlpha()
    }

    private fun applyThemeToAllElements() {
        themedContext?.let { context ->
            val nightModeFlags = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            val isDarkMode = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES

            // 定义颜色
            val iconColor = if (isDarkMode) android.graphics.Color.WHITE else android.graphics.Color.DKGRAY
            val textColor = if (isDarkMode) android.graphics.Color.WHITE else android.graphics.Color.BLACK
            val hintColor = if (isDarkMode) android.graphics.Color.LTGRAY else android.graphics.Color.GRAY

            // 应用到清除按钮
            val clearButton = floatingView?.findViewById<ImageButton>(R.id.clear_button)
            clearButton?.setColorFilter(iconColor, android.graphics.PorterDuff.Mode.SRC_IN)

            // 应用到搜索输入框
            searchInput?.let { input ->
                input.setTextColor(textColor)
                input.setHintTextColor(hintColor)
            }

            // 应用到搜索按钮（放大镜图标）
            val searchButton = floatingView?.findViewById<ImageButton>(R.id.search_button)
            searchButton?.setColorFilter(iconColor, android.graphics.PorterDuff.Mode.SRC_IN)

            // 应用到搜索模式按钮（放大镜图标）
            val searchModeButton = floatingView?.findViewById<ImageButton>(R.id.search_mode_button)
            searchModeButton?.setColorFilter(iconColor, android.graphics.PorterDuff.Mode.SRC_IN)

            // 应用到其他按钮（只处理存在的按钮）
            val voiceSearchButton = floatingView?.findViewById<ImageButton>(R.id.voice_search_button)
            voiceSearchButton?.setColorFilter(iconColor, android.graphics.PorterDuff.Mode.SRC_IN)

            // 应用到关闭应用搜索按钮
            val closeAppSearchButton = floatingView?.findViewById<ImageButton>(R.id.close_app_search_button)
            closeAppSearchButton?.setColorFilter(iconColor, android.graphics.PorterDuff.Mode.SRC_IN)

            // 应用到文本视图（只处理存在的文本视图）
            val shortcutNameViews = floatingView?.findViewById<TextView>(R.id.shortcut_name)
            shortcutNameViews?.setTextColor(textColor)
        }
    }

    private fun initializeNotificationBar() {
        val inflater = LayoutInflater.from(themedContext)
        notificationBarView = inflater.inflate(R.layout.notification_bar_layout, null).apply {
            visibility = View.GONE // Initially hidden
            setOnClickListener {
                val searchText = it.tag as? String
                if (!searchText.isNullOrBlank()) {
                    val intent = Intent(this@FloatingWindowService, DualFloatingWebViewService::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        putExtra("SEARCH_QUERY", searchText)
                    }
                    startService(intent)
                }
                // Hide bar after click, and cancel the auto-hide
                notificationHideRunnable?.let { runnable -> notificationHideHandler.removeCallbacks(runnable) }
                visibility = View.GONE
            }
        }

        notificationBarParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 50 // Margin from top
        }

        windowManager.addView(notificationBarView, notificationBarParams)
    }

    private fun showNotificationBar(displayText: String, searchText: String, icon: android.graphics.Bitmap?) {
        notificationBarView?.let { bar ->
            val textView = bar.findViewById<TextView>(R.id.notification_text)
            val iconView = bar.findViewById<ImageView>(R.id.notification_app_icon)

            textView.text = displayText
            bar.tag = searchText // Store search text in the tag

            if (icon != null) {
                iconView.setImageBitmap(icon)
                iconView.visibility = View.VISIBLE
            } else {
                iconView.visibility = View.GONE
            }

            if (bar.visibility == View.GONE) {
                bar.visibility = View.VISIBLE
            }

            // Always reset the hide timer
            notificationHideRunnable?.let { notificationHideHandler.removeCallbacks(it) }
            notificationHideRunnable = Runnable {
                bar.visibility = View.GONE
            }
            notificationHideHandler.postDelayed(notificationHideRunnable!!, 5000)
        }
    }

    private fun setupFloatingBall() {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val savedPosition = settingsManager.getFloatingBallPosition()
        val screenWidth = getScreenWidth()
        val isLeftHanded = settingsManager.isLeftHandedModeEnabled()

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = if (isLeftHanded) 0 else screenWidth
            y = savedPosition.second
        }

        try {
            windowManager.addView(floatingView, params)
            // 设置初始透明度
            updateBallAlpha()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add floating view", e)
        }
        setupTouchListener()
    }

    private fun setupSearchInput() {
        searchInput?.setOnEditorActionListener { v, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                performSearch(v.text.toString())
                true
            } else {
                false
            }
        }
        searchInput?.setOnLongClickListener {
            // It's safe to cast here as we know searchInput is an EditText
            textSelectionManager.showEditTextSelectionMenu(it as EditText)
            true // Consume the long click event
        }
        searchInput?.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // Delay showing the button slightly to allow keyboard animation to start
                aiButtonHandler.postDelayed({ showAIButton() }, 100)
            } else {
                hideAIButton()
            }
        }
        searchInput?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Hide the button as soon as the user starts typing
                hideAIButton()
                val query = s.toString()
                if (query.isNotEmpty()) {
                    val appResults = appInfoManager.search(query)
                    if (appResults.isNotEmpty()) {
                        showAppSearchResults(appResults)
                    } else {
                        hideAppSearchResults()
                    }
                } else {
                    hideAppSearchResults()
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun performSearch(query: String) {
        if (query.isNotBlank()) {
            // 检查用户偏好，决定使用哪种浏览器
            val useMultiTabBrowser = settingsManager.getBoolean("use_multi_tab_browser", false)

            if (useMultiTabBrowser) {
                // 使用新的多标签页浏览器
                val browserIntent = Intent(this, MultiTabBrowserActivity::class.java).apply {
                    putExtra(MultiTabBrowserActivity.EXTRA_INITIAL_QUERY, query)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                try {
                    startActivity(browserIntent)
                    Log.d(TAG, "启动多标签页浏览器进行搜索: $query")
                } catch (e: Exception) {
                    Log.e(TAG, "启动多标签页浏览器失败，回退到双搜索", e)
                    // 回退到原有的双搜索功能
                    startDualSearch(query)
                }
            } else {
                // 使用原有的双搜索功能
                startDualSearch(query)
            }

            hideSearchInterface()
        }
    }

    private fun startDualSearch(query: String = "") {
        // 检查DualFloatingWebViewService是否正在运行
        if (DualFloatingWebViewService.isRunning) {
            // 如果服务正在运行，尝试恢复卡片视图界面
            // 确保隐藏悬浮球的搜索界面，让用户直接看到卡片搜索结果
            if (isMenuVisible) {
                hideSearchInterface()
            }
            
            val restoreIntent = Intent(DualFloatingWebViewService.ACTION_RESTORE_CARD_VIEW).apply {
                setPackage(packageName)
            }
            sendBroadcast(restoreIntent)
            Log.d(TAG, "DualFloatingWebViewService正在运行，发送恢复卡片视图广播，隐藏悬浮球搜索界面")
            return
        }
        
        // 如果服务未运行，启动新的搜索
        val engineName = if (query.isNotBlank()) {
            settingsManager.getSearchEngineForPosition(0)
        } else {
            "" // 如果没有查询词，不指定引擎
        }
        val serviceIntent = Intent(this, DualFloatingWebViewService::class.java).apply {
            if (query.isNotBlank()) {
                putExtra("search_query", query)
                putExtra("engine_key", engineName)
                putExtra("search_source", "悬浮窗")
                putExtra("startTime", System.currentTimeMillis())
            }
            // 从悬浮球触发搜索时，默认使用卡片视图模式
            putExtra("use_card_view_mode", true)
        }
        startService(serviceIntent)
        Log.d(TAG, "启动新的DualFloatingWebViewService搜索: $query")
    }

    private fun startForegroundService() {
        val channel = NotificationChannel(CHANNEL_ID, "AI Floating Ball Service", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val notificationIntent = Intent(this, SimpleModeActivity::class.java).apply {
            putExtra("open_settings", true)
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AI Floating Ball")
            .setContentText("Service is running.")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Use a valid icon
            .setContentIntent(pendingIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun setupTouchListener() {
        floatingBallIcon?.setOnTouchListener { _, event ->
            // Reset idle timer on any touch to prevent fade-out during interaction
            resetIdleTimer()

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoving = false
                    true // Consume the event
                }
                MotionEvent.ACTION_MOVE -> {
                    // Calculate the distance moved
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY

                    // If not already moving and movement exceeds touch slop, it's a drag
                    if (!isMoving && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                        isMoving = true
                    }

                    // If in moving state, update the ball's position
                    if (isMoving) {
                        params?.x = initialX + dx.toInt()
                        params?.y = initialY + dy.toInt()
                        try {
                            if (floatingView?.isAttachedToWindow == true && params != null) {
                                windowManager.updateViewLayout(floatingView, params)
                            }
                        } catch (e: IllegalArgumentException) {
                            android.util.Log.w("FloatingWindowService", "View not attached during drag", e)
                        }
                    }
                    true // Consume the event
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMoving) {
                        // It's a click
                        val action = settingsManager.getActionBallClick()
                        executeAction(action)
                    } else {
                        // If it was a move, snap to the edge if enabled.
                        if (settingsManager.getAutoHide()) {
                            snapToEdge()
                        }
                    }
                    // Reset the moving flag
                    isMoving = false
                    true // Consume the event
                }
                else -> false
            }
        }
    }

    private fun executeAction(action: String) {
        when (action) {
            "voice_recognize" -> showVoiceRecognition()
            "floating_menu" -> {
                // 如果DualFloatingWebViewService正在运行，优先恢复卡片视图
                if (DualFloatingWebViewService.isRunning) {
                    // 隐藏搜索界面，恢复卡片视图
                    if (isMenuVisible) {
                        hideSearchInterface()
                    }
                    val restoreIntent = Intent(DualFloatingWebViewService.ACTION_RESTORE_CARD_VIEW).apply {
                        setPackage(packageName)
                    }
                    sendBroadcast(restoreIntent)
                    Log.d(TAG, "DualFloatingWebViewService正在运行，点击悬浮球恢复卡片视图")
                } else {
                    toggleSearchInterface()
                }
            }
            "dual_search" -> startDualSearch()
            "island_panel" -> { /* No-op in FloatingWindowService */ }
            "settings" -> openSettings()
            "mode_switch" -> showModeSwitchMenu()
            "none" -> { /* No-op */ }
        }
    }

    /**
     * 显示模式切换菜单
     */
    private fun showModeSwitchMenu() {
        try {
            // 直接切换到下一个模式，而不是显示菜单
            // 因为在Service中显示PopupMenu比较复杂
            ModeManager.switchToNextMode(this)

            // 显示Toast提示当前模式
            val currentMode = ModeManager.getCurrentMode(this)
            Toast.makeText(this, "已切换到: ${currentMode.displayName}", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            android.util.Log.e("FloatingWindowService", "模式切换失败", e)
            Toast.makeText(this, "模式切换功能暂时不可用", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showVoiceRecognition() {
        floatingBallIcon?.apply {
            setImageResource(R.drawable.avd_voice_ripple)
            val drawable = this.drawable
            if (drawable is android.graphics.drawable.AnimatedVectorDrawable) {
                drawable.start()
            }
        }

        val intent = Intent(this, VoiceRecognitionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun startDualSearch() {
        val serviceIntent = Intent(this, DualFloatingWebViewService::class.java).apply {
            // Potentially pass a default query or open it empty
        }
        startService(serviceIntent)
        hideSearchInterface()
    }

    private fun openSettings() {
        val intent = Intent(this, SimpleModeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("open_settings", true)
        }
        startActivity(intent)
        hideSearchInterface()
    }

    /**
     * 检查AI引擎是否需要API配置
     */
    private fun needsApiConfiguration(engineName: String): Boolean {
        return when (engineName.lowercase()) {
            "deepseek (api)", "deepseek (custom)" -> {
                val apiKey = settingsManager.getDeepSeekApiKey()
                apiKey.isBlank()
            }
            "chatgpt (api)" -> {
                val apiKey = settingsManager.getChatGPTApiKey()
                apiKey.isBlank()
            }
            else -> false
        }
    }

    /**
     * 显示API配置对话框
     */
    private fun showApiConfigurationDialog(engineName: String, query: String) {
        try {
            val builder = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)

            val engineDisplayName = when (engineName.lowercase()) {
                "deepseek (api)", "deepseek (custom)" -> "DeepSeek"
                "chatgpt (api)" -> "ChatGPT"
                else -> engineName
            }

            builder.setTitle("配置 $engineDisplayName API")
            builder.setMessage("要使用 $engineDisplayName AI 对话功能，需要先配置 API 密钥。\n\n配置完成后，您就可以开始使用 AI 对话功能了。")
            builder.setCancelable(true)

            // 设置按钮
            builder.setPositiveButton("去配置") { _, _ ->
                openApiSettings(engineName)
            }

            builder.setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
            }

            builder.setNeutralButton("稍后配置") { dialog, _ ->
                // 直接进入页面，让用户在页面内看到配置引导
                val serviceIntent = Intent(this, DualFloatingWebViewService::class.java).apply {
                    putExtra("search_query", query)
                    putExtra("engine_key", engineName)
                    putExtra("search_source", "悬浮窗")
                    putExtra("startTime", System.currentTimeMillis())
                }
                startService(serviceIntent)
                hideSearchInterface()
                dialog.dismiss()
            }

            val dialog = builder.create()
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            dialog.show()

        } catch (e: Exception) {
            Log.e(TAG, "显示API配置对话框失败", e)
            // 如果对话框显示失败，直接打开设置
            openApiSettings(engineName)
        }
    }

    /**
     * 打开API设置页面
     */
    private fun openApiSettings(engineName: String) {
        try {
            val intent = Intent(this, SettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra("highlight_section", "ai_settings")
                putExtra("highlight_engine", engineName)
            }
            startActivity(intent)
            hideSearchInterface()
        } catch (e: Exception) {
            Log.e(TAG, "打开API设置失败", e)
            Toast.makeText(this, "无法打开设置页面", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleSearchInterface() {
        if (isMenuVisible) {
            hideSearchInterface()
        } else {
            showSearchInterface()
        }
    }

    private fun showSearchInterface(shouldAnimate: Boolean = true) {
        if (isMenuVisible) return
        isMenuVisible = true
        cancelIdleTimer() // Don't fade while menu is open
        floatingBallIcon?.animate()?.alpha(1f)?.setDuration(150)?.start() // Ensure ball is fully visible

        val menuWidth = resources.getDimensionPixelSize(R.dimen.floating_menu_width)

        // Adjust position before showing menu to keep ball stationary
        if (!settingsManager.isLeftHandedModeEnabled()) { // Right-handed, menu appears on the left
            params?.x = params?.x?.minus(menuWidth)
        }

        val contentContainer = floatingView?.findViewById<LinearLayout>(R.id.floating_view_content_container)
        val layoutParams = contentContainer?.layoutParams as? FrameLayout.LayoutParams
        // Re-order views for handedness
        val searchContainer = floatingView?.findViewById<View>(R.id.search_container)
        val ballIcon = floatingView?.findViewById<View>(R.id.floating_ball_icon)
        contentContainer?.removeView(searchContainer)
        contentContainer?.removeView(ballIcon)
        if (settingsManager.isLeftHandedModeEnabled()) {
            layoutParams?.gravity = Gravity.START
            contentContainer?.addView(ballIcon)
            contentContainer?.addView(searchContainer)
        } else {
            layoutParams?.gravity = Gravity.END
            contentContainer?.addView(searchContainer)
            contentContainer?.addView(ballIcon)
        }
        contentContainer?.layoutParams = layoutParams

        if (shouldAnimate) {
        searchContainer?.visibility = View.VISIBLE
        } else {
            searchContainer?.visibility = View.VISIBLE // Should already be visible, but just in case
        }
        
        updateSearchModeVisibility()
        
        // 确保组合搜索和应用搜索已加载
        loadSavedCombos()
        updateAppSearchIcons()

        // Make search container focusable
        params?.flags = params?.flags?.and(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv())
        try {
            if (floatingView?.isAttachedToWindow == true && params != null) {
                windowManager.updateViewLayout(floatingView, params)
            }
        } catch (e: IllegalArgumentException) {
            android.util.Log.w("FloatingWindowService", "View not attached during showSearchInterface", e)
        }

        searchInput?.requestFocus()
        if (settingsManager.isAutoPasteEnabled()) {
            autoPaste(searchInput)
        }
        showKeyboard()
        showAIButton()
        setupSearchInputListener() // Add listener when menu is shown
    }

    private fun hideSearchInterface() {
        if (!isMenuVisible) return
        isMenuVisible = false
        resetIdleTimer() // Restart idle timer when menu closes

        val menuWidth = resources.getDimensionPixelSize(R.dimen.floating_menu_width)

        searchContainer?.animate()?.alpha(0f)?.setDuration(200)?.withEndAction {
            searchContainer?.visibility = View.GONE
            searchContainer?.alpha = 1f

            // Adjust position and flags AFTER menu is gone
            try {
                if (!settingsManager.isLeftHandedModeEnabled()) { // Right-handed, menu was on the left
                    params?.x = params?.x?.plus(menuWidth)
                }
                params?.flags = params?.flags?.or(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                    ?.and(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH.inv())

                // 检查floatingView是否仍然附加到WindowManager
                if (floatingView?.isAttachedToWindow == true && params != null) {
                    windowManager.updateViewLayout(floatingView, params)
                }
            } catch (e: IllegalArgumentException) {
                // View已经从WindowManager中移除，忽略这个错误
                android.util.Log.w("FloatingWindowService", "View not attached to window manager during hideSearchInterface", e)
            } catch (e: Exception) {
                // 处理其他可能的异常
                android.util.Log.e("FloatingWindowService", "Error updating view layout in hideSearchInterface", e)
            }
        }

        hideKeyboard()
        hideAppSearchResults() // Also hide app results
    }

    private fun showKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchInput?.windowToken, 0)
    }

    private fun updateSearchModeVisibility() {
        try {
            val enabledModes = settingsManager.getFloatingWindowDisplayModes()
            Log.d(TAG, "Enabled modes: $enabledModes")

            animateModuleVisibility(enabledModes.contains("combo"), comboTitle, savedCombosContainer, comboDivider)
            animateModuleVisibility(enabledModes.contains("ai"), aiTitle, aiEnginesContainer, aiDivider)
            animateModuleVisibility(enabledModes.contains("normal"), regularTitle, regularEnginesContainer, regularDivider)
            animateModuleVisibility(enabledModes.contains("app"), appTitle, appSearchContainer, null) // app module has no divider

        } catch (e: Exception) {
            Log.e(TAG, "更新搜索模式可见性时出错", e)
        }
    }

    private fun animateModuleVisibility(show: Boolean, title: View?, container: View?, divider: View?) {
        val targetVisibility = if (show) View.VISIBLE else View.GONE
        val viewsToAnimate = listOfNotNull(title, container, divider)

        if (viewsToAnimate.isEmpty() || (viewsToAnimate.first().visibility == targetVisibility && !show)) {
             // If we want to show and it's already visible, we might still want to run the animation for consistency, but if hiding, no need to re-animate.
            if(viewsToAnimate.first().visibility == targetVisibility) return
        }

        viewsToAnimate.forEach { view ->
            view.animate().cancel() // Cancel any ongoing animation
            if (show) {
                if (view.visibility != View.VISIBLE) {
                    view.alpha = 0f
                    view.visibility = View.VISIBLE
                }
                view.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            } else {
                view.animate()
                    .alpha(0f)
                    .setDuration(200)
                    .setInterpolator(AccelerateInterpolator())
                    .withEndAction {
                        view.visibility = View.GONE
                    }
                    .start()
            }
        }
    }

    private fun loadSearchEngines() {
        try {
            // 从 SettingsManager 获取用户配置的搜索引擎名称
            val enabledAIEngineNames = settingsManager.getEnabledAIEngines()
            val enabledRegularEngineNames = settingsManager.getEnabledSearchEngines()

            Log.d(TAG, "Enabled AI engines: $enabledAIEngineNames")
            Log.d(TAG, "Enabled regular engines: $enabledRegularEngineNames")

            // 获取所有可用的搜索引擎
            val allAIEngines = AISearchEngine.DEFAULT_AI_ENGINES
            val allRegularEngines = SearchEngine.DEFAULT_ENGINES

            // 如果没有启用的引擎，使用所有默认引擎
            aiSearchEngines = if (enabledAIEngineNames.isEmpty()) {
                allAIEngines.toList()
            } else {
                allAIEngines.filter { it.name in enabledAIEngineNames }
            }

            regularSearchEngines = if (enabledRegularEngineNames.isEmpty()) {
                allRegularEngines.toList()
            } else {
                allRegularEngines.filter { it.name in enabledRegularEngineNames }
            }

            Log.d(TAG, "Loaded AI engines: ${aiSearchEngines.map { it.name }}")
            Log.d(TAG, "Loaded regular engines: ${regularSearchEngines.map { it.name }}")

            updateSearchEngineDisplay()
        } catch (e: Exception) {
            Log.e(TAG, "Error loading search engines", e)
            // 出错时使用默认引擎
            aiSearchEngines = AISearchEngine.DEFAULT_AI_ENGINES.toList()
            regularSearchEngines = SearchEngine.DEFAULT_ENGINES.toList()
            updateSearchEngineDisplay()
        }
    }

    private fun updateSearchEngineDisplay() {
        try {
            aiEnginesContainer?.removeAllViews()
            regularEnginesContainer?.removeAllViews()

            // 获取显示模式设置
            val enabledModes = settingsManager.getFloatingWindowDisplayModes()
            
            // 根据显示模式决定是否显示各类搜索引擎
            if (enabledModes.contains("ai")) {
                addEnginesToContainer(aiEnginesContainer, aiSearchEngines)
            }
            
            if (enabledModes.contains("normal")) {
                addEnginesToContainer(regularEnginesContainer, regularSearchEngines)
            }

            // 更新各容器的可见性
            updateSearchModeVisibility()
        } catch (e: Exception) {
            Log.e(TAG, "Error updating search engine display", e)
        }
    }

    private fun addEnginesToContainer(container: LinearLayout?, engines: List<Any>) {
        if (container == null || engines.isEmpty()) return

        try {
            val horizontalScrollView = HorizontalScrollView(themedContext ?: this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                isHorizontalScrollBarEnabled = false
            }
            
            val linearLayout = LinearLayout(themedContext ?: this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            }

            engines.forEach { engine ->
                val engineView = createEngineView(engine)
                linearLayout.addView(engineView)
            }

            horizontalScrollView.addView(linearLayout)
            container.addView(horizontalScrollView)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding engines to container", e)
        }
    }

    private fun createEngineView(engine: Any): View {
        try {
            val view = LayoutInflater.from(themedContext ?: this).inflate(R.layout.search_engine_shortcut, null)
            val icon = view.findViewById<ImageView>(R.id.shortcut_icon)
            val name = view.findViewById<TextView>(R.id.shortcut_name)

            val url: String
            val engineName: String
            val fallbackIconResId: Int
            when (engine) {
                is SearchEngine -> {
                    url = engine.url
                    engineName = engine.name
                    fallbackIconResId = R.drawable.ic_web_default
                }
                is AISearchEngine -> {
                    url = engine.url
                    engineName = engine.name
                    fallbackIconResId = R.drawable.ic_web_default
                }
                else -> {
                    Log.e(TAG, "Unknown engine type: ${engine.javaClass.simpleName}")
                    return view
                }
            }

            // 加载图标
            try {
                FaviconLoader.loadIcon(icon, url, fallbackIconResId)
            } catch (e: Exception) {
                Log.e(TAG, "Error loading favicon for $engineName", e)
                icon.setImageResource(fallbackIconResId)
            }

            // 设置引擎名称
            name.text = engineName

            // 设置点击事件
            view.setOnClickListener {
                val query = searchInput?.text.toString()
                val isAiEngine = engine is AISearchEngine

                if (isAiEngine || query.isNotBlank()) {
                    // 如果是AI引擎，检查API配置
                    if (isAiEngine && needsApiConfiguration(engineName)) {
                        showApiConfigurationDialog(engineName, query)
                        return@setOnClickListener
                    }

                    // 如果是AI引擎，检查是否应该使用自定义HTML页面
                    // 对于使用自定义HTML的AI引擎（如DeepSeek (API)、ChatGPT (Custom)等），直接使用WebView方式，不跳转到App
                    if (isAiEngine) {
                        val shouldUseCustomHtml = engineName.contains("Custom", ignoreCase = true) || 
                                                  engineName.contains("API", ignoreCase = true) ||
                                                  engineName.contains("(Custom)", ignoreCase = true) ||
                                                  engineName.contains("(API)", ignoreCase = true)
                        
                        if (!shouldUseCustomHtml) {
                            // 只有非自定义HTML的AI引擎才尝试跳转到App
                            if (tryJumpToAIApp(engineName, query)) {
                                // 成功跳转到App，隐藏搜索界面
                                hideSearchInterface()
                                return@setOnClickListener
                            }
                            // 如果跳转失败，继续使用WebView方式
                            Log.d(TAG, "AI App未安装或跳转失败，使用WebView方式: $engineName")
                        } else {
                            Log.d(TAG, "AI引擎使用自定义HTML页面，跳过App跳转，直接使用WebView方式: $engineName")
                        }
                    }

                    // 标准化引擎键，确保能正确匹配
                    val normalizedEngineKey = when {
                        engineName.contains("临时专线", ignoreCase = true) -> "临时专线"
                        engineName.contains("ChatGPT", ignoreCase = true) && engineName.contains("Custom", ignoreCase = true) -> "ChatGPT (Custom)"
                        engineName.contains("ChatGPT", ignoreCase = true) && engineName.contains("API", ignoreCase = true) -> "ChatGPT (API)"
                        engineName.contains("DeepSeek", ignoreCase = true) && engineName.contains("API", ignoreCase = true) -> "DeepSeek (API)"
                        engineName.contains("Claude", ignoreCase = true) && engineName.contains("Custom", ignoreCase = true) -> "Claude (Custom)"
                        engineName.contains("Claude", ignoreCase = true) && engineName.contains("API", ignoreCase = true) -> "Claude (API)"
                        engineName.contains("通义千问", ignoreCase = true) && engineName.contains("Custom", ignoreCase = true) -> "通义千问 (Custom)"
                        engineName.contains("通义千问", ignoreCase = true) && engineName.contains("API", ignoreCase = true) -> "通义千问 (API)"
                        engineName.contains("智谱", ignoreCase = true) && engineName.contains("Custom", ignoreCase = true) -> "智谱AI (Custom)"
                        engineName.contains("智谱", ignoreCase = true) && engineName.contains("API", ignoreCase = true) -> "智谱AI (API)"
                        engineName.contains("文心一言", ignoreCase = true) && engineName.contains("Custom", ignoreCase = true) -> "文心一言 (Custom)"
                        engineName.contains("Gemini", ignoreCase = true) && engineName.contains("Custom", ignoreCase = true) -> "Gemini (Custom)"
                        engineName.contains("Kimi", ignoreCase = true) && engineName.contains("Custom", ignoreCase = true) -> "Kimi (Custom)"
                        engineName.contains("讯飞星火", ignoreCase = true) && engineName.contains("Custom", ignoreCase = true) -> "讯飞星火 (Custom)"
                        else -> engineName
                    }
                    
                    Log.d(TAG, "启动AI搜索: 原始引擎键='$engineName', 标准化后='$normalizedEngineKey', 查询='$query'")
                    
                    val serviceIntent = Intent(this, DualFloatingWebViewService::class.java).apply {
                        putExtra("search_query", query)
                        putExtra("engine_key", normalizedEngineKey)
                        putExtra("search_source", "悬浮窗")
                        putExtra("startTime", System.currentTimeMillis())
                    }
                    startService(serviceIntent)
                    hideSearchInterface()
                } else {
                    Toast.makeText(this, "请输入搜索内容", Toast.LENGTH_SHORT).show()
                }
            }

            return view
        } catch (e: Exception) {
            Log.e(TAG, "Error creating engine view", e)
            // 返回一个空的视图作为后备
            return View(themedContext ?: this)
        }
    }

    private fun loadSavedCombos() {
        val json = settingsManager.getSearchEngineShortcuts()
        if (json.isNotEmpty()) {
            try {
                val listType = object : TypeToken<List<SearchEngineShortcut>>() {}.type
                searchEngineShortcuts = gson.fromJson<List<SearchEngineShortcut>>(json, listType)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse search engine shortcuts", e)
                searchEngineShortcuts = emptyList()
            }
        } else {
            searchEngineShortcuts = emptyList()
        }
        updateSavedCombosDisplay()
    }

    private fun updateSavedCombosDisplay() {
        savedCombosContainer?.removeAllViews()
        if (searchEngineShortcuts.isNotEmpty()) {
            val horizontalScrollView = HorizontalScrollView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                isHorizontalScrollBarEnabled = false
            }
            val linearLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            searchEngineShortcuts.forEach { shortcut ->
                val shortcutView = createShortcutView(shortcut)
                linearLayout.addView(shortcutView)
            }
            horizontalScrollView.addView(linearLayout)
            savedCombosContainer?.addView(horizontalScrollView)
            Log.d(TAG, "组合搜索已显示，共 ${searchEngineShortcuts.size} 个组合")
        } else {
            Log.d(TAG, "组合搜索为空，没有保存的组合")
        }
    }

    private fun createShortcutView(shortcut: SearchEngineShortcut): View {
        val view = View.inflate(this, R.layout.search_engine_shortcut, null)
        val icon = view.findViewById<ImageView>(R.id.shortcut_icon)
        val name = view.findViewById<TextView>(R.id.shortcut_name)

        if (shortcut.engines.isNotEmpty()) {
            FaviconLoader.loadIcon(icon, shortcut.engines.first().url, R.drawable.ic_search)
        }
        name.text = shortcut.name

        view.setOnClickListener {
            val query = searchInput?.text.toString()
            if (query.isNotBlank()) {
                shortcut.engines.forEach { engine ->
                    val serviceIntent = Intent(this, DualFloatingWebViewService::class.java).apply {
                        putExtra("search_query", query)
                        putExtra("engine_key", engine.name) // Pass engine name as the key
                        putExtra("search_source", "悬浮窗")
                        putExtra("startTime", System.currentTimeMillis())
                    }
                    startService(serviceIntent)
                }
                hideSearchInterface()
            }
        }
        return view
    }

    private fun loadAndDisplayAppSearch() {
        updateAppSearchIcons()
    }
    
    /**
     * 根据输入框文字智能匹配应用图标
     */
    private fun updateAppSearchIcons() {
        appSearchContainer?.removeAllViews()
        val enabledAppConfigs = appSearchSettings.getEnabledAppConfigs()

        if(enabledAppConfigs.isEmpty()) return

        val query = searchInput?.text?.toString()?.trim() ?: ""
        val appsToShow = if (query.isNotEmpty()) {
            // 根据输入文字匹配应用
            matchAppsByQuery(query, enabledAppConfigs)
        } else {
            // 如果输入框为空，显示最近使用的应用（按order排序）
            enabledAppConfigs.take(8) // 最多显示8个
        }

        if (appsToShow.isEmpty()) return

        val horizontalScrollView = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isHorizontalScrollBarEnabled = false
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        appsToShow.forEach { appConfig ->
            val button = ImageButton(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    dpToPx(48),
                    dpToPx(48)
                ).also { it.marginEnd = dpToPx(8) }
                try {
                    val iconDrawable = packageManager.getApplicationIcon(appConfig.packageName)
                    setImageDrawable(iconDrawable)
                } catch (e: PackageManager.NameNotFoundException) {
                    setImageResource(appConfig.iconResId) // Fallback icon
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundResource(R.drawable.circle_ripple)
                contentDescription = "${appConfig.appName} 搜索"
                setOnClickListener {
                    // 记录应用选择历史
                    val historyManager = AppSelectionHistoryManager.getInstance(this@FloatingWindowService)
                    historyManager.addAppSelection(appConfig)
                    
                    val searchQuery = searchInput?.text?.toString() ?: ""
                    if (searchQuery.isNotEmpty()) {
                        openAppSearch(appConfig, searchQuery)
                    } else {
                        packageManager.getLaunchIntentForPackage(appConfig.packageName)?.let {
                            startActivity(it.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                        } ?: Toast.makeText(context, "无法启动 ${appConfig.appName}", Toast.LENGTH_SHORT).show()
                    }
                    hideSearchInterface()
                }
            }
            container.addView(button)
        }
        horizontalScrollView.addView(container)
        appSearchContainer?.addView(horizontalScrollView)
        
        Log.d(TAG, "应用搜索图标已更新，查询: '$query', 显示 ${appsToShow.size} 个应用")
    }
    
    /**
     * 根据查询文字匹配应用
     * 如果无法匹配到应用，自动恢复用户最后点击历史排序的app
     */
    private fun matchAppsByQuery(query: String, enabledAppConfigs: List<AppSearchConfig>): List<AppSearchConfig> {
        val normalizedQuery = query.lowercase().trim()
        if (normalizedQuery.isEmpty()) {
            // 如果输入框为空，返回按历史排序的应用
            return getHistorySortedApps(enabledAppConfigs).take(8)
        }
        
        // 使用AppInfoManager进行智能匹配
        val appInfoManager = AppInfoManager.getInstance()
        val matchedApps = if (appInfoManager.isLoaded()) {
            val appInfos = appInfoManager.search(normalizedQuery)
            // 将AppInfo转换为AppSearchConfig
            appInfos.mapNotNull { appInfo ->
                enabledAppConfigs.find { it.packageName == appInfo.packageName }
            }
        } else {
            emptyList()
        }
        
        // 如果AppInfoManager没有匹配到，使用简单的名称匹配
        val simpleMatchedApps = if (matchedApps.isEmpty()) {
            enabledAppConfigs.filter { config ->
                config.appName.lowercase().contains(normalizedQuery) ||
                config.packageName.lowercase().contains(normalizedQuery)
            }
        } else {
            matchedApps
        }
        
        // 如果仍然没有匹配到，返回按历史排序的应用（而不是空白）
        if (simpleMatchedApps.isEmpty()) {
            Log.d(TAG, "无法匹配到应用，返回历史排序的应用")
            return getHistorySortedApps(enabledAppConfigs).take(8)
        }
        
        // 返回匹配的应用，最多8个
        return simpleMatchedApps.take(8)
    }
    
    /**
     * 获取按历史排序的应用列表
     * 优先显示最近点击的应用
     */
    private fun getHistorySortedApps(enabledAppConfigs: List<AppSearchConfig>): List<AppSearchConfig> {
        // 使用AppSelectionHistoryManager获取最近选择的应用
        val historyManager = AppSelectionHistoryManager.getInstance(this)
        val recentApps = historyManager.getRecentApps()
        
        if (recentApps.isNotEmpty()) {
            // 按历史记录排序
            val historyMap = recentApps.mapIndexed { index, item -> item.packageName to index }.toMap()
            return enabledAppConfigs.sortedWith { a, b ->
                val aIndex = historyMap[a.packageName] ?: Int.MAX_VALUE
                val bIndex = historyMap[b.packageName] ?: Int.MAX_VALUE
                aIndex.compareTo(bIndex)
            }
        }
        
        // 如果没有历史记录，使用AppSortManager按使用次数排序
        val appSortManager = AppSortManager.getInstance(this)
        return appSortManager.sortApps(enabledAppConfigs, AppCategory.ALL)
    }

    /**
     * 尝试跳转到AI App
     * @param engineName AI引擎名称
     * @param query 搜索查询内容
     * @return 是否成功跳转到App
     */
    private fun tryJumpToAIApp(engineName: String, query: String): Boolean {
        try {
            Log.d(TAG, "尝试跳转到AI App: $engineName, 查询: $query")
            
            // 获取AI应用的包名列表
            val possiblePackages = getAIPackages(engineName)
            if (possiblePackages.isEmpty()) {
                Log.d(TAG, "未找到AI应用包名: $engineName")
                return false
            }
            
            // 检查是否有已安装的AI应用
            val installedPackage = getInstalledAIPackageName(possiblePackages)
            if (installedPackage == null) {
                Log.d(TAG, "AI应用未安装: $engineName")
                return false
            }
            
            // 跳转到AI App并传递输入框内容
            launchAIAppWithIntent(installedPackage, query, engineName)
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "跳转到AI App失败: $engineName", e)
            return false
        }
    }
    
    /**
     * 获取AI应用的包名列表
     */
    private fun getAIPackages(engineName: String): List<String> {
        return when {
            engineName.contains("DeepSeek", ignoreCase = true) -> listOf("com.deepseek.chat")
            engineName.contains("豆包", ignoreCase = true) -> listOf("com.larus.nova")
            engineName.contains("ChatGPT", ignoreCase = true) -> listOf("com.openai.chatgpt")
            engineName.contains("Kimi", ignoreCase = true) -> listOf("com.moonshot.kimichat")
            engineName.contains("腾讯元宝", ignoreCase = true) -> listOf("com.tencent.hunyuan.app.chat")
            engineName.contains("讯飞星火", ignoreCase = true) -> listOf("com.iflytek.spark")
            engineName.contains("智谱清言", ignoreCase = true) || engineName.contains("智谱AI", ignoreCase = true) -> listOf("com.zhipuai.qingyan")
            engineName.contains("通义千问", ignoreCase = true) -> listOf("com.aliyun.tongyi")
            engineName.contains("文小言", ignoreCase = true) || engineName.contains("文心一言", ignoreCase = true) -> listOf("com.baidu.newapp")
            engineName.contains("Grok", ignoreCase = true) -> listOf("ai.x.grok")
            engineName.contains("Perplexity", ignoreCase = true) -> listOf("ai.perplexity.app.android")
            engineName.contains("Manus", ignoreCase = true) -> listOf("com.manus.im.app")
            engineName.contains("秘塔AI搜索", ignoreCase = true) || engineName.contains("秘塔", ignoreCase = true) -> listOf("com.metaso")
            engineName.contains("Poe", ignoreCase = true) -> listOf("com.poe.android")
            engineName.contains("IMA", ignoreCase = true) -> listOf("com.tencent.ima")
            engineName.contains("纳米AI", ignoreCase = true) -> listOf("com.qihoo.namiso")
            engineName.contains("Gemini", ignoreCase = true) -> listOf("com.google.android.apps.gemini")
            engineName.contains("Copilot", ignoreCase = true) -> listOf("com.microsoft.copilot")
            else -> emptyList()
        }
    }
    
    /**
     * 检查AI应用是否已安装
     */
    private fun getInstalledAIPackageName(possiblePackages: List<String>): String? {
        for (packageName in possiblePackages) {
            try {
                packageManager.getPackageInfo(packageName, 0)
                Log.d(TAG, "找到已安装的AI应用: $packageName")
                return packageName
            } catch (e: PackageManager.NameNotFoundException) {
                // 继续检查下一个包名
            }
        }
        return null
    }
    
    /**
     * 启动AI应用并使用Intent发送文本
     */
    private fun launchAIAppWithIntent(packageName: String, query: String, appName: String) {
        try {
            Log.d(TAG, "启动AI应用: $appName, 包名: $packageName, 查询: $query")
            
            // 对于特定AI应用，尝试使用Intent直接发送文本
            if (tryIntentSendForAIApp(packageName, query, appName)) {
                return
            }
            
            // 使用通用的启动应用并自动粘贴方法
            launchAppWithAutoPaste(packageName, query, appName)
            
        } catch (e: Exception) {
            Log.e(TAG, "AI应用启动失败: $appName", e)
            Toast.makeText(this, "$appName 启动失败", Toast.LENGTH_SHORT).show()
            sendQuestionViaClipboard(packageName, query, appName)
        }
    }
    
    /**
     * 尝试使用Intent直接发送文本到AI应用
     */
    private fun tryIntentSendForAIApp(packageName: String, query: String, appName: String): Boolean {
        try {
            Log.d(TAG, "尝试Intent直接发送到${appName}: $query")
            
            // 方案1：尝试使用ACTION_SEND直接发送文本
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, query)
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (sendIntent.resolveActivity(packageManager) != null) {
                startActivity(sendIntent)
                Toast.makeText(this, "正在向${appName}发送问题...", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "${appName} Intent发送成功")
                return true
            }
            
            // 方案2：对于特定应用，尝试使用URL Scheme
            val urlScheme = getAIAppUrlScheme(packageName, query)
            if (urlScheme != null) {
                try {
                    val schemeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(urlScheme)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    if (schemeIntent.resolveActivity(packageManager) != null) {
                        startActivity(schemeIntent)
                        Toast.makeText(this, "正在打开${appName}...", Toast.LENGTH_SHORT).show()
                        Log.d(TAG, "${appName} URL Scheme跳转成功")
                        return true
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "${appName} URL Scheme跳转失败", e)
                }
            }
            
            Log.d(TAG, "${appName} Intent发送失败，回退到剪贴板方案")
            return false
            
        } catch (e: Exception) {
            Log.e(TAG, "${appName} Intent发送失败", e)
            return false
        }
    }
    
    /**
     * 获取AI应用的URL Scheme
     */
    private fun getAIAppUrlScheme(packageName: String, query: String): String? {
        return when (packageName) {
            "com.iflytek.spark" -> "iflytek://spark?query=${Uri.encode(query)}"
            "com.zhipuai.qingyan" -> "zhipuai://qingyan?query=${Uri.encode(query)}"
            "com.aliyun.tongyi" -> "tongyi://aliyun?query=${Uri.encode(query)}"
            "com.baidu.newapp" -> "wenxiaoyan://app?query=${Uri.encode(query)}"
            "ai.x.grok" -> "grok://xai?query=${Uri.encode(query)}"
            "ai.perplexity.app.android" -> "perplexity://ai?query=${Uri.encode(query)}"
            "com.manus.im.app" -> "manus://app?query=${Uri.encode(query)}"
            "com.metaso" -> "mita://ai?query=${Uri.encode(query)}"
            "com.poe.android" -> "poe://app?query=${Uri.encode(query)}"
            "com.qihoo.namiso" -> "nano://ai?query=${Uri.encode(query)}"
            "com.google.android.apps.gemini" -> "gemini://google?query=${Uri.encode(query)}"
            "com.microsoft.copilot" -> "copilot://microsoft?query=${Uri.encode(query)}"
            else -> null
        }
    }
    
    /**
     * 启动应用并使用自动化粘贴
     */
    private fun launchAppWithAutoPaste(packageName: String, query: String, appName: String) {
        try {
            Log.d(TAG, "启动应用并使用自动化粘贴: $appName, 问题: $query")
            
            // 将问题复制到剪贴板
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("AI问题", query)
            clipboard.setPrimaryClip(clip)
            
            // 启动AI应用
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                Toast.makeText(this, "正在启动${appName}...", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "${appName}启动成功")
                
                // 延迟显示悬浮窗（如果需要）
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    // 可以通过无障碍服务自动粘贴
                    val autoPasteIntent = Intent("com.example.aifloatingball.AUTO_PASTE").apply {
                        putExtra("package_name", packageName)
                        putExtra("query", query)
                        putExtra("app_name", appName)
                    }
                    sendBroadcast(autoPasteIntent)
                }, 1000) // 等待1秒让应用完全加载
                
            } else {
                Toast.makeText(this, "无法启动${appName}，请检查应用是否已安装", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动应用并自动粘贴失败: ${appName}", e)
            // 回退到剪贴板方案
            sendQuestionViaClipboard(packageName, query, appName)
        }
    }
    
    /**
     * 使用剪贴板发送问题
     */
    private fun sendQuestionViaClipboard(packageName: String, query: String, appName: String) {
        try {
            // 将问题复制到剪贴板
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("AI问题", query)
            clipboard.setPrimaryClip(clip)
            
            // 启动AI应用
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                Toast.makeText(this, "已复制问题到剪贴板，请在${appName}中粘贴", Toast.LENGTH_LONG).show()
                Log.d(TAG, "剪贴板方案成功: $appName")
            } else {
                Toast.makeText(this, "$appName 未安装", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "剪贴板方案失败: $appName", e)
            Toast.makeText(this, "$appName 启动失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openAppSearch(appConfig: AppSearchConfig, query: String) {
        try {
            val url = appConfig.searchUrl.replace("{q}", Uri.encode(query))
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
                // --- Search History ---
                val historyItem = mapOf(
                    "keyword" to "${appConfig.appName}: $query",
                    "source" to "悬浮窗-应用搜索",
                    "timestamp" to System.currentTimeMillis(),
                    "duration" to 0 // Duration is not applicable here
                )
                settingsManager.addSearchHistoryItem(historyItem)
                // --- End Search History ---
            } else {
                Toast.makeText(this, "无法在 ${appConfig.appName} 中搜索", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "启动 ${appConfig.appName} 搜索失败", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Error opening app search for ${appConfig.appName}", e)
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun showSearchModeMenu(anchor: View) {
        // Use the themedContext, which is now a member variable, to ensure correct theming
        val context = themedContext ?: this
        val popup = PopupMenu(context, anchor)

        // Inflate the correct menu resource
        popup.menuInflater.inflate(R.menu.search_display_menu, popup.menu)

        // Set the initial checked state from settings
        val currentModes = settingsManager.getFloatingWindowDisplayModes()
        popup.menu.findItem(R.id.menu_show_combo).isChecked = currentModes.contains("combo")
        popup.menu.findItem(R.id.menu_show_ai).isChecked = currentModes.contains("ai")
        popup.menu.findItem(R.id.menu_show_normal).isChecked = currentModes.contains("normal")
        popup.menu.findItem(R.id.menu_show_app).isChecked = currentModes.contains("app")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_settings -> {
                    // Handle settings click
                    val intent = Intent(this, SettingsActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    hideSearchInterface()
                    true // Handled
                }
                R.id.menu_search_history -> {
                    // 处理查看搜索历史点击
                    val intent = Intent(this, SearchHistoryActivity::class.java)
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    hideSearchInterface()
                    true // Handled
                }
                else -> {
                    // This block handles the checkable items
                    item.isChecked = !item.isChecked

                    // Update settings based on the new checked state
                    val modesToSave = mutableSetOf<String>()
                    if (popup.menu.findItem(R.id.menu_show_combo).isChecked) modesToSave.add("combo")
                    if (popup.menu.findItem(R.id.menu_show_ai).isChecked) modesToSave.add("ai")
                    if (popup.menu.findItem(R.id.menu_show_normal).isChecked) modesToSave.add("normal")
                    if (popup.menu.findItem(R.id.menu_show_app).isChecked) modesToSave.add("app")

                    settingsManager.setFloatingWindowDisplayModes(modesToSave)
                    
                    // Return true to indicate the event was handled. This will close the menu
                    // but ensures the action is reliably performed.
                    true
                }
            }
        }
        popup.show()
    }

    private fun updateBallAlpha() {
        val alphaPercentage = settingsManager.getBallAlpha()
        val alpha = alphaPercentage / 100f
        floatingBallIcon?.alpha = alpha
        Log.d(TAG, "Updated ball alpha to $alpha (from percentage $alphaPercentage)")
    }

    private fun resetIdleTimer() {
        // Cancel any previous timer and bring ball to full alpha
        cancelIdleTimer()
        floatingBallIcon?.animate()?.alpha(1.0f)?.setDuration(150)?.start()

        // Set a new timer to fade ONLY the ball
        fadeOutRunnable = Runnable {
            val alphaPercentage = settingsManager.getBallAlpha()
            val idleAlpha = (alphaPercentage / 100f) * 0.5f // Fade to 50% of the set alpha
            floatingBallIcon?.animate()?.alpha(idleAlpha)?.setDuration(500)?.start()
        }
        idleHandler.postDelayed(fadeOutRunnable!!, 3000) // 3-second delay
    }

    private fun cancelIdleTimer() {
        fadeOutRunnable?.let { idleHandler.removeCallbacks(it) }
    }

    private fun calculateTargetX(): Int {
        val menuWidth = resources.getDimensionPixelSize(R.dimen.floating_menu_width)
        val screenWidth = getScreenWidth()
        val isLeftHanded = settingsManager.isLeftHandedModeEnabled()

        return if (isLeftHanded) {
            params?.x ?: 0
        } else {
            screenWidth - menuWidth
        }
    }

    private fun showAIButton() {
        val input = searchInput ?: return
        if (!input.isShown) return // Don't show if the input field itself is not visible

        if (aiButtonView == null) {
            // Use themedContext to inflate the view, which is crucial
            val context = themedContext ?: this
            val inflater = LayoutInflater.from(context)
            aiButtonView = inflater.inflate(R.layout.ai_button, null)

            aiButtonView?.setOnClickListener {
                showAIPopup()
            }
        }

        // Ensure the view is not already added
        if (aiButtonView?.parent != null) {
            return
        }

        // Post the positioning logic to run after the input view has been laid out,
        // ensuring the coordinates are for its final position.
        input.post {
            // Re-check if the button has been added or hidden in the meantime
            if (aiButtonView?.parent != null || !input.isShown) {
                return@post
            }

            // Measure the AI button to get its width for centering
            aiButtonView?.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            val aiButtonWidth = aiButtonView?.measuredWidth ?: 0

            val locationOnScreen = IntArray(2)
            input.getLocationOnScreen(locationOnScreen)
            val inputX = locationOnScreen[0]
            val inputY = locationOnScreen[1]
            val inputWidth = input.width

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = inputX + (inputWidth - aiButtonWidth) / 2 // Center the button horizontally
                y = inputY + input.height // Position directly below the input field
            }

            try {
                windowManager.addView(aiButtonView, params)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add AI button", e)
            }

            // Schedule to hide
            hideAiButtonRunnable = Runnable { hideAIButton() }
            aiButtonHandler.postDelayed(hideAiButtonRunnable!!, 10000) // Show for 10 seconds
        }
    }

    private fun hideAIButton() {
        hideAiButtonRunnable?.let { aiButtonHandler.removeCallbacks(it) }
        hideAiButtonRunnable = null
        if (aiButtonView?.parent != null) {
            try {
                windowManager.removeView(aiButtonView)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove AI button", e)
            }
        }
        // Don't null out aiButtonView here, so it can be reused
    }

    /**
     * 显示AI按钮弹出菜单
     */
    private fun showAIPopup() {
        hideAIButton() // 先隐藏AI按钮
        
        // 检查是否应该显示增强面板
        if (shouldShowEnhancedPanel()) {
            showEnhancedAIPanel()
            return
        }
        
        if (aiPopupView == null) {
            // 使用 AppCompat 主题来确保 Material 组件能正确解析
            val appCompatContext = ContextThemeWrapper(
                this,
                androidx.appcompat.R.style.Theme_AppCompat_Light_NoActionBar
            )
            val inflater = LayoutInflater.from(appCompatContext)
            try {
                aiPopupView = inflater.inflate(R.layout.ai_buttons_popup, null)
                // 设置AI按钮点击事件
                setupAIButtons()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to inflate AI popup layout with AppCompat theme", e)
                // 如果仍然失败，尝试使用 MaterialComponents 主题
                try {
                    val materialContext = ContextThemeWrapper(
                        this,
                        com.google.android.material.R.style.Theme_MaterialComponents_Light_NoActionBar
                    )
                    val materialInflater = LayoutInflater.from(materialContext)
                    aiPopupView = materialInflater.inflate(R.layout.ai_buttons_popup, null)
                    setupAIButtons()
                } catch (e2: Exception) {
                    Log.e(TAG, "Failed to inflate AI popup layout with MaterialComponents theme", e2)
                    Toast.makeText(this, "无法加载AI助手", Toast.LENGTH_SHORT).show()
                    return
                }
            }
        }
        
        if (aiPopupView?.parent != null) {
            return
        }
        
        val input = searchInput ?: return
        input.post {
            if (aiPopupView?.parent != null || !input.isShown) {
                return@post
            }
            
            // 测量弹出菜单尺寸
            aiPopupView?.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            val popupWidth = aiPopupView?.measuredWidth ?: 0
            val popupHeight = aiPopupView?.measuredHeight ?: 0
            
            val locationOnScreen = IntArray(2)
            input.getLocationOnScreen(locationOnScreen)
            val inputX = locationOnScreen[0]
            val inputY = locationOnScreen[1]
            val inputWidth = input.width
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = inputX + (inputWidth - popupWidth) / 2 // 居中显示
                y = inputY + input.height + 10 // 在输入框下方10dp处显示
            }
            
            try {
                windowManager.addView(aiPopupView, params)
                
                // 3秒后自动隐藏
                aiButtonHandler.postDelayed({
                    hideAIPopup()
                }, 3000)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add AI popup", e)
            }
        }
    }
    
    /**
     * 判断是否应该显示增强面板
     */
    private fun shouldShowEnhancedPanel(): Boolean {
        // 可以根据用户设置或使用频率来决定
        return false // 暂时使用简单弹出菜单
    }
    
    /**
     * 显示增强的AI助手面板
     */
    private fun showEnhancedAIPanel() {
        hideAIButton() // 先隐藏AI按钮
        
        if (aiAssistantPanelView == null) {
            val context = themedContext ?: this
            val inflater = LayoutInflater.from(context)
            aiAssistantPanelView = inflater.inflate(R.layout.ai_assistant_panel, null)
            
            // 设置面板按钮点击事件
            setupAIAssistantPanel()
        }
        
        if (aiAssistantPanelView?.parent != null) {
            return
        }
        
        val input = searchInput ?: return
        input.post {
            if (aiAssistantPanelView?.parent != null || !input.isShown) {
                return@post
            }
            
            // 测量面板尺寸
            aiAssistantPanelView?.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            val panelWidth = aiAssistantPanelView?.measuredWidth ?: 0
            val panelHeight = aiAssistantPanelView?.measuredHeight ?: 0
            
            val locationOnScreen = IntArray(2)
            input.getLocationOnScreen(locationOnScreen)
            val inputX = locationOnScreen[0]
            val inputY = locationOnScreen[1]
            val inputWidth = input.width
            
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = inputX + (inputWidth - panelWidth) / 2 // 居中显示
                y = inputY + input.height + 10 // 在输入框下方10dp处显示
            }
            
            try {
                windowManager.addView(aiAssistantPanelView, params)
                
                // 5秒后自动隐藏
                aiButtonHandler.postDelayed({
                    hideEnhancedAIPanel()
                }, 5000)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add AI assistant panel", e)
            }
        }
    }
    
    /**
     * 隐藏增强的AI助手面板
     */
    private fun hideEnhancedAIPanel() {
        if (aiAssistantPanelView?.parent != null) {
            try {
                windowManager.removeView(aiAssistantPanelView)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove AI assistant panel", e)
            }
        }
    }
    
    /**
     * 设置AI助手面板的按钮事件
     */
    private fun setupAIAssistantPanel() {
        // 设置关闭按钮
        aiAssistantPanelView?.findViewById<ImageButton>(R.id.close_panel_button)?.setOnClickListener {
            hideEnhancedAIPanel()
        }
        
        // 设置快速操作按钮
        aiAssistantPanelView?.findViewById<com.google.android.material.button.MaterialButton>(R.id.voice_input_button)?.setOnClickListener {
            // 启动语音输入
            Toast.makeText(this, "语音输入功能", Toast.LENGTH_SHORT).show()
        }
        
        aiAssistantPanelView?.findViewById<com.google.android.material.button.MaterialButton>(R.id.history_button)?.setOnClickListener {
            // 显示历史记录
            Toast.makeText(this, "历史记录功能", Toast.LENGTH_SHORT).show()
        }
        
        aiAssistantPanelView?.findViewById<com.google.android.material.button.MaterialButton>(R.id.settings_button)?.setOnClickListener {
            // 打开设置
            Toast.makeText(this, "AI设置功能", Toast.LENGTH_SHORT).show()
        }
        
        // 设置AI应用按钮
        val aiAppIds = listOf(R.id.ai_app_1, R.id.ai_app_2, R.id.ai_app_3, R.id.ai_app_4, R.id.ai_app_5)
        val aiStatusIds = listOf(R.id.ai_status_1, R.id.ai_status_2, R.id.ai_status_3, R.id.ai_status_4, R.id.ai_status_5)
        
        // 获取常用AI配置
        val favoriteAIManager = com.example.aifloatingball.manager.FavoriteAIManager.getInstance(this)
        val appSearchSettings = com.example.aifloatingball.model.AppSearchSettings.getInstance(this)
        val allConfigs = appSearchSettings.getAppConfigs()
        val favoriteAIs = favoriteAIManager.getFavoriteAIConfigs(allConfigs)
        
        // 默认AI配置
        val defaultAIs = listOf(
            Triple("chatgpt", "ChatGPT", "com.openai.chatgpt"),
            Triple("kimi", "Kimi", "com.moonshot.kimichat"), 
            Triple("deepseek", "DeepSeek", "com.deepseek.chat"),
            Triple("gemini", "Gemini", "com.google.android.apps.gemini"),
            Triple("claude", "Claude", "com.anthropic.claude")
        )
        
        val displayAIs = mutableListOf<Triple<String, String, String>>()
        favoriteAIs.take(5).forEach { config ->
            displayAIs.add(Triple(config.appId, config.appName, config.packageName))
        }
        if (displayAIs.size < 5) {
            val usedAppIds = displayAIs.map { it.first }.toSet()
            defaultAIs.filter { it.first !in usedAppIds }
                .take(5 - displayAIs.size)
                .forEach { displayAIs.add(it) }
        }
        
        aiAppIds.forEachIndexed { index, buttonId ->
            val button = aiAssistantPanelView?.findViewById<com.google.android.material.button.MaterialButton>(buttonId)
            val statusView = aiAssistantPanelView?.findViewById<View>(aiStatusIds[index])
            
            if (index < displayAIs.size) {
                val (appId, appName, packageName) = displayAIs[index]
                button?.text = appName
                button?.visibility = View.VISIBLE
                button?.setOnClickListener {
                    hideEnhancedAIPanel()
                    launchAIApp(packageName, appName)
                }
                
                // 设置状态指示器
                val isInstalled = isAppInstalled(packageName)
                statusView?.setBackgroundResource(
                    if (isInstalled) R.drawable.ai_status_indicator 
                    else R.drawable.ai_status_indicator_offline
                )
                statusView?.visibility = View.VISIBLE
                
            } else {
                button?.visibility = View.GONE
                statusView?.visibility = View.GONE
            }
        }
        
        // 更多按钮
        aiAssistantPanelView?.findViewById<com.google.android.material.button.MaterialButton>(R.id.more_ai_button)?.setOnClickListener {
            Toast.makeText(this, "更多AI应用", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 隐藏AI弹出菜单
     */
    private fun hideAIPopup() {
        if (aiPopupView?.parent != null) {
            try {
                windowManager.removeView(aiPopupView)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove AI popup", e)
            }
        }
    }
    
    /**
     * 设置AI按钮点击事件
     */
    private fun setupAIButtons() {
        val context = themedContext ?: this
        
        // 获取常用AI配置
        val favoriteAIManager = com.example.aifloatingball.manager.FavoriteAIManager.getInstance(context)
        val appSearchSettings = com.example.aifloatingball.model.AppSearchSettings.getInstance(context)
        val allConfigs = appSearchSettings.getAppConfigs()
        val favoriteAIs = favoriteAIManager.getFavoriteAIConfigs(allConfigs)
        
        // 默认AI配置（如果常用AI不足5个，用这些补充）
        val defaultAIs = listOf(
            Triple("chatgpt", "ChatGPT", "com.openai.chatgpt"),
            Triple("kimi", "Kimi", "com.moonshot.kimichat"), 
            Triple("deepseek", "DeepSeek", "com.deepseek.chat"),
            Triple("gemini", "Gemini", "com.google.android.apps.gemini"),
            Triple("claude", "Claude", "com.anthropic.claude")
        )
        
        // 合并常用AI和默认AI，最多显示5个
        val displayAIs = mutableListOf<Triple<String, String, String>>()
        
        // 先添加常用AI
        favoriteAIs.take(5).forEach { config ->
            displayAIs.add(Triple(config.appId, config.appName, config.packageName))
        }
        
        // 如果不足5个，添加默认AI
        if (displayAIs.size < 5) {
            val usedAppIds = displayAIs.map { it.first }.toSet()
            defaultAIs.filter { it.first !in usedAppIds }
                .take(5 - displayAIs.size)
                .forEach { displayAIs.add(it) }
        }
        
        // 设置按钮
        val buttonIds = listOf(R.id.ai_button_1, R.id.ai_button_2, R.id.ai_button_3, R.id.ai_button_4, R.id.ai_button_5)
        val statusIds = listOf(R.id.ai_status_1, R.id.ai_status_2, R.id.ai_status_3, R.id.ai_status_4, R.id.ai_status_5)
        
        // 更新AI数量显示
        val aiCountText = aiPopupView?.findViewById<TextView>(R.id.ai_count_text)
        aiCountText?.text = "${displayAIs.size}个可用"
        
        buttonIds.forEachIndexed { index, buttonId ->
            val button = aiPopupView?.findViewById<com.google.android.material.button.MaterialButton>(buttonId)
            val statusView = aiPopupView?.findViewById<View>(statusIds[index])
            
            if (index < displayAIs.size) {
                val (appId, appName, packageName) = displayAIs[index]
                button?.text = appName
                button?.visibility = View.VISIBLE
                button?.setOnClickListener {
                    hideAIPopup()
                    launchAIApp(packageName, appName)
                }
                
                // 设置状态指示器
                val isInstalled = isAppInstalled(packageName)
                statusView?.setBackgroundResource(
                    if (isInstalled) R.drawable.ai_status_indicator 
                    else R.drawable.ai_status_indicator_offline
                )
                statusView?.visibility = View.VISIBLE
                
                // 异步加载AI应用图标
                loadAIIconAsync(appId, appName, packageName, button, isInstalled)
                
            } else {
                button?.visibility = View.GONE
                statusView?.visibility = View.GONE
            }
        }
    }
    
    /**
     * 启动AI应用
     */
    private fun launchAIApp(packageName: String, appName: String) {
        try {
            val query = searchInput?.text?.toString()?.trim() ?: ""
            
            // 将查询内容复制到剪贴板
            if (query.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("AI问题", query)
                clipboard.setPrimaryClip(clip)
            }
            
            // 启动AI应用
            val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(launchIntent)
                Toast.makeText(this, "正在启动$appName...", Toast.LENGTH_SHORT).show()
                
                // 延迟显示悬浮窗提示
                aiButtonHandler.postDelayed({
                    showAIAppOverlay(packageName, query, appName)
                }, 2000)
                
            } else {
                Toast.makeText(this, "$appName 未安装", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "启动AI应用失败: $appName", e)
            Toast.makeText(this, "启动失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 显示AI应用悬浮窗
     */
    private fun showAIAppOverlay(packageName: String, query: String, appName: String) {
        try {
            val intent = Intent(this, com.example.aifloatingball.service.AIAppOverlayService::class.java).apply {
                action = com.example.aifloatingball.service.AIAppOverlayService.ACTION_SHOW_OVERLAY
                putExtra(com.example.aifloatingball.service.AIAppOverlayService.EXTRA_APP_NAME, appName)
                putExtra(com.example.aifloatingball.service.AIAppOverlayService.EXTRA_QUERY, query)
                putExtra(com.example.aifloatingball.service.AIAppOverlayService.EXTRA_PACKAGE_NAME, packageName)
            }
            startService(intent)
            
        } catch (e: Exception) {
            Log.e(TAG, "显示AI应用悬浮窗失败: $appName", e)
        }
    }
    
    /**
     * 检查应用是否已安装
     */
    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    /**
     * 异步加载AI应用图标 - 参考软件tab的实现
     */
    private fun loadAIIconAsync(appId: String, appName: String, packageName: String, button: com.google.android.material.button.MaterialButton?, isInstalled: Boolean) {
        if (button == null) return
        
        // 使用协程异步加载图标
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // 1. 优先使用已安装应用的真实图标
                if (isInstalled) {
                    try {
                        val realIcon = packageManager.getApplicationIcon(packageName)
                        val iconResId = getCustomIconResourceId(appId)
                        if (iconResId != 0) {
                            button.setIconResource(iconResId)
                        } else {
                            // 使用真实图标
                            button.icon = realIcon
                        }
                        return@launch
                    } catch (e: Exception) {
                        // 继续尝试其他方法
                    }
                }
                
                // 2. 尝试使用预设的高质量图标资源
                val iconResId = getCustomIconResourceId(appId)
                if (iconResId != 0) {
                    button.setIconResource(iconResId)
                    return@launch
                }
                
                // 3. 尝试从在线获取图标
                val onlineIcon = withContext(Dispatchers.IO) {
                    getOnlineAIIcon(appName, packageName)
                }
                if (onlineIcon != null) {
                    button.icon = onlineIcon
                    return@launch
                }
                
                // 4. 使用默认AI图标
                button.setIconResource(R.drawable.ic_ai)
                
            } catch (e: Exception) {
                Log.e(TAG, "加载AI图标失败: $appName", e)
                button.setIconResource(R.drawable.ic_ai)
            }
        }
    }
    
    /**
     * 根据应用ID获取自定义图标资源ID
     */
    private fun getCustomIconResourceId(appId: String): Int {
        return when (appId.lowercase()) {
            "chatgpt", "gpt" -> R.drawable.ic_chatgpt
            "kimi" -> R.drawable.ic_kimi
            "deepseek" -> R.drawable.ic_deepseek
            "gemini" -> R.drawable.ic_gemini
            "claude" -> R.drawable.ic_claude
            "chatglm" -> R.drawable.ic_chatglm
            "perplexity" -> R.drawable.ic_perplexity
            "copilot" -> R.drawable.ic_copilot
            "manus" -> R.drawable.ic_manus
            "ima" -> R.drawable.ic_ima
            else -> 0
        }
    }
    
    /**
     * 从在线获取AI图标
     */
    private suspend fun getOnlineAIIcon(appName: String, packageName: String): android.graphics.drawable.Drawable? {
        return try {
            // 使用PreciseIconManager获取精准图标
            val preciseIconManager = com.example.aifloatingball.manager.PreciseIconManager(this)
            preciseIconManager.getPreciseIcon(
                packageName, 
                appName, 
                com.example.aifloatingball.manager.PreciseIconManager.IconType.AI_APP
            )
        } catch (e: Exception) {
            Log.e(TAG, "从在线获取AI图标失败: $appName", e)
            null
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

    private fun showVoiceRecognitionAnimation() {
        floatingBallIcon?.apply {
            setImageResource(R.drawable.avd_voice_ripple)
            val drawable = this.drawable
            if (drawable is android.graphics.drawable.AnimatedVectorDrawable) {
                drawable.start()
            }
        }

        val intent = Intent(this, VoiceRecognitionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun stopVoiceRecognitionAnimation() {
        floatingBallIcon?.apply {
            // This method is empty as the original implementation was removed
        }
    }

    private fun snapToEdge() {
        val screenWidth = getScreenWidth()
        val isLeftHanded = settingsManager.isLeftHandedModeEnabled()

        params?.x = if (isLeftHanded) {
            0
        } else {
            screenWidth - (floatingView?.width ?: 0)
        }

        try {
            val currentParams = params
            if (floatingView?.isAttachedToWindow == true && currentParams != null) {
                windowManager.updateViewLayout(floatingView, currentParams)
                // 保存最后的位置
                settingsManager.setFloatingBallPosition(currentParams.x, currentParams.y)
            }
        } catch (e: IllegalArgumentException) {
            android.util.Log.w("FloatingWindowService", "View not attached during snapToEdge", e)
        }
    }

    private fun getScreenWidth(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            windowMetrics.bounds.width() - insets.left - insets.right
        } else {
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay
            val size = Point()
            @Suppress("DEPRECATION")
            display.getSize(size)
            size.x
        }
    }

    private fun isMainMenuVisible(): Boolean {
        return isMenuVisible
    }

    private fun showMainMenu() {
        showSearchInterface()
    }

    private fun hideMainMenu() {
        hideSearchInterface()
    }

    private fun updateFloatingBallPosition() {
        val newPosition = settingsManager.getFloatingBallPosition()
        params?.let {
            it.x = newPosition.first
            it.y = newPosition.second
            if (floatingView?.isAttachedToWindow == true) {
                windowManager.updateViewLayout(floatingView, it)
            }
        }
    }

    private fun setupSearchInputListener() {
        searchInput?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val query = s.toString()
                if (query.isNotEmpty()) {
                    val appResults = appInfoManager.search(query)
                    if (appResults.isNotEmpty()) {
                        showAppSearchResults(appResults)
                    } else {
                        hideAppSearchResults()
                    }
                } else {
                    hideAppSearchResults()
                }
                // 更新应用搜索图标（根据输入文字智能匹配）
                updateAppSearchIcons()
            }
        })
    }

    private fun showAppSearchResults(results: List<AppInfo>) {
        if (appSearchAdapter == null) {
            appSearchAdapter = AppSearchAdapter(results, isHorizontal = true) { appInfo ->
                // Launch the app
                val intent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    hideSearchInterface() // Hide menu after launch
                    hideAppSearchResults()
                } else {
                    Toast.makeText(this, "无法启动该应用", Toast.LENGTH_SHORT).show()
                }
            }
            appSearchRecyclerView?.apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                adapter = appSearchAdapter
            }
        } else {
            appSearchAdapter?.updateData(results)
        }

        appSearchResultsContainer?.visibility = View.VISIBLE
    }

    private fun hideAppSearchResults() {
        appSearchResultsContainer?.visibility = View.GONE
    }
}