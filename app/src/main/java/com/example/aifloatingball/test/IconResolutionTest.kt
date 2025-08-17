package com.example.aifloatingball.test

import android.content.Context
import com.example.aifloatingball.config.IconResolutionConfig
import com.example.aifloatingball.manager.AppStoreIconManager
import kotlinx.coroutines.runBlocking

/**
 * 图标分辨率优化测试类
 * 用于验证修复后的功能是否正常工作
 */
class IconResolutionTest(private val context: Context) {
    
    /**
     * 测试不同显示场景的图标获取
     */
    fun testIconResolutionForDifferentContexts() {
        val appStoreIconManager = AppStoreIconManager.getInstance(context)
        
        // 测试应用搜索网格场景
        testAppSearchGridIcon(appStoreIconManager)
        
        // 测试小组件场景
        testWidgetIcon(appStoreIconManager)
        
        // 测试工具栏场景
        testToolbarIcon(appStoreIconManager)
        
        // 测试搜索引擎场景
        testSearchEngineIcon(appStoreIconManager)
    }
    
    private fun testAppSearchGridIcon(manager: AppStoreIconManager) {
        println("测试应用搜索网格图标获取...")
        
        runBlocking {
            manager.getAppStoreIcon(
                packageName = "com.tencent.mm",
                appName = "微信",
                displayContext = IconResolutionConfig.DisplayContext.APP_SEARCH_GRID
            ) { icon ->
                if (icon != null) {
                    println("✅ 应用搜索网格图标获取成功")
                } else {
                    println("❌ 应用搜索网格图标获取失败")
                }
            }
        }
    }
    
    private fun testWidgetIcon(manager: AppStoreIconManager) {
        println("测试小组件图标获取...")
        
        runBlocking {
            manager.getAppStoreIcon(
                packageName = "com.netease.cloudmusic",
                appName = "网易云音乐",
                displayContext = IconResolutionConfig.DisplayContext.WIDGET_ICON
            ) { icon ->
                if (icon != null) {
                    println("✅ 小组件图标获取成功")
                } else {
                    println("❌ 小组件图标获取失败")
                }
            }
        }
    }
    
    private fun testToolbarIcon(manager: AppStoreIconManager) {
        println("测试工具栏图标获取...")
        
        runBlocking {
            manager.getAppStoreIcon(
                packageName = "com.baidu.searchbox",
                appName = "百度",
                displayContext = IconResolutionConfig.DisplayContext.TOOLBAR_ICON
            ) { icon ->
                if (icon != null) {
                    println("✅ 工具栏图标获取成功")
                } else {
                    println("❌ 工具栏图标获取失败")
                }
            }
        }
    }
    
    private fun testSearchEngineIcon(manager: AppStoreIconManager) {
        println("测试搜索引擎图标获取...")
        
        runBlocking {
            manager.getAppStoreIcon(
                packageName = "com.google.android.googlequicksearchbox",
                appName = "Google",
                displayContext = IconResolutionConfig.DisplayContext.SEARCH_ENGINE_ICON
            ) { icon ->
                if (icon != null) {
                    println("✅ 搜索引擎图标获取成功")
                } else {
                    println("❌ 搜索引擎图标获取失败")
                }
            }
        }
    }
    
    /**
     * 测试性能模式配置
     */
    fun testPerformanceModeConfiguration() {
        println("测试性能模式配置...")
        
        // 测试质量优先模式
        IconResolutionConfig.setPerformanceMode(IconResolutionConfig.PerformanceMode.QUALITY_FIRST)
        val qualityResolutions = IconResolutionConfig.getRecommendedResolutions(
            IconResolutionConfig.DisplayContext.APP_SEARCH_GRID
        )
        println("质量优先模式推荐分辨率: ${qualityResolutions.joinToString { it.suffix }}")
        
        // 测试平衡模式
        IconResolutionConfig.setPerformanceMode(IconResolutionConfig.PerformanceMode.BALANCED)
        val balancedResolutions = IconResolutionConfig.getRecommendedResolutions(
            IconResolutionConfig.DisplayContext.APP_SEARCH_GRID
        )
        println("平衡模式推荐分辨率: ${balancedResolutions.joinToString { it.suffix }}")
        
        // 测试速度优先模式
        IconResolutionConfig.setPerformanceMode(IconResolutionConfig.PerformanceMode.SPEED_FIRST)
        val speedResolutions = IconResolutionConfig.getRecommendedResolutions(
            IconResolutionConfig.DisplayContext.APP_SEARCH_GRID
        )
        println("速度优先模式推荐分辨率: ${speedResolutions.joinToString { it.suffix }}")
        
        // 恢复默认配置
        IconResolutionConfig.resetToDefaults()
    }
    
    /**
     * 测试自动设备配置
     */
    fun testAutoDeviceConfiguration() {
        println("测试自动设备配置...")
        
        // 模拟高端设备
        IconResolutionConfig.autoConfigureForDevice(6144, true)
        println("高端设备配置: ${IconResolutionConfig.currentPerformanceMode}")
        
        // 模拟中端设备
        IconResolutionConfig.autoConfigureForDevice(3072, false)
        println("中端设备配置: ${IconResolutionConfig.currentPerformanceMode}")
        
        // 模拟低端设备
        IconResolutionConfig.autoConfigureForDevice(1024, false)
        println("低端设备配置: ${IconResolutionConfig.currentPerformanceMode}")
        
        // 恢复默认配置
        IconResolutionConfig.resetToDefaults()
    }
    
    /**
     * 测试配置摘要
     */
    fun testConfigurationSummary() {
        println("当前配置摘要:")
        println(IconResolutionConfig.getConfigSummary())
    }
    
    /**
     * 运行所有测试
     */
    fun runAllTests() {
        println("=== 开始图标分辨率优化测试 ===")
        
        try {
            testPerformanceModeConfiguration()
            println()
            
            testAutoDeviceConfiguration()
            println()
            
            testConfigurationSummary()
            println()
            
            testIconResolutionForDifferentContexts()
            println()
            
            println("=== 所有测试完成 ===")
        } catch (e: Exception) {
            println("❌ 测试过程中发生错误: ${e.message}")
            e.printStackTrace()
        }
    }
}

/**
 * 在Activity中使用测试的示例
 */
/*
class TestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 运行图标分辨率测试
        val test = IconResolutionTest(this)
        test.runAllTests()
    }
}
*/
