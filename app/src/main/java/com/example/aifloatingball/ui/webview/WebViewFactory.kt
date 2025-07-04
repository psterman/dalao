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
                    
                    // 智能焦点管理 - 基于用户交互意图
                    view?.evaluateJavascript("""
                        (function() {
                            try {
                                console.log('Initializing smart focus management...');
                                
                                // 创建焦点协调器
                                window.focusCoordinator = {
                                    allowPageFocus: true,
                                    lastUserAction: 'none',
                                    actionTimestamp: 0
                                };
                                
                                // 监听来自应用的焦点协调信号
                                window.addEventListener('message', function(event) {
                                    if (event.data && event.data.type === 'focus_control') {
                                        window.focusCoordinator.allowPageFocus = event.data.allow;
                                        window.focusCoordinator.lastUserAction = event.data.action;
                                        window.focusCoordinator.actionTimestamp = Date.now();
                                        console.log('Focus control updated:', event.data);
                                        
                                        // 特殊处理紧急焦点清理
                                        if (event.data.action === 'emergency_focus_clear') {
                                            console.log('Emergency focus clear requested');
                                            // 立即移除所有焦点
                                            if (document.activeElement && document.activeElement !== document.body) {
                                                document.activeElement.blur();
                                            }
                                            // 暂时禁用页面交互
                                            const elements = document.querySelectorAll('button, [role="button"], input, [tabindex]');
                                            elements.forEach(function(el) {
                                                el.tabIndex = -1;
                                            });
                                            
                                            // 3秒后恢复
                                            setTimeout(function() {
                                                elements.forEach(function(el) {
                                                    if (el.getAttribute('data-original-tabindex')) {
                                                        el.tabIndex = parseInt(el.getAttribute('data-original-tabindex'));
                                                    } else {
                                                        el.removeAttribute('tabindex');
                                                    }
                                                });
                                            }, 3000);
                                        }
                                    }
                                });
                                
                                // 智能焦点拦截器 - 只在应用输入框活跃时拦截
                                function smartFocusInterceptor(e) {
                                    const now = Date.now();
                                    const timeSinceAction = now - window.focusCoordinator.actionTimestamp;
                                    
                                    // 如果用户最近点击了应用搜索框，暂时拦截页面焦点
                                    if (!window.focusCoordinator.allowPageFocus && 
                                        window.focusCoordinator.lastUserAction === 'search_input_clicked' &&
                                        timeSinceAction < 2000) { // 2秒内保护期
                                        
                                        console.log('Temporarily blocking page focus for search input priority');
                                        e.preventDefault();
                                        e.stopPropagation();
                                        if (e.target && e.target.blur) {
                                            e.target.blur();
                                        }
                                        return;
                                    }
                                    
                                    // 记录页面内的焦点活动
                                    if (e.target) {
                                        window.focusCoordinator.lastUserAction = 'page_interaction';
                                        window.focusCoordinator.actionTimestamp = now;
                                        console.log('Page element focused:', e.target.tagName, e.target.className);
                                    }
                                }
                                
                                // 添加智能焦点监听器
                                document.addEventListener('focusin', smartFocusInterceptor, true);
                                
                                // 监听用户在页面内的点击，表示用户想与页面交互
                                document.addEventListener('click', function(e) {
                                    window.focusCoordinator.lastUserAction = 'page_click';
                                    window.focusCoordinator.actionTimestamp = Date.now();
                                    window.focusCoordinator.allowPageFocus = true;
                                    console.log('User clicked on page, allowing page focus');
                                }, true);
                                
                                // 处理页面加载时的自动焦点 - 只移除真正干扰的焦点
                                function handleInitialFocus() {
                                    const problematicElements = document.querySelectorAll([
                                        '[autofocus]',
                                        'button:focus',
                                        '[role="button"]:focus'
                                    ].join(','));
                                    
                                    problematicElements.forEach(function(el) {
                                        if (el === document.activeElement) {
                                            el.blur();
                                            console.log('Removed problematic initial focus from:', el.tagName);
                                        }
                                        // 只移除自动聚焦属性，不阻止用户主动点击
                                        el.removeAttribute('autofocus');
                                    });
                                }
                                
                                // 页面加载完成后处理初始焦点
                                if (document.readyState === 'loading') {
                                    document.addEventListener('DOMContentLoaded', handleInitialFocus);
                                } else {
                                    handleInitialFocus();
                                }
                                
                                // 监听DOM变化，处理动态内容
                                const observer = new MutationObserver(function(mutations) {
                                    mutations.forEach(function(mutation) {
                                        if (mutation.type === 'childList') {
                                            mutation.addedNodes.forEach(function(node) {
                                                if (node.nodeType === 1 && node.hasAttribute && node.hasAttribute('autofocus')) {
                                                    node.removeAttribute('autofocus');
                                                    if (node === document.activeElement && 
                                                        !window.focusCoordinator.allowPageFocus) {
                                                        node.blur();
                                                    }
                                                }
                                            });
                                        }
                                    });
                                });
                                
                                observer.observe(document.body, {
                                    childList: true,
                                    subtree: true
                                });
                                
                                console.log('Smart focus management initialized');
                                
                            } catch (e) {
                                console.error('Smart focus management error:', e);
                            }
                        })();
                    """.trimIndent(), null)
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