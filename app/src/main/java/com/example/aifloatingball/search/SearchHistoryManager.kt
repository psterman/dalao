package com.example.aifloatingball.search

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SearchHistoryManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("search_history", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val maxHistorySize = 20
    
    fun addSearchQuery(query: String) {
        val history = getSearchHistory().toMutableList()
        history.remove(query)  // 移除已存在的相同查询
        history.add(0, query)  // 添加到开头
        
        // 保持历史记录不超过最大数量
        while (history.size > maxHistorySize) {
            history.removeAt(history.size - 1)
        }
        
        saveHistory(history)
    }
    
    fun getSearchHistory(): List<String> {
        val json = prefs.getString("history", null) ?: return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun getLastQuery(): String? {
        return getSearchHistory().firstOrNull()
    }
    
    private fun saveHistory(history: List<String>) {
        val json = gson.toJson(history)
        prefs.edit().putString("history", json).apply()
    }
    
    fun clearHistory() {
        prefs.edit().remove("history").apply()
    }
} 