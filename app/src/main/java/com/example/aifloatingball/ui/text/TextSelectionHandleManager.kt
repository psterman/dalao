package com.example.aifloatingball.ui.text

import android.content.Context
import android.util.Log
import android.view.WindowManager
import android.webkit.WebView
// import com.example.aifloatingball.view.TextSelectionHandleView // 已删除，移除引用

/**
 * 文本选择柄管理器
 * 负责创建和管理WebView中的文本选择柄
 * (功能已禁用)
 */
class TextSelectionHandleManager(private val context: Context) {
    companion object {
        private const val TAG = "TextSelectionHandleManager"
    }

    // private var leftHandle: TextSelectionHandleView? = null // 已删除
    // private var rightHandle: TextSelectionHandleView? = null // 已删除
    private var currentWebView: WebView? = null
    private val windowManager by lazy { context.getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    
    // 选择范围坐标
    private var selectionLeft = 0
    private var selectionTop = 0
    private var selectionRight = 0
    private var selectionBottom = 0
    
    /**
     * 显示文本选择柄 (功能已禁用)
     */
    fun showSelectionHandles(webView: WebView, left: Int, top: Int, right: Int, bottom: Int) {
        Log.d(TAG, "显示文本选择柄: left=$left, top=$top, right=$right, bottom=$bottom - (功能已禁用)")
    }
    
    /**
     * 隐藏文本选择柄 (功能已禁用)
     */
    fun hideSelectionHandles() {
        Log.d(TAG, "隐藏文本选择柄 - (功能已禁用)")
    }
} 