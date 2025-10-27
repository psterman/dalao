package com.example.aifloatingball.model

/**
 * Prompt社区数据模型
 */
data class PromptCommunityItem(
    val id: String,
    val title: String,
    val content: String,
    val author: String,
    val authorId: String,
    val tags: List<String>,
    val category: PromptCategory,
    val scene: String,
    val description: String,
    val likeCount: Int = 0,
    val collectCount: Int = 0,
    val commentCount: Int = 0,
    val viewCount: Int = 0,
    val isLiked: Boolean = false,
    val isCollected: Boolean = false,
    val publishTime: Long = System.currentTimeMillis(),
    val isOriginal: Boolean = true,
    val originalAuthor: String? = null,
    val version: String = "1.0",
    val previewImage: String? = null
)

/**
 * Prompt分类枚举 - 重构后的4大主分类
 */
enum class PromptCategory(
    val displayName: String,
    val icon: String,
    val isMainCategory: Boolean = false  // 标记是否为主分类
) {
    // 主分类
    FUNCTIONAL("功能分类", "⚙️", true),
    HIGH_FREQUENCY("高频场景", "📍", true),
    POPULAR("热门推荐", "🔥", true),
    MY_CONTENT("我的内容", "👤", true),
    
    // 功能分类子分类
    CREATIVE_WRITING("文案创作", "✏️", false),
    DATA_ANALYSIS("数据分析", "📊", false),
    TRANSLATION_CONVERSION("翻译转换", "🌐", false),
    
    // 高频场景子分类
    WORKPLACE_OFFICE("职场办公", "💼", false),
    EDUCATION_STUDY("教育学习", "📚", false),
    LIFE_SERVICE("生活服务", "🏠", false),
    
    // 热门推荐子分类
    TOP10_WEEK("本周TOP10", "🏆", false),
    EXPERT_PICKS("达人精选", "⭐", false),
    
    // 我的内容子分类
    MY_COLLECTIONS("我的收藏", "❤️", false),
    MY_UPLOADS("我的上传", "📤", false),
    
    // 兼容旧分类
    UNKNOWN("其他", "📌", false)
}

/**
 * Prompt筛选类型
 */
enum class FilterType {
    HOT,           // 热门
    LATEST,        // 最新
    MY_COLLECTION, // 我的收藏
    MY_UPLOAD      // 我的上传
}

/**
 * Prompt社区统计信息
 */
data class PromptStats(
    val totalPrompts: Int = 0,
    val totalUsers: Int = 0,
    val todayUploads: Int = 0,
    val thisWeekUploads: Int = 0
)

/**
 * 上传Prompt信息
 */
data class UploadPromptInfo(
    val title: String,
    val content: String,
    val tags: List<String>,
    val category: PromptCategory,
    val scene: String,
    val description: String,
    val isOriginal: Boolean = true,
    val originalAuthor: String? = null,
    val previewImage: String? = null
)

