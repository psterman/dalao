package com.example.aifloatingball.manager

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.concurrent.ConcurrentHashMap

/**
 * 应用使用记录追踪器
 * 记录应用的使用时间、次数、位置等信息
 */
data class AppUsageRecord(
    val packageName: String,
    val appName: String,
    var useCount: Int = 0,
    var lastUsedTime: Long = 0L,  // 最后使用时间戳
    var firstUsedTime: Long = 0L, // 首次使用时间戳
    var lastPosition: Int = -1,   // 最后一次使用时的位置索引
    var positions: MutableList<Int> = mutableListOf() // 历史位置列表
) {
    // 为了JSON序列化，需要提供空的构造函数
    constructor() : this("", "", 0, 0L, 0L, -1, mutableListOf())
}

class AppUsageTracker private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("app_usage_tracker", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // 内存缓存，提高性能
    @Volatile
    private var usageMap: ConcurrentHashMap<String, AppUsageRecord> = ConcurrentHashMap()

    companion object {
        @Volatile
        private var instance: AppUsageTracker? = null

        fun getInstance(context: Context): AppUsageTracker {
            return instance ?: synchronized(this) {
                instance ?: AppUsageTracker(context.applicationContext).also { instance = it }
            }
        }
    }

    init {
        loadFromPrefs()
    }

    /**
     * 记录应用使用
     */
    fun recordUsage(packageName: String, appName: String, position: Int = -1) {
        val now = System.currentTimeMillis()
        val record = usageMap.getOrPut(packageName) {
            AppUsageRecord(
                packageName = packageName,
                appName = appName,
                firstUsedTime = now
            )
        }
        
        record.useCount++
        record.lastUsedTime = now
        if (position >= 0) {
            record.lastPosition = position
            if (!record.positions.contains(position)) {
                record.positions.add(position)
            }
        }
        
        usageMap[packageName] = record
        saveToPrefs()
    }

    /**
     * 获取应用使用记录
     */
    fun getUsageRecord(packageName: String): AppUsageRecord? {
        return usageMap[packageName]
    }

    /**
     * 获取所有使用记录
     */
    fun getAllUsageRecords(): Map<String, AppUsageRecord> {
        return usageMap.toMap()
    }

    /**
     * 清除使用记录
     */
    fun clearUsage(packageName: String) {
        usageMap.remove(packageName)
        saveToPrefs()
    }

    /**
     * 清除所有使用记录
     */
    fun clearAllUsage() {
        usageMap.clear()
        prefs.edit().clear().apply()
    }

    /**
     * 从SharedPreferences加载数据
     */
    private fun loadFromPrefs() {
        try {
            val json = prefs.getString("usage_records", null)
            if (json != null) {
                val type = object : TypeToken<Map<String, AppUsageRecord>>() {}.type
                val loaded = gson.fromJson<Map<String, AppUsageRecord>>(json, type)
                usageMap.clear()
                usageMap.putAll(loaded ?: emptyMap())
            }
        } catch (e: Exception) {
            android.util.Log.e("AppUsageTracker", "加载使用记录失败", e)
            usageMap.clear()
        }
    }

    /**
     * 保存到SharedPreferences
     */
    private fun saveToPrefs() {
        try {
            val json = gson.toJson(usageMap.toMap())
            prefs.edit().putString("usage_records", json).apply()
        } catch (e: Exception) {
            android.util.Log.e("AppUsageTracker", "保存使用记录失败", e)
        }
    }
}

