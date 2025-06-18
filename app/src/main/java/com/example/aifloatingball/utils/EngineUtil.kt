package com.example.aifloatingball.utils

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import com.example.aifloatingball.R
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.model.BaseSearchEngine
import com.example.aifloatingball.model.SearchEngine

/**
 * 搜索引擎工具类
 * 用于处理搜索引擎相关的公共逻辑
 */
object EngineUtil {
    
    /**
     * 获取搜索引擎名称
     */
    fun getSearchEngineName(engineKey: String, isAI: Boolean): String {
        return if (isAI) {
            AISearchEngine.DEFAULT_AI_ENGINES.find { getEngineKey(it.name) == engineKey }?.name ?: "AI搜索"
        } else {
            // 省略了很长的 when 语句，保持原有逻辑
            SearchEngine.DEFAULT_ENGINES.find { it.name == engineKey }?.displayName ?: "搜索"
        }
    }
    
    /**
     * 获取搜索引擎键名
     */
    fun getEngineKey(engineName: String): String {
        return engineName.lowercase().replace(" ", "_")
    }
    
    /**
     * 获取搜索引擎首页URL
     */
    fun getSearchEngineHomeUrl(engineKey: String): String {
        return SearchEngine.DEFAULT_ENGINES.find { it.name == engineKey }?.url ?: ""
    }
    
    /**
     * 获取AI搜索引擎首页URL
     */
    fun getAISearchEngineHomeUrl(engineKey: String): String {
        return AISearchEngine.DEFAULT_AI_ENGINES.find { it.name == engineKey }?.url ?: ""
    }
    
    /**
     * 获取搜索引擎搜索URL
     */
    fun getSearchEngineSearchUrl(engineKey: String, query: String): String {
        val engine = SearchEngine.DEFAULT_ENGINES.find { it.name == engineKey }
        return engine?.getSearchUrl(query) ?: ""
    }
    
    /**
     * 获取AI搜索引擎搜索URL
     */
    fun getAISearchEngineUrl(engineKey: String, query: String): String {
        val engine = AISearchEngine.DEFAULT_AI_ENGINES.find { it.name == engineKey }
        return engine?.getSearchUrl(query) ?: "https://chat.openai.com"
    }
    
    /**
     * 获取谷歌Favicon服务的图标URL
     */
    private fun getFaviconUrl(domain: String, size: Int = 32): String {
        return "https://www.google.com/s2/favicons?domain=$domain&sz=$size"
    }
    
    /**
     * 获取搜索引擎图标URL
     */
    fun getEngineIconUrl(engineKey: String, size: Int = 32): String {
        val domain = SearchEngine.DEFAULT_ENGINES.find { it.name == engineKey }?.url
            ?: return getFaviconUrl("google.com", size)
        return getFaviconUrl(extractDomain(domain), size)
    }

    /**
     * [新方法] 获取AI搜索引擎图标URL
     */
    fun getAIEngineIconUrl(engineKey: String, size: Int = 32): String {
        val url = AISearchEngine.DEFAULT_AI_ENGINES.find { it.name == engineKey }?.url
            ?: return getFaviconUrl("openai.com", size)
        return getFaviconUrl(extractDomain(url), size)
    }

    private fun extractDomain(url: String): String {
        return try {
            val host = java.net.URL(url).host
            // 简单的移除 "www."
            host.removePrefix("www.")
        } catch (e: Exception) {
            // 如果URL解析失败，返回一个默认值
            "google.com"
        }
    }

    /**
     * [新方法] 获取普通搜索引擎的占位图资源ID
     */
    fun getPlaceholder(engineKey: String): Int {
        return SearchEngine.DEFAULT_ENGINES.find { it.name == engineKey }?.iconResId ?: R.drawable.ic_search
    }

    /**
     * [新方法] 获取AI搜索引擎的占位图Drawable
     */
    fun getAIEnginePlaceholder(engineKey: String, context: Context): Drawable? {
        val resId = AISearchEngine.DEFAULT_AI_ENGINES.find { it.name == engineKey }?.iconResId ?: R.drawable.ic_search
        return ContextCompat.getDrawable(context, resId)
    }

    /**
     * 根据域名获取对应的图标资源ID (重新添加)
     */
    fun getIconResourceByDomain(domain: String): Int {
        return when {
            domain.contains("baidu.com") -> R.drawable.ic_baidu
            domain.contains("google.com") || domain.contains("gemini.google.com") -> R.drawable.ic_google
            domain.contains("bing.com") -> R.drawable.ic_bing
            domain.contains("sogou.com") -> R.drawable.ic_sogou
            domain.contains("so.com") -> R.drawable.ic_360
            domain.contains("sm.cn") -> R.drawable.ic_quark
            domain.contains("toutiao.com") -> R.drawable.ic_toutiao
            domain.contains("zhihu.com") -> R.drawable.ic_zhihu
            domain.contains("bilibili.com") -> R.drawable.ic_bilibili
            domain.contains("douban.com") -> R.drawable.ic_douban
            domain.contains("weibo.com") -> R.drawable.ic_weibo
            domain.contains("taobao.com") -> R.drawable.ic_taobao
            domain.contains("jd.com") -> R.drawable.ic_jd
            domain.contains("douyin.com") -> R.drawable.ic_douyin
            domain.contains("xiaohongshu.com") -> R.drawable.ic_xiaohongshu
            domain.contains("openai.com") || domain.contains("chatgpt.com") -> R.drawable.ic_chatgpt
            domain.contains("claude.ai") -> R.drawable.ic_claude
            domain.contains("zhipuai.cn") -> R.drawable.ic_chatglm 
            domain.contains("deepseek.com") -> R.drawable.ic_deepseek
            else -> R.drawable.ic_search
        }
    }
} 