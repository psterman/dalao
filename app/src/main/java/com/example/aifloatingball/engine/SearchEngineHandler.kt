package com.example.aifloatingball.engine

import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.utils.EngineUtil

/**
 * 搜索引擎处理器，负责处理搜索引擎相关逻辑
 */
class SearchEngineHandler {

    companion object {
        const val DEFAULT_ENGINE_KEY = "default_standard" // 或者您希望的默认标准引擎的键名
        // 您也可以为AI引擎定义一个默认键，如果需要的话
        // const val DEFAULT_AI_ENGINE_KEY = "default_ai"
    }
    
    /**
     * 根据搜索查询和引擎键获取搜索URL
     */
    fun getSearchUrl(query: String, engineKey: String?): String {
        val currentEngineKey = engineKey ?: DEFAULT_ENGINE_KEY // 如果 engineKey 为 null，则使用默认键

        return when {
            currentEngineKey.startsWith("ai_") -> {
                // 处理AI搜索引擎
                val aiEngineKey = currentEngineKey.substring(3)
                EngineUtil.getAISearchEngineUrl(aiEngineKey, query)
            }
            // 当 engineKey 是 DEFAULT_ENGINE_KEY 或者其他非 AI 引擎键时
            else -> {
                // 处理标准搜索引擎，包括我们定义的默认标准引擎
                // 如果 DEFAULT_ENGINE_KEY 确实应该指向特定的引擎如 Google，EngineUtil.getSearchEngineSearchUrl 应该能处理它
                // 或者，如果 DEFAULT_ENGINE_KEY 是一个特殊标记，这里需要getDefaultSearchUrl的逻辑
                if (currentEngineKey == DEFAULT_ENGINE_KEY) {
                    getDefaultSearchUrl(query) // 使用内部的默认逻辑
                } else {
                    EngineUtil.getSearchEngineSearchUrl(currentEngineKey, query)
                }
            }
        }
    }
    
    /**
     * 获取默认搜索引擎的搜索URL
     * 这个方法现在主要在内部通过 DEFAULT_ENGINE_KEY 间接触发，或者作为最后的保障
     */
    private fun getDefaultSearchUrl(query: String): String {
        // 这里的逻辑可以保持，或者更直接地使用某个硬编码的默认引擎URL，如果 SearchEngine.DEFAULT_ENGINES 可能为空
        return SearchEngine.DEFAULT_ENGINES.firstOrNull()?.getSearchUrl(query)
            ?: "https://www.google.com/search?q=$query" // 兜底URL
    }
} 