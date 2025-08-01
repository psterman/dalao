package com.example.aifloatingball.model

/**
 * 内容平台枚举
 */
enum class ContentPlatform(
    val platformId: String,
    val displayName: String,
    val iconRes: Int,
    val primaryColor: String,
    val baseUrl: String
) {
    BILIBILI(
        platformId = "bilibili",
        displayName = "哔哩哔哩",
        iconRes = com.example.aifloatingball.R.drawable.ic_bilibili,
        primaryColor = "#FB7299",
        baseUrl = "https://www.bilibili.com"
    ),
    
    DOUYIN(
        platformId = "douyin",
        displayName = "抖音",
        iconRes = com.example.aifloatingball.R.drawable.ic_douyin,
        primaryColor = "#FE2C55",
        baseUrl = "https://www.douyin.com"
    ),
    
    KUAISHOU(
        platformId = "kuaishou",
        displayName = "快手",
        iconRes = com.example.aifloatingball.R.drawable.ic_kuaishou,
        primaryColor = "#FF6600",
        baseUrl = "https://www.kuaishou.com"
    ),
    
    XIMALAYA(
        platformId = "ximalaya",
        displayName = "喜马拉雅",
        iconRes = com.example.aifloatingball.R.drawable.ic_ximalaya,
        primaryColor = "#FF6B35",
        baseUrl = "https://www.ximalaya.com"
    ),
    
    XIAOHONGSHU(
        platformId = "xiaohongshu",
        displayName = "小红书",
        iconRes = com.example.aifloatingball.R.drawable.ic_xiaohongshu,
        primaryColor = "#FF2442",
        baseUrl = "https://www.xiaohongshu.com"
    ),
    
    WEIBO(
        platformId = "weibo",
        displayName = "微博",
        iconRes = com.example.aifloatingball.R.drawable.ic_weibo,
        primaryColor = "#E6162D",
        baseUrl = "https://weibo.com"
    );
    
    companion object {
        fun fromPlatformId(platformId: String): ContentPlatform? {
            return values().find { it.platformId == platformId }
        }
        
        fun getEnabledPlatforms(): List<ContentPlatform> {
            // 可以根据配置返回启用的平台
            return listOf(BILIBILI, DOUYIN, KUAISHOU, XIMALAYA)
        }
    }
}

/**
 * 通用创作者信息
 */
data class Creator(
    val uid: String,                    // 创作者唯一ID
    val platform: ContentPlatform,     // 所属平台
    val name: String,                   // 创作者名称
    val avatar: String,                 // 头像URL
    val description: String = "",       // 描述/简介
    val followerCount: Long = 0,        // 粉丝数
    val isVerified: Boolean = false,    // 是否认证
    val verifyInfo: String = "",        // 认证信息
    val profileUrl: String = "",        // 个人主页URL
    val subscribeTime: Long = System.currentTimeMillis(), // 订阅时间
    val lastUpdateTime: Long = 0        // 最后更新时间
)

/**
 * 通用内容信息
 */
data class Content(
    val id: String,                     // 内容唯一ID
    val platform: ContentPlatform,     // 所属平台
    val creatorUid: String,             // 创作者ID
    val creatorName: String,            // 创作者名称
    val creatorAvatar: String,          // 创作者头像
    val title: String,                  // 内容标题
    val description: String = "",       // 内容描述
    val contentType: ContentType,       // 内容类型
    val coverUrl: String = "",          // 封面图片URL
    val contentUrl: String = "",        // 内容链接
    val publishTime: Long,              // 发布时间
    val viewCount: Long = 0,            // 播放/浏览量
    val likeCount: Long = 0,            // 点赞数
    val commentCount: Long = 0,         // 评论数
    val shareCount: Long = 0,           // 分享数
    val duration: Long = 0,             // 时长（秒，适用于视频/音频）
    val tags: List<String> = emptyList(), // 标签
    val isTop: Boolean = false,         // 是否置顶
    val extra: Map<String, Any> = emptyMap() // 平台特有的额外信息
)

/**
 * 内容类型枚举
 */
enum class ContentType(
    val typeId: String,
    val displayName: String,
    val iconRes: Int
) {
    VIDEO(
        typeId = "video",
        displayName = "视频",
        iconRes = com.example.aifloatingball.R.drawable.ic_video
    ),
    
    AUDIO(
        typeId = "audio",
        displayName = "音频",
        iconRes = com.example.aifloatingball.R.drawable.ic_audio
    ),
    
    IMAGE(
        typeId = "image",
        displayName = "图文",
        iconRes = com.example.aifloatingball.R.drawable.ic_image
    ),
    
    TEXT(
        typeId = "text",
        displayName = "文字",
        iconRes = com.example.aifloatingball.R.drawable.ic_text
    ),
    
    LIVE(
        typeId = "live",
        displayName = "直播",
        iconRes = com.example.aifloatingball.R.drawable.ic_live
    ),
    
    ARTICLE(
        typeId = "article",
        displayName = "文章",
        iconRes = com.example.aifloatingball.R.drawable.ic_article
    ),
    
    REPOST(
        typeId = "repost",
        displayName = "转发",
        iconRes = com.example.aifloatingball.R.drawable.ic_share
    );
    
    companion object {
        fun fromTypeId(typeId: String): ContentType? {
            return values().find { it.typeId == typeId }
        }
    }
}

/**
 * 订阅配置
 */
data class SubscriptionConfig(
    val platform: ContentPlatform,
    val isEnabled: Boolean = true,
    val updateInterval: Long = 12 * 60 * 60 * 1000L, // 更新间隔（毫秒）
    val maxContentCount: Int = 20,      // 最大缓存内容数
    val contentTypes: Set<ContentType> = setOf(ContentType.VIDEO, ContentType.AUDIO, ContentType.IMAGE), // 订阅的内容类型
    val enableNotification: Boolean = true, // 是否启用通知
    val autoRefresh: Boolean = true     // 是否自动刷新
)
