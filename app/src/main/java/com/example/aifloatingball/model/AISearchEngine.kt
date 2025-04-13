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
                "https://tongyi.aliyun.com/qianwen/",
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
                "DeepSeek",
                "https://chat.deepseek.com",
                R.drawable.ic_search,
                "DeepSeek Chat"
            ),
            AISearchEngine(
                "Kimi",
                "https://kimi.moonshot.cn",
                R.drawable.ic_search,
                "Moonshot AI Kimi"
            ),
            AISearchEngine(
                "百小度",
                "https://xiaodong.baidu.com",
                R.drawable.ic_baidu,
                "百度小度对话"
            ),
            AISearchEngine(
                "豆包",
                "https://doubao.com",
                R.drawable.ic_search,
                "字节豆包"
            ),
            AISearchEngine(
                "腾讯混元",
                "https://hunyuan.tencent.com",
                R.drawable.ic_search,
                "腾讯混元大模型"
            ),
            AISearchEngine(
                "秘塔AI",
                "https://meta-ai.com",
                R.drawable.ic_search,
                "秘塔AI助手"
            ),
            AISearchEngine(
                "Poe",
                "https://poe.com",
                R.drawable.ic_search,
                "Poe AI平台"
            ),
            AISearchEngine(
                "Perplexity",
                "https://perplexity.ai",
                R.drawable.ic_perplexity,
                "Perplexity AI搜索"
            ),
            AISearchEngine(
                "天工AI",
                "https://tiangong.kunlun.com",
                R.drawable.ic_search,
                "昆仑万维天工"
            ),
            AISearchEngine(
                "Grok",
                "https://grok.x.ai",
                R.drawable.ic_grok,
                "X Grok AI"
            ),
            AISearchEngine(
                "小Yi",
                "https://xiaoyi.baidu.com",
                R.drawable.ic_baidu,
                "百度小Yi"
            ),
            AISearchEngine(
                "Monica",
                "https://monica.im",
                R.drawable.ic_search,
                "Monica AI助手"
            ),
            AISearchEngine(
                "You",
                "https://you.com",
                R.drawable.ic_search,
                "You AI搜索"
            ),
            AISearchEngine(
                "Pi",
                "https://pi.ai",
                R.drawable.ic_search,
                "Pi AI助手"
            ),
            AISearchEngine(
                "Character.AI",
                "https://character.ai",
                R.drawable.ic_search,
                "Character.AI对话"
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
            "通义千问" -> "https://tongyi.aliyun.com/qianwen/"
            "讯飞星火" -> "https://xinghuo.xfyun.cn"
            "DeepSeek" -> "https://chat.deepseek.com"
            "Kimi" -> "https://kimi.moonshot.cn"
            "百小度" -> "https://xiaodong.baidu.com"
            "豆包" -> "https://doubao.com"
            "腾讯混元" -> "https://hunyuan.tencent.com"
            "秘塔AI" -> "https://meta-ai.com"
            "Poe" -> "https://poe.com"
            "Perplexity" -> "https://perplexity.ai/search?q=$encodedQuery"
            "天工AI" -> "https://tiangong.kunlun.com"
            "Grok" -> "https://grok.x.ai"
            "小Yi" -> "https://xiaoyi.baidu.com"
            "Monica" -> "https://monica.im"
            "You" -> "https://you.com/search?q=$encodedQuery"
            "Pi" -> "https://pi.ai"
            "Character.AI" -> "https://character.ai"
            else -> url
        }
    }
} 