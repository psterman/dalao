package com.example.aifloatingball.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.core.graphics.drawable.RoundedBitmapDrawable
import com.example.aifloatingball.R
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest

class FaviconManager private constructor(private val context: Context) {
    companion object {
        private const val TAG = "FaviconManager"
        private const val CACHE_SIZE = 20 * 1024 * 1024 // 20MB
        private const val FAVICON_CACHE_DIR = "favicons"
        
        @Volatile
        private var instance: FaviconManager? = null
        
        fun getInstance(context: Context): FaviconManager {
            return instance ?: synchronized(this) {
                instance ?: FaviconManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val memoryCache = object : LruCache<String, Bitmap>(CACHE_SIZE) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount
        }
    }
    
    private val cacheDir: File by lazy {
        File(context.cacheDir, FAVICON_CACHE_DIR).also { it.mkdirs() }
    }
    
    /**
     * 获取网站favicon图标
     * 
     * @param domain 域名
     * @param callback 回调函数，返回图标Bitmap
     */
    fun getFavicon(domain: String, callback: (Bitmap?) -> Unit) {
        // 先从内存缓存获取
        val cachedBitmap = memoryCache.get(domain)
        if (cachedBitmap != null) {
            callback(cachedBitmap)
            return
        }
        
        // 从文件缓存获取
        val cacheFile = getFaviconFile(domain)
        if (cacheFile.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(cacheFile.absolutePath)
                if (bitmap != null) {
                    memoryCache.put(domain, bitmap)
                    callback(bitmap)
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load favicon from cache: $domain", e)
            }
        }
        
        // 尝试从资源获取
        val resourceBitmap = getResourceFavicon(domain)
        if (resourceBitmap != null) {
            memoryCache.put(domain, resourceBitmap)
            callback(resourceBitmap)
            saveFaviconToCache(domain, resourceBitmap)
            return
        }
        
        // 从网络获取
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = downloadFavicon(domain)
                if (bitmap != null) {
                    // 保存到缓存
                    saveFaviconToCache(domain, bitmap)
                    memoryCache.put(domain, bitmap)
                    withContext(Dispatchers.Main) {
                        callback(bitmap)
                    }
                } else {
                    // 使用默认图标
                    val defaultBitmap = getDefaultFavicon()
                    memoryCache.put(domain, defaultBitmap)
                    withContext(Dispatchers.Main) {
                        callback(defaultBitmap)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download favicon: $domain", e)
                // 使用默认图标
                val defaultBitmap = getDefaultFavicon()
                withContext(Dispatchers.Main) {
                    callback(defaultBitmap)
                }
            }
        }
    }
    
    /**
     * 从资源中获取图标
     * 
     * @param domain 域名
     * @return 从资源加载的Bitmap，如果不存在则返回null
     */
    private fun getResourceFavicon(domain: String): Bitmap? {
        // 根据域名获取资源名称
        val resourceName = when {
            domain.contains("baidu.com") -> R.drawable.ic_baidu
            domain.contains("google.com") -> R.drawable.ic_google
            domain.contains("bing.com") -> R.drawable.ic_bing
            domain.contains("sogou.com") -> R.drawable.ic_sogou
            domain.contains("so.com") -> R.drawable.ic_360
            domain.contains("toutiao.com") -> R.drawable.ic_search
            domain.contains("zhihu.com") -> R.drawable.ic_zhihu
            domain.contains("bilibili.com") -> R.drawable.ic_bilibili
            domain.contains("douban.com") -> R.drawable.ic_douban
            domain.contains("weibo.com") -> R.drawable.ic_weibo
            domain.contains("taobao.com") -> R.drawable.ic_taobao
            domain.contains("jd.com") -> R.drawable.ic_jd
            domain.contains("douyin.com") -> R.drawable.ic_douyin
            domain.contains("xiaohongshu.com") -> R.drawable.ic_xiaohongshu
            domain.contains("qq.com") -> R.drawable.ic_qq
            domain.contains("openai.com") -> R.drawable.ic_chatgpt
            domain.contains("claude.ai") -> R.drawable.ic_claude
            domain.contains("gemini.google.com") -> R.drawable.ic_gemini
            domain.contains("zhipuai.cn") -> R.drawable.ic_zhipu
            domain.contains("aliyun.com") -> R.drawable.ic_qianwen
            domain.contains("xfyun.cn") -> R.drawable.ic_xinghuo
            domain.contains("perplexity.ai") -> R.drawable.ic_perplexity
            else -> null
        } ?: return null

        return try {
            BitmapFactory.decodeResource(context.resources, resourceName)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load favicon from resources: $domain", e)
            null
        }
    }
    
    /**
     * 获取默认favicon图标
     */
    private fun getDefaultFavicon(): Bitmap {
        return BitmapFactory.decodeResource(context.resources, R.drawable.ic_search)
    }
    
    /**
     * 从网络下载favicon图标
     * 
     * @param domain 域名
     * @return 下载的Bitmap，如果下载失败则返回null
     */
    private suspend fun downloadFavicon(domain: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                // 尝试不同的 favicon 服务，按顺序尝试
                val services = listOf(
                    "https://www.google.com/s2/favicons?domain=$domain&sz=64",
                    "https://icon.horse/icon/$domain?size=large",
                    "https://t2.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON&fallback_opts=TYPE,SIZE,URL&url=https://$domain&size=64",
                    "https://api.faviconkit.com/$domain/144",
                    "https://favicon.yandex.net/favicon/$domain",
                    "https://$domain/favicon.ico",
                    "https://www.$domain/favicon.ico"
                )
                
                for (service in services) {
                    try {
                        val url = URL(service)
                        val connection = url.openConnection()
                        connection.connectTimeout = 3000 // 3秒超时
                        connection.readTimeout = 3000
                        val inputStream = connection.getInputStream()
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream.close()
                        if (bitmap != null && bitmap.width > 1 && bitmap.height > 1) {
                            return@withContext bitmap
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to download favicon from $service: $domain", e)
                        continue
                    }
                }
                null
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download favicon: $domain", e)
                null
            }
        }
    }
    
    /**
     * 将favicon保存到缓存
     * 
     * @param domain 域名
     * @param bitmap 图标Bitmap
     */
    private fun saveFaviconToCache(domain: String, bitmap: Bitmap) {
        try {
            val file = getFaviconFile(domain)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save favicon to cache: $domain", e)
        }
    }
    
    /**
     * 获取缓存文件路径
     * 
     * @param domain 域名
     * @return 缓存文件
     */
    private fun getFaviconFile(domain: String): File {
        val hash = MessageDigest.getInstance("MD5")
            .digest(domain.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$hash.png")
    }
    
    /**
     * 创建圆角图标
     * 
     * @param bitmap 原始图标Bitmap
     * @param cornerRadius 圆角半径
     * @return 圆角图标Drawable
     */
    fun createRoundedIcon(bitmap: Bitmap, cornerRadius: Float = 8f): RoundedBitmapDrawable {
        val roundedBitmapDrawable = RoundedBitmapDrawableFactory.create(context.resources, bitmap)
        roundedBitmapDrawable.cornerRadius = cornerRadius
        return roundedBitmapDrawable
    }
    
    /**
     * 清除缓存
     */
    fun clearCache() {
        memoryCache.evictAll()
        cacheDir.listFiles()?.forEach { it.delete() }
    }
} 