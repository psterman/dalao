package com.example.aifloatingball.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.Date

/**
 * 聊天记录问题数据模型
 */
@Parcelize
data class ChatHistoryQuestion(
    val id: String,
    val content: String,
    val timestamp: Long,
    val messageIndex: Int, // 在聊天记录中的位置索引
    val sessionId: String,
    val aiServiceType: String,
    val hasLink: Boolean = false, // 是否包含链接
    val isDeleted: Boolean = false, // 是否已删除
    val isFavorite: Boolean = false // 是否已收藏
) : Parcelable

/**
 * 问题分类类型枚举
 */
enum class QuestionFilterType(val displayName: String, val description: String) {
    ALL("全部", "显示所有问题"),
    RECENT("最近提问", "最近7天的问题"),
    WITH_LINKS("包含链接", "包含网址链接的问题"),
    DELETED("已删除", "用户删除的问题"),
    FAVORITES("已收藏", "用户收藏的问题")
}

/**
 * 聊天记录统计信息
 */
data class ChatHistoryStats(
    val totalQuestions: Int,
    val recentQuestions: Int, // 最近7天
    val linkQuestions: Int, // 包含链接
    val deletedQuestions: Int, // 已删除
    val favoriteQuestions: Int // 已收藏
)

/**
 * 聊天记录管理器
 */
class ChatHistoryManager {
    
    companion object {
        private const val PREFS_NAME = "chat_history"
        private const val KEY_QUESTIONS = "questions"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_DELETED = "deleted"
    }
    
    /**
     * 检测问题是否包含链接
     */
    fun hasLink(content: String): Boolean {
        val linkPatterns = listOf(
            "http://", "https://", "www.", ".com", ".cn", ".org", ".net", ".edu", ".gov"
        )
        val lowerContent = content.lowercase()
        return linkPatterns.any { pattern -> lowerContent.contains(pattern) }
    }
    
    /**
     * 获取所有分类类型
     */
    fun getAllFilterTypes(): List<QuestionFilterType> {
        return QuestionFilterType.values().toList()
    }
    
    /**
     * 获取分类显示名称
     */
    fun getFilterDisplayName(filterType: QuestionFilterType): String {
        return filterType.displayName
    }
    
    /**
     * 获取分类描述
     */
    fun getFilterDescription(filterType: QuestionFilterType): String {
        return filterType.description
    }
    
    /**
     * 检查问题是否属于指定分类
     */
    fun matchesFilter(question: ChatHistoryQuestion, filterType: QuestionFilterType): Boolean {
        return when (filterType) {
            QuestionFilterType.ALL -> true
            QuestionFilterType.RECENT -> {
                val sevenDaysAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
                question.timestamp >= sevenDaysAgo
            }
            QuestionFilterType.WITH_LINKS -> question.hasLink
            QuestionFilterType.DELETED -> question.isDeleted
            QuestionFilterType.FAVORITES -> question.isFavorite
        }
    }
}
