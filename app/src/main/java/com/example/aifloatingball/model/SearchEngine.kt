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
            SearchEngine(name = "baidu", displayName = "百度", url = "https://m.baidu.com", iconResId = R.drawable.ic_baidu, description = "全球最大的中文搜索引擎", searchUrl = "https://m.baidu.com/s?wd={query}", category = SearchEngineCategory.GENERAL),
            SearchEngine(name = "google", displayName = "谷歌", url = "https://www.google.com", iconResId = R.drawable.ic_google, description = "全球最大的搜索引擎", searchUrl = "https://www.google.com/search?q={query}&hl=zh-CN", category = SearchEngineCategory.GENERAL),
            SearchEngine(name = "sogou", displayName = "搜狗", url = "https://m.sogou.com", iconResId = R.drawable.ic_sogou, description = "腾讯旗下，深度整合微信公众号和知乎内容", searchUrl = "https://m.sogou.com/web?query={query}", category = SearchEngineCategory.GENERAL),
            SearchEngine(name = "360", displayName = "360搜索", url = "https://m.so.com", iconResId = R.drawable.ic_360search, description = "奇虎360旗下，以\"安全\"为特色", searchUrl = "https://m.so.com/s?q={query}", category = SearchEngineCategory.GENERAL),
            SearchEngine(name = "bing_cn", displayName = "必应", url = "https://cn.bing.com", iconResId = R.drawable.ic_bing, description = "微软旗下，提供高质量国际视野和学术搜索", searchUrl = "https://cn.bing.com/search?q={query}&mkt=zh-CN", category = SearchEngineCategory.GENERAL),
            SearchEngine(name = "duckduckgo", displayName = "DuckDuckGo", url = "https://duckduckgo.com", iconResId = R.drawable.ic_duckduckgo, description = "注重隐私保护的搜索引擎", searchUrl = "https://duckduckgo.com/?q={query}", category = SearchEngineCategory.GENERAL),
            SearchEngine(name = "quark", displayName = "夸克", url = "https://quark.sm.cn", iconResId = R.drawable.ic_search, description = "UC旗下，AI驱动的智能搜索引擎", searchUrl = "https://quark.sm.cn/s?q={query}", category = SearchEngineCategory.GENERAL),
            SearchEngine(name = "shenma", displayName = "神马", url = "https://m.sm.cn", iconResId = R.drawable.ic_search, description = "阿里巴巴旗下，专注于移动端体验", searchUrl = "https://m.sm.cn/s?q={query}", category = SearchEngineCategory.GENERAL),

            // A2. 新闻与门户
            SearchEngine(name = "toutiao", displayName = "今日头条", url = "https://m.toutiao.com", iconResId = R.drawable.ic_toutiao, description = "基于个性化推荐的资讯平台", searchUrl = "https://m.toutiao.com/search/?keyword={query}", category = SearchEngineCategory.NEWS),
            SearchEngine(name = "tencent_news", displayName = "腾讯新闻", url = "https://m.xw.qq.com", iconResId = R.drawable.ic_search, description = "腾讯官方新闻资讯平台", searchUrl = "https://m.xw.qq.com/search?q={query}", category = SearchEngineCategory.NEWS),
            SearchEngine(name = "sina_news", displayName = "新浪新闻", url = "https://news.sina.cn", iconResId = R.drawable.ic_search, description = "中国最早的商业门户网站之一", searchUrl = "https://search.sina.com.cn/?q={query}", category = SearchEngineCategory.NEWS),
            SearchEngine(name = "netease_news", displayName = "网易新闻", url = "https://3g.163.com/news", iconResId = R.drawable.ic_search, description = "以\"有态度\"和高质量用户跟帖闻名", searchUrl = "https://3g.163.com/search/{query}", category = SearchEngineCategory.NEWS),
            SearchEngine(name = "sohu_news", displayName = "搜狐新闻", url = "https://m.sohu.com", iconResId = R.drawable.ic_search, description = "老牌门户网站，提供综合资讯", searchUrl = "https://m.sohu.com/search?keyword={query}", category = SearchEngineCategory.NEWS),
            SearchEngine(name = "the_paper", displayName = "澎湃新闻", url = "https://m.thepaper.cn", iconResId = R.drawable.ic_search, description = "专注于时政与思想的互联网媒体", searchUrl = "https://m.thepaper.cn/searchResult.jsp?keyword={query}", category = SearchEngineCategory.NEWS),
            SearchEngine(name = "guancha", displayName = "观察者网", url = "https://m.guancha.cn", iconResId = R.drawable.ic_search, description = "专注于国际关系和中国时政分析", searchUrl = "https://m.guancha.cn/search/all.shtml?q={query}", category = SearchEngineCategory.NEWS),
            SearchEngine(name = "jiemian", displayName = "界面新闻", url = "https://www.jiemian.com", iconResId = R.drawable.ic_search, description = "主打原创财经和商业报道", searchUrl = "https://www.jiemian.com/search/index.html?q={query}", category = SearchEngineCategory.NEWS),
            SearchEngine(name = "caixin", displayName = "财新网", url = "https://search.caixin.com", iconResId = R.drawable.ic_search, description = "提供专业的财经新闻和深度商业报道", searchUrl = "https://search.caixin.com/search/search.jsp?keyword={query}", category = SearchEngineCategory.NEWS),
            SearchEngine(name = "people_cn", displayName = "人民网", url = "https://wap.people.com.cn", iconResId = R.drawable.ic_search, description = "《人民日报》建设的官方新闻网站", searchUrl = "http://search.people.com.cn/cnpeople/search.do?keyword={query}", category = SearchEngineCategory.NEWS),
            SearchEngine(name = "xinhuanet", displayName = "新华网", url = "https://m.xinhuanet.com", iconResId = R.drawable.ic_search, description = "新华社主办的官方新闻门户", searchUrl = "http://so.news.cn/search?keyword={query}", category = SearchEngineCategory.NEWS),
            SearchEngine(name = "cctv", displayName = "央视网", url = "https://search.cctv.com", iconResId = R.drawable.ic_search, description = "中央广播电视总台的官方网站", searchUrl = "https://search.cctv.com/search.php?qtext={query}", category = SearchEngineCategory.NEWS),
            SearchEngine(name = "ifeng", displayName = "凤凰网", url = "https://search.ifeng.com", iconResId = R.drawable.ic_search, description = "提供全球视野的综合新闻资讯", searchUrl = "https://search.ifeng.com/listpage?q={query}", category = SearchEngineCategory.NEWS),

            // B1. 购物
            SearchEngine(name = "taobao", displayName = "淘宝", url = "https://m.taobao.com", iconResId = R.drawable.ic_taobao, description = "中国最大的综合性电商平台", searchUrl = "https://s.m.taobao.com/h5?q={query}", category = SearchEngineCategory.SHOPPING),
            SearchEngine(name = "jd", displayName = "京东", url = "https://m.jd.com", iconResId = R.drawable.ic_jd, description = "以自营式家电和数码产品著称的电商平台", searchUrl = "https://m.jd.com/search/index.action?key={query}", category = SearchEngineCategory.SHOPPING),
            SearchEngine(name = "pinduoduo", displayName = "拼多多", url = "https://mobile.yangkeduo.com", iconResId = R.drawable.ic_search, description = "以社交拼团为特色的电商平台", searchUrl = "https://mobile.yangkeduo.com/search_result.html?search_key={query}", category = SearchEngineCategory.SHOPPING),
            SearchEngine(name = "suning", displayName = "苏宁易购", url = "https://m.suning.com", iconResId = R.drawable.ic_search, description = "家电、3C、母婴产品等综合零售商", searchUrl = "https://m.suning.com/search/{query}/", category = SearchEngineCategory.SHOPPING),
            SearchEngine(name = "dangdang", displayName = "当当网", url = "https://m.dangdang.com", iconResId = R.drawable.ic_search, description = "知名的图书音像和百货网上商城", searchUrl = "https://m.dangdang.com/search?key={query}", category = SearchEngineCategory.SHOPPING),
            // B2. 社交与内容分享
            SearchEngine(name = "weibo", displayName = "微博", url = "https://m.weibo.cn", iconResId = R.drawable.ic_weibo, description = "中文社交媒体平台", searchUrl = "https://m.weibo.cn/search?containerid=100103type%3D1%26q%3D{query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "zhihu", displayName = "知乎", url = "https://www.zhihu.com", iconResId = R.drawable.ic_zhihu, description = "高质量的问答社区", searchUrl = "https://www.zhihu.com/search?q={query}&type=content", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "douban", displayName = "豆瓣", url = "https://m.douban.com/home_guide", iconResId = R.drawable.ic_douban, description = "关于书籍、电影、音乐等作品的社区", searchUrl = "https://m.douban.com/search/?query={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "xiaohongshu", displayName = "小红书", url = "https://www.xiaohongshu.com", iconResId = R.drawable.ic_xiaohongshu, description = "生活方式分享社区", searchUrl = "https://www.xiaohongshu.com/search_result?keyword={query}&source=web_search_result_notes", category = SearchEngineCategory.SOCIAL),

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
            SearchEngine(name = "zcool", displayName = "站酷", url = "https://www.zcool.com.cn", iconResId = R.drawable.ic_search, description = "中国设计师互动平台", searchUrl = "https://www.zcool.com.cn/search/content?word={query}", category = SearchEngineCategory.DESIGN),

            // I1. 视频平台（新增）
            SearchEngine(name = "douyin", displayName = "抖音", url = "https://www.douyin.com", iconResId = R.drawable.ic_search, description = "字节跳动旗下短视频平台", searchUrl = "https://www.douyin.com/search/{query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "kuaishou", displayName = "快手", url = "https://www.kuaishou.com", iconResId = R.drawable.ic_search, description = "快手短视频平台", searchUrl = "https://www.kuaishou.com/search/video?searchKey={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "iqiyi", displayName = "爱奇艺", url = "https://m.iqiyi.com", iconResId = R.drawable.ic_search, description = "爱奇艺视频平台", searchUrl = "https://m.iqiyi.com/search.html?source=input&key={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "weibo_video", displayName = "微博视频", url = "https://m.weibo.cn", iconResId = R.drawable.ic_weibo, description = "微博视频搜索", searchUrl = "https://m.weibo.cn/search?containerid=100103type%3D64%26q%3D{query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "xiaohongshu_video", displayName = "小红书视频", url = "https://www.xiaohongshu.com", iconResId = R.drawable.ic_xiaohongshu, description = "小红书视频搜索", searchUrl = "https://www.xiaohongshu.com/search_result?keyword={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "tencent_video_search", displayName = "腾讯视频搜索", url = "https://m.v.qq.com", iconResId = R.drawable.ic_search, description = "腾讯视频搜索", searchUrl = "https://m.v.qq.com/x/search/?q={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "youku_search", displayName = "优酷搜索", url = "https://m.youku.com", iconResId = R.drawable.ic_search, description = "优酷视频搜索", searchUrl = "https://m.youku.com/video/search?q={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "open163", displayName = "网易公开课", url = "https://m.open.163.com", iconResId = R.drawable.ic_search, description = "网易公开课", searchUrl = "https://m.open.163.com/search/search.htm?query={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "youtube_mobile", displayName = "YouTube移动", url = "https://m.youtube.com", iconResId = R.drawable.ic_search, description = "YouTube移动版", searchUrl = "https://m.youtube.com/results?search_query={query}", category = SearchEngineCategory.VIDEO),

            // I2. 直播平台（新增）
            SearchEngine(name = "douyin_live", displayName = "抖音直播", url = "https://www.douyin.com", iconResId = R.drawable.ic_search, description = "抖音直播搜索", searchUrl = "https://www.douyin.com/search/{query}?type=live", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "kuaishou_live", displayName = "快手直播", url = "https://live.kuaishou.com", iconResId = R.drawable.ic_search, description = "快手直播", searchUrl = "https://live.kuaishou.com/search?keyword={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "bilibili_live", displayName = "B站直播", url = "https://live.bilibili.com", iconResId = R.drawable.ic_bilibili, description = "B站直播搜索", searchUrl = "https://live.bilibili.com/mobile#search={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "huya", displayName = "虎牙", url = "https://m.huya.com", iconResId = R.drawable.ic_search, description = "虎牙直播", searchUrl = "https://m.huya.com/search?hsk={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "douyu", displayName = "斗鱼", url = "https://m.douyu.com", iconResId = R.drawable.ic_search, description = "斗鱼直播", searchUrl = "https://m.douyu.com/search/?kw={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "yy", displayName = "YY直播", url = "https://www.yy.com", iconResId = R.drawable.ic_search, description = "YY直播", searchUrl = "https://www.yy.com/mobile/search/{query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "inke", displayName = "映客直播", url = "https://www.inke.cn", iconResId = R.drawable.ic_search, description = "映客直播", searchUrl = "https://www.inke.cn/search?keyword={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "cc_live", displayName = "CC直播", url = "https://cc.163.com", iconResId = R.drawable.ic_search, description = "CC直播", searchUrl = "https://cc.163.com/search/{query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "egame", displayName = "企鹅电竞", url = "https://m.egame.qq.com", iconResId = R.drawable.ic_search, description = "企鹅电竞", searchUrl = "https://m.egame.qq.com/search?keyword={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "now_live", displayName = "NOW直播", url = "https://now.qq.com", iconResId = R.drawable.ic_search, description = "NOW直播", searchUrl = "https://now.qq.com/mobile/search.html?k={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "taobao_live", displayName = "淘宝直播", url = "https://m.tb.cn", iconResId = R.drawable.ic_taobao, description = "淘宝直播", searchUrl = "https://m.tb.cn/live/search?q={query}", category = SearchEngineCategory.VIDEO),

            // I3. 短剧平台（新增）
            SearchEngine(name = "douyin_short", displayName = "抖音短剧", url = "https://www.douyin.com", iconResId = R.drawable.ic_search, description = "抖音短剧频道", searchUrl = "https://www.douyin.com/search/{query}?type=video&filter_duration=short", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "kuaishou_short", displayName = "快手小剧场", url = "https://www.kuaishou.com", iconResId = R.drawable.ic_search, description = "快手小剧场", searchUrl = "https://www.kuaishou.com/search/video?searchKey={query}&filter=shortplay", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "bilibili_short", displayName = "B站短剧", url = "https://m.bilibili.com", iconResId = R.drawable.ic_bilibili, description = "B站短剧频道", searchUrl = "https://m.bilibili.com/search?keyword={query}&duration=1", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "mgtv", displayName = "芒果TV", url = "https://m.mgtv.com", iconResId = R.drawable.ic_search, description = "芒果TV短剧", searchUrl = "https://m.mgtv.com/so/{query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "youku_short", displayName = "优酷短剧", url = "https://m.youku.com", iconResId = R.drawable.ic_search, description = "优酷短剧", searchUrl = "https://m.youku.com/video/search?q={query}&type=short", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "tencent_short", displayName = "腾讯视频短剧", url = "https://m.v.qq.com", iconResId = R.drawable.ic_search, description = "腾讯视频短剧", searchUrl = "https://m.v.qq.com/x/search/?q={query}&filter=short", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "ixigua_short", displayName = "西瓜短剧", url = "https://www.ixigua.com", iconResId = R.drawable.ic_search, description = "西瓜短剧", searchUrl = "https://www.ixigua.com/search/{query}?filter=shortvideo", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "weishi", displayName = "微视", url = "https://m.weishi.qq.com", iconResId = R.drawable.ic_search, description = "微视短剧", searchUrl = "https://m.weishi.qq.com/search/{query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "iqiyi_short", displayName = "爱奇艺竖屏剧", url = "https://m.iqiyi.com", iconResId = R.drawable.ic_search, description = "爱奇艺竖屏剧", searchUrl = "https://m.iqiyi.com/search.html?key={query}&channel=short", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "sohu_tv", displayName = "搜狐视频", url = "https://m.tv.sohu.com", iconResId = R.drawable.ic_search, description = "搜狐短剧", searchUrl = "https://m.tv.sohu.com/search/{query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "taobao_short", displayName = "淘宝短剧", url = "https://m.tb.cn", iconResId = R.drawable.ic_taobao, description = "淘宝短剧", searchUrl = "https://m.tb.cn/shortplay/search?q={query}", category = SearchEngineCategory.VIDEO),

            // I4. 美食平台（新增）
            SearchEngine(name = "xiachufang", displayName = "下厨房", url = "https://m.xiachufang.com", iconResId = R.drawable.ic_search, description = "下厨房", searchUrl = "https://m.xiachufang.com/search/?keyword={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "douguo", displayName = "豆果美食", url = "https://m.douguo.com", iconResId = R.drawable.ic_search, description = "豆果美食", searchUrl = "https://m.douguo.com/search/{query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "meishichina", displayName = "美食天下", url = "https://m.meishichina.com", iconResId = R.drawable.ic_search, description = "美食天下", searchUrl = "https://m.meishichina.com/search/?q={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "dianping_shop", displayName = "大众点评店铺", url = "https://m.dianping.com", iconResId = R.drawable.ic_search, description = "大众点评店铺搜索", searchUrl = "https://m.dianping.com/shopsearch?keyword={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "eleme", displayName = "饿了么", url = "https://m.ele.me", iconResId = R.drawable.ic_search, description = "饿了么搜索", searchUrl = "https://m.ele.me/search?keywords={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "meituan_waimai", displayName = "美团外卖", url = "https://m.waimai.meituan.com", iconResId = R.drawable.ic_search, description = "美团外卖搜索", searchUrl = "https://m.waimai.meituan.com/search?w={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "xiaohongshu_food", displayName = "小红书美食", url = "https://www.xiaohongshu.com", iconResId = R.drawable.ic_xiaohongshu, description = "小红书美食", searchUrl = "https://www.xiaohongshu.com/search_result?keyword={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "bilibili_food", displayName = "B站美食", url = "https://m.bilibili.com", iconResId = R.drawable.ic_bilibili, description = "B站美食", searchUrl = "https://m.bilibili.com/search?keyword={query}&tid=21", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "douyin_food", displayName = "抖音美食", url = "https://www.douyin.com", iconResId = R.drawable.ic_search, description = "抖音美食", searchUrl = "https://www.douyin.com/search/{query}?type=video&filter_channel=food", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "kuaishou_food", displayName = "快手美食", url = "https://www.kuaishou.com", iconResId = R.drawable.ic_search, description = "快手美食", searchUrl = "https://www.kuaishou.com/search/video?searchKey={query}&filter=food", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "weibo_food", displayName = "微博美食", url = "https://m.weibo.cn", iconResId = R.drawable.ic_weibo, description = "微博美食", searchUrl = "https://m.weibo.cn/search?containerid=100103type%3D64%26q%3D{query}%26ext%3Dfood", category = SearchEngineCategory.SOCIAL),

            // I5. 旅行平台（新增）
            SearchEngine(name = "mafengwo", displayName = "马蜂窝", url = "https://m.mafengwo.cn", iconResId = R.drawable.ic_search, description = "马蜂窝旅游", searchUrl = "https://m.mafengwo.cn/search/s.php?q={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "ctrip", displayName = "携程", url = "https://m.ctrip.com", iconResId = R.drawable.ic_search, description = "携程搜索", searchUrl = "https://m.ctrip.com/webapp/you/search?keyword={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "qunar", displayName = "去哪儿", url = "https://m.qunar.com", iconResId = R.drawable.ic_search, description = "去哪儿搜索", searchUrl = "https://m.qunar.com/touch/search/all?keyword={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "fliggy", displayName = "飞猪", url = "https://m.fliggy.com", iconResId = R.drawable.ic_search, description = "飞猪搜索", searchUrl = "https://m.fliggy.com/search.htm?q={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "ly", displayName = "同程旅行", url = "https://m.ly.com", iconResId = R.drawable.ic_search, description = "同程旅行", searchUrl = "https://m.ly.com/search/{query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "qyer", displayName = "穷游", url = "https://m.qyer.com", iconResId = R.drawable.ic_search, description = "穷游", searchUrl = "https://m.qyer.com/search/?keyword={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "xiaohongshu_travel", displayName = "小红书旅行", url = "https://www.xiaohongshu.com", iconResId = R.drawable.ic_xiaohongshu, description = "小红书旅行", searchUrl = "https://www.xiaohongshu.com/search_result?keyword={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "bilibili_travel", displayName = "B站旅行", url = "https://m.bilibili.com", iconResId = R.drawable.ic_bilibili, description = "B站旅行", searchUrl = "https://m.bilibili.com/search?keyword={query}&tid=217", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "douyin_travel", displayName = "抖音旅行", url = "https://www.douyin.com", iconResId = R.drawable.ic_search, description = "抖音旅行", searchUrl = "https://www.douyin.com/search/{query}?type=video&filter_channel=travel", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "kuaishou_travel", displayName = "快手旅行", url = "https://www.kuaishou.com", iconResId = R.drawable.ic_search, description = "快手旅行", searchUrl = "https://www.kuaishou.com/search/video?searchKey={query}&filter=travel", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "weibo_travel", displayName = "微博旅行", url = "https://m.weibo.cn", iconResId = R.drawable.ic_weibo, description = "微博旅行", searchUrl = "https://m.weibo.cn/search?containerid=100103type%3D64%26q%3D{query}%26ext%3Dtravel", category = SearchEngineCategory.SOCIAL),

            // I6. 穿搭平台（新增）
            SearchEngine(name = "xiaohongshu_fashion", displayName = "小红书穿搭", url = "https://www.xiaohongshu.com", iconResId = R.drawable.ic_xiaohongshu, description = "小红书穿搭", searchUrl = "https://www.xiaohongshu.com/search_result?keyword={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "douyin_fashion", displayName = "抖音穿搭", url = "https://www.douyin.com", iconResId = R.drawable.ic_search, description = "抖音穿搭", searchUrl = "https://www.douyin.com/search/{query}?type=video&filter_channel=fashion", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "kuaishou_fashion", displayName = "快手穿搭", url = "https://www.kuaishou.com", iconResId = R.drawable.ic_search, description = "快手穿搭", searchUrl = "https://www.kuaishou.com/search/video?searchKey={query}&filter=dress", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "bilibili_fashion", displayName = "B站穿搭", url = "https://m.bilibili.com", iconResId = R.drawable.ic_bilibili, description = "B站穿搭", searchUrl = "https://m.bilibili.com/search?keyword={query}&tid=157", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "weibo_fashion", displayName = "微博穿搭", url = "https://m.weibo.cn", iconResId = R.drawable.ic_weibo, description = "微博穿搭", searchUrl = "https://m.weibo.cn/search?containerid=100103type%3D64%26q%3D{query}%26ext%3Dfashion", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "taobao_search", displayName = "淘宝搜索", url = "https://m.taobao.com", iconResId = R.drawable.ic_taobao, description = "淘宝搜索", searchUrl = "https://m.taobao.com/search?q={query}", category = SearchEngineCategory.SHOPPING),
            SearchEngine(name = "jd_search", displayName = "京东搜索", url = "https://m.jd.com", iconResId = R.drawable.ic_jd, description = "京东搜索", searchUrl = "https://m.jd.com/search?keyword={query}", category = SearchEngineCategory.SHOPPING),
            SearchEngine(name = "poizon", displayName = "得物", url = "https://m.poizon.com", iconResId = R.drawable.ic_search, description = "得物", searchUrl = "https://m.poizon.com/search?keyword={query}", category = SearchEngineCategory.SHOPPING),
            SearchEngine(name = "mogujie", displayName = "蘑菇街", url = "https://m.mogujie.com", iconResId = R.drawable.ic_search, description = "蘑菇街", searchUrl = "https://m.mogujie.com/book/search/{query}", category = SearchEngineCategory.SHOPPING),
            SearchEngine(name = "smzdm", displayName = "什么值得买", url = "https://m.smzdm.com", iconResId = R.drawable.ic_search, description = "什么值得买", searchUrl = "https://m.smzdm.com/search/?keyword={query}", category = SearchEngineCategory.SHOPPING),
            SearchEngine(name = "meilishuo", displayName = "美丽说", url = "https://m.meilishuo.com", iconResId = R.drawable.ic_search, description = "美丽说", searchUrl = "https://m.meilishuo.com/search/show?keyword={query}", category = SearchEngineCategory.SHOPPING),

            // I7. 手工平台（新增）
            SearchEngine(name = "xiaohongshu_handmake", displayName = "小红书手工", url = "https://www.xiaohongshu.com", iconResId = R.drawable.ic_xiaohongshu, description = "小红书手工", searchUrl = "https://www.xiaohongshu.com/search_result?keyword={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "bilibili_handmake", displayName = "B站手工", url = "https://m.bilibili.com", iconResId = R.drawable.ic_bilibili, description = "B站手工", searchUrl = "https://m.bilibili.com/search?keyword={query}&tid=161", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "douyin_handmake", displayName = "抖音手工", url = "https://www.douyin.com", iconResId = R.drawable.ic_search, description = "抖音手工", searchUrl = "https://www.douyin.com/search/{query}?type=video&filter_channel=handmake", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "kuaishou_handmake", displayName = "快手手工", url = "https://www.kuaishou.com", iconResId = R.drawable.ic_search, description = "快手手工", searchUrl = "https://www.kuaishou.com/search/video?searchKey={query}&filter=craft", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "weibo_handmake", displayName = "微博手工", url = "https://m.weibo.cn", iconResId = R.drawable.ic_weibo, description = "微博手工", searchUrl = "https://m.weibo.cn/search?containerid=100103type%3D64%26q%3D{query}%26ext%3Dhandmake", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "xiachufang_handmake", displayName = "下厨房手工", url = "https://m.xiachufang.com", iconResId = R.drawable.ic_search, description = "下厨房手工", searchUrl = "https://m.xiachufang.com/search/?keyword={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "diysite", displayName = "手工客", url = "https://m.diysite.com", iconResId = R.drawable.ic_search, description = "手工客", searchUrl = "https://m.diysite.com/search/{query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "duitang", displayName = "堆糖", url = "https://m.duitang.com", iconResId = R.drawable.ic_search, description = "堆糖", searchUrl = "https://m.duitang.com/search/?kw={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "lofter", displayName = "lofter", url = "https://m.lofter.com", iconResId = R.drawable.ic_search, description = "lofter", searchUrl = "https://m.lofter.com/search/tag/{query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "douban_group", displayName = "豆瓣小组", url = "https://m.douban.com", iconResId = R.drawable.ic_douban, description = "豆瓣小组", searchUrl = "https://m.douban.com/group/search?q={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "jianshu", displayName = "简书", url = "https://www.jianshu.com", iconResId = R.drawable.ic_search, description = "简书", searchUrl = "https://www.jianshu.com/search?q={query}", category = SearchEngineCategory.SOCIAL),

            // I8. 搞笑平台（新增）
            SearchEngine(name = "douyin_funny", displayName = "抖音搞笑", url = "https://www.douyin.com", iconResId = R.drawable.ic_search, description = "抖音搞笑", searchUrl = "https://www.douyin.com/search/{query}?type=video&filter_channel=funny", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "kuaishou_funny", displayName = "快手搞笑", url = "https://www.kuaishou.com", iconResId = R.drawable.ic_search, description = "快手搞笑", searchUrl = "https://www.kuaishou.com/search/video?searchKey={query}&filter=funny", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "bilibili_funny", displayName = "B站搞笑", url = "https://m.bilibili.com", iconResId = R.drawable.ic_bilibili, description = "B站搞笑", searchUrl = "https://m.bilibili.com/search?keyword={query}&tid=138", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "weibo_funny", displayName = "微博搞笑", url = "https://m.weibo.cn", iconResId = R.drawable.ic_weibo, description = "微博搞笑", searchUrl = "https://m.weibo.cn/search?containerid=100103type%3D64%26q%3D{query}%26ext%3Dfunny", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "ppx", displayName = "皮皮虾", url = "https://m.ppx.com", iconResId = R.drawable.ic_search, description = "皮皮虾", searchUrl = "https://m.ppx.com/search?keyword={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "neihanshequ", displayName = "内涵段子", url = "https://m.neihanshequ.com", iconResId = R.drawable.ic_search, description = "内涵段子", searchUrl = "https://m.neihanshequ.com/search/?word={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "izuiyou", displayName = "最右", url = "https://m.izuiyou.com", iconResId = R.drawable.ic_search, description = "最右", searchUrl = "https://m.izuiyou.com/search?keyword={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "qiushibaike", displayName = "糗事百科", url = "https://m.qiushibaike.com", iconResId = R.drawable.ic_search, description = "糗事百科", searchUrl = "https://m.qiushibaike.com/search/?keyword={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "weishi_funny", displayName = "微视搞笑", url = "https://m.weishi.qq.com", iconResId = R.drawable.ic_search, description = "微视搞笑", searchUrl = "https://m.weishi.qq.com/search/{query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "xiaohongshu_funny", displayName = "小红书搞笑", url = "https://www.xiaohongshu.com", iconResId = R.drawable.ic_xiaohongshu, description = "小红书搞笑", searchUrl = "https://www.xiaohongshu.com/search_result?keyword={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "zhihu_funny", displayName = "知乎搞笑", url = "https://www.zhihu.com", iconResId = R.drawable.ic_zhihu, description = "知乎搞笑", searchUrl = "https://www.zhihu.com/search?type=content&q={query}", category = SearchEngineCategory.SOCIAL),

            // I9. 壁纸平台（新增）
            SearchEngine(name = "bing_images", displayName = "必应壁纸", url = "https://m.bing.com", iconResId = R.drawable.ic_bing, description = "必应壁纸移动", searchUrl = "https://m.bing.com/images/search?q={query}+wallpaper", category = SearchEngineCategory.DESIGN),
            SearchEngine(name = "sogou_images", displayName = "搜狗壁纸", url = "https://m.sogou.com", iconResId = R.drawable.ic_sogou, description = "搜狗壁纸", searchUrl = "https://m.sogou.com/images/search?query={query}+壁纸", category = SearchEngineCategory.DESIGN),
            SearchEngine(name = "360_images", displayName = "360壁纸", url = "https://m.so.com", iconResId = R.drawable.ic_360search, description = "360壁纸", searchUrl = "https://m.so.com/image?q={query}+壁纸", category = SearchEngineCategory.DESIGN),
            SearchEngine(name = "duitang_wallpaper", displayName = "堆糖壁纸", url = "https://m.duitang.com", iconResId = R.drawable.ic_search, description = "堆糖壁纸", searchUrl = "https://m.duitang.com/search/?kw={query}+壁纸", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "xiaohongshu_wallpaper", displayName = "小红书壁纸", url = "https://www.xiaohongshu.com", iconResId = R.drawable.ic_xiaohongshu, description = "小红书壁纸", searchUrl = "https://www.xiaohongshu.com/search_result?keyword={query}+壁纸", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "douyin_wallpaper", displayName = "抖音壁纸", url = "https://www.douyin.com", iconResId = R.drawable.ic_search, description = "抖音壁纸", searchUrl = "https://www.douyin.com/search/{query}+壁纸?type=video", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "kuaishou_wallpaper", displayName = "快手壁纸", url = "https://www.kuaishou.com", iconResId = R.drawable.ic_search, description = "快手壁纸", searchUrl = "https://www.kuaishou.com/search/video?searchKey={query}+壁纸", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "bilibili_wallpaper", displayName = "B站壁纸", url = "https://m.bilibili.com", iconResId = R.drawable.ic_bilibili, description = "B站壁纸", searchUrl = "https://m.bilibili.com/search?keyword={query}+壁纸", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "lofter_wallpaper", displayName = "lofter壁纸", url = "https://m.lofter.com", iconResId = R.drawable.ic_search, description = "lofter壁纸", searchUrl = "https://m.lofter.com/search/tag/{query}+壁纸", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "douban_wallpaper", displayName = "豆瓣壁纸小组", url = "https://m.douban.com", iconResId = R.drawable.ic_douban, description = "豆瓣壁纸小组", searchUrl = "https://m.douban.com/group/search?q={query}+壁纸", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "zzzmh", displayName = "极简壁纸", url = "https://m.zzzmh.cn", iconResId = R.drawable.ic_search, description = "极简壁纸", searchUrl = "https://m.zzzmh.cn/s?q={query}", category = SearchEngineCategory.DESIGN),

            // I10. 摄影平台（新增）
            SearchEngine(name = "500px", displayName = "500px", url = "https://m.500px.com", iconResId = R.drawable.ic_search, description = "500px", searchUrl = "https://m.500px.com/search?q={query}", category = SearchEngineCategory.DESIGN),
            SearchEngine(name = "tuchong", displayName = "图虫", url = "https://m.tuchong.com", iconResId = R.drawable.ic_search, description = "图虫", searchUrl = "https://m.tuchong.com/search/?q={query}", category = SearchEngineCategory.DESIGN),
            SearchEngine(name = "lofter_photo", displayName = "lofter摄影", url = "https://m.lofter.com", iconResId = R.drawable.ic_search, description = "lofter摄影", searchUrl = "https://m.lofter.com/search/tag/{query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "xiaohongshu_photo", displayName = "小红书摄影", url = "https://www.xiaohongshu.com", iconResId = R.drawable.ic_xiaohongshu, description = "小红书摄影", searchUrl = "https://www.xiaohongshu.com/search_result?keyword={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "bilibili_photo", displayName = "B站摄影", url = "https://m.bilibili.com", iconResId = R.drawable.ic_bilibili, description = "B站摄影", searchUrl = "https://m.bilibili.com/search?keyword={query}&tid=95", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "douyin_photo", displayName = "抖音摄影", url = "https://www.douyin.com", iconResId = R.drawable.ic_search, description = "抖音摄影", searchUrl = "https://www.douyin.com/search/{query}?type=video&filter_channel=photography", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "kuaishou_photo", displayName = "快手摄影", url = "https://www.kuaishou.com", iconResId = R.drawable.ic_search, description = "快手摄影", searchUrl = "https://www.kuaishou.com/search/video?searchKey={query}&filter=photo", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "weibo_photo", displayName = "微博摄影", url = "https://m.weibo.cn", iconResId = R.drawable.ic_weibo, description = "微博摄影", searchUrl = "https://m.weibo.cn/search?containerid=100103type%3D64%26q%3D{query}%26ext%3Dphoto", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "zhihu_photo", displayName = "知乎摄影", url = "https://www.zhihu.com", iconResId = R.drawable.ic_zhihu, description = "知乎摄影", searchUrl = "https://www.zhihu.com/search?type=content&q={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "douban_photo", displayName = "豆瓣摄影小组", url = "https://m.douban.com", iconResId = R.drawable.ic_douban, description = "豆瓣摄影小组", searchUrl = "https://m.douban.com/group/search?q={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "nikonclub", displayName = "尼康俱乐部", url = "https://m.nikonclub.cn", iconResId = R.drawable.ic_search, description = "尼康俱乐部", searchUrl = "https://m.nikonclub.cn/search/?keyword={query}", category = SearchEngineCategory.DESIGN),

            // I11. 音乐平台（新增）
            SearchEngine(name = "qqmusic", displayName = "QQ音乐", url = "https://m.y.qq.com", iconResId = R.drawable.ic_search, description = "QQ音乐移动", searchUrl = "https://m.y.qq.com/search.html?key={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "netease_music", displayName = "网易云音乐", url = "https://m.music.163.com", iconResId = R.drawable.ic_search, description = "网易云音乐", searchUrl = "https://m.music.163.com/search/?s={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "kugou", displayName = "酷狗音乐", url = "https://m.kugou.com", iconResId = R.drawable.ic_search, description = "酷狗音乐", searchUrl = "https://m.kugou.com/search/index?keyword={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "kuwo", displayName = "酷我音乐", url = "https://m.kuwo.cn", iconResId = R.drawable.ic_search, description = "酷我音乐", searchUrl = "https://m.kuwo.cn/search?key={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "migu_music", displayName = "咪咕音乐", url = "https://m.music.migu.cn", iconResId = R.drawable.ic_search, description = "咪咕音乐", searchUrl = "https://m.music.migu.cn/search?key={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "qishui", displayName = "汽水音乐", url = "https://m.qishui.com", iconResId = R.drawable.ic_search, description = "汽水音乐", searchUrl = "https://m.qishui.com/search?keyword={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "douyin_music", displayName = "抖音音乐", url = "https://www.douyin.com", iconResId = R.drawable.ic_search, description = "抖音音乐", searchUrl = "https://www.douyin.com/search/{query}?type=video&filter_channel=music", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "bilibili_music", displayName = "B站音乐", url = "https://m.bilibili.com", iconResId = R.drawable.ic_bilibili, description = "B站音乐", searchUrl = "https://m.bilibili.com/search?keyword={query}&tid=3", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "kuaishou_music", displayName = "快手音乐", url = "https://www.kuaishou.com", iconResId = R.drawable.ic_search, description = "快手音乐", searchUrl = "https://www.kuaishou.com/search/video?searchKey={query}&filter=music", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "weibo_music", displayName = "微博音乐", url = "https://m.weibo.cn", iconResId = R.drawable.ic_weibo, description = "微博音乐", searchUrl = "https://m.weibo.cn/search?containerid=100103type%3D64%26q%3D{query}%26ext%3Dmusic", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "soundcloud", displayName = "SoundCloud", url = "https://m.soundcloud.com", iconResId = R.drawable.ic_search, description = "SoundCloud移动", searchUrl = "https://m.soundcloud.com/search?q={query}", category = SearchEngineCategory.VIDEO),

            // I12. 家装平台（新增）
            SearchEngine(name = "xiaohongshu_home", displayName = "小红书家装", url = "https://www.xiaohongshu.com", iconResId = R.drawable.ic_xiaohongshu, description = "小红书家装", searchUrl = "https://www.xiaohongshu.com/search_result?keyword={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "douyin_home", displayName = "抖音家装", url = "https://www.douyin.com", iconResId = R.drawable.ic_search, description = "抖音家装", searchUrl = "https://www.douyin.com/search/{query}?type=video&filter_channel=home", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "bilibili_home", displayName = "B站家装", url = "https://m.bilibili.com", iconResId = R.drawable.ic_bilibili, description = "B站家装", searchUrl = "https://m.bilibili.com/search?keyword={query}&tid=47", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "weibo_home", displayName = "微博家装", url = "https://m.weibo.cn", iconResId = R.drawable.ic_weibo, description = "微博家装", searchUrl = "https://m.weibo.cn/search?containerid=100103type%3D64%26q%3D{query}%26ext%3Dhome", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "haohaozhu", displayName = "好好住", url = "https://m.haohaozhu.com", iconResId = R.drawable.ic_search, description = "好好住", searchUrl = "https://m.haohaozhu.com/search/?keyword={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "yidoutang", displayName = "一兜糖", url = "https://m.yidoutang.com", iconResId = R.drawable.ic_search, description = "一兜糖", searchUrl = "https://m.yidoutang.com/search/?keyword={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "jia", displayName = "齐家网", url = "https://m.jia.com", iconResId = R.drawable.ic_search, description = "齐家网", searchUrl = "https://m.jia.com/search/{query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "to8to", displayName = "土巴兔", url = "https://m.to8to.com", iconResId = R.drawable.ic_search, description = "土巴兔", searchUrl = "https://m.to8to.com/search/{query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "jd_home", displayName = "京东家居", url = "https://m.jd.com", iconResId = R.drawable.ic_jd, description = "京东家居", searchUrl = "https://m.jd.com/search?keyword={query}+家居", category = SearchEngineCategory.SHOPPING),
            SearchEngine(name = "taobao_home", displayName = "淘宝家居", url = "https://m.taobao.com", iconResId = R.drawable.ic_taobao, description = "淘宝家居", searchUrl = "https://m.taobao.com/search?q={query}+家装", category = SearchEngineCategory.SHOPPING),
            SearchEngine(name = "zhihu_home", displayName = "知乎家装", url = "https://www.zhihu.com", iconResId = R.drawable.ic_zhihu, description = "知乎家装", searchUrl = "https://www.zhihu.com/search?type=content&q={query}", category = SearchEngineCategory.SOCIAL),

            // I13. 汽车平台（新增）
            SearchEngine(name = "autohome", displayName = "汽车之家", url = "https://m.autohome.com.cn", iconResId = R.drawable.ic_search, description = "汽车之家", searchUrl = "https://m.autohome.com.cn/search/?kw={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "dongchedi", displayName = "懂车帝", url = "https://m.dongchedi.com", iconResId = R.drawable.ic_search, description = "懂车帝", searchUrl = "https://m.dongchedi.com/search?keyword={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "yiche", displayName = "易车", url = "https://m.yiche.com", iconResId = R.drawable.ic_search, description = "易车", searchUrl = "https://m.yiche.com/search/?keyword={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "pcauto", displayName = "太平洋汽车", url = "https://m.pcauto.com.cn", iconResId = R.drawable.ic_search, description = "太平洋汽车", searchUrl = "https://m.pcauto.com.cn/search/?q={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "xcar", displayName = "爱卡汽车", url = "https://m.xcar.com.cn", iconResId = R.drawable.ic_search, description = "爱卡汽车", searchUrl = "https://m.xcar.com.cn/search/{query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "xiaohongshu_car", displayName = "小红书汽车", url = "https://www.xiaohongshu.com", iconResId = R.drawable.ic_xiaohongshu, description = "小红书汽车", searchUrl = "https://www.xiaohongshu.com/search_result?keyword={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "douyin_car", displayName = "抖音汽车", url = "https://www.douyin.com", iconResId = R.drawable.ic_search, description = "抖音汽车", searchUrl = "https://www.douyin.com/search/{query}?type=video&filter_channel=car", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "bilibili_car", displayName = "B站汽车", url = "https://m.bilibili.com", iconResId = R.drawable.ic_bilibili, description = "B站汽车", searchUrl = "https://m.bilibili.com/search?keyword={query}&tid=188", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "weibo_car", displayName = "微博汽车", url = "https://m.weibo.cn", iconResId = R.drawable.ic_weibo, description = "微博汽车", searchUrl = "https://m.weibo.cn/search?containerid=100103type%3D64%26q%3D{query}%26ext%3Dcar", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "zhihu_car", displayName = "知乎汽车", url = "https://www.zhihu.com", iconResId = R.drawable.ic_zhihu, description = "知乎汽车", searchUrl = "https://www.zhihu.com/search?type=content&q={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "jd_car", displayName = "京东汽车", url = "https://m.jd.com", iconResId = R.drawable.ic_jd, description = "京东汽车", searchUrl = "https://m.jd.com/search?keyword={query}", category = SearchEngineCategory.SHOPPING),

            // I14. 情感平台（新增）
            SearchEngine(name = "xiaohongshu_emotion", displayName = "小红书情感", url = "https://www.xiaohongshu.com", iconResId = R.drawable.ic_xiaohongshu, description = "小红书情感", searchUrl = "https://www.xiaohongshu.com/search_result?keyword={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "douyin_emotion", displayName = "抖音情感", url = "https://www.douyin.com", iconResId = R.drawable.ic_search, description = "抖音情感", searchUrl = "https://www.douyin.com/search/{query}?type=video&filter_channel=emotion", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "kuaishou_emotion", displayName = "快手情感", url = "https://www.kuaishou.com", iconResId = R.drawable.ic_search, description = "快手情感", searchUrl = "https://www.kuaishou.com/search/video?searchKey={query}&filter=emotion", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "bilibili_emotion", displayName = "B站情感", url = "https://m.bilibili.com", iconResId = R.drawable.ic_bilibili, description = "B站情感", searchUrl = "https://m.bilibili.com/search?keyword={query}&tid=158", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "weibo_emotion", displayName = "微博情感", url = "https://m.weibo.cn", iconResId = R.drawable.ic_weibo, description = "微博情感", searchUrl = "https://m.weibo.cn/search?containerid=100103type%3D64%26q%3D{query}%26ext%3Demotion", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "zhihu_emotion", displayName = "知乎情感", url = "https://www.zhihu.com", iconResId = R.drawable.ic_zhihu, description = "知乎情感", searchUrl = "https://www.zhihu.com/search?type=content&q={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "douban_emotion", displayName = "豆瓣小组", url = "https://m.douban.com", iconResId = R.drawable.ic_douban, description = "豆瓣小组", searchUrl = "https://m.douban.com/group/search?q={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "xinli001", displayName = "壹心理", url = "https://m.xinli001.com", iconResId = R.drawable.ic_search, description = "壹心理", searchUrl = "https://m.xinli001.com/search?keyword={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "songguo7", displayName = "松果倾诉", url = "https://m.songguo7.com", iconResId = R.drawable.ic_search, description = "松果倾诉", searchUrl = "https://m.songguo7.com/search?keyword={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "tianya", displayName = "天涯社区", url = "https://m.tianya.cn", iconResId = R.drawable.ic_search, description = "天涯社区", searchUrl = "https://m.tianya.cn/search?q={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "jianshu_emotion", displayName = "简书情感", url = "https://www.jianshu.com", iconResId = R.drawable.ic_search, description = "简书情感", searchUrl = "https://www.jianshu.com/search?q={query}", category = SearchEngineCategory.SOCIAL),

            // I15. 家居平台（新增）
            SearchEngine(name = "xiaohongshu_furniture", displayName = "小红书家居", url = "https://www.xiaohongshu.com", iconResId = R.drawable.ic_xiaohongshu, description = "小红书家居", searchUrl = "https://www.xiaohongshu.com/search_result?keyword={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "douyin_furniture", displayName = "抖音家居", url = "https://www.douyin.com", iconResId = R.drawable.ic_search, description = "抖音家居", searchUrl = "https://www.douyin.com/search/{query}?type=video&filter_channel=furniture", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "bilibili_furniture", displayName = "B站家居", url = "https://m.bilibili.com", iconResId = R.drawable.ic_bilibili, description = "B站家居", searchUrl = "https://m.bilibili.com/search?keyword={query}&tid=47", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "weibo_furniture", displayName = "微博家居", url = "https://m.weibo.cn", iconResId = R.drawable.ic_weibo, description = "微博家居", searchUrl = "https://m.weibo.cn/search?containerid=100103type%3D64%26q%3D{query}%26ext%3Dfurniture", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "haohaozhu_furniture", displayName = "好好住家居", url = "https://m.haohaozhu.com", iconResId = R.drawable.ic_search, description = "好好住家居", searchUrl = "https://m.haohaozhu.com/search/?keyword={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "yidoutang_furniture", displayName = "一兜糖家居", url = "https://m.yidoutang.com", iconResId = R.drawable.ic_search, description = "一兜糖家居", searchUrl = "https://m.yidoutang.com/search/?keyword={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "jd_furniture", displayName = "京东家居", url = "https://m.jd.com", iconResId = R.drawable.ic_jd, description = "京东家居", searchUrl = "https://m.jd.com/search?keyword={query}", category = SearchEngineCategory.SHOPPING),
            SearchEngine(name = "taobao_furniture", displayName = "淘宝家居", url = "https://m.taobao.com", iconResId = R.drawable.ic_taobao, description = "淘宝家居", searchUrl = "https://m.taobao.com/search?q={query}", category = SearchEngineCategory.SHOPPING),
            SearchEngine(name = "ikea", displayName = "宜家", url = "https://m.ikea.com.cn", iconResId = R.drawable.ic_search, description = "宜家", searchUrl = "https://m.ikea.com.cn/search/?query={query}", category = SearchEngineCategory.SHOPPING),
            SearchEngine(name = "you163", displayName = "网易严选", url = "https://m.you.163.com", iconResId = R.drawable.ic_search, description = "网易严选", searchUrl = "https://m.you.163.com/search?q={query}", category = SearchEngineCategory.SHOPPING),
            SearchEngine(name = "zhihu_furniture", displayName = "知乎家居", url = "https://www.zhihu.com", iconResId = R.drawable.ic_zhihu, description = "知乎家居", searchUrl = "https://www.zhihu.com/search?type=content&q={query}", category = SearchEngineCategory.SOCIAL),

            // I16. 游戏平台（新增）
            SearchEngine(name = "taptap", displayName = "TapTap", url = "https://m.taptap.com", iconResId = R.drawable.ic_search, description = "TapTap", searchUrl = "https://m.taptap.com/search/{query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "bilibili_game", displayName = "B站游戏", url = "https://m.bilibili.com", iconResId = R.drawable.ic_bilibili, description = "B站游戏", searchUrl = "https://m.bilibili.com/search?keyword={query}&tid=4", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "douyin_game", displayName = "抖音游戏", url = "https://www.douyin.com", iconResId = R.drawable.ic_search, description = "抖音游戏", searchUrl = "https://www.douyin.com/search/{query}?type=video&filter_channel=game", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "kuaishou_game", displayName = "快手游戏", url = "https://www.kuaishou.com", iconResId = R.drawable.ic_search, description = "快手游戏", searchUrl = "https://www.kuaishou.com/search/video?searchKey={query}&filter=game", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "weibo_game", displayName = "微博游戏", url = "https://m.weibo.cn", iconResId = R.drawable.ic_weibo, description = "微博游戏", searchUrl = "https://m.weibo.cn/search?containerid=100103type%3D64%26q%3D{query}%26ext%3Dgame", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "xiaohongshu_game", displayName = "小红书游戏", url = "https://www.xiaohongshu.com", iconResId = R.drawable.ic_xiaohongshu, description = "小红书游戏", searchUrl = "https://www.xiaohongshu.com/search_result?keyword={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "zhihu_game", displayName = "知乎游戏", url = "https://www.zhihu.com", iconResId = R.drawable.ic_zhihu, description = "知乎游戏", searchUrl = "https://www.zhihu.com/search?type=content&q={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "4399", displayName = "4399", url = "https://m.4399.cn", iconResId = R.drawable.ic_search, description = "4399移动", searchUrl = "https://m.4399.cn/search/?k={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "gamersky", displayName = "游民星空", url = "https://m.gamersky.com", iconResId = R.drawable.ic_search, description = "游民星空", searchUrl = "https://m.gamersky.com/search/?q={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "gcores", displayName = "机核", url = "https://m.gcores.com", iconResId = R.drawable.ic_search, description = "机核", searchUrl = "https://m.gcores.com/search/{query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "ds163", displayName = "网易大神", url = "https://m.ds.163.com", iconResId = R.drawable.ic_search, description = "网易大神", searchUrl = "https://m.ds.163.com/search?q={query}", category = SearchEngineCategory.VIDEO),

            // I17. 影视平台（新增）
            SearchEngine(name = "douban_movie", displayName = "豆瓣影视", url = "https://m.douban.com", iconResId = R.drawable.ic_douban, description = "豆瓣影视", searchUrl = "https://m.douban.com/search?q={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "maoyan", displayName = "猫眼", url = "https://m.maoyan.com", iconResId = R.drawable.ic_search, description = "猫眼", searchUrl = "https://m.maoyan.com/search?kw={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "taopiaopiao", displayName = "淘票票", url = "https://m.taopiaopiao.com", iconResId = R.drawable.ic_search, description = "淘票票", searchUrl = "https://m.taopiaopiao.com/search.html?kw={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "iqiyi_movie", displayName = "爱奇艺影视", url = "https://m.iqiyi.com", iconResId = R.drawable.ic_search, description = "爱奇艺影视", searchUrl = "https://m.iqiyi.com/search.html?key={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "tencent_video_movie", displayName = "腾讯视频影视", url = "https://m.v.qq.com", iconResId = R.drawable.ic_search, description = "腾讯视频影视", searchUrl = "https://m.v.qq.com/x/search/?q={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "youku_movie", displayName = "优酷影视", url = "https://m.youku.com", iconResId = R.drawable.ic_search, description = "优酷影视", searchUrl = "https://m.youku.com/video/search?q={query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "bilibili_movie", displayName = "B站影视", url = "https://m.bilibili.com", iconResId = R.drawable.ic_bilibili, description = "B站影视", searchUrl = "https://m.bilibili.com/search?keyword={query}&tid=11", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "douyin_movie", displayName = "抖音影视", url = "https://www.douyin.com", iconResId = R.drawable.ic_search, description = "抖音影视", searchUrl = "https://www.douyin.com/search/{query}?type=video&filter_channel=movie", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "kuaishou_movie", displayName = "快手影视", url = "https://www.kuaishou.com", iconResId = R.drawable.ic_search, description = "快手影视", searchUrl = "https://www.kuaishou.com/search/video?searchKey={query}&filter=movie", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "weibo_movie", displayName = "微博影视", url = "https://m.weibo.cn", iconResId = R.drawable.ic_weibo, description = "微博影视", searchUrl = "https://m.weibo.cn/search?containerid=100103type%3D64%26q%3D{query}%26ext%3Dmovie", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "zhihu_movie", displayName = "知乎影视", url = "https://www.zhihu.com", iconResId = R.drawable.ic_zhihu, description = "知乎影视", searchUrl = "https://www.zhihu.com/search?type=content&q={query}", category = SearchEngineCategory.SOCIAL),

            // I18. 科技数码平台（新增）
            SearchEngine(name = "zhihu_tech", displayName = "知乎科技", url = "https://www.zhihu.com", iconResId = R.drawable.ic_zhihu, description = "知乎科技", searchUrl = "https://www.zhihu.com/search?type=content&q={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "xiaohongshu_tech", displayName = "小红书科技", url = "https://www.xiaohongshu.com", iconResId = R.drawable.ic_xiaohongshu, description = "小红书科技", searchUrl = "https://www.xiaohongshu.com/search_result?keyword={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "bilibili_tech", displayName = "B站科技", url = "https://m.bilibili.com", iconResId = R.drawable.ic_bilibili, description = "B站科技", searchUrl = "https://m.bilibili.com/search?keyword={query}&tid=188", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "douyin_tech", displayName = "抖音科技", url = "https://www.douyin.com", iconResId = R.drawable.ic_search, description = "抖音科技", searchUrl = "https://www.douyin.com/search/{query}?type=video&filter_channel=tech", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "kuaishou_tech", displayName = "快手科技", url = "https://www.kuaishou.com", iconResId = R.drawable.ic_search, description = "快手科技", searchUrl = "https://www.kuaishou.com/search/video?searchKey={query}&filter=tech", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "weibo_tech", displayName = "微博科技", url = "https://m.weibo.cn", iconResId = R.drawable.ic_weibo, description = "微博科技", searchUrl = "https://m.weibo.cn/search?containerid=100103type%3D64%26q%3D{query}%26ext%3Dtech", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "ithome", displayName = "IT之家", url = "https://m.ithome.com", iconResId = R.drawable.ic_search, description = "IT之家", searchUrl = "https://m.ithome.com/search/{query}", category = SearchEngineCategory.DEVELOPER),
            SearchEngine(name = "pconline", displayName = "太平洋电脑", url = "https://m.pconline.com.cn", iconResId = R.drawable.ic_search, description = "太平洋电脑", searchUrl = "https://m.pconline.com.cn/search/?q={query}", category = SearchEngineCategory.DEVELOPER),
            SearchEngine(name = "zol", displayName = "中关村在线", url = "https://m.zol.com.cn", iconResId = R.drawable.ic_search, description = "中关村在线", searchUrl = "https://m.zol.com.cn/search/{query}", category = SearchEngineCategory.DEVELOPER),
            SearchEngine(name = "smzdm_tech", displayName = "什么值得买科技", url = "https://m.smzdm.com", iconResId = R.drawable.ic_search, description = "什么值得买科技", searchUrl = "https://m.smzdm.com/search/?keyword={query}", category = SearchEngineCategory.SHOPPING),
            SearchEngine(name = "jd_tech", displayName = "京东数码", url = "https://m.jd.com", iconResId = R.drawable.ic_jd, description = "京东数码", searchUrl = "https://m.jd.com/search?keyword={query}", category = SearchEngineCategory.SHOPPING),

            // I19. 绘画平台（新增）
            SearchEngine(name = "xiaohongshu_paint", displayName = "小红书绘画", url = "https://www.xiaohongshu.com", iconResId = R.drawable.ic_xiaohongshu, description = "小红书绘画", searchUrl = "https://www.xiaohongshu.com/search_result?keyword={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "bilibili_paint", displayName = "B站绘画", url = "https://m.bilibili.com", iconResId = R.drawable.ic_bilibili, description = "B站绘画", searchUrl = "https://m.bilibili.com/search?keyword={query}&tid=191", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "douyin_paint", displayName = "抖音绘画", url = "https://www.douyin.com", iconResId = R.drawable.ic_search, description = "抖音绘画", searchUrl = "https://www.douyin.com/search/{query}?type=video&filter_channel=paint", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "kuaishou_paint", displayName = "快手绘画", url = "https://www.kuaishou.com", iconResId = R.drawable.ic_search, description = "快手绘画", searchUrl = "https://www.kuaishou.com/search/video?searchKey={query}&filter=paint", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "weibo_paint", displayName = "微博绘画", url = "https://m.weibo.cn", iconResId = R.drawable.ic_weibo, description = "微博绘画", searchUrl = "https://m.weibo.cn/search?containerid=100103type%3D64%26q%3D{query}%26ext%3Dpaint", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "lofter_paint", displayName = "lofter绘画", url = "https://m.lofter.com", iconResId = R.drawable.ic_search, description = "lofter绘画", searchUrl = "https://m.lofter.com/search/tag/{query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "zcool_paint", displayName = "站酷绘画", url = "https://m.zcool.com.cn", iconResId = R.drawable.ic_search, description = "站酷绘画", searchUrl = "https://m.zcool.com.cn/search?word={query}", category = SearchEngineCategory.DESIGN),
            SearchEngine(name = "poocg", displayName = "涂鸦王国", url = "https://m.poocg.com", iconResId = R.drawable.ic_search, description = "涂鸦王国", searchUrl = "https://m.poocg.com/search/{query}", category = SearchEngineCategory.DESIGN),
            SearchEngine(name = "pixiv", displayName = "Pixiv", url = "https://m.pixiv.net", iconResId = R.drawable.ic_search, description = "Pixiv移动", searchUrl = "https://m.pixiv.net/search.php?word={query}", category = SearchEngineCategory.DESIGN),
            SearchEngine(name = "artstation", displayName = "ArtStation", url = "https://m.artstation.com", iconResId = R.drawable.ic_search, description = "ArtStation移动", searchUrl = "https://m.artstation.com/search?q={query}", category = SearchEngineCategory.DESIGN),
            SearchEngine(name = "douban_paint", displayName = "豆瓣绘画小组", url = "https://m.douban.com", iconResId = R.drawable.ic_douban, description = "豆瓣绘画小组", searchUrl = "https://m.douban.com/group/search?q={query}", category = SearchEngineCategory.SOCIAL),

            // I20. 健身塑型平台（新增）
            SearchEngine(name = "keep", displayName = "Keep", url = "https://m.gotokeep.com", iconResId = R.drawable.ic_search, description = "Keep搜索", searchUrl = "https://m.gotokeep.com/search?keyword={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "xiaohongshu_fitness", displayName = "小红书健身", url = "https://www.xiaohongshu.com", iconResId = R.drawable.ic_xiaohongshu, description = "小红书健身", searchUrl = "https://www.xiaohongshu.com/search_result?keyword={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "bilibili_fitness", displayName = "B站健身", url = "https://m.bilibili.com", iconResId = R.drawable.ic_bilibili, description = "B站健身", searchUrl = "https://m.bilibili.com/search?keyword={query}&tid=86", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "douyin_fitness", displayName = "抖音健身", url = "https://www.douyin.com", iconResId = R.drawable.ic_search, description = "抖音健身", searchUrl = "https://www.douyin.com/search/{query}?type=video&filter_channel=fitness", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "kuaishou_fitness", displayName = "快手健身", url = "https://www.kuaishou.com", iconResId = R.drawable.ic_search, description = "快手健身", searchUrl = "https://www.kuaishou.com/search/video?searchKey={query}&filter=fitness", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "weibo_fitness", displayName = "微博健身", url = "https://m.weibo.cn", iconResId = R.drawable.ic_weibo, description = "微博健身", searchUrl = "https://m.weibo.cn/search?containerid=100103type%3D64%26q%3D{query}%26ext%3Dfitness", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "zhihu_fitness", displayName = "知乎健身", url = "https://www.zhihu.com", iconResId = R.drawable.ic_zhihu, description = "知乎健身", searchUrl = "https://www.zhihu.com/search?type=content&q={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "codoon", displayName = "咕咚", url = "https://m.codoon.com", iconResId = R.drawable.ic_search, description = "咕咚", searchUrl = "https://m.codoon.com/search?keyword={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "yuepaocircle", displayName = "悦跑圈", url = "https://m.yuepaocircle.com", iconResId = R.drawable.ic_search, description = "悦跑圈", searchUrl = "https://m.yuepaocircle.com/search?keyword={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "boohee", displayName = "薄荷健康", url = "https://m.boohee.com", iconResId = R.drawable.ic_search, description = "薄荷健康", searchUrl = "https://m.boohee.com/search?keyword={query}", category = SearchEngineCategory.LIFESTYLE),
            SearchEngine(name = "leoao", displayName = "乐刻运动", url = "https://m.leoao.com", iconResId = R.drawable.ic_search, description = "乐刻运动", searchUrl = "https://m.leoao.com/search?keyword={query}", category = SearchEngineCategory.LIFESTYLE),

            // I21. 职场平台（新增）
            SearchEngine(name = "zhihu_job", displayName = "知乎职场", url = "https://www.zhihu.com", iconResId = R.drawable.ic_zhihu, description = "知乎职场", searchUrl = "https://www.zhihu.com/search?type=content&q={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "xiaohongshu_job", displayName = "小红书职场", url = "https://www.xiaohongshu.com", iconResId = R.drawable.ic_xiaohongshu, description = "小红书职场", searchUrl = "https://www.xiaohongshu.com/search_result?keyword={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "bilibili_job", displayName = "B站职场", url = "https://m.bilibili.com", iconResId = R.drawable.ic_bilibili, description = "B站职场", searchUrl = "https://m.bilibili.com/search?keyword={query}&tid=207", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "douyin_job", displayName = "抖音职场", url = "https://www.douyin.com", iconResId = R.drawable.ic_search, description = "抖音职场", searchUrl = "https://www.douyin.com/search/{query}?type=video&filter_channel=job", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "kuaishou_job", displayName = "快手职场", url = "https://www.kuaishou.com", iconResId = R.drawable.ic_search, description = "快手职场", searchUrl = "https://www.kuaishou.com/search/video?searchKey={query}&filter=job", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "weibo_job", displayName = "微博职场", url = "https://m.weibo.cn", iconResId = R.drawable.ic_weibo, description = "微博职场", searchUrl = "https://m.weibo.cn/search?containerid=100103type%3D64%26q%3D{query}%26ext%3Djob", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "maimai", displayName = "脉脉", url = "https://m.maimai.cn", iconResId = R.drawable.ic_search, description = "脉脉搜索", searchUrl = "https://m.maimai.cn/search/web?keyword={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "lagou", displayName = "拉勾", url = "https://m.lagou.com", iconResId = R.drawable.ic_search, description = "拉勾", searchUrl = "https://m.lagou.com/search/{query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "zhipin", displayName = "BOSS直聘", url = "https://m.zhipin.com", iconResId = R.drawable.ic_search, description = "BOSS直聘", searchUrl = "https://m.zhipin.com/search/?query={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "liepin", displayName = "猎聘", url = "https://m.liepin.com", iconResId = R.drawable.ic_search, description = "猎聘", searchUrl = "https://m.liepin.com/search/?keyword={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "linkedin", displayName = "LinkedIn", url = "https://m.linkedin.com", iconResId = R.drawable.ic_search, description = "LinkedIn移动", searchUrl = "https://m.linkedin.com/search/results/all/?keywords={query}", category = SearchEngineCategory.SOCIAL),

            // I22. 头像平台（新增）
            SearchEngine(name = "xiaohongshu_avatar", displayName = "小红书头像", url = "https://www.xiaohongshu.com", iconResId = R.drawable.ic_xiaohongshu, description = "小红书头像", searchUrl = "https://www.xiaohongshu.com/search_result?keyword={query}+头像", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "duitang_avatar", displayName = "堆糖头像", url = "https://m.duitang.com", iconResId = R.drawable.ic_search, description = "堆糖头像", searchUrl = "https://m.duitang.com/search/?kw={query}+头像", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "lofter_avatar", displayName = "lofter头像", url = "https://m.lofter.com", iconResId = R.drawable.ic_search, description = "lofter头像", searchUrl = "https://m.lofter.com/search/tag/{query}+头像", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "douyin_avatar", displayName = "抖音头像", url = "https://www.douyin.com", iconResId = R.drawable.ic_search, description = "抖音头像", searchUrl = "https://www.douyin.com/search/{query}+头像?type=video", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "kuaishou_avatar", displayName = "快手头像", url = "https://www.kuaishou.com", iconResId = R.drawable.ic_search, description = "快手头像", searchUrl = "https://www.kuaishou.com/search/video?searchKey={query}+头像", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "bilibili_avatar", displayName = "B站头像", url = "https://m.bilibili.com", iconResId = R.drawable.ic_bilibili, description = "B站头像", searchUrl = "https://m.bilibili.com/search?keyword={query}+头像", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "weibo_avatar", displayName = "微博头像", url = "https://m.weibo.cn", iconResId = R.drawable.ic_weibo, description = "微博头像", searchUrl = "https://m.weibo.cn/search?containerid=100103type%3D64%26q%3D{query}+头像", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "canva", displayName = "Canva", url = "https://m.canva.cn", iconResId = R.drawable.ic_search, description = "Canva移动", searchUrl = "https://m.canva.cn/search/templates/{query}+头像", category = SearchEngineCategory.DESIGN),
            SearchEngine(name = "touxiangdaquan", displayName = "头像大全", url = "https://m.touxiangdaquan.com", iconResId = R.drawable.ic_search, description = "头像大全", searchUrl = "https://m.touxiangdaquan.com/search/?keyword={query}", category = SearchEngineCategory.DESIGN),
            SearchEngine(name = "qzone", displayName = "QQ空间", url = "https://m.qzone.qq.com", iconResId = R.drawable.ic_search, description = "QQ头像", searchUrl = "https://m.qzone.qq.com/search/头像?keyword={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "weixin", displayName = "微信", url = "https://m.weixin.qq.com", iconResId = R.drawable.ic_search, description = "微信头像", searchUrl = "https://m.weixin.qq.com/cgi-bin/search?query={query}+头像", category = SearchEngineCategory.SOCIAL),

            // I23. 读书平台（新增）
            SearchEngine(name = "weread", displayName = "微信读书", url = "https://m.weread.qq.com", iconResId = R.drawable.ic_search, description = "微信读书", searchUrl = "https://m.weread.qq.com/search?keyword={query}", category = SearchEngineCategory.KNOWLEDGE),
            SearchEngine(name = "douban_book", displayName = "豆瓣读书", url = "https://m.douban.com", iconResId = R.drawable.ic_douban, description = "豆瓣读书", searchUrl = "https://m.douban.com/search?q={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "zhihu_book", displayName = "知乎读书", url = "https://www.zhihu.com", iconResId = R.drawable.ic_zhihu, description = "知乎读书", searchUrl = "https://www.zhihu.com/search?type=content&q={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "xiaohongshu_book", displayName = "小红书读书", url = "https://www.xiaohongshu.com", iconResId = R.drawable.ic_xiaohongshu, description = "小红书读书", searchUrl = "https://www.xiaohongshu.com/search_result?keyword={query}", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "bilibili_book", displayName = "B站读书", url = "https://m.bilibili.com", iconResId = R.drawable.ic_bilibili, description = "B站读书", searchUrl = "https://m.bilibili.com/search?keyword={query}&tid=123", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "douyin_book", displayName = "抖音读书", url = "https://www.douyin.com", iconResId = R.drawable.ic_search, description = "抖音读书", searchUrl = "https://www.douyin.com/search/{query}?type=video&filter_channel=book", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "kuaishou_book", displayName = "快手读书", url = "https://www.kuaishou.com", iconResId = R.drawable.ic_search, description = "快手读书", searchUrl = "https://www.kuaishou.com/search/video?searchKey={query}&filter=book", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "weibo_book", displayName = "微博读书", url = "https://m.weibo.cn", iconResId = R.drawable.ic_weibo, description = "微博读书", searchUrl = "https://m.weibo.cn/search?containerid=100103type%3D64%26q%3D{query}%26ext%3Dbook", category = SearchEngineCategory.SOCIAL),
            SearchEngine(name = "dedao", displayName = "得到", url = "https://m.dedao.cn", iconResId = R.drawable.ic_search, description = "得到", searchUrl = "https://m.dedao.cn/search?keyword={query}", category = SearchEngineCategory.KNOWLEDGE),
            SearchEngine(name = "ximalaya", displayName = "喜马拉雅", url = "https://m.ximalaya.com", iconResId = R.drawable.ic_search, description = "喜马拉雅", searchUrl = "https://m.ximalaya.com/search/{query}", category = SearchEngineCategory.VIDEO),
            SearchEngine(name = "kindle", displayName = "Kindle商店", url = "https://m.amazon.cn", iconResId = R.drawable.ic_search, description = "Kindle商店", searchUrl = "https://m.amazon.cn/s?k={query}", category = SearchEngineCategory.SHOPPING)
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