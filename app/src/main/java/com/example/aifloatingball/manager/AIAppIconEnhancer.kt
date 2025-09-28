package com.example.aifloatingball.manager

import android.content.Context
import android.graphics.drawable.Drawable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * AI应用图标获取增强器
 * 专门针对DeepSeek、Kimi、Gemini、小红书、知乎等应用提供高质量图标
 */
class AIAppIconEnhancer(private val context: Context) {
    
    companion object {
        private const val TAG = "AIAppIconEnhancer"
    }
    
    /**
     * AI应用和社交平台的高质量图标映射
     */
    private val aiAppIconMappings = mapOf(
        // AI应用
        "deepseek" to listOf(
            "https://chat.deepseek.com/favicon-32x32.png",
            "https://chat.deepseek.com/favicon-96x96.png",
            "https://chat.deepseek.com/apple-touch-icon.png",
            "https://raw.githubusercontent.com/deepseek-ai/DeepSeek-Chat/main/assets/logo.png"
        ),
        "kimi" to listOf(
            "https://kimi.moonshot.cn/favicon-32x32.png",
            "https://kimi.moonshot.cn/favicon-96x96.png", 
            "https://kimi.moonshot.cn/apple-touch-icon.png",
            "https://raw.githubusercontent.com/moonshot-ai/kimi-chat/main/assets/logo.png"
        ),
        "gemini" to listOf(
            "https://www.gstatic.com/lamda/images/gemini_sparkle_v002_d4735304ff6292a690345.svg",
            "https://www.gstatic.com/lamda/images/gemini_favicon_f069958c85030456e93de685481c559f160ea06b.png",
            "https://ssl.gstatic.com/ui/v1/icons/mail/rfr/logo_gmail_lockup_default_1x_r2.png"
        ),
        "chatglm" to listOf(
            "https://chatglm.cn/favicon-32x32.png",
            "https://chatglm.cn/favicon-96x96.png",
            "https://chatglm.cn/apple-touch-icon.png"
        ),
        
        // 社交平台
        "小红书" to listOf(
            "https://fe-video-qc.xhscdn.com/fe-platform/hera/static/favicon.ico",
            "https://www.xiaohongshu.com/favicon.ico",
            "https://fe-video-qc.xhscdn.com/fe-platform/hera/static/apple-touch-icon.png"
        ),
        "知乎" to listOf(
            "https://static.zhihu.com/heifetz/favicon.ico",
            "https://static.zhihu.com/heifetz/assets/apple-touch-icon-152.png",
            "https://pic1.zhimg.com/80/v2-a47051e92cf74930bedd7469978e6c08_720w.jpg"
        ),
        "抖音" to listOf(
            "https://lf1-cdn-tos.bytegoofy.com/goofy/tiktok/web/node/_next/static/images/logo-7328701c910ebbccb5670085d243fc12.svg",
            "https://www.douyin.com/favicon.ico"
        ),
        "美团" to listOf(
            "https://www.meituan.com/favicon.ico",
            "https://p0.meituan.net/travelcube/2d05c8c1c82d4b8dbf0b7f8a58e7ac06.png"
        )
    )
    
    /**
     * 包名到应用名称的映射
     */
    private val packageToAppNameMapping = mapOf(
        "com.deepseek.chat" to "deepseek",
        "com.moonshot.kimi" to "kimi", 
        "com.google.android.apps.bard" to "gemini",
        "com.zhipu.chatglm" to "chatglm",
        "com.baidu.wenxiaoyan" to "wenxiaoyan",
        "com.xai.grok" to "grok",
        "ai.perplexity.app" to "perplexity",
        "com.manus.app" to "manus",
        "com.mita.ai" to "mita_ai",
        "com.poe.app" to "poe",
        "com.ima.app" to "ima",
        "com.nano.ai" to "nano_ai",
        "com.xingin.xhs" to "小红书",
        "com.zhihu.android" to "知乎",
        "com.ss.android.ugc.aweme" to "抖音",
        "com.sankuai.meituan" to "美团"
    )
    
    /**
     * 应用名称到关键词的映射
     */
    private val appNameToKeywordMapping = mapOf(
        "DeepSeek" to "deepseek",
        "Kimi" to "kimi",
        "Gemini" to "gemini", 
        "智谱" to "chatglm",
        "ChatGLM" to "chatglm",
        "文小言" to "wenxiaoyan",
        "Grok" to "grok",
        "Perplexity" to "perplexity",
        "Manus" to "manus",
        "秘塔AI搜索" to "mita_ai",
        "Poe" to "poe",
        "IMA" to "ima",
        "纳米AI" to "nano_ai",
        "小红书" to "小红书",
        "知乎" to "知乎",
        "抖音" to "抖音",
        "美团" to "美团"
    )
    
    /**
     * 获取AI应用的高质量图标
     */
    suspend fun getAIAppIcon(packageName: String, appName: String): Drawable? {
        return withContext(Dispatchers.IO) {
            try {
                // 1. 通过包名映射获取
                val mappedAppName = packageToAppNameMapping[packageName]
                if (mappedAppName != null) {
                    val icon = tryGetIconFromMappings(mappedAppName)
                    if (icon != null) return@withContext icon
                }
                
                // 2. 通过应用名称映射获取
                val keyword = appNameToKeywordMapping[appName]
                if (keyword != null) {
                    val icon = tryGetIconFromMappings(keyword)
                    if (icon != null) return@withContext icon
                }
                
                // 3. 模糊匹配应用名称
                for ((key, urls) in aiAppIconMappings) {
                    if (appName.contains(key, ignoreCase = true) || key.contains(appName, ignoreCase = true)) {
                        val icon = tryGetIconFromUrls(urls)
                        if (icon != null) return@withContext icon
                    }
                }
                
                null
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * 从映射中尝试获取图标
     */
    private suspend fun tryGetIconFromMappings(appKey: String): Drawable? {
        val urls = aiAppIconMappings[appKey] ?: return null
        return tryGetIconFromUrls(urls)
    }
    
    /**
     * 从URL列表中尝试获取图标
     */
    private suspend fun tryGetIconFromUrls(urls: List<String>): Drawable? {
        for (url in urls) {
            try {
                val drawable = downloadIconFromUrl(url)
                if (drawable != null) {
                    return drawable
                }
            } catch (e: Exception) {
                // 继续尝试下一个URL
                continue
            }
        }
        return null
    }
    
    /**
     * 从URL下载图标
     */
    private suspend fun downloadIconFromUrl(urlString: String): Drawable? {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(urlString)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val drawable = Drawable.createFromStream(inputStream, null)
                    inputStream.close()
                    connection.disconnect()
                    drawable
                } else {
                    connection.disconnect()
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * 检查是否为AI应用或社交平台
     */
    fun isAIOrSocialApp(packageName: String, appName: String): Boolean {
        // 检查包名映射
        if (packageToAppNameMapping.containsKey(packageName)) {
            return true
        }
        
        // 检查应用名称映射
        if (appNameToKeywordMapping.containsKey(appName)) {
            return true
        }
        
        // 模糊匹配
        val lowerAppName = appName.lowercase()
        val aiKeywords = listOf("deepseek", "kimi", "gemini", "chatglm", "智谱", "文心", "通义")
        val socialKeywords = listOf("小红书", "知乎", "抖音", "美团", "微博", "豆瓣")
        
        return aiKeywords.any { lowerAppName.contains(it) } || 
               socialKeywords.any { lowerAppName.contains(it) }
    }
    
    /**
     * 获取应用的备用搜索关键词
     */
    fun getAlternativeSearchKeywords(appName: String): List<String> {
        val keywords = mutableListOf<String>()
        
        when {
            appName.contains("DeepSeek", ignoreCase = true) -> {
                keywords.addAll(listOf("DeepSeek", "Deep Seek", "DeepSeek Chat", "深度求索"))
            }
            appName.contains("Kimi", ignoreCase = true) -> {
                keywords.addAll(listOf("Kimi", "Moonshot", "Kimi Chat", "月之暗面"))
            }
            appName.contains("Gemini", ignoreCase = true) -> {
                keywords.addAll(listOf("Gemini", "Google Gemini", "Bard", "谷歌双子座"))
            }
            appName.contains("智谱", ignoreCase = true) || appName.contains("ChatGLM", ignoreCase = true) -> {
                keywords.addAll(listOf("智谱", "ChatGLM", "智谱清言", "GLM"))
            }
            appName.contains("小红书", ignoreCase = true) -> {
                keywords.addAll(listOf("小红书", "RedBook", "Little Red Book", "Xiaohongshu"))
            }
            appName.contains("知乎", ignoreCase = true) -> {
                keywords.addAll(listOf("知乎", "Zhihu", "知乎日报"))
            }
        }
        
        return keywords
    }
    
    /**
     * 清理缓存
     */
    fun clearCache() {
        // 如果有缓存机制，在这里清理
    }
}
