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
                // 基本功能设置
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
                
                // 增强功能设置
                setGeolocationEnabled(true)
                loadsImagesAutomatically = true
                allowFileAccess = true
                allowContentAccess = true
                databaseEnabled = true
                setSupportMultipleWindows(true)
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

            // 4. 设置触摸监听器
            webView.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // 确保窗口可以获取焦点
                        toggleWindowFocusableFlag(true)
                        
                        // 检查点击位置是否是输入元素
                        webView.evaluateJavascript("""
                            (function() {
                                var x = ${event.x};
                                var y = ${event.y};
                                var element = document.elementFromPoint(x, y);
                                if (element) {
                                    var isInput = element.tagName === 'INPUT' || 
                                                element.tagName === 'TEXTAREA' || 
                                                element.contentEditable === 'true' ||
                                                element.role === 'textbox' ||
                                                window.getComputedStyle(element).webkitUserModify === 'read-write' ||
                                                window.getComputedStyle(element).userModify === 'read-write';
                                    if (isInput) {
                                        element.focus();
                                        return true;
                                    }
                                }
                                return false;
                            })();
                        """.trimIndent()) { result ->
                            if (result == "true") {
                                forceShowInputMethod(webView)
                            }
                        }
                    }
                }
                false
            }

            // 5. 注入初始化脚本
            injectInputMethodSupport(webView)
            
            // 6. 确保窗口参数正确
            ensureWindowParameters()

        } catch (e: Exception) {
            Log.e(TAG, "准备WebView输入支持失败", e)
        }
    }

    private fun ensureWindowParameters() {
        try {
            val params = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return
            
            // 移除阻止获取焦点的标志
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
            // 移除阻止输入法的标志
            params.flags = params.flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM.inv()
            // 设置输入法模式
            params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE

            try {
                windowManager.updateViewLayout(floatingView, params)
                Log.d(TAG, "已更新窗口参数以支持输入法")
            } catch (e: Exception) {
                Log.e(TAG, "更新窗口布局失败", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "确保窗口参数正确失败", e)
        }
    }

    fun injectInputMethodSupport(webView: WebView) {
        webView.evaluateJavascript("""
            (function() {
                // 1. 监听所有可能的输入事件
                document.addEventListener('focusin', function(e) {
                    if (isInputElement(e.target)) {
                        InputMethodBridge.onInputFieldFocused();
                    }
                }, true);

                document.addEventListener('click', function(e) {
                    if (isInputElement(e.target)) {
                        e.target.focus();
                        InputMethodBridge.onInputFieldClicked();
                    }
                }, true);

                // 2. 监听动态添加的元素
                var observer = new MutationObserver(function(mutations) {
                    mutations.forEach(function(mutation) {
                        if (mutation.addedNodes) {
                            mutation.addedNodes.forEach(function(node) {
                                if (node.nodeType === 1) {
                                    var inputs = node.querySelectorAll(inputSelectors);
                                    inputs.forEach(setupInputElement);
                                }
                            });
                        }
                    });
                });

                observer.observe(document.body, {
                    childList: true,
                    subtree: true
                });

                // 3. 辅助函数
                var inputSelectors = 'input, textarea, [contenteditable="true"], [role="textbox"]';

                function isInputElement(element) {
                    return element.matches(inputSelectors) ||
                           window.getComputedStyle(element).webkitUserModify === 'read-write' ||
                           window.getComputedStyle(element).userModify === 'read-write';
                }

                function setupInputElement(element) {
                    element.addEventListener('click', function(e) {
                        this.focus();
                        InputMethodBridge.onInputFieldClicked();
                    });

                    element.addEventListener('focus', function() {
                        InputMethodBridge.onInputFieldFocused();
                    });

                    // 设置输入相关属性
                    if (element.tagName === 'INPUT' || element.tagName === 'TEXTAREA') {
                        element.setAttribute('inputmode', 'text');
                        element.setAttribute('enterkeyhint', 'done');
                    }
                }

                // 4. 初始化现有输入元素
                document.querySelectorAll(inputSelectors).forEach(setupInputElement);

                // 5. 注入CSS样式
                var style = document.createElement('style');
                style.textContent = `
                    input, textarea, [contenteditable="true"] {
                        -webkit-user-select: text !important;
                        user-select: text !important;
                        -webkit-tap-highlight-color: rgba(0,0,0,0.1) !important;
                    }
                `;
                document.head.appendChild(style);
            })();
        """.trimIndent(), null)
    }

    fun ensureInputMethodAvailable(webView: WebView) {
        try {
            // 1. 确保窗口参数正确
            ensureWindowParameters()

            // 2. 确保WebView可以获取焦点
            webView.isFocusable = true
            webView.isFocusableInTouchMode = true
            webView.requestFocus()

            // 3. 显示输入法
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(webView, InputMethodManager.SHOW_IMPLICIT)
            
            // 4. 如果输入法没有显示，尝试强制显示
            Handler(Looper.getMainLooper()).postDelayed({
                if (!imm.isActive(webView)) {
                    forceShowInputMethod(webView)
                }
            }, 100)
        } catch (e: Exception) {
            Log.e(TAG, "确保输入法可用失败", e)
        }
    }

    fun forceShowInputMethod(webView: WebView) {
        Handler(Looper.getMainLooper()).post {
            try {
                // 1. 强制请求焦点
                webView.requestFocus()
                webView.requestFocusFromTouch()

                // 2. 强制显示输入法
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(webView, InputMethodManager.SHOW_FORCED)

                // 3. 如果输入法还是没有显示，尝试切换输入法状态
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!imm.isActive(webView)) {
                        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
                    }
                }, 100)
            } catch (e: Exception) {
                Log.e(TAG, "强制显示输入法失败", e)
            }
        }
    }

    fun toggleWindowFocusableFlag(focusable: Boolean) {
        try {
            val params = floatingView?.layoutParams as? WindowManager.LayoutParams ?: return
            
            if (focusable) {
                // 移除阻止获取焦点的标志
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
                // 移除阻止输入法的标志
                params.flags = params.flags and WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM.inv()
                // 设置输入法模式
                params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            } else {
                // 添加阻止获取焦点的标志
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                // 添加阻止输入法的标志
                params.flags = params.flags or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                // 重置输入法模式
                params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            }

            try {
                windowManager.updateViewLayout(floatingView, params)
            } catch (e: Exception) {
                Log.e(TAG, "更新窗口布局参数失败", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "切换窗口焦点状态失败", e)
        }
    }
} 