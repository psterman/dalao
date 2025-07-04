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
        private const val PREFS_NAME = "settings"
        private const val DEFAULT_BALL_ALPHA = 85
        private const val DEFAULT_LAYOUT_THEME = 0
        private const val DEFAULT_PAGE = "home"
        private const val KEY_SEARCH_HISTORY = "search_history"
        private const val KEY_DISPLAY_MODE = "display_mode"
        private const val DEFAULT_DISPLAY_MODE = "floating_ball"
        private const val KEY_SEARCH_ENGINE_GROUPS = "search_engine_groups"
        private const val KEY_CUSTOM_SEARCH_ENGINES = "custom_search_engines"
        private const val KEY_PROMPT_PROFILES = "prompt_profiles"
        private const val KEY_ACTIVE_PROMPT_PROFILE_ID = "active_prompt_profile_id"
        
        // API相关常量
        private const val KEY_DEEPSEEK_API_KEY = "deepseek_api_key"
        private const val KEY_DEEPSEEK_API_URL = "deepseek_api_url"
        private const val KEY_CHATGPT_API_KEY = "chatgpt_api_key"
        private const val KEY_CHATGPT_API_URL = "chatgpt_api_url"
        
        // 主题模式常量
        const val THEME_MODE_SYSTEM = -1   // 跟随系统 (AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        const val THEME_MODE_LIGHT = 1     // 浅色主题 (AppCompatDelegate.MODE_NIGHT_NO)
        const val THEME_MODE_DARK = 2      // 深色主题 (AppCompatDelegate.MODE_NIGHT_YES)
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
        // 根据主题模式设置应用主题
        when (mode) {
            THEME_MODE_LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            THEME_MODE_DARK -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            THEME_MODE_SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            else -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
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
        return prefs.getStringSet("enabled_ai_engines", emptySet()) ?: emptySet()
    }
    
    // 保存已启用的AI搜索引擎
    fun saveEnabledAIEngines(enabledEngines: Set<String>) {
        prefs.edit().putStringSet("enabled_ai_engines", enabledEngines).apply()
        notifyListeners("enabled_ai_engines", enabledEngines)
    }
    
    // 获取默认的AI搜索引擎
    fun getDefaultAIEngine(): String {
        val enabledAIEngines = getEnabledAIEngines()
        return enabledAIEngines.firstOrNull() ?: "deepseek_chat" // 如果没有启用的AI引擎，则返回一个默认值
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
        return prefs.getBoolean("clipboard_listener", true)
    }
    
    fun setClipboardListenerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("clipboard_listener", enabled).apply()
        notifyListeners("clipboard_listener", enabled)
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
                mutableListOf(PromptProfile(name = "默认档案"))
            }
        } else {
            // 如果没有存储过，创建一个包含默认配置的列表
            mutableListOf(PromptProfile(name = "默认档案"))
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
        prefs.edit().apply {
            putString("prompt_gender", profile.gender)
            putString("prompt_birth_date", profile.birthDate)
            putString("prompt_education", profile.education)
            putString("prompt_occupation", profile.occupation)
            putStringSet("prompt_occupation_current", profile.occupationCurrent)
            putStringSet("prompt_occupation_interest", profile.occupationInterest)
            putStringSet("prompt_interests", profile.interests)
            putStringSet("prompt_interests_entertainment", profile.interestsEntertainment)
            putStringSet("prompt_interests_shopping", profile.interestsShopping)
            putStringSet("prompt_interests_niche", profile.interestsNiche)
            putString("prompt_interests_orientation", profile.interestsOrientation)
            putStringSet("prompt_interests_values", profile.interestsValues)
            putStringSet("prompt_health", profile.health)
            putStringSet("prompt_health_diet", profile.healthDiet)
            putStringSet("prompt_health_chronic", profile.healthChronic)
            putString("prompt_health_physical_state", profile.healthPhysicalState)
            putString("prompt_health_medication", profile.healthMedication)
            putStringSet("prompt_health_constitution", profile.healthConstitution)
            putString("prompt_health_medical_pref", profile.healthMedicalPref)
            putString("prompt_health_habits", profile.healthHabits)
            putStringSet("prompt_health_diagnosed", profile.healthDiagnosed)
            putBoolean("prompt_health_had_surgery", profile.healthHadSurgery)
            putStringSet("prompt_health_surgery_type", profile.healthSurgeryType)
            putString("prompt_health_surgery_time", profile.healthSurgeryTime)
            putBoolean("prompt_health_has_allergies", profile.healthHasAllergies)
            putStringSet("prompt_health_allergy_cause", profile.healthAllergyCause)
            putStringSet("prompt_health_allergy_history", profile.healthAllergyHistory)
            putStringSet("prompt_health_family_history", profile.healthFamilyHistory)
            putStringSet("prompt_health_dietary_restrictions", profile.healthDietaryRestrictions)
            putString("prompt_health_sleep_pattern", profile.healthSleepPattern)
            putStringSet("prompt_reply_format", profile.replyFormats)
            putStringSet("prompt_refused_topics", profile.refusedTopics)
            putString("prompt_tone_style", profile.toneStyle)
            apply()
        }
    }

    fun savePreferencesToProfile(profile: PromptProfile): PromptProfile {
        return profile.apply {
            gender = getPromptGender()
            birthDate = getPromptBirthDate()
            education = getPromptEducation()
            occupation = getPromptOccupation()
            occupationCurrent = getPromptOccupationCurrent()
            occupationInterest = getPromptOccupationInterest()
            interests = getPromptInterests()
            interestsEntertainment = getPromptInterestsEntertainment()
            interestsShopping = getPromptInterestsShopping()
            interestsNiche = getPromptInterestsNiche()
            interestsOrientation = getPromptInterestsOrientation()
            interestsValues = getPromptInterestsValues()
            health = getPromptHealth()
            healthDiet = getPromptHealthDiet()
            healthChronic = getPromptHealthChronic()
            healthPhysicalState = getPromptHealthPhysicalState()
            healthMedication = getPromptHealthMedication()
            healthConstitution = getPromptHealthConstitution()
            healthMedicalPref = getPromptHealthMedicalPref()
            healthHabits = getPromptHealthHabits()
            healthDiagnosed = getPromptHealthDiagnosed()
            healthHadSurgery = getPromptHealthHadSurgery()
            healthSurgeryType = getPromptHealthSurgeryType()
            healthSurgeryTime = getPromptHealthSurgeryTime()
            healthHasAllergies = getPromptHealthHasAllergies()
            healthAllergyCause = getPromptHealthAllergyCause()
            healthAllergyHistory = getPromptHealthAllergyHistory()
            healthFamilyHistory = getPromptHealthFamilyHistory()
            healthDietaryRestrictions = getPromptHealthDietaryRestrictions()
            healthSleepPattern = getPromptHealthSleepPattern()
            replyFormats = getPromptReplyFormats()
            refusedTopics = getPromptRefusedTopics()
            toneStyle = getPromptToneStyle()
        }
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

        prompt.append("### 用户画像(User Profile)\n\n")

        // --- 基本信息 ---
        prompt.append("#### 1. 基本信息:\n")
        val gender = when (profile.gender) {
            "male" -> "男"
            "female" -> "女"
            else -> "未指定"
        }
        prompt.append("- **性别**: $gender\n")
        if (profile.birthDate.isNotBlank()) prompt.append("- **出生日期**: ${profile.birthDate}\n")
        if (profile.education.isNotBlank()) prompt.append("- **教育程度**: ${profile.education}\n")

        // --- 职业信息 ---
        prompt.append("\n#### 2. 职业信息:\n")
        if (profile.occupation.isNotBlank()) prompt.append("- **当前从事行业**: ${profile.occupation}\n")
        if (profile.occupationCurrent.isNotEmpty()) prompt.append("- **具体职业/岗位**: ${profile.occupationCurrent.joinToString(", ")}\n")
        if (profile.occupationInterest.isNotEmpty()) prompt.append("- **感兴趣的职业领域**: ${profile.occupationInterest.joinToString(", ")}\n")

        // --- 兴趣爱好 ---
        prompt.append("\n#### 3. 兴趣爱好:\n")
        if (profile.interestsEntertainment.isNotEmpty()) prompt.append("- **娱乐偏好**: ${profile.interestsEntertainment.joinToString(", ")}\n")
        if (profile.interestsShopping.isNotEmpty()) prompt.append("- **消费偏好**: ${profile.interestsShopping.joinToString(", ")}\n")
        if (profile.interestsNiche.isNotEmpty()) prompt.append("- **小众爱好**: ${profile.interestsNiche.joinToString(", ")}\n")
        if (profile.interestsValues.isNotEmpty()) prompt.append("- **看重的价值观**: ${profile.interestsValues.joinToString(", ")}\n")
        val orientation = when (profile.interestsOrientation) {
            "heterosexual" -> "异性恋"
            "homosexual" -> "同性恋"
            "bisexual" -> "双性恋"
            "pansexual" -> "泛性恋"
            "asexual" -> "无性恋"
            else -> "不愿透露"
        }
        prompt.append("- **取向**: $orientation\n")

        // --- 健康状况 ---
        prompt.append("\n#### 4. 健康状况:\n")
        if (profile.healthDiagnosed.isNotEmpty()) prompt.append("- **曾确诊的疾病**: ${profile.healthDiagnosed.joinToString(", ")}\n")
        if (profile.healthHadSurgery) {
            prompt.append("- **手术史**: 有\n")
            if (profile.healthSurgeryType.isNotEmpty()) prompt.append("  - **手术类型**: ${profile.healthSurgeryType.joinToString(", ")}\n")
            if (profile.healthSurgeryTime.isNotBlank()) prompt.append("  - **最近手术时间**: ${profile.healthSurgeryTime}\n")
        }
        if (profile.healthHasAllergies) {
            prompt.append("- **过敏史**: 有\n")
            if (profile.healthAllergyCause.isNotEmpty()) prompt.append("  - **过敏原因**: ${profile.healthAllergyCause.joinToString(", ")}\n")
            if (profile.healthAllergyHistory.isNotEmpty()) prompt.append("  - **相关疾病/症状**: ${profile.healthAllergyHistory.joinToString(", ")}\n")
        }
        if (profile.healthFamilyHistory.isNotEmpty()) prompt.append("- **家族病史**: ${profile.healthFamilyHistory.joinToString(", ")}\n")
        if (profile.healthDietaryRestrictions.isNotEmpty()) prompt.append("- **饮食偏好/禁忌**: ${profile.healthDietaryRestrictions.joinToString(", ")}\n")
        if (profile.healthSleepPattern.isNotBlank()) prompt.append("- **睡眠状况**: ${profile.healthSleepPattern}\n")


        // --- 回复偏好 ---
        prompt.append("\n### 回复偏好(Reply Preferences)\n\n")
        if (profile.replyFormats.isNotEmpty()) prompt.append("- **内容呈现形式**: ${profile.replyFormats.joinToString(", ")}\n")
        if (profile.toneStyle.isNotBlank()) prompt.append("- **回复口吻**: ${profile.toneStyle}\n")
        if (profile.refusedTopics.isNotEmpty()) prompt.append("- **希望避免的话题**: ${profile.refusedTopics.joinToString(", ")}\n")


        return prompt.toString()
    }

    fun generateMasterPrompt(): String {
        val activeProfileId = getActiveProfileId()
        val profiles = getPromptProfiles()
        val activeProfile = profiles.find { it.id == activeProfileId } ?: profiles.firstOrNull() ?: PromptProfile()
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
}