package com.example.aifloatingball.utils

import android.content.Intent
import android.util.Log

/**
 * Intent解析器，负责解析搜索相关的Intent
 */
class IntentParser {
    companion object {
        private const val TAG = "IntentParser"
    }

    /**
     * 解析搜索Intent
     */
    fun parseSearchIntent(intent: Intent): SearchParams? {
        return try {
            // Support both parameter name formats for compatibility
            val query = intent.getStringExtra("search_query") 
                ?: intent.getStringExtra("query") 
                ?: ""
            val windowCount = intent.getIntExtra("window_count", 3)
            val engineKey = intent.getStringExtra("engine_key") 
                ?: intent.getStringExtra("engine")
            
            Log.d(TAG, "解析搜索Intent: query='$query', windowCount=$windowCount, engineKey='$engineKey'")
            
            SearchParams(query, windowCount, engineKey)
        } catch (e: Exception) {
            Log.e(TAG, "解析搜索Intent失败: ${e.message}")
            null
        }
    }
} 