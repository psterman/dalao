package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.webkit.URLUtil
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.aifloatingball.adblock.AdBlockFilter
import com.example.aifloatingball.tab.EnhancedTabManager
import com.example.aifloatingball.web.EnhancedWebViewClient
import com.example.aifloatingball.reader.NovelReaderModeManager
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * 多标签页浏览器Activity
 */
class MultiTabBrowserActivity : AppCompatActivity() {
    
    companion object {
        private const val TAG = "MultiTabBrowserActivity"
        const val EXTRA_INITIAL_URL = "initial_url"
        const val EXTRA_INITIAL_QUERY = "initial_query"
    }
    
    // UI组件
    private lateinit var toolbar: Toolbar
    private lateinit var addressBar: TextInputEditText
    private lateinit var addressBarLayout: TextInputLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var tabRecyclerView: RecyclerView
    private lateinit var addTabButton: FloatingActionButton
    private lateinit var progressBar: ProgressBar
    private lateinit var statusText: TextView
    
    // 核心组件
    private lateinit var tabManager: EnhancedTabManager
    private lateinit var adBlockFilter: AdBlockFilter
    private lateinit var readerModeManager: NovelReaderModeManager
    
    // 状态
    private var isTabOverviewVisible = false
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_multi_tab_browser)
        
        initViews()
        initComponents()
        setupUI()
        handleIntent()
    }
    
    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        addressBar = findViewById(R.id.address_bar)
        addressBarLayout = findViewById(R.id.address_bar_layout)
        viewPager = findViewById(R.id.view_pager)
        tabLayout = findViewById(R.id.tab_layout)
        tabRecyclerView = findViewById(R.id.tab_recycler_view)
        addTabButton = findViewById(R.id.add_tab_button)
        progressBar = findViewById(R.id.progress_bar)
        statusText = findViewById(R.id.status_text)
    }
    
    private fun initComponents() {
        // 初始化AdBlock过滤器
        adBlockFilter = AdBlockFilter(this)
        
        // 初始化阅读模式管理器
        readerModeManager = NovelReaderModeManager(this)
        readerModeManager.setListener(object : NovelReaderModeManager.ReaderModeListener {
            override fun onReaderModeEntered() {
                runOnUiThread {
                    statusText.text = "已进入阅读模式"
                    // 可以在这里更新UI，比如显示退出阅读模式的按钮
                }
            }
            
            override fun onReaderModeExited() {
                runOnUiThread {
                    statusText.text = "已退出阅读模式"
                }
            }
            
            override fun onChapterLoaded(chapter: NovelReaderModeManager.ChapterInfo) {
                runOnUiThread {
                    statusText.text = "加载章节: ${chapter.title}"
                    updateAddressBar(chapter.url)
                }
            }
            
            override fun onNextChapterRequested() {
                runOnUiThread {
                    val currentWebView = tabManager.getCurrentTab()?.webView
                    if (currentWebView != null) {
                        readerModeManager.loadNextChapter(currentWebView)
                    }
                }
            }
        })
        
        // 初始化标签页管理器
        tabManager = EnhancedTabManager(
            context = this,
            adBlockFilter = adBlockFilter,
            readerModeManager = readerModeManager,
            onPageLoadListener = object : EnhancedWebViewClient.PageLoadListener {
                override fun onPageStarted(view: android.webkit.WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    runOnUiThread {
                        progressBar.visibility = View.VISIBLE
                        statusText.text = "正在加载..."
                        updateAddressBar(url)
                    }
                }
                
                override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        val isReaderMode = readerModeManager.isReaderModeActive()
                        statusText.text = if (isReaderMode) "阅读模式" else "加载完成"
                        updateAddressBar(url)
                        updateTabTitle()
                        
                        // 如果处于阅读模式，且页面加载完成，重新解析内容
                        if (isReaderMode && view != null && url != null) {
                            // 延迟重新解析，确保页面完全加载
                            view.postDelayed({
                                if (readerModeManager.isReaderModeActive()) {
                                    readerModeManager.enterReaderMode(view, url, useNoImageMode = false)
                                }
                            }, 1000)
                        }
                    }
                }
                
                override fun onProgressChanged(view: android.webkit.WebView?, newProgress: Int) {
                    runOnUiThread {
                        progressBar.progress = newProgress
                        statusText.text = "加载中... $newProgress%"
                    }
                }
            },
            onUrlChangeListener = object : EnhancedWebViewClient.UrlChangeListener {
                override fun onUrlChanged(view: android.webkit.WebView?, url: String?) {
                    runOnUiThread {
                        updateAddressBar(url)
                    }
                }
            }
        )
    }
    
    private fun setupUI() {
        // 设置Toolbar
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "浏览器"
        
        // 初始化标签页管理器
        tabManager.initialize(viewPager, tabLayout, tabRecyclerView)
        
        // 设置地址栏
        setupAddressBar()
        
        // 设置添加标签页按钮
        addTabButton.setOnClickListener {
            showNewTabDialog()
        }
        
        // 设置标签页概览切换
        tabLayout.setOnClickListener {
            toggleTabOverview()
        }
        
        // 创建第一个标签页
        if (tabManager.getTabCount() == 0) {
            tabManager.restoreTabsState()
            if (tabManager.getTabCount() == 0) {
                tabManager.addTab()
            }
        }
    }
    
    private fun setupAddressBar() {
        addressBar.setOnEditorActionListener { _, _, _ ->
            val input = addressBar.text.toString().trim()
            if (input.isNotEmpty()) {
                loadUrl(input)
            }
            true
        }
        
        // 设置地址栏图标点击事件
        addressBarLayout.setEndIconOnClickListener {
            val input = addressBar.text.toString().trim()
            if (input.isNotEmpty()) {
                loadUrl(input)
            }
        }
    }
    
    private fun handleIntent() {
        val initialUrl = intent.getStringExtra(EXTRA_INITIAL_URL)
        val initialQuery = intent.getStringExtra(EXTRA_INITIAL_QUERY)
        
        when {
            !initialUrl.isNullOrEmpty() -> {
                loadUrl(initialUrl)
            }
            !initialQuery.isNullOrEmpty() -> {
                searchQuery(initialQuery)
            }
        }
    }
    
    private fun loadUrl(input: String) {
        val trimmedInput = input.trim()
        val isUrl = URLUtil.isValidUrl(trimmedInput) || 
                   (trimmedInput.contains(".") && !trimmedInput.contains(" ")) ||
                   trimmedInput.startsWith("http://") || 
                   trimmedInput.startsWith("https://")
        
        val url = if (isUrl) {
            if (trimmedInput.startsWith("http://") || trimmedInput.startsWith("https://")) {
                trimmedInput
            } else {
                "https://$trimmedInput"
            }
        } else {
            // 使用搜索引擎搜索
            "https://www.google.com/search?q=${java.net.URLEncoder.encode(trimmedInput, "UTF-8")}"
        }
        
        // 记录搜索历史（仅在非URL时记录）
        if (!isUrl && trimmedInput.isNotEmpty()) {
            com.example.aifloatingball.manager.SearchHistoryAutoRecorder.recordSearchHistory(
                context = this,
                query = trimmedInput,
                source = com.example.aifloatingball.manager.SearchHistoryAutoRecorder.SearchSource.SEARCH_TAB,
                tags = emptyList(),
                searchType = "网页搜索"
            )
        }
        
        tabManager.getCurrentTab()?.webView?.loadUrl(url)
        Log.d(TAG, "Loading URL: $url")
    }

    private fun searchQuery(query: String) {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isNotEmpty()) {
            // 记录搜索历史
            com.example.aifloatingball.manager.SearchHistoryAutoRecorder.recordSearchHistory(
                context = this,
                query = trimmedQuery,
                source = com.example.aifloatingball.manager.SearchHistoryAutoRecorder.SearchSource.SEARCH_TAB,
                tags = emptyList(),
                searchType = "网页搜索"
            )
        }
        val searchUrl = "https://www.google.com/search?q=${java.net.URLEncoder.encode(trimmedQuery, "UTF-8")}"
        tabManager.getCurrentTab()?.webView?.loadUrl(searchUrl)
        Log.d(TAG, "Searching: $trimmedQuery")
    }
    
    private fun updateAddressBar(url: String?) {
        url?.let {
            addressBar.setText(it)
        }
    }
    
    private fun updateTabTitle() {
        val currentTab = tabManager.getCurrentTab()
        currentTab?.webView?.title?.let { title ->
            tabManager.updateTabTitle(title)
        }
    }
    
    private fun showNewTabDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_new_tab, null)
        val urlInput = dialogView.findViewById<EditText>(R.id.url_input)
        
        AlertDialog.Builder(this)
            .setTitle("新建标签页")
            .setView(dialogView)
            .setPositiveButton("打开") { _, _ ->
                val url = urlInput.text.toString().trim()
                if (url.isNotEmpty()) {
                    tabManager.addTab(url)
                } else {
                    tabManager.addTab()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun toggleTabOverview() {
        isTabOverviewVisible = !isTabOverviewVisible
        
        if (isTabOverviewVisible) {
            // 显示标签页概览
            viewPager.visibility = View.GONE
            tabRecyclerView.visibility = View.VISIBLE
            addTabButton.show()
        } else {
            // 隐藏标签页概览
            viewPager.visibility = View.VISIBLE
            tabRecyclerView.visibility = View.GONE
            addTabButton.hide()
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.browser_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.action_refresh -> {
                tabManager.getCurrentTab()?.webView?.reload()
                true
            }
            R.id.action_forward -> {
                tabManager.getCurrentTab()?.webView?.goForward()
                true
            }
            R.id.action_back -> {
                tabManager.getCurrentTab()?.webView?.goBack()
                true
            }
            R.id.action_adblock_settings -> {
                showAdBlockSettings()
                true
            }
            R.id.action_new_tab -> {
                showNewTabDialog()
                true
            }
            R.id.action_close_tab -> {
                tabManager.closeCurrentTab()
                true
            }
            R.id.action_reader_mode -> {
                val currentWebView = tabManager.getCurrentTab()?.webView
                val currentUrl = currentWebView?.url
                if (currentWebView != null && currentUrl != null) {
                    if (readerModeManager.isReaderModeActive()) {
                        readerModeManager.exitReaderMode(currentWebView)
                    } else {
                        readerModeManager.enterReaderMode(currentWebView, currentUrl)
                    }
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val readerModeItem = menu.findItem(R.id.action_reader_mode)
        readerModeItem?.let {
            if (readerModeManager.isReaderModeActive()) {
                it.title = "退出阅读模式"
            } else {
                it.title = "进入阅读模式"
            }
        }
        return super.onPrepareOptionsMenu(menu)
    }
    
    private fun showAdBlockSettings() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_adblock_settings, null)
        val enableSwitch = dialogView.findViewById<Switch>(R.id.adblock_enable_switch)
        val statusText = dialogView.findViewById<TextView>(R.id.adblock_status_text)
        
        // 设置当前状态
        enableSwitch.isChecked = true // adBlockFilter.isEnabled
        updateAdBlockStatus(statusText)
        
        enableSwitch.setOnCheckedChangeListener { _, isChecked ->
            adBlockFilter.setEnabled(isChecked)
            updateAdBlockStatus(statusText)
        }
        
        AlertDialog.Builder(this)
            .setTitle("广告拦截设置")
            .setView(dialogView)
            .setPositiveButton("确定", null)
            .show()
    }
    
    private fun updateAdBlockStatus(statusText: TextView) {
        // 这里可以显示拦截统计信息
        statusText.text = "广告拦截已启用"
    }
    
    override fun onBackPressed() {
        when {
            isTabOverviewVisible -> {
                toggleTabOverview()
            }
            tabManager.canGoBack() -> {
                tabManager.goBack()
            }
            else -> {
                super.onBackPressed()
            }
        }
    }
    
    override fun onDestroy() {
        tabManager.saveTabsState()
        super.onDestroy()
        tabManager.cleanup()
    }
}
