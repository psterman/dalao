package com.example.aifloatingball.tab

import android.content.Context
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebSettings
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.aifloatingball.adblock.AdBlockFilter
import com.example.aifloatingball.web.EnhancedWebViewClient
import android.content.Context.MODE_PRIVATE
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.example.aifloatingball.utils.WebViewConstants

/**
 * 增强的标签页管理器，支持AdBlock和多tab功能
 */
class EnhancedTabManager(
    private val context: Context,
    private val adBlockFilter: AdBlockFilter,
    private val onPageLoadListener: EnhancedWebViewClient.PageLoadListener? = null,
    private val onUrlChangeListener: EnhancedWebViewClient.UrlChangeListener? = null
) {
    companion object {
        private const val TAG = "EnhancedTabManager"
        private const val MAX_TABS = 10
    }
    
    private val _tabs = MutableStateFlow<List<EnhancedWebViewTab>>(emptyList())
    val tabs: StateFlow<List<EnhancedWebViewTab>> = _tabs
    
    private var viewPager: ViewPager2? = null
    private var tabLayout: TabLayout? = null
    private var tabRecyclerView: RecyclerView? = null
    private var currentPosition = 0
    
    private val sharedPreferences by lazy {
        context.getSharedPreferences("tab_manager_state", MODE_PRIVATE)
    }
    
    private lateinit var tabPagerAdapter: EnhancedTabPagerAdapter
    private lateinit var tabOverviewAdapter: EnhancedTabOverviewAdapter
    
    fun initialize(
        viewPager: ViewPager2,
        tabLayout: TabLayout,
        tabRecyclerView: RecyclerView
    ) {
        this.viewPager = viewPager
        this.tabLayout = tabLayout
        this.tabRecyclerView = tabRecyclerView
        
        // 初始化适配器
        tabPagerAdapter = EnhancedTabPagerAdapter(this)
        tabOverviewAdapter = EnhancedTabOverviewAdapter(this) { position ->
            switchTab(position)
        }
        
        // 设置ViewPager
        viewPager.apply {
            adapter = tabPagerAdapter
            offscreenPageLimit = 2
            
            // 监听页面切换
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    currentPosition = position
                    updateTabLayout()
                    Log.d(TAG, "Switched to tab $position")
                }
            })
        }
        
        // 设置TabLayout
        setupTabLayout()
        
        // 设置标签页概览RecyclerView
        tabRecyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = tabOverviewAdapter
        }
    }
    
    private fun setupTabLayout() {
        tabLayout?.apply {
            // 清除现有标签
            removeAllTabs()
            
            // 添加标签页切换监听器
            addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    tab?.position?.let { position ->
                        if (position != currentPosition) {
                            switchTab(position)
                        }
                    }
                }
                
                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })
        }
    }
    
    fun addTab(url: String? = null, loadInBackground: Boolean = false): Boolean {
        if (_tabs.value.size >= MAX_TABS) {
            Log.w(TAG, "Maximum number of tabs reached")
            return false
        }
        
        val newTab = EnhancedWebViewTab(
            id = System.currentTimeMillis(),
            webView = createWebView(),
            url = url,
            title = url ?: "新标签页"
        )
        
        val currentTabs = _tabs.value.toMutableList()
        currentTabs.add(newTab)
        _tabs.value = currentTabs
        
        // 更新UI
        updateAdapters()
        updateTabLayout()
        
        // 如果不是后台加载，切换到新标签页
        if (!loadInBackground) {
            switchTab(currentTabs.size - 1)
        }
        
        // 加载URL
        url?.let { newTab.webView.loadUrl(it) }
        
        Log.d(TAG, "Added new tab: ${newTab.id}")
        return true
    }
    
    fun removeTab(position: Int): Boolean {
        val currentTabs = _tabs.value
        if (position < 0 || position >= currentTabs.size) {
            Log.w(TAG, "Invalid tab position: $position")
            return false
        }
        
        val tabToRemove = currentTabs[position]
        
        // 清理WebView
        try {
            tabToRemove.webView.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying WebView", e)
        }
        
        val newTabs = currentTabs.toMutableList()
        newTabs.removeAt(position)
        _tabs.value = newTabs
        
        // 更新UI
        updateAdapters()
        updateTabLayout()
        
        // 如果没有标签页了，添加一个新的
        if (newTabs.isEmpty()) {
            addTab()
        } else {
            // 调整当前位置
            if (currentPosition >= newTabs.size) {
                currentPosition = newTabs.size - 1
            }
            switchTab(currentPosition)
        }
        
        Log.d(TAG, "Removed tab at position: $position")
        return true
    }
    
    fun closeCurrentTab(): Boolean {
        return removeTab(currentPosition)
    }
    
    fun switchTab(position: Int) {
        val currentTabs = _tabs.value
        if (position < 0 || position >= currentTabs.size) {
            Log.w(TAG, "Invalid tab position for switch: $position")
            return
        }
        
        currentPosition = position
        viewPager?.currentItem = position
        updateTabLayout()
        
        Log.d(TAG, "Switched to tab: $position")
    }
    
    fun getCurrentTab(): EnhancedWebViewTab? {
        return _tabs.value.getOrNull(currentPosition)
    }
    
    fun getTabCount(): Int = _tabs.value.size

    fun getTabs(): List<EnhancedWebViewTab> = _tabs.value

    fun updateTabTitle(title: String) {
        val currentTab = getCurrentTab()
        currentTab?.let {
            it.title = title
            updateTabLayout()
        }
    }
    
    fun canGoBack(): Boolean {
        return getCurrentTab()?.webView?.canGoBack() ?: false
    }
    
    fun goBack(): Boolean {
        val currentTab = getCurrentTab()
        return if (currentTab?.webView?.canGoBack() == true) {
            currentTab.webView.goBack()
            true
        } else {
            false
        }
    }
    
    fun canGoForward(): Boolean {
        return getCurrentTab()?.webView?.canGoForward() ?: false
    }
    
    fun goForward(): Boolean {
        val currentTab = getCurrentTab()
        return if (currentTab?.webView?.canGoForward() == true) {
            currentTab.webView.goForward()
            true
        } else {
            false
        }
    }
    
    private fun createWebView(): WebView {
        return WebView(context).apply {
            // 基本设置
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            // 启用硬件加速
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            
            // WebView设置
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                setSupportMultipleWindows(true)
                
                // 性能优化
                setRenderPriority(WebSettings.RenderPriority.HIGH)
                cacheMode = WebSettings.LOAD_DEFAULT
                allowContentAccess = true
                allowFileAccess = true
                databaseEnabled = true
                
                // 安全设置
                allowUniversalAccessFromFileURLs = false
                allowFileAccessFromFileURLs = false
                
                // 用户代理 - 使用最新的移动版Chrome User-Agent
                userAgentString = WebViewConstants.MOBILE_USER_AGENT
                
                // 移动端优化设置
                textZoom = 100
                minimumFontSize = 8
                setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING)
            }
            
            // 设置WebViewClient
            webViewClient = EnhancedWebViewClient(
                adBlockFilter = adBlockFilter,
                onPageLoadListener = onPageLoadListener,
                onUrlChangeListener = onUrlChangeListener
            )
            
            // 设置WebChromeClient
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    onPageLoadListener?.onProgressChanged(view, newProgress)
                }
                
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    title?.let { updateTabTitle(it) }
                }
            }
        }
    }
    
    private fun updateAdapters() {
        tabPagerAdapter.notifyDataSetChanged()
        tabOverviewAdapter.notifyDataSetChanged()
    }
    
    private fun updateTabLayout() {
        tabLayout?.let { layout ->
            // 清除现有标签
            layout.removeAllTabs()
            
            // 添加新标签
            _tabs.value.forEachIndexed { index, tab ->
                val tabItem = layout.newTab()
                tabItem.text = if (tab.title.length > 15) {
                    "${tab.title.take(12)}..."
                } else {
                    tab.title
                }
                layout.addTab(tabItem)
            }
            
            // 选择当前标签
            if (currentPosition < layout.tabCount) {
                layout.getTabAt(currentPosition)?.select()
            }
        }
    }
    
    fun cleanup() {
        _tabs.value.forEach { tab ->
            try {
                tab.webView.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying WebView during cleanup", e)
            }
        }
        _tabs.value = emptyList()
    }

    fun saveTabsState() {
        val urls = _tabs.value.mapNotNull { it.url }.toSet()
        sharedPreferences.edit().putStringSet("open_tabs", urls).apply()
        Log.d(TAG, "Saved tabs state with URLs: $urls")
    }

    fun restoreTabsState() {
        val urls = sharedPreferences.getStringSet("open_tabs", emptySet()) ?: emptySet()
        if (urls.isNotEmpty()) {
            _tabs.value = emptyList() // 清除默认创建的空标签页
            urls.forEach { url ->
                addTab(url, loadInBackground = true)
            }
            if (_tabs.value.isNotEmpty()) {
                switchTab(0)
            }
            Log.d(TAG, "Restored tabs state with URLs: $urls")
        }
    }
}

/**
 * 增强的WebView标签页数据类
 */
data class EnhancedWebViewTab(
    val id: Long,
    val webView: WebView,
    var url: String? = null,
    var title: String = "新标签页",
    var favicon: String? = null,
    var isLoading: Boolean = false
)
