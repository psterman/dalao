package com.example.aifloatingball.model

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.aifloatingball.R

data class AppSearchConfig(
    val appId: String,          // 应用标识符
    val appName: String,        // 应用名称
    val packageName: String,    // 应用包名
    var isEnabled: Boolean,     // 是否启用
    var order: Int,            // 排序顺序
    val iconResId: Int,         // 图标资源ID
    val searchUrl: String       // 搜索URL模板
)

class AppSearchSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_search_settings", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_APP_CONFIGS = "app_configs"
        private const val KEY_LAST_ORDER = "last_order"
        
        @Volatile
        private var instance: AppSearchSettings? = null

        fun getInstance(context: Context): AppSearchSettings {
            return instance ?: synchronized(this) {
                instance ?: AppSearchSettings(context.applicationContext).also { instance = it }
            }
        }
    }

    // 获取所有应用配置
    fun getAppConfigs(): List<AppSearchConfig> {
        val json = prefs.getString(KEY_APP_CONFIGS, null)
        return if (json != null) {
            val type = object : TypeToken<List<AppSearchConfig>>() {}.type
            gson.fromJson(json, type)
        } else {
            // 返回默认配置
            getDefaultConfigs()
        }
    }

    // 保存应用配置
    fun saveAppConfigs(configs: List<AppSearchConfig>) {
        val json = gson.toJson(configs)
        prefs.edit().putString(KEY_APP_CONFIGS, json).apply()
    }

    // 更新单个应用配置
    fun updateAppConfig(appId: String, update: (AppSearchConfig) -> Unit) {
        val configs = getAppConfigs().toMutableList()
        val index = configs.indexOfFirst { it.appId == appId }
        if (index != -1) {
            update(configs[index])
            saveAppConfigs(configs)
        }
    }

    // 获取默认配置
    private fun getDefaultConfigs(): List<AppSearchConfig> {
        return listOf(
            AppSearchConfig(
                appId = "wechat",
                appName = "微信",
                packageName = "com.tencent.mm",
                isEnabled = true,
                order = 1,
                iconResId = R.drawable.ic_wechat,
                searchUrl = "weixin://sogousearch/search/" // 示例URL，实际可能不同
            ),
            AppSearchConfig(
                appId = "taobao",
                appName = "淘宝",
                packageName = "com.taobao.taobao",
                isEnabled = true,
                order = 2,
                iconResId = R.drawable.ic_taobao,
                searchUrl = "taobao://s.taobao.com/search?q="
            ),
            AppSearchConfig(
                appId = "pdd",
                appName = "拼多多",
                packageName = "com.xunmeng.pinduoduo",
                isEnabled = true,
                order = 3,
                iconResId = R.drawable.ic_app_search_default,
                searchUrl = "pinduoduo://com.xunmeng.pinduoduo/search_result.html?keyword="
            ),
            AppSearchConfig(
                appId = "douyin",
                appName = "抖音",
                packageName = "com.ss.android.ugc.aweme",
                isEnabled = true,
                order = 4,
                iconResId = R.drawable.ic_douyin,
                searchUrl = "snssdk1128://search/live?keyword="
            ),
            AppSearchConfig(
                appId = "xiaohongshu",
                appName = "小红书",
                packageName = "com.xingin.xhs",
                isEnabled = true,
                order = 5,
                iconResId = R.drawable.ic_app_search_default,
                searchUrl = "xhsdiscover://search/notes?keyword="
            )
        )
    }

    // 重新排序应用
    fun reorderApp(appId: String, newOrder: Int) {
        val configs = getAppConfigs().toMutableList()
        val app = configs.find { it.appId == appId } ?: return
        val oldOrder = app.order
        
        // 更新其他应用的顺序
        configs.forEach { config ->
            when {
                config.appId == appId -> config.order = newOrder
                newOrder > oldOrder && config.order in (oldOrder + 1)..newOrder ->
                    config.order--
                newOrder < oldOrder && config.order in newOrder until oldOrder ->
                    config.order++
            }
        }
        
        saveAppConfigs(configs)
    }

    // 启用/禁用应用
    fun toggleAppEnabled(appId: String, enabled: Boolean) {
        updateAppConfig(appId) { it.isEnabled = enabled }
    }

    // 获取已启用的应用配置（按顺序）
    fun getEnabledAppConfigs(): List<AppSearchConfig> {
        return getAppConfigs()
            .filter { it.isEnabled }
            .sortedBy { it.order }
    }
} 