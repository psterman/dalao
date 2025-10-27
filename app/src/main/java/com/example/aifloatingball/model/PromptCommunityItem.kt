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
 * Prompt分类枚举
 */
enum class PromptCategory(
    val displayName: String,
    val icon: String
) {
    PROFESSIONAL("行业", "💼"),
    SCENARIO("场景", "🎭"),
    TECHNIQUE("技巧", "⚙️"),
    HOT("热门", "🔥"),
    MASTER("达人专栏", "⭐"),
    LIFE("生活", "🏠"),
    ENTERTAINMENT("娱乐", "🎮"),
    EDUCATION("教育", "📚"),
    BUSINESS("商业", "💼"),
    CREATIVE("创作", "✏️"),
    ANALYSIS("分析", "📊"),
    TRANSLATION("翻译", "🌐"),
    UNKNOWN("其他", "📌")
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

