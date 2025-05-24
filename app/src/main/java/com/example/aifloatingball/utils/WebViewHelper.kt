package com.example.aifloatingball.utils

import android.os.Build
import android.webkit.WebSettings
import android.webkit.WebView

object WebViewHelper {
    fun setupWebView(webView: WebView) {
        webView.settings.apply {
            // 启用JavaScript
            javaScriptEnabled = true
            
            // 支持缩放
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            
            // 自适应屏幕
            useWideViewPort = true
            loadWithOverviewMode = true
            
            // 支持多窗口
            setSupportMultipleWindows(true)
            
            // 启用DOM存储
            domStorageEnabled = true
            
            // 允许文件访问
            allowFileAccess = true
            
            // 混合内容
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            
            // 缓存配置
            cacheMode = WebSettings.LOAD_DEFAULT
            databaseEnabled = true
            domStorageEnabled = true
            
            // 设置默认编码
            defaultTextEncodingName = "UTF-8"
            
            // 设置UA
            userAgentString = "Mozilla/5.0 (Linux; Android ${Build.VERSION.RELEASE}; ${Build.MODEL}) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/120.0.0.0 Mobile Safari/537.36"
            
            // 设置地理位置
            setGeolocationEnabled(true)
            
            // 设置文件访问
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true
            
            // 设置媒体播放
            mediaPlaybackRequiresUserGesture = false
            
            // 设置WebRTC
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setWebRtcEnabled(true)
            }
        }
    }
    
    private fun WebSettings.setWebRtcEnabled(enabled: Boolean) {
        try {
            javaClass.getMethod("setWebRtcEnabled", Boolean::class.java)
                .invoke(this, enabled)
        } catch (e: Exception) {
            // 忽略不支持的API
        }
    }
} 