package com.example.aifloatingball.tab

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.viewpager2.widget.ViewPager2
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.example.aifloatingball.adblock.AdBlockFilter
import com.example.aifloatingball.web.EnhancedWebViewClient
import com.google.android.material.tabs.TabLayout

class TabManager(private val context: Context) {
    private val _tabs = MutableStateFlow<List<WebViewTab>>(emptyList())
    val tabs: StateFlow<List<WebViewTab>> = _tabs
    
    private var viewPager: ViewPager2? = null
    private var tabPreviewList: RecyclerView? = null
    private var currentPosition = 0
    
    fun initialize(viewPager: ViewPager2, tabPreviewList: RecyclerView) {
        this.viewPager = viewPager
        this.tabPreviewList = tabPreviewList
        
        // 设置ViewPager触摸事件处理
        viewPager.apply {
            // 启用ViewPager2的用户输入，通过智能触摸处理器管理冲突
            isUserInputEnabled = true
            
            // 设置页面转换动画
            setPageTransformer { page, position ->
                page.alpha = 1f
            }
            
            // 预加载相邻页面
            offscreenPageLimit = 1
            
            // 设置适配器
            adapter = TabPagerAdapter(context)
        }
        
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
    
    fun addTab(url: String? = null, loadInBackground: Boolean = false) {
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
        
        // 如果不是后台加载，切换到新标签页
        if (!loadInBackground) {
            switchTab(currentTabs.size - 1)
        }
        
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
            // 确保WebView可以接收触摸事件
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            
            // 设置WebView的布局参数
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            // 启用硬件加速
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                
                // 启用更多设置以提高性能和用户体验
                setRenderPriority(WebSettings.RenderPriority.HIGH)
                cacheMode = WebSettings.LOAD_DEFAULT
                allowContentAccess = true
                allowFileAccess = true
                databaseEnabled = true
                
                // 设置移动版User-Agent，确保网页加载移动版
                userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36"
                
                // 移动端优化设置
                textZoom = 100
                minimumFontSize = 8
                setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING)
            }
            
            // 设置WebViewClient以处理页面加载
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // 页面加载完成后确保WebView可以接收触摸事件
                    view?.isClickable = true
                    view?.isFocusable = true
                    
                    // 注入viewport meta标签确保移动版显示
                    view?.evaluateJavascript("""
                        (function() {
                            try {
                                // 检查是否已有viewport meta标签
                                var viewportMeta = document.querySelector('meta[name="viewport"]');
                                if (viewportMeta) {
                                    // 更新现有的viewport设置
                                    viewportMeta.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes');
                                } else {
                                    // 创建新的viewport meta标签
                                    var meta = document.createElement('meta');
                                    meta.name = 'viewport';
                                    meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes';
                                    document.head.appendChild(meta);
                                }
                                
                                // 确保页面使用移动端样式
                                document.documentElement.style.setProperty('--mobile-viewport', '1');
                                
                                console.log('Viewport meta tag injected for mobile display');
                            } catch (e) {
                                console.error('Failed to inject viewport meta tag:', e);
                            }
                        })();
                    """.trimIndent(), null)
                }
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