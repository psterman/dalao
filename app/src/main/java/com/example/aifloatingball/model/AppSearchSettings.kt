package com.example.aifloatingball.model

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.aifloatingball.R

data class AppSearchConfig(
    val appId: String,          // 应用标识符
    val appName: String,        // 应用名称
    val packageName: String,    // 应用包名
    var isEnabled: Boolean,     // 是否启用
    var order: Int,            // 排序顺序
    val iconResId: Int,         // 图标资源ID
    val searchUrl: String       // 搜索URL模板
)

class AppSearchSettings(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_search_settings", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_APP_CONFIGS = "app_configs"
        private const val KEY_LAST_ORDER = "last_order"
        
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
        val json = prefs.getString(KEY_APP_CONFIGS, null)
        return if (json != null) {
            try {
                val type = object : TypeToken<List<AppSearchConfig>>() {}.type
                gson.fromJson(json, type)
            } catch (e: Exception) {
                // 解析失败时返回默认配置
                getDefaultConfigs()
            }
        } else {
            // 返回默认配置
            getDefaultConfigs()
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
                searchUrl = "weixin://" // 微信没有直接的搜索Scheme，只能启动应用
            ),
            AppSearchConfig(
                appId = "taobao",
                appName = "淘宝",
                packageName = "com.taobao.taobao",
                isEnabled = true,
                order = 2,
                iconResId = R.drawable.ic_web_default,
                searchUrl = "taobao://s.taobao.com/search?q={q}"
            ),
            AppSearchConfig(
                appId = "pdd",
                appName = "拼多多",
                packageName = "com.xunmeng.pinduoduo",
                isEnabled = true,
                order = 3,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "pinduoduo://com.xunmeng.pinduoduo/search_result.html?search_key={q}"
            ),
            AppSearchConfig(
                appId = "douyin",
                appName = "抖音",
                packageName = "com.ss.android.ugc.aweme",
                isEnabled = true,
                order = 4,
                iconResId = R.drawable.ic_douyin,
                searchUrl = "snssdk1128://search/tabs?keyword={q}"
            ),
            AppSearchConfig(
                appId = "xiaohongshu",
                appName = "小红书",
                packageName = "com.xingin.xhs",
                isEnabled = true,
                order = 5,
                iconResId = R.drawable.ic_xiaohongshu,
                searchUrl = "xhsdiscover://search/result?keyword={q}"
            ),
            AppSearchConfig(
                appId = "meituan",
                appName = "美团",
                packageName = "com.sankuai.meituan",
                isEnabled = true,
                order = 6,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "imeituan://www.meituan.com/search?q={q}"
            ),
            AppSearchConfig(
                appId = "douban",
                appName = "豆瓣",
                packageName = "com.douban.frodo",
                isEnabled = false,
                order = 7,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "douban:///search?q={q}"
            ),
            AppSearchConfig(
                appId = "weibo",
                appName = "微博",
                packageName = "com.sina.weibo",
                isEnabled = false,
                order = 8,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "sinaweibo://searchall?q={q}"
            ),
            AppSearchConfig(
                appId = "tiktok",
                appName = "TikTok",
                packageName = "com.zhiliaoapp.musically",
                isEnabled = false,
                order = 9,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "snssdk1128://search/tabs?keyword={q}" // 与抖音相同
            ),
            AppSearchConfig(
                appId = "twitter",
                appName = "Twitter-X",
                packageName = "com.twitter.android",
                isEnabled = false,
                order = 10,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "twitter://search?query={q}"
            ),
            AppSearchConfig(
                appId = "zhihu",
                appName = "知乎",
                packageName = "com.zhihu.android",
                isEnabled = false,
                order = 11,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "zhihu://search?q={q}"
            ),
            AppSearchConfig(
                appId = "bilibili",
                appName = "哔哩哔哩",
                packageName = "tv.danmaku.bili",
                isEnabled = false,
                order = 12,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "bilibili://search?keyword={q}"
            ),
            AppSearchConfig(
                appId = "youtube",
                appName = "YouTube",
                packageName = "com.google.android.youtube",
                isEnabled = false,
                order = 13,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "youtube://results?search_query={q}"
            ),
            AppSearchConfig(
                appId = "spotify",
                appName = "Spotify",
                packageName = "com.spotify.music",
                isEnabled = false,
                order = 14,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "spotify:search:{q}"
            ),
            AppSearchConfig(
                appId = "tmall",
                appName = "天猫",
                packageName = "com.tmall.wireless",
                isEnabled = false,
                order = 15,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "tmall://page.tm/search?q={q}"
            ),
            AppSearchConfig(
                appId = "jd",
                appName = "京东",
                packageName = "com.jingdong.app.mall",
                isEnabled = false,
                order = 16,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "openjd://virtual?params={\"des\":\"productList\",\"keyWord\":\"{q}\"}"
            ),
            AppSearchConfig(
                appId = "xianyu",
                appName = "闲鱼",
                packageName = "com.taobao.idlefish",
                isEnabled = false,
                order = 17,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "fleamarket://x_search_items" // 闲鱼不支持直接带关键词
            ),
            AppSearchConfig(
                appId = "dianping",
                appName = "大众点评",
                packageName = "com.dianping.v1",
                isEnabled = false,
                order = 18,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "dianping://searchshoplist?keyword={q}"
            ),
            AppSearchConfig(
                appId = "chrome",
                appName = "Chrome",
                packageName = "com.android.chrome",
                isEnabled = false,
                order = 19,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "googlechrome://www.google.com/search?q={q}"
            ),
            AppSearchConfig(
                appId = "eudic",
                appName = "欧路词典",
                packageName = "com.eusoft.eudic",
                isEnabled = false,
                order = 20,
                iconResId = android.R.drawable.ic_menu_search,
                searchUrl = "eudic://dict/{q}"
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
} 