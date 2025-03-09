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
    val name: String,
    val url: String,
    val iconResId: Int,
    val description: String = ""  // 添加description字段，并提供默认空字符串值
) {
    // 添加次级构造函数以兼容旧代码
    constructor(name: String, url: String, iconResId: Int) : this(name, url, iconResId, "")

    companion object {
        /**
         * 默认AI搜索引擎列表
         */
        val DEFAULT_AI_ENGINES by lazy {
            Log.d("AISearchEngine", "初始化AI搜索引擎列表")
            listOf(
                AISearchEngine("ChatGPT", "https://chat.openai.com", R.drawable.ic_chatgpt, "ChatGPT AI聊天"),
                AISearchEngine("Claude", "https://claude.ai", R.drawable.ic_claude, "Claude AI助手"),
                AISearchEngine("Gemini", "https://gemini.google.com", R.drawable.ic_gemini, "Google Gemini AI"),
                AISearchEngine("Bing Chat", "https://www.bing.com/chat", R.drawable.ic_search, "微软必应AI聊天"),
                AISearchEngine("百度文心一言", "https://yiyan.baidu.com", R.drawable.ic_search, "百度AI大语言模型"),
                AISearchEngine("讯飞星火", "https://xinghuo.xfyun.cn", R.drawable.ic_search, "讯飞AI大模型"),
                AISearchEngine("通义千问", "https://qianwen.aliyun.com", R.drawable.ic_search, "阿里AI大模型"),
                AISearchEngine("Perplexity", "https://www.perplexity.ai", R.drawable.ic_search, "AI搜索引擎"),
                AISearchEngine("Anthropic Claude", "https://www.anthropic.com/claude", R.drawable.ic_claude, "Anthropic Claude AI"),
                AISearchEngine("Poe", "https://poe.com", R.drawable.ic_search, "集成多种AI的平台"),
                AISearchEngine(
                    name = "豆包",
                    url = "https://www.doubao.com/chat",
                    iconResId = R.drawable.ic_chatgpt, // 需要替换为豆包图标
                    description = "字节跳动豆包AI"
                ),
                AISearchEngine(
                    name = "DeepSeek",
                    url = "https://chat.deepseek.com",
                    iconResId = R.drawable.ic_chatgpt, // 需要替换为DeepSeek图标
                    description = "DeepSeek AI对话"
                ),
                AISearchEngine(
                    name = "华为小艺",
                    url = "https://xiaoyi.huawei.com/chat/",
                    iconResId = R.drawable.ic_chatgpt, // 需要替换为华为小艺图标
                    description = "华为小艺AI助手"
                ),
                AISearchEngine(
                    name = "腾讯混沌",
                    url = "https://hunyuan.tencent.com",
                    iconResId = R.drawable.ic_chatgpt, // 需要替换为腾讯混沌图标
                    description = "腾讯混沌大模型"
                ),
                AISearchEngine(
                    name = "秘塔写作",
                    url = "https://xiezuocat.com",
                    iconResId = R.drawable.ic_chatgpt, // 需要替换为秘塔图标
                    description = "秘塔AI写作助手"
                ),
                AISearchEngine(
                    name = "海螺AI",
                    url = "https://chat.conch.ai",
                    iconResId = R.drawable.ic_chatgpt, // 需要替换为海螺图标
                    description = "海螺AI对话"
                ),
                AISearchEngine(
                    name = "天工AI",
                    url = "https://chat.tiangong.cn",
                    iconResId = R.drawable.ic_chatgpt, // 需要替换为天工图标
                    description = "昆仑万维天工AI"
                ),
                AISearchEngine(
                    name = "纳米AI",
                    url = "https://www.nami.ai",
                    iconResId = R.drawable.ic_chatgpt, // 需要替换为纳米图标
                    description = "纳米AI助手"
                ),
                AISearchEngine(
                    name = "Grok",
                    url = "https://grok.x.ai",
                    iconResId = R.drawable.ic_chatgpt, // 需要替换为Grok图标
                    description = "X.AI Grok"
                ),
                AISearchEngine(
                    name = "GitHub Copilot",
                    url = "https://github.com/features/copilot",
                    iconResId = R.drawable.ic_chatgpt, // 需要替换为Copilot图标
                    description = "GitHub AI编程助手"
                ),
                AISearchEngine(
                    name = "问小白",
                    url = "https://www.wenxiaobai.com/chat/",
                    iconResId = R.drawable.ic_chatgpt, // 需要替换为问小白图标
                    description = "问小白AI助手"
                )
            ).also {
                Log.d("AISearchEngine", "AI搜索引擎列表初始化完成，共有${it.size}个引擎")
            }
        }
    }
} 