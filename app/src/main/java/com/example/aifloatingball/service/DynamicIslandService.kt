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
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.HomeActivity
import com.example.aifloatingball.R
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
import com.example.aifloatingball.utils.FaviconLoader
import com.google.android.material.tabs.TabLayout
import android.content.res.Configuration
import android.util.TypedValue
import androidx.annotation.AttrRes
import android.content.pm.PackageManager
import com.example.aifloatingball.MasterPromptSettingsActivity
import com.google.android.material.button.MaterialButton
import android.content.ClipData
import android.content.ClipboardManager
import androidx.appcompat.app.AlertDialog
import android.widget.Toast
import android.content.ClipDescription
import android.util.Log
import com.example.aifloatingball.model.AppSearchConfig
import com.example.aifloatingball.VoiceRecognitionActivity
import com.example.aifloatingball.SettingsActivity
import android.os.VibrationEffect
import com.example.aifloatingball.adapter.AppSearchAdapter
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

    private var proxyIndicatorView: View? = null
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
        compactWidth = (islandWidth * displayMetrics.density).toInt()
        expandedWidth = (displayMetrics.widthPixels * 0.9).toInt()
        compactHeight = resources.getDimensionPixelSize(R.dimen.dynamic_island_compact_height)
        expandedHeight = resources.getDimensionPixelSize(R.dimen.dynamic_island_expanded_height)

        // 1. The Stage
        windowContainerView = FrameLayout(this)
        val stageParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, // Use full width for the stage
            WindowManager.LayoutParams.WRAP_CONTENT, // Adjust height to content
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START // Change gravity to START to avoid horizontal conflicts
            y = 0
        }

        // 2. The Animating View (Island itself, not the proxy bar)
        animatingIslandView = FrameLayout(this).apply {
            background = ColorDrawable(Color.TRANSPARENT)
            layoutParams = FrameLayout.LayoutParams(compactWidth, statusBarHeight, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
            visibility = View.GONE // Initially hidden
        }
        
        // 3. The Content
        islandContentView = inflater.inflate(R.layout.dynamic_island_layout, animatingIslandView, false)
        islandContentView?.background = ColorDrawable(Color.TRANSPARENT)
        notificationIconContainer = islandContentView!!.findViewById(R.id.notification_icon_container)
        // 移除永久隐藏通知图标的代码
        // notificationIconContainer.visibility = View.GONE // 删除这行
        appSearchIconScrollView = islandContentView!!.findViewById(R.id.app_search_icon_scroll_view)
        appSearchIconContainer = islandContentView!!.findViewById(R.id.app_search_icon_container)
        animatingIslandView!!.addView(islandContentView)

        // 4. Create a touch target that spans the full width to avoid clipping
        val touchTargetView = FrameLayout(this).apply {
            id = R.id.touch_target_view // Set an ID to find this view later
            // Use full width to prevent clipping when the indicator moves to screen edges
            val touchableWidth = WindowManager.LayoutParams.MATCH_PARENT
            // A reasonable height for a touch target below the status bar
            val touchableHeight = 36.dpToPx() 
            
            layoutParams = FrameLayout.LayoutParams(touchableWidth, touchableHeight, Gravity.TOP or Gravity.START).apply {
                // Position the entire touch target just below the status bar
                topMargin = statusBarHeight
            }
            
            // Don't set click listeners on the container to avoid wide touch area
        }

        // 5. The Proxy Indicator (Enhanced Dynamic Island), now inside the touch target with its own click handling
        proxyIndicatorView = DynamicIslandIndicatorView(this).apply {
            // The new DynamicIslandIndicatorView handles its own sizing and touch area
            (this as View).layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.START or Gravity.TOP
            )
            
            setOnClickListener {
                if (!isSearchModeActive) {
                    // 检查是否有通知需要显示
                    if (activeNotifications.isNotEmpty()) {
                        showNotificationExpandedView()
                    } else {
                        // Show app icon based on action
                        val action = settingsManager.getActionIslandClick()
                        showActionIcon(action)
                        executeAction(action)
                    }
                }
            }
            setOnLongClickListener {
                if (!isSearchModeActive && !isDragging()) {
                    val action = settingsManager.getActionIslandLongPress()
                    showActionIcon(action)
                    executeAction(action)
                    true // Consume the long click
                } else {
                    false
                }
            }
            
            // 设置拖拽监听器
            setOnDragListener(object : DynamicIslandIndicatorView.OnDragListener {
                private var isDragInProgress = false
                
                override fun onDragStart() {
                    isDragInProgress = true
                    Log.d(TAG, "拖拽开始")
                }
                
                override fun onDragMove(deltaX: Float, deltaY: Float) {
                    if (!isDragInProgress) return
                    
                    // 拖拽移动时更新位置
                    val touchTarget = windowContainerView?.findViewById<FrameLayout>(R.id.touch_target_view)
                    touchTarget?.let { target ->
                        val params = target.layoutParams as FrameLayout.LayoutParams
                        val screenWidth = resources.displayMetrics.widthPixels
                        
                        // 使用translationX来实现平滑移动，避免跳跃
                        val currentTranslationX = target.translationX
                        val newTranslationX = currentTranslationX + deltaX
                        
                        // 获取小横条的实际宽度
                        val proxyIndicator = target.getChildAt(0)
                        val indicatorWidth = if (proxyIndicator != null) {
                            if (proxyIndicator.measuredWidth == 0) {
                                proxyIndicator.measure(
                                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                                )
                            }
                            proxyIndicator.measuredWidth
                        } else {
                            96.dpToPx() // 默认宽度
                        }
                        
                        // 计算实际位置（margin + translation）
                        val actualX = params.leftMargin + newTranslationX
                        val edgeMargin = 4.dpToPx()
                        
                        // 限制在屏幕范围内
                        val minX = edgeMargin.toFloat()
                        val maxX = (screenWidth - edgeMargin - indicatorWidth).toFloat()
                        val safeMaxX = maxX.coerceAtLeast(minX)
                        val clampedX = actualX.coerceIn(minX, safeMaxX)
                        
                        // 更新translationX
                        target.translationX = clampedX - params.leftMargin
                        
                        // 根据新位置计算百分比并保存（减少保存频率，避免频繁IO）
                        val range = safeMaxX - minX
                        val position = if (range > 0) {
                            (((clampedX - minX) / range) * 100).toInt().coerceIn(0, 100)
                        } else {
                            50
                        }
                        
                        // 使用Handler延迟保存，避免频繁保存
                        savePositionRunnable?.let { uiHandler.removeCallbacks(it) }
                        val newRunnable = Runnable {
                            settingsManager.setIslandPosition(position)
                        }
                        savePositionRunnable = newRunnable
                        uiHandler.postDelayed(newRunnable, 50)
                    }
                }
                
                override fun onDragEnd() {
                    isDragInProgress = false
                    Log.d(TAG, "拖拽结束")
                    
                    // 拖拽结束时，将translationX合并到margin中，重置translationX
                    val touchTarget = windowContainerView?.findViewById<FrameLayout>(R.id.touch_target_view)
                    touchTarget?.let { target ->
                        val params = target.layoutParams as FrameLayout.LayoutParams
                        val finalX = params.leftMargin + target.translationX.toInt()
                        
                        params.leftMargin = finalX
                        target.translationX = 0f
                        target.layoutParams = params
                        
                        // 立即保存最终位置
                        savePositionRunnable?.let { uiHandler.removeCallbacks(it) }
                        val screenWidth = resources.displayMetrics.widthPixels
                        
                        // 获取小横条的实际宽度
                        val proxyIndicator = target.getChildAt(0)
                        val indicatorWidth = if (proxyIndicator != null) {
                            if (proxyIndicator.measuredWidth == 0) {
                                proxyIndicator.measure(
                                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                                )
                            }
                            proxyIndicator.measuredWidth
                        } else {
                            96.dpToPx() // 默认宽度
                        }
                        
                        val edgeMargin = 4.dpToPx()
                        val minX = edgeMargin
                        val maxX = screenWidth - edgeMargin - indicatorWidth
                        val safeMaxX = maxX.coerceAtLeast(minX)
                        val range = safeMaxX - minX
                        
                        val position = if (range > 0) {
                            ((finalX - minX) * 100 / range).coerceIn(0, 100)
                        } else {
                            50
                        }
                        
                        Log.d(TAG, "拖拽结束位置计算: finalX=$finalX, minX=$minX, maxX=$safeMaxX, range=$range, position=$position")
                        settingsManager.setIslandPosition(position)
                    }
                    
                    // 拖拽结束后，如果处于搜索状态，需要隐藏小横条
                    if (isSearchModeActive) {
                        uiHandler.postDelayed({
                            proxyIndicatorView?.visibility = View.GONE
                        }, 100) // 短暂延迟确保拖拽动画完成
                    }
                }
            })
        }
        touchTargetView.addView(proxyIndicatorView)

        // Position the entire touchTargetView horizontally
        updateIslandPositionForView(touchTargetView)
        
        setupProxyIndicator()
        
        windowContainerView!!.addView(animatingIslandView)
        windowContainerView!!.addView(touchTargetView)
        
        updateIslandVisibility()

        try {
            windowManager.addView(windowContainerView, stageParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun showActionIcon(action: String) {
        val indicatorView = proxyIndicatorView as? DynamicIslandIndicatorView ?: return
        
        val iconResId = when (action) {
            "voice_recognize" -> R.drawable.ic_mic
            "floating_menu" -> R.drawable.ic_menu
            "dual_search" -> R.drawable.ic_search
            "island_panel" -> R.drawable.ic_apps
            "settings" -> R.drawable.ic_settings
            else -> R.drawable.ic_apps // 默认图标
        }
        
        indicatorView.showAppIcon(iconResId)
    }

    private fun executeAction(action: String) {
        when (action) {
            "voice_recognize" -> startVoiceRecognition()
            "floating_menu" -> { /* No-op in DynamicIslandService */ }
            "dual_search" -> startDualSearch()
            "island_panel" -> transitionToSearchState()
            "settings" -> openSettings()
            "none" -> { /* No-op */ }
        }
    }

    private fun startVoiceRecognition() {
        val intent = Intent(this, VoiceRecognitionActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun startDualSearch() {
        // This action might open the multi-window search directly
        // It might be useful to pre-fill a query from clipboard if available
        val intent = Intent(this, DualFloatingWebViewService::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startService(intent)
    }

    private fun openSettings() {
        val intent = Intent(this, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun transitionToSearchState(force: Boolean = false) {
        if (isSearchModeActive && !force) return
        isSearchModeActive = true

        // 检查是否正在拖拽，如果是则不隐藏小横条
        val indicatorView = proxyIndicatorView as? DynamicIslandIndicatorView
        val isDragging = indicatorView?.isDragging() ?: false
        
        if (!isDragging) {
        proxyIndicatorView?.visibility = View.GONE
        } else {
            Log.d(TAG, "正在拖拽中，不隐藏小横条")
        }
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
                proxyIndicatorView?.visibility = View.VISIBLE
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
            searchAction()
        }
    }

    private fun performSearch() {
        val query = searchInput?.text.toString().trim()
        if (query.isEmpty()) return

        // Prepend assistant prompt if selected
        val finalQuery = selectedAssistantPrompt?.let {
            "${it.prompt}\n\n---\n\n${query}"
        } ?: query

        // 使用第一个活动卡槽中的引擎，或使用默认引擎
        val engine = activeSlots[1] ?: loadSearchCategories().firstOrNull()?.engines?.firstOrNull() ?: return

        val intent = Intent(this, DualFloatingWebViewService::class.java).apply {
            putExtra("search_query", finalQuery)
            putExtra("engine_key", engine.name.lowercase())
            putExtra("search_source", "灵动岛输入")
            putExtra("startTime", System.currentTimeMillis())
        }
        startService(intent)
        
        transitionToCompactState()
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
        (proxyIndicatorView as? DynamicIslandIndicatorView)?.showNotification(icon, title, text)
    }

    private fun clearNotificationOnIndicator() {
        (proxyIndicatorView as? DynamicIslandIndicatorView)?.clearNotification()
    }

    private fun updateIslandVisibility() {
        val hasNotifications = activeNotifications.isNotEmpty()
        val hasAppSearchIcons = appSearchIconContainer?.childCount ?: 0 > 0
        
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
            // 都隐藏，保持紧凑状态
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

        // Set initial state and add listener for the send button's alpha
        searchButton?.alpha = 0.5f // Start as semi-transparent
        searchInput?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                hidePasteButton() // Hide as soon as user interacts
            }
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

        searchInput?.setOnFocusChangeListener { _, hasFocus ->
            // If the input field loses focus, we should hide the paste button.
            // Showing the button is now handled when the panel appears.
            if (!hasFocus) {
                hidePasteButton()
            }
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

        val panelParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        )

        // Add the panel to the window
        windowContainerView?.addView(configPanelView, panelParams)
        configPanelView?.apply {
            alpha = 0f
            translationY = 1000f
            animate().alpha(1f).translationY(0f).setDuration(300).start()
        }

        // Automatically show the paste button when the config panel appears.
        showPasteButton()
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
                compactWidth = (islandWidth * displayMetrics.density).toInt()
                
                // 如果不在搜索模式，更新当前宽度
                if (!isSearchModeActive) {
                    animatingIslandView?.layoutParams?.width = compactWidth
                    animatingIslandView?.requestLayout()
                }
            }
            "island_position" -> {
                updateProxyIndicatorPosition()
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, // Not focusable, but can receive touch
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

        val animation = panelToRemove?.animate()?.translationY(panelToRemove.height.toFloat())?.setDuration(300)
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

                try {
                    setImageDrawable(packageManager.getApplicationIcon(config.packageName))
                } catch (e: PackageManager.NameNotFoundException) {
                    setImageResource(config.iconResId) // Fallback
                }
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
        (proxyIndicatorView as? DynamicIslandIndicatorView)?.updateSettings()
        
        // Apply the current position setting
        updateProxyIndicatorPosition()
    }

    private fun updateProxyIndicatorPosition() {
        Log.d(TAG, "更新小横条位置: proxyIndicatorView=${proxyIndicatorView != null}")
        
        // Find the touchTargetView first
        val touchTargetView = windowContainerView?.findViewById<FrameLayout>(R.id.touch_target_view)
        touchTargetView?.let { targetView ->
            updateIslandPositionForView(targetView)
            Log.d(TAG, "通过 touchTargetView 更新小横条位置")
        } ?: run {
            Log.d(TAG, "未找到 touchTargetView，无法更新位置")
        }
    }

    private fun updateIslandPosition(position: Int) {
        // Find the touchTargetView and proxyIndicatorView and update their position
        windowContainerView?.let { container ->
            val touchTarget = container.findViewById<FrameLayout>(R.id.touch_target_view)
            touchTarget?.let {
                updateIslandPositionForView(it)
                Log.d(TAG, "动态更新小横条位置: position=$position")
            }
        }
    }

    private fun updateIslandPositionForView(touchTargetView: View) {
        val position = settingsManager.getIslandPosition()
        val screenWidth = resources.displayMetrics.widthPixels
        
        // Find the proxyIndicatorView within the touchTargetView
        val proxyIndicator = if (touchTargetView is FrameLayout) {
            touchTargetView.getChildAt(0) // proxyIndicatorView is the first (and only) child
        } else {
            null
        }
        
        proxyIndicator?.let { indicator ->
            // 确保View已经测量过，获取实际宽度
            if (indicator.measuredWidth == 0) {
                indicator.measure(
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
            }
            
            val indicatorWidth = indicator.measuredWidth
            val edgeMargin = 4.dpToPx()
            
            val minX = edgeMargin
            val maxX = screenWidth - edgeMargin - indicatorWidth
            
            // 确保maxX不小于minX，避免负数范围
            val safeMaxX = maxX.coerceAtLeast(minX)
            
            val targetX = when {
                position <= 0 -> minX
                position >= 100 -> safeMaxX
                else -> {
                    val range = safeMaxX - minX
                    if (range > 0) {
                        minX + (position / 100.0f * range).toInt()
                    } else {
                        minX
                    }
                }
            }
            
            Log.d(TAG, "位置计算: position=$position, screenWidth=$screenWidth, indicatorWidth=$indicatorWidth, targetX=$targetX, range=${safeMaxX - minX}")
            
            // Update the proxyIndicatorView position within its parent
            (indicator.layoutParams as FrameLayout.LayoutParams).apply {
                gravity = Gravity.START or Gravity.TOP
                leftMargin = targetX
                rightMargin = 0
            }
            indicator.requestLayout()
        }
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
        
        // Re-apply island position after configuration change
        uiHandler.post {
            updateProxyIndicatorPosition()
        }
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
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
                val intent = packageManager.getLaunchIntentForPackage(appInfo.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    startActivity(intent)
                    hideContent() // This will collapse the island
                } else {
                    Toast.makeText(this, "无法启动该应用", Toast.LENGTH_SHORT).show()
                }
            }
            appSearchRecyclerView?.apply {
                layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                adapter = appSearchAdapter
                overScrollMode = View.OVER_SCROLL_NEVER
            }
        } else {
            appSearchAdapter?.updateData(results)
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
    
    private fun hideContent() {
        // This function is called when an app is launched from the search results.
        // It should collapse the island and hide the keyboard and search results.
        transitionToCompactState()
        hideAppSearchResults()
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
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
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

        windowContainerView = view as FrameLayout
        islandContentView = view
    }
}