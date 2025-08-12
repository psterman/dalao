package com.example.aifloatingball

import android.content.Context
import android.content.SharedPreferences
import com.example.aifloatingball.model.AppSearchConfig
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 应用选择历史管理器
 */
class AppSelectionHistoryManager private constructor(private val context: Context) {
    
    companion object {
        private const val PREFS_NAME = "app_selection_history"
        private const val KEY_SELECTION_HISTORY = "selection_history"
        private const val MAX_HISTORY_SIZE = 10
        
        @Volatile
        private var INSTANCE: AppSelectionHistoryManager? = null
        
        fun getInstance(context: Context): AppSelectionHistoryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppSelectionHistoryManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    /**
     * 应用选择历史项
     */
    data class AppSelectionItem(
        val appName: String,
        val packageName: String,
        val categoryName: String,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * 添加应用选择历史
     */
    fun addAppSelection(appConfig: AppSearchConfig) {
        val historyList = getSelectionHistory().toMutableList()
        val newItem = AppSelectionItem(
            appName = appConfig.appName,
            packageName = appConfig.packageName,
            categoryName = appConfig.category.name,
            timestamp = System.currentTimeMillis()
        )
        
        // 移除重复项
        historyList.removeAll { it.packageName == newItem.packageName }
        
        // 添加到开头
        historyList.add(0, newItem)
        
        // 限制历史记录数量
        if (historyList.size > MAX_HISTORY_SIZE) {
            historyList.removeAt(historyList.size - 1)
        }
        
        // 保存
        saveSelectionHistory(historyList)
    }
    
    /**
     * 获取应用选择历史
     */
    fun getSelectionHistory(): List<AppSelectionItem> {
        val json = prefs.getString(KEY_SELECTION_HISTORY, null) ?: return emptyList()
        return try {
            val type = object : TypeToken<List<AppSelectionItem>>() {}.type
            gson.fromJson(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 获取最近选择的应用（去重）
     */
    fun getRecentApps(): List<AppSelectionItem> {
        return getSelectionHistory()
            .distinctBy { it.packageName }
            .take(8) // 最多显示8个
    }
    
    /**
     * 清空选择历史
     */
    fun clearSelectionHistory() {
        prefs.edit().remove(KEY_SELECTION_HISTORY).apply()
    }
    
    /**
     * 删除指定应用的选择历史
     */
    fun removeAppSelection(packageName: String) {
        val historyList = getSelectionHistory().toMutableList()
        historyList.removeAll { it.packageName == packageName }
        saveSelectionHistory(historyList)
    }
    
    /**
     * 保存选择历史
     */
    private fun saveSelectionHistory(historyList: List<AppSelectionItem>) {
        val json = gson.toJson(historyList)
        prefs.edit().putString(KEY_SELECTION_HISTORY, json).apply()
    }
}
