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
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

// AndroidX imports
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.cardview.widget.CardView
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.view.GestureDetectorCompat
import androidx.core.view.GravityCompat
import androidx.core.widget.NestedScrollView
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import androidx.recyclerview.widget.LinearLayoutManager

// Google Material imports
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.switchmaterial.SwitchMaterial

// Third-party library imports
import net.sourceforge.pinyin4j.PinyinHelper

// Project imports
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.view.LetterIndexBar
import com.example.aifloatingball.DualFloatingWebViewService
import java.io.ByteArrayInputStream
import kotlin.math.abs
import com.example.aifloatingball.tab.TabManager

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
    }

    private fun setupDrawer() {
        // 禁用抽屉的自动关闭
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        
        // 根据设置更新抽屉位置
        updateDrawerGravity()
        
        // 应用主题颜色
        applyDrawerTheme()
        
        // 设置抽屉监听器
        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                // 更新透明度和动画
                drawerView.alpha = 0.3f + (0.7f * slideOffset)
            }
            
            override fun onDrawerOpened(drawerView: View) {
                isDrawerEnabled = true
                drawerView.alpha = 1.0f
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
        webView.apply {
            settings.apply {
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

            // 设置WebViewClient
            webViewClient = object : WebViewClient() {
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
                    // 页面加载完成后的处理
                    webView.visibility = View.VISIBLE
                    homeContent.visibility = View.GONE
                }
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
            // 检查是否是URL
            val isUrl = URLUtil.isValidUrl(query) || 
                       (query.contains(".") && !query.contains(" ")) ||
                       query.startsWith("http://") || 
                       query.startsWith("https://")

            val url = if (isUrl) {
                // 如果是URL，确保有http/https前缀
                if (query.startsWith("http://") || query.startsWith("https://")) {
                    query
                } else {
                    "https://$query"
                }
            } else {
                // 如果不是URL，使用搜索引擎搜索
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
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
            // TODO: 显示书签
            Toast.makeText(this, "书签功能即将推出", Toast.LENGTH_SHORT).show()
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
                // TODO: 实现书签功能
                Toast.makeText(this@HomeActivity, "书签功能即将推出", Toast.LENGTH_SHORT).show()
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
                    // 使用默认主题
                    window.statusBarColor = getColor(R.color.colorPrimaryDark)
                    window.navigationBarColor = getColor(R.color.colorPrimaryDark)
                    rootLayout?.setBackgroundColor(getColor(R.color.colorBackground))
                }
                SettingsManager.THEME_MODE_LIGHT -> {
                    // 使用浅色主题
                    window.statusBarColor = getColor(R.color.colorLightPrimaryDark)
                    window.navigationBarColor = getColor(R.color.colorLightPrimaryDark)
                    rootLayout?.setBackgroundColor(getColor(R.color.colorLightBackground))
                }
                SettingsManager.THEME_MODE_DARK -> {
                    // 使用深色主题
                    window.statusBarColor = getColor(R.color.colorDarkPrimaryDark)
                    window.navigationBarColor = getColor(R.color.colorDarkPrimaryDark)
                    rootLayout?.setBackgroundColor(getColor(R.color.colorDarkBackground))
                }
            }
        } catch (e: Resources.NotFoundException) {
            // 如果颜色资源不存在，使用默认颜色
            Log.e("HomeActivity", "Error applying theme: ${e.message}")
            window.statusBarColor = Color.parseColor("#1976D2") // Default blue
            rootLayout?.setBackgroundColor(Color.WHITE)
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
        // 直接使用普通搜索引擎列表，移除AI模式切换
        letterIndexBar.engines = SearchEngine.getNormalSearchEngines()

        letterIndexBar.onLetterSelectedListener = object : LetterIndexBar.OnLetterSelectedListener {
            override fun onLetterSelected(view: View, letter: Char) {
                updateEngineList(letter)
            }
        }

        // 设置搜索引擎点击监听器
        previewEngineList.setOnClickListener(null)
    }

    private fun updateEngineList(letter: Char) {
        // 更新字母标题
        letterTitle.text = letter.toString()
        letterTitle.visibility = View.VISIBLE
        
        // 显示对应字母的搜索引擎
        showSearchEnginesByLetter(letter)
    }

    private fun showSearchEnginesByLetter(letter: Char) {
        // 清空现有列表
        previewEngineList.removeAllViews()

        // 获取当前主题模式
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

        // 直接使用普通搜索引擎列表
        val engines = SearchEngine.getNormalSearchEngines()

        // 过滤匹配的搜索引擎
        val matchingEngines = engines.filter { engine ->
            val firstChar = engine.name.first()
            when {
                firstChar.toString().matches(Regex("[A-Za-z]")) -> 
                    firstChar.uppercaseChar() == letter.uppercaseChar()
                firstChar.toString().matches(Regex("[\u4e00-\u9fa5]")) -> {
                    val pinyinArray = PinyinHelper.toHanyuPinyinStringArray(firstChar)
                    pinyinArray?.firstOrNull()?.firstOrNull()?.uppercaseChar() == letter.uppercaseChar()
                }
                else -> false
            }
        }

        if (matchingEngines.isEmpty()) {
            // 如果没有匹配的搜索引擎，显示提示信息
            val noEngineText = TextView(this).apply {
                text = "没有以 $letter 开头的搜索引擎"
                textSize = 16f
                setTextColor(ContextCompat.getColor(context, if (isDarkMode) R.color.engine_name_text_dark else R.color.engine_name_text_light))
                gravity = android.view.Gravity.CENTER
                setPadding(16, 32, 16, 32)
            }
            previewEngineList.addView(noEngineText)
        } else {
            // 添加匹配的搜索引擎
            matchingEngines.forEach { engine ->
                val engineItem = LayoutInflater.from(this).inflate(
                    R.layout.item_search_engine,
                    previewEngineList,
                    false
                )

                // 设置引擎图标
                engineItem.findViewById<ImageView>(R.id.engine_icon).apply {
                    setImageResource(engine.iconResId)
                    setColorFilter(ContextCompat.getColor(context, if (isDarkMode) R.color.engine_icon_dark else R.color.engine_icon_light))
                }

                // 设置引擎名称
                engineItem.findViewById<TextView>(R.id.engine_name).apply {
                    text = engine.name
                    setTextColor(ContextCompat.getColor(context, if (isDarkMode) R.color.engine_name_text_dark else R.color.engine_name_text_light))
                }

                // 设置点击事件
                engineItem.setOnClickListener {
                    val query = searchInput.text.toString().trim()
                    openSearchEngine(engine, query)
                    drawerLayout.closeDrawer(if (settingsManager.getBoolean("right_handed_mode", true)) GravityCompat.START else GravityCompat.END)
                }

                previewEngineList.addView(engineItem)

                // 添加分隔线（除了最后一项）
                if (engine != matchingEngines.last()) {
                    View(this).apply {
                        setBackgroundColor(ContextCompat.getColor(context, if (isDarkMode) R.color.divider_dark else R.color.divider_light))
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            1
                        ).apply {
                            setMargins(16, 0, 16, 0)
                        }
                    }.also { previewEngineList.addView(it) }
                }
            }
        }
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