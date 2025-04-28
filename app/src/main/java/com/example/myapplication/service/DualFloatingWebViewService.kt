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
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ImageButton
import android.widget.PopupWindow
import android.widget.Toast
import com.example.aifloatingball.R
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.manager.SearchEngineManager
import com.example.aifloatingball.manager.TextSelectionManager
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import androidx.core.content.ContextCompat
import android.view.Gravity
import android.view.ViewGroup
import android.content.ClipboardManager
import android.content.ClipData
import android.webkit.WebSettings
import org.json.JSONObject

class DualFloatingWebViewService : Service() {
    companion object {
        private const val TAG = "DualFloatingWebView"
    }

    // Define the missing WebView constants
    private object WebViewConstants {
        const val TEXT_TYPE = 3 // This is the value of WebView.HitTestResult.TEXT_TYPE
    }
    
    // 定义tag ID
    private object ResourceIds {
        var last_click_time: Int = View.generateViewId()
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
    private var textSelectionPopup: PopupWindow? = null

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
                },
                onHandleMoved = { handleType, x, y ->
                    // 处理左侧选择柄移动
                    Log.d(TAG, "左侧选择柄移动: $handleType, x=$x, y=$y")
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
                },
                onHandleMoved = { handleType, x, y ->
                    // 处理右侧选择柄移动
                    Log.d(TAG, "右侧选择柄移动: $handleType, x=$x, y=$y")
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

        webView.webViewClient = createWebViewClient(webView)
        
        // 明确启用长按
        webView.isLongClickable = true
        
        // 设置长按监听器以触发文本选择
        webView.setOnLongClickListener { view ->
            Log.d(TAG, "WebView长按检测")
            val result = webView.hitTestResult
            
            when (result.type) {
                WebView.HitTestResult.UNKNOWN_TYPE,
                WebView.HitTestResult.EDIT_TEXT_TYPE,
                WebViewConstants.TEXT_TYPE -> {
                    Log.d(TAG, "长按文本区域，尝试触发文本选择")
                    // 记录长按位置用于稍后菜单显示
                    val touchX = webView.getTag(ResourceIds.last_click_time + 1) as? Float ?: 0f
                    val touchY = webView.getTag(ResourceIds.last_click_time + 2) as? Float ?: 0f
                    
                    // 尝试使用JavaScript选择当前位置的文本
                    webView.evaluateJavascript("""
                        (function() {
                            try {
                                // 获取长按位置的元素
                                var clickedElement = document.elementFromPoint(window.lastTouchX, window.lastTouchY);
                                
                                if (clickedElement) {
                                    console.log("找到长按元素:", clickedElement.tagName);
                                    
                                    // 尝试选择元素内容
                                    var range = document.createRange();
                                    
                                    // 对于输入框，选择其中的文本
                                    if (clickedElement.tagName === 'INPUT' || clickedElement.tagName === 'TEXTAREA') {
                                        clickedElement.focus();
                                        clickedElement.select();
                                        return "已选择输入框文本";
                                    }
                                    
                                    // 尝试查找最近的文本节点
                                    var node = clickedElement;
                                    
                                    // 对于一般元素，尝试选择其文本内容
                                    if (node.childNodes.length > 0) {
                                        var textNodes = [];
                                        var walk = document.createTreeWalker(node, NodeFilter.SHOW_TEXT);
                                        var currentNode;
                                        while (currentNode = walk.nextNode()) {
                                            if (currentNode.textContent.trim().length > 0) {
                                                textNodes.push(currentNode);
                                            }
                                        }
                                        
                                        if (textNodes.length > 0) {
                                            // 选择第一个有内容的文本节点
                                            range.selectNodeContents(textNodes[0]);
                                        } else {
                                            // 如果没有文本节点，选择元素内容
                                            range.selectNodeContents(node);
                                        }
                                    } else {
                                        // 没有子节点，选择元素本身
                                        range.selectNodeContents(node);
                                    }
                                    
                                    // 应用选择
                                    var selection = window.getSelection();
                                    selection.removeAllRanges();
                                    selection.addRange(range);
                                    
                                    // 获取选择的范围并主动触发菜单显示
                                    var text = selection.toString().trim();
                                    if (text) {
                                        var rect = range.getBoundingClientRect();
                                        window.TextSelectionCallback.onSelectionChanged(
                                            text,
                                            rect.left.toString(),
                                            rect.top.toString(),
                                            rect.right.toString(),
                                            rect.bottom.toString()
                                        );
                                    }
                                    
                                    return "已触发文本选择";
                                }
                                
                                return "未找到长按元素";
                            } catch(e) {
                                console.error("长按选择文本失败:", e);
                                return "选择失败: " + e.message;
                            }
                        })();
                    """) { result ->
                        Log.d(TAG, "长按选择JavaScript结果: $result")
                        
                        // 无论JavaScript执行结果如何，尝试延迟显示菜单
                        // 这是个兜底机制，确保即使onSelectionChanged回调失败也能显示菜单
                        showSelectionMenuWithDelay(webView, touchX.toInt(), touchY.toInt())
                    }
                    return@setOnLongClickListener true
                }
                else -> {
                    Log.d(TAG, "长按非文本区域，不处理")
                    false
                }
            }
        }
        
        // 添加触摸监听器以记录触摸位置并增强双击选择功能
        webView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 将触摸坐标保存到tag中，用于长按时获取
                    webView.setTag(ResourceIds.last_click_time + 1, event.x)
                    webView.setTag(ResourceIds.last_click_time + 2, event.y)
                    
                    webView.evaluateJavascript("""
                        (function() {
                            window.lastTouchX = ${event.x};
                            window.lastTouchY = ${event.y};
                            return "Touch position recorded";
                        })();
                    """) { result ->
                        Log.d(TAG, "记录触摸位置: $result")
                    }
                }
                
                MotionEvent.ACTION_MOVE -> {
                    // 检查是否有文本被选中，并且正在移动
                    webView.evaluateJavascript("""
                        (function() {
                            var selection = window.getSelection();
                            var text = selection ? selection.toString().trim() : '';
                            if (text && text.length > 0) {
                                window.isDraggingSelection = true;
                                return "正在拖动选择文本: " + text;
                            }
                            return "无选中文本或非拖动操作";
                        })();
                    """) { result ->
                        // 只记录积极结果以避免日志过多
                        if (result.contains("正在拖动选择文本")) {
                            Log.d(TAG, "检测到拖动选择: $result")
                        }
                    }
                }
                
                MotionEvent.ACTION_UP -> {
                    // 检测是否是双击
                    val clickTime = System.currentTimeMillis()
                    val lastClickTime = webView.getTag(ResourceIds.last_click_time) as? Long ?: 0
                    val isDoubleClick = clickTime - lastClickTime < 300 // 300毫秒内的点击被视为双击
                    
                    if (isDoubleClick) {
                        webView.evaluateJavascript("""
                            (function() {
                                try {
                                    // 双击选择单词
                                    var selection = window.getSelection();
                                    selection.removeAllRanges();
                                    
                                    // 创建范围
                                    var range = document.caretRangeFromPoint(window.lastTouchX, window.lastTouchY);
                                    if (!range) return "无法获取点击位置范围";
                                    
                                    // 扩展到单词边界
                                    if (selection.modify) {
                                        // 添加初始范围
                                        selection.addRange(range);
                                        
                                        // 移动到单词起始位置
                                        selection.modify('move', 'backward', 'word');
                                        // 扩展到单词结束位置
                                        selection.modify('extend', 'forward', 'word');
                                        
                                        var text = selection.toString().trim();
                                        if (text) {
                                            var rect = selection.getRangeAt(0).getBoundingClientRect();
                                            window.TextSelectionCallback.onSelectionChanged(
                                                text,
                                                rect.left.toString(),
                                                rect.top.toString(),
                                                rect.right.toString(),
                                                rect.bottom.toString()
                                            );
                                        }
                                    }
                                    
                                    return "双击选择未能选中文本";
                                } catch(e) {
                                    console.error("双击选择失败:", e);
                                    return "双击选择失败: " + e.message;
                                }
                            })();
                        """) { result ->
                            Log.d(TAG, "双击选择结果: $result")
                        }
                    } else {
                        // 检查是否结束了拖动选择
                        webView.evaluateJavascript("""
                            (function() {
                                var wasDragging = window.isDraggingSelection || false;
                                var dragDuration = 0;
                                
                                if (window.dragSelectionStartTime) {
                                    dragDuration = Date.now() - window.dragSelectionStartTime;
                                    window.dragSelectionStartTime = null;
                                }
                                
                                // 重置拖动状态
                                window.isDraggingSelection = false;
                                
                                var selection = window.getSelection();
                                var text = selection ? selection.toString().trim() : "";
                                
                                if (text && wasDragging) {
                                    // 记录诊断信息
                                    console.log("手势结束，拖动选择完成，文本长度: " + text.length + "，持续时间: " + dragDuration + "ms");
                                    
                                    // 立即获取选择范围并触发菜单显示
                                    if (selection.rangeCount > 0) {
                                        var range = selection.getRangeAt(0);
                                        var rect = range.getBoundingClientRect();
                                        
                                        // 立即触发菜单显示，无延迟
                                        window.TextSelectionCallback.onSelectionChanged(
                                            text,
                                            rect.left.toString(),
                                            rect.top.toString(),
                                            rect.right.toString(),
                                            rect.bottom.toString()
                                        );
                                    }
                                    
                                    return JSON.stringify({
                                        wasDragging: true,
                                        text: text,
                                        duration: dragDuration
                                    });
                                }
                                return JSON.stringify({
                                    wasDragging: false,
                                    text: text
                                });
                            })();
                        """) { result ->
                            if (result.contains("wasDragging\":true")) {
                                try {
                                    val jsonResult = result.trim('"').replace("\\\"", "\"")
                                    val data = JSONObject(jsonResult)
                                    val text = data.optString("text")
                                    val duration = data.optInt("duration")
                                    Log.d(TAG, "拖动选择结束: 选中文本='$text', 持续时间=${duration}ms")
                                } catch (e: Exception) {
                                    Log.e(TAG, "解析拖动结果失败: ${e.message}")
                                }
                            }
                        }
                    }
                    
                    // 保存此次点击时间
                    webView.setTag(ResourceIds.last_click_time, clickTime)
                }
            }
            
            false  // 不消耗事件，允许其他处理器处理
        }

        // 添加 JavaScript 接口，优化处理逻辑
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun onSelectionChanged(text: String, startX: String, startY: String, endX: String, endY: String) {
                if (text.isNotEmpty()) {
                    Log.d(TAG, "JS回调: 选择改变 - 文本: $text 坐标: ($startX,$startY)-($endX,$endY)")
                    
                    // 确保在主线程处理UI操作
                    handler.post {
                        try {
                            val selectionManager = if (webView == leftWebView) {
                                leftTextSelectionManager
                            } else {
                                rightTextSelectionManager
                            }
                            
                            // 设置选中文本
                            selectionManager?.setSelectedText(text)
                            
                            // 解析坐标并显示选择柄
                            val startXInt = startX.toIntOrNull() ?: 0
                            val startYInt = startY.toIntOrNull() ?: 0
                            val endXInt = endX.toIntOrNull() ?: 0
                            val endYInt = endY.toIntOrNull() ?: 0
                            
                            selectionManager?.showSelectionHandles(startXInt, startYInt, endXInt, endYInt)
                            
                            // 检查当前是否有文本选择菜单显示
                            val hasActiveMenu = textSelectionPopup?.isShowing == true
                            
                            // 直接调用我们自己的菜单显示方法
                            val menuX = (startXInt + endXInt) / 2
                            val menuY = Math.min(startYInt, endYInt) - 30
                            
                            // 如果是拖动操作中，我们只在没有菜单时显示菜单，或者当拖动完成时
                            webView.evaluateJavascript("""
                                (function() {
                                    var isDragging = window.isDraggingSelection || false;
                                    return isDragging ? "拖动中" : "非拖动";
                                })();
                            """) { isDraggingResult ->
                                val isDragging = isDraggingResult.contains("拖动中")
                                if (!isDragging || !hasActiveMenu) {
                                    // 非拖动状态或无菜单时显示菜单
                                    showTextSelectionMenu(webView, menuX, menuY)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "处理选择改变失败: ${e.message}", e)
                        }
                    }
                } else {
                    Log.d(TAG, "JS回调: 空文本选择")
                }
            }
            
            @JavascriptInterface
            fun clearSelection() {
                handler.post {
                    try {
                        val selectionManager = if (webView == leftWebView) {
                            leftTextSelectionManager
                        } else {
                            rightTextSelectionManager
                        }
                        selectionManager?.clearSelection()
                        dismissPopupIfShowing()
                    } catch (e: Exception) {
                        Log.e(TAG, "清除选择失败: ${e.message}", e)
                    }
                }
            }
        }, "TextSelectionCallback")
    }

    /**
     * 注入处理文本选择的JavaScript脚本
     */
    private fun injectSelectionJavaScript(webView: WebView): String {
        val selectionScript = """
        (function() {
            // 避免重复初始化
            if (window.textSelectionInitialized) {
                console.log("文本选择JS已初始化，跳过");
                return "文本选择JavaScript已初始化";
            }
            
            console.log("开始初始化文本选择JS...");
            
            // 状态跟踪
            var activeSelection = false;
            var lastSelection = '';
            var lastSelectionRect = null;
            var isDraggingSelection = false;
            
            // 让所有元素可选择，包括iframe中的元素
            function makeAllElementsSelectable() {
                var style = document.createElement('style');
                style.textContent = '* { -webkit-user-select: text !important; user-select: text !important; }';
                document.head.appendChild(style);
                
                // 处理所有iframe
                var iframes = document.querySelectorAll('iframe');
                for (var i = 0; i < iframes.length; i++) {
                    try {
                        var iframeDocument = iframes[i].contentDocument || iframes[i].contentWindow.document;
                        var iframeStyle = iframeDocument.createElement('style');
                        iframeStyle.textContent = '* { -webkit-user-select: text !important; user-select: text !important; }';
                        iframeDocument.head.appendChild(iframeStyle);
                    } catch (e) {
                        console.log('无法访问iframe内容: ' + e.message);
                    }
                }
            }
            
            // 主动触发文本选择菜单显示
            function triggerSelectionMenu() {
                var selection = window.getSelection();
                var text = selection.toString().trim();
                
                if (text && selection.rangeCount > 0) {
                    var range = selection.getRangeAt(0);
                    var rect = range.getBoundingClientRect();
                    console.log("主动触发菜单显示，选中文本: " + text);
                    
                    try {
                        // 确保回调函数存在
                        if (window.TextSelectionCallback && window.TextSelectionCallback.onSelectionChanged) {
                            // 将选择信息发送到Android
                            window.TextSelectionCallback.onSelectionChanged(
                                text,
                                rect.left.toString(),
                                rect.top.toString(),
                                rect.right.toString(),
                                rect.bottom.toString()
                            );
                        } else {
                            console.error("TextSelectionCallback接口不可用");
                        }
                    } catch(e) {
                        console.error("触发菜单显示失败: " + e.message);
                    }
                }
            }
            
            // 选择变化监听器
            function handleSelectionChange() {
                var selection = window.getSelection();
                var text = selection.toString().trim();
                
                console.log("选择变化: " + text);
                
                if (text && text !== lastSelection) {
                    lastSelection = text;
                    activeSelection = true;
                    
                    if (selection.rangeCount > 0) {
                        var range = selection.getRangeAt(0);
                        var rect = range.getBoundingClientRect();
                        lastSelectionRect = rect;
                        
                        try {
                            // 将选择信息发送到Android
                            console.log("发送选择信息到Android: " + text);
                            window.TextSelectionCallback.onSelectionChanged(
                                text,
                                rect.left.toString(),
                                rect.top.toString(),
                                rect.right.toString(),
                                rect.bottom.toString()
                            );
                        } catch(e) {
                            console.error("发送选择信息失败: " + e.message);
                        }
                    }
                } else if (text && text === lastSelection && isDraggingSelection) {
                    // 文本相同但正在拖动选择，可能是调整选择范围
                    if (selection.rangeCount > 0) {
                        var range = selection.getRangeAt(0);
                        var rect = range.getBoundingClientRect();
                        
                        // 检查选择矩形是否变化
                        if (!lastSelectionRect || 
                            rect.left !== lastSelectionRect.left || 
                            rect.top !== lastSelectionRect.top || 
                            rect.right !== lastSelectionRect.right || 
                            rect.bottom !== lastSelectionRect.bottom) {
                            
                            lastSelectionRect = rect;
                            console.log("选择范围已更新，相同文本但位置变化");
                            
                            try {
                                // 通知位置变化
                                window.TextSelectionCallback.onSelectionChanged(
                                    text,
                                    rect.left.toString(),
                                    rect.top.toString(),
                                    rect.right.toString(),
                                    rect.bottom.toString()
                                );
                            } catch(e) {
                                console.error("发送更新选择位置失败: " + e.message);
                            }
                        }
                    }
                } else if (!text && activeSelection) {
                    lastSelection = '';
                    activeSelection = false;
                    lastSelectionRect = null;
                    try {
                        window.TextSelectionCallback.clearSelection();
                    } catch(e) {
                        console.error("清除选择失败: " + e.message);
                    }
                }
            }
            
            // 阻止默认上下文菜单
            function handleContextMenu(e) {
                if (activeSelection) {
                    e.preventDefault();
                    // 手动触发菜单显示
                    triggerSelectionMenu();
                    return false;
                }
                return true;
            }
            
            // 处理点击事件
            function handleClick(e) {
                // 如果点击位置不在选择区域内，则清除选择
                if (activeSelection && lastSelectionRect) {
                    var clickX = e.clientX;
                    var clickY = e.clientY;
                    
                    if (clickX < lastSelectionRect.left || clickX > lastSelectionRect.right ||
                        clickY < lastSelectionRect.top || clickY > lastSelectionRect.bottom) {
                        window.getSelection().removeAllRanges();
                        activeSelection = false;
                        lastSelection = '';
                        lastSelectionRect = null;
                        try {
                            window.TextSelectionCallback.clearSelection();
                        } catch(e) {
                            console.error("清除选择失败: " + e.message);
                        }
                    }
                }
            }
            
            // 双击事件处理
            function handleDoubleClick(e) {
                // 在点击位置选择单词
                var selection = window.getSelection();
                if (selection.rangeCount > 0) {
                    var range = selection.getRangeAt(0);
                    
                    // 尝试扩展到单词
                    if (selection.modify) {
                        selection.modify('move', 'backward', 'word');
                        selection.modify('extend', 'forward', 'word');
                        
                        // 检查选择是否成功
                        var text = selection.toString().trim();
                        if (text) {
                            activeSelection = true;
                            lastSelection = text;
                            
                            var updatedRange = selection.getRangeAt(0);
                            var rect = updatedRange.getBoundingClientRect();
                            lastSelectionRect = rect;
                            
                            console.log("双击选择单词: " + text);
                            
                            // 将选择信息发送到Android
                            try {
                                window.TextSelectionCallback.onSelectionChanged(
                                    text,
                                    rect.left.toString(),
                                    rect.top.toString(),
                                    rect.right.toString(),
                                    rect.bottom.toString()
                                );
                            } catch(e) {
                                console.error("双击选择回调失败: " + e.message);
                            }
                            
                            e.preventDefault();
                            return false;
                        }
                    }
                }
            }
            
            // 设置所有元素可选择
            makeAllElementsSelectable();
            
            // 添加事件监听器
            document.addEventListener('selectionchange', handleSelectionChange);
            document.addEventListener('contextmenu', handleContextMenu);
            document.addEventListener('click', handleClick);
            document.addEventListener('dblclick', handleDoubleClick);
            
            // 监听文本选择结束（手指抬起）事件
            document.addEventListener('mouseup', function(e) {
                // 检查是否正在拖动选择
                if (isDraggingSelection) {
                    console.log("检测到选择拖动操作结束");
                    
                    // 计算拖动持续时间
                    var dragDuration = 0;
                    if (window.dragSelectionStartTime) {
                        dragDuration = Date.now() - window.dragSelectionStartTime;
                        window.dragSelectionStartTime = null;
                    }
                    
                    var selection = window.getSelection();
                    var text = selection.toString().trim();
                    
                    // 如果有选中文本，立即显示菜单
                    if (text && text.length > 0) {
                        console.log("拖动选择完成，文本长度: " + text.length + "，持续时间: " + dragDuration + "ms");
                        
                        // 立即触发菜单显示
                        if (window.TextSelectionCallback) {
                            var range = selection.getRangeAt(0);
                            var rect = range.getBoundingClientRect();
                            
                            window.TextSelectionCallback.onSelectionChanged(
                                text,
                                rect.left.toString(),
                                rect.top.toString(),
                                rect.right.toString(),
                                rect.bottom.toString()
                            );
                        }
                    }
                    
                    isDraggingSelection = false;
                }
                
                // 短暂延迟确保文本选择已完成
                setTimeout(function() {
                    triggerSelectionMenu();
                }, 100);
            });
            
            document.addEventListener('touchend', function(e) {
                // 检查是否正在拖动选择
                if (isDraggingSelection) {
                    console.log("触摸拖动选择操作结束");
                    
                    // 计算拖动持续时间
                    var dragDuration = 0;
                    if (window.dragSelectionStartTime) {
                        dragDuration = Date.now() - window.dragSelectionStartTime;
                        window.dragSelectionStartTime = null;
                    }
                    
                    var selection = window.getSelection();
                    var text = selection.toString().trim();
                    
                    // 如果有选中文本，立即显示菜单
                    if (text && text.length > 0) {
                        console.log("触摸拖动选择完成，文本长度: " + text.length + "，持续时间: " + dragDuration + "ms");
                        
                        // 立即触发菜单显示
                        if (window.TextSelectionCallback) {
                            var range = selection.getRangeAt(0);
                            var rect = range.getBoundingClientRect();
                            
                            window.TextSelectionCallback.onSelectionChanged(
                                text,
                                rect.left.toString(),
                                rect.top.toString(),
                                rect.right.toString(),
                                rect.bottom.toString()
                            );
                        }
                    }
                    
                    isDraggingSelection = false;
                }
                
                // 短暂延迟确保文本选择已完成
                setTimeout(function() {
                    triggerSelectionMenu();
                }, 100);
            });
            
            // 添加触摸移动和鼠标移动处理，检测拖动选择
            document.addEventListener('touchmove', function(e) {
                var selection = window.getSelection();
                var text = selection.toString().trim();
                
                // 如果已经有文本被选中，则认为正在拖动选择
                if (text && text.length > 0) {
                    isDraggingSelection = true;
                    console.log("检测到触摸拖动选择: " + text);
                    
                    // 记录拖动选择的开始时间
                    if (!window.dragSelectionStartTime) {
                        window.dragSelectionStartTime = Date.now();
                    }
                }
            });
            
            document.addEventListener('mousemove', function(e) {
                var selection = window.getSelection();
                var text = selection.toString().trim();
                
                // 如果已经有文本被选中，则认为正在拖动选择
                if (text && text.length > 0) {
                    isDraggingSelection = true;
                    console.log("检测到鼠标拖动选择: " + text);
                    
                    // 记录拖动选择的开始时间
                    if (!window.dragSelectionStartTime) {
                        window.dragSelectionStartTime = Date.now();
                    }
                }
            });
            
            // 处理iframe的事件
            function handleIframes() {
                var iframes = document.querySelectorAll('iframe');
                for (var i = 0; i < iframes.length; i++) {
                    try {
                        var iframeDocument = iframes[i].contentDocument || iframes[i].contentWindow.document;
                        
                        iframeDocument.addEventListener('selectionchange', handleSelectionChange);
                        iframeDocument.addEventListener('contextmenu', handleContextMenu);
                        iframeDocument.addEventListener('click', handleClick);
                        iframeDocument.addEventListener('dblclick', handleDoubleClick);
                        
                        // 添加mouseup和touchend事件监听
                        iframeDocument.addEventListener('mouseup', function(e) {
                            setTimeout(function() { triggerSelectionMenu(); }, 100);
                        });
                        iframeDocument.addEventListener('touchend', function(e) {
                            setTimeout(function() { triggerSelectionMenu(); }, 100);
                        });
                        
                        // 添加触摸移动事件处理
                        iframeDocument.addEventListener('touchmove', function(e) {
                            var selection = iframeDocument.getSelection();
                            var text = selection.toString().trim();
                            if (text && text.length > 0) {
                                isDraggingSelection = true;
                                console.log("iframe内检测到触摸拖动选择");
                            }
                        });
                        
                        iframeDocument.addEventListener('mousemove', function(e) {
                            var selection = iframeDocument.getSelection();
                            var text = selection.toString().trim();
                            if (text && text.length > 0) {
                                isDraggingSelection = true;
                                console.log("iframe内检测到鼠标拖动选择");
                            }
                        });
                        
                        // 递归处理嵌套iframe
                        handleIframes.call(iframeDocument);
                    } catch (e) {
                        console.log('无法为iframe添加事件监听器: ' + e.message);
                    }
                }
            }
            
            // 处理iframe
            handleIframes();
            
            // 标记已初始化
            window.textSelectionInitialized = true;
            
            console.log("文本选择JS初始化完成");
            return "文本选择JavaScript初始化完成";
        })();
        """.trimIndent()
        
        webView.evaluateJavascript(selectionScript) { result ->
            Log.d(TAG, "文本选择脚本注入结果: $result")
        }
        
        return selectionScript
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

    // 添加文本选择菜单显示方法
    private fun showTextSelectionMenu(webView: WebView, x: Int, y: Int) {
        try {
            Log.d(TAG, "准备显示文本选择菜单在位置: ($x, $y)")
            
            // 先关闭已有弹窗
            dismissPopupIfShowing()
            
            // 1. 获取选择管理器
            val selectionManager = if (webView == leftWebView) {
                leftTextSelectionManager
            } else {
                rightTextSelectionManager
            }
            
            // 通过JavaScript重新获取选中文本和位置，确保最新
            webView.evaluateJavascript("""
                (function() {
                    var selection = window.getSelection();
                    var text = selection ? selection.toString().trim() : "";
                    
                    if (text && selection.rangeCount > 0) {
                        var range = selection.getRangeAt(0);
                        var rect = range.getBoundingClientRect();
                        
                        return JSON.stringify({
                            text: text,
                            left: Math.round(rect.left),
                            top: Math.round(rect.top),
                            right: Math.round(rect.right),
                            bottom: Math.round(rect.bottom)
                        });
                    }
                    return JSON.stringify({ text: "" });
                })();
            """) { result ->
                try {
                    val jsonResult = result.trim('"').replace("\\\"", "\"")
                    val data = JSONObject(jsonResult)
                    val selectedText = data.optString("text", "")
                    
                    Log.d(TAG, "JavaScript获取选中文本: '$selectedText'")
                    
                    if (selectedText.isNotEmpty()) {
                        // 更新选择管理器的选中文本
                        selectionManager?.setSelectedText(selectedText)
                        
                        // 获取最新的位置信息
                        val left = data.optInt("left", x)
                        val top = data.optInt("top", y)
                        val right = data.optInt("right", x)
                        val bottom = data.optInt("bottom", y)
                        
                        // 使用最新的位置计算菜单显示位置
                        var menuX = (left + right) / 2
                        var menuY = top - 40 // 在选中文本上方显示
                        
                        Log.d(TAG, "选中文本: '$selectedText'，长度: ${selectedText.length}，准备显示菜单")
                        
                        // 3. 创建菜单视图
                        val menuView = LayoutInflater.from(this).inflate(R.layout.text_selection_menu, null)
                        menuView.measure(
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                        )
                        
                        // 4. 设置菜单项点击事件
                        // 复制按钮
                        menuView.findViewById<View>(R.id.menu_copy)?.setOnClickListener {
                            Log.d(TAG, "点击复制按钮")
                            copySelectedText(webView)
                            dismissPopupIfShowing()
                        }
                        
                        // 分享按钮
                        menuView.findViewById<View>(R.id.menu_share)?.setOnClickListener {
                            Log.d(TAG, "点击分享按钮")
                            shareSelectedText(webView)
                            dismissPopupIfShowing()
                        }
                        
                        // 5. 计算位置
                        val location = IntArray(2)
                        webView.getLocationOnScreen(location)
                        
                        // 适配位置，确保在屏幕范围内
                        val menuWidth = menuView.measuredWidth
                        val menuHeight = menuView.measuredHeight
                        val screenWidth = resources.displayMetrics.widthPixels
                        val screenHeight = resources.displayMetrics.heightPixels
                        
                        // 默认显示在文本上方，加大偏移量确保不遮挡文本
                        menuX = (location[0] + menuX - menuWidth / 2).coerceIn(0, screenWidth - menuWidth)
                        menuY = (location[1] + menuY - menuHeight - 20).coerceIn(0, screenHeight - menuHeight)
                        
                        // 如果显示在上方会被遮挡，则显示在下方
                        if (menuY < 0 || menuY < location[1]) {
                            menuY = (location[1] + bottom + 20).coerceIn(0, screenHeight - menuHeight)
                        }
                        
                        // 6. 创建并显示PopupWindow
                        textSelectionPopup = PopupWindow(
                            menuView,
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            WindowManager.LayoutParams.WRAP_CONTENT,
                            true
                        ).apply {
                            isOutsideTouchable = true
                            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                            
                            // 点击外部自动关闭
                            setOnDismissListener {
                                Log.d(TAG, "文本选择菜单已关闭")
                                textSelectionPopup = null
                            }
                        }
                        
                        // 7. 显示并设置自动消失
                        textSelectionPopup?.showAtLocation(webView, android.view.Gravity.NO_GRAVITY, menuX, menuY)
                        Log.d(TAG, "文本选择菜单已显示在: ($menuX, $menuY)")
                        
                        // 自动关闭计时器
                        handler.postDelayed({
                            dismissPopupIfShowing()
                        }, 8000) // 8秒后自动关闭
                    } else {
                        Log.d(TAG, "没有选中文本，不显示菜单")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "处理JavaScript结果失败: ${e.message}", e)
                    
                    // 使用备用方法尝试显示菜单
                    val selectedText = selectionManager?.getSelectedText() ?: ""
                    if (selectedText.isNotEmpty()) {
                        displayBackupMenu(webView, x, y, selectedText)
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "显示文本选择菜单失败: ${e.message}", e)
        }
    }
    
    // 备用菜单显示方法，在主方法失败时使用
    private fun displayBackupMenu(webView: WebView, x: Int, y: Int, selectedText: String) {
        try {
            Log.d(TAG, "使用备用方法显示菜单，选中文本: '$selectedText'")
            
            // 创建菜单视图
            val menuView = LayoutInflater.from(this).inflate(R.layout.text_selection_menu, null)
            
            // 设置点击事件
            menuView.findViewById<View>(R.id.menu_copy)?.setOnClickListener {
                copySelectedText(webView)
                dismissPopupIfShowing()
            }
            
            menuView.findViewById<View>(R.id.menu_share)?.setOnClickListener {
                shareSelectedText(webView)
                dismissPopupIfShowing()
            }
            
            // 计算位置
            val location = IntArray(2)
            webView.getLocationOnScreen(location)
            
            // 创建并显示PopupWindow
            textSelectionPopup = PopupWindow(
                menuView,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                true
            ).apply {
                isOutsideTouchable = true
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            }
            
            // 直接在触摸位置显示
            val menuX = location[0] + x
            val menuY = location[1] + y - 50
            
            textSelectionPopup?.showAtLocation(webView, android.view.Gravity.NO_GRAVITY, menuX, menuY)
            
            // 自动关闭计时器
            handler.postDelayed({
                dismissPopupIfShowing()
            }, 8000)
        } catch (e: Exception) {
            Log.e(TAG, "备用菜单显示失败: ${e.message}", e)
        }
    }

    // 关闭弹窗
    private fun dismissPopupIfShowing() {
        textSelectionPopup?.let { popup ->
            if (popup.isShowing) {
                try {
                    Log.d(TAG, "关闭文本选择菜单")
                    popup.dismiss()
                } catch (e: Exception) {
                    Log.e(TAG, "关闭菜单失败: ${e.message}")
                }
                textSelectionPopup = null
            }
        }
    }

    // 复制选中文本
    private fun copySelectedText(webView: WebView) {
        val selectionManager = if (webView == leftWebView) leftTextSelectionManager else rightTextSelectionManager
        val selectedText = selectionManager?.getSelectedText() ?: ""
        
        if (selectedText.isNotEmpty()) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("选中文本", selectedText)
            clipboard.setPrimaryClip(clip)
            
            Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "已复制文本: $selectedText")
        }
    }

    // 全选文本
    private fun selectAllText(webView: WebView) {
        webView.evaluateJavascript("""
            (function() {
                try {
                    var selection = window.getSelection();
                    selection.removeAllRanges();
                    
                    var range = document.createRange();
                    range.selectNodeContents(document.body);
                    selection.addRange(range);
                    
                    // 触发选择变化事件，通知原生代码
                    var event = new Event('selectionchange');
                    document.dispatchEvent(event);
                    
                    return selection.toString().length;
                } catch(e) {
                    console.error('全选失败:', e);
                    return 0;
                }
            })();
        """.trimIndent()) { result ->
            Log.d(TAG, "全选结果: 选择了 $result 个字符")
            Toast.makeText(this, "已全选文本", Toast.LENGTH_SHORT).show()
        }
    }

    // 分享选中文本
    private fun shareSelectedText(webView: WebView) {
        val selectionManager = if (webView == leftWebView) leftTextSelectionManager else rightTextSelectionManager
        val selectedText = selectionManager?.getSelectedText() ?: ""
        
        if (selectedText.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_SEND)
            intent.type = "text/plain"
            intent.putExtra(Intent.EXTRA_TEXT, selectedText)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            try {
                startActivity(Intent.createChooser(intent, "分享文本").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                Log.d(TAG, "已启动分享文本: $selectedText")
            } catch (e: Exception) {
                Log.e(TAG, "分享文本失败: ${e.message}")
                Toast.makeText(this, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 全屏打开
    private fun openInFullscreen(webView: WebView) {
        try {
            val url = webView.url
            if (url != null) {
                val intent = Intent(this, Class.forName("com.example.aifloatingball.HomeActivity"))
                intent.action = Intent.ACTION_VIEW
                intent.data = Uri.parse(url)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                Log.d(TAG, "在HomeActivity中全屏打开: $url")
                Toast.makeText(this, "已在全屏模式打开", Toast.LENGTH_SHORT).show()
            } else {
                Log.e(TAG, "无法获取当前URL")
                Toast.makeText(this, "无法获取当前页面链接", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "全屏打开失败: ${e.message}")
            Toast.makeText(this, "全屏打开失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // 清除选择
    private fun clearSelection(webView: WebView) {
        val selectionManager = if (webView == leftWebView) leftTextSelectionManager else rightTextSelectionManager
        selectionManager?.clearSelection()
        Log.d(TAG, "已清除文本选择")
    }

    // 更新webViewClient以优化页面加载完成后的行为
    private fun createWebViewClient(webView: WebView): WebViewClient {
        return object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                Log.d(TAG, "页面开始加载: $url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "页面加载完成: $url")
                
                // 注入文本选择JavaScript
                if (view != null) {
                    // 先清除任何先前的选择
                    view.evaluateJavascript(
                        "window.getSelection().removeAllRanges();",
                        null
                    )
                    
                    // 注入文本选择JavaScript
                    val scriptResult = injectSelectionJavaScript(view)
                    
                    // 调试检查JS接口是否可用
                    view.evaluateJavascript("""
                        (function() {
                            try {
                                if (window.TextSelectionCallback) {
                                    console.log("TextSelectionCallback接口已注册");
                                    if (window.TextSelectionCallback.onSelectionChanged) {
                                        console.log("onSelectionChanged方法已注册");
                                        return "接口检查正常";
                                    } else {
                                        console.error("onSelectionChanged方法未注册");
                                        return "onSelectionChanged方法未注册";
                                    }
                                } else {
                                    console.error("TextSelectionCallback接口未注册");
                                    return "TextSelectionCallback接口未注册";
                                }
                            } catch(e) {
                                console.error("检查接口失败: " + e.message);
                                return "接口检查失败: " + e.message;
                            }
                        })();
                    """) { result ->
                        Log.d(TAG, "JS接口检查结果: $result")
                    }
                    
                    // 确保WebView准备就绪
                    handler.postDelayed({
                        Log.d(TAG, "WebView准备就绪，设置完成")
                    }, 300)
                }
            }
        }
    }

    // 长按完成后延迟显示菜单
    private fun showSelectionMenuWithDelay(webView: WebView, x: Int, y: Int) {
        // 短暂延迟，确保文本选择已完成
        handler.postDelayed({
            try {
                val selectionManager = if (webView == leftWebView) {
                    leftTextSelectionManager
                } else {
                    rightTextSelectionManager
                }
                
                // 检查是否处于拖动状态
                webView.evaluateJavascript("""
                    (function() {
                        var wasDragging = window.isDraggingSelection || false;
                        var selection = window.getSelection();
                        var text = selection ? selection.toString().trim() : "";
                        
                        // 重置拖动状态
                        window.isDraggingSelection = false;
                        
                        return JSON.stringify({
                            wasDragging: wasDragging,
                            text: text,
                            hasSelection: text.length > 0
                        });
                    })();
                """) { result ->
                    try {
                        // 解析JSON结果
                        val jsonResult = result.trim('"').replace("\\\"", "\"")
                        val data = JSONObject(jsonResult)
                        val wasDragging = data.optBoolean("wasDragging", false)
                        val text = data.optString("text", "")
                        val hasSelection = data.optBoolean("hasSelection", false)
                        
                        Log.d(TAG, "延迟检查选择状态: 文本='$text', 之前是否拖动=$wasDragging")
                        
                        if (hasSelection) {
                            // 如果有选中文本，无论是否拖动过都显示菜单
                            selectionManager?.setSelectedText(text)
                            Log.d(TAG, "延迟显示文本菜单，选中文本: '$text'")
                            showTextSelectionMenu(webView, x, y)
                        } else {
                            Log.d(TAG, "延迟检查，但未发现选中文本")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "解析选择状态失败: ${e.message}")
                        // 失败时尝试直接显示菜单
                        val selectedText = selectionManager?.getSelectedText() ?: ""
                        if (selectedText.isNotEmpty()) {
                            showTextSelectionMenu(webView, x, y)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "延迟显示菜单失败: ${e.message}", e)
            }
        }, 200) // 200毫秒延迟
    }
} 