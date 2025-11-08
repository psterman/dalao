package com.example.aifloatingball.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 标签页恢复管理器
 * 负责保存和恢复非正常关闭的页面，支持延迟加载以降低功耗和内存消耗
 */
class TabRecoveryManager private constructor(private val context: Context) {
    
    companion object {
        private const val TAG = "TabRecoveryManager"
        private const val PREFS_NAME = "tab_recovery"
        private const val KEY_RECOVERY_DATA = "recovery_data"
        private const val KEY_NORMAL_SHUTDOWN = "normal_shutdown"
        private const val KEY_LAST_SAVE_TIME = "last_save_time"
        private const val KEY_LAST_GROUP_ID = "last_group_id"
        private const val KEY_LAST_TAB_INDEX = "last_tab_index"
        
        @Volatile
        private var INSTANCE: TabRecoveryManager? = null
        
        fun getInstance(context: Context): TabRecoveryManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TabRecoveryManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    /**
     * 恢复的标签页数据（轻量级，不包含WebView）
     */
    data class RecoveryTabData(
        val id: String,
        val title: String,
        val url: String,
        val groupId: String?,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * 恢复数据（按组组织）
     */
    data class RecoveryData(
        val groups: Map<String, List<RecoveryTabData>>, // groupId -> tabs
        val lastGroupId: String? = null, // 最后浏览的组ID
        val lastTabIndex: Int = 0, // 最后浏览的标签页索引（在组内的索引）
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * 保存所有标签组的页面状态
     * @param groupTabsMap 组ID到标签页列表的映射
     * @param lastGroupId 最后浏览的组ID
     * @param lastTabIndex 最后浏览的标签页索引（在组内的索引）
     */
    fun saveRecoveryData(groupTabsMap: Map<String, List<RecoveryTabSource>>, lastGroupId: String? = null, lastTabIndex: Int = 0) {
        try {
            val recoveryGroups = mutableMapOf<String, List<RecoveryTabData>>()
            
            groupTabsMap.forEach { (groupId, tabs) ->
                val recoveryTabs = tabs.map { tab ->
                    RecoveryTabData(
                        id = tab.id,
                        title = tab.title,
                        url = tab.url,
                        groupId = groupId
                    )
                }
                if (recoveryTabs.isNotEmpty()) {
                    recoveryGroups[groupId] = recoveryTabs
                }
            }
            
            val recoveryData = RecoveryData(recoveryGroups, lastGroupId, lastTabIndex)
            val recoveryJson = gson.toJson(recoveryData)
            
            prefs.edit()
                .putString(KEY_RECOVERY_DATA, recoveryJson)
                .putLong(KEY_LAST_SAVE_TIME, System.currentTimeMillis())
                .apply()
            
            Log.d(TAG, "保存恢复数据: ${recoveryGroups.size} 个组，共 ${recoveryGroups.values.sumOf { it.size }} 个标签页，最后浏览: 组=$lastGroupId, 索引=$lastTabIndex")
        } catch (e: Exception) {
            Log.e(TAG, "保存恢复数据失败", e)
        }
    }
    
    /**
     * 获取恢复数据
     */
    fun getRecoveryData(): RecoveryData? {
        try {
            val recoveryJson = prefs.getString(KEY_RECOVERY_DATA, null)
            if (recoveryJson.isNullOrEmpty()) {
                return null
            }
            
            val type = object : TypeToken<RecoveryData>() {}.type
            val recoveryData = gson.fromJson<RecoveryData>(recoveryJson, type)
            
            Log.d(TAG, "获取恢复数据: ${recoveryData?.groups?.size ?: 0} 个组")
            return recoveryData
        } catch (e: Exception) {
            Log.e(TAG, "获取恢复数据失败", e)
            return null
        }
    }
    
    /**
     * 检查是否有需要恢复的数据
     */
    fun hasRecoveryData(): Boolean {
        val recoveryData = getRecoveryData()
        return recoveryData != null && recoveryData.groups.isNotEmpty()
    }
    
    /**
     * 检查是否为非正常关闭（没有正常关闭标记）
     */
    fun isAbnormalShutdown(): Boolean {
        val normalShutdown = prefs.getBoolean(KEY_NORMAL_SHUTDOWN, false)
        val hasRecoveryData = hasRecoveryData()
        
        // 如果有恢复数据且没有正常关闭标记，说明是非正常关闭
        val isAbnormal = hasRecoveryData && !normalShutdown
        
        Log.d(TAG, "检查关闭状态: normalShutdown=$normalShutdown, hasRecoveryData=$hasRecoveryData, isAbnormal=$isAbnormal")
        return isAbnormal
    }
    
    /**
     * 标记正常关闭
     */
    fun markNormalShutdown() {
        prefs.edit().putBoolean(KEY_NORMAL_SHUTDOWN, true).apply()
        Log.d(TAG, "标记正常关闭")
    }
    
    /**
     * 检查上次是否正常关闭（不改变状态）
     */
    fun isNormalShutdown(): Boolean {
        return prefs.getBoolean(KEY_NORMAL_SHUTDOWN, false)
    }
    
    /**
     * 清除正常关闭标记（应用启动时调用）
     */
    fun clearNormalShutdownFlag() {
        prefs.edit().putBoolean(KEY_NORMAL_SHUTDOWN, false).apply()
        Log.d(TAG, "清除正常关闭标记")
    }
    
    /**
     * 清除恢复数据（用户选择恢复后或拒绝恢复后）
     */
    fun clearRecoveryData() {
        prefs.edit()
            .remove(KEY_RECOVERY_DATA)
            .remove(KEY_LAST_SAVE_TIME)
            .apply()
        Log.d(TAG, "清除恢复数据")
    }
    
    /**
     * 获取恢复数据中的标签页总数
     */
    fun getRecoveryTabCount(): Int {
        val recoveryData = getRecoveryData() ?: return 0
        return recoveryData.groups.values.sumOf { it.size }
    }
    
    /**
     * 标签页数据源接口（用于从不同来源获取标签页数据）
     */
    interface RecoveryTabSource {
        val id: String
        val title: String
        val url: String
    }
}

