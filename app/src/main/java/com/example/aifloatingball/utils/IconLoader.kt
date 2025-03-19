package com.example.aifloatingball.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ImageView
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class IconLoader(private val context: Context) {
    private val iconCache = mutableMapOf<String, Bitmap>()
    private val iconCacheDir by lazy { File(context.cacheDir, "search_icons") }
    
    fun loadIcon(url: String, iconView: ImageView, defaultIconRes: Int) {
        val domain = Uri.parse(url).host ?: return
        val iconFile = File(iconCacheDir, "${domain.replace(".", "_")}.png")
        
        // 设置默认图标
        iconView.setImageResource(defaultIconRes)
        
        // 检查内存缓存
        iconCache[domain]?.let {
            iconView.setImageBitmap(it)
            return
        }
        
        // 检查文件缓存
        if (iconFile.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(iconFile.absolutePath)
                if (bitmap != null) {
                    iconCache[domain] = bitmap
                    iconView.setImageBitmap(bitmap)
                    return
                }
            } catch (e: Exception) {
                Log.e("IconLoader", "从缓存加载图标失败: ${e.message}")
            }
        }
        
        // 从网络加载
        Thread {
            try {
                // 尝试不同的 favicon URL
                val iconUrls = listOf(
                    "https://$domain/favicon.ico",
                    "https://$domain/favicon.png",
                    "https://www.google.com/s2/favicons?domain=$domain&sz=64"
                )
                
                var bitmap: Bitmap? = null
                
                for (iconUrl in iconUrls) {
                    try {
                        val connection = URL(iconUrl).openConnection() as HttpURLConnection
                        connection.connectTimeout = 5000
                        connection.readTimeout = 5000
                        
                        if (connection.responseCode == 200) {
                            bitmap = BitmapFactory.decodeStream(connection.inputStream)
                            if (bitmap != null) break
                        }
                    } catch (e: Exception) {
                        continue
                    }
                }
                
                bitmap?.let {
                    // 缓存到内存
                    iconCache[domain] = it
                    
                    // 缓存到文件
                    iconCacheDir.mkdirs()
                    try {
                        FileOutputStream(iconFile).use { out ->
                            it.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                    } catch (e: Exception) {
                        Log.e("IconLoader", "保存图标到文件失败: ${e.message}")
                    }
                    
                    // 在主线程更新UI
                    Handler(Looper.getMainLooper()).post {
                        iconView.setImageBitmap(it)
                    }
                }
            } catch (e: Exception) {
                Log.e("IconLoader", "加载图标失败: ${e.message}")
            }
        }.start()
    }
    
    fun clearCache() {
        iconCache.clear()
        try {
            if (iconCacheDir.exists()) {
                iconCacheDir.listFiles()?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            Log.e("IconLoader", "清理缓存失败", e)
        }
    }
    
    fun cleanupOldCache() {
        try {
            if (iconCacheDir.exists()) {
                // 删除超过7天的缓存文件
                val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
                iconCacheDir.listFiles()?.forEach { file ->
                    if (file.lastModified() < sevenDaysAgo) {
                        file.delete()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("IconLoader", "清理旧缓存失败", e)
        }
    }
} 