package com.example.aifloatingball.manager

import android.content.Context
import android.util.Log
import com.example.aifloatingball.model.CollectionType
import com.example.aifloatingball.model.UnifiedCollectionItem
import java.util.regex.Pattern

/**
 * 搜索历史自动记录工具类
 * 自动记录用户在各个输入框中的输入内容
 */
object SearchHistoryAutoRecorder {
    
    private const val TAG = "SearchHistoryAutoRecorder"
    
    /**
     * 搜索历史来源枚举
     */
    enum class SearchSource(val displayName: String, val icon: String) {
        SEARCH_TAB("搜索Tab", "🔍"),
        APP_TAB("软件Tab", "📱"),
        CHAT_TAB("对话Tab", "💬"),
        FLOATING_BALL("悬浮球", "⚪"),
        DYNAMIC_ISLAND("灵动岛", "🏝️")
    }
    
    /**
     * 记录搜索历史
     * 
     * @param context 上下文
     * @param query 搜索内容
     * @param source 搜索来源
     * @param tags 标签列表（可选）
     * @param searchType 搜索类型（可选，如"应用搜索"、"网页搜索"等）
     */
    fun recordSearchHistory(
        context: Context,
        query: String,
        source: SearchSource,
        tags: List<String> = emptyList(),
        searchType: String? = null
    ) {
        try {
            // 过滤空内容和过短内容
            val trimmedQuery = query.trim()
            if (trimmedQuery.isEmpty() || trimmedQuery.length < 1) {
                return
            }
            
            // 过滤URL（如果输入的是完整URL，不记录为搜索历史）
            if (isUrl(trimmedQuery)) {
                Log.d(TAG, "跳过URL记录: $trimmedQuery")
                return
            }
            
            val collectionManager = UnifiedCollectionManager.getInstance(context)
            
            // 检查是否已存在相同的搜索（避免重复记录）
            val existingCollections = collectionManager.getAllCollections()
            val isDuplicate = existingCollections.any { item ->
                item.collectionType == CollectionType.SEARCH_HISTORY &&
                item.content == trimmedQuery &&
                item.sourceDetail == source.displayName &&
                // 如果时间相差小于5秒，认为是重复
                (System.currentTimeMillis() - item.collectedTime) < 5000
            }
            
            if (isDuplicate) {
                Log.d(TAG, "跳过重复搜索记录: $trimmedQuery")
                return
            }
            
            // 生成标题（使用搜索内容的前50字符）
            val title = if (trimmedQuery.length > 50) {
                trimmedQuery.take(50) + "..."
            } else {
                trimmedQuery
            }
            
            // 生成预览（使用搜索内容的前200字符）
            val preview = if (trimmedQuery.length > 200) {
                trimmedQuery.take(200) + "..."
            } else {
                trimmedQuery
            }
            
            // 构建标签列表（包含来源标签）
            val allTags = mutableListOf<String>().apply {
                addAll(tags)
                add(source.displayName) // 添加来源标签
                if (searchType != null) {
                    add(searchType) // 添加搜索类型标签
                }
            }
            
            // 创建收藏项
            val collectionItem = UnifiedCollectionItem(
                title = title,
                content = trimmedQuery,
                preview = preview,
                collectionType = CollectionType.SEARCH_HISTORY,
                sourceLocation = "搜索历史",
                sourceDetail = source.displayName,
                collectedTime = System.currentTimeMillis(),
                customTags = allTags.distinct(), // 去重
                extraData = mapOf(
                    "searchSource" to source.name,
                    "searchSourceDisplay" to source.displayName,
                    "searchType" to (searchType ?: "通用搜索"),
                    "queryLength" to trimmedQuery.length,
                    "recordedAt" to System.currentTimeMillis()
                )
            )
            
            // 保存到统一收藏管理器
            val success = collectionManager.addCollection(collectionItem)
            
            if (success) {
                Log.d(TAG, "搜索历史记录成功: 来源=${source.displayName}, 内容='$trimmedQuery'")
            } else {
                Log.e(TAG, "搜索历史记录失败: 来源=${source.displayName}, 内容='$trimmedQuery'")
            }
        } catch (e: Exception) {
            Log.e(TAG, "记录搜索历史时发生错误", e)
        }
    }
    
    /**
     * 检查字符串是否为URL
     */
    private fun isUrl(text: String): Boolean {
        val urlPattern = Pattern.compile(
            "^(https?://)?([\\da-z\\.-]+)\\.([a-z\\.]{2,6})([/\\w \\.-]*)*/?$",
            Pattern.CASE_INSENSITIVE
        )
        return urlPattern.matcher(text).matches() || 
               text.startsWith("http://") || 
               text.startsWith("https://") ||
               text.contains("://")
    }
    
    /**
     * 批量记录搜索历史
     * 
     * @param context 上下文
     * @param queries 搜索内容列表
     * @param source 搜索来源
     * @param tags 标签列表（可选）
     */
    fun recordBatchSearchHistory(
        context: Context,
        queries: List<String>,
        source: SearchSource,
        tags: List<String> = emptyList()
    ) {
        queries.forEach { query ->
            recordSearchHistory(context, query, source, tags)
        }
    }
}

