package com.example.aifloatingball.utils

import android.content.Context
import android.graphics.PixelFormat
import android.view.*
import android.webkit.WebView
import android.widget.ImageView
import com.example.aifloatingball.R
import org.json.JSONObject

class TextSelectionHelper(private val context: Context) {
    private var startHandle: ImageView? = null
    private var endHandle: ImageView? = null
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var webView: WebView? = null
    private var startX = 0f
    private var startY = 0f
    private var endX = 0f
    private var endY = 0f
    private var isDraggingHandle = false
    private var isTextSelectionActive = false

    init {
        createSelectionHandles()
    }

    private fun createSelectionHandles() {
        // Create start handle
        startHandle = (LayoutInflater.from(context)
            .inflate(R.layout.selection_handle_start, null) as ImageView).apply {
            setImageResource(R.drawable.selection_handle_start)
            visibility = View.GONE
        }

        // Create end handle
        endHandle = (LayoutInflater.from(context)
            .inflate(R.layout.selection_handle_end, null) as ImageView).apply {
            setImageResource(R.drawable.selection_handle_end)
            visibility = View.GONE
        }

        // Add handles to window
        val params = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.WRAP_CONTENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.START or Gravity.TOP
        }

        try {
            windowManager.addView(startHandle, params)
            windowManager.addView(endHandle, WindowManager.LayoutParams().apply {
                copyFrom(params)
            })
        } catch (e: Exception) {
            e.printStackTrace()
        }

        setupHandleTouchListeners()
    }

    private fun setupHandleTouchListeners() {
        val handleTouchListener = View.OnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Store initial touch position
                    startX = event.rawX
                    startY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    // Calculate new position
                    val dx = event.rawX - startX
                    val dy = event.rawY - startY
                    
                    // Update handle position
                    val params = view.layoutParams as WindowManager.LayoutParams
                    params.x += dx.toInt()
                    params.y += dy.toInt()
                    
                    // Update selection in WebView
                    updateSelection(view == startHandle, params.x, params.y)
                    
                    // Update view layout
                    windowManager.updateViewLayout(view, params)
                    
                    // Update initial position for next move
                    startX = event.rawX
                    startY = event.rawY
                    true
                }
                else -> false
            }
        }

        startHandle?.setOnTouchListener(handleTouchListener)
        endHandle?.setOnTouchListener(handleTouchListener)
    }

    fun showSelectionHandles(webView: WebView, startX: Int, startY: Int, endX: Int, endY: Int) {
        this.webView = webView
        
        // Update handle positions
        startHandle?.let { handle ->
            val params = handle.layoutParams as WindowManager.LayoutParams
            params.x = startX
            params.y = startY
            handle.visibility = View.VISIBLE
            windowManager.updateViewLayout(handle, params)
        }

        endHandle?.let { handle ->
            val params = handle.layoutParams as WindowManager.LayoutParams
            params.x = endX
            params.y = endY
            handle.visibility = View.VISIBLE
            windowManager.updateViewLayout(handle, params)
        }
    }

    fun hideSelectionHandles() {
        startHandle?.visibility = View.GONE
        endHandle?.visibility = View.GONE
    }

    private fun updateSelection(isStartHandle: Boolean, x: Int, y: Int) {
        // Convert screen coordinates to WebView coordinates
        webView?.let { view ->
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            
            val webViewX = x - location[0]
            val webViewY = y - location[1]
            
            // Inject JavaScript to update selection
            val script = if (isStartHandle) {
                "javascript:window.getSelection().setBaseAndExtent(" +
                    "document.elementFromPoint($webViewX, $webViewY), 0, " +
                    "window.getSelection().focusNode, window.getSelection().focusOffset);"
            } else {
                "javascript:window.getSelection().setBaseAndExtent(" +
                    "window.getSelection().anchorNode, window.getSelection().anchorOffset, " +
                    "document.elementFromPoint($webViewX, $webViewY), 0);"
            }
            
            view.evaluateJavascript(script, null)
        }
    }

    fun cleanup() {
        try {
            startHandle?.let { windowManager.removeView(it) }
            endHandle?.let { windowManager.removeView(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isOnHandle(x: Float, y: Float): Boolean {
        return startHandle?.let { isPointInsideView(x, y, it) } == true ||
                endHandle?.let { isPointInsideView(x, y, it) } == true
    }

    private fun isPointInsideView(x: Float, y: Float, view: View): Boolean {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        return x >= location[0] && x <= location[0] + view.width &&
                y >= location[1] && y <= location[1] + view.height
    }

    fun updateSelectionRange(x: Float, y: Float) {
        // Convert screen coordinates to WebView coordinates
        webView?.let { view ->
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            
            val webViewX = x - location[0]
            val webViewY = y - location[1]
            
            // Update selection via JavaScript
            val script = """
                (function() {
                    var elem = document.elementFromPoint($webViewX, $webViewY);
                    if (elem) {
                        var range = document.caretRangeFromPoint($webViewX, $webViewY);
                        if (range) {
                            var sel = window.getSelection();
                            sel.extend(range.startContainer, range.startOffset);
                            return true;
                        }
                    }
                    return false;
                })();
            """.trimIndent()
            
            view.evaluateJavascript(script, null)
        }
    }

    fun toggleWindowFocusableFlag(focusable: Boolean) {
        val flag = if (focusable) 0 else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        startHandle?.let { handle ->
            (handle.layoutParams as WindowManager.LayoutParams).flags = flag
            windowManager.updateViewLayout(handle, handle.layoutParams)
        }
        endHandle?.let { handle ->
            (handle.layoutParams as WindowManager.LayoutParams).flags = flag
            windowManager.updateViewLayout(handle, handle.layoutParams)
        }
    }

    fun parseSelectionPositions(result: String): Pair<Pair<Int, Int>, Pair<Int, Int>>? {
        return try {
            val bounds = JSONObject(result)
            val startX = bounds.getInt("startX")
            val startY = bounds.getInt("startY")
            val endX = bounds.getInt("endX")
            val endY = bounds.getInt("endY")
            Pair(Pair(startX, startY), Pair(endX, endY))
        } catch (e: Exception) {
            null
        }
    }

    fun positionHandle(handle: ImageView?, x: Int, y: Int) {
        handle?.let {
            val params = it.layoutParams as WindowManager.LayoutParams
            params.x = x
            params.y = y
            windowManager.updateViewLayout(it, params)
        }
    }
} 