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
import android.widget.Toast
import com.example.aifloatingball.R
import java.util.concurrent.atomic.AtomicBoolean
import androidx.appcompat.view.ContextThemeWrapper

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
        private const val MENU_WINDOW_FLAGS = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or // 允许点击菜单外部传递到下面的窗口
                                              WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or // 监听外部点击以便隐藏
                                              WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS // 允许内容超出屏幕
                                              // FLAG_NOT_FOCUSABLE 是不需要的，因为菜单内的按钮需要焦点
    }

    private var currentWebView: WebView? = null
    private var floatingMenuView: View? = null // 新增菜单视图
    private val isMenuShowing = AtomicBoolean(false)
    private val isMenuAnimating = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private var autoHideRunnable: Runnable? = null
    private var lastMenuPositionX = 0f
    private var lastMenuPositionY = 0f
    
    // 文本选择柄管理器
    private val handleManager = TextSelectionHandleManager(context)

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
        Log.d(TAG, "[MENU_LIFECYCLE] proceedWithShowingMenu: AnchorXY=($menuAnchorX, $menuAnchorY), SelLTRB=($selLeft, $selTop, $selRight, $selBottom)")
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.e(TAG, "[MENU_LIFECYCLE] Cannot show menu from non-UI thread! Aborting.")
            return
        }
        currentWebView = webView

        // Check for SYSTEM_ALERT_WINDOW permission on API 23+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(context)) {
                Log.e(TAG, "[MENU_PERMISSION_CHECK] SYSTEM_ALERT_WINDOW permission not granted. Cannot show overlay view. Please grant it manually.")
                cleanupStateAndHandles()
                return
            } else {
                Log.i(TAG, "[MENU_PERMISSION_CHECK] SYSTEM_ALERT_WINDOW permission granted.")
            }
        }

        isMenuAnimating.set(true)

        if (floatingMenuView != null && floatingMenuView?.parent != null) {
            try {
                windowManager.removeView(floatingMenuView)
                Log.d(TAG, "[MENU_LIFECYCLE] Removed existing floatingMenuView before creating new one.")
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "[MENU_LIFECYCLE] floatingMenuView was already removed, or not attached: ${e.message}")
            }
        }

        // 使用 ContextThemeWrapper 来确保 Material Components 属性可以被解析
        // 请将 R.style.Theme_AIFloatingBall 替换为您应用中实际的 Material Components 主题名称
        val themedContext = ContextThemeWrapper(this.context, R.style.Theme_AIFloatingBall) 
        Log.i(TAG, "[MENU_CONTEXT_DEBUG] Using ContextThemeWrapper with app theme (R.style.Theme_AIFloatingBall) for inflating menu.")
        
        floatingMenuView = LayoutInflater.from(themedContext) // 使用带主题的Context
            .inflate(R.layout.text_selection_menu, null).apply {
                alpha = 0f
                scaleX = 0.8f
                scaleY = 0.8f
                setOnTouchListener { _, event ->
                    if (event.action == MotionEvent.ACTION_OUTSIDE) {
                        Log.d(TAG, "[MENU_LIFECYCLE] ACTION_OUTSIDE detected, hiding menu.")
                        hideTextSelectionMenu()
                        true
                    } else {
                        false
                    }
                }
            }

        setupMenuItems(floatingMenuView!!, webView)

        val webViewLocation = IntArray(2)
        webView.getLocationOnScreen(webViewLocation) // Get WebView's screen location

        // 计算菜单的绝对屏幕位置
        val absoluteMenuX = webViewLocation[0] + menuAnchorX
        val absoluteMenuY = webViewLocation[1] + menuAnchorY + 20 // 菜单通常在选区下方
        
        Log.d(TAG, "[MENU_LIFECYCLE] WebView on-screen: (${webViewLocation[0]}, ${webViewLocation[1]}). Calculated Absolute Menu Pos for Floating View: ($absoluteMenuX, $absoluteMenuY)")

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            OVERLAY_WINDOW_TYPE, // 使用我们定义的系统覆盖类型
            MENU_WINDOW_FLAGS, // 使用我们定义的flags
            PixelFormat.TRANSLUCENT // 允许透明背景
        ).apply {
            gravity = Gravity.TOP or Gravity.START // 菜单位置基于x, y，所以需要LEFT | TOP
            x = absoluteMenuX
            y = absoluteMenuY
        }

        // 显示选择柄，使用JS直接返回的选区边界
        handleManager.showSelectionHandles(webView, selLeft, selTop, selRight, selBottom)
        
        // 尝试添加视图到WindowManager
        try {
            windowManager.addView(floatingMenuView, layoutParams)
            Log.i(TAG, "[MENU_LIFECYCLE] floatingMenuView added to WindowManager. View hash: ${floatingMenuView?.hashCode()}, LayoutParams type: ${layoutParams.type}, flags: ${layoutParams.flags}")
            showMenuWithAnimation(floatingMenuView!!) // 启动动画
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
                handleManager.hideSelectionHandles()
            }
            return
        }

        if (isMenuShowing.get() && !isMenuAnimating.get()) {
            Log.d(TAG, "[MENU_LIFECYCLE] Menu is showing (View hash: ${floatingMenuView?.hashCode()}), starting hide animation.")
            isMenuAnimating.set(true)
            floatingMenuView?.animate() // 对floatingMenuView执行动画
                ?.alpha(0f)
                ?.scaleX(0.8f)
                ?.scaleY(0.8f)
                ?.setDuration(150)
                ?.setInterpolator(AccelerateInterpolator())
                ?.withEndAction {
                    Log.d(TAG, "[MENU_LIFECYCLE] Menu hide animation ended (View hash: ${floatingMenuView?.hashCode()}).")
                    removeFloatingMenuViewSafely() // 安全移除视图
                    if (cleanupHandlesAndState) {
                        cleanupStateAndHandles()
                    } else {
                        isMenuAnimating.set(false)
                        isMenuShowing.set(false)
                    }
                }
                ?.start()
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
        handleManager.hideSelectionHandles()
    }

    /**
     * 设置菜单项点击事件
     */
    private fun setupMenuItems(menuView: View, webView: WebView) {
        // 全选
        menuView.findViewById<View>(R.id.action_select_all)?.setOnClickListener {
            executeSelectAll(webView)
            animateMenuItemAndHide(menuView)
        }
        
        // 剪切
        menuView.findViewById<View>(R.id.action_cut)?.setOnClickListener {
            executeCut(webView)
            animateMenuItemAndHide(menuView)
        }
        
        // 复制
        menuView.findViewById<View>(R.id.action_copy)?.setOnClickListener {
            executeCopy(webView)
            animateMenuItemAndHide(menuView)
        }
        
        // 粘贴
        menuView.findViewById<View>(R.id.action_paste)?.setOnClickListener {
            executePaste(webView)
            animateMenuItemAndHide(menuView)
        }
        
        // 尝试查找并设置分享按钮
        val shareButton = menuView.findViewById<View>(R.id.action_share)
        shareButton?.setOnClickListener {
            executeShare(webView)
            animateMenuItemAndHide(menuView)
        }
    }

    /**
     * 动画菜单项并隐藏菜单
     */
    private fun animateMenuItemAndHide(view: View) {
        view.animate()
            .alpha(0.8f)
            .scaleX(0.9f)
            .scaleY(0.9f)
            .setDuration(100)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                hideTextSelectionMenu()
            }
            .start()
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
    private fun executeCopy(webView: WebView) {
        webView.evaluateJavascript("""
            (function() {
                var selection = window.getSelection();
                var text = selection.toString();
                selection.removeAllRanges(); // 清除选择以避免视觉干扰
                return text;
            })();
        """.trimIndent()) { result ->
            val text = result?.trim('"') ?: ""
            if (text.isNotEmpty()) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("selected text", text)
                clipboard.setPrimaryClip(clip)
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                }
                Log.d(TAG, "已复制文本: ${text.take(50)}...")
            }
        }
    }

    /**
     * 执行剪切操作
     */
    private fun executeCut(webView: WebView) {
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
                            // 在非可编辑区域仅复制并清除选择
                            var range = selection.getRangeAt(0);
                            range.deleteContents();
                            selection.removeAllRanges();
                        }
                    } catch(e) {
                        // 如果execCommand失败，手动删除选中内容
                        var range = selection.getRangeAt(0);
                        range.deleteContents();
                        selection.removeAllRanges();
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
            }
        }
    }

    /**
     * 执行粘贴操作
     */
    private fun executePaste(webView: WebView) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.hasPrimaryClip()) {
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
                            } else if (window.getSelection().rangeCount > 0) {
                                // 在普通网页内容中尝试插入
                                var selection = window.getSelection();
                                var range = selection.getRangeAt(0);
                                
                                // 创建文本节点
                                var textNode = document.createTextNode("$escapedText");
                                
                                // 删除当前选中内容
                                range.deleteContents();
                                
                                // 插入新文本
                                range.insertNode(textNode);
                                
                                // 将光标移动到插入文本的末尾
                                range.setStartAfter(textNode);
                                range.setEndAfter(textNode);
                                selection.removeAllRanges();
                                selection.addRange(range);
                                
                                return true;
                            }
                            return false;
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
                    } else {
                        Log.e(TAG, "粘贴失败: $result")
                    }
                }
            }
        }
    }
    
    /**
     * 执行分享操作
     */
    private fun executeShare(webView: WebView) {
        webView.evaluateJavascript("""
            (function() {
                var selection = window.getSelection();
                var text = selection.toString();
                return text;
            })();
        """.trimIndent()) { result ->
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
} 