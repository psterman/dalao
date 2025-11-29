package com.example.aifloatingball.webview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.appcompat.app.AlertDialog
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
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
import com.example.aifloatingball.manager.UnifiedCollectionManager
import com.example.aifloatingball.model.*
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
                // 获取当前页面信息用于记录来源（保存到局部变量，确保在回调中可用）
                val currentUrl = webView.url ?: ""
                val currentTitle = webView.title ?: ""
                
                // 从图片URL提取文件名作为标题
                val imageTitle = try {
                    val urlParts = imageUrl.split("/")
                    urlParts.lastOrNull()?.split("?")?.firstOrNull() ?: "图片"
                } catch (e: Exception) {
                    "图片"
                }
                
                Log.d(TAG, "开始保存图片: $imageUrl, 来源: $currentTitle ($currentUrl)")
                
                // 显示保存位置选择对话框
                val saveOptions = arrayOf(
                    "保存到下载文件夹",
                    "保存到相册",
                    "同时保存到下载文件夹和相册",
                    "图片收藏（仅保存到AI助手tab）"
                )
                AlertDialog.Builder(context)
                    .setTitle("选择保存位置")
                    .setItems(saveOptions) { _, which ->
                        when (which) {
                            0 -> {
                                // 只保存到下载文件夹
                                saveImageToDirectories(
                                    imageUrl,
                                    listOf(Environment.DIRECTORY_DOWNLOADS),
                                    currentUrl,
                                    currentTitle,
                                    imageTitle
                                )
                            }
                            1 -> {
                                // 只保存到相册
                                saveImageToDirectories(
                                    imageUrl,
                                    listOf(Environment.DIRECTORY_PICTURES),
                                    currentUrl,
                                    currentTitle,
                                    imageTitle
                                )
                            }
                            2 -> {
                                // 同时保存到下载文件夹和相册
                                saveImageToDirectories(
                                    imageUrl,
                                    listOf(Environment.DIRECTORY_DOWNLOADS, Environment.DIRECTORY_PICTURES),
                                    currentUrl,
                                    currentTitle,
                                    imageTitle
                                )
                            }
                            3 -> {
                                // 只保存到图片收藏（不下载文件）
                                saveImageToCollectionOnly(
                                    imageUrl,
                                    imageTitle,
                                    currentUrl,
                                    currentTitle
                                )
                            }
                            else -> {
                                // 默认保存到相册
                                saveImageToDirectories(
                                    imageUrl,
                                    listOf(Environment.DIRECTORY_PICTURES),
                                    currentUrl,
                                    currentTitle,
                                    imageTitle
                                )
                            }
                        }
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } catch (e: Exception) {
                Log.e(TAG, "保存图片失败", e)
                e.printStackTrace()
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

        // 进入阅读模式（优先使用阅读模式2）
        menuView.findViewById<View>(R.id.action_enter_reader_mode)?.setOnClickListener {
            try {
                val currentUrl = webView.url
                
                // 🔧 优先使用阅读模式2（NovelReaderManager + NovelReaderUI）
                // 阅读模式2支持目录解析、章节跳转等完整功能
                com.example.aifloatingball.reader.NovelReaderManager.getInstance(context).enterReaderMode(webView)
                Toast.makeText(context, "正在进入阅读模式...", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "✅ 已进入阅读模式2，URL: $currentUrl")
            } catch (e: Exception) {
                Log.e(TAG, "进入阅读模式2失败", e)
                Toast.makeText(context, "进入阅读模式失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            hideMenu()
        }
        
        // 进入无图模式（无广告、无图片）
        menuView.findViewById<View>(R.id.action_enter_no_image_mode)?.setOnClickListener {
            try {
                val currentUrl = webView.url
                
                // 🔧 优先使用 SimpleModeActivity 的全局阅读模式管理器实例
                val readerModeManager = try {
                    com.example.aifloatingball.SimpleModeActivity.getGlobalReaderModeManager()
                        ?: throw Exception("全局实例不可用")
                } catch (e: Exception) {
                    Log.w(TAG, "无法获取全局阅读模式管理器，创建新实例: ${e.message}")
                    com.example.aifloatingball.reader.NovelReaderModeManager(context)
                }
                
                readerModeManager.enterReaderMode(webView, currentUrl, useNoImageMode = true)
                Toast.makeText(context, "已启用无图模式（无广告、无图片）", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "✅ 已进入无图模式，URL: $currentUrl")
            } catch (e: Exception) {
                Log.e(TAG, "进入无图模式失败", e)
                Toast.makeText(context, "进入无图模式失败", Toast.LENGTH_SHORT).show()
            }
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
            val headerView = previewWindowView!!.findViewById<View>(R.id.preview_header)!!
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
            
            // 🔧 设置菜单折叠功能，避免遮挡输入法
            setupPreviewMenuCollapse(container)
            
            // 自底部弹出的预览卡片，下滑可关闭（仅对链接预览启用）
            setupBottomSheetSwipeToDismiss(container, headerView)
            
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
            
            // 创建窗口参数：全屏透明遮罩，自底部显示卡片
            val screenWidth = context.resources.displayMetrics.widthPixels
            val screenHeight = context.resources.displayMetrics.heightPixels
            
            previewWindowParams = WindowManager.LayoutParams(
                screenWidth,
                screenHeight,
                OVERLAY_WINDOW_TYPE,
                MENU_WINDOW_FLAGS,
                PixelFormat.TRANSLUCENT
            ).apply {
                this.x = 0
                this.y = 0
                gravity = Gravity.TOP or Gravity.START
            }
            
            // 添加预览窗到窗口管理器
            windowManager.addView(previewWindowView, previewWindowParams)
            
            // 初始位置：将卡片放在屏幕底部外（完全隐藏）
            container.translationY = screenHeight.toFloat()
            container.alpha = 1f
            
            // 从底部滑入动画
            container.animate()
                .translationY(0f)
                .setDuration(300)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
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
        
        // 🔧 修复：新标签改成新窗口打开，用户马上跳转新窗口加载链接
        previewWindowView?.findViewById<com.google.android.material.button.MaterialButton>(R.id.action_preview_open_new)?.setOnClickListener {
            // 先关闭预览窗口
            hidePreviewWindow()
            // 然后在新窗口打开（前台模式，立即跳转）
            onNewTabListener?.invoke(url, false)
        }
        
        // 🔧 修复：后台打开应该关闭预览弹窗，停留在当前窗口，在后台创建新窗口但不跳转
        previewWindowView?.findViewById<com.google.android.material.button.MaterialButton>(R.id.action_preview_open_background)?.setOnClickListener {
            // 先关闭预览窗口
            hidePreviewWindow()
            // 然后在后台创建新窗口（后台模式，不跳转）
            onNewTabListener?.invoke(url, true)
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
     * 设置预览窗菜单折叠功能，避免遮挡输入法
     */
    private fun setupPreviewMenuCollapse(container: View) {
        try {
            // 找到菜单内容容器（LinearLayout）
            val menuContent = previewWindowView?.findViewById<android.widget.LinearLayout>(
                R.id.preview_menu_content
            )
            
            // 找到菜单ScrollView（它是menuContent的父视图）
            val menuScrollView = menuContent?.parent as? android.widget.ScrollView
            
            if (menuScrollView == null || menuContent == null) {
                Log.w(TAG, "预览窗菜单视图未找到，无法设置折叠功能")
                return
            }
            
            // 监听根视图的布局变化，检测输入法显示状态
            val rootView = (context as? android.app.Activity)?.window?.decorView?.rootView
            rootView?.viewTreeObserver?.addOnGlobalLayoutListener {
                val rect = android.graphics.Rect()
                rootView.getWindowVisibleDisplayFrame(rect)
                val screenHeight = rootView.height
                val keypadHeight = screenHeight - rect.bottom
                
                // 如果键盘高度超过屏幕高度的15%，认为键盘已显示
                val keyboardVisible = keypadHeight > screenHeight * 0.15
                
                // 当输入法显示时，折叠菜单（隐藏菜单ScrollView）
                if (keyboardVisible) {
                    if (menuScrollView.visibility == android.view.View.VISIBLE) {
                        menuScrollView.visibility = android.view.View.GONE
                        Log.d(TAG, "输入法显示，折叠预览窗菜单")
                    }
                } else {
                    if (menuScrollView.visibility == android.view.View.GONE) {
                        menuScrollView.visibility = android.view.View.VISIBLE
                        Log.d(TAG, "输入法隐藏，展开预览窗菜单")
                    }
                }
            }
            
            Log.d(TAG, "预览窗菜单折叠功能已设置")
        } catch (e: Exception) {
            Log.e(TAG, "设置预览窗菜单折叠功能失败", e)
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
     * 为链接预览卡片设置“自底部弹出 + 下滑关闭”的行为
     *
     * @param container  整个预览卡片容器（CardView）
     * @param dragHandle 负责处理下滑手势的区域（通常是标题栏）
     */
    private fun setupBottomSheetSwipeToDismiss(container: View, dragHandle: View) {
        try {
            var downY = 0f
            var startTranslationY = 0f
            var isDragging = false
            val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
            val screenHeight = context.resources.displayMetrics.heightPixels.toFloat()
            val dismissThreshold = screenHeight * 0.25f // 下滑超过25%屏幕高度则关闭
            
            // 创建统一的拖动监听器，整个卡片都可以拖动
            val dragListener = View.OnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downY = event.rawY
                        startTranslationY = container.translationY
                        isDragging = false
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dy = event.rawY - downY
                        if (Math.abs(dy) > touchSlop) {
                            isDragging = true
                        }
                        if (dy > 0 && isDragging) {
                            // 只允许向下拖动
                            container.translationY = startTranslationY + dy
                            // 根据拖动距离调整透明度，增加视觉反馈
                            val progress = (dy / screenHeight).coerceIn(0f, 1f)
                            container.alpha = 1f - progress * 0.5f
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isDragging) {
                            val dy = container.translationY - startTranslationY
                            // 拖动距离超过阈值则关闭，否则回弹
                            if (dy > dismissThreshold) {
                                // 向下滑出关闭
                                container.animate()
                                    .translationY(screenHeight)
                                    .alpha(0f)
                                    .setDuration(250)
                                    .setInterpolator(android.view.animation.AccelerateInterpolator())
                                    .withEndAction {
                                        hidePreviewWindow(true)
                                        container.alpha = 1f
                                        container.translationY = 0f
                                    }
                                    .start()
                            } else {
                                // 回弹到原位
                                container.animate()
                                    .translationY(0f)
                                    .alpha(1f)
                                    .setDuration(200)
                                    .setInterpolator(android.view.animation.DecelerateInterpolator())
                                    .start()
                            }
                        }
                        isDragging = false
                        true
                    }
                    else -> false
                }
            }
            
            // 标题栏和整个容器都可以拖动
            dragHandle.setOnTouchListener(dragListener)
            container.setOnTouchListener(dragListener)
            
        } catch (e: Exception) {
            Log.e(TAG, "设置底部预览卡片下滑关闭行为失败", e)
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
            val screenHeight = context.resources.displayMetrics.heightPixels.toFloat()
            
            if (container != null && !immediate) {
                // 向下滑出动画
                container.animate()
                    .translationY(screenHeight)
                    .alpha(0f)
                    .setDuration(250)
                    .setInterpolator(android.view.animation.AccelerateInterpolator())
                    .withEndAction {
                        cleanupPreviewState()
                        container.alpha = 1f
                        container.translationY = 0f
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
     * 保存图片到指定目录（支持多个目录），并同时保存到图片收藏
     * @param imageUrl 图片URL
     * @param destinationDirs 目标目录列表
     * @param currentUrl 当前页面URL
     * @param currentTitle 当前页面标题
     * @param imageTitle 图片标题
     */
    private fun saveImageToDirectories(
        imageUrl: String,
        destinationDirs: List<String>,
        currentUrl: String,
        currentTitle: String,
        imageTitle: String
    ) {
        if (destinationDirs.isEmpty()) {
            Log.e(TAG, "目标目录列表为空")
            // 即使没有目标目录，也保存到收藏（仅保存图片链接）
            Handler(Looper.getMainLooper()).post {
                saveImageToCollection(
                    imageUrl,
                    imageTitle,
                    currentUrl,
                    currentTitle,
                    emptyList()
                )
            }
            return
        }
        
        Log.d(TAG, "开始保存图片到${destinationDirs.size}个位置: $imageUrl")
        
        // 用于跟踪所有下载任务
        val downloadResults = mutableListOf<Pair<String, String?>>() // Pair<目录名, 本地路径>
        var completedCount = 0
        val totalCount = destinationDirs.size
        var hasSuccess = false // 标记是否有成功的下载
        
        // 为每个目录创建下载任务
        destinationDirs.forEach { destinationDir ->
            val downloadId = enhancedDownloadManager.downloadImageToDirectory(
                imageUrl,
                destinationDir,
                object : EnhancedDownloadManager.DownloadCallback {
                    override fun onDownloadSuccess(downloadId: Long, localUri: String?, fileName: String?) {
                        val dirName = when (destinationDir) {
                            Environment.DIRECTORY_PICTURES -> "相册"
                            Environment.DIRECTORY_DOWNLOADS -> "下载文件夹"
                            else -> "指定位置"
                        }
                        
                        Log.d(TAG, "图片下载成功: $dirName, localUri=$localUri, fileName=$fileName")
                        
                        synchronized(downloadResults) {
                            downloadResults.add(Pair(dirName, localUri ?: fileName))
                            hasSuccess = true
                            completedCount++
                            
                            Log.d(TAG, "下载进度: $completedCount/$totalCount, 成功: $hasSuccess")
                            
                            // 所有下载完成后，保存到收藏
                            if (completedCount == totalCount) {
                                Handler(Looper.getMainLooper()).post {
                                    Log.d(TAG, "所有下载任务完成，开始保存到收藏")
                                    saveImageToCollection(
                                        imageUrl,
                                        imageTitle,
                                        currentUrl,
                                        currentTitle,
                                        downloadResults
                                    )
                                }
                            }
                        }
                    }
                    
                    override fun onDownloadFailed(downloadId: Long, reason: Int) {
                        Log.e(TAG, "图片下载失败: destinationDir=$destinationDir, reason=$reason")
                        synchronized(downloadResults) {
                            completedCount++
                            
                            Log.d(TAG, "下载进度: $completedCount/$totalCount, 成功: $hasSuccess")
                            
                            // 所有下载完成后，保存到收藏（即使全部失败，也保存图片链接）
                            if (completedCount == totalCount) {
                                Handler(Looper.getMainLooper()).post {
                                    Log.d(TAG, "所有下载任务完成（部分或全部失败），开始保存到收藏")
                                    saveImageToCollection(
                                        imageUrl,
                                        imageTitle,
                                        currentUrl,
                                        currentTitle,
                                        downloadResults
                                    )
                                }
                            }
                        }
                    }
                }
            )
            
            if (downloadId == -1L) {
                Log.e(TAG, "无法创建下载任务: destinationDir=$destinationDir")
                synchronized(downloadResults) {
                    completedCount++
                    if (completedCount == totalCount) {
                        Handler(Looper.getMainLooper()).post {
                            Log.d(TAG, "所有下载任务完成（部分创建失败），开始保存到收藏")
                            // 即使创建失败，也保存到收藏（至少保存图片链接）
                            saveImageToCollection(
                                imageUrl,
                                imageTitle,
                                currentUrl,
                                currentTitle,
                                downloadResults
                            )
                        }
                    }
                }
            } else {
                Log.d(TAG, "下载任务已创建: downloadId=$downloadId, destinationDir=$destinationDir")
            }
        }
    }
    
    /**
     * 仅保存图片到收藏（不下载文件）
     * @param imageUrl 图片URL
     * @param imageTitle 图片标题
     * @param currentUrl 当前页面URL
     * @param currentTitle 当前页面标题
     */
    private fun saveImageToCollectionOnly(
        imageUrl: String,
        imageTitle: String,
        currentUrl: String,
        currentTitle: String
    ) {
        Log.d(TAG, "仅保存图片到收藏（不下载文件）: $imageUrl")
        Toast.makeText(context, "正在保存到图片收藏...", Toast.LENGTH_SHORT).show()
        
        // 直接调用保存到收藏的方法，传入空的下载结果列表
        saveImageToCollection(
            imageUrl,
            imageTitle,
            currentUrl,
            currentTitle,
            emptyList() // 空的下载结果，表示只保存链接
        )
    }
    
    /**
     * 保存图片到收藏
     * @param imageUrl 图片URL
     * @param imageTitle 图片标题
     * @param currentUrl 当前页面URL
     * @param currentTitle 当前页面标题
     * @param downloadResults 下载结果列表（目录名和本地路径）
     */
    private fun saveImageToCollection(
        imageUrl: String,
        imageTitle: String,
        currentUrl: String,
        currentTitle: String,
        downloadResults: List<Pair<String, String?>>
    ) {
        try {
            Log.d(TAG, "开始保存图片到收藏: imageUrl=$imageUrl, imageTitle=$imageTitle")
            Log.d(TAG, "下载结果数量: ${downloadResults.size}")
            
            val collectionManager = UnifiedCollectionManager.getInstance(context)
            
            // 使用第一个成功的下载路径作为主要路径
            val primaryPath = downloadResults.firstOrNull()?.second
            Log.d(TAG, "主要路径: $primaryPath")
            
            // 优化图片标题：如果标题太短或不够描述性，使用更详细的标题
            val optimizedTitle = if (imageTitle.length < 5 || imageTitle == "图片") {
                // 尝试从URL或页面标题生成更好的标题
                val betterTitle = try {
                    val urlFileName = imageUrl.substringAfterLast("/").substringBefore("?")
                    if (urlFileName.isNotEmpty() && urlFileName.length > 3 && urlFileName.contains(".")) {
                        urlFileName.substringBeforeLast(".")
                    } else if (currentTitle.isNotEmpty() && currentTitle.length > 3) {
                        "${currentTitle.take(20)}的图片"
                    } else {
                        "图片_${System.currentTimeMillis().toString().takeLast(6)}"
                    }
                } catch (e: Exception) {
                    "图片_${System.currentTimeMillis().toString().takeLast(6)}"
                }
                betterTitle
            } else {
                imageTitle
            }
            
            Log.d(TAG, "优化后的标题: $optimizedTitle")
            
            // 提取图片格式
            val imageFormat = try {
                val ext = imageUrl.substringAfterLast(".", "").substringBefore("?").uppercase()
                if (ext in listOf("JPG", "JPEG", "PNG", "GIF", "WEBP", "BMP")) ext else "UNKNOWN"
            } catch (e: Exception) {
                "UNKNOWN"
            }
            
            // 构建保存位置信息
            val saveLocations = if (downloadResults.isNotEmpty()) {
                downloadResults.joinToString("、") { it.first }
            } else {
                "仅收藏链接"
            }
            
            Log.d(TAG, "保存位置: $saveLocations")
            
            // 构建扩展数据
            val extraData = mutableMapOf<String, Any>(
                "imageUrl" to imageUrl,
                "imagePath" to (primaryPath ?: ""),
                "imageFormat" to imageFormat,
                "sourceUrl" to currentUrl,
                "sourceTitle" to currentTitle,
                "saveLocations" to saveLocations,
                "downloadResults" to downloadResults.map { mapOf("dir" to it.first, "path" to (it.second ?: "")) }
            )
            
            // 从文件名或URL提取可能的标签
            val autoTags = mutableListOf<String>()
            try {
                // 从URL域名提取标签
                val urlObj = URL(imageUrl)
                val domain = urlObj.host?.replace("www.", "")?.split(".")?.firstOrNull()
                if (!domain.isNullOrEmpty() && domain.length > 2) {
                    autoTags.add(domain)
                }
                
                // 从文件名提取可能的标签
                val fileName = imageUrl.substringAfterLast("/").substringBefore("?")
                if (fileName.isNotEmpty() && fileName.length > 2 && fileName.length < 20) {
                    val nameWithoutExt = fileName.substringBeforeLast(".")
                    if (nameWithoutExt.isNotEmpty()) {
                        autoTags.add(nameWithoutExt)
                    }
                }
                
                // 添加保存位置标签
                if (downloadResults.any { it.first == "下载文件夹" }) {
                    autoTags.add("下载文件夹")
                }
                if (downloadResults.any { it.first == "相册" }) {
                    autoTags.add("相册")
                }
            } catch (e: Exception) {
                Log.w(TAG, "提取自动标签失败", e)
            }
            
            // 构建预览文本，包含保存位置信息
            val previewText = buildString {
                if (currentTitle.isNotEmpty()) {
                    append("来源: $currentTitle")
                } else if (currentUrl.isNotEmpty()) {
                    append("来源: $currentUrl")
                }
                if (saveLocations.isNotEmpty() && saveLocations != "仅收藏链接") {
                    append("\n保存位置: $saveLocations")
                }
                if (imageFormat != "UNKNOWN") {
                    append("\n格式: $imageFormat")
                }
            }
            
            // 创建图片收藏项
            val collectionItem = UnifiedCollectionItem(
                title = optimizedTitle,
                content = imageUrl, // 完整图片URL作为内容
                preview = previewText,
                thumbnail = primaryPath ?: imageUrl, // 使用本地路径或原始URL作为缩略图
                collectionType = CollectionType.IMAGE_COLLECTION,
                sourceLocation = "搜索Tab",
                sourceDetail = if (currentTitle.isNotEmpty()) currentTitle else currentUrl,
                collectedTime = System.currentTimeMillis(), // 收藏时间
                customTags = autoTags.distinct(), // 自动提取的标签（去重）
                priority = Priority.NORMAL, // 默认优先级
                completionStatus = CompletionStatus.NOT_STARTED, // 完成状态
                likeLevel = 0, // 默认喜欢程度
                emotionTag = EmotionTag.NEUTRAL, // 默认情感标签
                isEncrypted = false, // 加密状态
                reminderTime = null, // 默认无提醒
                extraData = extraData
            )
            
            Log.d(TAG, "准备保存图片收藏:")
            Log.d(TAG, "  - ID: ${collectionItem.id}")
            Log.d(TAG, "  - 标题: ${collectionItem.title}")
            Log.d(TAG, "  - 来源: ${collectionItem.sourceDetail}")
            Log.d(TAG, "  - 保存位置: $saveLocations")
            Log.d(TAG, "  - 标签: ${collectionItem.customTags}")
            Log.d(TAG, "  - 图片URL: $imageUrl")
            
            // 保存到收藏管理器
            val success = collectionManager.addCollection(collectionItem)
            
            if (success) {
                Log.d(TAG, "✅ 图片已保存到收藏: id=${collectionItem.id}, title=${collectionItem.title}")
                
                // 立即验证保存是否成功
                val savedItem = collectionManager.getCollectionById(collectionItem.id)
                if (savedItem != null) {
                    Log.d(TAG, "✅ 验证成功：收藏项已保存")
                    Log.d(TAG, "  - 保存的标题: ${savedItem.title}")
                    Log.d(TAG, "  - 保存的类型: ${savedItem.collectionType}")
                    
                    // 发送广播通知收藏更新
                    try {
                        val intent = Intent("com.example.aifloatingball.COLLECTION_UPDATED").apply {
                            putExtra("collection_type", CollectionType.IMAGE_COLLECTION.name)
                            putExtra("action", "add")
                            putExtra("collection_id", collectionItem.id)
                        }
                        context.sendBroadcast(intent)
                        Log.d(TAG, "✅ 已发送收藏更新广播")
                    } catch (e: Exception) {
                        Log.e(TAG, "发送收藏更新广播失败", e)
                    }
                } else {
                    Log.e(TAG, "❌ 验证失败：收藏项未找到，ID=${collectionItem.id}")
                }
                
                // 构建成功提示信息
                val successMessage = if (downloadResults.size > 1) {
                    "图片已保存到${saveLocations}和收藏"
                } else {
                    "图片已保存到${downloadResults.firstOrNull()?.first ?: "指定位置"}和收藏"
                }
                Toast.makeText(context, successMessage, Toast.LENGTH_SHORT).show()
            } else {
                Log.e(TAG, "❌ 保存图片到收藏失败: addCollection返回false")
                val locationsMessage = if (downloadResults.size > 1) {
                    "图片已保存到${saveLocations}，但收藏失败"
                } else {
                    "图片已保存到${downloadResults.firstOrNull()?.first ?: "指定位置"}，但收藏失败"
                }
                Toast.makeText(context, locationsMessage, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 保存图片到收藏时出错", e)
            e.printStackTrace()
            val locationsMessage = if (downloadResults.size > 1) {
                val saveLocations = downloadResults.joinToString("、") { it.first }
                "图片已保存到${saveLocations}，但收藏失败: ${e.message}"
            } else {
                "图片已保存到${downloadResults.firstOrNull()?.first ?: "指定位置"}，但收藏失败: ${e.message}"
            }
            Toast.makeText(context, locationsMessage, Toast.LENGTH_SHORT).show()
        }
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
