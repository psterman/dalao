package com.example.aifloatingball

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 搜索历史管理器
 */
class SearchHistoryManager private constructor(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "search_history"
        private const val KEY_HISTORY_LIST = "history_list"
        private const val KEY_DEFAULT_SEARCH = "default_search"
        private const val MAX_HISTORY_SIZE = 20
        
        @Volatile
        private var INSTANCE: SearchHistoryManager? = null
        
        fun getInstance(context: Context): SearchHistoryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SearchHistoryManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    /**
     * 搜索历史项
     */
    data class SearchHistoryItem(
        val query: String,
        val appName: String,
        val packageName: String,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * 添加搜索历史
     */
    fun addSearchHistory(query: String, appName: String, packageName: String) {
        if (query.trim().isEmpty()) return
        
        val historyList = getSearchHistory().toMutableList()
        val newItem = SearchHistoryItem(query.trim(), appName, packageName)
        
        // 移除重复项
        historyList.removeAll { it.query == newItem.query && it.packageName == newItem.packageName }
        
        // 添加到开头
        historyList.add(0, newItem)
        
        // 限制历史记录数量
        if (historyList.size > MAX_HISTORY_SIZE) {
            historyList.removeAt(historyList.size - 1)
        }
        
        // 保存
        saveSearchHistory(historyList)
    }
    
    /**
     * 获取搜索历史
     */
    fun getSearchHistory(): List<SearchHistoryItem> {
        val json = prefs.getString(KEY_HISTORY_LIST, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<SearchHistoryItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 获取查询关键词历史（去重）
     */
    fun getQueryHistory(): List<String> {
        return getSearchHistory()
            .map { it.query }
            .distinct()
            .take(10)
    }
    
    /**
     * 清空搜索历史
     */
    fun clearSearchHistory() {
        prefs.edit().remove(KEY_HISTORY_LIST).apply()
    }
    
    /**
     * 删除指定搜索历史
     */
    fun removeSearchHistory(query: String, packageName: String) {
        val historyList = getSearchHistory().toMutableList()
        historyList.removeAll { it.query == query && it.packageName == packageName }
        saveSearchHistory(historyList)
    }
    
    /**
     * 设置默认搜索选项
     */
    fun setDefaultSearch(appName: String, packageName: String) {
        val defaultSearch = mapOf(
            "appName" to appName,
            "packageName" to packageName
        )
        val json = gson.toJson(defaultSearch)
        prefs.edit().putString(KEY_DEFAULT_SEARCH, json).apply()
    }

    /**
     * 获取默认搜索选项
     */
    fun getDefaultSearch(): Pair<String, String>? {
        val json = prefs.getString(KEY_DEFAULT_SEARCH, null) ?: return null
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            val defaultSearch: Map<String, String> = gson.fromJson(json, type)
            val appName = defaultSearch["appName"] ?: return null
            val packageName = defaultSearch["packageName"] ?: return null
            Pair(appName, packageName)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 清除默认搜索选项
     */
    fun clearDefaultSearch() {
        prefs.edit().remove(KEY_DEFAULT_SEARCH).apply()
    }

    /**
     * 保存搜索历史
     */
    private fun saveSearchHistory(historyList: List<SearchHistoryItem>) {
        val json = gson.toJson(historyList)
        prefs.edit().putString(KEY_HISTORY_LIST, json).apply()
    }
}
