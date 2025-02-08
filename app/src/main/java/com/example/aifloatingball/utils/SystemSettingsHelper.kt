package com.example.aifloatingball.utils

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import android.os.Build

class SystemSettingsHelper(private val context: Context) {
    
    fun toggleWifi() {
        try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "无法切换WiFi状态", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun toggleBluetooth() {
        try {
            val intent = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "无法切换蓝牙状态", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun showBrightnessDialog() {
        try {
            val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开亮度设置", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun takeScreenshot() {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "无法执行截图操作", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun showRecentApps() {
        try {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "无法显示最近应用", Toast.LENGTH_SHORT).show()
        }
    }
    
    fun openSettings() {
        try {
            val intent = Intent(Settings.ACTION_SETTINGS)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开系统设置", Toast.LENGTH_SHORT).show()
        }
    }
} 