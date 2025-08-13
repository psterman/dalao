package com.example.aifloatingball.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import com.example.aifloatingball.R

/**
 * 小组件配置Activity
 * 用于配置小组件的初始设置，提高在各厂商设备上的兼容性
 */
class WidgetConfigureActivity : Activity() {
    
    companion object {
        private const val TAG = "WidgetConfigureActivity"
    }
    
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "WidgetConfigureActivity onCreate")
        
        // 设置结果为取消，如果用户没有完成配置就退出
        setResult(RESULT_CANCELED)
        
        // 获取小组件ID
        appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            Log.e(TAG, "无效的小组件ID")
            finish()
            return
        }
        
        // 设置简单的配置界面
        setupConfigurationUI()
    }
    
    private fun setupConfigurationUI() {
        // 创建简单的配置界面
        setContentView(R.layout.widget_configure_layout)
        
        val titleText = findViewById<TextView>(R.id.configure_title)
        val descriptionText = findViewById<TextView>(R.id.configure_description)
        val confirmButton = findViewById<Button>(R.id.configure_confirm)
        val cancelButton = findViewById<Button>(R.id.configure_cancel)
        
        titleText.text = "配置AI搜索小组件"
        descriptionText.text = "点击确认将小组件添加到桌面，您可以通过小组件快速访问AI对话、应用搜索和网络搜索功能。"
        
        confirmButton.setOnClickListener {
            configureWidget()
        }
        
        cancelButton.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }
    
    private fun configureWidget() {
        try {
            Log.d(TAG, "配置小组件: $appWidgetId")
            
            // 获取AppWidgetManager
            val appWidgetManager = AppWidgetManager.getInstance(this)
            
            // 更新小组件
            SearchWidgetProvider().onUpdate(this, appWidgetManager, intArrayOf(appWidgetId))
            
            // 保存配置到SharedPreferences（如果需要）
            saveWidgetConfiguration()
            
            // 设置成功结果
            val resultValue = Intent().apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            setResult(RESULT_OK, resultValue)
            
            // 显示成功提示
            Toast.makeText(this, "小组件配置完成！", Toast.LENGTH_SHORT).show()
            
            Log.d(TAG, "小组件配置成功: $appWidgetId")
            
            // 关闭配置界面
            finish()
            
        } catch (e: Exception) {
            Log.e(TAG, "配置小组件失败", e)
            Toast.makeText(this, "配置失败，请重试", Toast.LENGTH_SHORT).show()
            setResult(RESULT_CANCELED)
        }
    }
    
    private fun saveWidgetConfiguration() {
        try {
            val prefs = getSharedPreferences("widget_config", Context.MODE_PRIVATE)
            prefs.edit().apply {
                putBoolean("widget_${appWidgetId}_configured", true)
                putLong("widget_${appWidgetId}_create_time", System.currentTimeMillis())
                putString("widget_${appWidgetId}_version", "1.0")
                apply()
            }
            Log.d(TAG, "小组件配置已保存: $appWidgetId")
        } catch (e: Exception) {
            Log.e(TAG, "保存小组件配置失败", e)
        }
    }
}
