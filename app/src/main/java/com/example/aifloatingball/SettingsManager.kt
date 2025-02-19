package com.example.aifloatingball

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SettingsManager private constructor(context: Context) {
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    private val appContext = context.applicationContext
    private val gson = Gson()
    
    companion object {
        private const val KEY_FLOATING_BALL_SIZE = "floating_ball_size"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_PRIVACY_MODE = "privacy_mode"
        private const val KEY_AUTO_START = "auto_start"
        private const val KEY_ENGINE_ORDER = "engine_order"
        private const val KEY_AUTO_HIDE = "auto_hide"
        private const val KEY_LAYOUT_THEME = "layout_theme"
        private const val KEY_ENABLED_ENGINES = "enabled_engines"
        
        @Volatile
        private var instance: SettingsManager? = null
        
        fun getInstance(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context).also { instance = it }
            }
        }
    }
    
    // 悬浮球大小设置 (30-100)
    fun getFloatingBallSize(): Int = prefs.getInt(KEY_FLOATING_BALL_SIZE, 50)
    
    fun setFloatingBallSize(size: Int) {
        prefs.edit().putInt(KEY_FLOATING_BALL_SIZE, size).apply()
    }

    // 主题设置
    fun getThemeMode(): String = prefs.getString(KEY_THEME_MODE, "system") ?: "system"
    
    fun setThemeMode(mode: String) {
        prefs.edit().putString(KEY_THEME_MODE, mode).apply()
    }

    // 隐私模式设置
    fun getPrivacyMode(): Boolean = prefs.getBoolean(KEY_PRIVACY_MODE, false)
    
    fun setPrivacyMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PRIVACY_MODE, enabled).apply()
    }

    // 开机自启动设置
    fun getAutoStart(): Boolean = prefs.getBoolean(KEY_AUTO_START, false)
    
    fun setAutoStart(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_START, enabled).apply()
    }
    
    // 搜索引擎排序设置
    fun getEngineOrder(): List<AIEngine> {
        val json = prefs.getString(KEY_ENGINE_ORDER, null)
        return if (json != null) {
            try {
                val type = object : TypeToken<List<AIEngine>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                AIEngineConfig.engines
            }
        } else {
            AIEngineConfig.engines
        }
    }
    
    fun saveEngineOrder(engines: List<AIEngine>) {
        val json = gson.toJson(engines)
        prefs.edit().putString(KEY_ENGINE_ORDER, json).apply()
    }
    
    // 自动隐藏设置
    fun setAutoHide(autoHide: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_HIDE, autoHide).apply()
    }
    
    fun getAutoHide(): Boolean = prefs.getBoolean(KEY_AUTO_HIDE, false)

    // 布局主题设置
    fun getLayoutTheme(): String = prefs.getString(KEY_LAYOUT_THEME, "fold") ?: "fold"
    
    fun setLayoutTheme(theme: String) {
        prefs.edit().putString(KEY_LAYOUT_THEME, theme).apply()
    }

    // 获取启用的搜索引擎列表
    fun getEnabledEngines(): Set<String> {
        return prefs.getStringSet(KEY_ENABLED_ENGINES, null) ?: 
               getEngineOrder().map { it.name }.toSet() // 默认全部启用
    }
    
    // 保存启用的搜索引擎列表
    fun saveEnabledEngines(enabledEngines: Set<String>) {
        prefs.edit().putStringSet(KEY_ENABLED_ENGINES, enabledEngines).apply()
    }

    // 获取经过筛选的搜索引擎列表（只返回启用的引擎）
    fun getFilteredEngineOrder(): List<AIEngine> {
        val enabledEngines = getEnabledEngines()
        return getEngineOrder().filter { it.name in enabledEngines }
    }
} 