package com.example.aifloatingball.video

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.VideoView

/**
 * 紧凑型 MediaController，用于小窗口播放器
 * 控件紧贴播放器底部，大小适配小窗口
 */
class CompactMediaController @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : MediaController(context, attrs) {

    companion object {
        private const val TAG = "CompactMediaController"
    }

    override fun setAnchorView(view: View?) {
        super.setAnchorView(view)
        // 调整控件大小和位置
        adjustLayoutForCompactView()
    }

    override fun show(timeout: Int) {
        super.show(timeout)
        // 确保控件紧贴播放器底部
        adjustLayoutForCompactView()
    }

    /**
     * 调整布局以适应紧凑型视图
     */
    private fun adjustLayoutForCompactView() {
        try {
            // 获取父容器
            val parent = parent as? ViewGroup ?: return
            
            // 设置控件位置在底部
            val layoutParams = layoutParams as? android.widget.FrameLayout.LayoutParams
            layoutParams?.apply {
                gravity = Gravity.BOTTOM
                width = android.view.ViewGroup.LayoutParams.MATCH_PARENT
                height = dpToPx(48) // 紧凑型高度
                setMargins(0, 0, 0, 0)
            }
            this.layoutParams = layoutParams
            
            // 调整内部控件大小
            adjustChildViews()
            
            Log.d(TAG, "紧凑型MediaController布局已调整")
        } catch (e: Exception) {
            Log.e(TAG, "调整紧凑型MediaController布局失败", e)
        }
    }

    /**
     * 调整子视图大小
     */
    private fun adjustChildViews() {
        try {
            val childCount = childCount
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                // 调整子视图的padding和大小
                child.setPadding(
                    dpToPx(8),
                    dpToPx(4),
                    dpToPx(8),
                    dpToPx(4)
                )
                
                // 如果是按钮，调整大小
                if (child is android.widget.ImageButton || child is android.widget.Button) {
                    val params = child.layoutParams
                    params?.width = dpToPx(36)
                    params?.height = dpToPx(36)
                    child.layoutParams = params
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "调整子视图大小失败", e)
        }
    }

    /**
     * dp转px
     */
    private fun dpToPx(dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density + 0.5f).toInt()
    }
}

