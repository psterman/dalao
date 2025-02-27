package com.example.aifloatingball.model

data class AISearchEngine(
    val id: String,
    val name: String,
    val url: String,
    val iconResId: Int,
    var isEnabled: Boolean = true,
    val category: Category = Category.AI,
    val description: String = ""
) {
    enum class Category {
        AI,
        SEARCH,
        WRITING,
        GENERAL
    }

    companion object {
        val DEFAULT_AI_ENGINES = listOf(
            AISearchEngine(
                "cueme",
                "CueMe写作",
                "https://cueme.cn/",
                android.R.drawable.ic_menu_edit,
                description = "专业的AI写作助手"
            ),
            AISearchEngine(
                "grok",
                "Grok",
                "https://grok.x.ai/",
                android.R.drawable.ic_dialog_email,
                description = "X公司的AI助手"
            ),
            AISearchEngine(
                "mita",
                "秘塔AI",
                "https://www.metaphor.com/",
                android.R.drawable.ic_menu_search,
                description = "智能搜索引擎"
            ),
            AISearchEngine(
                "perplexity",
                "Perplexity",
                "https://www.perplexity.ai/",
                android.R.drawable.ic_menu_help,
                description = "AI搜索引擎"
            ),
            AISearchEngine(
                "baixiao",
                "百小应",
                "https://chat.baidu.com/",
                android.R.drawable.ic_menu_compass,
                description = "百度AI助手"
            ),
            AISearchEngine(
                "hailuo",
                "海螺AI",
                "https://hailuo.ai/",
                android.R.drawable.ic_menu_rotate,
                description = "智能对话助手"
            ),
            AISearchEngine(
                "xiaoyi",
                "华为小艺",
                "https://xiaoyi.huawei.com/chat/",
                android.R.drawable.ic_menu_send,
                description = "华为AI助手"
            ),
            AISearchEngine(
                "gemini",
                "Gemini",
                "https://gemini.google.com/",
                android.R.drawable.ic_menu_view,
                description = "Google AI助手"
            ),
            AISearchEngine(
                "poe",
                "Poe",
                "https://poe.com/",
                android.R.drawable.ic_dialog_info,
                description = "多模型AI平台"
            ),
            AISearchEngine(
                "meta",
                "Meta AI",
                "https://ai.meta.com/",
                android.R.drawable.ic_menu_compass,
                description = "Meta AI助手"
            ),
            AISearchEngine(
                "tiangong",
                "天工AI",
                "https://tiangong.kunlun.com/",
                android.R.drawable.ic_menu_zoom,
                description = "昆仑万维AI助手"
            ),
            AISearchEngine(
                "copilot",
                "Copilot",
                "https://copilot.microsoft.com/",
                android.R.drawable.ic_menu_edit,
                description = "微软AI助手"
            ),
            AISearchEngine(
                "nanoai",
                "纳米AI",
                "https://nanoai.com/",
                android.R.drawable.ic_menu_view,
                description = "智能AI助手"
            ),
            AISearchEngine(
                "claude",
                "Claude",
                "https://claude.ai/",
                android.R.drawable.ic_menu_help,
                description = "Anthropic AI助手"
            ),
            AISearchEngine(
                "wenxiaobai",
                "问小白",
                "https://www.wenxiaobai.com/chat/",
                android.R.drawable.ic_dialog_email,
                description = "智能问答助手"
            )
        )
    }
} 