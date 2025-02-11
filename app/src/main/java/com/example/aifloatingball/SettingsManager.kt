package com.example.aifloatingball

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.aifloatingball.adapter.EngineAdapter.SearchEngine

class SettingsManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val PREFS_NAME = "settings"
        private const val KEY_AUTO_START = "auto_start"
        private const val KEY_ENGINES = "search_engines"
        
        @Volatile
        private var instance: SettingsManager? = null
        
        fun getInstance(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    fun setAutoStart(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_START, enabled).apply()
    }
    
    fun getAutoStart(): Boolean {
        return prefs.getBoolean(KEY_AUTO_START, false)
    }
    
    fun saveEngineOrder(engines: List<SearchEngine>) {
        val json = gson.toJson(engines)
        prefs.edit().putString(KEY_ENGINES, json).apply()
    }
    
    fun getEngineOrder(): List<SearchEngine> {
        val json = prefs.getString(KEY_ENGINES, null) ?: return getDefaultEngines()
        return try {
            gson.fromJson(json, object : TypeToken<List<SearchEngine>>() {}.type)
        } catch (e: Exception) {
            getDefaultEngines()
        }
    }
    
    private fun getDefaultEngines(): List<SearchEngine> {
        return listOf(
            SearchEngine("Kimi", "https://kimi.moonshot.cn/chat"),
            SearchEngine("DeepSeek", "https://chat.deepseek.com/chat"),
            SearchEngine("豆包", "https://www.doubao.com/chat")
        )
    }
    
    fun setAutoHide(autoHide: Boolean) {
        prefs.edit().putBoolean("auto_hide", autoHide).apply()
    }
    
    fun getAutoHide(): Boolean = prefs.getBoolean("auto_hide", false)
} 