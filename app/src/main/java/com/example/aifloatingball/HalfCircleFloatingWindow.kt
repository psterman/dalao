package com.example.aifloatingball

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.Drawable
import android.graphics.Bitmap
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.animation.Interpolator
import android.animation.ValueAnimator
import android.animation.AnimatorListenerAdapter
import android.animation.Animator
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import android.widget.ImageView
import android.webkit.WebView

/**
 * 半圆形悬浮窗管理类
 * 用于处理悬浮窗的半圆形变换和动画效果
 */
class HalfCircleFloatingWindow(private val context: Context) {
    
    companion object {
        private const val TAG = "HalfCircleFloatingWindow"
        private const val ANIMATION_DURATION = 300L
        
        // 边缘位置常量
        const val EDGE_NONE = 0
        const val EDGE_LEFT = 1
        const val EDGE_RIGHT = 2
        
        private const val BALL_SIZE = 48 // dp
    }
    
    // 添加 floatingView 属性
    private var floatingView: View? = null
    
    // 屏幕尺寸
    private val screenWidth = context.resources.displayMetrics.widthPixels
    private val screenHeight = context.resources.displayMetrics.heightPixels
    
    // 动画相关
    private var animator: ValueAnimator? = null
    private var isAnimating = false
    
    // 状态相关
    private var isHidden = false
    private var edgePosition = EDGE_NONE
    
    // 原始尺寸和位置
    private var originalWidth = 0
    private var originalHeight = 0
    private var originalX = 0
    
    private var originalY = 0
    
    // 边缘检测阈值
    private val edgeThreshold = context.resources.getDimensionPixelSize(R.dimen.edge_snap_threshold)
    
    private var currentFavicon: Bitmap? = null
    private var defaultIcon: Drawable? = null
    
    private var edgeSnapAnimator: ValueAnimator? = null
    
    init {
        defaultIcon = ContextCompat.getDrawable(context, R.drawable.ic_web_default)
    }
    
    /**
     * 检查是否靠近边缘并执行相应动画
     */
    fun checkEdgeAndSnap(x: Int, y: Int, view: View?, params: WindowManager.LayoutParams?, windowManager: WindowManager?, onEdgeSnap: () -> Unit, onWebViewScrollDisable: () -> Unit): Boolean {
        if (isHidden || isAnimating || view == null || params == null || windowManager == null) return false
        
        // 检查是否靠近左边缘
        if (x <= edgeThreshold) {
            animateToEdge(true, view, params, windowManager, onWebViewScrollDisable)
            onEdgeSnap()
            return true
        }
        
        // 检查是否靠近右边缘
        if (x >= screenWidth - edgeThreshold) {
            animateToEdge(false, view, params, windowManager, onWebViewScrollDisable)
            onEdgeSnap()
            return true
        }
        
        return false
    }
    
    /**
     * 将悬浮窗动画到边缘并变为半圆形
     */
    fun animateToEdge(isLeft: Boolean, view: View, params: WindowManager.LayoutParams, windowManager: WindowManager, onWebViewScrollDisable: () -> Unit) {
        if (isAnimating) return
        
        try {
            // 保存 view 引用
            floatingView = view
            
            // 取消之前的动画
            edgeSnapAnimator?.cancel()
            
            // 保存原始状态
            if (!isHidden) {
                originalWidth = params.width
                originalHeight = params.height
                originalX = params.x
                originalY = params.y
            }
            
            val ballSize = (BALL_SIZE * context.resources.displayMetrics.density).toInt()
            
            // 创建动画
            edgeSnapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = ANIMATION_DURATION
                interpolator = DecelerateInterpolator()
                
                addUpdateListener { animator ->
                    val fraction = animator.animatedFraction
                    params.width = lerpInt(originalWidth, ballSize, fraction)
                    params.height = lerpInt(originalHeight, ballSize, fraction)
                    params.x = lerpInt(originalX, if (isLeft) 0 else (screenWidth - ballSize), fraction)
                    
                    try {
                        windowManager.updateViewLayout(view, params)
                        updateViewsAlpha(view, 1f - fraction)
                    } catch (e: Exception) {
                        Log.e(TAG, "更新布局失败", e)
                    }
                }
                
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        isAnimating = true
                        onWebViewScrollDisable()
                        // 显示悬浮球
                        getFloatingBallView()?.visibility = View.VISIBLE
                        // 设置当前图标
                        updateFloatingBallIcon(currentFavicon)
                    }
                    
                    override fun onAnimationEnd(animation: Animator) {
                        isAnimating = false
                        isHidden = true
                        // 隐藏主要内容视图
                        view.findViewById<WebView>(R.id.floating_webview)?.visibility = View.GONE
                        view.findViewById<View>(R.id.title_bar)?.visibility = View.GONE
                        view.findViewById<View>(R.id.search_bar)?.visibility = View.GONE
                        view.findViewById<View>(R.id.navigation_bar)?.visibility = View.GONE
                    }
                })
                
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "创建边缘动画失败", e)
            isAnimating = false
            isHidden = false
        }
    }
    
    /**
     * 应用半圆形状态到视图
     */
    private fun applyHalfCircleShape(view: View, isLeft: Boolean, progress: Float) {
        try {
            // 创建半圆形状
            val shape = GradientDrawable()
            shape.shape = GradientDrawable.RECTANGLE
            
            // 设置圆角 - 只在一侧设置圆角，形成半圆
            val cornerRadius = view.height / 2f
            if (isLeft) {
                // 左侧靠边，右侧为半圆
                shape.cornerRadii = floatArrayOf(
                    0f, 0f,                    // 左上角
                    cornerRadius, cornerRadius, // 右上角
                    cornerRadius, cornerRadius, // 右下角
                    0f, 0f                     // 左下角
                )
            } else {
                // 右侧靠边，左侧为半圆
                shape.cornerRadii = floatArrayOf(
                    cornerRadius, cornerRadius, // 左上角
                    0f, 0f,                    // 右上角
                    0f, 0f,                    // 右下角
                    cornerRadius, cornerRadius  // 左下角
                )
            }
            
            // 设置背景颜色 - 使用当前主题颜色，并根据进度调整透明度
            val backgroundColor = ContextCompat.getColor(context, R.color.floating_window_background)
            val alpha = (255 * (0.7f + 0.3f * (1f - progress))).toInt()
            shape.setColor(ColorUtils.setAlphaComponent(backgroundColor, alpha))
            
            // 设置边框
            shape.setStroke(2, ContextCompat.getColor(context, R.color.floating_window_border))
            
            // 应用形状到视图背景
            view.background = shape
            
        } catch (e: Exception) {
            Log.e(TAG, "应用半圆形状失败", e)
        }
    }
    
    /**
     * 从边缘恢复到中心
     */
    fun animateShowToCenter(view: View, params: WindowManager.LayoutParams, windowManager: WindowManager, onWebViewScrollEnable: () -> Unit) {
        if (isAnimating) return
        
        try {
            // 保存 view 引用
            floatingView = view
            
            // 取消当前动画
            edgeSnapAnimator?.cancel()
            
            // 显示主视图
            view.visibility = View.VISIBLE
            
            val ballSize = (BALL_SIZE * context.resources.displayMetrics.density).toInt()
            
            // 创建动画
            edgeSnapAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = ANIMATION_DURATION
                interpolator = OvershootInterpolator(1.2f)
                
                addUpdateListener { animator ->
                    val fraction = animator.animatedFraction
                    params.width = lerpInt(ballSize, originalWidth, fraction)
                    params.height = lerpInt(ballSize, originalHeight, fraction)
                    params.x = lerpInt(params.x, originalX, fraction)
                    
                    try {
                        windowManager.updateViewLayout(view, params)
                        updateViewsAlpha(view, fraction)
                    } catch (e: Exception) {
                        Log.e(TAG, "更新布局失败", e)
                    }
                }
                
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        isAnimating = true
                        onWebViewScrollEnable()
                        // 显示主要内容视图
                        view.findViewById<WebView>(R.id.floating_webview)?.visibility = View.VISIBLE
                        view.findViewById<View>(R.id.title_bar)?.visibility = View.VISIBLE
                    }
                    
                    override fun onAnimationEnd(animation: Animator) {
                        isAnimating = false
                        isHidden = false
                        // 隐藏悬浮球
                        getFloatingBallView()?.visibility = View.GONE
                    }
                })
                
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "创建恢复动画失败", e)
            isAnimating = false
            isHidden = false
        }
    }
    
    /**
     * 恢复正常形状
     */
    private fun restoreNormalShape(view: View, progress: Float) {
        try {
            // 创建正常矩形形状，带圆角
            val shape = GradientDrawable()
            shape.shape = GradientDrawable.RECTANGLE
            
            // 设置统一的圆角
            val cornerRadius = context.resources.getDimensionPixelSize(R.dimen.floating_window_corner_radius).toFloat()
            shape.cornerRadius = cornerRadius
            
            // 设置背景颜色
            val backgroundColor = ContextCompat.getColor(context, R.color.floating_window_background)
            shape.setColor(backgroundColor)
            
            // 设置边框
            shape.setStroke(2, ContextCompat.getColor(context, R.color.floating_window_border))
            
            // 应用形状到视图背景
            view.background = shape
            
        } catch (e: Exception) {
            Log.e(TAG, "恢复正常形状失败", e)
        }
    }
    
    /**
     * 立即从边缘恢复
     */
    fun restoreFromEdgeImmediately(view: View, params: WindowManager.LayoutParams, windowManager: WindowManager, onWebViewScrollEnable: () -> Unit, onViewsShow: () -> Unit) {
        try {
            // 取消之前的动画
            animator?.cancel()
            
            // 恢复到原始尺寸和位置
            params.width = originalWidth
            params.height = originalHeight
            params.x = (screenWidth - originalWidth) / 2
            params.y = (screenHeight - originalHeight) / 3
            
            // 更新布局
            windowManager.updateViewLayout(view, params)
            
            // 重置状态
            isHidden = false
            edgePosition = EDGE_NONE
            
            // 恢复正常形状
            restoreNormalShape(view, 1f)
            
            // 显示所有控件
            onViewsShow()
            
            // 启用WebView滚动和缩放
            onWebViewScrollEnable()
            
        } catch (e: Exception) {
            Log.e(TAG, "立即恢复失败", e)
        }
    }
    
    /**
     * 线性插值
     */
    private fun lerpInt(start: Int, end: Int, fraction: Float): Int {
        return (start + (end - start) * fraction).toInt()
    }
    
    /**
     * 获取当前状态
     */
    fun isHidden(): Boolean = isHidden
    
    fun isAnimating(): Boolean = isAnimating
    
    fun getEdgePosition(): Int = edgePosition
    
    fun setFavicon(favicon: Bitmap?) {
        currentFavicon = favicon
        if (isHidden) {
            // 如果当前是隐藏状态，更新浮动球的图标
            updateFloatingBallIcon(favicon)
        }
    }
    
    private fun updateFloatingBallIcon(favicon: Bitmap?) {
        val floatingBall = getFloatingBallView()
        if (floatingBall != null) {
            if (favicon != null) {
                floatingBall.setImageBitmap(favicon)
            } else {
                floatingBall.setImageResource(R.drawable.ic_web_default)
            }
        }
    }
    
    private fun getFloatingBallView(): ImageView? {
        return floatingView?.findViewById(R.id.floating_ball)
    }
    
    private fun updateViewsAlpha(view: View, alpha: Float) {
        view.findViewById<WebView>(R.id.floating_webview)?.alpha = alpha
        view.findViewById<View>(R.id.title_bar)?.alpha = alpha
        view.findViewById<View>(R.id.search_bar)?.alpha = alpha
        view.findViewById<View>(R.id.navigation_bar)?.alpha = alpha
        getFloatingBallView()?.alpha = 1f - alpha
    }
} 