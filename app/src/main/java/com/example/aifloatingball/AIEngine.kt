package com.example.aifloatingball

data class AIEngine(
    val name: String,
    val url: String,
    val iconResId: Int
) {
    fun getSearchUrl(query: String): String {
        return when (name) {
            "Google" -> "https://www.google.com/search?q=$query"
            "Bing" -> "https://www.bing.com/search?q=$query"
            "百度" -> "https://www.baidu.com/s?wd=$query"
            "搜狗" -> "https://www.sogou.com/web?query=$query"
            "360搜索" -> "https://www.so.com/s?q=$query"
            "必应中国" -> "https://cn.bing.com/search?q=$query"
            else -> if (url.contains("{query}")) {
                url.replace("{query}", query)
            } else {
                url
            }
        }
    }
} 