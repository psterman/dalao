package com.example.aifloatingball.model

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.aifloatingball.R

/**
 * 应用分类枚举
 */
enum class AppCategory(val displayName: String, val iconResId: Int) {
    CUSTOM("自定义", android.R.drawable.ic_menu_preferences),
    ALL("全部", android.R.drawable.ic_menu_view),
    AI("AI", android.R.drawable.ic_menu_help),
    SHOPPING("购物", android.R.drawable.ic_menu_gallery),
    SOCIAL("社交", android.R.drawable.ic_menu_share),
    VIDEO("视频", android.R.drawable.ic_media_play),
    MUSIC("音乐", android.R.drawable.ic_media_play),
    LIFESTYLE("生活", android.R.drawable.ic_menu_mylocation),
    MAPS("地图", android.R.drawable.ic_dialog_map),
    BROWSER("浏览器", android.R.drawable.ic_menu_search),
    FINANCE("金融", android.R.drawable.ic_menu_manage),
    TRAVEL("出行", android.R.drawable.ic_menu_directions),
    JOBS("招聘", android.R.drawable.ic_menu_agenda),
    EDUCATION("教育", android.R.drawable.ic_menu_info_details),
    NEWS("新闻", android.R.drawable.ic_menu_recent_history)
}

data class AppSearchConfig(
    val appId: String,          // 应用标识符
    val appName: String,        // 应用名称
    val packageName: String,    // 应用包名
    var isEnabled: Boolean,     // 是否启用
    var order: Int,            // 排序顺序
    val iconResId: Int,         // 图标资源ID
    val searchUrl: String,      // 搜索URL模板
    val category: AppCategory = AppCategory.ALL, // 应用分类
    val description: String = "" // 应用描述
) {
    /**
     * 获取搜索URL
     */
    fun getSearchUrl(query: String): String {
        return searchUrl.replace("{q}", query)
    }
}

class AppSearchSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_search_settings", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_APP_CONFIGS = "app_configs"
        private const val KEY_LAST_ORDER = "last_order"
        private const val KEY_CONFIG_VERSION = "config_version"
        private const val CURRENT_CONFIG_VERSION = 4 // 增加版本号以触发配置更新

        @Volatile
        private var instance: AppSearchSettings? = null

        fun getInstance(context: Context): AppSearchSettings {
            return instance ?: synchronized(this) {
                instance ?: AppSearchSettings(context.applicationContext).also { instance = it }
            }
        }
    }

    // 获取所有应用配置
    fun getAppConfigs(): List<AppSearchConfig> {
        val currentVersion = prefs.getInt(KEY_CONFIG_VERSION, 0)
        val json = prefs.getString(KEY_APP_CONFIGS, null)

        return if (json != null && currentVersion >= CURRENT_CONFIG_VERSION) {
            try {
                val type = object : TypeToken<List<AppSearchConfig>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                // 解析失败时返回默认配置并更新版本
                val defaultConfigs = getDefaultConfigs()
                saveAppConfigs(defaultConfigs)
                updateConfigVersion()
                defaultConfigs
            }
        } else {
            // 配置版本过旧或不存在，合并新配置
            val existingConfigs = if (json != null) {
                try {
                    val type = object : TypeToken<List<AppSearchConfig>>() {}.type
                    gson.fromJson<List<AppSearchConfig>>(json, type)
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }

            val mergedConfigs = mergeConfigs(existingConfigs, getDefaultConfigs())
            saveAppConfigs(mergedConfigs)
            updateConfigVersion()
            mergedConfigs
        }
    }

    // 保存应用配置
    fun saveAppConfigs(configs: List<AppSearchConfig>) {
        val json = gson.toJson(configs)
        prefs.edit().putString(KEY_APP_CONFIGS, json).apply()
    }

    // 更新单个应用配置
    fun updateAppConfig(appId: String, update: (AppSearchConfig) -> Unit) {
        val configs = getAppConfigs().toMutableList()
        val index = configs.indexOfFirst { it.appId == appId }
        if (index != -1) {
            update(configs[index])
            saveAppConfigs(configs)
        }
    }

    // 更新配置版本
    private fun updateConfigVersion() {
        prefs.edit().putInt(KEY_CONFIG_VERSION, CURRENT_CONFIG_VERSION).apply()
    }

    // 合并配置：保留用户的自定义设置，添加新的默认配置
    private fun mergeConfigs(existingConfigs: List<AppSearchConfig>, defaultConfigs: List<AppSearchConfig>): List<AppSearchConfig> {
        val existingMap = existingConfigs.associateBy { it.appId }
        val mergedConfigs = mutableListOf<AppSearchConfig>()
        var maxOrder = existingConfigs.maxOfOrNull { it.order } ?: 0

        // 首先添加现有配置
        mergedConfigs.addAll(existingConfigs)

        // 然后添加新的配置（不在现有配置中的）
        defaultConfigs.forEach { defaultConfig ->
            if (!existingMap.containsKey(defaultConfig.appId)) {
                // 新配置，添加到列表中，使用递增的order
                maxOrder++
                mergedConfigs.add(defaultConfig.copy(order = maxOrder))
            }
        }

        return mergedConfigs.sortedBy { it.order }
    }

    // 获取默认配置
    private fun getDefaultConfigs(): List<AppSearchConfig> {
        return listOf(
            AppSearchConfig(
                appId = "wechat",
                appName = "微信",
                packageName = "com.tencent.mm",
                isEnabled = true,
                order = 1,
                iconResId = R.drawable.ic_web_default,
                searchUrl = "weixin://", // 微信没有直接的搜索Scheme，只能启动应用
                category = AppCategory.SOCIAL,
                description = "微信搜索"
            ),
            AppSearchConfig(
                appId = "taobao",
                appName = "淘宝",
                packageName = "com.taobao.taobao",
                isEnabled = true,
                order = 2,
                iconResId = R.drawable.ic_web_default,
                searchUrl = "taobao://s.taobao.com/search?q={q}",
                category = AppCategory.SHOPPING,
                description = "淘宝购物搜索"
            ),
            AppSearchConfig(
                appId = "pdd",
                appName = "拼多多",
                packageName = "com.xunmeng.pinduoduo",
                isEnabled = true,
                order = 3,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "pinduoduo://com.xunmeng.pinduoduo/search_result.html?search_key={q}",
                category = AppCategory.SHOPPING,
                description = "拼多多购物搜索"
            ),
            AppSearchConfig(
                appId = "douyin",
                appName = "抖音",
                packageName = "com.ss.android.ugc.aweme",
                isEnabled = true,
                order = 4,
                iconResId = R.drawable.ic_douyin,
                searchUrl = "snssdk1128://search/tabs?keyword={q}",
                category = AppCategory.VIDEO,
                description = "抖音视频搜索"
            ),
            AppSearchConfig(
                appId = "xiaohongshu",
                appName = "小红书",
                packageName = "com.xingin.xhs",
                isEnabled = true,
                order = 5,
                iconResId = R.drawable.ic_xiaohongshu,
                searchUrl = "xhsdiscover://search/result/?keyword={q}",
                category = AppCategory.SOCIAL,
                description = "小红书搜索"
            ),
            AppSearchConfig(
                appId = "meituan",
                appName = "美团",
                packageName = "com.sankuai.meituan",
                isEnabled = true,
                order = 6,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "imeituan://www.meituan.com/search?q={q}",
                category = AppCategory.LIFESTYLE,
                description = "美团生活服务搜索"
            ),
            AppSearchConfig(
                appId = "douban",
                appName = "豆瓣",
                packageName = "com.douban.frodo",
                isEnabled = false,
                order = 7,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "douban:///search?q={q}",
                category = AppCategory.SOCIAL,
                description = "豆瓣搜索"
            ),
            AppSearchConfig(
                appId = "weibo",
                appName = "微博",
                packageName = "com.sina.weibo",
                isEnabled = false,
                order = 8,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "sinaweibo://searchall?q={q}",
                category = AppCategory.SOCIAL,
                description = "微博搜索"
            ),
            AppSearchConfig(
                appId = "tiktok",
                appName = "TikTok",
                packageName = "com.zhiliaoapp.musically",
                isEnabled = false,
                order = 9,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "snssdk1128://search/tabs?keyword={q}", // 与抖音相同
                category = AppCategory.VIDEO,
                description = "TikTok视频搜索"
            ),
            AppSearchConfig(
                appId = "twitter",
                appName = "Twitter-X",
                packageName = "com.twitter.android",
                isEnabled = false,
                order = 10,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "twitter://search?query={q}",
                category = AppCategory.SOCIAL,
                description = "Twitter搜索"
            ),
            AppSearchConfig(
                appId = "zhihu",
                appName = "知乎",
                packageName = "com.zhihu.android",
                isEnabled = false,
                order = 11,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "zhihu://search?q={q}",
                category = AppCategory.SOCIAL,
                description = "知乎搜索"
            ),
            AppSearchConfig(
                appId = "bilibili",
                appName = "哔哩哔哩",
                packageName = "tv.danmaku.bili",
                isEnabled = false,
                order = 12,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "bilibili://search?keyword={q}",
                category = AppCategory.VIDEO,
                description = "B站视频搜索"
            ),
            AppSearchConfig(
                appId = "youtube",
                appName = "YouTube",
                packageName = "com.google.android.youtube",
                isEnabled = false,
                order = 13,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "youtube://results?search_query={q}",
                category = AppCategory.VIDEO,
                description = "YouTube视频搜索"
            ),
            AppSearchConfig(
                appId = "spotify",
                appName = "Spotify",
                packageName = "com.spotify.music",
                isEnabled = false,
                order = 14,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "spotify:search:{q}",
                category = AppCategory.MUSIC,
                description = "Spotify音乐搜索"
            ),
            AppSearchConfig(
                appId = "tmall",
                appName = "天猫",
                packageName = "com.tmall.wireless",
                isEnabled = false,
                order = 15,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "tmall://page.tm/search?q={q}",
                category = AppCategory.SHOPPING,
                description = "天猫购物搜索"
            ),
            AppSearchConfig(
                appId = "jd",
                appName = "京东",
                packageName = "com.jingdong.app.mall",
                isEnabled = false,
                order = 16,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "openjd://virtual?params={\"des\":\"productList\",\"keyWord\":\"{q}\"}",
                category = AppCategory.SHOPPING,
                description = "京东购物搜索"
            ),
            AppSearchConfig(
                appId = "xianyu",
                appName = "闲鱼",
                packageName = "com.taobao.idlefish",
                isEnabled = false,
                order = 17,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "fleamarket://x_search_items", // 闲鱼不支持直接带关键词
                category = AppCategory.SHOPPING,
                description = "闲鱼二手交易"
            ),
            AppSearchConfig(
                appId = "dianping",
                appName = "大众点评",
                packageName = "com.dianping.v1",
                isEnabled = false,
                order = 18,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "dianping://searchshoplist?keyword={q}",
                category = AppCategory.LIFESTYLE,
                description = "大众点评搜索"
            ),
            AppSearchConfig(
                appId = "chrome",
                appName = "Chrome",
                packageName = "com.android.chrome",
                isEnabled = false,
                order = 19,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "googlechrome://www.google.com/search?q={q}",
                category = AppCategory.BROWSER,
                description = "Chrome浏览器搜索"
            ),
            AppSearchConfig(
                appId = "eudic",
                appName = "欧路词典",
                packageName = "com.eusoft.eudic",
                isEnabled = false,
                order = 20,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "eudic://dict/{q}",
                category = AppCategory.EDUCATION,
                description = "欧路词典查词"
            ),

            // 音乐类应用
            AppSearchConfig(
                appId = "qqmusic",
                appName = "QQ音乐",
                packageName = "com.tencent.qqmusic",
                isEnabled = true,
                order = 21,
                iconResId = R.drawable.ic_qqmusic,
                searchUrl = "qqmusic://search?key={q}",
                category = AppCategory.MUSIC,
                description = "QQ音乐搜索歌曲、歌手、专辑"
            ),
            AppSearchConfig(
                appId = "netease_music",
                appName = "网易云音乐",
                packageName = "com.netease.cloudmusic",
                isEnabled = true,
                order = 22,
                iconResId = R.drawable.ic_netease_music,
                searchUrl = "orpheus://search?keyword={q}",
                category = AppCategory.MUSIC,
                description = "网易云音乐搜索歌曲、歌手、专辑"
            ),

            // 生活服务类应用
            AppSearchConfig(
                appId = "eleme",
                appName = "饿了么",
                packageName = "me.ele",
                isEnabled = true,
                order = 23,
                iconResId = R.drawable.ic_eleme,
                searchUrl = "eleme://search?keyword={q}",
                category = AppCategory.LIFESTYLE,
                description = "饿了么外卖搜索美食、商家"
            ),
            AppSearchConfig(
                appId = "douban",
                appName = "豆瓣",
                packageName = "com.douban.frodo",
                isEnabled = true,
                order = 24,
                iconResId = R.drawable.ic_douban,
                searchUrl = "douban://search?q={q}",
                category = AppCategory.LIFESTYLE,
                description = "豆瓣搜索电影、图书、音乐"
            ),

            // 地图导航类应用
            AppSearchConfig(
                appId = "gaode_map",
                appName = "高德地图",
                packageName = "com.autonavi.minimap",
                isEnabled = true,
                order = 25,
                iconResId = R.drawable.ic_gaode_map,
                searchUrl = "androidamap://poi?sourceApplication=appname&keywords={q}",
                category = AppCategory.MAPS,
                description = "高德地图搜索地点、导航"
            ),
            AppSearchConfig(
                appId = "baidu_map",
                appName = "百度地图",
                packageName = "com.baidu.BaiduMap",
                isEnabled = true,
                order = 26,
                iconResId = R.drawable.ic_baidu_map,
                searchUrl = "baidumap://map/place/search?query={q}",
                category = AppCategory.MAPS,
                description = "百度地图搜索地点、导航"
            ),

            // 浏览器类应用
            AppSearchConfig(
                appId = "quark",
                appName = "夸克",
                packageName = "com.quark.browser",
                isEnabled = true,
                order = 27,
                iconResId = R.drawable.ic_quark,
                searchUrl = "quark://search?q={q}",
                category = AppCategory.BROWSER,
                description = "夸克浏览器智能搜索"
            ),
            AppSearchConfig(
                appId = "uc_browser",
                appName = "UC浏览器",
                packageName = "com.UCMobile",
                isEnabled = true,
                order = 28,
                iconResId = R.drawable.ic_uc_browser,
                searchUrl = "ucbrowser://search?keyword={q}",
                category = AppCategory.BROWSER,
                description = "UC浏览器快速搜索"
            ),

            // 金融类应用
            AppSearchConfig(
                appId = "alipay",
                appName = "支付宝",
                packageName = "com.eg.android.AlipayGphone",
                isEnabled = true,
                order = 29,
                iconResId = R.drawable.ic_alipay,
                searchUrl = "alipay://platformapi/startapp?appId=20000067&query={q}",
                category = AppCategory.FINANCE,
                description = "支付宝搜索服务、商家"
            ),
            AppSearchConfig(
                appId = "wechat_pay",
                appName = "微信支付",
                packageName = "com.tencent.mm",
                isEnabled = true,
                order = 30,
                iconResId = android.R.drawable.ic_menu_manage, // 将使用字母图标
                searchUrl = "weixin://dl/scan",
                category = AppCategory.FINANCE,
                description = "微信支付扫码功能"
            ),
            AppSearchConfig(
                appId = "cmb",
                appName = "招商银行",
                packageName = "cmb.pb",
                isEnabled = true,
                order = 31,
                iconResId = android.R.drawable.ic_menu_manage, // 将使用字母图标
                searchUrl = "cmbmobilebank://search?keyword={q}",
                category = AppCategory.FINANCE,
                description = "招商银行搜索理财、服务"
            ),
            AppSearchConfig(
                appId = "antfortune",
                appName = "蚂蚁财富",
                packageName = "com.antfortune.wealth",
                isEnabled = true,
                order = 32,
                iconResId = android.R.drawable.ic_menu_manage, // 将使用字母图标
                searchUrl = "antfortune://search?keyword={q}",
                category = AppCategory.FINANCE,
                description = "蚂蚁财富搜索理财产品"
            ),

            // 出行类应用
            AppSearchConfig(
                appId = "didi",
                appName = "滴滴出行",
                packageName = "com.sdu.didi.psnger",
                isEnabled = true,
                order = 33,
                iconResId = android.R.drawable.ic_menu_directions, // 将使用字母图标
                searchUrl = "diditaxi://search?keyword={q}",
                category = AppCategory.TRAVEL,
                description = "滴滴出行搜索目的地"
            ),
            AppSearchConfig(
                appId = "railway12306",
                appName = "12306",
                packageName = "com.MobileTicket",
                isEnabled = true,
                order = 34,
                iconResId = android.R.drawable.ic_menu_directions, // 将使用字母图标
                searchUrl = "cn.12306://search?keyword={q}",
                category = AppCategory.TRAVEL,
                description = "12306火车票搜索车次"
            ),
            AppSearchConfig(
                appId = "ctrip",
                appName = "携程旅行",
                packageName = "ctrip.android.view",
                isEnabled = true,
                order = 35,
                iconResId = android.R.drawable.ic_menu_directions, // 将使用字母图标
                searchUrl = "ctrip://search?keyword={q}",
                category = AppCategory.TRAVEL,
                description = "携程旅行搜索酒店、机票"
            ),
            AppSearchConfig(
                appId = "qunar",
                appName = "去哪儿",
                packageName = "com.Qunar",
                isEnabled = true,
                order = 36,
                iconResId = android.R.drawable.ic_menu_directions, // 将使用字母图标
                searchUrl = "qunar://search?keyword={q}",
                category = AppCategory.TRAVEL,
                description = "去哪儿旅行搜索机票、酒店"
            ),
            AppSearchConfig(
                appId = "hellobike",
                appName = "哈啰出行",
                packageName = "com.jingyao.easybike",
                isEnabled = true,
                order = 37,
                iconResId = android.R.drawable.ic_menu_directions, // 将使用字母图标
                searchUrl = "hellobike://search?keyword={q}",
                category = AppCategory.TRAVEL,
                description = "哈啰出行搜索单车、打车"
            ),

            // 招聘类应用
            AppSearchConfig(
                appId = "boss",
                appName = "BOSS直聘",
                packageName = "com.hpbr.bosszhipin",
                isEnabled = true,
                order = 38,
                iconResId = android.R.drawable.ic_menu_agenda,
                searchUrl = "bosszhipin://search?keyword={q}",
                category = AppCategory.JOBS,
                description = "BOSS直聘搜索职位、公司"
            ),
            AppSearchConfig(
                appId = "liepin",
                appName = "猎聘",
                packageName = "com.liepin.android",
                isEnabled = true,
                order = 39,
                iconResId = android.R.drawable.ic_menu_agenda,
                searchUrl = "liepin://search?keyword={q}",
                category = AppCategory.JOBS,
                description = "猎聘搜索高端职位"
            ),
            AppSearchConfig(
                appId = "zhaopin",
                appName = "前程无忧",
                packageName = "com.job.android",
                isEnabled = true,
                order = 40,
                iconResId = android.R.drawable.ic_menu_agenda,
                searchUrl = "zhaopin://search?keyword={q}",
                category = AppCategory.JOBS,
                description = "前程无忧搜索工作机会"
            ),

            // 教育类应用
            AppSearchConfig(
                appId = "youdao_dict",
                appName = "有道词典",
                packageName = "com.youdao.dict",
                isEnabled = true,
                order = 41,
                iconResId = android.R.drawable.ic_menu_info_details,
                searchUrl = "yddict://search?keyword={q}",
                category = AppCategory.EDUCATION,
                description = "有道词典查词翻译"
            ),
            AppSearchConfig(
                appId = "baicizhan",
                appName = "百词斩",
                packageName = "com.jiongji.andriod.card",
                isEnabled = true,
                order = 42,
                iconResId = android.R.drawable.ic_menu_info_details,
                searchUrl = "baicizhan://search?keyword={q}",
                category = AppCategory.EDUCATION,
                description = "百词斩搜索单词学习"
            ),
            AppSearchConfig(
                appId = "zuoyebang",
                appName = "作业帮",
                packageName = "com.baidu.homework",
                isEnabled = true,
                order = 43,
                iconResId = android.R.drawable.ic_menu_info_details,
                searchUrl = "zuoyebang://search?keyword={q}",
                category = AppCategory.EDUCATION,
                description = "作业帮搜索题目答案"
            ),
            AppSearchConfig(
                appId = "yuansouti",
                appName = "小猿搜题",
                packageName = "com.fenbi.android.solar",
                isEnabled = true,
                order = 44,
                iconResId = android.R.drawable.ic_menu_info_details,
                searchUrl = "yuansouti://search?keyword={q}",
                category = AppCategory.EDUCATION,
                description = "小猿搜题拍照搜题"
            ),

            // 新闻类应用
            AppSearchConfig(
                appId = "netease_news",
                appName = "网易新闻",
                packageName = "com.netease.nr",
                isEnabled = true,
                order = 45,
                iconResId = android.R.drawable.ic_menu_recent_history,
                searchUrl = "newsapp://search?keyword={q}",
                category = AppCategory.NEWS,
                description = "网易新闻搜索资讯内容"
            ),

            // AI类应用
            AppSearchConfig(
                appId = "deepseek",
                appName = "DeepSeek",
                packageName = "com.deepseek.chat",
                isEnabled = true,
                order = 46,
                iconResId = R.drawable.ic_deepseek,
                searchUrl = "https://play.google.com/store/apps/details?id=com.deepseek.chat",
                category = AppCategory.AI,
                description = "DeepSeek AI助手搜索"
            ),
            AppSearchConfig(
                appId = "doubao",
                appName = "豆包",
                packageName = "com.larus.nova",
                isEnabled = true,
                order = 47,
                iconResId = R.drawable.ic_doubao,
                searchUrl = "https://play.google.com/store/apps/details?id=com.larus.nova",
                category = AppCategory.AI,
                description = "豆包AI助手搜索"
            ),
            AppSearchConfig(
                appId = "chatgpt",
                appName = "ChatGPT",
                packageName = "com.openai.chatgpt",
                isEnabled = true,
                order = 48,
                iconResId = R.drawable.ic_chatgpt,
                searchUrl = "https://play.google.com/store/apps/details?id=com.openai.chatgpt",
                category = AppCategory.AI,
                description = "ChatGPT AI助手搜索"
            ),
            AppSearchConfig(
                appId = "kimi",
                appName = "Kimi",
                packageName = "com.moonshot.kimichat",
                isEnabled = true,
                order = 49,
                iconResId = R.drawable.ic_kimi,
                searchUrl = "https://play.google.com/store/apps/details?id=com.moonshot.kimichat",
                category = AppCategory.AI,
                description = "Kimi AI助手搜索"
            ),
            AppSearchConfig(
                appId = "tencent_yuanbao",
                appName = "腾讯元宝",
                packageName = "com.tencent.hunyuan.app.chat",
                isEnabled = true,
                order = 50,
                iconResId = R.drawable.ic_yuanbao,
                searchUrl = "https://play.google.com/store/apps/details?id=com.tencent.hunyuan.app.chat",
                category = AppCategory.AI,
                description = "腾讯元宝AI助手搜索"
            ),
            // 讯飞星火
            AppSearchConfig(
                appId = "xinghuo",
                appName = "讯飞星火",
                packageName = "com.iflytek.spark",
                isEnabled = true,
                order = 51,
                iconResId = R.drawable.ic_xinghuo,
                searchUrl = "iflytek://spark?query={q}",
                category = AppCategory.AI,
                description = "讯飞星火AI助手搜索"
            ),
            // 智谱清言
            AppSearchConfig(
                appId = "zhipu_qingyan",
                appName = "智谱清言",
                packageName = "com.zhipuai.qingyan",
                isEnabled = true,
                order = 52,
                iconResId = R.drawable.ic_zhipu_qingyan,
                searchUrl = "zhipuai://qingyan?query={q}",
                category = AppCategory.AI,
                description = "智谱清言AI助手搜索"
            ),
            // 通义千问
            AppSearchConfig(
                appId = "tongyi",
                appName = "通义千问",
                packageName = "com.aliyun.tongyi",
                isEnabled = true,
                order = 53,
                iconResId = R.drawable.ic_tongyi,
                searchUrl = "tongyi://aliyun?query={q}",
                category = AppCategory.AI,
                description = "通义千问AI助手搜索"
            ),
            // 文小言
            AppSearchConfig(
                appId = "wenxiaoyan",
                appName = "文小言",
                packageName = "com.baidu.newapp",
                isEnabled = true,
                order = 54,
                iconResId = R.drawable.ic_wenxiaoyan,
                searchUrl = "wenxiaoyan://app?query={q}",
                category = AppCategory.AI,
                description = "文小言AI助手搜索"
            ),
            // Grok
            AppSearchConfig(
                appId = "grok",
                appName = "Grok",
                packageName = "ai.x.grok", // 真实包名
                isEnabled = true,
                order = 55,
                iconResId = R.drawable.ic_grok,
                searchUrl = "grok://xai?query={q}",
                category = AppCategory.AI,
                description = "Grok AI助手搜索"
            ),
            // Perplexity
            AppSearchConfig(
                appId = "perplexity",
                appName = "Perplexity",
                packageName = "ai.perplexity.app.android", // 真实包名
                isEnabled = true,
                order = 56,
                iconResId = R.drawable.ic_perplexity,
                searchUrl = "perplexity://ai?query={q}",
                category = AppCategory.AI,
                description = "Perplexity AI助手搜索"
            ),
            // Manus
            AppSearchConfig(
                appId = "manus",
                appName = "Manus",
                packageName = "com.manus.im.app", // 真实包名
                isEnabled = true,
                order = 57,
                iconResId = R.drawable.ic_manus,
                searchUrl = "manus://app?query={q}",
                category = AppCategory.AI,
                description = "Manus AI助手搜索"
            ),
            // 秘塔AI搜索
            AppSearchConfig(
                appId = "mita_ai",
                appName = "秘塔AI搜索",
                packageName = "com.metaso",
                isEnabled = true,
                order = 58,
                iconResId = R.drawable.ic_mita_ai,
                searchUrl = "mita://ai?query={q}",
                category = AppCategory.AI,
                description = "秘塔AI搜索助手"
            ),
            // Poe
            AppSearchConfig(
                appId = "poe",
                appName = "Poe",
                packageName = "com.poe.android", // 真实包名
                isEnabled = true,
                order = 59,
                iconResId = R.drawable.ic_poe,
                searchUrl = "poe://app?query={q}",
                category = AppCategory.AI,
                description = "Poe AI聊天助手"
            ),
            // IMA
            AppSearchConfig(
                appId = "ima",
                appName = "IMA",
                packageName = "com.tencent.ima", // 真实包名
                isEnabled = true,
                order = 60,
                iconResId = R.drawable.ic_ima,
                searchUrl = "ima://app?query={q}",
                category = AppCategory.AI,
                description = "IMA AI助手"
            ),
            // 纳米AI
            AppSearchConfig(
                appId = "nano_ai",
                appName = "纳米AI",
                packageName = "com.qihoo.namiso", // 真实包名
                isEnabled = true,
                order = 61,
                iconResId = R.drawable.ic_nano_ai,
                searchUrl = "nano://ai?query={q}",
                category = AppCategory.AI,
                description = "纳米AI助手"
            ),
            // Gemini
            AppSearchConfig(
                appId = "gemini",
                appName = "Gemini",
                packageName = "com.google.android.apps.gemini",
                isEnabled = true,
                order = 62,
                iconResId = R.drawable.ic_gemini,
                searchUrl = "gemini://google?query={q}",
                category = AppCategory.AI,
                description = "Gemini AI助手搜索"
            ),
            // Copilot
            AppSearchConfig(
                appId = "copilot",
                appName = "Copilot",
                packageName = "com.microsoft.copilot",
                isEnabled = true,
                order = 63,
                iconResId = R.drawable.ic_copilot,
                searchUrl = "copilot://microsoft?query={q}",
                category = AppCategory.AI,
                description = "Copilot AI助手搜索"
            ),
            
            // ========== 办公软件类 ==========
            // WPS Office
            AppSearchConfig(
                appId = "wps_office",
                appName = "WPS Office",
                packageName = "cn.wps.moffice_eng",
                isEnabled = true,
                order = 64,
                iconResId = android.R.drawable.ic_menu_manage,
                searchUrl = "wps://search?keyword={q}",
                category = AppCategory.LIFESTYLE,
                description = "WPS Office搜索文档、表格、演示文稿"
            ),
            // Microsoft OneNote
            AppSearchConfig(
                appId = "onenote",
                appName = "Microsoft OneNote",
                packageName = "com.microsoft.office.onenote",
                isEnabled = true,
                order = 65,
                iconResId = android.R.drawable.ic_menu_manage,
                searchUrl = "onenote://search?query={q}",
                category = AppCategory.LIFESTYLE,
                description = "Microsoft OneNote搜索笔记内容"
            ),
            
            // ========== 搜索引擎与浏览器类 ==========
            // Google搜索
            AppSearchConfig(
                appId = "google_search",
                appName = "Google搜索",
                packageName = "com.google.android.googlequicksearchbox",
                isEnabled = true,
                order = 66,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "google://search?q={q}",
                category = AppCategory.BROWSER,
                description = "Google搜索网页、图片、视频"
            ),
            // 百度搜索
            AppSearchConfig(
                appId = "baidu_search",
                appName = "百度搜索",
                packageName = "com.baidu.searchbox",
                isEnabled = true,
                order = 67,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "baiduboxapp://search?keyword={q}",
                category = AppCategory.BROWSER,
                description = "百度搜索网页、图片"
            ),
            // 搜狗搜索
            AppSearchConfig(
                appId = "sogou_search",
                appName = "搜狗搜索",
                packageName = "com.sohu.inputmethod.sogou",
                isEnabled = true,
                order = 68,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "sogou://search?keyword={q}",
                category = AppCategory.BROWSER,
                description = "搜狗搜索网页内容"
            ),
            // 360浏览器
            AppSearchConfig(
                appId = "360_browser",
                appName = "360浏览器",
                packageName = "com.qihoo.browser",
                isEnabled = true,
                order = 69,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "qihoobrowser://search?keyword={q}",
                category = AppCategory.BROWSER,
                description = "360浏览器网页搜索"
            ),
            // Alook浏览器
            AppSearchConfig(
                appId = "alook_browser",
                appName = "Alook浏览器",
                packageName = "me.mycake.browser",
                isEnabled = true,
                order = 70,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "alook://search?q={q}",
                category = AppCategory.BROWSER,
                description = "Alook浏览器网页搜索"
            ),
            
            // ========== AI智能搜索类 ==========
            // 天工AI搜索
            AppSearchConfig(
                appId = "tiangong_ai",
                appName = "天工AI搜索",
                packageName = "com.tiangong.search",
                isEnabled = true,
                order = 71,
                iconResId = android.R.drawable.ic_menu_help,
                searchUrl = "tiangong://search?query={q}",
                category = AppCategory.AI,
                description = "天工AI搜索自然语言搜索和问答"
            ),
            // 360AI搜索
            AppSearchConfig(
                appId = "360_ai_search",
                appName = "360AI搜索",
                packageName = "com.qihoo.aisearch",
                isEnabled = true,
                order = 72,
                iconResId = android.R.drawable.ic_menu_help,
                searchUrl = "qihooai://search?keyword={q}",
                category = AppCategory.AI,
                description = "360AI搜索自然语言处理搜索"
            ),
            // MindSearch
            AppSearchConfig(
                appId = "mindsearch",
                appName = "MindSearch",
                packageName = "com.mindsearch.app",
                isEnabled = true,
                order = 73,
                iconResId = android.R.drawable.ic_menu_help,
                searchUrl = "mindsearch://search?query={q}",
                category = AppCategory.AI,
                description = "MindSearch学术领域搜索"
            ),
            
            // ========== 购物电商类 ==========
            // 唯品会
            AppSearchConfig(
                appId = "vipshop",
                appName = "唯品会",
                packageName = "com.achievo.vipshop",
                isEnabled = true,
                order = 74,
                iconResId = android.R.drawable.ic_menu_gallery,
                searchUrl = "vipshop://search?keyword={q}",
                category = AppCategory.SHOPPING,
                description = "唯品会商品搜索"
            ),
            // 当当
            AppSearchConfig(
                appId = "dangdang",
                appName = "当当",
                packageName = "com.dangdang.buy2",
                isEnabled = true,
                order = 75,
                iconResId = android.R.drawable.ic_menu_gallery,
                searchUrl = "dangdang://search?keyword={q}",
                category = AppCategory.SHOPPING,
                description = "当当图书等搜索"
            ),
            // 亚马逊
            AppSearchConfig(
                appId = "amazon",
                appName = "亚马逊",
                packageName = "com.amazon.mShop.android.shopping",
                isEnabled = true,
                order = 76,
                iconResId = android.R.drawable.ic_menu_gallery,
                searchUrl = "amazon://search?query={q}",
                category = AppCategory.SHOPPING,
                description = "亚马逊商品搜索"
            ),
            // 蘑菇街
            AppSearchConfig(
                appId = "mogujie",
                appName = "蘑菇街",
                packageName = "com.mogujie",
                isEnabled = true,
                order = 77,
                iconResId = android.R.drawable.ic_menu_gallery,
                searchUrl = "mogujie://search?keyword={q}",
                category = AppCategory.SHOPPING,
                description = "蘑菇街时尚商品搜索"
            ),
            
            // ========== 图片与设计类 ==========
            // PicsArt
            AppSearchConfig(
                appId = "picsart",
                appName = "PicsArt",
                packageName = "com.picsart.studio",
                isEnabled = true,
                order = 78,
                iconResId = android.R.drawable.ic_menu_gallery,
                searchUrl = "picsart://search?query={q}",
                category = AppCategory.LIFESTYLE,
                description = "PicsArt滤镜、贴纸搜索"
            ),
            // Canva
            AppSearchConfig(
                appId = "canva",
                appName = "Canva",
                packageName = "com.canva.editor",
                isEnabled = true,
                order = 79,
                iconResId = android.R.drawable.ic_menu_gallery,
                searchUrl = "canva://search?query={q}",
                category = AppCategory.LIFESTYLE,
                description = "Canva设计模板搜索"
            ),
            // VSCO
            AppSearchConfig(
                appId = "vsco",
                appName = "VSCO",
                packageName = "com.vsco.android",
                isEnabled = true,
                order = 80,
                iconResId = android.R.drawable.ic_menu_gallery,
                searchUrl = "vsco://search?query={q}",
                category = AppCategory.LIFESTYLE,
                description = "VSCO滤镜预设搜索"
            ),
            
            // ========== 学习与教育类 ==========
            // 学习强国
            AppSearchConfig(
                appId = "xuexi",
                appName = "学习强国",
                packageName = "cn.xuexi.android",
                isEnabled = true,
                order = 81,
                iconResId = android.R.drawable.ic_menu_info_details,
                searchUrl = "xuexi://search?keyword={q}",
                category = AppCategory.EDUCATION,
                description = "学习强国课程、文章搜索"
            ),
            // 中国大学MOOC
            AppSearchConfig(
                appId = "icourse163",
                appName = "中国大学MOOC",
                packageName = "com.netease.edu.study",
                isEnabled = true,
                order = 82,
                iconResId = android.R.drawable.ic_menu_info_details,
                searchUrl = "icourse163://search?keyword={q}",
                category = AppCategory.EDUCATION,
                description = "中国大学MOOC课程搜索"
            ),
            // 网易公开课
            AppSearchConfig(
                appId = "netease_open",
                appName = "网易公开课",
                packageName = "com.netease.open.iStudy",
                isEnabled = true,
                order = 83,
                iconResId = android.R.drawable.ic_menu_info_details,
                searchUrl = "neteaseopen://search?keyword={q}",
                category = AppCategory.EDUCATION,
                description = "网易公开课课程搜索"
            ),
            // 扇贝单词
            AppSearchConfig(
                appId = "shanbay",
                appName = "扇贝单词",
                packageName = "com.shanbay.words",
                isEnabled = true,
                order = 84,
                iconResId = android.R.drawable.ic_menu_info_details,
                searchUrl = "shanbay://search?keyword={q}",
                category = AppCategory.EDUCATION,
                description = "扇贝单词词汇搜索"
            ),
            
            // ========== 健康医疗类 ==========
            // 丁香医生
            AppSearchConfig(
                appId = "dxy",
                appName = "丁香医生",
                packageName = "com.dxy.pharmacy",
                isEnabled = true,
                order = 85,
                iconResId = android.R.drawable.ic_menu_mylocation,
                searchUrl = "dxy://search?keyword={q}",
                category = AppCategory.LIFESTYLE,
                description = "丁香医生疾病、药品信息搜索"
            ),
            // Keep
            AppSearchConfig(
                appId = "keep",
                appName = "Keep",
                packageName = "com.gotokeep.keep",
                isEnabled = true,
                order = 86,
                iconResId = android.R.drawable.ic_menu_mylocation,
                searchUrl = "keep://search?keyword={q}",
                category = AppCategory.LIFESTYLE,
                description = "Keep健身课程搜索"
            ),
            // 薄荷健康
            AppSearchConfig(
                appId = "boohee",
                appName = "薄荷健康",
                packageName = "com.boohee.one",
                isEnabled = true,
                order = 87,
                iconResId = android.R.drawable.ic_menu_mylocation,
                searchUrl = "boohee://search?keyword={q}",
                category = AppCategory.LIFESTYLE,
                description = "薄荷健康食物热量搜索"
            ),
            
            // ========== 新闻资讯类 ==========
            // 今日头条
            AppSearchConfig(
                appId = "toutiao",
                appName = "今日头条",
                packageName = "com.ss.android.article.news",
                isEnabled = true,
                order = 88,
                iconResId = android.R.drawable.ic_menu_recent_history,
                searchUrl = "snssdk141://search?keyword={q}",
                category = AppCategory.NEWS,
                description = "今日头条新闻、资讯搜索"
            ),
            // 澎湃新闻
            AppSearchConfig(
                appId = "thepaper",
                appName = "澎湃新闻",
                packageName = "com.thepaper.cn",
                isEnabled = true,
                order = 89,
                iconResId = android.R.drawable.ic_menu_recent_history,
                searchUrl = "thepaper://search?keyword={q}",
                category = AppCategory.NEWS,
                description = "澎湃新闻搜索"
            ),
            // 网易新闻
            AppSearchConfig(
                appId = "netease_news",
                appName = "网易新闻",
                packageName = "com.netease.newsreader.activity",
                isEnabled = true,
                order = 90,
                iconResId = android.R.drawable.ic_menu_recent_history,
                searchUrl = "netease://search?keyword={q}",
                category = AppCategory.NEWS,
                description = "网易新闻搜索"
            ),
            // 腾讯新闻
            AppSearchConfig(
                appId = "tencent_news",
                appName = "腾讯新闻",
                packageName = "com.tencent.news",
                isEnabled = true,
                order = 91,
                iconResId = android.R.drawable.ic_menu_recent_history,
                searchUrl = "tencentnews://search?keyword={q}",
                category = AppCategory.NEWS,
                description = "腾讯新闻搜索"
            ),
            
            // ========== 开发工具类 ==========
            // Visual Studio Code
            AppSearchConfig(
                appId = "vscode",
                appName = "Visual Studio Code",
                packageName = "com.microsoft.vscode",
                isEnabled = true,
                order = 92,
                iconResId = android.R.drawable.ic_menu_manage,
                searchUrl = "vscode://search?query={q}",
                category = AppCategory.LIFESTYLE,
                description = "Visual Studio Code项目文件、代码搜索"
            ),
            // Android Studio
            AppSearchConfig(
                appId = "android_studio",
                appName = "Android Studio",
                packageName = "com.google.android.studio",
                isEnabled = true,
                order = 93,
                iconResId = android.R.drawable.ic_menu_manage,
                searchUrl = "androidstudio://search?query={q}",
                category = AppCategory.LIFESTYLE,
                description = "Android Studio项目文件、代码搜索"
            ),
            // Sublime Text
            AppSearchConfig(
                appId = "sublime",
                appName = "Sublime Text",
                packageName = "com.sublimetext.three",
                isEnabled = true,
                order = 94,
                iconResId = android.R.drawable.ic_menu_manage,
                searchUrl = "sublime://search?query={q}",
                category = AppCategory.LIFESTYLE,
                description = "Sublime Text文件、代码搜索"
            )
        )
    }

    // 重新排序应用
    fun reorderApp(appId: String, newOrder: Int) {
        val configs = getAppConfigs().toMutableList()
        val app = configs.find { it.appId == appId } ?: return
        val oldOrder = app.order
        
        // 更新其他应用的顺序
        configs.forEach { config ->
            when {
                config.appId == appId -> config.order = newOrder
                newOrder > oldOrder && config.order in (oldOrder + 1)..newOrder ->
                    config.order--
                newOrder < oldOrder && config.order in newOrder until oldOrder ->
                    config.order++
            }
        }
        
        saveAppConfigs(configs)
    }

    // 启用/禁用应用
    fun toggleAppEnabled(appId: String, enabled: Boolean) {
        updateAppConfig(appId) { it.isEnabled = enabled }
    }

    // 获取已启用的应用配置（按顺序）
    fun getEnabledAppConfigs(): List<AppSearchConfig> {
        return getAppConfigs()
            .filter { it.isEnabled }
            .sortedBy { it.order }
    }

    // 根据分类获取应用配置
    fun getAppConfigsByCategory(category: AppCategory): List<AppSearchConfig> {
        return if (category == AppCategory.ALL) {
            getAppConfigs().filter { it.isEnabled }.sortedBy { it.order }
        } else {
            getAppConfigs().filter { it.category == category && it.isEnabled }.sortedBy { it.order }
        }
    }

    // 获取所有分类
    fun getAllCategories(): List<AppCategory> {
        return AppCategory.values().toList()
    }

    // 强制重置配置到最新版本（用于调试或强制更新）
    fun forceResetToLatestConfig() {
        val defaultConfigs = getDefaultConfigs()
        saveAppConfigs(defaultConfigs)
        updateConfigVersion()
    }
}