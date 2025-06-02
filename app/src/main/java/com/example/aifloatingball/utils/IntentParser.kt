package com.example.aifloatingball.utils

import android.content.Intent

/**
 * 搜索参数数据类，包含从Intent中提取的搜索信息
 */
data class SearchParams(
    val query: String,
    val windowCount: Int,
    val engineKey: String?
)

/**
 * 意图解析器，负责从Intent中提取信息
 */
class IntentParser {
    
    /**
     * 解析搜索意图，提取搜索参数
     * @return 如果意图包含有效的搜索查询，则返回SearchParams，否则返回null
     */
    fun parseSearchIntent(intent: Intent): SearchParams? {
        val query = intent.getStringExtra("search_query") ?: return null
        val windowCount = intent.getIntExtra("window_count", 2)
        val engineKey = intent.getStringExtra("engine_key")
        
        return SearchParams(query, windowCount, engineKey)
    }
} 