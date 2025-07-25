package com.example.aifloatingball.manager

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.aifloatingball.HomeActivity
import com.example.aifloatingball.SimpleModeActivity
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.service.DynamicIslandService
import com.example.aifloatingball.service.FloatingWindowService
import com.example.aifloatingball.service.SimpleModeService

/**
 * 统一模式管理器
 * 负责管理悬浮球、灵动岛、简易模式三种显示模式的切换
 */
object ModeManager {
    private const val TAG = "ModeManager"
    
    /**
     * 显示模式枚举
     */
    enum class DisplayMode(val value: String, val displayName: String) {
        FLOATING_BALL("floating_ball", "悬浮球模式"),
        DYNAMIC_ISLAND("dynamic_island", "灵动岛模式"),
        SIMPLE_MODE("simple_mode", "简易模式");
        
        companion object {
            fun fromValue(value: String): DisplayMode {
                return values().find { it.value == value } ?: SIMPLE_MODE
            }
        }
    }
    
    /**
     * 获取当前显示模式
     */
    fun getCurrentMode(context: Context): DisplayMode {
        val settingsManager = SettingsManager.getInstance(context)
        return DisplayMode.fromValue(settingsManager.getDisplayMode())
    }
    
    /**
     * 切换到指定模式
     * @param context 上下文
     * @param targetMode 目标模式
     * @param fromMode 来源模式（用于优化切换逻辑）
     */
    fun switchToMode(context: Context, targetMode: DisplayMode, fromMode: DisplayMode? = null) {
        Log.d(TAG, "切换模式: ${fromMode?.displayName} -> ${targetMode.displayName}")
        
        try {
            // 1. 停止所有相关服务和Activity
            stopAllModes(context)
            
            // 2. 更新设置
            val settingsManager = SettingsManager.getInstance(context)
            settingsManager.setDisplayMode(targetMode.value)
            
            // 3. 启动目标模式
            startTargetMode(context, targetMode)
            
            Log.d(TAG, "模式切换完成: ${targetMode.displayName}")
            
        } catch (e: Exception) {
            Log.e(TAG, "模式切换失败", e)
            // 如果切换失败，尝试启动简易模式作为备选
            if (targetMode != DisplayMode.SIMPLE_MODE) {
                startTargetMode(context, DisplayMode.SIMPLE_MODE)
            }
        }
    }
    
    /**
     * 停止所有模式
     */
    private fun stopAllModes(context: Context) {
        Log.d(TAG, "停止所有模式")
        
        // 停止所有服务
        context.stopService(Intent(context, FloatingWindowService::class.java))
        context.stopService(Intent(context, DynamicIslandService::class.java))
        context.stopService(Intent(context, SimpleModeService::class.java))
        
        // 注意：不直接finish SimpleModeActivity，因为可能是从SimpleModeActivity发起的切换
        // SimpleModeActivity会在适当的时候自己finish
    }
    
    /**
     * 启动目标模式
     */
    private fun startTargetMode(context: Context, targetMode: DisplayMode) {
        when (targetMode) {
            DisplayMode.FLOATING_BALL -> {
                if (hasOverlayPermission(context)) {
                    try {
                        val intent = Intent(context, FloatingWindowService::class.java)
                        context.startService(intent)
                        Log.d(TAG, "启动悬浮球服务")
                    } catch (e: Exception) {
                        Log.e(TAG, "启动悬浮球服务失败", e)
                        // 回退到简易模式
                        startTargetMode(context, DisplayMode.SIMPLE_MODE)
                    }
                } else {
                    Log.w(TAG, "没有悬浮窗权限，回退到简易模式")
                    startTargetMode(context, DisplayMode.SIMPLE_MODE)
                }
            }
            
            DisplayMode.DYNAMIC_ISLAND -> {
                if (hasOverlayPermission(context)) {
                    try {
                        val intent = Intent(context, DynamicIslandService::class.java)
                        context.startService(intent)
                        Log.d(TAG, "启动灵动岛服务")
                    } catch (e: Exception) {
                        Log.e(TAG, "启动灵动岛服务失败", e)
                        // 回退到简易模式
                        startTargetMode(context, DisplayMode.SIMPLE_MODE)
                    }
                } else {
                    Log.w(TAG, "没有悬浮窗权限，回退到简易模式")
                    startTargetMode(context, DisplayMode.SIMPLE_MODE)
                }
            }
            
            DisplayMode.SIMPLE_MODE -> {
                try {
                    val intent = Intent(context, SimpleModeActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
                    context.startActivity(intent)
                    Log.d(TAG, "启动简易模式Activity")
                } catch (e: Exception) {
                    Log.e(TAG, "启动简易模式Activity失败", e)
                }
            }
        }
    }
    
    /**
     * 检查是否有悬浮窗权限
     */
    private fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }
    
    /**
     * 获取所有可用模式
     */
    fun getAvailableModes(context: Context): List<DisplayMode> {
        val modes = mutableListOf<DisplayMode>()
        
        // 简易模式始终可用
        modes.add(DisplayMode.SIMPLE_MODE)
        
        // 悬浮球和灵动岛需要悬浮窗权限
        if (hasOverlayPermission(context)) {
            modes.add(DisplayMode.FLOATING_BALL)
            modes.add(DisplayMode.DYNAMIC_ISLAND)
        }
        
        return modes
    }
    
    /**
     * 显示模式切换对话框
     */
    fun showModeSwitchDialog(context: Context, currentMode: DisplayMode, onModeSelected: ((DisplayMode) -> Unit)? = null) {
        val availableModes = getAvailableModes(context)
        val modeNames = availableModes.map { it.displayName }.toTypedArray()
        val currentIndex = availableModes.indexOf(currentMode)

        try {
            if (context is android.app.Activity) {
                android.app.AlertDialog.Builder(context)
                    .setTitle("选择显示模式")
                    .setSingleChoiceItems(modeNames, currentIndex) { dialog, which ->
                        val selectedMode = availableModes[which]
                        if (selectedMode != currentMode) {
                            onModeSelected?.invoke(selectedMode) ?: switchToMode(context, selectedMode, currentMode)
                        }
                        dialog.dismiss()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else {
                // 如果不是Activity上下文，直接切换到下一个模式
                switchToNextMode(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "显示模式切换对话框失败", e)
            // 回退到直接切换
            switchToNextMode(context)
        }
    }
    
    /**
     * 快速切换到下一个模式（循环切换）
     */
    fun switchToNextMode(context: Context) {
        val currentMode = getCurrentMode(context)
        val availableModes = getAvailableModes(context)
        val currentIndex = availableModes.indexOf(currentMode)
        val nextIndex = (currentIndex + 1) % availableModes.size
        val nextMode = availableModes[nextIndex]
        
        switchToMode(context, nextMode, currentMode)
    }
    
    /**
     * 检查指定模式是否正在运行
     */
    fun isModeRunning(context: Context, mode: DisplayMode): Boolean {
        return when (mode) {
            DisplayMode.FLOATING_BALL -> FloatingWindowService.isRunning(context)
            DisplayMode.DYNAMIC_ISLAND -> DynamicIslandService.isRunning(context)
            DisplayMode.SIMPLE_MODE -> SimpleModeService.isRunning(context) || isSimpleModeActivityRunning(context)
        }
    }
    
    /**
     * 检查SimpleModeActivity是否正在运行
     */
    private fun isSimpleModeActivityRunning(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val runningTasks = activityManager.getRunningTasks(10)
        
        for (task in runningTasks) {
            val className = task.topActivity?.className
            if (className == "com.example.aifloatingball.SimpleModeActivity") {
                return true
            }
        }
        return false
    }
}
