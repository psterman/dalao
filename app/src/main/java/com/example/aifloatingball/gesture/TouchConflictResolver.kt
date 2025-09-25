package com.example.aifloatingball.gesture

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.viewpager2.widget.ViewPager2
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * 触摸冲突解决器
 * 专门解决ViewPager2横滑与WebView垂直滑动的冲突问题
 */
class TouchConflictResolver(private val context: Context) {
    
    companion object {
        private const val TAG = "TouchConflictResolver"
        
        // 滑动方向判断阈值
        private const val MIN_DISTANCE = 30f // 最小滑动距离
        private const val ANGLE_THRESHOLD = 30f // 角度阈值（度）
        private const val VELOCITY_THRESHOLD = 500f // 速度阈值
        
        // 触摸状态
        private const val TOUCH_STATE_IDLE = 0
        private const val TOUCH_STATE_HORIZONTAL = 1
        private const val TOUCH_STATE_VERTICAL = 2
        private const val TOUCH_STATE_UNDETERMINED = 3
    }
    
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var touchState = TOUCH_STATE_IDLE
    private var initialX = 0f
    private var initialY = 0f
    private var lastX = 0f
    private var lastY = 0f
    
    /**
     * 滑动方向枚举
     */
    enum class SwipeDirection {
        HORIZONTAL,
        VERTICAL,
        DIAGONAL,
        NONE
    }
    
    /**
     * 触摸事件处理结果
     */
    data class TouchResult(
        val shouldIntercept: Boolean,
        val direction: SwipeDirection,
        val allowWebViewScroll: Boolean
    )
    
    /**
     * 分析触摸事件并返回处理建议
     */
    fun analyzeTouchEvent(event: MotionEvent): TouchResult {
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                return handleActionDown(event)
            }
            MotionEvent.ACTION_MOVE -> {
                return handleActionMove(event)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                return handleActionUp()
            }
        }
        
        return TouchResult(false, SwipeDirection.NONE, true)
    }
    
    private fun handleActionDown(event: MotionEvent): TouchResult {
        initialX = event.x
        initialY = event.y
        lastX = event.x
        lastY = event.y
        touchState = TOUCH_STATE_IDLE
        
        Log.d(TAG, "ACTION_DOWN: x=${event.x}, y=${event.y}")
        
        return TouchResult(false, SwipeDirection.NONE, true)
    }
    
    private fun handleActionMove(event: MotionEvent): TouchResult {
        val deltaX = event.x - initialX
        val deltaY = event.y - initialY
        val distance = sqrt(deltaX * deltaX + deltaY * deltaY)
        
        // 距离太小，继续等待
        if (distance < MIN_DISTANCE) {
            return TouchResult(false, SwipeDirection.NONE, true)
        }
        
        // 如果还没有确定方向，进行判断
        if (touchState == TOUCH_STATE_IDLE || touchState == TOUCH_STATE_UNDETERMINED) {
            val direction = determineSwipeDirection(deltaX, deltaY)
            
            when (direction) {
                SwipeDirection.HORIZONTAL -> {
                    touchState = TOUCH_STATE_HORIZONTAL
                    Log.d(TAG, "检测到水平滑动，拦截事件用于tab切换")
                    return TouchResult(true, SwipeDirection.HORIZONTAL, false)
                }
                SwipeDirection.VERTICAL -> {
                    touchState = TOUCH_STATE_VERTICAL
                    Log.d(TAG, "检测到垂直滑动，允许WebView处理")
                    return TouchResult(false, SwipeDirection.VERTICAL, true)
                }
                SwipeDirection.DIAGONAL -> {
                    touchState = TOUCH_STATE_UNDETERMINED
                    Log.d(TAG, "检测到对角线滑动，优先让WebView处理")
                    return TouchResult(false, SwipeDirection.DIAGONAL, true)
                }
                SwipeDirection.NONE -> {
                    return TouchResult(false, SwipeDirection.NONE, true)
                }
            }
        }
        
        // 已经确定方向，保持当前状态
        return when (touchState) {
            TOUCH_STATE_HORIZONTAL -> TouchResult(true, SwipeDirection.HORIZONTAL, false)
            TOUCH_STATE_VERTICAL -> TouchResult(false, SwipeDirection.VERTICAL, true)
            else -> TouchResult(false, SwipeDirection.NONE, true)
        }
    }
    
    private fun handleActionUp(): TouchResult {
        val result = when (touchState) {
            TOUCH_STATE_HORIZONTAL -> TouchResult(true, SwipeDirection.HORIZONTAL, false)
            TOUCH_STATE_VERTICAL -> TouchResult(false, SwipeDirection.VERTICAL, true)
            else -> TouchResult(false, SwipeDirection.NONE, true)
        }
        
        // 重置状态
        touchState = TOUCH_STATE_IDLE
        Log.d(TAG, "ACTION_UP: 重置触摸状态")
        
        return result
    }
    
    /**
     * 判断滑动方向
     */
    private fun determineSwipeDirection(deltaX: Float, deltaY: Float): SwipeDirection {
        val absX = abs(deltaX)
        val absY = abs(deltaY)
        
        // 计算角度
        val angle = Math.toDegrees(atan2(absY.toDouble(), absX.toDouble())).toFloat()
        
        return when {
            // 主要是水平方向（角度小于30度）
            angle < ANGLE_THRESHOLD -> {
                Log.d(TAG, "水平滑动: angle=$angle, deltaX=$deltaX, deltaY=$deltaY")
                SwipeDirection.HORIZONTAL
            }
            // 主要是垂直方向（角度大于60度）
            angle > (90 - ANGLE_THRESHOLD) -> {
                Log.d(TAG, "垂直滑动: angle=$angle, deltaX=$deltaX, deltaY=$deltaY")
                SwipeDirection.VERTICAL
            }
            // 对角线方向
            else -> {
                Log.d(TAG, "对角线滑动: angle=$angle, deltaX=$deltaX, deltaY=$deltaY")
                SwipeDirection.DIAGONAL
            }
        }
    }
    
    /**
     * 为ViewPager2设置智能触摸处理
     */
    fun setupViewPager2TouchHandling(viewPager: ViewPager2, webViewContainer: View) {
        webViewContainer.setOnTouchListener { _, event ->
            val result = analyzeTouchEvent(event)
            
            when (result.direction) {
                SwipeDirection.HORIZONTAL -> {
                    // 水平滑动：允许ViewPager2处理
                    viewPager.isUserInputEnabled = true
                    webViewContainer.parent?.requestDisallowInterceptTouchEvent(false)
                    Log.d(TAG, "允许ViewPager2处理水平滑动")
                    false // 不消费事件，让ViewPager2处理
                }
                SwipeDirection.VERTICAL -> {
                    // 垂直滑动：禁用ViewPager2，让WebView处理
                    viewPager.isUserInputEnabled = false
                    webViewContainer.parent?.requestDisallowInterceptTouchEvent(true)
                    Log.d(TAG, "禁用ViewPager2，让WebView处理垂直滑动")
                    false // 不消费事件，让WebView处理
                }
                else -> {
                    // 其他情况：优先让WebView处理
                    viewPager.isUserInputEnabled = false
                    webViewContainer.parent?.requestDisallowInterceptTouchEvent(true)
                    false
                }
            }
        }
    }
    
    /**
     * 重置触摸状态
     */
    fun reset() {
        touchState = TOUCH_STATE_IDLE
        Log.d(TAG, "触摸状态已重置")
    }
}
