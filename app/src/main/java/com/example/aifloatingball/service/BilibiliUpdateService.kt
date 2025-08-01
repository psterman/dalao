package com.example.aifloatingball.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.aifloatingball.R
import com.example.aifloatingball.SimpleModeActivity
import com.example.aifloatingball.manager.BilibiliDynamicManager
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

/**
 * B站动态更新服务
 */
class BilibiliUpdateService : Service() {
    
    companion object {
        private const val TAG = "BilibiliUpdateService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "bilibili_update_channel"
        private const val WORK_NAME = "bilibili_dynamic_update"
        
        fun startService(context: Context) {
            val intent = Intent(context, BilibiliUpdateService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, BilibiliUpdateService::class.java)
            context.stopService(intent)
        }
        
        fun schedulePeriodicUpdate(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            
            val updateRequest = PeriodicWorkRequestBuilder<BilibiliUpdateWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(1, TimeUnit.HOURS) // 首次延迟1小时
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                updateRequest
            )
            
            Log.d(TAG, "Scheduled periodic bilibili update")
        }
        
        fun cancelPeriodicUpdate(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Cancelled periodic bilibili update")
        }
    }
    
    private lateinit var dynamicManager: BilibiliDynamicManager
    private var serviceJob: Job? = null
    
    override fun onCreate() {
        super.onCreate()
        dynamicManager = BilibiliDynamicManager.getInstance(this)
        createNotificationChannel()
        Log.d(TAG, "BilibiliUpdateService created")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("B站动态更新服务运行中"))
        
        // 启动更新任务
        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                updateNotification("正在更新B站动态...")
                val result = dynamicManager.updateAllDynamics()
                
                if (result.isSuccess) {
                    val dynamicsCount = dynamicManager.getAllDynamics().size
                    updateNotification("更新完成，共 $dynamicsCount 条动态")
                    Log.d(TAG, "Successfully updated bilibili dynamics: $dynamicsCount items")
                } else {
                    updateNotification("更新失败，请检查网络连接")
                    Log.e(TAG, "Failed to update bilibili dynamics", result.exceptionOrNull())
                }
                
                // 5秒后停止服务
                delay(5000)
                stopSelf()
            } catch (e: Exception) {
                Log.e(TAG, "Error in update service", e)
                updateNotification("更新出错")
                stopSelf()
            }
        }
        
        return START_NOT_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        serviceJob?.cancel()
        Log.d(TAG, "BilibiliUpdateService destroyed")
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "B站动态更新",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "B站用户动态更新通知"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(content: String): android.app.Notification {
        val intent = Intent(this, SimpleModeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("B站动态更新")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
    
    private fun updateNotification(content: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(content))
    }
}

/**
 * B站动态更新Worker
 */
class BilibiliUpdateWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {
    
    companion object {
        private const val TAG = "BilibiliUpdateWorker"
    }
    
    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Starting bilibili dynamics update work")
            
            val dynamicManager = BilibiliDynamicManager.getInstance(applicationContext)
            
            // 检查是否需要更新
            if (!dynamicManager.shouldUpdate()) {
                Log.d(TAG, "Update not needed yet")
                return Result.success()
            }
            
            // 执行更新
            val updateResult = dynamicManager.updateAllDynamics()
            
            if (updateResult.isSuccess) {
                val dynamicsCount = dynamicManager.getAllDynamics().size
                Log.d(TAG, "Successfully updated bilibili dynamics: $dynamicsCount items")

                // 发送更新完成通知
                sendUpdateNotification(dynamicsCount)

                Result.success()
            } else {
                Log.e(TAG, "Failed to update bilibili dynamics", updateResult.exceptionOrNull())
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in bilibili update worker", e)
            Result.failure()
        }
    }
    
    private fun sendUpdateNotification(dynamicsCount: Int) {
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // 创建通知渠道
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "bilibili_update_result",
                "B站动态更新结果",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }
        
        val intent = Intent(applicationContext, SimpleModeActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("show_bilibili_tab", true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(applicationContext, "bilibili_update_result")
            .setContentTitle("B站动态已更新")
            .setContentText("发现 $dynamicsCount 条新动态")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(1002, notification)
    }
}
