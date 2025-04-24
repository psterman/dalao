package com.example.aifloatingball

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.view.WindowManager
import android.widget.Toast

class FloatingService : Service() {
    companion object {
        var isRunning = false
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // TODO: 实现悬浮窗逻辑
        Toast.makeText(this, "悬浮窗服务已启动", Toast.LENGTH_SHORT).show()
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
} 