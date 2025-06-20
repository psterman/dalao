package com.example.aifloatingball.service

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
import com.example.aifloatingball.utils.FaviconLoader
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

class FloatingWindowService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var params: WindowManager.LayoutParams? = null

    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0.toFloat()
    private var initialTouchY: Float = 0.toFloat()
    private var isMoving: Boolean = false
    private val touchSlop: Int by lazy { ViewConfiguration.get(this).scaledTouchSlop }

    private val idleHandler = Handler(android.os.Looper.getMainLooper())
    private var fadeOutRunnable: Runnable? = null

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

    private lateinit var settingsManager: SettingsManager
    private lateinit var appSearchSettings: AppSearchSettings
    private lateinit var textSelectionManager: TextSelectionManager
    private var isMenuVisible: Boolean = false
    private var themedContext: Context? = null // Make themedContext a member variable

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

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "FloatingWindowServiceChannel"
        private const val TAG = "FloatingWindowService"
        const val ACTION_SHOW_SEARCH = "com.example.aifloatingball.service.SHOW_SEARCH"
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager.getInstance(this)
        appSearchSettings = AppSearchSettings.getInstance(this)
        settingsManager.registerOnSharedPreferenceChangeListener(this)

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        textSelectionManager = TextSelectionManager(this, windowManager)
        initializeViews()
        setupFloatingBall()
        loadSearchEngines()
        loadSavedCombos()
        loadAndDisplayAppSearch()
        startForegroundService()
        resetIdleTimer() // Start idle timer on creation
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHOW_SEARCH) {
            showSearchInterface()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingView != null) windowManager.removeView(floatingView)
        settingsManager.unregisterOnSharedPreferenceChangeListener(this)
        idleHandler.removeCallbacksAndMessages(null) // Clean up handler
        hidePasteButton()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            "theme_mode" -> recreateViews()
            "search_engine_shortcuts" -> loadSavedCombos()
            "floating_window_display_mode" -> {
                if (isMenuVisible) updateSearchModeVisibility()
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

        // Repopulate the content. This was the missing piece.
        loadSearchEngines()
        loadSavedCombos()
        loadAndDisplayAppSearch()

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
            showSearchInterface(shouldAnimate = false)
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
        setupSearchInput()

        val searchModeButton = floatingView?.findViewById<ImageButton>(R.id.search_mode_button)
        searchModeButton?.setOnClickListener { view ->
            showSearchModeMenu(view)
        }

        val clearButton = floatingView?.findViewById<ImageButton>(R.id.clear_button)
        clearButton?.setOnClickListener {
            searchInput?.text?.clear()
        }

        // Programmatically set the tint for the clear button based on the theme
        themedContext?.let {
            val nightModeFlags = it.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
            val tintColor = if (nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES) {
                android.graphics.Color.WHITE
            } else {
                android.graphics.Color.DKGRAY
            }
            clearButton?.setColorFilter(tintColor, android.graphics.PorterDuff.Mode.SRC_IN)
        }

        updateSearchModeVisibility()
        updateBallAlpha()
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
        val isLeftHanded = settingsManager.isLeftHandModeEnabled()

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
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun performSearch(query: String) {
        if (query.isNotBlank()) {
            // 修复：不再使用此服务中的第一个引擎作为默认值，
            // 而是从设置中获取为DualFloatingWebViewService的第一个窗口配置的默认引擎。
            val engineName = settingsManager.getSearchEngineForPosition(0)
            val serviceIntent = Intent(this, DualFloatingWebViewService::class.java).apply {
                putExtra("search_query", query)
                putExtra("engine_key", engineName)
                putExtra("search_source", "悬浮窗")
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
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params?.x ?: 0
                    initialY = params?.y ?: 0
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoving = false
                    resetIdleTimer() // Reset timer on touch
                    
                    // 安排一个长按任务
                    longPressRunnable = Runnable {
                        // 长按被触发
                        isMoving = true // 将其标记为"移动"，以防止后续的单击事件
                        Log.d(TAG, "长按检测到，启动语音识别")
                        val intent = Intent(this, VoiceRecognitionActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        startActivity(intent)
                        // 可以在这里添加震动反馈
                    }
                    longPressHandler.postDelayed(longPressRunnable!!, ViewConfiguration.getLongPressTimeout().toLong())
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!isMoving && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                        isMoving = true
                        // 如果用户开始移动，则取消长按任务
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    }
                    if (isMoving) {
                        params?.x = initialX + dx.toInt()
                        params?.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(floatingView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // 手指抬起，取消长按任务
                    longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    
                    if (isMoving) {
                        // Just save the new position, don't snap to edge.
                        params?.let {
                            settingsManager.setFloatingBallPosition(it.x, it.y)
                        }
                        resetIdleTimer()
                    } else {
                        // Toggle menu visibility on click
                        if (isMenuVisible) {
                            hideSearchInterface()
                        } else {
                            showSearchInterface()
                        }
                    }
                    isMoving = false
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEdge() {
        val screenWidth = getScreenWidth()
        val isLeftHanded = settingsManager.isLeftHandModeEnabled()

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

    private fun showSearchInterface(shouldAnimate: Boolean = true) {
        if (isMenuVisible) return
        isMenuVisible = true
        cancelIdleTimer() // Don't fade while menu is open
        floatingBallIcon?.animate()?.alpha(1f)?.setDuration(150)?.start() // Ensure ball is fully visible

        val menuWidth = resources.getDimensionPixelSize(R.dimen.floating_menu_width)

        // Adjust position before showing menu to keep ball stationary
        if (!settingsManager.isLeftHandModeEnabled()) { // Right-handed, menu appears on the left
            params?.x = params?.x?.minus(menuWidth)
        }

        val contentContainer = floatingView?.findViewById<LinearLayout>(R.id.floating_view_content_container)
        val layoutParams = contentContainer?.layoutParams as? FrameLayout.LayoutParams
        // Re-order views for handedness
        val searchContainer = floatingView?.findViewById<View>(R.id.search_container)
        val ballIcon = floatingView?.findViewById<View>(R.id.floating_ball_icon)
        contentContainer?.removeView(searchContainer)
        contentContainer?.removeView(ballIcon)
        if (settingsManager.isLeftHandModeEnabled()) {
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
            if (!settingsManager.isLeftHandModeEnabled()) { // Right-handed, menu was on the left
                params?.x = params?.x?.plus(menuWidth)
            }
            params?.flags = params?.flags?.or(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                ?.and(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH.inv())

            windowManager.updateViewLayout(floatingView, params)
        }

        hideKeyboard()
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
        aiSearchEngines = AISearchEngine.DEFAULT_AI_ENGINES.toList()
        regularSearchEngines = SearchEngine.DEFAULT_ENGINES.toList()
        updateSearchEngineDisplay()
    }

    private fun updateSearchEngineDisplay() {
        aiEnginesContainer?.removeAllViews()
        regularEnginesContainer?.removeAllViews()

        addEnginesToContainer(aiEnginesContainer, aiSearchEngines)
        addEnginesToContainer(regularEnginesContainer, regularSearchEngines)
    }

    private fun addEnginesToContainer(container: LinearLayout?, engines: List<Any>) {
        if (container == null || engines.isEmpty()) return

        val horizontalScrollView = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isHorizontalScrollBarEnabled = false
        }
        val linearLayout = LinearLayout(this).apply {
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
    }

    private fun createEngineView(engine: Any): View {
        val view = View.inflate(this, R.layout.search_engine_shortcut, null)
        val icon = view.findViewById<ImageView>(R.id.shortcut_icon)
        val name = view.findViewById<TextView>(R.id.shortcut_name)

        val url: String
        val engineName: String
        when (engine) {
            is SearchEngine -> {
                url = engine.url
                engineName = engine.name
            }
            is AISearchEngine -> {
                url = engine.url
                engineName = engine.name
            }
            else -> return view // Should not happen
        }
        FaviconLoader.loadIcon(icon, url, R.drawable.ic_search)
        name.text = engineName
        view.setOnClickListener {
            val query = searchInput?.text.toString()
            val isAiEngine = engine is AISearchEngine

            // 修复逻辑：对于AI引擎，无论是否有查询，都应启动服务。
            // 对于普通引擎，则需要查询。
            if (isAiEngine || query.isNotBlank()) {
                val serviceIntent = Intent(this, DualFloatingWebViewService::class.java).apply {
                    putExtra("search_query", query) // 查询可以为空
                    putExtra("engine_key", engineName)
                    putExtra("search_source", "悬浮窗")
                }
                startService(serviceIntent)
                hideSearchInterface()
            } else {
                // 此分支现在仅在普通引擎且无查询时触发
                Toast.makeText(this, "请输入搜索内容", Toast.LENGTH_SHORT).show()
            }
        }
        return view
    }

    private fun loadSavedCombos() {
        val json = settingsManager.getSearchEngineShortcuts()
        if (json.isNotEmpty()) {
            val type = object : TypeToken<List<SearchEngineShortcut>>() {}.type
            try {
                searchEngineShortcuts = gson.fromJson(json, type)
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
        val isLeftHanded = settingsManager.isLeftHandModeEnabled()

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
}