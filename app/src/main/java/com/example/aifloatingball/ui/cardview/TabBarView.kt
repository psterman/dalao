package com.example.aifloatingball.ui.cardview

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import com.example.aifloatingball.R
import com.example.aifloatingball.model.SearchEngine
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 标签栏视图
 * 支持自定义标签、排序、点击切换
 */
class TabBarView(
    context: Context,
    private val container: ViewGroup
) {
    companion object {
        private const val TAG = "TabBarView"
        private const val PREFS_NAME = "card_view_tabs"
        private const val KEY_TABS = "tabs_json"
        private const val KEY_DATA_VERSION = "tabs_data_version"
        private const val CURRENT_DATA_VERSION = 2 // 版本2：区分"我的频道"和"推荐频道"
        
        // 默认标签（参考图片中的频道设计，包含"我的频道"和"推荐频道"中的所有标签）
        private val DEFAULT_TABS = listOf(
            // 我的频道
            "推荐", "视频", "直播", "短剧", "美食", "旅行", "穿搭", "手工",
            "搞笑", "壁纸", "摄影", "音乐", "家装", "汽车", "情感", "家居",
            "游戏", "影视", "科技数码", "绘画", "健身塑型", "职场", "头像", "读书",
            "科学科普", "舞蹈", "动漫", "婚礼", "潮鞋", "户外", "艺术", "萌宠",
            "体育", "竞技体育", "减脂", "男士理容", "机车", "学习", "文具手账", "明星",
            "文化", "社科", "母婴", "综艺", "护肤", "露营", "心理", "潮玩手办",
            "校园生活", "人文", "AI", "咨询",
            // 推荐频道（新增）
            "购物", "时尚", "美妆", "数码", "生活", "健康", "教育", "财经",
            "娱乐", "军事", "历史", "探索", "育儿", "宠物", "星座", "房产",
            "设计", "科技"
        )
        
        // 默认在"我的频道"中的标签（只包含部分常用标签）
        private val DEFAULT_MY_CHANNELS = listOf(
            "推荐", "视频", "直播", "短剧", "美食", "旅行", "穿搭", "手工",
            "搞笑", "壁纸", "摄影", "音乐", "家装", "汽车", "情感", "家居",
            "游戏", "影视", "科技数码", "绘画", "健身塑型", "职场", "头像", "读书"
        )
    }

    private val context: Context = context
    private var scrollView: HorizontalScrollView? = null
    private var tabContainer: LinearLayout? = null
    private var editButton: TextView? = null
    private val tabList = mutableListOf<TabItem>()
    private var selectedTabIndex = 0
    private var isEditMode = false
    
    // 标签点击监听器
    private var onTabClickListener: OnTabClickListener? = null
    
    // 当前标签管理对话框
    private var currentManageDialog: AlertDialog? = null
    
    // 编辑模式状态（用于标签管理对话框）
    private var isManageDialogEditMode = false
    
    // 对话框内的视图引用，用于无闪动更新
    private var manageDialogScrollView: android.widget.ScrollView? = null
    private var manageDialogMainContainer: LinearLayout? = null
    private var manageDialogEditButton: TextView? = null
    
    // 拖拽相关变量
    private var draggedTabView: TextView? = null
    private var draggedTabIndex: Int = -1
    private var dragStartY: Float = 0f
    private var dragStartX: Float = 0f
    private var isDragging = false
    // 保存所有标签视图的引用，用于拖动时更新位置
    private var myChannelsTabViews: MutableList<TextView> = mutableListOf()
    private var myChannelsGridContainer: LinearLayout? = null
    // 保存每个标签的原始位置（在拖动开始时记录），用于准确计算目标位置
    private var tabOriginalPositions: MutableMap<TextView, Pair<Float, Float>> = mutableMapOf()
    
    // 标签数据类
    data class TabItem(
        val name: String,
        val engineKeys: List<String> = emptyList(), // 该标签对应的搜索引擎名称列表
        var isCustom: Boolean = false
    ) {
        /**
         * 获取该标签对应的默认搜索引擎列表
         * 如果engineKeys为空，则根据标签名称返回对应的默认搜索引擎
         */
        fun getDefaultEngines(): List<String> {
            if (engineKeys.isNotEmpty()) {
                return engineKeys
            }
            
            // 根据标签名称返回对应的默认搜索引擎（每个标签配置11个搜索引擎）
            return when (name) {
                // 视频相关
                "推荐" -> listOf("baidu", "google", "bing_cn", "sogou", "360", "quark", "shenma", "duckduckgo", "yahoo", "yandex", "ecosia")
                "视频" -> listOf("douyin", "kuaishou", "bilibili", "ixigua", "weibo_video", "xiaohongshu_video", "tencent_video_search", "youku_search", "open163", "youtube_mobile", "iqiyi")
                "直播" -> listOf("douyin_live", "kuaishou_live", "bilibili_live", "huya", "douyu", "yy", "inke", "cc_live", "egame", "now_live", "taobao_live")
                "短剧" -> listOf("douyin_short", "kuaishou_short", "bilibili_short", "mgtv", "youku_short", "tencent_short", "ixigua_short", "weishi", "iqiyi_short", "sohu_tv", "taobao_short")
                "影视" -> listOf("douban_movie", "maoyan", "taopiaopiao", "iqiyi_movie", "tencent_video_movie", "youku_movie", "bilibili_movie", "douyin_movie", "kuaishou_movie", "weibo_movie", "zhihu_movie")
                
                // 生活相关
                "美食" -> listOf("xiachufang", "douguo", "meishichina", "dianping_shop", "eleme", "meituan_waimai", "xiaohongshu_food", "bilibili_food", "douyin_food", "kuaishou_food", "weibo_food")
                "旅行" -> listOf("mafengwo", "ctrip", "qunar", "fliggy", "ly", "qyer", "xiaohongshu_travel", "bilibili_travel", "douyin_travel", "kuaishou_travel", "weibo_travel")
                "穿搭" -> listOf("xiaohongshu_fashion", "douyin_fashion", "kuaishou_fashion", "bilibili_fashion", "weibo_fashion", "taobao_search", "jd_search", "poizon", "mogujie", "smzdm", "meilishuo")
                "手工" -> listOf("xiaohongshu_handmake", "bilibili_handmake", "douyin_handmake", "kuaishou_handmake", "weibo_handmake", "xiachufang_handmake", "diysite", "duitang", "lofter", "douban_group", "jianshu")
                "家装" -> listOf("xiaohongshu_home", "douyin_home", "bilibili_home", "weibo_home", "haohaozhu", "yidoutang", "jia", "to8to", "jd_home", "taobao_home", "zhihu_home")
                "家居" -> listOf("xiaohongshu_furniture", "douyin_furniture", "bilibili_furniture", "weibo_furniture", "haohaozhu_furniture", "yidoutang_furniture", "jd_furniture", "taobao_furniture", "ikea", "you163", "zhihu_furniture")
                "情感" -> listOf("xiaohongshu_emotion", "douyin_emotion", "kuaishou_emotion", "bilibili_emotion", "weibo_emotion", "zhihu_emotion", "douban_emotion", "xinli001", "songguo7", "tianya", "jianshu_emotion")
                
                // 娱乐相关
                "搞笑" -> listOf("douyin_funny", "kuaishou_funny", "bilibili_funny", "weibo_funny", "ppx", "neihanshequ", "izuiyou", "qiushibaike", "weishi_funny", "xiaohongshu_funny", "zhihu_funny")
                "壁纸" -> listOf("bing_images", "sogou_images", "360_images", "duitang_wallpaper", "xiaohongshu_wallpaper", "douyin_wallpaper", "kuaishou_wallpaper", "bilibili_wallpaper", "lofter_wallpaper", "douban_wallpaper", "zzzmh")
                "摄影" -> listOf("500px", "tuchong", "lofter_photo", "xiaohongshu_photo", "bilibili_photo", "douyin_photo", "kuaishou_photo", "weibo_photo", "zhihu_photo", "douban_photo", "nikonclub")
                "音乐" -> listOf("qqmusic", "netease_music", "kugou", "kuwo", "migu_music", "qishui", "douyin_music", "bilibili_music", "kuaishou_music", "weibo_music", "soundcloud")
                "绘画" -> listOf("xiaohongshu_paint", "bilibili_paint", "douyin_paint", "kuaishou_paint", "weibo_paint", "lofter_paint", "zcool_paint", "poocg", "pixiv", "artstation", "douban_paint")
                "舞蹈" -> listOf("douyin", "kuaishou", "bilibili", "weibo", "xiaohongshu", "zhihu", "baidu", "google", "sogou", "bing_cn", "toutiao")
                "动漫" -> listOf("bilibili", "douyin", "kuaishou", "weibo", "xiaohongshu", "zhihu", "baidu", "google", "sogou", "bing_cn", "douban")
                
                // 购物相关
                "购物" -> listOf("taobao", "jd", "pinduoduo", "suning", "dangdang", "baidu", "google", "sogou", "xiaohongshu", "weibo")
                "潮鞋" -> listOf("taobao", "jd", "xiaohongshu", "baidu", "pinduoduo", "suning", "google", "sogou", "weibo", "zhihu")
                
                // 汽车相关
                "汽车" -> listOf("autohome", "dongchedi", "yiche", "pcauto", "xcar", "xiaohongshu_car", "douyin_car", "bilibili_car", "weibo_car", "zhihu_car", "jd_car")
                "机车" -> listOf("autohome", "dongchedi", "yiche", "pcauto", "xcar", "xiaohongshu_car", "douyin_car", "bilibili_car", "weibo_car", "zhihu_car", "jd_car")
                
                // 职场学习
                "职场" -> listOf("zhihu_job", "xiaohongshu_job", "bilibili_job", "douyin_job", "kuaishou_job", "weibo_job", "maimai", "lagou", "zhipin", "liepin", "linkedin")
                "学习" -> listOf("zhihu", "bilibili", "baidu", "google", "csdn", "juejin", "github", "wikipedia", "baidu_baike", "douban", "weread")
                "文具手账" -> listOf("taobao_search", "xiaohongshu", "bilibili", "baidu", "jd_search", "pinduoduo", "suning", "google", "weibo", "zhihu", "smzdm")
                "校园生活" -> listOf("bilibili", "xiaohongshu", "zhihu", "weibo", "baidu", "google", "douban", "toutiao", "sogou", "tencent_news", "weread")
                
                // 知识相关
                "读书" -> listOf("weread", "douban_book", "zhihu_book", "xiaohongshu_book", "bilibili_book", "douyin_book", "kuaishou_book", "weibo_book", "dedao", "ximalaya", "kindle")
                "科学科普" -> listOf("zhihu", "bilibili", "baidu", "google", "wikipedia", "baidu_baike", "bing_cn", "sogou", "google_scholar", "douban")
                "文化" -> listOf("zhihu", "douban", "baidu", "google", "wikipedia", "baidu_baike", "bing_cn", "sogou", "toutiao", "sina_news")
                "社科" -> listOf("zhihu", "baidu", "google", "wikipedia", "baidu_baike", "bing_cn", "sogou", "google_scholar", "cnki", "douban")
                "人文" -> listOf("zhihu", "douban", "baidu", "wikipedia", "baidu_baike", "google", "bing_cn", "sogou", "toutiao", "sina_news")
                
                // 健康运动
                "健身塑型" -> listOf("keep", "xiaohongshu_fitness", "bilibili_fitness", "douyin_fitness", "kuaishou_fitness", "weibo_fitness", "zhihu_fitness", "codoon", "yuepaocircle", "boohee", "leoao")
                "减脂" -> listOf("keep", "xiaohongshu_fitness", "bilibili_fitness", "douyin_fitness", "kuaishou_fitness", "weibo_fitness", "zhihu_fitness", "codoon", "yuepaocircle", "boohee", "leoao")
                "体育" -> listOf("baidu", "google", "toutiao", "sina_news", "tencent_news", "netease_news", "sogou", "bing_cn", "weibo", "zhihu")
                "竞技体育" -> listOf("baidu", "google", "toutiao", "sina_news", "tencent_news", "netease_news", "sogou", "bing_cn", "weibo", "zhihu")
                
                // 美妆时尚
                "护肤" -> listOf("xiaohongshu", "taobao", "baidu", "google", "jd", "pinduoduo", "weibo", "zhihu", "sogou", "bing_cn")
                "男士理容" -> listOf("xiaohongshu", "taobao", "baidu", "google", "jd", "pinduoduo", "weibo", "zhihu", "sogou", "bing_cn")
                
                // 宠物
                "宠萌" -> listOf("baidu", "google", "xiaohongshu", "weibo", "zhihu", "douban", "bilibili", "sogou", "bing_cn", "toutiao")
                "萌宠" -> listOf("baidu", "google", "xiaohongshu", "weibo", "zhihu", "douban", "bilibili", "sogou", "bing_cn", "toutiao")
                
                // 其他
                "游戏" -> listOf("taptap", "bilibili_game", "douyin_game", "kuaishou_game", "weibo_game", "xiaohongshu_game", "zhihu_game", "4399", "gamersky", "gcores", "ds163")
                "科技数码" -> listOf("zhihu_tech", "xiaohongshu_tech", "bilibili_tech", "douyin_tech", "kuaishou_tech", "weibo_tech", "ithome", "pconline", "zol", "smzdm_tech", "jd_tech")
                "头像" -> listOf("xiaohongshu_avatar", "duitang_avatar", "lofter_avatar", "douyin_avatar", "kuaishou_avatar", "bilibili_avatar", "weibo_avatar", "canva", "touxiangdaquan", "qzone", "weixin")
                "咨询" -> listOf("toutiao", "tencent_news", "sina_news", "netease_news", "baidu", "google", "sogou", "bing_cn", "the_paper", "guancha")
                "AI" -> listOf("chatgpt_web", "claude_web", "gemini_web", "wenxin_yiyan", "tongyi_qianwen", "kimi_web", "deepseek_web", "zhipu_ai", "xinghuo_web", "doubao_web", "perplexity_web")
                "婚礼" -> listOf("xiaohongshu", "baidu", "taobao", "google", "weibo", "zhihu", "dianping", "meituan", "sogou", "bing_cn")
                "户外" -> listOf("xiaohongshu", "baidu", "dianping", "google", "meituan", "weibo", "zhihu", "sogou", "bing_cn", "toutiao")
                "艺术" -> listOf("bilibili", "xiaohongshu", "zhihu", "baidu", "google", "weibo", "douban", "dribbble", "behance", "zcool")
                "明星" -> listOf("weibo", "baidu", "google", "zhihu", "xiaohongshu", "bilibili", "douban", "sogou", "bing_cn", "toutiao")
                "母婴" -> listOf("taobao", "xiaohongshu", "baidu", "google", "jd", "pinduoduo", "weibo", "zhihu", "sogou", "bing_cn")
                "综艺" -> listOf("bilibili", "youku", "tencent_video", "baidu", "ixigua", "google", "sogou", "weibo", "xiaohongshu", "douban")
                "露营" -> listOf("xiaohongshu", "dianping", "baidu", "google", "meituan", "weibo", "zhihu", "sogou", "bing_cn", "toutiao")
                "心理" -> listOf("zhihu", "douban", "baidu", "google", "wikipedia", "baidu_baike", "bing_cn", "sogou", "weibo", "xiaohongshu")
                "潮玩手办" -> listOf("taobao", "bilibili", "xiaohongshu", "baidu", "jd", "pinduoduo", "weibo", "zhihu", "google", "sogou")
                
                // 推荐频道新增标签
                "购物" -> listOf("taobao", "jd", "pinduoduo", "suning", "dangdang", "baidu", "google", "sogou", "xiaohongshu", "weibo")
                "时尚" -> listOf("xiaohongshu", "taobao", "weibo", "baidu", "jd", "google", "sogou", "zhihu", "douban", "bilibili")
                "美妆" -> listOf("xiaohongshu", "taobao", "baidu", "google", "jd", "pinduoduo", "weibo", "zhihu", "sogou", "bing_cn")
                "数码" -> listOf("jd", "taobao", "zhihu", "baidu", "google", "sogou", "bing_cn", "csdn", "juejin", "github")
                "生活" -> listOf("xiaohongshu", "dianping", "meituan", "baidu", "google", "sogou", "weibo", "zhihu", "bing_cn", "toutiao")
                "健康" -> listOf("baidu", "google", "zhihu", "xiaohongshu", "wikipedia", "baidu_baike", "bing_cn", "sogou", "weibo", "toutiao")
                "教育" -> listOf("zhihu", "baidu", "google", "bilibili", "wikipedia", "baidu_baike", "csdn", "juejin", "google_scholar", "cnki")
                "财经" -> listOf("baidu", "google", "toutiao", "sina_news", "tencent_news", "netease_news", "sogou", "bing_cn", "caixin", "jiemian")
                "娱乐" -> listOf("weibo", "bilibili", "baidu", "youku", "tencent_video", "xiaohongshu", "zhihu", "google", "sogou", "toutiao")
                "军事" -> listOf("baidu", "google", "toutiao", "sina_news", "tencent_news", "netease_news", "sogou", "bing_cn", "the_paper", "guancha")
                "历史" -> listOf("baidu_baike", "wikipedia", "zhihu", "baidu", "google", "bing_cn", "sogou", "douban", "toutiao", "sina_news")
                "探索" -> listOf("baidu", "google", "zhihu", "bilibili", "wikipedia", "baidu_baike", "bing_cn", "sogou", "douban", "xiaohongshu")
                "育儿" -> listOf("xiaohongshu", "taobao", "zhihu", "baidu", "jd", "pinduoduo", "weibo", "google", "sogou", "bing_cn")
                "宠物" -> listOf("xiaohongshu", "baidu", "google", "weibo", "zhihu", "douban", "bilibili", "sogou", "bing_cn", "toutiao")
                "星座" -> listOf("baidu", "google", "weibo", "xiaohongshu", "zhihu", "douban", "sogou", "bing_cn", "toutiao", "sina_news")
                "房产" -> listOf("baidu", "google", "taobao", "jd", "sogou", "bing_cn", "toutiao", "sina_news", "58tongcheng", "ganji")
                "设计" -> listOf("bilibili", "xiaohongshu", "zhihu", "baidu", "google", "weibo", "douban", "dribbble", "behance", "zcool")
                "科技" -> listOf("zhihu", "csdn", "baidu", "google", "juejin", "github", "bing_cn", "sogou", "weibo", "toutiao")
                
                else -> listOf("baidu", "google", "bing_cn", "sogou") // 默认搜索引擎
            }
        }
    }

    /**
     * 标签点击监听器
     */
    interface OnTabClickListener {
        fun onTabClick(tab: TabItem, position: Int)
        fun onTabLongClick(tab: TabItem, position: Int)
    }

    init {
        setupTabBar()
        loadTabs()
    }

    /**
     * 设置标签栏
     */
    private fun setupTabBar() {
        // 创建主容器（包含编辑按钮和标签滚动视图）
        val mainContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        // 创建编辑按钮（绿色加号），放在标签最尾端
        editButton = TextView(context).apply {
            text = "+"
            textSize = 20f
            setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(6))
            gravity = Gravity.CENTER
            setTextColor(0xFF4CAF50.toInt()) // 绿色
            // 添加圆角背景
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dpToPx(8).toFloat()
                setColor(android.graphics.Color.TRANSPARENT) // 透明背景
                setStroke(dpToPx(2), 0xFF4CAF50.toInt()) // 绿色勾边
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dpToPx(4) // 与标签的间距
            }
            setOnClickListener {
                showEditDialog()
            }
        }
        
        scrollView = HorizontalScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }

        tabContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(dpToPx(8), dpToPx(8), dpToPx(8), dpToPx(8))
        }

        scrollView?.addView(tabContainer)
        mainContainer.addView(scrollView)
        mainContainer.addView(editButton) // 放在最后
        container.addView(mainContainer)
    }

    /**
     * 加载标签
     */
    private fun loadTabs() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val tabsJson = prefs.getString(KEY_TABS, null)
        val dataVersion = prefs.getInt(KEY_DATA_VERSION, 1) // 默认为版本1（旧版本）
        
        if (tabsJson != null) {
            try {
                val type = object : TypeToken<List<TabItem>>() {}.type
                val savedTabs: List<TabItem> = Gson().fromJson(tabsJson, type)
                tabList.clear()
                tabList.addAll(savedTabs)
                
                // 如果是旧版本数据（版本1），且所有标签都在"我的频道"中，进行数据迁移
                if (dataVersion < CURRENT_DATA_VERSION) {
                    migrateOldData(savedTabs)
                    // 更新版本号
                    prefs.edit().putInt(KEY_DATA_VERSION, CURRENT_DATA_VERSION).apply()
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载标签失败", e)
                initDefaultTabs()
            }
        } else {
            initDefaultTabs()
            // 设置版本号
            prefs.edit().putInt(KEY_DATA_VERSION, CURRENT_DATA_VERSION).apply()
        }
        
        refreshTabs()
    }
    
    /**
     * 迁移旧版本数据
     * 如果所有标签都在"我的频道"中（可能是旧版本的数据），只保留默认的"我的频道"标签
     */
    private fun migrateOldData(savedTabs: List<TabItem>) {
        // 检查是否所有默认标签都在"我的频道"中
        val savedTabNames = savedTabs.map { it.name }.toSet()
        val allDefaultTabsInMyChannels = DEFAULT_TABS.all { it in savedTabNames }
        
        if (allDefaultTabsInMyChannels && savedTabs.size >= DEFAULT_TABS.size) {
            // 这是旧版本的数据，所有标签都在"我的频道"中
            // 只保留默认的"我的频道"标签，其他标签移除（它们会在"推荐频道"中显示）
            Log.d(TAG, "检测到旧版本数据，进行数据迁移：从 ${savedTabs.size} 个标签迁移到 ${DEFAULT_MY_CHANNELS.size} 个标签")
            tabList.clear()
            // 只添加默认的"我的频道"标签
            DEFAULT_MY_CHANNELS.forEach { name ->
                // 保留用户的自定义配置（如果有）
                val existingTab = savedTabs.find { it.name == name }
                if (existingTab != null) {
                    tabList.add(existingTab)
                } else {
                    tabList.add(TabItem(name = name, isCustom = false))
                }
            }
            saveTabs()
        }
    }

    /**
     * 初始化默认标签
     * 只将部分常用标签添加到"我的频道"，其他标签在"推荐频道"中
     */
    private fun initDefaultTabs() {
        tabList.clear()
        // 只添加默认的"我的频道"标签
        DEFAULT_MY_CHANNELS.forEach { name ->
            tabList.add(TabItem(name = name, isCustom = false))
        }
        saveTabs()
    }

    /**
     * 刷新标签显示
     */
    private fun refreshTabs() {
        tabContainer?.removeAllViews()
        
        tabList.forEachIndexed { index, tab ->
            val tabView = createTabView(tab, index)
            if (isEditMode) {
                updateTabStyleForEditMode(tabView, tab, index)
            }
            tabContainer?.addView(tabView)
        }
        
        // 选中第一个标签
        if (tabList.isNotEmpty() && selectedTabIndex < tabList.size) {
            selectTab(selectedTabIndex)
        }
    }

    /**
     * 创建标签视图
     */
    private fun createTabView(tab: TabItem, position: Int): TextView {
        return TextView(context).apply {
            text = tab.name
            textSize = 14f
            setPadding(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8))
            gravity = Gravity.CENTER
            
            // 设置背景和文字颜色
            updateTabStyle(this, position == selectedTabIndex)
            
            // 点击事件
            setOnClickListener {
                selectTab(position)
                onTabClickListener?.onTabClick(tab, position)
            }
            
            // 长按事件（用于编辑/删除）
            setOnLongClickListener {
                if (isEditMode) {
                    showTabEditDialog(tab, position)
                } else {
                    onTabClickListener?.onTabLongClick(tab, position)
                }
                true
            }
            
            // 设置布局参数，缩小标签之间的间距
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = dpToPx(4) // 从8dp缩小到4dp
            }
        }
    }

    /**
     * 更新标签样式
     */
    private fun updateTabStyle(tabView: TextView, isSelected: Boolean) {
        if (isSelected) {
            // 使用淡绿色勾边背景
            val drawable = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dpToPx(8).toFloat()
                setColor(0xFFE8F5E9.toInt()) // 淡绿色背景
                setStroke(dpToPx(2), 0xFF4CAF50.toInt()) // 绿色勾边
            }
            tabView.background = drawable
            tabView.setTextColor(0xFF2E7D32.toInt()) // 深绿色文字
        } else {
            tabView.setBackgroundResource(R.drawable.tab_normal_background)
            tabView.setTextColor(ContextCompat.getColor(context, R.color.card_view_tab_normal_text))
        }
    }

    /**
     * 选中标签
     */
    fun selectTab(position: Int) {
        if (position < 0 || position >= tabList.size) return
        
        // 更新之前的选中状态
        val oldView = tabContainer?.getChildAt(selectedTabIndex) as? TextView
        oldView?.let { updateTabStyle(it, false) }
        
        // 更新新的选中状态
        selectedTabIndex = position
        val newView = tabContainer?.getChildAt(selectedTabIndex) as? TextView
        newView?.let { updateTabStyle(it, true) }
        
        // 滚动到选中标签
        newView?.let { scrollToTab(it) }
    }

    /**
     * 滚动到指定标签
     */
    private fun scrollToTab(tabView: View) {
        scrollView?.post {
            val scrollViewWidth = scrollView?.width ?: 0
            val tabLeft = tabView.left
            val tabWidth = tabView.width
            val scrollX = tabLeft - (scrollViewWidth - tabWidth) / 2
            
            scrollView?.smoothScrollTo(
                scrollX.coerceAtLeast(0),
                0
            )
        }
    }

    /**
     * 添加标签
     */
    fun addTab(name: String, engineKeys: List<String> = emptyList()): Boolean {
        if (tabList.any { it.name == name }) {
            Log.w(TAG, "标签已存在: $name")
            return false
        }
        
        val newTab = TabItem(name = name, engineKeys = engineKeys, isCustom = true)
        tabList.add(newTab)
        refreshTabs()
        saveTabs()
        
        Log.d(TAG, "添加标签: $name")
        return true
    }

    /**
     * 删除标签
     */
    fun removeTab(position: Int): Boolean {
        if (position < 0 || position >= tabList.size) return false
        
        val tab = tabList[position]
        if (!tab.isCustom) {
            Log.w(TAG, "不能删除默认标签: ${tab.name}")
            return false
        }
        
        tabList.removeAt(position)
        
        // 调整选中索引
        if (selectedTabIndex >= tabList.size) {
            selectedTabIndex = tabList.size - 1
        }
        
        refreshTabs()
        saveTabs()
        
        Log.d(TAG, "删除标签: ${tab.name}")
        return true
    }

    /**
     * 移动标签位置
     */
    fun moveTab(fromPosition: Int, toPosition: Int): Boolean {
        if (fromPosition < 0 || fromPosition >= tabList.size ||
            toPosition < 0 || toPosition >= tabList.size) {
            return false
        }
        
        val tab = tabList.removeAt(fromPosition)
        tabList.add(toPosition, tab)
        
        // 更新选中索引
        selectedTabIndex = when {
            selectedTabIndex == fromPosition -> toPosition
            selectedTabIndex > fromPosition && selectedTabIndex <= toPosition -> selectedTabIndex - 1
            selectedTabIndex < fromPosition && selectedTabIndex >= toPosition -> selectedTabIndex + 1
            else -> selectedTabIndex
        }
        
        refreshTabs()
        saveTabs()
        
        Log.d(TAG, "移动标签: ${tab.name} from $fromPosition to $toPosition")
        return true
    }

    /**
     * 获取当前选中的标签
     */
    fun getSelectedTab(): TabItem? {
        return tabList.getOrNull(selectedTabIndex)
    }
    
    /**
     * 获取当前选中的标签索引
     */
    fun getSelectedTabIndex(): Int = selectedTabIndex

    /**
     * 获取所有标签
     */
    fun getAllTabs(): List<TabItem> = tabList.toList()

    /**
     * 设置标签点击监听器
     */
    fun setOnTabClickListener(listener: OnTabClickListener) {
        this.onTabClickListener = listener
    }

    /**
     * 显示编辑对话框
     */
    private fun showEditDialog() {
        // 在 Service 上下文中必须使用 AppCompat 主题
        val dialogTheme = androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert
        val themedContext = ContextThemeWrapper(context, dialogTheme)
        
        // 显示标签管理对话框
        showTabManageDialog()
    }
    
    /**
     * 刷新标签管理对话框（不关闭，直接更新内容，避免闪动）
     */
    private fun refreshManageDialog() {
        val dialog = currentManageDialog ?: return
        val mainContainer = manageDialogMainContainer ?: return
        val scrollView = manageDialogScrollView ?: return
        val editButton = manageDialogEditButton ?: return
        
        // 更新编辑按钮文字
        editButton.text = if (isManageDialogEditMode) "完成" else "编辑"
        
        // 清除旧内容
        mainContainer.removeAllViews()
        
        // 检测暗色模式
        val isDarkMode = (context.resources.configuration.uiMode and 
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        
        // 重新创建内容
        // 我的频道部分
        val myChannelsTitle = TextView(context).apply {
            text = "我的频道"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dpToPx(8))
            setTextColor(if (isDarkMode) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
        }
        mainContainer.addView(myChannelsTitle)
        
        // 已选中的标签网格
        val myChannelsGrid = createTabGrid(tabList, true, isManageDialogEditMode)
        mainContainer.addView(myChannelsGrid)
        
        // 推荐频道部分
        val recommendedTitle = TextView(context).apply {
            text = "推荐频道"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dpToPx(16), 0, dpToPx(8))
            setTextColor(if (isDarkMode) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
        }
        mainContainer.addView(recommendedTitle)
        
        // 未选中的标签（推荐频道）
        val allTabs = DEFAULT_TABS.map { TabItem(it) }
        val recommendedTabs = allTabs.filter { defaultTab ->
            !tabList.any { it.name == defaultTab.name }
        }
        val recommendedGrid = createTabGrid(recommendedTabs, false)
        mainContainer.addView(recommendedGrid)
    }
    
    /**
     * 显示标签管理对话框（类似图片中的"我的频道"和"推荐频道"界面）
     */
    private fun showTabManageDialog() {
        val dialogTheme = androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert
        val themedContext = ContextThemeWrapper(context, dialogTheme)
        
        // 创建主容器，宽度撑满屏幕
        val mainContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            // 调整边距，让内容更贴近边缘
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
        }
        
        // 检测暗色模式
        val isDarkMode = (context.resources.configuration.uiMode and 
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        
        // 设置主容器背景色
        mainContainer.setBackgroundColor(
            if (isDarkMode) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt()
        )
        
        // 我的频道部分
        val myChannelsTitle = TextView(context).apply {
            text = "我的频道"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dpToPx(8))
            setTextColor(if (isDarkMode) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
        }
        mainContainer.addView(myChannelsTitle)
        
        // 已选中的标签网格
        val myChannelsGrid = createTabGrid(tabList, true, isManageDialogEditMode)
        mainContainer.addView(myChannelsGrid)
        
        // 推荐频道部分
        val recommendedTitle = TextView(context).apply {
            text = "推荐频道"
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dpToPx(16), 0, dpToPx(8))
            setTextColor(if (isDarkMode) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
        }
        mainContainer.addView(recommendedTitle)
        
        // 未选中的标签（推荐频道）
        val allTabs = DEFAULT_TABS.map { TabItem(it) }
        val recommendedTabs = allTabs.filter { defaultTab ->
            !tabList.any { it.name == defaultTab.name }
        }
        val recommendedGrid = createTabGrid(recommendedTabs, false)
        mainContainer.addView(recommendedGrid)
        
        // 创建滚动视图
        val scrollView = android.widget.ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dpToPx(400) // 限制高度
            )
            // 在编辑模式下，允许子视图处理触摸事件
            setOnTouchListener { view, event ->
                // 如果子视图请求不拦截，则允许子视图处理
                if (isManageDialogEditMode) {
                    // 在编辑模式下，让子视图优先处理触摸事件
                    false
                } else {
                    false
                }
            }
            addView(mainContainer)
        }
        
        // 创建标题栏，支持暗色/亮色模式，左上角关闭按钮，右上角编辑/完成按钮
        val titleContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12))
            setBackgroundColor(if (isDarkMode) 0xFF1E1E1E.toInt() else 0xFFFFFFFF.toInt())
        }
        
        // 左上角关闭按钮
        val closeButton = TextView(context).apply {
            text = "✕"
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(dpToPx(8), dpToPx(4), dpToPx(8), dpToPx(4))
            setTextColor(if (isDarkMode) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                currentManageDialog?.dismiss()
                currentManageDialog = null
                manageDialogScrollView = null
                manageDialogMainContainer = null
                manageDialogEditButton = null
            }
        }
        
        val titleView = TextView(context).apply {
            text = "管理标签"
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(if (isDarkMode) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = dpToPx(8)
            }
        }
        
        // 右上角编辑/完成按钮
        val editCompleteButton = TextView(context).apply {
            text = if (isManageDialogEditMode) "完成" else "编辑"
            textSize = 14f
            setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
            gravity = Gravity.CENTER
            setTextColor(if (isDarkMode) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
            setBackgroundColor(if (isDarkMode) 0xFF2C2C2C.toInt() else 0xFFF5F5F5.toInt())
            // 添加圆角
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dpToPx(6).toFloat()
                setColor(if (isDarkMode) 0xFF2C2C2C.toInt() else 0xFFF5F5F5.toInt())
            }
            setOnClickListener {
                if (isManageDialogEditMode) {
                    // 完成编辑，保存并退出编辑模式
                    isManageDialogEditMode = false
                    saveTabs()
                    // 无闪动刷新对话框
                    refreshManageDialog()
                } else {
                    // 进入编辑模式
                    isManageDialogEditMode = true
                    // 无闪动刷新对话框
                    refreshManageDialog()
                }
            }
        }
        
        // 保存引用以便后续更新
        manageDialogEditButton = editCompleteButton
        
        titleContainer.addView(closeButton)
        titleContainer.addView(titleView)
        titleContainer.addView(editCompleteButton)
        
        val builder = AlertDialog.Builder(themedContext)
            .setCustomTitle(titleContainer)
            .setView(scrollView)
            // 去掉底部的完成按钮
            .setNegativeButton(null, null)
        
        val dialog = builder.create()
        if (context is android.app.Service || context.javaClass.name.contains("Service")) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        }
        
        // 设置对话框不可通过点击外部区域取消，避免误操作
        dialog.setCanceledOnTouchOutside(false)
        dialog.setCancelable(false) // 只能通过关闭按钮关闭
        
        // 设置对话框宽度撑满屏幕
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
        
        // 保存引用以便后续更新
        currentManageDialog = dialog
        manageDialogScrollView = scrollView
        manageDialogMainContainer = mainContainer
        
        dialog.show()
    }
    
    /**
     * 创建标签网格
     */
    private fun createTabGrid(tabs: List<TabItem>, isMyChannels: Boolean, isEditMode: Boolean = false): LinearLayout {
        val gridContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        // 如果是"我的频道"，保存容器引用和清空标签视图列表
        if (isMyChannels) {
            myChannelsGridContainer = gridContainer
            myChannelsTabViews.clear()
        }
        
        // 每行4个标签
        val itemsPerRow = 4
        var currentRow: LinearLayout? = null
        
        tabs.forEachIndexed { index, tab ->
            if (index % itemsPerRow == 0) {
                currentRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }
                gridContainer.addView(currentRow)
            }
            
            // 检测暗色模式
            val isDarkMode = (context.resources.configuration.uiMode and 
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            
            val tabButton = TextView(context).apply {
                text = if (isMyChannels) tab.name else "+${tab.name}"
                textSize = 12f // 文字小一些
                gravity = Gravity.CENTER
                // 设置单行显示，不省略，完整显示文字
                maxLines = 1
                ellipsize = null // 不省略，完整显示
                // 调整内边距，不要正方形
                setPadding(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(6))
                // 确保可以接收触摸事件
                isClickable = true
                isFocusable = true
                // 禁用文本选择和长按菜单，避免干扰拖动操作
                setTextIsSelectable(false) // 使用方法调用而不是属性赋值
                setOnLongClickListener { false } // 禁用默认长按行为
                // 不设置最小和最大宽度，让文字自然显示
                // 使用 WRAP_CONTENT 让标签根据文字内容自适应宽度
                // 根据暗色/亮色模式和编辑模式设置背景和文字颜色
                // 编辑模式下使用拟态化效果，非编辑模式使用普通背景
                // 使用 isManageDialogEditMode 确保与触摸事件判断一致
                val isInEditMode = isMyChannels && isManageDialogEditMode
                if (isInEditMode) {
                    // 编辑模式下：使用拟态化背景，模拟实体感
                    background = createNeumorphicDrawable(false, isDarkMode)
                    // 添加轻微阴影，增强实体感
                    elevation = dpToPx(2).toFloat()
                } else {
                    // 非编辑模式：普通背景
                    val drawable = android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = dpToPx(8).toFloat()
                        val bgColor = if (isDarkMode) 0xFF2C2C2C.toInt() else 0xFFF5F5F5.toInt()
                        setColor(bgColor)
                    }
                    background = drawable
                    elevation = 0f
                }
                
                val textColor = when {
                    isInEditMode -> 0xFF4CAF50.toInt() // 编辑模式下淡绿色文字
                    isDarkMode -> 0xFFFFFFFF.toInt() // 暗色模式白色文字
                    else -> 0xFF000000.toInt() // 亮色模式黑色文字
                }
                setTextColor(textColor)
                // 创建布局参数，使用 WRAP_CONTENT 让标签根据文字内容自适应
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                // 统一规划标签之间的间距和边缘间距
                // 每行第一个标签左边距为0，最后一个标签右边距为0，中间标签左右各6dp
                params.marginStart = if (index % itemsPerRow == 0) 0 else dpToPx(6)
                params.marginEnd = if (index % itemsPerRow == itemsPerRow - 1) 0 else dpToPx(6)
                // 上下间距统一为6dp
                params.topMargin = dpToPx(6)
                params.bottomMargin = dpToPx(6)
                layoutParams = params
                // 长按和拖动事件：编辑模式下长按拖动
                if (isMyChannels && isManageDialogEditMode) {
                    var startY = 0f
                    var startX = 0f
                    var viewStartY = 0f
                    var viewStartX = 0f
                    var originalPosition = tabList.indexOfFirst { it.name == tab.name }
                    var hasMoved = false
                    var dragThreshold = dpToPx(8).toFloat() // 降低拖动阈值，更容易触发拖动
                    var startTime = 0L // 记录按下时间，用于区分长按和点击
                    var currentTargetIndex = -1 // 当前目标位置
                    
                    setOnTouchListener { view, event ->
                        when (event.action) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                startY = event.rawY
                                startX = event.rawX
                                viewStartY = view.y
                                viewStartX = view.x
                                startTime = System.currentTimeMillis()
                                originalPosition = tabList.indexOfFirst { it.name == tab.name }
                                hasMoved = false
                                currentTargetIndex = originalPosition
                                
                                // 请求所有父视图不要拦截触摸事件，以便处理拖动
                                var parentView = view.parent
                                while (parentView != null && parentView is ViewGroup) {
                                    (parentView as ViewGroup).requestDisallowInterceptTouchEvent(true)
                                    parentView = parentView.parent
                                }
                                // 返回 true 表示我们要处理这个事件，防止触发系统默认行为
                                true
                            }
                            android.view.MotionEvent.ACTION_MOVE -> {
                                // 继续请求父视图不拦截，确保拖动时事件不被拦截
                                var parentView = view.parent
                                while (parentView != null && parentView is ViewGroup) {
                                    (parentView as ViewGroup).requestDisallowInterceptTouchEvent(true)
                                    parentView = parentView.parent
                                }
                                
                                val deltaY = event.rawY - startY
                                val deltaX = event.rawX - startX
                                val distance = kotlin.math.sqrt(deltaX * deltaX + deltaY * deltaY)
                                val elapsedTime = System.currentTimeMillis() - startTime
                                
                                    // 如果移动距离超过阈值，或者长按时间超过200ms，开始拖动
                                if (distance > dragThreshold || (elapsedTime > 200 && !isDragging)) {
                                    hasMoved = true
                                    if (!isDragging) {
                                        // 开始拖动：放大标签并提升高度
                                        isDragging = true
                                        draggedTabView = this@apply
                                        draggedTabIndex = originalPosition
                                        dragStartY = startY
                                        dragStartX = startX
                                        
                                        // 保存所有标签的原始位置（基于屏幕坐标）
                                        tabOriginalPositions.clear()
                                        myChannelsTabViews.forEach { tabView ->
                                            val location = IntArray(2)
                                            tabView.getLocationOnScreen(location)
                                            tabOriginalPositions[tabView] = Pair(location[0].toFloat(), location[1].toFloat())
                                        }
                                        
                                        // 将被拖动的标签移到最上层
                                        (view.parent as? ViewGroup)?.let { parent ->
                                            parent.bringChildToFront(view)
                                        }
                                        
                                        // 拖动时：轻微放大并提升，模拟实体被拿起的效果
                                        view.animate()
                                            .scaleX(1.1f)
                                            .scaleY(1.1f)
                                            .alpha(0.98f) // 保持高透明度，显示实体感
                                            .setDuration(200)
                                            .setInterpolator(android.view.animation.OvershootInterpolator(1.3f))
                                            .start()
                                        
                                        // 设置标签为拖动状态：使用拟态化按压效果，模拟实体被按压
                                        val isDarkMode = (context.resources.configuration.uiMode and 
                                            android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
                                            android.content.res.Configuration.UI_MODE_NIGHT_YES
                                        this@apply.background = createNeumorphicPressedDrawable(isDarkMode)
                                        this@apply.setTextColor(if (isDarkMode) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
                                        // 提升高度，模拟实体被拿起的效果
                                        this@apply.elevation = dpToPx(12).toFloat()
                                    }
                                    
                                    if (isDragging && draggedTabView == this@apply) {
                                        // 让标签跟随手指移动
                                        // 计算手指相对于标签原始位置的偏移
                                        val deltaX = event.rawX - startX
                                        val deltaY = event.rawY - startY
                                        
                                        // 计算标签应该移动到的位置（相对于原始位置）
                                        view.translationX = deltaX
                                        view.translationY = deltaY
                                        
                                        // 计算目标位置（基于触摸的X和Y坐标）
                                        val currentPosition = tabList.indexOfFirst { it.name == tab.name }
                                        val targetIndex = findTargetIndexForDrag(event.rawX, event.rawY, currentPosition, gridContainer, itemsPerRow)
                                        
                                        // 如果目标位置改变，更新标签位置
                                        if (targetIndex != currentTargetIndex && targetIndex >= 0 && targetIndex < tabList.size && targetIndex != originalPosition) {
                                            currentTargetIndex = targetIndex
                                            animateTabsToNewPosition(originalPosition, targetIndex)
                                        }
                                        
                                        true
                                    } else {
                                        true
                                    }
                                } else {
                                    // 移动距离不够，但继续处理事件，防止触发其他行为
                                    true
                                }
                            }
                            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                                // 允许父视图拦截触摸事件
                                var parentView = view.parent
                                while (parentView != null && parentView is ViewGroup) {
                                    (parentView as ViewGroup).requestDisallowInterceptTouchEvent(false)
                                    parentView = parentView.parent
                                }
                                
                                val elapsedTime = System.currentTimeMillis() - startTime
                                
                                if (isDragging && draggedTabView == this@apply) {
                                    // 拖动结束：恢复标签大小和位置
                                    val finalPosition = if (currentTargetIndex >= 0 && currentTargetIndex < tabList.size && currentTargetIndex != originalPosition) {
                                        currentTargetIndex
                                    } else {
                                        originalPosition
                                    }
                                    
                                    // 更新tabList中的顺序
                                    if (finalPosition != originalPosition) {
                                        val movedTab = tabList[originalPosition]
                                        tabList.removeAt(originalPosition)
                                        tabList.add(finalPosition, movedTab)
                                        
                                        // 更新选中索引
                                        if (selectedTabIndex == originalPosition) {
                                            selectedTabIndex = finalPosition
                                        } else if (selectedTabIndex > originalPosition && selectedTabIndex <= finalPosition) {
                                            selectedTabIndex = selectedTabIndex - 1
                                        } else if (selectedTabIndex < originalPosition && selectedTabIndex >= finalPosition) {
                                            selectedTabIndex = selectedTabIndex + 1
                                        }
                                        
                                        // 保存数据
                                        saveTabs()
                                    }
                                    
                                    // 先重置拖动状态，避免在动画过程中再次触发拖动
                                    isDragging = false
                                    draggedTabView = null
                                    draggedTabIndex = -1
                                    currentTargetIndex = -1
                                    
                                    // 清理原始位置记录
                                    tabOriginalPositions.clear()
                                    
                                    // 恢复所有标签的 translation 和 scale（重置位置和大小）
                                    // 使用弹性动画，模拟物理回弹效果
                                    myChannelsTabViews.forEachIndexed { index, tabView ->
                                        if (tabView != view) {
                                            tabView.animate()
                                                .translationX(0f)
                                                .translationY(0f)
                                                .scaleX(1.0f)
                                                .scaleY(1.0f)
                                                .setStartDelay((index * 10).toLong()) // 添加轻微延迟，模拟连锁反应
                                                .setDuration(200)
                                                .setInterpolator(android.view.animation.OvershootInterpolator(1.2f))
                                                .start()
                                        }
                                    }
                                    
                                    // 恢复被拖动标签的大小、位置和透明度
                                    // 使用弹性动画，模拟实体放置效果
                                    val isDarkMode = (context.resources.configuration.uiMode and 
                                        android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
                                        android.content.res.Configuration.UI_MODE_NIGHT_YES
                                    
                                    view.animate()
                                        .scaleX(1.0f)
                                        .scaleY(1.0f)
                                        .alpha(1.0f) // 恢复透明度
                                        .translationX(0f)
                                        .translationY(0f)
                                        .setDuration(250)
                                        .setInterpolator(android.view.animation.OvershootInterpolator(1.3f))
                                        .withStartAction {
                                            // 恢复拟态化背景
                                            this@apply.background = createNeumorphicDrawable(false, isDarkMode)
                                            this@apply.elevation = dpToPx(2).toFloat()
                                        }
                                        .withEndAction {
                                            // 动画结束后刷新对话框，确保所有标签正确显示
                                            refreshTabs()
                                            refreshManageDialog()
                                        }
                                        .start()
                                    
                                    true
                                } else if (!hasMoved && elapsedTime < 500) {
                                    // 没有拖动且时间较短，触发点击事件（将标签移入推荐频道）
                                    // 如果长按时间超过500ms，不触发点击，避免误操作
                                    handleTabClick(tab, isMyChannels)
                                    true
                                } else {
                                    // 长按但没有拖动，或者已经移动过，不触发任何操作
                                    true
                                }
                            }
                            else -> true
                        }
                    }
                } else {
                    // 非编辑模式或推荐频道，使用普通点击
                    setOnClickListener {
                        handleTabClick(tab, isMyChannels)
                    }
                }
            }
            
            currentRow?.addView(tabButton)
            
            // 如果是"我的频道"，保存标签视图引用
            if (isMyChannels) {
                myChannelsTabViews.add(tabButton)
            }
        }
        
        return gridContainer
    }
    
    /**
     * 处理标签点击事件
     */
    private fun handleTabClick(tab: TabItem, isMyChannels: Boolean) {
        if (isMyChannels) {
            if (isManageDialogEditMode) {
                // 编辑模式下，单击将标签移入推荐频道
                val position = tabList.indexOfFirst { it.name == tab.name }
                if (position >= 0) {
                    tabList.removeAt(position)
                    if (selectedTabIndex >= tabList.size) {
                        selectedTabIndex = tabList.size - 1
                    }
                    refreshTabs()
                    saveTabs()
                    // 直接刷新对话框，不关闭
                    refreshManageDialog()
                }
            }
            // 非编辑模式下，单击不做任何操作
        } else {
            // 添加到我的频道，默认置于最前面
            if (!tabList.any { it.name == tab.name }) {
                tabList.add(0, tab) // 添加到最前面
                refreshTabs()
                saveTabs()
                // 直接刷新对话框，不关闭
                refreshManageDialog()
            }
        }
    }
    
    /**
     * 动画更新标签位置，让其他标签动态让位
     * 使用保存的原始位置计算目标位置，添加物理效果（弹性、碰撞）
     */
    private fun animateTabsToNewPosition(fromIndex: Int, toIndex: Int) {
        if (fromIndex == toIndex || fromIndex < 0 || toIndex < 0 || 
            fromIndex >= myChannelsTabViews.size || toIndex >= myChannelsTabViews.size) {
            return
        }
        
        // 先取消所有受影响标签的动画，确保从当前位置开始新动画
        val affectedIndices = if (fromIndex < toIndex) {
            (fromIndex + 1..toIndex).toList()
        } else {
            (toIndex until fromIndex).toList()
        }
        
        // 为每个受影响的标签计算目标位置，添加物理效果
        affectedIndices.forEachIndexed { order, currentIndex ->
            val targetIndex = if (fromIndex < toIndex) currentIndex - 1 else currentIndex + 1
            
            // 使用索引直接获取视图（myChannelsTabViews的顺序与tabList一致）
            val currentView = myChannelsTabViews.getOrNull(currentIndex) ?: return@forEachIndexed
            val targetView = myChannelsTabViews.getOrNull(targetIndex) ?: return@forEachIndexed
            
            // 取消之前的动画，立即停止当前动画
            currentView.animate().cancel()
            currentView.clearAnimation()
            
            // 获取原始位置（拖动开始时保存的位置）
            val currentOriginalPos = tabOriginalPositions[currentView]
            val targetOriginalPos = tabOriginalPositions[targetView]
            
            if (currentOriginalPos == null || targetOriginalPos == null) {
                // 如果没有原始位置记录，使用当前位置计算
                val currentLocation = IntArray(2)
                currentView.getLocationOnScreen(currentLocation)
                val currentActualX = currentLocation[0].toFloat() + currentView.translationX
                val currentActualY = currentLocation[1].toFloat() + currentView.translationY
                
                val targetLocation = IntArray(2)
                targetView.getLocationOnScreen(targetLocation)
                val targetActualX = targetLocation[0].toFloat() + targetView.translationX
                val targetActualY = targetLocation[1].toFloat() + targetView.translationY
                
                val deltaX = targetActualX - currentActualX
                val deltaY = targetActualY - currentActualY
                
                val targetTranslationX = currentView.translationX + deltaX
                val targetTranslationY = currentView.translationY + deltaY
                
                // 使用弹性插值器，模拟物理碰撞效果
                // 根据顺序添加延迟，模拟连锁反应
                val delay = (order * 20).toLong()
                currentView.animate()
                    .translationX(targetTranslationX)
                    .translationY(targetTranslationY)
                    .setStartDelay(delay)
                    .setDuration(250)
                    .setInterpolator(android.view.animation.OvershootInterpolator(1.5f))
                    .start()
            } else {
                // 使用原始位置计算目标translation
                val deltaX = targetOriginalPos.first - currentOriginalPos.first
                val deltaY = targetOriginalPos.second - currentOriginalPos.second
                
                // 添加轻微的缩放效果，模拟碰撞时的挤压
                val scale = 0.95f
                
                // 根据顺序添加延迟，模拟连锁反应（物理碰撞传播）
                val delay = (order * 20).toLong()
                
                // 使用弹性动画，模拟物理效果
                currentView.animate()
                    .translationX(deltaX)
                    .translationY(deltaY)
                    .scaleX(scale)
                    .scaleY(scale)
                    .setStartDelay(delay)
                    .setDuration(250)
                    .setInterpolator(android.view.animation.OvershootInterpolator(1.5f))
                    .withEndAction {
                        // 恢复原始大小
                        currentView.animate()
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(100)
                            .setInterpolator(android.view.animation.DecelerateInterpolator())
                            .start()
                    }
                    .start()
            }
        }
    }
    
    /**
     * 根据触摸位置找到目标索引（用于拖拽）
     * 同时考虑X和Y坐标，基于网格布局准确计算目标位置
     */
    private fun findTargetIndexForDrag(rawX: Float, rawY: Float, currentIndex: Int, gridContainer: LinearLayout, itemsPerRow: Int): Int {
        var itemCount = 0
        var minDistance = Float.MAX_VALUE
        var closestIndex = currentIndex
        
        // 遍历所有行和列，找到触摸位置最接近的标签
        for (i in 0 until gridContainer.childCount) {
            val row = gridContainer.getChildAt(i) as? LinearLayout ?: continue
            for (j in 0 until row.childCount) {
                val tabView = row.getChildAt(j) as? TextView ?: continue
                
                // 只处理"我的频道"中的标签（数量应该等于tabList.size）
                if (itemCount >= tabList.size) {
                    break
                }
                
                // 跳过正在拖动的标签本身
                if (itemCount == currentIndex) {
                    itemCount++
                    continue
                }
                
                val location = IntArray(2)
                tabView.getLocationOnScreen(location)
                val viewX = location[0].toFloat()
                val viewY = location[1].toFloat()
                val viewWidth = tabView.width
                val viewHeight = tabView.height
                
                // 计算标签中心点
                val viewCenterX = viewX + viewWidth / 2f
                val viewCenterY = viewY + viewHeight / 2f
                
                // 计算触摸位置到标签中心的欧几里得距离
                val deltaX = rawX - viewCenterX
                val deltaY = rawY - viewCenterY
                val distance = kotlin.math.sqrt(deltaX * deltaX + deltaY * deltaY)
                
                // 检查触摸位置是否在标签的扩展范围内（增加容错区域）
                val expandX = viewWidth * 0.6f // 扩展60%的宽度范围
                val expandY = viewHeight * 0.6f // 扩展60%的高度范围
                val isInRange = rawX >= viewX - expandX && rawX <= viewX + viewWidth + expandX &&
                               rawY >= viewY - expandY && rawY <= viewY + viewHeight + expandY
                
                if (isInRange && distance < minDistance) {
                    minDistance = distance
                    closestIndex = itemCount
                }
                
                itemCount++
            }
            if (itemCount >= tabList.size) {
                break
            }
        }
        
        // 确保返回的索引在有效范围内，且不等于当前索引
        return if (closestIndex >= 0 && closestIndex < tabList.size && closestIndex != currentIndex) {
            closestIndex
        } else {
            currentIndex
        }
    }
    
    /**
     * 显示添加标签对话框
     */
    private fun showAddTabDialog() {
        val input = EditText(context).apply {
            hint = "输入标签名称"
            setSingleLine(true)
        }
        
        // 在 Service 上下文中必须使用 AppCompat 主题
        val dialogTheme = androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert
        val themedContext = ContextThemeWrapper(context, dialogTheme)
        val builder = AlertDialog.Builder(themedContext)
            .setTitle("添加标签")
            .setView(input)
            .setPositiveButton("添加") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    if (addTab(name)) {
                        Toast.makeText(context, "标签已添加", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "标签已存在", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
        
        val dialog = builder.create()
        if (context is android.app.Service || context.javaClass.name.contains("Service")) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        }
        dialog.show()
    }
    
    /**
     * 显示标签编辑对话框
     */
    private fun showTabEditDialog(tab: TabItem, position: Int) {
        val options = mutableListOf<String>()
        if (tab.isCustom) {
            options.add("重命名")
            options.add("配置搜索引擎")
            options.add("删除")
        } else {
            options.add("配置搜索引擎")
        }
        if (position > 0) {
            options.add("向左移动")
        }
        if (position < tabList.size - 1) {
            options.add("向右移动")
        }
        
        // 在 Service 上下文中必须使用 AppCompat 主题
        val dialogTheme = androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert
        val themedContext = ContextThemeWrapper(context, dialogTheme)
        
        // 使用自定义视图来避免资源加载问题
        var dialog: AlertDialog? = null
        val listView = android.widget.ListView(themedContext).apply {
            adapter = android.widget.ArrayAdapter(themedContext, android.R.layout.simple_list_item_1, options)
            setOnItemClickListener { _, _, which, _ ->
                when {
                    options[which] == "重命名" -> {
                        dialog?.dismiss()
                        showRenameTabDialog(tab, position)
                    }
                    options[which] == "配置搜索引擎" -> {
                        dialog?.dismiss()
                        showEngineConfigDialog(tab, position)
                    }
                    options[which] == "删除" -> {
                        dialog?.dismiss()
                        showDeleteTabDialog(tab, position)
                    }
                    options[which] == "向左移动" -> {
                        if (position > 0) {
                            moveTab(position, position - 1)
                            Toast.makeText(context, "已向左移动", Toast.LENGTH_SHORT).show()
                        }
                        dialog?.dismiss()
                    }
                    options[which] == "向右移动" -> {
                        if (position < tabList.size - 1) {
                            moveTab(position, position + 1)
                            Toast.makeText(context, "已向右移动", Toast.LENGTH_SHORT).show()
                        }
                        dialog?.dismiss()
                    }
                }
            }
        }
        
        val builder = AlertDialog.Builder(themedContext)
            .setTitle("编辑标签: ${tab.name}")
            .setView(listView)
            .setNegativeButton("取消", null)
        
        dialog = builder.create()
        if (context is android.app.Service || context.javaClass.name.contains("Service")) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        }
        dialog.show()
    }
    
    /**
     * 显示重命名对话框
     */
    private fun showRenameTabDialog(tab: TabItem, position: Int) {
        val input = EditText(context).apply {
            setText(tab.name)
            hint = "输入新名称"
            setSingleLine(true)
            setSelection(text.length)
        }
        
        // 在 Service 上下文中必须使用 AppCompat 主题
        val dialogTheme = androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert
        val themedContext = ContextThemeWrapper(context, dialogTheme)
        val builder = AlertDialog.Builder(themedContext)
            .setTitle("重命名标签")
            .setView(input)
            .setPositiveButton("保存") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty() && newName != tab.name) {
                    if (tabList.any { it.name == newName && it != tab }) {
                        Toast.makeText(context, "标签名称已存在", Toast.LENGTH_SHORT).show()
                    } else {
                        val updatedTab = tab.copy(name = newName)
                        tabList[position] = updatedTab
                        refreshTabs()
                        saveTabs()
                        Toast.makeText(context, "标签已重命名", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("取消", null)
        
        val dialog = builder.create()
        if (context is android.app.Service || context.javaClass.name.contains("Service")) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        }
        dialog.show()
    }
    
    /**
     * 显示搜索引擎配置对话框
     */
    private fun showEngineConfigDialog(tab: TabItem, position: Int) {
        val availableEngines = SearchEngine.DEFAULT_ENGINES.map { it.displayName }
        val currentEngines = tab.engineKeys.mapNotNull { key ->
            SearchEngine.DEFAULT_ENGINES.find { it.name == key }?.displayName
        }
        val checkedItems = BooleanArray(availableEngines.size) { index ->
            currentEngines.contains(availableEngines[index])
        }
        
        val dialogTheme = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            android.R.style.Theme_Material_Dialog_Alert
        } else {
            androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert
        }
        
        val themedContext = ContextThemeWrapper(context, dialogTheme)
        val builder = AlertDialog.Builder(themedContext)
            .setTitle("为「${tab.name}」配置搜索引擎")
            .setMultiChoiceItems(availableEngines.toTypedArray(), checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("保存") { _, _ ->
                val selectedEngines = mutableListOf<String>()
                checkedItems.forEachIndexed { index, isChecked ->
                    if (isChecked) {
                        val engine = SearchEngine.DEFAULT_ENGINES[index]
                        selectedEngines.add(engine.name)
                    }
                }
                
                if (selectedEngines.isEmpty()) {
                    Toast.makeText(context, "请至少选择一个搜索引擎", Toast.LENGTH_SHORT).show()
                } else {
                    val updatedTab = tab.copy(engineKeys = selectedEngines)
                    tabList[position] = updatedTab
                    refreshTabs()
                    saveTabs()
                    Toast.makeText(context, "搜索引擎已配置", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
        
        val dialog = builder.create()
        if (context is android.app.Service || context.javaClass.name.contains("Service")) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        }
        dialog.show()
    }
    
    /**
     * 显示删除标签对话框
     */
    private fun showDeleteTabDialog(tab: TabItem, position: Int) {
        // 在 Service 上下文中必须使用 AppCompat 主题
        val dialogTheme = androidx.appcompat.R.style.Theme_AppCompat_Dialog_Alert
        
        val themedContext = ContextThemeWrapper(context, dialogTheme)
        val builder = AlertDialog.Builder(themedContext)
            .setTitle("删除标签")
            .setMessage("确定要删除标签「${tab.name}」吗？")
            .setPositiveButton("删除") { _, _ ->
                if (removeTab(position)) {
                    Toast.makeText(context, "标签已删除", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "无法删除默认标签", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消", null)
        
        val dialog = builder.create()
        if (context is android.app.Service || context.javaClass.name.contains("Service")) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        }
        dialog.show()
    }
    
    /**
     * 更新标签样式（编辑模式下显示删除标记）
     */
    private fun updateTabStyleForEditMode(tabView: TextView, tab: TabItem, position: Int) {
        if (isEditMode && tab.isCustom) {
            // 在自定义标签上显示删除标记
            tabView.text = "${tab.name} ✕"
        }
    }

    /**
     * 保存标签
     */
    private fun saveTabs() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val tabsJson = Gson().toJson(tabList)
        prefs.edit().putString(KEY_TABS, tabsJson).apply()
    }

    /**
     * 销毁
     */
    fun destroy() {
        container.removeView(scrollView)
        scrollView = null
        tabContainer = null
        tabList.clear()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
    
    /**
     * 创建拟态化背景（Neumorphism风格）
     * 使用阴影和光照效果模拟实体感
     */
    private fun createNeumorphicDrawable(
        isPressed: Boolean = false,
        isDarkMode: Boolean = false
    ): android.graphics.drawable.Drawable {
        val cornerRadius = dpToPx(8).toFloat()
        
        // 基础颜色
        val baseColor = if (isDarkMode) 0xFF2C2C2C.toInt() else 0xFFF5F5F5.toInt()
        
        // 创建自定义绘制器，实现真实的拟态化效果
        return object : android.graphics.drawable.Drawable() {
            private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            private val shadowPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            private val highlightPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            
            init {
                // 主体画笔
                paint.color = baseColor
                paint.style = android.graphics.Paint.Style.FILL
                
                // 阴影画笔（暗色，左下）
                shadowPaint.color = if (isDarkMode) 0x40000000 else 0x40000000
                shadowPaint.maskFilter = android.graphics.BlurMaskFilter(
                    dpToPx(4).toFloat(),
                    android.graphics.BlurMaskFilter.Blur.NORMAL
                )
                shadowPaint.style = android.graphics.Paint.Style.FILL
                
                // 高光画笔（亮色，右上）
                highlightPaint.color = if (isDarkMode) 0x30FFFFFF else 0x60FFFFFF
                highlightPaint.maskFilter = android.graphics.BlurMaskFilter(
                    dpToPx(3).toFloat(),
                    android.graphics.BlurMaskFilter.Blur.NORMAL
                )
                highlightPaint.style = android.graphics.Paint.Style.FILL
            }
            
            override fun draw(canvas: android.graphics.Canvas) {
                val bounds = bounds
                val rect = android.graphics.RectF(
                    bounds.left.toFloat(),
                    bounds.top.toFloat(),
                    bounds.right.toFloat(),
                    bounds.bottom.toFloat()
                )
                
                // 绘制阴影（左下）
                val shadowRect = android.graphics.RectF(
                    rect.left + dpToPx(2).toFloat(),
                    rect.top + dpToPx(2).toFloat(),
                    rect.right + dpToPx(2).toFloat(),
                    rect.bottom + dpToPx(2).toFloat()
                )
                canvas.drawRoundRect(shadowRect, cornerRadius, cornerRadius, shadowPaint)
                
                // 绘制高光（右上）
                val highlightRect = android.graphics.RectF(
                    rect.left - dpToPx(1).toFloat(),
                    rect.top - dpToPx(1).toFloat(),
                    rect.right - dpToPx(1).toFloat(),
                    rect.bottom - dpToPx(1).toFloat()
                )
                canvas.drawRoundRect(highlightRect, cornerRadius, cornerRadius, highlightPaint)
                
                // 绘制主体
                canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
            }
            
            override fun setAlpha(alpha: Int) {
                paint.alpha = alpha
                shadowPaint.alpha = alpha
                highlightPaint.alpha = alpha
            }
            
            override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
                paint.colorFilter = colorFilter
            }
            
            override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
        }
    }
    
    /**
     * 创建拟态化按压效果背景
     */
    private fun createNeumorphicPressedDrawable(isDarkMode: Boolean = false): android.graphics.drawable.Drawable {
        val cornerRadiusValue = dpToPx(8).toFloat()
        
        // 按压时使用内凹效果
        val baseColor = if (isDarkMode) 0xFF1E1E1E.toInt() else 0xFFE8E8E8.toInt()
        
        return android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = cornerRadiusValue
            setColor(baseColor)
            // 添加内阴影效果
            setStroke(dpToPx(1), if (isDarkMode) 0xFF0A0A0A.toInt() else 0xFFD0D0D0.toInt())
        }
    }
}

