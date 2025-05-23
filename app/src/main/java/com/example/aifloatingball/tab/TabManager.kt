package com.example.aifloatingball.tab

import android.content.Context
import android.view.View
import android.webkit.WebView
import androidx.viewpager2.widget.ViewPager2
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class TabManager(private val context: Context) {
    private val _tabs = MutableStateFlow<List<WebViewTab>>(emptyList())
    val tabs: StateFlow<List<WebViewTab>> = _tabs
    
    private var viewPager: ViewPager2? = null
    private var tabPreviewList: RecyclerView? = null
    private var currentPosition = 0
    
    fun initialize(viewPager: ViewPager2, tabPreviewList: RecyclerView) {
        this.viewPager = viewPager
        this.tabPreviewList = tabPreviewList
        
        // 设置ViewPager适配器
        viewPager.adapter = TabPagerAdapter(context)
        
        // 设置标签预览列表适配器
        tabPreviewList.adapter = TabPreviewAdapter { position ->
            switchTab(position)
        }
        
        // 监听页面切换
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                currentPosition = position
                updateTabPreview()
            }
        })
    }
    
    fun addTab(url: String? = null) {
        val newTab = WebViewTab(
            id = System.currentTimeMillis(),
            webView = createWebView(),
            url = url,
            title = url ?: "新标签页"
        )
        
        val currentTabs = _tabs.value.toMutableList()
        currentTabs.add(newTab)
        _tabs.value = currentTabs
        
        // 更新适配器
        viewPager?.adapter?.notifyItemInserted(currentTabs.size - 1)
        tabPreviewList?.adapter?.notifyItemInserted(currentTabs.size - 1)
        
        // 切换到新标签页
        switchTab(currentTabs.size - 1)
        
        // 加载URL
        url?.let { newTab.webView.loadUrl(it) }
    }
    
    fun removeTab(position: Int) {
        if (position < 0 || position >= _tabs.value.size) return
        
        val currentTabs = _tabs.value.toMutableList()
        currentTabs.removeAt(position)
        _tabs.value = currentTabs
        
        // 更新适配器
        viewPager?.adapter?.notifyItemRemoved(position)
        tabPreviewList?.adapter?.notifyItemRemoved(position)
        
        // 如果没有标签页了，添加一个新的
        if (currentTabs.isEmpty()) {
            addTab()
        } else {
            // 切换到相邻的标签页
            val newPosition = if (position == currentTabs.size) position - 1 else position
            switchTab(newPosition)
        }
    }
    
    fun switchTab(position: Int) {
        if (position < 0 || position >= _tabs.value.size) return
        
        viewPager?.currentItem = position
        currentPosition = position
        updateTabPreview()
    }
    
    fun getCurrentTab(): WebViewTab? {
        return _tabs.value.getOrNull(currentPosition)
    }
    
    private fun createWebView(): WebView {
        return WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }
        }
    }
    
    private fun updateTabPreview() {
        tabPreviewList?.adapter?.notifyDataSetChanged()
    }
    
    fun onBackPressed(): Boolean {
        val currentTab = getCurrentTab() ?: return false
        return if (currentTab.webView.canGoBack()) {
            currentTab.webView.goBack()
            true
        } else {
            false
        }
    }
}

data class WebViewTab(
    val id: Long,
    val webView: WebView,
    var url: String? = null,
    var title: String = "新标签页",
    var favicon: String? = null
) 