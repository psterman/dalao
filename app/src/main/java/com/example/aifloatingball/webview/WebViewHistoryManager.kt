package com.example.aifloatingball.webview

import android.util.Log

/**
 * WebView历史记录管理器
 * 用于管理WebView的浏览历史，解决特殊页面无法返回的问题
 */
class WebViewHistoryManager {
    
    companion object {
        private const val TAG = "WebViewHistoryManager"
        private const val MAX_HISTORY_SIZE = 50
    }
    
    private val historyStack = mutableListOf<String>()
    private var currentIndex = -1
    
    /**
     * 添加到历史记录
     */
    fun addToHistory(url: String) {
        if (url.isNotEmpty() && url != "about:blank" && !url.startsWith("javascript:")) {
            // 避免重复添加相同URL
            if (historyStack.isEmpty() || historyStack.last() != url) {
                historyStack.add(url)
                currentIndex = historyStack.size - 1
                
                // 限制历史记录大小
                if (historyStack.size > MAX_HISTORY_SIZE) {
                    historyStack.removeAt(0)
                    currentIndex--
                }
                
                Log.d(TAG, "添加到历史记录: $url, 当前历史记录数: ${historyStack.size}")
            }
        }
    }
    
    /**
     * 是否可以返回
     */
    fun canGoBack(): Boolean {
        return currentIndex > 0
    }
    
    /**
     * 返回上一页
     */
    fun goBack(): String? {
        return if (canGoBack()) {
            currentIndex--
            val url = historyStack[currentIndex]
            Log.d(TAG, "返回上一页: $url")
            url
        } else {
            Log.d(TAG, "无法返回，历史记录为空或已在第一页")
            null
        }
    }
    
    /**
     * 是否可以前进
     */
    fun canGoForward(): Boolean {
        return currentIndex < historyStack.size - 1
    }
    
    /**
     * 前进下一页
     */
    fun goForward(): String? {
        return if (canGoForward()) {
            currentIndex++
            val url = historyStack[currentIndex]
            Log.d(TAG, "前进下一页: $url")
            url
        } else {
            Log.d(TAG, "无法前进，已在最后一页")
            null
        }
    }
    
    /**
     * 获取当前URL
     */
    fun getCurrentUrl(): String? {
        return if (currentIndex >= 0 && currentIndex < historyStack.size) {
            historyStack[currentIndex]
        } else null
    }
    
    /**
     * 获取历史记录数量
     */
    fun getHistorySize(): Int = historyStack.size
    
    /**
     * 清除历史记录
     */
    fun clearHistory() {
        historyStack.clear()
        currentIndex = -1
        Log.d(TAG, "清除历史记录")
    }
    
    /**
     * 获取历史记录列表（用于调试）
     */
    fun getHistoryList(): List<String> = historyStack.toList()
    
    /**
     * 检查URL是否为特殊页面
     */
    fun isSpecialPage(url: String?): Boolean {
        if (url == null) return false
        
        return when {
            // 搜索引擎页面
            url.contains("google.com/search") -> true
            url.contains("baidu.com/s?") -> true
            url.contains("bing.com/search") -> true
            url.contains("sogou.com/web?") -> true
            
            // 社交媒体页面
            url.contains("weibo.com") -> true
            url.contains("douyin.com") -> true
            url.contains("xiaohongshu.com") -> true
            url.contains("zhihu.com") -> true
            
            // 电商页面
            url.contains("taobao.com") -> true
            url.contains("tmall.com") -> true
            url.contains("jd.com") -> true
            url.contains("pinduoduo.com") -> true
            
            // 视频网站
            url.contains("youtube.com") -> true
            url.contains("bilibili.com") -> true
            url.contains("iqiyi.com") -> true
            url.contains("youku.com") -> true
            
            // 新闻网站
            url.contains("news.") -> true
            url.contains("sina.com.cn") -> true
            url.contains("sohu.com") -> true
            
            // 技术网站
            url.contains("github.com") -> true
            url.contains("stackoverflow.com") -> true
            url.contains("csdn.net") -> true
            
            else -> false
        }
    }
    
    /**
     * 获取特殊页面的返回策略
     */
    fun getSpecialPageBackStrategy(url: String): String {
        return when {
            // 搜索引擎页面
            url.contains("google.com/search") -> "google_search_back"
            url.contains("baidu.com/s?") -> "baidu_search_back"
            url.contains("bing.com/search") -> "bing_search_back"
            
            // 社交媒体页面
            url.contains("weibo.com") -> "weibo_back"
            url.contains("douyin.com") -> "douyin_back"
            url.contains("xiaohongshu.com") -> "xiaohongshu_back"
            
            // 电商页面
            url.contains("taobao.com") -> "taobao_back"
            url.contains("jd.com") -> "jd_back"
            
            // 视频网站
            url.contains("youtube.com") -> "youtube_back"
            url.contains("bilibili.com") -> "bilibili_back"
            
            // 默认策略
            else -> "default_back"
        }
    }
}
