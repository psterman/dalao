package com.example.aifloatingball.ui.webview

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.webkit.WebView
import com.example.aifloatingball.ui.text.TextSelectionManager

/**
 * 自定义WebView类
 * 支持文本选择和自定义选择菜单
 */
class CustomWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {
    
    companion object {
        private const val TAG = "CustomWebView"
        private const val LONG_PRESS_TIMEOUT = 500L // 长按超时时间(毫秒)
    }
    
    private var textSelectionManager: TextSelectionManager? = null
    
    // 长按处理
    private var longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var isLongPress = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var hasSelection = false
    private var initialSelectionMade = false
    
    init {
        // 启用内建的文本选择功能
        isLongClickable = true
        setOnLongClickListener { 
            // 使用我们自己的长按处理逻辑
            false 
        }
    }
    
    /**
     * 设置文本选择管理器
     */
    fun setTextSelectionManager(manager: TextSelectionManager) {
        textSelectionManager = manager
    }
    
    /**
     * 重写触摸事件，处理长按弹出选择菜单
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                Log.d(TAG, "ACTION_DOWN: x=${event.x}, y=${event.y}")
                lastTouchX = event.x
                lastTouchY = event.y
                
                // 取消之前的长按
                longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                
                // 创建新的长按检测
                isLongPress = false
                initialSelectionMade = false
                longPressRunnable = Runnable {
                    isLongPress = true
                    initialSelectionMade = true
                    handleLongPress(lastTouchX, lastTouchY)
                }.also {
                    longPressHandler.postDelayed(it, LONG_PRESS_TIMEOUT)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                // 如果已经长按并且初始选择已经建立，处理拖动选择
                if (isLongPress && initialSelectionMade) {
                    handleSelectionDrag(event.x, event.y)
                    return true
                } else {
                    // 如果移动距离太大，取消长按
                    val moveThreshold = 10
                    if (Math.abs(event.x - lastTouchX) > moveThreshold || Math.abs(event.y - lastTouchY) > moveThreshold) {
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                Log.d(TAG, "ACTION_UP/CANCEL: isLongPress=$isLongPress, hasSelection=$hasSelection")
                longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                
                // 如果是长按结束，并且有文本选中，显示操作菜单
                if (isLongPress && hasSelection) {
                    // 延迟一点显示菜单，确保选择已经完成
                    Handler(Looper.getMainLooper()).postDelayed({
                        checkSelectionAndShowMenu(event.x.toInt(), event.y.toInt())
                    }, 100)
                    return true
                }
            }
        }
        
        return super.onTouchEvent(event)
    }
    
    /**
     * 处理选择拖动
     */
    private fun handleSelectionDrag(x: Float, y: Float) {
        // 扩展当前选择到拖动位置
        val script = """
            (function() {
                try {
                    var range = document.caretRangeFromPoint($x, $y);
                    if (range && window.getSelection().rangeCount > 0) {
                        var selection = window.getSelection();
                        var currentRange = selection.getRangeAt(0);
                        
                        // 使用拖动位置更新范围结束点
                        currentRange.setEnd(range.startContainer, range.startOffset);
                        selection.removeAllRanges();
                        selection.addRange(currentRange);
                        
                        return JSON.stringify({
                            text: selection.toString(),
                            hasSelection: selection.toString().length > 0
                        });
                    }
                    return JSON.stringify({hasSelection: false});
                } catch (e) {
                    return JSON.stringify({error: e.toString()});
                }
            })();
        """.trimIndent()
        
        evaluateJavascript(script) { result ->
            Log.d(TAG, "选择拖动结果: $result")
            hasSelection = result != null && result.contains("hasSelection\":true")
        }
    }
    
    /**
     * 处理长按事件
     */
    private fun handleLongPress(x: Float, y: Float) {
        Log.d(TAG, "处理长按: x=$x, y=$y")
        
        // 注入JavaScript创建初始选择
        val script = """
            (function() {
                try {
                    // 尝试在长按位置创建范围
                    var range = document.caretRangeFromPoint($x, $y);
                    if (range) {
                        // 清除现有选择
                        window.getSelection().removeAllRanges();
                        // 设置新选择
                        window.getSelection().addRange(range);
                        
                        // 扩展选择到单词
                        window.getSelection().modify('extend', 'forward', 'word');
                        
                        // 尝试识别是否在输入框内
                        var isInput = document.activeElement && 
                                      (document.activeElement.tagName === 'INPUT' || 
                                       document.activeElement.tagName === 'TEXTAREA' ||
                                       document.activeElement.isContentEditable);
                        
                        return JSON.stringify({
                            text: window.getSelection().toString(),
                            hasSelection: window.getSelection().toString().length > 0,
                            isInput: isInput
                        });
                    }
                    return JSON.stringify({hasSelection: false});
                } catch (e) {
                    return JSON.stringify({error: e.toString()});
                }
            })();
        """.trimIndent()
        
        evaluateJavascript(script) { result ->
            Log.d(TAG, "长按选择结果: $result")
            
            if (result != null && result.contains("hasSelection\":true")) {
                hasSelection = true
                
                // 如果是在输入框内，立即显示菜单
                if (result.contains("isInput\":true")) {
                    textSelectionManager?.showTextSelectionMenu(this, x.toInt(), y.toInt())
                }
                // 否则，让用户可以拖动选择文本，操作结束后再显示菜单
            }
        }
    }
    
    /**
     * 检查选择并显示菜单
     */
    private fun checkSelectionAndShowMenu(x: Int, y: Int) {
        val script = """
            (function() {
                var selection = window.getSelection();
                var text = selection.toString();
                return JSON.stringify({
                    text: text,
                    hasSelection: text.length > 0
                });
            })();
        """.trimIndent()
        
        evaluateJavascript(script) { result ->
            Log.d(TAG, "检查选择结果: $result")
            
            if (result != null && result.contains("hasSelection\":true")) {
                // 获取选择范围的位置
                getSelectionPosition { left, top, right, bottom ->
                    // 使用选择范围的位置显示菜单
                    Log.d(TAG, "显示菜单位置: left=$left, top=$top, right=$right, bottom=$bottom")
                    // 在选择区域的下方显示菜单
                    textSelectionManager?.showTextSelectionMenu(this, (left + right) / 2, bottom + 20)
                }
            }
        }
    }
    
    /**
     * 获取选择范围的位置
     */
    private fun getSelectionPosition(callback: (left: Int, top: Int, right: Int, bottom: Int) -> Unit) {
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
        
        evaluateJavascript(script) { result ->
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
    
    /**
     * 重写创建ActionMode的方法，替换为自定义实现
     */
    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? {
        return MyActionMode(context, callback)
    }
    
    /**
     * 自定义ActionMode，用于替代系统默认的文本选择菜单
     */
    private inner class MyActionMode(
        private val context: Context,
        private val originalCallback: ActionMode.Callback
    ) : ActionMode() {
        override fun setTitle(title: CharSequence?) {}
        override fun setTitle(resId: Int) {}
        override fun setSubtitle(subtitle: CharSequence?) {}
        override fun setSubtitle(resId: Int) {}
        override fun setCustomView(view: android.view.View?) {}
        override fun invalidate() {}
        override fun finish() {}
        override fun getMenu(): Menu = DummyMenu()
        override fun getTitle() = null
        override fun getSubtitle() = null
        override fun getCustomView() = null
        override fun getMenuInflater() = null
        
        /**
         * 虚拟Menu实现
         */
        private inner class DummyMenu : Menu {
            override fun add(titleRes: Int) = DummyMenuItem()
            override fun add(title: CharSequence?) = DummyMenuItem()
            override fun add(groupId: Int, itemId: Int, order: Int, titleRes: Int) = DummyMenuItem()
            override fun add(groupId: Int, itemId: Int, order: Int, title: CharSequence?) = DummyMenuItem()
            override fun addSubMenu(titleRes: Int) = null
            override fun addSubMenu(title: CharSequence?) = null
            override fun addSubMenu(groupId: Int, itemId: Int, order: Int, titleRes: Int) = null
            override fun addSubMenu(groupId: Int, itemId: Int, order: Int, title: CharSequence?) = null
            override fun addIntentOptions(groupId: Int, itemId: Int, order: Int, caller: android.content.ComponentName?, specifics: Array<out android.content.Intent>?, intent: android.content.Intent?, flags: Int, outSpecificItems: Array<out MenuItem>?) = 0
            override fun removeItem(id: Int) {}
            override fun removeGroup(groupId: Int) {}
            override fun clear() {}
            override fun setGroupCheckable(group: Int, checkable: Boolean, exclusive: Boolean) {}
            override fun setGroupVisible(group: Int, visible: Boolean) {}
            override fun setGroupEnabled(group: Int, enabled: Boolean) {}
            override fun hasVisibleItems() = false
            override fun findItem(id: Int) = null
            override fun size() = 0
            override fun getItem(index: Int) = DummyMenuItem()
            override fun close() {}
            override fun performShortcut(keyCode: Int, event: android.view.KeyEvent?, flags: Int) = false
            override fun isShortcutKey(keyCode: Int, event: android.view.KeyEvent?) = false
            override fun performIdentifierAction(id: Int, flags: Int) = false
            override fun setQwertyMode(isQwerty: Boolean) {}
        }
        
        /**
         * 虚拟MenuItem实现
         */
        private inner class DummyMenuItem : MenuItem {
            override fun setTitle(title: CharSequence?) = this
            override fun setTitle(title: Int) = this
            override fun getTitle() = null
            override fun setTitleCondensed(title: CharSequence?) = this
            override fun getTitleCondensed() = null
            override fun setIcon(icon: Int) = this
            override fun setIcon(icon: android.graphics.drawable.Drawable?) = this
            override fun getIcon() = null
            override fun setIntent(intent: android.content.Intent?) = this
            override fun getIntent() = null
            override fun setShortcut(numericChar: Char, alphaChar: Char) = this
            override fun setNumericShortcut(numericChar: Char) = this
            override fun getNumericShortcut() = ' '
            
            // 使用新的API，替换旧的setAlphaShortcut和getAlphaShortcut
            override fun setAlphabeticShortcut(alphaChar: Char) = this
            override fun getAlphabeticShortcut() = ' '
            
            override fun setCheckable(checkable: Boolean) = this
            override fun isCheckable() = false
            override fun setChecked(checked: Boolean) = this
            override fun isChecked() = false
            override fun setVisible(visible: Boolean) = this
            override fun isVisible() = false
            override fun setEnabled(enabled: Boolean) = this
            override fun isEnabled() = false
            override fun hasSubMenu() = false
            override fun getSubMenu() = null
            override fun setOnMenuItemClickListener(menuItemClickListener: MenuItem.OnMenuItemClickListener?) = this
            override fun getMenuInfo() = null
            override fun setShowAsAction(actionEnum: Int) {}
            override fun setShowAsActionFlags(actionEnum: Int) = this
            override fun setActionView(view: android.view.View?) = this
            override fun setActionView(resId: Int) = this
            override fun getActionView() = null
            override fun setActionProvider(actionProvider: android.view.ActionProvider?) = this
            override fun getActionProvider() = null
            override fun expandActionView() = false
            override fun collapseActionView() = false
            override fun isActionViewExpanded() = false
            override fun setOnActionExpandListener(listener: MenuItem.OnActionExpandListener?) = this
            override fun getItemId() = 0
            override fun getGroupId() = 0
            override fun getOrder() = 0
            
            // API 26+的方法，需要条件编译
            override fun setContentDescription(contentDescription: CharSequence?) = this
            override fun getContentDescription() = null
            override fun setTooltipText(tooltipText: CharSequence?) = this
            override fun getTooltipText() = null
            override fun setAlphabeticShortcut(alphaChar: Char, alphaModifiers: Int) = this
            override fun getAlphabeticModifiers() = 0
            override fun setNumericShortcut(numericChar: Char, numericModifiers: Int) = this
            override fun getNumericModifiers() = 0
            override fun setShortcut(numericChar: Char, alphaChar: Char, numericModifiers: Int, alphaModifiers: Int) = this
            override fun setIconTintList(tint: android.content.res.ColorStateList?) = this
            override fun getIconTintList() = null
            override fun setIconTintMode(tintMode: android.graphics.PorterDuff.Mode?) = this
            override fun getIconTintMode() = null
        }
    }
} 