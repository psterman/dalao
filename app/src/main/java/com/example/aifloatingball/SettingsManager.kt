package com.example.aifloatingball

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.SearchActivity
import android.util.Log
import android.content.Intent
import com.example.aifloatingball.model.MenuItem
import androidx.appcompat.app.AppCompatDelegate

class SettingsManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val appContext = context.applicationContext
    private val gson = Gson()
    private val listeners = mutableMapOf<String, MutableList<(String, Any?) -> Unit>>()
    
    companion object {
        private const val PREFS_NAME = "settings"
        private const val DEFAULT_BALL_SIZE = 100
        private const val DEFAULT_LAYOUT_THEME = 0
        private const val DEFAULT_PAGE = "home"
        
        // 主题模式常量
        const val THEME_MODE_SYSTEM = 0    // 跟随系统
        const val THEME_MODE_LIGHT = 1     // 浅色主题
        const val THEME_MODE_DARK = 2      // 深色主题
        const val THEME_MODE_DEFAULT = THEME_MODE_SYSTEM  // 默认主题模式
        
        @Volatile
        private var instance: SettingsManager? = null
        
        fun getInstance(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    // 悬浮球大小设置
    fun getBallSize(): Int {
        return prefs.getInt("ball_size", DEFAULT_BALL_SIZE)
    }
    
    fun setBallSize(size: Int) {
        prefs.edit().putInt("ball_size", size).apply()
    }

    // 统一主题设置
    fun getThemeMode(): Int {
        return prefs.getInt("theme_mode", THEME_MODE_SYSTEM)
    }
    
    fun setThemeMode(mode: Int) {
        prefs.edit().putInt("theme_mode", mode).apply()
        // 根据主题模式设置应用主题
        when (mode) {
            THEME_MODE_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_MODE_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    // 隐私模式设置
    fun isPrivacyModeEnabled(): Boolean {
        return prefs.getBoolean("privacy_mode", false)
    }
    
    fun setPrivacyModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("privacy_mode", enabled).apply()
    }
    
    // 搜索引擎设置
    fun getDefaultSearchEngine(): String {
        return prefs.getString("default_search_engine", "baidu") ?: "baidu"
    }
    
    fun setDefaultSearchEngine(engine: String) {
        prefs.edit().putString("default_search_engine", engine).apply()
    }
    
    // 获取当前模式下的搜索引擎列表
    fun getCurrentSearchEngines(): List<Any> {
        val isAIMode = isDefaultAIMode()
        val enabledEngines = getEnabledEngines()
        
        return try {
            val engines = if (isAIMode) {
                AISearchEngine.DEFAULT_AI_ENGINES
            } else {
                SearchEngine.DEFAULT_ENGINES
            }
            
            // 只返回已启用的搜索引擎
            engines.filter { engine -> enabledEngines.contains(engine.name) }
        } catch (e: Exception) {
            Log.e("SettingsManager", "加载搜索引擎列表失败: ${e.message}", e)
            // 返回默认搜索引擎
            if (isAIMode) {
                listOf(AISearchEngine("ChatGPT", "https://chat.openai.com", R.drawable.ic_chatgpt, "ChatGPT AI聊天"))
            } else {
                listOf(SearchEngine("百度", "https://www.baidu.com/s?wd=", R.drawable.ic_search, "百度搜索"))
            }
        }
    }
    
    // 获取和保存启用的搜索引擎
    fun getEnabledEngines(): Set<String> {
        return prefs.getStringSet("enabled_engines", null) ?: 
               SearchEngine.DEFAULT_ENGINES.map { it.name }.toSet()
    }
    
    fun saveEnabledEngines(enabledEngines: Set<String>) {
        prefs.edit().putStringSet("enabled_engines", enabledEngines).apply()
    }
    
    @Deprecated("Use getCurrentSearchEngines() instead", ReplaceWith("getCurrentSearchEngines()"))
    fun getFilteredEngineOrder(): List<Any> = getCurrentSearchEngines()
    
    @Deprecated("Use getCurrentSearchEngines() instead", ReplaceWith("getCurrentSearchEngines()"))
    fun getEngineOrder(): List<SearchEngine> = getCurrentSearchEngines() as List<SearchEngine>
    
    @Deprecated("Use saveEnabledEngines() instead", ReplaceWith("saveEnabledEngines(engines.map { it.name }.toSet())"))
    fun saveEngineOrder(engines: List<SearchEngine>) {
        saveEnabledEngines(engines.map { it.name }.toSet())
    }
    
    // 清除所有设置
    fun clearAllSettings() {
        prefs.edit().clear().apply()
    }
    
    // 默认页面设置
    fun getDefaultPage(): String {
        return prefs.getString("default_page", DEFAULT_PAGE) ?: DEFAULT_PAGE
    }
    
    fun saveDefaultPage(page: String) {
        prefs.edit().putString("default_page", page).apply()
    }
    
    // 剪贴板监听设置
    fun isClipboardListenerEnabled(): Boolean {
        return prefs.getBoolean("clipboard_listener", true)
    }
    
    fun setClipboardListenerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("clipboard_listener", enabled).apply()
    }
    
    // 左手模式设置
    fun isLeftHandedMode(): Boolean {
        return prefs.getBoolean("left_handed_mode", false)
    }
    
    fun setLeftHandedMode(enabled: Boolean) {
        prefs.edit().putBoolean("left_handed_mode", enabled).apply()
    }
    
    // AI模式设置
    fun isDefaultAIMode(): Boolean {
        return prefs.getBoolean("default_search_mode", false)
    }
    
    fun setDefaultAIMode(enabled: Boolean) {
        prefs.edit().putBoolean("default_search_mode", enabled).apply()
    }
    
    // 自动粘贴设置
    fun isAutoPasteEnabled(): Boolean {
        return prefs.getBoolean("auto_paste", true)
    }
    
    fun setAutoPasteEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_paste", enabled).apply()
    }
    
    // 自动隐藏设置
    fun getAutoHide(): Boolean = prefs.getBoolean("auto_hide", false)
    
    fun setAutoHide(autoHide: Boolean) {
        prefs.edit().putBoolean("auto_hide", autoHide).apply()
    }
    
    fun <T> registerOnSettingChangeListener(key: String, listener: (String, T) -> Unit) {
        if (!listeners.containsKey(key)) {
            listeners[key] = mutableListOf()
        }
        @Suppress("UNCHECKED_CAST")
        listeners[key]?.add(listener as (String, Any?) -> Unit)
    }

    private fun notifyListeners(key: String, value: Any?) {
        listeners[key]?.forEach { it(key, value) }
    }

    fun saveMenuItems(items: List<MenuItem>) {
        val editor = prefs.edit()
        editor.putString("menu_items", gson.toJson(items))
        editor.apply()
    }

    fun getMenuItems(): List<MenuItem> {
        val json = prefs.getString("menu_items", "")
        if (json.isNullOrEmpty()) return emptyList()
        
        return try {
            val type = object : TypeToken<List<MenuItem>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 添加菜单布局设置方法
    fun getMenuLayout(): String {
        return prefs.getString("menu_layout", "mixed") ?: "mixed"
    }

    fun setMenuLayout(layout: String) {
        prefs.edit().putString("menu_layout", layout).apply()
    }

    // SharedPreferences 辅助方法
    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }
    
    fun putBoolean(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }
    
    fun getString(key: String, defaultValue: String?): String? {
        return prefs.getString(key, defaultValue)
    }
    
    fun putString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    // 移除旧的主题相关方法
    @Deprecated("Use getThemeMode() instead", ReplaceWith("getThemeMode()"))
    fun getLayoutTheme(): Int = getThemeMode()
    
    @Deprecated("Use setThemeMode() instead", ReplaceWith("setThemeMode(theme)"))
    fun saveLayoutTheme(theme: Int) = setThemeMode(theme)
    
    @Deprecated("Use getThemeMode() instead", ReplaceWith("getThemeMode()"))
    fun getDarkMode(): Int = getThemeMode()
    
    @Deprecated("Use setThemeMode() instead", ReplaceWith("setThemeMode(mode)"))
    fun setDarkMode(mode: Int) = setThemeMode(mode)
    
    // 窗口数量设置
    fun getDefaultWindowCount(): Int {
        return prefs.getString("default_window_count", "2")?.toIntOrNull() ?: 2
    }
    
    fun setDefaultWindowCount(count: Int) {
        prefs.edit().putString("default_window_count", count.toString()).apply()
        notifyListeners("default_window_count", count)
    }
    
    // 窗口搜索引擎设置
    fun getLeftWindowSearchEngine(): String {
        return prefs.getString("left_window_search_engine", "baidu") ?: "baidu"
    }
    
    fun setLeftWindowSearchEngine(engine: String) {
        prefs.edit().putString("left_window_search_engine", engine).apply()
        notifyListeners("left_window_search_engine", engine)
    }
    
    fun getCenterWindowSearchEngine(): String {
        return prefs.getString("center_window_search_engine", "bing") ?: "bing"
    }
    
    fun setCenterWindowSearchEngine(engine: String) {
        prefs.edit().putString("center_window_search_engine", engine).apply()
        notifyListeners("center_window_search_engine", engine)
    }
    
    fun getRightWindowSearchEngine(): String {
        return prefs.getString("right_window_search_engine", "google") ?: "google"
    }
    
    fun setRightWindowSearchEngine(engine: String) {
        prefs.edit().putString("right_window_search_engine", engine).apply()
        notifyListeners("right_window_search_engine", engine)
    }

    fun getEnabledSearchEngines(): Set<String> {
        return prefs.getStringSet("enabled_search_engines", setOf("baidu", "google")) ?: setOf("baidu", "google")
    }

    fun setEnabledSearchEngines(engines: Set<String>) {
        prefs.edit().putStringSet("enabled_search_engines", engines).apply()
    }

    // 获取指定位置的搜索引擎
    fun getSearchEngineForPosition(position: Int): String {
        return when (position) {
            0 -> getLeftWindowSearchEngine()
            1 -> getCenterWindowSearchEngine()
            2 -> getRightWindowSearchEngine()
            else -> getDefaultSearchEngine()
        }
    }

    // 设置指定位置的搜索引擎
    fun setSearchEngineForPosition(position: Int, engine: String) {
        when (position) {
            0 -> setLeftWindowSearchEngine(engine)
            1 -> setCenterWindowSearchEngine(engine)
            2 -> setRightWindowSearchEngine(engine)
        }
    }
    
    // 默认浏览器设置
    fun getDefaultBrowser(): String {
        return prefs.getString("default_browser", "system") ?: "system"
    }
    
    fun setDefaultBrowser(browser: String) {
        prefs.edit().putString("default_browser", browser).apply()
        notifyListeners("default_browser", browser)
    }
}