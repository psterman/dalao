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
    override val searchUrl: String = url
) : BaseSearchEngine {
    override fun getSearchUrl(query: String): String {
        return searchUrl.replace("{query}", query)
    }

    companion object {
        @JvmField
        val DEFAULT_ENGINES = listOf(
            SearchEngine(
                name = "小红书",
                url = "https://www.xiaohongshu.com/explore",
                iconResId = R.drawable.ic_search,
                description = "小红书搜索",
                searchUrl = "https://www.xiaohongshu.com/explore?keyword={query}"
            ),
            SearchEngine(
                name = "什么值得买",
                url = "https://www.smzdm.com",
                iconResId = R.drawable.ic_search,
                description = "什么值得买搜索",
                searchUrl = "https://search.smzdm.com/?s={query}"
            ),
            SearchEngine(
                name = "知乎",
                url = "https://www.zhihu.com",
                iconResId = R.drawable.ic_search,
                description = "知乎搜索",
                searchUrl = "https://www.zhihu.com/search?type=content&q={query}"
            ),
            SearchEngine(
                name = "GitHub",
                url = "https://github.com",
                iconResId = R.drawable.ic_search,
                description = "GitHub搜索",
                searchUrl = "https://github.com/search?q={query}"
            ),
            SearchEngine(
                name = "CSDN",
                url = "https://www.csdn.net",
                iconResId = R.drawable.ic_search,
                description = "CSDN搜索",
                searchUrl = "https://so.csdn.net/so/search?q={query}"
            ),
            SearchEngine(
                name = "百度",
                url = "https://www.baidu.com",
                iconResId = R.drawable.ic_search,
                description = "百度搜索",
                searchUrl = "https://www.baidu.com/s?wd={query}"
            ),
            SearchEngine(
                name = "谷歌",
                url = "https://www.google.com",
                iconResId = R.drawable.ic_search,
                description = "Google搜索",
                searchUrl = "https://www.google.com/search?q={query}"
            ),
            SearchEngine(
                name = "搜狗",
                url = "https://www.sogou.com/web?query={query}",
                iconResId = R.drawable.ic_search,
                description = "搜狗搜索"
            ),
            SearchEngine(
                name = "V2EX",
                url = "https://www.v2ex.com/search?q={query}",
                iconResId = R.drawable.ic_search,
                description = "V2EX搜索"
            ),
            SearchEngine(
                name = "今日头条",
                url = "https://so.toutiao.com/search?keyword={query}",
                iconResId = R.drawable.ic_search,
                description = "今日头条搜索"
            ),
            SearchEngine(
                name = "YouTube",
                url = "https://www.youtube.com/results?search_query={query}",
                iconResId = R.drawable.ic_search,
                description = "YouTube搜索"
            ),
            SearchEngine(
                name = "哔哩哔哩",
                url = "https://search.bilibili.com/all?keyword={query}",
                iconResId = R.drawable.ic_search,
                description = "哔哩哔哩搜索"
            ),
            SearchEngine(
                name = "X",
                url = "https://twitter.com/search?q={query}",
                iconResId = R.drawable.ic_search,
                description = "X搜索"
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
                    searchUrl = parcel.readString()!!
        )
    }

            override fun newArray(size: Int): Array<SearchEngine?> {
                return arrayOfNulls(size)
            }
        }
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeString(url)
        parcel.writeInt(iconResId)
        parcel.writeString(description)
        parcel.writeString(searchUrl)
    }

    override fun describeContents(): Int = 0
} 