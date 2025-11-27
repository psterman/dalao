package com.example.aifloatingball.reader

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.aifloatingball.R
import com.google.gson.Gson

/**
 * 阅读模式2设置数据模型
 */
data class ReaderMode2Settings(
    var fontSize: Int = 18, // 字体大小（sp）
    var lineHeight: Float = 1.6f, // 行距
    var fontFamily: String = "sans-serif", // 字体家族：sans-serif, serif, monospace
    var backgroundColor: String = "#F5F5DC", // 背景颜色
    var textColor: String = "#333333", // 文字颜色
    var isNightMode: Boolean = false, // 夜间模式
    var keepScreenOn: Boolean = false, // 保持屏幕常亮
    var isAutoScroll: Boolean = false, // 自动翻页（滚动）
    var autoScrollSpeed: Int = 1500, // 自动翻页速度（毫秒，值越大越慢）
    var isNoImageMode: Boolean = false // 无图模式
)

/**
 * 阅读模式2工具栏控制接口
 */
interface ReaderMode2ToolbarController {
    fun showToolbar()
    fun hideToolbar()
    fun exitReaderMode2()
}

/**
 * 小说阅读模式UI
 */
class NovelReaderUI(private val context: Context, private val container: ViewGroup) : NovelReaderManager.ReaderModeListener {

    private var readerView: View? = null
    private var titleView: TextView? = null
    private var contentView: TextView? = null
    private var scrollView: ScrollView? = null
    private var topBar: RelativeLayout? = null
    private var bottomBar: LinearLayout? = null
    private var chapterNav: LinearLayout? = null
    private var loadingView: ProgressBar? = null
    private var headerTitleView: TextView? = null // 顶部工具栏标题
    
    private var isMenuVisible = false
    private val manager = NovelReaderManager.getInstance(context)
    
    // 目录列表，用于查找章节序号
    private var catalogList: List<NovelReaderManager.CatalogItem> = emptyList()
    // 当前章节URL，用于匹配目录
    private var currentChapterUrl: String? = null
    // 当前章节标题（从内容中提取的）
    private var currentChapterTitle: String? = null
    
    // 设置相关
    private var settings: ReaderMode2Settings = ReaderMode2Settings()
    private val prefs: SharedPreferences = context.getSharedPreferences("reader_mode2_settings", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // 窗口管理器（用于保持屏幕常亮）
    private var windowManager: WindowManager? = null
    
    // 工具栏控制器（用于控制SimpleModeActivity的工具栏）
    private var toolbarController: ReaderMode2ToolbarController? = null
    
    // 自动翻页相关
    private val autoScrollHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var autoScrollRunnable: Runnable? = null
    private var isAutoScrolling = false
    
    // 无图模式相关
    private var webViewForNoImageMode: android.webkit.WebView? = null

    init {
        manager.setListener(this)
        loadSettings()
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        // 尝试获取工具栏控制器
        if (context is ReaderMode2ToolbarController) {
            toolbarController = context
        }
    }
    
    /**
     * 设置WebView引用（用于无图模式）
     */
    fun setWebView(webView: android.webkit.WebView?) {
        webViewForNoImageMode = webView
        // 如果已启用无图模式，立即应用
        if (settings.isNoImageMode && webView != null) {
            applyNoImageMode(webView)
        }
    }
    
    /**
     * 应用无图模式
     */
    private fun applyNoImageMode(webView: android.webkit.WebView) {
        // 阻止图片加载
        webView.settings.blockNetworkImage = true
        webView.settings.loadsImagesAutomatically = false
        
        // 注入JS移除图片和广告
        val noImageScript = """
            (function() {
                try {
                    // 移除所有图片
                    var images = document.querySelectorAll('img');
                    images.forEach(function(img) {
                        img.style.display = 'none';
                    });
                    
                    // 移除所有广告元素
                    var adSelectors = [
                        '[id*="ad"]', '[class*="ad"]', '[id*="ads"]', '[class*="ads"]',
                        '[id*="advertisement"]', '[class*="advertisement"]',
                        '[id*="banner"]', '[class*="banner"]',
                        '[id*="popup"]', '[class*="popup"]',
                        '[id*="sponsor"]', '[class*="sponsor"]',
                        'iframe[src*="ads"]', 'iframe[src*="doubleclick"]',
                        'iframe[src*="googlesyndication"]', 'iframe[src*="googleadservices"]',
                        '.ad', '.ads', '.advertisement', '.banner', '.popup', '.sponsor'
                    ];
                    
                    adSelectors.forEach(function(selector) {
                        try {
                            var elements = document.querySelectorAll(selector);
                            elements.forEach(function(el) {
                                el.style.display = 'none';
                            });
                        } catch(e) {}
                    });
                    
                    // 移除导航栏、侧边栏等非内容元素
                    var nonContentSelectors = [
                        'nav', 'header', 'footer', 'aside', '.sidebar', '.navigation',
                        '.menu', '.navbar', '.header', '.footer'
                    ];
                    
                    nonContentSelectors.forEach(function(selector) {
                        try {
                            var elements = document.querySelectorAll(selector);
                            elements.forEach(function(el) {
                                el.style.display = 'none';
                            });
                        } catch(e) {}
                    });
                    
                    console.log('无图模式已启用');
                } catch (e) {
                    console.error('启用无图模式失败:', e);
                }
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(noImageScript, null)
    }
    
    /**
     * 取消无图模式
     */
    private fun cancelNoImageMode(webView: android.webkit.WebView) {
        webView.settings.blockNetworkImage = false
        webView.settings.loadsImagesAutomatically = true
    }
    
    /**
     * 开始自动翻页
     */
    private fun startAutoScroll() {
        if (isAutoScrolling) return
        
        isAutoScrolling = true
        val scrollView = this.scrollView ?: return
        
        autoScrollRunnable = object : Runnable {
            override fun run() {
                if (!isAutoScrolling || scrollView == null) return
                
                val child = scrollView.getChildAt(0)
                val scrollHeight = child?.height ?: 0
                val clientHeight = scrollView.height
                val currentScrollY = scrollView.scrollY
                
                // 检查是否已经滚动到底部
                if (scrollHeight - currentScrollY - clientHeight < 50) {
                    // 滚动到底部，尝试加载下一章
                    manager.loadNextChapter()
                    // 等待新内容加载后再继续
                    autoScrollHandler.postDelayed(this, (settings.autoScrollSpeed * 2).toLong())
                } else {
                    // 继续向下滚动
                    scrollView.smoothScrollBy(0, 30) // 每次滚动30px
                    autoScrollHandler.postDelayed(this, settings.autoScrollSpeed.toLong())
                }
            }
        }
        
        autoScrollHandler.postDelayed(autoScrollRunnable!!, settings.autoScrollSpeed.toLong())
    }
    
    /**
     * 停止自动翻页
     */
    private fun stopAutoScroll() {
        isAutoScrolling = false
        autoScrollRunnable?.let { autoScrollHandler.removeCallbacks(it) }
        autoScrollRunnable = null
    }
    
    /**
     * 设置工具栏控制器
     */
    fun setToolbarController(controller: ReaderMode2ToolbarController) {
        toolbarController = controller
    }
    
    /**
     * 加载设置
     */
    private fun loadSettings() {
        val json = prefs.getString("settings", null)
        if (json != null) {
            try {
                settings = gson.fromJson(json, ReaderMode2Settings::class.java) ?: ReaderMode2Settings()
            } catch (e: Exception) {
                settings = ReaderMode2Settings()
            }
        }
        // 应用已加载的设置
        applySettings()
    }
    
    /**
     * 保存设置
     */
    private fun saveSettings() {
        val json = gson.toJson(settings)
        prefs.edit().putString("settings", json).apply()
    }

    /**
     * 显示阅读器
     */
    fun show() {
        if (readerView == null) {
            initView()
        }
        readerView?.apply {
            visibility = View.VISIBLE
            // 确保阅读模式在最上层，完全覆盖底层的地址栏和tab栏
            bringToFront()
            // 设置高Z轴值，防止底层UI抖动出现
            elevation = 100f
            translationZ = 100f
            // 请求重新布局，确保Z轴变化生效
            requestLayout()
            parent?.requestLayout()
        }
        // 初始显示时，隐藏SimpleModeActivity的工具栏
        toolbarController?.hideToolbar()
        // 禁用SimpleModeActivity的下拉刷新和下拉工具栏功能
        disableSwipeRefreshAndPullDownToolbar()
        
        // 如果启用了无图模式，应用无图模式
        if (settings.isNoImageMode) {
            val webView = manager.getCurrentWebView()
            webView?.let { applyNoImageMode(it) }
        }
        
        // 如果启用了自动翻页，开始自动翻页
        if (settings.isAutoScroll) {
            startAutoScroll()
        }
    }
    
    /**
     * 禁用SimpleModeActivity的下拉刷新和下拉工具栏功能
     */
    private fun disableSwipeRefreshAndPullDownToolbar() {
        if (context is android.app.Activity) {
            val activity = context as android.app.Activity
            try {
                // 禁用SwipeRefreshLayout
                val swipeRefresh = activity.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.browser_swipe_refresh)
                swipeRefresh?.isEnabled = false
            } catch (e: Exception) {
                android.util.Log.e("NovelReaderUI", "禁用下拉刷新失败", e)
            }
        }
    }
    
    /**
     * 隐藏阅读器
     */
    fun hide() {
        readerView?.visibility = View.GONE
        // 停止自动翻页
        stopAutoScroll()
        // 重新启用SimpleModeActivity的下拉刷新和下拉工具栏功能
        enableSwipeRefreshAndPullDownToolbar()
    }
    
    /**
     * 重新启用SimpleModeActivity的下拉刷新和下拉工具栏功能
     */
    private fun enableSwipeRefreshAndPullDownToolbar() {
        if (context is android.app.Activity) {
            val activity = context as android.app.Activity
            try {
                // 重新启用SwipeRefreshLayout
                val swipeRefresh = activity.findViewById<androidx.swiperefreshlayout.widget.SwipeRefreshLayout>(R.id.browser_swipe_refresh)
                swipeRefresh?.isEnabled = true
            } catch (e: Exception) {
                android.util.Log.e("NovelReaderUI", "启用下拉刷新失败", e)
            }
        }
    }
    
    /**
     * 退出阅读模式2
     */
    fun exitReaderMode2() {
        manager.exitReaderMode()
        toolbarController?.exitReaderMode2()
    }



    private var contentContainer: LinearLayout? = null

    private fun initView() {
        readerView = LayoutInflater.from(context).inflate(R.layout.layout_novel_reader, container, false)
        container.addView(readerView)

        scrollView = readerView?.findViewById(R.id.reader_scroll_view)
        // 🔧 确保ScrollView可以正常滚动，不被其他View拦截
        scrollView?.isNestedScrollingEnabled = false // 禁用嵌套滚动，避免与SwipeRefreshLayout冲突
        scrollView?.isFocusable = true
        scrollView?.isFocusableInTouchMode = true
        
        // 获取ScrollView内部的LinearLayout
        contentContainer = scrollView?.getChildAt(0) as? LinearLayout
        
        // 初始的标题和内容View
        titleView = readerView?.findViewById(R.id.reader_title)
        contentView = readerView?.findViewById(R.id.reader_content)
        
        topBar = readerView?.findViewById(R.id.reader_top_bar)
        bottomBar = readerView?.findViewById(R.id.reader_bottom_bar)
        chapterNav = readerView?.findViewById(R.id.reader_chapter_nav)
        loadingView = readerView?.findViewById(R.id.reader_loading)
        headerTitleView = readerView?.findViewById(R.id.reader_header_title)

        // 点击中间区域切换菜单显示
        // 需要给整个容器设置点击事件，或者给新添加的View设置
        contentContainer?.setOnClickListener {
            // 如果正在自动翻页，暂停/恢复
            if (isAutoScrolling) {
                stopAutoScroll()
                Toast.makeText(context, "自动翻页已暂停", Toast.LENGTH_SHORT).show()
            } else {
                toggleMenu()
            }
        }
        contentView?.setOnClickListener { 
            if (isAutoScrolling) {
                stopAutoScroll()
                Toast.makeText(context, "自动翻页已暂停", Toast.LENGTH_SHORT).show()
            } else {
                toggleMenu()
            }
        }
        
        // 监听滚动，用户手动滚动时暂停自动翻页
        scrollView?.setOnTouchListener { _, _ ->
            if (isAutoScrolling) {
                stopAutoScroll()
                Toast.makeText(context, "自动翻页已暂停", Toast.LENGTH_SHORT).show()
            }
            false // 不拦截触摸事件
        }
        
        // 退出按钮（阅读模式2中通过返回键退出，但保留按钮用于点击退出）
        readerView?.findViewById<ImageButton>(R.id.btn_exit_reader)?.apply {
            visibility = View.VISIBLE // 显示退出按钮
            setOnClickListener {
                exitReaderMode2()
            }
        }
        
        // 上一章/下一章按钮
        readerView?.findViewById<Button>(R.id.btn_prev_chapter)?.setOnClickListener {
            try {
                loadingView?.visibility = View.VISIBLE
                manager.loadPrevChapter()
            } catch (e: Exception) {
                android.util.Log.e("NovelReaderUI", "加载上一章失败", e)
                loadingView?.visibility = View.GONE
                Toast.makeText(context, "加载上一章失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        readerView?.findViewById<Button>(R.id.btn_next_chapter)?.setOnClickListener {
            try {
                loadingView?.visibility = View.VISIBLE
                manager.loadNextChapter()
            } catch (e: Exception) {
                android.util.Log.e("NovelReaderUI", "加载下一章失败", e)
                loadingView?.visibility = View.GONE
                Toast.makeText(context, "加载下一章失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 底部工具栏按钮
        readerView?.findViewById<TextView>(R.id.btn_settings)?.setOnClickListener {
            showSettingsDialog()
        }
        
        readerView?.findViewById<TextView>(R.id.btn_catalog)?.setOnClickListener {
            try {
                // 获取目录
                loadingView?.visibility = View.VISIBLE
                manager.fetchCatalog()
            } catch (e: Exception) {
                android.util.Log.e("NovelReaderUI", "获取目录失败", e)
                loadingView?.visibility = View.GONE
                Toast.makeText(context, "获取目录失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 夜间模式切换
        readerView?.findViewById<TextView>(R.id.btn_night_mode)?.setOnClickListener {
            toggleNightMode()
        }
        
        // 滚动监听：实现智能UI显示/隐藏 + 自动加载下一章
        setupScrollListener()
    }

    private fun toggleMenu() {
        if (isMenuVisible) {
            hideUIBars()
        } else {
            showUIBars()
        }
    }
    
    /**
     * 切换日夜间模式
     */
    private fun toggleNightMode() {
        settings.isNightMode = !settings.isNightMode
        saveSettings()
        applySettings()
    }
    
    /**
     * 应用所有设置
     */
    private fun applySettings() {
        val bgColor = if (settings.isNightMode) Color.parseColor("#1a1a1a") else Color.parseColor(settings.backgroundColor)
        val textColor = if (settings.isNightMode) Color.parseColor("#a0a0a0") else Color.parseColor(settings.textColor)
        
        // 应用到根视图背景
        readerView?.setBackgroundColor(bgColor)
        
        // 应用字体设置
        val typeface = when (settings.fontFamily) {
            "serif" -> Typeface.SERIF
            "monospace" -> Typeface.MONOSPACE
            else -> Typeface.SANS_SERIF
        }
        
        // 应用到初始的标题和内容
        titleView?.apply {
            setTextColor(textColor)
            textSize = (settings.fontSize + 6).toFloat() // 标题比正文大6sp
            setTypeface(typeface, Typeface.BOLD)
        }
        contentView?.apply {
            setTextColor(textColor)
            textSize = settings.fontSize.toFloat()
            setTypeface(typeface, Typeface.NORMAL)
            setLineSpacing(0f, settings.lineHeight)
        }
        
        // 应用到所有动态添加的章节内容
        contentContainer?.let { container ->
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                if (child is TextView) {
                    if (child.tag == "dynamic_chapter") {
                        // 动态添加的章节标题或内容
                        val isTitle = child.textSize > settings.fontSize + 2
                        child.setTextColor(textColor)
                        child.setTypeface(typeface, if (isTitle) Typeface.BOLD else Typeface.NORMAL)
                        if (!isTitle) {
                            child.setLineSpacing(0f, settings.lineHeight)
                        }
                    } else if (child.tag == "catalog_item") {
                        // 目录项
                        child.setTextColor(if (settings.isNightMode) Color.parseColor("#a0a0a0") else Color.parseColor("#007AFF"))
                    }
                }
            }
        }
        
        // 更新按钮文本
        readerView?.findViewById<TextView>(R.id.btn_night_mode)?.text = if (settings.isNightMode) "日间" else "夜间"
        
        // 应用保持屏幕常亮设置
        val window = (context as? android.app.Activity)?.window
        if (settings.keepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    
    /**
     * 显示设置对话框
     */
    private fun showSettingsDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_reader_settings, null)
        
        // 字体大小选项
        val fontSizeSmall = dialogView.findViewById<TextView>(R.id.fontSizeSmall)
        val fontSizeMedium = dialogView.findViewById<TextView>(R.id.fontSizeMedium)
        val fontSizeLarge = dialogView.findViewById<TextView>(R.id.fontSizeLarge)
        val fontSizeExtraLarge = dialogView.findViewById<TextView>(R.id.fontSizeExtraLarge)
        val fontSizePreview = dialogView.findViewById<TextView>(R.id.fontSizePreview)
        
        // 设置当前选中状态
        listOf(fontSizeSmall, fontSizeMedium, fontSizeLarge, fontSizeExtraLarge).forEach { it.isSelected = false }
        when {
            settings.fontSize <= 14 -> {
                fontSizeSmall.isSelected = true
                fontSizePreview.textSize = 14f
            }
            settings.fontSize <= 18 -> {
                fontSizeMedium.isSelected = true
                fontSizePreview.textSize = 19f
            }
            settings.fontSize <= 22 -> {
                fontSizeLarge.isSelected = true
                fontSizePreview.textSize = 24f
            }
            else -> {
                fontSizeExtraLarge.isSelected = true
                fontSizePreview.textSize = 30f
            }
        }
        
        fontSizeSmall.setOnClickListener {
            settings.fontSize = 14
            fontSizePreview.textSize = 14f
            listOf(fontSizeSmall, fontSizeMedium, fontSizeLarge, fontSizeExtraLarge).forEach { it.isSelected = false }
            fontSizeSmall.isSelected = true
            saveSettings()
            applySettings()
        }
        fontSizeMedium.setOnClickListener {
            settings.fontSize = 18
            fontSizePreview.textSize = 19f
            listOf(fontSizeSmall, fontSizeMedium, fontSizeLarge, fontSizeExtraLarge).forEach { it.isSelected = false }
            fontSizeMedium.isSelected = true
            saveSettings()
            applySettings()
        }
        fontSizeLarge.setOnClickListener {
            settings.fontSize = 22
            fontSizePreview.textSize = 24f
            listOf(fontSizeSmall, fontSizeMedium, fontSizeLarge, fontSizeExtraLarge).forEach { it.isSelected = false }
            fontSizeLarge.isSelected = true
            saveSettings()
            applySettings()
        }
        fontSizeExtraLarge.setOnClickListener {
            settings.fontSize = 26
            fontSizePreview.textSize = 30f
            listOf(fontSizeSmall, fontSizeMedium, fontSizeLarge, fontSizeExtraLarge).forEach { it.isSelected = false }
            fontSizeExtraLarge.isSelected = true
            saveSettings()
            applySettings()
        }
        
        // 字体样式选项（使用系统字体，无版权问题）
        val fontStyleDefault = dialogView.findViewById<TextView>(R.id.fontStyleDefault)
        val fontStyleSerif = dialogView.findViewById<TextView>(R.id.fontStyleSerif)
        val fontStyleMonospace = dialogView.findViewById<TextView>(R.id.fontStyleMonospace)
        
        // 设置字体预览（使用对应的系统字体）
        fontStyleDefault.typeface = Typeface.SANS_SERIF
        fontStyleSerif.typeface = Typeface.SERIF
        fontStyleMonospace.typeface = Typeface.MONOSPACE
        
        listOf(fontStyleDefault, fontStyleSerif, fontStyleMonospace).forEach { it.isSelected = false }
        when (settings.fontFamily) {
            "serif" -> fontStyleSerif.isSelected = true
            "monospace" -> fontStyleMonospace.isSelected = true
            else -> fontStyleDefault.isSelected = true
        }
        
        fontStyleDefault.setOnClickListener {
            settings.fontFamily = "sans-serif"
            listOf(fontStyleDefault, fontStyleSerif, fontStyleMonospace).forEach { it.isSelected = false }
            fontStyleDefault.isSelected = true
            saveSettings()
            applySettings()
        }
        fontStyleSerif.setOnClickListener {
            settings.fontFamily = "serif"
            listOf(fontStyleDefault, fontStyleSerif, fontStyleMonospace).forEach { it.isSelected = false }
            fontStyleSerif.isSelected = true
            saveSettings()
            applySettings()
        }
        fontStyleMonospace.setOnClickListener {
            settings.fontFamily = "monospace"
            listOf(fontStyleDefault, fontStyleSerif, fontStyleMonospace).forEach { it.isSelected = false }
            fontStyleMonospace.isSelected = true
            saveSettings()
            applySettings()
        }
        
        // 其他设置
        val settingKeepScreen = dialogView.findViewById<TextView>(R.id.settingKeepScreen)
        val settingAutoMode = dialogView.findViewById<TextView>(R.id.settingAutoMode)
        
        settingKeepScreen.isSelected = settings.keepScreenOn
        settingAutoMode.isSelected = settings.isAutoScroll
        
        // 在"其他"设置区域添加无图模式选项
        val otherContainer = settingKeepScreen?.parent as? LinearLayout
        otherContainer?.let { container ->
            // 检查是否已经有无图模式选项
            var settingNoImageMode: TextView? = null
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                if (child is TextView && child.text == "无图模式") {
                    settingNoImageMode = child
                    break
                }
            }
            
            if (settingNoImageMode == null) {
                // 创建无图模式选项
                settingNoImageMode = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f
                    ).apply {
                        marginEnd = 8
                    }
                    text = "无图模式"
                    textSize = 14f
                    setTextColor(Color.parseColor("#1C1C1E"))
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                    gravity = android.view.Gravity.CENTER
                    setBackgroundResource(R.drawable.selector_setting_option)
                    isClickable = true
                    isFocusable = true
                    isSelected = settings.isNoImageMode
                    setOnClickListener {
                        settings.isNoImageMode = !settings.isNoImageMode
                        isSelected = settings.isNoImageMode
                        saveSettings()
                        // 应用无图模式
                        val webView = manager.getCurrentWebView()
                        if (webView != null) {
                            if (settings.isNoImageMode) {
                                applyNoImageMode(webView)
                                Toast.makeText(context, "无图模式已开启", Toast.LENGTH_SHORT).show()
                            } else {
                                cancelNoImageMode(webView)
                                Toast.makeText(context, "无图模式已关闭", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                // 在"保持屏幕"和"自动模式"之间插入
                val keepScreenIndex = container.indexOfChild(settingKeepScreen)
                container.addView(settingNoImageMode, keepScreenIndex + 1)
            } else {
                settingNoImageMode.isSelected = settings.isNoImageMode
                settingNoImageMode.setOnClickListener {
                    settings.isNoImageMode = !settings.isNoImageMode
                    settingNoImageMode.isSelected = settings.isNoImageMode
                    saveSettings()
                    // 应用无图模式
                    val webView = manager.getCurrentWebView()
                    if (webView != null) {
                        if (settings.isNoImageMode) {
                            applyNoImageMode(webView)
                            Toast.makeText(context, "无图模式已开启", Toast.LENGTH_SHORT).show()
                        } else {
                            cancelNoImageMode(webView)
                            Toast.makeText(context, "无图模式已关闭", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
        
        settingKeepScreen.setOnClickListener {
            settings.keepScreenOn = !settings.keepScreenOn
            settingKeepScreen.isSelected = settings.keepScreenOn
            saveSettings()
            applySettings()
        }
        
        settingAutoMode.setOnClickListener {
            settings.isAutoScroll = !settings.isAutoScroll
            settingAutoMode.isSelected = settings.isAutoScroll
            saveSettings()
            if (settings.isAutoScroll) {
                startAutoScroll()
                Toast.makeText(context, "自动翻页已开启", Toast.LENGTH_SHORT).show()
            } else {
                stopAutoScroll()
                Toast.makeText(context, "自动翻页已关闭", Toast.LENGTH_SHORT).show()
            }
        }
        
        // 翻页速度选项（阅读模式2支持自动翻页，显示速度选项）
        val speedContainer = dialogView.findViewById<LinearLayout>(R.id.speedContainer)
        val speedSlow = dialogView.findViewById<TextView>(R.id.speedSlow)
        val speedMedium = dialogView.findViewById<TextView>(R.id.speedMedium)
        val speedFast = dialogView.findViewById<TextView>(R.id.speedFast)
        
        // 设置当前选中状态
        listOf(speedSlow, speedMedium, speedFast).forEach { it.isSelected = false }
        when {
            settings.autoScrollSpeed >= 2000 -> speedSlow.isSelected = true
            settings.autoScrollSpeed >= 1000 -> speedMedium.isSelected = true
            else -> speedFast.isSelected = true
        }
        
        speedSlow.setOnClickListener {
            settings.autoScrollSpeed = 2000
            listOf(speedSlow, speedMedium, speedFast).forEach { it.isSelected = false }
            speedSlow.isSelected = true
            saveSettings()
            // 如果正在自动翻页，重启以应用新速度
            if (isAutoScrolling) {
                stopAutoScroll()
                startAutoScroll()
            }
        }
        
        speedMedium.setOnClickListener {
            settings.autoScrollSpeed = 1500
            listOf(speedSlow, speedMedium, speedFast).forEach { it.isSelected = false }
            speedMedium.isSelected = true
            saveSettings()
            if (isAutoScrolling) {
                stopAutoScroll()
                startAutoScroll()
            }
        }
        
        speedFast.setOnClickListener {
            settings.autoScrollSpeed = 500
            listOf(speedSlow, speedMedium, speedFast).forEach { it.isSelected = false }
            speedFast.isSelected = true
            saveSettings()
            if (isAutoScrolling) {
                stopAutoScroll()
                startAutoScroll()
            }
        }
        
        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .create()
        
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
    
    // 滚动控制相关变量
    private var lastScrollY = 0
    private var isAutoHiding = false // 防止重复触发隐藏/显示动画
    private val scrollThreshold = 10 // 滚动阈值，避免小幅度滑动触发UI变化
    private var lastScrollTime = 0L // 上次滚动处理时间（用于节流）
    private val scrollThrottleDelay = 50L // 节流延迟（毫秒）
    private var pendingScrollAction: Runnable? = null // 待处理的滚动动作
    private val scrollHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var lastScrollDirection = 0 // 上次滚动方向：1=向下，-1=向上，0=未确定
    private var consecutiveScrollCount = 0 // 连续同方向滚动次数
    
    /**
     * 设置滚动监听器
     * 功能：
     * 1. 向下滑动时自动隐藏顶部和底部工具栏
     * 2. 向上滑动时自动显示顶部和底部工具栏
     * 3. 滚动到底部时自动加载下一章
     * 4. 在顶部或底部时强制显示工具栏
     * 
     * 参考NovelReaderModeManager的滚动监听实现，添加节流和防抖动机制
     */
    private fun setupScrollListener() {
        scrollView?.viewTreeObserver?.addOnScrollChangedListener {
            val view = scrollView ?: return@addOnScrollChangedListener
            val currentScrollY = view.scrollY
            val currentTime = System.currentTimeMillis()
            
            // 节流：限制调用频率
            if (currentTime - lastScrollTime < scrollThrottleDelay) {
                // 取消之前的待处理动作
                pendingScrollAction?.let { scrollHandler.removeCallbacks(it) }
                // 创建新的待处理动作
                pendingScrollAction = Runnable {
                    processScrollEvent(view, currentScrollY)
                }
                scrollHandler.postDelayed(pendingScrollAction!!, scrollThrottleDelay)
                return@addOnScrollChangedListener
            }
            
            lastScrollTime = currentTime
            processScrollEvent(view, currentScrollY)
        }
    }
    
    /**
     * 处理滚动事件
     */
    private fun processScrollEvent(view: ScrollView, currentScrollY: Int) {
        val child = view.getChildAt(0)
        val scrollHeight = child?.height ?: 0
        val clientHeight = view.height
        val scrollTop = currentScrollY
        
        // 计算是否在顶部或底部（50px容差）
        val isAtTop = scrollTop < 50
        val isAtBottom = (scrollHeight - scrollTop - clientHeight) < 50
        
        // 计算滚动增量
        val scrollDelta = scrollTop - lastScrollY
        
        // 如果滚动距离太小（小于1px），更新lastScrollY后跳过处理
        if (kotlin.math.abs(scrollDelta) < 1) {
            lastScrollY = currentScrollY
            return
        }
        
        // 1. 检查是否滚动到底部，自动加载下一章（距离底部200px以内）
        if (scrollHeight - scrollTop - clientHeight < 200) {
            manager.loadNextChapter()
        }
        
        // 2. 在顶部或底部时，强制显示工具栏
        if (isAtTop || isAtBottom) {
            if (!isMenuVisible && !isAutoHiding) {
                showUIBars()
            }
            // 通知SimpleModeActivity显示工具栏
            toolbarController?.showToolbar()
            // 重置滚动方向记忆
            lastScrollDirection = 0
            consecutiveScrollCount = 0
            lastScrollY = currentScrollY
            return
        }
        
        // 3. 根据滚动方向控制UI显示/隐藏（只有在中间区域才响应）
        if (kotlin.math.abs(scrollDelta) > scrollThreshold) {
            val currentDirection = if (scrollDelta > 0) 1 else -1
            
            // 如果方向改变，重置计数
            if (currentDirection != lastScrollDirection) {
                consecutiveScrollCount = 0
                lastScrollDirection = currentDirection
            } else {
                consecutiveScrollCount++
            }
            
            // 只有在连续同方向滚动至少1次时才切换UI（减少抖动）
            if (consecutiveScrollCount >= 1) {
                when {
                    // 向下滚动，隐藏工具栏
                    currentDirection > 0 -> {
                        if (isMenuVisible && !isAutoHiding) {
                            hideUIBars()
                        }
                        // 通知SimpleModeActivity隐藏工具栏
                        toolbarController?.hideToolbar()
                    }
                    // 向上滚动，显示工具栏
                    currentDirection < 0 -> {
                        if (!isMenuVisible && !isAutoHiding) {
                            showUIBars()
                        }
                        // 通知SimpleModeActivity显示工具栏
                        toolbarController?.showToolbar()
                    }
                }
            }
        }
        
        lastScrollY = currentScrollY
    }
    
    /**
     * 隐藏顶部和底部工具栏（带动画）
     */
    private fun hideUIBars() {
        if (isAutoHiding) return
        isAutoHiding = true
        isMenuVisible = false
        
        // 使用动画隐藏顶部工具栏
        topBar?.animate()
            ?.alpha(0f)
            ?.translationY(-topBar!!.height.toFloat())
            ?.setDuration(200)
            ?.withEndAction {
                topBar?.visibility = View.GONE
                isAutoHiding = false
            }
            ?.start()
        
        // 使用动画隐藏章节导航层 - 向上滑出
        chapterNav?.animate()
            ?.alpha(0f)
            ?.translationY(-chapterNav!!.height.toFloat())  // 改为向上移动（负值）
            ?.setDuration(200)
            ?.withEndAction {
                chapterNav?.visibility = View.GONE
            }
            ?.start()
            
        // 使用动画隐藏底部工具栏
        bottomBar?.animate()
            ?.alpha(0f)
            ?.translationY(bottomBar!!.height.toFloat())
            ?.setDuration(200)
            ?.withEndAction {
                bottomBar?.visibility = View.GONE
            }
            ?.start()
    }
    
    /**
     * 显示顶部和底部工具栏（带动画）
     */
    private fun showUIBars() {
        if (isAutoHiding) return
        isAutoHiding = true
        isMenuVisible = true
        
        // 先设置为可见，然后执行动画 - 顶部工具栏
        topBar?.apply {
            visibility = View.VISIBLE
            alpha = 0f
            translationY = -height.toFloat()
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(200)
                .withEndAction {
                    isAutoHiding = false
                }
                .start()
        }
        
        // 显示章节导航层 - 从上往下滑入
        chapterNav?.apply {
            visibility = View.VISIBLE
            alpha = 0f
            translationY = -height.toFloat()  // 初始位置在上方（负值）
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(200)
                .start()
        }
        
        // 显示底部工具栏
        bottomBar?.apply {
            visibility = View.VISIBLE
            alpha = 0f
            translationY = height.toFloat()
            animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(200)
                .start()
        }
    }

    override fun onReaderModeStateChanged(isActive: Boolean) {
        if (isActive) {
            // 获取WebView引用（用于无图模式等功能）
            val webView = manager.getCurrentWebView()
            if (webView != null) {
                setWebView(webView)
            }
            show()
        } else {
            hide()
        }
    }

    override fun onChapterLoaded(title: String, content: String, hasNext: Boolean, hasPrev: Boolean, isAppend: Boolean) {
        // 在主线程更新UI
        readerView?.post {
            loadingView?.visibility = View.GONE
            
            // 保存当前章节标题
            currentChapterTitle = title
            
            // 更新顶部标题栏，显示章节序号
            updateHeaderTitle(title, content)
            
            // 如果启用了自动翻页，重新启动
            if (settings.isAutoScroll && !isAutoScrolling) {
                startAutoScroll()
            }
            
            // 如果启用了无图模式，应用无图模式
            if (settings.isNoImageMode) {
                val webView = manager.getCurrentWebView()
                webView?.let { applyNoImageMode(it) }
            }
            
            if (!isAppend) {
                // 如果不是追加，重置内容
                // 简单实现：重置初始View
                titleView?.text = title
                contentView?.text = content
                contentView?.visibility = View.VISIBLE
                
                // 移除所有动态添加的View
                // 实际上我们需要更健壮的方式。
                // 让我们重新查找初始View，并移除之后添加的章节View
                
                // 更好的方式：
                // 初始状态：
                // titleView (id: reader_title)
                // contentView (id: reader_content)
                // buttonsLayout
                
                // 移除所有动态添加的View (tag = "dynamic_chapter" 或 "catalog_item")
                contentContainer?.let { container ->
                    val viewsToRemove = ArrayList<View>()
                    for (i in 0 until container.childCount) {
                        val child = container.getChildAt(i)
                        if (child.tag == "dynamic_chapter" || child.tag == "catalog_item") {
                            viewsToRemove.add(child)
                        }
                    }
                    viewsToRemove.forEach { container.removeView(it) }
                }
                
                // 滚动到顶部
                scrollView?.scrollTo(0, 0)
                lastScrollY = 0
            } else {
                // 追加新章节
                addChapterView(title, content)
            }
            
            // 应用所有设置
            applySettings()
        }
    }
    
    /**
     * 更新顶部标题栏，显示章节序号
     */
    private fun updateHeaderTitle(chapterTitle: String, content: String? = null) {
        // 如果标题已经包含"第X章"格式，直接使用
        if (chapterTitle.contains("第") && chapterTitle.contains("章")) {
            headerTitleView?.text = chapterTitle
            return
        }
        
        // 如果标题看起来像网页标题（如"笔趣阁"），尝试从内容中提取章节标题
        var actualTitle = chapterTitle
        if (content != null && (chapterTitle.length < 10 || !chapterTitle.contains("章"))) {
            // 尝试从内容的第一行提取章节标题
            val lines = content.split("\n", "\r")
            for (i in 0 until minOf(5, lines.size)) {
                val line = lines[i].trim()
                // 检查是否是章节标题（包含"第X章"或长度较短）
                if (line.isNotEmpty() && line.length < 100 && 
                    (line.contains("第") && line.contains("章") || 
                     (line.length < 30 && i == 0))) {
                    actualTitle = line
                    break
                }
            }
        }
        
        // 如果提取到的标题已经包含"第X章"，直接使用
        if (actualTitle.contains("第") && actualTitle.contains("章")) {
            headerTitleView?.text = actualTitle
            return
        }
        
        // 如果目录为空，使用提取的标题
        if (catalogList.isEmpty()) {
            headerTitleView?.text = actualTitle
            return
        }
        
        // 在目录中查找当前章节
        val chapterIndex = catalogList.indexOfFirst { item ->
            // 尝试多种匹配方式
            item.title == actualTitle ||
            item.title.contains(actualTitle) ||
            actualTitle.contains(item.title) ||
            // 如果标题包含"第X章"格式，尝试提取并匹配
            extractChapterNumber(item.title) == extractChapterNumber(actualTitle)
        }
        
        if (chapterIndex >= 0) {
            // 找到章节，显示"第X章 标题"
            val chapterNumber = chapterIndex + 1
            val catalogTitle = catalogList[chapterIndex].title
            // 如果目录中的标题已经包含"第X章"，直接使用；否则添加序号
            if (catalogTitle.contains("第") && catalogTitle.contains("章")) {
                headerTitleView?.text = catalogTitle
            } else {
                headerTitleView?.text = "第${chapterNumber}章 $catalogTitle"
            }
        } else {
            // 没找到，使用提取的标题
            headerTitleView?.text = actualTitle
        }
    }
    
    /**
     * 从标题中提取章节序号（如果存在）
     */
    private fun extractChapterNumber(title: String): String? {
        // 匹配"第X章"、"第X节"等格式
        val pattern = Regex("第([\\d一二三四五六七八九十百千万]+)[章节回]")
        val match = pattern.find(title)
        return match?.groupValues?.get(1)
    }

    override fun onChapterLoadFailed(error: String) {
        readerView?.post {
            loadingView?.visibility = View.GONE
            Toast.makeText(context, "加载失败: $error", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCatalogLoaded(catalog: List<NovelReaderManager.CatalogItem>) {
        readerView?.post {
            loadingView?.visibility = View.GONE
            // 保存目录列表，用于查找章节序号
            catalogList = catalog
            showCatalogDialog(catalog)
        }
    }
    
    override fun onCatalogLoadFailed(error: String) {
        readerView?.post {
            loadingView?.visibility = View.GONE
            Toast.makeText(context, "获取目录失败: $error", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCatalogPageDetected(catalog: List<NovelReaderManager.CatalogItem>) {
        readerView?.post {
            loadingView?.visibility = View.GONE
            // 保存目录列表，用于查找章节序号
            catalogList = catalog
            // 目录页面：显示目录列表而不是章节内容
            showCatalogList(catalog)
        }
    }
    
    /**
     * 显示目录列表（在目录页面进入阅读模式时）
     */
    private fun showCatalogList(catalog: List<NovelReaderManager.CatalogItem>) {
        // 清空当前内容，显示目录列表
        contentContainer?.let { container ->
            // 移除所有动态添加的View（章节内容和目录项）
            val viewsToRemove = ArrayList<View>()
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                if (child.tag == "dynamic_chapter" || child.tag == "catalog_item") {
                    viewsToRemove.add(child)
                }
            }
            viewsToRemove.forEach { container.removeView(it) }
            
            // 显示标题
            titleView?.text = "章节目录 (${catalog.size}章)"
            titleView?.visibility = View.VISIBLE
            
            // 清空内容View（目录页面不需要显示正文内容）
            contentView?.text = ""
            contentView?.visibility = View.GONE
            
            // 添加目录列表
            catalog.forEachIndexed { index, item ->
                val chapterItem = TextView(context).apply {
                    tag = "catalog_item"
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        setMargins(0, 8, 0, 8)
                        topMargin = if (index == 0) 16 else 8
                    }
                    textSize = 16f
                    setTextColor(if (settings.isNightMode) Color.parseColor("#a0a0a0") else Color.parseColor("#007AFF"))
                    text = item.title
                    setPadding(16, 16, 16, 16)
                    background = context.getDrawable(android.R.drawable.list_selector_background)
                    setOnClickListener {
                        // 点击章节，跳转到阅读模式2
                        loadingView?.visibility = View.VISIBLE
                        manager.loadChapter(item.url)
                        // 清空目录列表，准备显示章节内容
                        clearCatalogList()
                    }
                }
                container.addView(chapterItem)
            }
            
            // 滚动到顶部
            scrollView?.scrollTo(0, 0)
        }
    }
    
    /**
     * 清空目录列表
     */
    private fun clearCatalogList() {
        contentContainer?.let { container ->
            val viewsToRemove = ArrayList<View>()
            for (i in 0 until container.childCount) {
                val child = container.getChildAt(i)
                if (child.tag == "catalog_item") {
                    viewsToRemove.add(child)
                }
            }
            viewsToRemove.forEach { container.removeView(it) }
        }
    }
    
    private fun showCatalogDialog(catalog: List<NovelReaderManager.CatalogItem>) {
        val titles = catalog.map { it.title }.toTypedArray()
        
        android.app.AlertDialog.Builder(context)
            .setTitle("目录 (${catalog.size}章)")
            .setItems(titles) { dialog, which ->
                val item = catalog[which]
                manager.loadChapter(item.url)
                dialog.dismiss()
                // 显示加载中
                loadingView?.visibility = View.VISIBLE
            }
            .setNegativeButton("关闭", null)
            .show()
    }
    
    private fun addChapterView(title: String, content: String) {
        val context = readerView?.context ?: return
        
        val bgColor = if (settings.isNightMode) Color.parseColor("#1a1a1a") else Color.parseColor(settings.backgroundColor)
        val textColor = if (settings.isNightMode) Color.parseColor("#a0a0a0") else Color.parseColor(settings.textColor)
        val typeface = when (settings.fontFamily) {
            "serif" -> Typeface.SERIF
            "monospace" -> Typeface.MONOSPACE
            else -> Typeface.SANS_SERIF
        }
        
        // 分割线
        val divider = View(context).apply {
            tag = "dynamic_chapter"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2 // height
            ).apply {
                setMargins(0, 48, 0, 48)
            }
            setBackgroundColor(if (settings.isNightMode) Color.DKGRAY else Color.LTGRAY)
        }
        
        // 标题
        val newTitleView = TextView(context).apply {
            tag = "dynamic_chapter"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 32 // px approx 16dp
            }
            textSize = (settings.fontSize + 6).toFloat() // 标题比正文大6sp
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(textColor)
            text = title
        }
        
        // 内容
        val newContentView = TextView(context).apply {
            tag = "dynamic_chapter"
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            textSize = settings.fontSize.toFloat()
            setLineSpacing(0f, settings.lineHeight)
            setTypeface(typeface, Typeface.NORMAL)
            setTextColor(textColor)
            text = content
            setOnClickListener { toggleMenu() }
        }
        
        // 添加到容器，在buttonsLayout之前
        // buttonsLayout应该是倒数第二个 (倒数第一个是placeholder)
        val count = contentContainer?.childCount ?: 0
        val insertIndex = if (count >= 2) count - 2 else count
        
        contentContainer?.addView(divider, insertIndex)
        contentContainer?.addView(newTitleView, insertIndex + 1)
        contentContainer?.addView(newContentView, insertIndex + 2)
    }
}
