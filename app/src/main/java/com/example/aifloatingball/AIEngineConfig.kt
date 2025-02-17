package com.example.aifloatingball

object AIEngineConfig {
    val engines = listOf(
        // 国内搜索引擎
        AIEngine("百度", "https://www.baidu.com/s?wd={query}", R.drawable.ic_search_engine),
        AIEngine("搜狗", "https://www.sogou.com/web?query={query}", R.drawable.ic_search_engine),
        AIEngine("360搜索", "https://www.so.com/s?q={query}", R.drawable.ic_search_engine),
        AIEngine("必应中国", "https://cn.bing.com/search?q={query}", R.drawable.ic_search_engine),
        
        // 国内AI模型
        AIEngine("文心一言", "https://yiyan.baidu.com/", R.drawable.ic_search_engine),
        AIEngine("通义千问", "https://qianwen.aliyun.com/", R.drawable.ic_search_engine),
        AIEngine("讯飞星火", "https://xinghuo.xfyun.cn/", R.drawable.ic_search_engine),
        AIEngine("豆包", "https://www.doubao.com/", R.drawable.ic_search_engine),
        AIEngine("智谱清言", "https://chatglm.cn/", R.drawable.ic_search_engine),
        AIEngine("商汤日日新", "https://chat.sensetime.com/", R.drawable.ic_search_engine),
        AIEngine("MiniMax", "https://api.minimax.chat/", R.drawable.ic_search_engine),
        AIEngine("月之暗面", "https://www.moonshot.cn/", R.drawable.ic_search_engine),
        
        // 国内AI创作平台
        AIEngine("文心一格", "https://yige.baidu.com/", R.drawable.ic_search_engine),
        AIEngine("阿里通义万相", "https://wanxiang.aliyun.com/", R.drawable.ic_search_engine),
        AIEngine("腾讯意像", "https://image.qq.com/", R.drawable.ic_search_engine),
        
        // 国内AI办公
        AIEngine("讯飞智文", "https://zhiwen.xfyun.cn/", R.drawable.ic_search_engine),
        AIEngine("金山文档AI", "https://www.kdocs.cn/", R.drawable.ic_search_engine),
        AIEngine("飞书多维表格", "https://bitable.feishu.cn/", R.drawable.ic_search_engine),
        AIEngine("阿里云智能助手", "https://www.aliyun.com/activity/intelligent/assistant", R.drawable.ic_search_engine),
        AIEngine("腾讯智影", "https://zenvideo.qq.com/", R.drawable.ic_search_engine),
        
        // 国际搜索引擎
        AIEngine("Google", "https://www.google.com/search?q={query}", R.drawable.ic_search_engine),
        AIEngine("Bing", "https://www.bing.com/search?q={query}", R.drawable.ic_search_engine),
        
        // 国际AI模型
        AIEngine("ChatGPT", "https://chat.openai.com/", R.drawable.ic_search_engine),
        AIEngine("Claude", "https://claude.ai/", R.drawable.ic_search_engine),
        AIEngine("Gemini", "https://gemini.google.com/", R.drawable.ic_search_engine),
        AIEngine("Copilot", "https://copilot.microsoft.com/", R.drawable.ic_search_engine),
        
        // 国际AI创作
        AIEngine("Midjourney", "https://www.midjourney.com/", R.drawable.ic_search_engine),
        AIEngine("DALL·E", "https://labs.openai.com/", R.drawable.ic_search_engine),
        AIEngine("Stable Diffusion", "https://stability.ai/", R.drawable.ic_search_engine),
        AIEngine("Runway", "https://runway.ml/", R.drawable.ic_search_engine)
    )
} 