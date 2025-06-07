package com.example.aifloatingball.model

import android.os.Parcel
import android.os.Parcelable
import com.example.aifloatingball.R

/**
 * 搜索引擎数据模型
 */
data class SearchEngine(
    override val name: String,
    override val url: String,
    override val iconResId: Int = 0,
    override val description: String = "",
    override val searchUrl: String = url,
    val isAI: Boolean = false,
    val iconUrl: String? = null,
    val category: SearchEngineCategory = SearchEngineCategory.GENERAL
) : BaseSearchEngine, Parcelable {
    override fun getSearchUrl(query: String): String {
        return searchUrl.replace("{query}", query)
    }

    companion object {
        // 默认搜索引擎列表
        val DEFAULT_ENGINES = listOf(
            // --- 通用分类 ---
            SearchEngine(
                name = "baidu",
                url = "https://www.baidu.com",
                iconResId = R.drawable.ic_baidu,
                description = "百度搜索",
                searchUrl = "https://www.baidu.com/s?wd={query}",
                category = SearchEngineCategory.GENERAL
            ),
            SearchEngine(
                name = "google",
                url = "https://www.google.com",
                iconResId = R.drawable.ic_google,
                description = "Google搜索",
                searchUrl = "https://www.google.com/search?q={query}",
                category = SearchEngineCategory.GENERAL
            ),
            SearchEngine(
                name = "bing",
                url = "https://www.bing.com",
                iconResId = R.drawable.ic_bing,
                description = "Microsoft Bing",
                searchUrl = "https://www.bing.com/search?q={query}",
                category = SearchEngineCategory.GENERAL
            ),
            SearchEngine(
                name = "sogou",
                url = "https://www.sogou.com",
                iconResId = R.drawable.ic_sogou,
                description = "搜狗搜索",
                searchUrl = "https://www.sogou.com/web?query={query}",
                category = SearchEngineCategory.GENERAL
            ),
            SearchEngine(
                name = "360",
                url = "https://www.so.com",
                iconResId = R.drawable.ic_360,
                description = "360搜索",
                searchUrl = "https://www.so.com/s?q={query}",
                category = SearchEngineCategory.GENERAL
            ),
            SearchEngine(
                name = "duckduckgo",
                url = "https://duckduckgo.com",
                iconResId = R.drawable.ic_duckduckgo,
                description = "DuckDuckGo",
                searchUrl = "https://duckduckgo.com/?q={query}",
                category = SearchEngineCategory.GENERAL
            ),
            SearchEngine(
                name = "yandex",
                url = "https://yandex.com",
                iconResId = R.drawable.ic_search,
                description = "Yandex",
                searchUrl = "https://yandex.com/search/?text={query}",
                category = SearchEngineCategory.GENERAL
            ),
            SearchEngine(
                name = "yahoo",
                url = "https://search.yahoo.com",
                iconResId = R.drawable.ic_search,
                description = "Yahoo Search",
                searchUrl = "https://search.yahoo.com/search?p={query}",
                category = SearchEngineCategory.GENERAL
            ),
            SearchEngine(
                name = "ecosia",
                url = "https://www.ecosia.org",
                iconResId = R.drawable.ic_search,
                description = "Ecosia",
                searchUrl = "https://www.ecosia.org/search?q={query}",
                category = SearchEngineCategory.GENERAL
            ),
            SearchEngine(
                name = "brave",
                url = "https://search.brave.com",
                iconResId = R.drawable.ic_search,
                description = "Brave Search",
                searchUrl = "https://search.brave.com/search?q={query}",
                category = SearchEngineCategory.GENERAL
            ),

            // --- 社交分类 ---
            SearchEngine(
                name = "weibo",
                url = "https://www.weibo.com",
                iconResId = R.drawable.ic_weibo,
                description = "微博",
                searchUrl = "https://s.weibo.com/weibo?q={query}",
                category = SearchEngineCategory.SOCIAL
            ),
            SearchEngine(
                name = "douban",
                url = "https://www.douban.com",
                iconResId = R.drawable.ic_douban,
                description = "豆瓣",
                searchUrl = "https://www.douban.com/search?q={query}",
                category = SearchEngineCategory.SOCIAL
            ),
             SearchEngine(
                name = "xiaohongshu",
                url = "https://www.xiaohongshu.com",
                iconResId = R.drawable.ic_xiaohongshu,
                description = "小红书",
                searchUrl = "https://www.xiaohongshu.com/search_result?keyword={query}",
                category = SearchEngineCategory.SOCIAL
            ),

            // --- 购物分类 ---
            SearchEngine(
                name = "taobao",
                url = "https://www.taobao.com",
                iconResId = R.drawable.ic_taobao,
                description = "淘宝",
                searchUrl = "https://s.taobao.com/search?q={query}",
                category = SearchEngineCategory.SHOPPING
            ),
            SearchEngine(
                name = "jd",
                url = "https://www.jd.com",
                iconResId = R.drawable.ic_jd,
                description = "京东",
                searchUrl = "https://search.jd.com/Search?keyword={query}",
                category = SearchEngineCategory.SHOPPING
            ),
             SearchEngine(
                name = "pinduoduo",
                url = "https://mobile.pinduoduo.com",
                iconResId = R.drawable.ic_search,
                description = "拼多多",
                searchUrl = "https://mobile.pinduoduo.com/search_result.html?keyword={query}",
                category = SearchEngineCategory.SHOPPING
            ),

            // --- 知识分类 ---
            SearchEngine(
                name = "zhihu",
                url = "https://www.zhihu.com",
                iconResId = R.drawable.ic_zhihu,
                description = "知乎",
                searchUrl = "https://www.zhihu.com/search?q={query}",
                category = SearchEngineCategory.KNOWLEDGE
            ),
            SearchEngine(
                name = "wikipedia",
                url = "https://www.wikipedia.org",
                iconResId = R.drawable.ic_search,
                description = "维基百科",
                searchUrl = "https://en.wikipedia.org/wiki/Special:Search?search={query}",
                category = SearchEngineCategory.KNOWLEDGE
            ),

            // --- 视频分类 ---
            SearchEngine(
                name = "bilibili",
                url = "https://www.bilibili.com",
                iconResId = R.drawable.ic_bilibili,
                description = "哔哩哔哩",
                searchUrl = "https://search.bilibili.com/all?keyword={query}",
                category = SearchEngineCategory.VIDEO
            ),
             SearchEngine(
                name = "douyin",
                url = "https://www.douyin.com",
                iconResId = R.drawable.ic_douyin,
                description = "抖音",
                searchUrl = "https://www.douyin.com/search/{query}",
                category = SearchEngineCategory.VIDEO
            ),
            SearchEngine(
                name = "youtube",
                url = "https://www.youtube.com",
                iconResId = R.drawable.ic_search,
                description = "YouTube",
                searchUrl = "https://www.youtube.com/results?search_query={query}",
                category = SearchEngineCategory.VIDEO
            ),

            // --- 开发分类 ---
            SearchEngine(
                name = "github",
                url = "https://github.com",
                iconResId = R.drawable.ic_search,
                description = "GitHub",
                searchUrl = "https://github.com/search?q={query}",
                category = SearchEngineCategory.DEVELOPMENT
            ),
            SearchEngine(
                name = "stackoverflow",
                url = "https://stackoverflow.com",
                iconResId = R.drawable.ic_search,
                description = "Stack Overflow",
                searchUrl = "https://stackoverflow.com/search?q={query}",
                category = SearchEngineCategory.DEVELOPMENT
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
                    isAI = parcel.readByte() != 0.toByte(),
                    iconUrl = parcel.readString(),
                    category = parcel.readSerializable() as SearchEngineCategory
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
        parcel.writeString(iconUrl)
        parcel.writeSerializable(category)
    }

    override fun describeContents(): Int = 0
} 