package com.example.aifloatingball.utils

import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.R

/**
 * 搜索引擎工具类
 * 用于处理搜索引擎相关的公共逻辑
 */
object EngineUtil {
    
    /**
     * 获取搜索引擎名称
     * 
     * @param engineKey 搜索引擎键名
     * @param isAI 是否是AI搜索引擎
     * @return 搜索引擎名称
     */
    fun getSearchEngineName(engineKey: String, isAI: Boolean): String {
        return if (isAI) {
            val engine = AISearchEngine.DEFAULT_AI_ENGINES.find { 
                getEngineKey(it.name) == engineKey 
            }
            engine?.name ?: "AI搜索"
        } else {
            when(engineKey) {
                "baidu" -> "百度"
                "google" -> "Google"
                "bing" -> "必应"
                "sogou" -> "搜狗"
                "360" -> "360搜索"
                "quark" -> "夸克搜索"
                "toutiao" -> "头条搜索"
                "zhihu" -> "知乎"
                "bilibili" -> "哔哩哔哩"
                "douban" -> "豆瓣"
                "weibo" -> "微博"
                "taobao" -> "淘宝"
                "jd" -> "京东"
                "douyin" -> "抖音"
                "xiaohongshu" -> "小红书"
                "wechat" -> "微信"
                "qq" -> "QQ"
                else -> "搜索"
            }
        }
    }
    
    /**
     * 获取搜索引擎键名
     * 
     * @param engineName 搜索引擎名称
     * @return 搜索引擎键名
     */
    fun getEngineKey(engineName: String): String {
        return when(engineName) {
            "百度" -> "baidu"
            "Google" -> "google"
            "必应" -> "bing"
            "搜狗" -> "sogou"
            "360搜索" -> "360"
            "夸克搜索" -> "quark"
            "头条搜索" -> "toutiao"
            "知乎" -> "zhihu"
            "哔哩哔哩" -> "bilibili"
            "豆瓣" -> "douban"
            "微博" -> "weibo"
            "淘宝" -> "taobao"
            "京东" -> "jd"
            "抖音" -> "douyin"
            "小红书" -> "xiaohongshu"
            "微信" -> "wechat"
            "QQ" -> "qq"
            "ChatGPT" -> "chatgpt"
            "Claude" -> "claude"
            "Gemini" -> "gemini"
            "文心一言" -> "wenxin"
            "智谱清言" -> "chatglm"
            "通义千问" -> "qianwen"
            "讯飞星火" -> "xinghuo"
            "Perplexity" -> "perplexity"
            "Phind" -> "phind"
            "Poe" -> "poe"
            else -> engineName.toLowerCase()
        }
    }
    
    /**
     * 获取搜索引擎首页URL
     * 
     * @param engineKey 搜索引擎键名
     * @return 搜索引擎首页URL
     */
    fun getSearchEngineHomeUrl(engineKey: String): String {
        return when(engineKey) {
            "baidu" -> "https://www.baidu.com"
            "google" -> "https://www.google.com"
            "bing" -> "https://www.bing.com"
            "sogou" -> "https://www.sogou.com"
            "360" -> "https://www.so.com"
            "quark" -> "https://quark.sm.cn"
            "toutiao" -> "https://so.toutiao.com"
            "zhihu" -> "https://www.zhihu.com"
            "bilibili" -> "https://www.bilibili.com"
            "douban" -> "https://www.douban.com"
            "weibo" -> "https://weibo.com"
            "taobao" -> "https://www.taobao.com"
            "jd" -> "https://www.jd.com"
            "douyin" -> "https://www.douyin.com"
            "xiaohongshu" -> "https://www.xiaohongshu.com"
            "wechat" -> "https://weixin.qq.com"
            "qq" -> "https://www.qq.com"
            else -> "https://www.baidu.com"
        }
    }
    
    /**
     * 获取AI搜索引擎首页URL
     * 
     * @param engineKey AI搜索引擎键名
     * @return AI搜索引擎首页URL
     */
    fun getAISearchEngineHomeUrl(engineKey: String): String {
        val engine = AISearchEngine.DEFAULT_AI_ENGINES.find { 
            getEngineKey(it.name) == engineKey 
        }
        return engine?.url ?: "https://chat.openai.com"
    }
    
    /**
     * 获取搜索引擎搜索URL
     * 
     * @param engineKey 搜索引擎键名
     * @param query 搜索关键词
     * @return 完整搜索URL
     */
    fun getSearchEngineSearchUrl(engineKey: String, query: String): String {
        return when(engineKey) {
            "baidu" -> "https://www.baidu.com/s?wd=$query"
            "google" -> "https://www.google.com/search?q=$query"
            "bing" -> "https://www.bing.com/search?q=$query"
            "sogou" -> "https://www.sogou.com/web?query=$query"
            "360" -> "https://www.so.com/s?q=$query"
            "quark" -> "https://quark.sm.cn/s?q=$query"
            "toutiao" -> "https://so.toutiao.com/search?keyword=$query"
            "zhihu" -> "https://www.zhihu.com/search?type=content&q=$query"
            "bilibili" -> "https://search.bilibili.com/all?keyword=$query"
            "douban" -> "https://www.douban.com/search?q=$query"
            "weibo" -> "https://s.weibo.com/weibo?q=$query"
            "taobao" -> "https://s.taobao.com/search?q=$query"
            "jd" -> "https://search.jd.com/Search?keyword=$query"
            "douyin" -> "https://www.douyin.com/search/$query"
            "xiaohongshu" -> "https://www.xiaohongshu.com/search_result?keyword=$query"
            "wechat" -> "https://weixin.sogou.com/weixin?type=2&query=$query"
            "qq" -> "https://www.sogou.com/web?query=$query&ie=utf8&insite=qq.com"
            else -> "https://www.baidu.com/s?wd=$query"
        }
    }
    
    /**
     * 获取AI搜索引擎搜索URL
     * 
     * @param engineKey AI搜索引擎键名
     * @param query 搜索关键词
     * @return 完整搜索URL
     */
    fun getAISearchEngineUrl(engineKey: String, query: String): String {
        val engine = AISearchEngine.DEFAULT_AI_ENGINES.find { 
            getEngineKey(it.name) == engineKey 
        }
        return engine?.getAISearchUrl(query) ?: "https://chat.openai.com"
    }
    
    /**
     * 获取谷歌Favicon服务的图标URL
     * 
     * @param domain 网站域名
     * @param size 图标大小，默认16像素
     * @return Favicon URL
     */
    fun getFaviconUrl(domain: String, size: Int = 16): String {
        return "https://www.google.com/s2/favicons?domain=$domain&sz=$size"
    }
    
    /**
     * 获取搜索引擎图标URL
     * 
     * @param engineKey 搜索引擎键名
     * @param size 图标大小，默认32像素
     * @return 图标URL
     */
    fun getEngineIconUrl(engineKey: String, size: Int = 32): String {
        val domain = when(engineKey) {
            "baidu" -> "baidu.com"
            "google" -> "google.com"
            "bing" -> "bing.com"
            "sogou" -> "sogou.com"
            "360" -> "so.com"
            "quark" -> "sm.cn"
            "toutiao" -> "toutiao.com"
            "zhihu" -> "zhihu.com"
            "bilibili" -> "bilibili.com"
            "douban" -> "douban.com"
            "weibo" -> "weibo.com"
            "taobao" -> "taobao.com"
            "jd" -> "jd.com"
            "douyin" -> "douyin.com"
            "xiaohongshu" -> "xiaohongshu.com"
            "wechat" -> "qq.com"
            "qq" -> "qq.com"
            "chatgpt" -> "openai.com"
            "claude" -> "claude.ai"
            "gemini" -> "gemini.google.com"
            "wenxin" -> "baidu.com"
            "chatglm" -> "zhipuai.cn"
            "qianwen" -> "aliyun.com"
            "xinghuo" -> "xfyun.cn"
            "perplexity" -> "perplexity.ai"
            "phind" -> "phind.com"
            "poe" -> "poe.com"
            else -> "google.com"
        }
        return getFaviconUrl(domain, size)
    }

    /**
     * 根据域名获取对应的图标资源ID
     */
    fun getIconResourceByDomain(domain: String): Int {
        return when {
            domain.contains("baidu.com") -> R.drawable.ic_baidu
            domain.contains("google.com") -> R.drawable.ic_google
            domain.contains("bing.com") -> R.drawable.ic_bing
            domain.contains("sogou.com") -> R.drawable.ic_sogou
            domain.contains("so.com") -> R.drawable.ic_360
            domain.contains("sm.cn") -> R.drawable.ic_search
            domain.contains("toutiao.com") -> R.drawable.ic_search
            domain.contains("zhihu.com") -> R.drawable.ic_zhihu
            domain.contains("bilibili.com") -> R.drawable.ic_bilibili
            domain.contains("douban.com") -> R.drawable.ic_douban
            domain.contains("weibo.com") -> R.drawable.ic_weibo
            domain.contains("taobao.com") -> R.drawable.ic_taobao
            domain.contains("jd.com") -> R.drawable.ic_jd
            domain.contains("douyin.com") -> R.drawable.ic_douyin
            domain.contains("xiaohongshu.com") -> R.drawable.ic_xiaohongshu
            domain.contains("qq.com") -> R.drawable.ic_qq
            domain.contains("openai.com") -> R.drawable.ic_chatgpt
            domain.contains("claude.ai") -> R.drawable.ic_claude
            domain.contains("gemini.google.com") -> R.drawable.ic_gemini
            domain.contains("zhipuai.cn") -> R.drawable.ic_zhipu
            domain.contains("aliyun.com") -> R.drawable.ic_qianwen
            domain.contains("xfyun.cn") -> R.drawable.ic_xinghuo
            domain.contains("perplexity.ai") -> R.drawable.ic_perplexity
            else -> R.drawable.ic_search
        }
    }
} 