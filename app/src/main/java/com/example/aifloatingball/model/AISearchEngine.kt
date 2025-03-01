package com.example.aifloatingball.model

class AISearchEngine(
    name: String,
    url: String,
    iconResId: Int,
    description: String,
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
                "cueme",
                "CueMe写作",
                android.R.drawable.ic_menu_edit,
                "专业的AI写作助手"
            ),
            AISearchEngine(
                "grok",
                "Grok",
                android.R.drawable.ic_dialog_email,
                "X公司的AI助手"
            ),
            AISearchEngine(
                "mita",
                "秘塔AI",
                android.R.drawable.ic_menu_search,
                "智能搜索引擎"
            ),
            AISearchEngine(
                "perplexity",
                "Perplexity",
                android.R.drawable.ic_menu_help,
                "AI搜索引擎"
            ),
            AISearchEngine(
                "baixiao",
                "百小应",
                android.R.drawable.ic_menu_compass,
                "百度AI助手"
            ),
            AISearchEngine(
                "hailuo",
                "海螺AI",
                android.R.drawable.ic_menu_rotate,
                "智能对话助手"
            ),
            AISearchEngine(
                "xiaoyi",
                "华为小艺",
                android.R.drawable.ic_menu_send,
                "华为AI助手"
            ),
            AISearchEngine(
                "gemini",
                "Gemini",
                android.R.drawable.ic_menu_view,
                "Google AI助手"
            ),
            AISearchEngine(
                "poe",
                "Poe",
                android.R.drawable.ic_dialog_info,
                "多模型AI平台"
            ),
            AISearchEngine(
                "meta",
                "Meta AI",
                android.R.drawable.ic_menu_compass,
                "Meta AI助手"
            ),
            AISearchEngine(
                "tiangong",
                "天工AI",
                android.R.drawable.ic_menu_zoom,
                "昆仑万维AI助手"
            ),
            AISearchEngine(
                "copilot",
                "Copilot",
                android.R.drawable.ic_menu_edit,
                "微软AI助手"
            ),
            AISearchEngine(
                "nanoai",
                "纳米AI",
                android.R.drawable.ic_menu_view,
                "智能AI助手"
            ),
            AISearchEngine(
                "claude",
                "Claude",
                android.R.drawable.ic_menu_help,
                "Anthropic AI助手"
            ),
            AISearchEngine(
                "wenxiaobai",
                "问小白",
                android.R.drawable.ic_dialog_email,
                "智能问答助手"
            )
        )
    }
} 