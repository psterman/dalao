package com.example.aifloatingball

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class MainApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Initialize SettingsManager
        val settingsManager = SettingsManager.getInstance(this)
        
        // Apply the saved theme at application startup
        val themeMode = settingsManager.getThemeMode()
        AppCompatDelegate.setDefaultNightMode(themeMode)
    }
} 