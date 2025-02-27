package com.example.aifloatingball

import android.os.Bundle
import android.view.View
import android.view.LayoutInflater
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.view.LetterIndexBar
import net.sourceforge.pinyin4j.PinyinHelper

class SearchActivity : AppCompatActivity() {
    private lateinit var drawerLayout: androidx.drawerlayout.widget.DrawerLayout
    private lateinit var webView: WebView
    private lateinit var letterIndexBar: LetterIndexBar
    private lateinit var letterTitle: TextView
    private lateinit var engineList: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var searchButton: ImageButton
    private lateinit var closeButton: ImageButton
    private lateinit var menuButton: ImageButton
    private lateinit var settingsManager: SettingsManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        
        settingsManager = SettingsManager.getInstance(this)
        initViews()
        setupWebView()
        setupLetterIndexBar()
        setupDrawer()
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        webView = findViewById(R.id.web_view)
        letterIndexBar = findViewById(R.id.letter_index_bar)
        letterTitle = findViewById(R.id.letter_title)
        engineList = findViewById(R.id.preview_engine_list)
        searchInput = findViewById(R.id.search_input)
        searchButton = findViewById(R.id.btn_search)
        closeButton = findViewById(R.id.btn_close)
        menuButton = findViewById(R.id.btn_menu)

        menuButton.setOnClickListener {
            if (drawerLayout.isDrawerOpen(androidx.core.view.GravityCompat.START)) {
                drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
            } else {
                drawerLayout.openDrawer(androidx.core.view.GravityCompat.START)
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
        engineList.removeAllViews()

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
                .inflate(R.layout.item_ai_engine, engineList, false)

            engineItem.findViewById<ImageView>(R.id.engine_icon)
                .setImageResource(engine.iconResId)
            engineItem.findViewById<TextView>(R.id.engine_name)
                .text = engine.name
            engineItem.findViewById<TextView>(R.id.engine_description)
                .text = engine.description
                
            val checkbox = engineItem.findViewById<CheckBox>(R.id.checkbox)
            checkbox.isChecked = engine.isEnabled
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                engine.isEnabled = isChecked
                getSharedPreferences("engine_prefs", MODE_PRIVATE)
                    .edit()
                    .putBoolean("engine_${engine.id}", isChecked)
                    .apply()
            }

            engineItem.findViewById<ImageButton>(R.id.btn_settings).setOnClickListener {
                showEngineSettings(engine)
            }

            engineItem.setOnClickListener {
                if (engine.isEnabled) {
                    openSearchEngine(engine)
                    drawerLayout.closeDrawer(androidx.core.view.GravityCompat.START)
                } else {
                    Toast.makeText(this, "请先启用该搜索引擎", Toast.LENGTH_SHORT).show()
                }
            }

            engineList.addView(engineItem)
        }
    }

    private fun showEngineSettings(engine: AISearchEngine) {
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("${engine.name} 设置")
            .setItems(arrayOf("访问主页", "复制链接", "分享")) { _, which ->
                when (which) {
                    0 -> openSearchEngine(engine)
                    1 -> {
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("URL", engine.url))
                        Toast.makeText(this, "已复制链接", Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, "${engine.name}: ${engine.url}")
                        }
                        startActivity(android.content.Intent.createChooser(intent, "分享到"))
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
        drawerLayout.addDrawerListener(object : androidx.drawerlayout.widget.DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {}
            
            override fun onDrawerOpened(drawerView: View) {
                updateEngineList()
            }
            
            override fun onDrawerClosed(drawerView: View) {}
            
            override fun onDrawerStateChanged(newState: Int) {}
        })

        drawerLayout.setDrawerLockMode(androidx.drawerlayout.widget.DrawerLayout.LOCK_MODE_UNLOCKED)
    }

    override fun onBackPressed() {
        when {
            webView.canGoBack() -> webView.goBack()
            else -> super.onBackPressed()
        }
    }
} 