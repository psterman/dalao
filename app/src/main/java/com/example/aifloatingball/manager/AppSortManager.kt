package com.example.aifloatingball.manager

import android.content.Context
import android.content.SharedPreferences
import com.example.aifloatingball.model.AppCategory
import com.example.aifloatingball.model.AppSearchConfig
import java.text.Collator
import java.util.Locale

/**
 * 应用排序管理器
 * 支持三种排序方式：
 * 1. 按首字母顺序
 * 2. 按历史使用顺序+次数降序排列
 * 3. 按上次使用时间+用户使用位置排序
 */
enum class AppSortType(val displayName: String) {
    ALPHABETICAL("按首字母"),
    USAGE_COUNT("按使用次数"),
    RECENT_POSITION("按最近使用")
}

class AppSortManager private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("app_sort_manager", Context.MODE_PRIVATE)
    private val usageTracker = AppUsageTracker.getInstance(context)
    private val collator = Collator.getInstance(Locale.CHINA)

    companion object {
        @Volatile
        private var instance: AppSortManager? = null

        fun getInstance(context: Context): AppSortManager {
            return instance ?: synchronized(this) {
                instance ?: AppSortManager(context.applicationContext).also { instance = it }
            }
        }
    }

    /**
     * 获取指定分类的排序方式
     */
    fun getSortType(category: AppCategory): AppSortType {
        val key = "sort_type_${category.name}"
        val value = prefs.getString(key, AppSortType.ALPHABETICAL.name)
        return try {
            AppSortType.valueOf(value ?: AppSortType.ALPHABETICAL.name)
        } catch (e: Exception) {
            AppSortType.ALPHABETICAL
        }
    }

    /**
     * 设置指定分类的排序方式
     */
    fun setSortType(category: AppCategory, sortType: AppSortType) {
        val key = "sort_type_${category.name}"
        prefs.edit().putString(key, sortType.name).apply()
    }

    /**
     * 对应用列表进行排序
     */
    fun sortApps(apps: List<AppSearchConfig>, category: AppCategory): List<AppSearchConfig> {
        val sortType = getSortType(category)
        return when (sortType) {
            AppSortType.ALPHABETICAL -> sortByAlphabetical(apps)
            AppSortType.USAGE_COUNT -> sortByUsageCount(apps)
            AppSortType.RECENT_POSITION -> sortByRecentPosition(apps)
        }
    }

    /**
     * 1️⃣ 按首字母顺序排序
     */
    private fun sortByAlphabetical(apps: List<AppSearchConfig>): List<AppSearchConfig> {
        return apps.sortedWith { a, b ->
            collator.compare(a.appName, b.appName)
        }
    }

    /**
     * 2️⃣ 按历史使用顺序+次数降序排列
     */
    private fun sortByUsageCount(apps: List<AppSearchConfig>): List<AppSearchConfig> {
        val usageRecords = usageTracker.getAllUsageRecords()
        
        return apps.sortedWith(compareByDescending<AppSearchConfig> { app ->
            val record = usageRecords[app.packageName]
            record?.useCount ?: 0
        }.thenBy { app ->
            val record = usageRecords[app.packageName]
            record?.firstUsedTime ?: Long.MAX_VALUE
        }.thenBy { app ->
            collator.compare(app.appName, "")
        })
    }

    /**
     * 3️⃣ 按上次使用时间+用户使用位置排序
     */
    private fun sortByRecentPosition(apps: List<AppSearchConfig>): List<AppSearchConfig> {
        val usageRecords = usageTracker.getAllUsageRecords()
        val now = System.currentTimeMillis()
        
        return apps.sortedWith(compareByDescending<AppSearchConfig> { app ->
            val record = usageRecords[app.packageName]
            // 优先按最后使用时间，然后按位置
            when {
                record == null -> Long.MIN_VALUE
                record.lastUsedTime > 0 -> {
                    // 最近使用时间（越近越大）+ 位置权重
                    val timeScore = record.lastUsedTime
                    val positionScore = if (record.lastPosition >= 0) {
                        // 位置越靠前，分数越高（假设屏幕顶部是0）
                        (1000 - record.lastPosition).toLong()
                    } else {
                        0L
                    }
                    timeScore + positionScore
                }
                else -> Long.MIN_VALUE
            }
        }.thenBy { app ->
            collator.compare(app.appName, "")
        })
    }

    /**
     * 获取排序选项列表（用于UI显示）
     */
    fun getSortOptions(): List<AppSortType> {
        return AppSortType.values().toList()
    }
}

