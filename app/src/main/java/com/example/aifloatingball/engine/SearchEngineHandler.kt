package com.example.aifloatingball.engine

import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.utils.EngineUtil
import android.util.Log

/**
 * 搜索引擎处理器，负责处理搜索引擎相关逻辑
 */
class SearchEngineHandler {

    companion object {
        const val DEFAULT_ENGINE_KEY = "baidu" // 修改为百度作为默认搜索引擎
        // 您也可以为AI引擎定义一个默认键，如果需要的话
        // const val DEFAULT_AI_ENGINE_KEY = "default_ai"
        private const val TAG = "SearchEngineHandler"
    }
    
    /**
     * 根据搜索查询和引擎键获取搜索URL
     */
    fun getSearchUrl(query: String, engineKey: String?): String {
        val currentEngineKey = engineKey ?: DEFAULT_ENGINE_KEY // 如果 engineKey 为 null，则使用默认键
        Log.d(TAG, "getSearchUrl called with query: '$query', engineKey: '$engineKey', using currentEngineKey: '$currentEngineKey'")

        // 处理空查询的情况
        val safeQuery = if (query.isBlank()) "" else query
        val encodedQuery = try {
            java.net.URLEncoder.encode(safeQuery, "UTF-8")
        } catch (e: Exception) {
            Log.e(TAG, "URL编码失败: $e")
            safeQuery // 如果编码失败则使用原始查询
        }

        val searchUrl = when {
            currentEngineKey.startsWith("ai_") -> {
                // 处理AI搜索引擎
                val aiEngineKey = currentEngineKey.substring(3)
                val aiUrl = EngineUtil.getAISearchEngineUrl(aiEngineKey, encodedQuery)
                if (aiUrl.isBlank()) {
                    // 如果AI引擎URL为空，返回一个备用URL
                    "https://www.baidu.com/s?wd=$encodedQuery"
                } else {
                    aiUrl
                }
            }
            // 当 engineKey 是 DEFAULT_ENGINE_KEY 或者其他非 AI 引擎键时
            else -> {
                // 处理标准搜索引擎，包括我们定义的默认标准引擎
                if (currentEngineKey == DEFAULT_ENGINE_KEY) {
                    "https://www.baidu.com/s?wd=$encodedQuery" // 使用百度作为默认搜索引擎
                } else {
                    val standardUrl = EngineUtil.getSearchEngineSearchUrl(currentEngineKey, encodedQuery)
                    if (standardUrl.isBlank()) {
                        // 如果标准引擎URL为空，返回默认URL
                        "https://www.baidu.com/s?wd=$encodedQuery"
                    } else {
                        standardUrl
                    }
                }
            }
        }
        
        // 确保返回的URL是有效的，否则返回一个备用URL
        val finalUrl = if (searchUrl.isBlank() || !searchUrl.startsWith("http")) {
            Log.w(TAG, "获取到无效的搜索URL: '$searchUrl'，使用备用URL")
            "https://www.baidu.com/s?wd=$encodedQuery"
        } else {
            searchUrl
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
} 