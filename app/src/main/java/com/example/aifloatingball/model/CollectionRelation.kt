package com.example.aifloatingball.model

import java.io.Serializable

/**
 * 关联关系类型枚举
 * 定义收藏项之间可以建立的各种关联类型
 */
enum class RelationType(
    val displayName: String,
    val icon: String,
    val color: Int = 0xFF2196F3.toInt()
) {
    RELATED("相关", "🔗", 0xFF2196F3.toInt()),           // 一般相关
    REFERENCE("引用", "📎", 0xFF4CAF50.toInt()),         // 引用关系
    DEPENDENCY("依赖", "⬇️", 0xFFFF9800.toInt()),       // 依赖关系
    CONTAINS("包含", "📦", 0xFF9C27B0.toInt()),         // 包含关系
    SIMILAR("相似", "🔀", 0xFF00BCD4.toInt()),          // 相似内容
    SEQUENCE("顺序", "➡️", 0xFF607D8B.toInt()),         // 顺序关系
    PARENT("父级", "⬆️", 0xFF795548.toInt()),           // 父级关系
    CHILD("子级", "⬇️", 0xFF795548.toInt()),            // 子级关系
    PREREQUISITE("前置", "⏮️", 0xFFE91E63.toInt()),     // 前置条件
    FOLLOW_UP("后续", "⏭️", 0xFF009688.toInt()),        // 后续内容
    CONTRAST("对比", "⚖️", 0xFF3F51B5.toInt()),         // 对比关系
    EXAMPLE("示例", "💡", 0xFFFFC107.toInt())           // 示例关系
}

/**
 * 关联关系数据模型（用于方案一：内嵌在收藏项中）
 * 
 * @param targetId 关联目标收藏项的ID
 * @param relationType 关联类型
 * @param createdAt 关联创建时间
 * @param note 关联备注说明
 * @param weight 关联权重（0-1，用于表示关联强度）
 */
data class CollectionRelation(
    val targetId: String,              // 关联目标ID
    val relationType: RelationType,    // 关联类型
    val createdAt: Long = System.currentTimeMillis(),  // 创建时间
    val note: String? = null,         // 关联备注
    val weight: Float = 1.0f          // 关联权重（0-1）
) : Serializable {
    
    /**
     * 更新关联备注
     */
    fun updateNote(newNote: String?): CollectionRelation {
        return copy(note = newNote)
    }
    
    /**
     * 更新关联权重
     */
    fun updateWeight(newWeight: Float): CollectionRelation {
        return copy(weight = newWeight.coerceIn(0f, 1f))
    }
    
    /**
     * 更新关联类型
     */
    fun updateType(newType: RelationType): CollectionRelation {
        return copy(relationType = newType)
    }
}

/**
 * 关联关系实体（用于方案二：独立存储）
 * 
 * @param id 关联关系唯一ID
 * @param sourceId 源收藏项ID
 * @param targetId 目标收藏项ID
 * @param relationType 关联类型
 * @param weight 关联权重
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @param note 关联备注
 * @param isBidirectional 是否双向关联
 */
data class CollectionRelationEntity(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sourceId: String,              // 源收藏项ID
    val targetId: String,              // 目标收藏项ID
    val relationType: RelationType,    // 关联类型
    val weight: Float = 1.0f,         // 关联权重（0-1）
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val note: String? = null,         // 关联备注
    val isBidirectional: Boolean = true  // 是否双向关联
) : Serializable {
    
    /**
     * 更新关联信息
     */
    fun update(
        relationType: RelationType? = null,
        weight: Float? = null,
        note: String? = null
    ): CollectionRelationEntity {
        return copy(
            relationType = relationType ?: this.relationType,
            weight = weight ?: this.weight,
            note = note ?: this.note,
            updatedAt = System.currentTimeMillis()
        )
    }
    
    /**
     * 检查是否包含指定收藏项ID
     */
    fun involves(collectionId: String): Boolean {
        return sourceId == collectionId || targetId == collectionId
    }
    
    /**
     * 获取另一个收藏项ID（给定一个ID，返回另一个）
     */
    fun getOtherId(givenId: String): String? {
        return when {
            sourceId == givenId -> targetId
            targetId == givenId -> sourceId
            else -> null
        }
    }
}

