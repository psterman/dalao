package com.example.aifloatingball.views

import android.content.Context
import android.widget.Toast
import com.example.aifloatingball.R

/**
 * QuarterArcOperationBar使用示例
 */
object QuarterArcOperationBarExample {
    
    /**
     * 创建自定义按钮配置示例
     */
    fun createCustomButtonConfigs(context: Context): List<QuarterArcOperationBar.ButtonConfig> {
        return listOf(
            QuarterArcOperationBar.ButtonConfig(
                icon = R.drawable.ic_refresh,
                action = {
                    Toast.makeText(context, "自定义刷新操作", Toast.LENGTH_SHORT).show()
                },
                description = "自定义刷新",
                isEnabled = true
            ),
            QuarterArcOperationBar.ButtonConfig(
                icon = R.drawable.ic_arrow_back,
                action = {
                    Toast.makeText(context, "自定义返回操作", Toast.LENGTH_SHORT).show()
                },
                description = "自定义返回",
                isEnabled = true
            ),
            QuarterArcOperationBar.ButtonConfig(
                icon = R.drawable.ic_tab_switch,
                action = {
                    Toast.makeText(context, "自定义标签切换", Toast.LENGTH_SHORT).show()
                },
                description = "自定义标签切换",
                isEnabled = false // 这个按钮被禁用
            )
        )
    }
    
    /**
     * 配置QuarterArcOperationBar的示例
     */
    fun configureOperationBar(operationBar: QuarterArcOperationBar, context: Context) {
        // 设置自定义按钮
        val customConfigs = createCustomButtonConfigs(context)
        operationBar.setButtonConfigs(customConfigs)
        
        // 设置圆弧大小
        operationBar.setArcRadius(100f) // 100dp
        
        // 设置左手模式
        operationBar.setLeftHandedMode(false)
        
        // 添加新按钮
        operationBar.addButton(
            QuarterArcOperationBar.ButtonConfig(
                icon = R.drawable.ic_undo,
                action = {
                    Toast.makeText(context, "动态添加的按钮", Toast.LENGTH_SHORT).show()
                },
                description = "动态按钮",
                isEnabled = true
            )
        )
    }
}
