package com.example.aifloatingball.service

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.*
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.aifloatingball.R
import com.example.aifloatingball.SimpleModeActivity

class SimpleModeService : Service() {
    private var windowManager: WindowManager? = null
    private var minimizedBar: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var initialY = 0
    private var initialTouchY = 0f

        companion object {
        private const val TAG = "SimpleModeService"
        const val ACTION_MINIMIZE = "com.example.aifloatingball.ACTION_MINIMIZE"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "simple_mode_channel"
        
        /**
         * 检查SimpleModeService是否正在运行
         */
        @Suppress("DEPRECATION") // 为了兼容性保留旧API
        fun isRunning(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            return manager.getRunningServices(Integer.MAX_VALUE)
                .any { it.service.className == SimpleModeService::class.java.name }
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        // 创建通知渠道（Android 8.0+）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "简易模式服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持简易模式最小化条显示"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
        
        // 启动前台服务
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_MINIMIZE) {
            showMinimizedBar()
        }
        return START_STICKY // 使用 START_STICKY 确保服务被系统杀死后会重新创建
    }
    
    private fun createNotification(): Notification {
        val notificationIntent = Intent(this, SimpleModeActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) 
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            else 
                PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("简易模式已最小化")
            .setContentText("点击小蓝条恢复简易模式")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showMinimizedBar() {
        // 简化权限检查，没有权限时直接停止服务
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Log.w("SimpleModeService", "No overlay permission, stopping service")
            stopSelf()
            return
        }

        try {
            // 如果已经存在，先移除
            minimizedBar?.let {
                windowManager?.removeView(it)
                minimizedBar = null
            }

            // 创建最小化条视图
            minimizedBar = LayoutInflater.from(this).inflate(R.layout.minimized_simple_mode_bar, null)

            // 设置窗口参数
            params = WindowManager.LayoutParams(
                (12 * resources.displayMetrics.density).toInt(), // 12dp宽
                (80 * resources.displayMetrics.density).toInt(), // 80dp高
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else 
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.END or Gravity.TOP // 改为TOP以支持垂直拖动
                x = 0
                y = 0
            }

            // 设置触摸事件处理
            minimizedBar?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                        // 记录初始位置
                        initialY = params?.y ?: 0
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                        // 计算移动距离
                        val deltaY = (event.rawY - initialTouchY).toInt()
                        
                        // 更新位置
                        params?.y = initialY + deltaY
                        
                        // 应用新位置
                        params?.let { p ->
                            try {
                                windowManager?.updateViewLayout(minimizedBar, p)
                        } catch (e: Exception) {
                                Log.e(TAG, "Failed to update minimized bar position", e)
                            }
                        }
                        true
                }
                MotionEvent.ACTION_UP -> {
                        // 如果移动距离很小，视为点击
                        val deltaY = Math.abs(event.rawY - initialTouchY)
                        if (deltaY < ViewConfiguration.get(this).scaledTouchSlop) {
                            view.performClick()
                    }
                    true
                }
                else -> false
            }
        }

                        // 设置点击事件
            minimizedBar?.setOnClickListener {
                Log.d(TAG, "Minimized bar clicked, launching SimpleModeActivity")
                
                // 启动SimpleModeActivity
                val intent = Intent(this, SimpleModeActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                startActivity(intent)
                
                // 停止服务
                stopSelf()
            }

            // 显示最小化条
            windowManager?.addView(minimizedBar, params)
            Log.d(TAG, "Minimized bar added to window manager")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show minimized bar", e)
            Toast.makeText(this, "无法显示最小化条", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "SimpleModeService onDestroy")
        minimizedBar?.let {
            try {
                windowManager?.removeView(it)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove minimized bar", e)
            }
            minimizedBar = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
