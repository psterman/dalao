package com.example.aifloatingball

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.Interpolator
import android.animation.ValueAnimator
import android.animation.AnimatorListenerAdapter
import android.animation.Animator
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils

/**
 * 半圆形悬浮窗管理类
 * 用于处理悬浮窗的半圆形变换和动画效果
 */
class HalfCircleFloatingWindow(private val context: Context) {
    
    companion object {
        private const val TAG = "HalfCircleWindow"
        private const val ANIMATION_DURATION = 350L
        
        // 边缘位置常量
        const val EDGE_NONE = 0
        const val EDGE_LEFT = 1
        const val EDGE_RIGHT = 2
    }
    
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
    private val edgeThreshold = context.resources.getDimensionPixelSize(R.dimen.floating_window_corner_radius) * 2
    
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
        try {
            // 取消之前的动画
            animator?.cancel()
            
            // 记录原始尺寸，用于恢复
            originalWidth = params.width
            originalHeight = params.height
            
            // 计算半圆形的尺寸 - 高度不变，宽度变为高度的一半
            val targetWidth = params.height / 2
            val targetHeight = params.height
            
            // 计算目标位置 - 靠边
            val targetX = if (isLeft) 0 else screenWidth - targetWidth
            val targetY = params.y
            
            // 创建动画
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = ANIMATION_DURATION
                interpolator = DecelerateInterpolator()
                
                addUpdateListener { animator ->
                    val progress = animator.animatedValue as Float
                    
                    try {
                        // 计算当前宽度、高度和位置
                        params.width = lerpInt(params.width, targetWidth, progress)
                        params.height = lerpInt(params.height, targetHeight, progress)
                        params.x = lerpInt(params.x, targetX, progress)
                        params.y = lerpInt(params.y, targetY, progress)
                        
                        // 更新悬浮窗
                        windowManager.updateViewLayout(view, params)
                        
                        // 应用半圆形状
                        applyHalfCircleShape(view, isLeft, progress)
                    } catch (e: Exception) {
                        Log.e(TAG, "更新布局参数失败", e)
                    }
                }
                
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        isAnimating = true
                        isHidden = true
                        edgePosition = if (isLeft) EDGE_LEFT else EDGE_RIGHT
                        
                        // 禁用 WebView 的滚动
                        onWebViewScrollDisable()
                    }
                    
                    override fun onAnimationEnd(animation: Animator) {
                        if (!animation.isRunning) {
                            isAnimating = false
                            Log.d(TAG, "动画结束，悬浮窗已靠边")
                        }
                    }
                    
                    override fun onAnimationCancel(animation: Animator) {
                        isAnimating = false
                        isHidden = false
                    }
                })
                
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "靠边动画失败", e)
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
    fun animateShowToCenter(view: View, params: WindowManager.LayoutParams, windowManager: WindowManager, onWebViewScrollEnable: () -> Unit, onViewsShow: () -> Unit) {
        try {
            // 取消之前的动画
            animator?.cancel()
            
            // 计算目标位置和尺寸
            val targetWidth = originalWidth
            val targetHeight = originalHeight
            val targetX = (screenWidth - targetWidth) / 2
            val targetY = (screenHeight - targetHeight) / 3
            
            // 创建动画
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = ANIMATION_DURATION
                interpolator = DecelerateInterpolator()
                
                addUpdateListener { animator ->
                    val progress = animator.animatedValue as Float
                    
                    try {
                        // 计算当前宽度、高度和位置
                        params.width = lerpInt(params.width, targetWidth, progress)
                        params.height = lerpInt(params.height, targetHeight, progress)
                        params.x = lerpInt(params.x, targetX, progress)
                        params.y = lerpInt(params.y, targetY, progress)
                        
                        // 更新悬浮窗
                        windowManager.updateViewLayout(view, params)
                        
                        // 恢复正常形状
                        restoreNormalShape(view, progress)
                    } catch (e: Exception) {
                        Log.e(TAG, "更新布局参数失败", e)
                    }
                }
                
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationStart(animation: Animator) {
                        isAnimating = true
                        
                        // 启用 WebView 的滚动和缩放
                        onWebViewScrollEnable()
                    }
                    
                    override fun onAnimationEnd(animation: Animator) {
                        isAnimating = false
                        isHidden = false
                        edgePosition = EDGE_NONE
                        
                        // 显示所有控件
                        onViewsShow()
                        
                        onWebViewScrollEnable()
                    }
                    
                    override fun onAnimationCancel(animation: Animator) {
                        isAnimating = false
                        onWebViewScrollEnable()
                    }
                })
                
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "恢复动画失败", e)
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
} 