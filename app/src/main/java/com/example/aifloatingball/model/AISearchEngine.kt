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
    override val searchUrl: String = url
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
                name = "通义千问",
                url = "https://qianwen.aliyun.com",
                iconResId = R.drawable.ic_qianwen,
                description = "阿里通义千问",
                searchUrl = "https://qianwen.aliyun.com/search?query={query}"
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