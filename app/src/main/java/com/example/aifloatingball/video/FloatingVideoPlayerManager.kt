package com.example.aifloatingball.video

import android.app.Activity
import android.content.Context
import android.graphics.Point
import android.net.Uri
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.MediaController
import android.widget.VideoView
import androidx.core.view.ViewCompat
import com.example.aifloatingball.R

/**
 * 轻量级悬浮视频播放器管理器（无外部依赖）。
 * - 使用 VideoView 播放（避免额外依赖）；
 * - 以可拖拽的小窗覆盖在 Activity 内容上；
 * - 提供 show(url)、hide() 控制；
 */
class FloatingVideoPlayerManager(private val activity: Activity) {

    private var overlayRoot: FrameLayout? = null
    private var container: FrameLayout? = null
    private var videoView: VideoView? = null
    private var closeBtn: ImageButton? = null
    private var menuBtn: ImageButton? = null

    private var isShowing = false

    // 拖拽相关
    private var dX = 0f
    private var dY = 0f
    private var lastX = 0f
    private var lastY = 0f

    fun attachIfNeeded() {
        if (overlayRoot != null) return

        val root = activity.findViewById<ViewGroup>(android.R.id.content)
        val inflater = LayoutInflater.from(activity)
        val overlay = inflater.inflate(R.layout.layout_floating_video_player, root, false) as FrameLayout

        container = overlay.findViewById(R.id.video_container)
        closeBtn = overlay.findViewById(R.id.btn_close)
        menuBtn = overlay.findViewById(R.id.btn_menu)

        // 在容器中创建 VideoView + 控制条
        val vv = VideoView(activity)
        val mc = MediaController(activity)
        mc.setAnchorView(vv)
        vv.setMediaController(mc)
        container?.addView(vv, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        videoView = vv

        // 初始为小窗尺寸并放在右下角
        val size = dp(activity, 240) to dp(activity, 135) // 16:9 窗口
        val lp = FrameLayout.LayoutParams(size.first, size.second)
        lp.gravity = Gravity.BOTTOM or Gravity.END
        lp.setMargins(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 80))
        overlay.layoutParams = lp

        // 可拖拽
        enableDrag(overlay)

        // 关闭按钮
        closeBtn?.setOnClickListener { hide() }

        // 预留菜单（比如切换比例/返回网页播放等）
        menuBtn?.setOnClickListener {
            // 暂时只提供收起
            hide()
        }

        // 默认隐藏
        overlay.visibility = View.GONE
        root.addView(overlay)
        overlayRoot = overlay
    }

    fun show(url: String?) {
        if (url.isNullOrBlank()) return
        attachIfNeeded()
        val ov = overlayRoot ?: return
        val vv = videoView ?: return

        try {
            vv.setVideoURI(Uri.parse(url))
            vv.setOnPreparedListener { mp ->
                mp.isLooping = false
                vv.start()
            }
            ov.visibility = View.VISIBLE
            isShowing = true
        } catch (_: Exception) {
            // ignore
        }
    }

    fun hide() {
        val vv = videoView
        try {
            vv?.stopPlayback()
        } catch (_: Exception) {}
        overlayRoot?.visibility = View.GONE
        isShowing = false
    }

    fun isShowing(): Boolean = isShowing

    private fun enableDrag(target: View) {
        target.setOnTouchListener { v, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dX = v.x - event.rawX
                    dY = v.y - event.rawY
                    lastX = event.rawX
                    lastY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val newX = event.rawX + dX
                    val newY = event.rawY + dY
                    v.x = newX
                    v.y = newY
                    lastX = event.rawX
                    lastY = event.rawY
                    true
                }
                else -> false
            }
        }
    }

    companion object {
        private fun dp(context: Context, value: Int): Int {
            val density = context.resources.displayMetrics.density
            return (value * density + 0.5f).toInt()
        }
    }
}

