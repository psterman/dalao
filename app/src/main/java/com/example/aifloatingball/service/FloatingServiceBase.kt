package com.example.aifloatingball.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * 浮动窗口服务的基类，提供通用服务功能
 */
abstract class FloatingServiceBase : Service() {
    /**
     * 创建服务时的初始化逻辑
     */
    override fun onCreate() {
        super.onCreate()
    }

    /**
     * 处理服务命令
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    /**
     * 绑定服务
     */
    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 销毁服务时的清理逻辑
     */
    override fun onDestroy() {
        super.onDestroy()
    }
} 