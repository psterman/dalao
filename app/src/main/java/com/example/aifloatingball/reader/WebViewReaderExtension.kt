package com.example.aifloatingball.reader

import android.content.Context
import android.view.ViewGroup
import com.example.aifloatingball.webview.PaperStackWebViewManager

/**
 * WebView阅读模式扩展
 */
object WebViewReaderExtension {
    
    private var novelReaderUI: NovelReaderUI? = null
    private val readerModeStates = mutableMapOf<String, Boolean>() // tabId -> isReaderMode
    
    /**
     * 初始化阅读模式
     * @param manager WebView管理器
     * @param container WebView容器
     */
    fun init(manager: PaperStackWebViewManager, container: ViewGroup) {
        // 每次初始化都重新创建UI，确保绑定到当前的container
        novelReaderUI = NovelReaderUI(manager.context, container)
        novelReaderUI?.hide() // 默认隐藏
        
        // 监听标签页切换
        manager.addOnTabSwitchedListener { tab, _ ->
            val isReaderMode = readerModeStates[tab.id] ?: false
            if (isReaderMode) {
                novelReaderUI?.show()
                // 恢复该标签页的阅读进度/内容 (如果需要)
                // 这里简单处理：如果是阅读模式，重新进入（会重新解析）
                // 理想情况下应该缓存解析结果
                NovelReaderManager.getInstance(manager.context).enterReaderMode(tab.webView)
            } else {
                novelReaderUI?.hide()
                NovelReaderManager.getInstance(manager.context).exitReaderMode()
            }
        }
        
        // 监听页面加载完成
        manager.addOnPageFinishedListener { tab, url ->
            val isReaderMode = readerModeStates[tab.id] ?: false
            if (isReaderMode) {
                NovelReaderManager.getInstance(manager.context).onPageFinished(url ?: "")
            }
        }
        
        // 监听阅读模式状态变化
        NovelReaderManager.getInstance(manager.context).setListener(object : NovelReaderManager.ReaderModeListener {
            override fun onReaderModeStateChanged(isActive: Boolean) {
                val currentTab = manager.getCurrentTab() ?: return
                readerModeStates[currentTab.id] = isActive
                if (isActive) {
                    novelReaderUI?.show()
                } else {
                    novelReaderUI?.hide()
                }
            }

            override fun onChapterLoaded(title: String, content: String, hasNext: Boolean, hasPrev: Boolean, isAppend: Boolean) {
                novelReaderUI?.onChapterLoaded(title, content, hasNext, hasPrev, isAppend)
            }

            override fun onChapterLoadFailed(error: String) {
                novelReaderUI?.onChapterLoadFailed(error)
            }

            override fun onCatalogLoaded(catalog: List<NovelReaderManager.CatalogItem>) {
                novelReaderUI?.onCatalogLoaded(catalog)
            }

            override fun onCatalogLoadFailed(error: String) {
                novelReaderUI?.onCatalogLoadFailed(error)
            }

            override fun onCatalogPageDetected(catalog: List<NovelReaderManager.CatalogItem>) {
                novelReaderUI?.onCatalogPageDetected(catalog)
            }
        })
    }
    
    /**
     * 检查并进入阅读模式
     */
    fun checkAndEnterReaderMode(manager: PaperStackWebViewManager) {
        val currentTab = manager.getCurrentTab() ?: return
        val webView = currentTab.webView
        val url = currentTab.url
        val title = currentTab.title
        
        // 如果已经是阅读模式，不需要重新检测
        if (readerModeStates[currentTab.id] == true) return
        
        NovelReaderManager.getInstance(webView.context).detectNovelPage(webView, url, title) { isNovel ->
            if (isNovel) {
                webView.post {
                     // 自动进入阅读模式
                     NovelReaderManager.getInstance(webView.context).enterReaderMode(webView)
                }
            }
        }
    }
}
