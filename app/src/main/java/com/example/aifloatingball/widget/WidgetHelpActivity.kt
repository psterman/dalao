package com.example.aifloatingball.widget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.example.aifloatingball.R

/**
 * 小组件帮助Activity
 * 为用户提供添加小组件的详细指导
 */
class WidgetHelpActivity : Activity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.widget_help_layout)
        
        setupUI()
    }
    
    private fun setupUI() {
        val deviceBrand = WidgetCompatibilityHelper.getDeviceBrand()
        
        val titleText = findViewById<TextView>(R.id.help_title)
        val instructionText = findViewById<TextView>(R.id.help_instruction)
        val deviceInfoText = findViewById<TextView>(R.id.help_device_info)
        val illustrationImage = findViewById<ImageView>(R.id.help_illustration)
        val openSettingsButton = findViewById<Button>(R.id.help_open_settings)
        val closeButton = findViewById<Button>(R.id.help_close)
        
        // 设置标题
        titleText.text = "如何添加AI搜索小组件"
        
        // 根据设备品牌设置不同的说明
        val instruction = when {
            WidgetCompatibilityHelper.isXiaomiDevice() -> {
                """
                小米/红米/POCO设备添加步骤：

                1. 长按桌面空白区域
                2. 点击"小组件"或"添加小组件"
                3. 在应用列表中找到"AI悬浮球"
                4. 选择"AI悬浮球搜索助手"
                5. 拖拽到桌面合适位置

                注意：如果找不到小组件，请检查应用权限设置
                """.trimIndent()
            }
            WidgetCompatibilityHelper.isVivoDevice() -> {
                """
                vivo/iQOO设备添加步骤：

                1. 长按桌面空白区域
                2. 点击"原子组件"或"小组件"
                3. 在应用列表中找到"AI悬浮球"
                4. 选择"AI悬浮球搜索助手"
                5. 拖拽到桌面合适位置

                注意：部分vivo设备可能需要在设置中开启小组件权限
                """.trimIndent()
            }
            WidgetCompatibilityHelper.isOppoDevice() -> {
                """
                OPPO设备添加步骤：

                1. 长按桌面空白区域
                2. 点击"小组件"
                3. 在应用列表中找到"AI悬浮球"
                4. 选择"AI悬浮球搜索助手"
                5. 拖拽到桌面合适位置

                注意：ColorOS系统通常对小组件支持良好
                """.trimIndent()
            }
            WidgetCompatibilityHelper.isOnePlusDevice() -> {
                """
                一加设备添加步骤：

                1. 长按桌面空白区域
                2. 点击"小组件"
                3. 在应用列表中找到"AI悬浮球"
                4. 选择"AI悬浮球搜索助手"
                5. 拖拽到桌面合适位置

                注意：OxygenOS接近原生Android体验
                """.trimIndent()
            }
            WidgetCompatibilityHelper.isHuaweiDevice() -> {
                """
                华为设备添加步骤：

                1. 长按桌面空白区域
                2. 点击"小组件"
                3. 在应用列表中找到"AI悬浮球"
                4. 选择"AI悬浮球搜索助手"
                5. 拖拽到桌面合适位置

                注意：EMUI/HarmonyOS设备建议同时使用服务卡片功能
                """.trimIndent()
            }
            WidgetCompatibilityHelper.isHonorDevice() -> {
                """
                荣耀设备添加步骤：

                1. 长按桌面空白区域
                2. 点击"小组件"
                3. 在应用列表中找到"AI悬浮球"
                4. 选择"AI悬浮球搜索助手"
                5. 拖拽到桌面合适位置

                注意：MagicOS支持Magic Live智能服务
                """.trimIndent()
            }
            WidgetCompatibilityHelper.isMeizuDevice() -> {
                """
                魅族设备添加步骤：

                1. 长按桌面空白区域
                2. 点击"小组件"
                3. 在应用列表中找到"AI悬浮球"
                4. 选择"AI悬浮球搜索助手"
                5. 拖拽到桌面合适位置

                注意：Flyme系统对小组件支持良好
                """.trimIndent()
            }
            WidgetCompatibilityHelper.isSamsungDevice() -> {
                """
                三星设备添加步骤：

                1. 长按桌面空白区域
                2. 点击"小组件"
                3. 在应用列表中找到"AI悬浮球"
                4. 选择"AI悬浮球搜索助手"
                5. 拖拽到桌面合适位置

                注意：One UI系统支持多种小组件尺寸
                """.trimIndent()
            }
            WidgetCompatibilityHelper.isRealmeDevice() -> {
                """
                realme设备添加步骤：

                1. 长按桌面空白区域
                2. 点击"小组件"
                3. 在应用列表中找到"AI悬浮球"
                4. 选择"AI悬浮球搜索助手"
                5. 拖拽到桌面合适位置

                注意：realme UI基于ColorOS，兼容性良好
                """.trimIndent()
            }
            else -> {
                """
                通用Android设备添加步骤：

                1. 长按桌面空白区域
                2. 点击"小组件"或"Widgets"
                3. 在应用列表中找到"AI悬浮球"
                4. 选择"AI悬浮球搜索助手"
                5. 拖拽到桌面合适位置

                注意：不同启动器可能界面略有差异
                """.trimIndent()
            }
        }
        
        instructionText.text = instruction
        
        // 显示设备信息
        deviceInfoText.text = "设备信息：${WidgetCompatibilityHelper.getDeviceInfo()}"
        
        // 设置插图（根据设备品牌可以设置不同的图片）
        when {
            WidgetCompatibilityHelper.isXiaomiDevice() -> {
                illustrationImage.setImageResource(R.drawable.ic_xiaomi_widget_guide)
            }
            WidgetCompatibilityHelper.isVivoDevice() -> {
                illustrationImage.setImageResource(R.drawable.ic_vivo_widget_guide)
            }
            WidgetCompatibilityHelper.isOppoDevice() -> {
                illustrationImage.setImageResource(R.drawable.ic_oppo_widget_guide)
            }
            WidgetCompatibilityHelper.isOnePlusDevice() -> {
                illustrationImage.setImageResource(R.drawable.ic_oneplus_widget_guide)
            }
            WidgetCompatibilityHelper.isHuaweiDevice() -> {
                illustrationImage.setImageResource(R.drawable.ic_huawei_widget_guide)
            }
            WidgetCompatibilityHelper.isHonorDevice() -> {
                illustrationImage.setImageResource(R.drawable.ic_honor_widget_guide)
            }
            WidgetCompatibilityHelper.isMeizuDevice() -> {
                illustrationImage.setImageResource(R.drawable.ic_meizu_widget_guide)
            }
            WidgetCompatibilityHelper.isSamsungDevice() -> {
                illustrationImage.setImageResource(R.drawable.ic_samsung_widget_guide)
            }
            WidgetCompatibilityHelper.isRealmeDevice() -> {
                illustrationImage.setImageResource(R.drawable.ic_realme_widget_guide)
            }
            else -> {
                illustrationImage.setImageResource(R.drawable.ic_generic_widget_guide)
            }
        }
        
        // 打开设置按钮
        openSettingsButton.setOnClickListener {
            WidgetCompatibilityHelper.openWidgetSettings(this)
        }
        
        // 关闭按钮
        closeButton.setOnClickListener {
            finish()
        }
    }
}
