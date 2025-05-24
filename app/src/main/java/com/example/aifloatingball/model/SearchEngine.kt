package com.example.aifloatingball.model

import android.os.Parcel
import android.os.Parcelable
import com.example.aifloatingball.R

/**
 * 普通搜索引擎数据类
 * 
 * @param name 搜索引擎名称
 * @param url 搜索引擎URL
 * @param iconResId 搜索引擎图标资源ID
 * @param description 搜索引擎描述
 */
data class SearchEngine(
    override val name: String,
    override val url: String,
    override val iconResId: Int,
    override val description: String = "",
    override val searchUrl: String = url,
    val isAI: Boolean = false
) : BaseSearchEngine {
    override fun getSearchUrl(query: String): String {
        return searchUrl.replace("{query}", query)
    }

    companion object {
        @JvmField
        val DEFAULT_ENGINES = listOf(
            SearchEngine(
                name = "百度",
                url = "https://www.baidu.com",
                iconResId = R.drawable.ic_baidu,
                description = "百度搜索",
                searchUrl = "https://www.baidu.com/s?wd={query}"
            ),
            SearchEngine(
                name = "谷歌",
                url = "https://www.google.com",
                iconResId = R.drawable.ic_google,
                description = "Google搜索",
                searchUrl = "https://www.google.com/search?q={query}"
            ),
            SearchEngine(
                name = "必应",
                url = "https://cn.bing.com",
                iconResId = R.drawable.ic_bing,
                description = "微软必应搜索",
                searchUrl = "https://cn.bing.com/search?q={query}"
            ),
            SearchEngine(
                name = "搜狗",
                url = "https://www.sogou.com",
                iconResId = R.drawable.ic_sogou,
                description = "搜狗搜索",
                searchUrl = "https://www.sogou.com/web?query={query}"
            ),
            SearchEngine(
                name = "360搜索",
                url = "https://www.so.com",
                iconResId = R.drawable.ic_360search,
                description = "360搜索",
                searchUrl = "https://www.so.com/s?q={query}"
            ),
            SearchEngine(
                name = "淘宝",
                url = "https://www.taobao.com",
                iconResId = R.drawable.ic_taobao,
                description = "淘宝搜索",
                searchUrl = "https://s.taobao.com/search?q={query}"
            ),
            SearchEngine(
                name = "京东",
                url = "https://www.jd.com",
                iconResId = R.drawable.ic_jd,
                description = "京东搜索",
                searchUrl = "https://search.jd.com/Search?keyword={query}"
            ),
            SearchEngine(
                name = "知乎",
                url = "https://www.zhihu.com",
                iconResId = R.drawable.ic_zhihu,
                description = "知乎搜索",
                searchUrl = "https://www.zhihu.com/search?type=content&q={query}"
            ),
            SearchEngine(
                name = "小红书",
                url = "https://www.xiaohongshu.com",
                iconResId = R.drawable.ic_xiaohongshu,
                description = "小红书搜索",
                searchUrl = "https://www.xiaohongshu.com/search_result?keyword={query}"
            ),
            SearchEngine(
                name = "哔哩哔哩",
                url = "https://www.bilibili.com",
                iconResId = R.drawable.ic_bilibili,
                description = "哔哩哔哩搜索",
                searchUrl = "https://search.bilibili.com/all?keyword={query}"
            ),
            SearchEngine(
                name = "抖音",
                url = "https://www.douyin.com",
                iconResId = R.drawable.ic_douyin,
                description = "抖音搜索",
                searchUrl = "https://www.douyin.com/search/{query}"
            )
        )

        @JvmStatic
        fun getNormalSearchEngines(): List<SearchEngine> = DEFAULT_ENGINES

        @JvmField
        val CREATOR = object : Parcelable.Creator<SearchEngine> {
            override fun createFromParcel(parcel: Parcel): SearchEngine {
                return SearchEngine(
                    name = parcel.readString()!!,
                    url = parcel.readString()!!,
                    iconResId = parcel.readInt(),
                    description = parcel.readString()!!,
                    searchUrl = parcel.readString()!!,
                    isAI = parcel.readByte() != 0.toByte()
                )
            }

            override fun newArray(size: Int): Array<SearchEngine?> {
                return arrayOfNulls(size)
            }
        }

        fun getAllSearchEngines(): List<SearchEngine> {
            return getNormalSearchEngines() + getAISearchEngines()
        }

        fun getAISearchEngines(): List<SearchEngine> = listOf(
            SearchEngine(
                name = "ChatGPT",
                url = "https://chat.openai.com/",
                iconResId = R.drawable.ic_chatgpt,
                description = "OpenAI开发的AI聊天机器人",
                isAI = true
            ),
            SearchEngine(
                name = "Bard",
                url = "https://bard.google.com/",
                iconResId = R.drawable.ic_gemini,
                description = "Google开发的AI助手",
                isAI = true
            ),
            SearchEngine(
                name = "Claude",
                url = "https://claude.ai/",
                iconResId = R.drawable.ic_claude,
                description = "Anthropic开发的AI助手",
                isAI = true
            ),
            SearchEngine(
                name = "文心一言",
                url = "https://yiyan.baidu.com/",
                iconResId = R.drawable.ic_wenxin,
                description = "百度开发的AI助手",
                isAI = true
            ),
            SearchEngine(
                name = "通义千问",
                url = "https://qianwen.aliyun.com/",
                iconResId = R.drawable.ic_qianwen,
                description = "阿里开发的AI助手",
                isAI = true
            ),
            SearchEngine(
                name = "讯飞星火",
                url = "https://xinghuo.xfyun.cn/",
                iconResId = R.drawable.ic_xinghuo,
                description = "科大讯飞开发的AI助手",
                isAI = true
            ),
            SearchEngine(
                name = "Poe",
                url = "https://poe.com/",
                iconResId = R.drawable.ic_poe,
                description = "Quora开发的AI聊天平台",
                isAI = true
            )
        )
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeString(url)
        parcel.writeInt(iconResId)
        parcel.writeString(description)
        parcel.writeString(searchUrl)
        parcel.writeByte(if (isAI) 1.toByte() else 0.toByte())
    }

    override fun describeContents(): Int = 0
} 