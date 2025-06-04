package com.example.aifloatingball.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

class WebViewInputHelper(
    private val context: Context,
    private val windowManager: WindowManager,
    private val floatingView: View?
) {
    companion object {
        private const val TAG = "WebViewInputHelper"
    }

    fun prepareWebViewForInput(webView: WebView) {
        try {
            // 1. 基础设置增强
            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                
                // 输入相关设置
                saveFormData = true
                javaScriptCanOpenWindowsAutomatically = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                mediaPlaybackRequiresUserGesture = false
                defaultTextEncodingName = "UTF-8"
            }

            // 2. 设置基本属性
            webView.isFocusable = true
            webView.isFocusableInTouchMode = true

            // 3. 添加输入法桥接接口
            webView.addJavascriptInterface(object {
                @JavascriptInterface
                fun onInputFieldFocused() {
                    Handler(Looper.getMainLooper()).post {
                        ensureInputMethodAvailable(webView)
                    }
                }

                @JavascriptInterface
                fun onInputFieldClicked() {
                    Handler(Looper.getMainLooper()).post {
                        forceShowInputMethod(webView)
                    }
                }
            }, "InputMethodBridge")

            // 4. 注入输入法支持脚本
            injectInputMethodSupport(webView)

            // 5. 设置 WebViewClient 来处理页面加载完成后的 JavaScript 注入
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // 页面加载完成后再次注入脚本，以确保动态加载的内容也能被处理
                    view?.let { injectInputMethodSupport(it) }
                    Log.d(TAG, "onPageFinished: 重新注入输入法支持脚本到URL: $url")
                }

                override fun onPageCommitVisible(view: WebView?, url: String?) {
                    super.onPageCommitVisible(view, url)
                    // 当页面内容首次可见时，再次注入脚本 (兼容某些特殊页面)
                    view?.let { injectInputMethodSupport(it) }
                    Log.d(TAG, "onPageCommitVisible: 再次注入输入法支持脚本到URL: $url")
                }
            }

            // 6. 不再设置 WebView 的 onTouchListener，让 WebViewManager 或其他组件管理。
            // 移除了之前的 webView.setOnTouchListener(null)
        } catch (e: Exception) {
            Log.e(TAG, "准备WebView输入失败", e)
        }
    }

    private fun ensureWindowParameters() {
        try {
            val params = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return
            
            // 不再直接修改 flags 和 softInputMode，这些由 FloatingWindowManager 主要管理。
            // 我们只确保布局更新生效。
            // params.flags = params.flags and (
            //     WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            //     WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM or
            //     WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            // ).inv()

            // params.softInputMode = (
            //     WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED or
            //     WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
            //     WindowManager.LayoutParams.SOFT_INPUT_IS_FORWARD_NAVIGATION
            // )

            try {
                windowManager.updateViewLayout(floatingView, params)
                Log.d(TAG, "已更新窗口参数（来自 WebViewInputHelper）")
            } catch (e: Exception) {
                Log.e(TAG, "更新窗口布局失败（来自 WebViewInputHelper）", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "确保窗口参数正确失败（来自 WebViewInputHelper）", e)
        }
    }

    fun injectInputMethodSupport(webView: WebView) {
        // 注入JavaScript代码，用于在WebView内部监听输入元素的焦点和点击事件
        // 当输入元素被聚焦或点击时，通过JavaScript接口通知Android端
        webView.evaluateJavascript("""
            (function() {
                // 1. 定义输入元素选择器：包括标准的input/textarea和contenteditable元素
                const inputSelectors = 'input, textarea, [contenteditable="true"]';
                
                // 2. 辅助函数：判断元素是否是输入元素
                function isInputElement(element) {
                    return element.matches(inputSelectors);
                }
                
                // 辅助函数：为输入元素设置属性和监听器
                function setupInputElement(element) {
                    // 确保元素可以获取焦点，并设置辅助输入法属性
                    element.setAttribute('tabindex', '0');
                    if (element.tagName === 'INPUT' || element.tagName === 'TEXTAREA') {
                        element.setAttribute('inputmode', 'text');
                        element.setAttribute('enterkeyhint', 'done');
                    }
                    
                    // 添加焦点事件监听器，通知Android输入字段已聚焦
                    element.addEventListener('focus', function() {
                        InputMethodBridge.onInputFieldFocused();
                        console.log('Input field focused: ' + element.tagName + ' ' + (element.id || element.name || ''));
                    });
                    
                    // 添加点击事件监听器，通知Android输入字段被点击
                    element.addEventListener('click', function() {
                        InputMethodBridge.onInputFieldClicked();
                        console.log('Input field clicked: ' + element.tagName + ' ' + (element.id || element.name || ''));
                    });
                }
                
                // 3. MutationObserver：监听DOM树的变化，以处理动态添加的输入元素
                const observer = new MutationObserver(function(mutations) {
                    mutations.forEach(function(mutation) {
                        mutation.addedNodes.forEach(function(node) {
                            if (node.nodeType === 1) { // 检查是否为元素节点
                                if (isInputElement(node)) {
                                    setupInputElement(node);
                                } else { // 如果不是输入元素，检查其子元素是否为输入元素
                                    node.querySelectorAll(inputSelectors).forEach(setupInputElement);
                                }
                            }
                        });
                    });
                });
                
                // 启动观察器，监听body及其子树的添加/移除子节点事件
                observer.observe(document.body, {
                    childList: true,
                    subtree: true
                });
                
                // 4. 初始化现有输入元素：为页面上所有当前存在的输入元素设置监听器
                document.querySelectorAll(inputSelectors).forEach(setupInputElement);
                
                // 5. 注入CSS样式：改善输入体验和视觉反馈
                const style = document.createElement('style');
                style.textContent = `
                    input, textarea, [contenteditable="true"] {
                        -webkit-user-select: text !important;
                        user-select: text !important;
                        -webkit-tap-highlight-color: rgba(0,0,0,0.1) !important;
                    }
                    input:focus, textarea:focus, [contenteditable="true"]:focus {
                        outline: 2px solid rgba(0,0,0,0.2) !important;
                    }
                `;
                document.head.appendChild(style);
                console.log('Input method support script injected.');
            })();
        """.trimIndent(), null)
    }

    fun ensureInputMethodAvailable(webView: WebView) {
        try {
            Log.d(TAG, "ensureInputMethodAvailable: 尝试确保输入法可用")
            // 1. 不再调用 ensureWindowParameters() 来修改窗口参数，这应该由 FloatingWindowManager 管理。
            // ensureWindowParameters()

            // 2. 确保WebView可以获取焦点
            webView.isFocusable = true
            webView.isFocusableInTouchMode = true

            // 3. 请求焦点
            if (!webView.hasFocus()) {
                webView.requestFocus()
                Log.d(TAG, "ensureInputMethodAvailable: WebView 请求焦点")
            } else {
                Log.d(TAG, "ensureInputMethodAvailable: WebView 已经有焦点")
            }

            // 4. 显示输入法
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT)
            Log.d(TAG, "ensureInputMethodAvailable: 调用 showSoftInput (SHOW_IMPLICIT)")
            
            // 5. 如果输入法没有显示，尝试强制显示
            Handler(Looper.getMainLooper()).postDelayed({
                if (!imm.isActive(webView)) {
                    Log.w(TAG, "ensureInputMethodAvailable: 输入法未激活，尝试强制显示")
                    forceShowInputMethod(webView)
                } else {
                    Log.d(TAG, "ensureInputMethodAvailable: 输入法已激活")
                }
            }, 200) // 延迟200ms检查
        } catch (e: Exception) {
            Log.e(TAG, "确保输入法可用失败", e)
        }
    }

    fun forceShowInputMethod(webView: WebView) {
        Handler(Looper.getMainLooper()).post {
            try {
                Log.d(TAG, "forceShowInputMethod: 尝试强制显示输入法")
                // 1. 强制请求焦点
                webView.requestFocus()
                webView.requestFocusFromTouch()
                Log.d(TAG, "forceShowInputMethod: WebView 强制请求焦点")

                // 2. 强制显示输入法
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(webView, InputMethodManager.SHOW_FORCED)
                Log.d(TAG, "forceShowInputMethod: 调用 showSoftInput (SHOW_FORCED)")

                // 3. 如果输入法还是没有显示，尝试切换输入法状态
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!imm.isActive(webView)) {
                        Log.e(TAG, "forceShowInputMethod: 输入法仍未激活，尝试切换状态")
                        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
                    } else {
                        Log.d(TAG, "forceShowInputMethod: 输入法已激活")
                    }
                }, 200) // 延迟200ms检查
            } catch (e: Exception) {
                Log.e(TAG, "强制显示输入法失败", e)
            }
        }
    }
} 