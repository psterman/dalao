package com.example.aifloatingball

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.view.LetterIndexBar
import net.sourceforge.pinyin4j.PinyinHelper
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.webkit.URLUtil
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import com.google.android.material.appbar.AppBarLayout

class SearchActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var webView: WebView
    private lateinit var letterIndexBar: LetterIndexBar
    private lateinit var letterTitle: TextView
    private lateinit var previewEngineList: LinearLayout
    private lateinit var closeButton: ImageButton
    private lateinit var menuButton: ImageButton
    private lateinit var settingsManager: SettingsManager
    private lateinit var engineAdapter: EngineAdapter
    private lateinit var modeSwitch: Switch
    private lateinit var webViewContainer: ViewGroup
    private lateinit var appBarLayout: AppBarLayout
    private lateinit var engineList: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var loadingView: View

    private var isAIMode: Boolean = true
    
    // Add flags to track receiver registration
    private var isSettingsReceiverRegistered = false
    private var isLayoutThemeReceiverRegistered = false
    
    companion object {
        val NORMAL_SEARCH_ENGINES = listOf(
            SearchEngine(
                name = "功能主页",
                url = "home",  // 特殊标记，表示这是主页选项
                iconResId = R.drawable.ic_home,  // 请确保有这个图标资源
                description = "打开功能主页"
            ),
            SearchEngine(
                name = "小红书",
                url = "https://www.xiaohongshu.com/explore?keyword={query}",
                iconResId = R.drawable.ic_search,
                description = "小红书搜索"
            ),
            SearchEngine(
                name = "什么值得买",
                url = "https://search.smzdm.com/?s={query}",
                iconResId = R.drawable.ic_search,
                description = "什么值得买搜索"
            ),
            SearchEngine(
                name = "知乎",
                url = "https://www.zhihu.com/search?type=content&q={query}",
                iconResId = R.drawable.ic_search,
                description = "知乎搜索"
            ),
            SearchEngine(
                name = "GitHub",
                url = "https://github.com/search?q={query}",
                iconResId = R.drawable.ic_search,
                description = "GitHub搜索"
            ),
            SearchEngine(
                name = "CSDN",
                url = "https://so.csdn.net/so/search?q={query}",
                iconResId = R.drawable.ic_search,
                description = "CSDN搜索"
            ),
            SearchEngine(
                name = "百度",
                url = "https://www.baidu.com/s?wd={query}",
                iconResId = R.drawable.ic_search,
                description = "百度搜索"
            ),
            SearchEngine(
                name = "谷歌",
                url = "https://www.google.com/search?q={query}",
                iconResId = R.drawable.ic_search,
                description = "Google搜索"
            ),
            SearchEngine(
                name = "搜狗",
                url = "https://www.sogou.com/web?query={query}",
                iconResId = R.drawable.ic_search,
                description = "搜狗搜索"
            ),
            SearchEngine(
                name = "V2EX",
                url = "https://www.v2ex.com/search?q={query}",
                iconResId = R.drawable.ic_search,
                description = "V2EX搜索"
            ),
            SearchEngine(
                name = "今日头条",
                url = "https://so.toutiao.com/search?keyword={query}",
                iconResId = R.drawable.ic_search,
                description = "今日头条搜索"
            ),
            SearchEngine(
                name = "YouTube",
                url = "https://www.youtube.com/results?search_query={query}",
                iconResId = R.drawable.ic_search,
                description = "YouTube搜索"
            ),
            SearchEngine(
                name = "哔哩哔哩",
                url = "https://search.bilibili.com/all?keyword={query}",
                iconResId = R.drawable.ic_search,
                description = "哔哩哔哩搜索"
            ),
            SearchEngine(
                name = "X",
                url = "https://twitter.com/search?q={query}",
                iconResId = R.drawable.ic_search,
                description = "X搜索"
            )
        )
    }
    
    private val settingsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == SettingsActivity.ACTION_SETTINGS_CHANGED &&
                intent.getBooleanExtra(SettingsActivity.EXTRA_LEFT_HANDED_MODE_CHANGED, false)) {
                updateLayoutForHandedness()
            }
        }
    }
    
    private val layoutThemeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.aifloatingball.LAYOUT_THEME_CHANGED") {
                // 更新 WebView 的主题
                updateWebViewTheme()
                // 更新字母索引栏和搜索引擎面板的主题
                updateLetterIndexBarTheme()
                updateEngineListTheme()
                // 重新加载当前页面以应用新主题
                webView.reload()
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDarkMode = currentNightMode == Configuration.UI_MODE_NIGHT_YES
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        
        settingsManager = SettingsManager.getInstance(this)
        
        try {
            // Initialize views
            initViews()
            
            // Register receivers
            registerReceivers()
            
            // Setup UI components
            setupWebView()
            setupLetterIndexBar()
            setupDrawer()
            updateLayoutForHandedness()

            // Load default search engine if opened from floating ball
            if (intent.getBooleanExtra("from_floating_ball", false)) {
                loadDefaultSearchEngine()
            }

            // Apply initial themes
            updateLetterIndexBarTheme()
            updateEngineListTheme()

            // Check clipboard after initialization
            checkClipboard()
        } catch (e: Exception) {
            Log.e("SearchActivity", "Error initializing views", e)
            Toast.makeText(this, "初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        webView = findViewById(R.id.web_view)
        letterIndexBar = findViewById(R.id.letter_index_bar)
        letterTitle = findViewById(R.id.letter_title)
        closeButton = findViewById(R.id.btn_close)
        menuButton = findViewById(R.id.btn_menu)
        webViewContainer = findViewById(R.id.webview_container)
        appBarLayout = findViewById(R.id.appbar)
        engineList = findViewById(R.id.engine_list)
        previewEngineList = findViewById(R.id.preview_engine_list)
        previewEngineList.orientation = LinearLayout.VERTICAL
        modeSwitch = findViewById(R.id.mode_switch)
        progressBar = findViewById(R.id.progress_bar)
        loadingView = findViewById(R.id.loading_view)

        // 初始化时隐藏进度条和加载视图
        progressBar.visibility = View.GONE
        loadingView.visibility = View.GONE

        // 设置基本点击事件
        setupBasicClickListeners()

        // 设置模式切换开关
        modeSwitch.apply {
            isChecked = settingsManager.getSearchMode()
            setOnCheckedChangeListener { _, isChecked ->
                isAIMode = isChecked
                updateEngineList()
                settingsManager.setSearchMode(isAIMode)
            }
        }

        setupEngineList()
        updateEngineList()
        
        // Initialize letter index bar with engines
        letterIndexBar.engines = if (isAIMode) {
            AISearchEngine.DEFAULT_AI_ENGINES
        } else {
            NORMAL_SEARCH_ENGINES
        }
    }

    private fun setupBasicClickListeners() {
        // 设置菜单按钮点击事件
        menuButton.setOnClickListener {
            val isLeftHanded = settingsManager.isLeftHandedMode
            if (isLeftHanded) {
                if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                    drawerLayout.closeDrawer(GravityCompat.END)
                } else {
                    drawerLayout.openDrawer(GravityCompat.END)
                }
            } else {
                if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    drawerLayout.openDrawer(GravityCompat.START)
                }
            }
        }

        // 设置关闭按钮点击事件
        closeButton.setOnClickListener {
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
            
            // 添加新的设置
            javaScriptCanOpenWindowsAutomatically = true
            allowFileAccess = true
            allowContentAccess = true
            databaseEnabled = true
            
            // 设置缓存模式
            cacheMode = WebSettings.LOAD_DEFAULT
            
            // 设置 UA
            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
            
            // 允许混合内容
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            
            // 启用第三方 Cookie
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // 显示进度条和加载视图
                progressBar.visibility = View.VISIBLE
                loadingView.visibility = View.VISIBLE
                Log.d("SearchActivity", "开始加载URL: $url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // 隐藏进度条和加载视图
                progressBar.visibility = View.GONE
                loadingView.visibility = View.GONE
                updateWebViewTheme()
                Log.d("SearchActivity", "页面加载完成: $url")
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                // 隐藏进度条和加载视图
                progressBar.visibility = View.GONE
                loadingView.visibility = View.GONE
                
                val errorUrl = request?.url?.toString() ?: "unknown"
                val errorDescription = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    error?.description?.toString()
                } else {
                    "未知错误"
                }
                val errorMsg = "加载失败: $errorDescription\nURL: $errorUrl"
                Log.e("SearchActivity", errorMsg)
                Toast.makeText(this@SearchActivity, errorMsg, Toast.LENGTH_LONG).show()
                
                // 显示错误页面
                val errorHtml = """
                    <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1">
                            <style>
                                body { 
                                    font-family: sans-serif;
                                    padding: 20px;
                                    text-align: center;
                                }
                                .error-container {
                                    margin-top: 50px;
                                }
                                .error-title {
                                    color: #d32f2f;
                                    font-size: 20px;
                                    margin-bottom: 10px;
                                }
                                .error-message {
                                    color: #666;
                                    font-size: 16px;
                                }
                                .error-url {
                                    color: #999;
                                    font-size: 14px;
                                    margin-top: 10px;
                                    word-break: break-all;
                                }
                            </style>
                        </head>
                        <body>
                            <div class="error-container">
                                <div class="error-title">加载失败</div>
                                <div class="error-message">$errorDescription</div>
                                <div class="error-url">$errorUrl</div>
                            </div>
                        </body>
                    </html>
                """.trimIndent()
                view?.loadData(errorHtml, "text/html", "UTF-8")
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                request?.url?.let { uri ->
                    Log.d("SearchActivity", "正在处理URL: ${uri.toString()}")
                    if (uri.scheme == "mailto" || uri.scheme == "tel" || uri.scheme == "sms") {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, uri))
                            return true
                        } catch (e: Exception) {
                            Log.e("SearchActivity", "处理特殊URL失败", e)
                        }
                    }
                }
                return false
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress == 100) {
                    // 加载完成，隐藏进度条
                    progressBar.visibility = View.GONE
                } else {
                    // 更新加载进度
                    if (progressBar.visibility != View.VISIBLE) {
                        progressBar.visibility = View.VISIBLE
                    }
                    progressBar.progress = newProgress
                }
            }
        }

        // 初始化时设置主题
        updateWebViewTheme()
    }

    private fun updateWebViewTheme() {
        val currentNightMode = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isDarkMode = currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            webView.settings.forceDark = if (isDarkMode) {
                WebSettings.FORCE_DARK_ON
            } else {
                WebSettings.FORCE_DARK_OFF
            }
        } else {
            // 对于低版本 Android，在页面加载完成后注入 CSS
            if (isDarkMode) {
                webView.evaluateJavascript("""
                    (function() {
                        var darkModeStyle = document.getElementById('dark-mode-style');
                        if (!darkModeStyle) {
                            darkModeStyle = document.createElement('style');
                            darkModeStyle.id = 'dark-mode-style';
                            darkModeStyle.type = 'text/css';
                            darkModeStyle.innerHTML = `
                                :root {
                                    filter: invert(90%) hue-rotate(180deg) !important;
                                }
                                img, video, canvas, [style*="background-image"] {
                                    filter: invert(100%) hue-rotate(180deg) !important;
                                }
                                @media (prefers-color-scheme: dark) {
                                    :root {
                                        filter: none !important;
                                    }
                                    img, video, canvas, [style*="background-image"] {
                                        filter: none !important;
                                    }
                                }
                            `;
                            document.head.appendChild(darkModeStyle);
                        }
                    })()
                """.trimIndent(), null)
            } else {
                webView.evaluateJavascript("""
                    (function() {
                        var darkModeStyle = document.getElementById('dark-mode-style');
                        if (darkModeStyle) {
                            darkModeStyle.remove();
                        }
                    })()
                """.trimIndent(), null)
            }
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // 配置变化时更新主题
        updateWebViewTheme()
    }

    private fun setupLetterIndexBar() {
        letterIndexBar.onLetterSelectedListener = { _, letter ->
            updateEngineList(letter)
        }
    }

    private fun updateEngineList(selectedLetter: Char? = null) {
        // 更新字母标题
        letterTitle.text = selectedLetter?.toString() ?: ""
        letterTitle.visibility = if (selectedLetter != null) View.VISIBLE else View.GONE
        
        // 设置字母标题的颜色和背景
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        letterTitle.setTextColor(ContextCompat.getColor(this,
            if (isDarkMode) R.color.letter_index_text_dark
            else R.color.letter_index_text_light))
        letterTitle.setBackgroundColor(ContextCompat.getColor(this,
            if (isDarkMode) R.color.letter_index_selected_background_dark
            else R.color.letter_index_selected_background_light))

        previewEngineList.removeAllViews()

        val engines = if (isAIMode) {
            AISearchEngine.DEFAULT_AI_ENGINES.filter { it.isEnabled }
        } else {
            NORMAL_SEARCH_ENGINES
        }
        
        val filteredEngines = if (selectedLetter != null) {
            engines.filter { engine ->
                val firstChar = engine.name.first()
                when {
                    firstChar.toString().matches(Regex("[A-Za-z]")) -> 
                        firstChar.uppercaseChar() == selectedLetter.uppercaseChar()
                    firstChar.toString().matches(Regex("[\u4e00-\u9fa5]")) -> {
                        val pinyinArray = PinyinHelper.toHanyuPinyinStringArray(firstChar)
                        pinyinArray?.firstOrNull()?.firstOrNull()?.uppercaseChar() == selectedLetter.uppercaseChar()
                    }
                    else -> false
                }
            }
        } else {
            engines
        }

        // 确保引擎列表可见
        previewEngineList.visibility = View.VISIBLE
        
        filteredEngines.forEach { engine ->
            val engineItem = LayoutInflater.from(this)
                .inflate(R.layout.item_ai_engine, previewEngineList, false)

            // 设置引擎图标
            engineItem.findViewById<ImageView>(R.id.engine_icon).apply {
                setImageResource(engine.iconResId)
                visibility = View.VISIBLE
                setColorFilter(ContextCompat.getColor(this@SearchActivity,
                    if (isDarkMode) R.color.engine_icon_dark
                    else R.color.engine_icon_light))
            }
            
            // 设置引擎名称
            engineItem.findViewById<TextView>(R.id.engine_name).apply {
                text = engine.name
                visibility = View.VISIBLE
                setTextColor(ContextCompat.getColor(this@SearchActivity,
                    if (isDarkMode) R.color.engine_name_text_dark
                    else R.color.engine_name_text_light))
            }
            
            // 设置引擎描述
            engineItem.findViewById<TextView>(R.id.engine_description).apply {
                text = engine.description
                visibility = if (engine.description.isNotEmpty()) View.VISIBLE else View.GONE
                setTextColor(ContextCompat.getColor(this@SearchActivity,
                    if (isDarkMode) R.color.engine_description_text_dark
                    else R.color.engine_description_text_light))
            }

            // 设置项目背景
            engineItem.setBackgroundColor(ContextCompat.getColor(this,
                if (isDarkMode) R.color.engine_list_background_dark
                else R.color.engine_list_background_light))

            // 设置点击事件
            engineItem.setOnClickListener {
                if (engine is AISearchEngine && !engine.isEnabled) {
                    Toast.makeText(this, "请先启用该搜索引擎", Toast.LENGTH_SHORT).show()
                } else {
                    openSearchEngine(engine)
                    drawerLayout.closeDrawer(if (settingsManager.isLeftHandedMode) GravityCompat.END else GravityCompat.START)
                }
            }

            engineItem.setOnLongClickListener {
                showEngineSettings(engine)
                true
            }

            // 添加到列表中
            previewEngineList.addView(engineItem)
            
            // 添加分隔线
            if (filteredEngines.last() != engine) {
                View(this).apply {
                    setBackgroundColor(ContextCompat.getColor(this@SearchActivity,
                        if (isDarkMode) R.color.divider_dark
                        else R.color.divider_light))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        1
                    )
                    previewEngineList.addView(this)
                }
            }
        }
    }

    // EngineAdapter class
    private inner class EngineAdapter(
        private var engines: List<AISearchEngine>,
        private val onEngineClick: (AISearchEngine) -> Unit
    ) : RecyclerView.Adapter<EngineAdapter.ViewHolder>() {
        
        fun updateEngines(newEngines: List<AISearchEngine>) {
            engines = newEngines
            notifyDataSetChanged()
        }
        
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_ai_engine, parent, false)
            return ViewHolder(view)
        }
        
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val engine = engines[position]
            holder.bind(engine)
        }
        
        override fun getItemCount() = engines.size
        
        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val icon: ImageView = itemView.findViewById(R.id.engine_icon)
            private val name: TextView = itemView.findViewById(R.id.engine_name)
            private val description: TextView = itemView.findViewById(R.id.engine_description)
            
            fun bind(engine: AISearchEngine) {
                icon.setImageResource(engine.iconResId)
                name.text = engine.name
                description.text = engine.description
                
                itemView.setOnClickListener {
                    if (engine.isEnabled) {
                        onEngineClick(engine)
                    } else {
                        Toast.makeText(this@SearchActivity, "请先启用该搜索引擎", Toast.LENGTH_SHORT).show()
                    }
                }
                
                itemView.setOnLongClickListener {
                    showEngineSettings(engine)
                    true
                }
            }
        }
    }

    private fun showEngineSettings(engine: SearchEngine) {
        val options = if (engine is AISearchEngine) {
            arrayOf("访问主页", "复制链接", "分享", "在浏览器中打开")
        } else {
            arrayOf("访问主页", "复制链接", "分享", "在浏览器中打开")
        }

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("${engine.name} 选项")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openSearchEngine(engine)
                    1 -> {
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("URL", engine.url))
                        Toast.makeText(this, "已复制链接", Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, "${engine.name}: ${engine.url}")
                        }
                        startActivity(Intent.createChooser(intent, "分享到"))
                    }
                    3 -> {
                        val intent = Intent(Intent.ACTION_VIEW, 
                            android.net.Uri.parse(engine.url))
                        startActivity(intent)
                    }
                }
            }
            .create()
        dialog.show()
    }

    private fun loadDefaultSearchEngine() {
        try {
            // 从设置中获取默认搜索引擎信息
            val defaultEngineValue = settingsManager.getString(SettingsActivity.PREF_DEFAULT_SEARCH_ENGINE, "DeepSeek|https://deepseek.com|true")
            
            // 解析设置值
            val parts = defaultEngineValue.split("|")
            if (parts.size < 3) {
                Log.e("SearchActivity", "搜索引擎设置格式错误: $defaultEngineValue")
                loadDeepSeekAsDefault()
                return
            }

            val engineName = parts[0]
            val engineUrl = parts[1]
            val isAIEngine = parts[2].toBoolean()

            // 如果是功能主页，直接打开 HomeActivity
            if (engineUrl == "home") {
                val intent = Intent(this, HomeActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
                }
                startActivity(intent)
                finish() // 关闭当前的 SearchActivity
                return
            }
            
            // 设置搜索模式
            isAIMode = isAIEngine
            modeSwitch.isChecked = isAIEngine

            // 查找对应的搜索引擎
            val engine = if (isAIEngine) {
                AISearchEngine.DEFAULT_AI_ENGINES.find { it.name == engineName }
            } else {
                NORMAL_SEARCH_ENGINES.find { it.name == engineName }
            }

            if (engine != null) {
                // 确保 WebView 已经初始化并加载URL
                webView.post {
                    try {
                        val urlToLoad = if (engine.url.contains("{query}")) {
                            engine.url.replace("{query}", "")
                        } else {
                            engine.url
                        }
                        webView.loadUrl(urlToLoad)
                        Log.d("SearchActivity", "正在加载URL: $urlToLoad")
                    } catch (e: Exception) {
                        Log.e("SearchActivity", "加载URL失败", e)
                        loadDeepSeekAsDefault()
                    }
                }
            } else {
                Log.e("SearchActivity", "未找到搜索引擎: $engineName")
                loadDeepSeekAsDefault()
            }
        } catch (e: Exception) {
            Log.e("SearchActivity", "加载默认搜索引擎失败", e)
            loadDeepSeekAsDefault()
        }
    }

    private fun loadDeepSeekAsDefault() {
        val deepseek = AISearchEngine.DEFAULT_AI_ENGINES.find { it.name == "DeepSeek" }
        deepseek?.let {
            webView.post {
                try {
                    webView.loadUrl("https://deepseek.com")
                    // 保存为默认搜索引擎
                    settingsManager.putString(SettingsActivity.PREF_DEFAULT_SEARCH_ENGINE, "${it.name}|${it.url}|true")
                    settingsManager.putBoolean(SettingsActivity.PREF_DEFAULT_SEARCH_MODE, true)
                    isAIMode = true
                    modeSwitch.isChecked = true
                    Toast.makeText(this, "已重置为 DeepSeek 作为默认搜索引擎", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("SearchActivity", "加载DeepSeek失败", e)
                    Toast.makeText(this, "加载搜索引擎失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openSearchEngine(engine: SearchEngine) {
        // 如果是主页选项，直接打开HomeActivity
        if (engine.url == "home") {
            val intent = Intent(this, HomeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            startActivity(intent)
            finish()
            return
        }

        // 特殊处理 ChatGPT
        if (engine.url.contains("chat.openai.com")) {
            AlertDialog.Builder(this)
                .setTitle("访问提示")
                .setMessage("访问 ChatGPT 需要：\n1. 科学上网\n2. OpenAI 账号登录\n\n是否继续访问？")
                .setPositiveButton("继续") { _, _ ->
                    webView.loadUrl(engine.url)
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }

        // 加载其他 URL
        webView.loadUrl(engine.url)
    }

    private fun updateLayoutForHandedness() {
        val isLeftHanded = settingsManager.isLeftHandedMode
        
        // 更新抽屉位置
        (drawerLayout.getChildAt(1) as? LinearLayout)?.let { drawer ->
            // 先关闭抽屉，避免切换时的动画问题
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            }
            if (drawerLayout.isDrawerOpen(GravityCompat.END)) {
                drawerLayout.closeDrawer(GravityCompat.END)
            }
            
            // 更新抽屉位置
            drawer.layoutParams = (drawer.layoutParams as DrawerLayout.LayoutParams).apply {
                gravity = if (isLeftHanded) Gravity.END else Gravity.START
            }
        }

        // 更新菜单按钮位置
        val leftButtons = findViewById<LinearLayout>(R.id.left_buttons)
        val rightButtons = findViewById<LinearLayout>(R.id.right_buttons)
        val menuButton = findViewById<ImageButton>(R.id.btn_menu)

        // 从当前父容器中移除菜单按钮
        (menuButton.parent as? ViewGroup)?.removeView(menuButton)

        if (isLeftHanded) {
            // 左手模式：将菜单按钮添加到右侧按钮容器的开始位置
            rightButtons.addView(menuButton, 0)
        } else {
            // 右手模式：将菜单按钮添加到左侧按钮容器
            leftButtons.addView(menuButton)
        }
    }

    override fun onBackPressed() {
        when {
            webView.canGoBack() -> webView.goBack()
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        try {
            if (isSettingsReceiverRegistered) {
                unregisterReceiver(settingsReceiver)
                isSettingsReceiverRegistered = false
            }
            if (isLayoutThemeReceiverRegistered) {
                unregisterReceiver(layoutThemeReceiver)
                isLayoutThemeReceiverRegistered = false
            }
        } catch (e: Exception) {
            Log.e("SearchActivity", "Error unregistering receivers", e)
        }
        super.onDestroy()
    }

    private fun updateLetterIndexBarTheme() {
        val nightModeFlags = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDarkMode = nightModeFlags == Configuration.UI_MODE_NIGHT_YES
        
        // 设置深色模式状态
        letterIndexBar.setDarkMode(isDarkMode)
        
        // 设置背景色
        letterIndexBar.setBackgroundColor(ContextCompat.getColor(this,
            if (isDarkMode) R.color.letter_index_background_dark
            else R.color.letter_index_background_light))
            
        // 设置字母标题的颜色和背景
        letterTitle.setTextColor(ContextCompat.getColor(this,
            if (isDarkMode) R.color.letter_index_text_dark
            else R.color.letter_index_text_light))
        letterTitle.setBackgroundColor(ContextCompat.getColor(this,
            if (isDarkMode) R.color.letter_index_selected_background_dark
            else R.color.letter_index_selected_background_light))
    }

    private fun updateEngineListTheme() {
        val currentNightMode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isDarkMode = currentNightMode == Configuration.UI_MODE_NIGHT_YES

        // 更新抽屉布局背景色
        (drawerLayout.getChildAt(1) as? LinearLayout)?.apply {
            setBackgroundColor(ContextCompat.getColor(this@SearchActivity,
                if (isDarkMode) R.color.engine_list_background_dark
                else R.color.engine_list_background_light))
        }

        // 更新搜索引擎列表的背景色
        previewEngineList.setBackgroundColor(ContextCompat.getColor(this,
            if (isDarkMode) R.color.engine_list_background_dark
            else R.color.engine_list_background_light))

        // 更新每个搜索引擎项的颜色
        for (i in 0 until previewEngineList.childCount) {
            val child = previewEngineList.getChildAt(i)
            if (child is ViewGroup) {
                // 更新引擎名称文本颜色
                child.findViewById<TextView>(R.id.engine_name)?.apply {
                    setTextColor(ContextCompat.getColor(this@SearchActivity,
                        if (isDarkMode) R.color.engine_name_text_dark
                        else R.color.engine_name_text_light))
                }

                // 更新引擎描述文本颜色
                child.findViewById<TextView>(R.id.engine_description)?.apply {
                    setTextColor(ContextCompat.getColor(this@SearchActivity,
                        if (isDarkMode) R.color.engine_description_text_dark
                        else R.color.engine_description_text_light))
                }

                // 更新图标颜色
                child.findViewById<ImageView>(R.id.engine_icon)?.apply {
                    setColorFilter(ContextCompat.getColor(this@SearchActivity,
                        if (isDarkMode) R.color.engine_icon_dark
                        else R.color.engine_icon_light))
                }

                // 更新整个项目的背景
                child.setBackgroundColor(ContextCompat.getColor(this,
                    if (isDarkMode) R.color.engine_list_background_dark
                    else R.color.engine_list_background_light))
            } else if (child is View && child.layoutParams.height == 1) {
                // 更新分隔线颜色
                child.setBackgroundColor(ContextCompat.getColor(this,
                    if (isDarkMode) R.color.divider_dark
                    else R.color.divider_light))
            }
        }

        // 强制重绘整个列表
        previewEngineList.invalidate()
    }

    private fun setupDrawer() {
        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            
            override fun onDrawerOpened(drawerView: View) {
                updateEngineList()
            }
            
            override fun onDrawerClosed(drawerView: View) {}
            
            override fun onDrawerStateChanged(newState: Int) {}
        })

        // 根据当前模式设置初始抽屉位置
        val isLeftHanded = settingsManager.isLeftHandedMode
        (drawerLayout.getChildAt(1) as? LinearLayout)?.let { drawer ->
            drawer.layoutParams = (drawer.layoutParams as DrawerLayout.LayoutParams).apply {
                gravity = if (isLeftHanded) Gravity.END else Gravity.START
            }
        }
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
    }

    private fun setupEngineList() {
        engineList.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        engineAdapter = EngineAdapter(
            engines = if (isAIMode) AISearchEngine.DEFAULT_AI_ENGINES else emptyList(),
            onEngineClick = { engine ->
                openSearchEngine(engine)
                drawerLayout.closeDrawer(if (settingsManager.isLeftHandedMode) GravityCompat.END else GravityCompat.START)
            }
        )
        engineList.adapter = engineAdapter
    }

    private fun checkClipboard() {
        if (intent.getBooleanExtra("from_floating_ball", false)) {
            val clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboardManager.hasPrimaryClip()) {
                val clipData = clipboardManager.primaryClip
                val clipText = clipData?.getItemAt(0)?.text?.toString()?.trim()
                
                if (!clipText.isNullOrEmpty()) {
                    showClipboardDialog(clipText)
                }
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
            val intent = Intent(this, FullscreenWebViewActivity::class.java).apply {
                putExtra("url", url)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "打开URL失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun searchContent(query: String) {
        try {
            val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
            val searchUrl = "https://www.baidu.com/s?wd=$encodedQuery"
            openUrl(searchUrl)
        } catch (e: Exception) {
            Toast.makeText(this, "搜索内容失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun registerReceivers() {
        try {
            // Register settings change receiver
            val settingsFilter = IntentFilter(SettingsActivity.ACTION_SETTINGS_CHANGED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                registerReceiver(settingsReceiver, settingsFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(settingsReceiver, settingsFilter)
            }
            isSettingsReceiverRegistered = true
            
            // Register layout theme change receiver
            val themeFilter = IntentFilter("com.example.aifloatingball.LAYOUT_THEME_CHANGED")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                registerReceiver(layoutThemeReceiver, themeFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(layoutThemeReceiver, themeFilter)
            }
            isLayoutThemeReceiverRegistered = true
        } catch (e: Exception) {
            Log.e("SearchActivity", "Error registering receivers", e)
        }
    }
} 