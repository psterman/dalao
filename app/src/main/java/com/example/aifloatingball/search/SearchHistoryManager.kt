package com.example.aifloatingball.search

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SearchHistoryManager(context: Context) {
    private val prefs = context.getSharedPreferences("search_history", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val MAX_HISTORY_SIZE = 20
    
    fun addSearchQuery(query: String) {
        val history = getSearchHistory().toMutableList()
        // 移除重复的查询
        history.remove(query)
        // 添加到开头
        history.add(0, query)
        // 保持历史记录不超过最大数量
        if (history.size > MAX_HISTORY_SIZE) {
            history.removeAt(history.size - 1)
        }
        saveSearchHistory(history)
    }
    
    fun getSearchHistory(): List<String> {
        val json = prefs.getString("history", null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<String>>() {}.type)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun getLastQuery(): String? {
        return getSearchHistory().firstOrNull()
    }
    
    private fun saveSearchHistory(history: List<String>) {
        prefs.edit().putString("history", gson.toJson(history)).apply()
    }
    
    fun clearHistory() {
        prefs.edit().remove("history").apply()
    }
} 