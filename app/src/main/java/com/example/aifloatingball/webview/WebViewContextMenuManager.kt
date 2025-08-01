package com.example.aifloatingball.webview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.PopupWindow
import android.widget.Toast
import com.example.aifloatingball.R
import com.google.android.material.button.MaterialButton

/**
 * WebView上下文菜单管理器
 * 处理WebView长按事件的上下文菜单显示和操作
 */
class WebViewContextMenuManager(
    private val context: Context
) {
    
    companion object {
        private const val TAG = "WebViewContextMenuManager"
    }
    
    private var currentPopup: PopupWindow? = null
    private var onNewTabListener: ((String, Boolean) -> Unit)? = null // URL, inBackground
    
    /**
     * 设置新标签页监听器
     */
    fun setOnNewTabListener(listener: (String, Boolean) -> Unit) {
        this.onNewTabListener = listener
    }
    
    /**
     * 显示链接上下文菜单
     */
    fun showLinkContextMenu(url: String, title: String, anchorView: View) {
        Log.d(TAG, "显示链接菜单: $url")
        
        val menuView = LayoutInflater.from(context).inflate(R.layout.webview_context_menu, null)
        
        // 显示链接选项
        menuView.findViewById<View>(R.id.link_options).visibility = View.VISIBLE
        menuView.findViewById<View>(R.id.image_options).visibility = View.GONE
        
        // 设置链接选项点击监听器
        setupLinkMenuListeners(menuView, url, title)
        setupGeneralMenuListeners(menuView, url, title)
        
        showPopupMenu(menuView, anchorView)
    }
    
    /**
     * 显示图片上下文菜单
     */
    fun showImageContextMenu(imageUrl: String, anchorView: View) {
        Log.d(TAG, "显示图片菜单: $imageUrl")
        
        val menuView = LayoutInflater.from(context).inflate(R.layout.webview_context_menu, null)
        
        // 显示图片选项
        menuView.findViewById<View>(R.id.link_options).visibility = View.GONE
        menuView.findViewById<View>(R.id.image_options).visibility = View.VISIBLE
        
        // 设置图片选项点击监听器
        setupImageMenuListeners(menuView, imageUrl)
        setupGeneralMenuListeners(menuView, imageUrl, "")
        
        showPopupMenu(menuView, anchorView)
    }
    
    /**
     * 显示图片链接上下文菜单
     */
    fun showImageLinkContextMenu(url: String, title: String, anchorView: View) {
        Log.d(TAG, "显示图片链接菜单: $url")
        
        val menuView = LayoutInflater.from(context).inflate(R.layout.webview_context_menu, null)
        
        // 显示链接和图片选项
        menuView.findViewById<View>(R.id.link_options).visibility = View.VISIBLE
        menuView.findViewById<View>(R.id.image_options).visibility = View.VISIBLE
        
        // 设置选项点击监听器
        setupLinkMenuListeners(menuView, url, title)
        setupImageMenuListeners(menuView, url)
        setupGeneralMenuListeners(menuView, url, title)
        
        showPopupMenu(menuView, anchorView)
    }
    
    /**
     * 显示通用上下文菜单
     */
    fun showGeneralContextMenu(webView: WebView, anchorView: View) {
        Log.d(TAG, "显示通用菜单")
        
        val menuView = LayoutInflater.from(context).inflate(R.layout.webview_context_menu, null)
        
        // 只显示通用选项
        menuView.findViewById<View>(R.id.link_options).visibility = View.GONE
        menuView.findViewById<View>(R.id.image_options).visibility = View.GONE
        
        // 设置通用选项点击监听器
        setupGeneralMenuListeners(menuView, webView.url ?: "", webView.title ?: "")
        
        // 添加刷新功能
        menuView.findViewById<MaterialButton>(R.id.menu_refresh).setOnClickListener {
            webView.reload()
            dismissPopup()
            Toast.makeText(context, "页面已刷新", Toast.LENGTH_SHORT).show()
        }
        
        showPopupMenu(menuView, anchorView)
    }
    
    /**
     * 设置链接菜单监听器
     */
    private fun setupLinkMenuListeners(menuView: View, url: String, title: String) {
        // 打开链接
        menuView.findViewById<MaterialButton>(R.id.menu_open_link).setOnClickListener {
            onNewTabListener?.invoke(url, false) // 前台打开
            dismissPopup()
        }
        
        // 在新卡片中打开
        menuView.findViewById<MaterialButton>(R.id.menu_open_link_new_tab).setOnClickListener {
            onNewTabListener?.invoke(url, false) // 前台打开新卡片
            dismissPopup()
            Toast.makeText(context, "已在新卡片中打开", Toast.LENGTH_SHORT).show()
        }
        
        // 在后台打开
        menuView.findViewById<MaterialButton>(R.id.menu_open_link_background).setOnClickListener {
            onNewTabListener?.invoke(url, true) // 后台打开
            dismissPopup()
            Toast.makeText(context, "已在后台打开", Toast.LENGTH_SHORT).show()
        }
        
        // 复制链接
        menuView.findViewById<MaterialButton>(R.id.menu_copy_link).setOnClickListener {
            copyToClipboard("链接", url)
            dismissPopup()
        }
    }
    
    /**
     * 设置图片菜单监听器
     */
    private fun setupImageMenuListeners(menuView: View, imageUrl: String) {
        // 查看图片
        menuView.findViewById<MaterialButton>(R.id.menu_view_image).setOnClickListener {
            onNewTabListener?.invoke(imageUrl, false) // 在新卡片中查看图片
            dismissPopup()
        }
        
        // 保存图片
        menuView.findViewById<MaterialButton>(R.id.menu_save_image).setOnClickListener {
            // TODO: 实现图片保存功能
            Toast.makeText(context, "图片保存功能待实现", Toast.LENGTH_SHORT).show()
            dismissPopup()
        }
        
        // 复制图片地址
        menuView.findViewById<MaterialButton>(R.id.menu_copy_image_url).setOnClickListener {
            copyToClipboard("图片地址", imageUrl)
            dismissPopup()
        }
    }
    
    /**
     * 设置通用菜单监听器
     */
    private fun setupGeneralMenuListeners(menuView: View, url: String, title: String) {
        // 分享页面
        menuView.findViewById<MaterialButton>(R.id.menu_share).setOnClickListener {
            shareContent(title, url)
            dismissPopup()
        }
        
        // 复制页面地址
        menuView.findViewById<MaterialButton>(R.id.menu_copy_url).setOnClickListener {
            copyToClipboard("页面地址", url)
            dismissPopup()
        }
    }
    
    /**
     * 显示弹出菜单
     */
    private fun showPopupMenu(menuView: View, anchorView: View) {
        dismissPopup() // 先关闭之前的菜单
        
        currentPopup = PopupWindow(
            menuView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            isFocusable = true
            elevation = 8f
            
            // 显示在锚点视图下方
            showAsDropDown(anchorView, 0, 0)
        }
        
        Log.d(TAG, "上下文菜单已显示")
    }
    
    /**
     * 关闭弹出菜单
     */
    private fun dismissPopup() {
        currentPopup?.dismiss()
        currentPopup = null
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
        
        val chooser = Intent.createChooser(shareIntent, "分享页面")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
        
        Log.d(TAG, "分享页面: $title - $url")
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        dismissPopup()
        onNewTabListener = null
    }
}
