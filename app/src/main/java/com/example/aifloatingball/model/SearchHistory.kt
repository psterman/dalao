package com.example.aifloatingball.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "search_history")
data class SearchHistory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val query: String,
    val timestamp: Long, // 搜索开始的时间戳
    val source: String, // 来源, e.g., "悬浮窗", "灵动岛"
    val durationInMillis: Long, // 搜索窗口持续的时长
    val engines: String // 使用的搜索引擎，逗号分隔
) 