package com.example.aifloatingball.webview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebViewClient
import android.webkit.URLUtil
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.webkit.WebView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.aifloatingball.R
import com.example.aifloatingball.download.EnhancedDownloadManager
import com.example.aifloatingball.utils.FaviconLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 增强版菜单管理器
 * 提供全功能的图片、链接和页面操作菜单
 */
class EnhancedMenuManager(
    private val context: Context,
    private val windowManager: WindowManager
) {
    
    companion object {
        private const val TAG = "EnhancedMenuManager"
        private const val MENU_AUTO_HIDE_DELAY = 10000L
        private const val OVERLAY_WINDOW_TYPE = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        private const val MENU_WINDOW_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                              WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    }
    
    private var currentWebView: WebView? = null
    private var floatingMenuView: View? = null
    private var previewWindowView: View? = null // 预览窗视图
    private var previewWebView: WebView? = null // 预览WebView
    private val isMenuShowing = AtomicBoolean(false)
    private val isMenuAnimating = AtomicBoolean(false)
    private val isPreviewShowing = AtomicBoolean(false) // 预览窗显示状态
    private val handler = Handler(Looper.getMainLooper())
    private var autoHideRunnable: Runnable? = null
    private var previewWindowParams: WindowManager.LayoutParams? = null
    private var previewInitialX = 0f
    private var previewInitialY = 0f
    private var previewInitialTouchX = 0f
    private var previewInitialTouchY = 0f
    private var isPreviewDragging = false
    
    // 增强下载管理器
    private val enhancedDownloadManager: EnhancedDownloadManager by lazy {
        EnhancedDownloadManager(context)
    }
    
    // 新标签页监听器
    private var onNewTabListener: ((String, Boolean) -> Unit)? = null
    
    /**
     * 设置新标签页监听器
     */
    fun setOnNewTabListener(listener: (String, Boolean) -> Unit) {
        this.onNewTabListener = listener
    }
    
    /**
     * 显示增强版图片菜单
     */
    fun showEnhancedImageMenu(webView: WebView, imageUrl: String, x: Int, y: Int) {
        // 如果是功能主页，屏蔽菜单
        val currentUrl = webView.url
        if (currentUrl == "home://functional" || currentUrl == "file:///android_asset/functional_home.html") {
            Log.d(TAG, "功能主页，屏蔽图片菜单")
            return
        }
        Log.d(TAG, "显示增强版图片菜单: $imageUrl")
        
        if (isMenuShowing.get() || isMenuAnimating.get()) {
            hideMenu(true)
            handler.postDelayed({
                doShowEnhancedImageMenu(webView, imageUrl, x, y)
            }, 160)
            return
        }
        
        doShowEnhancedImageMenu(webView, imageUrl, x, y)
    }
    
    private fun doShowEnhancedImageMenu(webView: WebView, imageUrl: String, x: Int, y: Int) {
        try {
            currentWebView = webView
            
            val themedContext = android.view.ContextThemeWrapper(context, R.style.Theme_AIFloatingBall)
            floatingMenuView = LayoutInflater.from(themedContext)
                .inflate(R.layout.enhanced_image_menu_wrapper, null)
            
            val menuContent = floatingMenuView!!.findViewById<View>(R.id.enhanced_image_menu_content)!!
            
            // 设置动画初始状态
            menuContent.alpha = 0f
            menuContent.scaleX = 0.8f
            menuContent.scaleY = 0.8f
            
            // 设置触摸监听器 - 点击空白处关闭菜单
            floatingMenuView!!.setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val contentRect = android.graphics.Rect()
                    menuContent.getGlobalVisibleRect(contentRect)
                    // 获取触摸点的全局坐标
                    val touchX = event.rawX.toInt()
                    val touchY = event.rawY.toInt()
                    
                    // 如果触摸点不在菜单内容区域内，关闭菜单
                    if (!contentRect.contains(touchX, touchY)) {
                        Log.d(TAG, "点击菜单外部区域，关闭图片菜单")
                        hideMenu()
                        return@setOnTouchListener true
                    }
                }
                false // 允许事件传递给菜单内容
            }
            
            // 设置菜单项
            setupEnhancedImageMenuItems(menuContent, webView, imageUrl)
            
            // 加载图片预览
            loadImagePreview(menuContent, imageUrl)
            
            // 显示菜单
            showMenu(menuContent, x, y)
            
        } catch (e: Exception) {
            Log.e(TAG, "显示增强版图片菜单失败", e)
            cleanupState()
        }
    }
    
    /**
     * 显示增强版链接菜单
     * 如果是网页链接，显示预览悬浮窗；否则显示普通菜单
     */
    fun showEnhancedLinkMenu(webView: WebView, url: String, title: String, x: Int, y: Int) {
        // 如果是功能主页，屏蔽菜单
        val currentUrl = webView.url
        if (currentUrl == "home://functional" || currentUrl == "file:///android_asset/functional_home.html") {
            Log.d(TAG, "功能主页，屏蔽链接菜单")
            return
        }
        Log.d(TAG, "显示增强版链接菜单: $url")
        
        // 检测链接类型，判断是否为可预览的网页链接
        if (isPreviewableUrl(url)) {
            // 网页链接，显示预览悬浮窗
            Log.d(TAG, "检测到网页链接，显示预览悬浮窗: $url")
            if (isPreviewShowing.get() || isMenuShowing.get()) {
                hidePreviewWindow(true)
                hideMenu(true)
                handler.postDelayed({
                    showLinkPreviewWindow(webView, url, title, x, y)
                }, 160)
                return
            }
            showLinkPreviewWindow(webView, url, title, x, y)
        } else {
            // 非网页链接（如mailto:、tel:等），显示普通菜单
            Log.d(TAG, "检测到非网页链接，显示普通菜单: $url")
        if (isMenuShowing.get() || isMenuAnimating.get()) {
            hideMenu(true)
            handler.postDelayed({
                doShowEnhancedLinkMenu(webView, url, title, x, y)
            }, 160)
            return
        }
        doShowEnhancedLinkMenu(webView, url, title, x, y)
        }
    }
    
    /**
     * 判断URL是否为可预览的网页链接
     */
    private fun isPreviewableUrl(url: String?): Boolean {
        if (url.isNullOrEmpty()) return false
        
        val lowerUrl = url.lowercase().trim()
        
        // 排除非网页链接协议
        val nonPreviewableSchemes = listOf(
            "mailto:", "tel:", "sms:", "smsto:", "geo:", "market:",
            "intent:", "weixin:", "mqqapi:", "taobao:", "alipay:",
            "snssdk1128:", "sinaweibo:", "bilibili:", "youtube:",
            "wework:", "tim:", "xhsdiscover:", "douban:", "twitter:",
            "zhihu:", "file:", "content:", "android.resource:"
        )
        
        // 检查是否为非预览协议
        if (nonPreviewableSchemes.any { lowerUrl.startsWith(it) }) {
            return false
        }
        
        // 检查是否为HTTP/HTTPS链接
        if (URLUtil.isHttpUrl(url) || URLUtil.isHttpsUrl(url)) {
            return true
        }
        
        // 检查是否为网络URL（包括其他协议如ftp:等，但这些通常也可以预览）
        if (URLUtil.isNetworkUrl(url)) {
            return true
        }
        
        // 如果URL包含常见域名模式，也认为是可预览的
        if (lowerUrl.matches(Regex(".*\\.(com|cn|net|org|gov|edu|io|co|me|tv|cc|so|tel|red|kim|xyz|ai|show|art|run|gold|fit|fan|ren|love|beer|luxe|yoga|fund|city|host|zone|cash|guru|pub|bid|plus|chat|law|tax|team|band|cab|tips|jobs|one|men|bet|fish|sale|game|help|gift|loan|cars|auto|care|cafe|pet|fit|hair|baby|toys|land|farm|food|wine|vote|voto|date|wed|sexy|sex|gay|porn|xxx|adult|sex|cam|xxx|porn|bet|tube|cam|pics|gay|sex|porn|xxx|loan)$", RegexOption.IGNORE_CASE))) {
            return true
        }
        
        return false
    }
    
    private fun doShowEnhancedLinkMenu(webView: WebView, url: String, title: String, x: Int, y: Int) {
        try {
            currentWebView = webView
            
            val themedContext = android.view.ContextThemeWrapper(context, R.style.Theme_AIFloatingBall)
            floatingMenuView = LayoutInflater.from(themedContext)
                .inflate(R.layout.enhanced_link_menu_wrapper, null)
            
            val menuContent = floatingMenuView!!.findViewById<View>(R.id.enhanced_link_menu_content)!!
            
            // 设置动画初始状态
            menuContent.alpha = 0f
            menuContent.scaleX = 0.8f
            menuContent.scaleY = 0.8f
            
            // 设置触摸监听器 - 点击空白处关闭菜单
            floatingMenuView!!.setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val contentRect = android.graphics.Rect()
                    menuContent.getGlobalVisibleRect(contentRect)
                    // 获取触摸点的全局坐标
                    val touchX = event.rawX.toInt()
                    val touchY = event.rawY.toInt()
                    
                    // 如果触摸点不在菜单内容区域内，关闭菜单
                    if (!contentRect.contains(touchX, touchY)) {
                        Log.d(TAG, "点击菜单外部区域，关闭链接菜单")
                        hideMenu()
                        return@setOnTouchListener true
                    }
                }
                false // 允许事件传递给菜单内容
            }
            
            // 设置菜单项
            setupEnhancedLinkMenuItems(menuContent, webView, url, title)
            
            // 加载链接预览
            loadLinkPreview(menuContent, url, title)
            
            // 显示菜单
            showMenu(menuContent, x, y)
            
        } catch (e: Exception) {
            Log.e(TAG, "显示增强版链接菜单失败", e)
            cleanupState()
        }
    }
    
    /**
     * 显示增强版刷新菜单
     */
    fun showEnhancedRefreshMenu(webView: WebView, x: Int, y: Int) {
        // 如果是功能主页，屏蔽菜单
        val currentUrl = webView.url
        if (currentUrl == "home://functional" || currentUrl == "file:///android_asset/functional_home.html") {
            Log.d(TAG, "功能主页，屏蔽刷新菜单")
            return
        }
        Log.d(TAG, "显示增强版刷新菜单")
        
        if (isMenuShowing.get() || isMenuAnimating.get()) {
            hideMenu(true)
            handler.postDelayed({
                doShowEnhancedRefreshMenu(webView, x, y)
            }, 160)
            return
        }
        
        doShowEnhancedRefreshMenu(webView, x, y)
    }
    
    private fun doShowEnhancedRefreshMenu(webView: WebView, x: Int, y: Int) {
        try {
            currentWebView = webView
            
            val themedContext = android.view.ContextThemeWrapper(context, R.style.Theme_AIFloatingBall)
            floatingMenuView = LayoutInflater.from(themedContext)
                .inflate(R.layout.enhanced_refresh_menu_wrapper, null)
            
            val menuContent = floatingMenuView!!.findViewById<View>(R.id.enhanced_refresh_menu_content)!!
            
            // 设置动画初始状态
            menuContent.alpha = 0f
            menuContent.scaleX = 0.8f
            menuContent.scaleY = 0.8f
            
            // 设置触摸监听器 - 点击空白处关闭菜单
            floatingMenuView!!.setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val contentRect = android.graphics.Rect()
                    menuContent.getGlobalVisibleRect(contentRect)
                    // 获取触摸点的全局坐标
                    val touchX = event.rawX.toInt()
                    val touchY = event.rawY.toInt()
                    
                    // 如果触摸点不在菜单内容区域内，关闭菜单
                    if (!contentRect.contains(touchX, touchY)) {
                        Log.d(TAG, "点击菜单外部区域，关闭刷新菜单")
                        hideMenu()
                        return@setOnTouchListener true
                    }
                }
                false // 允许事件传递给菜单内容
            }
            
            // 设置菜单项
            setupEnhancedRefreshMenuItems(menuContent, webView)
            
            // 加载页面预览
            loadPagePreview(menuContent, webView)
            
            // 显示菜单
            showMenu(menuContent, x, y)
            
        } catch (e: Exception) {
            Log.e(TAG, "显示增强版刷新菜单失败", e)
            cleanupState()
        }
    }
    
    /**
     * 设置增强版图片菜单项
     */
    private fun setupEnhancedImageMenuItems(menuView: View, webView: WebView, imageUrl: String) {
        // 全屏查看
        menuView.findViewById<View>(R.id.action_view_fullscreen)?.setOnClickListener {
            // 使用ImageViewerActivity全屏查看图片
            try {
                if (context is android.app.Activity) {
                    com.example.aifloatingball.viewer.ImageViewerActivity.start(context, imageUrl)
                    hideMenu()
                } else {
                    // 如果context不是Activity，使用新标签页打开
                    onNewTabListener?.invoke(imageUrl, false)
                    hideMenu()
                }
            } catch (e: Exception) {
                Log.e(TAG, "打开图片查看器失败", e)
                // 备用方案：使用新标签页打开
                onNewTabListener?.invoke(imageUrl, false)
                hideMenu()
            }
        }
        
        // 编辑图片
        menuView.findViewById<View>(R.id.action_edit_image)?.setOnClickListener {
            // TODO: 实现图片编辑功能
            Toast.makeText(context, "图片编辑功能开发中", Toast.LENGTH_SHORT).show()
            hideMenu()
        }
        
        // 保存图片
        menuView.findViewById<View>(R.id.action_save_image)?.setOnClickListener {
            try {
                val downloadId = enhancedDownloadManager.downloadImage(imageUrl, object : EnhancedDownloadManager.DownloadCallback {
                    override fun onDownloadSuccess(downloadId: Long, localUri: String?, fileName: String?) {
                        Log.d(TAG, "图片下载成功: $fileName")
                        Toast.makeText(context, "图片保存成功", Toast.LENGTH_SHORT).show()
                    }
                    
                    override fun onDownloadFailed(downloadId: Long, reason: Int) {
                        Log.e(TAG, "图片下载失败: $reason")
                        Toast.makeText(context, "图片保存失败", Toast.LENGTH_SHORT).show()
                    }
                })
                
                if (downloadId != -1L) {
                    Toast.makeText(context, "开始保存图片", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "保存图片失败", e)
                Toast.makeText(context, "保存图片失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            hideMenu()
        }
        
        // 分享图片
        menuView.findViewById<View>(R.id.action_share_image)?.setOnClickListener {
            shareContent("图片", imageUrl)
            hideMenu()
        }
        
        // 以图搜图
        menuView.findViewById<View>(R.id.action_search_by_image)?.setOnClickListener {
            // TODO: 实现以图搜图功能
            Toast.makeText(context, "以图搜图功能开发中", Toast.LENGTH_SHORT).show()
            hideMenu()
        }
        
        // 识别二维码
        menuView.findViewById<View>(R.id.action_recognize_qr)?.setOnClickListener {
            // TODO: 实现二维码识别功能
            Toast.makeText(context, "二维码识别功能开发中", Toast.LENGTH_SHORT).show()
            hideMenu()
        }
        
        // 设为壁纸
        menuView.findViewById<View>(R.id.action_set_wallpaper)?.setOnClickListener {
            // TODO: 实现设为壁纸功能
            Toast.makeText(context, "设为壁纸功能开发中", Toast.LENGTH_SHORT).show()
            hideMenu()
        }
        
        // 图片信息
        menuView.findViewById<View>(R.id.action_image_info)?.setOnClickListener {
            // TODO: 实现图片信息显示功能
            Toast.makeText(context, "图片信息功能开发中", Toast.LENGTH_SHORT).show()
            hideMenu()
        }
        
        // 复制图片链接
        menuView.findViewById<View>(R.id.action_copy_image_url)?.setOnClickListener {
            copyToClipboard("图片链接", imageUrl)
            hideMenu()
        }
        
        // 下载原图
        menuView.findViewById<View>(R.id.action_download_image)?.setOnClickListener {
            try {
                val downloadId = enhancedDownloadManager.downloadImage(imageUrl, object : EnhancedDownloadManager.DownloadCallback {
                    override fun onDownloadSuccess(downloadId: Long, localUri: String?, fileName: String?) {
                        Log.d(TAG, "原图下载成功: $fileName")
                        Toast.makeText(context, "原图下载完成", Toast.LENGTH_SHORT).show()
                    }
                    
                    override fun onDownloadFailed(downloadId: Long, reason: Int) {
                        Log.e(TAG, "原图下载失败: $reason")
                        Toast.makeText(context, "原图下载失败", Toast.LENGTH_SHORT).show()
                    }
                })
                
                if (downloadId != -1L) {
                    Toast.makeText(context, "开始下载原图", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "下载原图失败", e)
                Toast.makeText(context, "下载原图失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            hideMenu()
        }
        
        // 屏蔽相关广告
        menuView.findViewById<View>(R.id.action_block_ads)?.setOnClickListener {
            // TODO: 实现广告屏蔽功能
            Toast.makeText(context, "广告屏蔽功能开发中", Toast.LENGTH_SHORT).show()
            hideMenu()
        }
    }
    
    /**
     * 设置增强版链接菜单项
     */
    private fun setupEnhancedLinkMenuItems(menuView: View, webView: WebView, url: String, title: String) {
        // 当前标签打开
        menuView.findViewById<View>(R.id.action_open_current_tab)?.setOnClickListener {
            webView.loadUrl(url)
            hideMenu()
        }
        
        // 新标签打开
        menuView.findViewById<View>(R.id.action_open_new_tab)?.setOnClickListener {
            onNewTabListener?.invoke(url, false)
            hideMenu()
        }
        
        // 后台打开
        menuView.findViewById<View>(R.id.action_open_background)?.setOnClickListener {
            onNewTabListener?.invoke(url, true)
            hideMenu()
        }
        
        // 外部浏览器打开
        menuView.findViewById<View>(R.id.action_open_browser)?.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "打开外部浏览器失败", e)
                Toast.makeText(context, "打开外部浏览器失败", Toast.LENGTH_SHORT).show()
            }
            hideMenu()
        }
        
        // 复制链接
        menuView.findViewById<View>(R.id.action_copy_link)?.setOnClickListener {
            copyToClipboard("链接", url)
            hideMenu()
        }
        
        // 分享链接
        menuView.findViewById<View>(R.id.action_share_link)?.setOnClickListener {
            shareContent(title, url)
            hideMenu()
        }
        
        // 复制文本
        menuView.findViewById<View>(R.id.action_copy_text)?.setOnClickListener {
            copyToClipboard("链接文本", title)
            hideMenu()
        }
        
        // 自由复制
        menuView.findViewById<View>(R.id.action_free_copy)?.setOnClickListener {
            // TODO: 实现自由复制功能
            Toast.makeText(context, "自由复制功能开发中", Toast.LENGTH_SHORT).show()
            hideMenu()
        }
        
        // 下载链接
        menuView.findViewById<View>(R.id.action_download_link)?.setOnClickListener {
            try {
                val downloadId = enhancedDownloadManager.downloadSmart(url, object : EnhancedDownloadManager.DownloadCallback {
                    override fun onDownloadSuccess(downloadId: Long, localUri: String?, fileName: String?) {
                        Log.d(TAG, "链接下载成功: $fileName")
                        Toast.makeText(context, "文件下载完成", Toast.LENGTH_SHORT).show()
                    }
                    
                    override fun onDownloadFailed(downloadId: Long, reason: Int) {
                        Log.e(TAG, "链接下载失败: $reason")
                        Toast.makeText(context, "文件下载失败", Toast.LENGTH_SHORT).show()
                    }
                })
                
                if (downloadId != -1L) {
                    Toast.makeText(context, "开始下载文件", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "下载链接失败", e)
                Toast.makeText(context, "下载失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            hideMenu()
        }
        
        // 生成二维码
        menuView.findViewById<View>(R.id.action_generate_qr)?.setOnClickListener {
            // TODO: 实现二维码生成功能
            Toast.makeText(context, "二维码生成功能开发中", Toast.LENGTH_SHORT).show()
            hideMenu()
        }
        
        // 链接信息
        menuView.findViewById<View>(R.id.action_link_info)?.setOnClickListener {
            // TODO: 实现链接信息显示功能
            Toast.makeText(context, "链接信息功能开发中", Toast.LENGTH_SHORT).show()
            hideMenu()
        }
        
        // 下载管理
        menuView.findViewById<View>(R.id.action_download_manager)?.setOnClickListener {
            // TODO: 实现下载管理功能
            Toast.makeText(context, "下载管理功能开发中", Toast.LENGTH_SHORT).show()
            hideMenu()
        }
        
        // 屏蔽相关广告
        menuView.findViewById<View>(R.id.action_block_ads)?.setOnClickListener {
            // TODO: 实现广告屏蔽功能
            Toast.makeText(context, "广告屏蔽功能开发中", Toast.LENGTH_SHORT).show()
            hideMenu()
        }
    }
    
    /**
     * 设置增强版刷新菜单项
     */
    private fun setupEnhancedRefreshMenuItems(menuView: View, webView: WebView) {
        // 刷新页面
        menuView.findViewById<View>(R.id.action_refresh_page)?.setOnClickListener {
            webView.reload()
            Toast.makeText(context, "页面已刷新", Toast.LENGTH_SHORT).show()
            hideMenu()
        }
        
        // 强制刷新
        menuView.findViewById<View>(R.id.action_force_refresh)?.setOnClickListener {
            webView.clearCache(true)
            webView.reload()
            Toast.makeText(context, "页面已强制刷新", Toast.LENGTH_SHORT).show()
            hideMenu()
        }
        
        // 后退
        menuView.findViewById<View>(R.id.action_go_back)?.setOnClickListener {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                Toast.makeText(context, "无法后退", Toast.LENGTH_SHORT).show()
            }
            hideMenu()
        }
        
        // 前进
        menuView.findViewById<View>(R.id.action_go_forward)?.setOnClickListener {
            if (webView.canGoForward()) {
                webView.goForward()
            } else {
                Toast.makeText(context, "无法前进", Toast.LENGTH_SHORT).show()
            }
            hideMenu()
        }
        
        // 回到首页
        menuView.findViewById<View>(R.id.action_go_home)?.setOnClickListener {
            // TODO: 实现回到首页功能
            Toast.makeText(context, "回到首页功能开发中", Toast.LENGTH_SHORT).show()
            hideMenu()
        }
        
        // 重新加载
        menuView.findViewById<View>(R.id.action_reload_page)?.setOnClickListener {
            webView.reload()
            Toast.makeText(context, "页面重新加载", Toast.LENGTH_SHORT).show()
            hideMenu()
        }
        
        // 复制链接
        menuView.findViewById<View>(R.id.action_copy_url)?.setOnClickListener {
            val url = webView.url ?: ""
            copyToClipboard("页面链接", url)
            hideMenu()
        }
        
        // 分享页面
        menuView.findViewById<View>(R.id.action_share_page)?.setOnClickListener {
            val url = webView.url ?: ""
            val title = webView.title ?: ""
            shareContent(title, url)
            hideMenu()
        }
        
        // 页面信息
        menuView.findViewById<View>(R.id.action_page_info)?.setOnClickListener {
            // TODO: 实现页面信息显示功能
            Toast.makeText(context, "页面信息功能开发中", Toast.LENGTH_SHORT).show()
            hideMenu()
        }
        
        // 查看源码
        menuView.findViewById<View>(R.id.action_view_source)?.setOnClickListener {
            // TODO: 实现查看源码功能
            Toast.makeText(context, "查看源码功能开发中", Toast.LENGTH_SHORT).show()
            hideMenu()
        }
        
        // 保存页面
        menuView.findViewById<View>(R.id.action_save_page)?.setOnClickListener {
            // TODO: 实现保存页面功能
            Toast.makeText(context, "保存页面功能开发中", Toast.LENGTH_SHORT).show()
            hideMenu()
        }
        
        // 打印页面
        menuView.findViewById<View>(R.id.action_print_page)?.setOnClickListener {
            // TODO: 实现打印页面功能
            Toast.makeText(context, "打印页面功能开发中", Toast.LENGTH_SHORT).show()
            hideMenu()
        }
        
        // 页面设置
        menuView.findViewById<View>(R.id.action_page_settings)?.setOnClickListener {
            // TODO: 实现页面设置功能
            Toast.makeText(context, "页面设置功能开发中", Toast.LENGTH_SHORT).show()
            hideMenu()
        }
        
        // 清除缓存
        menuView.findViewById<View>(R.id.action_clear_cache)?.setOnClickListener {
            webView.clearCache(true)
            webView.clearHistory()
            Toast.makeText(context, "缓存已清除", Toast.LENGTH_SHORT).show()
            hideMenu()
        }
        
        // 屏蔽页面广告
        menuView.findViewById<View>(R.id.action_block_ads)?.setOnClickListener {
            // TODO: 实现广告屏蔽功能
            Toast.makeText(context, "广告屏蔽功能开发中", Toast.LENGTH_SHORT).show()
            hideMenu()
        }
    }
    
    /**
     * 加载图片预览
     */
    private fun loadImagePreview(menuView: View, imageUrl: String) {
        val imagePreview = menuView.findViewById<ImageView>(R.id.image_preview)
        val imageTitle = menuView.findViewById<TextView>(R.id.image_title)
        val imageInfo = menuView.findViewById<TextView>(R.id.image_info)
        
        imageTitle.text = "图片"
        imageInfo.text = "点击查看详情"
        
        // 异步加载图片
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val bitmap = loadBitmapFromUrl(imageUrl)
                withContext(Dispatchers.Main) {
                    bitmap?.let {
                        imagePreview.setImageBitmap(it)
                        imageTitle.text = "图片预览"
                        imageInfo.text = "已加载"
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "加载图片预览失败", e)
            }
        }
    }
    
    /**
     * 加载链接预览
     */
    private fun loadLinkPreview(menuView: View, url: String, title: String) {
        val linkFavicon = menuView.findViewById<ImageView>(R.id.link_favicon)
        val linkTitle = menuView.findViewById<TextView>(R.id.link_title)
        val linkUrl = menuView.findViewById<TextView>(R.id.link_url)
        
        linkTitle.text = title.ifEmpty { "链接" }
        linkUrl.text = url
        
        // 加载网站图标
        FaviconLoader.loadFavicon(linkFavicon, url)
    }
    
    /**
     * 加载页面预览
     */
    private fun loadPagePreview(menuView: View, webView: WebView) {
        val pageFavicon = menuView.findViewById<ImageView>(R.id.page_favicon)
        val pageTitle = menuView.findViewById<TextView>(R.id.page_title)
        val pageUrl = menuView.findViewById<TextView>(R.id.page_url)
        
        val url = webView.url ?: ""
        val title = webView.title ?: "页面"
        
        pageTitle.text = title
        pageUrl.text = url
        
        // 加载网站图标
        FaviconLoader.loadFavicon(pageFavicon, url)
    }
    
    /**
     * 从URL加载位图
     */
    private suspend fun loadBitmapFromUrl(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val inputStream = URL(url).openStream()
            BitmapFactory.decodeStream(inputStream)
        } catch (e: Exception) {
            Log.e(TAG, "从URL加载位图失败: $url", e)
            null
        }
    }
    
    /**
     * 显示菜单
     */
    private fun showMenu(menuContent: View, x: Int, y: Int) {
        try {
            isMenuShowing.set(true)
            isMenuAnimating.set(true)
            
            // 计算菜单位置和大小
            val screenWidth = context.resources.displayMetrics.widthPixels
            val screenHeight = context.resources.displayMetrics.heightPixels
            val density = context.resources.displayMetrics.density
            
            // 设置菜单最大宽度（屏幕宽度的85%）
            val maxMenuWidth = (screenWidth * 0.85f).toInt()
            val minMenuWidth = (280 * density).toInt()
            
            // 测量菜单内容
            menuContent.measure(
                View.MeasureSpec.makeMeasureSpec(maxMenuWidth, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec((screenHeight * 0.7f).toInt(), View.MeasureSpec.AT_MOST)
            )
            
            var menuWidth = menuContent.measuredWidth
            var menuHeight = menuContent.measuredHeight
            
            // 确保菜单有合适的宽度
            if (menuWidth < minMenuWidth) {
                menuWidth = minMenuWidth
            }
            if (menuWidth > maxMenuWidth) {
                menuWidth = maxMenuWidth
            }
            
            // 确保菜单有合适的高度（根据内容自适应，但不超过屏幕高度的70%）
            val maxMenuHeight = (screenHeight * 0.7f).toInt()
            if (menuHeight > maxMenuHeight) {
                menuHeight = maxMenuHeight
            }
            
            // 计算菜单位置：优先在触摸点上方，如果空间不够则显示在下方
            val margin = (16 * density).toInt()
            var finalX = x
            var finalY = y
            
            // 水平方向：确保不超出屏幕边界
            when {
                x + menuWidth > screenWidth - margin -> {
                    // 右侧超出，调整到左侧
                    finalX = screenWidth - menuWidth - margin
                }
                x < margin -> {
                    // 左侧超出，调整到右侧
                    finalX = margin
                }
                else -> {
                    // 如果触摸点靠近屏幕边缘，稍微偏移
                    if (x < menuWidth / 2) {
                        finalX = margin
                    } else if (x > screenWidth - menuWidth / 2) {
                        finalX = screenWidth - menuWidth - margin
                    } else {
                        // 居中在触摸点
                        finalX = x - menuWidth / 2
                    }
                }
            }
            
            // 垂直方向：优先在触摸点上方显示
            val verticalSpacing = (20 * density).toInt()
            if (y - menuHeight > margin + verticalSpacing) {
                // 触摸点上方有足够空间，显示在上方
                finalY = y - menuHeight - verticalSpacing
            } else if (y + menuHeight < screenHeight - margin - verticalSpacing) {
                // 触摸点上方空间不够，显示在下方
                finalY = y + (60 * density).toInt()
            } else {
                // 上下都不够，显示在屏幕中间
                finalY = (screenHeight - menuHeight) / 2
            }
            
            // 最终边界检查，确保菜单完全在屏幕内
            if (finalY < margin) {
                finalY = margin
            }
            if (finalY + menuHeight > screenHeight - margin) {
                finalY = screenHeight - menuHeight - margin
                // 如果仍然超出，限制高度并启用滚动
                if (finalY < margin) {
                    finalY = margin
                    menuHeight = screenHeight - margin * 2
                }
            }
            
            // 最终确保水平位置也在边界内
            if (finalX < margin) finalX = margin
            if (finalX + menuWidth > screenWidth - margin) {
                finalX = screenWidth - menuWidth - margin
            }
            
            // 使用WRAP_CONTENT让ScrollView能够正确工作
            // 但需要确保菜单不会超出屏幕
            val layoutParams = WindowManager.LayoutParams(
                menuWidth,
                WindowManager.LayoutParams.WRAP_CONTENT,
                OVERLAY_WINDOW_TYPE,
                MENU_WINDOW_FLAGS,
                android.graphics.PixelFormat.TRANSLUCENT
            )
            
            // 如果菜单高度超过限制，设置最大高度
            if (menuHeight > maxMenuHeight) {
                layoutParams.height = maxMenuHeight
            }
            
            layoutParams.x = finalX
            layoutParams.y = finalY
            
            Log.d(TAG, "显示菜单: 位置=($finalX, $finalY), 大小=($menuWidth, ${layoutParams.height})")
            
            windowManager.addView(floatingMenuView, layoutParams)
            
            // 显示动画
            menuContent.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(200)
                .withEndAction {
                    isMenuAnimating.set(false)
                }
                .start()
            
            // 设置自动隐藏
            setupAutoHide()
            
            Log.d(TAG, "增强版菜单已显示")
            
        } catch (e: Exception) {
            Log.e(TAG, "显示菜单失败", e)
            cleanupState()
        }
    }
    
    /**
     * 隐藏菜单
     */
    fun hideMenu(immediate: Boolean = false) {
        if (!isMenuShowing.get()) return
        
        try {
            isMenuShowing.set(false)
            
            val menuContent = floatingMenuView?.findViewById<View>(R.id.enhanced_image_menu_content)
                ?: floatingMenuView?.findViewById<View>(R.id.enhanced_link_menu_content)
                ?: floatingMenuView?.findViewById<View>(R.id.enhanced_refresh_menu_content)
            
            if (menuContent != null && !immediate) {
                isMenuAnimating.set(true)
                menuContent.animate()
                    .alpha(0f)
                    .scaleX(0.8f)
                    .scaleY(0.8f)
                    .setDuration(150)
                    .withEndAction {
                        cleanupState()
                    }
                    .start()
            } else {
                cleanupState()
            }
            
            // 取消自动隐藏
            autoHideRunnable?.let { handler.removeCallbacks(it) }
            
        } catch (e: Exception) {
            Log.e(TAG, "隐藏菜单失败", e)
            cleanupState()
        }
    }
    
    /**
     * 设置自动隐藏
     */
    private fun setupAutoHide() {
        autoHideRunnable?.let { handler.removeCallbacks(it) }
        autoHideRunnable = Runnable {
            if (isMenuShowing.get()) {
                hideMenu()
            }
        }
        handler.postDelayed(autoHideRunnable!!, MENU_AUTO_HIDE_DELAY)
    }
    
    /**
     * 显示链接预览悬浮窗
     */
    private fun showLinkPreviewWindow(webView: WebView, url: String, title: String, x: Int, y: Int) {
        try {
            currentWebView = webView
            isPreviewShowing.set(true)
            
            val themedContext = android.view.ContextThemeWrapper(context, R.style.Theme_AIFloatingBall)
            previewWindowView = LayoutInflater.from(themedContext)
                .inflate(R.layout.link_preview_window, null)
            
            val container = previewWindowView!!.findViewById<androidx.cardview.widget.CardView>(R.id.preview_window_container)!!
            val previewWebView = previewWindowView!!.findViewById<WebView>(R.id.preview_webview)!!
            val loadingIndicator = previewWindowView!!.findViewById<ProgressBar>(R.id.preview_loading)!!
            val previewTitle = previewWindowView!!.findViewById<TextView>(R.id.preview_title)!!
            val previewFavicon = previewWindowView!!.findViewById<ImageView>(R.id.preview_favicon)!!
            val btnClose = previewWindowView!!.findViewById<ImageButton>(R.id.btn_close_preview)!!
            
            // 保存预览WebView引用
            this.previewWebView = previewWebView
            
            // 设置标题
            previewTitle.text = title.ifEmpty { "链接预览" }
            
            // 加载favicon
            FaviconLoader.loadFavicon(previewFavicon, url)
            
            // 设置预览WebView
            setupPreviewWebView(previewWebView, url, loadingIndicator)
            
            // 设置菜单按钮
            setupPreviewMenuButtons(webView, url, title)
            
            // 设置关闭按钮
            btnClose.setOnClickListener {
                hidePreviewWindow()
            }
            
            // 设置拖拽功能
            setupPreviewDrag(container)
            
            // 设置点击外部关闭
            previewWindowView!!.setOnTouchListener { view, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val containerRect = android.graphics.Rect()
                    container.getGlobalVisibleRect(containerRect)
                    val touchX = event.rawX.toInt()
                    val touchY = event.rawY.toInt()
                    
                    if (!containerRect.contains(touchX, touchY)) {
                        Log.d(TAG, "点击预览窗外部区域，关闭预览")
                        hidePreviewWindow()
                        return@setOnTouchListener true
                    }
                }
                false
            }
            
            // 计算预览窗位置和大小
            val screenWidth = context.resources.displayMetrics.widthPixels
            val screenHeight = context.resources.displayMetrics.heightPixels
            val density = context.resources.displayMetrics.density
            
            // 测量预览窗
            container.measure(
                View.MeasureSpec.makeMeasureSpec((screenWidth * 0.9f).toInt(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec((screenHeight * 0.8f).toInt(), View.MeasureSpec.AT_MOST)
            )
            
            val previewWidth = container.measuredWidth.coerceAtMost((400 * density).toInt())
            val previewHeight = container.measuredHeight.coerceAtMost((600 * density).toInt())
            
            // 计算位置：优先在触摸点附近，但确保不超出屏幕
            val margin = (16 * density).toInt()
            var finalX = (x - previewWidth / 2).coerceIn(margin, screenWidth - previewWidth - margin)
            var finalY = (y - previewHeight / 2).coerceIn(margin, screenHeight - previewHeight - margin)
            
            // 如果触摸点太靠近边缘，居中显示
            if (x < screenWidth / 4 || x > screenWidth * 3 / 4) {
                finalX = (screenWidth - previewWidth) / 2
            }
            if (y < screenHeight / 4 || y > screenHeight * 3 / 4) {
                finalY = (screenHeight - previewHeight) / 2
            }
            
            // 创建窗口参数
            previewWindowParams = WindowManager.LayoutParams(
                previewWidth,
                previewHeight,
                OVERLAY_WINDOW_TYPE,
                MENU_WINDOW_FLAGS,
                PixelFormat.TRANSLUCENT
            ).apply {
                this.x = finalX
                this.y = finalY
                gravity = Gravity.TOP or Gravity.START
            }
            
            // 添加预览窗到窗口管理器
            windowManager.addView(previewWindowView, previewWindowParams)
            
            // 显示动画
            container.alpha = 0f
            container.scaleX = 0.9f
            container.scaleY = 0.9f
            container.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(250)
                .start()
            
            Log.d(TAG, "链接预览悬浮窗已显示: $url")
            
        } catch (e: Exception) {
            Log.e(TAG, "显示链接预览悬浮窗失败", e)
            cleanupPreviewState()
        }
    }
    
    /**
     * 设置预览WebView
     */
    private fun setupPreviewWebView(webView: WebView, url: String, loadingIndicator: ProgressBar) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportZoom(true)
            builtInZoomControls = false
            displayZoomControls = false
            loadWithOverviewMode = true
            useWideViewPort = true
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            // 设置用户代理，确保网站正确识别
            userAgentString = userAgentString
            // 启用媒体播放
            mediaPlaybackRequiresUserGesture = false
            // 允许访问文件
            allowFileAccess = true
            allowContentAccess = true
        }
        
        // 启用硬件加速，确保正确渲染
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        
        // 设置WebView背景为白色，避免黑框
        webView.setBackgroundColor(0xFFFFFFFF.toInt())
        webView.isHorizontalScrollBarEnabled = false
        webView.isVerticalScrollBarEnabled = false
        
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                loadingIndicator.visibility = View.VISIBLE
                // 确保WebView可见
                view?.visibility = View.VISIBLE
            }
            
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                loadingIndicator.visibility = View.GONE
                // 页面加载完成后，注入JavaScript确保内容正确显示
                view?.evaluateJavascript("""
                    (function() {
                        // 移除可能的全屏覆盖层
                        var overlays = document.querySelectorAll('[style*="position: fixed"], [style*="position:absolute"]');
                        overlays.forEach(function(el) {
                            if (el.style.zIndex > 1000) {
                                el.style.display = 'none';
                            }
                        });
                        // 确保body可见
                        document.body.style.visibility = 'visible';
                        document.body.style.opacity = '1';
                        // 移除可能的黑色背景
                        var blackElements = document.querySelectorAll('body, html');
                        blackElements.forEach(function(el) {
                            if (el.style.backgroundColor === 'rgb(0, 0, 0)' || 
                                el.style.backgroundColor === 'black') {
                                el.style.backgroundColor = '#FFFFFF';
                            }
                        });
                    })();
                """.trimIndent(), null)
            }
            
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                loadingIndicator.visibility = View.GONE
                Log.e(TAG, "预览WebView加载错误: ${error?.description}, URL: ${request?.url}")
            }
            
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                // 预览窗内不处理链接跳转，保持预览状态
                return true
            }
            
            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?
            ) {
                super.onReceivedHttpError(view, request, errorResponse)
                Log.e(TAG, "预览WebView HTTP错误: ${errorResponse?.statusCode}, URL: ${request?.url}")
            }
        }
        
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                if (newProgress >= 100) {
                    loadingIndicator.visibility = View.GONE
                }
            }
        }
        
        // 加载URL
        webView.loadUrl(url)
    }
    
    /**
     * 设置预览窗菜单按钮
     */
    private fun setupPreviewMenuButtons(webView: WebView, url: String, title: String) {
        // 当前标签打开
        previewWindowView?.findViewById<com.google.android.material.button.MaterialButton>(R.id.action_preview_open_current)?.setOnClickListener {
            webView.loadUrl(url)
            hidePreviewWindow()
        }
        
        // 新标签打开
        previewWindowView?.findViewById<com.google.android.material.button.MaterialButton>(R.id.action_preview_open_new)?.setOnClickListener {
            onNewTabListener?.invoke(url, false)
            hidePreviewWindow()
        }
        
        // 后台打开
        previewWindowView?.findViewById<com.google.android.material.button.MaterialButton>(R.id.action_preview_open_background)?.setOnClickListener {
            onNewTabListener?.invoke(url, true)
            hidePreviewWindow()
        }
        
        // 外部浏览器打开
        previewWindowView?.findViewById<com.google.android.material.button.MaterialButton>(R.id.action_preview_open_browser)?.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "打开外部浏览器失败", e)
                Toast.makeText(context, "打开外部浏览器失败", Toast.LENGTH_SHORT).show()
            }
            hidePreviewWindow()
        }
        
        // 复制链接
        previewWindowView?.findViewById<com.google.android.material.button.MaterialButton>(R.id.action_preview_copy_link)?.setOnClickListener {
            copyToClipboard("链接", url)
            hidePreviewWindow()
        }
        
        // 分享链接
        previewWindowView?.findViewById<com.google.android.material.button.MaterialButton>(R.id.action_preview_share_link)?.setOnClickListener {
            shareContent(title, url)
            hidePreviewWindow()
        }
    }
    
    /**
     * 设置预览窗拖拽功能
     */
    private fun setupPreviewDrag(container: View) {
        container.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isPreviewDragging = false
                    previewInitialTouchX = event.rawX
                    previewInitialTouchY = event.rawY
                    previewWindowParams?.let {
                        previewInitialX = it.x.toFloat()
                        previewInitialY = it.y.toFloat()
                    }
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - previewInitialTouchX
                    val deltaY = event.rawY - previewInitialTouchY
                    
                    // 如果移动距离超过阈值，开始拖拽
                    if (!isPreviewDragging && (kotlin.math.abs(deltaX) > 10 || kotlin.math.abs(deltaY) > 10)) {
                        isPreviewDragging = true
                    }
                    
                    if (isPreviewDragging) {
                        previewWindowParams?.let { params ->
                            val screenWidth = context.resources.displayMetrics.widthPixels
                            val screenHeight = context.resources.displayMetrics.heightPixels
                            val density = context.resources.displayMetrics.density
                            val margin = (16 * density).toInt()
                            
                            params.x = (previewInitialX + deltaX).toInt().coerceIn(
                                margin,
                                screenWidth - params.width - margin
                            )
                            params.y = (previewInitialY + deltaY).toInt().coerceIn(
                                margin,
                                screenHeight - params.height - margin
                            )
                            
                            windowManager.updateViewLayout(previewWindowView, params)
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isPreviewDragging = false
                    true
                }
                else -> false
            }
        }
    }
    
    /**
     * 隐藏预览窗
     */
    fun hidePreviewWindow(immediate: Boolean = false) {
        if (!isPreviewShowing.get()) return
        
        try {
            isPreviewShowing.set(false)
            
            val container = previewWindowView?.findViewById<androidx.cardview.widget.CardView>(R.id.preview_window_container)
            
            if (container != null && !immediate) {
                container.animate()
                    .alpha(0f)
                    .scaleX(0.9f)
                    .scaleY(0.9f)
                    .setDuration(200)
                    .withEndAction {
                        cleanupPreviewState()
                    }
                    .start()
            } else {
                cleanupPreviewState()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "隐藏预览窗失败", e)
            cleanupPreviewState()
        }
    }
    
    /**
     * 清理预览窗状态
     */
    private fun cleanupPreviewState() {
        try {
            // 销毁预览WebView
            previewWebView?.let { webView ->
                webView.stopLoading()
                webView.destroy()
            }
            previewWebView = null
            
            // 移除预览窗视图
            previewWindowView?.let { view ->
                windowManager.removeView(view)
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理预览窗视图失败", e)
        }
        
        previewWindowView = null
        previewWindowParams = null
        isPreviewShowing.set(false)
        isPreviewDragging = false
    }
    
    /**
     * 清理状态
     */
    private fun cleanupState() {
        try {
            floatingMenuView?.let { view ->
                windowManager.removeView(view)
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理菜单视图失败", e)
        }
        
        floatingMenuView = null
        currentWebView = null
        isMenuShowing.set(false)
        isMenuAnimating.set(false)
        autoHideRunnable?.let { handler.removeCallbacks(it) }
        autoHideRunnable = null
    }
    
    /**
     * 复制到剪贴板
     */
    private fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label 已复制", Toast.LENGTH_SHORT).show()
        Log.d(TAG, "已复制到剪贴板: $text")
    }
    
    /**
     * 分享内容
     */
    private fun shareContent(title: String, url: String) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, "$title\n$url")
        }
        
        val chooser = Intent.createChooser(shareIntent, "分享内容")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        
        Log.d(TAG, "分享内容: $title - $url")
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        hideMenu(true)
        hidePreviewWindow(true)
        onNewTabListener = null
    }
}
