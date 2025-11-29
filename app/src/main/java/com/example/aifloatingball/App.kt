package com.example.aifloatingball

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast

class App : Application() {
    
    companion object {
        const val CHANNEL_ID = "floating_service_channel"
        private const val TAG = "App"
        
        /**
         * 应用是否在前台
         * true: 应用在前台
         * false: 应用在后台
         */
        @Volatile
        var isAppInForeground: Boolean = false
            private set
        
        /**
         * 前台Activity数量
         * 当数量为0时，应用在后台
         * 当数量大于0时，应用在前台
         */
        private var foregroundActivityCount: Int = 0
        
        /**
         * 更新应用前台状态
         */
        @Synchronized
        fun updateForegroundState(isForeground: Boolean) {
            val oldState = isAppInForeground
            isAppInForeground = isForeground
            if (oldState != isAppInForeground) {
                Log.d(TAG, "应用前台状态变化: ${if (isAppInForeground) "进入前台" else "进入后台"}")
                // 通知NetworkMonitorFloatingService状态变化
                notifyNetworkMonitorService()
            }
        }
        
        /**
         * 通知NetworkMonitorFloatingService应用状态变化
         */
        private fun notifyNetworkMonitorService() {
            try {
                val context = instance
                if (context != null) {
                    val intent = Intent(context, com.example.aifloatingball.service.NetworkMonitorFloatingService::class.java)
                    intent.action = "com.example.aifloatingball.ACTION_APP_STATE_CHANGED"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "通知NetworkMonitorFloatingService失败", e)
            }
        }
        
        /**
         * Application实例
         */
        @Volatile
        private var instance: App? = null
        
        /**
         * 获取Application实例
         */
        fun getInstance(): App? = instance
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.d(TAG, "应用开始初始化")
        
        try {
            createNotificationChannel()
            
            // 注册Activity生命周期回调，用于检测应用前后台状态
            registerActivityLifecycleCallbacks()
            
            // 检查悬浮窗网速开关，如果打开则自动启动服务
            checkAndStartNetworkSpeedService()
            
            Log.d(TAG, "应用初始化完成")
        } catch (e: Exception) {
            Log.e(TAG, "应用初始化失败", e)
            Toast.makeText(this, "应用初始化失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * 注册Activity生命周期回调
     * 用于准确检测应用是否在前台
     */
    private fun registerActivityLifecycleCallbacks() {
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                // Activity创建时不做处理
            }
            
            override fun onActivityStarted(activity: Activity) {
                // Activity启动时，增加前台Activity计数
                synchronized(App) {
                    foregroundActivityCount++
                    // 确保计数不为负数
                    if (foregroundActivityCount < 0) {
                        foregroundActivityCount = 1
                    }
                    // 如果计数大于0，说明应用在前台
                    if (foregroundActivityCount > 0 && !isAppInForeground) {
                        // 应用进入前台（可能是首次启动或从后台恢复）
                        updateForegroundState(true)
                    }
                }
                Log.d(TAG, "Activity启动: ${activity.javaClass.simpleName}, 前台Activity数量: $foregroundActivityCount, 应用在前台: $isAppInForeground")
            }
            
            override fun onActivityResumed(activity: Activity) {
                // Activity恢复时不做处理（onStarted已经处理）
            }
            
            override fun onActivityPaused(activity: Activity) {
                // Activity暂停时不做处理
            }
            
            override fun onActivityStopped(activity: Activity) {
                // Activity停止时，减少前台Activity计数
                synchronized(App) {
                    foregroundActivityCount--
                    if (foregroundActivityCount == 0) {
                        // 从1变为0，说明应用进入后台
                        updateForegroundState(false)
                    }
                    // 确保计数不为负数
                    if (foregroundActivityCount < 0) {
                        foregroundActivityCount = 0
                    }
                }
                Log.d(TAG, "Activity停止: ${activity.javaClass.simpleName}, 前台Activity数量: $foregroundActivityCount")
            }
            
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
                // 不做处理
            }
            
            override fun onActivityDestroyed(activity: Activity) {
                // Activity销毁时不做处理
            }
        })
        Log.d(TAG, "Activity生命周期回调已注册")
    }
    
    override fun onTerminate() {
        super.onTerminate()
        instance = null
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