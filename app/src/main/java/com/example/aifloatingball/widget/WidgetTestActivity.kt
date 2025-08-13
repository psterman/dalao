package com.example.aifloatingball.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.example.aifloatingball.R

/**
 * 小组件测试Activity
 * 用于测试小组件的各种功能
 */
class WidgetTestActivity : Activity() {
    
    companion object {
        private const val TAG = "WidgetTestActivity"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.d(TAG, "WidgetTestActivity onCreate")
        
        // 处理小组件配置
        handleWidgetConfiguration()
        
        // 立即关闭Activity
        finish()
    }
    
    private fun handleWidgetConfiguration() {
        try {
            val appWidgetId = intent?.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
            
            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                Log.d(TAG, "配置小组件: $appWidgetId")
                
                // 更新小组件
                val appWidgetManager = AppWidgetManager.getInstance(this)
                SearchWidgetProvider().onUpdate(this, appWidgetManager, intArrayOf(appWidgetId))
                
                // 返回结果
                val resultValue = Intent().apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                setResult(RESULT_OK, resultValue)
                
                Toast.makeText(this, "小组件配置完成", Toast.LENGTH_SHORT).show()
            } else {
                Log.e(TAG, "无效的小组件ID")
                setResult(RESULT_CANCELED)
            }
        } catch (e: Exception) {
            Log.e(TAG, "配置小组件失败", e)
            setResult(RESULT_CANCELED)
        }
    }
}
