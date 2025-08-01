package com.example.aifloatingball.model

import com.google.gson.annotations.SerializedName

/**
 * B站用户信息
 */
data class BilibiliUser(
    @SerializedName("uid")
    val uid: Long,
    
    @SerializedName("name")
    val name: String,
    
    @SerializedName("face")
    val avatar: String,
    
    @SerializedName("sign")
    val signature: String = "",
    
    @SerializedName("level")
    val level: Int = 0,
    
    // 本地字段
    val isSubscribed: Boolean = false,
    val subscribeTime: Long = System.currentTimeMillis()
)

/**
 * B站动态信息
 */
data class BilibiliDynamic(
    @SerializedName("id_str")
    val id: String,
    
    @SerializedName("type")
    val type: Int,
    
    @SerializedName("timestamp")
    val timestamp: Long,
    
    @SerializedName("author")
    val author: BilibiliUser,
    
    @SerializedName("modules")
    val modules: DynamicModules
)

/**
 * 动态模块
 */
data class DynamicModules(
    @SerializedName("module_dynamic")
    val dynamic: DynamicContent?,
    
    @SerializedName("module_author")
    val author: AuthorModule?
)

/**
 * 动态内容
 */
data class DynamicContent(
    @SerializedName("desc")
    val desc: DynamicDesc?,
    
    @SerializedName("major")
    val major: DynamicMajor?
)

/**
 * 动态描述
 */
data class DynamicDesc(
    @SerializedName("text")
    val text: String,
    
    @SerializedName("rich_text_nodes")
    val richTextNodes: List<RichTextNode>?
)

/**
 * 富文本节点
 */
data class RichTextNode(
    @SerializedName("type")
    val type: String,
    
    @SerializedName("text")
    val text: String,
    
    @SerializedName("jump_url")
    val jumpUrl: String?
)

/**
 * 动态主要内容
 */
data class DynamicMajor(
    @SerializedName("type")
    val type: String,
    
    @SerializedName("archive")
    val archive: VideoArchive?,
    
    @SerializedName("article")
    val article: ArticleInfo?,
    
    @SerializedName("opus")
    val opus: OpusInfo?
)

/**
 * 视频信息
 */
data class VideoArchive(
    @SerializedName("title")
    val title: String,
    
    @SerializedName("desc")
    val desc: String,
    
    @SerializedName("cover")
    val cover: String,
    
    @SerializedName("jump_url")
    val jumpUrl: String
)

/**
 * 文章信息
 */
data class ArticleInfo(
    @SerializedName("title")
    val title: String,
    
    @SerializedName("desc")
    val desc: String,
    
    @SerializedName("jump_url")
    val jumpUrl: String
)

/**
 * 图文动态信息
 */
data class OpusInfo(
    @SerializedName("summary")
    val summary: OpusSummary
)

/**
 * 图文动态摘要
 */
data class OpusSummary(
    @SerializedName("text")
    val text: String
)

/**
 * 作者模块
 */
data class AuthorModule(
    @SerializedName("name")
    val name: String,
    
    @SerializedName("face")
    val face: String,
    
    @SerializedName("pub_time")
    val pubTime: String,
    
    @SerializedName("pub_ts")
    val pubTs: Long
)

/**
 * B站API响应
 */
data class BilibiliApiResponse<T>(
    @SerializedName("code")
    val code: Int,
    
    @SerializedName("message")
    val message: String,
    
    @SerializedName("data")
    val data: T?
)

/**
 * 动态列表响应数据
 */
data class DynamicListData(
    @SerializedName("items")
    val items: List<BilibiliDynamic>,
    
    @SerializedName("has_more")
    val hasMore: Boolean,
    
    @SerializedName("offset")
    val offset: String?
)

/**
 * 用户信息响应数据
 */
data class UserInfoData(
    @SerializedName("card")
    val card: BilibiliUser
)

/**
 * 本地存储的动态信息
 */
data class LocalDynamic(
    val id: String,
    val uid: Long,
    val authorName: String,
    val authorAvatar: String,
    val content: String,
    val type: String, // "video", "article", "text", "repost"
    val title: String = "",
    val jumpUrl: String = "",
    val timestamp: Long,
    val createTime: Long = System.currentTimeMillis()
)

/**
 * 订阅的用户信息
 */
data class SubscribedUser(
    val uid: Long,
    val name: String,
    val avatar: String,
    val signature: String,
    val subscribeTime: Long,
    val lastUpdateTime: Long = 0,
    val isEnabled: Boolean = true
)
