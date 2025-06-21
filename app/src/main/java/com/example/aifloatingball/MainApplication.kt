package com.example.aifloatingball

import android.app.Application
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Initialize SettingsManager
        val settingsManager = SettingsManager.getInstance(this)
        
        // Migrate theme setting if necessary
        migrateThemeSetting()
        
        // Apply theme
        settingsManager.setThemeMode(settingsManager.getThemeMode())
    }

    private fun migrateThemeSetting() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        try {
            val themeValue = prefs.all["theme_mode"]
            if (themeValue is Int) {
                // If it's an old Int, convert and save as a String
                prefs.edit().putString("theme_mode", themeValue.toString()).apply()
                Log.d("MainApplication", "Theme setting migrated from Int to String.")
            }
        } catch (e: Exception) {
            Log.e("MainApplication", "Error during theme setting migration", e)
        }
    }
} 