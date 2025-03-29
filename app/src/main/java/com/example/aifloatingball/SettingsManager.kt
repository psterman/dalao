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

class SettingsManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val appContext = context.applicationContext
    private val gson = Gson()
    private val listeners = mutableMapOf<String, MutableList<(String, Any?) -> Unit>>()
    
    companion object {
        private const val PREFS_NAME = "settings"
        private const val KEY_FLOATING_BALL_SIZE = "floating_ball_size"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_PRIVACY_MODE = "privacy_mode"
        private const val KEY_AUTO_START = "auto_start"
        private const val KEY_ENGINE_ORDER = "engine_order"
        private const val KEY_AUTO_HIDE = "auto_hide"
        private const val KEY_LAYOUT_THEME = "layout_theme"
        private const val KEY_ENABLED_ENGINES = "enabled_engines"
        private const val KEY_LEFT_HANDED_MODE = "left_handed_mode"
        private const val KEY_SEARCH_MODE = "search_mode"
        private const val KEY_DEFAULT_SEARCH_ENGINE = "default_search_engine"
        private const val KEY_DEFAULT_SEARCH_MODE = "default_search_mode"
        private const val KEY_HOME_PAGE_URL = "home_page_url"
        private const val KEY_MENU_LAYOUT = "menu_layout"
        
        @Volatile
        private var instance: SettingsManager? = null
        
        fun getInstance(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext).also { instance = it }
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
    fun saveEngineOrder(engines: List<SearchEngine>) {
        val json = gson.toJson(engines)
        prefs.edit().putString(KEY_ENGINE_ORDER, json).apply()
    }
    
    fun getEngineOrder(): List<SearchEngine> {
        val json = prefs.getString(KEY_ENGINE_ORDER, null)
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
    
    // 默认AI模式设置
    fun isDefaultAIMode(): Boolean {
        val isAIMode = prefs.getBoolean(KEY_DEFAULT_SEARCH_MODE, false)
        Log.d("SettingsManager", "检查当前搜索模式: ${if (isAIMode) "AI模式" else "普通模式"}")
        return isAIMode
    }
    
    fun setDefaultAIMode(enabled: Boolean) {
        Log.d("SettingsManager", "设置AI搜索模式=${enabled}")
        // 使用commit()而不是apply()，确保立即生效
        prefs.edit().putBoolean(KEY_DEFAULT_SEARCH_MODE, enabled).commit()
        
        // 发送全局广播通知SearchActivity
        val intent = Intent()
        // 设置明确的action
        intent.action = "com.example.aifloatingball.SEARCH_MODE_CHANGED"
        // 添加包名，确保广播能被正确路由
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        // 设置明确的组件 - 更新为aifloatingball包路径
        intent.setClassName("com.example.aifloatingball", "com.example.aifloatingball.SearchActivity")
        intent.putExtra("is_ai_mode", enabled)
        Log.d("SettingsManager", "向SearchActivity发送搜索模式变更广播: is_ai_mode=$enabled")
        appContext.sendBroadcast(intent)
    }

    var isLeftHandedMode: Boolean
        get() = prefs.getBoolean(KEY_LEFT_HANDED_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_LEFT_HANDED_MODE, value).apply()

    fun setSearchMode(isAIMode: Boolean) {
        prefs.edit().putBoolean(KEY_SEARCH_MODE, isAIMode).apply()
    }

    fun getSearchMode(): Boolean {
        return prefs.getBoolean(KEY_SEARCH_MODE, true)
    }

    fun getString(key: String, defaultValue: String): String {
        return prefs.getString(key, defaultValue) ?: defaultValue
    }

    fun getBoolean(key: String, defaultValue: Boolean): Boolean {
        return prefs.getBoolean(key, defaultValue)
    }

    fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }

    // Combined putBoolean method with notification functionality
    fun putBoolean(key: String, value: Boolean) {
        // 使用commit()而不是apply()，确保立即生效
        prefs.edit().putBoolean(key, value).commit()
        
        // 记录设置变更
        Log.d("SettingsManager", "更新布尔值设置: $key = $value")
        
        // 如果是默认搜索模式设置，发送广播通知
        if (key == KEY_DEFAULT_SEARCH_MODE) {
            Log.d("SettingsManager", "检测到默认搜索模式变更，发送广播")
            val intent = Intent()
            // 设置明确的action
            intent.action = "com.example.aifloatingball.SEARCH_MODE_CHANGED"
            // 添加包名，确保广播能被正确路由
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            // 设置明确的组件 - 更新为aifloatingball包路径
            intent.setClassName("com.example.aifloatingball", "com.example.aifloatingball.SearchActivity")
            intent.putExtra("is_ai_mode", value)
            Log.d("SettingsManager", "向SearchActivity发送搜索模式变更广播: is_ai_mode=$value")
            appContext.sendBroadcast(intent)
        }
        
        notifyListeners(key, value)
    }

    // 主页URL设置
    fun getHomePageUrl(): String {
        return prefs.getString(KEY_HOME_PAGE_URL, "") ?: ""
    }

    fun setHomePageUrl(url: String) {
        prefs.edit().putString(KEY_HOME_PAGE_URL, url).apply()
    }

    // 获取默认页面设置
    fun getDefaultPage(): String {
        return try {
            prefs.getString(KEY_DEFAULT_SEARCH_MODE, "home")
        } catch (e: ClassCastException) {
            // 如果存储的是布尔值，则转换为对应的字符串
            if (prefs.getBoolean(KEY_DEFAULT_SEARCH_MODE, false)) "search" else "home"
        } ?: "home"
    }

    fun setDefaultPage(page: String) {
        prefs.edit().putString(KEY_DEFAULT_SEARCH_MODE, page).apply()
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
        return prefs.getString(KEY_MENU_LAYOUT, "mixed") ?: "mixed"
    }

    fun setMenuLayout(layout: String) {
        prefs.edit().putString(KEY_MENU_LAYOUT, layout).apply()
    }
}