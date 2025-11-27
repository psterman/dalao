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
                    // 初始化滚动处理（JavaScript已处理滚动监听）
                    setupReaderModeScrollListener()
                    // 确保UI初始状态为显示
                    if (!isAddressBarVisible) {
                        showAddressBarAndTabs(true)
                    }
                }
            }
            
            override fun onReaderModeExited() {
                runOnUiThread {
                    statusText.text = "已退出阅读模式"
                    removeReaderModeScrollListener()
                    // 确保UI显示
                    showAddressBarAndTabs(true)
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
            
            override fun onScroll(scrollTop: Int, scrollDelta: Int, isAtTop: Boolean, isAtBottom: Boolean) {
                runOnUiThread {
                    handleReaderModeScroll(scrollTop, scrollDelta, isAtTop, isAtBottom)
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
                                    // 重新初始化滚动处理（JavaScript会自动处理滚动监听）
                                    setupReaderModeScrollListener()
                                    // 确保UI状态正确
                                    if (!isAddressBarVisible) {
                                        showAddressBarAndTabs(true)
                                    }
                                }
                            }, 1000)
                        } else if (isReaderMode) {
                            // 即使不在阅读模式，如果之前设置了监听，也要重新初始化
                            setupReaderModeScrollListener()
                            // 确保UI状态正确
                            if (!isAddressBarVisible) {
                                showAddressBarAndTabs(true)
                            }
                        } else {
                            // 非阅读模式，确保UI显示
                            if (!isAddressBarVisible) {
                                showAddressBarAndTabs(true)
                            }
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
        
        // 监听ViewPager页面切换，重新初始化滚动处理
        viewPager.registerOnPageChangeCallback(object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                // 如果处于阅读模式，重新初始化滚动处理（JavaScript会自动处理滚动监听）
                if (readerModeManager.isReaderModeActive()) {
                    setupReaderModeScrollListener()
                    // 确保UI状态正确
                    if (!isAddressBarVisible) {
                        showAddressBarAndTabs(true)
                    }
                }
            }
        })
        
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
            // 显示底部按钮（不受阅读模式滚动控制影响）
            addTabButton.visibility = View.VISIBLE
            addTabButton.alpha = 1f
            addTabButton.show()
        } else {
            // 隐藏标签页概览
            viewPager.visibility = View.VISIBLE
            tabRecyclerView.visibility = View.GONE
            // 根据阅读模式状态决定是否显示底部按钮
            if (readerModeManager.isReaderModeActive()) {
                // 阅读模式下，根据地址栏可见性决定底部按钮显示
                if (isAddressBarVisible) {
                    addTabButton.visibility = View.VISIBLE
                    addTabButton.alpha = 1f
                    addTabButton.show()
                } else {
                    addTabButton.hide()
                }
            } else {
                addTabButton.hide()
            }
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
        removeReaderModeScrollListener()
        super.onDestroy()
        tabManager.cleanup()
    }
    
    // ==================== 阅读模式滚动监听 ====================
    
    private var isAddressBarVisible = true
    private val scrollThreshold = 15 // 滚动阈值，进一步降低以提高响应速度
    private var scrollHandler: android.os.Handler? = null
    private var pendingScrollAction: Runnable? = null
    private var lastScrollDirection: Int = 0 // 记录上次滚动方向：1=向下，-1=向上，0=未确定
    private var consecutiveScrollCount: Int = 0 // 连续同方向滚动计数，用于防抖
    
    // 缓存UI组件引用，避免重复findViewById
    private var cachedAppBarLayout: com.google.android.material.appbar.AppBarLayout? = null
    private var cachedFloatingButton: com.google.android.material.floatingactionbutton.FloatingActionButton? = null
    private var cachedAppBarHeight: Float = 0f
    
    /**
     * 设置阅读模式滚动监听
     * 注意：实际滚动监听由JavaScript代码处理，这里只做初始化标记
     */
    private fun setupReaderModeScrollListener() {
        // JavaScript代码已经处理了滚动监听，这里不需要再设置View.OnScrollChangeListener
        // 避免双重监听导致冲突
        scrollHandler = android.os.Handler(android.os.Looper.getMainLooper())
        
        // 初始化缓存UI组件引用
        initCachedViews()
    }
    
    /**
     * 初始化缓存的UI组件引用
     */
    private fun initCachedViews() {
        if (cachedAppBarLayout == null) {
            cachedAppBarLayout = findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.app_bar_layout)
            cachedFloatingButton = findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.add_tab_button)
            
            // 预计算并缓存AppBar高度
            cachedAppBarLayout?.let { appBar ->
                if (appBar.height > 0) {
                    cachedAppBarHeight = appBar.height.toFloat()
                } else {
                    // 估算高度：toolbar(56dp) + addressBar(约60dp) + tabLayout(48dp) + margin = 约200dp
                    cachedAppBarHeight = resources.displayMetrics.density * 200
                }
            }
        }
    }
    
    /**
     * 移除阅读模式滚动监听
     */
    private fun removeReaderModeScrollListener() {
        // 取消待处理的滚动动作
        pendingScrollAction?.let {
            scrollHandler?.removeCallbacks(it)
        }
        pendingScrollAction = null
        scrollHandler = null
        
        // 重置滚动状态
        lastScrollDirection = 0
        consecutiveScrollCount = 0
    }
    
    /**
     * 处理阅读模式滚动事件（由JavaScript回调触发）
     * 优化：添加滚动方向记忆和防抖逻辑，减少频繁切换，确保流畅响应
     */
    private fun handleReaderModeScroll(scrollTop: Int, scrollDelta: Int, isAtTop: Boolean, isAtBottom: Boolean) {
        // 如果不在阅读模式，直接返回
        if (!readerModeManager.isReaderModeActive()) {
            return
        }
        
        // 取消之前的待处理动作
        pendingScrollAction?.let {
            scrollHandler?.removeCallbacks(it)
        }
        
        // 创建新的滚动处理动作
        pendingScrollAction = Runnable {
            // 在顶部或底部时，强制显示UI
            if (isAtTop || isAtBottom) {
                if (!isAddressBarVisible) {
                    showAddressBarAndTabs(true)
                }
                // 重置滚动方向记忆
                lastScrollDirection = 0
                consecutiveScrollCount = 0
            } else {
                // 根据滚动方向立即响应
                val absDelta = Math.abs(scrollDelta)
                if (absDelta > scrollThreshold) {
                    val currentDirection = if (scrollDelta > 0) 1 else -1
                    
                    // 如果方向改变，重置计数
                    if (currentDirection != lastScrollDirection) {
                        consecutiveScrollCount = 0
                        lastScrollDirection = currentDirection
                    } else {
                        consecutiveScrollCount++
                    }
                    
                    // 只有在连续同方向滚动至少1次时才切换UI（减少抖动）
                    if (consecutiveScrollCount >= 1) {
                        if (currentDirection > 0) {
                            // 向下滚动，隐藏UI
                            if (isAddressBarVisible) {
                                showAddressBarAndTabs(false)
                            }
                        } else {
                            // 向上滚动，显示UI
                            if (!isAddressBarVisible) {
                                showAddressBarAndTabs(true)
                            }
                        }
                    }
                }
            }
        }
        
        // 立即执行，不使用延迟，确保响应速度
        scrollHandler?.post(pendingScrollAction!!)
    }
    
    /**
     * 显示或隐藏地址栏、标签组和底部悬浮按钮（带动画）
     * 优化：使用缓存的UI引用，优化动画性能，确保立即执行
     */
    private fun showAddressBarAndTabs(show: Boolean) {
        // 如果状态没有变化，直接返回
        if (isAddressBarVisible == show) {
            return
        }
        
        isAddressBarVisible = show
        
        // 使用缓存的UI组件引用
        val appBarLayout = cachedAppBarLayout ?: run {
            initCachedViews()
            cachedAppBarLayout
        }
        
        val floatingButton = cachedFloatingButton ?: run {
            initCachedViews()
            cachedFloatingButton
        }
        
        if (appBarLayout == null) {
            return
        }
        
        // 使用缓存的高度，如果为0则重新计算
        val height = if (cachedAppBarHeight > 0) {
            cachedAppBarHeight
        } else {
            if (appBarLayout.height > 0) {
                appBarLayout.height.toFloat().also { cachedAppBarHeight = it }
            } else {
                resources.displayMetrics.density * 200
            }
        }
        
        // 取消之前的动画，确保立即响应
        appBarLayout.clearAnimation()
        appBarLayout.animate().cancel()
        floatingButton?.animate()?.cancel()
        
        // 启用硬件加速，提升动画性能
        appBarLayout.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        floatingButton?.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        
        if (show) {
            // 显示：从上方滑入
            appBarLayout.visibility = View.VISIBLE
            appBarLayout.alpha = 0f
            appBarLayout.translationY = -height
            
            // 使用更流畅的插值器和优化的动画时长
            // 使用DecelerateInterpolator实现快速开始、缓慢结束的效果
            appBarLayout.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180) // 进一步优化动画时长
                .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f)) // 增加减速因子，更流畅
                .withStartAction {
                    appBarLayout.visibility = View.VISIBLE
                }
                .withEndAction {
                    // 动画结束后关闭硬件加速，节省资源
                    appBarLayout.setLayerType(View.LAYER_TYPE_NONE, null)
                }
                .start()
            
            // 显示底部悬浮按钮（仅在阅读模式下且不在标签页概览时）
            floatingButton?.let {
                if (!isTabOverviewVisible && readerModeManager.isReaderModeActive()) {
                    it.visibility = View.VISIBLE
                    it.alpha = 0f
                    it.animate()
                        .alpha(1f)
                        .setDuration(180)
                        .setInterpolator(android.view.animation.DecelerateInterpolator(1.5f))
                        .withEndAction {
                            it.setLayerType(View.LAYER_TYPE_NONE, null)
                        }
                        .start()
                }
            }
        } else {
            // 隐藏：向上滑出
            // 使用AccelerateInterpolator实现缓慢开始、快速结束的效果
            appBarLayout.animate()
                .alpha(0f)
                .translationY(-height)
                .setDuration(180) // 进一步优化动画时长
                .setInterpolator(android.view.animation.AccelerateInterpolator(1.5f)) // 增加加速因子，更流畅
                .withEndAction {
                    appBarLayout.visibility = View.GONE
                    appBarLayout.translationY = 0f
                    appBarLayout.alpha = 1f // 重置alpha，避免下次显示时出现问题
                    appBarLayout.setLayerType(View.LAYER_TYPE_NONE, null)
                }
                .start()
            
            // 隐藏底部悬浮按钮（仅在阅读模式下）
            floatingButton?.let {
                if (readerModeManager.isReaderModeActive() && !isTabOverviewVisible) {
                    it.animate()
                        .alpha(0f)
                        .setDuration(180)
                        .setInterpolator(android.view.animation.AccelerateInterpolator(1.5f))
                        .withEndAction {
                            it.visibility = View.GONE
                            it.alpha = 1f // 重置alpha
                            it.setLayerType(View.LAYER_TYPE_NONE, null)
                        }
                        .start()
                }
            }
        }
    }
}
