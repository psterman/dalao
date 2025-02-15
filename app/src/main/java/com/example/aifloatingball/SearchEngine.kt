package com.example.aifloatingball

data class SearchEngine(
    val name: String,
    val url: String,
    val iconResId: Int
) {
    fun getSearchUrl(query: String): String {
        return when (name) {
            "Google" -> "https://www.google.com/search?q=$query"
            "Bing" -> "https://www.bing.com/search?q=$query"
            "百度" -> "https://www.baidu.com/s?wd=$query"
            "ChatGPT" -> "https://chat.openai.com/"
            "Claude" -> "https://claude.ai/"
            "文心一言" -> "https://yiyan.baidu.com/"
            "通义千问" -> "https://qianwen.aliyun.com/"
            "讯飞星火" -> "https://xinghuo.xfyun.cn/"
            "Gemini" -> "https://gemini.google.com/"
            "Copilot" -> "https://copilot.microsoft.com/"
            "豆包" -> "https://www.doubao.com/"
            else -> url
        }
    }
} 