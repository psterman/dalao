package com.example.aifloatingball.service

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
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsActivity
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.VoiceRecognitionActivity
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.model.AppSearchConfig
import com.example.aifloatingball.model.AppSearchSettings
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.model.SearchEngineShortcut
import com.example.aifloatingball.service.NotificationHelper
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.net.URLEncoder
import com.example.aifloatingball.ui.text.TextSelectionManager
import android.text.Editable
import android.text.TextWatcher
import android.content.ClipDescription
import android.content.ClipboardManager
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

    // 新增：用于处理粘贴按钮的 Handler 和 Runnable
    private var pasteButtonView: View? = null
    private val pasteButtonHandler = Handler(Looper.getMainLooper())
    private var hidePasteButtonRunnable: Runnable? = null

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
        hidePasteButton()
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
                pasteButtonHandler.postDelayed({ showPasteButton() }, 100)
            } else {
                hidePasteButton()
            }
        }
        searchInput?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Hide the button as soon as the user starts typing
                hidePasteButton()
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
            val engineName = settingsManager.getSearchEngineForPosition(0)
            val serviceIntent = Intent(this, DualFloatingWebViewService::class.java).apply {
                putExtra("search_query", query)
                putExtra("engine_key", engineName)
                putExtra("search_source", "悬浮窗")
                putExtra("startTime", System.currentTimeMillis())
            }
            startService(serviceIntent)
            hideSearchInterface()
        }
    }

    private fun startForegroundService() {
        val channel = NotificationChannel(CHANNEL_ID, "AI Floating Ball Service", NotificationManager.IMPORTANCE_LOW)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)

        val notificationIntent = Intent(this, SettingsActivity::class.java)
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
                    isLongPress = false
                    isClick = true // Assume it's a click until proven otherwise
                    
                    // Schedule a task to run after the long-press timeout
                    longPressRunnable = Runnable {
                        isLongPress = true
                        isClick = false // A long press is not a click
                        vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
                        val action = settingsManager.getActionBallLongPress()
                        executeAction(action)
                    }
                    longPressHandler.postDelayed(longPressRunnable!!, ViewConfiguration.getLongPressTimeout().toLong())
                    
                    true // Consume the event
                }
                MotionEvent.ACTION_MOVE -> {
                    // Calculate the distance moved
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY

                    // If not already moving and movement exceeds touch slop, it's a drag
                    if (!isMoving && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                        isMoving = true
                        isClick = false // It's a drag, not a click
                        // A drag action cancels the pending long press
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    }

                    // If in moving state, update the ball's position
                    if (isMoving) {
                        params?.x = initialX + dx.toInt()
                        params?.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(floatingView, params)
                    }
                    true // Consume the event
                }
                MotionEvent.ACTION_UP -> {
                    // Always cancel any pending long press when the touch is released
                    longPressRunnable?.let { longPressHandler.removeCallbacks(it) }

                    if (isLongPress) {
                        // It's a long press, do nothing here as it's handled by the long press runnable
                    } else if (isClick) {
                        // It's a click
                        val action = settingsManager.getActionBallClick()
                        executeAction(action)
                    }
                    // Reset flags
                    isLongPress = false
                    isClick = false
                    
                    if (isMoving) {
                        // If it was a move, snap to the edge if enabled.
                        if (settingsManager.getAutoHide()) {
                            snapToEdge()
                        }
                    }
                    true // Consume the event
                }
                else -> false
            }
        }
    }

    private fun executeAction(action: String) {
        when (action) {
            "voice_recognize" -> showVoiceRecognition()
            "floating_menu" -> toggleSearchInterface()
            "dual_search" -> startDualSearch()
            "island_panel" -> { /* No-op in FloatingWindowService */ }
            "settings" -> openSettings()
            "none" -> { /* No-op */ }
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
        val intent = Intent(this, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        hideSearchInterface()
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

        // Make search container focusable
        params?.flags = params?.flags?.and(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv())
        windowManager.updateViewLayout(floatingView, params)

        searchInput?.requestFocus()
        if (settingsManager.isAutoPasteEnabled()) {
            autoPaste(searchInput)
        }
        showKeyboard()
        showPasteButton()
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
            if (!settingsManager.isLeftHandedModeEnabled()) { // Right-handed, menu was on the left
                params?.x = params?.x?.plus(menuWidth)
            }
            params?.flags = params?.flags?.or(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                ?.and(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH.inv())

            windowManager.updateViewLayout(floatingView, params)
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
                    val serviceIntent = Intent(this, DualFloatingWebViewService::class.java).apply {
                        putExtra("search_query", query)
                        putExtra("engine_key", engineName)
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
        appSearchContainer?.removeAllViews()
        val enabledAppConfigs = appSearchSettings.getEnabledAppConfigs()

        if(enabledAppConfigs.isEmpty()) return

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

        enabledAppConfigs.forEach { appConfig ->
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
                    val query = searchInput?.text?.toString() ?: ""
                    if (query.isNotEmpty()) {
                        openAppSearch(appConfig, query)
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

    private fun showPasteButton() {
        val input = searchInput ?: return
        if (!input.isShown) return // Don't show if the input field itself is not visible

        // Check clipboard for text
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (!clipboard.hasPrimaryClip() || clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) != true) {
            return // No text on clipboard, do nothing
        }

        if (pasteButtonView == null) {
            // Use themedContext to inflate the view, which is crucial
            val context = themedContext ?: this
            val inflater = LayoutInflater.from(context)
            pasteButtonView = inflater.inflate(R.layout.paste_button, null)

            pasteButtonView?.setOnClickListener {
                val pasteData = clipboard.primaryClip?.getItemAt(0)?.text?.toString()
                if (!pasteData.isNullOrEmpty()) {
                    val selectionStart = input.selectionStart
                    val selectionEnd = input.selectionEnd
                    input.text.replace(selectionStart.coerceAtMost(selectionEnd), selectionStart.coerceAtLeast(selectionEnd), pasteData, 0, pasteData.length)
                }
                hidePasteButton()
            }
        }

        // Ensure the view is not already added
        if (pasteButtonView?.parent != null) {
            return
        }

        // Post the positioning logic to run after the input view has been laid out,
        // ensuring the coordinates are for its final position.
        input.post {
            // Re-check if the button has been added or hidden in the meantime
            if (pasteButtonView?.parent != null || !input.isShown) {
                return@post
            }

            // Measure the paste button to get its width for centering
            pasteButtonView?.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            val pasteButtonWidth = pasteButtonView?.measuredWidth ?: 0

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
                x = inputX + (inputWidth - pasteButtonWidth) / 2 // Center the button horizontally
                y = inputY + input.height // Position directly below the input field
            }

            try {
                windowManager.addView(pasteButtonView, params)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add paste button", e)
            }

            // Schedule to hide
            hidePasteButtonRunnable = Runnable { hidePasteButton() }
            pasteButtonHandler.postDelayed(hidePasteButtonRunnable!!, 5000)
        }
    }

    private fun hidePasteButton() {
        hidePasteButtonRunnable?.let { pasteButtonHandler.removeCallbacks(it) }
        hidePasteButtonRunnable = null
        if (pasteButtonView?.parent != null) {
            try {
                windowManager.removeView(pasteButtonView)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove paste button", e)
            }
        }
        // Don't null out pasteButtonView here, so it can be reused
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

        windowManager.updateViewLayout(floatingView, params)
        // 保存最后的位置
        params?.let {
            settingsManager.setFloatingBallPosition(it.x, it.y)
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