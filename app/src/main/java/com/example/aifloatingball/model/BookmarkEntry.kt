package com.example.aifloatingball.model

import java.util.Date

/**
 * 收藏页面数据模型
 */
data class BookmarkEntry(
    val id: String,
    val title: String,
    val url: String,
    val favicon: String? = null,
    val folder: String = "默认文件夹",
    val createTime: Date,
    val tags: List<String> = emptyList()
) {
    /**
     * 获取格式化的创建时间
     */
    fun getFormattedTime(): String {
        val now = Date()
        val diff = now.time - createTime.time
        
        return when {
            diff < 60 * 1000 -> "刚刚"
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}分钟前"
            diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}小时前"
            diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)}天前"
            else -> {
                val formatter = java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault())
                formatter.format(createTime)
            }
        }
    }
    
    /**
     * 获取域名
     */
    fun getDomain(): String {
        return try {
            val uri = java.net.URI(url)
            uri.host ?: url
        } catch (e: Exception) {
            url
        }
    }
}
