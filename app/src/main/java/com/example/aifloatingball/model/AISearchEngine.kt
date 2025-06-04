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
    override val iconResId: Int = 0,
    override val description: String = "",
    override val searchUrl: String = url,
    val isChatMode: Boolean = false
) : BaseSearchEngine, Parcelable {
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
        parcel.writeBoolean(isChatMode)
    }

    override fun describeContents(): Int = 0

    companion object {
        /**
         * 默认AI搜索引擎列表
         */
        @JvmField
        val DEFAULT_AI_ENGINES = listOf(
            AISearchEngine(
                name = "ChatGPT",
                url = "https://chat.openai.com",
                iconResId = R.drawable.ic_chatgpt,
                description = "OpenAI ChatGPT",
                searchUrl = "https://chat.openai.com/search?q={query}"
            ),
            AISearchEngine(
                name = "Claude",
                url = "https://claude.ai",
                iconResId = R.drawable.ic_claude,
                description = "Anthropic Claude",
                searchUrl = "https://claude.ai/search?q={query}"
            ),
            AISearchEngine(
                name = "Gemini",
                url = "https://gemini.google.com",
                iconResId = R.drawable.ic_gemini,
                description = "Google Gemini",
                searchUrl = "https://gemini.google.com/search?q={query}"
            ),
            AISearchEngine(
                name = "文心一言",
                url = "https://yiyan.baidu.com",
                iconResId = R.drawable.ic_wenxin,
                description = "百度文心一言",
                searchUrl = "https://yiyan.baidu.com/search?query={query}"
            ),
            AISearchEngine(
                name = "智谱清言",
                url = "https://chatglm.cn",
                iconResId = R.drawable.ic_chatglm,
                description = "智谱AI清言",
                searchUrl = "https://chatglm.cn/main/explore?query={query}"
            ),
            AISearchEngine(
                name = "通义千问",
                url = "https://qianwen.aliyun.com",
                iconResId = R.drawable.ic_qianwen,
                description = "阿里通义千问",
                searchUrl = "https://qianwen.aliyun.com/search?query={query}"
            ),
            AISearchEngine(
                name = "讯飞星火",
                url = "https://xinghuo.xfyun.cn",
                iconResId = R.drawable.ic_xinghuo,
                description = "讯飞星火认知大模型",
                searchUrl = "https://xinghuo.xfyun.cn/desk?q={query}"
            ),
            AISearchEngine(
                name = "Perplexity",
                url = "https://perplexity.ai",
                iconResId = R.drawable.ic_perplexity,
                description = "Perplexity AI",
                searchUrl = "https://perplexity.ai/search?q={query}"
            ),
            AISearchEngine(
                name = "Phind",
                url = "https://phind.com",
                iconResId = R.drawable.ic_phind,
                description = "Phind - AI搜索引擎",
                searchUrl = "https://phind.com/search?q={query}"
            ),
            AISearchEngine(
                name = "Poe",
                url = "https://poe.com",
                iconResId = R.drawable.ic_poe,
                description = "Poe - 多模型聊天平台",
                searchUrl = "https://poe.com/search?q={query}"
            ),
            AISearchEngine(
                name = "ChatGPT对话",
                url = "chat://chatgpt",
                iconResId = R.drawable.ic_chatgpt,
                description = "使用API进行ChatGPT对话",
                searchUrl = "chat://chatgpt?q={query}",
                isChatMode = true
            ),
            AISearchEngine(
                name = "DeepSeek对话",
                url = "chat://deepseek",
                iconResId = R.drawable.ic_deepseek,
                description = "使用API进行DeepSeek对话",
                searchUrl = "chat://deepseek?q={query}",
                isChatMode = true
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
                    searchUrl = parcel.readString()!!,
                    isChatMode = parcel.readBoolean()
                )
            }

            override fun newArray(size: Int): Array<AISearchEngine?> {
                return arrayOfNulls(size)
            }
        }
    }
}