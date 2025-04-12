package com.example.aifloatingball.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import com.example.aifloatingball.DualFloatingWebViewService
import com.example.aifloatingball.SettingsManager

/**
 * 服务工具类
 * 用于管理服务相关操作
 */
object ServiceUtils {
    /**
     * 检查服务是否在运行
     */
    fun isServiceRunning(context: Context, serviceName: String): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceName == service.service.className) {
                return true
            }
        }
        return false
    }

    /**
     * 重启浮动窗口服务
     */
    fun restartFloatingService(context: Context) {
        // 停止现有服务
        context.stopService(Intent(context, DualFloatingWebViewService::class.java))
        
        // 等待短暂时间确保服务已停止
        Handler().postDelayed({
            // 启动新服务，并传递窗口数量
            val intent = Intent(context, DualFloatingWebViewService::class.java).apply {
                putExtra("window_count", SettingsManager.getInstance(context).getDefaultWindowCount())
            }
            context.startService(intent)
        }, 500) // 500毫秒延迟
    }
} 