package com.example.aifloatingball.ui.text

import android.content.Context
import android.graphics.Point
import android.util.Log
import android.view.WindowManager
import android.webkit.WebView
import com.example.aifloatingball.view.TextSelectionHandleView

/**
 * 文本选择柄管理器
 * 负责创建和管理WebView中的文本选择柄
 */
class TextSelectionHandleManager(private val context: Context) {
    companion object {
        private const val TAG = "TextSelectionHandleManager"
    }
    
    private var leftHandle: TextSelectionHandleView? = null
    private var rightHandle: TextSelectionHandleView? = null
    private var currentWebView: WebView? = null
    private val windowManager by lazy { context.getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    
    // 选择范围坐标
    private var selectionLeft = 0
    private var selectionTop = 0
    private var selectionRight = 0
    private var selectionBottom = 0
    
    /**
     * 显示文本选择柄
     */
    fun showSelectionHandles(webView: WebView, left: Int, top: Int, right: Int, bottom: Int) {
        Log.d(TAG, "显示文本选择柄: left=$left, top=$top, right=$right, bottom=$bottom")
        currentWebView = webView
        
        // 保存选择范围坐标
        selectionLeft = left
        selectionTop = top
        selectionRight = right
        selectionBottom = bottom
        
        // 获取WebView在屏幕上的位置
        val location = IntArray(2)
        webView.getLocationOnScreen(location)
        val webViewX = location[0]
        val webViewY = location[1]
        
        // 创建并显示左选择柄
        if (leftHandle == null) {
            leftHandle = createSelectionHandle(true) { deltaX, deltaY ->
                // 左柄移动处理
                handleLeftHandleMoved(deltaX, deltaY)
            }
        }
        
        // 创建并显示右选择柄
        if (rightHandle == null) {
            rightHandle = createSelectionHandle(false) { deltaX, deltaY ->
                // 右柄移动处理
                handleRightHandleMoved(deltaX, deltaY)
            }
        }
        
        // 更新选择柄位置
        leftHandle?.let {
            it.updatePosition(
                webViewX + left - it.width / 2,
                webViewY + bottom
            )
        }
        
        rightHandle?.let {
            it.updatePosition(
                webViewX + right - it.width / 2,
                webViewY + bottom
            )
        }
    }
    
    /**
     * 隐藏文本选择柄
     */
    fun hideSelectionHandles() {
        try {
            leftHandle?.let {
                windowManager.removeView(it)
                leftHandle = null
            }
            
            rightHandle?.let {
                windowManager.removeView(it)
                rightHandle = null
            }
            
            Log.d(TAG, "文本选择柄已隐藏")
        } catch (e: Exception) {
            Log.e(TAG, "隐藏文本选择柄失败", e)
        }
    }
    
    /**
     * 创建文本选择柄
     */
    private fun createSelectionHandle(
        isLeft: Boolean,
        onHandleMoved: (Float, Float) -> Unit
    ): TextSelectionHandleView {
        // 创建选择柄视图
        val handle = TextSelectionHandleView(
            context,
            isLeft,
            windowManager,
            onHandleMoved
        ) {
            // 处理柄释放回调
            Log.d(TAG, "选择柄释放: ${if (isLeft) "左" else "右"}")
        }
        
        // 创建WindowManager布局参数
        val params = WindowManager.LayoutParams().apply {
            // 设置窗口类型为应用窗口或系统提示窗口(取决于是否运行在悬浮窗服务中)
            type = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            }
            
            // 设置窗口标志
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            
            // 窗口格式
            format = android.graphics.PixelFormat.TRANSLUCENT
            
            // 设置窗口尺寸
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
        }
        
        // 将选择柄添加到窗口
        try {
            windowManager.addView(handle, params)
        } catch (e: Exception) {
            Log.e(TAG, "添加选择柄到窗口失败", e)
        }
        
        return handle
    }
    
    /**
     * 处理左柄移动
     */
    private fun handleLeftHandleMoved(deltaX: Float, deltaY: Float) {
        val leftHandlePos = leftHandle?.getHandlePosition() ?: return
        val location = IntArray(2)
        currentWebView?.getLocationOnScreen(location) ?: return
        
        // 计算WebView内相对坐标
        val relativeX = leftHandlePos.x + deltaX - location[0]
        val relativeY = leftHandlePos.y - location[1]
        
        // 更新选择范围
        updateSelectionForLeftHandle(relativeX.toInt(), relativeY.toInt())
    }
    
    /**
     * 处理右柄移动
     */
    private fun handleRightHandleMoved(deltaX: Float, deltaY: Float) {
        val rightHandlePos = rightHandle?.getHandlePosition() ?: return
        val location = IntArray(2)
        currentWebView?.getLocationOnScreen(location) ?: return
        
        // 计算WebView内相对坐标
        val relativeX = rightHandlePos.x + deltaX - location[0]
        val relativeY = rightHandlePos.y - location[1]
        
        // 更新选择范围
        updateSelectionForRightHandle(relativeX.toInt(), relativeY.toInt())
    }
    
    /**
     * 为左柄更新选择范围
     */
    private fun updateSelectionForLeftHandle(x: Int, y: Int) {
        currentWebView?.let { webView ->
            val script = """
                (function() {
                    try {
                        var range = document.caretRangeFromPoint($x, $y);
                        if (range && window.getSelection().rangeCount > 0) {
                            var selection = window.getSelection();
                            var currentRange = selection.getRangeAt(0);
                            
                            // 使用新位置更新范围起始点
                            currentRange.setStart(range.startContainer, range.startOffset);
                            selection.removeAllRanges();
                            selection.addRange(currentRange);
                            
                            // 获取更新后的范围位置
                            var rects = currentRange.getClientRects();
                            if (rects.length > 0) {
                                var firstRect = rects[0];
                                var lastRect = rects[rects.length - 1];
                                
                                return JSON.stringify({
                                    left: Math.round(firstRect.left),
                                    top: Math.round(firstRect.top),
                                    right: Math.round(lastRect.right),
                                    bottom: Math.round(lastRect.bottom),
                                    text: selection.toString()
                                });
                            }
                        }
                        return JSON.stringify({error: "No selection"});
                    } catch (e) {
                        return JSON.stringify({error: e.toString()});
                    }
                })();
            """.trimIndent()
            
            webView.evaluateJavascript(script) { result ->
                try {
                    val cleanResult = result?.replace("\\\"", "\"")?.trim('"') ?: ""
                    if (!cleanResult.contains("error")) {
                        val jsonReader = android.util.JsonReader(java.io.StringReader(cleanResult))
                        jsonReader.beginObject()
                        
                        var left = 0
                        var top = 0
                        var right = 0
                        var bottom = 0
                        
                        while (jsonReader.hasNext()) {
                            when (jsonReader.nextName()) {
                                "left" -> left = jsonReader.nextInt()
                                "top" -> top = jsonReader.nextInt()
                                "right" -> right = jsonReader.nextInt()
                                "bottom" -> bottom = jsonReader.nextInt()
                                else -> jsonReader.skipValue()
                            }
                        }
                        
                        jsonReader.endObject()
                        jsonReader.close()
                        
                        // 更新选择柄位置
                        updateHandlePositions(webView, left, top, right, bottom)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "更新左柄选择范围失败", e)
                }
            }
        }
    }
    
    /**
     * 为右柄更新选择范围
     */
    private fun updateSelectionForRightHandle(x: Int, y: Int) {
        currentWebView?.let { webView ->
            val script = """
                (function() {
                    try {
                        var range = document.caretRangeFromPoint($x, $y);
                        if (range && window.getSelection().rangeCount > 0) {
                            var selection = window.getSelection();
                            var currentRange = selection.getRangeAt(0);
                            
                            // 使用新位置更新范围结束点
                            currentRange.setEnd(range.startContainer, range.startOffset);
                            selection.removeAllRanges();
                            selection.addRange(currentRange);
                            
                            // 获取更新后的范围位置
                            var rects = currentRange.getClientRects();
                            if (rects.length > 0) {
                                var firstRect = rects[0];
                                var lastRect = rects[rects.length - 1];
                                
                                return JSON.stringify({
                                    left: Math.round(firstRect.left),
                                    top: Math.round(firstRect.top),
                                    right: Math.round(lastRect.right),
                                    bottom: Math.round(lastRect.bottom),
                                    text: selection.toString()
                                });
                            }
                        }
                        return JSON.stringify({error: "No selection"});
                    } catch (e) {
                        return JSON.stringify({error: e.toString()});
                    }
                })();
            """.trimIndent()
            
            webView.evaluateJavascript(script) { result ->
                try {
                    val cleanResult = result?.replace("\\\"", "\"")?.trim('"') ?: ""
                    if (!cleanResult.contains("error")) {
                        val jsonReader = android.util.JsonReader(java.io.StringReader(cleanResult))
                        jsonReader.beginObject()
                        
                        var left = 0
                        var top = 0
                        var right = 0
                        var bottom = 0
                        
                        while (jsonReader.hasNext()) {
                            when (jsonReader.nextName()) {
                                "left" -> left = jsonReader.nextInt()
                                "top" -> top = jsonReader.nextInt()
                                "right" -> right = jsonReader.nextInt()
                                "bottom" -> bottom = jsonReader.nextInt()
                                else -> jsonReader.skipValue()
                            }
                        }
                        
                        jsonReader.endObject()
                        jsonReader.close()
                        
                        // 更新选择柄位置
                        updateHandlePositions(webView, left, top, right, bottom)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "更新右柄选择范围失败", e)
                }
            }
        }
    }
    
    /**
     * 更新选择柄位置
     */
    private fun updateHandlePositions(webView: WebView, left: Int, top: Int, right: Int, bottom: Int) {
        val location = IntArray(2)
        webView.getLocationOnScreen(location)
        val webViewX = location[0]
        val webViewY = location[1]
        
        // 更新左柄位置
        leftHandle?.updatePosition(
            webViewX + left - (leftHandle?.width ?: 0) / 2,
            webViewY + bottom
        )
        
        // 更新右柄位置
        rightHandle?.updatePosition(
            webViewX + right - (rightHandle?.width ?: 0) / 2,
            webViewY + bottom
        )
        
        // 保存最新的选择范围
        selectionLeft = left
        selectionTop = top
        selectionRight = right
        selectionBottom = bottom
    }
} 