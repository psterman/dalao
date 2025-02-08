package com.example.aifloatingball

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import android.widget.Toast

class App : Application() {
    
    companion object {
        const val CHANNEL_ID = "floating_service_channel"
        private const val TAG = "App"
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "应用开始初始化")
        
        try {
            createNotificationChannel()
            Log.d(TAG, "应用初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "应用初始化失败", e)
            Toast.makeText(this, "应用初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                Log.d(TAG, "开始创建通知渠道")
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                
                // 检查通知渠道是否已存在
                val existingChannel = notificationManager.getNotificationChannel(CHANNEL_ID)
                if (existingChannel != null) {
                    Log.d(TAG, "通知渠道已存在，正在删除旧渠道")
                    notificationManager.deleteNotificationChannel(CHANNEL_ID)
                }
                
                // 创建新的通知渠道
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "AI悬浮球服务",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "用于保持悬浮球服务在后台运行"
                    setShowBadge(false)
                    enableLights(false)
                    enableVibration(false)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                }
                
                notificationManager.createNotificationChannel(channel)
                Log.d(TAG, "通知渠道创建成功")
            } catch (e: Exception) {
                Log.e(TAG, "创建通知渠道失败", e)
                throw e
            }
        } else {
            Log.d(TAG, "当前系统版本不需要创建通知渠道")
        }
    }
} 