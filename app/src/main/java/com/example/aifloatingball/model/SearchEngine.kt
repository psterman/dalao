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
            // --- 通用分类 (General) ---
            SearchEngine("baidu", "https://www.baidu.com", R.drawable.ic_baidu, "百度", "https://www.baidu.com/s?wd={query}", category = SearchEngineCategory.GENERAL),
            SearchEngine("google", "https://www.google.com", R.drawable.ic_google, "Google", "https://www.google.com/search?q={query}", category = SearchEngineCategory.GENERAL),
            SearchEngine("bing", "https://www.bing.com", R.drawable.ic_bing, "Bing", "https://www.bing.com/search?q={query}", category = SearchEngineCategory.GENERAL),
            SearchEngine("sogou", "https://www.sogou.com", R.drawable.ic_sogou, "搜狗", "https://www.sogou.com/web?query={query}", category = SearchEngineCategory.GENERAL),
            SearchEngine("360", "https://www.so.com", R.drawable.ic_360, "360搜索", "https://www.so.com/s?q={query}", category = SearchEngineCategory.GENERAL),
            SearchEngine("duckduckgo", "https://duckduckgo.com", R.drawable.ic_duckduckgo, "DuckDuckGo", "https://duckduckgo.com/?q={query}", category = SearchEngineCategory.GENERAL),
            SearchEngine("yandex", "https://yandex.com", R.drawable.ic_search, "Yandex", "https://yandex.com/search/?text={query}", category = SearchEngineCategory.GENERAL),
            SearchEngine("yahoo", "https://search.yahoo.com", R.drawable.ic_search, "Yahoo", "https://search.yahoo.com/search?p={query}", category = SearchEngineCategory.GENERAL),
            SearchEngine("ecosia", "https://www.ecosia.org", R.drawable.ic_search, "Ecosia", "https://www.ecosia.org/search?q={query}", category = SearchEngineCategory.GENERAL),
            SearchEngine("brave", "https://search.brave.com", R.drawable.ic_search, "Brave", "https://search.brave.com/search?q={query}", category = SearchEngineCategory.GENERAL),
            SearchEngine("startpage", "https://www.startpage.com", R.drawable.ic_search, "Startpage", "https://www.startpage.com/sp/search?query={query}", category = SearchEngineCategory.GENERAL),
            SearchEngine("qwant", "https://www.qwant.com", R.drawable.ic_search, "Qwant", "https://www.qwant.com/?q={query}", category = SearchEngineCategory.GENERAL),
            SearchEngine("swisscows", "https://swisscows.com", R.drawable.ic_search, "Swisscows", "https://swisscows.com/web?query={query}", category = SearchEngineCategory.GENERAL),
            SearchEngine("mojeek", "https://www.mojeek.com", R.drawable.ic_search, "Mojeek", "https://www.mojeek.com/search?q={query}", category = SearchEngineCategory.GENERAL),
            SearchEngine("metager", "https://metager.org", R.drawable.ic_search, "MetaGer", "https://metager.org/meta/meta.ger?eingabe={query}", category = SearchEngineCategory.GENERAL),

            // --- 社交分类 (Social) ---
            SearchEngine("weibo", "https://www.weibo.com", R.drawable.ic_weibo, "微博", "https://s.weibo.com/weibo?q={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine("douban", "https://www.douban.com", R.drawable.ic_douban, "豆瓣", "https://www.douban.com/search?q={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine("xiaohongshu", "https://www.xiaohongshu.com", R.drawable.ic_xiaohongshu, "小红书", "https://www.xiaohongshu.com/search_result?keyword={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine("twitter", "https://twitter.com", R.drawable.ic_search, "Twitter / X", "https://twitter.com/search?q={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine("reddit", "https://www.reddit.com", R.drawable.ic_search, "Reddit", "https://www.reddit.com/search/?q={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine("pinterest", "https://www.pinterest.com", R.drawable.ic_search, "Pinterest", "https://www.pinterest.com/search/pins/?q={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine("linkedin", "https://www.linkedin.com", R.drawable.ic_search, "LinkedIn", "https://www.linkedin.com/search/results/all/?keywords={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine("facebook", "https://www.facebook.com", R.drawable.ic_search, "Facebook", "https://www.facebook.com/search/top/?q={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine("instagram", "https://www.instagram.com", R.drawable.ic_search, "Instagram", "https://www.instagram.com/explore/tags/{query}/", category = SearchEngineCategory.SOCIAL),

            // --- 购物分类 (Shopping) ---
            SearchEngine("taobao", "https://www.taobao.com", R.drawable.ic_taobao, "淘宝", "https://s.taobao.com/search?q={query}", category = SearchEngineCategory.SHOPPING),
            SearchEngine("jd", "https://www.jd.com", R.drawable.ic_jd, "京东", "https://search.jd.com/Search?keyword={query}", category = SearchEngineCategory.SHOPPING),
            SearchEngine("pinduoduo", "https://mobile.pinduoduo.com", R.drawable.ic_search, "拼多多", "https://mobile.pinduoduo.com/search_result.html?keyword={query}", category = SearchEngineCategory.SHOPPING),
            SearchEngine("amazon", "https://www.amazon.com", R.drawable.ic_search, "Amazon", "https://www.amazon.com/s?k={query}", category = SearchEngineCategory.SHOPPING),
            SearchEngine("ebay", "https://www.ebay.com", R.drawable.ic_search, "eBay", "https://www.ebay.com/sch/i.html?_nkw={query}", category = SearchEngineCategory.SHOPPING),
            SearchEngine("walmart", "https://www.walmart.com", R.drawable.ic_search, "Walmart", "https://www.walmart.com/search/?query={query}", category = SearchEngineCategory.SHOPPING),
            SearchEngine("aliexpress", "https://www.aliexpress.com", R.drawable.ic_search, "AliExpress", "https://www.aliexpress.com/wholesale?SearchText={query}", category = SearchEngineCategory.SHOPPING),
            SearchEngine("suning", "https://www.suning.com", R.drawable.ic_search, "苏宁易购", "https://search.suning.com/{query}/", category = SearchEngineCategory.SHOPPING),
            SearchEngine("gome", "https://www.gome.com.cn", R.drawable.ic_search, "国美", "https://search.gome.com.cn/search?question={query}", category = SearchEngineCategory.SHOPPING),
            SearchEngine("dangdang", "http://www.dangdang.com", R.drawable.ic_search, "当当网", "http://search.dangdang.com/?key={query}", category = SearchEngineCategory.SHOPPING),

            // --- 知识分类 (Knowledge) ---
            SearchEngine("zhihu", "https://www.zhihu.com", R.drawable.ic_zhihu, "知乎", "https://www.zhihu.com/search?q={query}", category = SearchEngineCategory.KNOWLEDGE),
            SearchEngine("wikipedia", "https://www.wikipedia.org", R.drawable.ic_search, "Wikipedia", "https://en.wikipedia.org/wiki/Special:Search?search={query}", category = SearchEngineCategory.KNOWLEDGE),
            SearchEngine("baike", "https://baike.baidu.com", R.drawable.ic_search, "百度百科", "https://baike.baidu.com/item/{query}", category = SearchEngineCategory.KNOWLEDGE),
            SearchEngine("quora", "https://www.quora.com", R.drawable.ic_search, "Quora", "https://www.quora.com/search?q={query}", category = SearchEngineCategory.KNOWLEDGE),
            SearchEngine("wolframalpha", "https://www.wolframalpha.com", R.drawable.ic_search, "WolframAlpha", "https://www.wolframalpha.com/input/?i={query}", category = SearchEngineCategory.KNOWLEDGE),
            SearchEngine("scholar", "https://scholar.google.com", R.drawable.ic_search, "Google Scholar", "https://scholar.google.com/scholar?q={query}", category = SearchEngineCategory.KNOWLEDGE),

            // --- 视频分类 (Video) ---
            SearchEngine("bilibili", "https://www.bilibili.com", R.drawable.ic_bilibili, "哔哩哔哩", "https://search.bilibili.com/all?keyword={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine("douyin", "https://www.douyin.com", R.drawable.ic_douyin, "抖音", "https://www.douyin.com/search/{query}", category = SearchEngineCategory.VIDEO),
            SearchEngine("youtube", "https://www.youtube.com", R.drawable.ic_search, "YouTube", "https://www.youtube.com/results?search_query={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine("vimeo", "https://vimeo.com", R.drawable.ic_search, "Vimeo", "https://vimeo.com/search?q={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine("iqiyi", "https://www.iq.com", R.drawable.ic_search, "爱奇艺", "https://www.iq.com/search?q={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine("tencentvideo", "https://v.qq.com", R.drawable.ic_search, "腾讯视频", "https://v.qq.com/x/search/?q={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine("youku", "https://www.youku.com", R.drawable.ic_search, "优酷", "https://www.soku.com/search_video/q_{query}", category = SearchEngineCategory.VIDEO),

            // --- 开发分类 (Development) ---
            SearchEngine("github", "https://github.com", R.drawable.ic_search, "GitHub", "https://github.com/search?q={query}", category = SearchEngineCategory.DEVELOPMENT),
            SearchEngine("stackoverflow", "https://stackoverflow.com", R.drawable.ic_search, "Stack Overflow", "https://stackoverflow.com/search?q={query}", category = SearchEngineCategory.DEVELOPMENT),
            SearchEngine("gitlab", "https://gitlab.com", R.drawable.ic_search, "GitLab", "https://gitlab.com/search?search={query}", category = SearchEngineCategory.DEVELOPMENT),
            SearchEngine("mdn", "https://developer.mozilla.org", R.drawable.ic_search, "MDN Web Docs", "https://developer.mozilla.org/en-US/search?q={query}", category = SearchEngineCategory.DEVELOPMENT),
            SearchEngine("devto", "https://dev.to", R.drawable.ic_search, "dev.to", "https://dev.to/search?q={query}", category = SearchEngineCategory.DEVELOPMENT),
            SearchEngine("juejin", "https://juejin.cn", R.drawable.ic_search, "稀土掘金", "https://juejin.cn/search?query={query}", category = SearchEngineCategory.DEVELOPMENT),
            SearchEngine("csdn", "https://www.csdn.net", R.drawable.ic_search, "CSDN", "https://so.csdn.net/so/search?q={query}", category = SearchEngineCategory.DEVELOPMENT),
            SearchEngine("packagist", "https://packagist.org", R.drawable.ic_search, "Packagist", "https://packagist.org/?q={query}", category = SearchEngineCategory.DEVELOPMENT),
            SearchEngine("npm", "https://www.npmjs.com", R.drawable.ic_search, "npm", "https://www.npmjs.com/search?q={query}", category = SearchEngineCategory.DEVELOPMENT),

            // --- 设计分类 (Design) ---
            SearchEngine("dribbble", "https://dribbble.com", R.drawable.ic_search, "Dribbble", "https://dribbble.com/search/{query}", category = SearchEngineCategory.DESIGN),
            SearchEngine("behance", "https://www.behance.net", R.drawable.ic_search, "Behance", "https://www.behance.net/search?search={query}", category = SearchEngineCategory.DESIGN),
            SearchEngine("pinterest_design", "https://www.pinterest.com", R.drawable.ic_search, "Pinterest", "https://www.pinterest.com/search/pins/?q={query}", category = SearchEngineCategory.DESIGN),
            SearchEngine("unsplash", "https://unsplash.com", R.drawable.ic_search, "Unsplash", "https://unsplash.com/s/photos/{query}", category = SearchEngineCategory.DESIGN),
            SearchEngine("pexels", "https://www.pexels.com", R.drawable.ic_search, "Pexels", "https://www.pexels.com/search/{query}", category = SearchEngineCategory.DESIGN),
            SearchEngine("huaban", "https://huaban.com", R.drawable.ic_search, "花瓣", "https://huaban.com/search/?q={query}", category = SearchEngineCategory.DESIGN),
            SearchEngine("zcool", "https://www.zcool.com.cn", R.drawable.ic_search, "站酷", "https://www.zcool.com.cn/search/content?word={query}", category = SearchEngineCategory.DESIGN),
            SearchEngine("iconfinder", "https://www.iconfinder.com", R.drawable.ic_search, "Iconfinder", "https://www.iconfinder.com/search?q={query}", category = SearchEngineCategory.DESIGN),

            // --- 生活分类 (Lifestyle) ---
            SearchEngine("tripadvisor", "https://www.tripadvisor.com", R.drawable.ic_search, "Tripadvisor", "https://www.tripadvisor.com/Search?q={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine("imdb", "https://www.imdb.com", R.drawable.ic_search, "IMDb", "https://www.imdb.com/find?q={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine("douban_movie", "https://movie.douban.com", R.drawable.ic_search, "豆瓣电影", "https://movie.douban.com/subject_search?search_text={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine("meituan", "https://www.meituan.com", R.drawable.ic_search, "美团", "https://www.meituan.com/s/{query}/", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine("dianping", "https://www.dianping.com", R.drawable.ic_search, "大众点评", "https://www.dianping.com/search/keyword/1/0_{query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine("ctrip", "https://www.ctrip.com", R.drawable.ic_search, "携程", "https://www.ctrip.com/search/hotel/deal?kwd={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine("autohome", "https://www.autohome.com.cn", R.drawable.ic_search, "汽车之家", "https://sou.autohome.com.cn/zonghe?q={query}", category = SearchEngineCategory.LIFESTYLE),

            // --- 新闻分类 (News) ---
            SearchEngine("googlenews", "https://news.google.com", R.drawable.ic_search, "Google News", "https://news.google.com/search?q={query}", category = SearchEngineCategory.NEWS),
            SearchEngine("toutiao", "https://www.toutiao.com", R.drawable.ic_toutiao, "今日头条", "https://www.toutiao.com/search/?keyword={query}", category = SearchEngineCategory.NEWS),
            SearchEngine("bbc", "https://www.bbc.co.uk", R.drawable.ic_search, "BBC", "https://www.bbc.co.uk/search?q={query}", category = SearchEngineCategory.NEWS),
            SearchEngine("reuters", "https://www.reuters.com", R.drawable.ic_search, "Reuters", "https://www.reuters.com/search/news?blob={query}", category = SearchEngineCategory.NEWS),
            SearchEngine("apnews", "https://apnews.com", R.drawable.ic_search, "AP News", "https://apnews.com/search?q={query}", category = SearchEngineCategory.NEWS),

            // --- 其他分类 (Others) ---
            SearchEngine("archiveorg", "https://archive.org", R.drawable.ic_search, "Internet Archive", "https://archive.org/search.php?query={query}", category = SearchEngineCategory.OTHERS),
            SearchEngine("torrentz2", "https://torrentz2.eu", R.drawable.ic_search, "Torrentz2", "https://torrentz2.eu/search?f={query}", category = SearchEngineCategory.OTHERS)
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