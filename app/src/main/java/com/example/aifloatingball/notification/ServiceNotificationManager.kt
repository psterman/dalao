package com.example.aifloatingball.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.aifloatingball.HomeActivity
import com.example.aifloatingball.R

/**
 * 服务通知管理器，处理前台服务的通知创建和更新
 */
class ServiceNotificationManager(
    private val service: Service,
    private val channelId: String,
    private val channelName: String = "Dual Floating WebView Service",
    private val channelDescription: String = "Keeps the floating webview service running"
) {
    
    /**
     * 创建通知渠道（Android 8.0及以上需要）
     */
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = channelDescription
            }
            val notificationManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 创建前台服务通知
     */
    fun createNotification() = NotificationCompat.Builder(service, channelId)
        .setContentTitle("多窗口搜索")
        .setContentText("点击返回主界面")
        .setSmallIcon(R.mipmap.ic_launcher)
        .setOngoing(true)
        .setContentIntent(createPendingIntent())
        .build()
    
    /**
     * 创建点击通知时的待定意图
     */
    private fun createPendingIntent() = PendingIntent.getActivity(
        service,
        0,
        Intent(service, HomeActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE
    )
} 