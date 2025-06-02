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
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.webkit.WebView
import android.widget.PopupWindow
import android.widget.Toast
import com.example.aifloatingball.R
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 文本选择管理器
 * 负责处理WebView中的文本选择与操作
 */
class TextSelectionManager(private val context: Context) {
    companion object {
        private const val TAG = "TextSelectionManager"
        private const val MENU_AUTO_HIDE_DELAY = 8000L
    }

    private var currentWebView: WebView? = null
    private var popupWindow: PopupWindow? = null
    private val isMenuShowing = AtomicBoolean(false)
    private val isMenuAnimating = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private var autoHideRunnable: Runnable? = null
    private var lastMenuPositionX = 0
    private var lastMenuPositionY = 0

    /**
     * 显示文本选择菜单
     * @param webView WebView实例
     * @param x 选择点的X坐标
     * @param y 选择点的Y坐标
     */
    fun showTextSelectionMenu(webView: WebView, x: Int, y: Int) {
        Log.d(TAG, "显示文本选择菜单: x=$x, y=$y")
        
        // 保存位置，用于可能的重新定位
        lastMenuPositionX = x
        lastMenuPositionY = y
        
        // 如果菜单正在显示或动画中，先隐藏
        if (isMenuShowing.get() || isMenuAnimating.get()) {
            hideTextSelectionMenu()
            
            // 给隐藏动画一些时间
            handler.postDelayed({
                doShowTextSelectionMenu(webView, x, y)
            }, 150)
            return
        }

        doShowTextSelectionMenu(webView, x, y)
    }
    
    /**
     * 实际执行菜单显示
     */
    private fun doShowTextSelectionMenu(webView: WebView, x: Int, y: Int) {
        try {
            currentWebView = webView
            isMenuAnimating.set(true)
            
            // 创建菜单视图
            val menuView = LayoutInflater.from(context)
                .inflate(R.layout.text_selection_menu, null).apply {
                    alpha = 0f
                    scaleX = 0.8f
                    scaleY = 0.8f
                }

            // 设置菜单项点击事件
            setupMenuItems(menuView, webView)

            // 创建PopupWindow
            popupWindow = PopupWindow(
                menuView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            ).apply {
                isOutsideTouchable = true
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                
                setOnDismissListener {
                    cleanupState()
                }
                
                // 确保菜单在输入法上方显示
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    windowLayoutType = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
                }
            }

            // 计算显示位置
            val location = IntArray(2)
            webView.getLocationOnScreen(location)
            val menuX = location[0] + x
            val menuY = location[1] + y
            
            // 验证当前是否有文本选择，如果没有则不显示菜单
            webView.evaluateJavascript(
                "(function() { return window.getSelection().toString().length > 0; })();"
            ) { result ->
                if (result == "true") {
                    showMenuWithAnimation(webView, menuView, menuX, menuY)
                } else {
                    Log.d(TAG, "没有文本选择，不显示菜单")
                    cleanupState()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "显示菜单失败", e)
            cleanupState()
        }
    }
    
    /**
     * 使用动画显示菜单
     */
    private fun showMenuWithAnimation(webView: WebView, menuView: View, x: Int, y: Int) {
        try {
            // 显示菜单
            popupWindow?.showAtLocation(
                webView,
                Gravity.NO_GRAVITY,
                x,
                y
            )

            // 添加显示动画
            menuView.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(150)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    isMenuAnimating.set(false)
                    isMenuShowing.set(true)
                }
                .start()

            // 设置自动隐藏
            autoHideRunnable?.let { handler.removeCallbacks(it) }
            autoHideRunnable = Runnable { hideTextSelectionMenu() }.also {
                handler.postDelayed(it, MENU_AUTO_HIDE_DELAY)
            }
        } catch (e: Exception) {
            Log.e(TAG, "显示菜单动画失败", e)
            cleanupState()
        }
    }

    /**
     * 隐藏文本选择菜单
     */
    fun hideTextSelectionMenu() {
        if (!isMenuShowing.get() && !isMenuAnimating.get()) {
            return
        }

        try {
            isMenuAnimating.set(true)
            
            popupWindow?.contentView?.animate()
                ?.alpha(0f)
                ?.scaleX(0.8f)
                ?.scaleY(0.8f)
                ?.setDuration(150)
                ?.setInterpolator(AccelerateInterpolator())
                ?.withEndAction {
                    dismissPopupSafely()
                    cleanupState()
                }
                ?.start()

        } catch (e: Exception) {
            Log.e(TAG, "隐藏菜单失败", e)
            dismissPopupSafely()
            cleanupState()
        }
    }

    /**
     * 安全关闭弹出窗口
     */
    private fun dismissPopupSafely() {
        try {
            popupWindow?.let { popup ->
                if (popup.isShowing) {
                    popup.dismiss()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "关闭弹出窗口失败", e)
        }
        popupWindow = null
    }

    /**
     * 清理状态
     */
    private fun cleanupState() {
        isMenuShowing.set(false)
        isMenuAnimating.set(false)
        popupWindow = null
        autoHideRunnable?.let { handler.removeCallbacks(it) }
        autoHideRunnable = null
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
                    
                    // 检查是否在可编辑区域
                    var isInEditableArea = document.activeElement && 
                        (document.activeElement.tagName === 'INPUT' || 
                         document.activeElement.tagName === 'TEXTAREA' || 
                         document.activeElement.isContentEditable);
                    
                    // 如果在可编辑区域，仅选择该区域内容
                    if (isInEditableArea) {
                        document.execCommand('selectAll', false, null);
                    }
                    
                    return selection.toString();
                } catch (e) {
                    return "Error: " + e.toString();
                }
            })();
        """.trimIndent()) { result ->
            Log.d(TAG, "全选文本: ${result?.take(50)}...")
            
            // 全选后，获取选择位置并重新显示菜单
            handler.postDelayed({
                if (currentWebView != null) {
                    getSelectionPosition(currentWebView!!) { left, top, right, bottom ->
                        // 重新显示菜单在选择位置
                        if (left != 0 || top != 0) {
                            showTextSelectionMenu(currentWebView!!, (left + right) / 2, bottom + 20)
                        }
                    }
                }
            }, 200)
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
     * 获取选择范围的位置
     */
    private fun getSelectionPosition(webView: WebView, callback: (left: Int, top: Int, right: Int, bottom: Int) -> Unit) {
        val script = """
            (function() {
                var selection = window.getSelection();
                if (selection.rangeCount > 0) {
                    var range = selection.getRangeAt(0);
                    var rects = range.getClientRects();
                    
                    if (rects.length > 0) {
                        // 获取第一个和最后一个矩形，确定选择范围的边界
                        var firstRect = rects[0];
                        var lastRect = rects[rects.length - 1];
                        
                        // 计算整个选择范围的边界
                        var left = firstRect.left;
                        var top = firstRect.top;
                        var right = lastRect.right;
                        var bottom = lastRect.bottom;
                        
                        return JSON.stringify({
                            left: Math.round(left),
                            top: Math.round(top),
                            right: Math.round(right),
                            bottom: Math.round(bottom)
                        });
                    }
                }
                return JSON.stringify({error: "No selection range"});
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(script) { result ->
            try {
                // 解析JSON结果
                val cleanResult = result?.replace("\\\"", "\"")?.trim('"') ?: ""
                if (!cleanResult.contains("error")) {
                    val jsonStr = android.util.JsonReader(java.io.StringReader(cleanResult))
                    jsonStr.beginObject()
                    
                    var left = 0
                    var top = 0
                    var right = 0
                    var bottom = 0
                    
                    while (jsonStr.hasNext()) {
                        when (jsonStr.nextName()) {
                            "left" -> left = jsonStr.nextInt()
                            "top" -> top = jsonStr.nextInt()
                            "right" -> right = jsonStr.nextInt()
                            "bottom" -> bottom = jsonStr.nextInt()
                            else -> jsonStr.skipValue()
                        }
                    }
                    
                    jsonStr.endObject()
                    jsonStr.close()
                    
                    callback(left, top, right, bottom)
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析选择位置失败", e)
            }
        }
    }
} 