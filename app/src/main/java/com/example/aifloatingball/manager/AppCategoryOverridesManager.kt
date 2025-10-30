package com.example.aifloatingball.manager

import android.content.Context
import android.content.SharedPreferences
import com.example.aifloatingball.model.AppCategory

/**
 * 持久化保存用户对应用分类的自定义覆盖映射：packageName -> AppCategory
 */
class AppCategoryOverridesManager private constructor(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("app_category_overrides", Context.MODE_PRIVATE)

    companion object {
        @Volatile
        private var instance: AppCategoryOverridesManager? = null

        fun getInstance(context: Context): AppCategoryOverridesManager {
            return instance ?: synchronized(this) {
                instance ?: AppCategoryOverridesManager(context).also { instance = it }
            }
        }
    }

    fun getOverride(packageName: String): AppCategory? {
        val value = prefs.getString(packageName, null) ?: return null
        return runCatching { AppCategory.valueOf(value) }.getOrNull()
    }

    fun setOverride(packageName: String, category: AppCategory) {
        prefs.edit().putString(packageName, category.name).apply()
    }

    fun removeOverride(packageName: String) {
        prefs.edit().remove(packageName).apply()
    }
}

