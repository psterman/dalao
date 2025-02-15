package com.example.aifloatingball

object AIEngineConfig {
    val engines = listOf(
        AIEngine(
            name = "ChatGPT",
            url = "https://chat.openai.com",
            iconResId = R.drawable.ic_chatgpt
        ),
        AIEngine(
            name = "Claude",
            url = "https://claude.ai",
            iconResId = R.drawable.ic_claude
        ),
        AIEngine(
            name = "文心一言",
            url = "https://yiyan.baidu.com",
            iconResId = R.drawable.ic_wenxin
        ),
        AIEngine(
            name = "讯飞星火",
            url = "https://xinghuo.xfyun.cn",
            iconResId = R.drawable.ic_xinghuo
        ),
        AIEngine(
            name = "通义千问",
            url = "https://qianwen.aliyun.com",
            iconResId = R.drawable.ic_qianwen
        ),
        AIEngine(
            name = "豆包",
            url = "https://www.doubao.com",
            iconResId = R.drawable.ic_doubao
        )
    )
} 