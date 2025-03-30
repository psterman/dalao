package com.example.aifloatingball.model

data class MenuItem(
    val name: String,
    val iconRes: Int,
    val url: String,
    val category: MenuCategory,
    var isEnabled: Boolean
)

enum class MenuCategory {
    AI_SEARCH,      // AI搜索引擎
    NORMAL_SEARCH,  // 普通搜索引擎
    FUNCTION        // 功能
} 