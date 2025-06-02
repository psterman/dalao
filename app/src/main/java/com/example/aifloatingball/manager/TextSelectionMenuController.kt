package com.example.aifloatingball.manager

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
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
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object TextSelectionMenuController {
    private var currentPopupWindow: PopupWindow? = null
    private var isMenuShowing = AtomicBoolean(false)
    private var isMenuAnimating = AtomicBoolean(false)
    private val menuLock = ReentrantLock()
    private val handler = Handler(Looper.getMainLooper())
    private var autoHideRunnable: Runnable? = null
    private var onMenuShownCallback: (() -> Unit)? = null

    fun showMenu(
        context: Context,
        windowManager: WindowManager,
        webView: WebView,
        x: Int,
        y: Int,
        selectedText: String,
        onMenuShown: () -> Unit = {}
    ) {
        menuLock.withLock {
            if (isMenuShowing.get() || isMenuAnimating.get()) {
                hideMenu()
            }

            try {
                isMenuAnimating.set(true)
                onMenuShownCallback = onMenuShown
                
                // 创建菜单视图
                val menuView = LayoutInflater.from(context)
                    .inflate(R.layout.text_selection_menu, null).apply {
                        alpha = 0f
                        scaleX = 0.8f
                        scaleY = 0.8f
                    }

                // 设置菜单项点击事件
                setupMenuItems(menuView, webView, context)

                // 创建PopupWindow
                currentPopupWindow = PopupWindow(
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
                }

                // 计算显示位置
                val location = IntArray(2)
                webView.getLocationOnScreen(location)
                val menuX = location[0] + x
                val menuY = location[1] + y - 50

                // 显示菜单
                currentPopupWindow?.showAtLocation(
                    webView,
                    Gravity.NO_GRAVITY,
                    menuX,
                    menuY
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
                        onMenuShownCallback?.invoke()
                    }
                    .start()

                // 设置自动隐藏
                autoHideRunnable?.let { handler.removeCallbacks(it) }
                autoHideRunnable = Runnable { hideMenu() }.also {
                    handler.postDelayed(it, 8000)
                }

            } catch (e: Exception) {
                Log.e("TextSelectionMenu", "显示菜单失败", e)
                cleanupState()
            }
        }
    }

    fun hideMenu() {
        menuLock.withLock {
            if (!isMenuShowing.get() && !isMenuAnimating.get()) {
                return@withLock
            }

            try {
                isMenuAnimating.set(true)
                
                currentPopupWindow?.contentView?.animate()
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
                Log.e("TextSelectionMenu", "隐藏菜单失败", e)
                dismissPopupSafely()
                cleanupState()
            }
        }
    }

    private fun dismissPopupSafely() {
        try {
            currentPopupWindow?.let { popup ->
                if (popup.isShowing) {
                    popup.dismiss()
                }
            }
        } catch (e: Exception) {
            Log.e("TextSelectionMenu", "关闭弹出窗口失败", e)
        }
        currentPopupWindow = null
    }

    private fun cleanupState() {
        isMenuShowing.set(false)
        isMenuAnimating.set(false)
        currentPopupWindow = null
        autoHideRunnable?.let { handler.removeCallbacks(it) }
        autoHideRunnable = null
        onMenuShownCallback = null
    }

    private fun setupMenuItems(menuView: View, webView: WebView, context: Context) {
        menuView.findViewById<View>(R.id.menu_copy)?.setOnClickListener {
            copySelectedText(webView, context)
            hideMenu()
        }

        // 确保资源ID正确
        val shareButton = menuView.findViewById<View>(R.id.action_share)
        if (shareButton != null) {
            shareButton.setOnClickListener {
                shareSelectedText(webView, context)
                hideMenu()
            }
        }
    }

    private fun copySelectedText(webView: WebView, context: Context) {
        webView.evaluateJavascript(
            "(function() { return window.getSelection().toString(); })();"
        ) { result ->
            val text = result.trim('"')
            if (text.isNotEmpty()) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("selected text", text)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun shareSelectedText(webView: WebView, context: Context) {
        webView.evaluateJavascript(
            "(function() { return window.getSelection().toString(); })();"
        ) { result ->
            val text = result.trim('"')
            if (text.isNotEmpty()) {
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(intent, "分享文本"))
            }
        }
    }
} 