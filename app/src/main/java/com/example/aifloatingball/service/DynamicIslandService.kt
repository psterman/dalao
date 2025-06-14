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
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.HomeActivity
import com.example.aifloatingball.R
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.WindowInsets

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
    private var searchEngineSelectorView: View? = null
    private var selectorScrimView: View? = null

    private lateinit var notificationIconContainer: LinearLayout
    private var searchInput: EditText? = null
    private var searchButton: ImageView? = null

    private var isSearchModeActive = false
    private var isEditingModeActive = false // New state for editing
    private var compactWidth: Int = 0
    private var expandedWidth: Int = 0
    private var statusBarHeight: Int = 0

    private var currentKeyboardHeight = 0
    private var editingScrimView: View? = null // New scrim view for background blur/dim

    private val activeNotifications = ConcurrentHashMap<String, ImageView>()
    private val activeSlots = ConcurrentHashMap<Int, SearchEngine>()
    private var slot1View: View? = null
    private var slot2View: View? = null
    private var slot3View: View? = null
    private var textActionMenu: PopupWindow? = null

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

        updateIslandVisibility()

        try {
            windowManager.addView(windowContainerView, stageParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun transitionToSearchState() {
        if (isSearchModeActive) return
        isSearchModeActive = true

        touchProxyView?.visibility = View.GONE

        val windowParams = windowContainerView?.layoutParams as? WindowManager.LayoutParams
        windowParams?.let {
            it.width = WindowManager.LayoutParams.MATCH_PARENT
            it.height = WindowManager.LayoutParams.MATCH_PARENT
            it.flags = it.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            windowManager.updateViewLayout(windowContainerView, it)
        }
        
        windowContainerView?.setBackgroundColor(Color.argb(128, 0, 0, 0))
        setupOutsideTouchListener()
        setupInsetsListener()

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
                    showConfigPanel()

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
            it.width = expandedWidth
            it.height = statusBarHeight * 2
            it.flags = it.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            windowManager.updateViewLayout(windowContainerView, it)
        }

        windowContainerView?.setBackgroundColor(Color.TRANSPARENT)
        windowContainerView?.setOnTouchListener(null)
        windowContainerView?.setOnApplyWindowInsetsListener(null)

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
                }
                override fun onAnimationEnd(animation: Animator) {
                    notificationIconContainer.visibility = View.VISIBLE
                    touchProxyView?.visibility = View.VISIBLE
                }
            })
        }.start()
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
                searchAction()
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
        // 优先使用卡槽1中的增强指令
        val aiEnhancedEngine = activeSlots[1]
        val queryToSearch = if (aiEnhancedEngine != null && aiEnhancedEngine.name == "AI 增强指令") {
            searchInput?.text.toString().trim() // 直接使用输入框里已经增强过的完整文本
        } else {
            searchInput?.text.toString().trim() // 否则使用普通文本
        }

        if (queryToSearch.isNotEmpty()) {
            // 决定用哪个搜索引擎
            // 如果是增强指令，强制使用AI引擎，否则用默认引擎
            val searchEngineToUse = if (aiEnhancedEngine != null && aiEnhancedEngine.name == "AI 增强指令") {
                searchCategories.find { it.title == "AI 搜索引擎" }?.engines?.first() // 例如总是用Kimi
            } else {
                activeSlots[1] // 否则使用卡槽1里用户选择的普通引擎
            } ?: searchCategories.first().engines.first() // 最终的兜底

            val searchUrl = searchEngineToUse.searchUrl + URLEncoder.encode(queryToSearch, "UTF-8")

            val intent = Intent(this, DualFloatingWebViewService::class.java).apply {
                putExtra("search_query", queryToSearch)
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
        hideEditingScrim()
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

    private fun showConfigPanel() {
        if (configPanelView != null || isEditingModeActive) return

        val themedContext = ContextThemeWrapper(this, R.style.Theme_FloatingWindow)
        configPanelView = LayoutInflater.from(themedContext).inflate(R.layout.dynamic_island_config_panel, null)

        searchInput = configPanelView?.findViewById(R.id.search_input)
        searchButton = configPanelView?.findViewById(R.id.search_button)
        setupSearchListeners()

        // Set initial state and add listener for the send button's alpha
        searchButton?.alpha = 0.5f // Start as semi-transparent
        searchInput?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchButton?.alpha = if (s.isNullOrEmpty()) 0.5f else 1.0f
            }
        })

        // We abandon ActionMode as it's unreliable in overlays.
        // Instead, we manually show our popup menu on long click.
        searchInput?.setOnLongClickListener {
            // We post this to the handler to ensure the default text selection
            // has occurred before we try to position our menu.
            uiHandler.post { showCustomTextMenu() }
            true // Consume the event
        }

        slot1View = configPanelView?.findViewById(R.id.slot_1)
        slot2View = configPanelView?.findViewById(R.id.slot_2)
        slot3View = configPanelView?.findViewById(R.id.slot_3)

        val addPromptButton = configPanelView?.findViewById<View>(R.id.btn_add_master_prompt)
        addPromptButton?.setOnClickListener {
            enterEditingMode()
        }

        slot1View?.setOnClickListener { showSearchEngineSelector(it, 1) }
        slot2View?.setOnClickListener { showSearchEngineSelector(it, 2) }
        slot3View?.setOnClickListener { showSearchEngineSelector(it, 3) }

        slot1View?.findViewById<View>(R.id.slot_clear_button)?.setOnClickListener { clearSlot(1) }
        slot2View?.findViewById<View>(R.id.slot_clear_button)?.setOnClickListener { clearSlot(2) }
        slot3View?.findViewById<View>(R.id.slot_clear_button)?.setOnClickListener { clearSlot(3) }

        updateSlotView(1, activeSlots[1])
        updateSlotView(2, activeSlots[2])
        updateSlotView(3, activeSlots[3])

        val panelParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.BOTTOM
        )
        windowContainerView?.addView(configPanelView, panelParams)
        configPanelView?.apply {
            translationY = 1000f
            animate().translationY(0f).setDuration(300).start()
        }
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
        val panelToRemove = configPanelView
        if (panelToRemove != null) {
            panelToRemove.animate()
                .alpha(0f)
                .translationY(100f)
                .setDuration(250)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    try {
                        windowContainerView?.removeView(panelToRemove)
                    } catch (e: Exception) { /* ignore */ }
                }
                .start()
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
        configPanelView?.visibility = View.GONE

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
                    .alpha(1f)
                    .translationY(finalTranslationY)
                    .setDuration(350)
                    .setInterpolator(AccelerateDecelerateInterpolator())
                    .start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 3. Animate the AI Engines panel to indicate readiness
        // This view was removed, so the animation is no longer needed.
        /*
        val aiEnginesPanel = configPanelView?.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.ai_engines_recycler_view)
        aiEnginesPanel?.let {
            it.alpha = 0.5f
            it.animate()
                .alpha(1f)
                .setDuration(400)
                .start()
        }
        */
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
            title.visibility = View.VISIBLE
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
        configPanelView?.visibility = View.VISIBLE

        val viewToRemove = searchEngineSelectorView
        val scrimToRemove = selectorScrimView
        
        searchEngineSelectorView = null
        selectorScrimView = null
        
        if (viewToRemove == null) return

        viewToRemove.animate()
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

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "display_mode") {
            if (sharedPreferences?.getString(key, "floating_ball") != "dynamic_island") {
                stopSelf()
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

    private fun hideConfigPanel(isForEditing: Boolean = false) {
        if (configPanelView == null) return
        val panelToRemove = configPanelView
        configPanelView = null

        val animation = panelToRemove?.animate()?.translationY(panelToRemove.height.toFloat())?.setDuration(300)
        animation?.setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                windowContainerView?.removeView(panelToRemove)
            }
        })
        animation?.start()
    }
} 