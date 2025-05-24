package com.example.aifloatingball.model

import android.graphics.Bitmap
import java.util.Date
import java.io.Serializable

/**
 * 书签数据模型
 */
data class Bookmark(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val faviconPath: String? = null,
    val addTime: Long = System.currentTimeMillis(),
    val folder: String = "默认",
    val position: Int = -1
) : Serializable {
    
    /**
     * 根据域名获取 favicon 缓存的文件名
     */
    fun getFaviconFileName(): String {
        return "favicon_${id}.png"
    }
    
    /**
     * 从 URL 中提取域名
     */
    fun getDomain(): String {
        return try {
            val uri = java.net.URI(url)
            uri.host ?: ""
        } catch (e: Exception) {
            ""
        }
    }
} 