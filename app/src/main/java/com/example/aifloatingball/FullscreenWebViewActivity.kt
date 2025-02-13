package com.example.aifloatingball

import android.os.Bundle
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class FullscreenWebViewActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var titleTextView: TextView
    private lateinit var closeButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // 设置全屏模式
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        )
        
        setContentView(R.layout.activity_fullscreen_webview)
        
        // 初始化视图
        webView = findViewById(R.id.fullscreen_webview)
        titleTextView = findViewById(R.id.fullscreen_title)
        closeButton = findViewById(R.id.fullscreen_close_button)
        
        // 获取传递的 URL 和标题
        val url = intent.getStringExtra("URL") ?: ""
        val title = intent.getStringExtra("TITLE") ?: "AI助手"
        
        // 设置标题
        titleTextView.text = title
        
        // 配置 WebView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            
            // 启用自适应屏幕
            layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
            
            // 允许混合内容
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
        }
        
        // 设置 WebView 客户端
        webView.webViewClient = WebViewClient()
        
        // 加载 URL
        webView.loadUrl(url)
        
        // 关闭按钮
        closeButton.setOnClickListener {
            finish()
        }
    }
    
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
} 