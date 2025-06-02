package com.example.aifloatingball.engine

import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.utils.EngineUtil

/**
 * 搜索引擎处理器，负责处理搜索引擎相关逻辑
 */
class SearchEngineHandler {
    
    /**
     * 根据搜索查询和引擎键获取搜索URL
     */
    fun getSearchUrl(query: String, engineKey: String?): String {
        return when {
            engineKey?.startsWith("ai_") == true -> {
                // 处理AI搜索引擎
                val aiEngineKey = engineKey.substring(3)
                EngineUtil.getAISearchEngineUrl(aiEngineKey, query)
            }
            !engineKey.isNullOrEmpty() -> {
                // 处理标准搜索引擎
                EngineUtil.getSearchEngineSearchUrl(engineKey, query)
            }
            else -> {
                // 使用默认搜索引擎
                getDefaultSearchUrl(query)
            }
        }
    }
    
    /**
     * 获取默认搜索引擎的搜索URL
     */
    private fun getDefaultSearchUrl(query: String): String {
        return SearchEngine.DEFAULT_ENGINES.firstOrNull()?.getSearchUrl(query)
            ?: "https://www.google.com/search?q=$query" // 兜底URL
    }
} 