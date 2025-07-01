package com.example.aifloatingball.engine

import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.utils.EngineUtil
import android.util.Log

/**
 * 搜索引擎处理器，负责处理搜索引擎相关逻辑
 */
class SearchEngineHandler(private val settingsManager: SettingsManager) {

    companion object {
        const val DEFAULT_ENGINE_KEY = "baidu" // 此常量仅作为标识符，不再作为硬编码后备
        // 您也可以为AI引擎定义一个默认键，如果需要的话
        // const val DEFAULT_AI_ENGINE_KEY = "default_ai"
        private const val TAG = "SearchEngineHandler"
    }
    
    /**
     * 根据搜索查询和引擎键获取搜索URL
     */
    fun getSearchUrl(query: String, engineKey: String?): String {
        // 如果 engineKey 为 null，从设置中获取第一个窗口的默认引擎
        val currentEngineKey = engineKey ?: settingsManager.getSearchEngineForPosition(0)
        Log.d(TAG, "getSearchUrl called with query: '$query', engineKey: '$engineKey', using currentEngineKey: '$currentEngineKey'")

        // 处理空查询的情况
        val safeQuery = if (query.isBlank()) "" else query
        val encodedQuery = try {
            java.net.URLEncoder.encode(safeQuery, "UTF-8")
        } catch (e: Exception) {
            Log.e(TAG, "URL编码失败: $e")
            safeQuery // 如果编码失败则使用原始查询
        }

        // 尝试从EngineUtil获取URL
        // 1. 判断是否是AI引擎 - 支持多种格式的匹配
        val isAIEngine = isAIEngine(currentEngineKey)

        val searchUrl = if (isAIEngine) {
            // 2. 如果是AI引擎，调用AI引擎的URL获取方法
            EngineUtil.getAISearchEngineUrl(currentEngineKey, encodedQuery)
        } else {
            // 3. 否则，使用原有的标准引擎方法
            EngineUtil.getSearchEngineSearchUrl(currentEngineKey, encodedQuery)
        }
        
        // 确保返回的URL是有效的，否则使用基于设置的后备方案
        val finalUrl = if (searchUrl.isNotBlank() && searchUrl.startsWith("http")) {
            searchUrl
        } else {
            Log.w(TAG, "获取到无效的搜索URL: '$searchUrl' for key '$currentEngineKey', 使用后备方案")
            // 后备逻辑：总是使用用户为第一个窗口设置的默认引擎
            val fallbackEngineKey = settingsManager.getSearchEngineForPosition(0)
            EngineUtil.getSearchEngineSearchUrl(fallbackEngineKey, encodedQuery)
        }
        
        Log.d(TAG, "Generated search URL: $finalUrl")
        return finalUrl
    }
    
    /**
     * 获取默认搜索引擎的搜索URL
     * 这个方法现在主要在内部通过 DEFAULT_ENGINE_KEY 间接触发，或者作为最后的保障
     */
    private fun getDefaultSearchUrl(query: String): String {
        // 这里的逻辑可以保持，或者更直接地使用某个硬编码的默认引擎URL，如果 SearchEngine.DEFAULT_ENGINES 可能为空
        val defaultUrl = SearchEngine.DEFAULT_ENGINES.firstOrNull()?.getSearchUrl(query)
            ?: "https://www.google.com/search?q=$query" // 兜底URL
        Log.d(TAG, "Using default search URL: $defaultUrl for query: '$query'")
        return defaultUrl
    }
    
    /**
     * 判断是否是AI搜索引擎，支持多种格式匹配
     */
    private fun isAIEngine(engineKey: String): Boolean {
        // 支持的AI引擎标识符（包括name和key格式）
        val aiEngineIdentifiers = setOf(
            // Name format (from AISearchEngine.DEFAULT_AI_ENGINES)
            "chatgpt", "claude", "gemini", "文心一言", "智谱清言", "通义千问", "讯飞星火",
            "perplexity", "phind", "poe", "天工ai", "秘塔ai搜索", "夸克ai", "360ai搜索",
            "百度ai", "you.com", "brave search", "wolframalpha", "chatgpt (api)", 
            "deepseek (api)", "kimi", "deepseek (web)", "万知", "百小应", "跃问", 
            "豆包", "cici", "海螺", "groq", "腾讯元宝",
            // Key format (converted keys from SimpleModeService)
            "wenxin", "chatglm", "qianwen", "xinghuo", "tiangong", "metaso", "quark", 
            "360ai", "baiduai", "you", "brave", "wolfram", "chatgpt_chat", "deepseek_chat",
            "deepseek", "wanzhi", "baixiaoying", "yuewen", "doubao", "hailuo", "yuanbao"
        )
        
        val result = aiEngineIdentifiers.any { it.equals(engineKey, ignoreCase = true) }
        Log.d(TAG, "isAIEngine check for '$engineKey': $result")
        return result
    }
} 