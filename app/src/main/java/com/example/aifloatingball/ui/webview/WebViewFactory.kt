package com.example.aifloatingball.ui.webview

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.ViewGroup
import android.view.textclassifier.TextClassifier
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
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
        // 一个通用的移动版Chrome User-Agent 字符串示例 (Android)
        private const val COMMON_MOBILE_USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.36"
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
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false

                // 新增：设置一个更通用的User-Agent字符串
                userAgentString = COMMON_MOBILE_USER_AGENT
                Log.d(TAG, "Set User-Agent to: $COMMON_MOBILE_USER_AGENT")
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                
                @Suppress("DEPRECATION")
                setRenderPriority(WebSettings.RenderPriority.HIGH)
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            
            // 确保WebView可获取焦点和触摸焦点，这对于输入法激活至关重要
            isFocusable = true
            isFocusableInTouchMode = true

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString()
                    Log.d(TAG, "CustomWebViewClient shouldOverrideUrlLoading: $url")
                    // 返回false以确保WebView自行处理URL加载
                    return false
                }

                @Deprecated("Deprecated in Java")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    Log.d(TAG, "CustomWebViewClient shouldOverrideUrlLoading (legacy): $url")
                    // 返回false以确保WebView自行处理URL加载
                    return false
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    Log.d(TAG, "CustomWebViewClient onPageStarted: $url")
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    Log.d(TAG, "CustomWebViewClient onPageFinished: $url")
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Log.e(TAG, "CustomWebViewClient onReceivedError: ${error?.errorCode} - ${error?.description} for URL: ${request?.url}")
                    } else {
                         Log.e(TAG, "CustomWebViewClient onReceivedError (legacy) for URL: ${request?.url}")
                    }
                }
            }
            
            // 新增：设置WebChromeClient来处理JS对话框、进度、标题等，这对于输入法激活有时是必要的。
            webChromeClient = object : android.webkit.WebChromeClient() {
                // 可以根据需要在此处添加其他回调方法，例如 onProgressChanged, onReceivedTitle 等
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                this.setTextClassifier(TextClassifier.NO_OP)
            }
            
            layoutParams = LinearLayout.LayoutParams(
                context.resources.displayMetrics.widthPixels,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                marginEnd = context.resources.getDimensionPixelSize(R.dimen.webview_margin)
            }
            
            setTextSelectionManager(textSelectionManager)
            
            setOnLongClickListener { false }
        }
    }
} 