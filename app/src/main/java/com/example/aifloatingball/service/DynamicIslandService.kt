package com.example.aifloatingball.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.preference.PreferenceManager
import android.view.ContextThemeWrapper
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.aifloatingball.HomeActivity
import com.example.aifloatingball.R
import com.example.aifloatingball.service.DualFloatingWebViewService
import java.util.concurrent.ConcurrentHashMap
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Point
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.animation.AccelerateDecelerateInterpolator

class DynamicIslandService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var windowManager: WindowManager
    
    private var windowContainerView: FrameLayout? = null // The stage
    private var animatingIslandView: FrameLayout? = null // The moving/transforming view
    private var islandContentView: View? = null // The content (icons, searchbox)
    private var touchProxyView: View? = null
    private var configPanelView: View? = null
    private var backgroundScrimView: View? = null // For background scrim

    private lateinit var notificationIconContainer: LinearLayout
    private var searchViewContainer: View? = null
    private var searchInput: EditText? = null
    private var searchButton: ImageView? = null

    private var isSearchModeActive = false
    private var compactWidth: Int = 0
    private var expandedWidth: Int = 0
    private var statusBarHeight: Int = 0

    private val activeNotifications = ConcurrentHashMap<String, ImageView>()
    private val uiHandler = Handler(Looper.getMainLooper())
    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            val command = intent.getStringExtra(NotificationListener.EXTRA_COMMAND)
            val key = intent.getStringExtra(NotificationListener.EXTRA_NOTIFICATION_KEY)
            key ?: return

            when (command) {
                NotificationListener.COMMAND_POSTED -> {
                    val packageName = intent.getStringExtra(NotificationListener.EXTRA_PACKAGE_NAME)
                    val iconByteArray = intent.getByteArrayExtra(NotificationListener.EXTRA_ICON)
                    if (packageName != null && iconByteArray != null) {
                        val iconBitmap = BitmapFactory.decodeByteArray(iconByteArray, 0, iconByteArray.size)
                        addNotificationIcon(key, iconBitmap)
                    }
                }
                NotificationListener.COMMAND_REMOVED -> {
                    removeNotificationIcon(key)
                }
            }
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "DynamicIslandChannel"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        showDynamicIsland()

        LocalBroadcastManager.getInstance(this).registerReceiver(
            notificationReceiver,
            IntentFilter(NotificationListener.ACTION_NOTIFICATION)
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Dynamic Island Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
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

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        statusBarHeight = getStatusBarHeight()
        compactWidth = (resources.displayMetrics.widthPixels * 0.4).toInt()
        expandedWidth = (resources.displayMetrics.widthPixels * 0.9).toInt()

        // 1. The Stage
        windowContainerView = FrameLayout(this)
        val stageParams = WindowManager.LayoutParams(
            expandedWidth, // Use max width for the stage
            statusBarHeight * 2,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 0
        }

        // 2. The Animating View
        animatingIslandView = FrameLayout(this).apply {
            background = getDrawable(R.drawable.dynamic_island_background)
            layoutParams = FrameLayout.LayoutParams(compactWidth, statusBarHeight, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        }
        
        // 3. The Content
        islandContentView = inflater.inflate(R.layout.dynamic_island_layout, animatingIslandView, false)
        notificationIconContainer = islandContentView!!.findViewById(R.id.notification_icon_container)
        searchViewContainer = islandContentView!!.findViewById(R.id.search_view_container)
        searchInput = islandContentView!!.findViewById(R.id.search_input)
        searchButton = islandContentView!!.findViewById(R.id.search_button)
        animatingIslandView!!.addView(islandContentView)

        // 4. The Proxy
        touchProxyView = View(this).apply {
            layoutParams = FrameLayout.LayoutParams(compactWidth, statusBarHeight, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
                topMargin = statusBarHeight
            }
        }
        
        // Add views to stage
        windowContainerView!!.addView(animatingIslandView)
        windowContainerView!!.addView(touchProxyView)
        
        // Set Listeners
        touchProxyView?.setOnClickListener { if (!isSearchModeActive) transitionToSearchState() }
        setupSearchListeners()

        // 初始时就应该可见
        updateIslandVisibility()

        windowContainerView!!.setOnTouchListener { _, event ->
            if (isSearchModeActive && event.action == MotionEvent.ACTION_DOWN) {
                val islandRect = android.graphics.Rect()
                animatingIslandView?.getGlobalVisibleRect(islandRect)
                if (!islandRect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                    transitionToCompactState()
                    return@setOnTouchListener true
                }
            }
            false
        }

        try {
            windowManager.addView(windowContainerView, stageParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun transitionToSearchState() {
        if (isSearchModeActive) return
        isSearchModeActive = true

        // --- Create and show background scrim ---
        if (backgroundScrimView == null) {
            backgroundScrimView = View(this).apply {
                setBackgroundColor(Color.argb(1, 0, 0, 0)) // Almost transparent, but clickable
                layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
                setOnClickListener { transitionToCompactState() }
            }
            val scrimParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )
            try { windowManager.addView(backgroundScrimView, scrimParams) } catch (e: Exception) { e.printStackTrace() }
        }

        touchProxyView?.visibility = View.GONE

        // Allow window to be focusable
        val windowParams = windowContainerView?.layoutParams as? WindowManager.LayoutParams
        windowParams?.let {
            it.flags = it.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            windowManager.updateViewLayout(windowContainerView, it)
        }

        val islandParams = animatingIslandView?.layoutParams as FrameLayout.LayoutParams
        val animator = ValueAnimator.ofInt(0, statusBarHeight).apply {
            duration = 350
            addUpdateListener {
                val value = it.animatedValue as Int
                val fraction = it.animatedFraction
                islandParams.topMargin = value
                islandParams.width = compactWidth + ((expandedWidth - compactWidth) * fraction).toInt()
                animatingIslandView?.layoutParams = islandParams
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    notificationIconContainer.visibility = View.GONE
                }
                override fun onAnimationEnd(animation: Animator) {
                    searchViewContainer?.visibility = View.VISIBLE
                    showConfigurationPanel()
                    
                    // --- BEGIN: Show Panel Logic ---
                    if (configPanelView == null) {
                        val inflater = LayoutInflater.from(this@DynamicIslandService)
                        configPanelView = inflater.inflate(R.layout.dynamic_island_config_panel, null)
                        val params = WindowManager.LayoutParams(
                            WindowManager.LayoutParams.MATCH_PARENT,
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
                            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                            PixelFormat.TRANSLUCENT
                        ).apply {
                            gravity = Gravity.TOP
                            val islandRect = android.graphics.Rect()
                            windowContainerView?.getGlobalVisibleRect(islandRect)
                            y = islandRect.bottom + 10
                        }
                        try {
                            windowManager.addView(configPanelView, params)
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                    // --- END: Show Panel Logic ---

                    searchInput?.requestFocus()
                    uiHandler.postDelayed({
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
                    }, 100)
                }
            })
        }
        animator.start()
    }

    private fun transitionToCompactState() {
        if (!isSearchModeActive) return
        isSearchModeActive = false

        cleanupExpandedViews()

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowContainerView?.windowToken, 0)
        searchInput?.clearFocus()
        searchInput?.setText("")

        // Make window non-focusable again
        val windowParams = windowContainerView?.layoutParams as? WindowManager.LayoutParams
        windowParams?.let {
            it.flags = it.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager.updateViewLayout(windowContainerView, it)
        }

        val islandParams = animatingIslandView?.layoutParams as FrameLayout.LayoutParams
        val animator = ValueAnimator.ofInt(statusBarHeight, 0).apply {
            duration = 350
            addUpdateListener {
                val value = it.animatedValue as Int
                val fraction = it.animatedFraction
                islandParams.topMargin = value
                islandParams.width = expandedWidth + ((compactWidth - expandedWidth) * fraction).toInt()
                animatingIslandView?.layoutParams = islandParams
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    searchViewContainer?.visibility = View.GONE
                }
                override fun onAnimationEnd(animation: Animator) {
                    notificationIconContainer.visibility = View.VISIBLE
                    touchProxyView?.visibility = View.VISIBLE
                }
            })
        }
        animator.start()
    }
    
    private fun setupSearchListeners() {
        searchButton?.setOnClickListener { performSearch() }
        searchInput?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else false
        }
    }

    private fun performSearch() {
        val query = searchInput?.text.toString().trim()
        if (query.isNotEmpty()) {
            val intent = Intent(this, DualFloatingWebViewService::class.java)
            intent.putExtra("search_query", query)
            startService(intent)
            
            // Just transition back to the compact state.
            transitionToCompactState()
        }
    }

    private fun addNotificationIcon(key: String, icon: android.graphics.Bitmap) {
        uiHandler.post {
            if (activeNotifications.containsKey(key)) {
                return@post
            }

            val imageView = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    getStatusBarHeight() - 16.dpToPx(),
                    getStatusBarHeight() - 16.dpToPx()
                ).also { params ->
                    params.setMargins(4.dpToPx(), 0, 4.dpToPx(), 0)
                }
                setImageBitmap(icon)
            }
            notificationIconContainer.addView(imageView)
            activeNotifications[key] = imageView
            updateIslandVisibility()
        }
    }

    private fun removeNotificationIcon(key: String) {
        uiHandler.post {
            activeNotifications[key]?.let {
                notificationIconContainer.removeView(it)
                activeNotifications.remove(key)
                updateIslandVisibility()
            }
        }
    }

    private fun updateIslandVisibility() {
        // 始终保持灵动岛可见，即使没有通知
        windowContainerView?.visibility = View.VISIBLE
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) {
            resources.getDimensionPixelSize(resourceId)
        } else {
            (24 * resources.displayMetrics.density).toInt()
        }
    }

    private fun Int.dpToPx(): Int {
        val scale = resources.displayMetrics.density
        return (this * scale + 0.5f).toInt()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Removed temporary preview logic
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(notificationReceiver)
        cleanupViews()
    }

    private fun cleanupViews() {
        try {
            windowContainerView?.let { windowManager.removeView(it) }
            configPanelView?.let { windowManager.removeView(it) }
            backgroundScrimView?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        windowContainerView = null
        configPanelView = null
        backgroundScrimView = null
        islandContentView = null
        touchProxyView = null
    }

    private fun showConfigurationPanel() {
        if (configPanelView != null) return

        // Create a themed context to inflate the layout with Material Components theme
        val themedContext = ContextThemeWrapper(this, R.style.Theme_FloatingWindow)
        configPanelView = LayoutInflater.from(themedContext).inflate(R.layout.dynamic_island_config_panel, null)

        val panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            val islandRect = android.graphics.Rect()
            windowContainerView?.getGlobalVisibleRect(islandRect)
            y = islandRect.bottom + 10
        }
        try {
            windowManager.addView(configPanelView, panelParams)
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun cleanupExpandedViews() {
        if (backgroundScrimView != null) {
            try { windowManager.removeView(backgroundScrimView) } catch (e: Exception) { e.printStackTrace() }
            backgroundScrimView = null
        }
        if (configPanelView != null) {
            try { windowManager.removeView(configPanelView) } catch (e: Exception) { e.printStackTrace() }
            configPanelView = null
        }
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "display_mode") {
            val displayMode = sharedPreferences?.getString(key, "floating_ball")
            if (displayMode != "dynamic_island") {
                stopSelf()
            }
        }
    }
} 