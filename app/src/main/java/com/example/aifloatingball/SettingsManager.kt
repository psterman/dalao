package com.example.aifloatingball

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SettingsManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val PREFS_NAME = "ai_floating_ball_settings"
        private const val KEY_ENGINE_ORDER = "engine_order"
        private const val KEY_AUTO_START = "auto_start"
        
        @Volatile
        private var instance: SettingsManager? = null
        
        fun getInstance(context: Context): SettingsManager {
            return instance ?: synchronized(this) {
                instance ?: SettingsManager(context).also { instance = it }
            }
        }
    }
    
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
    
    fun getAutoStart(): Boolean {
        return prefs.getBoolean(KEY_AUTO_START, false)
    }
    
    fun setAutoStart(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_START, enabled).apply()
    }
    
    fun setAutoHide(autoHide: Boolean) {
        prefs.edit().putBoolean("auto_hide", autoHide).apply()
    }
    
    fun getAutoHide(): Boolean = prefs.getBoolean("auto_hide", false)
} 