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
                R.drawable.ic_search,
                "Anthropic Claude"
            ),
            AISearchEngine(
                "Bard",
                "https://bard.google.com",
                R.drawable.ic_search,
                "Google Bard"
            )
        )
    }
} 