package com.example.aifloatingball.model

import android.os.Parcel
import android.os.Parcelable
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
data class AISearchEngine(
    override val name: String,
    override val url: String,
    override val iconResId: Int,
    override val description: String = "",
    override val searchUrl: String = url
) : BaseSearchEngine {
    override val displayName: String
        get() = name  // AI搜索引擎的显示名称与名称相同

    override fun getSearchUrl(query: String): String {
        return searchUrl.replace("{query}", query)
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeString(url)
        parcel.writeInt(iconResId)
        parcel.writeString(description)
        parcel.writeString(searchUrl)
    }

    override fun describeContents(): Int = 0

    companion object {
        /**
         * 默认AI搜索引擎列表
         */
        @JvmField
        val DEFAULT_AI_ENGINES = listOf(
            // OpenAI系列
            AISearchEngine(
                name = "ChatGPT",
                url = "https://chat.openai.com/",
                iconResId = R.drawable.ic_chatgpt,
                description = "OpenAI开发的AI聊天助手"
            ),
            AISearchEngine(
                name = "DALL·E",
                url = "https://labs.openai.com/",
                iconResId = R.drawable.ic_search,
                description = "OpenAI的AI图像生成工具"
            ),
            
            // Google系列
            AISearchEngine(
                name = "Gemini",
                url = "https://gemini.google.com/",
                iconResId = R.drawable.ic_gemini,
                description = "Google的AI助手,前身是Bard"
            ),
            AISearchEngine(
                name = "Google AI",
                url = "https://ai.google/",
                iconResId = R.drawable.ic_google,
                description = "Google的AI研发平台"
            ),
            
            // Anthropic
            AISearchEngine(
                name = "Claude",
                url = "https://claude.ai/",
                iconResId = R.drawable.ic_claude,
                description = "Anthropic开发的AI助手"
            ),
            
            // 百度系列
            AISearchEngine(
                name = "文心一言",
                url = "https://yiyan.baidu.com/chat?q={query}",
                iconResId = R.drawable.ic_wenxin,
                description = "百度开发的大语言模型"
            ),
            AISearchEngine(
                name = "文心一格",
                url = "https://yige.baidu.com/",
                iconResId = R.drawable.ic_wenxin,
                description = "百度AI图像创作平台"
            ),
            
            // 阿里系列
            AISearchEngine(
                name = "通义千问",
                url = "https://qianwen.aliyun.com/chat?q={query}",
                iconResId = R.drawable.ic_qianwen,
                description = "阿里开发的大语言模型"
            ),
            AISearchEngine(
                name = "通义万相",
                url = "https://wanxiang.aliyun.com/",
                iconResId = R.drawable.ic_qianwen,
                description = "阿里AI图像生成平台"
            ),
            
            // 讯飞系列
            AISearchEngine(
                name = "讯飞星火",
                url = "https://xinghuo.xfyun.cn/",
                iconResId = R.drawable.ic_xinghuo,
                description = "科大讯飞开发的大语言模型"
            ),
            AISearchEngine(
                name = "讯飞智文",
                url = "https://zhiwen.xfyun.cn/",
                iconResId = R.drawable.ic_xinghuo,
                description = "科大讯飞AI文档助手"
            ),
            
            // 腾讯系列
            AISearchEngine(
                name = "腾讯混元",
                url = "https://hunyuan.tencent.com/",
                iconResId = R.drawable.ic_search,
                description = "腾讯开发的大语言模型"
            ),
            AISearchEngine(
                name = "腾讯意像",
                url = "https://image.qq.com/",
                iconResId = R.drawable.ic_search,
                description = "腾讯AI图像生成平台"
            ),
            
            // 字节系列
            AISearchEngine(
                name = "豆包",
                url = "https://www.doubao.com/",
                iconResId = R.drawable.ic_search,
                description = "字节跳动开发的大语言模型"
            ),
            
            // 微软系列
            AISearchEngine(
                name = "Copilot",
                url = "https://copilot.microsoft.com/",
                iconResId = R.drawable.ic_bing,
                description = "微软AI助手,集成在必应中"
            ),
            
            // 其他国内AI
            AISearchEngine(
                name = "智谱清言",
                url = "https://chatglm.cn/",
                iconResId = R.drawable.ic_search,
                description = "智谱AI开发的大语言模型"
            ),
            AISearchEngine(
                name = "商汤日日新",
                url = "https://chat.sensetime.com/",
                iconResId = R.drawable.ic_search,
                description = "商汤科技的AI助手"
            ),
            AISearchEngine(
                name = "MiniMax",
                url = "https://api.minimax.chat/",
                iconResId = R.drawable.ic_search,
                description = "MiniMax开发的大语言模型"
            ),
            AISearchEngine(
                name = "月之暗面",
                url = "https://www.moonshot.cn/",
                iconResId = R.drawable.ic_search,
                description = "月之暗面开发的大语言模型"
            ),
            
            // 国外其他AI
            AISearchEngine(
                name = "Perplexity",
                url = "https://www.perplexity.ai/",
                iconResId = R.drawable.ic_perplexity,
                description = "AI驱动的搜索引擎"
            ),
            AISearchEngine(
                name = "Poe",
                url = "https://poe.com/",
                iconResId = R.drawable.ic_poe,
                description = "Quora开发的AI聊天平台"
            ),
            AISearchEngine(
                name = "Midjourney",
                url = "https://www.midjourney.com/",
                iconResId = R.drawable.ic_search,
                description = "先进的AI图像生成服务"
            ),
            AISearchEngine(
                name = "Stable Diffusion",
                url = "https://stability.ai/",
                iconResId = R.drawable.ic_search,
                description = "开源AI图像生成模型"
            ),
            AISearchEngine(
                name = "Runway",
                url = "https://runway.ml/",
                iconResId = R.drawable.ic_search,
                description = "AI视频和图像创作平台"
            ),
            AISearchEngine(
                name = "Phind",
                url = "https://www.phind.com/",
                iconResId = R.drawable.ic_search,
                description = "面向开发者的AI搜索引擎"
            ),
            AISearchEngine(
                name = "You.com",
                url = "https://you.com/",
                iconResId = R.drawable.ic_search,
                description = "AI驱动的搜索引擎"
            ),
            AISearchEngine(
                name = "Character.AI",
                url = "https://character.ai/",
                iconResId = R.drawable.ic_search,
                description = "AI角色聊天平台"
            ),
            AISearchEngine(
                name = "Pi by Inflection",
                url = "https://pi.ai/",
                iconResId = R.drawable.ic_search,
                description = "个人AI助手"
            )
        )

        @JvmField
        val CREATOR = object : Parcelable.Creator<AISearchEngine> {
            override fun createFromParcel(parcel: Parcel): AISearchEngine {
                return AISearchEngine(
                    name = parcel.readString()!!,
                    url = parcel.readString()!!,
                    iconResId = parcel.readInt(),
                    description = parcel.readString()!!,
                    searchUrl = parcel.readString()!!
                )
            }

            override fun newArray(size: Int): Array<AISearchEngine?> {
                return arrayOfNulls(size)
            }
        }
    }
}