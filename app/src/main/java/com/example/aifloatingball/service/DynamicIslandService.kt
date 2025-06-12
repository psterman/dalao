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
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.preference.PreferenceManager
import android.view.ContextThemeWrapper
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
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.HomeActivity
import com.example.aifloatingball.R
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import android.view.animation.AccelerateDecelerateInterpolator

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

    private val searchCategories = listOf(
        SearchCategory("普通搜索引擎", listOf(
            SearchEngine("百度", "全球最大的中文搜索引擎", R.mipmap.ic_launcher, "https://www.baidu.com/s?wd="),
            SearchEngine("Google", "全球领先的搜索引擎", R.mipmap.ic_launcher, "https://www.google.com/search?q="),
            SearchEngine("Bing", "微软出品的智能搜索引擎", R.mipmap.ic_launcher, "https://www.bing.com/search?q=")
        )),
        SearchCategory("AI 搜索引擎", listOf(
            SearchEngine("Kimi", "长文本深度对话", R.mipmap.ic_launcher, "https://kimi.moonshot.cn/?q="),
            SearchEngine("DeepSeek", "代码生成与理解", R.mipmap.ic_launcher, "https://www.deepseek.com/search?q="),
            SearchEngine("ChatGPT", "全球领先的AI对话模型", R.mipmap.ic_launcher, "https://chat.openai.com/?q=")
        )),
        SearchCategory("App 搜索", listOf(
            SearchEngine("GitHub", "面向开发者的代码搜索", R.mipmap.ic_launcher, "https://github.com/search?q="),
            SearchEngine("知乎", "高质量的问答社区", R.mipmap.ic_launcher, "https://www.zhihu.com/search?type=content&q=")
        ))
    )
    // NOTE: You need to add the actual drawable resources for each engine.

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var windowManager: WindowManager

    private var windowContainerView: FrameLayout? = null // The stage
    private var animatingIslandView: FrameLayout? = null // The moving/transforming view
    private var islandContentView: View? = null // The content (icons, searchbox)
    private var touchProxyView: View? = null
    private var configPanelView: View? = null
    private var backgroundScrimView: View? = null // For background scrim
    private var searchEngineSelectorView: View? = null
    private var selectorScrimView: View? = null

    private lateinit var notificationIconContainer: LinearLayout
    private var searchViewContainer: View? = null
    private var searchInput: EditText? = null
    private var searchButton: ImageView? = null

    private var isSearchModeActive = false
    private var compactWidth: Int = 0
    private var expandedWidth: Int = 0
    private var statusBarHeight: Int = 0

    private val activeNotifications = ConcurrentHashMap<String, ImageView>()
    private val activeSlots = ConcurrentHashMap<Int, SearchEngine>()
    private var slot1View: View? = null
    private var slot2View: View? = null
    private var slot3View: View? = null

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

        windowContainerView!!.addView(animatingIslandView)
        windowContainerView!!.addView(touchProxyView)
        
        touchProxyView?.setOnClickListener { if (!isSearchModeActive) transitionToSearchState() }
        setupSearchListeners()

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

        if (backgroundScrimView == null) {
            backgroundScrimView = View(this).apply {
                setBackgroundColor(Color.argb(1, 0, 0, 0))
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

        val windowParams = windowContainerView?.layoutParams as? WindowManager.LayoutParams
        windowParams?.let {
            it.flags = it.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            windowManager.updateViewLayout(windowContainerView, it)
        }

        val islandParams = animatingIslandView?.layoutParams as FrameLayout.LayoutParams
        ValueAnimator.ofInt(0, statusBarHeight).apply {
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
                    
                    searchInput?.requestFocus()
                    uiHandler.postDelayed({
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
                    }, 100)
                }
            })
        }.start()
    }

    private fun transitionToCompactState() {
        if (!isSearchModeActive) return
        isSearchModeActive = false

        cleanupExpandedViews()

        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(windowContainerView?.windowToken, 0)
        searchInput?.clearFocus()
        searchInput?.setText("")

        val windowParams = windowContainerView?.layoutParams as? WindowManager.LayoutParams
        windowParams?.let {
            it.flags = it.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager.updateViewLayout(windowContainerView, it)
        }

        val islandParams = animatingIslandView?.layoutParams as FrameLayout.LayoutParams
        ValueAnimator.ofInt(statusBarHeight, 0).apply {
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
        }.start()
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
            val searchEngine = activeSlots[1] ?: searchCategories.first().engines.first()
            val searchUrl = searchEngine.searchUrl + URLEncoder.encode(query, "UTF-8")

            val intent = Intent(this, DualFloatingWebViewService::class.java).apply {
                putExtra("search_query", query)
                putExtra("search_url", searchUrl)
            }
            startService(intent)
            
            transitionToCompactState()
        }
    }

    private fun addNotificationIcon(key: String, icon: android.graphics.Bitmap) {
        uiHandler.post {
            if (activeNotifications.containsKey(key)) return@post

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
            activeNotifications.remove(key)?.let {
                notificationIconContainer.removeView(it)
                updateIslandVisibility()
            }
        }
    }

    private fun updateIslandVisibility() {
        windowContainerView?.visibility = View.VISIBLE
    }

    private fun getStatusBarHeight(): Int {
        val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resourceId > 0) resources.getDimensionPixelSize(resourceId)
        else (24 * resources.displayMetrics.density).toInt()
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density + 0.5f).toInt()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(notificationReceiver)
        cleanupViews()
    }

    private fun cleanupViews() {
        try {
            windowContainerView?.let { windowManager.removeView(it) }
            cleanupExpandedViews()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        windowContainerView = null
    }

    private fun showConfigurationPanel() {
        if (configPanelView != null) return

        val themedContext = ContextThemeWrapper(this, R.style.Theme_FloatingWindow)
        configPanelView = LayoutInflater.from(themedContext).inflate(R.layout.dynamic_island_config_panel, null)

        slot1View = configPanelView?.findViewById(R.id.slot_1)
        slot2View = configPanelView?.findViewById(R.id.slot_2)
        slot3View = configPanelView?.findViewById(R.id.slot_3)

        slot1View?.setOnClickListener { showSearchEngineSelector(it, 1) }
        slot2View?.setOnClickListener { showSearchEngineSelector(it, 2) }
        slot3View?.setOnClickListener { showSearchEngineSelector(it, 3) }

        slot1View?.findViewById<View>(R.id.slot_clear_button)?.setOnClickListener { clearSlot(1) }
        slot2View?.findViewById<View>(R.id.slot_clear_button)?.setOnClickListener { clearSlot(2) }
        slot3View?.findViewById<View>(R.id.slot_clear_button)?.setOnClickListener { clearSlot(3) }

        updateSlotView(1, activeSlots[1])
        updateSlotView(2, activeSlots[2])
        updateSlotView(3, activeSlots[3])

        val panelParams = WindowManager.LayoutParams(
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
            slot1View = null
            slot2View = null
            slot3View = null
        }
        dismissSearchEngineSelector()
    }

    private fun showSearchEngineSelector(anchorView: View, slotIndex: Int) {
        dismissSearchEngineSelector()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurEffect = RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.CLAMP)
            animatingIslandView?.setRenderEffect(blurEffect)
            configPanelView?.setRenderEffect(blurEffect)
        }

        val themedContext = ContextThemeWrapper(this, R.style.Theme_FloatingWindow)
        val inflater = LayoutInflater.from(themedContext)
        val panelWrapper = inflater.inflate(R.layout.search_engine_panel_wrapper, null) as com.google.android.material.card.MaterialCardView
        inflater.inflate(R.layout.search_engine_selector, panelWrapper, true)
        searchEngineSelectorView = panelWrapper

        val recyclerView = searchEngineSelectorView?.findViewById<RecyclerView>(R.id.main_recycler_view)
        recyclerView?.adapter = SearchCategoryAdapter(searchCategories) { selectedEngine ->
            selectSearchEngineForSlot(selectedEngine, slotIndex)
        }

        selectorScrimView = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            setOnClickListener { dismissSearchEngineSelector() }
        }
        val scrimParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )
        try { windowManager.addView(selectorScrimView, scrimParams) } catch (e: Exception) { e.printStackTrace() }

        val selectorParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            val configPanelRect = android.graphics.Rect()
            configPanelView?.getGlobalVisibleRect(configPanelRect)
            y = if (configPanelRect.isEmpty) 300 else configPanelRect.bottom + 8.dpToPx()
        }

        try {
            windowManager.addView(searchEngineSelectorView, selectorParams)
            searchEngineSelectorView?.apply {
                alpha = 0f
                translationY = -40f
                animate()
                    .alpha(1f)
                    .translationY(0f)
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
        updateSlotView(slotIndex, engine)
        dismissSearchEngineSelector()
    }

    private fun clearSlot(slotIndex: Int) {
        activeSlots.remove(slotIndex)
        updateSlotView(slotIndex, null)
    }

    private fun updateSlotView(slotIndex: Int, engine: SearchEngine?) {
        val slotView = when(slotIndex) {
            1 -> slot1View
            2 -> slot2View
            3 -> slot3View
            else -> null
        } ?: return

        val hintText = slotView.findViewById<TextView>(R.id.slot_hint_text)
        val filledContent = slotView.findViewById<View>(R.id.slot_filled_content)
        val clearButton = slotView.findViewById<View>(R.id.slot_clear_button)

        if (engine != null) {
            hintText.visibility = View.GONE
            filledContent.visibility = View.VISIBLE
            clearButton.visibility = View.VISIBLE

            val icon = filledContent.findViewById<ImageView>(R.id.slot_icon)
            val title = filledContent.findViewById<TextView>(R.id.slot_title)
            val subtitle = filledContent.findViewById<TextView>(R.id.slot_subtitle)

            icon.setImageResource(engine.iconResId)
            title.text = engine.name
            subtitle.text = engine.description
            subtitle.visibility = View.VISIBLE
        } else {
            hintText.visibility = View.VISIBLE
            filledContent.visibility = View.GONE
            clearButton.visibility = View.GONE
        }
    }

    private fun dismissSearchEngineSelector() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            animatingIslandView?.setRenderEffect(null)
            configPanelView?.setRenderEffect(null)
        }

        val viewToRemove = searchEngineSelectorView
        val scrimToRemove = selectorScrimView
        
        searchEngineSelectorView = null
        selectorScrimView = null
        
        if (viewToRemove == null) return

        viewToRemove.animate()
            .alpha(0f)
            .translationY(-40f)
            .setDuration(250)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction {
                try {
                    windowManager.removeView(viewToRemove)
                    scrimToRemove?.let { windowManager.removeView(it) }
                } catch (e: Exception) { /* ignore */ }
            }
            .start()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "display_mode") {
            if (sharedPreferences?.getString(key, "floating_ball") != "dynamic_island") {
                stopSelf()
            }
        }
    }
}