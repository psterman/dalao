package com.example.aifloatingball.model

import java.io.Serializable

/**
 * 统一收藏项数据模型
 * 支持所有类型的收藏和历史记录，包含11个元数据字段
 */
data class UnifiedCollectionItem(
    // 基础信息
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,                    // 标题/内容预览
    val content: String,                  // 完整内容（AI回复、文本等）
    val preview: String? = null,          // 预览文本（截取前200字符）
    val thumbnail: String? = null,        // 缩略图路径/URL（图片、视频、网页favicon）
    
    // 收藏类型和来源
    val collectionType: CollectionType,   // 收藏类型（合并后9种）
    val sourceLocation: String,           // 收藏地点（如"AI对话Tab"、"搜索Tab"）
    val sourceDetail: String? = null,     // 收藏来源详情（如"DeepSeek对话"、"百度搜索"、"搜索Tab"等）
    
    // 元数据（11个字段）
    val collectedTime: Long = System.currentTimeMillis(),  // 收藏时间
    val modifiedTime: Long = System.currentTimeMillis(),  // 修改时间
    val customTags: List<String> = emptyList(),            // 自定义标签
    val priority: Priority = Priority.NORMAL,              // 优先级
    val completionStatus: CompletionStatus = CompletionStatus.NOT_STARTED, // 完成状态
    val likeLevel: Int = 0,                                // 喜欢程度（0-5星）
    val emotionTag: EmotionTag = EmotionTag.NEUTRAL,       // 情感标签
    val isEncrypted: Boolean = false,                     // 加密状态
    val reminderTime: Long? = null,                       // 提醒时间（null表示无提醒）
    
    // 类型特定数据（根据collectionType存储不同数据）
    val extraData: Map<String, Any> = emptyMap(),  // 扩展数据
    
    // 关联关系（存储关联的其他收藏项ID和类型）
    val relations: List<CollectionRelation> = emptyList()  // 关联关系列表
) : Serializable {
    
    /**
     * 获取格式化的收藏时间（相对时间）
     */
    fun getFormattedCollectedTime(): String {
        val now = System.currentTimeMillis()
        val diff = now - collectedTime
        
        return when {
            diff < 60 * 1000 -> "刚刚"
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}分钟前"
            diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}小时前"
            diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)}天前"
            else -> {
                val formatter = java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault())
                formatter.format(java.util.Date(collectedTime))
            }
        }
    }
    
    /**
     * 获取格式化的修改时间（相对时间）
     */
    fun getFormattedModifiedTime(): String {
        val now = System.currentTimeMillis()
        val diff = now - modifiedTime
        
        return when {
            diff < 60 * 1000 -> "刚刚"
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)}分钟前"
            diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)}小时前"
            diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)}天前"
            else -> {
                val formatter = java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault())
                formatter.format(java.util.Date(modifiedTime))
            }
        }
    }
    
    /**
     * 获取来源显示文本（包含来源标记）
     */
    fun getSourceDisplayText(): String {
        return if (sourceDetail != null) {
            "$sourceLocation · $sourceDetail"
        } else {
            sourceLocation
        }
    }
    
    /**
     * 更新修改时间
     */
    fun updateModifiedTime(): UnifiedCollectionItem {
        return copy(modifiedTime = System.currentTimeMillis())
    }
    
    /**
     * 添加关联关系
     * 
     * @param targetId 关联目标收藏项ID
     * @param relationType 关联类型
     * @param note 关联备注（可选）
     * @param weight 关联权重（0-1，默认1.0）
     * @return 更新后的收藏项
     */
    fun addRelation(
        targetId: String,
        relationType: RelationType,
        note: String? = null,
        weight: Float = 1.0f
    ): UnifiedCollectionItem {
        // 检查是否已存在关联
        if (relations.any { it.targetId == targetId }) {
            return this
        }
        
        // 防止自关联
        if (targetId == id) {
            return this
        }
        
        return copy(
            relations = relations + CollectionRelation(
                targetId = targetId,
                relationType = relationType,
                note = note,
                weight = weight
            ),
            modifiedTime = System.currentTimeMillis()
        )
    }
    
    /**
     * 移除关联关系
     * 
     * @param targetId 要移除的关联目标ID
     * @return 更新后的收藏项
     */
    fun removeRelation(targetId: String): UnifiedCollectionItem {
        return copy(
            relations = relations.filter { it.targetId != targetId },
            modifiedTime = System.currentTimeMillis()
        )
    }
    
    /**
     * 更新关联类型
     * 
     * @param targetId 关联目标ID
     * @param newType 新的关联类型
     * @return 更新后的收藏项
     */
    fun updateRelationType(targetId: String, newType: RelationType): UnifiedCollectionItem {
        return copy(
            relations = relations.map {
                if (it.targetId == targetId) {
                    it.updateType(newType)
                } else {
                    it
                }
            },
            modifiedTime = System.currentTimeMillis()
        )
    }
    
    /**
     * 更新关联备注
     * 
     * @param targetId 关联目标ID
     * @param note 新的备注
     * @return 更新后的收藏项
     */
    fun updateRelationNote(targetId: String, note: String?): UnifiedCollectionItem {
        return copy(
            relations = relations.map {
                if (it.targetId == targetId) {
                    it.updateNote(note)
                } else {
                    it
                }
            },
            modifiedTime = System.currentTimeMillis()
        )
    }
    
    /**
     * 更新关联权重
     * 
     * @param targetId 关联目标ID
     * @param weight 新的权重（0-1）
     * @return 更新后的收藏项
     */
    fun updateRelationWeight(targetId: String, weight: Float): UnifiedCollectionItem {
        return copy(
            relations = relations.map {
                if (it.targetId == targetId) {
                    it.updateWeight(weight)
                } else {
                    it
                }
            },
            modifiedTime = System.currentTimeMillis()
        )
    }
    
    /**
     * 获取所有关联的收藏项ID
     * 
     * @return 关联ID列表
     */
    fun getRelatedIds(): List<String> {
        return relations.map { it.targetId }
    }
    
    /**
     * 检查是否关联了指定ID
     * 
     * @param targetId 目标ID
     * @return 是否关联
     */
    fun isRelatedTo(targetId: String): Boolean {
        return relations.any { it.targetId == targetId }
    }
    
    /**
     * 获取指定类型的关联数量
     * 
     * @param relationType 关联类型
     * @return 关联数量
     */
    fun getRelationCountByType(relationType: RelationType): Int {
        return relations.count { it.relationType == relationType }
    }
    
    /**
     * 获取指定类型的关联列表
     * 
     * @param relationType 关联类型
     * @return 关联列表
     */
    fun getRelationsByType(relationType: RelationType): List<CollectionRelation> {
        return relations.filter { it.relationType == relationType }
    }
}

/**
 * 收藏类型枚举（合并后10种）
 * 注意：搜索历史合并为1种，但通过sourceDetail标记来源
 */
enum class CollectionType(
    val displayName: String,
    val icon: String,
    val color: Int
) {
    AI_REPLY("AI回复收藏", "🤖", 0xFF2196F3.toInt()),
    SEARCH_HISTORY("搜索历史", "🔍", 0xFF4CAF50.toInt()),  // 合并4种搜索历史
    WEB_BOOKMARK("网页收藏", "🌐", 0xFF2196F3.toInt()),
    EBOOK_BOOKMARK("电子书收藏", "📚", 0xFFFF9800.toInt()),
    IMAGE_COLLECTION("图片收藏", "🖼️", 0xFFE91E63.toInt()),
    VIDEO_COLLECTION("视频收藏", "🎬", 0xFF9C27B0.toInt()),
    READING_HIGHLIGHT("读书划线", "✏️", 0xFF00BCD4.toInt()),
    CLIPBOARD_HISTORY("剪贴板历史", "📋", 0xFF9E9E9E.toInt()),
    VOICE_TO_TEXT("语音转文", "🎤", 0xFF4CAF50.toInt()),  // 语音转文本
    MY_COLLECTIONS("我的收藏", "❤️", 0xFFF44336.toInt())  // 原有分类
}

/**
 * 搜索历史来源枚举（用于标记搜索历史的具体来源）
 */
enum class SearchHistorySource(val displayName: String, val icon: String) {
    SEARCH_TAB("搜索Tab", "🔍"),
    APP_TAB("软件Tab", "📱"),
    VOICE_TAB("语音Tab", "🎤"),
    FLOATING_BALL("悬浮球", "⚪"),
    DYNAMIC_ISLAND("灵动岛", "🏝️")
}

/**
 * 优先级枚举
 */
enum class Priority(val displayName: String, val value: Int) {
    HIGH("高", 3),
    NORMAL("中", 2),
    LOW("低", 1)
}

/**
 * 完成状态枚举
 */
enum class CompletionStatus(val displayName: String) {
    NOT_STARTED("未开始"),
    IN_PROGRESS("进行中"),
    COMPLETED("已完成")
}

/**
 * 情感标签枚举
 */
enum class EmotionTag(val displayName: String) {
    POSITIVE("正面"),
    NEUTRAL("中性"),
    NEGATIVE("负面"),
    INSPIRING("激励"),
    FUNNY("有趣"),
    SERIOUS("严肃")
}

/**
 * 排序维度枚举
 */
enum class SortDimension(val displayName: String) {
    COLLECTED_TIME("收藏时间"),
    MODIFIED_TIME("修改时间"),
    SOURCE_LOCATION("收藏地点"),
    COLLECTION_TYPE("收藏类型"),
    IS_ENCRYPTED("加密状态"),
    PRIORITY("优先级"),
    LIKE_LEVEL("喜欢程度")
}

/**
 * 排序方向枚举
 */
enum class SortDirection(val displayName: String) {
    ASC("升序"),
    DESC("降序")
}

