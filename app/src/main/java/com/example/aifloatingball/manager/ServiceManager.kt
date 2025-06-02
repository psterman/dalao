package com.example.aifloatingball.manager

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.example.aifloatingball.service.DualFloatingWebViewService
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.utils.SearchParams

/**
 * 服务管理器
 * 集中管理应用中所有服务的启动、停止和状态检查
 */
class ServiceManager private constructor(private val context: Context) {
    
    companion object {
        @Volatile
        private var instance: ServiceManager? = null
        
        fun getInstance(context: Context): ServiceManager {
            return instance ?: synchronized(this) {
                instance ?: ServiceManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val settingsManager by lazy { SettingsManager.getInstance(context) }
    private val handler = Handler(Looper.getMainLooper())
    
    /**
     * 检查服务是否在运行
     */
    fun isServiceRunning(serviceName: String): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceName == service.service.className) {
                return true
            }
        }
        return false
    }
    
    /**
     * 启动多窗口搜索服务
     */
    fun startDualFloatingWebViewService(searchParams: SearchParams) {
        val intent = Intent(context, DualFloatingWebViewService::class.java).apply {
            putExtra("search_query", searchParams.query)
            putExtra("window_count", searchParams.windowCount)
            putExtra("engine_key", searchParams.engineKey)
        }
        context.startService(intent)
    }
    
    /**
     * 停止多窗口搜索服务
     */
    fun stopDualFloatingWebViewService() {
        context.stopService(Intent(context, DualFloatingWebViewService::class.java))
    }
    
    /**
     * 重启多窗口搜索服务
     */
    fun restartDualFloatingWebViewService() {
        // 停止现有服务
        stopDualFloatingWebViewService()
        
        // 等待短暂时间确保服务已停止
        handler.postDelayed({
            // 启动新服务，并传递窗口数量
            val intent = Intent(context, DualFloatingWebViewService::class.java).apply {
                putExtra("window_count", settingsManager.getDefaultWindowCount())
            }
            context.startService(intent)
        }, 500) // 500毫秒延迟
    }
} 