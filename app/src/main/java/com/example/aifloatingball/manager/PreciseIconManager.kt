package com.example.aifloatingball.manager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 精准图标管理器
 * 专门解决AI应用、常规应用、搜索引擎图标获取不准确的问题
 */
class PreciseIconManager(private val context: Context) {
    
    companion object {
        private const val TAG = "PreciseIconManager"
        private const val TIMEOUT = 8000
    }
    
    /**
     * AI应用精准图标映射
     */
    private val aiAppPreciseIcons = mapOf(
        // AI应用 - 使用官方高清图标
        "deepseek" to listOf(
            "https://chat.deepseek.com/favicon-96x96.png",
            "https://chat.deepseek.com/apple-touch-icon.png",
            "https://deepseek.com/favicon.ico"
        ),
        "kimi" to listOf(
            "https://kimi.moonshot.cn/favicon-96x96.png", 
            "https://kimi.moonshot.cn/apple-touch-icon.png",
            "https://kimi.moonshot.cn/favicon.ico"
        ),
        "gemini" to listOf(
            "https://www.gstatic.com/lamda/images/gemini_favicon_f069958c85030456e93de685481c559f160ea06b.png",
            "https://ssl.gstatic.com/ui/v1/icons/mail/rfr/gmail_icon_32.png"
        ),
        "chatglm" to listOf(
            "https://chatglm.cn/favicon-96x96.png",
            "https://chatglm.cn/apple-touch-icon.png"
        ),
        "claude" to listOf(
            "https://claude.ai/favicon.ico",
            "https://claude.ai/apple-touch-icon.png"
        ),
        "gpt" to listOf(
            "https://chat.openai.com/favicon-32x32.png",
            "https://openai.com/favicon.ico",
            "https://logo.clearbit.com/openai.com"
        ),
        // 新增AI应用
        "perplexity" to listOf(
            "https://www.perplexity.ai/favicon.ico",
            "https://www.perplexity.ai/apple-touch-icon.png",
            "https://logo.clearbit.com/perplexity.ai"
        ),
        "doubao" to listOf(
            "https://www.doubao.com/favicon.ico",
            "https://www.doubao.com/apple-touch-icon.png",
            "https://logo.clearbit.com/doubao.com"
        ),
        "tongyi" to listOf(
            "https://tongyi.aliyun.com/favicon.ico",
            "https://tongyi.aliyun.com/apple-touch-icon.png",
            "https://logo.clearbit.com/aliyun.com"
        ),
        "wenxin" to listOf(
            "https://yiyan.baidu.com/favicon.ico",
            "https://yiyan.baidu.com/apple-touch-icon.png",
            "https://logo.clearbit.com/baidu.com"
        ),
        "xinghuo" to listOf(
            "https://xinghuo.xfyun.cn/favicon.ico",
            "https://xinghuo.xfyun.cn/apple-touch-icon.png",
            "https://logo.clearbit.com/xfyun.cn"
        )
    )
    
    /**
     * 常规应用精准图标映射
     */
    private val regularAppPreciseIcons = mapOf(
        // 社交平台
        "小红书" to listOf(
            "https://fe-video-qc.xhscdn.com/fe-platform/hera/static/apple-touch-icon.png",
            "https://www.xiaohongshu.com/favicon.ico"
        ),
        "知乎" to listOf(
            "https://static.zhihu.com/heifetz/assets/apple-touch-icon-152.png",
            "https://static.zhihu.com/heifetz/favicon.ico"
        ),
        "抖音" to listOf(
            "https://lf1-cdn-tos.bytegoofy.com/goofy/tiktok/web/node/_next/static/images/logo-7328701c910ebbccb5670085d243fc12.svg",
            "https://www.douyin.com/favicon.ico"
        ),
        "美团" to listOf(
            "https://p0.meituan.net/travelcube/2d05c8c1c82d4b8dbf0b7f8a58e7ac06.png",
            "https://www.meituan.com/favicon.ico"
        ),
        "微博" to listOf(
            "https://weibo.com/favicon.ico",
            "https://h5.sinaimg.cn/upload/2015/09/25/3/timeline_card_small_web_default.png"
        ),
        "豆瓣" to listOf(
            "https://www.douban.com/favicon.ico",
            "https://img1.doubanio.com/f/movie/8dd0c794499fe925ae2ae89ee30cd225750457b4/pics/movie/logo.png"
        )
    )
    
    /**
     * 搜索引擎精准图标映射 - 增强版
     */
    private val searchEnginePreciseIcons = mapOf(
        "google" to listOf(
            "https://www.google.com/favicon.ico",
            "https://ssl.gstatic.com/ui/v1/icons/mail/rfr/logo_gmail_lockup_default_1x_r2.png",
            "https://logo.clearbit.com/google.com",
            "https://www.google.com/images/branding/googleg/1x/googleg_standard_color_128dp.png"
        ),
        "baidu" to listOf(
            "https://www.baidu.com/favicon.ico",
            "https://dss0.bdstatic.com/5aV1bjqh_Q23odCf/static/superman/img/logo/bd_logo1_31bdc765.png",
            "https://logo.clearbit.com/baidu.com",
            "https://www.baidu.com/img/flexible/logo/pc/result.png"
        ),
        "bing" to listOf(
            "https://www.bing.com/favicon.ico",
            "https://www.bing.com/sa/simg/bing_p_rr_teal_min.ico",
            "https://logo.clearbit.com/bing.com"
        ),
        "sogou" to listOf(
            "https://www.sogou.com/favicon.ico",
            "https://dlweb.sogoucdn.com/common/pc/sogou.ico",
            "https://logo.clearbit.com/sogou.com"
        ),
        "360" to listOf(
            "https://www.so.com/favicon.ico",
            "https://p1.ssl.qhimg.com/t01d91636862957f76e.png",
            "https://logo.clearbit.com/360.cn"
        ),
        "duckduckgo" to listOf(
            "https://duckduckgo.com/favicon.ico",
            "https://duckduckgo.com/assets/logo_homepage.normal.v108.svg",
            "https://logo.clearbit.com/duckduckgo.com"
        ),
        // 新增搜索引擎
        "yahoo" to listOf(
            "https://www.yahoo.com/favicon.ico",
            "https://s.yimg.com/rz/l/yahoo_en-US_f_p_142x37_2x.png",
            "https://logo.clearbit.com/yahoo.com"
        ),
        "yandex" to listOf(
            "https://yandex.com/favicon.ico",
            "https://logo.clearbit.com/yandex.com"
        ),
        "startpage" to listOf(
            "https://www.startpage.com/favicon.ico",
            "https://logo.clearbit.com/startpage.com"
        )
    )
    
    /**
     * 包名到应用关键词的精准映射 - 增强版
     */
    private val packageToKeywordMapping = mapOf(
        // AI应用 - 更全面的包名映射
        "com.deepseek.chat" to "deepseek",
        "com.deepseek.ai" to "deepseek",
        "ai.deepseek.chat" to "deepseek",
        "com.moonshot.kimi" to "kimi",
        "cn.moonshot.kimi" to "kimi",
        "com.google.android.apps.bard" to "gemini",
        "com.google.ai.gemini" to "gemini",
        "com.zhipu.chatglm" to "chatglm",
        "cn.zhipu.chatglm" to "chatglm",
        "com.anthropic.claude" to "claude",
        "ai.anthropic.claude" to "claude",
        "com.openai.chatgpt" to "gpt",
        "com.openai.gpt" to "gpt",
        "ai.perplexity.app" to "perplexity",
        "com.perplexity.ai" to "perplexity",
        "com.bytedance.doubao" to "doubao",
        "com.doubao.ai" to "doubao",
        "com.aliyun.tongyi" to "tongyi",
        "com.alibaba.tongyi" to "tongyi",
        "com.baidu.wenxin" to "wenxin",
        "com.baidu.yiyan" to "wenxin",
        "com.baidu.wenxiaoyan" to "wenxiaoyan",
        "ai.qwenlm.chat.android" to "wenxiaoyan",
        "com.xai.grok" to "grok",
        "ai.x.grok" to "grok",
        "ai.perplexity.app" to "perplexity",
        "ai.perplexity.app.android" to "perplexity",
        "tech.butterfly.app" to "manus",
        "com.mita.ai" to "mita_ai",
        "com.poe.app" to "poe",
        "com.poe.android" to "poe",
        "com.ima.app" to "ima",
        "com.tencent.ima" to "ima",
        "com.nano.ai" to "nano_ai",
        "com.iflytek.xinghuo" to "xinghuo",
        "cn.xfyun.xinghuo" to "xinghuo",

        // 常规应用 - 更全面的包名映射
        "com.xingin.xhs" to "小红书",
        "com.xiaohongshu.app" to "小红书",
        "com.zhihu.android" to "知乎",
        "com.zhihu.app" to "知乎",
        "com.ss.android.ugc.aweme" to "抖音",
        "com.bytedance.douyin" to "抖音",
        "com.sankuai.meituan" to "美团",
        "com.meituan.android" to "美团",
        "com.sina.weibo" to "微博",
        "com.weibo.android" to "微博",
        "com.douban.frodo" to "豆瓣",
        "com.douban.app" to "豆瓣",
        "com.kuaishou.nebula" to "快手",
        "com.smile.gifmaker" to "快手",
        "com.tencent.mm" to "微信",
        "com.tencent.mobileqq" to "QQ",
        "com.taobao.taobao" to "淘宝",
        "com.jingdong.app.mall" to "京东",
        "com.eg.android.AlipayGphone" to "支付宝"
    )
    
    /**
     * 应用名称到关键词的模糊映射 - 增强版
     */
    private val appNameToKeywordMapping = mapOf(
        // AI应用名称映射
        "DeepSeek" to "deepseek",
        "深度求索" to "deepseek",
        "Kimi" to "kimi",
        "月之暗面" to "kimi",
        "Gemini" to "gemini",
        "双子座" to "gemini",
        "智谱" to "chatglm",
        "ChatGLM" to "chatglm",
        "清言" to "chatglm",
        "Claude" to "claude",
        "克劳德" to "claude",
        "ChatGPT" to "gpt",
        "GPT" to "gpt",
        "OpenAI" to "gpt",
        "Perplexity" to "perplexity",
        "豆包" to "doubao",
        "Doubao" to "doubao",
        "通义千问" to "tongyi",
        "通义" to "tongyi",
        "文心一言" to "wenxin",
        "文心" to "wenxin",
        "百度AI" to "wenxin",
        "讯飞星火" to "xinghuo",
        "星火" to "xinghuo",
        "科大讯飞" to "xinghuo",

        // 常规应用名称映射
        "小红书" to "小红书",
        "RED" to "小红书",
        "知乎" to "知乎",
        "Zhihu" to "知乎",
        "抖音" to "抖音",
        "TikTok" to "抖音",
        "美团" to "美团",
        "Meituan" to "美团",
        "微博" to "微博",
        "Weibo" to "微博",
        "豆瓣" to "豆瓣",
        "Douban" to "豆瓣",
        "快手" to "快手",
        "Kuaishou" to "快手",
        "微信" to "微信",
        "WeChat" to "微信",
        "QQ" to "QQ",
        "腾讯QQ" to "QQ",
        "淘宝" to "淘宝",
        "Taobao" to "淘宝",
        "京东" to "京东",
        "JD" to "京东",
        "支付宝" to "支付宝",
        "Alipay" to "支付宝",

        // 搜索引擎名称映射
        "Google" to "google",
        "谷歌" to "google",
        "百度" to "baidu",
        "Baidu" to "baidu",
        "Bing" to "bing",
        "必应" to "bing",
        "搜狗" to "sogou",
        "Sogou" to "sogou",
        "360搜索" to "360",
        "360" to "360",
        "DuckDuckGo" to "duckduckgo",
        "Yahoo" to "yahoo",
        "雅虎" to "yahoo"
    )
    
    /**
     * 获取精准图标
     */
    suspend fun getPreciseIcon(packageName: String, appName: String, type: IconType): Drawable? {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "获取精准图标: packageName=$packageName, appName=$appName, type=$type")
                
                when (type) {
                    IconType.AI_APP -> getAIAppIcon(packageName, appName)
                    IconType.REGULAR_APP -> getRegularAppIcon(packageName, appName)
                    IconType.SEARCH_ENGINE -> getSearchEngineIcon(packageName, appName)
                }
            } catch (e: Exception) {
                Log.e(TAG, "获取精准图标失败", e)
                null
            }
        }
    }
    
    /**
     * 获取AI应用图标
     */
    private suspend fun getAIAppIcon(packageName: String, appName: String): Drawable? {
        // 1. 通过包名精准匹配
        val keyword = packageToKeywordMapping[packageName]
        if (keyword != null) {
            val icon = tryGetIconFromUrls(aiAppPreciseIcons[keyword] ?: emptyList())
            if (icon != null) return icon
        }
        
        // 2. 通过应用名称匹配
        val mappedKeyword = appNameToKeywordMapping[appName]
        if (mappedKeyword != null) {
            val icon = tryGetIconFromUrls(aiAppPreciseIcons[mappedKeyword] ?: emptyList())
            if (icon != null) return icon
        }
        
        // 3. 模糊匹配
        for ((key, urls) in aiAppPreciseIcons) {
            if (appName.contains(key, ignoreCase = true) || 
                key.contains(appName, ignoreCase = true)) {
                val icon = tryGetIconFromUrls(urls)
                if (icon != null) return icon
            }
        }
        
        return null
    }
    
    /**
     * 获取常规应用图标
     */
    private suspend fun getRegularAppIcon(packageName: String, appName: String): Drawable? {
        // 1. 通过包名精准匹配
        val keyword = packageToKeywordMapping[packageName]
        if (keyword != null) {
            val icon = tryGetIconFromUrls(regularAppPreciseIcons[keyword] ?: emptyList())
            if (icon != null) return icon
        }
        
        // 2. 通过应用名称匹配
        val mappedKeyword = appNameToKeywordMapping[appName]
        if (mappedKeyword != null) {
            val icon = tryGetIconFromUrls(regularAppPreciseIcons[mappedKeyword] ?: emptyList())
            if (icon != null) return icon
        }
        
        // 3. 模糊匹配
        for ((key, urls) in regularAppPreciseIcons) {
            if (appName.contains(key, ignoreCase = true) || 
                key.contains(appName, ignoreCase = true)) {
                val icon = tryGetIconFromUrls(urls)
                if (icon != null) return icon
            }
        }
        
        return null
    }
    
    /**
     * 获取搜索引擎图标
     */
    private suspend fun getSearchEngineIcon(packageName: String, appName: String): Drawable? {
        // 通过应用名称匹配搜索引擎
        val lowerAppName = appName.lowercase()
        
        for ((key, urls) in searchEnginePreciseIcons) {
            if (lowerAppName.contains(key) || key.contains(lowerAppName)) {
                val icon = tryGetIconFromUrls(urls)
                if (icon != null) return icon
            }
        }
        
        return null
    }
    
    /**
     * 尝试从URL列表获取图标
     */
    private suspend fun tryGetIconFromUrls(urls: List<String>): Drawable? {
        for (url in urls) {
            try {
                val drawable = downloadIconFromUrl(url)
                if (drawable != null) {
                    Log.d(TAG, "成功从URL获取图标: $url")
                    return drawable
                }
            } catch (e: Exception) {
                Log.w(TAG, "从URL获取图标失败: $url", e)
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
                connection.connectTimeout = TIMEOUT
                connection.readTimeout = TIMEOUT
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream: InputStream = connection.inputStream
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()
                    connection.disconnect()
                    
                    if (bitmap != null) {
                        BitmapDrawable(context.resources, bitmap)
                    } else {
                        null
                    }
                } else {
                    connection.disconnect()
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "下载图标失败: $urlString", e)
                null
            }
        }
    }
    
    /**
     * 图标类型枚举
     */
    enum class IconType {
        AI_APP,         // AI应用
        REGULAR_APP,    // 常规应用
        SEARCH_ENGINE   // 搜索引擎
    }
    
    /**
     * 判断应用类型
     */
    fun getAppType(packageName: String, appName: String): IconType {
        // AI应用判断
        val aiKeywords = listOf("deepseek", "kimi", "gemini", "chatglm", "claude", "gpt", "ai", "智谱", "文心", "通义")
        if (packageToKeywordMapping[packageName] in listOf("deepseek", "kimi", "gemini", "chatglm", "claude", "gpt") ||
            aiKeywords.any { appName.contains(it, ignoreCase = true) }) {
            return IconType.AI_APP
        }
        
        // 搜索引擎判断
        val searchKeywords = listOf("google", "baidu", "bing", "sogou", "360", "duckduckgo", "搜索", "search")
        if (searchKeywords.any { appName.contains(it, ignoreCase = true) }) {
            return IconType.SEARCH_ENGINE
        }
        
        // 默认为常规应用
        return IconType.REGULAR_APP
    }
}
