package com.example.aifloatingball.web

import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

class CustomWebViewClient : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        // 返回false以允许WebView自行处理URL加载
        return false
    }
} 