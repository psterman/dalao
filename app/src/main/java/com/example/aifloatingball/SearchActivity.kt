package com.example.aifloatingball

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.webkit.SslErrorHandler
import android.net.http.SslError
import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType
import android.view.LayoutInflater
import android.view.Gravity
import android.graphics.Color
import androidx.viewpager2.widget.ViewPager2
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.WindowManager
import android.graphics.PixelFormat
import android.util.DisplayMetrics
import android.view.inputmethod.InputMethodManager
import android.os.Handler
import android.os.Looper
import android.app.Dialog
import android.view.Window
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.graphics.drawable.ColorDrawable
import android.provider.Settings
import android.util.Log

class SearchActivity : AppCompatActivity() {
    private lateinit var searchInput: EditText
    private lateinit var searchButton: ImageButton
    private lateinit var closeButton: ImageButton
    private lateinit var settingsManager: SettingsManager
    private var currentLayoutTheme: String = "fold"
    private lateinit var letterIndexBar: com.example.aifloatingball.view.LetterIndexBar
    private lateinit var previewContainer: LinearLayout
    private lateinit var letterTitle: TextView
    private lateinit var previewEngineList: LinearLayout
    private var currentLetter: Char = 'A'
    private var sortedEngines: List<SearchEngine> = emptyList()
    private lateinit var windowManager: WindowManager
    private var cardViews = mutableListOf<View>()
    private var currentCardIndex = 0
    private var screenWidth = 0
    private var screenHeight = 0
    private lateinit var gestureDetector: GestureDetector

    private val layoutThemeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.aifloatingball.LAYOUT_THEME_CHANGED") {
                currentLayoutTheme = settingsManager.getLayoutTheme()
                setupViews()
            }
        }
    }

    private val focusClearReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.aifloatingball.CLEAR_SEARCH_FOCUS") {
                searchInput.clearFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_search)

            // 初始化窗口管理器
            windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            
            // 获取屏幕尺寸
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels

            settingsManager = SettingsManager.getInstance(this)
            currentLayoutTheme = settingsManager.getLayoutTheme()
            
            // 初始化手势检测器
            gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                    if (e1 == null) return false
                    
                    val diffX = e2.x - e1.x
                    if (Math.abs(diffX) > 100 && Math.abs(velocityX) > 100) {
                        if (diffX > 0) {
                            // 右滑，显示上一个卡片
                            showPreviousCard()
                        } else {
                            // 左滑，显示下一个卡片
                            showNextCard()
                        }
                        return true
                    }
                    return false
                }
            })
            
            // 注册布局主题变更广播接收器
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(
                    layoutThemeReceiver,
                    IntentFilter("com.example.aifloatingball.LAYOUT_THEME_CHANGED"),
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                registerReceiver(
                    layoutThemeReceiver,
                    IntentFilter("com.example.aifloatingball.LAYOUT_THEME_CHANGED")
                )
            }
        
            // 注册广播接收器
            registerReceiver(focusClearReceiver, IntentFilter("com.example.aifloatingball.CLEAR_SEARCH_FOCUS"))
        
            setupViews()
            setupClickListeners()

            // 检查是否有剪贴板文本传入
            val clipboardText = intent.getStringExtra("CLIPBOARD_TEXT")
            clipboardText?.let { text ->
                if (text.isNotBlank()) {
                    searchInput.setText(text)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SearchActivity", "Error in onCreate", e)
            Toast.makeText(this, "启动失败：${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun setupViews() {
        try {
            // 初始化所有视图
            searchInput = findViewById(R.id.search_input) ?: throw IllegalStateException("搜索输入框未找到")
            searchButton = findViewById(R.id.btn_search) ?: throw IllegalStateException("搜索按钮未找到")
            closeButton = findViewById(R.id.btn_close) ?: throw IllegalStateException("关闭按钮未找到")
            letterIndexBar = findViewById(R.id.letter_index_bar) ?: throw IllegalStateException("字母索引栏未找到")
            previewContainer = findViewById(R.id.preview_container) ?: throw IllegalStateException("预览容器未找到")
            letterTitle = findViewById(R.id.letter_title) ?: throw IllegalStateException("字母标题未找到")
            previewEngineList = findViewById(R.id.preview_engine_list) ?: throw IllegalStateException("预览引擎列表未找到")

            // 初始化搜索引擎列表
            sortedEngines = settingsManager.getFilteredEngineOrder().map { aiEngine ->
                SearchEngine(aiEngine.name, aiEngine.url, aiEngine.iconResId)
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

            setupLetterIndexLayout()

        } catch (e: Exception) {
            android.util.Log.e("SearchActivity", "Error setting up views", e)
            Toast.makeText(this, "视图初始化失败：${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupClickListeners() {
        searchButton.setOnClickListener {
            val query = searchInput.text.toString().trim()
            if (query.isNotEmpty()) {
                // 启动悬浮窗服务并传入搜索内容
                val selectedEngine = sortedEngines.firstOrNull()
                selectedEngine?.let { engine ->
                    createFloatingCard(engine, query)
                }
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

    private fun setupLetterIndexLayout() {
        letterIndexBar.onLetterSelectedListener = { _, letter ->
            currentLetter = letter
            showSearchEnginesByLetter(letter)
            // 震动反馈
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(50)
            }
        }
    }

    private fun showSearchEnginesByLetter(letter: Char) {
        // 更新字母标题
        letterTitle.text = letter.toString()

        // 清空搜索引擎列表
        previewEngineList.removeAllViews()

        // 查找所有匹配该字母的搜索引擎
        val matchingEngines = sortedEngines.filter { engine ->
            val firstChar = engine.name.first()
            when {
                firstChar.toString().matches(Regex("[A-Za-z]")) -> 
                    firstChar.toString().uppercase() == letter.toString()
                firstChar.toString().matches(Regex("[\u4e00-\u9fa5]")) -> {
                    try {
                        val format = HanyuPinyinOutputFormat().apply {
                            toneType = HanyuPinyinToneType.WITHOUT_TONE
                            caseType = HanyuPinyinCaseType.UPPERCASE
                            vCharType = HanyuPinyinVCharType.WITH_V
                        }
                        val pinyin = PinyinHelper.toHanyuPinyinStringArray(firstChar, format)
                        pinyin?.firstOrNull()?.firstOrNull()?.toString()?.uppercase() == letter.toString()
                    } catch (e: Exception) {
                        false
                    }
                }
                else -> false
            }
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
                val engineItem = LayoutInflater.from(this).inflate(
                    R.layout.item_search_engine,
                    previewEngineList,
                    false
                )

                engineItem.findViewById<ImageView>(R.id.engine_icon)?.setImageResource(engine.iconResId)
                engineItem.findViewById<TextView>(R.id.engine_name)?.text = engine.name

                // 添加点击事件
                engineItem.setOnClickListener {
                    val query = searchInput.text.toString().trim()
                    createFloatingCard(engine, query)
                }

                previewEngineList.addView(engineItem)

                // 在每个搜索引擎项之间添加分隔线
                if (engine != matchingEngines.last()) {
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
                }
            }
        }
    }

    private fun createFloatingCard(engine: SearchEngine, query: String) {
        try {
            // 检查是否有悬浮窗权限
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
                return
            }

            val dialog = Dialog(this, R.style.FloatingCardDialog).apply {
                requestWindowFeature(Window.FEATURE_NO_TITLE)
                val cardView = createCardView(engine, query)
                setContentView(cardView)
                
                window?.apply {
                    setLayout(MATCH_PARENT, WRAP_CONTENT)
                    
                    // 设置窗口类型
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
                    } else {
                        setType(WindowManager.LayoutParams.TYPE_PHONE)
                    }
                    
                    // 设置窗口属性
                    attributes = attributes.apply {
                        gravity = Gravity.TOP
                        x = 50 + (cardViews.size * 60)
                        y = 100 + (cardViews.size * 60)
                        flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    }
                    
                    // 设置背景透明
                    setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                }

                // 设置对话框不可取消
                setCancelable(false)
                setCanceledOnTouchOutside(false)
            }
            
            // 保存 Dialog 引用以便后续管理
            dialog.window?.decorView?.let { decorView ->
                cardViews.add(decorView)
                currentCardIndex = cardViews.size - 1
            }
            
            // 显示对话框
            dialog.show()

            // 最小化当前Activity
            moveTaskToBack(true)
            
        } catch (e: Exception) {
            Log.e("SearchActivity", "创建浮动卡片失败", e)
            Toast.makeText(this, "创建浮动卡片失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun createCardView(engine: SearchEngine, query: String): View {
        val cardView = LayoutInflater.from(this).inflate(R.layout.card_ai_engine, null)
        
        // 获取标题栏和内容区域
        val titleBar = cardView.findViewById<View>(R.id.title_bar)
        val contentContainer = cardView.findViewById<ViewGroup>(R.id.content_container)
        
        // 设置卡片基本属性
        cardView.findViewById<ImageView>(R.id.engine_icon).setImageResource(engine.iconResId)
        cardView.findViewById<TextView>(R.id.engine_name).text = engine.name
        
        // 设置WebView
        val webView = cardView.findViewById<WebView>(R.id.web_view)
        setupWebView(webView, engine)
        
        // 设置最小化按钮
        cardView.findViewById<ImageButton>(R.id.btn_minimize)?.setOnClickListener {
            try {
                val parent = cardView.parent as? ViewGroup
                parent?.removeView(cardView)
                cardViews.remove(cardView)
                if (cardViews.isEmpty()) {
                    // 如果没有更多卡片，返回到Activity
                    startActivity(Intent(this, SearchActivity::class.java))
                }
            } catch (e: Exception) {
                Log.e("SearchActivity", "最小化卡片失败", e)
            }
        }

        // 初始化拖动相关变量
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        
        // 设置标题栏的触摸事件，处理拖动
        titleBar.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 记录初始位置
                    val params = cardView.layoutParams as WindowManager.LayoutParams
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // 计算移动距离并更新位置
                    val params = cardView.layoutParams as WindowManager.LayoutParams
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        windowManager.updateViewLayout(cardView, params)
                    } catch (e: Exception) {
                        android.util.Log.e("SearchActivity", "更新卡片位置失败", e)
                    }
                    true
                }
                else -> false
            }
        }
        
        // 添加折叠/展开按钮
        val toggleButton = cardView.findViewById<ImageButton>(R.id.btn_toggle)
        toggleButton.setOnClickListener {
            if (contentContainer.visibility == View.VISIBLE) {
                // 折叠
                contentContainer.visibility = View.GONE
                toggleButton.setImageResource(R.drawable.ic_expand_more) // 使用 expand_more 图标
                val params = cardView.layoutParams as WindowManager.LayoutParams
                params.height = WindowManager.LayoutParams.WRAP_CONTENT
                windowManager.updateViewLayout(cardView, params)
            } else {
                // 展开
                contentContainer.visibility = View.VISIBLE
                toggleButton.setImageResource(R.drawable.ic_expand_less) // 使用 expand_less 图标
                val params = cardView.layoutParams as WindowManager.LayoutParams
                params.height = WindowManager.LayoutParams.WRAP_CONTENT
                windowManager.updateViewLayout(cardView, params)
            }
        }

        // 设置 WebView 的焦点属性
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        
        // 添加触摸监听器来处理焦点和输入
        webView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    try {
                        // 获取父视图的 WindowManager.LayoutParams
                        val params = cardView.layoutParams as WindowManager.LayoutParams
                        if (params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0) {
                            // 移除 FLAG_NOT_FOCUSABLE 并添加 FLAG_ALT_FOCUSABLE_IM
                            params.flags = (params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()) or
                                    WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                            windowManager.updateViewLayout(cardView, params)
                            
                            // 请求焦点并显示输入法
                            Handler(Looper.getMainLooper()).postDelayed({
                                webView.requestFocus()
                                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                                imm.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT)
                            }, 100)
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("SearchActivity", "更新窗口焦点状态失败", e)
                    }
                }
            }
            false // 继续传递触摸事件
        }

        // 添加焦点变化监听
        webView.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                try {
                    val params = cardView.layoutParams as WindowManager.LayoutParams
                    if (params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE == 0) {
                        // 恢复 FLAG_NOT_FOCUSABLE 并移除 FLAG_ALT_FOCUSABLE_IM
                        params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        params.flags = params.flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM.inv()
                        windowManager.updateViewLayout(cardView, params)
                        
                        // 隐藏输入法
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.hideSoftInputFromWindow(webView.windowToken, 0)
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SearchActivity", "恢复窗口焦点状态失败", e)
                }
            }
        }

        // 加载URL
        val url = if (query.isNotEmpty()) {
            when (engine.name) {
                "Google" -> "https://www.google.com/search?q=$query"
                "Bing" -> "https://www.bing.com/search?q=$query"
                "百度" -> "https://www.baidu.com/s?wd=$query"
                "ChatGPT" -> "https://chat.openai.com/"
                "Claude" -> "https://claude.ai/"
                "文心一言" -> "https://yiyan.baidu.com/"
                "通义千问" -> "https://qianwen.aliyun.com/"
                "讯飞星火" -> "https://xinghuo.xfyun.cn/"
                "Gemini" -> "https://gemini.google.com/"
                "Copilot" -> "https://copilot.microsoft.com/"
                "豆包" -> "https://www.doubao.com/"
                else -> engine.url
            }
        } else {
            engine.url
        }
        webView.loadUrl(url)

        // 设置卡片点击事件（全屏展开）
        cardView.setOnClickListener {
            expandCard(cardView)
        }

        // 添加触摸事件处理
        cardView.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            true
        }

        // 修改悬浮窗参数
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,  // 添加这个标志
            PixelFormat.TRANSLUCENT
        )

        // 设置初始位置
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 50 + (cardViews.size * 60)  // 错开显示
        params.y = 100 + (cardViews.size * 60)

        // 添加到窗口管理器
        windowManager.addView(cardView, params)
        cardViews.add(cardView)
        currentCardIndex = cardViews.size - 1

        // 更新卡片层级
        updateCardStack()

        return cardView
    }

    private fun setupWebView(webView: WebView, engine: SearchEngine) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                handler?.proceed()
            }
        }
    }

    private fun expandCard(cardView: View) {
        try {
            val params = cardView.layoutParams as WindowManager.LayoutParams
            
            // 展开到全屏并设置正确的标志
            params.width = WindowManager.LayoutParams.MATCH_PARENT
            params.height = WindowManager.LayoutParams.MATCH_PARENT
            params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or  // 允许外部点击
                          WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or  // 监听外部触摸
                          WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM  // 允许输入法
            params.x = 0
            params.y = 0
            
            windowManager.updateViewLayout(cardView, params)
            
            // 获取并设置 WebView 焦点
            cardView.findViewById<WebView>(R.id.web_view)?.apply {
                isFocusable = true
                isFocusableInTouchMode = true
                requestFocus()
            }
            
            // 显示控制栏
            cardView.findViewById<View>(R.id.control_bar).visibility = View.VISIBLE
            
            // 设置最小化按钮点击事件
            cardView.findViewById<ImageButton>(R.id.btn_minimize).setOnClickListener {
                minimizeCard(cardView)
            }
        } catch (e: Exception) {
            android.util.Log.e("SearchActivity", "展开卡片失败", e)
        }
    }

    private fun minimizeCard(cardView: View) {
        val params = cardView.layoutParams as WindowManager.LayoutParams
        
        // 恢复到原始大小
        params.width = WindowManager.LayoutParams.WRAP_CONTENT
        params.height = WindowManager.LayoutParams.WRAP_CONTENT
        
        // 恢复到原始位置
        val index = cardViews.indexOf(cardView)
        params.x = 50 + (index * 60)
        params.y = 100 + (index * 60)
        
        windowManager.updateViewLayout(cardView, params)
        
        // 隐藏控制栏
        cardView.findViewById<View>(R.id.control_bar).visibility = View.GONE
        
        // 更新卡片层级
        updateCardStack()
    }

    private fun showNextCard() {
        if (currentCardIndex < cardViews.size - 1) {
            currentCardIndex++
            updateCardStack()
        }
    }

    private fun showPreviousCard() {
        if (currentCardIndex > 0) {
            currentCardIndex--
            updateCardStack()
        }
    }

    private fun updateCardStack() {
        cardViews.forEachIndexed { index, cardView ->
            val params = cardView.layoutParams as WindowManager.LayoutParams
            
            // 更新Z轴顺序
            params.x = 50 + ((index - currentCardIndex) * 60)
            params.y = 100 + ((index - currentCardIndex) * 60)
            
            // 更新透明度和缩放
            cardView.alpha = if (index == currentCardIndex) 1f else 0.8f
            cardView.scaleX = if (index == currentCardIndex) 1f else 0.9f
            cardView.scaleY = if (index == currentCardIndex) 1f else 0.9f
            
            try {
                windowManager.updateViewLayout(cardView, params)
            } catch (e: Exception) {
                android.util.Log.e("SearchActivity", "更新卡片布局失败", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(layoutThemeReceiver)
        unregisterReceiver(focusClearReceiver)
        
        // 清理所有卡片
        cardViews.forEach { cardView ->
            try {
                (cardView.parent as? ViewGroup)?.removeView(cardView)
            } catch (e: Exception) {
                android.util.Log.e("SearchActivity", "移除卡片失败", e)
            }
        }
        cardViews.clear()
    }

    data class SearchEngine(
        val name: String,
        val url: String,
        val iconResId: Int
    )
} 