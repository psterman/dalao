package com.example.aifloatingball.service

import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.aifloatingball.utils.BitmapUtils
import androidx.preference.PreferenceManager
import android.content.Context
import android.graphics.Bitmap

class NotificationListener : NotificationListenerService() {

    companion object {
        const val ACTION_NOTIFICATION = "com.example.aifloatingball.NOTIFICATION_ACTION"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_NOTIFICATION_KEY = "notification_key"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_ICON = "icon"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_SUB_TEXT = "sub_text"
        const val EXTRA_BIG_TEXT = "big_text"
        const val EXTRA_TIMESTAMP = "timestamp"

        const val COMMAND_POSTED = "posted"
        const val COMMAND_REMOVED = "removed"
        
        // 存储通知信息的Map
        private val notificationStorage = mutableMapOf<String, NotificationData>()
        
        fun getNotificationData(key: String): NotificationData? {
            return notificationStorage[key]
        }
        
        fun getAllNotifications(): List<NotificationData> {
            return notificationStorage.values.sortedByDescending { it.timestamp }
        }
    }
    
    data class NotificationData(
        val key: String,
        val packageName: String,
        val title: String,
        val text: String,
        val subText: String,
        val bigText: String,
        val icon: Bitmap?,
        val timestamp: Long
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
        val isForwardingEnabled = sharedPreferences.getBoolean("enable_notification_listener", false)
        if (!isForwardingEnabled) {
            return
        }

        // 过滤掉持续的、前台服务的通知，只处理真正的用户通知
        if (sbn.isOngoing || (sbn.notification.flags and android.app.Notification.FLAG_FOREGROUND_SERVICE != 0)) {
            return
        }

        // 检查应用白名单（如果启用了应用选择功能）
        val allowedPackages = sharedPreferences.getStringSet("selected_notification_apps", null)
        if (!allowedPackages.isNullOrEmpty() && sbn.packageName !in allowedPackages) {
            return
        }

        val packageName = sbn.packageName
        val key = sbn.key
        val notification = sbn.notification
        val title = notification?.extras?.getString("android.title") ?: ""
        val text = notification?.extras?.getString("android.text") ?: ""
        val subText = notification?.extras?.getString("android.subText") ?: ""
        val bigText = notification?.extras?.getString("android.bigText") ?: ""

        // 如果标题或文本为空，则可能不是有用的通知，跳过
        if (title.isBlank() && text.isBlank()) {
            return
        }

        // 提取图标
        val iconBitmap = try {
            val drawable = packageManager.getApplicationIcon(packageName)
            if (drawable is BitmapDrawable) {
                drawable.bitmap
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        // 存储完整的通知信息
        val notificationData = NotificationData(
            key = key,
            packageName = packageName,
            title = title,
            text = text,
            subText = subText,
            bigText = bigText,
            icon = iconBitmap,
            timestamp = sbn.postTime
        )
        notificationStorage[key] = notificationData

        // 清理过期的通知数据（保留最近100个）
        if (notificationStorage.size > 100) {
            val sortedKeys = notificationStorage.entries
                .sortedBy { it.value.timestamp }
                .map { it.key }
            sortedKeys.take(notificationStorage.size - 100).forEach { oldKey ->
                notificationStorage.remove(oldKey)
            }
        }

        val intent = Intent(ACTION_NOTIFICATION).apply {
            putExtra(EXTRA_COMMAND, COMMAND_POSTED)
            putExtra(EXTRA_NOTIFICATION_KEY, key)
            putExtra(EXTRA_PACKAGE_NAME, packageName)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_TEXT, text)
            putExtra(EXTRA_SUB_TEXT, subText)
            putExtra(EXTRA_BIG_TEXT, bigText)
            putExtra(EXTRA_TIMESTAMP, sbn.postTime)
            // 将Bitmap转换为ByteArray以便通过Intent传递
            iconBitmap?.let {
                val compressedIcon = BitmapUtils.compressBitmap(it)
                putExtra(EXTRA_ICON, compressedIcon)
            }
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        val key = sbn.key

        // 从存储中移除通知数据
        notificationStorage.remove(key)

        val intent = Intent(ACTION_NOTIFICATION).apply {
            putExtra(EXTRA_COMMAND, COMMAND_REMOVED)
            putExtra(EXTRA_NOTIFICATION_KEY, key)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
} 