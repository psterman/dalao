package com.example.aifloatingball.service

import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.aifloatingball.utils.BitmapUtils

class NotificationListener : NotificationListenerService() {

    companion object {
        const val ACTION_NOTIFICATION = "com.example.aifloatingball.NOTIFICATION_ACTION"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_NOTIFICATION_KEY = "notification_key"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_ICON = "icon"

        const val COMMAND_POSTED = "posted"
        const val COMMAND_REMOVED = "removed"
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        // 过滤掉持续的、前台服务的通知，只处理真正的用户通知
        if (sbn.isOngoing || (sbn.notification.flags and android.app.Notification.FLAG_FOREGROUND_SERVICE != 0)) {
            return
        }

        val packageName = sbn.packageName
        val key = sbn.key

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

        val intent = Intent(ACTION_NOTIFICATION).apply {
            putExtra(EXTRA_COMMAND, COMMAND_POSTED)
            putExtra(EXTRA_NOTIFICATION_KEY, key)
            putExtra(EXTRA_PACKAGE_NAME, packageName)
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

        val intent = Intent(ACTION_NOTIFICATION).apply {
            putExtra(EXTRA_COMMAND, COMMAND_REMOVED)
            putExtra(EXTRA_NOTIFICATION_KEY, key)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
} 