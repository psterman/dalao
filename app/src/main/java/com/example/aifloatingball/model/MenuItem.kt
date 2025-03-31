package com.example.aifloatingball.model

data class MenuItem(
    val name: String,
    val iconResId: Int,
    val url: String,
    val category: MenuCategory,
    var isEnabled: Boolean = true,
    var lastUsed: Long = 0
) {
    fun action() {
        // 更新最后使用时间
        lastUsed = System.currentTimeMillis()
    }
}

enum class MenuCategory {
    AI_SEARCH,      // AI搜索引擎
    NORMAL_SEARCH,  // 普通搜索引擎
    FUNCTION        // 功能
} 