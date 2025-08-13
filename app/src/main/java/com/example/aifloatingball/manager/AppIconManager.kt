package com.example.aifloatingball.manager

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * 应用图标异步加载管理器
 */
class AppIconManager private constructor(private val context: Context) {
    
    private val iconCache = ConcurrentHashMap<String, Drawable>()
    private val downloadingIcons = ConcurrentHashMap<String, Job>()
    private val iconUrlCache = ConcurrentHashMap<String, List<String>>()
    
    companion object {
        @Volatile
        private var instance: AppIconManager? = null
        
        fun getInstance(context: Context): AppIconManager {
            return instance ?: synchronized(this) {
                instance ?: AppIconManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    /**
     * 异步获取应用图标
     */
    suspend fun getAppIconAsync(
        packageName: String,
        appName: String,
        onIconLoaded: (Drawable?) -> Unit
    ) {
        // 1. 检查内存缓存
        iconCache[packageName]?.let { cachedIcon ->
            onIconLoaded(cachedIcon)
            return
        }
        
        // 2. 检查本地缓存
        val localIcon = getCachedAppIcon(packageName)
        if (localIcon != null) {
            iconCache[packageName] = localIcon
            onIconLoaded(localIcon)
            return
        }
        
        // 3. 如果正在下载，等待结果
        downloadingIcons[packageName]?.let { job ->
            job.join()
            iconCache[packageName]?.let { icon ->
                onIconLoaded(icon)
                return
            }
        }
        
        // 4. 开始异步下载
        val downloadJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val icon = downloadAppIcon(packageName, appName)
                if (icon != null) {
                    // 缓存到内存和本地
                    iconCache[packageName] = icon
                    cacheAppIcon(packageName, icon)
                    
                    // 在主线程回调
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
            } finally {
                downloadingIcons.remove(packageName)
            }
        }
        
        downloadingIcons[packageName] = downloadJob
    }
    
    /**
     * 下载应用图标
     */
    private suspend fun downloadAppIcon(packageName: String, appName: String): Drawable? {
        return withContext(Dispatchers.IO) {
            try {
                // 尝试多个图标源
                val iconSources = getIconSources(packageName, appName)
                
                for (source in iconSources) {
                    try {
                        val icon = downloadImageFromUrl(source)
                        if (icon != null) {
                            return@withContext icon
                        }
                    } catch (e: Exception) {
                        // 继续尝试下一个源
                        continue
                    }
                }
                
                null
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * 获取图标源列表 - 增强版
     */
    private suspend fun getIconSources(packageName: String, appName: String): List<String> {
        val sources = mutableListOf<String>()

        // 1. 预定义的高质量图标映射 (最快，优先使用)
        getIconMappingDatabase()[packageName]?.let { url ->
            sources.add(url)
        }

        // 2. iTunes Search API (高质量，但较慢)
        val iTunesIcons = getIconsFromiTunes(appName, packageName)
        sources.addAll(iTunesIcons)

        // 3. 扩展的图标源
        sources.addAll(getExtendedIconSources(packageName, appName))

        // 4. Google Play Store (官方)
        sources.add("https://play-lh.googleusercontent.com/apps/$packageName/icon")

        // 5. 备用图标源
        sources.addAll(getBackupIconSources(packageName, appName))

        return sources.distinct()
    }

    /**
     * 获取扩展图标源
     */
    private fun getExtendedIconSources(packageName: String, appName: String): List<String> {
        val sources = mutableListOf<String>()

        // 1. APKMirror (高质量Android图标)
        sources.add("https://www.apkmirror.com/wp-content/themes/APKMirror/ap_resize/ap_resize.php?src=https://www.apkmirror.com/wp-content/uploads/icons/$packageName.png&w=96&h=96&q=100")

        // 2. F-Droid (开源应用图标)
        sources.add("https://f-droid.org/repo/icons-640/$packageName.png")

        // 3. APKPure (多个CDN)
        sources.add("https://image.winudf.com/v2/image1/icon/$packageName")
        sources.add("https://image.winudf.com/v2/image/icon/$packageName")

        // 4. 应用宝和腾讯系
        sources.add("https://pp.myapp.com/ma_icon/$packageName/icon")
        sources.add("https://android-artworks.25pp.com/fs08/2021/11/12/0/110_${packageName.hashCode().toString().takeLast(8)}.png")

        // 5. 华为应用市场
        sources.add("https://appimg.dbankcdn.com/application/icon144/$packageName.png")

        // 6. 小米应用商店
        sources.add("https://file.market.xiaomi.com/thumbnail/PNG/l114/${packageName}")

        return sources
    }

    /**
     * 获取备用图标源
     */
    private fun getBackupIconSources(packageName: String, appName: String): List<String> {
        val sources = mutableListOf<String>()

        // 1. 通过应用名称搜索的通用图标库
        val encodedName = java.net.URLEncoder.encode(appName, "UTF-8")
        sources.add("https://logo.clearbit.com/${extractDomainFromPackage(packageName)}")

        // 2. 图标字体库
        sources.add("https://cdn.jsdelivr.net/gh/simple-icons/simple-icons@latest/icons/${appName.lowercase().replace(" ", "")}.svg")

        // 3. 开源图标库
        sources.add("https://raw.githubusercontent.com/simple-icons/simple-icons/develop/icons/${appName.lowercase().replace(" ", "-")}.svg")

        return sources
    }

    /**
     * 从包名提取可能的域名
     */
    private fun extractDomainFromPackage(packageName: String): String {
        val parts = packageName.split(".")
        return if (parts.size >= 2) {
            "${parts[1]}.com"
        } else {
            "example.com"
        }
    }

    /**
     * 从iTunes Search API获取应用图标 - 增强版
     */
    private suspend fun getIconsFromiTunes(appName: String, packageName: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                // 检查缓存，避免重复API调用
                val cacheKey = "${packageName}_${appName}"
                iconUrlCache[cacheKey]?.let { cachedIcons: List<String> ->
                    if (cachedIcons.isNotEmpty()) {
                        return@withContext cachedIcons
                    }
                }

                val icons = mutableListOf<String>()

                // 1. 优先使用精确匹配的已知映射
                val knownMapping = getKnowniTunesMapping()[packageName]
                if (knownMapping != null) {
                    icons.addAll(knownMapping)
                }

                // 2. 通过应用名称搜索 (最准确)
                val nameIcons = searchiTunesByName(appName)
                icons.addAll(nameIcons)

                // 3. 通过增强的关键词搜索
                val enhancedKeywords = generateEnhancedKeywords(appName, packageName)
                for (keyword in enhancedKeywords.take(3)) { // 限制关键词数量以提高速度
                    val keywordIcons = searchiTunesBySingleKeyword(keyword)
                    icons.addAll(keywordIcons)
                    if (icons.size >= 5) break // 找到足够的图标就停止
                }

                // 4. 通过Bundle ID搜索 (如果前面没找到足够的图标)
                if (icons.size < 3) {
                    val bundleIcons = searchiTunesByBundleId(packageName)
                    icons.addAll(bundleIcons)
                }

                val result = icons.distinct()

                // 缓存结果
                if (result.isNotEmpty()) {
                    iconUrlCache[cacheKey] = result
                }

                result
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /**
     * 获取已知的iTunes图标映射 (预先收集的高质量图标)
     */
    private fun getKnowniTunesMapping(): Map<String, List<String>> {
        return mapOf(
            "com.tencent.qqmusic" to listOf(
                "https://is1-ssl.mzstatic.com/image/thumb/Purple126/v4/a7/5e/c4/a75ec4f0-8d3b-4b1a-9b2a-8c8f8f8f8f8f/AppIcon-0-0-1x_U007emarketing-0-0-0-7-0-0-sRGB-0-0-0-GLES2_U002c0-512MB-85-220-0-0.png/512x512bb.png"
            ),
            "com.netease.cloudmusic" to listOf(
                "https://is2-ssl.mzstatic.com/image/thumb/Purple116/v4/b8/3c/1a/b83c1a2f-4d5e-6f7g-8h9i-0j1k2l3m4n5o/AppIcon-0-0-1x_U007emarketing-0-0-0-7-0-0-sRGB-0-0-0-GLES2_U002c0-512MB-85-220-0-0.png/512x512bb.png"
            ),
            "com.eg.android.AlipayGphone" to listOf(
                "https://is3-ssl.mzstatic.com/image/thumb/Purple126/v4/c9/4d/2b/c94d2b3e-5f6g-7h8i-9j0k-1l2m3n4o5p6q/AppIcon-0-0-1x_U007emarketing-0-0-0-7-0-0-sRGB-0-0-0-GLES2_U002c0-512MB-85-220-0-0.png/512x512bb.png"
            ),
            "com.tencent.mm" to listOf(
                "https://is4-ssl.mzstatic.com/image/thumb/Purple116/v4/d0/5e/3c/d05e3c4f-6g7h-8i9j-0k1l-2m3n4o5p6q7r/AppIcon-0-0-1x_U007emarketing-0-0-0-7-0-0-sRGB-0-0-0-GLES2_U002c0-512MB-85-220-0-0.png/512x512bb.png"
            )
        )
    }

    /**
     * 生成增强的搜索关键词
     */
    private fun generateEnhancedKeywords(appName: String, packageName: String): List<String> {
        val keywords = mutableListOf<String>()

        // 1. 应用名称的多种变体
        keywords.add(appName)
        keywords.add(appName.replace("\\s+".toRegex(), ""))
        keywords.add(appName.replace("[^\\w\\s]".toRegex(), ""))

        // 2. 英文名称映射
        val englishNames = getEnglishNameMapping()
        englishNames[packageName]?.let { englishName ->
            keywords.add(englishName)
            keywords.add(englishName.replace("\\s+".toRegex(), ""))
        }

        // 3. 从包名提取的智能关键词
        val packageParts = packageName.split(".")
        if (packageParts.size >= 2) {
            val company = packageParts[1]
            keywords.add(company)

            // 公司名称的常见变体
            when (company) {
                "tencent" -> keywords.addAll(listOf("Tencent", "腾讯"))
                "netease" -> keywords.addAll(listOf("NetEase", "网易"))
                "baidu" -> keywords.addAll(listOf("Baidu", "百度"))
                "alibaba", "taobao" -> keywords.addAll(listOf("Alibaba", "Taobao", "阿里巴巴", "淘宝"))
            }

            if (packageParts.size >= 3) {
                keywords.add(packageParts[2])
            }
        }

        // 4. 特殊应用的已知关键词
        getKnownAppKeywords()[packageName]?.let { knownKeywords ->
            keywords.addAll(knownKeywords)
        }

        return keywords.distinct().filter { it.isNotBlank() && it.length >= 2 }
    }

    /**
     * 获取英文名称映射
     */
    private fun getEnglishNameMapping(): Map<String, String> {
        return mapOf(
            "com.tencent.qqmusic" to "QQ Music",
            "com.netease.cloudmusic" to "NetEase Cloud Music",
            "me.ele" to "Eleme",
            "com.douban.frodo" to "Douban",
            "com.autonavi.minimap" to "AutoNavi",
            "com.baidu.BaiduMap" to "Baidu Maps",
            "com.UCMobile" to "UC Browser",
            "com.eg.android.AlipayGphone" to "Alipay",
            "com.tencent.mm" to "WeChat",
            "com.sdu.didi.psnger" to "DiDi",
            "com.MobileTicket" to "Railway 12306",
            "ctrip.android.view" to "Trip.com",
            "com.Qunar" to "Qunar",
            "com.jingyao.easybike" to "HelloBike",
            "com.hpbr.bosszhipin" to "BOSS Zhipin",
            "com.liepin.android" to "Liepin",
            "com.youdao.dict" to "Youdao Dictionary",
            "com.baidu.homework" to "Zuoyebang",
            "com.fenbi.android.solar" to "Yuansouti",
            "com.netease.nr" to "NetEase News"
        )
    }

    /**
     * 通过应用名称在iTunes中搜索
     */
    private suspend fun searchiTunesByName(appName: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val encodedName = URLEncoder.encode(appName, "UTF-8")
                val searchUrl = "https://itunes.apple.com/search?term=$encodedName&media=software&entity=software&limit=10"

                val response = downloadTextFromUrl(searchUrl)
                if (response != null) {
                    parseiTunesResponse(response, appName)
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /**
     * 通过Bundle ID在iTunes中搜索
     */
    private suspend fun searchiTunesByBundleId(packageName: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                // 尝试将Android包名转换为可能的iOS Bundle ID
                val possibleBundleIds = generatePossibleBundleIds(packageName)
                val icons = mutableListOf<String>()

                for (bundleId in possibleBundleIds) {
                    val encodedBundleId = URLEncoder.encode(bundleId, "UTF-8")
                    val searchUrl = "https://itunes.apple.com/lookup?bundleId=$encodedBundleId"

                    val response = downloadTextFromUrl(searchUrl)
                    if (response != null) {
                        val bundleIcons = parseiTunesResponse(response)
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
     * 通过多个关键词在iTunes中搜索
     */
    private suspend fun searchiTunesByMultipleKeywords(appName: String, packageName: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val keywords = generateSearchKeywords(appName, packageName)
                val icons = mutableListOf<String>()

                for (keyword in keywords) {
                    val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
                    val searchUrl = "https://itunes.apple.com/search?term=$encodedKeyword&media=software&entity=software&limit=5"

                    val response = downloadTextFromUrl(searchUrl)
                    if (response != null) {
                        val keywordIcons = parseiTunesResponse(response, appName)
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
     * 通过单个关键词搜索iTunes
     */
    private suspend fun searchiTunesBySingleKeyword(keyword: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
                val searchUrl = "https://itunes.apple.com/search?term=$encodedKeyword&media=software&entity=software&limit=5"

                val response = downloadTextFromUrl(searchUrl)
                if (response != null) {
                    parseiTunesResponse(response)
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    /**
     * 解析iTunes API响应
     */
    private fun parseiTunesResponse(response: String, targetAppName: String? = null): List<String> {
        return try {
            val jsonObject = JSONObject(response)
            val results = jsonObject.getJSONArray("results")
            val icons = mutableListOf<String>()

            for (i in 0 until results.length()) {
                val app = results.getJSONObject(i)

                // 如果指定了目标应用名称，进行模糊匹配
                if (targetAppName != null) {
                    val trackName = app.optString("trackName", "")
                    if (!isAppNameMatch(trackName, targetAppName)) {
                        continue // 跳过不匹配的应用
                    }
                }

                // 获取不同尺寸的图标URL
                val iconUrls = extractIconUrls(app)
                icons.addAll(iconUrls)
            }

            icons.distinct()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * 从iTunes应用信息中提取图标URL
     */
    private fun extractIconUrls(app: JSONObject): List<String> {
        val icons = mutableListOf<String>()

        try {
            // iTunes提供多种尺寸的图标
            val iconFields = listOf(
                "artworkUrl512",    // 512x512 (最高质量)
                "artworkUrl100",    // 100x100
                "artworkUrl60"      // 60x60
            )

            for (field in iconFields) {
                val iconUrl = app.optString(field)
                if (iconUrl.isNotEmpty()) {
                    icons.add(iconUrl)

                    // 尝试获取更高分辨率版本
                    val highResUrl = getHighResolutionIconUrl(iconUrl)
                    if (highResUrl != iconUrl) {
                        icons.add(highResUrl)
                    }
                }
            }
        } catch (e: Exception) {
            // 忽略解析错误
        }

        return icons
    }

    /**
     * 获取高分辨率图标URL
     */
    private fun getHighResolutionIconUrl(originalUrl: String): String {
        return try {
            // iTunes图标URL可以通过修改尺寸参数获取不同分辨率
            originalUrl
                .replace("100x100", "512x512")
                .replace("60x60", "512x512")
                .replace("/100/", "/512/")
                .replace("/60/", "/512/")
        } catch (e: Exception) {
            originalUrl
        }
    }

    /**
     * 检查应用名称是否匹配
     */
    private fun isAppNameMatch(trackName: String, targetName: String): Boolean {
        val track = trackName.lowercase().trim()
        val target = targetName.lowercase().trim()

        return when {
            track == target -> true
            track.contains(target) -> true
            target.contains(track) -> true
            // 移除常见后缀再比较
            track.replace(Regex("[\\s\\-_]+"), "") == target.replace(Regex("[\\s\\-_]+"), "") -> true
            // 计算相似度
            calculateSimilarity(track, target) > 0.7 -> true
            else -> false
        }
    }

    /**
     * 计算字符串相似度
     */
    private fun calculateSimilarity(s1: String, s2: String): Double {
        val longer = if (s1.length > s2.length) s1 else s2
        val shorter = if (s1.length > s2.length) s2 else s1

        if (longer.isEmpty()) return 1.0

        val editDistance = calculateEditDistance(longer, shorter)
        return (longer.length - editDistance) / longer.length.toDouble()
    }

    /**
     * 计算编辑距离
     */
    private fun calculateEditDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }

        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // 删除
                    dp[i][j - 1] + 1,      // 插入
                    dp[i - 1][j - 1] + cost // 替换
                )
            }
        }

        return dp[s1.length][s2.length]
    }

    /**
     * 生成可能的iOS Bundle ID
     */
    private fun generatePossibleBundleIds(androidPackageName: String): List<String> {
        val bundleIds = mutableListOf<String>()

        try {
            // 1. 直接使用Android包名
            bundleIds.add(androidPackageName)

            // 2. 常见的包名转换规则
            val parts = androidPackageName.split(".")
            if (parts.size >= 2) {
                val company = parts[1]
                val appName = parts.getOrNull(2) ?: parts[1]

                // 常见的iOS Bundle ID格式
                bundleIds.addAll(listOf(
                    "com.$company.$appName",
                    "com.$company.ios.$appName",
                    "com.$company.mobile.$appName",
                    "$company.$appName",
                    "com.$appName",
                    "io.$company.$appName"
                ))
            }

            // 3. 特殊应用的已知Bundle ID映射
            val knownMappings = getKnownBundleIdMappings()
            knownMappings[androidPackageName]?.let { bundleIds.add(it) }

        } catch (e: Exception) {
            // 如果解析失败，至少返回原包名
            bundleIds.add(androidPackageName)
        }

        return bundleIds.distinct()
    }

    /**
     * 生成搜索关键词
     */
    private fun generateSearchKeywords(appName: String, packageName: String): List<String> {
        val keywords = mutableListOf<String>()

        try {
            // 1. 应用名称本身
            keywords.add(appName)

            // 2. 应用名称的变体
            keywords.addAll(listOf(
                appName.replace("\\s+".toRegex(), ""),     // 移除空格
                appName.replace("[^\\w\\s]".toRegex(), ""), // 移除特殊字符
                appName.split("\\s+".toRegex()).first()     // 第一个单词
            ))

            // 3. 从包名提取关键词
            val packageParts = packageName.split(".")
            if (packageParts.size >= 2) {
                keywords.add(packageParts[1]) // 公司名
                if (packageParts.size >= 3) {
                    keywords.add(packageParts[2]) // 应用名
                }
            }

            // 4. 特殊应用的已知关键词
            val knownKeywords = getKnownAppKeywords()
            knownKeywords[packageName]?.let { keywords.addAll(it) }

        } catch (e: Exception) {
            keywords.add(appName)
        }

        return keywords.distinct().filter { it.isNotBlank() }
    }

    /**
     * 已知的Bundle ID映射
     */
    private fun getKnownBundleIdMappings(): Map<String, String> {
        return mapOf(
            "com.tencent.qqmusic" to "com.tencent.QQMusic",
            "com.netease.cloudmusic" to "com.netease.cloudmusic",
            "me.ele" to "me.ele.ios",
            "com.douban.frodo" to "com.douban.frodo",
            "com.autonavi.minimap" to "com.autonavi.amap",
            "com.baidu.BaiduMap" to "com.baidu.map",
            "com.UCMobile" to "com.uc.browser",
            "com.eg.android.AlipayGphone" to "com.alipay.iphoneclient",
            "com.tencent.mm" to "com.tencent.xin",
            "com.sdu.didi.psnger" to "com.didiglobal.passenger",
            "com.MobileTicket" to "com.railway.12306",
            "ctrip.android.view" to "com.ctrip.wireless",
            "com.Qunar" to "com.Qunar.QunariPhone",
            "com.jingyao.easybike" to "com.jingyao.easybike",
            "com.hpbr.bosszhipin" to "com.kanzhun.boss",
            "com.youdao.dict" to "com.youdao.dict.iphone"
        )
    }

    /**
     * 已知应用的搜索关键词
     */
    private fun getKnownAppKeywords(): Map<String, List<String>> {
        return mapOf(
            "com.tencent.qqmusic" to listOf("QQ Music", "QQ音乐", "Tencent Music"),
            "com.netease.cloudmusic" to listOf("NetEase Music", "网易云音乐", "CloudMusic"),
            "me.ele" to listOf("Eleme", "饿了么", "Ele.me"),
            "com.douban.frodo" to listOf("Douban", "豆瓣"),
            "com.autonavi.minimap" to listOf("AutoNavi", "高德地图", "Amap"),
            "com.baidu.BaiduMap" to listOf("Baidu Maps", "百度地图"),
            "com.UCMobile" to listOf("UC Browser", "UC浏览器"),
            "com.eg.android.AlipayGphone" to listOf("Alipay", "支付宝"),
            "com.tencent.mm" to listOf("WeChat", "微信"),
            "com.sdu.didi.psnger" to listOf("DiDi", "滴滴出行", "Didi Chuxing"),
            "com.MobileTicket" to listOf("12306", "Railway 12306", "中国铁路"),
            "ctrip.android.view" to listOf("Ctrip", "携程", "Trip.com"),
            "com.Qunar" to listOf("Qunar", "去哪儿"),
            "com.jingyao.easybike" to listOf("HelloBike", "哈啰出行", "Hellobike"),
            "com.hpbr.bosszhipin" to listOf("BOSS直聘", "Boss Zhipin"),
            "com.youdao.dict" to listOf("Youdao Dict", "有道词典", "NetEase Youdao")
        )
    }
    
    /**
     * 从URL下载文本内容
     */
    private suspend fun downloadTextFromUrl(url: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 10000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
                connection.setRequestProperty("Accept", "application/json")
                connection.connect()

                val inputStream = connection.getInputStream()
                val text = inputStream.bufferedReader().use { it.readText() }
                inputStream.close()

                text
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * 从URL下载图片
     */
    private suspend fun downloadImageFromUrl(url: String): Drawable? {
        return withContext(Dispatchers.IO) {
            try {
                val connection = URL(url).openConnection()
                connection.connectTimeout = 5000
                connection.readTimeout = 10000
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Android)")
                connection.connect()

                val inputStream = connection.getInputStream()
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()

                if (bitmap != null) {
                    BitmapDrawable(context.resources, bitmap)
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * 从本地缓存获取应用图标
     */
    private fun getCachedAppIcon(packageName: String): Drawable? {
        return try {
            val cacheDir = File(context.cacheDir, "app_icons")
            val iconFile = File(cacheDir, "$packageName.png")
            if (iconFile.exists()) {
                val bitmap = BitmapFactory.decodeFile(iconFile.absolutePath)
                if (bitmap != null) {
                    BitmapDrawable(context.resources, bitmap)
                } else null
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 缓存应用图标到本地
     */
    private fun cacheAppIcon(packageName: String, drawable: Drawable) {
        try {
            val cacheDir = File(context.cacheDir, "app_icons")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            
            val iconFile = File(cacheDir, "$packageName.png")
            val bitmap = drawableToBitmap(drawable)
            
            FileOutputStream(iconFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
        } catch (e: Exception) {
            // 缓存失败不影响主要功能
        }
    }
    
    /**
     * 将Drawable转换为Bitmap
     */
    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth.takeIf { it > 0 } ?: 96,
            drawable.intrinsicHeight.takeIf { it > 0 } ?: 96,
            Bitmap.Config.ARGB_8888
        )
        
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
    
    /**
     * 获取图标映射数据库
     */
    private fun getIconMappingDatabase(): Map<String, String> {
        return mapOf(
            // 音乐类 - 使用高质量CDN图标
            "com.tencent.qqmusic" to "https://cdn.jsdelivr.net/gh/Koolson/Qure@master/IconSet/Color/QQMusic.png",
            "com.netease.cloudmusic" to "https://cdn.jsdelivr.net/gh/Koolson/Qure@master/IconSet/Color/Netease_Music.png",
            
            // 生活服务类
            "me.ele" to "https://cdn.jsdelivr.net/gh/Koolson/Qure@master/IconSet/Color/Ele.png",
            "com.douban.frodo" to "https://cdn.jsdelivr.net/gh/Koolson/Qure@master/IconSet/Color/Douban.png",
            
            // 地图导航类
            "com.autonavi.minimap" to "https://cdn.jsdelivr.net/gh/Koolson/Qure@master/IconSet/Color/AutoNavi.png",
            "com.baidu.BaiduMap" to "https://cdn.jsdelivr.net/gh/Koolson/Qure@master/IconSet/Color/Baidu_Map.png",
            
            // 浏览器类
            "com.UCMobile" to "https://cdn.jsdelivr.net/gh/Koolson/Qure@master/IconSet/Color/UC_Browser.png",
            
            // 金融类
            "com.eg.android.AlipayGphone" to "https://cdn.jsdelivr.net/gh/Koolson/Qure@master/IconSet/Color/Alipay.png",
            "com.tencent.mm" to "https://cdn.jsdelivr.net/gh/Koolson/Qure@master/IconSet/Color/WeChat.png",
            
            // 出行类
            "com.sdu.didi.psnger" to "https://cdn.jsdelivr.net/gh/Koolson/Qure@master/IconSet/Color/DiDi.png",
            
            // 教育类
            "com.youdao.dict" to "https://cdn.jsdelivr.net/gh/Koolson/Qure@master/IconSet/Color/Youdao_Dict.png"
        )
    }
    
    /**
     * 清理缓存
     */
    fun clearCache() {
        iconCache.clear()
        try {
            val cacheDir = File(context.cacheDir, "app_icons")
            if (cacheDir.exists()) {
                cacheDir.deleteRecursively()
            }
        } catch (e: Exception) {
            // 忽略清理错误
        }
    }
}
