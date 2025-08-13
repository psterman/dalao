package com.example.aifloatingball.utils

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.Log
import com.example.aifloatingball.manager.AppIconManager
import com.example.aifloatingball.manager.IconPreloader
import com.example.aifloatingball.model.AppSearchConfig
import kotlinx.coroutines.*

/**
 * 图标测试助手
 * 用于测试和验证图标获取效果
 */
class IconTestHelper(private val context: Context) {
    
    private val iconManager = AppIconManager.getInstance(context)
    private val iconPreloader = IconPreloader.getInstance(context)
    private val iconProcessor = IconProcessor(context)
    
    companion object {
        private const val TAG = "IconTestHelper"
    }
    
    /**
     * 测试所有应用的图标获取效果
     */
    suspend fun testAllAppsIcons(apps: List<AppSearchConfig>): TestReport {
        Log.d(TAG, "开始测试${apps.size}个应用的图标获取效果")
        
        val results = mutableListOf<AppIconTestResult>()
        var totalTime = 0L
        
        for (app in apps) {
            val startTime = System.currentTimeMillis()
            val result = testSingleAppIcon(app)
            val endTime = System.currentTimeMillis()
            
            result.loadTime = endTime - startTime
            results.add(result)
            totalTime += result.loadTime
            
            Log.d(TAG, "${app.appName}: ${if (result.success) "✅" else "❌"} ${result.loadTime}ms")
            
            // 避免API调用过于频繁
            delay(100)
        }
        
        return generateTestReport(results, totalTime)
    }
    
    /**
     * 测试单个应用的图标获取
     */
    private suspend fun testSingleAppIcon(app: AppSearchConfig): AppIconTestResult {
        return withContext(Dispatchers.IO) {
            try {
                var icon: Drawable? = null
                var iconSource = "none"
                
                // 1. 检查是否已安装
                val isInstalled = isAppInstalled(app.packageName)
                if (isInstalled) {
                    try {
                        icon = context.packageManager.getApplicationIcon(app.packageName)
                        iconSource = "installed"
                    } catch (e: Exception) {
                        // 继续尝试其他方法
                    }
                }
                
                // 2. 检查预加载缓存
                if (icon == null) {
                    icon = iconPreloader.getPreloadedIcon(app.packageName, app.appName)
                    if (icon != null) {
                        iconSource = "preloaded"
                    }
                }
                
                // 3. 尝试在线获取
                if (icon == null) {
                    val onlineIcon = getOnlineIcon(app)
                    if (onlineIcon != null) {
                        icon = onlineIcon
                        iconSource = "online"
                    }
                }
                
                // 4. 使用字母图标
                if (icon == null) {
                    icon = generateLetterIcon(app)
                    iconSource = "letter"
                }
                
                AppIconTestResult(
                    app = app,
                    success = icon != null && iconSource != "letter",
                    iconSource = iconSource,
                    iconSize = getIconSize(icon),
                    loadTime = 0L // 将在外部设置
                )
                
            } catch (e: Exception) {
                AppIconTestResult(
                    app = app,
                    success = false,
                    iconSource = "error",
                    iconSize = "0x0",
                    loadTime = 0L,
                    error = e.message
                )
            }
        }
    }
    
    /**
     * 获取在线图标 (简化版测试)
     */
    private suspend fun getOnlineIcon(app: AppSearchConfig): Drawable? {
        return withContext(Dispatchers.IO) {
            try {
                var result: Drawable? = null
                val job = launch {
                    iconManager.getAppIconAsync(
                        packageName = app.packageName,
                        appName = app.appName
                    ) { downloadedIcon ->
                        result = downloadedIcon
                    }
                }
                job.join()
                result
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * 生成字母图标
     */
    private fun generateLetterIcon(app: AppSearchConfig): Drawable? {
        return iconProcessor.processIcon(
            createSimpleLetterDrawable(app.appName.firstOrNull()?.toString() ?: "A"),
            IconProcessor.IconStyle.ROUNDED_SQUARE
        )
    }
    
    /**
     * 创建简单字母图标
     */
    private fun createSimpleLetterDrawable(letter: String): Drawable? {
        // 这里可以创建一个简单的字母图标
        // 为了简化，返回null，让IconProcessor处理
        return null
    }
    
    /**
     * 检查应用是否已安装
     */
    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getApplicationInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * 获取图标尺寸
     */
    private fun getIconSize(drawable: Drawable?): String {
        return if (drawable != null) {
            "${drawable.intrinsicWidth}x${drawable.intrinsicHeight}"
        } else {
            "0x0"
        }
    }
    
    /**
     * 生成测试报告
     */
    private fun generateTestReport(results: List<AppIconTestResult>, totalTime: Long): TestReport {
        val totalApps = results.size
        val successfulApps = results.count { it.success }
        val successRate = if (totalApps > 0) (successfulApps.toDouble() / totalApps * 100).toInt() else 0
        
        val sourceStats = results.groupBy { it.iconSource }.mapValues { it.value.size }
        val avgLoadTime = if (totalApps > 0) totalTime / totalApps else 0L
        
        val report = TestReport(
            totalApps = totalApps,
            successfulApps = successfulApps,
            successRate = successRate,
            avgLoadTime = avgLoadTime,
            totalTime = totalTime,
            sourceStats = sourceStats,
            results = results
        )
        
        // 打印报告
        printTestReport(report)
        
        return report
    }
    
    /**
     * 打印测试报告
     */
    private fun printTestReport(report: TestReport) {
        Log.d(TAG, "=== 图标获取测试报告 ===")
        Log.d(TAG, "总应用数: ${report.totalApps}")
        Log.d(TAG, "成功获取: ${report.successfulApps}")
        Log.d(TAG, "成功率: ${report.successRate}%")
        Log.d(TAG, "平均加载时间: ${report.avgLoadTime}ms")
        Log.d(TAG, "总耗时: ${report.totalTime}ms")
        Log.d(TAG, "")
        Log.d(TAG, "图标来源统计:")
        report.sourceStats.forEach { (source, count) ->
            val percentage = (count.toDouble() / report.totalApps * 100).toInt()
            Log.d(TAG, "  $source: $count ($percentage%)")
        }
        Log.d(TAG, "========================")
        
        // 详细结果
        Log.d(TAG, "详细结果:")
        report.results.forEach { result ->
            val status = if (result.success) "✅" else "❌"
            Log.d(TAG, "$status ${result.app.appName}: ${result.iconSource} (${result.loadTime}ms)")
            if (result.error != null) {
                Log.d(TAG, "    错误: ${result.error}")
            }
        }
    }
    
    /**
     * 测试预加载效果
     */
    suspend fun testPreloadingEffect(apps: List<AppSearchConfig>): PreloadTestResult {
        Log.d(TAG, "测试预加载效果")
        
        // 清理现有缓存
        iconPreloader.clearPreloadCache()
        
        // 测试预加载前的加载时间
        val beforePreloadTimes = mutableListOf<Long>()
        for (app in apps.take(5)) { // 测试前5个应用
            val startTime = System.currentTimeMillis()
            testSingleAppIcon(app)
            val endTime = System.currentTimeMillis()
            beforePreloadTimes.add(endTime - startTime)
            delay(100)
        }
        
        // 执行预加载
        val preloadStartTime = System.currentTimeMillis()
        iconPreloader.preloadPopularApps(apps) { progress, total ->
            Log.d(TAG, "预加载进度: $progress/$total")
        }
        val preloadEndTime = System.currentTimeMillis()
        val preloadTime = preloadEndTime - preloadStartTime
        
        // 测试预加载后的加载时间
        val afterPreloadTimes = mutableListOf<Long>()
        for (app in apps.take(5)) {
            val startTime = System.currentTimeMillis()
            testSingleAppIcon(app)
            val endTime = System.currentTimeMillis()
            afterPreloadTimes.add(endTime - startTime)
            delay(100)
        }
        
        val avgBeforePreload = beforePreloadTimes.average().toLong()
        val avgAfterPreload = afterPreloadTimes.average().toLong()
        val speedImprovement = if (avgBeforePreload > 0) {
            ((avgBeforePreload - avgAfterPreload).toDouble() / avgBeforePreload * 100).toInt()
        } else 0
        
        val result = PreloadTestResult(
            preloadTime = preloadTime,
            avgLoadTimeBeforePreload = avgBeforePreload,
            avgLoadTimeAfterPreload = avgAfterPreload,
            speedImprovement = speedImprovement,
            preloadedCount = iconPreloader.getCacheStats().preloadedCount
        )
        
        Log.d(TAG, "=== 预加载测试结果 ===")
        Log.d(TAG, "预加载耗时: ${result.preloadTime}ms")
        Log.d(TAG, "预加载前平均加载时间: ${result.avgLoadTimeBeforePreload}ms")
        Log.d(TAG, "预加载后平均加载时间: ${result.avgLoadTimeAfterPreload}ms")
        Log.d(TAG, "速度提升: ${result.speedImprovement}%")
        Log.d(TAG, "预加载图标数: ${result.preloadedCount}")
        Log.d(TAG, "=====================")
        
        return result
    }
    
    // 数据类
    data class AppIconTestResult(
        val app: AppSearchConfig,
        val success: Boolean,
        val iconSource: String, // installed, preloaded, online, letter, error
        val iconSize: String,
        var loadTime: Long,
        val error: String? = null
    )
    
    data class TestReport(
        val totalApps: Int,
        val successfulApps: Int,
        val successRate: Int, // 百分比
        val avgLoadTime: Long, // 毫秒
        val totalTime: Long, // 毫秒
        val sourceStats: Map<String, Int>,
        val results: List<AppIconTestResult>
    )
    
    data class PreloadTestResult(
        val preloadTime: Long,
        val avgLoadTimeBeforePreload: Long,
        val avgLoadTimeAfterPreload: Long,
        val speedImprovement: Int, // 百分比
        val preloadedCount: Int
    )
}
