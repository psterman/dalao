package com.example.aifloatingball.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.example.aifloatingball.R

object NotificationTestHelper {
    
    private const val CHANNEL_ID = "test_notification_channel"
    private const val CHANNEL_NAME = "测试通知"
    
    fun sendTestNotification(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // 创建通知渠道
        createNotificationChannel(context, notificationManager)
        
        // 测试通知内容
        val testNotifications = listOf(
            TestNotification("微信消息", "张三: 今天天气不错，要不要一起出去玩？"),
            TestNotification("淘宝购物", "您的订单已发货，预计明天到达，请注意查收"),
            TestNotification("系统提醒", "您的手机存储空间不足，建议清理缓存文件"),
            TestNotification("新闻推送", "科技新闻: AI技术在移动应用中的最新应用趋势"),
            TestNotification("邮件通知", "您收到一封来自 support@example.com 的重要邮件"),
            TestNotification("日历提醒", "会议提醒: 下午3点有一个重要的项目讨论会议"),
            TestNotification("天气预报", "明天将有小雨，气温15-22℃，记得带伞"),
            TestNotification("银行通知", "您的账户发生一笔支出，金额为￥128.50")
        )
        
        // 随机选择一个测试通知
        val testNotification = testNotifications.random()
        
        // 创建通知
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(testNotification.title)
            .setContentText(testNotification.content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setTimeoutAfter(15000) // 15秒后自动消失
            .build()
        
        // 发送通知
        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notification)
        
        // 延迟移除通知
        Handler(Looper.getMainLooper()).postDelayed({
            notificationManager.cancel(notificationId)
        }, 20000) // 20秒后移除
    }
    
    fun sendMultipleTestNotifications(context: Context, count: Int = 3) {
        repeat(count) { index ->
            Handler(Looper.getMainLooper()).postDelayed({
                sendTestNotification(context)
            }, index * 2000L) // 每2秒发送一个
        }
    }
    
    private fun createNotificationChannel(context: Context, notificationManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "用于测试灵动岛通知功能的通知渠道"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private data class TestNotification(
        val title: String,
        val content: String
    )
} 