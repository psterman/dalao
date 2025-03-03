package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GestureDetectorCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.content.ClipboardManager
import android.content.Context
import android.webkit.URLUtil
import android.app.AlertDialog
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import kotlin.math.abs
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.view.inputmethod.EditorInfo
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.appbar.AppBarLayout
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.switchmaterial.SwitchMaterial
import androidx.appcompat.app.AppCompatDelegate
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import java.io.ByteArrayInputStream
import android.webkit.WebStorage
import android.widget.ImageView
import android.widget.LinearLayout

class HomeActivity : AppCompatActivity(), GestureDetector.OnGestureListener {
    private lateinit var searchInput: EditText
    private lateinit var voiceSearchButton: ImageButton
    private lateinit var shortcutsGrid: RecyclerView
    private lateinit var gestureDetectorCompat: GestureDetectorCompat
    private lateinit var webView: WebView
    private lateinit var webViewContainer: FrameLayout
    private lateinit var appBarLayout: AppBarLayout
    private lateinit var homeContent: View
    private lateinit var menuDialog: BottomSheetDialog
    
    // 浏览器设置状态
    private var isNoImageMode = false
    private var isDesktopMode = false
    private var isAdBlockEnabled = true
    private var isIncognitoMode = false
    private var isNightMode = false

    private val SWIPE_THRESHOLD = 100
    private val SWIPE_VELOCITY_THRESHOLD = 100
    private var lastTouchPointerCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        // 初始化视图
        searchInput = findViewById(R.id.search_input)
        voiceSearchButton = findViewById(R.id.voice_search)
        shortcutsGrid = findViewById(R.id.shortcuts_grid)
        webViewContainer = findViewById(R.id.webview_container)
        appBarLayout = findViewById(R.id.appbar)
        homeContent = findViewById(R.id.home_content)

        // 初始化 WebView
        setupWebView()

        // 设置手势检测
        gestureDetectorCompat = GestureDetectorCompat(this, this)

        // 设置搜索功能
        setupSearch()

        // 设置快捷方式
        setupShortcuts()

        // 设置底部按钮
        setupBottomBar()

        // 在Activity完全初始化后检查剪贴板
        window.decorView.post {
            checkClipboard()
        }
    }

    private fun setupWebView() {
        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY)
                settings.setEnableSmoothTransition(true)
                
                // 移除无图模式设置，改为始终加载图片
                loadsImagesAutomatically = true
            }

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    visibility = View.VISIBLE
                    
                    // 根据无图模式状态应用滤镜
                    if (isNoImageMode) {
                        applyImageFilter()
                    } else {
                        removeImageFilter()
                    }
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    // 实现广告过滤
                    if (isAdBlockEnabled && request?.url?.toString()?.containsAdUrl() == true) {
                        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream("".toByteArray()))
                    }
                    return super.shouldInterceptRequest(view, request)
                }
            }

            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        
        val nestedScrollView = androidx.core.widget.NestedScrollView(this).apply {
            layoutParams = CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                behavior = AppBarLayout.ScrollingViewBehavior()
            }
            addView(webView)
        }
        
        webViewContainer.addView(nestedScrollView)
        webView.visibility = View.GONE
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
        webView.visibility = View.VISIBLE
        homeContent.visibility = View.GONE
        webView.loadUrl(url)
    }
    
    private fun searchContent(query: String) {
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        val searchUrl = "https://www.baidu.com/s?wd=$encodedQuery"
        webView.visibility = View.VISIBLE
        homeContent.visibility = View.GONE
        webView.loadUrl(searchUrl)
    }

    private fun setupSearch() {
        searchInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)) {
                performSearch(searchInput.text.toString())
                true
            } else {
                false
            }
        }

        voiceSearchButton.setOnClickListener {
            performSearch(searchInput.text.toString())
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
            menuDialog.setContentView(view)

            // 设置开关状态和监听器
            setupSwitches(view)
            setupButtons(view)
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

    private fun performSearch(query: String) {
        if (query.isNotEmpty()) {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://www.baidu.com/s?wd=$encodedQuery"
            webView.visibility = View.VISIBLE
            homeContent.visibility = View.GONE
            webView.loadUrl(searchUrl)
        }
    }

    override fun onBackPressed() {
        when {
            webView.visibility == View.VISIBLE && webView.canGoBack() -> webView.goBack()
            webView.visibility == View.VISIBLE -> {
                webView.visibility = View.GONE
                homeContent.visibility = View.VISIBLE
                webView.loadUrl("about:blank")
            }
            else -> super.onBackPressed()
        }
    }

    // 手势检测实现
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
    }

    private fun onSwipeLeft() {
        // TODO: 左滑操作，例如：前进
    }

    private fun onSwipeUp() {
        // TODO: 上滑操作，例如：刷新
    }

    private fun onSwipeDown() {
        // TODO: 下滑操作，例如：显示通知栏
    }

    private fun String.containsAdUrl(): Boolean {
        val adPatterns = listOf(
            "ads", "analytics", "tracker", "doubleclick",
            "pagead", "banner", "popup", "stats"
        )
        return adPatterns.any { this.contains(it, ignoreCase = true) }
    }

    private fun applyImageFilter() {
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

    private fun removeImageFilter() {
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