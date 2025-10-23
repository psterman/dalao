package com.example.aifloatingball.utils

import android.content.Context
import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView

object WebViewHelper {
    fun setupWebView(webView: WebView) {
        // 使用新的移动端优化器
        WebViewMobileOptimizer.applyMobileOptimizations(webView, webView.context)
    }
    
    fun setupWebView(webView: WebView, context: Context) {
        // 使用新的移动端优化器
        WebViewMobileOptimizer.applyMobileOptimizations(webView, context)
    }
    
    /**
     * 切换到桌面模式
     */
    fun switchToDesktopMode(webView: WebView) {
        WebViewMobileOptimizer.applyDesktopOptimizations(webView, webView.context)
    }
    
    /**
     * 切换到移动端模式
     */
    fun switchToMobileMode(webView: WebView) {
        WebViewMobileOptimizer.applyMobileOptimizations(webView, webView.context)
    }
    
    /**
     * 检查当前模式
     */
    fun isMobileMode(webView: WebView): Boolean {
        return WebViewMobileOptimizer.isMobileUserAgent(webView)
    }
    
    /**
     * 获取当前模式类型
     */
    fun getCurrentMode(webView: WebView): String {
        return WebViewMobileOptimizer.getUserAgentType(webView)
    }
} 