package com.example.aifloatingball.model

import android.graphics.Bitmap
import java.util.Date
import java.io.Serializable

/**
 * 书签数据模型（统一的书签数据模型）
 * 支持描述、标签、模板标记等功能
 */
data class Bookmark(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val description: String? = null, // 描述信息
    val tags: List<String> = emptyList(), // 标签列表
    val faviconPath: String? = null,
    val addTime: Long = System.currentTimeMillis(),
    val folder: String = "默认",
    val position: Int = -1,
    val isTemplate: Boolean = false, // 是否来自模板
    val templateId: String? = null // 模板ID（如果来自模板）
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
    
    /**
     * 获取格式化的创建时间（相对时间）
     */
    fun getFormattedTime(): String {
        val now = System.currentTimeMillis()
        val diff = now - addTime
        
        return when {
            diff < 60 * 1000 -> "刚刚"
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}分钟前"
            diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}小时前"
            diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)}天前"
            else -> {
                val formatter = java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault())
                formatter.format(Date(addTime))
            }
        }
    }
    
    /**
     * 从BookmarkEntry转换为Bookmark（用于数据迁移）
     */
    companion object {
        fun fromBookmarkEntry(entry: BookmarkEntry): Bookmark {
            return Bookmark(
                id = entry.id,
                title = entry.title,
                url = entry.url,
                description = null,
                tags = entry.tags,
                faviconPath = entry.favicon,
                addTime = entry.createTime.time,
                folder = entry.folder.replace("默认文件夹", "默认"), // 统一文件夹名称
                position = -1,
                isTemplate = false,
                templateId = null
            )
        }
    }
} 