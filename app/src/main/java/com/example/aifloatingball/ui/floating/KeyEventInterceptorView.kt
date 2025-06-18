package com.example.aifloatingball.ui.floating

import android.content.Context
import android.util.AttributeSet
import android.view.KeyEvent
import androidx.cardview.widget.CardView

/**
 * 一个自定义的视图，用于拦截关键事件，特别是返回按钮的按下。
 * 这允许我们将在浮动窗口中的返回键事件重定向到自定义操作，
 * 例如在WebView中向后导航。
 */
class KeyEventInterceptorView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : CardView(context, attrs, defStyleAttr) {

    /**
     * 用于处理返回按钮按下的监听器。
     */
    interface BackPressListener {
        /**
         * 当返回按钮被按下时调用。
         * @return 如果事件被处理，则为true，否则为false。
         */
        fun onBackButtonPressed(): Boolean
    }

    var backPressListener: BackPressListener? = null

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // 检查是否是返回键的"弹起"事件
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            // 如果监听器已设置并处理了事件，则返回true
            if (backPressListener?.onBackButtonPressed() == true) {
                return true
            }
        }
        // 对于所有其他事件，或如果返回键未被处理，则调用超类实现
        return super.dispatchKeyEvent(event)
    }
} 