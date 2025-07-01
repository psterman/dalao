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
        // Create a key-to-engine mapping for better lookup
        val engine = findAIEngineByKey(engineKey)
        return engine?.getSearchUrl(query) ?: "https://chat.openai.com"
    }
    
    /**
     * 根据引擎键值查找AI搜索引擎（支持多种格式）
     */
    private fun findAIEngineByKey(engineKey: String): AISearchEngine? {
        // First try exact name match
        val byName = AISearchEngine.DEFAULT_AI_ENGINES.find { it.name.equals(engineKey, ignoreCase = true) }
        if (byName != null) return byName
        
        // Then try key-based mapping
        val keyToNameMap = mapOf(
            "chatgpt" to "ChatGPT",
            "claude" to "Claude", 
            "gemini" to "Gemini",
            "wenxin" to "文心一言",
            "chatglm" to "智谱清言",
            "qianwen" to "通义千问",
            "xinghuo" to "讯飞星火",
            "perplexity" to "Perplexity",
            "phind" to "Phind",
            "poe" to "Poe",
            "tiangong" to "天工AI",
            "metaso" to "秘塔AI搜索",
            "quark" to "夸克AI",
            "360ai" to "360AI搜索",
            "baiduai" to "百度AI",
            "you" to "You.com",
            "brave" to "Brave Search",
            "wolfram" to "WolframAlpha",
            "chatgpt_chat" to "ChatGPT (API)",
            "deepseek_chat" to "DeepSeek (API)",
            "kimi" to "Kimi",
            "deepseek" to "DeepSeek (Web)",
            "wanzhi" to "万知",
            "baixiaoying" to "百小应",
            "yuewen" to "跃问",
            "doubao" to "豆包",
            "cici" to "Cici",
            "hailuo" to "海螺",
            "groq" to "Groq",
            "yuanbao" to "腾讯元宝"
        )
        
        val mappedName = keyToNameMap[engineKey.lowercase()]
        return if (mappedName != null) {
            AISearchEngine.DEFAULT_AI_ENGINES.find { it.name == mappedName }
        } else {
            null
        }
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