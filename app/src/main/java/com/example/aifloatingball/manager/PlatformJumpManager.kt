package com.example.aifloatingball.manager

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.example.aifloatingball.manager.PlatformIconCustomizationManager

/**
 * 平台跳转管理器
 * 负责处理用户点击平台图标后的跳转逻辑
 */
class PlatformJumpManager(private val context: Context) {
    
    companion object {
        private const val TAG = "PlatformJumpManager"
        
        // 平台包名和URL Scheme
        private val PLATFORM_CONFIGS = mapOf(
            "抖音" to PlatformConfig(
                packageName = "com.ss.android.ugc.aweme",
                urlScheme = "snssdk1128://",
                searchUrl = "https://www.douyin.com/search/%s",
                iconRes = "ic_douyin"
            ),
            "小红书" to PlatformConfig(
                packageName = "com.xingin.xhs",
                urlScheme = "xhsdiscover://",
                searchUrl = "https://www.xiaohongshu.com/search_result?keyword=%s",
                iconRes = "ic_xiaohongshu"
            ),
            "YouTube" to PlatformConfig(
                packageName = "com.google.android.youtube",
                urlScheme = "youtube://",
                searchUrl = "https://www.youtube.com/results?search_query=%s",
                iconRes = "ic_youtube"
            ),
            "哔哩哔哩" to PlatformConfig(
                packageName = "tv.danmaku.bili",
                urlScheme = "bilibili://",
                searchUrl = "https://search.bilibili.com/all?keyword=%s",
                iconRes = "ic_bilibili"
            ),
            "快手" to PlatformConfig(
                packageName = "com.smile.gifmaker",
                urlScheme = "kwai://",
                searchUrl = "https://www.kuaishou.com/search/video?searchKey=%s",
                iconRes = "ic_kuaishou"
            ),
            "微博" to PlatformConfig(
                packageName = "com.sina.weibo",
                urlScheme = "sinaweibo://",
                searchUrl = "https://s.weibo.com/weibo/%s",
                iconRes = "ic_weibo"
            ),
            "豆瓣" to PlatformConfig(
                packageName = "com.douban.frodo",
                urlScheme = "douban://",
                searchUrl = "https://www.douban.com/search?q=%s",
                iconRes = "ic_douban"
            )
        )
    }
    
    private val customizationManager = PlatformIconCustomizationManager.getInstance(context)
    
    /**
     * 平台配置数据类
     */
    data class PlatformConfig(
        val packageName: String,
        val urlScheme: String,
        val searchUrl: String,
        val iconRes: String
    )
    
    /**
     * 平台信息数据类
     */
    data class PlatformInfo(
        val name: String,
        val config: PlatformConfig,
        val isInstalled: Boolean
    )
    
    /**
     * 获取所有平台信息
     */
    fun getAllPlatforms(): List<PlatformInfo> {
        return PLATFORM_CONFIGS.map { (name, config) ->
            PlatformInfo(
                name = name,
                config = config,
                isInstalled = isAppInstalled(config.packageName)
            )
        }
    }
    
    /**
     * 检查应用是否已安装
     */
    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
    
    /**
     * 跳转到指定平台搜索
     */
    fun jumpToPlatform(platformName: String, query: String) {
        val config = PLATFORM_CONFIGS[platformName]
        if (config == null) {
            Log.e(TAG, "未知平台: $platformName")
            Toast.makeText(context, "不支持的平台", Toast.LENGTH_SHORT).show()
            return
        }
        
        try {
            if (isAppInstalled(config.packageName)) {
                // 尝试使用URL Scheme跳转到应用
                jumpToApp(config, query)
            } else {
                // 应用未安装，使用Web搜索
                jumpToWebSearch(config, query)
            }
        } catch (e: Exception) {
            Log.e(TAG, "跳转失败", e)
            // 跳转失败，使用Web搜索作为备选方案
            jumpToWebSearch(config, query)
        }
    }
        
    /**
     * 跳转到应用内搜索
     */
    private fun jumpToApp(config: PlatformConfig, query: String) {
        try {
            // 构建搜索URL
            val searchUrl = when (config.packageName) {
                "com.ss.android.ugc.aweme" -> "${config.urlScheme}search?keyword=${Uri.encode(query)}"
                "com.xingin.xhs" -> "${config.urlScheme}search?keyword=${Uri.encode(query)}"
                "com.google.android.youtube" -> "${config.urlScheme}results?search_query=${Uri.encode(query)}"
                "tv.danmaku.bili" -> "${config.urlScheme}search?keyword=${Uri.encode(query)}"
                "com.smile.gifmaker" -> "${config.urlScheme}search?keyword=${Uri.encode(query)}"
                "com.sina.weibo" -> "${config.urlScheme}search?keyword=${Uri.encode(query)}"
                "com.douban.frodo" -> "${config.urlScheme}search?keyword=${Uri.encode(query)}"
                else -> "${config.urlScheme}search?keyword=${Uri.encode(query)}"
            }
            
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            
            Log.d(TAG, "成功跳转到${config.packageName}搜索: $query")
            Toast.makeText(context, "正在跳转到${getPlatformDisplayName(config.packageName)}", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e(TAG, "应用内跳转失败，尝试Web搜索", e)
            jumpToWebSearch(config, query)
        }
    }
    
    /**
     * 跳转到Web搜索
     */
    private fun jumpToWebSearch(config: PlatformConfig, query: String) {
        try {
            val searchUrl = config.searchUrl.format(Uri.encode(query))
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            
            Log.d(TAG, "成功跳转到Web搜索: $searchUrl")
            Toast.makeText(context, "正在跳转到${getPlatformDisplayName(config.packageName)}网页版", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e(TAG, "Web搜索跳转失败", e)
            Toast.makeText(context, "跳转失败，请手动搜索", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 获取平台显示名称
     */
    private fun getPlatformDisplayName(packageName: String): String {
        return PLATFORM_CONFIGS.entries.find { it.value.packageName == packageName }?.key ?: "未知平台"
    }
    
    /**
     * 根据用户问题智能推荐相关平台
     * 现在默认显示所有平台，确保八个图标完整显示
     * 支持用户定制显示哪些平台
     */
    fun getRelevantPlatforms(query: String): List<PlatformInfo> {
        val allPlatforms = getAllPlatforms()
        
        // 根据关键词匹配推荐平台，但确保显示所有平台
        val lowerQuery = query.lowercase()
        
        // 根据问题类型调整平台优先级，但始终显示所有平台
        val prioritizedPlatforms = when {
            // 视频相关内容 - 视频平台优先
            lowerQuery.contains("视频") || lowerQuery.contains("vlog") || lowerQuery.contains("短视频") -> {
                listOf("抖音", "快手", "哔哩哔哩", "YouTube", "小红书", "微博", "豆瓣")
            }
            // 美妆、生活相关内容 - 生活平台优先
            lowerQuery.contains("美妆") || lowerQuery.contains("护肤") || lowerQuery.contains("穿搭") || 
            lowerQuery.contains("生活") || lowerQuery.contains("美食") -> {
                listOf("小红书", "抖音", "微博", "豆瓣", "哔哩哔哩", "YouTube", "快手")
            }
            // 科技、学习相关内容 - 学习平台优先
            lowerQuery.contains("科技") || lowerQuery.contains("编程") || lowerQuery.contains("学习") || 
            lowerQuery.contains("教程") -> {
                listOf("哔哩哔哩", "YouTube", "微博", "豆瓣", "抖音", "小红书", "快手")
            }
            // 娱乐、音乐相关内容 - 娱乐平台优先
            lowerQuery.contains("音乐") || lowerQuery.contains("娱乐") || lowerQuery.contains("明星") -> {
                listOf("抖音", "微博", "快手", "哔哩哔哩", "YouTube", "小红书", "豆瓣")
            }
            // 书籍、电影相关内容 - 文化平台优先
            lowerQuery.contains("书") || lowerQuery.contains("电影") || lowerQuery.contains("影评") -> {
                listOf("豆瓣", "微博", "小红书", "哔哩哔哩", "YouTube", "抖音", "快手")
            }
            // 默认显示所有平台
            else -> {
                listOf("抖音", "小红书", "哔哩哔哩", "YouTube", "微博", "豆瓣", "快手")
            }
        }
        
        // 按照优先级排序，确保所有平台都显示
        val sortedPlatforms = mutableListOf<PlatformInfo>()
        
        // 先添加优先平台
        prioritizedPlatforms.forEach { platformName ->
            allPlatforms.find { it.name == platformName }?.let { platform ->
                sortedPlatforms.add(platform)
            }
        }
        
        // 添加其他平台（如果有遗漏）
        allPlatforms.forEach { platform ->
            if (!sortedPlatforms.any { it.name == platform.name }) {
                sortedPlatforms.add(platform)
            }
        }
        
        // 应用用户定制设置
        return customizationManager.filterEnabledPlatforms(sortedPlatforms)
    }
    
    /**
     * 获取定制管理器
     */
    fun getCustomizationManager(): PlatformIconCustomizationManager {
        return customizationManager
    }
}
