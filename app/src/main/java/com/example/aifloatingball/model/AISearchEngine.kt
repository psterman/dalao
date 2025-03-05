package com.example.aifloatingball.model

import com.example.aifloatingball.R

class AISearchEngine(
    name: String,
    url: String,
    iconResId: Int,
    description: String = "",
    var isEnabled: Boolean = true
) : SearchEngine(name, url, iconResId, description) {
    enum class Category {
        AI,
        SEARCH,
        WRITING,
        GENERAL
    }

    companion object {
        val DEFAULT_AI_ENGINES = listOf(
            AISearchEngine(
                "ChatGPT",
                "https://chat.openai.com/",
                R.drawable.ic_chatgpt,
                "OpenAI 官方 ChatGPT"
            ),
            AISearchEngine(
                "Claude",
                "https://claude.ai/",
                R.drawable.ic_claude,
                "Anthropic Claude AI"
            ),
            AISearchEngine(
                "Gemini",
                "https://gemini.google.com/",
                R.drawable.ic_gemini,
                "Google Gemini AI"
            ),
            AISearchEngine(
                name = "文心一言",
                url = "https://yiyan.baidu.com",
                iconResId = R.drawable.ic_menu_edit,
                description = "百度AI助手"
            ),
            AISearchEngine(
                name = "通义千问",
                url = "https://qianwen.aliyun.com",
                iconResId = R.drawable.ic_menu_help,
                description = "阿里AI助手"
            ),
            AISearchEngine(
                name = "讯飞星火",
                url = "https://xinghuo.xfyun.cn",
                iconResId = R.drawable.ic_menu_search,
                description = "讯飞AI助手"
            ),
            AISearchEngine(
                name = "Copilot",
                url = "https://copilot.microsoft.com",
                iconResId = R.drawable.ic_menu_manage,
                description = "微软AI助手"
            ),
            AISearchEngine(
                name = "豆包",
                url = "https://www.doubao.com",
                iconResId = R.drawable.ic_menu_compass,
                description = "字节跳动AI助手"
            ),
            AISearchEngine(
                name = "Kimi",
                url = "https://kimi.moonshot.cn",
                iconResId = R.drawable.ic_menu_rotate,
                description = "Moonshot AI助手"
            ),
            AISearchEngine(
                name = "秘塔AI",
                url = "https://metaso.cn",
                iconResId = R.drawable.ic_menu_send,
                description = "智能搜索引擎"
            ),
            AISearchEngine(
                name = "DeepSeek",
                url = "https://chat.deepseek.com",
                iconResId = R.drawable.ic_menu_agenda,
                description = "DeepSeek AI助手"
            ),
            AISearchEngine(
                name = "Perplexity",
                url = "https://www.perplexity.ai",
                iconResId = R.drawable.ic_menu_zoom,
                description = "AI搜索引擎"
            ),
            AISearchEngine(
                name = "百小应",
                url = "https://yiyan.baidu.com",
                iconResId = R.drawable.ic_menu_today,
                description = "百度AI助手"
            ),
            AISearchEngine(
                name = "华为小艺",
                url = "https://xiaoyi.huawei.com/chat/",
                iconResId = R.drawable.ic_menu_call,
                description = "华为AI助手"
            ),
            AISearchEngine(
                name = "Poe",
                url = "https://poe.com",
                iconResId = R.drawable.ic_menu_camera,
                description = "多模型AI平台"
            ),
            AISearchEngine(
                name = "Meta AI",
                url = "https://meta.ai",
                iconResId = R.drawable.ic_menu_directions,
                description = "Meta AI助手"
            ),
            AISearchEngine(
                name = "纳米AI",
                url = "https://nanoai.com",
                iconResId = R.drawable.ic_menu_gallery,
                description = "智能AI助手"
            ),
            AISearchEngine(
                name = "问小白",
                url = "https://www.wenxiaobai.com/chat/",
                iconResId = R.drawable.ic_menu_info_details,
                description = "智能问答助手"
            )
        )
    }
} 