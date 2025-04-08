package com.example.aifloatingball.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import android.util.LruCache
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
                    withContext(Dispatchers.Main) {
                        callback(null)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download favicon: $domain", e)
                withContext(Dispatchers.Main) {
                    callback(null)
                }
            }
        }
    }
    
    private suspend fun downloadFavicon(domain: String): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                // 尝试不同的 favicon 服务
                val services = listOf(
                    "https://www.google.com/s2/favicons?domain=",
                    "https://favicon.yandex.net/favicon/",
                    "https://api.faviconkit.com/"
                )
                
                for (service in services) {
                    try {
                        val url = URL(service + domain)
                        val connection = url.openConnection()
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        val inputStream = connection.getInputStream()
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        inputStream.close()
                        if (bitmap != null) {
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
    
    private fun getFaviconFile(domain: String): File {
        val hash = MessageDigest.getInstance("MD5")
            .digest(domain.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$hash.png")
    }
    
    fun clearCache() {
        memoryCache.evictAll()
        cacheDir.listFiles()?.forEach { it.delete() }
    }
} 