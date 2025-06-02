package com.example.aifloatingball.ui.webview

import android.content.Context
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.view.textclassifier.TextClassifier
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import com.example.aifloatingball.R
import com.example.aifloatingball.ui.text.TextSelectionManager
import android.content.Context.WINDOW_SERVICE
import android.view.WindowManager

/**
 * WebView工厂，负责创建和配置WebView实例
 */
class WebViewFactory(private val context: Context) {
    
    companion object {
        private const val TAG = "WebViewFactory"
    }
    
    val textSelectionManager: TextSelectionManager by lazy {
        val windowManager = context.getSystemService(WINDOW_SERVICE) as WindowManager
        TextSelectionManager(context, windowManager)
    }
    
    /**
     * 创建配置好的WebView实例
     */
    fun createWebView(): CustomWebView {
        Log.d(TAG, "创建新的CustomWebView")
        
        return CustomWebView(context).apply {
            // 基本设置
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                
                // Android 8.0及以上支持混合内容模式设置
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                
                // 提高渲染性能
                @Suppress("DEPRECATION")
                setRenderPriority(WebSettings.RenderPriority.HIGH)
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            
            // 设置WebViewClient
            webViewClient = WebViewClient()
            
            // Android 8.0及以上支持文本分类器
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // 禁用默认文本分类器，使用我们自己的
                this.setTextClassifier(TextClassifier.NO_OP)
            }
            
            // 设置布局参数
            layoutParams = LinearLayout.LayoutParams(
                context.resources.displayMetrics.widthPixels,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                marginEnd = context.resources.getDimensionPixelSize(R.dimen.webview_margin)
            }
            
            // 设置文本选择管理器
            setTextSelectionManager(textSelectionManager)
            
            // 启用JavaScript接口支持
            setOnLongClickListener { false } // 确保不拦截长按事件
        }
    }
} 