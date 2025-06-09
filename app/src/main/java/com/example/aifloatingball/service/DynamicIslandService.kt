package com.example.aifloatingball.service

import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.os.IBinder
import android.preference.PreferenceManager
import android.widget.Toast

class DynamicIslandService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        // 在这里初始化灵动岛视图和逻辑
        Toast.makeText(this, "灵动岛服务已启动", Toast.LENGTH_SHORT).show()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 在这里处理启动命令
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        // 在这里清理资源
        Toast.makeText(this, "灵动岛服务已停止", Toast.LENGTH_SHORT).show()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == "display_mode") {
            val displayMode = sharedPreferences?.getString(key, "floating_ball")
            if (displayMode == "floating_ball") {
                stopSelf()
            }
        }
    }
} 