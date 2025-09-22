package com.example.aifloatingball.model

import java.util.Date

/**
 * 历史访问记录数据模型
 */
data class HistoryEntry(
    val id: String,
    val title: String,
    val url: String,
    val favicon: String? = null,
    val visitTime: Date,
    val visitCount: Int = 1
) {
    /**
     * 获取格式化的访问时间
     */
    fun getFormattedTime(): String {
        val now = Date()
        val diff = now.time - visitTime.time
        
        return when {
            diff < 60 * 1000 -> "刚刚"
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}分钟前"
            diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}小时前"
            diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)}天前"
            else -> {
                val formatter = java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault())
                formatter.format(visitTime)
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
