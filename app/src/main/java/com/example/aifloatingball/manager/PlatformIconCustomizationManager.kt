package com.example.aifloatingball.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.aifloatingball.model.AppSearchConfig

/**
 * 平台图标定制管理器
 * 负责管理用户在AI回复中显示哪些应用图标的定制设置
 * 支持所有应用，不仅仅是预设的平台
 */
class PlatformIconCustomizationManager private constructor(context: Context) {

    private val sharedPreferences: SharedPreferences
    private val enabledApps: MutableSet<String> // 存储用户启用的应用包名

    init {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        enabledApps = sharedPreferences.getStringSet(KEY_ENABLED_APPS, getDefaultEnabledApps())?.toMutableSet() ?: mutableSetOf()
        Log.d(TAG, "初始化 PlatformIconCustomizationManager, 已启用应用: $enabledApps")
    }

    companion object {
        private const val TAG = "PlatformIconCustomizationManager"
        private const val PREFS_NAME = "platform_icon_customization_prefs"
        private const val KEY_ENABLED_APPS = "enabled_apps"

        @Volatile
        private var INSTANCE: PlatformIconCustomizationManager? = null

        fun getInstance(context: Context): PlatformIconCustomizationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlatformIconCustomizationManager(context.applicationContext).also { INSTANCE = it }
            }
        }

        // 默认启用的应用（预设平台）
        fun getDefaultEnabledApps(): Set<String> {
            return setOf(
                "com.ss.android.ugc.aweme", // 抖音
                "com.xingin.xhs", // 小红书
                "com.google.android.youtube", // YouTube
                "tv.danmaku.bili", // 哔哩哔哩
                "com.smile.gifmaker", // 快手
                "com.sina.weibo", // 微博
                "com.douban.frodo" // 豆瓣
            )
        }
    }

    /**
     * 切换应用的启用状态
     * @param appConfig 应用配置
     * @return 切换后该应用是否启用
     */
    fun toggleApp(appConfig: AppSearchConfig): Boolean {
        val packageName = appConfig.packageName
        val isEnabled = if (enabledApps.contains(packageName)) {
            enabledApps.remove(packageName)
            false
        } else {
            enabledApps.add(packageName)
            true
        }
        saveEnabledApps()
        Log.d(TAG, "切换应用 ${appConfig.appName} ($packageName), 状态: $isEnabled, 当前已启用应用: $enabledApps")
        return isEnabled
    }

    /**
     * 检查应用是否启用
     * @param packageName 应用包名
     * @return 如果应用启用则返回true，否则返回false
     */
    fun isAppEnabled(packageName: String): Boolean {
        return enabledApps.contains(packageName)
    }

    /**
     * 根据用户设置过滤应用列表
     * @param allApps 包含所有应用信息的列表
     * @return 过滤后的应用列表
     */
    fun filterEnabledApps(allApps: List<AppSearchConfig>): List<AppSearchConfig> {
        return allApps.filter { isAppEnabled(it.packageName) }
    }

    /**
     * 获取应用在菜单中显示的状态文本
     */
    fun getAppStatusText(appConfig: AppSearchConfig): String {
        return if (isAppEnabled(appConfig.packageName)) {
            "取消添加到AI回复"
        } else {
            "添加到AI回复"
        }
    }

    /**
     * 获取所有启用的应用包名
     */
    fun getEnabledApps(): Set<String> {
        return enabledApps.toSet()
    }

    /**
     * 添加应用到启用列表
     */
    fun addApp(packageName: String) {
        enabledApps.add(packageName)
        saveEnabledApps()
        Log.d(TAG, "添加应用到启用列表: $packageName")
    }

    /**
     * 从启用列表中移除应用
     */
    fun removeApp(packageName: String) {
        enabledApps.remove(packageName)
        saveEnabledApps()
        Log.d(TAG, "从启用列表中移除应用: $packageName")
    }

    /**
     * 清空所有启用的应用
     */
    fun clearAllApps() {
        enabledApps.clear()
        saveEnabledApps()
        Log.d(TAG, "清空所有启用的应用")
    }

    /**
     * 重置为默认设置
     */
    fun resetToDefault() {
        enabledApps.clear()
        enabledApps.addAll(getDefaultEnabledApps())
        saveEnabledApps()
        Log.d(TAG, "重置为默认设置")
    }

    /**
     * 保存启用的应用列表到SharedPreferences
     */
    private fun saveEnabledApps() {
        sharedPreferences.edit().putStringSet(KEY_ENABLED_APPS, enabledApps).apply()
    }

    // 为了向后兼容，保留原有的平台相关方法
    @Deprecated("使用 toggleApp 替代")
    fun togglePlatform(platformName: String): Boolean {
        // 将平台名称转换为包名
        val packageName = when (platformName) {
            "抖音" -> "com.ss.android.ugc.aweme"
            "小红书" -> "com.xingin.xhs"
            "YouTube" -> "com.google.android.youtube"
            "哔哩哔哩" -> "tv.danmaku.bili"
            "快手" -> "com.smile.gifmaker"
            "微博" -> "com.sina.weibo"
            "豆瓣" -> "com.douban.frodo"
            else -> return false
        }
        return toggleApp(AppSearchConfig("", platformName, packageName, true, 0, 0, "", com.example.aifloatingball.model.AppCategory.ALL))
    }

    @Deprecated("使用 isAppEnabled 替代")
    fun isPlatformEnabled(platformName: String): Boolean {
        val packageName = when (platformName) {
            "抖音" -> "com.ss.android.ugc.aweme"
            "小红书" -> "com.xingin.xhs"
            "YouTube" -> "com.google.android.youtube"
            "哔哩哔哩" -> "tv.danmaku.bili"
            "快手" -> "com.smile.gifmaker"
            "微博" -> "com.sina.weibo"
            "豆瓣" -> "com.douban.frodo"
            else -> return false
        }
        return isAppEnabled(packageName)
    }

    @Deprecated("使用 getAppStatusText 替代")
    fun getPlatformStatusText(platformName: String): String {
        return if (isPlatformEnabled(platformName)) {
            "取消添加到AI回复"
        } else {
            "添加到AI回复"
        }
    }
}