package com.example.aifloatingball.utils

import android.content.Context
import android.os.Build
import android.util.Log
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.CookieManager

/**
 * WebView移动端优化配置工具
 */
object WebViewMobileOptimizer {
    
    private const val TAG = "WebViewMobileOptimizer"
    
    /**
     * 为WebView应用移动端优化设置
     */
    fun applyMobileOptimizations(webView: WebView, context: Context) {
        Log.d(TAG, "应用移动端优化设置")
        
        webView.settings.apply {
            // 基础设置
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            
            // 移动端User-Agent
            userAgentString = WebViewConstants.MOBILE_USER_AGENT
            
            // 页面自适应设置
            useWideViewPort = true
            loadWithOverviewMode = true
            
            // 缩放设置 - 移动端优化
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            
            // 文本缩放设置
            textZoom = WebViewConstants.MobileSettings.TEXT_ZOOM
            minimumFontSize = WebViewConstants.MobileSettings.MINIMUM_FONT_SIZE
            
            // 布局算法 - 移动端优化
            setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING)
            
            // 多窗口支持
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            
            // 缓存设置
            cacheMode = WebSettings.LOAD_DEFAULT
            
            // 文件访问设置
            allowFileAccess = true
            allowContentAccess = true
            
            // 混合内容处理
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            
            // 第三方Cookie支持
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
            }
            
            // 默认文本编码
            defaultTextEncodingName = "UTF-8"
            
            // 图片加载
            loadsImagesAutomatically = true
            
            // 硬件加速
            webView.setLayerType(WebView.LAYER_TYPE_HARDWARE, null)
            
            // 渲染优先级
            @Suppress("DEPRECATION")
            setRenderPriority(WebSettings.RenderPriority.HIGH)
            
            // 强制暗色模式关闭（避免黑屏）
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                forceDark = WebSettings.FORCE_DARK_OFF
            }
            
            // 地理位置支持
            setGeolocationEnabled(true)
            
            // 安全设置
            allowUniversalAccessFromFileURLs = false
            allowFileAccessFromFileURLs = false
        }
        
        // 设置初始缩放
        webView.setInitialScale(WebViewConstants.MobileSettings.INITIAL_SCALE)
        
        // 确保WebView可获取焦点
        webView.isFocusable = true
        webView.isFocusableInTouchMode = true
        webView.isClickable = true
        
        Log.d(TAG, "移动端优化设置完成")
    }
    
    /**
     * 为WebView应用桌面端设置（用于桌面模式切换）
     */
    fun applyDesktopOptimizations(webView: WebView, context: Context) {
        Log.d(TAG, "应用桌面端设置")
        
        webView.settings.apply {
            // 桌面端User-Agent
            userAgentString = WebViewConstants.DESKTOP_USER_AGENT
            
            // 其他设置保持移动端优化，只改变User-Agent
        }
        
        Log.d(TAG, "桌面端设置完成")
    }
    
    /**
     * 检查当前是否为移动端User-Agent
     */
    fun isMobileUserAgent(webView: WebView): Boolean {
        val userAgent = webView.settings.userAgentString
        return userAgent.contains("Mobile") || userAgent.contains("Android")
    }
    
    /**
     * 获取当前User-Agent类型
     */
    fun getUserAgentType(webView: WebView): String {
        return if (isMobileUserAgent(webView)) "Mobile" else "Desktop"
    }
}
