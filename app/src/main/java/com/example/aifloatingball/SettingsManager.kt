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
        
        @Volatile
        private var instance: SettingsManager? = null
        
        fun getInstance(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext).also { instance = it }
            }
        }
        
        // 默认值常量
        private const val DEFAULT_THEME_MODE = 0 // 默认主题模式
        
        // 主题模式常量
        const val THEME_MODE_DEFAULT = 0
        const val THEME_MODE_LIGHT = 1
        const val THEME_MODE_DARK = 2
    }
    
    // 悬浮球大小设置
    fun getBallSize(): Int {
        return prefs.getInt("ball_size", DEFAULT_BALL_SIZE)
    }
    
    fun setBallSize(size: Int) {
        prefs.edit().putInt("ball_size", size).apply()
    }

    // 主题设置
    fun getLayoutTheme(): Int {
        return prefs.getInt("layout_theme", DEFAULT_LAYOUT_THEME)
    }
    
    fun saveLayoutTheme(theme: Int) {
        prefs.edit().putInt("layout_theme", theme).apply()
    }
    
    // 深色模式设置
    fun getDarkMode(): Int {
        return prefs.getInt("dark_mode", 0)
    }
    
    fun setDarkMode(mode: Int) {
        prefs.edit().putInt("dark_mode", mode).apply()
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
    
    // 搜索引擎排序设置
    fun saveEngineOrder(engines: List<SearchEngine>) {
        val json = gson.toJson(engines)
        prefs.edit().putString("engine_order", json).apply()
    }
    
    fun getEngineOrder(): List<SearchEngine> {
        val json = prefs.getString("engine_order", null)
        return if (json != null) {
            try {
                val type = object : TypeToken<List<SearchEngine>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                if (isDefaultAIMode()) {
                    AISearchEngine.DEFAULT_AI_ENGINES
                } else {
                    SearchEngine.NORMAL_SEARCH_ENGINES
                }
            }
        } else {
            if (isDefaultAIMode()) {
                AISearchEngine.DEFAULT_AI_ENGINES
            } else {
                SearchEngine.NORMAL_SEARCH_ENGINES
            }
        }
    }

    // 获取经过筛选的搜索引擎列表（根据当前模式返回AI或普通搜索引擎）
    fun getFilteredEngineOrder(): List<Any> {
        val isAIMode = isDefaultAIMode()
        
        Log.d("SettingsManager", "开始获取搜索引擎列表，当前模式=${if (isAIMode) "AI模式" else "普通模式"}")
        
        return try {
            if (isAIMode) {
                // AI模式下返回AI搜索引擎列表
                val aiEngines = AISearchEngine.DEFAULT_AI_ENGINES
                
                if (aiEngines.isEmpty()) {
                    Log.e("SettingsManager", "AI搜索引擎列表为空")
                    throw Exception("AI搜索引擎列表为空")
                }
                
                // 添加日志，帮助调试
                Log.d("SettingsManager", "成功获取AI搜索引擎列表: ${aiEngines.size}个引擎")
                
                // 遍历并打印每个AI引擎
                aiEngines.forEachIndexed { index, engine ->
                    Log.d("SettingsManager", "AI引擎 $index: ${engine.name} - ${engine.url}")
                }
                
                return aiEngines
            } else {
                // 普通模式下返回普通搜索引擎列表
                val normalEngines = SearchActivity.NORMAL_SEARCH_ENGINES
                
                if (normalEngines.isEmpty()) {
                    Log.e("SettingsManager", "普通搜索引擎列表为空")
                    throw Exception("普通搜索引擎列表为空")
                }
                
                // 添加日志，帮助调试
                Log.d("SettingsManager", "成功获取普通搜索引擎列表: ${normalEngines.size}个引擎")
                
                // 遍历并打印每个普通引擎
                normalEngines.forEachIndexed { index, engine ->
                    Log.d("SettingsManager", "普通引擎 $index: ${engine.name} - ${engine.url}")
                }
                
                return normalEngines
            }
        } catch (e: Exception) {
            // 出错时返回至少一个默认搜索引擎
            Log.e("SettingsManager", "加载搜索引擎列表失败: ${e.message}", e)
            if (isAIMode) {
                listOf(AISearchEngine("ChatGPT", "https://chat.openai.com", R.drawable.ic_chatgpt, "ChatGPT AI聊天"))
            } else {
                listOf(SearchEngine("百度", "https://www.baidu.com/s?wd=", R.drawable.ic_search, "百度搜索"))
            }
        }
    }
    
    // 获取启用的搜索引擎列表
    fun getEnabledEngines(): Set<String> {
        return prefs.getStringSet("enabled_engines", null) ?: 
               getEngineOrder().map { it.name }.toSet() // 默认全部启用
    }
    
    // 保存启用的搜索引擎列表
    fun saveEnabledEngines(enabledEngines: Set<String>) {
        prefs.edit().putStringSet("enabled_engines", enabledEngines).apply()
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

    // 主题模式设置
    fun getThemeMode(): Int {
        return prefs.getInt("theme_mode", DEFAULT_THEME_MODE)
    }
    
    fun setThemeMode(mode: Int) {
        prefs.edit().putInt("theme_mode", mode).apply()
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
}