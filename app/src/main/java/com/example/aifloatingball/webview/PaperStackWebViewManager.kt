package com.example.aifloatingball.webview

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.AccelerateDecelerateInterpolator
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.core.view.ViewCompat
import androidx.core.view.children
import com.example.aifloatingball.utils.WebViewConstants
import android.view.WindowManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.example.aifloatingball.model.HistoryEntry
import com.example.aifloatingball.download.EnhancedDownloadManager
import android.webkit.URLUtil
import android.widget.Toast
import java.util.Date
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * 扩展函数：Float的幂运算
 */
private fun Float.pow(exponent: Int): Float {
    return this.pow(exponent.toFloat())
}

/**
 * 纸堆WebView管理器 - 重新设计版本
 * 实现真正的标签页纵向叠加效果，每个WebView作为独立标签页
 * 用户横向滑动可以切换不同标签页，每个标签页纵向叠加显示
 */
class PaperStackWebViewManager(
    private val context: Context,
    private val container: ViewGroup,
    private val windowManager: WindowManager? = null
) {
    companion object {
        private const val TAG = "PaperStackWebViewManager"
        private const val MAX_TABS = 8 // 最大标签页数量
        private const val TAB_OFFSET_X = 15f // 每个标签页的X轴偏移
        private const val TAB_OFFSET_Y = 10f // 每个标签页的Y轴偏移
        private const val SWIPE_THRESHOLD = 50f // 滑动阈值 - 进一步降低阈值提高响应性
        private const val SWIPE_VELOCITY_THRESHOLD = 500f // 滑动速度阈值
        private const val ANIMATION_DURATION = 350L // 动画持续时间
        private const val TAB_SHADOW_RADIUS = 15f // 标签页阴影半径
        private const val TAB_CORNER_RADIUS = 10f // 标签页圆角半径
        private const val TAB_SCALE_FACTOR = 0.96f // 标签页缩放因子
        private const val TAB_ALPHA_FACTOR = 0.15f // 标签页透明度因子
    }

    // 标签页数据类
    data class WebViewTab(
        val id: String,
        val webView: WebView,
        var title: String,
        val url: String,
        var isActive: Boolean = false,
        var stackIndex: Int = 0,
        var groupId: String? = null, // 所属组ID
        var isLazyLoaded: Boolean = false, // 是否延迟加载（未加载URL）
        var screenshot: android.graphics.Bitmap? = null // 🔧 修复4：保存用户最后浏览的界面截图
    )

    private val tabs = mutableListOf<WebViewTab>()
    private var currentTabIndex = 0
    private var currentGroupId: String? = null // 当前组ID
    private var isAnimating = false
    private var gestureDetector: GestureDetector? = null
    private var onTabCreatedListener: ((WebViewTab) -> Unit)? = null
    private var onTabSwitchedListener: ((WebViewTab, Int) -> Unit)? = null
    private var onFaviconReceivedListener: ((WebViewTab, android.graphics.Bitmap?) -> Unit)? = null
    private var onTitleReceivedListener: ((WebViewTab, String?) -> Unit)? = null
    private var onPageStartedListener: ((WebViewTab, String?) -> Unit)? = null
    private var onPageFinishedListener: ((WebViewTab, String?) -> Unit)? = null
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var isSwipeStarted = false
    private var swipeDirection = SwipeDirection.NONE
    private var isTextSelectionActive = false
    private var lastTouchTime = 0L
    private var touchDownTime = 0L
    private var enhancedMenuManager: EnhancedMenuManager? = null
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    
    // 增强下载管理器
    private val enhancedDownloadManager: EnhancedDownloadManager by lazy {
        EnhancedDownloadManager(context)
    }

    private val historyPrefs: SharedPreferences = context.getSharedPreferences("browser_history", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val MAX_HISTORY_SIZE = 100 // 最大历史记录数量
    
    init {
        setupGestureDetector()
        setupContainer()
        setupEnhancedMenuManager()
    }
    
    /**
     * 设置favicon监听器
     */
    fun setOnFaviconReceivedListener(listener: (WebViewTab, android.graphics.Bitmap?) -> Unit) {
        this.onFaviconReceivedListener = listener
    }
    
    /**
     * 设置标题更新监听器
     */
    fun setOnTitleReceivedListener(listener: (WebViewTab, String?) -> Unit) {
        this.onTitleReceivedListener = listener
    }
    
    /**
     * 设置页面开始加载监听器
     */
    fun setOnPageStartedListener(listener: (WebViewTab, String?) -> Unit) {
        this.onPageStartedListener = listener
    }
    
    /**
     * 设置页面加载完成监听器
     */
    fun setOnPageFinishedListener(listener: (WebViewTab, String?) -> Unit) {
        this.onPageFinishedListener = listener
    }
    
    /**
     * 设置增强菜单管理器
     */
    private fun setupEnhancedMenuManager() {
        windowManager?.let {
            enhancedMenuManager = EnhancedMenuManager(context, it)
            // 设置新标签页监听器
            enhancedMenuManager?.setOnNewTabListener { url, inBackground ->
                if (!inBackground) {
                    addTab(url)
                } else {
                    addTab(url)
                    // 后台加载，不切换到新标签页
                    val newTabIndex = tabs.size - 1
                    if (newTabIndex > 0 && newTabIndex != currentTabIndex) {
                        // 保持当前标签页，新标签页在后台加载
                        updateTabPositions()
                    }
                }
            }
            Log.d(TAG, "增强菜单管理器已初始化")
        }
    }

    /**
     * 设置容器
     */
    private fun setupContainer() {
        container.clipChildren = false
        container.clipToPadding = false
    }

    /**
     * 设置手势检测器
     */
    private fun setupGestureDetector() {
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (isAnimating || tabs.isEmpty()) return false
                
                val deltaX = e2.x - (e1?.x ?: 0f)
                val deltaY = e2.y - (e1?.y ?: 0f)
                
                // 检测横向滑动 - 切换标签页
                if (abs(deltaX) > abs(deltaY) && abs(deltaX) > SWIPE_THRESHOLD) {
                    if (deltaX > 0) {
                        // 右滑 - 切换到上一个标签页
                        switchToPreviousTab()
                    } else {
                        // 左滑 - 切换到下一个标签页
                        switchToNextTab()
                    }
                    return true
                }
                return false
            }
        })
    }

    /**
     * 设置当前组ID
     */
    fun setCurrentGroupId(groupId: String?) {
        currentGroupId = groupId
        Log.d(TAG, "设置当前组ID: $groupId")
    }
    
    /**
     * 获取当前组ID
     */
    fun getCurrentGroupId(): String? = currentGroupId
    
    /**
     * 切换到指定组（加载该组的标签页）
     */
    fun switchToGroup(groupId: String?, onTabsLoaded: (List<WebViewTab>) -> Unit) {
        // 保存当前组的标签页
        if (currentGroupId != null) {
            saveCurrentGroupTabs()
        }
        
        // 清理当前标签页
        cleanup()
        
        // 设置新组ID
        currentGroupId = groupId
        
        // 加载新组的标签页
        loadGroupTabs(groupId, onTabsLoaded)
    }

    /**
     * 添加新的标签页
     */
    fun addTab(url: String? = null, title: String? = null, groupId: String? = null): WebViewTab {
        val tabId = "tab_${System.currentTimeMillis()}"
        val webView = PaperWebView(context)
        webView.setupWebView()
        
        // 使用传入的groupId或当前组ID
        val tabGroupId = groupId ?: currentGroupId
        
        // 创建标签页
        val tab = WebViewTab(
            id = tabId,
            webView = webView,
            title = title ?: "新标签页",
            url = url ?: "https://www.baidu.com",
            isActive = false,
            stackIndex = tabs.size,
            groupId = tabGroupId,
            isLazyLoaded = false // 正常创建的标签页立即加载
        )
        
        // 添加到容器
        container.addView(webView)
        tabs.add(tab)
        
        // 立即切换到新标签页（确保用户看到新页面加载）
        val newTabIndex = tabs.size - 1
        currentTabIndex = newTabIndex
        
        // 🔧 修复：确保每个组至少有一个功能主页
        ensureFunctionalHomeExists(tabGroupId)
        
        // 更新标签页位置
        updateTabPositions()
        
        // 加载URL（如果是功能主页，加载功能主页HTML并设置JavaScript接口）
        if (url == "home://functional") {
            // 为功能主页设置JavaScript接口
            setupFunctionalHomeInterface(webView)
            
            // 设置WebViewClient以在页面加载完成后刷新按钮和设置主题
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    if (url == "file:///android_asset/functional_home.html") {
                        // 页面加载完成后，刷新按钮显示状态和顺序
                        view?.postDelayed({
                            view.evaluateJavascript("updateButtonVisibility();", null)
                            view.evaluateJavascript("loadButtonOrder();", null)
                            view.evaluateJavascript("updateButtonLayout();", null)
                            
                            // 设置深色模式
                            val settingsManager = com.example.aifloatingball.SettingsManager.getInstance(context)
                            val themeMode = settingsManager.getThemeMode()
                            val isDarkMode = when (themeMode) {
                                com.example.aifloatingball.SettingsManager.THEME_MODE_DARK -> true
                                com.example.aifloatingball.SettingsManager.THEME_MODE_LIGHT -> false
                                else -> {
                                    // 跟随系统
                                    val nightModeFlags = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
                                    nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
                                }
                            }
                            val theme = if (isDarkMode) "dark" else "light"
                            view.evaluateJavascript("setTheme('$theme');", null)
                            
                            Log.d(TAG, "功能主页加载完成，已刷新按钮状态和设置主题: $theme")
                        }, 300)
                    }
                }
            }
            
            webView.loadUrl("file:///android_asset/functional_home.html")
        } else {
            webView.loadUrl(tab.url)
        }
        
        // 保存当前组的标签页数据
        saveCurrentGroupTabs()
        
        // 通知监听器
        onTabCreatedListener?.invoke(tab)
        
        // 如果是新创建的标签页且是当前标签页，触发切换监听器
        if (currentTabIndex == newTabIndex) {
            onTabSwitchedListener?.invoke(tab, currentTabIndex)
        }
        
        Log.d(TAG, "添加新标签页: ${tab.title}, 当前数量: ${tabs.size}, 组ID: $tabGroupId, 已切换到新标签页")
        return tab
    }
    
    /**
     * 为功能主页设置JavaScript接口
     */
    private fun setupFunctionalHomeInterface(webView: WebView) {
        try {
            // 移除旧的接口（如果存在）
            try {
                webView.removeJavascriptInterface("AndroidInterface")
            } catch (e: Exception) {
                // 忽略接口不存在的异常
            }
            
            // 添加新的JavaScript接口
            webView.addJavascriptInterface(object {
                @android.webkit.JavascriptInterface
                fun createNewTab() {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        // 创建新标签页
                        addTab()
                        Log.d(TAG, "功能主页：创建新标签页")
                    }
                }
                
                @android.webkit.JavascriptInterface
                fun showGestureGuide() {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        // 显示手势指南（通过回调通知外部）
                        onFunctionalHomeActionListener?.onShowGestureGuide()
                        Log.d(TAG, "功能主页：显示手势指南")
                    }
                }
                
                @android.webkit.JavascriptInterface
                fun openDownloadManager() {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        // 打开下载管理（通过回调通知外部）
                        onFunctionalHomeActionListener?.onOpenDownloadManager()
                        Log.d(TAG, "功能主页：打开下载管理")
                    }
                }
                
                @android.webkit.JavascriptInterface
                fun createNewGroup() {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        // 创建新组（通过回调通知外部）
                        onFunctionalHomeActionListener?.onCreateNewGroup()
                        Log.d(TAG, "功能主页：创建新组")
                    }
                }
                
                @android.webkit.JavascriptInterface
                fun openGroupManager() {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        // 打开组管理（通过回调通知外部）
                        onFunctionalHomeActionListener?.onOpenGroupManager()
                        Log.d(TAG, "功能主页：打开组管理")
                    }
                }
                
                @android.webkit.JavascriptInterface
                fun openBookmarks() {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        // 打开收藏夹（通过回调通知外部）
                        onFunctionalHomeActionListener?.onOpenBookmarks()
                        Log.d(TAG, "功能主页：打开收藏夹")
                    }
                }
                
                @android.webkit.JavascriptInterface
                fun openHistory() {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        // 打开历史记录（通过回调通知外部）
                        onFunctionalHomeActionListener?.onOpenHistory()
                        Log.d(TAG, "功能主页：打开历史记录")
                    }
                }
                
                @android.webkit.JavascriptInterface
                fun openFileReader() {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        // 打开文件阅读器（通过回调通知外部）
                        onFunctionalHomeActionListener?.onOpenFileReader()
                        Log.d(TAG, "功能主页：打开文件阅读器")
                    }
                }
                
                @android.webkit.JavascriptInterface
                fun hideButton(buttonId: String) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        // 隐藏按钮（通过回调通知外部）
                        onFunctionalHomeActionListener?.onHideButton(buttonId)
                        Log.d(TAG, "功能主页：隐藏按钮 $buttonId")
                    }
                }
                
                @android.webkit.JavascriptInterface
                fun showRestoreButtonsDialog() {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        // 显示恢复按钮对话框（通过回调通知外部）
                        onFunctionalHomeActionListener?.onShowRestoreButtonsDialog()
                        Log.d(TAG, "功能主页：显示恢复按钮对话框")
                    }
                }
                
                @android.webkit.JavascriptInterface
                fun getButtonVisibility(): String {
                    // 获取按钮显示状态（通过回调通知外部）
                    return onFunctionalHomeActionListener?.getButtonVisibility() ?: "{}"
                }
                
                @android.webkit.JavascriptInterface
                fun openSettings() {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        // 打开综合设置（通过回调通知外部）
                        onFunctionalHomeActionListener?.onOpenSettings()
                        Log.d(TAG, "功能主页：打开综合设置")
                    }
                }
                
                @android.webkit.JavascriptInterface
                fun saveButtonOrder(orderJson: String) {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        // 保存按钮顺序（通过回调通知外部）
                        onFunctionalHomeActionListener?.onSaveButtonOrder(orderJson)
                        Log.d(TAG, "功能主页：保存按钮顺序")
                    }
                }
                
                @android.webkit.JavascriptInterface
                fun getButtonOrder(): String {
                    // 获取按钮顺序（通过回调通知外部）
                    return onFunctionalHomeActionListener?.getButtonOrder() ?: "[]"
                }
                
                @android.webkit.JavascriptInterface
                fun cancelButtonLongPress() {
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        // 取消长按（通过回调通知外部）
                        onFunctionalHomeActionListener?.onCancelButtonLongPress()
                        Log.d(TAG, "功能主页：取消长按")
                    }
                }
            }, "AndroidInterface")
            
            Log.d(TAG, "功能主页JavaScript接口已设置")
        } catch (e: Exception) {
            Log.e(TAG, "设置功能主页JavaScript接口失败", e)
        }
    }
    
    /**
     * 功能主页操作监听器
     */
    interface FunctionalHomeActionListener {
        fun onShowGestureGuide()
        fun onOpenDownloadManager()
        fun onCreateNewGroup()
        fun onOpenGroupManager()
        fun onOpenBookmarks()
        fun onOpenHistory()
        fun onOpenFileReader()
        fun onHideButton(buttonId: String)
        fun onShowRestoreButtonsDialog()
        fun getButtonVisibility(): String
        fun onOpenSettings()
        fun onSaveButtonOrder(orderJson: String)
        fun getButtonOrder(): String
        fun onCancelButtonLongPress()
    }
    
    private var onFunctionalHomeActionListener: FunctionalHomeActionListener? = null
    
    /**
     * 设置功能主页操作监听器
     */
    fun setOnFunctionalHomeActionListener(listener: FunctionalHomeActionListener) {
        onFunctionalHomeActionListener = listener
    }

    /**
     * 移除指定标签页
     */
    fun removeTab(tabId: String): Boolean {
        val tabIndex = tabs.indexOfFirst { it.id == tabId }
        if (tabIndex == -1) return false
        
        val tab = tabs[tabIndex]
        
        // 检查是否是功能主页，如果是则不允许删除
        if (tab.url == "home://functional") {
            Log.d(TAG, "⚠️ 功能主页不能被删除")
            return false
        }
        
        container.removeView(tab.webView)
        tab.webView.destroy()
        tabs.removeAt(tabIndex)
        
        // 调整当前标签页索引
        if (currentTabIndex >= tabs.size) {
            currentTabIndex = max(0, tabs.size - 1)
        }
        
        // 更新标签页位置
        updateTabPositions()
        
        // 保存当前组的标签页数据
        saveCurrentGroupTabs()
        
        Log.d(TAG, "移除标签页: ${tab.title}, 当前数量: ${tabs.size}")
        return true
    }
    
    /**
     * 通过URL关闭标签页
     */
    fun closeTabByUrl(url: String): Boolean {
        // 检查是否是功能主页，如果是则不允许关闭
        if (url == "home://functional") {
            Log.d(TAG, "⚠️ 功能主页不能被关闭")
            return false
        }
        
        val tabIndex = tabs.indexOfFirst { it.url == url }
        if (tabIndex == -1) return false
        
        val tab = tabs[tabIndex]
        container.removeView(tab.webView)
        tab.webView.destroy()
        tabs.removeAt(tabIndex)
        
        // 调整当前标签页索引
        if (currentTabIndex >= tabs.size) {
            currentTabIndex = max(0, tabs.size - 1)
        }
        
        // 更新标签页位置
        updateTabPositions()
        
        // 保存当前组的标签页数据
        saveCurrentGroupTabs()
        
        Log.d(TAG, "通过URL关闭标签页: ${tab.title}, URL: $url, 当前数量: ${tabs.size}")
        return true
    }

    /**
     * 切换到下一个标签页（不循环，到达边界时停止）
     */
    fun switchToNextTab() {
        if (isAnimating || tabs.isEmpty()) return
        
        // 不循环，如果已经是最后一个标签页，不切换
        if (currentTabIndex >= tabs.size - 1) {
            Log.d(TAG, "已经是最后一个标签页，不切换")
            return
        }
        
        val nextIndex = currentTabIndex + 1
        if (nextIndex < tabs.size) {
            switchToTab(nextIndex)
        }
    }

    /**
     * 切换到上一个标签页（不循环，到达边界时停止）
     */
    fun switchToPreviousTab() {
        if (isAnimating || tabs.isEmpty()) return
        
        // 不循环，如果已经是第一个标签页，不切换
        if (currentTabIndex <= 0) {
            Log.d(TAG, "已经是第一个标签页，不切换")
            return
        }
        
        val prevIndex = currentTabIndex - 1
        if (prevIndex >= 0) {
            switchToTab(prevIndex)
        }
    }

    /**
     * 切换到指定标签页
     * @param targetIndex 目标标签页索引
     * @param swipeDirection 滑动方向（可选，如果提供则使用，否则根据索引判断）
     */
    fun switchToTab(targetIndex: Int, swipeDirection: SwipeDirection? = null) {
        if (isAnimating || targetIndex < 0 || targetIndex >= tabs.size || tabs.isEmpty()) {
            Log.w(TAG, "switchToTab: 无效参数或条件不满足。isAnimating=$isAnimating, targetIndex=$targetIndex, tabs.size=${tabs.size}")
            return
        }
        
        // 如果目标索引就是当前索引，不需要切换
        if (targetIndex == currentTabIndex) {
            Log.d(TAG, "目标标签页就是当前标签页，跳过切换")
            return
        }
        
        isAnimating = true
        val currentTab = tabs[currentTabIndex]
        val targetTab = tabs[targetIndex]
        
        // 如果目标标签页是延迟加载的，现在加载它
        if (targetTab.isLazyLoaded) {
            Log.d(TAG, "延迟加载标签页: ${targetTab.title}")
            if (targetTab.url == "home://functional") {
                setupFunctionalHomeInterface(targetTab.webView)
                targetTab.webView.loadUrl("file:///android_asset/functional_home.html")
            } else {
                targetTab.webView.loadUrl(targetTab.url)
            }
            targetTab.isLazyLoaded = false
        }
        
        // 判断滑动方向：优先使用传入的滑动方向，否则根据索引判断
        val isSwipeLeft = when {
            swipeDirection == SwipeDirection.LEFT -> true
            swipeDirection == SwipeDirection.RIGHT -> false
            targetIndex > currentTabIndex -> true // 索引增大表示左滑（下一个）
            targetIndex < currentTabIndex -> false // 索引减小表示右滑（上一个）
            currentTabIndex == tabs.size - 1 && targetIndex == 0 -> true // 从最后一个到第一个，视为左滑
            currentTabIndex == 0 && targetIndex == tabs.size - 1 -> false // 从第一个到最后一个，视为右滑
            else -> targetIndex > currentTabIndex // 默认根据索引判断
        }
        
        Log.d(TAG, "开始卡片交叠切换：从 ${currentTab.title} 到 ${targetTab.title}, 方向=${if (isSwipeLeft) "左滑" else "右滑"}")
        
        // 创建卡片交叠动画
        val animatorSet = createCardStackAnimation(currentTab, targetTab, isSwipeLeft)
        
        animatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                Log.d(TAG, "卡片交叠切换动画开始")
            }
            
            override fun onAnimationEnd(animation: Animator) {
                isAnimating = false
                
                // 🔧 修复4：在切换前保存当前页面的截图
                try {
                    if (currentTab.webView.width > 0 && currentTab.webView.height > 0) {
                        currentTab.webView.isDrawingCacheEnabled = true
                        currentTab.webView.buildDrawingCache()
                        val bitmap = currentTab.webView.drawingCache
                        if (bitmap != null) {
                            // 创建副本避免引用问题
                            val screenshot = android.graphics.Bitmap.createBitmap(bitmap)
                            // 找到对应的tab并更新截图
                            tabs.find { it.id == currentTab.id }?.let { tab ->
                                tab.screenshot?.recycle() // 回收旧截图
                                tab.screenshot = screenshot
                                Log.d(TAG, "✅ 已保存页面截图: ${currentTab.title}")
                            }
                        }
                        currentTab.webView.isDrawingCacheEnabled = false
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "保存页面截图失败: ${currentTab.title}", e)
                }
                
                // 🔧 修复1：iOS风格动画结束后，隐藏当前页面，显示目标页面
                currentTab.webView.visibility = View.GONE
                currentTab.webView.translationX = 0f
                currentTab.webView.alpha = 1.0f
                
                targetTab.webView.visibility = View.VISIBLE
                targetTab.webView.translationX = 0f
                targetTab.webView.alpha = 1.0f
                
                // 重新排序标签页数组（只更新视觉位置，不改变数组顺序）
                reorderTabs(currentTabIndex, targetIndex)
                
                // 检查目标标签页的错误状态，确保背景正确设置
                if (targetTab.webView is PaperWebView) {
                    if (targetTab.webView.isErrorState) {
                        // 如果目标标签页处于错误状态，确保背景是不透明的白色
                        Log.d(TAG, "切换到错误状态的标签页，设置背景为白色: ${targetTab.title}")
                        targetTab.webView.setBackgroundColor(Color.WHITE)
                    } else {
                        // 如果目标标签页正常，确保背景透明
                        Log.d(TAG, "切换到正常标签页，设置背景为透明: ${targetTab.title}")
                        targetTab.webView.setBackgroundColor(Color.TRANSPARENT)
                    }
                }
                
                // 通知监听器
                onTabSwitchedListener?.invoke(targetTab, currentTabIndex)
                
                Log.d(TAG, "卡片交叠切换完成，当前标签页: ${targetTab.title}, 索引: $currentTabIndex")
            }
            
            override fun onAnimationCancel(animation: Animator) {
                isAnimating = false
                Log.d(TAG, "卡片交叠切换动画被取消")
            }
        })
        
        animatorSet.start()
    }

    /**
     * 创建iOS风格的切换动画（左右滑动）
     * 🔧 修复1：参考iOS切换动画，使用简单的左右滑动效果
     * @param currentTab 当前卡片
     * @param targetTab 目标卡片
     * @param isSwipeLeft 是否左滑（true=左滑，false=右滑）
     */
    private fun createCardStackAnimation(currentTab: WebViewTab, targetTab: WebViewTab, isSwipeLeft: Boolean): AnimatorSet {
        val duration = 300L // iOS风格动画时长：300ms
        val currentWebView = currentTab.webView
        val targetWebView = targetTab.webView
        
        // 获取容器宽度
        val containerWidth = container.width.toFloat()
        val swipeDistance = if (containerWidth > 0) containerWidth else 800f
        
        // iOS风格：当前页面滑出，目标页面滑入
        // 左滑（下一个）：当前页面向左滑出，目标页面从右侧滑入
        // 右滑（上一个）：当前页面向右滑出，目标页面从左侧滑入
        
        val currentTargetX = if (isSwipeLeft) {
            -swipeDistance // 左滑：当前页面向左滑出
        } else {
            swipeDistance // 右滑：当前页面向右滑出
        }
        
        val targetStartX = if (isSwipeLeft) {
            swipeDistance // 左滑：目标页面从右侧开始
        } else {
            -swipeDistance // 右滑：目标页面从左侧开始
        }
        
        // 确保目标页面可见并设置初始位置
        targetWebView.visibility = View.VISIBLE
        targetWebView.translationX = targetStartX
        targetWebView.translationY = 0f
        targetWebView.scaleX = 1.0f
        targetWebView.scaleY = 1.0f
        targetWebView.alpha = 1.0f
        targetWebView.elevation = currentWebView.elevation + 1f
        
        // 🔧 修复：设置背景色为不透明，避免透视看到页面下方
        // 在动画过程中，确保WebView背景不透明
        if (targetWebView is PaperWebView) {
            targetWebView.setBackgroundColor(Color.WHITE)
        } else {
            targetWebView.setBackgroundColor(Color.WHITE)
        }
        
        // 确保当前页面也有不透明背景，避免透视
        if (currentWebView is PaperWebView) {
            currentWebView.setBackgroundColor(Color.WHITE)
        } else {
            currentWebView.setBackgroundColor(Color.WHITE)
        }
        
        // 当前页面滑出动画
        val currentAnimatorX = ObjectAnimator.ofFloat(currentWebView, "translationX", 0f, currentTargetX).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator(1.2f) // iOS风格减速曲线
        }
        
        val currentAnimatorAlpha = ObjectAnimator.ofFloat(currentWebView, "alpha", 1.0f, 0.7f).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator(1.2f)
        }
        
        // 目标页面滑入动画
        val targetAnimatorX = ObjectAnimator.ofFloat(targetWebView, "translationX", targetStartX, 0f).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator(1.2f) // iOS风格减速曲线
        }
        
        val targetAnimatorAlpha = ObjectAnimator.ofFloat(targetWebView, "alpha", 0.7f, 1.0f).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator(1.2f)
        }
        
        // 组合动画：同时执行
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(
            currentAnimatorX,
            currentAnimatorAlpha,
            targetAnimatorX,
            targetAnimatorAlpha
        )
        
        return animatorSet
    }

    /**
     * 创建移到底部的动画
     */
    private fun createMoveToBottomAnimation(tab: WebViewTab, targetStackIndex: Int): Animator {
        val targetOffsetX = targetStackIndex * TAB_OFFSET_X
        val targetOffsetY = targetStackIndex * TAB_OFFSET_Y
        val targetScale = TAB_SCALE_FACTOR.pow(targetStackIndex)
        // 修复透明度计算：非激活页面保持适当透明度
        val targetAlpha = max(0.4f, 1f - (targetStackIndex * TAB_ALPHA_FACTOR))
        val targetElevation = (tabs.size - targetStackIndex + 10).toFloat()
        
        val animatorX = ObjectAnimator.ofFloat(tab.webView, "translationX", tab.webView.translationX, targetOffsetX)
        val animatorY = ObjectAnimator.ofFloat(tab.webView, "translationY", tab.webView.translationY, targetOffsetY)
        val animatorScaleX = ObjectAnimator.ofFloat(tab.webView, "scaleX", tab.webView.scaleX, targetScale)
        val animatorScaleY = ObjectAnimator.ofFloat(tab.webView, "scaleY", tab.webView.scaleY, targetScale)
        val animatorAlpha = ObjectAnimator.ofFloat(tab.webView, "alpha", tab.webView.alpha, targetAlpha)
        val animatorElevation = ObjectAnimator.ofFloat(tab.webView, "elevation", tab.webView.elevation, targetElevation)
        
        // 设置动画持续时间
        val duration = ANIMATION_DURATION
        animatorX.duration = duration
        animatorY.duration = duration
        animatorScaleX.duration = duration
        animatorScaleY.duration = duration
        animatorAlpha.duration = duration
        animatorElevation.duration = duration
        
        // 设置插值器
        val interpolator = DecelerateInterpolator(1.2f)
        animatorX.interpolator = interpolator
        animatorY.interpolator = interpolator
        animatorScaleX.interpolator = interpolator
        animatorScaleY.interpolator = interpolator
        animatorAlpha.interpolator = interpolator
        animatorElevation.interpolator = interpolator
        
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(animatorX, animatorY, animatorScaleX, animatorScaleY, animatorAlpha, animatorElevation)
        
        return animatorSet
    }

    /**
     * 创建移到顶部的动画
     */
    private fun createMoveToTopAnimation(tab: WebViewTab, targetStackIndex: Int): Animator {
        val targetOffsetX = targetStackIndex * TAB_OFFSET_X
        val targetOffsetY = targetStackIndex * TAB_OFFSET_Y
        val targetScale = TAB_SCALE_FACTOR.pow(targetStackIndex)
        // 修复透明度计算：激活页面完全不透明
        val targetAlpha = 1.0f
        val targetElevation = (tabs.size + 20).toFloat()
        
        val animatorX = ObjectAnimator.ofFloat(tab.webView, "translationX", tab.webView.translationX, targetOffsetX)
        val animatorY = ObjectAnimator.ofFloat(tab.webView, "translationY", tab.webView.translationY, targetOffsetY)
        val animatorScaleX = ObjectAnimator.ofFloat(tab.webView, "scaleX", tab.webView.scaleX, targetScale)
        val animatorScaleY = ObjectAnimator.ofFloat(tab.webView, "scaleY", tab.webView.scaleY, targetScale)
        val animatorAlpha = ObjectAnimator.ofFloat(tab.webView, "alpha", tab.webView.alpha, targetAlpha)
        val animatorElevation = ObjectAnimator.ofFloat(tab.webView, "elevation", tab.webView.elevation, targetElevation)
        
        // 设置动画持续时间
        val duration = ANIMATION_DURATION
        animatorX.duration = duration
        animatorY.duration = duration
        animatorScaleX.duration = duration
        animatorScaleY.duration = duration
        animatorAlpha.duration = duration
        animatorElevation.duration = duration
        
        // 设置插值器
        val interpolator = DecelerateInterpolator(1.2f)
        animatorX.interpolator = interpolator
        animatorY.interpolator = interpolator
        animatorScaleX.interpolator = interpolator
        animatorScaleY.interpolator = interpolator
        animatorAlpha.interpolator = interpolator
        animatorElevation.interpolator = interpolator
        
        val animatorSet = AnimatorSet()
        animatorSet.playTogether(animatorX, animatorY, animatorScaleX, animatorScaleY, animatorAlpha, animatorElevation)
        
        return animatorSet
    }

    /**
     * 创建重新排列的动画
     */
    private fun createRearrangeAnimations(currentIndex: Int, targetIndex: Int): List<Animator> {
        val animators = mutableListOf<Animator>()
        
        tabs.forEachIndexed { index, tab ->
            if (index != currentIndex && index != targetIndex) {
                // 计算新的层叠位置：基于与目标标签页的距离
                val distanceFromTarget = abs(index - targetIndex)
                val newStackIndex = distanceFromTarget
                
                val targetOffsetX = newStackIndex * TAB_OFFSET_X
                val targetOffsetY = newStackIndex * TAB_OFFSET_Y
                val targetScale = TAB_SCALE_FACTOR.pow(newStackIndex)
                val targetAlpha = max(0.4f, 1f - (newStackIndex * TAB_ALPHA_FACTOR))
                val targetElevation = (tabs.size - newStackIndex + 10).toFloat()
                
                val animatorX = ObjectAnimator.ofFloat(tab.webView, "translationX", tab.webView.translationX, targetOffsetX)
                val animatorY = ObjectAnimator.ofFloat(tab.webView, "translationY", tab.webView.translationY, targetOffsetY)
                val animatorScaleX = ObjectAnimator.ofFloat(tab.webView, "scaleX", tab.webView.scaleX, targetScale)
                val animatorScaleY = ObjectAnimator.ofFloat(tab.webView, "scaleY", tab.webView.scaleY, targetScale)
                val animatorAlpha = ObjectAnimator.ofFloat(tab.webView, "alpha", tab.webView.alpha, targetAlpha)
                val animatorElevation = ObjectAnimator.ofFloat(tab.webView, "elevation", tab.webView.elevation, targetElevation)
                
                val animatorSet = AnimatorSet()
                animatorSet.playTogether(animatorX, animatorY, animatorScaleX, animatorScaleY, animatorAlpha, animatorElevation)
                animators.add(animatorSet)
            }
        }
        
        return animators
    }

    /**
     * 重新排序标签页数组
     */
    private fun reorderTabs(currentIndex: Int, targetIndex: Int) {
        // 检查数组边界，避免越界异常
        if (tabs.isEmpty() || currentIndex < 0 || currentIndex >= tabs.size || 
            targetIndex < 0 || targetIndex >= tabs.size) {
            Log.w(TAG, "reorderTabs: 索引超出边界，跳过重新排序。tabs.size=${tabs.size}, currentIndex=$currentIndex, targetIndex=$targetIndex")
            return
        }
        
        // 如果只有一个标签页，不需要重新排序
        if (tabs.size == 1) {
            Log.d(TAG, "只有一个标签页，跳过重新排序")
            return
        }
        
        // 关键修复：在纸堆模式中，不要重新排序数组，只更新当前索引和视觉位置
        // 这样可以保持标签页的原始顺序，确保StackedCardPreview的索引对应正确
        Log.d(TAG, "纸堆模式：保持标签页数组顺序不变，更新当前索引: $currentIndex -> $targetIndex")
        
        // 更新当前标签页索引
        currentTabIndex = targetIndex
        
        // 更新所有标签页的位置
        updateTabPositions()
        
        Log.d(TAG, "纸堆模式标签页切换完成，当前激活索引: $currentTabIndex")
    }

    /**
     * 更新所有标签页的位置 - 实现真正的纵向叠加效果
     */
    private fun updateTabPositions() {
        tabs.forEachIndexed { index, tab ->
            // 计算层叠位置：当前激活的标签页在最上面，其他按距离排序
            val distanceFromCurrent = abs(index - currentTabIndex)
            val stackIndex = distanceFromCurrent
            
            // 🔧 修复1：确保只有当前页面可见，其他页面完全隐藏
            if (index == currentTabIndex) {
                // 当前页面：完全可见
                tab.webView.visibility = View.VISIBLE
                tab.webView.alpha = 1.0f
                tab.webView.translationX = 0f
                tab.webView.translationY = 0f
                tab.webView.scaleX = 1.0f
                tab.webView.scaleY = 1.0f
                tab.webView.elevation = (tabs.size + 20).toFloat()
                // 🔧 修复：确保当前页面在最上层，其他页面在下方
                tab.webView.bringToFront()
            } else {
                // 非当前页面：完全隐藏，避免重叠显示
                tab.webView.visibility = View.GONE
                tab.webView.alpha = 0f
                tab.webView.translationX = 0f
                tab.webView.translationY = 0f
                tab.webView.scaleX = 1.0f
                tab.webView.scaleY = 1.0f
                tab.webView.elevation = (tabs.size - stackIndex + 10).toFloat()
            }
            
            // 更新标签页状态
            tab.isActive = (index == currentTabIndex)
            tab.stackIndex = stackIndex
            
            Log.d(TAG, "标签页 ${tab.title}: index=$index, currentTabIndex=$currentTabIndex, visible=${index == currentTabIndex}")
        }
    }

    /**
     * 获取当前标签页
     */
    fun getCurrentTab(): WebViewTab? {
        return tabs.getOrNull(currentTabIndex)
    }

    /**
     * 获取标签页数量
     */
    fun getTabCount(): Int = tabs.size

    /**
     * 获取所有标签页数据
     */
    fun getAllTabs(): List<WebViewTab> {
        // 只返回当前组的标签页
        return if (currentGroupId != null) {
            tabs.filter { it.groupId == currentGroupId }
        } else {
            tabs.toList()
        }
    }
    
    /**
     * 获取所有组的所有标签页（用于保存恢复数据）
     */
    fun getAllTabsByGroup(): Map<String, List<WebViewTab>> {
        return tabs.groupBy { it.groupId ?: "default" }
    }
    
    /**
     * 保存恢复数据
     */
    fun saveRecoveryData() {
        try {
            val recoveryManager = com.example.aifloatingball.manager.TabRecoveryManager.getInstance(context)
            val allTabsByGroup = getAllTabsByGroup()
            
            // 转换为RecoveryTabSource接口，排除功能主页
            val recoveryTabsMap = allTabsByGroup.mapValues { (groupId, tabs) ->
                tabs
                    .filter { it.url != "home://functional" } // 排除功能主页
                    .map { tab ->
                        object : com.example.aifloatingball.manager.TabRecoveryManager.RecoveryTabSource {
                            override val id: String = tab.id
                            override val title: String = tab.title
                            override val url: String = tab.url
                        }
                    }
            }.filter { it.value.isNotEmpty() } // 只保留有标签页的组
            
            // 保存当前标签页索引（用户最后浏览的页面，排除功能主页后的索引）
            val currentGroupId = currentGroupId
            val currentTab = tabs.getOrNull(currentTabIndex)
            val currentTabIndexInGroup = if (currentGroupId != null && currentTab != null) {
                // 获取当前组的标签页（排除功能主页）
                val currentGroupTabs = tabs.filter { 
                    it.groupId == currentGroupId && it.url != "home://functional" 
                }
                // 找到当前标签页在组内的索引（排除功能主页后）
                val index = currentGroupTabs.indexOfFirst { it.id == currentTab.id }
                if (index >= 0) index else 0
            } else {
                0
            }
            
            recoveryManager.saveRecoveryData(recoveryTabsMap, currentGroupId, currentTabIndexInGroup)
            Log.d(TAG, "保存恢复数据: ${allTabsByGroup.size} 个组，共 ${tabs.size} 个标签页，当前组: $currentGroupId，当前索引: $currentTabIndexInGroup")
        } catch (e: Exception) {
            Log.e(TAG, "保存恢复数据失败", e)
        }
    }
    
    /**
     * 从恢复数据中恢复标签页（延迟加载）
     * @param recoveryData 恢复数据
     * @param onTabRestored 标签页恢复回调（groupId, tabId, tabTitle, isLoaded）
     */
    fun restoreTabsFromRecoveryData(
        recoveryData: com.example.aifloatingball.manager.TabRecoveryManager.RecoveryData,
        onTabRestored: ((String, String, String, Boolean) -> Unit)? = null
    ) {
        try {
            var totalRestored = 0
            var totalLazyLoaded = 0
            var lastTabIndex = -1 // 最后浏览的标签页索引（在所有恢复的标签页中的索引）
            var lastTabGroupId: String? = null
            
            // 先清理当前标签页
            cleanup()
            
            // 按组恢复标签页
            recoveryData.groups.forEach { (groupId, recoveryTabs) ->
                // 设置当前组ID（不调用switchToGroup，避免清理）
                currentGroupId = groupId
                
                recoveryTabs.forEachIndexed { index, recoveryTab ->
                    // 排除功能主页
                    if (recoveryTab.url == "home://functional") {
                        Log.d(TAG, "跳过功能主页: ${recoveryTab.title}")
                        return@forEachIndexed
                    }
                    
                    // 延迟加载：只创建标签页结构，不立即加载WebView
                    val tabId = recoveryTab.id
                    val tabTitle = recoveryTab.title
                    val tabUrl = recoveryTab.url
                    
                    // 创建标签页但不加载URL（延迟加载）
                    val webView = PaperWebView(context)
                    webView.setupWebView()
                    
                    val tab = WebViewTab(
                        id = tabId,
                        webView = webView,
                        title = tabTitle,
                        url = tabUrl,
                        isActive = false,
                        stackIndex = tabs.size,
                        groupId = groupId,
                        isLazyLoaded = true // 标记为延迟加载
                    )
                    
                    // 添加到容器但不加载URL
                    container.addView(webView)
                    tabs.add(tab)
                    
                    // 检查是否是最后浏览的标签页
                    val isLastTab = (groupId == recoveryData.lastGroupId && 
                                    index == recoveryData.lastTabIndex)
                    
                    if (isLastTab) {
                        lastTabIndex = tabs.size - 1
                        lastTabGroupId = groupId
                    }
                    
                    // 其他标签页延迟加载（不加载URL）
                    totalLazyLoaded++
                    onTabRestored?.invoke(groupId, tabId, tabTitle, false)
                    
                    totalRestored++
                }
                
                Log.d(TAG, "恢复组 $groupId 的 ${recoveryTabs.size} 个标签页（延迟加载）")
            }
            
            // 🔧 修复2：确保默认主页在最左边（索引0）
            val functionalHomeIndex = tabs.indexOfFirst { tab ->
                tab.url == "home://functional" || tab.url == "file:///android_asset/functional_home.html"
            }
            if (functionalHomeIndex > 0) {
                // 如果默认主页不在最左边，移到最左边
                val functionalHomeTab = tabs.removeAt(functionalHomeIndex)
                tabs.add(0, functionalHomeTab)
                Log.d(TAG, "🔧 修复2：将默认主页移到最左边（索引0）")
            }
            
            // 更新标签页位置并切换到最后一个浏览的标签页
            if (tabs.isNotEmpty()) {
                // 如果找到了最后浏览的标签页，切换到它；否则切换到第一个
                val targetIndex = if (lastTabIndex >= 0) {
                    // 如果最后浏览的标签页被移动了，需要重新计算索引
                    val lastTabId = if (lastTabIndex < tabs.size) tabs[lastTabIndex].id else null
                    if (lastTabId != null) {
                        tabs.indexOfFirst { it.id == lastTabId }.takeIf { it >= 0 } ?: 0
                    } else {
                        0
                    }
                } else {
                    0
                }
                
                currentTabIndex = targetIndex
                updateTabPositions()
                
                // 🔧 修复3：立即加载所有恢复的标签页，而不是延迟加载
                tabs.forEachIndexed { index, tab ->
                    if (tab.isLazyLoaded) {
                        if (tab.url == "home://functional") {
                            setupFunctionalHomeInterface(tab.webView)
                            tab.webView.loadUrl("file:///android_asset/functional_home.html")
                        } else {
                            tab.webView.loadUrl(tab.url)
                        }
                        tab.isLazyLoaded = false
                        totalLazyLoaded--
                        onTabRestored?.invoke(tab.groupId ?: "default", tab.id, tab.title, true)
                        Log.d(TAG, "🔧 修复3：立即加载恢复的标签页: ${tab.title}")
                    }
                }
                
                // 切换到目标标签页
                val targetTab = tabs[targetIndex]
                Log.d(TAG, "切换到最后浏览的标签页: 索引=$targetIndex, 标题=${targetTab.title}")
            }
            
            Log.d(TAG, "恢复完成: 共 $totalRestored 个标签页，$totalLazyLoaded 个延迟加载")
        } catch (e: Exception) {
            Log.e(TAG, "恢复标签页失败", e)
        }
    }
    
    /**
     * 获取指定组的所有标签页
     */
    fun getTabsByGroup(groupId: String?): List<WebViewTab> {
        return tabs.filter { it.groupId == groupId }
    }
    
    /**
     * 保存当前组的标签页数据
     */
    private fun saveCurrentGroupTabs() {
        if (currentGroupId == null) return
        try {
            val groupTabDataManager = com.example.aifloatingball.manager.GroupTabDataManager.getInstance(context)
            val currentGroupTabs = tabs.filter { it.groupId == currentGroupId }
            groupTabDataManager.saveGroupTabs(currentGroupId!!, currentGroupTabs)
        } catch (e: Exception) {
            Log.e(TAG, "保存当前组标签页失败", e)
        }
    }
    
    /**
     * 加载组的标签页数据
     */
    private fun loadGroupTabs(groupId: String?, onTabsLoaded: (List<WebViewTab>) -> Unit) {
        if (groupId == null) {
            // 🔧 修复：如果没有组ID，创建功能主页而不是百度首页
                val functionalHomeUrl = "home://functional"
                val defaultTab = addTab(functionalHomeUrl, "快捷入口", null)
            onTabsLoaded(listOf(defaultTab))
            return
        }
        
        try {
            val groupTabDataManager = com.example.aifloatingball.manager.GroupTabDataManager.getInstance(context)
            val tabDataList = groupTabDataManager.restoreGroupTabs(groupId)
            
            if (tabDataList.isEmpty()) {
                // 🔧 修复：如果没有保存的标签页，创建功能主页而不是百度首页
                val functionalHomeUrl = "home://functional"
                val defaultTab = addTab(functionalHomeUrl, "快捷入口", groupId)
                onTabsLoaded(listOf(defaultTab))
            } else {
                // 恢复标签页
                val restoredTabs = mutableListOf<WebViewTab>()
                var hasFunctionalHome = false
                
                tabDataList.forEach { tabData ->
                    // 检查是否已经有功能主页
                    if (tabData.url == "home://functional" || tabData.url == "file:///android_asset/functional_home.html") {
                        hasFunctionalHome = true
                    }
                    val restoredTab = addTab(tabData.url, tabData.title, groupId)
                    restoredTabs.add(restoredTab)
                }
                
                // 🔧 修复：如果没有功能主页，创建一个
                if (!hasFunctionalHome) {
                    val functionalHomeUrl = "home://functional"
                    val functionalHomeTab = addTab(functionalHomeUrl, "快捷入口", groupId)
                    restoredTabs.add(0, functionalHomeTab) // 将功能主页添加到第一个位置
                    Log.d(TAG, "组 $groupId 没有功能主页，已创建")
                }
                
                // 切换到第一个标签页（功能主页）
                if (restoredTabs.isNotEmpty()) {
                    switchToTab(0)
                }
                
                onTabsLoaded(restoredTabs)
                Log.d(TAG, "恢复组 $groupId 的 ${restoredTabs.size} 个标签页")
            }
        } catch (e: Exception) {
            Log.e(TAG, "加载组标签页失败", e)
            // 🔧 修复：加载失败时，创建功能主页而不是百度首页
            val functionalHomeUrl = "home://functional"
            val defaultTab = addTab(functionalHomeUrl, "快捷入口", groupId)
            onTabsLoaded(listOf(defaultTab))
        }
    }

    /**
     * 保存历史访问记录（精准记录每次访问）
     */
    private fun saveHistoryEntry(url: String, title: String, finalUrl: String) {
        try {
            val historyJson = historyPrefs.getString("history_data", "[]")
            val type = object : TypeToken<MutableList<HistoryEntry>>() {}.type
            val historyList = if (historyJson != null && historyJson.isNotEmpty()) {
                try {
                    gson.fromJson<MutableList<HistoryEntry>>(historyJson, type) ?: mutableListOf()
                } catch (e: Exception) {
                    mutableListOf()
                }
            } else {
                mutableListOf()
            }
            
            // 创建新的历史记录条目，记录当前组ID
            val newEntry = HistoryEntry(
                id = System.currentTimeMillis().toString(),
                title = if (title.isNotEmpty() && title != finalUrl) title else {
                    // 如果没有标题，尝试从URL提取
                    try {
                        val uri = java.net.URI(finalUrl)
                        uri.host ?: finalUrl
                    } catch (e: Exception) {
                        finalUrl
                    }
                },
                url = finalUrl,
                visitTime = Date(),
                groupId = currentGroupId // 记录当前组ID
            )
            
            // 添加到历史记录列表（即使URL相同也记录，因为是精准记录每次访问）
            historyList.add(0, newEntry)
            
            // 限制历史记录数量
            if (historyList.size > MAX_HISTORY_SIZE) {
                historyList.removeAt(historyList.size - 1)
            }
            
            // 保存到SharedPreferences
            val updatedJson = gson.toJson(historyList)
            historyPrefs.edit().putString("history_data", updatedJson).apply()
            
            Log.d(TAG, "保存历史记录: ${newEntry.title}, URL: $finalUrl, 总数: ${historyList.size}")
        } catch (e: Exception) {
            Log.e(TAG, "保存历史记录失败", e)
        }
    }

    /**
     * 检查当前标签页是否可以返回
     */
    fun canGoBack(): Boolean {
        val currentTab = getCurrentTab()
        return currentTab?.webView?.canGoBack() == true
    }

    /**
     * 返回上一页
     */
    fun goBack(): Boolean {
        val currentTab = getCurrentTab()
        val webView = currentTab?.webView
        
        if (webView != null && webView.canGoBack()) {
            webView.goBack()
            Log.d(TAG, "当前标签页返回上一页: ${currentTab.title}")
            return true
        }
        
        Log.d(TAG, "当前标签页无法返回")
        return false
    }

    /**
     * 检查当前标签页是否可以前进
     */
    fun canGoForward(): Boolean {
        val currentTab = getCurrentTab()
        return currentTab?.webView?.canGoForward() == true
    }

    /**
     * 前进下一页
     */
    fun goForward(): Boolean {
        val currentTab = getCurrentTab()
        val webView = currentTab?.webView
        
        if (webView != null && webView.canGoForward()) {
            webView.goForward()
            Log.d(TAG, "当前标签页前进下一页: ${currentTab.title}")
            return true
        }
        
        Log.d(TAG, "当前标签页无法前进")
        return false
    }

    /**
     * 设置标签页创建监听器
     */
    fun setOnTabCreatedListener(listener: (WebViewTab) -> Unit) {
        onTabCreatedListener = listener
    }

    /**
     * 设置标签页切换监听器
     */
    fun setOnTabSwitchedListener(listener: (WebViewTab, Int) -> Unit) {
        onTabSwitchedListener = listener
    }

    /**
     * 确保功能主页存在（每个组至少有一个功能主页）
     */
    private fun ensureFunctionalHomeExists(groupId: String?) {
        if (groupId == null) return
        
        // 检查当前组是否已经有功能主页
        val hasFunctionalHome = tabs.any { tab ->
            tab.groupId == groupId && 
            (tab.url == "home://functional" || tab.url == "file:///android_asset/functional_home.html")
        }
        
        // 如果没有功能主页，创建一个
        if (!hasFunctionalHome) {
            val functionalHomeUrl = "home://functional"
            // 注意：这里不能直接调用addTab，因为会导致递归调用
            // 应该创建一个新的标签页，但不触发ensureFunctionalHomeExists
            val tabId = "tab_${System.currentTimeMillis()}"
            val webView = PaperWebView(context)
            webView.setupWebView()
            
            val tab = WebViewTab(
                id = tabId,
                webView = webView,
                title = "快捷入口",
                url = functionalHomeUrl,
                isActive = false,
                stackIndex = tabs.size,
                groupId = groupId,
                isLazyLoaded = false
            )
            
            container.addView(webView)
            tabs.add(0, tab) // 将功能主页添加到第一个位置
            
            // 设置功能主页接口
            setupFunctionalHomeInterface(webView)
            webView.loadUrl("file:///android_asset/functional_home.html")
            
            Log.d(TAG, "组 $groupId 缺少功能主页，已自动创建")
        }
    }
    
    /**
     * 清理所有标签页
     */
    fun cleanup() {
        tabs.forEach { tab ->
            container.removeView(tab.webView)
            tab.webView.destroy()
        }
        tabs.clear()
        currentTabIndex = 0
    }

    /**
     * 处理触摸事件
     */
    fun onTouchEvent(event: MotionEvent): Boolean {
        // 如果没有标签页，不处理触摸事件
        if (tabs.isEmpty()) return false
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                swipeStartX = event.x
                swipeStartY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                isSwipeStarted = false
                swipeDirection = SwipeDirection.NONE
                touchDownTime = System.currentTimeMillis()
                // 立即清除文本选择状态，防止误触发
                isTextSelectionActive = false
                Log.d(TAG, "触摸开始: x=${event.x}, y=${event.y}")
            }
            
            MotionEvent.ACTION_MOVE -> {
                // 更新触摸坐标
                lastTouchX = event.x
                lastTouchY = event.y
                
                if (!isSwipeStarted) {
                    val deltaX = abs(event.x - swipeStartX)
                    val deltaY = abs(event.y - swipeStartY)
                    
                    // 进一步降低滑动检测阈值，提高响应性
                    if (deltaX > 15f || deltaY > 15f) {
                        isSwipeStarted = true
                        // 确定滑动方向 - 优化方向判断逻辑
                        swipeDirection = if (deltaX > deltaY * 1.3f) {
                            SwipeDirection.HORIZONTAL
                        } else if (deltaY > deltaX * 1.1f) {
                            SwipeDirection.VERTICAL
                        } else {
                            SwipeDirection.NONE
                        }
                        
                        Log.d(TAG, "滑动开始: 方向=${swipeDirection}, deltaX=$deltaX, deltaY=$deltaY")
                        
                        // 如果是横向滑动，阻止WebView的滚动，并清除文本选择
                        if (swipeDirection == SwipeDirection.HORIZONTAL) {
                            // 立即清除文本选择，防止文本选择菜单弹出
                            clearTextSelection()
                            isTextSelectionActive = false
                            // 取消所有WebView的长按事件
                            tabs.forEach { it.webView.cancelLongPress() }
                            return true
                        }
                    }
                } else if (swipeDirection == SwipeDirection.HORIZONTAL) {
                    // 横向滑动过程中，继续阻止WebView滚动，并保持清除文本选择状态
                    isTextSelectionActive = false
                    // 持续取消长按事件
                    tabs.forEach { it.webView.cancelLongPress() }
                    return true
                }
            }
            
            MotionEvent.ACTION_UP -> {
                val currentTime = System.currentTimeMillis()
                val touchDuration = currentTime - touchDownTime
                
                if (isSwipeStarted && swipeDirection == SwipeDirection.HORIZONTAL) {
                    val deltaX = event.x - swipeStartX
                    val deltaY = event.y - swipeStartY
                    
                    Log.d(TAG, "滑动结束: deltaX=$deltaX, deltaY=$deltaY, 阈值=$SWIPE_THRESHOLD, 持续时间=${touchDuration}ms")
                    
                    // 横向滑动时，确保清除文本选择，防止菜单弹出
                    clearTextSelection()
                    isTextSelectionActive = false
                    
                    // 检查是否满足滑动条件 - 进一步降低阈值提高响应性
                    val effectiveThreshold = if (touchDuration < 300) SWIPE_THRESHOLD * 0.5f else SWIPE_THRESHOLD * 0.7f
                    if (abs(deltaX) > effectiveThreshold && abs(deltaX) > abs(deltaY) * 1.1f) {
                        if (deltaX > 0) {
                            // 右滑 - 切换到上一个标签页
                            Log.d(TAG, "右滑检测到，切换到上一个标签页")
                            val prevIndex = if (currentTabIndex > 0) currentTabIndex - 1 else tabs.size - 1
                            switchToTab(prevIndex, SwipeDirection.RIGHT)
                        } else {
                            // 左滑 - 切换到下一个标签页
                            Log.d(TAG, "左滑检测到，切换到下一个标签页")
                            val nextIndex = if (currentTabIndex < tabs.size - 1) currentTabIndex + 1 else 0
                            switchToTab(nextIndex, SwipeDirection.LEFT)
                        }
                        return true
                    }
                }
                
                // 重置状态
                isSwipeStarted = false
                swipeDirection = SwipeDirection.NONE
                lastTouchTime = currentTime
            }
            
            MotionEvent.ACTION_CANCEL -> {
                // 重置状态
                isSwipeStarted = false
                swipeDirection = SwipeDirection.NONE
            }
        }
        
        // 只有在非横向滑动时才传递给WebView
        return if (swipeDirection == SwipeDirection.HORIZONTAL) {
            true
        } else {
            false
        }
    }
    
    /**
     * 执行后退操作并添加动画
     */
    private fun goBackWithAnimation(webView: WebView) {
        if (isAnimating || !webView.canGoBack()) {
            Log.d(TAG, "无法后退: isAnimating=$isAnimating, canGoBack=${webView.canGoBack()}")
            return
        }
        
        isAnimating = true
        val containerWidth = container.width.toFloat()
        val swipeDistance = if (containerWidth > 0) containerWidth else 800f
        
        Log.d(TAG, "开始后退动画")
        
        // 后退：新页面从右侧滑入，模拟iOS后退效果
        // 先将WebView移到右侧（上一页位置）
        webView.translationX = swipeDistance
        webView.alpha = 0.7f
        
        // 执行后退操作
        webView.goBack()
        
        // 创建滑入动画：从右侧滑入到中心，同时透明度增加
        val slideInAnimator = ObjectAnimator.ofFloat(webView, "translationX", swipeDistance, 0f).apply {
            duration = ANIMATION_DURATION
            interpolator = DecelerateInterpolator()
        }
        
        val alphaAnimator = ObjectAnimator.ofFloat(webView, "alpha", 0.7f, 1f).apply {
            duration = ANIMATION_DURATION
            interpolator = DecelerateInterpolator()
        }
        
        val animatorSet = AnimatorSet().apply {
            playTogether(slideInAnimator, alphaAnimator)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // 确保位置和透明度正确
                    webView.translationX = 0f
                    webView.alpha = 1f
                    
                    isAnimating = false
                    Log.d(TAG, "后退动画完成")
                }
                
                override fun onAnimationCancel(animation: Animator) {
                    webView.translationX = 0f
                    webView.alpha = 1f
                    isAnimating = false
                }
            })
        }
        
        // 延迟一小段时间再开始动画，让goBack()先执行
        webView.postDelayed({
            animatorSet.start()
        }, 50)
    }
    
    /**
     * 执行前进操作并添加动画
     */
    private fun goForwardWithAnimation(webView: WebView) {
        if (isAnimating || !webView.canGoForward()) {
            Log.d(TAG, "无法前进: isAnimating=$isAnimating, canGoForward=${webView.canGoForward()}")
            return
        }
        
        isAnimating = true
        val containerWidth = container.width.toFloat()
        val swipeDistance = if (containerWidth > 0) containerWidth else 800f
        
        Log.d(TAG, "开始前进动画")
        
        // 前进：新页面从左侧滑入，模拟iOS前进效果
        // 先将WebView移到左侧（下一页位置）
        webView.translationX = -swipeDistance
        webView.alpha = 0.7f
        
        // 执行前进操作
        webView.goForward()
        
        // 创建滑入动画：从左侧滑入到中心，同时透明度增加
        val slideInAnimator = ObjectAnimator.ofFloat(webView, "translationX", -swipeDistance, 0f).apply {
            duration = ANIMATION_DURATION
            interpolator = DecelerateInterpolator()
        }
        
        val alphaAnimator = ObjectAnimator.ofFloat(webView, "alpha", 0.7f, 1f).apply {
            duration = ANIMATION_DURATION
            interpolator = DecelerateInterpolator()
        }
        
        val animatorSet = AnimatorSet().apply {
            playTogether(slideInAnimator, alphaAnimator)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // 确保位置和透明度正确
                    webView.translationX = 0f
                    webView.alpha = 1f
                    
                    isAnimating = false
                    Log.d(TAG, "前进动画完成")
                }
                
                override fun onAnimationCancel(animation: Animator) {
                    webView.translationX = 0f
                    webView.alpha = 1f
                    isAnimating = false
                }
            })
        }
        
        // 延迟一小段时间再开始动画，让goForward()先执行
        webView.postDelayed({
            animatorSet.start()
        }, 50)
    }
    
    /**
     * 检查是否在文本选择区域
     */
    private fun isInTextSelectionArea(event: MotionEvent): Boolean {
        val currentTab = getCurrentTab()
        if (currentTab != null) {
            val webView = currentTab.webView
            
            // 检查触摸位置是否在文本区域内
            val hitTestResult = webView.hitTestResult
            val isInTextArea = hitTestResult.type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                              hitTestResult.type == WebView.HitTestResult.EDIT_TEXT_TYPE ||
                              hitTestResult.type == WebView.HitTestResult.UNKNOWN_TYPE
            
            // 如果已经在文本选择状态，继续保持
            if (isTextSelectionActive) {
                return true
            }
            
            return isInTextArea
        }
        return false
    }
    
    /**
     * 清除WebView中的文本选择，防止文本选择菜单弹出
     */
    private fun clearTextSelection() {
        try {
            // 获取所有标签页的WebView，不仅仅是当前标签页
            tabs.forEach { tab ->
                val webView = tab.webView
                // 通过JavaScript清除文本选择
                webView.evaluateJavascript("""
                    (function() {
                        if (window.getSelection) {
                            window.getSelection().removeAllRanges();
                        }
                        if (document.selection && document.selection.empty) {
                            document.selection.empty();
                        }
                        var activeElement = document.activeElement;
                        if (activeElement && activeElement.blur) {
                            activeElement.blur();
                        }
                        // 禁用文本选择
                        if (document.body) {
                            document.body.style.webkitUserSelect = 'none';
                            document.body.style.userSelect = 'none';
                        }
                    })();
                """.trimIndent(), null)
                
                // 清除WebView的文本选择状态和焦点
                webView.clearFocus()
                
                // 取消任何可能的长按事件
                webView.cancelLongPress()
            }
            
            // 重置文本选择状态标志
            isTextSelectionActive = false
            
            Log.d(TAG, "已清除所有WebView的文本选择")
        } catch (e: Exception) {
            Log.e(TAG, "清除文本选择失败", e)
        }
    }
    
    /**
     * 滑动方向枚举
     */
    enum class SwipeDirection {
        NONE, HORIZONTAL, VERTICAL, LEFT, RIGHT
    }

    /**
     * 标签页WebView类
     */
    /**
     * 检测是否为下载链接
     */
    private fun isDownloadUrl(url: String, contentType: String?): Boolean {
        if (url.isBlank()) return false
        
        val lowerUrl = url.lowercase()
        
        // 检测文件扩展名
        val downloadExtensions = listOf(
            ".apk", ".zip", ".rar", ".7z", ".tar", ".gz", ".bz2",
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".mp4", ".avi", ".mkv", ".mov", ".wmv", ".flv", ".webm",
            ".mp3", ".wav", ".flac", ".aac", ".ogg",
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp"
        )
        
        if (downloadExtensions.any { lowerUrl.endsWith(it) }) {
            return true
        }
        
        // 检测Content-Type
        contentType?.let {
            val lowerContentType = it.lowercase()
            if (lowerContentType.contains("application/octet-stream") ||
                lowerContentType.contains("application/zip") ||
                lowerContentType.contains("application/x-rar-compressed") ||
                lowerContentType.contains("application/pdf") ||
                lowerContentType.startsWith("application/vnd.android.package-archive") ||
                lowerContentType.startsWith("video/") ||
                lowerContentType.startsWith("audio/")) {
                return true
            }
        }
        
        // 检测URL参数中的下载标识
        if (lowerUrl.contains("download=true") ||
            lowerUrl.contains("action=download") ||
            lowerUrl.contains("/download/") ||
            lowerUrl.contains("/file/")) {
            return true
        }
        
        return false
    }
    
    /**
     * 处理下载请求
     */
    private fun handleDownloadRequest(
        url: String,
        userAgent: String,
        contentDisposition: String,
        mimeType: String,
        contentLength: Long
    ) {
        Log.d(TAG, "🔽 处理下载请求: url=$url")
        Log.d(TAG, "🔽 MIME类型: $mimeType")
        Log.d(TAG, "🔽 文件大小: $contentLength bytes")
        
        try {
            // 检查URL是否有效
            if (!URLUtil.isValidUrl(url)) {
                Log.e(TAG, "❌ 无效的下载URL: $url")
                Toast.makeText(context, "无效的下载链接", Toast.LENGTH_SHORT).show()
                return
            }
            
            // 使用智能下载功能，自动根据文件类型选择合适的目录
            Log.d(TAG, "🔽 使用智能下载功能")
            enhancedDownloadManager.downloadSmart(url, object : EnhancedDownloadManager.DownloadCallback {
                override fun onDownloadSuccess(downloadId: Long, localUri: String?, fileName: String?) {
                    Log.d(TAG, "✅ 文件下载成功: $fileName")
                    Toast.makeText(context, "文件下载完成", Toast.LENGTH_SHORT).show()
                }
                
                override fun onDownloadFailed(downloadId: Long, reason: Int) {
                    Log.e(TAG, "❌ 文件下载失败: $reason")
                    Toast.makeText(context, "文件下载失败", Toast.LENGTH_SHORT).show()
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "❌ 下载处理失败", e)
            Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 处理特殊 scheme URL（如 intent://、douban://、clash:// 等）
     * 直接启动Intent，让系统显示应用选择对话框（类似 Chrome）
     * @param url URL 字符串
     * @param view WebView 实例
     * @return true 表示已处理，false 表示非特殊 scheme
     */
    private fun handleSpecialSchemeUrl(url: String, view: WebView?): Boolean {
        if (url.isBlank()) {
            Log.d(TAG, "handleSpecialSchemeUrl: URL 为空")
            return false
        }
        
        val lower = url.lowercase()
        
        // 检查是否为 HTTP/HTTPS，这些应该在 WebView 中加载
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return false
        }
        
        // 检查是否为特殊 scheme
        val isSpecialScheme = when {
            lower.startsWith("intent://") -> true
            lower.startsWith("clash://") -> true
            lower.startsWith("douban://") -> true
            lower.startsWith("baidumap://") -> true
            lower.startsWith("amap://") -> true
            lower.startsWith("alipay://") -> true
            lower.startsWith("wechat://") -> true
            lower.startsWith("weixin://") -> true
            lower.startsWith("qq://") -> true
            lower.contains("://") && !lower.startsWith("http://") && !lower.startsWith("https://") && 
            !lower.startsWith("file://") && !lower.startsWith("javascript:") -> true
            else -> false
        }
        
        if (!isSpecialScheme) {
            return false
        }
        
        // 对于特殊 scheme，直接启动Intent（类似 Chrome，让系统显示对话框）
        Log.d(TAG, "检测到特殊 scheme URL: $url，直接启动Intent")
        
        // 在主线程启动Intent
        if (context is android.app.Activity) {
            context.runOnUiThread {
                launchSchemeUrlDirectly(url)
            }
        } else {
            // 如果不是 Activity，尝试直接启动
            try {
                launchSchemeUrlDirectly(url)
            } catch (e: Exception) {
                Log.e(TAG, "处理特殊 scheme 失败: $url", e)
            }
        }
        
        return true // 返回 true 表示已处理，阻止在 WebView 中加载
    }
    
    /**
     * 直接启动 scheme URL（类似 Chrome，让系统显示应用选择对话框）
     */
    private fun launchSchemeUrlDirectly(schemeUrl: String) {
        try {
            val packageManager = context.packageManager
            
            if (schemeUrl.startsWith("intent://")) {
                // 处理 intent:// URL
                val intent = android.content.Intent.parseUri(schemeUrl, android.content.Intent.URI_INTENT_SCHEME)
                intent.addCategory(android.content.Intent.CATEGORY_BROWSABLE)
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                
                // 针对 clash:// 指定优先包
                val data = intent.dataString
                if (intent.`package` == null && data != null && data.startsWith("clash://")) {
                    val clashPackages = listOf("com.github.kr328.clash", "com.github.metacubex.clash")
                    for (pkg in clashPackages) {
                        try {
                            packageManager.getPackageInfo(pkg, 0)
                            intent.`package` = pkg
                            break
                        } catch (_: Exception) { }
                    }
                }
                
                if (intent.resolveActivity(packageManager) != null) {
                    context.startActivity(intent)
                    Log.d(TAG, "直接启动 intent:// 链接成功: $schemeUrl")
                } else {
                    // 尝试 fallback URL
                    val fallback = intent.getStringExtra("browser_fallback_url")
                    if (!fallback.isNullOrBlank()) {
                        Log.d(TAG, "使用 fallback URL: $fallback")
                    } else {
                        android.widget.Toast.makeText(context, "未找到可处理的应用", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                // 处理普通 scheme URL
                val uri = android.net.Uri.parse(schemeUrl)
                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                    addCategory(android.content.Intent.CATEGORY_BROWSABLE)
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                // 针对 clash:// 指定优先包
                if (schemeUrl.startsWith("clash://")) {
                    val clashPackages = listOf("com.github.kr328.clash", "com.github.metacubex.clash")
                    for (pkg in clashPackages) {
                        try {
                            packageManager.getPackageInfo(pkg, 0)
                            intent.`package` = pkg
                            break
                        } catch (_: Exception) { }
                    }
                }
                
                if (intent.resolveActivity(packageManager) != null) {
                    // 直接启动，让系统显示应用选择对话框（类似 Chrome）
                    context.startActivity(intent)
                    Log.d(TAG, "直接启动 scheme 链接成功: $schemeUrl")
                } else {
                    android.widget.Toast.makeText(context, "未找到可处理的应用", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "直接启动 scheme 链接失败: $schemeUrl", e)
            android.widget.Toast.makeText(context, "打开应用失败", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private inner class PaperWebView(context: Context) : WebView(context) {
        var stackIndex = 0
        var isErrorState = false // 标记是否处于错误状态
        
        init {
            setupTabStyle()
        }
        
        private fun setupTabStyle() {
            // 设置标签页样式 - 完全透明，避免任何蒙版效果
            setBackgroundColor(Color.TRANSPARENT)
            setBackground(null)
            
            // 移除阴影和边框
            ViewCompat.setElevation(this, 0f)
            
            // 设置圆角
            clipToOutline = false
            
            // 设置初始变换
            scaleX = 1f
            scaleY = 1f
            alpha = 1f
            
            // 使用硬件加速，避免软件渲染的阴影
            setLayerType(LAYER_TYPE_HARDWARE, null)
        }
        
        fun setupWebView() {
            // 设置WebView完全透明
            setBackgroundColor(Color.TRANSPARENT)
            setBackground(null)
            setLayerType(LAYER_TYPE_HARDWARE, null)
            
            // 禁用系统默认的上下文菜单
            setLongClickable(true)
            setOnCreateContextMenuListener(null)
            
            // 设置长按监听器处理图片和链接
            setOnLongClickListener { view ->
                val webView = view as WebView
                
                // 如果是功能主页，屏蔽长按菜单
                val currentUrl = webView.url
                if (currentUrl == "home://functional" || currentUrl == "file:///android_asset/functional_home.html") {
                    Log.d(TAG, "功能主页长按，屏蔽菜单")
                    return@setOnLongClickListener true // 返回true阻止默认行为
                }
                
                val result = webView.hitTestResult
                
                Log.d(TAG, "WebView长按检测: type=${result.type}, extra=${result.extra}")
                
                // 如果是横向滑动，不处理长按
                if (this@PaperStackWebViewManager.swipeDirection == SwipeDirection.HORIZONTAL) {
                    Log.d(TAG, "横向滑动中，忽略长按事件")
                    return@setOnLongClickListener true
                }
                
                // 获取触摸点在屏幕上的坐标
                val location = IntArray(2)
                webView.getLocationOnScreen(location)
                val screenX = (this@PaperStackWebViewManager.lastTouchX + location[0]).toInt()
                val screenY = (this@PaperStackWebViewManager.lastTouchY + location[1]).toInt()
                
                // 精准识别：通过URL和类型综合判断
                val url = result.extra
                
                // 判断是否为图片URL（通过文件扩展名和MIME类型）
                fun isImageUrl(urlString: String?): Boolean {
                    if (urlString.isNullOrEmpty()) return false
                    val lowerUrl = urlString.lowercase()
                    val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".svg", ".ico")
                    val imageMimeTypes = listOf("image/", "data:image/")
                    return imageExtensions.any { lowerUrl.contains(it) } || 
                           imageMimeTypes.any { lowerUrl.contains(it) } ||
                           lowerUrl.matches(Regex(".*/(img|image|photo|pic|picture).*"))
                }
                
                // 精准识别逻辑
                when (result.type) {
                    // 纯图片（不包含链接）- 肯定是图片
                    WebView.HitTestResult.IMAGE_TYPE -> {
                        if (!url.isNullOrEmpty() && isImageUrl(url)) {
                            Log.d(TAG, "检测到纯图片，显示图片菜单: $url")
                            this@PaperStackWebViewManager.enhancedMenuManager?.showEnhancedImageMenu(
                                webView, url, screenX, screenY
                            )
                        } else {
                            Log.d(TAG, "图片URL无效，显示刷新菜单")
                            this@PaperStackWebViewManager.enhancedMenuManager?.showEnhancedRefreshMenu(
                                webView, screenX, screenY
                            )
                        }
                        true
                    }
                    // SRC_IMAGE_ANCHOR_TYPE：可能是图片链接，也可能是链接的图片
                    // 需要根据URL判断：如果是图片URL则显示图片菜单，否则显示链接菜单
                    WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                        if (!url.isNullOrEmpty()) {
                            if (isImageUrl(url)) {
                                // URL指向图片文件，显示图片菜单
                                Log.d(TAG, "检测到图片链接（URL指向图片），显示图片菜单: $url")
                                this@PaperStackWebViewManager.enhancedMenuManager?.showEnhancedImageMenu(
                                    webView, url, screenX, screenY
                                )
                            } else {
                                // URL指向网页链接，显示链接菜单
                                Log.d(TAG, "检测到链接（包含图片元素），显示链接菜单: $url")
                                this@PaperStackWebViewManager.enhancedMenuManager?.showEnhancedLinkMenu(
                                    webView, url, "", screenX, screenY
                                )
                            }
                        } else {
                            Log.d(TAG, "SRC_IMAGE_ANCHOR_TYPE URL为空，显示刷新菜单")
                            this@PaperStackWebViewManager.enhancedMenuManager?.showEnhancedRefreshMenu(
                                webView, screenX, screenY
                            )
                        }
                        true
                    }
                    // 纯链接（不包含图片）
                    WebView.HitTestResult.ANCHOR_TYPE,
                    WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                        if (!url.isNullOrEmpty()) {
                            Log.d(TAG, "检测到纯链接，显示链接菜单: $url")
                            this@PaperStackWebViewManager.enhancedMenuManager?.showEnhancedLinkMenu(
                                webView, url, "", screenX, screenY
                            )
                        } else {
                            Log.d(TAG, "链接URL为空，显示刷新菜单")
                            this@PaperStackWebViewManager.enhancedMenuManager?.showEnhancedRefreshMenu(
                                webView, screenX, screenY
                            )
                        }
                        true
                    }
                    else -> {
                        // 其他类型显示刷新菜单
                        Log.d(TAG, "显示刷新菜单（其他类型: ${result.type}）")
                        this@PaperStackWebViewManager.enhancedMenuManager?.showEnhancedRefreshMenu(
                            webView, screenX, screenY
                        )
                        true
                    }
                }
            }
            
            // 设置触摸监听器来检测文本选择
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 检测是否在文本区域
                        val hitTestResult = hitTestResult
                        isTextSelectionActive = hitTestResult.type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                                               hitTestResult.type == WebView.HitTestResult.EDIT_TEXT_TYPE ||
                                               hitTestResult.type == WebView.HitTestResult.UNKNOWN_TYPE
                    }
                    MotionEvent.ACTION_UP -> {
                        // 延迟重置文本选择状态
                        postDelayed({
                            isTextSelectionActive = false
                        }, 1000)
                    }
                }
                false // 不拦截事件，让WebView正常处理
            }
            
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                
                // 性能优化
                setRenderPriority(WebSettings.RenderPriority.HIGH)
                cacheMode = WebSettings.LOAD_DEFAULT
                allowContentAccess = true
                allowFileAccess = true
                databaseEnabled = true
                
                // 用户代理
                userAgentString = WebViewConstants.MOBILE_USER_AGENT
                
                // 移动端优化
                textZoom = 100
                minimumFontSize = 8
                setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING)
            }
            
            // 设置WebChromeClient监听favicon
            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onReceivedIcon(view: WebView?, icon: android.graphics.Bitmap?) {
                    super.onReceivedIcon(view, icon)
                    // 查找对应的标签页并通知监听器
                    if (view != null) {
                        val tab = tabs.find { it.webView == view }
                        if (tab != null) {
                            onFaviconReceivedListener?.invoke(tab, icon)
                        }
                    }
                }
                
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    // 更新标签页标题
                    if (view != null) {
                        val tab = tabs.find { it.webView == view }
                        if (tab != null) {
                            if (title != null) {
                                tab.title = title
                            }
                            // 通知标题更新监听器
                            onTitleReceivedListener?.invoke(tab, title)
                        }
                    }
                }
            }
            
            // 设置下载监听器 - 这是处理下载的正确方式
            setDownloadListener { url, userAgent, contentDisposition, mimeType, contentLength ->
                Log.d(TAG, "🔽 WebView下载请求: url=$url, mimeType=$mimeType, contentLength=$contentLength")
                handleDownloadRequest(url, userAgent, contentDisposition, mimeType, contentLength)
            }
            
            // 设置WebViewClient
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                    val url = request?.url?.toString()
                    Log.d(TAG, "PaperWebView URL加载拦截: $url")
                    
                    if (url != null) {
                        // 检测是否为下载链接
                        if (isDownloadUrl(url, request?.requestHeaders?.get("Content-Type"))) {
                            Log.d(TAG, "🔽 检测到下载链接，拦截并下载: $url")
                            handleDownloadRequest(url, "", "", "", 0)
                            return true
                        }
                        return handleSpecialSchemeUrl(url, view)
                    }
                    return false
                }
                
                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    Log.d(TAG, "PaperWebView URL加载拦截 (legacy): $url")
                    
                    if (url != null) {
                        // 检测是否为下载链接
                        if (isDownloadUrl(url, null)) {
                            Log.d(TAG, "🔽 检测到下载链接，拦截并下载: $url")
                            handleDownloadRequest(url, "", "", "", 0)
                            return true
                        }
                        return handleSpecialSchemeUrl(url, view)
                    }
                    return false
                }
                
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    
                    // 新页面开始加载：重置错误状态，恢复透明背景
                    if (view is PaperWebView) {
                        Log.d(TAG, "页面开始加载，重置错误状态并恢复透明背景: $url")
                        view.isErrorState = false
                        // 恢复透明背景，避免之前的错误状态影响
                        view.setBackgroundColor(Color.TRANSPARENT)
                    }
                    
                    // 通知页面开始加载监听器
                    if (view != null) {
                        val tab = tabs.find { it.webView == view }
                        if (tab != null) {
                            onPageStartedListener?.invoke(tab, url)
                        }
                    }
                }
                
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    
                    // 页面加载完成：如果成功加载（非错误状态），确保背景透明
                    if (view is PaperWebView && !view.isErrorState) {
                        Log.d(TAG, "页面加载完成，确保透明背景: $url")
                        view.setBackgroundColor(Color.TRANSPARENT)
                    }
                    
                    // 保存历史访问记录（精准记录每次访问）
                    if (url != null && url.isNotEmpty() && !url.startsWith("javascript:") && url != "about:blank") {
                        try {
                            saveHistoryEntry(url, view?.title ?: url, view?.url ?: url)
                        } catch (e: Exception) {
                            Log.e(TAG, "保存历史记录失败", e)
                        }
                    }
                    
                    // 查找对应的标签页并更新favicon
                    if (view != null) {
                        val tab = tabs.find { it.webView == view }
                        if (tab != null && view.favicon != null) {
                            onFaviconReceivedListener?.invoke(tab, view.favicon)
                        }
                    }
                    
                    // 注入viewport meta标签
                    view?.evaluateJavascript("""
                        (function() {
                            try {
                                var viewportMeta = document.querySelector('meta[name="viewport"]');
                                if (viewportMeta) {
                                    viewportMeta.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes');
                                } else {
                                    var meta = document.createElement('meta');
                                    meta.name = 'viewport';
                                    meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes';
                                    document.head.appendChild(meta);
                                }
                                document.documentElement.style.setProperty('--mobile-viewport', '1');
                            } catch (e) {
                                console.error('Failed to inject viewport meta tag:', e);
                            }
                        })();
                    """.trimIndent(), null)
                    
                    // 注入下载拦截脚本
                    view?.evaluateJavascript("""
                        (function() {
                            try {
                                // 拦截所有下载按钮和链接的点击
                                document.addEventListener('click', function(e) {
                                    var target = e.target;
                                    // 检查按钮文本是否包含"下载"、"立即下载"等关键词
                                    var buttonText = target.textContent || target.innerText || '';
                                    if (buttonText.includes('下载') || buttonText.includes('Download')) {
                                        // 查找最近的链接或按钮
                                        var link = target.closest('a') || target;
                                        if (link.href) {
                                            e.preventDefault();
                                            e.stopPropagation();
                                            // 触发下载
                                            window.location.href = link.href;
                                            return false;
                                        }
                                    }
                                }, true);
                                
                                // 拦截所有a标签的点击，检测下载链接
                                var links = document.querySelectorAll('a[href]');
                                links.forEach(function(link) {
                                    link.addEventListener('click', function(e) {
                                        var href = this.href;
                                        // 检测是否为下载链接
                                        if (href.match(/\.(apk|zip|rar|7z|tar|gz|pdf|doc|docx|xls|xlsx|ppt|pptx|mp4|avi|mkv|mov|mp3|wav|flac)$/i) ||
                                            this.download ||
                                            this.getAttribute('download')) {
                                            e.preventDefault();
                                            e.stopPropagation();
                                            window.location.href = href;
                                            return false;
                                        }
                                    });
                                });
                            } catch (e) {
                                console.error('Failed to inject download interceptor:', e);
                            }
                        })();
                    """.trimIndent(), null)
                    
                    // 通知页面加载完成监听器（用于注入视频检测脚本）
                    if (view != null) {
                        val tab = tabs.find { it.webView == view }
                        if (tab != null) {
                            onPageFinishedListener?.invoke(tab, url)
                        }
                    }
                }
                
                override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                    val errorUrl = request?.url?.toString()
                    val errorCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        error?.errorCode
                    } else {
                        -1
                    }
                    val errorDescription = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        error?.description?.toString()
                    } else {
                        "Unknown error"
                    }
                    
                    Log.e(TAG, "PaperWebView加载错误: $errorDescription, URL: $errorUrl, ErrorCode: $errorCode")
                    
                    // 检查是否为 ERR_UNKNOWN_URL_SCHEME 错误，且 URL 是特殊 scheme
                    if (request?.isForMainFrame == true && errorUrl != null) {
                        if (errorCode == -2 || errorDescription?.contains("ERR_UNKNOWN_URL_SCHEME") == true || 
                            errorDescription?.contains("net::ERR_UNKNOWN_URL_SCHEME") == true) {
                            // 检查是否为特殊 scheme
                            val lower = errorUrl.lowercase()
                            val isSpecialScheme = lower.startsWith("intent://") || 
                                                 lower.startsWith("clash://") ||
                                                 lower.startsWith("douban://") ||
                                                 (lower.contains("://") && !lower.startsWith("http://") && !lower.startsWith("https://"))
                            
                            if (isSpecialScheme) {
                                // 特殊 scheme 导致的错误，直接启动Intent（类似 Chrome）
                                Log.d(TAG, "检测到特殊 scheme 错误，直接启动Intent: $errorUrl")
                                handleSpecialSchemeUrl(errorUrl, view)
                                // 不调用 super.onReceivedError，避免显示错误页面和可能的循环
                                return
                            }
                        }
                        
                        // 主框架加载错误：设置WebView背景为不透明的白色，避免透视下方页面
                        Log.d(TAG, "检测到主框架错误，设置WebView背景为白色，避免透明背景透视问题")
                        if (view is PaperWebView) {
                            view.setBackgroundColor(Color.WHITE)
                            // 标记为错误状态
                            view.isErrorState = true
                        } else {
                            // 如果不是PaperWebView，仍然设置背景，但无法标记状态
                            view?.setBackgroundColor(Color.WHITE)
                        }
                    }
                    
                    super.onReceivedError(view, request, error)
                }
                
                @Deprecated("Deprecated in Java")
                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                    Log.e(TAG, "PaperWebView加载错误 (legacy): $description, URL: $failingUrl, ErrorCode: $errorCode")
                    
                    // 检查是否为 ERR_UNKNOWN_URL_SCHEME 错误（错误代码 -2），且 URL 是特殊 scheme
                    if (errorCode == -2 || description?.contains("ERR_UNKNOWN_URL_SCHEME") == true || 
                        description?.contains("net::ERR_UNKNOWN_URL_SCHEME") == true) {
                        if (failingUrl != null) {
                            val lower = failingUrl.lowercase()
                            val isSpecialScheme = lower.startsWith("intent://") || 
                                                 lower.startsWith("clash://") ||
                                                 lower.startsWith("douban://") ||
                                                 (lower.contains("://") && !lower.startsWith("http://") && !lower.startsWith("https://"))
                            
                            if (isSpecialScheme) {
                                // 特殊 scheme 导致的错误，直接启动Intent（类似 Chrome）
                                Log.d(TAG, "检测到特殊 scheme 错误 (legacy)，直接启动Intent: $failingUrl")
                                handleSpecialSchemeUrl(failingUrl, view)
                                // 不调用 super.onReceivedError，避免显示错误页面和可能的循环
                                return
                            }
                        }
                    }
                    
                    // 主框架加载错误：设置WebView背景为不透明的白色，避免透视下方页面
                    if (failingUrl != null && errorCode != -2) {
                        // 排除特殊scheme错误，只处理真正的网络错误
                        val lower = failingUrl.lowercase()
                        val isNetworkUrl = lower.startsWith("http://") || lower.startsWith("https://")
                        if (isNetworkUrl) {
                            Log.d(TAG, "检测到网络错误 (legacy)，设置WebView背景为白色，避免透明背景透视问题")
                            view?.setBackgroundColor(Color.WHITE)
                            // 标记为错误状态
                            if (view is PaperWebView) {
                                view.isErrorState = true
                            }
                        }
                    }
                    
                    super.onReceivedError(view, errorCode, description, failingUrl)
                }
            }
        }
        
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            // 移除阴影和边框绘制，避免灰色蒙版效果
        }
    }
}
