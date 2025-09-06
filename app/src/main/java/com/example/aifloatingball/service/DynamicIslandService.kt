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
import android.view.WindowInsets
import android.widget.HorizontalScrollView
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
    
    // App Search Components
    private lateinit var appInfoManager: AppInfoManager
    private var appSearchRecyclerView: RecyclerView? = null
    private var appSearchAdapter: AppSearchAdapter? = null
    private var appSearchResultsContainer: View? = null
    private var closeAppSearchButton: View? = null
    
    // 最近选中的APP相关
    private var recentAppButton: MaterialButton? = null
    private var recentAppsDropdown: PopupWindow? = null
    private var recentAppAdapter: RecentAppAdapter? = null
    private val recentApps = mutableListOf<AppInfo>()
    private var currentSelectedApp: AppInfo? = null
    private var lastSearchQuery: String = ""
    
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
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
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
            // This action might open the multi-window search directly
            // It might be useful to pre-fill a query from clipboard if available
            val intent = Intent(this, DualFloatingWebViewService::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startService(intent)
            Log.d(TAG, "启动双搜索服务")
        } catch (e: Exception) {
            Log.e(TAG, "启动双搜索服务失败", e)
            Toast.makeText(this, "无法启动双搜索功能", Toast.LENGTH_SHORT).show()
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
        islandParams.height = 56.dpToPx() // Final height
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
        val aiResponseText = configPanelView?.findViewById<TextView>(R.id.ai_response_text)
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
                        val currentText = configPanelView?.findViewById<TextView>(R.id.ai_response_text)?.text?.toString() ?: ""
                        configPanelView?.findViewById<TextView>(R.id.ai_response_text)?.text = currentText + chunk
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
        
        // 获取当前选择的AI服务
        val spinner = configPanelView?.findViewById<Spinner>(R.id.ai_service_spinner)
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

        // Setup AI 助手窗口
        val aiAssistantContainer = configPanelView?.findViewById<MaterialCardView>(R.id.ai_assistant_container)
        val aiServiceSpinner = configPanelView?.findViewById<Spinner>(R.id.ai_service_spinner)
        val aiInputText = configPanelView?.findViewById<EditText>(R.id.ai_input_text)
        val aiResponseText = configPanelView?.findViewById<TextView>(R.id.ai_response_text)
        val btnAiSettings = configPanelView?.findViewById<ImageButton>(R.id.btn_ai_settings)
        val btnClearAiResponse = configPanelView?.findViewById<ImageButton>(R.id.btn_clear_ai_response)
        val btnSendAiMessage = configPanelView?.findViewById<ImageButton>(R.id.btn_send_ai_message)
        
        // 设置AI服务选择器
        setupAIServiceSpinner(aiServiceSpinner)
        
        // 设置按钮点击事件
        btnClearAiResponse?.setOnClickListener {
            aiResponseText?.text = "选择AI服务并输入问题获取回复..."
        }
        
        btnAiSettings?.setOnClickListener {
            // 打开AI设置页面
            val intent = Intent(this, AIApiConfigActivity::class.java)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
        
        btnSendAiMessage?.setOnClickListener {
            val query = aiInputText?.text?.toString()?.trim()
            if (!query.isNullOrEmpty()) {
                sendAIMessage(query, aiResponseText)
            }
        }
        
        // 设置输入框监听器
        aiInputText?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                val query = aiInputText?.text?.toString()?.trim()
                if (!query.isNullOrEmpty()) {
                    sendAIMessage(query, aiResponseText)
                }
                true
            } else {
                false
            }
        }

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

        // 设置按钮点击处理
        val settingsButton = configPanelView?.findViewById<View>(R.id.btn_settings)
        settingsButton?.setOnClickListener {
            animationView?.apply {
                visibility = View.VISIBLE
                playAnimation()
            }

            // 添加点击动画效果
            settingsButton.animate()
                .scaleX(0.95f)
                .scaleY(0.95f)
                .setDuration(100)
                .withEndAction {
                    settingsButton.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(100)
                        .start()
                }
                .start()

            // 打开设置页面
            openSettings()
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
            topMargin = statusBarHeight + 56.dpToPx() + 16.dpToPx()
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
            topMargin = statusBarHeight + 56.dpToPx() + 16.dpToPx()
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
            if (textActionMenu?.isShowing == true) {
                hideCustomTextMenu()
                return@setOnTouchListener true
            }

            if (isSearchModeActive && event.action == MotionEvent.ACTION_DOWN) {
                if (isTouchOutsideAllViews(event)) {
                    transitionToCompactState()
                    return@setOnTouchListener true // 消费掉外部点击事件
                }
            }
            false // 对于内部点击，不消费事件，让子视图处理
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
            animatingIslandView?.layoutParams?.width = it.animatedValue as Int
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
                Log.d(TAG, "文本变化: '$query', 当前选中应用: ${currentSelectedApp?.label}")
                
                if (query.isNotEmpty()) {
                    // 清除选中提示
                    if (currentSelectedApp != null) {
                        searchInput?.hint = "搜索应用或输入内容"
                        Log.d(TAG, "清除选中提示，当前选中应用: ${currentSelectedApp?.label}")
                    }
                    
                    // 确保AppInfoManager已加载
                    val appInfoManager = AppInfoManager.getInstance()
                    if (!appInfoManager.isLoaded()) {
                        Log.d(TAG, "AppInfoManager未加载，开始加载")
                        appInfoManager.loadApps(this@DynamicIslandService)
                        // 显示加载提示
                        showLoadingIndicator()
                        return@afterTextChanged
                    }
                    
                    // 实时搜索匹配的APP
                    performRealTimeSearch(query, appInfoManager)
                } else {
                    // 输入框为空时，显示常用APP图标
                    Log.d(TAG, "输入框为空，显示默认APP图标")
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
        Log.d(TAG, "搜索查询: '$query', 找到 ${appResults.size} 个结果")
        
        if (appResults.isNotEmpty()) {
            Log.d(TAG, "找到匹配的APP: ${appResults.map { it.label }}")
            showAppSearchResults(appResults)
        } else {
            // 没有匹配的APP时，显示支持URL scheme的APP图标
            Log.d(TAG, "没有匹配的APP，显示URL scheme APP图标")
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
        
        // 定义支持URL scheme的APP列表（更全面的列表）
        val urlSchemeAppConfigs = listOf(
            // 社交类
            Triple("com.tencent.mm", "weixin", "微信"),
            Triple("com.tencent.mobileqq", "mqqapi", "QQ"),
            Triple("com.tencent.wework", "wework", "企业微信"),
            Triple("com.tencent.tim", "tim", "TIM"),
            Triple("com.sina.weibo", "sinaweibo", "微博"),
            Triple("com.tencent.mm", "weixin", "微信"),
            
            // 购物类
            Triple("com.taobao.taobao", "taobao", "淘宝"),
            Triple("com.eg.android.AlipayGphone", "alipay", "支付宝"),
            Triple("com.jingdong.app.mall", "openapp.jdmobile", "京东"),
            Triple("com.pinduoduo", "pinduoduo", "拼多多"),
            
            // 视频类
            Triple("com.ss.android.ugc.aweme", "snssdk1128", "抖音"),
            Triple("com.ss.android.ugc.live", "snssdk1128", "抖音直播"),
            Triple("tv.danmaku.bili", "bilibili", "哔哩哔哩"),
            Triple("com.tencent.qqlive", "qqlive", "腾讯视频"),
            Triple("com.iqiyi.app", "iqiyi", "爱奇艺"),
            
            // 浏览器类
            Triple("com.tencent.mtt", "mttbrowser", "QQ浏览器"),
            Triple("com.UCMobile", "ucbrowser", "UC浏览器"),
            Triple("com.android.chrome", "googlechrome", "Chrome"),
            Triple("org.mozilla.firefox", "firefox", "Firefox"),
            
            // 工具类
            Triple("com.tencent.mm", "weixin", "微信"),
            Triple("com.tencent.mobileqq", "mqqapi", "QQ"),
            Triple("com.tencent.wework", "wework", "企业微信"),
            Triple("com.tencent.tim", "tim", "TIM"),
            Triple("com.sina.weibo", "sinaweibo", "微博"),
            Triple("com.taobao.taobao", "taobao", "淘宝"),
            Triple("com.eg.android.AlipayGphone", "alipay", "支付宝"),
            Triple("com.ss.android.ugc.aweme", "snssdk1128", "抖音"),
            Triple("com.tencent.mtt", "mttbrowser", "QQ浏览器"),
            Triple("com.UCMobile", "ucbrowser", "UC浏览器")
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
                val urlScheme = appInfo.urlScheme
                val intent = when (urlScheme) {
                    "weixin" -> {
                        // 微信搜索
                        Intent(Intent.ACTION_VIEW, Uri.parse("weixin://dl/search?query=${Uri.encode(searchQuery)}"))
                    }
                    "mqqapi" -> {
                        // QQ搜索
                        Intent(Intent.ACTION_VIEW, Uri.parse("mqqapi://search?query=${Uri.encode(searchQuery)}"))
                    }
                    "taobao" -> {
                        // 淘宝搜索
                        Intent(Intent.ACTION_VIEW, Uri.parse("taobao://search?q=${Uri.encode(searchQuery)}"))
                    }
                    "alipay" -> {
                        // 支付宝搜索
                        Intent(Intent.ACTION_VIEW, Uri.parse("alipay://search?query=${Uri.encode(searchQuery)}"))
                    }
                    "snssdk1128" -> {
                        // 抖音搜索
                        Intent(Intent.ACTION_VIEW, Uri.parse("snssdk1128://search?keyword=${Uri.encode(searchQuery)}"))
                    }
                    "sinaweibo" -> {
                        // 微博搜索
                        Intent(Intent.ACTION_VIEW, Uri.parse("sinaweibo://search?keyword=${Uri.encode(searchQuery)}"))
                    }
                    "wework" -> {
                        // 企业微信搜索
                        Intent(Intent.ACTION_VIEW, Uri.parse("wework://search?query=${Uri.encode(searchQuery)}"))
                    }
                    "tim" -> {
                        // TIM搜索
                        Intent(Intent.ACTION_VIEW, Uri.parse("tim://search?query=${Uri.encode(searchQuery)}"))
                    }
                    "mttbrowser" -> {
                        // QQ浏览器搜索
                        Intent(Intent.ACTION_VIEW, Uri.parse("mttbrowser://search?query=${Uri.encode(searchQuery)}"))
                    }
                    "ucbrowser" -> {
                        // UC浏览器搜索
                        Intent(Intent.ACTION_VIEW, Uri.parse("ucbrowser://search?query=${Uri.encode(searchQuery)}"))
                    }
                    else -> {
                        // 通用URL scheme
                        Intent(Intent.ACTION_VIEW, Uri.parse("$urlScheme://search?query=${Uri.encode(searchQuery)}"))
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
        Log.d(TAG, "添加到最近APP: ${appInfo.label}")
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
        // 使用已知的包名到URL scheme的映射
        val urlSchemeMap = mapOf(
            "com.tencent.mm" to "weixin",
            "com.tencent.mobileqq" to "mqqapi",
            "com.taobao.taobao" to "taobao",
            "com.eg.android.AlipayGphone" to "alipay",
            "com.ss.android.ugc.aweme" to "snssdk1128",
            "com.sina.weibo" to "sinaweibo",
            "com.tencent.wework" to "wework",
            "com.tencent.tim" to "tim",
            "com.tencent.mtt" to "mttbrowser",
            "com.UCMobile" to "ucbrowser",
            "com.android.chrome" to "googlechrome",
            "org.mozilla.firefox" to "firefox",
            "com.jingdong.app.mall" to "openapp.jdmobile",
            "com.pinduoduo" to "pinduoduo",
            "tv.danmaku.bili" to "bilibili",
            "com.tencent.qqlive" to "qqlive",
            "com.iqiyi.app" to "iqiyi"
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
                val urlScheme = appInfo.urlScheme
                val intent = when (urlScheme) {
                    "weixin" -> {
                        Intent(Intent.ACTION_VIEW, Uri.parse("weixin://dl/search?query=${Uri.encode(query)}"))
                    }
                    "mqqapi" -> {
                        Intent(Intent.ACTION_VIEW, Uri.parse("mqqapi://search?query=${Uri.encode(query)}"))
                    }
                    "taobao" -> {
                        Intent(Intent.ACTION_VIEW, Uri.parse("taobao://search?q=${Uri.encode(query)}"))
                    }
                    "alipay" -> {
                        Intent(Intent.ACTION_VIEW, Uri.parse("alipay://search?query=${Uri.encode(query)}"))
                    }
                    "snssdk1128" -> {
                        Intent(Intent.ACTION_VIEW, Uri.parse("snssdk1128://search?keyword=${Uri.encode(query)}"))
                    }
                    "sinaweibo" -> {
                        Intent(Intent.ACTION_VIEW, Uri.parse("sinaweibo://search?keyword=${Uri.encode(query)}"))
                    }
                    "wework" -> {
                        Intent(Intent.ACTION_VIEW, Uri.parse("wework://search?query=${Uri.encode(query)}"))
                    }
                    "tim" -> {
                        Intent(Intent.ACTION_VIEW, Uri.parse("tim://search?query=${Uri.encode(query)}"))
                    }
                    "mttbrowser" -> {
                        Intent(Intent.ACTION_VIEW, Uri.parse("mttbrowser://search?query=${Uri.encode(query)}"))
                    }
                    "ucbrowser" -> {
                        Intent(Intent.ACTION_VIEW, Uri.parse("ucbrowser://search?query=${Uri.encode(query)}"))
                    }
                    else -> {
                        Intent(Intent.ACTION_VIEW, Uri.parse("$urlScheme://search?query=${Uri.encode(query)}"))
                    }
                }

                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                // 执行搜索动作后退出搜索面板
                hideContent()
                Toast.makeText(this, "已跳转到${appInfo.label}搜索", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "跳转到APP搜索失败: ${appInfo.label}", e)
                showNoUrlSchemeDialog(query, appInfo)
            }
        } else {
            // 没有URL scheme，显示提示对话框
            showNoUrlSchemeDialog(query, appInfo)
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
            showQuickChatDialog()
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

        // 退出按钮
        btnExit?.setOnClickListener {
            Log.d(TAG, "退出按钮被点击")
            exitDynamicIsland()
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