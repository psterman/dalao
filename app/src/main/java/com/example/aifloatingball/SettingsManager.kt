package com.example.aifloatingball

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.model.BaseSearchEngine
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
        private const val DEFAULT_BALL_ALPHA = 85
        private const val DEFAULT_LAYOUT_THEME = 0
        private const val DEFAULT_PAGE = "home"
        private const val KEY_SEARCH_HISTORY = "search_history"
        
        // API相关常量
        private const val KEY_DEEPSEEK_API_KEY = "deepseek_api_key"
        private const val KEY_DEEPSEEK_API_URL = "deepseek_api_url"
        private const val KEY_CHATGPT_API_KEY = "chatgpt_api_key"
        private const val KEY_CHATGPT_API_URL = "chatgpt_api_url"
        
        // 主题模式常量
        const val THEME_MODE_SYSTEM = 0    // 跟随系统
        const val THEME_MODE_LIGHT = 1     // 浅色主题
        const val THEME_MODE_DARK = 2      // 深色主题
        const val THEME_MODE_DEFAULT = THEME_MODE_SYSTEM  // 默认主题模式
        
        // 默认API地址
        const val DEFAULT_DEEPSEEK_API_URL = "https://api.deepseek.com/v1/chat/completions"
        const val DEFAULT_CHATGPT_API_URL = "https://api.openai.com/v1/chat/completions"
        
        @Volatile
        private var instance: SettingsManager? = null
        
        fun getInstance(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    // 悬浮球透明度设置
    fun getBallAlpha(): Int {
        return prefs.getInt("ball_alpha", DEFAULT_BALL_ALPHA)
    }
    
    fun setBallAlpha(alpha: Int) {
        prefs.edit().putInt("ball_alpha", alpha).apply()
        notifyListeners("ball_alpha", alpha)
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
        notifyListeners("default_search_engine", engine)
    }

    // 获取搜索引擎实例
    fun getSearchEngineById(id: String): BaseSearchEngine {
        return if (id.startsWith("ai_")) {
            val aiName = id.substring(3)
            AISearchEngine.DEFAULT_AI_ENGINES.find { it.name == aiName }
                ?: AISearchEngine.DEFAULT_AI_ENGINES.firstOrNull() // 使用 firstOrNull() 安全地获取第一个元素
                ?: AISearchEngine( // 如果找不到任何AI引擎，提供一个硬编码的默认AI引擎
                    name = "ChatGPT",
                    url = "https://chat.openai.com",
                    iconResId = R.drawable.ic_chatgpt,
                    description = "ChatGPT AI助手",
                    searchUrl = "https://chat.openai.com/search?q={query}"
                )
        } else {
            SearchEngine.DEFAULT_ENGINES.find { it.name == id }
                ?: SearchEngine.DEFAULT_ENGINES.firstOrNull() // 使用 firstOrNull() 安全地获取第一个元素
                ?: SearchEngine( // 如果找不到任何普通搜索引擎，提供一个硬编码的默认普通搜索引擎
                    name = "baidu",
                    displayName = "百度",
                    url = "https://www.baidu.com",
                    iconResId = R.drawable.ic_baidu,
                    description = "百度搜索",
                    searchUrl = "https://www.baidu.com/s?wd={query}"
                )
        }
    }

    // 获取当前模式下的搜索引擎列表
    fun getCurrentSearchEngines(): List<BaseSearchEngine> {
        val isAIMode = isDefaultAIMode()
        
        return try {
            val engines = if (isAIMode) {
                val enabledAIEngines = getEnabledAIEngines()
                AISearchEngine.DEFAULT_AI_ENGINES.filter { engine -> enabledAIEngines.contains(engine.name) }
            } else {
                val enabledSearchEngines = getEnabledSearchEngines()
                SearchEngine.DEFAULT_ENGINES.filter { engine -> enabledSearchEngines.contains(engine.name) }
            }
            
            // 如果没有已启用的引擎，返回默认的几个
            if (engines.isEmpty()) {
                if (isAIMode) {
                    AISearchEngine.DEFAULT_AI_ENGINES.take(3)
                } else {
                    SearchEngine.DEFAULT_ENGINES.take(3)
                }
            } else {
                engines
            }
        } catch (e: Exception) {
            Log.e("SettingsManager", "加载搜索引擎列表失败: ${e.message}", e)
            // 返回默认搜索引擎
            if (isAIMode) {
                listOf(AISearchEngine(
                    name = "ChatGPT",
                    url = "https://chat.openai.com",
                    iconResId = R.drawable.ic_chatgpt,
                    description = "ChatGPT AI助手",
                    searchUrl = "https://chat.openai.com/search?q={query}"
                ))
            } else {
                listOf(SearchEngine(
                    name = "baidu",
                    displayName = "百度",
                    url = "https://www.baidu.com",
                    iconResId = R.drawable.ic_baidu,
                    description = "百度搜索",
                    searchUrl = "https://www.baidu.com/s?wd={query}"
                ))
            }
        }
    }
    
    // 获取和保存启用的搜索引擎
    fun getEnabledEngines(): Set<String> {
        return prefs.getStringSet("enabled_engines", emptySet()) ?: emptySet()
    }
    
    fun saveEnabledEngines(enabledEngines: Set<String>) {
        prefs.edit().putStringSet("enabled_engines", enabledEngines).apply()
    }
    
    // 获取已启用的普通搜索引擎
    fun getEnabledSearchEngines(): Set<String> {
        return getEnabledEngines()
    }
    
    // 获取已启用的AI搜索引擎
    fun getEnabledAIEngines(): Set<String> {
        return prefs.getStringSet("enabled_ai_engines", emptySet()) ?: emptySet()
    }
    
    // 保存已启用的AI搜索引擎
    fun saveEnabledAIEngines(enabledEngines: Set<String>) {
        prefs.edit().putStringSet("enabled_ai_engines", enabledEngines).apply()
    }
    
    // 获取所有已启用的搜索引擎（包括普通搜索引擎和AI搜索引擎）
    fun getAllEnabledEngines(): Set<String> {
        val result = mutableSetOf<String>()
        result.addAll(getEnabledSearchEngines()) // 普通搜索引擎
        result.addAll(getEnabledAIEngines()) // AI搜索引擎
        return result
    }
    
    @Deprecated("Use getCurrentSearchEngines() instead", ReplaceWith("getCurrentSearchEngines()"))
    fun getFilteredEngineOrder(): List<BaseSearchEngine> = getCurrentSearchEngines()
    
    @Deprecated("Use getCurrentSearchEngines() instead", ReplaceWith("getCurrentSearchEngines()"))
    fun getEngineOrder(): List<SearchEngine> = getCurrentSearchEngines().filterIsInstance<SearchEngine>()
    
    @Deprecated("Use saveEnabledEngines() instead", ReplaceWith("saveEnabledEngines(engines.map { it.name }.toSet())"))
    fun saveEngineOrder(engines: List<SearchEngine>) {
        saveEnabledEngines(engines.map { it.name }.toSet())
    }
    
    // 是否显示AI搜索引擎分类
    fun showAIEngineCategory(): Boolean {
        return prefs.getBoolean("show_ai_engine_category", true)
    }
    
    fun setShowAIEngineCategory(show: Boolean) {
        prefs.edit().putBoolean("show_ai_engine_category", show).apply()
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
    
    // 当前AI模式状态
    fun getIsAIMode(): Boolean {
        return prefs.getBoolean("is_ai_mode", false)
    }
    
    fun setIsAIMode(enabled: Boolean) {
        prefs.edit().putBoolean("is_ai_mode", enabled).apply()
        notifyListeners("is_ai_mode", enabled)
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

    // 获取选择的搜索引擎（旧的方法，使用不同的键）
    fun getSelectedSearchEngines(): Set<String> {
        return prefs.getStringSet("enabled_search_engines", setOf("baidu", "google")) ?: setOf("baidu", "google")
    }

    fun setSelectedSearchEngines(engines: Set<String>) {
        prefs.edit().putStringSet("enabled_search_engines", engines).apply()
    }
    
    /**
     * 获取启用的搜索引擎组合（显示在FloatingWindowService窗口中的快捷方式）
     */
    fun getEnabledSearchEngineGroups(): Set<String> {
        return prefs.getStringSet("enabled_search_engine_groups", emptySet()) ?: emptySet()
    }
    
    /**
     * 设置启用的搜索引擎组合
     */
    fun setEnabledSearchEngineGroups(groupNames: Set<String>) {
        prefs.edit().putStringSet("enabled_search_engine_groups", groupNames).apply()
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

    // 获取启用的应用搜索列表
    fun getEnabledAppSearches(): Set<String> {
        return prefs.getStringSet("enabled_app_searches", getDefaultAppSearches()) ?: getDefaultAppSearches()
    }

    // 设置启用的应用搜索列表
    fun setEnabledAppSearches(apps: Set<String>) {
        prefs.edit().putStringSet("enabled_app_searches", apps).apply()
        notifyListeners("enabled_app_searches", apps)
    }

    // 获取应用搜索排序
    fun getAppSearchOrder(): List<String> {
        val json = prefs.getString("app_search_order", "") ?: ""
        if (json.isEmpty()) {
            return getDefaultAppSearchOrder()
        }
        return try {
            gson.fromJson<List<String>>(json, object : TypeToken<List<String>>() {}.type)
        } catch (e: Exception) {
            getDefaultAppSearchOrder()
        }
    }

    // 设置应用搜索排序
    fun setAppSearchOrder(order: List<String>) {
        val json = gson.toJson(order)
        prefs.edit().putString("app_search_order", json).apply()
        notifyListeners("app_search_order", order)
    }

    // 获取默认的应用搜索列表
    private fun getDefaultAppSearches(): Set<String> {
        return setOf("taobao", "pdd", "douyin", "xiaohongshu")
    }

    // 获取默认的应用搜索排序
    private fun getDefaultAppSearchOrder(): List<String> {
        return listOf("taobao", "pdd", "douyin", "xiaohongshu")
    }

    // 获取应用搜索配置
    fun getAppSearchConfig(): Map<String, AppSearchConfig> {
        return mapOf(
            "taobao" to AppSearchConfig(
                id = "taobao",
                name = "淘宝",
                packageName = "com.taobao.taobao",
                searchScheme = "taobao://search.taobao.com/search?q={query}",
                webUrl = "https://s.taobao.com/search?q={query}",
                iconResId = R.drawable.ic_taobao
            ),
            "pdd" to AppSearchConfig(
                id = "pdd",
                name = "拼多多",
                packageName = "com.xunmeng.pinduoduo",
                searchScheme = "pinduoduo://com.xunmeng.pinduoduo/search_result.html?search_key={query}",
                webUrl = "https://mobile.yangkeduo.com/search_result.html?search_key={query}",
                iconResId = R.drawable.ic_search
            ),
            "douyin" to AppSearchConfig(
                id = "douyin",
                name = "抖音",
                packageName = "com.ss.android.ugc.aweme",
                searchScheme = "snssdk1128://search?keyword={query}",
                webUrl = "https://www.douyin.com/search/{query}",
                iconResId = R.drawable.ic_douyin
            ),
            "xiaohongshu" to AppSearchConfig(
                id = "xiaohongshu",
                name = "小红书",
                packageName = "com.xingin.xhs",
                searchScheme = "xhsdiscover://search/result?keyword={query}",
                webUrl = "https://www.xiaohongshu.com/search_result?keyword={query}",
                iconResId = R.drawable.ic_xiaohongshu
            )
        )
    }

    // 数据类：应用搜索配置
    data class AppSearchConfig(
        val id: String,
        val name: String,
        val packageName: String,
        val searchScheme: String,
        val webUrl: String,
        val iconResId: Int
    )

    fun getSearchHistory(): List<Map<String, Any>> {
        val json = prefs.getString(KEY_SEARCH_HISTORY, "[]")
        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        return gson.fromJson(json, type) ?: emptyList()
    }

    fun saveSearchHistory(history: List<Map<String, Any>>) {
        val json = gson.toJson(history)
        prefs.edit().putString(KEY_SEARCH_HISTORY, json).apply()
    }

    // API密钥相关方法
    fun getDeepSeekApiKey(): String {
        return prefs.getString(KEY_DEEPSEEK_API_KEY, "") ?: ""
    }
    
    fun setDeepSeekApiKey(apiKey: String) {
        prefs.edit().putString(KEY_DEEPSEEK_API_KEY, apiKey).apply()
    }
    
    fun getChatGPTApiKey(): String {
        return prefs.getString(KEY_CHATGPT_API_KEY, "") ?: ""
    }
    
    fun setChatGPTApiKey(apiKey: String) {
        prefs.edit().putString(KEY_CHATGPT_API_KEY, apiKey).apply()
    }
    
    fun getDeepSeekApiUrl(): String {
        return prefs.getString(KEY_DEEPSEEK_API_URL, DEFAULT_DEEPSEEK_API_URL) ?: DEFAULT_DEEPSEEK_API_URL
    }
    
    fun setDeepSeekApiUrl(apiUrl: String) {
        prefs.edit().putString(KEY_DEEPSEEK_API_URL, apiUrl).apply()
    }
    
    fun getChatGPTApiUrl(): String {
        return prefs.getString(KEY_CHATGPT_API_URL, DEFAULT_CHATGPT_API_URL) ?: DEFAULT_CHATGPT_API_URL
    }
    
    fun setChatGPTApiUrl(apiUrl: String) {
        prefs.edit().putString(KEY_CHATGPT_API_URL, apiUrl).apply()
    }

    // 提供SharedPreferences实例给外部访问
    fun getSharedPreferences(): SharedPreferences {
        return prefs
    }
}