package com.example.aifloatingball.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

/**
 * 悬浮工具栏服务
 * 为AI搜索引擎提供辅助功能，如粘贴文本、加载身份模板等
 */
class WebViewToolbarService : Service() {

    companion object {
        private const val TAG = "WebViewToolbarService"
        
        // 广播动作常量
        const val ACTION_SHOW_TOOLBAR = "com.example.aifloatingball.SHOW_TOOLBAR"
        const val ACTION_HIDE_TOOLBAR = "com.example.aifloatingball.HIDE_TOOLBAR"
        
        // Intent参数
        const val EXTRA_SEARCH_TEXT = "search_text"
        const val EXTRA_AI_ENGINE = "ai_engine"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "WebViewToolbarService created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "WebViewToolbarService: onStartCommand")
        return START_NOT_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "WebViewToolbarService destroyed")
    }
} 