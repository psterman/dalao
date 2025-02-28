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
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.view.LetterIndexBar
import net.sourceforge.pinyin4j.PinyinHelper
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import android.os.Build

class SearchActivity : AppCompatActivity() {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var webView: WebView
    private lateinit var letterIndexBar: LetterIndexBar
    private lateinit var letterTitle: TextView
    private lateinit var previewEngineList: LinearLayout
    private lateinit var searchHistoryList: RecyclerView
    private lateinit var searchInput: EditText
    private lateinit var searchButton: ImageButton
    private lateinit var closeButton: ImageButton
    private lateinit var menuButton: ImageButton
    private lateinit var settingsManager: SettingsManager
    private lateinit var engineAdapter: EngineAdapter
    
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
            // Handle layout theme change
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        
        settingsManager = SettingsManager.getInstance(this)
        
        previewEngineList = findViewById(R.id.preview_engine_list)
        searchHistoryList = findViewById(R.id.searchHistoryList)
        
        // 设置搜索历史RecyclerView
        searchHistoryList.layoutManager = LinearLayoutManager(this)
        
        // 注册设置变化广播接收器
        val settingsFilter = IntentFilter(SettingsActivity.ACTION_SETTINGS_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerReceiver(settingsReceiver, settingsFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(settingsReceiver, settingsFilter)
        }
        
        // 注册布局主题变化的广播接收器
        val filter = IntentFilter("com.example.aifloatingball.LAYOUT_THEME_CHANGED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            registerReceiver(layoutThemeReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(layoutThemeReceiver, filter)
        }
        
        // 初始化布局
        initViews()
        setupWebView()
        setupLetterIndexBar()
        setupDrawer()
        updateLayoutForHandedness()
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        webView = findViewById(R.id.web_view)
        letterIndexBar = findViewById(R.id.letter_index_bar)
        letterTitle = findViewById(R.id.letter_title)
        searchInput = findViewById(R.id.searchInput)
        searchButton = findViewById(R.id.voiceSearchButton)
        closeButton = findViewById(R.id.btn_close)
        menuButton = findViewById(R.id.btn_menu)

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

        searchButton.setOnClickListener {
            val query = searchInput.text.toString().trim()
            if (query.isNotEmpty()) {
                performSearch(query)
            }
        }

        closeButton.setOnClickListener {
            finish()
        }

        letterIndexBar.engines = settingsManager.getEngineOrder().map { engine ->
            SearchEngine(engine.name, engine.url, engine.iconResId)
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
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
            }
        }
    }

    private fun setupLetterIndexBar() {
        letterIndexBar.onLetterSelectedListener = { _, letter ->
            updateEngineList(letter)
        }
    }

    private fun updateEngineList(selectedLetter: Char? = null) {
        letterTitle.text = selectedLetter?.toString() ?: ""
        previewEngineList.removeAllViews()

        val engines = AISearchEngine.DEFAULT_AI_ENGINES.filter { engine -> engine.isEnabled }
        
        val filteredEngines = if (selectedLetter != null) {
            engines.filter { engine ->
                val firstChar = engine.name.first()
                when {
                    firstChar.toString().matches(Regex("[A-Za-z]")) -> 
                        firstChar.uppercaseChar() == selectedLetter.uppercaseChar()
                    firstChar.toString().matches(Regex("[\u4e00-\u9fa5]")) -> 
                        PinyinHelper.toHanyuPinyinStringArray(firstChar)
                            ?.firstOrNull()
                            ?.first()
                            ?.uppercaseChar() == selectedLetter.uppercaseChar()
                    else -> false
                }
            }
        } else {
            engines
        }
        
        filteredEngines.forEach { engine ->
            val engineItem = LayoutInflater.from(this)
                .inflate(R.layout.item_ai_engine, previewEngineList, false)

            engineItem.findViewById<ImageView>(R.id.engine_icon)
                .setImageResource(engine.iconResId)
            engineItem.findViewById<TextView>(R.id.engine_name)
                .text = engine.name
            engineItem.findViewById<TextView>(R.id.engine_description)
                .text = engine.description

            engineItem.setOnClickListener {
                if (engine.isEnabled) {
                    openSearchEngine(engine)
                    drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    Toast.makeText(this, "请先启用该搜索引擎", Toast.LENGTH_SHORT).show()
                }
            }

            engineItem.setOnLongClickListener {
                showEngineSettings(engine)
                true
            }

            previewEngineList.addView(engineItem)
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

    private fun showEngineSettings(engine: AISearchEngine) {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("${engine.name} 选项")
            .setItems(arrayOf("访问主页", "复制链接", "分享", "在浏览器中打开")) { _, which ->
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

    private fun openSearchEngine(engine: AISearchEngine) {
        val query = searchInput.text.toString().trim()
        val url = if (query.isNotEmpty() && engine.url.contains("{query}")) {
            engine.url.replace("{query}", android.net.Uri.encode(query))
        } else {
            engine.url
        }
        webView.loadUrl(url)
    }

    private fun performSearch(query: String) {
        val currentEngine = AISearchEngine.DEFAULT_AI_ENGINES.firstOrNull { it.isEnabled }
        currentEngine?.let { openSearchEngine(it) }
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
        
        // 更新搜索历史列表边距
        val historyParams = searchHistoryList.layoutParams
        val marginStart = resources.getDimensionPixelSize(
            if (isLeftHanded) R.dimen.search_history_margin else R.dimen.engine_list_width
        )
        val marginEnd = resources.getDimensionPixelSize(
            if (isLeftHanded) R.dimen.engine_list_width else R.dimen.search_history_margin
        )
        
        when (historyParams) {
            is RelativeLayout.LayoutParams -> {
                historyParams.marginStart = marginStart
                historyParams.marginEnd = marginEnd
                searchHistoryList.layoutParams = historyParams
            }
            is LinearLayout.LayoutParams -> {
                historyParams.marginStart = marginStart
                historyParams.marginEnd = marginEnd
                searchHistoryList.layoutParams = historyParams
            }
            is FrameLayout.LayoutParams -> {
                historyParams.marginStart = marginStart
                historyParams.marginEnd = marginEnd
                searchHistoryList.layoutParams = historyParams
            }
        }
    }

    override fun onBackPressed() {
        when {
            webView.canGoBack() -> webView.goBack()
            else -> super.onBackPressed()
        }
    }

    override fun onDestroy() {
        unregisterReceiver(settingsReceiver)
        unregisterReceiver(layoutThemeReceiver)
        super.onDestroy()
    }
} 