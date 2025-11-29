package com.example.aifloatingball.manager

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.example.aifloatingball.manager.PlatformIconCustomizationManager
import com.example.aifloatingball.service.AIAppOverlayService

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
     * 支持预设平台和动态应用，提供web搜索和favicon loader候补方案
     * 新增：所有平台跳转后都激活悬浮窗服务
     */
    fun jumpToPlatform(platformName: String, query: String) {
        val config = PLATFORM_CONFIGS[platformName]
        
        if (config != null) {
            // 预设平台，使用原有逻辑
            try {
                if (isAppInstalled(config.packageName)) {
                    // 尝试使用URL Scheme跳转到应用
                    jumpToApp(config, query)
                } else {
                    // 应用未安装，使用Web搜索
                    jumpToWebSearch(config, query)
                }
                
                // 预设平台跳转后也激活悬浮窗服务
                activateAIAppOverlayService(config.packageName, query, platformName)
                
            } catch (e: Exception) {
                Log.e(TAG, "预设平台跳转失败", e)
                // 跳转失败，使用Web搜索作为备选方案
                jumpToWebSearch(config, query)
                
                // 即使跳转失败也尝试激活悬浮窗服务
                activateAIAppOverlayService(config.packageName, query, platformName)
            }
        } else {
            // 动态应用，使用通用跳转逻辑
            jumpToDynamicApp(platformName, query)
        }
    }
    
    /**
     * 跳转到动态应用
     * 提供web搜索和favicon loader候补方案
     */
    private fun jumpToDynamicApp(appName: String, query: String) {
        try {
            // 1. 检查是否是AI应用
            if (isAIApp(appName)) {
                jumpToAIApp(appName, query)
                return
            }
            
            // 2. 尝试通过包名跳转到应用
            val packageName = getPackageNameByAppName(appName)
            if (packageName != null && isAppInstalled(packageName)) {
                jumpToDynamicAppByPackage(packageName, query)
                return
            }
            
            // 3. 应用未安装或找不到包名，使用Web搜索
            jumpToWebSearchForDynamicApp(appName, query)
            
        } catch (e: Exception) {
            Log.e(TAG, "动态应用跳转失败: $appName", e)
            // 跳转失败，使用Web搜索作为备选方案
            jumpToWebSearchForDynamicApp(appName, query)
        }
    }
    
    /**
     * 检查是否是AI应用
     */
    private fun isAIApp(appName: String): Boolean {
        val aiAppNames = listOf(
            "DeepSeek", "豆包", "ChatGPT", "Kimi", "腾讯元宝", "讯飞星火", 
            "智谱清言", "通义千问", "文小言", "Grok", "Perplexity", "Manus",
            "秘塔AI搜索", "Poe", "IMA", "纳米AI", "Gemini", "Copilot"
        )
        return aiAppNames.any { aiName -> appName.contains(aiName) }
    }
    
    /**
     * 跳转到AI应用
     * 使用软件tab的AI跳转方法
     */
    private fun jumpToAIApp(appName: String, query: String) {
        try {
            Log.d(TAG, "跳转到AI应用: $appName, 查询: $query")
            
            // 获取AI应用的包名列表
            val possiblePackages = getAIPackages(appName)
            if (possiblePackages.isEmpty()) {
                Log.e(TAG, "未找到AI应用包名: $appName")
                jumpToWebSearchForDynamicApp(appName, query)
                return
            }
            
            // 检查是否有已安装的AI应用
            val installedPackage = getInstalledAIPackageName(possiblePackages)
            if (installedPackage != null) {
                // 使用软件tab的AI跳转方法
                launchAIAppWithIntent(installedPackage, query, appName)
            } else {
                // 没有安装AI应用，使用Web搜索
                jumpToWebSearchForDynamicApp(appName, query)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "AI应用跳转失败: $appName", e)
            jumpToWebSearchForDynamicApp(appName, query)
        }
    }
    
    /**
     * 获取AI应用的包名列表
     */
    private fun getAIPackages(appName: String): List<String> {
        return when {
            appName.contains("DeepSeek") -> listOf("com.deepseek.chat")
            appName.contains("豆包") -> listOf("com.larus.nova")
            appName.contains("ChatGPT") -> listOf("com.openai.chatgpt")
            appName.contains("Kimi") -> listOf("com.moonshot.kimichat")
            appName.contains("腾讯元宝") -> listOf("com.tencent.hunyuan.app.chat")
            appName.contains("讯飞星火") -> listOf("com.iflytek.spark")
            appName.contains("智谱清言") -> listOf("com.zhipuai.qingyan")
            appName.contains("通义千问") -> listOf("com.aliyun.tongyi")
            appName.contains("文小言") -> listOf("com.baidu.newapp")
            appName.contains("Grok") -> listOf("ai.x.grok")
            appName.contains("Perplexity") -> listOf("ai.perplexity.app.android")
            appName.contains("Manus") -> listOf("com.manus.im.app")
            appName.contains("秘塔AI搜索") -> listOf("com.metaso")
            appName.contains("Poe") -> listOf("com.poe.android")
            appName.contains("IMA") -> listOf("com.tencent.ima")
            appName.contains("纳米AI") -> listOf("com.qihoo.namiso")
            appName.contains("Gemini") -> listOf("com.google.android.apps.gemini")
            appName.contains("Copilot") -> listOf("com.microsoft.copilot")
            else -> emptyList()
        }
    }
    
    /**
     * 检查AI应用是否已安装
     */
    private fun getInstalledAIPackageName(possiblePackages: List<String>): String? {
        for (packageName in possiblePackages) {
            if (isAppInstalled(packageName)) {
                Log.d(TAG, "找到已安装的AI应用: $packageName")
                return packageName
            }
        }
        return null
    }
    
    /**
     * 启动AI应用并使用Intent发送文本
     * 完全参考软件tab的AI跳转方法
     * 新增：激活AI应用悬浮窗服务模式
     */
    private fun launchAIAppWithIntent(packageName: String, query: String, appName: String) {
        try {
            Log.d(TAG, "启动AI应用: $appName, 包名: $packageName, 查询: $query")
            
            // 对于豆包应用，尝试使用Intent直接发送文本
            if (appName.contains("豆包") && packageName == "com.larus.nova") {
                if (tryIntentSendForDoubao(packageName, query, appName)) {
                    // 激活AI应用悬浮窗服务
                    activateAIAppOverlayService(packageName, query, appName)
                    return
                }
            }
            
            // 参考软件tab的AI跳转方法：启动应用并使用自动化粘贴
            launchAppWithAutoPaste(packageName, query, appName)
            
            // 激活AI应用悬浮窗服务
            activateAIAppOverlayService(packageName, query, appName)
            
        } catch (e: Exception) {
            Log.e(TAG, "AI应用启动失败: $appName", e)
            Toast.makeText(context, "$appName 启动失败", Toast.LENGTH_SHORT).show()
            sendQuestionViaClipboard(packageName, query, appName)
            
            // 即使启动失败也尝试激活悬浮窗服务
            activateAIAppOverlayService(packageName, query, appName)
        }
    }
    
    /**
     * 尝试使用Intent直接发送文本到豆包应用
     */
    private fun tryIntentSendForDoubao(packageName: String, query: String, appName: String): Boolean {
        try {
            Log.d(TAG, "尝试Intent直接发送到豆包: $query")
            
            // 方案1：尝试使用ACTION_SEND直接发送文本
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, query)
                setPackage(packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (sendIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(sendIntent)
                Toast.makeText(context, "正在向豆包发送问题...", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "豆包Intent发送成功")
                
                // 延迟显示悬浮窗
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    showAIAppOverlay(packageName, query, appName)
                }, 2000)
                
                return true
            }
            
            // 方案2：尝试使用豆包特定的URL Scheme
            val doubaoUrl = "doubao://chat?text=${Uri.encode(query)}"
            val urlIntent = Intent(Intent.ACTION_VIEW, Uri.parse(doubaoUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            if (urlIntent.resolveActivity(context.packageManager) != null) {
                context.startActivity(urlIntent)
                Toast.makeText(context, "正在向豆包发送问题...", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "豆包URL Scheme发送成功")
                
                // 延迟显示悬浮窗
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    showAIAppOverlay(packageName, query, appName)
                }, 2000)
                
                return true
            }
            
            Log.d(TAG, "豆包Intent发送失败，回退到剪贴板方案")
            return false
            
        } catch (e: Exception) {
            Log.e(TAG, "豆包Intent发送失败", e)
            return false
        }
    }
    
    /**
     * 启动应用并使用自动化粘贴
     * 完全参考软件tab的实现
     */
    private fun launchAppWithAutoPaste(packageName: String, query: String, appName: String) {
        try {
            Log.d(TAG, "启动应用并使用自动化粘贴: $appName, 问题: $query")
            
            // 将问题复制到剪贴板
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("AI问题", query)
            clipboard.setPrimaryClip(clip)
            
            // 启动AI应用
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                Toast.makeText(context, "正在启动${appName}...", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "${appName}启动成功")
                
                // 延迟显示悬浮窗
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    showAIAppOverlay(packageName, query, appName)
                }, 2000) // 等待2秒让应用完全加载
                
            } else {
                Toast.makeText(context, "无法启动${appName}，请检查应用是否已安装", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "启动应用并自动粘贴失败: ${appName}", e)
            // 回退到剪贴板方案
            sendQuestionViaClipboard(packageName, query, appName)
        }
    }
    
    /**
     * 显示AI应用悬浮窗
     * 完全参考软件tab的实现
     */
    private fun showAIAppOverlay(packageName: String, query: String, appName: String) {
        try {
            Log.d(TAG, "🎯 显示AI应用悬浮窗: $appName")
            
            val intent = Intent(context, com.example.aifloatingball.service.AIAppOverlayService::class.java).apply {
                action = com.example.aifloatingball.service.AIAppOverlayService.ACTION_SHOW_OVERLAY
                putExtra(com.example.aifloatingball.service.AIAppOverlayService.EXTRA_APP_NAME, appName)
                putExtra(com.example.aifloatingball.service.AIAppOverlayService.EXTRA_QUERY, query)
                putExtra(com.example.aifloatingball.service.AIAppOverlayService.EXTRA_PACKAGE_NAME, packageName)
            }
            context.startService(intent)
            
        } catch (e: Exception) {
            Log.e(TAG, "显示AI应用悬浮窗失败: $appName", e)
            // 回退到剪贴板方案
            sendQuestionViaClipboard(packageName, query, appName)
        }
    }
    
    
    /**
     * 使用剪贴板发送问题
     */
    private fun sendQuestionViaClipboard(packageName: String, query: String, appName: String) {
        try {
            // 将问题复制到剪贴板
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("AI问题", query)
            clipboard.setPrimaryClip(clip)
            
            // 启动AI应用
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                Toast.makeText(context, "已复制问题到剪贴板，请在${appName}中粘贴", Toast.LENGTH_LONG).show()
                Log.d(TAG, "剪贴板方案成功: $appName")
            } else {
                Toast.makeText(context, "$appName 未安装", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "剪贴板方案失败: $appName", e)
            Toast.makeText(context, "$appName 启动失败", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 通过包名跳转到动态应用
     */
    private fun jumpToDynamicAppByPackage(packageName: String, query: String) {
        try {
            val cleanQuery = cleanQueryForSearch(query)
            var intentHandled = false
            
            // 方案1：尝试使用通用的搜索URL scheme
            try {
                val searchUrl = "$packageName://search?keyword=${Uri.encode(cleanQuery)}"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    Log.d(TAG, "通过URL scheme跳转到动态应用搜索: $packageName, query=$cleanQuery")
                    Toast.makeText(context, "正在跳转到${getAppDisplayName(packageName)}搜索", Toast.LENGTH_SHORT).show()
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "动态应用URL scheme跳转失败: $packageName, 尝试ACTION_SEARCH", e)
            }
            
            // 方案2：尝试使用ACTION_SEARCH intent
            if (!intentHandled) {
                try {
                    val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
                        `package` = packageName
                        putExtra(android.app.SearchManager.QUERY, cleanQuery)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    
                    if (searchIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(searchIntent)
                        Log.d(TAG, "通过ACTION_SEARCH跳转到动态应用搜索: $packageName, query=$cleanQuery")
                        Toast.makeText(context, "正在跳转到${getAppDisplayName(packageName)}搜索", Toast.LENGTH_SHORT).show()
                        return
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "ACTION_SEARCH不支持: $packageName, 尝试ACTION_SEND", e)
                }
            }
            
            // 方案3：尝试使用ACTION_SEND intent（发送文本）
            if (!intentHandled) {
                try {
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, cleanQuery)
                        setPackage(packageName)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    
                    if (sendIntent.resolveActivity(context.packageManager) != null) {
                        context.startActivity(sendIntent)
                        Log.d(TAG, "通过ACTION_SEND跳转到动态应用: $packageName, query=$cleanQuery")
                        Toast.makeText(context, "正在打开${getAppDisplayName(packageName)}并发送文本", Toast.LENGTH_SHORT).show()
                        return
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "ACTION_SEND不支持: $packageName, 降级到普通启动", e)
                }
            }
            
            // 所有搜索方案都失败，降级到普通启动
            Log.w(TAG, "所有搜索intent方案都失败，降级到普通启动: $packageName")
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                Log.d(TAG, "成功启动动态应用: $packageName")
                Toast.makeText(context, "已启动${getAppDisplayName(packageName)}，请手动搜索", Toast.LENGTH_SHORT).show()
            } else {
                throw Exception("无法获取启动Intent")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "启动动态应用失败: $packageName", e)
            // 启动失败，使用Web搜索
            jumpToWebSearchForDynamicApp(getAppDisplayName(packageName), query)
        }
    }
    
    /**
     * 为动态应用使用Web搜索
     */
    private fun jumpToWebSearchForDynamicApp(appName: String, query: String) {
        try {
            val cleanQuery = cleanQueryForSearch(query)
            
            // 根据应用名称选择最合适的搜索引擎
            val searchUrl = when {
                appName.contains("微信") -> "https://weixin.sogou.com/weixin?type=2&query=${Uri.encode(cleanQuery)}"
                appName.contains("淘宝") -> "https://s.taobao.com/search?q=${Uri.encode(cleanQuery)}"
                appName.contains("京东") -> "https://search.jd.com/Search?keyword=${Uri.encode(cleanQuery)}"
                appName.contains("知乎") -> "https://www.zhihu.com/search?q=${Uri.encode(cleanQuery)}"
                appName.contains("拼多多") -> "https://mobile.yangkeduo.com/search_result.html?search_key=${Uri.encode(cleanQuery)}"
                appName.contains("天猫") -> "https://list.tmall.com/search_product.htm?q=${Uri.encode(cleanQuery)}"
                appName.contains("QQ") -> "https://www.sogou.com/web?query=${Uri.encode(cleanQuery)}"
                else -> "https://www.google.com/search?q=${Uri.encode(cleanQuery)}+${Uri.encode(appName)}"
            }
            
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            
            Log.d(TAG, "成功跳转到${appName} Web搜索结果页面: $cleanQuery")
            Toast.makeText(context, "已通过网页搜索跳转到${appName}相关内容", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e(TAG, "Web搜索跳转失败: $appName", e)
            Toast.makeText(context, "跳转失败，请手动搜索", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 根据应用名称获取包名
     */
    private fun getPackageNameByAppName(appName: String): String? {
        // 预设平台包名映射
        val presetPlatforms = mapOf(
            "抖音" to "com.ss.android.ugc.aweme",
            "小红书" to "com.xingin.xhs",
            "YouTube" to "com.google.android.youtube",
            "哔哩哔哩" to "tv.danmaku.bili",
            "快手" to "com.smile.gifmaker",
            "微博" to "com.sina.weibo",
            "豆瓣" to "com.douban.frodo"
        )
        
        // 先检查预设平台
        presetPlatforms[appName]?.let { packageName ->
            return packageName
        }
        
        // 如果不是预设平台，尝试通过包管理器查找
        try {
            val packageManager = context.packageManager
            val installedPackages = packageManager.getInstalledPackages(0)
            
            for (packageInfo in installedPackages) {
                val label = packageManager.getApplicationLabel(packageInfo.applicationInfo).toString()
                if (label == appName) {
                    return packageInfo.packageName
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to find package name for $appName: ${e.message}")
        }
        
        return null
    }
    
    /**
     * 获取应用显示名称
     */
    private fun getAppDisplayName(packageName: String): String {
        return try {
            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageManager.getApplicationLabel(packageInfo.applicationInfo).toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get app display name for $packageName: ${e.message}")
            packageName
        }
    }
        
    /**
     * 跳转到应用内搜索
     * 使用用户原始问题作为关键词，构建更精准的URL scheme
     */
    private fun jumpToApp(config: PlatformConfig, query: String) {
        try {
            // 清理和优化查询关键词
            val cleanQuery = cleanQueryForSearch(query)
            
            // 构建搜索结果页面URL - 使用专用URL scheme
            val encodedQuery = Uri.encode(cleanQuery)
            val searchUrl = getAppSearchUrlScheme(config.packageName, cleanQuery, encodedQuery)
                ?: "${config.urlScheme}search?keyword=$encodedQuery" // 降级到通用格式
            
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(searchUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            try {
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    Log.d(TAG, "成功跳转到${config.packageName}搜索结果页面（使用软件tab一致的URL scheme）: $cleanQuery")
                    Toast.makeText(context, "正在跳转到${getPlatformDisplayName(config.packageName)}搜索结果", Toast.LENGTH_SHORT).show()
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "URL scheme跳转失败: ${config.packageName}, 尝试ACTION_SEARCH", e)
            }
            
            // URL scheme失败，尝试使用ACTION_SEARCH
            try {
                val searchIntent = Intent(Intent.ACTION_SEARCH).apply {
                    `package` = config.packageName
                    putExtra(android.app.SearchManager.QUERY, cleanQuery)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                if (searchIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(searchIntent)
                    Log.d(TAG, "通过ACTION_SEARCH跳转到${config.packageName}搜索: $cleanQuery")
                    Toast.makeText(context, "正在跳转到${getPlatformDisplayName(config.packageName)}搜索结果", Toast.LENGTH_SHORT).show()
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "ACTION_SEARCH跳转失败: ${config.packageName}, 尝试ACTION_SEND", e)
            }
            
            // ACTION_SEARCH失败，尝试使用ACTION_SEND
            try {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, cleanQuery)
                    setPackage(config.packageName)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                if (sendIntent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(sendIntent)
                    Log.d(TAG, "通过ACTION_SEND跳转到${config.packageName}: $cleanQuery")
                    Toast.makeText(context, "正在打开${getPlatformDisplayName(config.packageName)}并发送文本", Toast.LENGTH_SHORT).show()
                    return
                }
            } catch (e: Exception) {
                Log.w(TAG, "ACTION_SEND跳转失败: ${config.packageName}, 尝试Web搜索", e)
            }
            
            // 所有intent方案都失败，尝试Web搜索
            Log.w(TAG, "所有应用内跳转方案都失败，尝试Web搜索")
            jumpToWebSearch(config, query)
            
        } catch (e: Exception) {
            Log.e(TAG, "应用内跳转失败，尝试Web搜索", e)
            jumpToWebSearch(config, query)
        }
    }
    
    /**
     * 获取应用的专用搜索URL scheme
     * 根据包名返回正确的搜索结果页面URL
     */
    private fun getAppSearchUrlScheme(packageName: String, query: String, encodedQuery: String): String? {
        return when (packageName) {
            // 社交媒体类
            "com.sina.weibo" -> "sinaweibo://searchall?q=$encodedQuery" // 微博
            "com.xingin.xhs" -> "xhsdiscover://search/result/?keyword=$encodedQuery" // 小红书
            "com.zhihu.android" -> "zhihu://search?q=$encodedQuery" // 知乎
            "com.douban.frodo" -> "douban:///search?q=$encodedQuery" // 豆瓣
            
            // 视频类
            "com.ss.android.ugc.aweme" -> "snssdk1128://search/tabs?keyword=$encodedQuery" // 抖音
            "tv.danmaku.bili" -> "bilibili://search?keyword=$encodedQuery" // 哔哩哔哩
            "com.smile.gifmaker" -> "kwai://search?keyword=$encodedQuery" // 快手
            "com.youku.phone" -> "youku://search?word=$encodedQuery" // 优酷
            "com.iqiyi.app", "com.iqiyi.hd" -> "iqiyi://search?key=$encodedQuery" // 爱奇艺
            "com.tencent.qqlive" -> "tenvideo://search?query=$encodedQuery" // 腾讯视频
            "com.google.android.youtube" -> "youtube://results?search_query=$encodedQuery" // YouTube
            
            // 购物类
            "com.taobao.taobao" -> "taobao://s.taobao.com/search?q=$encodedQuery" // 淘宝
            "com.tmall.wireless" -> "tmall://page.tm/search?q=$encodedQuery" // 天猫
            "com.jingdong.app.mall" -> "openapp.jdmobile://virtual?params={\"des\":\"productList\",\"keyWord\":\"$encodedQuery\"}" // 京东
            "com.xunmeng.pinduoduo" -> "pinduoduo://com.xunmeng.pinduoduo/search_result.html?search_key=$encodedQuery" // 拼多多
            "com.taobao.fleamarket" -> "fleamarket://x_search_items?keyword=$encodedQuery" // 闲鱼
            
            // 生活服务类
            "com.sankuai.meituan" -> "imeituan://www.meituan.com/search?q=$encodedQuery" // 美团
            "me.ele" -> "eleme://search?keyword=$encodedQuery" // 饿了么
            "com.dianping.v1" -> "dianping://searchshoplist?keyword=$encodedQuery" // 大众点评
            
            // 浏览器类
            "com.UCMobile" -> "ucbrowser://search?keyword=$encodedQuery" // UC浏览器
            "com.tencent.mtt" -> "mttbrowser://search?query=$encodedQuery" // QQ浏览器
            "com.quark.browser" -> "quark://search?query=$encodedQuery" // 夸克
            
            // 音乐类
            "com.tencent.qqmusic" -> "qqmusic://search?key=$encodedQuery" // QQ音乐
            "com.netease.cloudmusic" -> "orpheus://search?keyword=$encodedQuery" // 网易云音乐
            
            // 地图导航类
            "com.autonavi.minimap" -> "androidamap://poi?sourceApplication=aifloatingball&keywords=$encodedQuery" // 高德地图
            "com.tencent.map" -> "qqmap://search?keyword=$encodedQuery" // 腾讯地图
            "com.baidu.BaiduMap" -> "baidumap://map/place/search?query=$encodedQuery" // 百度地图
            
            // 办公软件类
            "cn.wps.moffice_eng" -> "wps://search?keyword=$encodedQuery" // WPS Office
            "cn.wps.moffice" -> "wps://search?keyword=$encodedQuery" // WPS Office
            "cn.wps.xiaomi.launcher" -> "wps://search?keyword=$encodedQuery" // WPS Office
            "com.microsoft.office.onenote" -> "onenote://search?query=$encodedQuery" // Microsoft OneNote
            
            // 搜索引擎与浏览器类
            "com.google.android.googlequicksearchbox" -> "google://search?q=$encodedQuery" // Google搜索
            "com.baidu.searchbox" -> "baiduboxapp://search?keyword=$encodedQuery" // 百度搜索
            "com.sohu.inputmethod.sogou" -> "sogou://search?keyword=$encodedQuery" // 搜狗搜索
            "com.qihoo.browser" -> "qihoobrowser://search?keyword=$encodedQuery" // 360浏览器
            "me.mycake.browser" -> "alook://search?q=$encodedQuery" // Alook浏览器
            
            // AI智能搜索类
            "com.tiangong.search" -> "tiangong://search?query=$encodedQuery" // 天工AI搜索
            "com.metaso" -> "metaso://search?query=$encodedQuery" // 秘塔AI搜索（MetaSo）
            "com.qihoo.aisearch" -> "qihooai://search?keyword=$encodedQuery" // 360AI搜索
            "com.deepseek.chat" -> "deepseek://search?query=$encodedQuery" // DeepSeek
            "ai.perplexity.app.android" -> "perplexity://search?q=$encodedQuery" // Perplexity AI
            "com.mindsearch.app" -> "mindsearch://search?query=$encodedQuery" // MindSearch
            
            // 购物电商类
            "com.achievo.vipshop" -> "vipshop://search?keyword=$encodedQuery" // 唯品会
            "com.dangdang.buy2" -> "dangdang://search?keyword=$encodedQuery" // 当当
            "com.amazon.mShop.android.shopping" -> "amazon://search?query=$encodedQuery" // 亚马逊
            "com.mogujie" -> "mogujie://search?keyword=$encodedQuery" // 蘑菇街
            
            // 图片与设计类
            "com.picsart.studio" -> "picsart://search?query=$encodedQuery" // PicsArt
            "com.canva.editor" -> "canva://search?query=$encodedQuery" // Canva
            "com.vsco.android" -> "vsco://search?query=$encodedQuery" // VSCO
            
            // 学习与教育类
            "cn.xuexi.android" -> "xuexi://search?keyword=$encodedQuery" // 学习强国
            "com.netease.edu.study" -> "icourse163://search?keyword=$encodedQuery" // 中国大学MOOC
            "com.netease.open.iStudy" -> "neteaseopen://search?keyword=$encodedQuery" // 网易公开课
            "com.shanbay.words" -> "shanbay://search?keyword=$encodedQuery" // 扇贝单词
            
            // 健康医疗类
            "com.dxy.pharmacy" -> "dxy://search?keyword=$encodedQuery" // 丁香医生
            "com.gotokeep.keep" -> "keep://search?keyword=$encodedQuery" // Keep
            "com.boohee.one" -> "boohee://search?keyword=$encodedQuery" // 薄荷健康
            
            // 新闻资讯类
            "com.ss.android.article.news" -> "snssdk141://search?keyword=$encodedQuery" // 今日头条
            "com.thepaper.cn" -> "thepaper://search?keyword=$encodedQuery" // 澎湃新闻
            "com.netease.newsreader.activity" -> "netease://search?keyword=$encodedQuery" // 网易新闻
            "com.tencent.news" -> "tencentnews://search?keyword=$encodedQuery" // 腾讯新闻
            
            // 开发工具类
            "com.microsoft.vscode" -> "vscode://search?query=$encodedQuery" // Visual Studio Code
            "com.google.android.studio" -> "androidstudio://search?query=$encodedQuery" // Android Studio
            "com.sublimetext.three" -> "sublime://search?query=$encodedQuery" // Sublime Text
            
            else -> null // 未知应用，返回null使用通用格式
        }
    }
    
    /**
     * 清理查询关键词，优化搜索效果
     */
    private fun cleanQueryForSearch(query: String): String {
        return query.trim()
            .replace(Regex("[\\[\\](){}【】（）]"), "") // 移除括号
            .replace(Regex("[，。！？,。!?]"), " ") // 替换标点为空格
            .replace(Regex("\\s+"), " ") // 合并多个空格
            .trim()
    }
    
    /**
     * 跳转到Web搜索
     * 使用清理后的查询关键词
     */
    private fun jumpToWebSearch(config: PlatformConfig, query: String) {
        try {
            // 清理查询关键词
            val cleanQuery = cleanQueryForSearch(query)
            
            // 构建Web搜索结果页面URL - 使用与软件tab一致的URL scheme
            val webSearchUrl = when (config.packageName) {
                "com.ss.android.ugc.aweme" -> "https://www.douyin.com/search/${Uri.encode(cleanQuery)}"
                "com.xingin.xhs" -> "https://www.xiaohongshu.com/search_result?keyword=${Uri.encode(cleanQuery)}"
                "com.google.android.youtube" -> "https://www.youtube.com/results?search_query=${Uri.encode(cleanQuery)}"
                "tv.danmaku.bili" -> "https://search.bilibili.com/all?keyword=${Uri.encode(cleanQuery)}"
                "com.smile.gifmaker" -> "https://www.kuaishou.com/search/video?searchKey=${Uri.encode(cleanQuery)}"
                "com.sina.weibo" -> "https://s.weibo.com/weibo/${Uri.encode(cleanQuery)}"
                "com.douban.frodo" -> "https://www.douban.com/search?q=${Uri.encode(cleanQuery)}"
                else -> config.searchUrl.format(Uri.encode(cleanQuery))
            }
            
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webSearchUrl))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            
            Log.d(TAG, "成功跳转到${config.packageName} Web搜索结果页面（使用软件tab一致的URL scheme）: $cleanQuery")
            Toast.makeText(context, "已通过网页搜索跳转到${getPlatformDisplayName(config.packageName)}搜索结果", Toast.LENGTH_SHORT).show()
            
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
     * 获取相关平台信息
     * 现在支持所有应用，不仅仅是预设平台
     */
    fun getRelevantPlatforms(query: String): List<PlatformInfo> {
        // 获取所有启用的应用
        val enabledApps = customizationManager.getEnabledApps()
        
        // 创建应用信息列表
        val appInfos = mutableListOf<PlatformInfo>()
        
        // 添加预设平台（如果启用）
        val allPlatforms = getAllPlatforms()
        allPlatforms.forEach { platform ->
            if (enabledApps.contains(platform.config.packageName)) {
                appInfos.add(platform)
            }
        }
        
        // 添加其他启用的应用
        enabledApps.forEach { packageName ->
            if (!allPlatforms.any { it.config.packageName == packageName }) {
                // 这是一个非预设应用，创建PlatformInfo
                val appInfo = createAppInfo(packageName)
                if (appInfo != null) {
                    appInfos.add(appInfo)
                }
            }
        }
        
        // 根据查询内容进行优先级排序
        val lowerQuery = query.lowercase()
        val prioritizedApps = when {
            lowerQuery.contains("视频") || lowerQuery.contains("电影") || lowerQuery.contains("音乐") -> {
                listOf("抖音", "哔哩哔哩", "YouTube", "快手")
            }
            lowerQuery.contains("购物") || lowerQuery.contains("商品") || lowerQuery.contains("价格") -> {
                listOf("淘宝", "京东", "拼多多", "天猫")
            }
            lowerQuery.contains("社交") || lowerQuery.contains("朋友") || lowerQuery.contains("聊天") -> {
                listOf("微信", "QQ", "微博", "小红书")
            }
            lowerQuery.contains("学习") || lowerQuery.contains("教育") || lowerQuery.contains("知识") -> {
                listOf("哔哩哔哩", "知乎", "豆瓣", "YouTube")
            }
            else -> {
                // 默认优先级
                listOf("抖音", "小红书", "哔哩哔哩", "YouTube", "微博", "豆瓣", "快手")
            }
        }
        
        val sortedApps = mutableListOf<PlatformInfo>()
        prioritizedApps.forEach { appName ->
            appInfos.find { it.name == appName }?.let { platform ->
                sortedApps.add(platform)
            }
        }
        
        // 添加剩余的应用
        appInfos.forEach { platform ->
            if (!sortedApps.any { it.name == platform.name }) {
                sortedApps.add(platform)
            }
        }
        
        return sortedApps
    }
    
    /**
     * 获取定制管理器
     */
    fun getCustomizationManager(): PlatformIconCustomizationManager {
        return customizationManager
    }
    
    /**
     * 为动态应用创建PlatformInfo
     * 使用与软件tab相同的图标获取方式
     */
    private fun createAppInfo(packageName: String): PlatformInfo? {
        return try {
            val packageManager = context.packageManager
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val appName = packageManager.getApplicationLabel(packageInfo.applicationInfo).toString()
            
            // 创建通用的PlatformConfig，不依赖硬编码图标
            val config = PlatformConfig(
                packageName = packageName,
                urlScheme = "$packageName://",
                searchUrl = "https://www.google.com/search?q=%s",
                iconRes = "" // 空字符串，让PlatformIconLoader处理
            )
            
            PlatformInfo(
                name = appName,
                config = config,
                isInstalled = true
            )
        } catch (e: Exception) {
            Log.e(TAG, "创建应用信息失败: $packageName", e)
            null
        }
    }
    
    /**
     * 激活AI应用悬浮窗服务
     * 在AI应用跳转后启动悬浮窗服务，支持用户反复跳转
     */
    private fun activateAIAppOverlayService(packageName: String, query: String, appName: String) {
        try {
            Log.d(TAG, "激活AI应用悬浮窗服务: $appName, 包名: $packageName, 查询: $query")
            
            val intent = Intent(context, AIAppOverlayService::class.java).apply {
                action = AIAppOverlayService.ACTION_SHOW_OVERLAY
                putExtra(AIAppOverlayService.EXTRA_APP_NAME, appName)
                putExtra(AIAppOverlayService.EXTRA_QUERY, query)
                putExtra(AIAppOverlayService.EXTRA_PACKAGE_NAME, packageName)
                putExtra("mode", "overlay") // 设置为悬浮窗模式
            }
            
            context.startService(intent)
            
            Log.d(TAG, "AI应用悬浮窗服务已激活: $appName")
            Toast.makeText(context, "已激活AI应用悬浮窗服务，可反复跳转", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e(TAG, "激活AI应用悬浮窗服务失败: $appName", e)
            Toast.makeText(context, "悬浮窗服务启动失败", Toast.LENGTH_SHORT).show()
        }
    }
}
