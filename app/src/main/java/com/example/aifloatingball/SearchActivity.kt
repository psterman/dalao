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
import com.example.aifloatingball.view.LetterIndexBar

class SearchActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var letterIndexBar: LetterIndexBar
    private lateinit var letterTitle: TextView
    private lateinit var engineList: LinearLayout
    private lateinit var searchInput: EditText
    private lateinit var searchButton: ImageButton
    private lateinit var closeButton: ImageButton
    private lateinit var settingsManager: SettingsManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search)
        
        settingsManager = SettingsManager.getInstance(this)
        initViews()
        setupWebView()
        setupLetterIndexBar()
    }

    private fun initViews() {
        webView = findViewById(R.id.web_view)
        letterIndexBar = findViewById(R.id.letter_index_bar)
        letterTitle = findViewById(R.id.letter_title)
        engineList = findViewById(R.id.preview_engine_list)
        searchInput = findViewById(R.id.search_input)
        searchButton = findViewById(R.id.btn_search)
        closeButton = findViewById(R.id.btn_close)

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
            showEnginesByLetter(letter)
        }
    }

    private fun showEnginesByLetter(letter: Char) {
        letterTitle.text = letter.toString()
        engineList.removeAllViews()

        val engines = settingsManager.getEngineOrder().map { engine ->
            SearchEngine(engine.name, engine.url, engine.iconResId)
        }
        
        val matchingEngines = engines.filter {
            val firstChar = it.name.first()
            when {
                firstChar.toString().matches(Regex("[A-Za-z]")) -> 
                    firstChar.uppercaseChar() == letter
                firstChar.toString().matches(Regex("[\u4e00-\u9fa5]")) -> 
                    firstChar.toString().first().uppercaseChar() == letter
                else -> false
            }
        }

        matchingEngines.forEach { engine ->
            val engineItem = LayoutInflater.from(this)
                .inflate(R.layout.item_search_engine, engineList, false)

            engineItem.findViewById<ImageView>(R.id.engine_icon)
                .setImageResource(engine.iconResId)
            engineItem.findViewById<TextView>(R.id.engine_name)
                .text = engine.name

            engineItem.setOnClickListener {
                openSearchEngine(engine)
            }

            engineList.addView(engineItem)
        }
    }

    private fun openSearchEngine(engine: SearchEngine) {
        val query = searchInput.text.toString().trim()
        val url = if (query.isNotEmpty()) {
            engine.getSearchUrl(query)
        } else {
            engine.url
        }
        webView.loadUrl(url)
    }

    private fun performSearch(query: String) {
        val currentEngine = settingsManager.getEngineOrder().firstOrNull()?.let { engine ->
            SearchEngine(engine.name, engine.url, engine.iconResId)
        }
        currentEngine?.let { openSearchEngine(it) }
    }

    override fun onBackPressed() {
        when {
            webView.canGoBack() -> webView.goBack()
            else -> super.onBackPressed()
        }
    }
} 