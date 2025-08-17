package com.example.aifloatingball.config

/**
 * 图标分辨率配置
 * 根据实际显示需求优化图标获取策略
 */
object IconResolutionConfig {
    
    /**
     * 图标分辨率枚举
     */
    enum class IconResolution(val size: Int, val suffix: String, val description: String) {
        ULTRA_HIGH(1024, "1024x1024", "超高清 - 适合高端设备或特殊需求"),
        HIGH(512, "512x512", "高清 - 适合48dp应用图标显示"),
        MEDIUM(256, "256x256", "中等 - 适合32dp小组件显示"),
        STANDARD(100, "100x100", "标准 - 适合24dp工具栏图标"),
        LOW(60, "60x60", "低分辨率 - 最小可用尺寸")
    }
    
    /**
     * 显示场景枚举
     */
    enum class DisplayContext(val targetSize: Int, val preferredResolutions: List<IconResolution>) {
        APP_SEARCH_GRID(48, listOf(IconResolution.HIGH, IconResolution.MEDIUM, IconResolution.STANDARD)),
        WIDGET_ICON(32, listOf(IconResolution.MEDIUM, IconResolution.STANDARD, IconResolution.HIGH)),
        TOOLBAR_ICON(24, listOf(IconResolution.STANDARD, IconResolution.MEDIUM, IconResolution.LOW)),
        SEARCH_ENGINE_ICON(40, listOf(IconResolution.MEDIUM, IconResolution.HIGH, IconResolution.STANDARD)),
        FLOATING_MENU(36, listOf(IconResolution.MEDIUM, IconResolution.STANDARD, IconResolution.HIGH))
    }
    
    /**
     * 性能模式枚举
     */
    enum class PerformanceMode(val description: String) {
        QUALITY_FIRST("质量优先 - 优先获取高分辨率图标"),
        BALANCED("平衡模式 - 根据显示尺寸选择合适分辨率"),
        SPEED_FIRST("速度优先 - 优先获取小尺寸图标以提升加载速度")
    }
    
    /**
     * 当前配置
     */
    var currentPerformanceMode: PerformanceMode = PerformanceMode.BALANCED
    var enableHighResolutionForLargeScreens: Boolean = true
    var maxConcurrentDownloads: Int = 3
    var enableIconCaching: Boolean = true
    var cacheExpiryHours: Int = 24
    
    /**
     * 根据显示上下文获取推荐的图标分辨率
     */
    fun getRecommendedResolutions(context: DisplayContext): List<IconResolution> {
        return when (currentPerformanceMode) {
            PerformanceMode.QUALITY_FIRST -> {
                // 质量优先：从高到低
                listOf(IconResolution.HIGH, IconResolution.MEDIUM, IconResolution.STANDARD, IconResolution.LOW)
            }
            PerformanceMode.SPEED_FIRST -> {
                // 速度优先：从低到高
                context.preferredResolutions.reversed()
            }
            PerformanceMode.BALANCED -> {
                // 平衡模式：使用预定义的优先级
                context.preferredResolutions
            }
        }
    }
    
    /**
     * 获取iTunes API URL的分辨率后缀
     */
    fun getResolutionSuffix(resolution: IconResolution): String {
        return resolution.suffix
    }
    
    /**
     * 判断是否应该下载指定分辨率的图标
     */
    fun shouldDownloadResolution(resolution: IconResolution, context: DisplayContext): Boolean {
        val recommendedResolutions = getRecommendedResolutions(context)
        return recommendedResolutions.contains(resolution)
    }
    
    /**
     * 获取目标处理尺寸
     */
    fun getTargetProcessingSize(context: DisplayContext): Int {
        return when (currentPerformanceMode) {
            PerformanceMode.QUALITY_FIRST -> context.targetSize * 4 // 高质量处理
            PerformanceMode.BALANCED -> context.targetSize * 3      // 标准处理
            PerformanceMode.SPEED_FIRST -> context.targetSize * 2   // 快速处理
        }
    }
    
    /**
     * 获取网络超时配置
     */
    fun getNetworkTimeouts(): Pair<Int, Int> {
        return when (currentPerformanceMode) {
            PerformanceMode.QUALITY_FIRST -> Pair(8000, 8000)  // 连接超时, 读取超时
            PerformanceMode.BALANCED -> Pair(5000, 5000)
            PerformanceMode.SPEED_FIRST -> Pair(3000, 3000)
        }
    }
    
    /**
     * 设置性能模式
     */
    fun setPerformanceMode(mode: PerformanceMode) {
        currentPerformanceMode = mode
    }
    
    /**
     * 获取当前配置摘要
     */
    fun getConfigSummary(): String {
        return """
            图标分辨率配置摘要:
            - 性能模式: ${currentPerformanceMode.description}
            - 大屏高分辨率: ${if (enableHighResolutionForLargeScreens) "启用" else "禁用"}
            - 最大并发下载: $maxConcurrentDownloads
            - 图标缓存: ${if (enableIconCaching) "启用" else "禁用"}
            - 缓存过期时间: ${cacheExpiryHours}小时
            
            各场景推荐分辨率:
            - 应用搜索网格 (48dp): ${getRecommendedResolutions(DisplayContext.APP_SEARCH_GRID).joinToString { it.suffix }}
            - 小组件图标 (32dp): ${getRecommendedResolutions(DisplayContext.WIDGET_ICON).joinToString { it.suffix }}
            - 工具栏图标 (24dp): ${getRecommendedResolutions(DisplayContext.TOOLBAR_ICON).joinToString { it.suffix }}
            - 搜索引擎图标 (40dp): ${getRecommendedResolutions(DisplayContext.SEARCH_ENGINE_ICON).joinToString { it.suffix }}
        """.trimIndent()
    }
    
    /**
     * 重置为默认配置
     */
    fun resetToDefaults() {
        currentPerformanceMode = PerformanceMode.BALANCED
        enableHighResolutionForLargeScreens = true
        maxConcurrentDownloads = 3
        enableIconCaching = true
        cacheExpiryHours = 24
    }
    
    /**
     * 根据设备性能自动调整配置
     */
    fun autoConfigureForDevice(availableMemoryMB: Long, isHighEndDevice: Boolean) {
        when {
            availableMemoryMB > 4096 && isHighEndDevice -> {
                // 高端设备：质量优先
                setPerformanceMode(PerformanceMode.QUALITY_FIRST)
                maxConcurrentDownloads = 5
                enableHighResolutionForLargeScreens = true
            }
            availableMemoryMB > 2048 -> {
                // 中端设备：平衡模式
                setPerformanceMode(PerformanceMode.BALANCED)
                maxConcurrentDownloads = 3
                enableHighResolutionForLargeScreens = true
            }
            else -> {
                // 低端设备：速度优先
                setPerformanceMode(PerformanceMode.SPEED_FIRST)
                maxConcurrentDownloads = 2
                enableHighResolutionForLargeScreens = false
            }
        }
    }
}
