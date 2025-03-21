package com.example.aifloatingball.models

/**
 * 表示搜索引擎的数据模型
 */
data class SearchEngine(
    val id: String,
    val name: String,
    val url: String,
    val iconUrl: String? = null,
    val isDefault: Boolean = false,
    val searchUrlTemplate: String = "$url/search?q=%s"
) {
    /**
     * 获取搜索链接
     * @param query 搜索关键词
     * @return 完整的搜索URL
     */
    fun getSearchUrl(query: String): String {
        return searchUrlTemplate.replace("%s", query)
    }
    
    /**
     * 获取搜索引擎的首字母（大写）
     */
    fun getInitial(): Char {
        return if (name.isNotEmpty()) name.first().uppercaseChar() else ' '
    }
} 