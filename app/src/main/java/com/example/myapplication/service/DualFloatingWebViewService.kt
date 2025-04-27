package com.example.aifloatingball.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.Toast
import com.example.aifloatingball.R
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.manager.SearchEngineManager
import com.example.aifloatingball.manager.TextSelectionManager
import android.view.WindowManager
import android.webkit.JavascriptInterface

class DualFloatingWebViewService : Service() {
    companion object {
        private const val TAG = "DualFloatingWebView"
    }

    private lateinit var rootView: View
    private lateinit var leftWebView: WebView
    private lateinit var rightWebView: WebView
    private lateinit var saveButton: ImageButton
    private var isSaving = false
    private val handler = Handler(Looper.getMainLooper())
    private val menuAutoHideHandler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var leftTextSelectionManager: TextSelectionManager? = null
    private var rightTextSelectionManager: TextSelectionManager? = null

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun initializeViews() {
        rootView = LayoutInflater.from(this).inflate(R.layout.floating_webview_dual, null)
        leftWebView = rootView.findViewById(R.id.left_webview)
        rightWebView = rootView.findViewById(R.id.right_webview)
        saveButton = rootView.findViewById(R.id.save_button)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        setupWebViews()
        setupSaveButton()
    }

    private fun setupWebViews() {
        // 设置左侧 WebView
        setupWebView(leftWebView) { webView ->
            leftTextSelectionManager = TextSelectionManager(
                context = this,
                webView = webView,
                windowManager = windowManager,
                onSelectionChanged = { selectedText ->
                    Log.d(TAG, "左侧选中文本: $selectedText")
                }
            )
        }

        // 设置右侧 WebView
        setupWebView(rightWebView) { webView ->
            rightTextSelectionManager = TextSelectionManager(
                context = this,
                webView = webView,
                windowManager = windowManager,
                onSelectionChanged = { selectedText ->
                    Log.d(TAG, "右侧选中文本: $selectedText")
                }
            )
        }
    }

    private fun setupWebView(webView: WebView, onWebViewReady: (WebView) -> Unit) {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectSelectionJavaScript(view!!)
                onWebViewReady(view)
            }
        }

        // 添加 JavaScript 接口
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onSelectionChanged(text: String, startX: Int, startY: Int, endX: Int, endY: Int) {
                handler.post {
                    val selectionManager = if (webView == leftWebView) leftTextSelectionManager else rightTextSelectionManager
                    selectionManager?.showSelectionHandles(startX, startY, endX, endY)
                    selectionManager?.setSelectedText(text)
                }
            }
        }, "TextSelectionCallback")
    }

    private fun injectSelectionJavaScript(webView: WebView) {
        val script = """
            (function() {
                // 增强文本选择能力
                document.addEventListener('selectionchange', function() {
                    var selection = window.getSelection();
                    if (selection.rangeCount > 0 && selection.toString().length > 0) {
                        var range = selection.getRangeAt(0);
                        var rects = range.getClientRects();
                        
                        if (rects.length > 0) {
                            var firstRect = rects[0];
                            var lastRect = rects[rects.length - 1];
                            
                            // 向原生代码报告选择变化
                            try {
                                window.TextSelectionCallback.onSelectionChanged(
                                    selection.toString(),
                                    firstRect.left + window.scrollX,
                                    firstRect.bottom + window.scrollY,
                                    lastRect.right + window.scrollX,
                                    lastRect.bottom + window.scrollY
                                );
                            } catch(e) {
                                console.error("报告选择变化失败:", e);
                            }
                        }
                    }
                });
                
                // 禁用浏览器默认的长按菜单
                document.addEventListener('contextmenu', function(e) {
                    e.preventDefault();
                    return false;
                }, true);
                
                // 应用全局选择样式
                var style = document.createElement('style');
                style.textContent = `
                    * {
                        -webkit-user-select: text !important;
                        user-select: text !important;
                    }
                    ::selection {
                        background: rgba(33, 150, 243, 0.4) !important;
                    }
                `;
                document.head.appendChild(style);
                
                console.log("文本选择增强已启用");
                return "selection-js-injected";
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(script) { result ->
            Log.d(TAG, "文本选择JavaScript注入结果: $result")
        }
    }

    private fun setupSaveButton() {
        saveButton.visibility = View.VISIBLE
        saveButton.setOnClickListener {
            if (!isSaving) {
                saveCurrentSearchEngines()
            }
        }
    }

    private fun saveCurrentSearchEngines() {
        isSaving = true
        try {
            // 获取当前所有窗口的搜索引擎
            val searchEngines = mutableListOf<SearchEngine>()
            
            // 获取左侧窗口的搜索引擎
            leftWebView.let { webView ->
                val leftEngine = SearchEngine(
                    name = webView.title ?: "未知搜索引擎",
                    url = webView.url ?: "",
                    iconResId = R.drawable.ic_search,
                    description = "从浏览器保存的搜索引擎"
                )
                searchEngines.add(leftEngine)
            }
            
            // 获取右侧窗口的搜索引擎
            rightWebView.let { webView ->
                val rightEngine = SearchEngine(
                    name = webView.title ?: "未知搜索引擎",
                    url = webView.url ?: "",
                    iconResId = R.drawable.ic_search,
                    description = "从浏览器保存的搜索引擎"
                )
                searchEngines.add(rightEngine)
            }
            
            // 保存搜索引擎
            val searchEngineManager = SearchEngineManager.getInstance(this)
            searchEngines.forEach { engine ->
                searchEngineManager.saveSearchEngine(engine)
            }
            
            // 显示成功提示
            showSaveSuccessAnimation(saveButton)
            Toast.makeText(this, "搜索引擎保存成功", Toast.LENGTH_SHORT).show()
            
        } catch (e: Exception) {
            Log.e(TAG, "保存搜索引擎失败", e)
            Toast.makeText(this, "保存失败：${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            isSaving = false
        }
    }

    private fun showSaveSuccessAnimation(view: View) {
        view.animate()
            .scaleX(1.2f)
            .scaleY(1.2f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    private fun extractSearchUrl(url: String): String {
        // 从当前URL中提取搜索URL模板
        return try {
            val uri = Uri.parse(url)
            val query = uri.getQueryParameter("q") ?: uri.getQueryParameter("query")
            if (query != null) {
                url.replace(query, "{searchTerms}")
            } else {
                ""
            }
        } catch (e: Exception) {
            Log.e(TAG, "提取搜索URL失败", e)
            ""
        }
    }
} 