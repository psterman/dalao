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
            
            // 检查悬浮窗网速开关，如果打开则自动启动服务
            checkAndStartNetworkSpeedService()
            
            Log.d(TAG, "应用初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "应用初始化失败", e)
            Toast.makeText(this, "应用初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 检查并启动悬浮窗网速服务
     */
    private fun checkAndStartNetworkSpeedService() {
        try {
            // 延迟启动，确保应用完全初始化
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    val settingsManager = SettingsManager.getInstance(this@App)
                    val isNetworkSpeedEnabled = settingsManager.getBoolean("network_speed_display_enabled", false)
                    
                    if (isNetworkSpeedEnabled) {
                        Log.d(TAG, "悬浮窗网速开关已打开，准备启动服务")
                        
                        // 检查悬浮窗权限（Android 6.0+需要）
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                            if (!android.provider.Settings.canDrawOverlays(this@App)) {
                                Log.w(TAG, "没有悬浮窗权限，无法启动网速悬浮窗服务")
                                return@postDelayed
                            }
                        }
                        
                        // 检查服务是否已经在运行
                        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
                        val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)
                        val isServiceRunning = runningServices.any { 
                            it.service.className == com.example.aifloatingball.service.NetworkMonitorFloatingService::class.java.name 
                        }
                        
                        if (!isServiceRunning) {
                            val intent = android.content.Intent(this@App, com.example.aifloatingball.service.NetworkMonitorFloatingService::class.java)
                            try {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    startForegroundService(intent)
                                } else {
                                    startService(intent)
                                }
                                Log.d(TAG, "悬浮窗网速服务已启动")
                            } catch (e: SecurityException) {
                                Log.e(TAG, "启动悬浮窗网速服务失败：缺少权限", e)
                            } catch (e: Exception) {
                                Log.e(TAG, "启动悬浮窗网速服务失败", e)
                            }
                        } else {
                            Log.d(TAG, "悬浮窗网速服务已在运行，跳过启动")
                            // 即使服务在运行，也触发一次onStartCommand确保状态正确
                            val intent = android.content.Intent(this@App, com.example.aifloatingball.service.NetworkMonitorFloatingService::class.java)
                            try {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    startForegroundService(intent)
                                } else {
                                    startService(intent)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "重新启动悬浮窗网速服务失败", e)
                            }
                        }
                    } else {
                        Log.d(TAG, "悬浮窗网速开关未打开，跳过服务启动")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "启动悬浮窗网速服务失败", e)
                }
            }, 2000) // 延迟2秒启动，确保所有初始化完成
        } catch (e: Exception) {
            Log.e(TAG, "检查悬浮窗网速服务失败", e)
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