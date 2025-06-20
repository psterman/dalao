package com.example.aifloatingball.ui.text

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.webkit.WebView
import android.widget.EditText
import android.widget.Toast
import com.example.aifloatingball.R
import java.util.concurrent.atomic.AtomicBoolean
import androidx.appcompat.view.ContextThemeWrapper
import android.graphics.Rect
import android.widget.FrameLayout

/**
 * 文本选择管理器
 * 负责处理WebView中的文本选择与操作
 * 现在使用WindowManager.addView()来管理悬浮菜单
 */
class TextSelectionManager(private val context: Context, private val windowManager: WindowManager) {
    companion object {
        private const val TAG = "TextSelectionManager"
        private const val MENU_AUTO_HIDE_DELAY = 8000L
        // 对于悬浮窗，使用TYPE_APPLICATION_OVERLAY更合适，它需要SYSTEM_ALERT_WINDOW权限
        private const val OVERLAY_WINDOW_TYPE = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        // 菜单显示时的Window Flags
        private const val MENU_WINDOW_FLAGS = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                                              WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    }

    private var currentWebView: WebView? = null
    private var floatingMenuView: View? = null // 新增菜单视图
    private val isMenuShowing = AtomicBoolean(false)
    private val isMenuAnimating = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private var autoHideRunnable: Runnable? = null
    private var lastMenuPositionX = 0f
    private var lastMenuPositionY = 0f
    
    // 为EditText保存选择状态
    private var savedSelectionStart: Int = -1
    private var savedSelectionEnd: Int = -1
    
    fun isShowing(): Boolean = isMenuShowing.get()
    
    // 文本选择柄管理器 (功能已禁用)
    // private val handleManager = TextSelectionHandleManager(context)

    /**
     * 显示文本选择菜单
     * @param webView WebView实例
     * @param x 选择点的X坐标
     * @param y 选择点的Y坐标
     */
    fun showTextSelectionMenu(webView: WebView, x: Int, y: Int) {
        Log.i(TAG, "[MENU_LIFECYCLE] showTextSelectionMenu: WebView hash: ${webView.hashCode()}, x=$x, y=$y. MenuShowing: ${isMenuShowing.get()}, Animating: ${isMenuAnimating.get()}")
        
        currentWebView = webView
        lastMenuPositionX = x.toFloat()
        lastMenuPositionY = y.toFloat()

        // 如果菜单正在显示或动画中，先隐藏
        if (isMenuShowing.get() || isMenuAnimating.get()) {
            Log.d(TAG, "[MENU_LIFECYCLE] Menu is already showing or animating. Will hide first.")
            hideTextSelectionMenu(false) // 隐藏但不清除状态，因为要立即重显示
            // 给隐藏动画一些时间，然后继续显示新的菜单
            handler.postDelayed({
                Log.d(TAG, "[MENU_LIFECYCLE] Proceeding to show menu after delay (from hide first).")
                doShowTextSelectionMenu(webView, x, y)
            }, 160) // 略长于动画时间
            return
        }

        doShowTextSelectionMenu(webView, x, y)
    }
    
    /**
     * 实际执行菜单显示
     */
    private fun doShowTextSelectionMenu(webView: WebView, x: Int, y: Int) {
        Log.d(TAG, "[MENU_LIFECYCLE] doShowTextSelectionMenu: x=$x, y=$y")
        try {
            // 验证当前是否有文本选择
            getSelectionPosition(webView) { selLeft, _top, selRight, selBottom, selectedText ->
                Log.d(TAG, "[MENU_LIFECYCLE] getSelectionPosition callback - Text: '${selectedText.take(30)}', Bounds LTRB: ($selLeft, ${_top}, selRight, $selBottom)")
                if (selectedText.isNotEmpty()) {
                    // 使用从JS获取的精确选择边界的中心点和底部来定位菜单和选择柄
                    // x, y 可能是原始触摸点，而selLeft等是JS计算的选区边界，更适合定位
                    val calculatedMenuAnchorX = (selLeft + selRight) / 2
                    val calculatedMenuAnchorY = selBottom // 通常菜单和选择柄基于选区底部

                    Log.d(TAG, "[MENU_LIFECYCLE] Selected text found. Proceeding to show. Calculated Menu Anchor: ($calculatedMenuAnchorX, $calculatedMenuAnchorY)")
                    proceedWithShowingMenu(webView, calculatedMenuAnchorX, calculatedMenuAnchorY, selLeft, _top, selRight, selBottom)
                } else {
                    Log.i(TAG, "[MENU_LIFECYCLE] No text selected according to getSelectionPosition. Not showing menu/handles.")
                    cleanupStateAndHandles() // 清理菜单和选择柄
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "[MENU_LIFECYCLE] Error in doShowTextSelectionMenu", e)
            cleanupStateAndHandles()
        }
    }

    private fun proceedWithShowingMenu(webView: WebView, menuAnchorX: Int, menuAnchorY: Int, selLeft: Int, selTop: Int, selRight: Int, selBottom: Int ) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.e(TAG, "Cannot show menu from non-UI thread! Aborting.")
            return
        }

        currentWebView = webView
        val themedContext = ContextThemeWrapper(this.context, R.style.Theme_AIFloatingBall) 
        floatingMenuView = LayoutInflater.from(themedContext)
            .inflate(R.layout.text_selection_menu, null)

        val menuContent = floatingMenuView!!.findViewById<View>(R.id.text_selection_menu_content)

        // 全屏的根视图监听触摸事件，以实现"点击外部"隐藏的功能
        floatingMenuView!!.setOnTouchListener { _, event ->
            // 只在ACTION_DOWN时检查，避免重复触发
            if (event.action == MotionEvent.ACTION_DOWN) {
            val contentRect = Rect()
            menuContent.getGlobalVisibleRect(contentRect)
            if (!contentRect.contains(event.x.toInt(), event.y.toInt())) {
                        hideTextSelectionMenu()
                }
            }
            // 修复：始终返回false，以允许触摸事件穿透到下面的WebView
            false
            }

        setupMenuItems(floatingMenuView!!, webView)

        val webViewLocation = IntArray(2)
        webView.getLocationOnScreen(webViewLocation)

        // 测量菜单"内容"视图
        menuContent.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val menuWidth = menuContent.measuredWidth
        val menuHeight = menuContent.measuredHeight
        val margin = (8 * context.resources.displayMetrics.density).toInt() // 8dp margin

        // 获取屏幕尺寸
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels

        // 初始定位：选区下方居中
        var absoluteMenuX = webViewLocation[0] + menuAnchorX - (menuWidth / 2)
        var absoluteMenuY = webViewLocation[1] + selBottom + margin

        // 边界检查与调整
        // 检查是否超出下边界
        if (absoluteMenuY + menuHeight > screenHeight) {
            // 移到选区上方
            absoluteMenuY = webViewLocation[1] + selTop - menuHeight - margin
        }
        // 检查是否超出右边界
        if (absoluteMenuX + menuWidth > screenWidth) {
            absoluteMenuX = screenWidth - menuWidth - margin
        }
        // 检查是否超出左边界
        if (absoluteMenuX < 0) {
            absoluteMenuX = margin
        }
        // 确保不会超出上边界
        if (absoluteMenuY < 0) {
            absoluteMenuY = margin
        }


        // 定位内容视图
        val contentParams = menuContent.layoutParams as FrameLayout.LayoutParams
        contentParams.gravity = Gravity.TOP or Gravity.START
        contentParams.leftMargin = absoluteMenuX
        contentParams.topMargin = absoluteMenuY
        menuContent.layoutParams = contentParams

        // 显示选择柄 (功能已禁用)
        // handleManager.showSelectionHandles(webView, selLeft, selTop, selRight, selBottom)

        // 窗口参数现在是全屏的
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OVERLAY_WINDOW_TYPE,
            MENU_WINDOW_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        try {
            windowManager.addView(floatingMenuView, layoutParams)
            Log.i(TAG, "[MENU_LIFECYCLE] floatingMenuView added to WindowManager (WebView).")
            showMenuWithAnimation(menuContent) // 动画应用于内容视图
        } catch (e: Exception) {
            Log.e(TAG, "[MENU_LIFECYCLE] Error adding floatingMenuView to WindowManager: ${e.message}", e)
            cleanupStateAndHandles()
        }
    }

    // 动画显示菜单（现在是floatingMenuView）
    private fun showMenuWithAnimation(menuView: View) {
        Log.d(TAG, "[MENU_LIFECYCLE] showMenuWithAnimation for menu (hash: ${menuView.hashCode()})")

        // 既然是直接通过WindowManager添加，我们不再需要检查WebView的isAttachedToWindow或anchorView的windowToken
        // 因为菜单本身就是顶级窗口

        try {
            // 启动动画
            menuView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(150)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    Log.d(TAG, "[MENU_LIFECYCLE] Menu show animation ended for menu (hash: ${menuView.hashCode()}).")
                    isMenuAnimating.set(false)
                    isMenuShowing.set(true)
                }
                .start()

            // 设置自动隐藏
            autoHideRunnable?.let { handler.removeCallbacks(it) }
            autoHideRunnable = Runnable {
                Log.d(TAG, "[MENU_LIFECYCLE] Auto-hiding menu (menu hash: ${menuView.hashCode()}).")
                hideTextSelectionMenu()
            }.also {
                handler.postDelayed(it, MENU_AUTO_HIDE_DELAY)
            }
        } catch (e: Exception) {
            Log.e(TAG, "[MENU_LIFECYCLE] Error in showMenuWithAnimation during animation for menu (hash: ${menuView.hashCode()})", e)
            cleanupStateAndHandles()
        }
    }

    /**
     * 隐藏文本选择菜单 (现在是floatingMenuView)
     */
    fun hideTextSelectionMenu(cleanupHandlesAndState: Boolean = true) {
        Log.d(TAG, "[MENU_LIFECYCLE] hideTextSelectionMenu called. cleanupHandlesAndState: $cleanupHandlesAndState, MenuShowing: ${isMenuShowing.get()}, Animating: ${isMenuAnimating.get()}")

        if (!isMenuShowing.get() && !isMenuAnimating.get()) {
            if(cleanupHandlesAndState) {
                Log.d(TAG, "[MENU_LIFECYCLE] Menu not showing/animating, but cleaning handles if requested.")
                // handleManager.hideSelectionHandles() // 功能已禁用
            }
            return
        }

        if (isMenuShowing.get() && !isMenuAnimating.get()) {
            Log.d(TAG, "[MENU_LIFECYCLE] Menu is showing (View hash: ${floatingMenuView?.hashCode()}), hiding without animation as requested.")
            isMenuAnimating.set(false) // Ensure this is false
            isMenuShowing.set(false)

            removeFloatingMenuViewSafely()
                    if (cleanupHandlesAndState) {
                        cleanupStateAndHandles()
                    }
        } else if (isMenuAnimating.get()) {
            Log.d(TAG, "[MENU_LIFECYCLE] Menu is already animating (View hash: ${floatingMenuView?.hashCode()}). Posting delayed cleanup if necessary for full cleanup request.")
            if(cleanupHandlesAndState){
                 handler.postDelayed({
                    if (!isMenuShowing.get() && !isMenuAnimating.get()) { 
                         Log.d(TAG, "[MENU_LIFECYCLE] Delayed cleanup: Menu is confirmed not showing/animating now, proceeding to cleanup.")
                         removeFloatingMenuViewSafely() // 确保移除
                         cleanupStateAndHandles()
                    } else {
                        Log.d(TAG, "[MENU_LIFECYCLE] Delayed cleanup: Menu is now showing or still animating (View hash: ${floatingMenuView?.hashCode()}). Aborting this cleanup path.")
                    }
                 }, 160) 
            }
        }
    }

    /**
     * 安全移除悬浮菜单视图
     */
    private fun removeFloatingMenuViewSafely() {
        if (floatingMenuView != null && floatingMenuView?.parent != null) {
            try {
                Log.d(TAG, "[MENU_LIFECYCLE] Attempting to remove floatingMenuView (hash: ${floatingMenuView?.hashCode()}) from WindowManager.")
                windowManager.removeView(floatingMenuView)
                floatingMenuView = null
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "[MENU_LIFECYCLE] Error removing floatingMenuView: ${e.message}", e)
            }
        }
    }

    /**
     * 清理状态 (不再处理PopupWindow)
     */
    private fun cleanupState() {
        Log.d(TAG, "[MENU_LIFECYCLE] cleanupState: Resetting menu showing/animating flags. Current floatingMenuView hash before null: ${floatingMenuView?.hashCode()}")
        isMenuShowing.set(false)
        isMenuAnimating.set(false)
        autoHideRunnable?.let { handler.removeCallbacks(it) }
        autoHideRunnable = null
        // floatingMenuView 在 removeFloatingMenuViewSafely() 中被置为 null
    }

    private fun cleanupStateAndHandles() {
        Log.d(TAG, "[MENU_LIFECYCLE] cleanupStateAndHandles: Cleaning up state and handles. Current floatingMenuView hash: ${floatingMenuView?.hashCode()}")
        cleanupState()
        // handleManager.hideSelectionHandles() // 功能已禁用
    }

    /**
     * 设置菜单项点击事件
     */
    private fun setupMenuItems(menuView: View, webView: WebView) {
        // 全选
        menuView.findViewById<View>(R.id.action_select_all)?.setOnClickListener {
            // 全选后，菜单会重新定位并显示，所以这里不需要手动隐藏
            executeSelectAll(webView)
        }
        
        // 剪切
        menuView.findViewById<View>(R.id.action_cut)?.setOnClickListener {
            executeCut(webView) { success ->
                if (success) {
                    hideTextSelectionMenu()
                } else {
                    Toast.makeText(context, "请选择要剪切的文本", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        // 复制
        menuView.findViewById<View>(R.id.action_copy)?.setOnClickListener {
            executeCopy(webView) { success ->
                if (success) {
                    hideTextSelectionMenu()
                } else {
                    Toast.makeText(context, "请选择要复制的文本", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        // 粘贴
        menuView.findViewById<View>(R.id.action_paste)?.setOnClickListener {
            executePaste(webView) { success ->
                if (success) {
                    hideTextSelectionMenu()
                } else {
                    Toast.makeText(context, "粘贴失败或剪贴板为空", Toast.LENGTH_SHORT).show()
                }
            }
        }
        
        // 尝试查找并设置分享按钮
        val shareButton = menuView.findViewById<View>(R.id.action_share)
        shareButton?.setOnClickListener {
            executeShare(webView) { success ->
                if (success) {
                    hideTextSelectionMenu()
                }
            }
        }
    }

    /**
     * 执行全选操作
     */
    private fun executeSelectAll(webView: WebView) {
        webView.evaluateJavascript("""
            (function() {
                try {
                    var selection = window.getSelection();
                    selection.removeAllRanges();
                    var range = document.createRange();
                    range.selectNodeContents(document.body);
                    selection.addRange(range);
                    
                    var isInEditableArea = document.activeElement && 
                        (document.activeElement.tagName === 'INPUT' || 
                         document.activeElement.tagName === 'TEXTAREA' || 
                         document.activeElement.isContentEditable);
                    
                    if (isInEditableArea) {
                        document.execCommand('selectAll', false, null);
                    }
                    
                    return selection.toString();
                } catch (e) {
                    return "Error: " + e.toString();
                }
            })();
        """.trimIndent()) { result ->
            Log.d(TAG, "All text selected: ${result?.take(50)}...")
            
            // 全选后，获取选择位置并重新显示菜单和选择柄
            handler.postDelayed({
                currentWebView?.let {
                    getSelectionPosition(it) { left, _top, right, bottom, selectedText ->
                        if (selectedText.isNotEmpty()) {
                            val menuX = (left + right) / 2
                            val menuY = bottom // Anchor Y for menu/handles after select all
                            Log.d(TAG, "executeSelectAll: Relaunching menu at ($menuX, $menuY) for selected text: '${selectedText.take(30)}'")
                            showTextSelectionMenu(it, menuX, menuY)
                        } else {
                            Log.d(TAG, "executeSelectAll: No text selected after JS execution, hiding menu.")
                            hideTextSelectionMenu()
                        }
                    }
                }
            }, 100) // 延迟以确保JS执行完毕
        }
    }

    /**
     * 执行复制操作
     */
    private fun executeCopy(webView: WebView, callback: (Boolean) -> Unit) {
        webView.evaluateJavascript("(function() { return window.getSelection().toString(); })();") { result ->
            val text = result?.trim('"') ?: ""
            if (text.isNotEmpty()) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("selected text", text)
                clipboard.setPrimaryClip(clip)
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                }
                Log.d(TAG, "已复制文本: ${text.take(50)}...")
                callback(true)
            } else {
                callback(false)
            }
        }
    }

    /**
     * 执行剪切操作
     */
    private fun executeCut(webView: WebView, callback: (Boolean) -> Unit) {
        webView.evaluateJavascript("""
            (function() {
                var selection = window.getSelection();
                var text = selection.toString();
                if (text) {
                    try {
                        // 检查是否在可编辑区域
                        var isInEditableArea = document.activeElement && 
                            (document.activeElement.tagName === 'INPUT' || 
                             document.activeElement.tagName === 'TEXTAREA' || 
                             document.activeElement.isContentEditable);
                        
                        if (isInEditableArea) {
                            // 使用execCommand在可编辑区域
                            document.execCommand('cut');
                        } else {
                            // 在非可编辑区域，不执行任何破坏性操作
                            return ""; // 返回空字符串表示无法剪切
                        }
                    } catch(e) {
                         return ""; // 出错也返回空
                    }
                }
                return text;
            })();
        """.trimIndent()) { result ->
            val text = result?.trim('"') ?: ""
            if (text.isNotEmpty()) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("cut text", text)
                clipboard.setPrimaryClip(clip)
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "已剪切", Toast.LENGTH_SHORT).show()
                }
                Log.d(TAG, "已剪切文本: ${text.take(50)}...")
                callback(true)
            } else {
                callback(false)
            }
        }
    }

    /**
     * 执行粘贴操作
     */
    private fun executePaste(webView: WebView, callback: (Boolean) -> Unit) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.hasPrimaryClip() && clipboard.primaryClip?.itemCount ?: 0 > 0) {
            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
            if (text.isNotEmpty()) {
                // 转义特殊字符
                val escapedText = text.replace("\\", "\\\\")
                    .replace("'", "\\'")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("""$""", """\\$""")
                    .replace("\"", "\\\"")
                
                webView.evaluateJavascript("""
                    (function() {
                        try {
                            // 检查是否在可编辑区域
                            var isInEditableArea = document.activeElement && 
                                (document.activeElement.tagName === 'INPUT' || 
                                 document.activeElement.tagName === 'TEXTAREA' || 
                                 document.activeElement.isContentEditable);
                            
                            if (isInEditableArea) {
                                // 在可编辑区域使用execCommand
                                document.execCommand('insertText', false, "$escapedText");
                                return true;
                            }
                            return false; // 非可编辑区域不允许粘贴
                        } catch (e) {
                            return "Error: " + e.toString();
                        }
                    })();
                """.trimIndent()) { result ->
                    if (result == "true") {
                        Handler(Looper.getMainLooper()).post {
                            Toast.makeText(context, "已粘贴", Toast.LENGTH_SHORT).show()
                        }
                        Log.d(TAG, "已粘贴文本: ${text.take(50)}...")
                        callback(true)
                    } else {
                        Log.e(TAG, "粘贴失败: $result")
                        callback(false)
                    }
                }
            } else {
                callback(false)
            }
        } else {
            callback(false)
        }
    }
    
    /**
     * 执行分享操作
     */
    private fun executeShare(webView: WebView, callback: (Boolean) -> Unit) {
        webView.evaluateJavascript("(function() { return window.getSelection().toString(); })();") { result ->
            val text = result?.trim('"') ?: ""
            if (text.isNotEmpty()) {
                val shareIntent = android.content.Intent().apply {
                    action = android.content.Intent.ACTION_SEND
                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                    type = "text/plain"
                }
                
                val chooserIntent = android.content.Intent.createChooser(
                    shareIntent, 
                    "分享文本"
                ).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                
                context.startActivity(chooserIntent)
                Log.d(TAG, "分享文本: ${text.take(50)}...")
                callback(true)
            } else {
                callback(false)
            }
        }
    }
    
    /**
     * 获取选择范围的位置
     */
    internal fun getSelectionPosition(webView: WebView, callback: (left: Int, top: Int, right: Int, bottom: Int, selectedText: String) -> Unit) {
        Log.d(TAG, "getSelectionPosition called for WebView: ${webView.hashCode()}")
        val script = """
            (function() {
                var selection = window.getSelection();
                var text = "";
                var hasSelection = false;
                var bounds = {left: 0, top: 0, right: 0, bottom: 0};
                if (selection && selection.rangeCount > 0) {
                    text = selection.toString();
                    if (text.length > 0) {
                        hasSelection = true;
                        var range = selection.getRangeAt(0);
                        var rects = range.getClientRects();
                        if (rects.length > 0) {
                            bounds.left = rects[0].left;
                            bounds.top = rects[0].top;
                            bounds.right = rects[rects.length - 1].right;
                            bounds.bottom = rects[rects.length - 1].bottom;
                        } else {
                             // Fallback if no rects but text selected (e.g. some specific elements)
                             // This part might need adjustment based on observed behavior
                             console.warn("getSelectionPosition: Text selected but no client rects. Bounds will be 0.");
                        }
                    } else {
                        // No text content in selection, treat as no selection
                        hasSelection = false;
                    }
                } else {
                    // No selection range
                     hasSelection = false;
                }
                return JSON.stringify({
                    left: Math.round(bounds.left),
                    top: Math.round(bounds.top),
                    right: Math.round(bounds.right),
                    bottom: Math.round(bounds.bottom),
                    text: text,
                    hasSelection: hasSelection
                });
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(script) { result ->
            Log.d(TAG, "getSelectionPosition JS result: $result")
            try {
                val cleanResult = result?.replace("\\\"", "\"")?.trim('"') ?: "{\"hasSelection\":false, \"text\":\"\"}" // Provide default on null
                if (cleanResult.contains("\"error\":")) {
                     Log.e(TAG, "getSelectionPosition JS returned an error: $cleanResult")
                     callback(0,0,0,0, "")
                     return@evaluateJavascript
                }

                val jsonStr = android.util.JsonReader(java.io.StringReader(cleanResult))
                jsonStr.beginObject()
                
                var left = 0
                var top = 0
                var right = 0
                var bottom = 0
                var selectedText = ""
                var hasSel = false
                
                while (jsonStr.hasNext()) {
                    when (jsonStr.nextName()) {
                        "left" -> left = jsonStr.nextInt()
                        "top" -> top = jsonStr.nextInt()
                        "right" -> right = jsonStr.nextInt()
                        "bottom" -> bottom = jsonStr.nextInt()
                        "text" -> selectedText = jsonStr.nextString()
                        "hasSelection" -> hasSel = jsonStr.nextBoolean()
                        else -> jsonStr.skipValue()
                    }
                }
                
                jsonStr.endObject()
                jsonStr.close()
                
                if (hasSel && selectedText.isNotEmpty()) {
                    Log.d(TAG, "getSelectionPosition parsed: LTRB=($left,$top,$right,$bottom), Text='${selectedText.take(30)}'")
                    callback(left, top, right, bottom, selectedText)
                } else {
                    Log.d(TAG, "getSelectionPosition parsed: No selection or empty text.")
                    callback(0, 0, 0, 0, "")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing getSelectionPosition JS result", e)
                callback(0,0,0,0, "")
            }
        }
    }

    /**
     * 显示链接操作菜单
     */
    fun showLinkMenu(webView: WebView, url: String, x: Int, y: Int) {
        Log.i(TAG, "[MENU_LIFECYCLE] showLinkMenu: URL=$url, x=$x, y=$y")

        // 如果有其他菜单正在显示，先隐藏
        if (isMenuShowing.get() || isMenuAnimating.get()) {
            hideTextSelectionMenu(true)
            handler.postDelayed({
                doShowLinkMenu(webView, url, x, y)
            }, 160)
            return
        }
        doShowLinkMenu(webView, url, x, y)
    }

    private fun doShowLinkMenu(webView: WebView, url: String, x: Int, y: Int) {
        if (Looper.myLooper() != Looper.getMainLooper()) return

        val themedContext = ContextThemeWrapper(context, R.style.Theme_AIFloatingBall)
        // 1. 加载包装器布局，它将作为全屏的触摸拦截层
        floatingMenuView = LayoutInflater.from(themedContext)
            .inflate(R.layout.link_selection_menu_wrapper, null)

        // 2. 从包装器中找到实际的菜单内容视图
        val menuContent = floatingMenuView!!.findViewById<View>(R.id.link_selection_menu_content)!!

        // 为动画设置初始状态
        menuContent.alpha = 0f
        menuContent.scaleX = 0.8f
        menuContent.scaleY = 0.8f

        // 3. 在全屏的根视图上设置触摸监听器
        floatingMenuView!!.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val contentRect = Rect()
                menuContent.getGlobalVisibleRect(contentRect)
                // 如果触摸点在菜单内容视图的矩形区域之外，则隐藏菜单
                if (!contentRect.contains(event.x.toInt(), event.y.toInt())) {
                        hideTextSelectionMenu()
                    }
                }
            // 修复：始终返回false，允许事件穿透，同时让子视图（按钮）可以被点击
            false
            }

        // 在内容视图上设置菜单项
        setupLinkMenuItems(menuContent, webView, url)

        val webViewLocation = IntArray(2)
        webView.getLocationOnScreen(webViewLocation)

        // 4. 测量并定位内容视图
        menuContent.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val menuWidth = menuContent.measuredWidth
        val menuHeight = menuContent.measuredHeight
        val margin = (20 * context.resources.displayMetrics.density).toInt() // 20dp margin

        // 获取屏幕尺寸
        val screenWidth = context.resources.displayMetrics.widthPixels
        val screenHeight = context.resources.displayMetrics.heightPixels

        // 初始定位：点击坐标下方居中
        var absoluteMenuX = webViewLocation[0] + x - (menuWidth / 2)
        var absoluteMenuY = webViewLocation[1] + y + margin

        // 边界检查与调整
        if (absoluteMenuY + menuHeight > screenHeight) {
            absoluteMenuY = webViewLocation[1] + y - menuHeight - margin
        }
        if (absoluteMenuX + menuWidth > screenWidth) {
            absoluteMenuX = screenWidth - menuWidth - margin
        }
        if (absoluteMenuX < 0) {
            absoluteMenuX = margin
        }
        if (absoluteMenuY < 0) {
            absoluteMenuY = margin
        }


        // 使用FrameLayout.LayoutParams在包装器内定位内容视图
        val contentParams = menuContent.layoutParams as FrameLayout.LayoutParams
        contentParams.gravity = Gravity.TOP or Gravity.START
        contentParams.leftMargin = absoluteMenuX
        contentParams.topMargin = absoluteMenuY
        menuContent.layoutParams = contentParams

        // 5. 为根视图（包装器）使用全屏的窗口参数
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT, // 全屏大小以捕捉所有外部点击
            OVERLAY_WINDOW_TYPE,
            MENU_WINDOW_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = 0
            this.y = 0
        }
        
        try {
            windowManager.addView(floatingMenuView, layoutParams)
            // 6. 对内容视图执行显示动画
            showMenuWithAnimation(menuContent)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding link menu to WindowManager", e)
            cleanupStateAndHandles() // 出错时清理
        }
    }

    private fun setupLinkMenuItems(menuView: View, webView: WebView, url: String) {
        // Example:
        menuView.findViewById<View>(R.id.action_open_in_new_window)?.setOnClickListener {
            // 通过 context 获取 DualFloatingWebViewService 实例
            val service = context as? com.example.aifloatingball.service.DualFloatingWebViewService
            if (service == null) {
                Toast.makeText(context, "无法获取服务实例", Toast.LENGTH_SHORT).show()
            hideTextSelectionMenu()
                return@setOnClickListener
            }

            // 从服务获取 WebViewManager
            val webViewManager = service.webViewManager
            val webViews = webViewManager.getWebViews()
            val currentWebViewIndex = webViews.indexOf(webView)

            if (currentWebViewIndex != -1) {
                val nextWebViewIndex = currentWebViewIndex + 1
                if (nextWebViewIndex < webViews.size) {
                    // 在右侧的WebView中打开
                    val nextWebView = webViews[nextWebViewIndex]
                    nextWebView.loadUrl(url)
                    Toast.makeText(context, "在右侧窗口打开链接", Toast.LENGTH_SHORT).show()
                } else {
                    // 如果是最后一个WebView，则提示无法打开
                    Toast.makeText(context, "已是最后一个窗口，无法在右侧打开", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "无法确定当前窗口", Toast.LENGTH_SHORT).show()
            }
            hideTextSelectionMenu()
        }

        menuView.findViewById<View>(R.id.action_copy_link)?.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Link URL", url)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "链接已复制", Toast.LENGTH_SHORT).show()
            hideTextSelectionMenu()
        }
    }

    /**
     * 显示针对EditText的文本选择菜单
     */
    fun showEditTextSelectionMenu(editText: EditText) {
        if (isMenuShowing.get() || isMenuAnimating.get()) {
            hideTextSelectionMenu(false)
            handler.postDelayed({ doShowEditTextMenu(editText) }, 160)
        } else {
            doShowEditTextMenu(editText)
        }
    }

    private fun doShowEditTextMenu(editText: EditText) {
        if (Looper.myLooper() != Looper.getMainLooper()) return

        // 在显示菜单前保存EditText的选择状态
        savedSelectionStart = editText.selectionStart
        savedSelectionEnd = editText.selectionEnd

        val themedContext = ContextThemeWrapper(context, R.style.Theme_AIFloatingBall)
        floatingMenuView = LayoutInflater.from(themedContext)
            .inflate(R.layout.text_selection_menu, null)

        val menuContent = floatingMenuView!!.findViewById<View>(R.id.text_selection_menu_content)

        // 全屏的根视图监听触摸事件，以实现"点击外部"隐藏的功能
        floatingMenuView!!.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
            // 创建一个矩形来保存菜单内容部分的屏幕坐标
            val contentRect = Rect()
            menuContent.getGlobalVisibleRect(contentRect)

            // 检查触摸点是否在菜单内容矩形之外
            if (!contentRect.contains(event.x.toInt(), event.y.toInt())) {
                    // 如果在外部，隐藏菜单
                        hideTextSelectionMenu()
                }
            }
            // 修复：始终返回false，以允许触摸事件穿透到下面的视图
            false
            }

        setupEditTextMenuItems(floatingMenuView!!, editText)

        // 测量菜单"内容"视图，而不是全屏的根视图
        menuContent.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val menuWidth = menuContent.measuredWidth

        // 使用整个可点击区域（父视图，即search_bar）来定位
        val anchorView = (editText.parent as? View) ?: editText
        val anchorLocation = IntArray(2)
        anchorView.getLocationOnScreen(anchorLocation)
        
        // 我们不再直接设置窗口的x/y，而是设置窗口内"内容视图"的边距，来将它定位到正确的位置
        val contentParams = menuContent.layoutParams as FrameLayout.LayoutParams
        contentParams.gravity = Gravity.TOP or Gravity.START
        contentParams.leftMargin = anchorLocation[0] + (anchorView.width / 2) - (menuWidth / 2)
        val margin = (8 * context.resources.displayMetrics.density).toInt() // 8dp 边距
        contentParams.topMargin = anchorLocation[1] + anchorView.height + margin
        menuContent.layoutParams = contentParams


        // 窗口参数现在是全屏的
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            OVERLAY_WINDOW_TYPE,
            MENU_WINDOW_FLAGS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
            dimAmount = 0.2f // 调暗背景，让用户聚焦菜单
        }
        
        try {
            windowManager.addView(floatingMenuView, layoutParams)
            // 动画现在应用于内容视图，而不是根视图
            showMenuWithAnimation(menuContent)
        } catch (e: Exception) {
            Log.e(TAG, "Error adding edit text menu to WindowManager", e)
        }
    }

    private fun setupEditTextMenuItems(menuView: View, editText: EditText) {
        menuView.findViewById<View>(R.id.action_select_all)?.setOnClickListener {
            editText.selectAll()
            // 全选后，更新保存的选择，以便后续的复制/剪切操作能正确工作
            savedSelectionStart = 0
            savedSelectionEnd = editText.text.length
            // 不隐藏菜单
        }
        menuView.findViewById<View>(R.id.action_cut)?.setOnClickListener {
            // 使用打开菜单时保存的选择状态
            val start = savedSelectionStart
            val end = savedSelectionEnd
            if (start != -1 && end != -1 && start < end) {
                val textToCut = editText.text.substring(start, end)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("cut text", textToCut)
                clipboard.setPrimaryClip(clip)
                editText.text.delete(start, end)
                Toast.makeText(context, "已剪切", Toast.LENGTH_SHORT).show()
            hideTextSelectionMenu()
            } else {
                Toast.makeText(context, "请选择要剪切的文本", Toast.LENGTH_SHORT).show()
            }
        }
        menuView.findViewById<View>(R.id.action_copy)?.setOnClickListener {
            // 使用打开菜单时保存的选择状态
            val start = savedSelectionStart
            val end = savedSelectionEnd
            if (start != -1 && end != -1 && start < end) {
                val textToCopy = editText.text.substring(start, end)
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("copied text", textToCopy)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
            hideTextSelectionMenu()
            } else {
                Toast.makeText(context, "请选择要复制的文本", Toast.LENGTH_SHORT).show()
            }
        }
        menuView.findViewById<View>(R.id.action_paste)?.setOnClickListener {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            if (clipboard.hasPrimaryClip() && clipboard.primaryClip?.itemCount ?: 0 > 0) {
            clipboard.primaryClip?.getItemAt(0)?.text?.let { pasteData ->
                    // 使用保存的选择来决定粘贴/替换的位置
                    val start = savedSelectionStart.coerceAtLeast(0)
                    val end = savedSelectionEnd.coerceAtLeast(0)
                editText.text.replace(start, end, pasteData)
                Toast.makeText(context, "已粘贴", Toast.LENGTH_SHORT).show()
            hideTextSelectionMenu()
                } ?: Toast.makeText(context, "剪贴板内容为空", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "剪贴板为空", Toast.LENGTH_SHORT).show()
            }
        }
    }
} 