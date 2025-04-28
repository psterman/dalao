package com.example.aifloatingball.manager

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.Toast
import com.example.aifloatingball.R
import com.example.aifloatingball.view.TextSelectionHandleView
import com.example.aifloatingball.view.TextSelectionMenuView
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class HandleType {
    START, END
}

class TextSelectionManager(
    private val context: Context,
    private var webView: WebView,
    private val windowManager: WindowManager,
    private val onSelectionChanged: (String) -> Unit,
    private val onHandleMoved: (HandleType, Int, Int) -> Unit
) {
    companion object {
        private const val TAG = "TextSelectionManager"
        // 选择柄大小
        private const val HANDLE_WIDTH = 40
        private const val HANDLE_HEIGHT = 40
        // 菜单位置偏移
        private const val MENU_OFFSET_Y = -10
    }

    private var leftHandle: TextSelectionHandleView? = null
    private var rightHandle: TextSelectionHandleView? = null
    private var selectionMenu: PopupWindow? = null
    private var menuView: TextSelectionMenuView? = null
    
    private var isSelectionActive = false
    private var selectedText = ""
    private var leftHandlePosition = Point(0, 0)
    private var rightHandlePosition = Point(0, 0)
    
    // 主线程Handler
    private val handler = Handler(Looper.getMainLooper())

    private var selectionToolbar: View? = null
    private var startX = 0f
    private var startY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var activeHandle: TextSelectionHandleView? = null

    private var startHandle: ImageView? = null
    private var endHandle: ImageView? = null
    private var isHandleDragging = false
    private var activeHandleType: HandleType? = null

    init {
        createSelectionHandles()
    }

    private fun createSelectionHandles() {
        // 创建开始选择柄
        startHandle = ImageView(context).apply {
            setImageResource(R.drawable.ic_text_select_handle_start)
            visibility = View.GONE
        }

        // 创建结束选择柄
        endHandle = ImageView(context).apply {
            setImageResource(R.drawable.ic_text_select_handle_end)
            visibility = View.GONE
        }

        // 设置选择柄的触摸事件
        val handleTouchListener = View.OnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isHandleDragging = true
                    activeHandleType = if (view == startHandle) HandleType.START else HandleType.END
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isHandleDragging) {
                        val dx = event.rawX - lastTouchX
                        val dy = event.rawY - lastTouchY
                        
                        val params = view.layoutParams as WindowManager.LayoutParams
                        params.x += dx.toInt()
                        params.y += dy.toInt()
                        
                        try {
                            windowManager.updateViewLayout(view, params)
                            activeHandleType?.let { handleType ->
                                onHandleMoved(handleType, params.x, params.y)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                        
                        lastTouchX = event.rawX
                        lastTouchY = event.rawY
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isHandleDragging = false
                    activeHandleType = null
                    true
                }
                else -> false
            }
        }

        startHandle?.setOnTouchListener(handleTouchListener)
        endHandle?.setOnTouchListener(handleTouchListener)
    }

    fun startSelection(x: Int, y: Int) {
        // 记录开始调试信息
        Log.d(TAG, "开始文本选择: x=$x, y=$y")
        
        if (isSelectionActive) {
            Log.d(TAG, "选择已激活，忽略")
            return
        }

        // 尝试直接使用显示选择柄方法，用于测试
        try {
            Log.d(TAG, "尝试直接显示选择柄")
            showTestHandles()
            return
        } catch (e: Exception) {
            Log.e(TAG, "测试选择柄显示失败: ${e.message}")
        }

        // 原始实现 - 注入JavaScript获取初始选择位置
        val script = """
            (function() {
                try {
                    // 创建范围
                    var range = document.caretRangeFromPoint($x, $y);
                    if (range) {
                        // 清除现有选择
                        window.getSelection().removeAllRanges();
                        // 设置新选择
                        window.getSelection().addRange(range);
                        // 扩展选择到单词
                        window.getSelection().modify('extend', 'forward', 'word');
                        
                        // 获取选择区域位置
                        var rects = range.getClientRects();
                        var firstRect = rects[0];
                        var lastRect = rects[rects.length - 1];
                        
                        return JSON.stringify({
                            text: window.getSelection().toString(),
                            left: {
                                x: firstRect.left,
                                y: firstRect.bottom
                            },
                            right: {
                                x: lastRect.right,
                                y: lastRect.bottom
                            }
                        });
                    }
                    return "null-range";
                } catch (e) {
                    return "error:" + e.toString();
                }
            })();
        """.trimIndent()

        Log.d(TAG, "执行JavaScript获取选择")
        webView.evaluateJavascript(script) { result ->
            Log.d(TAG, "JavaScript返回结果: $result")
            
            if (result != "null" && !result.startsWith("\"null") && !result.startsWith("\"error")) {
                try {
                    val cleanResult = result.replace("\\\"", "\"")
                        .replace("^\"|\"$".toRegex(), "")
                    Log.d(TAG, "处理选择结果: $cleanResult")
                    
                    val json = JSONObject(cleanResult)
                    val selectedText = json.getString("text")
                    Log.d(TAG, "选中文本: $selectedText")
                    
                    val leftPos = json.getJSONObject("left")
                    val rightPos = json.getJSONObject("right")

                    // 创建并显示选择柄
                    showSelectionHandles(
                        Point(leftPos.getInt("x"), leftPos.getInt("y")),
                        Point(rightPos.getInt("x"), rightPos.getInt("y"))
                    )

                    isSelectionActive = true
                    onSelectionChanged(selectedText)
                } catch (e: Exception) {
                    Log.e(TAG, "解析JSON失败", e)
                    Toast.makeText(context, "文本选择失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } else {
                Log.e(TAG, "JavaScript返回空或错误: $result")
                Toast.makeText(context, "无法选择文本，请尝试其他区域", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 测试方法 - 显示固定位置的选择柄
    private fun showTestHandles() {
        Log.d(TAG, "显示测试选择柄")
        
        // 获取WebView位置
        val webViewPos = IntArray(2)
        webView.getLocationOnScreen(webViewPos)
        
        // 设置WebView中央位置作为测试点
        val centerX = webView.width / 2
        val centerY = webView.height / 2
        
        // 注入JavaScript创建初始选择
        val initScript = """
            (function() {
                try {
                    // 找到中心位置的元素
                    var centerElement = document.elementFromPoint($centerX, $centerY);
                    if (!centerElement) {
                        // 如果没有元素，尝试body
                        centerElement = document.body;
                    }
                    
                    // 确保元素可选
                    centerElement.style.webkitUserSelect = 'text';
                    centerElement.style.userSelect = 'text';
                    
                    // 创建选择
                    var selection = window.getSelection();
                    selection.removeAllRanges();
                    
                    var range = document.createRange();
                    
                    // 如果是文本节点，选择一部分文本
                    if (centerElement.firstChild && centerElement.firstChild.nodeType === 3) {
                        var textNode = centerElement.firstChild;
                        var start = Math.floor(textNode.length / 3);
                        var end = Math.floor(textNode.length * 2 / 3);
                        range.setStart(textNode, start);
                        range.setEnd(textNode, end);
                    } else {
                        // 否则选择整个元素
                        range.selectNodeContents(centerElement);
                    }
                    
                    selection.addRange(range);
                    
                    // 获取选择区域位置
                    var rects = range.getClientRects();
                    if (rects.length > 0) {
                        var firstRect = rects[0];
                        var lastRect = rects[rects.length - 1];
                        
                        return JSON.stringify({
                            text: selection.toString(),
                            left: {
                                x: firstRect.left + window.scrollX,
                                y: firstRect.bottom + window.scrollY
                            },
                            right: {
                                x: lastRect.right + window.scrollX,
                                y: lastRect.bottom + window.scrollY
                            }
                        });
                    }
                    return "no-rects";
                } catch(e) {
                    return "error:" + e.message;
                }
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(initScript) { result ->
            Log.d(TAG, "测试选择初始化结果: $result")
            
            if (result != "null" && !result.startsWith("\"no-") && !result.startsWith("\"error:")) {
                try {
                    val jsonStr = result.replace("\\\"", "\"")
                        .replace("^\"|\"$".toRegex(), "")
                    val json = JSONObject(jsonStr)
                    
                    val selectedText = json.getString("text")
                    Log.d(TAG, "测试选中文本: $selectedText")
                    
                    val leftPos = json.getJSONObject("left")
                    val rightPos = json.getJSONObject("right")
                    
                    // 显示选择柄
                    showSelectionHandles(
                        leftPos.getInt("x"),
                        leftPos.getInt("y"),
                        rightPos.getInt("x"),
                        rightPos.getInt("y")
                    )
                    
                    onSelectionChanged(selectedText)
                } catch (e: Exception) {
                    Log.e(TAG, "处理测试选择结果失败", e)
                    
                    // 如果处理失败，使用默认位置
                    val leftX = centerX - 50
                    val rightX = centerX + 50
                    
                    // 显示选择柄
                    showSelectionHandles(leftX, centerY, rightX, centerY)
                    onSelectionChanged("测试选择文本")
                }
            } else {
                // 使用默认位置
                val leftX = centerX - 50
                val rightX = centerX + 50
                
                // 显示选择柄
                showSelectionHandles(leftX, centerY, rightX, centerY)
                onSelectionChanged("测试选择文本")
            }
        }
    }

    private fun showSelectionHandles(leftPoint: Point, rightPoint: Point) {
        // 创建选择柄的布局参数
        val handleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
        }

        // 创建左侧选择柄
        leftHandle = TextSelectionHandleView(
            context = context,
            isLeft = true,
            windowManager = windowManager,
            onHandleMoved = { deltaX, deltaY ->
                updateSelection(true, deltaX, deltaY)
            },
            onHandleReleased = {
                showSelectionMenu()
            }
        )

        // 创建右侧选择柄
        rightHandle = TextSelectionHandleView(
            context = context,
            isLeft = false,
            windowManager = windowManager,
            onHandleMoved = { deltaX, deltaY ->
                updateSelection(false, deltaX, deltaY)
            },
            onHandleReleased = {
                showSelectionMenu()
            }
        )

        // 添加选择柄到窗口
        try {
            windowManager.addView(leftHandle, handleParams)
            // 创建新的布局参数实例而不是使用clone
            val rightHandleParams = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.START or Gravity.TOP
            }
            windowManager.addView(rightHandle, rightHandleParams)

            // 更新选择柄位置
            leftHandle?.updatePosition(leftPoint.x, leftPoint.y)
            rightHandle?.updatePosition(rightPoint.x, rightPoint.y)
            
            // 设置触摸监听器
            setupHandleTouchListeners()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateSelection(isLeft: Boolean, deltaX: Float, deltaY: Float) {
        val handle = if (isLeft) leftHandle else rightHandle
        val currentPos = handle?.getHandlePosition() ?: return

        // 更新选择柄位置
        val newX = (currentPos.x + deltaX).toInt()
        val newY = (currentPos.y + deltaY).toInt()
        handle.updatePosition(newX, newY)
        
        // 将屏幕坐标转换为WebView相对坐标
        val location = IntArray(2)
        webView.getLocationOnScreen(location)
        
        val webViewX = newX - location[0]
        val webViewY = newY - location[1]
        
        // 使用更强大的JavaScript来更新选择
        Log.d(TAG, "更新选择: ${if(isLeft) "左" else "右"}柄移动到WebView相对位置($webViewX, $webViewY)")
        
        val script = """
            (function() {
                try {
                    // 确保样式设置正确
                    document.body.style.webkitUserSelect = 'text';
                    document.body.style.userSelect = 'text';
                    
                    var selection = window.getSelection();
                    if (selection.rangeCount === 0) {
                        // 如果没有选择范围，创建一个
                        var range = document.createRange();
                        var node = document.elementFromPoint($webViewX, $webViewY);
                        if (node) {
                            range.selectNodeContents(node);
                            selection.addRange(range);
                        } else {
                            return "no-node-found";
                        }
                    }
                    
                    // 获取当前选择范围
                    var range = selection.getRangeAt(0);
                    
                    // 获取目标点的新位置
                    var newPosition = document.caretRangeFromPoint($webViewX, $webViewY);
                    if (!newPosition) {
                        // 兼容性处理
                        newPosition = document.caretRangeFromPoint($webViewX, $webViewY);
                    }
                    
                    if (!newPosition) {
                        return "no-position-found";
                    }
                    
                    // 更新选择范围边界
                    if (${isLeft}) {
                        // 更新左侧位置（开始位置）
                        range.setStart(newPosition.startContainer, newPosition.startOffset);
                    } else {
                        // 更新右侧位置（结束位置）
                        range.setEnd(newPosition.endContainer, newPosition.endOffset);
                    }
                    
                    // 应用新的选择
                    selection.removeAllRanges();
                    selection.addRange(range);
                    
                    // 保护选择不被清除
                    document.removeEventListener('mousedown', stopSelection, true);
                    document.removeEventListener('touchstart', stopSelection, true);
                    
                    function stopSelection(e) {
                        e.preventDefault();
                        e.stopPropagation();
                    }
                    
                    document.addEventListener('mousedown', stopSelection, true);
                    document.addEventListener('touchstart', stopSelection, true);
                    
                    // 获取选择文本
                    return selection.toString();
                } catch (e) {
                    console.error('更新选择失败:', e);
                    return "error:" + e.message;
                }
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { result ->
            Log.d(TAG, "选择更新结果: $result")
            if (result != "null" && !result.startsWith("\"no-") && !result.startsWith("\"error:")) {
                val selectedText = result.trim('"')
                onSelectionChanged(selectedText)
                
                // 获取选择区域位置以更新另一个柄
                updateOtherHandlePosition(isLeft)
            }
        }
    }

    // 更新另一个选择柄的位置，保持选择的视觉一致性
    private fun updateOtherHandlePosition(currentIsLeft: Boolean) {
        val script = """
            (function() {
                try {
                    var selection = window.getSelection();
                    if (selection.rangeCount > 0) {
                        var range = selection.getRangeAt(0);
                        var rects = range.getClientRects();
                        
                        if (rects.length > 0) {
                            // 如果当前移动的是左柄，更新右柄位置
                            // 如果当前移动的是右柄，更新左柄位置
                            var updateRect = ${if (currentIsLeft) "rects[rects.length - 1]" else "rects[0]"};
                            
                            return JSON.stringify({
                                x: ${if (currentIsLeft) "updateRect.right" else "updateRect.left"} + window.scrollX,
                                y: updateRect.bottom + window.scrollY
                            });
                        }
                    }
                    return null;
                } catch(e) {
                    return "error:" + e.message;
                }
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(script) { result ->
            if (result != "null" && !result.startsWith("\"error:")) {
                try {
                    val jsonStr = result.replace("\\\"", "\"")
                        .replace("^\"|\"$".toRegex(), "")
                    val json = JSONObject(jsonStr)
                    
                    // 获取需要更新的位置
                    val posX = json.getInt("x")
                    val posY = json.getInt("y")
                    
                    // 转换为屏幕坐标
                    val location = IntArray(2)
                    webView.getLocationOnScreen(location)
                    
                    val screenX = location[0] + posX
                    val screenY = location[1] + posY
                    
                    // 更新另一个柄的位置
                    val otherHandle = if (currentIsLeft) rightHandle else leftHandle
                    otherHandle?.updatePosition(screenX, screenY)
                } catch (e: Exception) {
                    Log.e(TAG, "更新另一个选择柄失败: ${e.message}")
                }
            }
        }
    }

    private fun updateSelectionText() {
        webView.evaluateJavascript(
            "(function() { return window.getSelection().toString(); })();"
        ) { result ->
            if (result != "null") {
                val selectedText = result.trim('"')
                onSelectionChanged(selectedText)
            }
        }
    }

    fun clearSelection() {
        isSelectionActive = false
        
        // 移除选择柄
        try {
            leftHandle?.let { windowManager.removeView(it) }
            rightHandle?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        leftHandle = null
        rightHandle = null

        // 清除WebView中的选择
        webView.evaluateJavascript(
            "window.getSelection().removeAllRanges();",
            null
        )
    }

    fun initializeHandles() {
        leftHandle = TextSelectionHandleView(
            context = context,
            isLeft = true,
            windowManager = windowManager,
            onHandleMoved = { _, _ -> },
            onHandleReleased = { }
        )
        
        rightHandle = TextSelectionHandleView(
            context = context,
            isLeft = false,
            windowManager = windowManager,
            onHandleMoved = { _, _ -> },
            onHandleReleased = { }
        )
        
        val handleParams = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.START or Gravity.TOP
        }

        try {
            windowManager.addView(leftHandle, handleParams)
            val rightHandleParams = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.START or Gravity.TOP
            }
            windowManager.addView(rightHandle, rightHandleParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateHandlePosition(handle: TextSelectionHandleView?, dx: Float, dy: Float) {
        handle?.let { handleView ->
            val params = handleView.layoutParams as? WindowManager.LayoutParams
            params?.let {
                it.x += dx.toInt()
                it.y += dy.toInt()
                try {
                    windowManager.updateViewLayout(handleView, it)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    private fun updateSelection() {
        val script = """
            (function() {
                var selection = window.getSelection();
                var range = document.caretRangeFromPoint(${leftHandle?.getHandlePosition()?.x}, ${leftHandle?.getHandlePosition()?.y});
                if (range) {
                    selection.removeAllRanges();
                    selection.addRange(range);
                    selection.extend(document.caretRangeFromPoint(${rightHandle?.getHandlePosition()?.x}, ${rightHandle?.getHandlePosition()?.y}).endContainer, 
                                  document.caretRangeFromPoint(${rightHandle?.getHandlePosition()?.x}, ${rightHandle?.getHandlePosition()?.y}).endOffset);
                }
                return window.getSelection().toString();
            })();
        """.trimIndent()

        webView.evaluateJavascript(script) { result ->
            if (result != "null") {
                val selectedText = result.trim('"')
                onSelectionChanged(selectedText)
            }
        }
    }

    fun showSelectionHandles(startX: Int, startY: Int, endX: Int, endY: Int) {
        try {
            Log.d(TAG, "显示选择柄 - 左:($startX,$startY) 右:($endX,$endY)")
            
            // 计算WebView在屏幕上的位置
            val location = IntArray(2)
            webView.getLocationOnScreen(location)
            
            // 转换为屏幕坐标
            val screenLeftX = location[0] + startX
            val screenLeftY = location[1] + startY
            val screenRightX = location[0] + endX
            val screenRightY = location[1] + endY
            
            // 设置选择柄位置
            if (leftHandle != null && rightHandle != null) {
                // 如果选择柄已创建，直接更新位置
                updateHandlePositions(screenLeftX, screenLeftY, screenRightX, screenRightY)
            } else {
                // 否则创建新的选择柄
                createVisibleHandles()
                // 设置选择柄初始位置
                updateHandlePositions(screenLeftX, screenLeftY, screenRightX, screenRightY)
            }
            
            // 确保选择柄可见
            leftHandle?.visibility = View.VISIBLE
            rightHandle?.visibility = View.VISIBLE
            
            isSelectionActive = true
        } catch (e: Exception) {
            Log.e(TAG, "显示选择柄失败", e)
        }
    }

    // 更新现有选择柄的位置
    fun updateHandlePositions(leftX: Int, leftY: Int, rightX: Int, rightY: Int) {
        val leftParams = leftHandle?.layoutParams as? WindowManager.LayoutParams
        val rightParams = rightHandle?.layoutParams as? WindowManager.LayoutParams
        
        leftParams?.let {
            it.x = leftX
            it.y = leftY
            try {
                leftHandle?.let { handle -> windowManager.updateViewLayout(handle, leftParams) }
            } catch (e: Exception) {
                Log.e(TAG, "更新左侧选择柄位置失败", e)
            }
        }
        
        rightParams?.let {
            it.x = rightX
            it.y = rightY
            try {
                rightHandle?.let { handle -> windowManager.updateViewLayout(handle, rightParams) }
            } catch (e: Exception) {
                Log.e(TAG, "更新右侧选择柄位置失败", e)
            }
        }
    }

    // 重命名为内部方法，避免重载冲突
    private fun showSelectionHandlesInternal(leftPoint: Point, rightPoint: Point) {
        // 创建选择柄的布局参数
        val handleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
        }

        // 创建左侧选择柄
        leftHandle = TextSelectionHandleView(
            context = context,
            isLeft = true,
            windowManager = windowManager,
            onHandleMoved = { deltaX, deltaY ->
                updateSelection(true, deltaX, deltaY)
            },
            onHandleReleased = {
                updateSelectionText()
            }
        )

        // 创建右侧选择柄
        rightHandle = TextSelectionHandleView(
            context = context,
            isLeft = false,
            windowManager = windowManager,
            onHandleMoved = { deltaX, deltaY ->
                updateSelection(false, deltaX, deltaY)
            },
            onHandleReleased = {
                updateSelectionText()
            }
        )

        // 添加选择柄到窗口
        try {
            windowManager.addView(leftHandle, handleParams)
            // 创建新的布局参数实例而不是使用clone
            val rightHandleParams = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.WRAP_CONTENT
                height = WindowManager.LayoutParams.WRAP_CONTENT
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.START or Gravity.TOP
            }
            windowManager.addView(rightHandle, rightHandleParams)

            // 更新选择柄位置
            leftHandle?.updatePosition(leftPoint.x, leftPoint.y)
            rightHandle?.updatePosition(rightPoint.x, rightPoint.y)
            
            // 设置触摸监听器
            setupHandleTouchListeners()
        } catch (e: Exception) {
            Log.e(TAG, "添加选择柄到窗口失败", e)
        }
    }

    fun hideSelectionHandles() {
        try {
            leftHandle?.let { handle -> windowManager.removeView(handle) }
            rightHandle?.let { handle -> windowManager.removeView(handle) }
            selectionToolbar?.let { toolbar -> windowManager.removeView(toolbar) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        isSelectionActive = false
    }

    fun isSelectionActive(): Boolean = isSelectionActive

    // 注入更强大的文本选择JavaScript
    fun injectSelectionJavaScript() {
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

    // 更新 WebView 引用
    fun updateWebView(newWebView: WebView) {
        if (this.webView != newWebView) {
            Log.d(TAG, "更新 WebView 引用")
            
            // 清除之前的选择
            clearSelection()
            
            // 更新引用
            this.webView = newWebView
            
            // 注入文本选择脚本
            injectSelectionJavaScript()
        }
    }

    // 在 TextSelectionHandleView 上设置触摸监听器
    private fun setupHandleTouchListeners() {
        leftHandle?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - lastTouchX
                    val dy = event.rawY - lastTouchY
                    
                    // 更新选择
                    updateSelection(true, dx, dy)
                    
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    updateSelectionText()
                    true
                }
                else -> false
            }
        }
        
        rightHandle?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - lastTouchX
                    val dy = event.rawY - lastTouchY
                    
                    // 更新选择
                    updateSelection(false, dx, dy)
                    
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_UP -> {
                    updateSelectionText()
                    true
                }
                else -> false
            }
        }
    }

    // 创建更明显的选择柄
    private fun createVisibleHandles() {
        // 确保创建的选择柄足够大，能够被用户清晰看到
        val handleParams = WindowManager.LayoutParams(
            50, // 宽度增加
            50, // 高度增加
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.START or Gravity.TOP
        }

        // 创建左侧选择柄
        leftHandle = TextSelectionHandleView(
            context = context,
            isLeft = true,
            windowManager = windowManager,
            onHandleMoved = { deltaX, deltaY ->
                updateSelection(true, deltaX, deltaY)
            },
            onHandleReleased = {
                updateSelectionText()
            }
        )

        // 创建右侧选择柄
        rightHandle = TextSelectionHandleView(
            context = context,
            isLeft = false,
            windowManager = windowManager,
            onHandleMoved = { deltaX, deltaY ->
                updateSelection(false, deltaX, deltaY)
            },
            onHandleReleased = {
                updateSelectionText()
            }
        )

        try {
            // 确保选择柄大小正确
            leftHandle?.let { handle ->
                handle.setMinimumWidth(50)
                handle.setMinimumHeight(50)
            }
            
            rightHandle?.let { handle ->
                handle.setMinimumWidth(50)
                handle.setMinimumHeight(50)
            }
            
            // 添加选择柄到窗口
            windowManager.addView(leftHandle, handleParams)
            
            val rightHandleParams = WindowManager.LayoutParams().apply {
                width = 50
                height = 50
                type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                format = PixelFormat.TRANSLUCENT
                gravity = Gravity.START or Gravity.TOP
            }
            windowManager.addView(rightHandle, rightHandleParams)
            
            // 设置触摸监听器
            setupHandleTouchListeners()
        } catch (e: Exception) {
            Log.e(TAG, "添加选择柄到窗口失败", e)
        }
    }

    /**
     * 创建并显示选择菜单
     */
    fun showSelectionMenu() {
        if (!isSelectionActive || selectedText.isEmpty()) return
        
        // 确保在主线程中运行
        handler.post {
            // 隐藏现有菜单
            hideSelectionMenu()
            
            // 获取位置 - 菜单显示在选择的中间上方
            val midX = (leftHandlePosition.x + rightHandlePosition.x) / 2
            val midY = min(leftHandlePosition.y, rightHandlePosition.y) + MENU_OFFSET_Y
            
            // 创建菜单视图
            menuView = TextSelectionMenuView(context).apply {
                setOnCopyClickListener {
                    copySelectedText()
                    cleanup()
                }
                setOnShareClickListener {
                    shareSelectedText()
                    cleanup()
                }
                setOnSearchClickListener {
                    searchSelectedText()
                    cleanup()
                }
            }
            
            // 创建弹出窗口
            selectionMenu = PopupWindow(
                menuView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            ).apply {
                isOutsideTouchable = true
                setBackgroundDrawable(context.getDrawable(R.drawable.app_selection_menu_background))
                
                // 在WebView中显示，位置相对于屏幕
                val location = IntArray(2)
                webView.getLocationOnScreen(location)
                
                showAtLocation(webView, Gravity.NO_GRAVITY, midX, midY)
            }
        }
    }

    /**
     * 隐藏选择菜单
     */
    fun hideSelectionMenu() {
        handler.post {
            selectionMenu?.dismiss()
            selectionMenu = null
            menuView = null
        }
    }

    /**
     * 设置选择的文本
     */
    fun setSelectedText(text: String) {
        this.selectedText = text
        Log.d(TAG, "选择的文本: $text")
    }

    /**
     * 复制选择的文本
     */
    private fun copySelectedText() {
        if (selectedText.isEmpty()) return
        
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) 
            as android.content.ClipboardManager
        
        val clip = android.content.ClipData.newPlainText("selected text", selectedText)
        clipboard.setPrimaryClip(clip)
        
        Log.d(TAG, "文本已复制到剪贴板: $selectedText")
    }

    /**
     * 分享选择的文本
     */
    private fun shareSelectedText() {
        if (selectedText.isEmpty()) return
        
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, selectedText)
        }
        
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(android.content.Intent.createChooser(intent, "分享文本").apply {
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    /**
     * 搜索选择的文本
     */
    private fun searchSelectedText() {
        if (selectedText.isEmpty()) return
        
        val intent = android.content.Intent(android.content.Intent.ACTION_WEB_SEARCH).apply {
            putExtra(android.app.SearchManager.QUERY, selectedText)
        }
        
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        hideSelectionHandles()
        hideSelectionMenu()
        isSelectionActive = false
    }

    /**
     * 获取当前选中的文本
     */
    fun getSelectedText(): String {
        return selectedText
    }
} 