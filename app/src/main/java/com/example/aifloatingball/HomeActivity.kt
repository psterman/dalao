package com.example.aifloatingball

// Android standard library imports
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.Switch
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.GravityCompat
import androidx.core.widget.NestedScrollView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.switchmaterial.SwitchMaterial
import net.sourceforge.pinyin4j.PinyinHelper
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.model.BaseSearchEngine
import com.example.aifloatingball.view.LetterIndexBar
import com.example.aifloatingball.service.DualFloatingWebViewService
import java.io.ByteArrayInputStream
import kotlin.math.abs
import com.example.aifloatingball.manager.BookmarkManager
import com.example.aifloatingball.model.Bookmark
import com.example.aifloatingball.tab.TabManager
import com.example.aifloatingball.utils.WebViewHelper
import com.google.android.material.textfield.TextInputEditText
import com.example.aifloatingball.service.FloatingWindowService
import com.example.aifloatingball.service.DynamicIslandService
import android.content.SharedPreferences

class HomeActivity : AppCompatActivity(), GestureDetector.OnGestureListener {
    companion object {
        private const val TAG = "HomeActivity"
    }

    private lateinit var rootLayout: ViewGroup
    private lateinit var searchInput: EditText
    private lateinit var voiceSearchButton: ImageButton
    private lateinit var shortcutsGrid: RecyclerView
    private lateinit var gestureDetectorCompat: GestureDetectorCompat
    private lateinit var webView: WebView
    private lateinit var webViewContainer: FrameLayout
    private lateinit var appBarLayout: AppBarLayout
    private lateinit var homeContent: View
    private lateinit var menuDialog: BottomSheetDialog
    private lateinit var gestureHintView: TextView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var letterIndexBar: LetterIndexBar
    private lateinit var letterTitle: TextView
    private lateinit var previewEngineList: LinearLayout
    private lateinit var settingsManager: SettingsManager
    private val gestureHintHandler = Handler(Looper.getMainLooper())
    
    // 浏览器设置状态
    private var isNoImageMode = false
    private var isDesktopMode = false
    private var isAdBlockEnabled = true
    private var isIncognitoMode = false
    private var isNightMode = false

    // 手势相关变量
    private lateinit var gestureDetector: GestureDetector
    private lateinit var scaleGestureDetector: ScaleGestureDetector
    private var currentScale = 1f
    private var isScaling = false
    private var initialSpan = 0f
    private val MIN_SCALE_SPAN = 20f
    private val SCALE_VELOCITY_THRESHOLD = 0.02f
    private var lastScaleFactor = 1f
    private var lastGestureHintRunnable: Runnable? = null
    
    // 手势状态追踪
    private var lastTapTime = 0L
    private var lastTapCount = 0
    private val DOUBLE_TAP_TIMEOUT = 300L
    private var isTwoFingerTap = false
    private var touchCount = 0
    private var lastTouchTime = 0L
    private val DOUBLE_TAP_TIMEOUT_TOUCH = 300L

    private val SWIPE_THRESHOLD = 100
    private val SWIPE_VELOCITY_THRESHOLD = 100
    private var lastTouchPointerCount = 0

    // 抽屉相关变量
    private var isDrawerEnabled = false
    private var longPressStartTime = 0L
    private val LONG_PRESS_DURATION = 500L
    private val EDGE_SIZE = 50 // dp
    private var edgeSizePixels = 0
    private var isAIMode = true
    private var edgeGravity = GravityCompat.START

    private var autoHideSwitch: SwitchCompat? = null
    private var clipboardSwitch: SwitchCompat? = null

    private lateinit var tabManager: TabManager
    private lateinit var viewPager: ViewPager2
    private lateinit var tabPreviewList: RecyclerView
    private lateinit var tabPreviewContainer: LinearLayout
    private lateinit var addTabButton: ImageButton
    private lateinit var progressBar: android.widget.ProgressBar

    // 添加成员变量
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private val preferenceChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "display_mode") {
            updateDisplayMode()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        settingsManager = SettingsManager.getInstance(this)
        
        rootLayout = findViewById(R.id.webview_container)

        // 计算边缘区域大小
        val density = resources.displayMetrics.density
        edgeSizePixels = (EDGE_SIZE * density).toInt()

        // 初始化视图
        searchInput = findViewById(R.id.search_input)
        voiceSearchButton = findViewById(R.id.voice_search)
        shortcutsGrid = findViewById(R.id.shortcuts_grid)
        webViewContainer = findViewById(R.id.webview_container)
        appBarLayout = findViewById(R.id.appbar)
        homeContent = findViewById(R.id.home_content)
        gestureHintView = findViewById(R.id.gesture_hint)
        drawerLayout = findViewById(R.id.drawer_layout)
        letterIndexBar = findViewById(R.id.letter_index_bar)
        letterTitle = findViewById(R.id.letter_title)
        previewEngineList = findViewById(R.id.preview_engine_list)
        progressBar = findViewById(R.id.progress_bar)

        // 设置抽屉布局
        setupDrawer()

        // 监听主题和左右手模式变化
        settingsManager.registerOnSettingChangeListener<Boolean>("right_handed_mode") { _, value ->
            updateDrawerGravity()
        }

        // 初始化标签页管理
        initTabManagement()

        // 设置手势检测
        initGestureDetectors()

        // 设置搜索功能
        setupSearch()

        // 设置快捷方式
        setupShortcuts()

        // 设置底部按钮
        setupBottomBar()

        // 设置字母索引栏
        setupLetterIndexBar()

        // 在Activity完全初始化后检查剪贴板
        window.decorView.post {
            checkClipboard()
        }

        // 尝试查找开关
        autoHideSwitch = findViewById(R.id.auto_hide_switch)
        clipboardSwitch = findViewById(R.id.clipboard_switch)
        
        // 设置开关状态
        updateSwitchStates()

        findViewById<ImageButton>(R.id.btn_floating_mode).setOnClickListener {
            toggleFloatingMode()
        }

        updateDisplayMode()
    }

    private fun updateDisplayMode() {
        val displayMode = settingsManager.getDisplayMode()
        Log.d(TAG, "Updating display mode to: $displayMode")

        // 停止所有相关服务
        stopService(Intent(this, FloatingWindowService::class.java))
        stopService(Intent(this, DynamicIslandService::class.java))

        // 根据新模式启动正确的服务
        when (displayMode) {
            "floating_ball" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                    startService(Intent(this, FloatingWindowService::class.java))
                }
            }
            "dynamic_island" -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                    startService(Intent(this, DynamicIslandService::class.java))
                }
            }
        }
    }

    private fun setupDrawer() {
        // 设置抽屉可解锁，允许用户通过手势打开
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
        
        // 根据设置更新抽屉位置
        updateDrawerGravity()
        
        // 应用主题颜色
        applyDrawerTheme()
        
        // 初始化字母索引栏
        setupLetterIndexBar()
        
        // 设置抽屉监听器
        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                // 更新透明度和动画
                drawerView.alpha = 0.3f + (0.7f * slideOffset)
            }
            
            override fun onDrawerOpened(drawerView: View) {
                isDrawerEnabled = true
                drawerView.alpha = 1.0f
                // 打开抽屉时更新搜索引擎列表
                updateEngineList('#')
            }
            
            override fun onDrawerClosed(drawerView: View) {
                isDrawerEnabled = false
            }
            
            override fun onDrawerStateChanged(newState: Int) {}
        })

        // 设置空白区域点击监听器
        findViewById<View>(R.id.drawer_layout).setOnClickListener { view ->
            if (view.id == R.id.drawer_layout && drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            }
        }

        // 设置退出按钮点击监听器
        findViewById<View>(R.id.exit_button)?.setOnClickListener {
            // 显示确认对话框
            AlertDialog.Builder(this)
                .setTitle("退出确认")
                .setMessage("确定要退出应用吗？")
                .setPositiveButton("确定") { _, _ ->
                    // 保存必要的状态
                    saveSettings()
                    
                    // 清理资源
                    webView.clearCache(true)
                    webView.clearHistory()
                    
                    // 关闭所有活动的服务
                    if (DualFloatingWebViewService.isRunning) {
                        stopService(Intent(this, DualFloatingWebViewService::class.java))
                    }
                    
                    // 结束应用
                    finish()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun updateDrawerGravity() {
        // 获取左右手模式设置
        val isRightHanded = settingsManager.getBoolean("right_handed_mode", true)
        
        // 更新抽屉位置
        val drawerContent = findViewById<View>(R.id.nav_drawer)
        val params = drawerContent.layoutParams as DrawerLayout.LayoutParams
        params.gravity = if (isRightHanded) GravityCompat.START else GravityCompat.END
        drawerContent.layoutParams = params
        
        // 更新长按区域的判断
        edgeGravity = if (isRightHanded) GravityCompat.START else GravityCompat.END

        // 更新工具栏布局
        updateToolbarLayout(isRightHanded)
    }

    private fun updateToolbarLayout(isRightHanded: Boolean) {
        // 获取搜索栏容器
        val searchBarContainer = findViewById<AppBarLayout>(R.id.appbar) ?: return
        val searchBarParams = searchBarContainer.layoutParams as ViewGroup.MarginLayoutParams
        
        // 获取底部工具栏容器和按钮组
        val leftButtonGroup = findViewById<LinearLayout>(R.id.left_buttons)
        val rightButtonGroup = findViewById<LinearLayout>(R.id.right_buttons)
        val menuButton = findViewById<ImageButton>(R.id.btn_menu)
        val historyButton = findViewById<ImageButton>(R.id.btn_history)
        val bookmarksButton = findViewById<ImageButton>(R.id.btn_bookmarks)
        val settingsButton = findViewById<ImageButton>(R.id.btn_settings)
        
        if (isRightHanded) {
            // 右手模式
            searchBarParams.marginStart = resources.getDimensionPixelSize(R.dimen.search_bar_margin)
            searchBarParams.marginEnd = resources.getDimensionPixelSize(R.dimen.search_bar_margin)
            
            // 移动菜单按钮到左侧
            leftButtonGroup?.apply {
                removeAllViews()
                addView(menuButton)
            }
            
            // 功能按钮在右侧
            rightButtonGroup?.apply {
                removeAllViews()
                addView(historyButton)
                addView(bookmarksButton)
                addView(settingsButton)
            }
            
            // 设置边距
            leftButtonGroup?.setPadding(
                resources.getDimensionPixelSize(R.dimen.toolbar_edge_margin),
                0,
                resources.getDimensionPixelSize(R.dimen.toolbar_group_spacing),
                0
            )
            
            rightButtonGroup?.setPadding(
                resources.getDimensionPixelSize(R.dimen.toolbar_group_spacing),
                0,
                resources.getDimensionPixelSize(R.dimen.toolbar_edge_margin),
                0
            )
            
            // 重新设置按钮间距
            historyButton.layoutParams = (historyButton.layoutParams as LinearLayout.LayoutParams).apply {
                marginStart = 0
            }
            bookmarksButton.layoutParams = (bookmarksButton.layoutParams as LinearLayout.LayoutParams).apply {
                marginStart = resources.getDimensionPixelSize(R.dimen.toolbar_icon_spacing)
            }
            settingsButton.layoutParams = (settingsButton.layoutParams as LinearLayout.LayoutParams).apply {
                marginStart = resources.getDimensionPixelSize(R.dimen.toolbar_icon_spacing)
            }
        } else {
            // 左手模式
            searchBarParams.marginStart = resources.getDimensionPixelSize(R.dimen.search_bar_margin_left_handed)
            searchBarParams.marginEnd = resources.getDimensionPixelSize(R.dimen.search_bar_margin)
            
            // 移动菜单按钮到右侧
            rightButtonGroup?.apply {
                removeAllViews()
                addView(menuButton)
            }
            
            // 功能按钮在左侧
            leftButtonGroup?.apply {
                removeAllViews()
                addView(historyButton)
                addView(bookmarksButton)
                addView(settingsButton)
            }
            
            // 设置边距
            leftButtonGroup?.setPadding(
                resources.getDimensionPixelSize(R.dimen.toolbar_edge_margin_left_handed),
                0,
                resources.getDimensionPixelSize(R.dimen.toolbar_group_spacing),
                0
            )
            
            rightButtonGroup?.setPadding(
                resources.getDimensionPixelSize(R.dimen.toolbar_group_spacing),
                0,
                resources.getDimensionPixelSize(R.dimen.toolbar_edge_margin_left_handed),
                0
            )
            
            // 重新设置按钮间距
            historyButton.layoutParams = (historyButton.layoutParams as LinearLayout.LayoutParams).apply {
                marginStart = 0
            }
            bookmarksButton.layoutParams = (bookmarksButton.layoutParams as LinearLayout.LayoutParams).apply {
                marginStart = resources.getDimensionPixelSize(R.dimen.toolbar_icon_spacing)
            }
            settingsButton.layoutParams = (settingsButton.layoutParams as LinearLayout.LayoutParams).apply {
                marginStart = resources.getDimensionPixelSize(R.dimen.toolbar_icon_spacing)
            }
        }
        
        // 应用搜索栏布局参数
        searchBarContainer.layoutParams = searchBarParams
    }

    private fun applyDrawerTheme() {
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val themeMode = settingsManager.getThemeMode()
        val layoutTheme = settingsManager.getLayoutTheme()
        
        // 获取主题颜色
        val backgroundColor = ContextCompat.getColor(this, 
            if (isDarkMode) R.color.drawer_background_dark else R.color.drawer_background_light)
        val textColor = ContextCompat.getColor(this,
            if (isDarkMode) R.color.drawer_text_dark else R.color.drawer_text_light)
        val accentColor = ContextCompat.getColor(this,
            when (layoutTheme) {
                0 -> if (isDarkMode) R.color.fold_accent_dark else R.color.fold_accent_light
                2 -> if (isDarkMode) R.color.glass_accent_dark else R.color.glass_accent_light
                else -> if (isDarkMode) R.color.material_accent_dark else R.color.material_accent_light
            }
        )
        
        // 应用颜色到抽屉组件
        val drawerContent = findViewById<View>(R.id.nav_drawer)
        drawerContent.setBackgroundColor(backgroundColor)
        
        // 更新字母索引栏颜色
        letterIndexBar.setThemeColors(textColor, accentColor)
        letterIndexBar.setDarkMode(isDarkMode)
        
        // 更新字母标题颜色
        letterTitle.setTextColor(textColor)
        
        // 更新搜索引擎列表样式
        for (i in 0 until previewEngineList.childCount) {
            val view = previewEngineList.getChildAt(i)
            when (view) {
                is TextView -> view.setTextColor(textColor)
                is ImageView -> view.setColorFilter(textColor)
                else -> {
                    // 分隔线
                    view.setBackgroundColor(textColor.withAlpha(0.1f))
                }
            }
        }
    }

    private fun Int.withAlpha(alpha: Float): Int {
        val a = (alpha * 255).toInt()
        return this and 0x00FFFFFF or (a shl 24)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 检查是否在边缘区域
                val isInEdgeArea = when (edgeGravity) {
                    GravityCompat.START -> event.x <= edgeSizePixels
                    GravityCompat.END -> event.x >= resources.displayMetrics.widthPixels - edgeSizePixels
                    else -> false
                }
                
                if (isInEdgeArea) {
                    longPressStartTime = System.currentTimeMillis()
                }
            }
            MotionEvent.ACTION_UP -> {
                if (longPressStartTime > 0) {
                    val pressDuration = System.currentTimeMillis() - longPressStartTime
                    if (pressDuration >= LONG_PRESS_DURATION) {
                        // 根据左右手模式打开抽屉
                        when (edgeGravity) {
                            GravityCompat.START -> drawerLayout.openDrawer(GravityCompat.START)
                            GravityCompat.END -> drawerLayout.openDrawer(GravityCompat.END)
                        }
                        return true
                    }
                }
                longPressStartTime = 0
            }
            MotionEvent.ACTION_CANCEL -> {
                longPressStartTime = 0
            }
        }
        return super.dispatchTouchEvent(event)
    }

    private fun initGestureDetectors() {
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null) return false
                
                val distanceX = e2.x - e1.x
                val distanceY = e2.y - e1.y
                
                // 检测水平滑动
                if (Math.abs(distanceX) > Math.abs(distanceY) && Math.abs(velocityX) > 1000) {
                    if (distanceX > 0 && webView.canGoBack()) {
                        showGestureHint("返回上一页")
                        webView.goBack()
                        return true
                    } else if (distanceX < 0 && webView.canGoForward()) {
                        showGestureHint("前进下一页")
                        webView.goForward()
                        return true
                    }
                }
                return false
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                // 获取屏幕高度和点击位置
                val screenHeight = webView.height
                val tapY = e.y

                // 判断点击位置是在屏幕上半部分还是下半部分
                val scrollToTop = tapY < screenHeight / 2

                webView.evaluateJavascript("""
                    (function() {
                        window.scrollTo({
                            top: ${if (scrollToTop) "0" else "document.documentElement.scrollHeight"},
                            behavior: 'smooth'
                        });
                        return '${if (scrollToTop) "top" else "bottom"}';
                    })()
                """) { result ->
                    val destination = result?.replace("\"", "") ?: "top"
                    showGestureHint(if (destination == "top") "返回顶部" else "滚动到底部")
                }
                return true
            }
        })

        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private var baseScale = 1f
            private var lastSpan = 0f
            
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                baseScale = webView.scale
                lastSpan = detector.currentSpan
                isScaling = true
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                // 计算手指间距离的变化比例
                val spanRatio = detector.currentSpan / lastSpan
                lastSpan = detector.currentSpan
                
                // 使用比例计算新的缩放值，并添加阻尼效果
                val dampingFactor = 0.8f // 阻尼系数，使缩放更平滑
                val scaleFactor = 1f + (spanRatio - 1f) * dampingFactor
                
                val newScale = baseScale * scaleFactor
                
                // 限制缩放范围并应用缩放
                if (newScale in 0.1f..5.0f) {
                    webView.setInitialScale((newScale * 100).toInt())
                    baseScale = newScale
                    
                    // 只在缩放比例变化显著时显示提示
                    if (Math.abs(newScale - currentScale) > 0.02f) {
                        showGestureHint("缩放: ${(newScale * 100).toInt()}%")
                        currentScale = newScale
                    }
                    return true
                }
                return false
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isScaling = false
                baseScale = webView.scale
            }
        })
    }

    private fun showGestureHint(message: String) {
        // 取消之前的提示
        lastGestureHintRunnable?.let { gestureHintHandler.removeCallbacks(it) }
        
        // 显示新提示
        gestureHintView.text = message
        gestureHintView.alpha = 1f
        gestureHintView.visibility = View.VISIBLE
        
        // 创建淡出动画
        gestureHintView.animate()
            .alpha(0f)
            .setDuration(1000)
            .setStartDelay(500)
            .withEndAction {
                gestureHintView.visibility = View.GONE
            }
            .start()
        
        // 设置自动隐藏
        lastGestureHintRunnable = Runnable {
            gestureHintView.visibility = View.GONE
        }
        gestureHintHandler.postDelayed(lastGestureHintRunnable!!, 1500)
    }

    private fun initTabManagement() {
        viewPager = findViewById(R.id.view_pager)
        tabPreviewList = findViewById(R.id.tab_preview_list)
        tabPreviewContainer = findViewById(R.id.tab_preview_container)
        addTabButton = findViewById(R.id.btn_add_tab)
        
        // 设置标签预览列表
        tabPreviewList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        
        // 初始化标签管理器
        tabManager = TabManager(this)
        tabManager.initialize(viewPager, tabPreviewList)
        
        // 添加新标签页按钮
        addTabButton.setOnClickListener {
            tabManager.addTab()
        }
        
        // 添加第一个标签页
        tabManager.addTab()
    }

    private fun setupWebView() {
        // 使用WebViewHelper设置WebView
        WebViewHelper.setupWebView(webView)

        // 设置WebViewClient
        webView.webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String): Boolean {
                return handleUrlLoading(view, url)
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return request?.url?.toString()?.let { handleUrlLoading(view, it) } ?: false
            }

            private fun handleUrlLoading(view: WebView?, url: String): Boolean {
                view?.loadUrl(url)
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // 页面加载完成后更新UI
                webView.visibility = View.VISIBLE
                homeContent.visibility = View.GONE
                progressBar.visibility = View.GONE
                
                // 更新地址栏
                url?.let { searchInput.setText(it) }
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // 显示进度条
                progressBar.visibility = View.VISIBLE
                progressBar.progress = 0
            }
        }

        // 设置WebChromeClient
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                progressBar.progress = newProgress
                if (newProgress == 100) {
                    progressBar.visibility = View.GONE
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                // 可以选择是否在地址栏显示网页标题
                // title?.let { searchInput.setText(it) }
            }
        }
    }

    private fun loadContent(input: String) {
        try {
            // 检查是否是URL
            val isUrl = URLUtil.isValidUrl(input) || 
                       (input.contains(".") && !input.contains(" ")) ||
                       input.startsWith("http://") || 
                       input.startsWith("https://")

            val url = if (isUrl) {
                // 如果是URL，确保有http/https前缀
                if (input.startsWith("http://") || input.startsWith("https://")) {
                    input
                } else {
                    "https://$input"
                }
            } else {
                // 如果不是URL，使用搜索引擎搜索
                val encodedQuery = java.net.URLEncoder.encode(input, "UTF-8")
                "https://www.baidu.com/s?wd=$encodedQuery"
            }

            // 在WebView中加载URL
            webView.loadUrl(url)
            
            // 更新UI显示
            webView.visibility = View.VISIBLE
            homeContent.visibility = View.GONE

            // 清空输入框
            searchInput.setText("")

            Log.d(TAG, "正在加载URL: $url")

        } catch (e: Exception) {
            Log.e(TAG, "加载内容失败", e)
            Toast.makeText(this, "无法加载页面: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleMultiTouchGesture(event: MotionEvent) {
        if (event.pointerCount == 2) {
            val x1 = event.getX(0)
            val y1 = event.getY(0)
            val x2 = event.getX(1)
            val y2 = event.getY(1)
            
            // 计算两个手指的中心点
            val centerX = (x1 + x2) / 2
            val centerY = (y1 + y2) / 2
            
            // 计算移动方向
            when {
                abs(y1 - y2) > SWIPE_THRESHOLD -> {
                    // 垂直方向的手势
                    if (y1 > y2) {
                        onTwoFingerSwipeUp()
                    } else {
                        onTwoFingerSwipeDown()
                    }
                }
                abs(x1 - x2) > SWIPE_THRESHOLD -> {
                    // 水平方向的手势
                    if (x1 > x2) {
                        onTwoFingerSwipeLeft()
                    } else {
                        onTwoFingerSwipeRight()
                    }
                }
            }
        }
    }

    private fun onTwoFingerSwipeUp() {
        // TODO: 自定义双指上滑功能
        Toast.makeText(this, "双指上滑", Toast.LENGTH_SHORT).show()
    }

    private fun onTwoFingerSwipeDown() {
        // TODO: 自定义双指下滑功能
        Toast.makeText(this, "双指下滑", Toast.LENGTH_SHORT).show()
    }

    private fun onTwoFingerSwipeLeft() {
        // TODO: 自定义双指左滑功能
        Toast.makeText(this, "双指左滑", Toast.LENGTH_SHORT).show()
    }

    private fun onTwoFingerSwipeRight() {
        // TODO: 自定义双指右滑功能
        Toast.makeText(this, "双指右滑", Toast.LENGTH_SHORT).show()
    }

    private fun checkClipboard() {
        val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboardManager.hasPrimaryClip()) {
            val clipData = clipboardManager.primaryClip
            val clipText = clipData?.getItemAt(0)?.text?.toString()?.trim()
            
            if (!clipText.isNullOrEmpty()) {
                // 有剪贴板内容，显示对话框
                showClipboardDialog(clipText)
            }
        }
    }

    private fun showClipboardDialog(content: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.overlay_dialog, null)
        
        // 设置内容
        dialogView.findViewById<TextView>(R.id.dialog_title).text = "检测到剪贴板内容"
        dialogView.findViewById<TextView>(R.id.dialog_message).text = content
        
        // 创建对话框
        val dialog = AlertDialog.Builder(this, R.style.TransparentDialog)
            .setView(dialogView)
            .create()
        
        // 设置按钮点击事件
        if (URLUtil.isValidUrl(content)) {
            dialogView.findViewById<Button>(R.id.btn_open_link).apply {
                visibility = View.VISIBLE
                setOnClickListener {
                    dialog.dismiss()
                    openUrl(content)
                }
            }
        }
        
        dialogView.findViewById<Button>(R.id.btn_search).setOnClickListener {
            dialog.dismiss()
            searchContent(content)
        }
        
        dialogView.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            dialog.dismiss()
        }
        
        // 显示对话框
        dialog.show()
        
        // 设置自动关闭
        Handler(Looper.getMainLooper()).postDelayed({
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        }, 5000)
    }
    
    private fun openUrl(url: String) {
        try {
            // 确保URL格式正确
            val processedUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                "https://$url"
            } else {
                url
            }
            
            // 在当前标签页或新标签页打开URL
            val currentTab = tabManager.getCurrentTab()
            if (currentTab?.webView?.url == null || currentTab.webView?.url == "about:blank") {
                currentTab?.webView?.loadUrl(processedUrl)
            } else {
                tabManager.addTab(processedUrl)
            }
            
            // 更新UI显示
            viewPager.visibility = View.VISIBLE
            tabPreviewContainer.visibility = View.VISIBLE
        homeContent.visibility = View.GONE
            
            Log.d(TAG, "在HomeActivity中打开URL: $processedUrl")
        } catch (e: Exception) {
            Log.e(TAG, "打开URL失败", e)
            Toast.makeText(this, "无法打开页面: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun searchContent(query: String) {
        try {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://www.baidu.com/s?wd=$encodedQuery"
            
            // 在当前标签页或新标签页搜索
            val currentTab = tabManager.getCurrentTab()
            if (currentTab?.webView?.url == null || currentTab.webView?.url == "about:blank") {
                currentTab?.webView?.loadUrl(searchUrl)
            } else {
                tabManager.addTab(searchUrl)
            }
            
            // 更新UI显示
            viewPager.visibility = View.VISIBLE
            tabPreviewContainer.visibility = View.VISIBLE
        homeContent.visibility = View.GONE
            
            Log.d(TAG, "在HomeActivity中搜索内容: $query")
        } catch (e: Exception) {
            Log.e(TAG, "搜索内容失败", e)
            Toast.makeText(this, "搜索失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupSearch() {
        // 初始化WebView
        webView = findViewById(R.id.webview)
        if (webView == null) {
            Log.e(TAG, "WebView not found in layout")
            return
        }
        
        webView.settings.apply {
            // 启用JavaScript
            javaScriptEnabled = true
            // 支持缩放
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            // 自适应屏幕
            useWideViewPort = true
            loadWithOverviewMode = true
            // 支持多窗口
            setSupportMultipleWindows(true)
            // 启用DOM存储
            domStorageEnabled = true
            // 允许文件访问
            allowFileAccess = true
            // 混合内容
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            // 缓存模式
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String): Boolean {
                view?.loadUrl(url)
                return true
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                request?.url?.toString()?.let { view?.loadUrl(it) }
                return true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                webView.visibility = View.VISIBLE
                homeContent.visibility = View.GONE
            }
        }

        // 设置触摸事件监听
        webView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
            }
            false
        }

        // 设置长按监听器
        webView.setOnLongClickListener { view ->
            val hitTestResult = webView.hitTestResult
            when (hitTestResult.type) {
                WebView.HitTestResult.SRC_ANCHOR_TYPE, // 链接
                WebView.HitTestResult.IMAGE_TYPE, // 图片
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> { // 图片链接
                    // 创建一个临时的不可见 View 作为锚点
                    val anchor = View(this).apply {
                        x = lastTouchX
                        y = lastTouchY
                    }
                    webViewContainer.addView(anchor, 1, 1)
                    
                    // 显示长按菜单，使用 hitTestResult.extra 获取实际的 URL
                    showLongClickMenu(hitTestResult.extra ?: "", webView.title ?: "", anchor)
                    
                    // 移除临时锚点
                    webViewContainer.removeView(anchor)
                    true
                }
                else -> false
            }
        }

        // 设置搜索输入框
        searchInput = findViewById(R.id.search_input)
        searchInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                val query = searchInput.text.toString().trim()
                if (query.isNotEmpty()) {
                    performSearch(query)
                }
                true
            } else {
                false
            }
        }

        // 设置搜索按钮
        voiceSearchButton = findViewById(R.id.voice_search)
        voiceSearchButton.setOnClickListener {
            val query = searchInput.text.toString().trim()
            if (query.isNotEmpty()) {
                performSearch(query)
            }
        }
    }

    private fun performSearch(query: String) {
        try {
            // 增强的URL判断逻辑
            val isUrl = when {
                // 1. 标准URL格式判断
                URLUtil.isValidUrl(query) -> true
                
                // 2. 常见顶级域名判断
                query.matches(Regex(".+\\.(com|cn|net|org|gov|edu|mil|biz|info|mobi|name|asia|xxx|pro|wang|top|club|shop|site|vip|ltd|ink|tech|online|store|fun|website|space|press|video|party|cool|email|company|life|world|today|media|work|live|digital|studio|link|design|software|social|dev|cloud|app|games|news|blog|wiki|me|io|tv|cc|co|so|tel|red|kim|xyz|ai|show|art|run|gold|fit|fan|ren|love|beer|luxe|yoga|fund|city|host|zone|cash|guru|pub|bid|plus|chat|law|tax|team|band|cab|tips|jobs|one|men|bet|fish|sale|game|help|gift|loan|cars|auto|care|cafe|pet|fit|hair|baby|toys|land|farm|food|wine|vote|voto|date|wed|sexy|sex|gay|porn|xxx|adult|sex|cam|xxx|porn|bet|tube|cam|pics|gay|sex|porn|xxx|loan)$", RegexOption.IGNORE_CASE)) -> true
                
                // 3. IP地址格式判断
                query.matches(Regex("^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$")) -> true
                
                // 4. 带协议的URL
                query.startsWith("http://") || query.startsWith("https://") -> true
                
                // 5. 常见二级域名格式
                query.matches(Regex("^[\\w-]+\\.[\\w-]+(\\.[a-zA-Z]{2,})+$")) -> true
                
                // 6. 不包含空格且包含点号的简单域名判断
                query.contains(".") && !query.contains(" ") && !query.contains("。") && query.matches(Regex("^[\\w-]+(\\.[\\w-]+)+$")) -> true
                
                // 其他情况视为搜索关键词
                else -> false
            }

            val url = if (isUrl) {
                // 处理特殊域名
                val processedUrl = when {
                    // Google特殊处理
                    query.contains("google") -> "https://www.google.com/search"
                    // 其他域名正常处理
                    else -> {
                        if (query.startsWith("http://") || query.startsWith("https://")) {
                            query
                        } else {
                            "https://${if (query.startsWith("www.")) query else "www.$query"}"
                        }
                    }
                }
                processedUrl
            } else {
                // 如果不是URL，使用搜索引擎搜索
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                "https://www.baidu.com/s?wd=$encodedQuery"
            }

            // 设置移动端User-Agent和WebView配置
            webView.settings.apply {
                // 设置UA
                userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                
                // 基本设置
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                
                // 缓存设置
                cacheMode = WebSettings.LOAD_DEFAULT
                
                // 页面自适应
                useWideViewPort = true
                loadWithOverviewMode = true
                
                // 缩放设置
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                
                // 多窗口支持
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = true
                
                // 混合内容
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                
                // 设置默认编码
                defaultTextEncodingName = "UTF-8"
                
                // 允许文件访问
                allowFileAccess = true
                
                // 允许内容URL访问
                allowContentAccess = true
            }

            // 在WebView中加载URL
            webView.loadUrl(url)
            
            // 更新UI显示
            webView.visibility = View.VISIBLE
            homeContent.visibility = View.GONE
            
            // 不要立即清空输入框，让 WebViewClient 的 onPageFinished 来处理
            // searchInput.setText("")

            // 记录日志
            Log.d(TAG, "输入内容: $query")
            Log.d(TAG, "是否URL: $isUrl")
            Log.d(TAG, "最终URL: $url")
            Log.d(TAG, "User-Agent: ${webView.settings.userAgentString}")

        } catch (e: Exception) {
            Log.e(TAG, "加载内容失败", e)
            Toast.makeText(this, "无法加载页面: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupShortcuts() {
        shortcutsGrid.layoutManager = GridLayoutManager(this, 4)
        // TODO: 实现快捷方式适配器
    }

    private fun setupBottomBar() {
        findViewById<ImageButton>(R.id.btn_menu).setOnClickListener {
            showMenuPanel()
        }

        findViewById<ImageButton>(R.id.btn_history).setOnClickListener {
            // TODO: 显示历史记录
            Toast.makeText(this, "历史记录功能即将推出", Toast.LENGTH_SHORT).show()
        }

        findViewById<ImageButton>(R.id.btn_bookmarks).setOnClickListener {
            // 跳转到书签页面
            startActivity(Intent(this, BookmarkActivity::class.java))
        }

        findViewById<ImageButton>(R.id.btn_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun showMenuPanel() {
        if (!::menuDialog.isInitialized) {
            menuDialog = BottomSheetDialog(this)
            val view = layoutInflater.inflate(R.layout.layout_menu_panel, null)
            
            // Inflate and add settings menu
            val settingsMenu = layoutInflater.inflate(R.layout.layout_settings_menu, null)
            val settingsContainer = view.findViewById<FrameLayout>(R.id.settings_container)
            settingsContainer?.addView(settingsMenu)
            
            menuDialog.setContentView(view)

            // 设置开关状态和监听器
            setupSwitches(view)
            setupButtons(view)
            
            // 初始化设置菜单中的开关
            autoHideSwitch = settingsMenu.findViewById(R.id.auto_hide_switch)
            clipboardSwitch = settingsMenu.findViewById(R.id.clipboard_switch)
            
            // 更新开关状态
            updateSwitchStates()
        }
        menuDialog.show()
    }

    private fun setupSwitches(view: View) {
        // 无图模式
        view.findViewById<LinearLayout>(R.id.layout_no_image).apply {
            val iconContainer = getChildAt(0) as FrameLayout
            setOnClickListener {
                isNoImageMode = !isNoImageMode
                updateButtonState(
                    isNoImageMode,
                    findViewById(R.id.icon_no_image),
                    findViewById(R.id.text_no_image),
                    iconContainer
                )
                if (isNoImageMode) {
                    applyImageFilter()
                } else {
                    removeImageFilter()
                }
            }
            // 初始状态
            updateButtonState(
                isNoImageMode,
                findViewById(R.id.icon_no_image),
                findViewById(R.id.text_no_image),
                iconContainer
            )
        }

        // 电脑版
        view.findViewById<LinearLayout>(R.id.layout_desktop_mode).apply {
            val iconContainer = getChildAt(0) as FrameLayout
            setOnClickListener {
                isDesktopMode = !isDesktopMode
                updateButtonState(
                    isDesktopMode,
                    findViewById(R.id.icon_desktop_mode),
                    findViewById(R.id.text_desktop_mode),
                    iconContainer
                )
                webView.settings.userAgentString = if (isDesktopMode) {
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"
                } else {
                    null
                }
                webView.reload()
            }
            // 初始状态
            updateButtonState(
                isDesktopMode,
                findViewById(R.id.icon_desktop_mode),
                findViewById(R.id.text_desktop_mode),
                iconContainer
            )
        }

        // 广告过滤
        view.findViewById<LinearLayout>(R.id.layout_ad_block).apply {
            val iconContainer = getChildAt(0) as FrameLayout
            setOnClickListener {
                isAdBlockEnabled = !isAdBlockEnabled
                updateButtonState(
                    isAdBlockEnabled,
                    findViewById(R.id.icon_ad_block),
                    findViewById(R.id.text_ad_block),
                    iconContainer
                )
            }
            // 初始状态
            updateButtonState(
                isAdBlockEnabled,
                findViewById(R.id.icon_ad_block),
                findViewById(R.id.text_ad_block),
                iconContainer
            )
        }

        // 隐身模式
        view.findViewById<LinearLayout>(R.id.layout_incognito).apply {
            val iconContainer = getChildAt(0) as FrameLayout
            setOnClickListener {
                isIncognitoMode = !isIncognitoMode
                updateButtonState(
                    isIncognitoMode,
                    findViewById(R.id.icon_incognito),
                    findViewById(R.id.text_incognito),
                    iconContainer
                )
                if (isIncognitoMode) {
                    webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    webView.clearCache(true)
                    webView.clearHistory()
                    webView.clearFormData()
                    CookieManager.getInstance().removeAllCookies(null)
                    WebStorage.getInstance().deleteAllData()
                } else {
                    webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
                }
            }
            // 初始状态
            updateButtonState(
                isIncognitoMode,
                findViewById(R.id.icon_incognito),
                findViewById(R.id.text_incognito),
                iconContainer
            )
        }
    }

    private fun setupButtons(view: View) {
        // 分享
        view.findViewById<LinearLayout>(R.id.btn_share).apply {
            setOnClickListener {
                if (webView.visibility == View.VISIBLE) {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, webView.url)
                    }
                    startActivity(Intent.createChooser(intent, "分享页面"))
                }
                menuDialog.dismiss()
            }
        }

        // 历史
        view.findViewById<LinearLayout>(R.id.btn_history).apply {
            setOnClickListener {
                // TODO: 实现历史记录功能
                Toast.makeText(this@HomeActivity, "历史记录功能即将推出", Toast.LENGTH_SHORT).show()
                menuDialog.dismiss()
            }
        }

        // 书签
        view.findViewById<LinearLayout>(R.id.btn_bookmark).apply {
            setOnClickListener {
                // 跳转到书签管理页面
                startActivity(Intent(this@HomeActivity, BookmarkActivity::class.java))
                menuDialog.dismiss()
            }
        }

        // 夜间模式
        view.findViewById<LinearLayout>(R.id.btn_night_mode).apply {
            val iconContainer = getChildAt(0) as FrameLayout
            setOnClickListener {
                isNightMode = !isNightMode
                updateButtonState(
                    isNightMode,
                    findViewById(R.id.icon_night_mode),
                    findViewById(R.id.text_night_mode),
                    iconContainer
                )
                if (isNightMode) {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                } else {
                    AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                }
                menuDialog.dismiss()
            }
            // 初始状态
            updateButtonState(
                isNightMode,
                findViewById(R.id.icon_night_mode),
                findViewById(R.id.text_night_mode),
                iconContainer
            )
        }
    }

    private fun updateButtonState(isActive: Boolean, icon: ImageView, text: TextView, background: FrameLayout) {
        if (isActive) {
            icon.setColorFilter(getColor(android.R.color.white))
            text.setTextColor(getColor(android.R.color.white))
            background.background = getDrawable(R.drawable.circle_ripple_active)
        } else {
            icon.setColorFilter(getColor(android.R.color.darker_gray))
            text.setTextColor(getColor(android.R.color.darker_gray))
            background.background = getDrawable(R.drawable.circle_ripple)
        }
    }

    private fun openSearchEngine(engine: SearchEngine, query: String = "") {
        // 检查是否应该使用悬浮窗模式
        val useFloatingMode = settingsManager.getBoolean("use_floating_mode", false)
        
        if (useFloatingMode) {
            // 检查是否有SYSTEM_ALERT_WINDOW权限
            if (!Settings.canDrawOverlays(this)) {
                // 没有权限，请求权限
                Toast.makeText(this, "需要悬浮窗权限", Toast.LENGTH_SHORT).show()
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
                return
            }
            
            // 有权限，启动悬浮窗服务
            val intent = Intent(this, DualFloatingWebViewService::class.java)
            
            if (query.isEmpty()) {
                // 如果没有查询文本，直接打开搜索引擎主页
                intent.putExtra("url", engine.url.replace("{query}", "").replace("search?q=", "")
                    .replace("search?query=", "")
                    .replace("search?word=", "")
                    .replace("s?wd=", ""))
            } else {
                // 有查询文本，使用搜索引擎进行搜索
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                val searchUrl = engine.url.replace("{query}", encodedQuery)
                intent.putExtra("url", searchUrl)
            }
            
            // 获取用户设置的窗口数量
            val windowCount = settingsManager.getDefaultWindowCount()
            intent.putExtra("window_count", windowCount)
            
            startService(intent)
        } else {
            // 使用普通模式
            if (query.isEmpty()) {
                // 如果没有查询文本，直接打开搜索引擎主页
                val url = engine.url.replace("{query}", "").replace("search?q=", "")
                    .replace("search?query=", "")
                    .replace("search?word=", "")
                    .replace("s?wd=", "")
                tabManager.addTab(url)
            } else {
                // 有查询文本，使用搜索引擎进行搜索
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                val searchUrl = engine.url.replace("{query}", encodedQuery)
                tabManager.addTab(searchUrl)
            }
            viewPager.visibility = View.VISIBLE
            tabPreviewContainer.visibility = View.VISIBLE
                homeContent.visibility = View.GONE
        }
    }

    private fun showEngineSettings(engine: SearchEngine) {
        val options = arrayOf("访问主页", "在悬浮窗中打开", "复制链接", "分享", "在浏览器中打开")

        AlertDialog.Builder(this)
            .setTitle("${engine.name} 选项")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openSearchEngine(engine)
                    1 -> {
                        // 在悬浮窗中打开
                        settingsManager.putBoolean("use_floating_mode", true)
                        openSearchEngine(engine)
                        settingsManager.putBoolean("use_floating_mode", false) // 重置为默认值
                    }
                    2 -> {
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("URL", engine.url))
                        Toast.makeText(this, "已复制链接", Toast.LENGTH_SHORT).show()
                    }
                    3 -> {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "${engine.name}: ${engine.url}")
                        }
                        startActivity(Intent.createChooser(intent, "分享到"))
                    }
                    4 -> {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(engine.url))
                        startActivity(intent)
                    }
                }
            }
            .show()
    }

    private fun toggleFloatingMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            // 请求悬浮窗权限
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
            Toast.makeText(this, "请授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            return
        }

        // 启动双窗口悬浮服务
        val intent = Intent(this, DualFloatingWebViewService::class.java)
        
        // 获取用户设置的窗口数量
        val windowCount = settingsManager.getDefaultWindowCount()
        intent.putExtra("window_count", windowCount)
        
        // 如果当前在WebView中有URL，传递给服务
        if (webView.visibility == View.VISIBLE && webView.url != null) {
            intent.putExtra("url", webView.url)
        }
        
        if (DualFloatingWebViewService.isRunning) {
            stopService(intent)
        } else {
            startService(intent)
        }
        finish()
    }

    // 更新主题
    private fun updateTheme() {
        try {
            when (settingsManager.getThemeMode()) {
                SettingsManager.THEME_MODE_SYSTEM -> {
                    window.statusBarColor = getColor(R.color.colorPrimaryDark)
                    window.navigationBarColor = getColor(R.color.colorPrimaryDark)
                    rootLayout.setBackgroundColor(getColor(R.color.colorBackground))
                }
                SettingsManager.THEME_MODE_LIGHT -> {
                    window.statusBarColor = getColor(R.color.colorLightPrimaryDark)
                    window.navigationBarColor = getColor(R.color.colorLightPrimaryDark)
                    rootLayout.setBackgroundColor(getColor(R.color.colorLightBackground))
                }
                SettingsManager.THEME_MODE_DARK -> {
                    window.statusBarColor = getColor(R.color.colorDarkPrimaryDark)
                    window.navigationBarColor = getColor(R.color.colorDarkPrimaryDark)
                    rootLayout.setBackgroundColor(getColor(R.color.colorDarkBackground))
                }
            }
        } catch (e: Resources.NotFoundException) {
            Log.e("HomeActivity", "Error applying theme: ${e.message}")
            window.statusBarColor = Color.parseColor("#1976D2")
            rootLayout.setBackgroundColor(Color.WHITE)
        }
    }

    // 保存设置
    private fun saveSettings() {
        // 使用 SettingsManager 直接保存设置，不依赖 UI 组件
        val autoHideEnabled = autoHideSwitch?.isChecked ?: settingsManager.getBoolean("auto_hide", false)
        val clipboardEnabled = clipboardSwitch?.isChecked ?: settingsManager.getBoolean("clipboard_enabled", true)
        
        settingsManager.putBoolean("auto_hide", autoHideEnabled)
        settingsManager.putBoolean("clipboard_enabled", clipboardEnabled)
    }

    // 加载设置
    private fun loadSettings() {
        // 如果 UI 组件存在，则将设置值加载到它们上
        autoHideSwitch?.isChecked = settingsManager.getBoolean("auto_hide", false)
        clipboardSwitch?.isChecked = settingsManager.getBoolean("clipboard_enabled", true)
    }

    private fun updateSwitchStates() {
        try {
            // 更新自动隐藏开关状态
            autoHideSwitch?.isChecked = settingsManager.getBoolean("auto_hide_enabled", true)
            autoHideSwitch?.setOnCheckedChangeListener { _, isChecked ->
                settingsManager.putBoolean("auto_hide_enabled", isChecked)
            }
            
            // 更新剪贴板监听开关状态
            clipboardSwitch?.isChecked = settingsManager.isClipboardListenerEnabled()
            clipboardSwitch?.setOnCheckedChangeListener { _, isChecked ->
                settingsManager.setClipboardListenerEnabled(isChecked)
            }
        } catch (e: Exception) {
            Log.e(TAG, "更新开关状态时出错", e)
        }
    }

    override fun onDown(e: MotionEvent): Boolean = true

    override fun onShowPress(e: MotionEvent) {}

    override fun onSingleTapUp(e: MotionEvent): Boolean = false

    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean = false

    override fun onLongPress(e: MotionEvent) {
        // TODO: 长按操作
    }

    override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
        try {
            if (e1 == null) return false
            val diffY = e2.y - e1.y
            val diffX = e2.x - e1.x
            if (abs(diffX) > abs(diffY)) {
                if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffX > 0) {
                        // 右滑
                        onSwipeRight()
                    } else {
                        // 左滑
                        onSwipeLeft()
                    }
                }
            } else {
                if (abs(diffY) > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffY > 0) {
                        // 下滑
                        onSwipeDown()
                    } else {
                        // 上滑
                        onSwipeUp()
                    }
                }
            }
        } catch (exception: Exception) {
            exception.printStackTrace()
        }
        return true
    }

    private fun onSwipeRight() {
        // TODO: 右滑操作，例如：返回
        val currentTab = tabManager.getCurrentTab()
        if (currentTab?.webView?.canGoBack() == true) {
            currentTab.webView.goBack()
        }
    }

    private fun onSwipeLeft() {
        // TODO: 左滑操作，例如：前进
        val currentTab = tabManager.getCurrentTab()
        if (currentTab?.webView?.canGoForward() == true) {
            currentTab.webView.goForward()
        }
    }

    private fun onSwipeUp() {
        // TODO: 上滑操作，例如：刷新
        val currentTab = tabManager.getCurrentTab()
        currentTab?.webView?.reload()
    }

    private fun onSwipeDown() {
        // TODO: 下滑操作，例如：显示通知栏
    }

    private fun setupLetterIndexBar() {
        try {
            // 获取视图引用
            letterIndexBar = findViewById(R.id.letter_index_bar)
            letterTitle = findViewById(R.id.letter_title)
            previewEngineList = findViewById(R.id.preview_engine_list)

            // 确保视图不为空
            if (letterIndexBar == null || letterTitle == null || previewEngineList == null) {
                Log.e(TAG, "setupLetterIndexBar: 视图引用为空")
                return
            }

            // 获取所有可用的搜索引擎(包括普通搜索引擎和AI搜索引擎)
            val allEngines = ArrayList<SearchEngine>()
            
            // 从SearchEngine类获取默认引擎
            val defaultEngines = SearchEngine.DEFAULT_ENGINES
            
            // 从AISearchEngine类获取默认AI引擎
            val aiEngines = AISearchEngine.DEFAULT_AI_ENGINES.map { 
                // 将AISearchEngine转换为SearchEngine，保留isAI标记
                SearchEngine(
                    name = it.name,
                    displayName = it.name,
                    url = it.url,
                    iconResId = it.iconResId,
                    description = it.description,
                    searchUrl = it.searchUrl ?: it.url,
                    isAI = true
                )
            }
            
            // 合并所有引擎
            allEngines.addAll(defaultEngines)
            allEngines.addAll(aiEngines)
            
            // 从SettingsManager获取所有已启用的引擎
            val enabledEngines = settingsManager.getAllEnabledEngines()
            
            // 仅保留已启用的引擎，如果没有已启用引擎，则使用所有引擎
            val filteredEngines = if (enabledEngines.isNotEmpty()) {
                allEngines.filter { enabledEngines.contains(it.name) }
            } else {
                allEngines
            }
            
            Log.d(TAG, "获取到搜索引擎总数: ${filteredEngines.size}，其中AI搜索引擎: ${filteredEngines.count { it.isAI }}个")
            
            // 打印所有引擎的名称和图标资源ID
            filteredEngines.forEach { engine ->
                Log.d(TAG, "搜索引擎: ${engine.name}, 图标ID: ${engine.iconResId}, 是否AI: ${engine.isAI}")
            }
            
            try {
                letterIndexBar.engines = filteredEngines
                Log.d(TAG, "成功设置字母索引栏引擎列表")
            } catch (e: Exception) {
                Log.e(TAG, "设置字母索引栏引擎列表失败", e)
            }

            // 设置字母选择监听器
            letterIndexBar.onLetterSelectedListener = object : LetterIndexBar.OnLetterSelectedListener {
                override fun onLetterSelected(view: View, letter: Char) {
                    updateEngineList(letter)
                }
            }

            // 初始显示"全部"选项，使用#字符作为标识
            updateEngineList('#')
            
            Log.d(TAG, "setupLetterIndexBar: 初始化完成，搜索引擎总数: ${filteredEngines.size}")
        } catch (e: Exception) {
            Log.e(TAG, "setupLetterIndexBar failed", e)
        }
    }

    private fun updateEngineList(letter: Char) {
        try {
            // 更新字母标题
            letterTitle.text = if (letter == '#') "全部" else letter.toString()
            letterTitle.visibility = View.VISIBLE
            
            // 清空现有列表
            previewEngineList.removeAllViews()
            previewEngineList.visibility = View.VISIBLE

            // 获取当前主题模式
            val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

            // 获取所有可用的搜索引擎(包括普通搜索引擎和AI搜索引擎)
            val allEngines = ArrayList<SearchEngine>()
            
            // 从SearchEngine类获取默认引擎
            val defaultEngines = SearchEngine.DEFAULT_ENGINES
            
            // 从AISearchEngine类获取默认AI引擎
            val aiEngines = AISearchEngine.DEFAULT_AI_ENGINES.map { 
                // 将AISearchEngine转换为SearchEngine，保留isAI标记
                SearchEngine(
                    name = it.name,
                    displayName = it.name,
                    url = it.url,
                    iconResId = it.iconResId,
                    description = it.description,
                    searchUrl = it.searchUrl ?: it.url,
                    isAI = true
                )
            }
            
            // 合并所有引擎
            allEngines.addAll(defaultEngines)
            allEngines.addAll(aiEngines)
            
            // 从SettingsManager获取所有已启用的引擎
            val enabledEngines = settingsManager.getAllEnabledEngines()
            
            // 仅保留已启用的引擎，如果没有已启用引擎，则使用所有引擎
            val filteredEngines = if (enabledEngines.isNotEmpty()) {
                allEngines.filter { enabledEngines.contains(it.name) }
            } else {
                allEngines
            }

            Log.d(TAG, "updateEngineList: 总搜索引擎数量: ${filteredEngines.size}")

            // 如果选择的是"全部"（#字符），显示所有引擎，否则按字母筛选
            val matchingEngines = if (letter == '#') {
                // 选择全部，返回所有引擎
                filteredEngines
            } else {
                // 按字母筛选
                filteredEngines.filter { engine ->
                val firstChar = engine.name.first()
                when {
                    firstChar.toString().matches(Regex("[A-Za-z]")) -> 
                        firstChar.uppercaseChar() == letter.uppercaseChar()
                    firstChar.toString().matches(Regex("[\u4e00-\u9fa5]")) -> {
                            try {
                        val pinyinArray = PinyinHelper.toHanyuPinyinStringArray(firstChar)
                                val result = pinyinArray?.firstOrNull()?.firstOrNull()?.uppercaseChar() == letter.uppercaseChar()
                                result
                            } catch (e: Exception) {
                                Log.e(TAG, "拼音转换失败: ${e.message}", e)
                                // 如果拼音转换失败，使用简单的首字母匹配
                                false
                            }
                    }
                    else -> false
                    }
                }
            }

            Log.d(TAG, "updateEngineList: 匹配的搜索引擎数量: ${matchingEngines.size}")

            if (matchingEngines.isEmpty()) {
                // 如果没有匹配的搜索引擎，显示提示信息
                val noEngineText = TextView(this).apply {
                    text = if (letter == '#') 
                        "没有可用的搜索引擎" 
                    else 
                        "没有以 $letter 开头的搜索引擎"
                    textSize = 16f
                    setTextColor(ContextCompat.getColor(context, if (isDarkMode) android.R.color.white else android.R.color.black))
                    gravity = Gravity.CENTER
                    setPadding(16, 32, 16, 32)
                }
                previewEngineList.addView(noEngineText)
            } else {
                // 分类显示搜索引擎
                val aiEngines = matchingEngines.filter { it.isAI }
                val normalEngines = matchingEngines.filter { !it.isAI }

                // 是否显示分类标题
                val showCategory = settingsManager.showAIEngineCategory()

                // 如果有AI搜索引擎，添加AI分类标题
                if (aiEngines.isNotEmpty()) {
                    if (showCategory) {
                    addCategoryTitle("AI搜索", isDarkMode)
                    }
                    aiEngines.forEach { engine ->
                        addEngineItem(engine, isDarkMode)
                    }
                }

                // 如果有普通搜索引擎，添加普通搜索分类标题
                if (normalEngines.isNotEmpty()) {
                    if (aiEngines.isNotEmpty() && showCategory) {
                        // 如果之前有AI搜索引擎，添加分隔线
                        addDivider(isDarkMode)
                    }
                    if (showCategory) {
                    addCategoryTitle("普通搜索", isDarkMode)
                    }
                    normalEngines.forEach { engine ->
                        addEngineItem(engine, isDarkMode)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "updateEngineList failed", e)
            Toast.makeText(this, "更新搜索引擎列表失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addCategoryTitle(title: String, isDarkMode: Boolean) {
        TextView(this).apply {
            text = title
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, if (isDarkMode) android.R.color.white else android.R.color.black))
            setPadding(16, 16, 16, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }.also { previewEngineList.addView(it) }
    }

    private fun addEngineItem(engine: SearchEngine, isDarkMode: Boolean) {
        try {
            val engineItem = LayoutInflater.from(this).inflate(
                R.layout.item_search_engine,
                previewEngineList,
                false
            )

            // 设置引擎图标
            engineItem.findViewById<ImageView>(R.id.engine_icon).apply {
                try {
                    // 尝试设置图标资源，如果失败则使用默认图标
                    if (engine.iconResId != 0) {
                setImageResource(engine.iconResId)
                    } else {
                        setImageResource(R.drawable.ic_search)
                    }
                } catch (e: Exception) {
                    // 如果无法加载指定的图标，使用默认图标
                    Log.e(TAG, "加载图标失败: ${engine.name}, 使用默认图标", e)
                    setImageResource(R.drawable.ic_search)
                }
                
                // 设置图标颜色
                setColorFilter(ContextCompat.getColor(context, 
                    if (isDarkMode) android.R.color.white else android.R.color.black))
            }

            // 设置引擎名称
            engineItem.findViewById<TextView>(R.id.engine_name).apply {
                text = engine.name
                setTextColor(ContextCompat.getColor(context, 
                    if (isDarkMode) android.R.color.white else android.R.color.black))
            }

            // 设置引擎描述
            engineItem.findViewById<TextView>(R.id.engine_description)?.apply {
                text = engine.description
                setTextColor(ContextCompat.getColor(context, 
                    if (isDarkMode) android.R.color.darker_gray else android.R.color.darker_gray))
                visibility = if (engine.description.isNotEmpty()) View.VISIBLE else View.GONE
            }

            // 隐藏开关按钮
            engineItem.findViewById<SwitchCompat>(R.id.engine_toggle)?.visibility = View.GONE

            // 设置点击事件
            engineItem.setOnClickListener {
                val query = searchInput.text.toString().trim()
                openSearchEngine(engine, query)
                drawerLayout.closeDrawer(if (settingsManager.getBoolean("right_handed_mode", true)) 
                    GravityCompat.START else GravityCompat.END)
            }

            // 设置长按事件
            engineItem.setOnLongClickListener {
                showEngineSettings(engine)
                true
            }

            // 设置水波纹效果
            engineItem.background = ContextCompat.getDrawable(this, 
                if (isDarkMode) R.drawable.ripple_dark else R.drawable.ripple_light)

            previewEngineList.addView(engineItem)
            
            Log.d(TAG, "添加搜索引擎项目: ${engine.name}, 图标ID: ${engine.iconResId}")
        } catch (e: Exception) {
            Log.e(TAG, "添加搜索引擎项目失败: ${engine.name}", e)
        }
    }

    private fun addDivider(isDarkMode: Boolean) {
        View(this).apply {
            setBackgroundColor(ContextCompat.getColor(context, 
                if (isDarkMode) R.color.divider_dark else R.color.divider_light))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                1
            ).apply {
                setMargins(16, 8, 16, 8)
            }
        }.also { previewEngineList.addView(it) }
    }

    private fun String.containsAdUrl(): Boolean {
        val adPatterns = listOf(
            "ads", "analytics", "tracker", "doubleclick",
            "pagead", "banner", "popup", "stats"
        )
        return adPatterns.any { this.contains(it, ignoreCase = true) }
    }

    private fun applyImageFilter() {
        val currentTab = tabManager.getCurrentTab()
        currentTab?.webView?.let { webView ->
            val js = """
                javascript:(function() {
                    var css = `
                        img, picture, video, canvas {
                            filter: grayscale(100%) opacity(70%) !important;
                            transition: filter 0.3s ease-in-out !important;
                        }
                        img:hover, picture:hover, video:hover, canvas:hover {
                            filter: grayscale(0%) opacity(100%) !important;
                        }
                    `;
                    var style = document.createElement('style');
                    style.id = 'low-image-style';
                    style.type = 'text/css';
                    style.appendChild(document.createTextNode(css));
                    document.head.appendChild(style);
                })()
            """.trimIndent()
            webView.evaluateJavascript(js, null)
        }
    }

    private fun removeImageFilter() {
        val currentTab = tabManager.getCurrentTab()
        currentTab?.webView?.let { webView ->
            val js = """
                javascript:(function() {
                    var style = document.getElementById('low-image-style');
                    if (style) {
                        style.remove();
                    }
                })()
            """.trimIndent()
            webView.evaluateJavascript(js, null)
        }
    }

    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            onBackPressedDispatcher.onBackPressed()
        } else {
            when {
                tabManager.onBackPressed() -> return
                viewPager.visibility == View.VISIBLE -> {
                    viewPager.visibility = View.GONE
                    tabPreviewContainer.visibility = View.GONE
                    homeContent.visibility = View.VISIBLE
                }
                else -> super.onBackPressed()
            }
        }
    }

    // 显示长按菜单
    private fun showLongClickMenu(url: String, title: String, anchor: View) {
        val popupMenu = PopupMenu(this, anchor, Gravity.NO_GRAVITY)
        val inflater = popupMenu.menuInflater
        inflater.inflate(R.menu.long_press_menu, popupMenu.menu)
        
        // 获取书签管理器
        val bookmarkManager = BookmarkManager.getInstance(this)
        
        // 检查当前页面是否已加入书签
        val isBookmarked = bookmarkManager.isBookmarkExist(url)
        
        // 更新菜单项文字
        val menuItem = popupMenu.menu.findItem(R.id.action_add_bookmark)
        if (menuItem != null) {
            menuItem.setTitle(if (isBookmarked) getString(R.string.action_edit_bookmark) else getString(R.string.action_add_bookmark))
        }
        
        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_add_bookmark -> {
                    if (isBookmarked) {
                        // 编辑现有书签
                        val bookmark = bookmarkManager.getAllBookmarks().find { bookmark -> bookmark.url == url }
                        if (bookmark != null) {
                            showEditBookmarkDialog(bookmark)
                        }
                    } else {
                        // 添加新书签
                        showAddBookmarkDialog(url, title)
                    }
                    true
                }
                R.id.action_background_open -> {
                    // 在新标签页后台打开链接
                    tabManager.addTab(url, loadInBackground = true)
                    Toast.makeText(this, "已在后台打开链接", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_copy_url -> {
                    // 复制链接
                    val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clipData = ClipData.newPlainText("URL", url)
                    clipboardManager.setPrimaryClip(clipData)
                    Toast.makeText(this, "链接已复制", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_copy_text -> {
                    // 复制链接文本
                    webView.evaluateJavascript("""
                        (function() {
                            var links = document.getElementsByTagName('a');
                            for(var i = 0; i < links.length; i++) {
                                if(links[i].href === "$url") {
                                    return links[i].textContent.trim();
                                }
                            }
                            return "";
                        })()
                    """.trimIndent()) { result ->
                        val linkText = result?.replace("\"", "") ?: ""
                        if (linkText.isNotEmpty()) {
                            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clipData = ClipData.newPlainText("Link Text", linkText)
                            clipboardManager.setPrimaryClip(clipData)
                            Toast.makeText(this, "链接文本已复制", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "无法获取链接文本", Toast.LENGTH_SHORT).show()
                        }
                    }
                    true
                }
                R.id.action_share -> {
                    // 分享链接
                    val intent = Intent(Intent.ACTION_SEND)
                    intent.type = "text/plain"
                    intent.putExtra(Intent.EXTRA_TEXT, url)
                    startActivity(Intent.createChooser(intent, "分享页面"))
                    true
                }
                else -> false
            }
        }
        
        popupMenu.show()
    }
    
    // 显示添加书签对话框
    private fun showAddBookmarkDialog(url: String, title: String) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_bookmark, null)
        val titleField = dialogView.findViewById<TextInputEditText>(R.id.bookmark_title_field)
        val urlField = dialogView.findViewById<TextInputEditText>(R.id.bookmark_url_field)
        
        titleField.setText(title)
        urlField.setText(url)
        
        // 创建对话框
        AlertDialog.Builder(this)
            .setTitle("添加书签")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val newTitle = titleField.text.toString().trim()
                val newUrl = urlField.text.toString().trim()
                
                if (newTitle.isNotEmpty() && newUrl.isNotEmpty()) {
                    // 创建书签对象
                    val bookmark = Bookmark(
                        title = newTitle,
                        url = newUrl
                    )
                    
                    // 获取当前网页的favicon
                    webView.evaluateJavascript(
                        """
                        (function() {
                            var favicon = undefined;
                            var links = document.getElementsByTagName('link');
                            for (var i = 0; i < links.length; i++) {
                                if ((links[i].getAttribute('rel') || '').match(/\b(icon|shortcut icon|apple-touch-icon)\b/i)) {
                                    favicon = links[i].getAttribute('href');
                                    break;
                                }
                            }
                            return favicon;
                        })();
                        """.trimIndent()
                    ) { result ->
                        // 处理JavaScript返回的favicon URL
                        var faviconUrl = result
                        
                        // 清理引号
                        if (faviconUrl != "null" && faviconUrl.startsWith("\"") && faviconUrl.endsWith("\"")) {
                            faviconUrl = faviconUrl.substring(1, faviconUrl.length - 1)
                        }
                        
                        // 保存书签
                        val bookmarkManager = BookmarkManager.getInstance(this)
                        bookmarkManager.addBookmark(bookmark)
                        
                        Toast.makeText(this, "已添加到书签", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    // 显示编辑书签对话框
    private fun showEditBookmarkDialog(bookmark: Bookmark) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_bookmark, null)
        val titleField = dialogView.findViewById<TextInputEditText>(R.id.bookmark_title_field)
        val urlField = dialogView.findViewById<TextInputEditText>(R.id.bookmark_url_field)
        
        titleField.setText(bookmark.title)
        urlField.setText(bookmark.url)
        
        // 创建对话框
        AlertDialog.Builder(this)
            .setTitle("编辑书签")
            .setView(dialogView)
            .setPositiveButton("保存") { _, _ ->
                val newTitle = titleField.text.toString().trim()
                val newUrl = urlField.text.toString().trim()
                
                if (newTitle.isNotEmpty() && newUrl.isNotEmpty()) {
                    // 更新书签
                    val updatedBookmark = bookmark.copy(
                        title = newTitle,
                        url = newUrl
                    )
                    
                    val bookmarkManager = BookmarkManager.getInstance(this)
                    bookmarkManager.updateBookmark(updatedBookmark)
                    
                    Toast.makeText(this, "书签已更新", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun getDefaultAIEngine(): BaseSearchEngine {
        return AISearchEngine.DEFAULT_AI_ENGINES.firstOrNull() ?: AISearchEngine(
            name = "ChatGPT",
            url = "https://chat.openai.com",
            iconResId = R.drawable.ic_chatgpt,
            description = "ChatGPT AI助手"
        )
    }

    private fun setDefaultEngine(engines: MutableList<BaseSearchEngine>) {
        if (engines.isEmpty()) {
            val defaultEngine = if (isAIMode) {
                AISearchEngine(
                    name = "ChatGPT",
                    url = "https://chat.openai.com",
                    iconResId = R.drawable.ic_chatgpt,
                    description = "默认AI搜索引擎"
                )
            } else {
                SearchEngine(
                    name = "baidu",
                    displayName = "百度",
                    url = "https://www.baidu.com",
                    iconResId = R.drawable.ic_baidu,
                    description = "默认搜索引擎"
                )
            }
            engines.add(defaultEngine)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
} 