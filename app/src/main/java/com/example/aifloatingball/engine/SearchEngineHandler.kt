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
        // 获取所有AI引擎的名称
        val allAIEngineNames = AISearchEngine.DEFAULT_AI_ENGINES.map { it.name.lowercase() }.toSet()
        
        // 额外的别名映射，用于兼容不同的命名方式
        val aliasMap = mapOf(
            "wenxin" to "文心一言",
            "chatglm" to "智谱清言", 
            "qianwen" to "通义千问",
            "xinghuo" to "讯飞星火",
            "tiangong" to "天工ai",
            "metaso" to "秘塔ai搜索",
            "360ai" to "360ai搜索",
            "baiduai" to "百度ai",
            "you" to "you.com",
            "brave" to "brave search",
            "wolfram" to "wolframalpha",
            "chatgpt_chat" to "chatgpt (api)",
            "deepseek_chat" to "deepseek (api)",
            "deepseek" to "deepseek (web)",
            "wanzhi" to "万知",
            "baixiaoying" to "百小应",
            "yuewen" to "跃问",
            "doubao" to "豆包",
            "hailuo" to "海螺",
            "yuanbao" to "腾讯元宝",
            "shangliang" to "商量",
            "devv" to "devv",
            "huggingchat" to "huggingchat",
            "nanoai" to "纳米ai搜索",
            "thinkany" to "thinkany",
            "hika" to "hika",
            "genspark" to "genspark",
            "grok" to "grok",
            "flowith" to "flowith",
            "notebooklm" to "notebooklm",
            "coze" to "coze",
            "dify" to "dify",
            "wps" to "wps灵感",
            "lechat" to "lechat",
            "monica" to "monica",
            "zhihu" to "知乎"
        )
        
        val lowerEngineKey = engineKey.lowercase()
        
        // 1. 直接匹配AI引擎名称
        if (allAIEngineNames.contains(lowerEngineKey)) {
            Log.d(TAG, "AI engine found by direct name match: '$engineKey'")
            return true
        }
        
        // 2. 通过别名映射匹配
        val mappedName = aliasMap[lowerEngineKey]
        if (mappedName != null && allAIEngineNames.contains(mappedName.lowercase())) {
            Log.d(TAG, "AI engine found by alias mapping: '$engineKey' -> '$mappedName'")
            return true
        }
        
        // 3. 部分匹配（包含关系）
        val partialMatch = allAIEngineNames.any { aiName ->
            aiName.contains(lowerEngineKey) || lowerEngineKey.contains(aiName)
        }
        
        if (partialMatch) {
            Log.d(TAG, "AI engine found by partial match: '$engineKey'")
            return true
        }
        
        Log.d(TAG, "Not an AI engine: '$engineKey'")
        return false
    }
} 