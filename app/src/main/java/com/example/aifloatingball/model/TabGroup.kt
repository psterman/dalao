package com.example.aifloatingball.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * 标签页组数据模型
 * 用于组织和管理不同情境下的网页卡片
 */
@Parcelize
data class TabGroup(
    val id: String,
    var name: String,
    var isPinned: Boolean = false, // 是否置顶
    var order: Int = 0, // 排序顺序
    var createdAt: Long = System.currentTimeMillis(), // 创建时间
    var updatedAt: Long = System.currentTimeMillis(), // 更新时间
    var passwordHash: String? = null, // 密码哈希值（加密存储）
    var isHidden: Boolean = false, // 是否隐藏
    var quickAccessCode: String? = null // 快捷访问码（特殊密码）
) : Parcelable {
    
    companion object {
        /**
         * 创建默认组
         */
        fun createDefault(): TabGroup {
            return TabGroup(
                id = "group_default_${System.currentTimeMillis()}",
                name = "默认组",
                isPinned = false,
                order = 0
            )
        }
        
        /**
         * 创建新组
         */
        fun createNew(name: String): TabGroup {
            return TabGroup(
                id = "group_${System.currentTimeMillis()}",
                name = name,
                isPinned = false,
                order = System.currentTimeMillis().toInt() // 使用时间戳作为初始排序
            )
        }
    }
}

