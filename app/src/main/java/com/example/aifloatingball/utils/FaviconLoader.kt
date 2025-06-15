package com.example.aifloatingball.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.LruCache
import android.widget.ImageView
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object FaviconLoader {

    private val memoryCache: LruCache<String, Bitmap>
    private val executor = Executors.newFixedThreadPool(5)
    private val uiHandler = Handler(Looper.getMainLooper())

    init {
        val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
        // Use 1/8th of the available memory for this cache.
        val cacheSize = maxMemory / 8
        memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
            override fun sizeOf(key: String, bitmap: Bitmap): Int {
                return bitmap.byteCount / 1024
            }
        }
    }

    private fun getFaviconServiceUrl(engineUrl: String): String? {
        return try {
            val host = URL(engineUrl).host
            // Using DuckDuckGo's favicon service for reliability and privacy.
            "https://icons.duckduckgo.com/ip3/$host.ico"
        } catch (e: Exception) {
            null
        }
    }

    fun loadIcon(imageView: ImageView, engineUrl: String, fallbackResId: Int) {
        val faviconUrl = getFaviconServiceUrl(engineUrl)

        if (faviconUrl == null) {
            imageView.setImageResource(fallbackResId)
            return
        }
        
        imageView.tag = faviconUrl

        val cachedBitmap = memoryCache.get(faviconUrl)
        if (cachedBitmap != null) {
            imageView.setImageBitmap(cachedBitmap)
            return
        }

        // Set fallback icon while loading
        imageView.setImageResource(fallbackResId)

        executor.execute {
            try {
                val url = URL(faviconUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connect()

                if (connection.responseCode == 200) {
                    val inputStream = connection.inputStream
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream.close()

                    if (bitmap != null) {
                        memoryCache.put(faviconUrl, bitmap)
                        uiHandler.post {
                            if (imageView.tag == faviconUrl) {
                                imageView.setImageBitmap(bitmap)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FaviconLoader", "Failed to load icon from $faviconUrl", e)
            }
        }
    }
} 