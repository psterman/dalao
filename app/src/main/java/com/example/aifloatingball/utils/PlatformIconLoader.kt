package com.example.aifloatingball.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.example.aifloatingball.R
import java.util.concurrent.Executors

/**
 * 平台图标加载器
 * 参考系统favicon加载机制，确保图标精准显示和合适缩放
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
            targetSize = 36
        ),
        "小红书" to PlatformIconConfig(
            resourceId = R.drawable.ic_xiaohongshu,
            iconUrls = listOf(
                "https://www.xiaohongshu.com/favicon.ico",
                "https://sns-webpic-qc.xhscdn.com/favicon.ico"
            ),
            targetSize = 36
        ),
        "YouTube" to PlatformIconConfig(
            resourceId = R.drawable.ic_youtube,
            iconUrls = listOf(
                "https://www.youtube.com/favicon.ico",
                "https://www.youtube.com/s/desktop/favicon.ico"
            ),
            targetSize = 36
        ),
        "哔哩哔哩" to PlatformIconConfig(
            resourceId = R.drawable.ic_bilibili,
            iconUrls = listOf(
                "https://www.bilibili.com/favicon.ico",
                "https://static.hdslb.com/images/favicon.ico"
            ),
            targetSize = 36
        ),
        "快手" to PlatformIconConfig(
            resourceId = R.drawable.ic_kuaishou,
            iconUrls = listOf(
                "https://www.kuaishou.com/favicon.ico",
                "https://static.yximgs.com/favicon.ico"
            ),
            targetSize = 36
        ),
        "微博" to PlatformIconConfig(
            resourceId = R.drawable.ic_weibo,
            iconUrls = listOf(
                "https://weibo.com/favicon.ico",
                "https://www.weibo.com/favicon.ico"
            ),
            targetSize = 36
        ),
        "豆瓣" to PlatformIconConfig(
            resourceId = R.drawable.ic_douban,
            iconUrls = listOf(
                "https://www.douban.com/favicon.ico",
                "https://img3.doubanio.com/favicon.ico"
            ),
            targetSize = 36
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
    fun loadPlatformIcon(imageView: ImageView, platformName: String, context: Context) {
        val config = platformIconConfigs[platformName]
        if (config == null) {
            Log.e(TAG, "Unknown platform: $platformName")
            imageView.setImageResource(R.drawable.ic_link)
            return
        }
        
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
            
            if (bitmap != null) {
                memoryCache.put(cacheKey, bitmap)
                uiHandler.post {
                    if (imageView.tag == cacheKey) {
                        imageView.setImageBitmap(bitmap)
                    }
                }
            } else {
                Log.d(TAG, "Using default icon for platform: $platformName")
            }
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
    fun preloadAllPlatformIcons(context: Context) {
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
