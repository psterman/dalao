package com.example.aifloatingball.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.example.aifloatingball.model.AppSearchConfig
import com.example.aifloatingball.model.AppSearchSettings

/**
 * 应用Intent搜索语句验证器
 * 用于检查和验证应用的Intent搜索URL scheme是否正确可用
 */
object AppIntentSearchValidator {
    private const val TAG = "AppIntentSearchValidator"
    
    /**
     * 验证应用的Intent搜索语句是否可用
     */
    fun validateAppIntentSearch(context: Context, appConfig: AppSearchConfig): Boolean {
        val searchUrl = appConfig.searchUrl.trim()
        
        return when {
            // searchUrl为空，不支持
            searchUrl.isEmpty() -> {
                Log.w(TAG, "❌ ${appConfig.appName}: searchUrl为空")
                false
            }
            
            // 包含 {q} 占位符，检查URL scheme是否可用
            searchUrl.contains("{q}") -> {
                val testUrl = searchUrl.replace("{q}", "test")
                val isValid = checkUrlScheme(context, testUrl, appConfig.packageName)
                if (isValid) {
                    Log.d(TAG, "✅ ${appConfig.appName}: URL scheme可用 - $testUrl")
                } else {
                    Log.w(TAG, "❌ ${appConfig.appName}: URL scheme不可用 - $testUrl")
                }
                isValid
            }
            
            // 是HTTP/HTTPS链接，不支持真正的搜索
            searchUrl.startsWith("http://") || searchUrl.startsWith("https://") -> {
                Log.w(TAG, "⚠️ ${appConfig.appName}: 使用HTTP/HTTPS链接，不支持直接搜索 - $searchUrl")
                false
            }
            
            // 其他情况
            else -> {
                val isValid = checkUrlScheme(context, searchUrl, appConfig.packageName)
                if (isValid) {
                    Log.d(TAG, "✅ ${appConfig.appName}: URL scheme可用 - $searchUrl")
                } else {
                    Log.w(TAG, "❌ ${appConfig.appName}: URL scheme不可用 - $searchUrl")
                }
                isValid
            }
        }
    }
    
    /**
     * 检查URL scheme是否可用
     */
    private fun checkUrlScheme(context: Context, url: String, packageName: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                setPackage(packageName)
            }
            val resolveInfo = context.packageManager.resolveActivity(intent, 0)
            resolveInfo != null
        } catch (e: Exception) {
            Log.e(TAG, "检查URL scheme失败: $url", e)
            false
        }
    }
    
    /**
     * 验证所有指定应用的Intent搜索语句
     */
    fun validateAllApps(context: Context) {
        val appSettings = AppSearchSettings.getInstance(context)
        val allConfigs = appSettings.getAppConfigs()
        
        // 需要验证的应用列表
        val targetApps = listOf(
            "优酷", "youku",
            "爱奇艺", "iqiyi",
            "腾讯视频", "qqlive",
            "哔哩哔哩", "bilibili",
            "快手", "kuaishou",
            "抖音", "douyin",
            "拼多多", "pdd",
            "京东", "jd",
            "闲鱼", "xianyu",
            "天猫", "tmall",
            "淘宝", "taobao",
            "知乎", "zhihu",
            "微博", "weibo",
            "豆瓣", "douban",
            "小红书", "xiaohongshu",
            "美团", "meituan",
            "饿了么", "eleme",
            "大众点评", "dianping",
            "UC浏览器", "uc_browser",
            "QQ浏览器", "qq_browser",
            "夸克", "quark",
            "QQ音乐", "qqmusic",
            "网易云音乐", "netease_music",
            "高德地图", "gaode_map",
            "腾讯地图", "tencent_map",
            "百度地图", "baidu_map"
        )
        
        Log.d(TAG, "=========================================")
        Log.d(TAG, "开始验证应用Intent搜索语句")
        Log.d(TAG, "=========================================")
        
        val results = mutableListOf<Pair<String, Boolean>>()
        
        allConfigs.forEach { config ->
            val appName = config.appName.lowercase()
            val appId = config.appId.lowercase()
            
            if (targetApps.any { it.lowercase() == appName || it.lowercase() == appId }) {
                val isValid = validateAppIntentSearch(context, config)
                results.add(Pair(config.appName, isValid))
            }
        }
        
        Log.d(TAG, "=========================================")
        Log.d(TAG, "验证结果汇总:")
        Log.d(TAG, "=========================================")
        results.forEach { (name, isValid) ->
            val status = if (isValid) "✅ 可用" else "❌ 不可用"
            Log.d(TAG, "$name: $status")
        }
        
        val successCount = results.count { it.second }
        val totalCount = results.size
        Log.d(TAG, "=========================================")
        Log.d(TAG, "总计: $successCount/$totalCount 可用")
        Log.d(TAG, "=========================================")
    }
}

