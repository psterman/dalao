package com.example.aifloatingball.model

import com.example.aifloatingball.R
import android.util.Log

/**
 * AI搜索引擎数据类
 * 
 * @param name 搜索引擎名称
 * @param url 搜索引擎URL
 * @param iconResId 搜索引擎图标资源ID
 * @param description 搜索引擎描述
 */
class AISearchEngine(
    name: String,
    url: String,
    iconResId: Int,
    description: String
) : SearchEngine(name, url, iconResId, description) {
    
    companion object {
        /**
         * 默认AI搜索引擎列表
         */
        val DEFAULT_AI_ENGINES = listOf(
            AISearchEngine(
                "ChatGPT",
                "https://chat.openai.com",
                R.drawable.ic_chatgpt,
                "OpenAI ChatGPT"
            ),
            AISearchEngine(
                "Claude",
                "https://claude.ai",
                R.drawable.ic_claude,
                "Anthropic Claude"
            ),
            AISearchEngine(
                "Gemini",
                "https://gemini.google.com",
                R.drawable.ic_gemini,
                "Google Gemini"
            ),
            AISearchEngine(
                "文心一言",
                "https://yiyan.baidu.com",
                R.drawable.ic_baidu,
                "百度文心一言"
            ),
            AISearchEngine(
                "智谱清言",
                "https://chatglm.cn",
                R.drawable.ic_search,
                "智谱AI清言"
            ),
            AISearchEngine(
                "通义千问",
                "https://qianwen.aliyun.com",
                R.drawable.ic_qianwen,
                "阿里通义千问"
            ),
            AISearchEngine(
                "讯飞星火",
                "https://xinghuo.xfyun.cn",
                R.drawable.ic_xinghuo,
                "讯飞星火认知大模型"
            ),
            AISearchEngine(
                "Perplexity",
                "https://perplexity.ai",
                R.drawable.ic_perplexity,
                "Perplexity AI搜索"
            ),
            AISearchEngine(
                "Phind",
                "https://phind.com",
                R.drawable.ic_search,
                "Phind AI搜索"
            ),
            AISearchEngine(
                "Poe",
                "https://poe.com",
                R.drawable.ic_search,
                "Poe AI平台"
            )
        )
    }
    
    /**
     * 获取AI搜索引擎URL
     */
    fun getAISearchUrl(query: String): String {
        val encodedQuery = android.net.Uri.encode(query)
        return when(name) {
            "ChatGPT" -> "https://chat.openai.com"
            "Claude" -> "https://claude.ai"
            "Gemini" -> "https://gemini.google.com"
            "文心一言" -> "https://yiyan.baidu.com"
            "智谱清言" -> "https://chatglm.cn"
            "通义千问" -> "https://qianwen.aliyun.com"
            "讯飞星火" -> "https://xinghuo.xfyun.cn"
            "Perplexity" -> "https://perplexity.ai/search?q=$encodedQuery"
            "Phind" -> "https://phind.com/search?q=$encodedQuery"
            "Poe" -> "https://poe.com/search?q=$encodedQuery"
            else -> url
        }
    }
} 