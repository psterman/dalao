package com.example.aifloatingball.model

import com.example.aifloatingball.R

/**
 * 普通搜索引擎数据类
 * 
 * @param name 搜索引擎名称
 * @param url 搜索引擎URL
 * @param iconResId 搜索引擎图标资源ID
 * @param description 搜索引擎描述
 */
open class SearchEngine(
    val name: String,
    val url: String,
    val iconResId: Int,
    val description: String
) {
    companion object {
        /**
         * 默认搜索引擎列表
         */
        val DEFAULT_ENGINES = listOf(
            SearchEngine(
                "百度",
                "https://www.baidu.com/s?wd=",
                R.drawable.ic_baidu,
                "百度搜索"
            ),
            SearchEngine(
                "必应",
                "https://www.bing.com/search?q=",
                R.drawable.ic_bing,
                "Microsoft Bing"
            ),
            SearchEngine(
                "谷歌",
                "https://www.google.com/search?q=",
                R.drawable.ic_google,
                "Google搜索"
            ),
            SearchEngine(
                "DuckDuckGo",
                "https://duckduckgo.com/?q=",
                R.drawable.ic_duckduckgo,
                "DuckDuckGo搜索"
            ),
            SearchEngine(
                "360搜索",
                "https://www.so.com/s?q=",
                R.drawable.ic_360,
                "360搜索"
            ),
            SearchEngine(
                "搜狗",
                "https://www.sogou.com/web?query=",
                R.drawable.ic_sogou,
                "搜狗搜索"
            ),
            SearchEngine(
                "知乎",
                "https://www.zhihu.com/search?type=content&q=",
                R.drawable.ic_zhihu,
                "知乎搜索"
            ),
            SearchEngine(
                "微博",
                "https://s.weibo.com/weibo?q=",
                R.drawable.ic_weibo,
                "微博搜索"
            ),
            SearchEngine(
                "豆瓣",
                "https://www.douban.com/search?q=",
                R.drawable.ic_douban,
                "豆瓣搜索"
            ),
            SearchEngine(
                "淘宝",
                "https://s.taobao.com/search?q=",
                R.drawable.ic_taobao,
                "淘宝搜索"
            ),
            SearchEngine(
                "京东",
                "https://search.jd.com/Search?keyword=",
                R.drawable.ic_jd,
                "京东搜索"
            ),
            SearchEngine(
                "抖音",
                "https://www.douyin.com/search/",
                R.drawable.ic_douyin,
                "抖音搜索"
            ),
            SearchEngine(
                "小红书",
                "https://www.xiaohongshu.com/search_result?keyword=",
                R.drawable.ic_xiaohongshu,
                "小红书搜索"
            ),
            SearchEngine(
                "微信",
                "https://weixin.sogou.com/weixin?type=2&query=",
                R.drawable.ic_wechat,
                "微信搜索"
            ),
            SearchEngine(
                "QQ",
                "https://www.sogou.com/web?query=&ie=utf8&insite=qq.com",
                R.drawable.ic_qq,
                "QQ搜索"
            ),
            SearchEngine(
                "哔哩哔哩",
                "https://search.bilibili.com/all?keyword=",
                R.drawable.ic_bilibili,
                "哔哩哔哩搜索"
            )
        )
    }

    fun getSearchUrl(query: String): String {
        return if (url.contains("{query}")) {
            url.replace("{query}", android.net.Uri.encode(query))
        } else {
            url + android.net.Uri.encode(query)
        }
    }
} 