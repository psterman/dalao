package com.example.aifloatingball

data class AIEngine(
    val name: String,
    val url: String,
    val iconResId: Int = android.R.drawable.ic_dialog_info // 默认图标，后续可以替换为实际的图标资源
)

object AIEngineConfig {
    val engines = listOf(
        AIEngine("Kimi", "https://kimi.moonshot.cn/"),
        AIEngine("DeepSeek", "https://chat.deepseek.com/"),
        AIEngine("豆包", "https://www.doubao.com/"),
        AIEngine("ChatGPT", "https://chat.openai.com/"),
        AIEngine("秘塔", "https://metaso.cn/"),
        AIEngine("元宝", "https://api.minimax.chat/"),
        AIEngine("阿里通义", "https://tongyi.aliyun.com/"),
        AIEngine("Perplexity", "https://www.perplexity.ai/"),
        AIEngine("文心一言", "https://yiyan.baidu.com/"),
        AIEngine("讯飞星火", "https://xinghuo.xfyun.cn/"),
        AIEngine("智谱AI", "https://chatglm.cn/")
    )
} 