package com.example.aifloatingball.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 权限检查工具类
 */
object PermissionUtils {
    
    /**
     * 检查存储权限
     */
    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 不需要WRITE_EXTERNAL_STORAGE权限
            true
        } else {
            // Android 12及以下需要WRITE_EXTERNAL_STORAGE权限
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
    
    /**
     * 检查录音权限
     */
    fun hasRecordAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * 检查通知权限
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // Android 12及以下不需要通知权限
            true
        }
    }
    
    /**
     * 检查悬浮窗权限
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            android.provider.Settings.canDrawOverlays(context)
        } else {
            true
        }
    }
    
    /**
     * 获取权限状态描述
     */
    fun getPermissionStatus(context: Context): String {
        val storage = if (hasStoragePermission(context)) "✅" else "❌"
        val audio = if (hasRecordAudioPermission(context)) "✅" else "❌"
        val notification = if (hasNotificationPermission(context)) "✅" else "❌"
        val overlay = if (hasOverlayPermission(context)) "✅" else "❌"
        
        return """
            存储权限: $storage
            录音权限: $audio
            通知权限: $notification
            悬浮窗权限: $overlay
        """.trimIndent()
    }
}
