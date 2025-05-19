package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.Gravity
import android.graphics.Color
import android.util.Log
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.HapticFeedbackConstants
import kotlin.math.abs
import android.view.VelocityTracker
import android.view.animation.AccelerateInterpolator
import android.os.Handler
import android.os.Looper
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.aifloatingball.views.LetterIndexBar
import com.example.aifloatingball.models.SearchEngine as ModelSearchEngine
import com.example.aifloatingball.service.FloatingWindowService

class SearchWebViewActivity : AppCompatActivity() {
    private lateinit var searchInput: EditText
    private lateinit var searchButton: ImageButton
    private lateinit var closeButton: ImageButton
    private lateinit var webView: WebView
    private lateinit var settingsManager: SettingsManager
    private lateinit var letterIndexBar: LetterIndexBar
    private lateinit var previewContainer: LinearLayout
    private lateinit var letterTitle: TextView
    private lateinit var previewEngineList: LinearLayout
    private var currentLetter: Char = 'A'
    private var sortedEngines: List<SearchEngine> = emptyList()
    private lateinit var engineListPopup: LinearLayout
    private lateinit var flymeEdgeLetterBar: LinearLayout
    private lateinit var flymeEnginePreview: LinearLayout
    private lateinit var previewEngineIcon: ImageView
    private lateinit var previewEngineName: TextView
    
    private var edgePeekView: View? = null
    private var isEdgeBarVisible = false
    private var currentTouchY = 0f
    private var lastSelectedLetter: Char? = null
    private var edgeBarAnimator: ValueAnimator? = null
    private val letterSpacing = 40f // dp

    private var isEdgeDetected = false
    private var isSwiping = false
    private var touchSlop = 0
    private var edgeSlop = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    private var touchStartX = 0f
    private var isEdgeTouch = false
    private val hideLetterBarHandler = Handler(Looper.getMainLooper())

    private lateinit var rootLayout: View

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_search_webview)

            settingsManager = SettingsManager.getInstance(this)
            
            // Initialize rootLayout
            rootLayout = findViewById(android.R.id.content)
            
            setupViews()
            setupWebView()
            setupClickListeners()
            setupFlymeEdgeBar()
            setupEdgePeekView()

            // 检查是否有剪贴板文本传入
            val clipboardText = intent.getStringExtra("CLIPBOARD_TEXT")
            clipboardText?.let { text ->
                if (text.isNotBlank()) {
                    searchInput.setText(text)
                }
            }

            // 初始化触摸阈值
            val configuration = ViewConfiguration.get(this)
            touchSlop = configuration.scaledTouchSlop
            edgeSlop = (configuration.scaledEdgeSlop * 1.5f).toInt() // 稍微增加边缘检测范围

            // 初始化字母索引栏
            letterIndexBar = findViewById(R.id.letter_index_bar)
            initLetterIndexBar()
            
            // 设置边缘滑动检测
            setupEdgeSwipeDetection()
        } catch (e: Exception) {
            android.util.Log.e("SearchWebViewActivity", "Error in onCreate", e)
            Toast.makeText(this, "启动失败：${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupViews() {
        try {
            // Initialize all views
            searchInput = findViewById(R.id.search_input) ?: throw IllegalStateException("搜索输入框未找到")
            searchButton = findViewById(R.id.btn_search) ?: throw IllegalStateException("搜索按钮未找到")
            closeButton = findViewById(R.id.btn_close) ?: throw IllegalStateException("关闭按钮未找到")
            webView = findViewById(R.id.webview) ?: throw IllegalStateException("WebView未找到")
            letterIndexBar = findViewById(R.id.letter_index_bar) ?: throw IllegalStateException("字母索引栏未找到")
            engineListPopup = findViewById(R.id.engine_list_popup) ?: throw IllegalStateException("搜索引擎列表弹窗未找到")
            letterTitle = findViewById(R.id.letter_title) ?: throw IllegalStateException("字母标题未找到")
            previewEngineList = findViewById(R.id.preview_engine_list) ?: throw IllegalStateException("预览引擎列表未找到")

            // Apply theme
            applyTheme()

            // Initialize search engine list
            sortedEngines = settingsManager.getEngineOrder().map { aiEngine ->
                SearchEngine(
                    name = aiEngine.name,
                    displayName = aiEngine.name,
                    searchUrl = aiEngine.searchUrl,
                    iconResId = aiEngine.iconResId,
                    description = ""
                )
            }.sortedWith(compareBy { 
                val firstChar = it.name.first().toString()
                when {
                    firstChar.matches(Regex("[A-Za-z]")) -> firstChar.uppercase()
                    firstChar.matches(Regex("[\u4e00-\u9fa5]")) -> {
                        try {
                            java.text.Collator.getInstance(java.util.Locale.CHINESE)
                                .getCollationKey(firstChar).sourceString
                        } catch (e: Exception) {
                            firstChar
                        }
                    }
                    else -> firstChar
                }
            })

            setupLetterIndexBar()
            
            // 点击空白区域隐藏搜索引擎列表
            findViewById<View>(android.R.id.content)?.setOnClickListener {
                if (engineListPopup.visibility == View.VISIBLE) {
                    engineListPopup.visibility = View.GONE
                }
            }
            
            // 防止点击搜索引擎列表时触发隐藏
            engineListPopup.setOnClickListener { /* 不做任何处理，阻止点击事件传递 */ }

        } catch (e: Exception) {
            Log.e("SearchWebViewActivity", "视图初始化失败", e)
            Toast.makeText(this, "视图初始化失败：${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            
            // 启用自适应屏幕
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
            
            // 允许混合内容
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // 页面加载完成后，自动查找输入框并填充剪贴板内容
                val clipboardText = intent.getStringExtra("CLIPBOARD_TEXT")
                if (!clipboardText.isNullOrBlank()) {
                    val js = """
                        (function() {
                            var inputs = document.querySelectorAll('input[type="text"], input[type="search"], input:not([type])');
                            if (inputs.length > 0) {
                                var searchInput = inputs[0];
                                searchInput.value = '$clipboardText';
                                searchInput.focus();
                                // 触发input事件以通知页面内容已更改
                                var event = new Event('input', { bubbles: true });
                                searchInput.dispatchEvent(event);
                            }
                        })();
                    """.trimIndent()
                    view?.evaluateJavascript(js, null)
                }
            }
        }
    }

    private fun performSearch(engine: SearchEngine, query: String?) {
        val searchUrl = if (query.isNullOrBlank()) {
            // 如果没有查询文本，使用搜索引擎的首页URL（去掉%s部分）
            engine.searchUrl.replace("%s", "").replace("search?q=", "")
                .replace("search?query=", "")
                .replace("search?word=", "")
                .replace("s?wd=", "")
        } else {
            // 有查询文本，使用完整的搜索URL
            engine.searchUrl.replace("%s", query)
        }
        webView.loadUrl(searchUrl)
    }

    private fun setupClickListeners() {
        searchButton.setOnClickListener {
            val query = searchInput.text.toString().trim()
            // 使用当前选中的搜索引擎进行搜索
            val selectedEngine = sortedEngines.firstOrNull()
            selectedEngine?.let { engine ->
                createFloatingCard(engine, query)
            }
        }

        closeButton.setOnClickListener {
            finish()
        }

        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH) {
                searchButton.performClick()
                true
            } else {
                false
            }
        }
    }

    private fun setupLetterIndexBar() {
        // 直接设置OnLetterSelectedListener实例
        letterIndexBar.onLetterSelectedListener = object : com.example.aifloatingball.views.LetterIndexBar.OnLetterSelectedListener {
            override fun onLetterSelected(view: View, letter: Char) {
                updateEngineList(letter)
            }
        }
    }

    private fun updateEngineList(letter: Char) {
        // Update letter title
        letterTitle.text = letter.toString()
        letterTitle.visibility = View.VISIBLE
        
        // Show engines for the selected letter
        showSearchEnginesByLetter(letter)
    }

    private fun showSearchEnginesByLetter(letter: Char) {
        try {
            // Update letter title
            letterTitle.text = letter.toString()

            // Get theme colors
            val isDarkMode = when (settingsManager.getThemeMode()) {
                SettingsManager.THEME_MODE_DARK -> true
                SettingsManager.THEME_MODE_LIGHT -> false
                else -> resources.configuration.uiMode and 
                        android.content.res.Configuration.UI_MODE_NIGHT_MASK == 
                        android.content.res.Configuration.UI_MODE_NIGHT_YES
            }
            
            val layoutTheme = settingsManager.getLayoutTheme()
            val textColor = when (layoutTheme) {
                0 -> if (isDarkMode) R.color.fold_text_dark else R.color.fold_text_light
                1 -> if (isDarkMode) R.color.material_text_dark else R.color.material_text_light
                2 -> if (isDarkMode) R.color.glass_text_dark else R.color.glass_text_light
                else -> if (isDarkMode) R.color.fold_text_dark else R.color.fold_text_light
            }

            // Clear engine list
            previewEngineList.removeAllViews()

            // 查找所有匹配该字母的搜索引擎
            val matchingEngines = try {
                sortedEngines.filter { engine ->
                    try {
                        val firstChar = engine.name.first()
                        when {
                            firstChar.toString().matches(Regex("[A-Za-z]")) -> 
                                firstChar.toString().uppercase() == letter.toString()
                            firstChar.toString().matches(Regex("[\u4e00-\u9fa5]")) -> {
                                try {
                                    val format = net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat().apply {
                                        toneType = net.sourceforge.pinyin4j.format.HanyuPinyinToneType.WITHOUT_TONE
                                        caseType = net.sourceforge.pinyin4j.format.HanyuPinyinCaseType.UPPERCASE
                                        vCharType = net.sourceforge.pinyin4j.format.HanyuPinyinVCharType.WITH_V
                                    }
                                    val pinyin = net.sourceforge.pinyin4j.PinyinHelper.toHanyuPinyinStringArray(firstChar, format)
                                    pinyin?.firstOrNull()?.firstOrNull()?.toString()?.uppercase() == letter.toString()
                                } catch (e: Exception) {
                                    Log.e("SearchWebViewActivity", "拼音转换失败", e)
                                    false
                                }
                            }
                            else -> false
                        }
                    } catch (e: Exception) {
                        Log.e("SearchWebViewActivity", "搜索引擎名称处理失败", e)
                        false
                    }
                }
            } catch (e: Exception) {
                Log.e("SearchWebViewActivity", "搜索引擎过滤失败", e)
                emptyList()
            }

            if (matchingEngines.isEmpty()) {
                // 如果没有匹配的搜索引擎，显示提示信息
                val noEngineText = TextView(this).apply {
                    text = "没有以 $letter 开头的搜索引擎"
                    textSize = 16f
                    setTextColor(Color.GRAY)
                    gravity = Gravity.CENTER
                    setPadding(16, 32, 16, 32)
                }
                previewEngineList.addView(noEngineText)
            } else {
                // 添加匹配的搜索引擎
                matchingEngines.forEach { engine ->
                    try {
                        val engineItem = LayoutInflater.from(this).inflate(
                            R.layout.item_search_engine,
                            previewEngineList,
                            false
                        )

                        engineItem.findViewById<ImageView>(R.id.engine_icon)?.apply {
                            setImageResource(engine.iconResId)
                            setColorFilter(ContextCompat.getColor(context, textColor))
                        }
                        
                        engineItem.findViewById<TextView>(R.id.engine_name)?.apply {
                            text = engine.name
                            setTextColor(ContextCompat.getColor(context, textColor))
                        }

                        // 添加点击事件
                        engineItem.setOnClickListener {
                            try {
                                val query = searchInput.text.toString().trim()
                                createFloatingCard(engine, query)
                                // 隐藏搜索引擎列表
                                engineListPopup.visibility = View.GONE
                            } catch (e: Exception) {
                                Log.e("SearchWebViewActivity", "搜索引擎点击处理失败", e)
                                Toast.makeText(this, "处理点击事件失败：${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }

                        previewEngineList.addView(engineItem)

                        // 在每个搜索引擎项之间添加分隔线
                        if (engine != matchingEngines.last()) {
                            try {
                                val divider = View(this).apply {
                                    setBackgroundColor(ContextCompat.getColor(context, R.color.divider))
                                    layoutParams = LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT,
                                        1
                                    ).apply {
                                        setMargins(16, 0, 16, 0)
                                    }
                                }
                                previewEngineList.addView(divider)
                            } catch (e: Exception) {
                                Log.e("SearchWebViewActivity", "添加分隔线失败", e)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("SearchWebViewActivity", "添加搜索引擎项失败", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SearchWebViewActivity", "显示搜索引擎列表失败", e)
            Toast.makeText(this, "显示搜索引擎列表失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createFloatingCard(engine: SearchEngine, query: String) {
        try {
            // 启动悬浮窗服务并传递搜索引擎信息
            val intent = Intent(this, FloatingWindowService::class.java).apply {
                putExtra("ENGINE_NAME", engine.name)
                putExtra("ENGINE_URL", engine.searchUrl)
                putExtra("ENGINE_ICON", engine.iconResId)
                putExtra("SEARCH_QUERY", query)
                putExtra("SHOULD_OPEN_URL", true)
            }
            startService(intent)
        } catch (e: Exception) {
            Log.e("SearchWebViewActivity", "创建悬浮卡片失败", e)
            Toast.makeText(this, "创建悬浮卡片失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun applyTheme() {
        val isDarkMode = when (settingsManager.getThemeMode()) {
            SettingsManager.THEME_MODE_DARK -> true
            SettingsManager.THEME_MODE_LIGHT -> false
            else -> resources.configuration.uiMode and 
                    android.content.res.Configuration.UI_MODE_NIGHT_MASK == 
                    android.content.res.Configuration.UI_MODE_NIGHT_YES
        }

        val layoutTheme = settingsManager.getLayoutTheme()
        
        // Apply theme colors based on layout theme
        val backgroundColor = when (layoutTheme) {
            0 -> if (isDarkMode) R.color.fold_background_dark else R.color.fold_background_light
            1 -> if (isDarkMode) R.color.material_background_dark else R.color.material_background_light
            2 -> if (isDarkMode) R.color.glass_background_dark else R.color.glass_background_light
            else -> if (isDarkMode) R.color.fold_background_dark else R.color.fold_background_light
        }

        val textColor = when (layoutTheme) {
            0 -> if (isDarkMode) R.color.fold_text_dark else R.color.fold_text_light
            1 -> if (isDarkMode) R.color.material_text_dark else R.color.material_text_light
            2 -> if (isDarkMode) R.color.glass_text_dark else R.color.glass_text_light
            else -> if (isDarkMode) R.color.fold_text_dark else R.color.fold_text_light
        }

        // Apply colors to views
        engineListPopup.setBackgroundColor(ContextCompat.getColor(this, backgroundColor))
        letterTitle.setTextColor(ContextCompat.getColor(this, textColor))
        letterIndexBar.setBackgroundColor(ContextCompat.getColor(this, backgroundColor))
        previewEngineList.setBackgroundColor(ContextCompat.getColor(this, backgroundColor))

        // Update letter index bar theme
        letterIndexBar.setDarkMode(isDarkMode)
        letterIndexBar.setThemeColors(
            ContextCompat.getColor(this, textColor),
            ContextCompat.getColor(this, backgroundColor)
        )
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
        } catch (e: android.content.res.Resources.NotFoundException) {
            // 如果颜色资源不存在，使用默认颜色
            Log.e("SearchWebViewActivity", "Error applying theme: ${e.message}")
            window.statusBarColor = Color.parseColor("#1976D2") // Default blue
            rootLayout?.setBackgroundColor(Color.WHITE)
        }
    }

    override fun onResume() {
        super.onResume()
        // Update theme when activity resumes
        updateTheme()
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    private fun setupFlymeEdgeBar() {
        flymeEdgeLetterBar = findViewById(R.id.flyme_edge_letter_bar)
        flymeEnginePreview = findViewById(R.id.flyme_engine_preview)
        previewEngineIcon = findViewById(R.id.preview_engine_icon)
        previewEngineName = findViewById(R.id.preview_engine_name)

        // 获取所有唯一的首字母
        val letters = sortedEngines.mapNotNull { engine ->
            getFirstLetter(engine.name)
        }.distinct().sorted()

        // 创建字母视图
        letters.forEach { letter ->
            val letterView = TextView(this).apply {
                text = letter.toString()
                textSize = resources.getDimension(R.dimen.flyme_letter_size)
                gravity = Gravity.CENTER
                setPadding(
                    resources.getDimensionPixelSize(R.dimen.flyme_letter_padding),
                    resources.getDimensionPixelSize(R.dimen.flyme_letter_padding),
                    resources.getDimensionPixelSize(R.dimen.flyme_letter_padding),
                    resources.getDimensionPixelSize(R.dimen.flyme_letter_padding)
                )
            }
            flymeEdgeLetterBar.addView(letterView)
        }

        // 设置触摸监听
        setupEdgeGesture()
    }

    private fun setupEdgePeekView() {
        edgePeekView = View(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.flyme_edge_peek_width),
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#20000000"))
            visibility = View.VISIBLE
        }
        (findViewById<View>(android.R.id.content) as ViewGroup).addView(edgePeekView)
        
        // 将peek view放置在屏幕右边缘
        edgePeekView?.post {
            edgePeekView?.x = resources.displayMetrics.widthPixels - 
                    resources.getDimensionPixelSize(R.dimen.flyme_edge_peek_width).toFloat()
        }
    }

    private fun setupEdgeGesture() {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels

        findViewById<View>(android.R.id.content).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.x
                    initialTouchY = event.y
                    lastTouchX = event.x
                    lastTouchY = event.y
                    
                    // 检查是否在右边缘区域
                    isEdgeDetected = initialTouchX >= screenWidth - edgeSlop
                    isSwiping = false
                    
                    if (isEdgeDetected) {
                        true
                    } else {
                        if (isEdgeBarVisible) {
                            hideEdgeBar()
                        }
                        false
                    }
                }
                
                MotionEvent.ACTION_MOVE -> {
                    if (!isEdgeDetected && !isSwiping) {
                        return@setOnTouchListener false
                    }

                    val deltaX = event.x - lastTouchX
                    val deltaY = event.y - lastTouchY
                    val totalDeltaX = event.x - initialTouchX
                    val totalDeltaY = event.y - initialTouchY

                    // 如果还没开始滑动，检查是否应该开始滑动
                    if (!isSwiping) {
                        // 确保是横向滑动手势
                        if (abs(totalDeltaX) > touchSlop && abs(totalDeltaX) > abs(totalDeltaY)) {
                            isSwiping = true
                            // 显示边缘栏
                            if (totalDeltaX < 0) { // 向左滑动
                                showEdgeBar()
                            }
                        }
                    } else {
                        // 已经在滑动状态
                        if (isEdgeBarVisible) {
                            // 更新字母选择
                            updateLetterSelection(event.y)
                        }
                    }

                    lastTouchX = event.x
                    lastTouchY = event.y
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isSwiping) {
                        val velocityTracker = VelocityTracker.obtain()
                        velocityTracker.addMovement(event)
                        velocityTracker.computeCurrentVelocity(1000)
                        val xVelocity = velocityTracker.xVelocity
                        velocityTracker.recycle()

                        // 如果有选中的字母且速度不太快，启动搜索引擎
                        if (lastSelectedLetter != null && abs(xVelocity) < 1000) {
                            launchSelectedEngine()
                        }
                        
                        // 根据速度决定是否隐藏边缘栏
                        if (xVelocity > 1000 || !isEdgeBarVisible) {
                            hideEdgeBar()
                        }
                    }
                    
                    // 重置状态
                    isEdgeDetected = false
                    isSwiping = false
                    true
                }
                
                else -> false
            }
        }
    }

    private fun showEdgeBar() {
        if (!isEdgeBarVisible) {
            isEdgeBarVisible = true
            edgeBarAnimator?.cancel()
            
            flymeEdgeLetterBar.visibility = View.VISIBLE
            flymeEdgeLetterBar.translationX = flymeEdgeLetterBar.width.toFloat()
            
            // 添加过渡动画
            edgeBarAnimator = ValueAnimator.ofFloat(flymeEdgeLetterBar.width.toFloat(), 0f).apply {
                duration = 250
                interpolator = OvershootInterpolator(0.8f)
                addUpdateListener { animator ->
                    flymeEdgeLetterBar.translationX = animator.animatedValue as Float
                }
                start()
            }

            // 添加触觉反馈
            provideHapticFeedback()
        }
    }

    private fun hideEdgeBar() {
        if (isEdgeBarVisible) {
            isEdgeBarVisible = false
            edgeBarAnimator?.cancel()
            
            edgeBarAnimator = ValueAnimator.ofFloat(0f, flymeEdgeLetterBar.width.toFloat()).apply {
                duration = 200
                interpolator = DecelerateInterpolator()
                addUpdateListener { animator ->
                    flymeEdgeLetterBar.translationX = animator.animatedValue as Float
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        flymeEdgeLetterBar.visibility = View.GONE
                        flymeEnginePreview.visibility = View.GONE
                        lastSelectedLetter = null
                    }
                })
                start()
            }
        }
    }

    private fun updateLetterSelection(y: Float) {
        // 添加边界检查
        if (!isEdgeBarVisible || flymeEdgeLetterBar.childCount == 0) return

        // 计算相对于字母栏的Y坐标
        val letterBarY = y - flymeEdgeLetterBar.y
        
        // 边界检查
        if (letterBarY < 0 || letterBarY > flymeEdgeLetterBar.height) return

        // 计算当前触摸位置对应的字母索引
        val letterHeight = flymeEdgeLetterBar.height.toFloat() / flymeEdgeLetterBar.childCount
        val letterIndex = (letterBarY / letterHeight).toInt()
            .coerceIn(0, flymeEdgeLetterBar.childCount - 1)
        
        val letterView = flymeEdgeLetterBar.getChildAt(letterIndex) as? TextView
        val letter = letterView?.text?.firstOrNull()
        
        if (letter != lastSelectedLetter) {
            lastSelectedLetter = letter
            letter?.let { 
                updateEnginePreview(it)
                // 添加触觉反馈
                provideHapticFeedback()
            }
        }
    }

    private fun updateEnginePreview(letter: Char) {
        // 查找对应字母的第一个搜索引擎
        val engine = sortedEngines.firstOrNull { getFirstLetter(it.name) == letter }
        
        if (engine != null) {
            previewEngineIcon.setImageResource(engine.iconResId)
            previewEngineName.text = engine.name
            
            if (flymeEnginePreview.visibility != View.VISIBLE) {
                flymeEnginePreview.alpha = 0f
                flymeEnginePreview.visibility = View.VISIBLE
                flymeEnginePreview.animate()
                    .alpha(1f)
                    .setDuration(200)
                    .setInterpolator(DecelerateInterpolator())
                    .start()
            }
        } else {
            flymeEnginePreview.visibility = View.GONE
        }
    }

    private fun launchSelectedEngine() {
        lastSelectedLetter?.let { letter ->
            val engine = sortedEngines.firstOrNull { getFirstLetter(it.name) == letter }
            engine?.let {
                val query = searchInput.text.toString().trim()
                createFloatingCard(it, query)
            }
        }
    }

    private fun getFirstLetter(text: String): Char? {
        return try {
            val firstChar = text.first()
            when {
                firstChar.toString().matches(Regex("[A-Za-z]")) -> 
                    firstChar.uppercaseChar()
                firstChar.toString().matches(Regex("[\u4e00-\u9fa5]")) -> {
                    val format = net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat().apply {
                        toneType = net.sourceforge.pinyin4j.format.HanyuPinyinToneType.WITHOUT_TONE
                        caseType = net.sourceforge.pinyin4j.format.HanyuPinyinCaseType.UPPERCASE
                        vCharType = net.sourceforge.pinyin4j.format.HanyuPinyinVCharType.WITH_V
                    }
                    val pinyin = net.sourceforge.pinyin4j.PinyinHelper
                        .toHanyuPinyinStringArray(firstChar, format)
                    pinyin?.firstOrNull()?.firstOrNull()?.uppercaseChar()
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e("SearchWebViewActivity", "获取首字母失败", e)
            null
        }
    }

    private fun provideHapticFeedback() {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(20, VibrationEffect.EFFECT_TICK))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(20)
        }
    }

    private fun initLetterIndexBar() {
        // 获取搜索引擎列表
        val engines = getSearchEngineList()
        
        // 设置搜索引擎到字母索引栏
        letterIndexBar.setEngines(engines)
        
        // 设置字母选择监听器
        letterIndexBar.setOnLetterSelectedListener { engine ->
            // 切换到所选搜索引擎
            switchToSearchEngine(engine)
            
            // 显示切换确认消息
            Toast.makeText(this, "已切换到 ${engine.name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun getSearchEngineList(): List<ModelSearchEngine> {
        // 示例数据
        return listOf(
            ModelSearchEngine("google", "Google", "https://www.google.com"),
            ModelSearchEngine("baidu", "百度", "https://www.baidu.com"),
            ModelSearchEngine("bing", "Bing", "https://www.bing.com"),
            ModelSearchEngine("yandex", "Yandex", "https://yandex.com"),
            ModelSearchEngine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com"),
            ModelSearchEngine("yahoo", "Yahoo", "https://search.yahoo.com"),
            ModelSearchEngine("sogou", "搜狗", "https://www.sogou.com")
        )
    }

    private fun switchToSearchEngine(engine: ModelSearchEngine) {
        // 查找匹配的本地SearchEngine对象
        val localEngine = sortedEngines.firstOrNull { it.name == engine.name }
        
        if (localEngine != null) {
            // 使用本地SearchEngine进行搜索
            val query = searchInput.text.toString().trim()
            createFloatingCard(localEngine, query)
        } else {
            Toast.makeText(this, "无法找到搜索引擎: ${engine.name}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupEdgeSwipeDetection() {
        val screenWidth = resources.displayMetrics.widthPixels
        val edgeThreshold = screenWidth / 10 // 屏幕宽度的10%作为边缘区域
        
        webView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 检测是否在屏幕边缘触摸
                    touchStartX = event.x
                    isEdgeTouch = touchStartX < edgeThreshold
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isEdgeTouch && event.x > touchStartX + 50) { // 向右滑动超过50像素
                        showLetterIndexBar()
                        return@setOnTouchListener true
                    }
                    false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isEdgeTouch = false
                    false
                }
                else -> false
            }
        }
    }

    private fun showLetterIndexBar() {
        // 取消之前的自动隐藏
        hideLetterBarHandler.removeCallbacksAndMessages(null)
        
        // 显示字母索引栏
        letterIndexBar.show()
        
        // 设置自动隐藏定时器
        hideLetterBarHandler.postDelayed({
            letterIndexBar.hide()
        }, 5000) // 5秒后自动隐藏
    }

    data class SearchEngine(
        val name: String,
        val displayName: String,
        val searchUrl: String,
        val iconResId: Int,
        val description: String
    )
} 