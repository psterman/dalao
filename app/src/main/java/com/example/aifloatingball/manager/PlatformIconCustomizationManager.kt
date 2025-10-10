package com.example.aifloatingball.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * 平台图标定制管理器
 * 管理用户在AI回复中显示的平台图标
 */
class PlatformIconCustomizationManager private constructor(context: Context) {
    
    companion object {
        private const val TAG = "PlatformIconCustomization"
        private const val PREFS_NAME = "platform_icon_customization"
        private const val KEY_ENABLED_PLATFORMS = "enabled_platforms"
        private const val KEY_CUSTOMIZATION_ENABLED = "customization_enabled"
        
        @Volatile
        private var INSTANCE: PlatformIconCustomizationManager? = null
        
        fun getInstance(context: Context): PlatformIconCustomizationManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PlatformIconCustomizationManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * 默认启用的平台
     */
    private val defaultEnabledPlatforms = setOf(
        "抖音", "小红书", "YouTube", "哔哩哔哩", "快手", "微博", "豆瓣"
    )
    
    /**
     * 检查是否启用了定制功能
     */
    fun isCustomizationEnabled(): Boolean {
        return prefs.getBoolean(KEY_CUSTOMIZATION_ENABLED, true)
    }
    
    /**
     * 设置是否启用定制功能
     */
    fun setCustomizationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CUSTOMIZATION_ENABLED, enabled).apply()
        Log.d(TAG, "Customization enabled: $enabled")
    }
    
    /**
     * 获取启用的平台列表
     */
    fun getEnabledPlatforms(): Set<String> {
        val enabledPlatforms = prefs.getStringSet(KEY_ENABLED_PLATFORMS, defaultEnabledPlatforms)
        return enabledPlatforms ?: defaultEnabledPlatforms
    }
    
    /**
     * 设置启用的平台列表
     */
    fun setEnabledPlatforms(platforms: Set<String>) {
        prefs.edit().putStringSet(KEY_ENABLED_PLATFORMS, platforms).apply()
        Log.d(TAG, "Enabled platforms: $platforms")
    }
    
    /**
     * 添加平台到启用列表
     */
    fun addPlatform(platformName: String) {
        val currentPlatforms = getEnabledPlatforms().toMutableSet()
        currentPlatforms.add(platformName)
        setEnabledPlatforms(currentPlatforms)
        Log.d(TAG, "Added platform: $platformName")
    }
    
    /**
     * 从启用列表中移除平台
     */
    fun removePlatform(platformName: String) {
        val currentPlatforms = getEnabledPlatforms().toMutableSet()
        currentPlatforms.remove(platformName)
        setEnabledPlatforms(currentPlatforms)
        Log.d(TAG, "Removed platform: $platformName")
    }
    
    /**
     * 切换平台启用状态
     */
    fun togglePlatform(platformName: String): Boolean {
        val currentPlatforms = getEnabledPlatforms()
        return if (currentPlatforms.contains(platformName)) {
            removePlatform(platformName)
            false
        } else {
            addPlatform(platformName)
            true
        }
    }
    
    /**
     * 检查平台是否启用
     */
    fun isPlatformEnabled(platformName: String): Boolean {
        return getEnabledPlatforms().contains(platformName)
    }
    
    /**
     * 重置为默认设置
     */
    fun resetToDefault() {
        setEnabledPlatforms(defaultEnabledPlatforms)
        setCustomizationEnabled(true)
        Log.d(TAG, "Reset to default settings")
    }
    
    /**
     * 获取平台显示状态文本
     */
    fun getPlatformStatusText(platformName: String): String {
        return if (isPlatformEnabled(platformName)) {
            "取消到AI回复"
        } else {
            "添加到AI回复"
        }
    }
    
    /**
     * 过滤平台列表，只返回启用的平台
     */
    fun filterEnabledPlatforms(allPlatforms: List<PlatformJumpManager.PlatformInfo>): List<PlatformJumpManager.PlatformInfo> {
        if (!isCustomizationEnabled()) {
            return allPlatforms
        }
        
        val enabledPlatforms = getEnabledPlatforms()
        return allPlatforms.filter { it.name in enabledPlatforms }
    }
}
