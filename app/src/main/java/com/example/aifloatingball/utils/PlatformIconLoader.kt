package com.example.aifloatingball.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.example.aifloatingball.R
import com.example.aifloatingball.manager.AppStoreIconManager
import com.example.aifloatingball.model.AppSearchConfig
import kotlinx.coroutines.*
import java.util.concurrent.Executors

/**
 * 平台图标加载器
 * 参考软件tab的图标获取方式，确保图标精准显示和合适缩放
 */
object PlatformIconLoader {
    
    private const val TAG = "PlatformIconLoader"
    
    private val memoryCache: LruCache<String, Bitmap>
    private val executor = Executors.newFixedThreadPool(3)
    private val uiHandler = Handler(Looper.getMainLooper())
    
    init {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        val cacheSize = maxMemory / 16 // 使用更小的缓存空间
        memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }
        
        // 预加载常用网站的favicon
        CoroutineScope(Dispatchers.IO).launch {
            FaviconLoader.preloadCommonFavicons()
        }
    }
    
    /**
     * 平台图标配置
     */
    private val platformIconConfigs = mapOf(
        "抖音" to PlatformIconConfig(
            resourceId = R.drawable.ic_douyin,
            iconUrls = listOf(
                "https://lf1-cdn-tos.bytescm.com/obj/static/douyin_web/favicon.ico",
                "https://www.douyin.com/favicon.ico"
            ),
            targetSize = 144
        ),
        "小红书" to PlatformIconConfig(
            resourceId = R.drawable.ic_xiaohongshu,
            iconUrls = listOf(
                "https://www.xiaohongshu.com/favicon.ico",
                "https://sns-webpic-qc.xhscdn.com/favicon.ico"
            ),
            targetSize = 144
        ),
        "YouTube" to PlatformIconConfig(
            resourceId = R.drawable.ic_youtube,
            iconUrls = listOf(
                "https://www.youtube.com/favicon.ico",
                "https://www.youtube.com/s/desktop/favicon.ico"
            ),
            targetSize = 144
        ),
        "哔哩哔哩" to PlatformIconConfig(
            resourceId = R.drawable.ic_bilibili,
            iconUrls = listOf(
                "https://www.bilibili.com/favicon.ico",
                "https://static.hdslb.com/images/favicon.ico"
            ),
            targetSize = 144
        ),
        "快手" to PlatformIconConfig(
            resourceId = R.drawable.ic_kuaishou,
            iconUrls = listOf(
                "https://www.kuaishou.com/favicon.ico",
                "https://static.yximgs.com/favicon.ico"
            ),
            targetSize = 144
        ),
        "微博" to PlatformIconConfig(
            resourceId = R.drawable.ic_weibo,
            iconUrls = listOf(
                "https://weibo.com/favicon.ico",
                "https://www.weibo.com/favicon.ico"
            ),
            targetSize = 144
        ),
        "豆瓣" to PlatformIconConfig(
            resourceId = R.drawable.ic_douban,
            iconUrls = listOf(
                "https://www.douban.com/favicon.ico",
                "https://img3.doubanio.com/favicon.ico"
            ),
            targetSize = 144
        )
    )
    
    /**
     * 平台图标配置数据类
     */
    data class PlatformIconConfig(
        val resourceId: Int,
        val iconUrls: List<String>,
        val targetSize: Int
    )
    
    /**
     * 加载平台图标
     */
    /**
     * 加载平台图标
     * 统一使用与软件tab相同的图标获取和处理方式
     */
    fun loadPlatformIcon(imageView: ImageView, platformName: String, context: Context) {
        // 统一使用动态应用图标加载方式，确保所有图标都经过IconProcessor处理
        loadDynamicAppIcon(imageView, platformName, context)
    }
    
    /**
     * 加载预设平台图标
     */
    private fun loadPresetPlatformIcon(imageView: ImageView, platformName: String, config: PlatformIconConfig) {
        val cacheKey = "platform_$platformName"
        imageView.tag = cacheKey
        
        // 检查内存缓存
        val cachedBitmap = memoryCache.get(cacheKey)
        if (cachedBitmap != null) {
            imageView.setImageBitmap(cachedBitmap)
            return
        }
        
        // 设置默认图标
        imageView.setImageResource(config.resourceId)
        
        // 异步加载网络图标
        executor.execute {
            var bitmap: Bitmap? = null
            
            // 尝试多个图标URL
            for (iconUrl in config.iconUrls) {
                try {
                    bitmap = loadBitmapFromUrl(iconUrl, config.targetSize)
                    if (bitmap != null) {
                        break
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Failed to load platform icon from $iconUrl: ${e.message}")
                    continue
                }
            }
            
            // 更新UI
            uiHandler.post {
                if (imageView.tag == cacheKey) {
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap)
                        memoryCache.put(cacheKey, bitmap)
                    }
                }
            }
        }
    }
    
    /**
     * 加载动态应用图标
     * 使用与软件tab相同的图标获取和处理方式
     * 支持预设平台和动态应用
     */
    private fun loadDynamicAppIcon(imageView: ImageView, appName: String, context: Context) {
        val cacheKey = "app_$appName"
        imageView.tag = cacheKey
        
        // 检查内存缓存
        val cachedBitmap = memoryCache.get(cacheKey)
        if (cachedBitmap != null) {
            imageView.setImageBitmap(cachedBitmap)
            return
        }
        
        // 设置默认图标
        imageView.setImageResource(R.drawable.ic_link)
        
        // 使用协程异步加载应用图标
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // 1. 优先使用已安装应用的真实图标
                val packageManager = context.packageManager
                
                // 根据应用名称获取包名
                val packageName = getPackageNameByAppName(appName, context)
                if (packageName != null) {
                    val packageInfo = packageManager.getPackageInfo(packageName, 0)
                    val realIcon = packageInfo.applicationInfo.loadIcon(packageManager)
                    
                    // 使用IconProcessor处理图标，与软件tab保持一致
                    val iconProcessor = IconProcessor(context)
                    val processedIcon = iconProcessor.processIcon(realIcon, IconProcessor.IconStyle.ROUNDED_SQUARE)
                    
                    if (processedIcon != null) {
                        // 将Drawable转换为Bitmap并缓存
                        val bitmap = drawableToBitmap(processedIcon)
                        if (bitmap != null) {
                            memoryCache.put(cacheKey, bitmap)
                            if (imageView.tag == cacheKey) {
                                imageView.setImageBitmap(bitmap)
                            }
                        }
                    } else {
                        // 如果IconProcessor处理失败，使用原始图标
                        val bitmap = drawableToBitmap(realIcon)
                        if (bitmap != null) {
                            val scaledBitmap = scaleBitmap(bitmap, 144)
                            memoryCache.put(cacheKey, scaledBitmap)
                            if (imageView.tag == cacheKey) {
                                imageView.setImageBitmap(scaledBitmap)
                            }
                        }
                    }
                } else {
                    // 如果找不到包名，尝试使用预设图标资源
                    tryPresetIconResource(imageView, appName, context, cacheKey)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load app icon for $appName: ${e.message}")
                
                // 2. 如果本地图标加载失败，尝试从App Store获取
                val packageName = getPackageNameByAppName(appName, context)
                if (packageName != null) {
                    try {
                        val appStoreManager = AppStoreIconManager.getInstance(context)
                        appStoreManager.getAppStoreIcon(
                            packageName = packageName,
                            appName = appName,
                            displayContext = com.example.aifloatingball.config.IconResolutionConfig.DisplayContext.APP_SEARCH_GRID
                        ) { appStoreIcon ->
                            if (appStoreIcon != null) {
                                // 使用IconProcessor处理App Store图标
                                val iconProcessor = IconProcessor(context)
                                val processedIcon = iconProcessor.processIcon(appStoreIcon, IconProcessor.IconStyle.ROUNDED_SQUARE)
                                
                                if (processedIcon != null) {
                                    val bitmap = drawableToBitmap(processedIcon)
                                    if (bitmap != null) {
                                        memoryCache.put(cacheKey, bitmap)
                                        if (imageView.tag == cacheKey) {
                                            imageView.setImageBitmap(bitmap)
                                        }
                                    }
                                }
                            } else {
                                // 3. 如果App Store也失败，尝试使用预设图标资源
                                tryPresetIconResource(imageView, appName, context, cacheKey)
                            }
                        }
                    } catch (e2: Exception) {
                        Log.e(TAG, "Failed to load App Store icon for $appName: ${e2.message}")
                        // 尝试使用预设图标资源
                        tryPresetIconResource(imageView, appName, context, cacheKey)
                    }
                } else {
                    // 尝试使用预设图标资源
                    tryPresetIconResource(imageView, appName, context, cacheKey)
                }
            }
        }
    }
    
    /**
     * 根据应用名称获取包名
     */
    private fun getPackageNameByAppName(appName: String, context: Context): String? {
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
        
        // AI应用包名映射
        val aiAppPackages = mapOf(
            "DeepSeek" to "com.deepseek.chat",
            "豆包" to "com.larus.nova",
            "ChatGPT" to "com.openai.chatgpt",
            "Kimi" to "com.moonshot.kimichat",
            "腾讯元宝" to "com.tencent.hunyuan.app.chat",
            "讯飞星火" to "com.iflytek.spark",
            "智谱清言" to "com.zhipuai.qingyan",
            "通义千问" to "com.aliyun.tongyi",
            "文小言" to "com.baidu.newapp",
            "Grok" to "ai.x.grok",
            "Perplexity" to "ai.perplexity.app.android",
            "Manus" to "com.manus.im.app",
            "秘塔AI搜索" to "com.metaso",
            "Poe" to "com.poe.android",
            "IMA" to "com.tencent.ima",
            "纳米AI" to "com.qihoo.namiso",
            "Gemini" to "com.google.android.apps.gemini",
            "Copilot" to "com.microsoft.copilot"
        )
        
        // 先检查预设平台
        presetPlatforms[appName]?.let { packageName ->
            return packageName
        }
        
        // 检查AI应用
        aiAppPackages[appName]?.let { packageName ->
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
     * 尝试使用预设图标资源
     */
    private fun tryPresetIconResource(imageView: ImageView, appName: String, context: Context, cacheKey: String) {
        val config = platformIconConfigs[appName]
        if (config != null) {
            try {
                val presetIcon = ContextCompat.getDrawable(context, config.resourceId)
                if (presetIcon != null) {
                    val iconProcessor = IconProcessor(context)
                    val processedIcon = iconProcessor.processIcon(presetIcon, IconProcessor.IconStyle.ROUNDED_SQUARE)
                    
                    if (processedIcon != null) {
                        val bitmap = drawableToBitmap(processedIcon)
                        if (bitmap != null) {
                            memoryCache.put(cacheKey, bitmap)
                            if (imageView.tag == cacheKey) {
                                imageView.setImageBitmap(bitmap)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load preset icon for $appName: ${e.message}")
                // 最后尝试使用FaviconLoader
                tryFaviconLoader(imageView, appName)
            }
        } else {
            // 尝试使用FaviconLoader
            tryFaviconLoader(imageView, appName)
        }
    }
    
    /**
     * 尝试使用FaviconLoader获取图标
     */
    private fun tryFaviconLoader(imageView: ImageView, appName: String) {
        try {
            // 根据应用名称生成对应的网站URL
            val websiteUrl = generateWebsiteUrl(appName)
            if (websiteUrl != null) {
                FaviconLoader.loadFavicon(imageView, websiteUrl)
                Log.d(TAG, "Using FaviconLoader for $appName: $websiteUrl")
            } else {
                Log.d(TAG, "No website URL found for $appName, keeping default icon")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load favicon for $appName: ${e.message}")
            // 保持默认图标
        }
    }
    
    /**
     * 根据应用名称生成对应的网站URL
     */
    private fun generateWebsiteUrl(appName: String): String? {
        return when {
            appName.contains("微信") -> "https://weixin.sogou.com"
            appName.contains("淘宝") -> "https://s.taobao.com"
            appName.contains("京东") -> "https://search.jd.com"
            appName.contains("知乎") -> "https://www.zhihu.com"
            appName.contains("拼多多") -> "https://mobile.yangkeduo.com"
            appName.contains("天猫") -> "https://list.tmall.com"
            appName.contains("QQ") -> "https://www.qq.com"
            appName.contains("抖音") -> "https://www.douyin.com"
            appName.contains("小红书") -> "https://www.xiaohongshu.com"
            appName.contains("YouTube") -> "https://www.youtube.com"
            appName.contains("哔哩哔哩") -> "https://www.bilibili.com"
            appName.contains("快手") -> "https://www.kuaishou.com"
            appName.contains("微博") -> "https://weibo.com"
            appName.contains("豆瓣") -> "https://www.douban.com"
            // AI应用网站URL
            appName.contains("DeepSeek") -> "https://chat.deepseek.com"
            appName.contains("豆包") -> "https://www.doubao.com"
            appName.contains("ChatGPT") -> "https://chat.openai.com"
            appName.contains("Kimi") -> "https://kimi.moonshot.cn"
            appName.contains("腾讯元宝") -> "https://hunyuan.tencent.com"
            appName.contains("讯飞星火") -> "https://xinghuo.xfyun.cn"
            appName.contains("智谱清言") -> "https://chatglm.cn"
            appName.contains("通义千问") -> "https://tongyi.aliyun.com"
            appName.contains("文小言") -> "https://xiaoyi.baidu.com"
            appName.contains("Grok") -> "https://grok.x.ai"
            appName.contains("Perplexity") -> "https://www.perplexity.ai"
            appName.contains("Manus") -> "https://manus.ai"
            appName.contains("秘塔AI搜索") -> "https://metaso.cn"
            appName.contains("Poe") -> "https://poe.com"
            appName.contains("IMA") -> "https://ima.ai"
            appName.contains("纳米AI") -> "https://nano.ai"
            appName.contains("Gemini") -> "https://gemini.google.com"
            appName.contains("Copilot") -> "https://copilot.microsoft.com"
            else -> null
        }
    }
    
    /**
     * 将Drawable转换为Bitmap
     */
    private fun drawableToBitmap(drawable: Drawable): Bitmap? {
        return try {
            if (drawable is BitmapDrawable) {
                drawable.bitmap
            } else {
                val bitmap = Bitmap.createBitmap(
                    drawable.intrinsicWidth,
                    drawable.intrinsicHeight,
                    Bitmap.Config.ARGB_8888
                )
                val canvas = Canvas(bitmap)
                drawable.setBounds(0, 0, canvas.width, canvas.height)
                drawable.draw(canvas)
                bitmap
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert drawable to bitmap: ${e.message}")
            null
        }
    }
    
    /**
     * 从URL加载Bitmap
     */
    private fun loadBitmapFromUrl(url: String, targetSize: Int): Bitmap? {
        return try {
            val connection = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 3000
            connection.readTimeout = 3000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            connection.connect()
            
            if (connection.responseCode == 200) {
                val inputStream = connection.inputStream
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                
                if (bitmap != null && bitmap.width > 0 && bitmap.height > 0) {
                    // 缩放到目标大小
                    return scaleBitmap(bitmap, targetSize)
                }
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error loading bitmap from $url", e)
            null
        }
    }
    
    /**
     * 缩放Bitmap到指定大小
     */
    private fun scaleBitmap(bitmap: Bitmap, targetSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        // 如果已经是目标大小，直接返回
        if (width == targetSize && height == targetSize) {
            return bitmap
        }
        
        // 计算缩放比例，保持宽高比
        val scale = minOf(targetSize.toFloat() / width, targetSize.toFloat() / height)
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * 预加载所有平台图标
     */
    fun preloadAllPlatformIcons() {
        platformIconConfigs.keys.forEach { platformName ->
            executor.execute {
                val config = platformIconConfigs[platformName] ?: return@execute
                val cacheKey = "platform_$platformName"
                
                // 如果已经缓存，跳过
                if (memoryCache.get(cacheKey) != null) {
                    return@execute
                }
                
                // 尝试加载网络图标
                for (iconUrl in config.iconUrls) {
                    try {
                        val bitmap = loadBitmapFromUrl(iconUrl, config.targetSize)
                        if (bitmap != null) {
                            memoryCache.put(cacheKey, bitmap)
                            Log.d(TAG, "Preloaded icon for platform: $platformName")
                            break
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "Failed to preload icon for $platformName from $iconUrl")
                        continue
                    }
                }
            }
        }
    }
    
    /**
     * 清理缓存
     */
    fun clearCache() {
        memoryCache.evictAll()
    }
}
