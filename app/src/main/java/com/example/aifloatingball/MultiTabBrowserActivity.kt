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
                    // 进入阅读模式前，必须先移除普通webview滚动监听，避免冲突
                    removeNormalWebViewScrollListener()
                    // 初始化滚动处理（JavaScript已处理滚动监听）
                    setupReaderModeScrollListener()
                    // 重置边界区域状态
                    isInBoundaryZone = false
                    lastScrollTop = 0
                    isReaderModeUIPermanentlyHidden = false // 重置永久隐藏标志
                    // 重置普通模式的滚动状态，避免状态残留
                    normalLastScrollY = 0
                    normalLastScrollDirection = 0
                    normalConsecutiveScrollCount = 0
                    isUIPermanentlyHidden = false // 重置普通模式的永久隐藏标志
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
                    // 退出阅读模式后，设置普通webview滚动监听
                    setupNormalWebViewScrollListener()
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
                            // 非阅读模式，设置普通webview滚动监听
                            setupNormalWebViewScrollListener()
                            // 只有在页面顶部时才确保UI显示，避免在用户滚动过程中强制显示UI
                            val currentWebView = tabManager.getCurrentTab()?.webView
                            val isAtTop = currentWebView?.let {
                                !it.canScrollVertically(-1) && it.scrollY <= 5
                            } ?: true
                            if (isAtTop && !isAddressBarVisible) {
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
                } else {
                    // 非阅读模式，设置普通webview滚动监听
                    // 重置滚动状态
                    val currentWebView = tabManager.getCurrentTab()?.webView
                    currentWebView?.let {
                        normalLastScrollY = it.scrollY
                        normalLastScrollDirection = 0
                        normalConsecutiveScrollCount = 0
                        // 重置永久隐藏标志
                        isUIPermanentlyHidden = false
                        // 切换tab时，如果UI被隐藏，先显示UI
                        if (!isAddressBarVisible) {
                            showAddressBarAndTabs(true)
                        }
                    }
                    setupNormalWebViewScrollListener()
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
        
        // 初始化普通webview滚动监听（如果不在阅读模式）
        if (!readerModeManager.isReaderModeActive()) {
            setupNormalWebViewScrollListener()
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
        removeNormalWebViewScrollListener()
        super.onDestroy()
        tabManager.cleanup()
    }
    
    // ==================== 滚动监听（阅读模式 + 普通模式） ====================
    
    private var isAddressBarVisible = true
    private val scrollThreshold = 15 // 滚动阈值，进一步降低以提高响应速度
    private var scrollHandler: android.os.Handler? = null
    private var pendingScrollAction: Runnable? = null
    private var lastScrollDirection: Int = 0 // 记录上次滚动方向：1=向下，-1=向上，0=未确定
    private var consecutiveScrollCount: Int = 0 // 连续同方向滚动计数，用于防抖
    
    // 边界区域状态记忆，避免在边界附近频繁切换
    private var isInBoundaryZone: Boolean = false // 是否在边界区域（顶部或底部附近）
    private var lastScrollTop: Int = 0 // 记录上次滚动位置，用于判断是否离开边界区域
    private val boundaryZoneThreshold = 100 // 边界区域阈值（像素），离开此距离后才恢复正常滚动判断
    private var isReaderModeUIPermanentlyHidden: Boolean = false // 标志：阅读模式下UI是否已被向下滚动永久隐藏
    
    // 缓存UI组件引用，避免重复findViewById
    private var cachedAppBarLayout: com.google.android.material.appbar.AppBarLayout? = null
    private var cachedFloatingButton: com.google.android.material.floatingactionbutton.FloatingActionButton? = null
    private var cachedAppBarHeight: Float = 0f
    
    // 普通webview滚动监听相关
    private var normalScrollHandler: android.os.Handler? = null
    private var normalScrollListener: View.OnScrollChangeListener? = null
    private var normalLastScrollY: Int = 0
    private var normalLastScrollDirection: Int = 0
    private var normalConsecutiveScrollCount: Int = 0
    private var isUIPermanentlyHidden: Boolean = false // 标志：UI是否已被向下滚动永久隐藏
    
    /**
     * 设置阅读模式滚动监听
     * 注意：实际滚动监听由JavaScript代码处理，这里只做初始化标记
     */
    private fun setupReaderModeScrollListener() {
        // 确保移除普通模式的滚动监听，避免冲突
        removeNormalWebViewScrollListener()
        
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
        isInBoundaryZone = false
        lastScrollTop = 0
        isReaderModeUIPermanentlyHidden = false // 重置永久隐藏标志
    }
    
    /**
     * 设置普通webview滚动监听（非阅读模式）
     */
    private fun setupNormalWebViewScrollListener() {
        // 移除之前的监听器
        removeNormalWebViewScrollListener()
        
        // 如果处于阅读模式，不设置普通滚动监听
        if (readerModeManager.isReaderModeActive()) {
            return
        }
        
        normalScrollHandler = android.os.Handler(android.os.Looper.getMainLooper())
        
        // 初始化缓存UI组件引用
        initCachedViews()
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            // 创建滚动监听器（动态获取当前webview，避免闭包捕获问题）
            normalScrollListener = View.OnScrollChangeListener { view, _, scrollY, _, oldScrollY ->
                // 只在非阅读模式下处理
                if (!readerModeManager.isReaderModeActive()) {
                    // 动态获取当前显示的webview
                    val currentWebView = tabManager.getCurrentTab()?.webView
                    // 检查是否是当前显示的webview
                    val isCurrentWebView = view == currentWebView
                    if (isCurrentWebView) {
                        val scrollDelta = scrollY - oldScrollY
                        handleNormalWebViewScroll(scrollY, scrollDelta, view as android.webkit.WebView)
                    }
                }
            }
            
            // 为所有标签页的webview设置监听器
            tabManager.getTabs().forEach { tab ->
                tab.webView.setOnScrollChangeListener(normalScrollListener)
            }
            
            // 重置滚动状态（针对当前webview）
            val currentWebView = tabManager.getCurrentTab()?.webView
            currentWebView?.let {
                normalLastScrollY = it.scrollY
                normalLastScrollDirection = 0
                normalConsecutiveScrollCount = 0
                // 只有在页面顶部时才重置永久隐藏标志，避免在用户滚动过程中重置
                val canScrollUp = it.canScrollVertically(-1)
                val isAtTop = !canScrollUp && it.scrollY <= 5
                if (isAtTop) {
                    isUIPermanentlyHidden = false
                }
                // 如果不在顶部且UI已被隐藏，保持永久隐藏标志
            }
            
            val currentPosition = tabManager.getTabs().indexOfFirst { it.webView == currentWebView }
            Log.d(TAG, "已为所有webview设置普通滚动监听（当前标签页: $currentPosition）")
        }
    }
    
    /**
     * 移除普通webview滚动监听
     */
    private fun removeNormalWebViewScrollListener() {
        // 移除所有webview的滚动监听
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            tabManager.getTabs().forEach { tab ->
                tab.webView.setOnScrollChangeListener(null)
            }
        }
        
        normalScrollListener = null
        normalScrollHandler = null
        
        // 重置滚动状态
        normalLastScrollY = 0
        normalLastScrollDirection = 0
        normalConsecutiveScrollCount = 0
        isUIPermanentlyHidden = false // 重置永久隐藏标志
        
        Log.d(TAG, "已移除普通webview滚动监听")
    }
    
    /**
     * 处理普通webview滚动事件（非阅读模式）
     * 优化：一旦向下滚动隐藏了UI，继续向下滚动时不再显示UI，只有在滚动到顶部时才显示
     */
    private fun handleNormalWebViewScroll(scrollY: Int, scrollDelta: Int, webView: android.webkit.WebView) {
        // 如果不在普通模式（在阅读模式），直接返回
        if (readerModeManager.isReaderModeActive()) {
            return
        }
        
        // 判断是否在顶部（使用更严格的判断，避免误判）
        // 使用 canScrollVertically 来准确判断是否在顶部
        val canScrollUp = webView.canScrollVertically(-1)
        val isAtTop = !canScrollUp && scrollY <= 5 // 严格判断：不能向上滚动且scrollY接近0
        
        // 如果在顶部，显示UI并清除永久隐藏标志（但只在真正在顶部时）
        if (isAtTop) {
            isUIPermanentlyHidden = false // 清除永久隐藏标志
            if (!isAddressBarVisible) {
                showAddressBarAndTabs(true)
            }
            normalLastScrollY = scrollY
            normalLastScrollDirection = 0
            normalConsecutiveScrollCount = 0
            return
        }
        
        // 如果UI已被永久隐藏，且不在顶部，直接返回，不处理任何显示逻辑
        if (isUIPermanentlyHidden) {
            normalLastScrollY = scrollY
            return
        }
        
        // 注意：移除了底部显示UI的逻辑，一旦向下滚动隐藏了UI，即使滚动到底部也不会再显示
        
        // 根据滚动方向处理UI显示/隐藏
        val absDelta = Math.abs(scrollDelta)
        if (absDelta > scrollThreshold) {
            val currentDirection = if (scrollDelta > 0) 1 else -1
            
            // 如果方向改变，重置计数
            if (currentDirection != normalLastScrollDirection) {
                normalConsecutiveScrollCount = 0
                normalLastScrollDirection = currentDirection
            } else {
                normalConsecutiveScrollCount++
            }
            
            // 只有在连续同方向滚动至少1次时才切换UI（减少抖动）
            if (normalConsecutiveScrollCount >= 1) {
                if (currentDirection > 0) {
                    // 向下滚动，隐藏UI并设置永久隐藏标志
                    if (isAddressBarVisible) {
                        showAddressBarAndTabs(false)
                        isUIPermanentlyHidden = true // 设置永久隐藏标志
                    }
                } else {
                    // 向上滚动，显示UI（但不会清除永久隐藏标志，因为只有滚动到顶部才会清除）
                    // 注意：这里不显示UI，因为一旦向下滚动隐藏了UI，只有滚动到顶部才显示
                    // 这样可以避免在继续向下滚动时因为轻微的向上滚动而显示UI
                }
            }
        }
        
        normalLastScrollY = scrollY
    }
    
    /**
     * 处理阅读模式滚动事件（由JavaScript回调触发）
     * 优化：添加滚动方向记忆和防抖逻辑，减少频繁切换，确保流畅响应
     * 修复：添加边界区域状态记忆，避免在边界附近频繁切换UI
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
            // 判断是否在顶部边界区域（仅顶部，不包括底部）
            val currentlyInBoundaryZone = isAtTop
            
            // 如果进入顶部边界区域，设置标志并显示UI，清除永久隐藏标志
            if (currentlyInBoundaryZone) {
                isReaderModeUIPermanentlyHidden = false // 清除永久隐藏标志
                if (!isInBoundaryZone) {
                    // 刚进入顶部边界区域，显示UI
                    isInBoundaryZone = true
                    if (!isAddressBarVisible) {
                        showAddressBarAndTabs(true)
                    }
                    // 重置滚动方向记忆
                    lastScrollDirection = 0
                    consecutiveScrollCount = 0
                } else {
                    // 已经在顶部边界区域内，保持UI显示状态
                    if (!isAddressBarVisible) {
                        showAddressBarAndTabs(true)
                    }
                }
                lastScrollTop = scrollTop
                return@Runnable // 在顶部边界区域内，直接返回，不执行滚动方向判断
            }
            
            // 如果UI已被永久隐藏，且不在顶部，直接返回，不处理任何显示逻辑
            // 注意：必须在边界区域检查之前检查永久隐藏标志，避免边界区域逻辑重新显示UI
            if (isReaderModeUIPermanentlyHidden) {
                // 如果UI已被永久隐藏，清除边界区域标志，避免后续逻辑干扰
                isInBoundaryZone = false
                lastScrollTop = scrollTop
                return@Runnable
            }
            
            // 注意：移除了底部边界区域的逻辑，一旦向下滚动隐藏了UI，即使滚动到底部也不会再显示
            
            // 如果之前在边界区域内，需要判断是否真正离开边界区域
            // 注意：这个逻辑只在UI未被永久隐藏时执行
            if (isInBoundaryZone) {
                // 计算从上次边界位置滚动的距离
                val distanceFromBoundary = Math.abs(scrollTop - lastScrollTop)
                
                // 只有当滚动距离超过阈值时，才认为真正离开边界区域
                if (distanceFromBoundary < boundaryZoneThreshold) {
                    // 还在边界区域附近，保持UI显示，不执行滚动方向判断
                    // 注意：这里显示UI是合理的，因为只有在顶部附近才会进入这个逻辑
                    if (!isAddressBarVisible) {
                        showAddressBarAndTabs(true)
                    }
                    return@Runnable
                } else {
                    // 真正离开边界区域，恢复正常滚动判断
                    isInBoundaryZone = false
                    lastScrollDirection = 0
                    consecutiveScrollCount = 0
                }
            }
            
            // 只有在非边界区域时，才根据滚动方向处理UI显示/隐藏
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
                        // 向下滚动，隐藏UI并设置永久隐藏标志
                        if (isAddressBarVisible) {
                            showAddressBarAndTabs(false)
                            isReaderModeUIPermanentlyHidden = true // 设置永久隐藏标志
                        }
                    } else {
                        // 向上滚动，不显示UI（但不会清除永久隐藏标志，因为只有滚动到顶部才会清除）
                        // 注意：这里不显示UI，因为一旦向下滚动隐藏了UI，只有滚动到顶部才显示
                        // 这样可以避免在继续向下滚动时因为轻微的向上滚动而显示UI
                    }
                }
            }
            
            lastScrollTop = scrollTop
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
