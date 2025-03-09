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
class SearchEngine(
    val name: String,
    val url: String,
    val iconResId: Int,
    val description: String = ""  // 添加description字段，并提供默认空字符串值
) {
    companion object {
        /**
         * 默认普通搜索引擎列表
         */
        val NORMAL_SEARCH_ENGINES = listOf(
            SearchEngine("功能主页", "app://home", R.drawable.ic_home, "打开功能主页"),
            SearchEngine("百度", "https://www.baidu.com/s?wd=", R.drawable.ic_search, "百度搜索"),
            SearchEngine("Google", "https://www.google.com/search?q=", R.drawable.ic_search, "Google搜索"),
            SearchEngine("必应", "https://www.bing.com/search?q=", R.drawable.ic_search, "必应搜索"),
            SearchEngine("搜狗", "https://www.sogou.com/web?query=", R.drawable.ic_search, "搜狗搜索"),
            SearchEngine("360搜索", "https://www.so.com/s?q=", R.drawable.ic_search, "360搜索"),
            SearchEngine("夸克搜索", "https://quark.sm.cn/s?q=", R.drawable.ic_search, "夸克搜索"),
            SearchEngine("头条搜索", "https://so.toutiao.com/search?keyword=", R.drawable.ic_search, "头条搜索"),
            SearchEngine("微博", "https://s.weibo.com/weibo?q=", R.drawable.ic_search, "微博搜索"),
            SearchEngine("知乎", "https://www.zhihu.com/search?type=content&q=", R.drawable.ic_search, "知乎搜索"),
            SearchEngine("哔哩哔哩", "https://search.bilibili.com/all?keyword=", R.drawable.ic_search, "哔哩哔哩搜索"),
            SearchEngine("淘宝", "https://s.taobao.com/search?q=", R.drawable.ic_search, "淘宝搜索"),
            SearchEngine("京东", "https://search.jd.com/Search?keyword=", R.drawable.ic_search, "京东搜索")
        )
    }

    fun getSearchUrl(query: String): String {
        return if (url.contains("{query}")) {
            url.replace("{query}", android.net.Uri.encode(query))
        } else {
            url
        }
    }
} 