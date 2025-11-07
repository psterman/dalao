package com.example.aifloatingball.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.aifloatingball.webview.PaperStackWebViewManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 组标签页数据管理器
 * 负责按组保存和恢复标签页数据
 */
class GroupTabDataManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "GroupTabDataManager"
        private const val PREFS_NAME = "group_tabs_data"
        
        @Volatile
        private var INSTANCE: GroupTabDataManager? = null
        
        fun getInstance(context: Context): GroupTabDataManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: GroupTabDataManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    /**
     * 标签页数据（用于保存和恢复）
     */
    data class TabData(
        val id: String,
        val title: String,
        val url: String
    )
    
    /**
     * 保存组的标签页数据
     */
    fun saveGroupTabs(groupId: String, tabs: List<PaperStackWebViewManager.WebViewTab>) {
        try {
            val tabDataList = tabs.map { tab ->
                TabData(
                    id = tab.id,
                    title = tab.title,
                    url = tab.url
                )
            }
            val tabsJson = gson.toJson(tabDataList)
            prefs.edit().putString("group_tabs_$groupId", tabsJson).apply()
            Log.d(TAG, "保存组 $groupId 的 ${tabs.size} 个标签页")
        } catch (e: Exception) {
            Log.e(TAG, "保存组标签页数据失败", e)
        }
    }
    
    /**
     * 恢复组的标签页数据
     */
    fun restoreGroupTabs(groupId: String): List<TabData> {
        try {
            val tabsJson = prefs.getString("group_tabs_$groupId", null)
            if (tabsJson.isNullOrEmpty()) {
                return emptyList()
            }
            val type = object : TypeToken<List<TabData>>() {}.type
            val tabDataList = gson.fromJson<List<TabData>>(tabsJson, type) ?: emptyList()
            Log.d(TAG, "恢复组 $groupId 的 ${tabDataList.size} 个标签页")
            return tabDataList
        } catch (e: Exception) {
            Log.e(TAG, "恢复组标签页数据失败", e)
            return emptyList()
        }
    }
    
    /**
     * 删除组的标签页数据
     */
    fun deleteGroupTabs(groupId: String) {
        try {
            prefs.edit().remove("group_tabs_$groupId").apply()
            Log.d(TAG, "删除组 $groupId 的标签页数据")
        } catch (e: Exception) {
            Log.e(TAG, "删除组标签页数据失败", e)
        }
    }
}

