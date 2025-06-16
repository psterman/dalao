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
import android.graphics.PixelFormat
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.os.IBinder
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
import androidx.core.app.NotificationCompat
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsActivity
import com.example.aifloatingball.SettingsManager
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

    private var floatingBallIcon: FloatingActionButton? = null
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
    private var isMenuVisible: Boolean = false

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
        initializeViews()
        setupFloatingBall()
        loadSearchEngines()
        loadSavedCombos()
        loadAndDisplayAppSearch()
        startForegroundService()

        val filter = IntentFilter("com.example.aifloatingball.SETTINGS_CHANGED")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(settingsChangeReceiver, filter, RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(settingsChangeReceiver, filter)
        }
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
        unregisterReceiver(settingsChangeReceiver)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            "search_engine_shortcuts" -> loadSavedCombos()
            "floating_window_display_mode" -> {
                if (isMenuVisible) updateSearchModeVisibility()
            }
        }
    }

    private fun initializeViews() {
        // Create a themed context
        val themedContext = ContextThemeWrapper(this, R.style.Theme_FloatingWindow)
        val inflater = LayoutInflater.from(themedContext)
        floatingView = inflater.inflate(R.layout.floating_ball_layout, null)

        // Add a touch listener to the root view to detect outside touches
        floatingView?.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE && isMenuVisible) {
                hideSearchInterface()
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

        updateSearchModeVisibility()
    }

    private fun setupFloatingBall() {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100
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
    }

    private fun performSearch(query: String) {
        if (query.isNotBlank()) {
            // Default to starting the dual webview service with the first regular engine
            val engineName = regularSearchEngines.firstOrNull()?.name ?: SearchEngine.DEFAULT_ENGINES.first().name
            val serviceIntent = Intent(this, DualFloatingWebViewService::class.java).apply {
                putExtra("search_query", query)
                putExtra("engine_key", engineName)
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
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (!isMoving && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                        isMoving = true
                    }
                    if (isMoving) {
                        params?.x = initialX + dx.toInt()
                        params?.y = initialY + dy.toInt()
                        windowManager.updateViewLayout(floatingView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMoving) {
                        if (isMenuVisible) {
                            hideSearchInterface()
                        } else {
                            showSearchInterface()
                        }
                    } else {
                        snapToEdge()
                    }
                    isMoving = false
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEdge() {
        val screenWidth: Int
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val insets = windowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
            screenWidth = windowMetrics.bounds.width() - insets.left - insets.right
        } else {
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay
            val size = Point()
            @Suppress("DEPRECATION")
            display.getSize(size)
            screenWidth = size.x
        }

        if ((params?.x ?: 0) < screenWidth / 2) {
            params?.x = 0
        } else {
            params?.x = screenWidth - (floatingView?.width ?: 0)
        }
        windowManager.updateViewLayout(floatingView, params)
    }

    private fun showSearchInterface() {
        if (isMenuVisible) return
        isMenuVisible = true

        searchContainer?.visibility = View.VISIBLE
        updateSearchModeVisibility()

        params?.flags = params?.flags?.and(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv())
            ?.or(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH)
        @Suppress("DEPRECATION")
        params?.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN
        windowManager.updateViewLayout(floatingView, params)

        searchInput?.requestFocus()
        showKeyboard()
    }

    private fun hideSearchInterface() {
        if (!isMenuVisible) return
        isMenuVisible = false

        searchContainer?.animate()?.alpha(0f)?.setDuration(200)?.withEndAction {
            searchContainer?.visibility = View.GONE
            searchContainer?.alpha = 1f
        }

        hideKeyboard()

        params?.flags = params?.flags?.or(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            ?.and(WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH.inv())
        params?.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
        windowManager.updateViewLayout(floatingView, params)
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

            // AI engines can be opened without a query to see their homepage.
            // Regular engines require a query.
            if (query.isNotBlank() || isAiEngine) {
                val serviceIntent = Intent(this, DualFloatingWebViewService::class.java).apply {
                    putExtra("search_query", query) // Query can be blank for AI engines
                    putExtra("engine_key", engineName)
                }
                startService(serviceIntent)
                hideSearchInterface()
            } else {
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
}