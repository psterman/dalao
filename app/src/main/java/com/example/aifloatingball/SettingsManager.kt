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
import com.example.aifloatingball.model.SearchEngineGroup
import com.example.aifloatingball.R
import android.content.res.Configuration
import com.example.aifloatingball.model.PromptProfile

class SettingsManager private constructor(context: Context) {
    private val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
    private val appContext = context.applicationContext
    private val gson = Gson()
    private val listeners = mutableMapOf<String, MutableList<(String, Any?) -> Unit>>()
    
    companion object {
        private const val TAG = "SettingsManager"
        private const val PREFS_NAME = "settings"
        private const val DEFAULT_BALL_ALPHA = 85
        private const val DEFAULT_LAYOUT_THEME = 0
        private const val DEFAULT_PAGE = "home"
        private const val KEY_SEARCH_HISTORY = "search_history"
        private const val KEY_DISPLAY_MODE = "display_mode"
        private const val DEFAULT_DISPLAY_MODE = "simple_mode"
        private const val KEY_SEARCH_ENGINE_GROUPS = "search_engine_groups"
        private const val KEY_CUSTOM_SEARCH_ENGINES = "custom_search_engines"
        private const val KEY_PROMPT_PROFILES = "prompt_profiles"
        private const val KEY_ACTIVE_PROMPT_PROFILE_ID = "active_prompt_profile_id"
        private const val KEY_CLIPBOARD_LISTENER = "clipboard_listener"
        
        // API相关常量
        private const val KEY_DEEPSEEK_API_KEY = "deepseek_api_key"
        private const val KEY_DEEPSEEK_API_URL = "deepseek_api_url"
        private const val KEY_CHATGPT_API_KEY = "chatgpt_api_key"
        private const val KEY_CHATGPT_API_URL = "chatgpt_api_url"
        
        // 主题模式常量 - 修复错乱问题
        const val THEME_MODE_SYSTEM = -1   // 跟随系统 (AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        const val THEME_MODE_LIGHT = 0     // 浅色主题 (AppCompatDelegate.MODE_NIGHT_NO) - 修复：改为0
        const val THEME_MODE_DARK = 1      // 深色主题 (AppCompatDelegate.MODE_NIGHT_YES) - 修复：改为1
        const val THEME_MODE_DEFAULT = THEME_MODE_SYSTEM  // 默认主题模式
        
        // 默认API地址
        const val DEFAULT_DEEPSEEK_API_URL = "https://api.deepseek.com/v1/chat/completions"
        const val DEFAULT_CHATGPT_API_URL = "https://api.openai.com/v1/chat/completions"
        
        // 灵动岛设置的默认值
        private const val DEFAULT_ISLAND_WIDTH = 280 // dp
        private const val DEFAULT_ISLAND_ALPHA = 255 // 0-255
        
        @Volatile
        private var instance: SettingsManager? = null
        
        fun getInstance(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context).also { instance = it }
            }
        }
    }
    
    // --- Prompt Profile Management ---

    /**
     * 获取所有用户画像
     */
    fun getAllPromptProfiles(): MutableList<PromptProfile> {
        val json = prefs.getString(KEY_PROMPT_PROFILES, null)
        return if (json != null) {
            val type = object : TypeToken<MutableList<PromptProfile>>() {}.type
            gson.fromJson(json, type)
        } else {
            // 如果没有保存任何画像，返回一个包含默认画像的列表
            mutableListOf(PromptProfile.DEFAULT)
        }
    }

    /**
     * 保存所有用户画像
     */
    fun saveAllPromptProfiles(profiles: List<PromptProfile>) {
        val json = gson.toJson(profiles)
        prefs.edit().putString(KEY_PROMPT_PROFILES, json).apply()
        notifyListeners(KEY_PROMPT_PROFILES, profiles)
    }

    /**
     * 添加或更新一个用户画像
     */
    fun savePromptProfile(profile: PromptProfile) {
        val profiles = getAllPromptProfiles()
        val index = profiles.indexOfFirst { it.id == profile.id }
        if (index != -1) {
            profiles[index] = profile
        } else {
            profiles.add(profile)
        }
        saveAllPromptProfiles(profiles)
    }

    /**
     * 删除一个用户画像
     */
    fun deletePromptProfile(profileId: String) {
        // 不允许删除默认画像
        if (profileId == PromptProfile.DEFAULT.id) {
            Log.w(TAG, "Attempted to delete the default prompt profile.")
            return
        }
        val profiles = getAllPromptProfiles()
        profiles.removeAll { it.id == profileId }
        // 如果删除的是当前激活的画像，则将激活画像重置为默认
        if (getActivePromptProfileId() == profileId) {
            setActivePromptProfileId(PromptProfile.DEFAULT.id)
        }
        saveAllPromptProfiles(profiles)
    }
    
    /**
     * 设置当前激活的用户画像ID
     */
    fun setActivePromptProfileId(profileId: String) {
        prefs.edit().putString(KEY_ACTIVE_PROMPT_PROFILE_ID, profileId).apply()
        notifyListeners(KEY_ACTIVE_PROMPT_PROFILE_ID, profileId)
    }

    /**
     * 获取当前激活的用户画像ID
     */
    fun getActivePromptProfileId(): String {
        return prefs.getString(KEY_ACTIVE_PROMPT_PROFILE_ID, PromptProfile.DEFAULT.id) ?: PromptProfile.DEFAULT.id
    }

    /**
     * 获取当前激活的用户画像实例
     * 这是SimpleModeService需要的方法
     */
    fun getPromptProfile(): PromptProfile {
        val activeId = getActivePromptProfileId()
        return getAllPromptProfiles().find { it.id == activeId } ?: PromptProfile.DEFAULT
    }


    /**
     * 获取所有搜索引擎，包括默认和自定义的。
     * 自定义搜索引擎会覆盖同名的默认搜索引擎。
     */
    fun getAllSearchEngines(): MutableList<SearchEngine> {
        val allEngines = SearchEngine.DEFAULT_ENGINES.associateBy { it.name }.toMutableMap()
        val customEngines = getCustomSearchEngines()
        customEngines.forEach { allEngines[it.name] = it }
        return allEngines.values.toMutableList()
    }
    
    // 悬浮球透明度设置
    fun getBallAlpha(): Int {
        return prefs.getInt("ball_alpha", DEFAULT_BALL_ALPHA)
    }
    
    fun setBallAlpha(alpha: Int) {
        prefs.edit().putInt("ball_alpha", alpha).apply()
        notifyListeners("ball_alpha", alpha)
    }

    // 显示模式设置
    fun getDisplayMode(): String {
        return prefs.getString(KEY_DISPLAY_MODE, DEFAULT_DISPLAY_MODE) ?: DEFAULT_DISPLAY_MODE
    }

    fun isFloatingBallEnabled(): Boolean {
        return prefs.getBoolean("floating_ball_enabled", true)
    }

    fun isDynamicIslandEnabled(): Boolean {
        return prefs.getBoolean("dynamic_island_enabled", true)
    }

    fun setDisplayMode(mode: String) {
        prefs.edit().putString(KEY_DISPLAY_MODE, mode).apply()
        notifyListeners(KEY_DISPLAY_MODE, mode)
    }

    // 左手模式
    fun isLeftHandedModeEnabled(): Boolean {
        return prefs.getBoolean("left_handed_mode", false)
    }
    
    fun setLeftHandedMode(enabled: Boolean) {
        prefs.edit().putBoolean("left_handed_mode", enabled).apply()
        notifyListeners("left_handed_mode", enabled)
    }

    // 统一主题设置
    fun getThemeMode(): Int {
        val allPrefs = prefs.all
        val themeValue = allPrefs["theme_mode"]

        return when (themeValue) {
            is String -> themeValue.toIntOrNull() ?: THEME_MODE_DEFAULT
            is Int -> themeValue
            else -> THEME_MODE_DEFAULT
        }
    }
    
    fun setThemeMode(mode: Int) {
        prefs.edit().putString("theme_mode", mode.toString()).apply()
        // 根据主题模式设置应用主题 - 修复错乱问题
        when (mode) {
            THEME_MODE_LIGHT -> {
                android.util.Log.d("SettingsManager", "Setting LIGHT mode (MODE_NIGHT_NO)")
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
            THEME_MODE_DARK -> {
                android.util.Log.d("SettingsManager", "Setting DARK mode (MODE_NIGHT_YES)")
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
            THEME_MODE_SYSTEM -> {
                android.util.Log.d("SettingsManager", "Setting SYSTEM mode (MODE_NIGHT_FOLLOW_SYSTEM)")
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
            else -> {
                android.util.Log.d("SettingsManager", "Setting default SYSTEM mode")
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
        notifyListeners("theme_mode", mode)
    }

    fun getThemeModeForWeb(): String {
        val themeMode = getThemeMode()
        return when (themeMode) {
            THEME_MODE_LIGHT -> "light"
            THEME_MODE_DARK -> "dark"
            else -> { // System default
                val nightModeFlags = appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (nightModeFlags == Configuration.UI_MODE_NIGHT_YES) {
                    "dark"
                } else {
                    "light"
                }
            }
        }
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
                listOf(AISearchEngine.DEFAULT_AI_ENGINES.first())
            } else {
                listOf(SearchEngine.DEFAULT_ENGINES.first())
            }
        }
    }
    
    // 获取已启用的普通搜索引擎
    fun getEnabledSearchEngines(): Set<String> {
        return prefs.getStringSet("enabled_search_engines", SearchEngine.DEFAULT_ENGINES.map { it.name }.toSet()) ?: emptySet()
    }
    
    // 保存已启用的普通搜索引擎
    fun saveEnabledSearchEngines(enabledEngines: Set<String>) {
        prefs.edit().putStringSet("enabled_search_engines", enabledEngines).apply()
        notifyListeners("enabled_search_engines", enabledEngines)
    }
    
    // 获取已启用的AI搜索引擎
    fun getEnabledAIEngines(): Set<String> {
        val enabledEngines = prefs.getStringSet("enabled_ai_engines", null)

        // 如果是第一次运行，初始化默认启用的AI引擎
        if (enabledEngines == null) {
            val defaultEnabledEngines = getDefaultEnabledAIEngines()
            saveEnabledAIEngines(defaultEnabledEngines)
            return defaultEnabledEngines
        }

        // 确保Kimi在已启用列表中（修复用户配置问题）
        val updatedEngines = enabledEngines.toMutableSet()
        if (!updatedEngines.contains("Kimi")) {
            updatedEngines.add("Kimi")
            saveEnabledAIEngines(updatedEngines)
            return updatedEngines
        }

        return enabledEngines
    }

    /**
     * 获取默认启用的AI引擎列表
     */
    private fun getDefaultEnabledAIEngines(): Set<String> {
        return setOf(
            "DeepSeek (API)",
            "ChatGPT (Custom)",
            "Claude (Custom)",
            "通义千问 (Custom)",
            "智谱AI (Custom)",
            "Kimi",
            "ChatGPT",
            "Claude",
            "Gemini"
        )
    }
    
    // 保存已启用的AI搜索引擎
    fun saveEnabledAIEngines(enabledEngines: Set<String>) {
        prefs.edit().putStringSet("enabled_ai_engines", enabledEngines).apply()
        notifyListeners("enabled_ai_engines", enabledEngines)
    }

    // 强制刷新AI引擎配置，确保Kimi被包含
    fun forceRefreshAIEngines() {
        val currentEngines = prefs.getStringSet("enabled_ai_engines", null)?.toMutableSet() ?: mutableSetOf()
        val defaultEngines = getDefaultEnabledAIEngines()
        
        // 合并当前配置和默认配置，确保Kimi被包含
        currentEngines.addAll(defaultEngines)
        
        // 特别确保Kimi在列表中
        if (!currentEngines.contains("Kimi")) {
            currentEngines.add("Kimi")
        }
        
        saveEnabledAIEngines(currentEngines)
    }

    /**
     * 生成统一的AI联系人ID
     * 确保所有地方使用相同的ID生成逻辑
     */
    fun generateAIContactId(aiName: String): String {
        val processedName = if (aiName.contains(Regex("[\\u4e00-\\u9fff]"))) {
            // 包含中文字符，直接使用原名称
            aiName
        } else {
            // 英文字符，转换为小写
            aiName.lowercase()
        }
        return "ai_${processedName.replace(" ", "_")}"
    }

    // AI回复字体大小相关
    private val defaultFontSize = 12f
    private val minFontSize = 8f
    private val maxFontSize = 20f
    private val fontSizeStep = 1f

    /**
     * 获取AI回复字体大小
     */
    fun getAIFontSize(): Float {
        return prefs.getFloat("ai_font_size", defaultFontSize)
    }

    /**
     * 设置AI回复字体大小
     */
    fun setAIFontSize(fontSize: Float) {
        val clampedSize = fontSize.coerceIn(minFontSize, maxFontSize)
        prefs.edit().putFloat("ai_font_size", clampedSize).apply()
        notifyListeners("ai_font_size", clampedSize)
    }

    /**
     * 增加AI回复字体大小
     */
    fun increaseAIFontSize(): Float {
        val currentSize = getAIFontSize()
        val newSize = (currentSize + fontSizeStep).coerceAtMost(maxFontSize)
        setAIFontSize(newSize)
        return newSize
    }

    /**
     * 减少AI回复字体大小
     */
    fun decreaseAIFontSize(): Float {
        val currentSize = getAIFontSize()
        val newSize = (currentSize - fontSizeStep).coerceAtLeast(minFontSize)
        setAIFontSize(newSize)
        return newSize
    }

    // 圆球位置相关
    private val defaultBallX = -1 // -1表示使用默认居中位置
    private val defaultBallY = -1 // -1表示使用默认位置

    /**
     * 获取圆球X位置
     */
    fun getBallX(): Int {
        return prefs.getInt("ball_x", defaultBallX)
    }

    /**
     * 获取圆球Y位置
     */
    fun getBallY(): Int {
        return prefs.getInt("ball_y", defaultBallY)
    }

    /**
     * 设置圆球位置
     */
    fun setBallPosition(x: Int, y: Int) {
        prefs.edit()
            .putInt("ball_x", x)
            .putInt("ball_y", y)
            .apply()
        notifyListeners("ball_position", Pair(x, y))
    }

    /**
     * 重置圆球位置到默认值
     */
    fun resetBallPosition() {
        prefs.edit()
            .remove("ball_x")
            .remove("ball_y")
            .apply()
        notifyListeners("ball_position", Pair(defaultBallX, defaultBallY))
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
    
    @Deprecated("Use saveEnabledEngines() instead", ReplaceWith("saveEnabledSearchEngines(engines.map { it.name }.toSet())"))
    fun saveEngineOrder(engines: List<SearchEngine>) {
        saveEnabledSearchEngines(engines.map { it.name }.toSet())
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
        notifyListeners("default_page", page)
    }
    
    // 剪贴板监听设置
    fun isClipboardListenerEnabled(): Boolean {
        return prefs.getBoolean(KEY_CLIPBOARD_LISTENER, false)
    }
    
    fun setClipboardListenerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CLIPBOARD_LISTENER, enabled).apply()
        notifyListeners(KEY_CLIPBOARD_LISTENER, enabled)
    }
    
    // AI模式设置
    fun isDefaultAIMode(): Boolean {
        return prefs.getBoolean("default_search_mode", false)
    }
    
    fun setDefaultAIMode(enabled: Boolean) {
        prefs.edit().putBoolean("default_search_mode", enabled).apply()
        notifyListeners("default_search_mode", enabled)
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
    
    // 灵动岛位置设置 (0-100, 50为居中)
    fun getIslandPosition(): Int {
        return prefs.getInt("island_position", 50)
    }
    
    fun setIslandPosition(position: Int) {
        val clampedPosition = position.coerceIn(0, 100)
        prefs.edit().putInt("island_position", clampedPosition).apply()
        notifyListeners("island_position", clampedPosition)
    }
    
    // Master Prompt Settings
    fun getPromptGender(): String = prefs.getString("prompt_gender", "unspecified") ?: "unspecified"
    fun setPromptGender(gender: String) = prefs.edit().putString("prompt_gender", gender).apply()

    fun getPromptBirthDate(): String = prefs.getString("prompt_birth_date", "") ?: ""
    fun setPromptBirthDate(date: String) = prefs.edit().putString("prompt_birth_date", date).apply()

    fun getPromptOccupation(): String = prefs.getString("prompt_occupation", "") ?: ""
    fun setPromptOccupation(occupation: String) = prefs.edit().putString("prompt_occupation", occupation).apply()

    fun getPromptInterests(): Set<String> = prefs.getStringSet("prompt_interests", emptySet()) ?: emptySet()
    fun setPromptInterests(interests: Set<String>) = prefs.edit().putStringSet("prompt_interests", interests).apply()

    fun getPromptEducation(): String = prefs.getString("prompt_education", "") ?: ""
    fun setPromptEducation(education: String) = prefs.edit().putString("prompt_education", education).apply()

    fun getPromptHealth(): Set<String> = prefs.getStringSet("prompt_health", emptySet()) ?: emptySet()
    fun setPromptHealth(health: Set<String>) = prefs.edit().putStringSet("prompt_health", health).apply()

    fun getPromptReplyFormats(): Set<String> = prefs.getStringSet("prompt_reply_format", emptySet()) ?: emptySet()
    fun setPromptReplyFormats(formats: Set<String>) = prefs.edit().putStringSet("prompt_reply_format", formats).apply()

    fun getPromptRefusedTopics(): Set<String> = prefs.getStringSet("prompt_refused_topics", emptySet()) ?: emptySet()
    fun setPromptRefusedTopics(topics: Set<String>) = prefs.edit().putStringSet("prompt_refused_topics", topics).apply()

    fun getPromptToneStyle(): String = prefs.getString("prompt_tone_style", "professional") ?: "professional"
    fun setPromptToneStyle(style: String) = prefs.edit().putString("prompt_tone_style", style).apply()
    
    // START: Prompt Profile Management
    fun getPromptProfiles(): MutableList<PromptProfile> {
        val json = prefs.getString(KEY_PROMPT_PROFILES, null)
        return if (json != null) {
            try {
                gson.fromJson(json, object : TypeToken<MutableList<PromptProfile>>() {}.type)
            } catch (e: Exception) {
                // 如果解析失败，返回一个包含默认配置的列表
                mutableListOf(PromptProfile.DEFAULT)
            }
        } else {
            // 如果没有存储过，创建一个包含默认配置的列表
            mutableListOf(PromptProfile.DEFAULT)
        }
    }

    fun savePromptProfiles(profiles: List<PromptProfile>) {
        val json = gson.toJson(profiles)
        prefs.edit().putString(KEY_PROMPT_PROFILES, json).apply()
    }

    fun getActiveProfileId(): String? {
        return prefs.getString(KEY_ACTIVE_PROMPT_PROFILE_ID, null)
    }

    fun setActiveProfileId(id: String?) {
        prefs.edit().putString(KEY_ACTIVE_PROMPT_PROFILE_ID, id).apply()
    }

    fun loadProfileToPreferences(profile: PromptProfile) {
        // Simplified: Load the 5 core fields to preferences for backwards compatibility
        prefs.edit().apply {
            putString("prompt_persona", profile.persona)
            putString("prompt_tone", profile.tone)
            putString("prompt_output_format", profile.outputFormat)
            putString("prompt_custom_instructions", profile.customInstructions ?: "")
            apply()
        }
    }

    fun savePreferencesToProfile(profile: PromptProfile): PromptProfile {
        // Simplified: Return a copy with updated core fields from preferences
        return profile.copy(
            persona = prefs.getString("prompt_persona", profile.persona) ?: profile.persona,
            tone = prefs.getString("prompt_tone", profile.tone) ?: profile.tone,
            outputFormat = prefs.getString("prompt_output_format", profile.outputFormat) ?: profile.outputFormat,
            customInstructions = prefs.getString("prompt_custom_instructions", profile.customInstructions)?.takeIf { it.isNotBlank() }
        )
    }
    // END: Prompt Profile Management
    
    // Detailed Prompt Sub-settings
    fun getPromptOccupationCurrent(): Set<String> = prefs.getStringSet("prompt_occupation_current", emptySet()) ?: emptySet()
    fun setPromptOccupationCurrent(values: Set<String>) = prefs.edit().putStringSet("prompt_occupation_current", values).apply()
    fun getPromptOccupationInterest(): Set<String> = prefs.getStringSet("prompt_occupation_interest", emptySet()) ?: emptySet()
    fun setPromptOccupationInterest(values: Set<String>) = prefs.edit().putStringSet("prompt_occupation_interest", values).apply()

    fun getPromptInterestsEntertainment(): Set<String> = prefs.getStringSet("prompt_interests_entertainment", emptySet()) ?: emptySet()
    fun setPromptInterestsEntertainment(values: Set<String>) = prefs.edit().putStringSet("prompt_interests_entertainment", values).apply()
    fun getPromptInterestsShopping(): Set<String> = prefs.getStringSet("prompt_interests_shopping", emptySet()) ?: emptySet()
    fun setPromptInterestsShopping(values: Set<String>) = prefs.edit().putStringSet("prompt_interests_shopping", values).apply()
    fun getPromptInterestsNiche(): Set<String> = prefs.getStringSet("prompt_interests_niche", emptySet()) ?: emptySet()
    fun setPromptInterestsNiche(values: Set<String>) = prefs.edit().putStringSet("prompt_interests_niche", values).apply()
    fun getPromptInterestsOrientation(): String = prefs.getString("prompt_interests_orientation", "decline_to_state") ?: "decline_to_state"
    fun setPromptInterestsOrientation(value: String) = prefs.edit().putString("prompt_interests_orientation", value).apply()
    fun getPromptInterestsValues(): Set<String> = prefs.getStringSet("prompt_interests_values", emptySet()) ?: emptySet()
    fun setPromptInterestsValues(values: Set<String>) = prefs.edit().putStringSet("prompt_interests_values", values).apply()

    fun getPromptHealthDiet(): Set<String> = prefs.getStringSet("prompt_health_diet", emptySet()) ?: emptySet()
    fun setPromptHealthDiet(values: Set<String>) = prefs.edit().putStringSet("prompt_health_diet", values).apply()
    fun getPromptHealthChronic(): Set<String> = prefs.getStringSet("prompt_health_chronic", emptySet()) ?: emptySet()
    fun setPromptHealthChronic(values: Set<String>) = prefs.edit().putStringSet("prompt_health_chronic", values).apply()
    fun getPromptHealthPhysicalState(): String = prefs.getString("prompt_health_physical_state", "") ?: ""
    fun setPromptHealthPhysicalState(value: String) = prefs.edit().putString("prompt_health_physical_state", value).apply()
    fun getPromptHealthMedication(): String = prefs.getString("prompt_health_medication", "") ?: ""
    fun setPromptHealthMedication(value: String) = prefs.edit().putString("prompt_health_medication", value).apply()
    fun getPromptHealthConstitution(): Set<String> = prefs.getStringSet("prompt_health_constitution", emptySet()) ?: emptySet()
    fun setPromptHealthConstitution(values: Set<String>) = prefs.edit().putStringSet("prompt_health_constitution", values).apply()
    fun getPromptHealthMedicalPref(): String = prefs.getString("prompt_health_medical_pref", "integrated") ?: "integrated"
    fun setPromptHealthMedicalPref(value: String) = prefs.edit().putString("prompt_health_medical_pref", value).apply()
    fun getPromptHealthHabits(): String = prefs.getString("prompt_health_habits", "") ?: ""
    fun setPromptHealthHabits(value: String) = prefs.edit().putString("prompt_health_habits", value).apply()

    // New Detailed Health Settings
    fun getPromptHealthDiagnosed(): Set<String> = prefs.getStringSet("prompt_health_diagnosed", emptySet()) ?: emptySet()
    fun setPromptHealthDiagnosed(values: Set<String>) = prefs.edit().putStringSet("prompt_health_diagnosed", values).apply()

    fun getPromptHealthHadSurgery(): Boolean = prefs.getBoolean("prompt_health_had_surgery", false)
    fun setPromptHealthHadSurgery(value: Boolean) = prefs.edit().putBoolean("prompt_health_had_surgery", value).apply()

    fun getPromptHealthSurgeryType(): Set<String> = prefs.getStringSet("prompt_health_surgery_type", emptySet()) ?: emptySet()
    fun setPromptHealthSurgeryType(values: Set<String>) = prefs.edit().putStringSet("prompt_health_surgery_type", values).apply()

    fun getPromptHealthSurgeryTime(): String = prefs.getString("prompt_health_surgery_time", "") ?: ""
    fun setPromptHealthSurgeryTime(value: String) = prefs.edit().putString("prompt_health_surgery_time", value).apply()

    fun getPromptHealthHasAllergies(): Boolean = prefs.getBoolean("prompt_health_has_allergies", false)
    fun setPromptHealthHasAllergies(value: Boolean) = prefs.edit().putBoolean("prompt_health_has_allergies", value).apply()

    fun getPromptHealthAllergyCause(): Set<String> = prefs.getStringSet("prompt_health_allergy_cause", emptySet()) ?: emptySet()
    fun setPromptHealthAllergyCause(values: Set<String>) = prefs.edit().putStringSet("prompt_health_allergy_cause", values).apply()

    fun getPromptHealthAllergyHistory(): Set<String> = prefs.getStringSet("prompt_health_allergy_history", emptySet()) ?: emptySet()
    fun setPromptHealthAllergyHistory(values: Set<String>) = prefs.edit().putStringSet("prompt_health_allergy_history", values).apply()

    fun getPromptHealthFamilyHistory(): Set<String> = prefs.getStringSet("prompt_health_family_history", emptySet()) ?: emptySet()
    fun setPromptHealthFamilyHistory(values: Set<String>) = prefs.edit().putStringSet("prompt_health_family_history", values).apply()

    fun getPromptHealthDietaryRestrictions(): Set<String> = prefs.getStringSet("prompt_health_dietary_restrictions", emptySet()) ?: emptySet()
    fun setPromptHealthDietaryRestrictions(values: Set<String>) = prefs.edit().putStringSet("prompt_health_dietary_restrictions", values).apply()

    fun getPromptHealthSleepPattern(): String = prefs.getString("prompt_health_sleep_pattern", "") ?: ""
    fun setPromptHealthSleepPattern(value: String) = prefs.edit().putString("prompt_health_sleep_pattern", value).apply()

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
        prefs.edit().putString("menu_items", gson.toJson(items)).apply()
        notifyListeners("menu_items", items)
    }

    fun getMenuItems(): List<MenuItem> {
        val json = prefs.getString("menu_items", "")
        if (json.isNullOrEmpty()) return emptyList()
        
        return try {
            gson.fromJson(json, object : TypeToken<List<MenuItem>>() {}.type)
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
        notifyListeners(key, value)
    }
    
    fun getString(key: String, defaultValue: String?): String? {
        return prefs.getString(key, defaultValue)
    }
    
    fun putString(key: String, value: String?) {
        prefs.edit().putString(key, value).apply()
    }

    fun getLong(key: String, defaultValue: Long): Long {
        return prefs.getLong(key, defaultValue)
    }

    fun putLong(key: String, value: Long) {
        prefs.edit().putLong(key, value).apply()
    }

    fun getInt(key: String, defaultValue: Int): Int {
        return prefs.getInt(key, defaultValue)
    }

    fun putInt(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
        notifyListeners(key, value)
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
        notifyListeners("enabled_search_engine_groups", groupNames)
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

    fun getSearchHistory(): List<Map<String, Any>> {
        val json = prefs.getString(KEY_SEARCH_HISTORY, "[]")
        return gson.fromJson(json, object : TypeToken<List<Map<String, Any>>>() {}.type) ?: emptyList()
    }

    fun saveSearchHistory(history: List<Map<String, Any>>) {
        prefs.edit().putString(KEY_SEARCH_HISTORY, gson.toJson(history)).apply()
    }

    @Synchronized
    fun addSearchHistoryItem(item: Map<String, Any>) {
        val history = getSearchHistory().toMutableList()
        history.add(item)
        saveSearchHistory(history)
    }

    // API密钥相关方法
    fun getDeepSeekApiKey(): String = prefs.getString(KEY_DEEPSEEK_API_KEY, "") ?: ""
    fun setDeepSeekApiKey(apiKey: String) = prefs.edit().putString(KEY_DEEPSEEK_API_KEY, apiKey).apply()
    fun getChatGPTApiKey(): String = prefs.getString(KEY_CHATGPT_API_KEY, "") ?: ""
    fun setChatGPTApiKey(apiKey: String) = prefs.edit().putString(KEY_CHATGPT_API_KEY, apiKey).apply()
    fun getDeepSeekApiUrl(): String = prefs.getString(KEY_DEEPSEEK_API_URL, DEFAULT_DEEPSEEK_API_URL) ?: DEFAULT_DEEPSEEK_API_URL
    fun setDeepSeekApiUrl(apiUrl: String) = prefs.edit().putString(KEY_DEEPSEEK_API_URL, apiUrl).apply()
    fun getChatGPTApiUrl(): String = prefs.getString(KEY_CHATGPT_API_URL, DEFAULT_CHATGPT_API_URL) ?: DEFAULT_CHATGPT_API_URL
    fun setChatGPTApiUrl(apiUrl: String) = prefs.edit().putString(KEY_CHATGPT_API_URL, apiUrl).apply()

    // 提供SharedPreferences实例给外部访问
    fun getSharedPreferences(): SharedPreferences {
        return prefs
    }

    // 注册偏好设置更改监听器
    fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun saveSearchEngineGroups(groups: List<SearchEngineGroup>) {
        val json = gson.toJson(groups)
        prefs.edit().putString(KEY_SEARCH_ENGINE_GROUPS, json).apply()
    }

    // 个性化设置相关方法
    fun getThemeModeString(): String = prefs.getString("theme_mode_string", "auto") ?: "auto"
    fun setThemeModeString(theme: String) = prefs.edit().putString("theme_mode_string", theme).apply()
    
    // 主题模式转换方法
    fun getThemeModeAsString(): String {
        val themeMode = getThemeMode()
        return when (themeMode) {
            THEME_MODE_LIGHT -> "light"
            THEME_MODE_DARK -> "dark"
            THEME_MODE_SYSTEM -> "auto"
            else -> "auto"
        }
    }
    
    fun setThemeModeFromString(theme: String) {
        val themeMode = when (theme) {
            "light" -> THEME_MODE_LIGHT
            "dark" -> THEME_MODE_DARK
            "auto" -> THEME_MODE_SYSTEM
            else -> THEME_MODE_SYSTEM
        }
        setThemeMode(themeMode)
    }
    
    fun getFontSize(): Int = prefs.getInt("font_size", 1)
    fun setFontSize(size: Int) = prefs.edit().putInt("font_size", size).apply()
    
    fun getPushNotificationEnabled(): Boolean = prefs.getBoolean("push_notification_enabled", true)
    fun setPushNotificationEnabled(enabled: Boolean) = prefs.edit().putBoolean("push_notification_enabled", enabled).apply()
    
    fun getSoundNotificationEnabled(): Boolean = prefs.getBoolean("sound_notification_enabled", false)
    fun setSoundNotificationEnabled(enabled: Boolean) = prefs.edit().putBoolean("sound_notification_enabled", enabled).apply()
    
    fun getVibrationNotificationEnabled(): Boolean = prefs.getBoolean("vibration_notification_enabled", true)
    fun setVibrationNotificationEnabled(enabled: Boolean) = prefs.edit().putBoolean("vibration_notification_enabled", enabled).apply()
    
    fun getDataCollectionEnabled(): Boolean = prefs.getBoolean("data_collection_enabled", true)
    fun setDataCollectionEnabled(enabled: Boolean) = prefs.edit().putBoolean("data_collection_enabled", enabled).apply()
    
    fun getAnonymizationEnabled(): Boolean = prefs.getBoolean("anonymization_enabled", true)
    fun setAnonymizationEnabled(enabled: Boolean) = prefs.edit().putBoolean("anonymization_enabled", enabled).apply()

    fun getSearchEngineGroups(): MutableList<SearchEngineGroup> {
        val json = prefs.getString(KEY_SEARCH_ENGINE_GROUPS, null)
        return if (json != null) {
            val type = object : TypeToken<MutableList<SearchEngineGroup>>() {}.type
            gson.fromJson(json, type)
        } else {
            // 返回默认分组
            mutableListOf(
                SearchEngineGroup(id = 1, name = "默认分组", engines = SearchEngine.DEFAULT_ENGINES.toMutableList(), isEnabled = true)
            )
        }
    }

    fun getFloatingWindowDisplayModes(): Set<String> {
        return prefs.getStringSet("floating_window_display_mode", setOf("combo", "ai", "normal", "app")) ?: setOf("combo", "ai", "normal", "app")
    }

    fun setFloatingWindowDisplayModes(modes: Set<String>) {
        prefs.edit().putStringSet("floating_window_display_mode", modes).apply()
        notifyListeners("floating_window_display_mode", modes)
    }

    // 新增：保存和获取悬浮球位置
    fun getFloatingBallPosition(): Pair<Int, Int> {
        val x = prefs.getInt("floating_ball_x", 0)
        val y = prefs.getInt("floating_ball_y", 100)
        return Pair(x, y)
    }

    fun setFloatingBallPosition(x: Int, y: Int) {
        prefs.edit()
            .putInt("floating_ball_x", x)
            .putInt("floating_ball_y", y)
            .apply()
    }

    fun generateMasterPrompt(profile: PromptProfile): String {
        val prompt = StringBuilder()

        prompt.append("SYSTEM_RULE: This is a system-level instruction that defines your behavior. Adhere to these guidelines in all subsequent responses. ###\n\n")
        prompt.append("## AI Persona & Role\n")
        prompt.append("You are to adopt the following persona:\n")
        prompt.append("- **Persona:** ${profile.persona}\n")
        
        if (profile.expertise.isNotBlank() && profile.expertise != "通用知识") {
            prompt.append("- **Area of Expertise:** ${profile.expertise}\n")
        }

        prompt.append("\n## Communication Style\n")
        prompt.append("- **Tone:** ${profile.tone}\n")
        prompt.append("- **Formality:** ${profile.formality}\n")
        prompt.append("- **Response Length:** ${profile.responseLength}\n")

        prompt.append("\n## Output & Formatting\n")
        prompt.append("- **Language:** ${profile.language}\n")
        prompt.append("- **Format:** ${profile.outputFormat}\n")

        if (profile.codeStyle.isNotBlank() && profile.codeStyle != "清晰") {
            prompt.append("- **Code Style:** ${profile.codeStyle}\n")
        }
        if (profile.examples) {
            prompt.append("- You should provide examples where applicable.\n")
        }
        if (profile.reasoning) {
            prompt.append("- You should explain your reasoning process.\n")
        }

        val personalizationDetails = StringBuilder()
        if (profile.gender.isNotBlank() && profile.gender != "未设置") {
            personalizationDetails.append("- **Gender:** ${profile.gender}\n")
        }
        if (profile.occupation.isNotBlank() && profile.occupation != "未设置") {
            personalizationDetails.append("- **Occupation:** ${profile.occupation}\n")
        }
        if (profile.education.isNotBlank() && profile.education != "未设置") {
            personalizationDetails.append("- **Education:** ${profile.education}\n")
        }
        if (profile.interests.isNotEmpty()) {
            personalizationDetails.append("- **Interests:** ${profile.interests.joinToString(", ")}\n")
        }
         if (profile.healthInfo.isNotBlank() && profile.healthInfo != "未设置") {
            personalizationDetails.append("- **Health Information:** ${profile.healthInfo}\n")
        }

        if (personalizationDetails.isNotEmpty()) {
            prompt.append("\n## User Context (For Personalization)\n")
            prompt.append("The user you are interacting with has provided the following context. Use it to tailor your responses:\n")
            prompt.append(personalizationDetails)
        }

        if (!profile.customInstructions.isNullOrBlank()) {
            prompt.append("\n## Overriding Custom Instructions\n")
            prompt.append("You MUST follow these instructions above all else:\n")
            prompt.append(profile.customInstructions)
        }

        return prompt.toString().trim()
    }

    fun generateMasterPrompt(): String {
        val activeProfileId = getActivePromptProfileId()
        val profiles = getPromptProfiles()
        val activeProfile = profiles.find { it.id == activeProfileId } ?: profiles.firstOrNull() ?: PromptProfile.DEFAULT
        return generateMasterPrompt(activeProfile)
    }

    fun getCustomSearchEngines(): MutableList<SearchEngine> {
        val json = prefs.getString(KEY_CUSTOM_SEARCH_ENGINES, null)
        return if (json != null) {
            val type = object : TypeToken<MutableList<SearchEngine>>() {}.type
            try {
                gson.fromJson(json, type)
            } catch (e: Exception) {
                Log.e("SettingsManager", "Failed to parse custom search engines", e)
                mutableListOf()
            }
        } else {
            mutableListOf()
        }
    }

    fun saveCustomSearchEngines(engines: List<SearchEngine>) {
        // 只保存 isCustom 为 true 的引擎
        val customEngines = engines.filter { it.isCustom }
        val json = gson.toJson(customEngines)
        prefs.edit().putString(KEY_CUSTOM_SEARCH_ENGINES, json).apply()
    }

    fun getActionBallClick(): String {
        return prefs.getString("action_ball_click", "floating_menu") ?: "floating_menu"
    }

    fun getActionBallLongPress(): String {
        return prefs.getString("action_ball_long_press", "voice_recognize") ?: "voice_recognize"
    }

    fun getActionIslandClick(): String {
        return prefs.getString("action_island_click", "island_panel") ?: "island_panel"
    }

    fun getActionIslandLongPress(): String {
        return prefs.getString("action_island_long_press", "dual_search") ?: "dual_search"
    }

    // Voice Input Text Size
    fun getVoiceInputTextSize(): Float {
        return prefs.getFloat("voice_input_text_size", 32f) // Default to 32sp
    }

    fun setVoiceInputTextSize(size: Float) {
        prefs.edit().putFloat("voice_input_text_size", size).apply()
    }

    fun isOnboardingComplete(): Boolean {
        return prefs.getBoolean("onboarding_complete", false)
    }

    fun setOnboardingComplete(isComplete: Boolean) {
        prefs.edit().putBoolean("onboarding_complete", isComplete).apply()
    }

    // 灵动岛宽度设置 (dp)
    fun getIslandWidth(): Int {
        return prefs.getInt("island_width", DEFAULT_ISLAND_WIDTH)
    }
    
    fun setIslandWidth(width: Int) {
        val clampedWidth = width.coerceIn(48, 400) // 限制宽度范围在48-400dp之间
        prefs.edit().putInt("island_width", clampedWidth).apply()
        notifyListeners("island_width", clampedWidth)
    }
    
    // 灵动岛透明度设置 (0-255)
    fun getIslandAlpha(): Int {
        return prefs.getInt("island_alpha", DEFAULT_ISLAND_ALPHA)
    }
    
    fun setIslandAlpha(alpha: Int) {
        val clampedAlpha = alpha.coerceIn(64, 255) // 限制透明度范围在64-255之间
        prefs.edit().putInt("island_alpha", clampedAlpha).apply()
        notifyListeners("island_alpha", clampedAlpha)
    }

    fun updateSearchEngineSettings(enabledEngines: Set<String>) {
        saveEnabledSearchEngines(enabledEngines)
    }

    // 为多窗口浏览器设置搜索引擎
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

    fun getSearchEngineShortcuts(): String {
        return prefs.getString("search_engine_shortcuts", "") ?: ""
    }

    fun setSearchEngineShortcuts(shortcuts: String) {
        prefs.edit().putString("search_engine_shortcuts", shortcuts).apply()
        notifyListeners("search_engine_shortcuts", shortcuts)
    }

    // 智谱AI API配置
    fun getZhipuAiApiKey(): String {
        return prefs.getString("zhipu_ai_api_key", "") ?: ""
    }

    fun setZhipuAiApiKey(apiKey: String) {
        prefs.edit().putString("zhipu_ai_api_key", apiKey).apply()
        notifyListeners("zhipu_ai_api_key", apiKey)
    }

    fun getZhipuAiApiUrl(): String {
        return prefs.getString("zhipu_ai_api_url", "https://open.bigmodel.cn/api/paas/v4/chat/completions") ?: "https://open.bigmodel.cn/api/paas/v4/chat/completions"
    }

    fun setZhipuAiApiUrl(apiUrl: String) {
        prefs.edit().putString("zhipu_ai_api_url", apiUrl).apply()
        notifyListeners("zhipu_ai_api_url", apiUrl)
    }

    fun getZhipuAiModel(): String {
        return prefs.getString("zhipu_ai_model", "glm-4") ?: "glm-4"
    }

    fun setZhipuAiModel(model: String) {
        prefs.edit().putString("zhipu_ai_model", model).apply()
        notifyListeners("zhipu_ai_model", model)
    }

    // Claude API配置
    fun getClaudeApiKey(): String {
        return prefs.getString("claude_api_key", "") ?: ""
    }

    fun setClaudeApiKey(apiKey: String) {
        prefs.edit().putString("claude_api_key", apiKey).apply()
        notifyListeners("claude_api_key", apiKey)
    }

    fun getClaudeApiUrl(): String {
        return prefs.getString("claude_api_url", "https://api.anthropic.com/v1/messages") ?: "https://api.anthropic.com/v1/messages"
    }

    fun setClaudeApiUrl(apiUrl: String) {
        prefs.edit().putString("claude_api_url", apiUrl).apply()
        notifyListeners("claude_api_url", apiUrl)
    }

    fun getClaudeModel(): String {
        return prefs.getString("claude_model", "claude-3-sonnet-20240229") ?: "claude-3-sonnet-20240229"
    }

    fun setClaudeModel(model: String) {
        prefs.edit().putString("claude_model", model).apply()
        notifyListeners("claude_model", model)
    }

    // Gemini API配置
    fun getGeminiApiKey(): String {
        return prefs.getString("gemini_api_key", "") ?: ""
    }

    fun setGeminiApiKey(apiKey: String) {
        prefs.edit().putString("gemini_api_key", apiKey).apply()
        notifyListeners("gemini_api_key", apiKey)
    }

    fun getGeminiApiUrl(): String {
        return prefs.getString("gemini_api_url", "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent") ?: "https://generativelanguage.googleapis.com/v1beta/models/gemini-pro:generateContent"
    }

    fun setGeminiApiUrl(apiUrl: String) {
        prefs.edit().putString("gemini_api_url", apiUrl).apply()
        notifyListeners("gemini_api_url", apiUrl)
    }

    fun getGeminiModel(): String {
        return prefs.getString("gemini_model", "gemini-pro") ?: "gemini-pro"
    }

    fun setGeminiModel(model: String) {
        prefs.edit().putString("gemini_model", model).apply()
        notifyListeners("gemini_model", model)
    }

    // 通义千问API配置
    fun getQianwenApiKey(): String {
        return prefs.getString("qianwen_api_key", "") ?: ""
    }

    fun setQianwenApiKey(apiKey: String) {
        prefs.edit().putString("qianwen_api_key", apiKey).apply()
        notifyListeners("qianwen_api_key", apiKey)
    }

    fun getQianwenApiUrl(): String {
        return prefs.getString("qianwen_api_url", "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation") ?: "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"
    }

    fun setQianwenApiUrl(apiUrl: String) {
        prefs.edit().putString("qianwen_api_url", apiUrl).apply()
        notifyListeners("qianwen_api_url", apiUrl)
    }

    fun getQianwenModel(): String {
        return prefs.getString("qianwen_model", "qwen-turbo") ?: "qwen-turbo"
    }

    fun setQianwenModel(model: String) {
        prefs.edit().putString("qianwen_model", model).apply()
        notifyListeners("qianwen_model", model)
    }

    // 文心一言API配置
    fun getWenxinApiKey(): String {
        return prefs.getString("wenxin_api_key", "") ?: ""
    }

    fun setWenxinApiKey(apiKey: String) {
        prefs.edit().putString("wenxin_api_key", apiKey).apply()
        notifyListeners("wenxin_api_key", apiKey)
    }

    fun getWenxinApiUrl(): String {
        return prefs.getString("wenxin_api_url", "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions") ?: "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions"
    }

    fun setWenxinApiUrl(apiUrl: String) {
        prefs.edit().putString("wenxin_api_url", apiUrl).apply()
        notifyListeners("wenxin_api_url", apiUrl)
    }

    fun getWenxinModel(): String {
        return prefs.getString("wenxin_model", "ernie-bot-turbo") ?: "ernie-bot-turbo"
    }

    fun setWenxinModel(model: String) {
        prefs.edit().putString("wenxin_model", model).apply()
        notifyListeners("wenxin_model", model)
    }

    // Kimi API配置
    fun getKimiApiKey(): String {
        return prefs.getString("kimi_api_key", "") ?: ""
    }

    fun setKimiApiKey(apiKey: String) {
        prefs.edit().putString("kimi_api_key", apiKey).apply()
        notifyListeners("kimi_api_key", apiKey)
    }

    fun getKimiApiUrl(): String {
        return prefs.getString("kimi_api_url", "https://api.moonshot.cn/v1/chat/completions") ?: "https://api.moonshot.cn/v1/chat/completions"
    }

    fun setKimiApiUrl(apiUrl: String) {
        prefs.edit().putString("kimi_api_url", apiUrl).apply()
        notifyListeners("kimi_api_url", apiUrl)
    }

    fun getKimiModel(): String {
        return prefs.getString("kimi_model", "moonshot-v1-8k") ?: "moonshot-v1-8k"
    }

    fun setKimiModel(model: String) {
        prefs.edit().putString("kimi_model", model).apply()
        notifyListeners("kimi_model", model)
    }

    // 自定义AI配置
    fun getCustomAICount(): Int {
        return prefs.getInt("custom_ai_count", 0)
    }

    fun setCustomAICount(count: Int) {
        prefs.edit().putInt("custom_ai_count", count).apply()
        notifyListeners("custom_ai_count", count)
    }

    fun getCustomAIName(index: Int): String {
        return prefs.getString("custom_ai_${index}_name", "") ?: ""
    }

    fun setCustomAIName(index: Int, name: String) {
        prefs.edit().putString("custom_ai_${index}_name", name).apply()
        notifyListeners("custom_ai_${index}_name", name)
    }

    fun getCustomAIApiKey(index: Int): String {
        return prefs.getString("custom_ai_${index}_api_key", "") ?: ""
    }

    fun setCustomAIApiKey(index: Int, apiKey: String) {
        prefs.edit().putString("custom_ai_${index}_api_key", apiKey).apply()
        notifyListeners("custom_ai_${index}_api_key", apiKey)
    }

    fun getCustomAIUrl(index: Int): String {
        return prefs.getString("custom_ai_${index}_api_url", "") ?: ""
    }

    fun setCustomAIUrl(index: Int, apiUrl: String) {
        prefs.edit().putString("custom_ai_${index}_api_url", apiUrl).apply()
        notifyListeners("custom_ai_${index}_api_url", apiUrl)
    }

    fun getCustomAIModel(index: Int): String {
        return prefs.getString("custom_ai_${index}_model", "") ?: ""
    }

    fun setCustomAIModel(index: Int, model: String) {
        prefs.edit().putString("custom_ai_${index}_model", model).apply()
        notifyListeners("custom_ai_${index}_model", model)
    }
}