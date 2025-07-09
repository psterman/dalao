package com.example.aifloatingball.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.view.WindowManager
import android.widget.Toast
import com.example.aifloatingball.SettingsManager

class SimpleModeService : Service() {
    private lateinit var settingsManager: SettingsManager
    
    override fun onCreate() {
        super.onCreate()
        settingsManager = SettingsManager.getInstance(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val content = intent?.getStringExtra("search_content")
        val mode = intent?.getStringExtra("mode")
        
        if (!content.isNullOrEmpty()) {
            when (mode) {
                "clipboard" -> handleClipboardContent(content)
                else -> handleNormalContent(content)
            }
        }
        
        return START_NOT_STICKY
    }

    private fun handleClipboardContent(content: String) {
        // 处理剪贴板内容
        Toast.makeText(this, "简易模式处理剪贴板内容: $content", Toast.LENGTH_SHORT).show()
        // TODO: 实现具体的处理逻辑
    }

    private fun handleNormalContent(content: String) {
        // 处理普通内容
        Toast.makeText(this, "简易模式处理内容: $content", Toast.LENGTH_SHORT).show()
        // TODO: 实现具体的处理逻辑
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
