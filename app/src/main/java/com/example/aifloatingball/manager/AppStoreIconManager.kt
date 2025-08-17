package com.example.aifloatingball.manager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.example.aifloatingball.config.IconResolutionConfig
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * 专门用于App Store图标获取的管理器
 * 提供更精确的iTunes API集成和缓存策略
 */
class AppStoreIconManager private constructor(private val context: Context) {
    
    private val iconCache = ConcurrentHashMap<String, Drawable>()
    private val urlCache = ConcurrentHashMap<String, List<String>>()
    private val failedUrls = ConcurrentHashMap<String, Long>()
    private val cacheDir = File(context.cacheDir, "appstore_icons")
    
    companion object {
        @Volatile
        private var instance: AppStoreIconManager? = null
        
        private const val CACHE_EXPIRY_TIME = 24 * 60 * 60 * 1000L // 24小时
        private const val FAILED_URL_RETRY_TIME = 60 * 60 * 1000L // 1小时后重试失败的URL
        
        fun getInstance(context: Context): AppStoreIconManager {
            return instance ?: synchronized(this) {
                instance ?: AppStoreIconManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }
    
    /**
     * 获取App Store高质量图标 (支持配置化分辨率)
     */
    suspend fun getAppStoreIcon(
        packageName: String,
        appName: String,
        displayContext: IconResolutionConfig.DisplayContext = IconResolutionConfig.DisplayContext.APP_SEARCH_GRID,
        onIconLoaded: (Drawable?) -> Unit
    ) {
        val cacheKey = "${packageName}_${appName}_appstore"
        
        // 1. 检查内存缓存
        iconCache[cacheKey]?.let { cachedIcon ->
            onIconLoaded(cachedIcon)
            return
        }
        
        // 2. 检查本地缓存
        val localIcon = loadFromLocalCache(cacheKey)
        if (localIcon != null) {
            iconCache[cacheKey] = localIcon
            onIconLoaded(localIcon)
            return
        }
        
        // 3. 异步从App Store获取 (使用配置化分辨率)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val icon = fetchFromAppStore(packageName, appName, displayContext)
                if (icon != null) {
                    // 缓存到内存和本地
                    iconCache[cacheKey] = icon
                    saveToLocalCache(cacheKey, icon)

                    withContext(Dispatchers.Main) {
                        onIconLoaded(icon)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onIconLoaded(null)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onIconLoaded(null)
                }
            }
        }
    }
    
    /**
     * 从App Store获取图标 (支持配置化分辨率 + AI应用增强)
     */
    private suspend fun fetchFromAppStore(packageName: String, appName: String, displayContext: IconResolutionConfig.DisplayContext): Drawable? {
        return withContext(Dispatchers.IO) {
            try {
                // 0. 优先尝试AI应用图标增强器
                val aiIconEnhancer = AIAppIconEnhancer(context)
                if (aiIconEnhancer.isAIOrSocialApp(packageName, appName)) {
                    val aiIcon = aiIconEnhancer.getAIAppIcon(packageName, appName)
                    if (aiIcon != null) return@withContext aiIcon

                    // 如果AI增强器没有找到，使用备用关键词搜索
                    val alternativeKeywords = aiIconEnhancer.getAlternativeSearchKeywords(appName)
                    for (keyword in alternativeKeywords) {
                        val keywordIcons = searchByExactName(keyword, displayContext)
                        for (iconUrl in keywordIcons) {
                            val icon = downloadIcon(iconUrl)
                            if (icon != null) return@withContext icon
                        }
                    }
                }

                // 1. 尝试精确搜索
                val exactIcons = searchByExactName(appName, displayContext)
                for (iconUrl in exactIcons) {
                    val icon = downloadIcon(iconUrl)
                    if (icon != null) return@withContext icon
                }

                // 2. 尝试模糊搜索
                val fuzzyIcons = searchByFuzzyName(appName, displayContext)
                for (iconUrl in fuzzyIcons) {
                    val icon = downloadIcon(iconUrl)
                    if (icon != null) return@withContext icon
                }

                // 3. 尝试通过包名推测
                val packageIcons = searchByPackageName(packageName, displayContext)
                for (iconUrl in packageIcons) {
                    val icon = downloadIcon(iconUrl)
                    if (icon != null) return@withContext icon
                }

                null
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * 精确名称搜索
     */
    private suspend fun searchByExactName(appName: String, displayContext: IconResolutionConfig.DisplayContext): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val encodedName = URLEncoder.encode(appName, "UTF-8")
                val searchUrl = "https://itunes.apple.com/search?term=$encodedName&media=software&entity=software&limit=5&country=us"
                
                val response = downloadText(searchUrl)
                if (response != null) {
                    parseIconUrls(response, appName, exactMatch = true, displayContext)
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    /**
     * 模糊名称搜索
     */
    private suspend fun searchByFuzzyName(appName: String, displayContext: IconResolutionConfig.DisplayContext): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val keywords = generateSearchKeywords(appName)
                val icons = mutableListOf<String>()
                
                for (keyword in keywords.take(3)) {
                    val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
                    val searchUrl = "https://itunes.apple.com/search?term=$encodedKeyword&media=software&entity=software&limit=3&country=us"
                    
                    val response = downloadText(searchUrl)
                    if (response != null) {
                        val keywordIcons = parseIconUrls(response, appName, exactMatch = false, displayContext)
                        icons.addAll(keywordIcons)
                    }
                }
                
                icons.distinct()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    /**
     * 通过包名搜索
     */
    private suspend fun searchByPackageName(packageName: String, displayContext: IconResolutionConfig.DisplayContext): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                // 尝试将Android包名转换为可能的iOS Bundle ID
                val possibleBundleIds = generatePossibleBundleIds(packageName)
                val icons = mutableListOf<String>()
                
                for (bundleId in possibleBundleIds) {
                    val searchUrl = "https://itunes.apple.com/lookup?bundleId=$bundleId"
                    val response = downloadText(searchUrl)
                    if (response != null) {
                        val bundleIcons = parseIconUrls(response, displayContext = displayContext)
                        icons.addAll(bundleIcons)
                    }
                }
                
                icons.distinct()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    /**
     * 生成可能的Bundle ID (增强版 - 包含AI应用和社交平台)
     */
    private fun generatePossibleBundleIds(packageName: String): List<String> {
        val bundleIds = mutableListOf<String>()

        // 直接使用Android包名
        bundleIds.add(packageName)

        // 扩展的包名映射 (包含AI应用和社交平台)
        val mappings = mapOf(
            // 基础应用
            "com.tencent.mm" to "com.tencent.xin",
            "com.tencent.mobileqq" to "com.tencent.qq",
            "com.alibaba.android.rimet" to "com.alibaba.DingTalk",
            "com.eg.android.AlipayGphone" to "com.alipay.iphoneclient",
            "com.netease.cloudmusic" to "com.netease.cloudmusic",
            "com.baidu.BaiduMap" to "com.baidu.map",
            "com.sina.weibo" to "com.sina.weibo",

            // AI应用映射
            "com.deepseek.chat" to "com.deepseek.chat",
            "com.moonshot.kimi" to "com.moonshot.kimi",
            "com.google.android.apps.bard" to "com.google.Bard",
            "com.zhipu.chatglm" to "com.zhipu.chatglm",

            // 社交平台
            "com.xingin.xhs" to "com.xingin.xhs", // 小红书
            "com.zhihu.android" to "com.zhihu.ios", // 知乎
            "com.ss.android.ugc.aweme" to "com.ss.iphone.ugc.Aweme", // 抖音
            "com.sankuai.meituan" to "com.meituan.imeituan", // 美团

            // 其他常用应用
            "com.taobao.taobao" to "com.taobao.mobile",
            "com.jingdong.app.mall" to "com.jd.JDMobile"
        )

        mappings[packageName]?.let { bundleIds.add(it) }

        return bundleIds
    }
    
    /**
     * 生成搜索关键词 (增强版 - 包含AI应用和社交平台)
     */
    private fun generateSearchKeywords(appName: String): List<String> {
        val keywords = mutableListOf<String>()

        // 原始名称
        keywords.add(appName)

        // 特定应用的英文名称映射
        val appNameMappings = mapOf(
            "DeepSeek" to listOf("DeepSeek", "Deep Seek", "DeepSeek Chat"),
            "Kimi" to listOf("Kimi", "Moonshot", "Kimi Chat"),
            "Gemini" to listOf("Gemini", "Google Gemini", "Bard"),
            "小红书" to listOf("小红书", "RedBook", "Little Red Book", "Xiaohongshu"),
            "知乎" to listOf("知乎", "Zhihu", "知乎日报"),
            "智谱" to listOf("智谱", "ChatGLM", "智谱清言", "GLM"),
            "文心一言" to listOf("文心一言", "ERNIE", "百度文心"),
            "通义千问" to listOf("通义千问", "Qwen", "阿里通义")
        )

        // 添加特定映射
        appNameMappings[appName]?.let { mappedNames ->
            keywords.addAll(mappedNames)
        }

        // 移除常见后缀
        val cleanName = appName.replace(Regex("(?i)\\s*(app|应用|软件|客户端|chat|ai)$"), "").trim()
        if (cleanName != appName) {
            keywords.add(cleanName)
        }

        // 提取英文部分
        val englishPart = appName.replace(Regex("[^a-zA-Z\\s]"), "").trim()
        if (englishPart.isNotEmpty() && englishPart != appName) {
            keywords.add(englishPart)
        }

        // 提取中文部分
        val chinesePart = appName.replace(Regex("[a-zA-Z0-9\\s]"), "").trim()
        if (chinesePart.isNotEmpty() && chinesePart != appName) {
            keywords.add(chinesePart)
        }

        return keywords.distinct()
    }
    
    /**
     * 解析图标URL (优化分辨率配比)
     */
    private fun parseIconUrls(response: String, targetAppName: String? = null, exactMatch: Boolean = false, displayContext: IconResolutionConfig.DisplayContext = IconResolutionConfig.DisplayContext.APP_SEARCH_GRID): List<String> {
        return try {
            val jsonObject = JSONObject(response)
            val results = jsonObject.getJSONArray("results")
            val icons = mutableListOf<String>()

            for (i in 0 until results.length()) {
                val app = results.getJSONObject(i)
                val trackName = app.optString("trackName", "")

                // 如果需要精确匹配，检查应用名称
                if (targetAppName != null && exactMatch) {
                    if (!isExactMatch(trackName, targetAppName)) {
                        continue
                    }
                } else if (targetAppName != null) {
                    if (!isFuzzyMatch(trackName, targetAppName)) {
                        continue
                    }
                }

                // 根据配置获取推荐的分辨率优先级
                val recommendedResolutions = IconResolutionConfig.getRecommendedResolutions(displayContext)
                val iconUrls = mutableListOf<String>()

                // 按配置的优先级获取图标URL
                for (resolution in recommendedResolutions) {
                    val iconUrl = when (resolution) {
                        IconResolutionConfig.IconResolution.HIGH -> app.optString("artworkUrl512")
                        IconResolutionConfig.IconResolution.MEDIUM -> {
                            // 尝试获取256x256，如果没有则使用512x512并转换
                            val icon512 = app.optString("artworkUrl512")
                            if (icon512.isNotEmpty()) {
                                icon512.replace("512x512", "256x256")
                            } else {
                                ""
                            }
                        }
                        IconResolutionConfig.IconResolution.STANDARD -> app.optString("artworkUrl100")
                        IconResolutionConfig.IconResolution.LOW -> app.optString("artworkUrl60")
                        IconResolutionConfig.IconResolution.ULTRA_HIGH -> {
                            // 只在质量优先模式下获取1024x1024
                            if (IconResolutionConfig.currentPerformanceMode == IconResolutionConfig.PerformanceMode.QUALITY_FIRST) {
                                val icon512 = app.optString("artworkUrl512")
                                if (icon512.isNotEmpty()) {
                                    icon512.replace("512x512", "1024x1024")
                                } else {
                                    ""
                                }
                            } else {
                                ""
                            }
                        }
                    }

                    if (iconUrl.isNotEmpty()) {
                        iconUrls.add(iconUrl)
                    }
                }

                icons.addAll(iconUrls)
            }

            icons.distinct()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    /**
     * 精确匹配
     */
    private fun isExactMatch(trackName: String, targetName: String): Boolean {
        val track = trackName.lowercase().trim()
        val target = targetName.lowercase().trim()
        return track == target || track.replace(Regex("[\\s\\-_]+"), "") == target.replace(Regex("[\\s\\-_]+"), "")
    }
    
    /**
     * 模糊匹配
     */
    private fun isFuzzyMatch(trackName: String, targetName: String): Boolean {
        val track = trackName.lowercase().trim()
        val target = targetName.lowercase().trim()
        
        return track.contains(target) || target.contains(track) || 
               calculateSimilarity(track, target) > 0.6
    }
    
    /**
     * 计算相似度
     */
    private fun calculateSimilarity(s1: String, s2: String): Double {
        val maxLen = maxOf(s1.length, s2.length)
        if (maxLen == 0) return 1.0
        return (maxLen - levenshteinDistance(s1, s2)) / maxLen.toDouble()
    }
    
    /**
     * 计算编辑距离
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        
        return dp[s1.length][s2.length]
    }

    /**
     * 下载文本内容 (使用配置化超时)
     */
    private fun downloadText(urlString: String): String? {
        return try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            val (connectTimeout, readTimeout) = IconResolutionConfig.getNetworkTimeouts()
            connection.connectTimeout = connectTimeout
            connection.readTimeout = readTimeout
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X)")

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            response
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 下载图标
     */
    private fun downloadIcon(iconUrl: String): Drawable? {
        if (iconUrl.isEmpty()) return null

        // 检查失败缓存
        failedUrls[iconUrl]?.let { failTime ->
            if (System.currentTimeMillis() - failTime < FAILED_URL_RETRY_TIME) {
                return null
            }
        }

        return try {
            val url = URL(iconUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 14_0 like Mac OS X)")

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val bitmap = BitmapFactory.decodeStream(connection.inputStream)
                connection.disconnect()

                if (bitmap != null && bitmap.width >= 32 && bitmap.height >= 32) {
                    BitmapDrawable(context.resources, bitmap)
                } else {
                    failedUrls[iconUrl] = System.currentTimeMillis()
                    null
                }
            } else {
                failedUrls[iconUrl] = System.currentTimeMillis()
                null
            }
        } catch (e: Exception) {
            failedUrls[iconUrl] = System.currentTimeMillis()
            null
        }
    }

    /**
     * 从本地缓存加载
     */
    private fun loadFromLocalCache(cacheKey: String): Drawable? {
        return try {
            val cacheFile = File(cacheDir, "$cacheKey.png")
            if (cacheFile.exists() &&
                System.currentTimeMillis() - cacheFile.lastModified() < CACHE_EXPIRY_TIME) {
                val bitmap = BitmapFactory.decodeFile(cacheFile.absolutePath)
                if (bitmap != null) {
                    BitmapDrawable(context.resources, bitmap)
                } else {
                    cacheFile.delete()
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 保存到本地缓存
     */
    private fun saveToLocalCache(cacheKey: String, drawable: Drawable) {
        try {
            if (drawable is BitmapDrawable) {
                val cacheFile = File(cacheDir, "$cacheKey.png")
                val outputStream = FileOutputStream(cacheFile)
                drawable.bitmap.compress(Bitmap.CompressFormat.PNG, 90, outputStream)
                outputStream.close()
            }
        } catch (e: Exception) {
            // 忽略缓存保存错误
        }
    }

    /**
     * 清理过期缓存
     */
    fun cleanExpiredCache() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val currentTime = System.currentTimeMillis()
                cacheDir.listFiles()?.forEach { file ->
                    if (currentTime - file.lastModified() > CACHE_EXPIRY_TIME) {
                        file.delete()
                    }
                }

                // 清理失败URL缓存
                val iterator = failedUrls.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (currentTime - entry.value > FAILED_URL_RETRY_TIME) {
                        iterator.remove()
                    }
                }
            } catch (e: Exception) {
                // 忽略清理错误
            }
        }
    }
}
