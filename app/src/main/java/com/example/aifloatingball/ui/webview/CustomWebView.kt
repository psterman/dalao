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
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import android.webkit.WebView.HitTestResult
import com.example.aifloatingball.ui.text.TextSelectionManager

/**
 * 链接长按监听器
 */
interface LinkMenuListener {
    fun onLinkLongPressed(url: String, x: Int, y: Int)
}

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
    var linkMenuListener: LinkMenuListener? = null
    
    // 长按处理
    private var longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var isLongPress = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var hasSelection = false
    private var initialSelectionMade = false
    
    // 输入法管理器
    private var inputMethodManager: InputMethodManager? = null
    
    init {
        // 启用内建的文本选择功能
        isLongClickable = true
        setOnLongClickListener { v ->
            val result = (v as WebView).hitTestResult
            val x = lastTouchX.toInt()
            val y = lastTouchY.toInt()

            // 检查是否长按了链接
            if (result.type == HitTestResult.SRC_ANCHOR_TYPE || result.type == HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                result.extra?.let { url ->
                    Log.d(TAG, "Link long pressed: $url at ($x, $y)")
                    linkMenuListener?.onLinkLongPressed(url, x, y)
                    return@setOnLongClickListener true // 消费事件
                }
            }
            
            // 如果不是链接，则允许我们自己的文本选择逻辑接管
            false
        }
        
        // 初始化输入法管理器
        inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        
        // 设置WebView属性以更好地支持输入
        isFocusable = true
        isFocusableInTouchMode = true
    }
    
    /**
     * 显示软键盘
     */
    fun showSoftKeyboard() {
        requestFocus()
        inputMethodManager?.showSoftInput(this, InputMethodManager.SHOW_FORCED)
    }
    
    /**
     * 隐藏软键盘
     */
    fun hideSoftKeyboard() {
        inputMethodManager?.hideSoftInputFromWindow(windowToken, 0)
    }
    
    /**
     * 聚焦到特定输入元素并显示键盘
     */
    fun focusInputElement(elementId: String) {
        requestFocus()
        
        // 通过JavaScript获取并聚焦输入元素
        val script = """
            (function() {
                var inputElement = document.getElementById('${elementId}');
                if (inputElement) {
                    inputElement.focus();
                    return true;
                }
                return false;
            })();
        """.trimIndent()
        
        evaluateJavascript(script) { result ->
            if (result == "true") {
                // 如果成功聚焦到输入元素，显示软键盘
                Handler(Looper.getMainLooper()).postDelayed({
                    showSoftKeyboard()
                }, 200)
            }
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
                isLongPress = false // 重置长按状态
                hasSelection = false // 重置选择状态
                initialSelectionMade = false // 重置初始选择状态

                longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                longPressRunnable = Runnable {
                    isLongPress = true
                    // 长按发生时，直接尝试处理长按逻辑，包括选择和显示菜单/选择柄
                    handleLongPress(lastTouchX, lastTouchY)
                }.also {
                    longPressHandler.postDelayed(it, LONG_PRESS_TIMEOUT)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isLongPress && initialSelectionMade) {
                    // 如果已经触发长按并且初始选择已建立，则处理拖动
                    handleSelectionDrag(event.x, event.y)
                    return true // 消费事件，因为我们在处理拖动选择
                } else {
                    // 如果移动距离超过阈值，则取消长按检测
                    val moveThreshold = 10 // 像素
                    if (Math.abs(event.x - lastTouchX) > moveThreshold || Math.abs(event.y - lastTouchY) > moveThreshold) {
                        longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                // 当手指抬起或事件取消时，如果已经标记为长按并且有选择，则检查并显示菜单
                // 即使没有拖动，如果长按产生了选择，也应该显示菜单
                if (isLongPress && hasSelection) {
                    // 使用最后一次触摸的精确点（或选择区域的中心点）来定位菜单
                    // checkSelectionAndShowMenu 会获取当前选择并显示菜单及选择柄
                     Handler(Looper.getMainLooper()).postDelayed({
                        checkSelectionAndShowMenu(lastTouchX.toInt(), lastTouchY.toInt(), true)
                    }, 50) // 稍作延迟以确保JS执行完毕
                    return true // 消费事件
                }
                isLongPress = false // 重置长按状态
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
                        
                        // 获取更新后的选择范围位置
                        var rects = currentRange.getClientRects();
                        var left = 0, top = 0, right = 0, bottom = 0;
                        
                        if (rects.length > 0) {
                            left = rects[0].left;
                            top = rects[0].top;
                            right = rects[rects.length-1].right;
                            bottom = rects[rects.length-1].bottom;
                        }
                        
                        return JSON.stringify({
                            text: selection.toString(),
                            hasSelection: selection.toString().length > 0,
                            left: Math.round(left),
                            top: Math.round(top),
                            right: Math.round(right),
                            bottom: Math.round(bottom)
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
            
            try {
                val cleanResult = result?.replace("\\\"", "\"")?.trim('"') ?: ""
                if (cleanResult.contains("hasSelection\":true")) {
                    hasSelection = true
                    
                    // 解析选择范围位置，但不主动更新菜单
                    // 选择柄管理器会自行处理选择柄位置更新
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析拖动选择结果失败", e)
            }
        }
    }
    
    /**
     * 处理长按事件
     */
    private fun handleLongPress(x: Float, y: Float) {
        Log.d(TAG, "处理长按: x=$x, y=$y")
        val script = """
            (function() {
                try {
                    var range = document.caretRangeFromPoint($x, $y);
                    var selection = window.getSelection();
                    selection.removeAllRanges();

                    if (range) {
                        selection.addRange(range);
                        selection.modify('extend', 'forward', 'word');
                        selection.modify('extend', 'backward', 'word');
                    }

                    var selectedText = selection.toString();
                    var rects = selection.rangeCount > 0 ? selection.getRangeAt(0).getClientRects() : null;
                    var bounds = { left: 0, top: 0, right: 0, bottom: 0 };

                    if (rects && rects.length > 0) {
                        bounds.left = rects[0].left;
                        bounds.top = rects[0].top;
                        bounds.right = rects[rects.length - 1].right;
                        bounds.bottom = rects[rects.length - 1].bottom;
                    } else if (selectedText.length > 0) {
                        console.error('Selected text found, but no client rects.');
                    }
                    
                    var isInput = document.activeElement && 
                                  (document.activeElement.tagName === 'INPUT' || 
                                   document.activeElement.tagName === 'TEXTAREA' ||
                                   document.activeElement.isContentEditable);

                    return JSON.stringify({
                        text: selectedText,
                        hasSelection: selectedText.length > 0,
                        isInput: isInput,
                        left: Math.round(bounds.left),
                        top: Math.round(bounds.top),
                        right: Math.round(bounds.right),
                        bottom: Math.round(bounds.bottom)
                    });
                } catch (e) {
                    console.error('Error in handleLongPress JS: ' + e.toString());
                    return JSON.stringify({error: e.toString(), hasSelection: false});
                }
            })();
        """.trimIndent()

        evaluateJavascript(script) { result ->
            Log.d(TAG, "长按选择JS结果: $result")
            if (result == null || result.contains("\"error\":")) {
                Log.e(TAG, "长按JS执行错误或无结果: $result")
                hasSelection = false
                initialSelectionMade = false
                return@evaluateJavascript
            }

            try {
                val cleanResult = result.replace("\\\"", "\"").trim('"')
                val jsonReader = android.util.JsonReader(java.io.StringReader(cleanResult))
                jsonReader.beginObject()

                var jsonLeft = 0
                var jsonTop = 0
                var jsonRight = 0
                var jsonBottom = 0
                var jsonIsInput = false
                var jsonHasSelection = false
                var selectedText = ""

                while (jsonReader.hasNext()) {
                    when (jsonReader.nextName()) {
                        "left" -> jsonLeft = jsonReader.nextInt()
                        "top" -> jsonTop = jsonReader.nextInt()
                        "right" -> jsonRight = jsonReader.nextInt()
                        "bottom" -> jsonBottom = jsonReader.nextInt()
                        "isInput" -> jsonIsInput = jsonReader.nextBoolean()
                        "hasSelection" -> jsonHasSelection = jsonReader.nextBoolean()
                        "text" -> selectedText = jsonReader.nextString()
                        else -> jsonReader.skipValue()
                    }
                }
                jsonReader.endObject()
                jsonReader.close()

                hasSelection = jsonHasSelection && selectedText.isNotEmpty()
                initialSelectionMade = hasSelection

                if (hasSelection) {
                    Log.i(TAG, "handleLongPress: 有效选择. Text: '${selectedText.take(30)}', Bounds LTRB: ($jsonLeft, $jsonTop, $jsonRight, $jsonBottom). 调用 showTextSelectionMenu.")
                    val menuAnchorX = (jsonLeft + jsonRight) / 2
                    val menuAnchorY = jsonBottom + 20
                    Log.d(TAG, "[MENU_TRIGGER_DEBUG] Attempting to call showTextSelectionMenu from handleLongPress (selection). Anchor: ($menuAnchorX, $menuAnchorY)")
                    textSelectionManager?.showTextSelectionMenu(this, menuAnchorX, menuAnchorY)
                } else {
                    Log.i(TAG, "handleLongPress: JS报告无选择. isInput: $jsonIsInput")
                    if (jsonIsInput) {
                        Log.i(TAG, "handleLongPress: 无选择但在输入框内. 调用 showTextSelectionMenu 基于点击坐标 ($x, $y).")
                        Log.d(TAG, "[MENU_TRIGGER_DEBUG] Attempting to call showTextSelectionMenu from handleLongPress (input field click). Anchor: ($x, $y)")
                        textSelectionManager?.showTextSelectionMenu(this, x.toInt(), y.toInt())
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析长按JS结果失败", e)
                hasSelection = false
                initialSelectionMade = false
                Log.d(TAG, "[MENU_TRIGGER_DEBUG] Attempting to call showTextSelectionMenu from handleLongPress (exception case). Anchor: ($x, $y)")
                textSelectionManager?.showTextSelectionMenu(this, x.toInt(), y.toInt())
            }
        }
    }
    
    /**
     * 检查选择并显示菜单。增加一个参数，表明是否由长按后的 ACTION_UP 触发。
     */
    private fun checkSelectionAndShowMenu(x: Int, y: Int, fromActionUp: Boolean = false) {
        val script = """
            (function() {
                var selection = window.getSelection();
                var text = selection.toString();
                var rects = selection.rangeCount > 0 ? selection.getRangeAt(0).getClientRects() : null;
                var bounds = { left: 0, top: 0, right: 0, bottom: 0 };
                if (rects && rects.length > 0) {
                    bounds.left = rects[0].left;
                    bounds.top = rects[0].top;
                    bounds.right = rects[rects.length - 1].right;
                    bounds.bottom = rects[rects.length - 1].bottom;
                }
                return JSON.stringify({
                    text: text,
                    hasSelection: text.length > 0,
                    left: Math.round(bounds.left),
                    top: Math.round(bounds.top),
                    right: Math.round(bounds.right),
                    bottom: Math.round(bounds.bottom)
                });
            })();
        """.trimIndent()

        evaluateJavascript(script) { result ->
            Log.d(TAG, "检查选择JS结果: $result")
            if (result == null || result.contains("\"error\":")) {
                 Log.e(TAG, "检查选择JS错误或无结果: $result")
                if (fromActionUp) {
                    textSelectionManager?.hideTextSelectionMenu()
                }
                return@evaluateJavascript
            }

            try {
                val cleanResult = result.replace("\\\"", "\"").trim('"')
                val jsonReader = android.util.JsonReader(java.io.StringReader(cleanResult))
                jsonReader.beginObject()

                var jsonLeft = 0
                var jsonTop = 0
                var jsonRight = 0
                var jsonBottom = 0
                var jsonHasSelection = false
                var selectedText = ""

                while (jsonReader.hasNext()) {
                    when (jsonReader.nextName()) {
                        "left" -> jsonLeft = jsonReader.nextInt()
                        "top" -> jsonTop = jsonReader.nextInt()
                        "right" -> jsonRight = jsonReader.nextInt()
                        "bottom" -> jsonBottom = jsonReader.nextInt()
                        "hasSelection" -> jsonHasSelection = jsonReader.nextBoolean()
                        "text" -> selectedText = jsonReader.nextString()
                        else -> jsonReader.skipValue()
                    }
                }
                jsonReader.endObject()
                jsonReader.close()

                if (jsonHasSelection && selectedText.isNotEmpty()) {
                    hasSelection = true
                    Log.i(TAG, "checkSelectionAndShowMenu: 有效选择. Text: '${selectedText.take(30)}', Bounds LTRB: ($jsonLeft, $jsonTop, $jsonRight, $jsonBottom). fromActionUp: $fromActionUp. 调用 showTextSelectionMenu.")
                    val menuAnchorX = (jsonLeft + jsonRight) / 2
                    val menuAnchorY = jsonBottom + 20 
                    Log.d(TAG, "[MENU_TRIGGER_DEBUG] Attempting to call showTextSelectionMenu from checkSelectionAndShowMenu. Anchor: ($menuAnchorX, $menuAnchorY), fromActionUp: $fromActionUp")
                    textSelectionManager?.showTextSelectionMenu(this, menuAnchorX, menuAnchorY)
                } else {
                    Log.i(TAG, "checkSelectionAndShowMenu: JS报告无选择. fromActionUp: $fromActionUp")
                    if (fromActionUp) {
                        textSelectionManager?.hideTextSelectionMenu()
                    }
                    hasSelection = false
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析检查选择JS结果失败", e)
                if (fromActionUp) {
                    textSelectionManager?.hideTextSelectionMenu()
                }
                hasSelection = false
            }
        }
    }
    
    /**
     * 获取选择范围的位置
     */
    private fun getSelectionPosition(callback: (left: Int, top: Int, right: Int, bottom: Int, text: String) -> Unit) {
        val script = """
            (function() {
                var selection = window.getSelection();
                if (selection.rangeCount > 0) {
                    var range = selection.getRangeAt(0);
                    var rects = range.getClientRects();
                    var text = selection.toString();
                    
                    if (rects.length > 0) {
                        var firstRect = rects[0];
                        var lastRect = rects[rects.length - 1];
                        return JSON.stringify({
                            left: Math.round(firstRect.left),
                            top: Math.round(firstRect.top),
                            right: Math.round(lastRect.right),
                            bottom: Math.round(lastRect.bottom),
                            text: text,
                            hasSelection: text.length > 0
                        });
                    }
                }
                return JSON.stringify({error: "No selection range", hasSelection: false});
            })();
        """.trimIndent()

        evaluateJavascript(script) { result ->
            if (result == null || result.contains("\"error\":")) {
                Log.e(TAG, "getSelectionPosition JS错误: $result")
                callback(0,0,0,0, "")
                return@evaluateJavascript
            }
            try {
                val cleanResult = result.replace("\\\"", "\"").trim('"')
                val jsonReader = android.util.JsonReader(java.io.StringReader(cleanResult))
                jsonReader.beginObject()
                
                var jsonLeft = 0
                var jsonTop = 0
                var jsonRight = 0
                var jsonBottom = 0
                var selectedText = ""
                var jsonHasSelection = false

                while (jsonReader.hasNext()) {
                    when (jsonReader.nextName()) {
                        "left" -> jsonLeft = jsonReader.nextInt()
                        "top" -> jsonTop = jsonReader.nextInt()
                        "right" -> jsonRight = jsonReader.nextInt()
                        "bottom" -> jsonBottom = jsonReader.nextInt()
                        "text" -> selectedText = jsonReader.nextString()
                        "hasSelection" -> jsonHasSelection = jsonReader.nextBoolean()
                        else -> jsonReader.skipValue()
                    }
                }
                jsonReader.endObject()
                jsonReader.close()

                if(jsonHasSelection && selectedText.isNotEmpty()){
                    callback(jsonLeft, jsonTop, jsonRight, jsonBottom, selectedText)
                } else {
                    callback(0,0,0,0, "")
                }
            } catch (e: Exception) {
                Log.e(TAG, "解析getSelectionPosition JS结果失败", e)
                callback(0,0,0,0, "")
            }
        }
    }
    
    /**
     * 重写创建ActionMode的方法，替换为自定义实现
     */
    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? {
        Log.d(TAG, "startActionMode called, type: $type. Attempting to show custom menu.")

        // 关键：在这里调用 textSelectionManager 来显示或更新菜单
        // 我们需要获取当前选择的精确位置
        textSelectionManager?.getSelectionPosition(this) { left, _top, right, bottom, selectedText ->
            if (selectedText.isNotEmpty()) {
                val menuX = (left + right) / 2
                // 根据您的设计，菜单可以显示在选区上方或下方
                // val menuY = _top - (floatingMenuView?.measuredHeight ?: 100) // 示例：选区上方
                val menuY = bottom // 当前 TextSelectionManager 似乎倾向于在选区下方或基于底部定位
                Log.d(TAG, "startActionMode: Selection found ('${selectedText.take(30)}...'), showing custom menu at ($menuX, $menuY)")
                textSelectionManager?.showTextSelectionMenu(this, menuX, menuY)
            } else {
                Log.d(TAG, "startActionMode: No text selected, hiding custom menu.")
                textSelectionManager?.hideTextSelectionMenu()
            }
        }

        // 返回自定义的ActionMode以阻止系统默认菜单，同时允许我们的自定义菜单运作
        // MyActionMode 应该只阻止默认UI，而不干扰我们的TextSelectionManager
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