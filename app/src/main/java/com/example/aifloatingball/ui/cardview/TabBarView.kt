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
    private var isDragging = false
    
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
        
        if (tabsJson != null) {
            try {
                val type = object : TypeToken<List<TabItem>>() {}.type
                val savedTabs: List<TabItem> = Gson().fromJson(tabsJson, type)
                tabList.clear()
                tabList.addAll(savedTabs)
            } catch (e: Exception) {
                Log.e(TAG, "加载标签失败", e)
                initDefaultTabs()
            }
        } else {
            initDefaultTabs()
        }
        
        refreshTabs()
    }

    /**
     * 初始化默认标签
     */
    private fun initDefaultTabs() {
        tabList.clear()
        DEFAULT_TABS.forEach { name ->
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
                // 不设置最小和最大宽度，让文字自然显示
                // 使用 WRAP_CONTENT 让标签根据文字内容自适应宽度
                // 根据暗色/亮色模式和编辑模式设置背景和文字颜色
                // 编辑模式下使用镂空淡绿色勾边，非编辑模式使用普通背景
                val drawable = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = dpToPx(8).toFloat()
                    if (isEditMode && isMyChannels) {
                        // 编辑模式下：镂空淡绿色勾边
                        setColor(android.graphics.Color.TRANSPARENT) // 透明背景
                        setStroke(dpToPx(2), 0xFF4CAF50.toInt()) // 淡绿色勾边
                    } else {
                        // 非编辑模式：普通背景
                        val bgColor = if (isDarkMode) 0xFF2C2C2C.toInt() else 0xFFF5F5F5.toInt()
                        setColor(bgColor)
                    }
                }
                background = drawable
                
                val textColor = when {
                    isEditMode && isMyChannels -> 0xFF4CAF50.toInt() // 编辑模式下淡绿色文字
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
                setOnClickListener {
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
                
                // 长按和拖动事件：编辑模式下长按拖动
                if (isMyChannels && isManageDialogEditMode) {
                    var startY = 0f
                    var startX = 0f
                    var originalIndex = index
                    
                    setOnTouchListener { view, event ->
                        when (event.action) {
                            android.view.MotionEvent.ACTION_DOWN -> {
                                startY = event.rawY
                                startX = event.rawX
                                originalIndex = index
                                true
                            }
                            android.view.MotionEvent.ACTION_MOVE -> {
                                val deltaY = event.rawY - startY
                                val deltaX = event.rawX - startX
                                val distance = kotlin.math.sqrt(deltaX * deltaX + deltaY * deltaY)
                                
                                // 如果移动距离超过阈值，开始拖动
                                if (distance > dpToPx(10) && !isDragging) {
                                    isDragging = true
                                    draggedTabView = this@apply
                                    draggedTabIndex = originalIndex
                                    dragStartY = startY
                                    
                                    // 设置标签为拖动状态（改变颜色）
                                    val dragDrawable = android.graphics.drawable.GradientDrawable().apply {
                                        cornerRadius = dpToPx(8).toFloat()
                                        setColor(0xFFFF9800.toInt()) // 橙色表示拖动状态
                                    }
                                    this@apply.background = dragDrawable
                                    this@apply.setTextColor(0xFFFFFFFF.toInt())
                                    this@apply.elevation = dpToPx(8).toFloat() // 提升高度，显示拖动状态
                                }
                                
                                if (isDragging && draggedTabView == this@apply) {
                                    // 计算目标位置（基于触摸的Y坐标）
                                    val targetIndex = findTargetIndexForDrag(event.rawY, originalIndex, gridContainer, itemsPerRow)
                                    if (targetIndex != originalIndex && targetIndex >= 0 && targetIndex < tabList.size) {
                                        // 更新tabList中的顺序
                                        val fromPosition = originalIndex
                                        val toPosition = targetIndex
                                        
                                        if (fromPosition != toPosition) {
                                            val movedTab = tabList[fromPosition]
                                            tabList.removeAt(fromPosition)
                                            tabList.add(toPosition, movedTab)
                                            
                                            // 更新选中索引
                                            if (selectedTabIndex == fromPosition) {
                                                selectedTabIndex = toPosition
                                            } else if (selectedTabIndex > fromPosition && selectedTabIndex <= toPosition) {
                                                selectedTabIndex = selectedTabIndex - 1
                                            } else if (selectedTabIndex < fromPosition && selectedTabIndex >= toPosition) {
                                                selectedTabIndex = selectedTabIndex + 1
                                            }
                                            
                                            // 刷新对话框以显示新位置（延迟刷新，避免频繁刷新）
                                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                                refreshManageDialog()
                                            }, 50)
                                            originalIndex = toPosition
                                            draggedTabIndex = toPosition
                                        }
                                    }
                                    true
                                } else {
                                    false
                                }
                            }
                            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                                if (isDragging && draggedTabView == this@apply) {
                                    // 拖动结束，保存更改
                                    refreshTabs()
                                    saveTabs()
                                    
                                    // 重置拖动状态
                                    isDragging = false
                                    draggedTabView = null
                                    draggedTabIndex = -1
                                    
                                    // 刷新对话框以恢复正常样式
                                    refreshManageDialog()
                                }
                                false
                            }
                            else -> false
                        }
                    }
                } else {
                    setOnLongClickListener {
                        if (isMyChannels && isManageDialogEditMode) {
                            // 非编辑模式或非我的频道，不做处理
                        }
                        true
                    }
                }
            }
            
            currentRow?.addView(tabButton)
        }
        
        return gridContainer
    }
    
    /**
     * 根据触摸位置找到目标索引（用于拖拽）
     */
    private fun findTargetIndexForDrag(rawY: Float, currentIndex: Int, gridContainer: LinearLayout, itemsPerRow: Int): Int {
        // 遍历所有行，找到触摸位置对应的标签
        var itemCount = 0
        for (i in 0 until gridContainer.childCount) {
            val row = gridContainer.getChildAt(i) as? LinearLayout ?: continue
            for (j in 0 until row.childCount) {
                val tabView = row.getChildAt(j) as? TextView ?: continue
                val location = IntArray(2)
                tabView.getLocationOnScreen(location)
                val viewY = location[1]
                val viewHeight = tabView.height
                
                // 检查触摸位置是否在这个标签的范围内
                if (rawY >= viewY && rawY <= viewY + viewHeight) {
                    if (itemCount >= 0 && itemCount < tabList.size) {
                        return itemCount
                    }
                }
                itemCount++
            }
        }
        
        return currentIndex
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
}

