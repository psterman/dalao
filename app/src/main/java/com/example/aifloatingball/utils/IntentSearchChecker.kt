package com.example.aifloatingball.utils

import android.content.Context
import android.util.Log
import com.example.aifloatingball.model.AppSearchConfig
import com.example.aifloatingball.model.AppSearchSettings

/**
 * Intent搜索支持检查器
 * 用于检查哪些应用支持Intent搜索功能
 */
object IntentSearchChecker {
    private const val TAG = "IntentSearchChecker"
    
    /**
     * 检查应用是否支持Intent搜索
     * @param config 应用配置
     * @return true表示支持Intent搜索，false表示不支持
     */
    fun supportsIntentSearch(config: AppSearchConfig): Boolean {
        val searchUrl = config.searchUrl.trim()
        
        return when {
            // searchUrl为空，不支持
            searchUrl.isEmpty() -> false
            
            // 是HTTP/HTTPS链接，不支持真正的Intent搜索（只能打开网页）
            searchUrl.startsWith("http://") || searchUrl.startsWith("https://") -> false
            
            // 包含 {q} 占位符，支持Intent搜索
            searchUrl.contains("{q}") -> true
            
            // 只是启动应用的scheme（如weixin://），不支持搜索
            searchUrl.endsWith("://") || !searchUrl.contains("?") -> false
            
            // 其他情况，可能支持（需要进一步验证）
            else -> true
        }
    }
    
    /**
     * 检查所有应用并生成报告
     */
    fun checkAllApps(context: Context) {
        val appSettings = AppSearchSettings.getInstance(context)
        val allConfigs = appSettings.getAppConfigs()
        
        Log.d(TAG, "=========================================")
        Log.d(TAG, "开始检查应用Intent搜索支持情况")
        Log.d(TAG, "=========================================")
        
        val supportedApps = mutableListOf<AppSearchConfig>()
        val unsupportedApps = mutableListOf<AppSearchConfig>()
        
        allConfigs.forEach { config ->
            val supports = supportsIntentSearch(config)
            if (supports) {
                supportedApps.add(config)
                Log.d(TAG, "✅ ${config.appName} (${config.category.displayName}) - 支持Intent搜索")
                Log.d(TAG, "   URL: ${config.searchUrl}")
            } else {
                unsupportedApps.add(config)
                val reason = when {
                    config.searchUrl.isEmpty() -> "searchUrl为空"
                    config.searchUrl.startsWith("http://") || config.searchUrl.startsWith("https://") -> "使用HTTP/HTTPS链接"
                    config.searchUrl.endsWith("://") || !config.searchUrl.contains("?") -> "仅启动应用，不支持搜索"
                    else -> "未知原因"
                }
                Log.d(TAG, "❌ ${config.appName} (${config.category.displayName}) - 不支持Intent搜索: $reason")
                Log.d(TAG, "   URL: ${config.searchUrl}")
            }
        }
        
        Log.d(TAG, "=========================================")
        Log.d(TAG, "检查结果汇总:")
        Log.d(TAG, "=========================================")
        Log.d(TAG, "✅ 支持Intent搜索的应用: ${supportedApps.size} 个")
        Log.d(TAG, "❌ 不支持Intent搜索的应用: ${unsupportedApps.size} 个")
        Log.d(TAG, "总计: ${allConfigs.size} 个应用")
        Log.d(TAG, "=========================================")
        
        // 按分类统计
        val categoryStats = mutableMapOf<String, Pair<Int, Int>>()
        allConfigs.forEach { config ->
            val categoryName = config.category.displayName
            val stats = categoryStats.getOrDefault(categoryName, Pair(0, 0))
            if (supportsIntentSearch(config)) {
                categoryStats[categoryName] = Pair(stats.first + 1, stats.second)
            } else {
                categoryStats[categoryName] = Pair(stats.first, stats.second + 1)
            }
        }
        
        Log.d(TAG, "按分类统计:")
        categoryStats.forEach { (category, stats) ->
            Log.d(TAG, "  $category: ✅ ${stats.first} 个支持, ❌ ${stats.second} 个不支持")
        }
        
        Log.d(TAG, "=========================================")
        Log.d(TAG, "支持Intent搜索的应用列表:")
        Log.d(TAG, "=========================================")
        supportedApps.sortedBy { it.category.displayName }.forEach { config ->
            Log.d(TAG, "  • ${config.appName} (${config.category.displayName})")
        }
        
        Log.d(TAG, "=========================================")
        Log.d(TAG, "不支持Intent搜索的应用列表:")
        Log.d(TAG, "=========================================")
        unsupportedApps.sortedBy { it.category.displayName }.forEach { config ->
            val reason = when {
                config.searchUrl.isEmpty() -> "searchUrl为空"
                config.searchUrl.startsWith("http://") || config.searchUrl.startsWith("https://") -> "HTTP/HTTPS链接"
                config.searchUrl.endsWith("://") || !config.searchUrl.contains("?") -> "仅启动应用"
                else -> "未知"
            }
            Log.d(TAG, "  • ${config.appName} (${config.category.displayName}) - $reason")
        }
        Log.d(TAG, "=========================================")
    }
    
    /**
     * 获取支持Intent搜索的应用列表
     */
    fun getSupportedApps(context: Context): List<AppSearchConfig> {
        val appSettings = AppSearchSettings.getInstance(context)
        return appSettings.getAppConfigs()
            .filter { supportsIntentSearch(it) }
            .sortedBy { it.category.displayName }
    }
    
    /**
     * 获取不支持Intent搜索的应用列表
     */
    fun getUnsupportedApps(context: Context): List<AppSearchConfig> {
        val appSettings = AppSearchSettings.getInstance(context)
        return appSettings.getAppConfigs()
            .filter { !supportsIntentSearch(it) }
            .sortedBy { it.category.displayName }
    }
}








