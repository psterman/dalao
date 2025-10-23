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
    private val isMenuShowing = AtomicBoolean(false)
    private val isMenuAnimating = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private var autoHideRunnable: Runnable? = null
    
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
            
            // 设置触摸监听器
            floatingMenuView!!.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val contentRect = android.graphics.Rect()
                    menuContent.getGlobalVisibleRect(contentRect)
                    if (!contentRect.contains(event.x.toInt(), event.y.toInt())) {
                        hideMenu()
                    }
                }
                false
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
     */
    fun showEnhancedLinkMenu(webView: WebView, url: String, title: String, x: Int, y: Int) {
        Log.d(TAG, "显示增强版链接菜单: $url")
        
        if (isMenuShowing.get() || isMenuAnimating.get()) {
            hideMenu(true)
            handler.postDelayed({
                doShowEnhancedLinkMenu(webView, url, title, x, y)
            }, 160)
            return
        }
        
        doShowEnhancedLinkMenu(webView, url, title, x, y)
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
            
            // 设置触摸监听器
            floatingMenuView!!.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val contentRect = android.graphics.Rect()
                    menuContent.getGlobalVisibleRect(contentRect)
                    if (!contentRect.contains(event.x.toInt(), event.y.toInt())) {
                        hideMenu()
                    }
                }
                false
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
            
            // 设置触摸监听器
            floatingMenuView!!.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    val contentRect = android.graphics.Rect()
                    menuContent.getGlobalVisibleRect(contentRect)
                    if (!contentRect.contains(event.x.toInt(), event.y.toInt())) {
                        hideMenu()
                    }
                }
                false
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
            onNewTabListener?.invoke(imageUrl, false)
            hideMenu()
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
            
            val layoutParams = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                OVERLAY_WINDOW_TYPE,
                MENU_WINDOW_FLAGS,
                android.graphics.PixelFormat.TRANSLUCENT
            )
            
            // 计算菜单位置
            val screenWidth = context.resources.displayMetrics.widthPixels
            val screenHeight = context.resources.displayMetrics.heightPixels
            
            menuContent.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            val menuWidth = menuContent.measuredWidth
            val menuHeight = menuContent.measuredHeight
            
            // 确保菜单不超出屏幕边界
            val finalX = when {
                x + menuWidth > screenWidth -> screenWidth - menuWidth - 20
                x < 20 -> 20
                else -> x
            }
            
            val finalY = when {
                y + menuHeight > screenHeight -> screenHeight - menuHeight - 20
                y < 20 -> 20
                else -> y
            }
            
            layoutParams.x = finalX
            layoutParams.y = finalY
            
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
        onNewTabListener = null
    }
}
