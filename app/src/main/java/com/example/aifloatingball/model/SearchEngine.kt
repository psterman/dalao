package com.example.aifloatingball.model

import android.os.Build
import android.os.Parcel
import android.os.Parcelable
import com.example.aifloatingball.R

/**
 * 搜索引擎数据模型
 */
data class SearchEngine(
    override val name: String,
    override val displayName: String,
    override val url: String,
    override val iconResId: Int = 0,
    override val description: String = "",
    override val searchUrl: String = url,
    val isAI: Boolean = false,
    val iconUrl: String? = null,
    val category: SearchEngineCategory = SearchEngineCategory.GENERAL,
    val isCustom: Boolean = false
) : BaseSearchEngine, Parcelable {
    override fun getSearchUrl(query: String): String {
        return searchUrl.replace("{query}", query)
    }

    companion object {
        // 默认搜索引擎列表
        val DEFAULT_ENGINES = listOf(
            // A1. 通用搜索引擎
            SearchEngine(name = "baidu", displayName = "百度", url = "https://www.baidu.com", iconResId = R.drawable.ic_baidu, description = "全球最大的中文搜索引擎", searchUrl = "https://www.baidu.com/s?wd={query}", category = SearchEngineCategory.GENERAL),
            SearchEngine(name = "google", displayName = "谷歌", url = "https://www.google.com", iconResId = R.drawable.ic_google, description = "全球最大的搜索引擎", searchUrl = "https://www.google.com/search?q={query}", category = SearchEngineCategory.GENERAL),
            SearchEngine(name = "sogou", displayName = "搜狗", url = "https://www.sogou.com", iconResId = R.drawable.ic_sogou, description = "腾讯旗下，深度整合微信公众号和知乎内容", searchUrl = "https://www.sogou.com/web?query={query}", category = SearchEngineCategory.GENERAL),
            SearchEngine(name = "360", displayName = "360搜索", url = "https://www.so.com", iconResId = R.drawable.ic_360search, description = "奇虎360旗下，以\"安全\"为特色", searchUrl = "https://www.so.com/s?q={query}", category = SearchEngineCategory.GENERAL),
            SearchEngine(name = "bing_cn", displayName = "必应", url = "https://cn.bing.com", iconResId = R.drawable.ic_bing, description = "微软旗下，提供高质量国际视野和学术搜索", searchUrl = "https://cn.bing.com/search?q={query}", category = SearchEngineCategory.GENERAL),
            SearchEngine(name = "duckduckgo", displayName = "DuckDuckGo", url = "https://duckduckgo.com", iconResId = R.drawable.ic_duckduckgo, description = "注重隐私保护的搜索引擎", searchUrl = "https://duckduckgo.com/?q={query}", category = SearchEngineCategory.GENERAL),
            SearchEngine(name = "quark", displayName = "夸克", url = "https://quark.sm.cn", iconResId = R.drawable.ic_search, description = "UC旗下，AI驱动的智能搜索引擎", searchUrl = "https://quark.sm.cn/s?q={query}", category = SearchEngineCategory.GENERAL),
            SearchEngine(name = "shenma", displayName = "神马", url = "https://m.sm.cn", iconResId = R.drawable.ic_search, description = "阿里巴巴旗下，专注于移动端体验", searchUrl = "https://m.sm.cn/s?q={query}", category = SearchEngineCategory.GENERAL),

            // A2. 新闻与门户
            SearchEngine(name = "toutiao", displayName = "今日头条", url = "https://www.toutiao.com", iconResId = R.drawable.ic_toutiao, description = "基于个性化推荐的资讯平台", searchUrl = "https://www.toutiao.com/search/?keyword={query}", category = SearchEngineCategory.NEWS),
            SearchEngine(name = "tencent_news", displayName = "腾讯新闻", url = "https://xw.qq.com", iconResId = R.drawable.ic_search, description = "腾讯官方新闻资讯平台", searchUrl = "https://xw.qq.com/search?q={query}", category = SearchEngineCategory.NEWS),
            SearchEngine(name = "sina_news", displayName = "新浪新闻", url = "https://news.sina.cn", iconResId = R.drawable.ic_search, description = "中国最早的商业门户网站之一", searchUrl = "https://search.sina.com.cn/?q={query}", category = SearchEngineCategory.NEWS),
            SearchEngine(name = "netease_news", displayName = "网易新闻", url = "https://3g.163.com/news", iconResId = R.drawable.ic_search, description = "以\"有态度\"和高质量用户跟帖闻名", searchUrl = "https://3g.163.com/search/{query}", category = SearchEngineCategory.NEWS),
            SearchEngine(name = "sohu_news", displayName = "搜狐新闻", url = "https://m.sohu.com", iconResId = R.drawable.ic_search, description = "老牌门户网站，提供综合资讯", searchUrl = "https://m.sohu.com/search?keyword={query}", category = SearchEngineCategory.NEWS),
            SearchEngine(name = "the_paper", displayName = "澎湃新闻", url = "https://www.thepaper.cn", iconResId = R.drawable.ic_search, description = "专注于时政与思想的互联网媒体", searchUrl = "https://www.thepaper.cn/searchResult.jsp?keyword={query}", category = SearchEngineCategory.NEWS),
            SearchEngine(name = "guancha", displayName = "观察者网", url = "https://www.guancha.cn", iconResId = R.drawable.ic_search, description = "专注于国际关系和中国时政分析", searchUrl = "https://www.guancha.cn/search/all.shtml?q={query}", category = SearchEngineCategory.NEWS),
            SearchEngine(name = "jiemian", displayName = "界面新闻", url = "https://www.jiemian.com", iconResId = R.drawable.ic_search, description = "主打原创财经和商业报道", searchUrl = "https://www.jiemian.com/search/index.html?q={query}", category = SearchEngineCategory.NEWS),
            SearchEngine(name = "caixin", displayName = "财新网", url = "https://search.caixin.com", iconResId = R.drawable.ic_search, description = "提供专业的财经新闻和深度商业报道", searchUrl = "https://search.caixin.com/search/search.jsp?keyword={query}", category = SearchEngineCategory.NEWS),
            SearchEngine(name = "people_cn", displayName = "人民网", url = "https://wap.people.com.cn", iconResId = R.drawable.ic_search, description = "《人民日报》建设的官方新闻网站", searchUrl = "http://search.people.com.cn/cnpeople/search.do?keyword={query}", category = SearchEngineCategory.NEWS),
            SearchEngine(name = "xinhuanet", displayName = "新华网", url = "https://m.xinhuanet.com", iconResId = R.drawable.ic_search, description = "新华社主办的官方新闻门户", searchUrl = "http://so.news.cn/search?keyword={query}", category = SearchEngineCategory.NEWS),
            SearchEngine(name = "cctv", displayName = "央视网", url = "https://search.cctv.com", iconResId = R.drawable.ic_search, description = "中央广播电视总台的官方网站", searchUrl = "https://search.cctv.com/search.php?qtext={query}", category = SearchEngineCategory.NEWS),
            SearchEngine(name = "ifeng", displayName = "凤凰网", url = "https://search.ifeng.com", iconResId = R.drawable.ic_search, description = "提供全球视野的综合新闻资讯", searchUrl = "https://search.ifeng.com/listpage?q={query}", category = SearchEngineCategory.NEWS),

            // B1. 购物
            SearchEngine(name = "taobao", displayName = "淘宝", url = "https://m.taobao.com", iconResId = R.drawable.ic_taobao, description = "中国最大的综合性电商平台", searchUrl = "https://s.m.taobao.com/h5?q={query}", category = SearchEngineCategory.SHOPPING),
            SearchEngine(name = "jd", displayName = "京东", url = "https://m.jd.com", iconResId = R.drawable.ic_jd, description = "以自营式家电和数码产品著称的电商平台", searchUrl = "https://m.jd.com/search/index.action?key={query}", category = SearchEngineCategory.SHOPPING),
            SearchEngine(name = "pinduoduo", displayName = "拼多多", url = "https://www.pinduoduo.com", iconResId = R.drawable.ic_search, description = "以社交拼团为特色的电商平台", searchUrl = "https://mobile.yangkeduo.com/search_result.html?search_key={query}", category = SearchEngineCategory.SHOPPING),
            SearchEngine(name = "suning", displayName = "苏宁易购", url = "https://www.suning.com", iconResId = R.drawable.ic_search, description = "家电、3C、母婴产品等综合零售商", searchUrl = "https://search.suning.com/{query}/", category = SearchEngineCategory.SHOPPING),
            SearchEngine(name = "dangdang", displayName = "当当网", url = "http://www.dangdang.com", iconResId = R.drawable.ic_search, description = "知名的图书音像和百货网上商城", searchUrl = "http://search.dangdang.com/?key={query}", category = SearchEngineCategory.SHOPPING),
            // B2. 社交与内容分享
            SearchEngine(name = "weibo", displayName = "微博", url = "https://m.weibo.cn", iconResId = R.drawable.ic_weibo, description = "中文社交媒体平台", searchUrl = "https://m.weibo.cn/search?containerid=100103type%3D1%26q%3D{query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "zhihu", displayName = "知乎", url = "https://www.zhihu.com", iconResId = R.drawable.ic_zhihu, description = "高质量的问答社区", searchUrl = "https://www.zhihu.com/search?q={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "douban", displayName = "豆瓣", url = "https://m.douban.com/home_guide", iconResId = R.drawable.ic_douban, description = "关于书籍、电影、音乐等作品的社区", searchUrl = "https://m.douban.com/search/?query={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "xiaohongshu", displayName = "小红书", url = "https://www.xiaohongshu.com", iconResId = R.drawable.ic_xiaohongshu, description = "生活方式分享社区", searchUrl = "https://www.xiaohongshu.com/search_result?keyword={query}", category = SearchEngineCategory.SOCIAL),

            // C1. 视频
            SearchEngine(name = "bilibili", displayName = "哔哩哔哩", url = "https://m.bilibili.com", iconResId = R.drawable.ic_bilibili, description = "国内领先的年轻人文化社区和视频平台", searchUrl = "https://m.bilibili.com/search?keyword={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "youku", displayName = "优酷", url = "https://m.youku.com", iconResId = R.drawable.ic_search, description = "阿里巴巴旗下的在线视频平台", searchUrl = "https://m.youku.com/search?keyword={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "tencent_video", displayName = "腾讯视频", url = "https://m.v.qq.com", iconResId = R.drawable.ic_search, description = "腾讯旗下的在线视频媒体平台", searchUrl = "https://m.v.qq.com/search.html?keyWord={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "ixigua", displayName = "西瓜视频", url = "https://www.ixigua.com", iconResId = R.drawable.ic_search, description = "字节跳动旗下的中长视频平台", searchUrl = "https://www.ixigua.com/search?keyword={query}", category = SearchEngineCategory.VIDEO),

            // D1. 学术与专业
            SearchEngine(name = "google_scholar", displayName = "Google学术", url = "https://scholar.google.com", iconResId = R.drawable.ic_search, description = "Google提供的免费学术搜索引擎", searchUrl = "https://scholar.google.com/scholar?q={query}", category = SearchEngineCategory.ACADEMIC),
            SearchEngine(name = "cnki", displayName = "知网", url = "https://i.cnki.net/newHome.html", iconResId = R.drawable.ic_search, description = "中国知识基础设施工程，提供学术文献检索", searchUrl = "https://scholar.cnki.net/kns/defaultresult/index?dbcode=CFLS&kw={query}", category = SearchEngineCategory.ACADEMIC),
            SearchEngine(name = "wanfang", displayName = "万方数据", url = "https://m.wanfangdata.com.cn", iconResId = R.drawable.ic_search, description = "提供学术文献和科技信息的综合服务平台", searchUrl = "https://s.wanfangdata.com.cn/paper?q={query}", category = SearchEngineCategory.ACADEMIC),
            SearchEngine(name = "cqvip", displayName = "维普", url = "https://m.cqvip.com", iconResId = R.drawable.ic_search, description = "中文科技期刊数据库", searchUrl = "http://www.cqvip.com/qk/search.aspx?k={query}", category = SearchEngineCategory.ACADEMIC),
            SearchEngine(name = "pubmed", displayName = "PubMed", url = "https://pubmed.ncbi.nlm.nih.gov", iconResId = R.drawable.ic_search, description = "生物医学和生命科学文献数据库", searchUrl = "https://pubmed.ncbi.nlm.nih.gov/?term={query}", category = SearchEngineCategory.ACADEMIC),

            // E1. 生活与服务
            SearchEngine(name = "dianping", displayName = "大众点评", url = "https://www.dianping.com", iconResId = R.drawable.ic_search, description = "本地生活信息及交易平台", searchUrl = "https://www.dianping.com/search/keyword?keyword={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "meituan", displayName = "美团", url = "https://i.meituan.com", iconResId = R.drawable.ic_search, description = "一站式吃喝玩乐消费平台", searchUrl = "https://i.meituan.com/search?q={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "58tongcheng", displayName = "58同城", url = "https://www.58.com", iconResId = R.drawable.ic_search, description = "本地分类信息网站", searchUrl = "https://www.58.com/sou/?key={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "ganji", displayName = "赶集网", url = "https://www.ganji.com", iconResId = R.drawable.ic_search, description = "专业的分类信息网", searchUrl = "https://www.ganji.com/so.php?keyword={query}", category = SearchEngineCategory.LIFESTYLE),

            // F1. IT与开发者
            SearchEngine(name = "github", displayName = "GitHub", url = "https://github.com", iconResId = R.drawable.ic_search, description = "全球最大的代码托管平台和开发者社区", searchUrl = "https://github.com/search?q={query}", category = SearchEngineCategory.DEVELOPER),
            SearchEngine(name = "csdn", displayName = "CSDN", url = "https://m.csdn.net", iconResId = R.drawable.ic_search, description = "中文IT技术社区和服务平台", searchUrl = "https://so.csdn.net/so/search?q={query}", category = SearchEngineCategory.DEVELOPER),
            SearchEngine(name = "juejin", displayName = "稀土掘金", url = "https://juejin.cn", iconResId = R.drawable.ic_search, description = "一个帮助开发者成长的技术社区", searchUrl = "https://juejin.cn/search?query={query}", category = SearchEngineCategory.DEVELOPER),

            // G1. 知识与百科
            SearchEngine(name = "baidu_baike", displayName = "百度百科", url = "https://baike.baidu.com", iconResId = R.drawable.ic_baidu, description = "中文网络百科全书", searchUrl = "https://baike.baidu.com/search?word={query}", category = SearchEngineCategory.KNOWLEDGE),
            SearchEngine(name = "wikipedia", displayName = "维基百科", url = "https://zh.wikipedia.org", iconResId = R.drawable.ic_search, description = "自由的百科全书", searchUrl = "https://zh.wikipedia.org/wiki/Special:Search?search={query}", category = SearchEngineCategory.KNOWLEDGE),
            SearchEngine(name = "hudong_baike", displayName = "互动百科", url = "http://www.baike.com", iconResId = R.drawable.ic_search, description = "全球最大中文百科网站", searchUrl = "http://www.baike.com/wiki/{query}", category = SearchEngineCategory.KNOWLEDGE),

            // H1. 设计与创意
            SearchEngine(name = "dribbble", displayName = "Dribbble", url = "https://dribbble.com", iconResId = R.drawable.ic_search, description = "设计师作品展示和交流平台", searchUrl = "https://dribbble.com/search/{query}", category = SearchEngineCategory.DESIGN),
            SearchEngine(name = "behance", displayName = "Behance", url = "https://www.behance.net", iconResId = R.drawable.ic_search, description = "Adobe旗下的创意作品展示平台", searchUrl = "https://www.behance.net/search/projects?search={query}", category = SearchEngineCategory.DESIGN),
            SearchEngine(name = "zcool", displayName = "站酷", url = "https://www.zcool.com.cn", iconResId = R.drawable.ic_search, description = "中国设计师互动平台", searchUrl = "https://www.zcool.com.cn/search/content?word={query}", category = SearchEngineCategory.DESIGN)
        )

        @JvmStatic
        fun getNormalSearchEngines(): List<SearchEngine> = DEFAULT_ENGINES

        @JvmField
        val CREATOR = object : Parcelable.Creator<SearchEngine> {
            override fun createFromParcel(parcel: Parcel): SearchEngine {
                val name = parcel.readString()!!
                val displayName = parcel.readString()!!
                val url = parcel.readString()!!
                val iconResId = parcel.readInt()
                val description = parcel.readString()!!
                val searchUrl = parcel.readString()!!
                val isAI = parcel.readByte() != 0.toByte()
                val iconUrl = parcel.readString()
                val category = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    parcel.readSerializable(SearchEngineCategory::class.java.classLoader, SearchEngineCategory::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    parcel.readSerializable() as SearchEngineCategory?
                }
                val isCustom = parcel.readByte() != 0.toByte()
                return SearchEngine(
                    name = name,
                    displayName = displayName,
                    url = url,
                    iconResId = iconResId,
                    description = description,
                    searchUrl = searchUrl,
                    isAI = isAI,
                    iconUrl = iconUrl,
                    category = category ?: SearchEngineCategory.GENERAL,
                    isCustom = isCustom
                )
            }

            override fun newArray(size: Int): Array<SearchEngine?> {
                return arrayOfNulls(size)
            }
        }

        fun getAllSearchEngines(): List<SearchEngine> {
            return getNormalSearchEngines()
        }
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(name)
        parcel.writeString(displayName)
        parcel.writeString(url)
        parcel.writeInt(iconResId)
        parcel.writeString(description)
        parcel.writeString(searchUrl)
        parcel.writeByte(if (isAI) 1.toByte() else 0.toByte())
        parcel.writeString(iconUrl)
        parcel.writeSerializable(category)
        parcel.writeByte(if (isCustom) 1.toByte() else 0.toByte())
    }

    override fun describeContents(): Int = 0
} 