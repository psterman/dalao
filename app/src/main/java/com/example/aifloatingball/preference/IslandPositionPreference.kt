package com.example.aifloatingball.preference

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.util.Log
import android.widget.Toast
import androidx.preference.SeekBarPreference
import com.example.aifloatingball.SettingsManager
import com.example.aifloatingball.service.DynamicIslandService

class IslandPositionPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.seekBarPreferenceStyle,
    defStyleRes: Int = 0
) : SeekBarPreference(context, attrs, defStyleAttr, defStyleRes) {

    private val settingsManager = SettingsManager.getInstance(context)
    
    init {
        // 设置初始值
        value = settingsManager.getIslandPosition()
        
        // 监听滑动变化，实现实时预览
        setOnPreferenceChangeListener { _, newValue ->
            val position = newValue as Int
            
            // 检查当前是否为灵动岛模式
            val displayMode = settingsManager.getDisplayMode()
            if (displayMode != "dynamic_island") {
                Log.w("IslandPosition", "当前不是灵动岛模式: $displayMode")
                Toast.makeText(context, "请先切换到灵动岛模式", Toast.LENGTH_SHORT).show()
                // 仍然保存设置，但不发送预览广播
                settingsManager.setIslandPosition(position)
                updateSummary(position)
                return@setOnPreferenceChangeListener true
            }
            
            // 检查DynamicIslandService是否在运行
            if (!DynamicIslandService.isRunning) {
                Log.w("IslandPosition", "DynamicIslandService未运行，尝试启动")
                // 尝试启动服务
                val serviceIntent = Intent(context, DynamicIslandService::class.java)
                try {
                    context.startForegroundService(serviceIntent)
                    // 给服务一点时间启动
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        sendPositionUpdate(position)
                    }, 500)
                } catch (e: Exception) {
                    Log.e("IslandPosition", "启动DynamicIslandService失败", e)
                    Toast.makeText(context, "无法启动灵动岛服务", Toast.LENGTH_SHORT).show()
                }
            } else {
                sendPositionUpdate(position)
                // 添加直接调用作为备用方案
                try {
                    val serviceClass = Class.forName("com.example.aifloatingball.service.DynamicIslandService")
                    val isRunningField = serviceClass.getDeclaredField("isRunning")
                    val isRunning = isRunningField.getBoolean(null)
                    Log.d("IslandPosition", "服务运行状态检查: $isRunning")
                } catch (e: Exception) {
                    Log.e("IslandPosition", "无法检查服务状态", e)
                }
            }
            
            // 保存设置到本地（确保即使服务没收到广播也能保存）
            settingsManager.setIslandPosition(position)
            // 更新摘要文本
            updateSummary(position)
            
            true
        }
        
        // 设置初始摘要
        updateSummary(value)
    }
    
    private fun sendPositionUpdate(position: Int) {
        val intent = Intent("com.example.aifloatingball.ACTION_UPDATE_ISLAND_POSITION")
        intent.putExtra("position", position)
        intent.setPackage(context.packageName) // 确保广播只在应用内传递
        Log.d("IslandPosition", "发送位置更新广播: position=$position")
        try {
            context.sendBroadcast(intent)
            Log.d("IslandPosition", "广播发送成功")
        } catch (e: Exception) {
            Log.e("IslandPosition", "广播发送失败", e)
        }
    }
    
    private fun updateSummary(position: Int) {
        summary = when {
            position == 0 -> "完全靠左 ($position%)"
            position < 20 -> "偏左 ($position%)"
            position < 45 -> "左偏中 ($position%)"
            position < 55 -> "居中 ($position%)"
            position < 80 -> "右偏中 ($position%)"
            position < 100 -> "偏右 ($position%)"
            else -> "完全靠右 ($position%)"
        }
    }
    
    override fun onSetInitialValue(defaultValue: Any?) {
        val initialValue = getPersistedInt(50)
        value = initialValue
        updateSummary(initialValue)
    }
} 