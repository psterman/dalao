package com.example.aifloatingball.webview

import android.content.Context
import android.util.Log
import android.view.MotionEvent
import android.webkit.WebView
import androidx.viewpager2.widget.ViewPager2
import com.example.aifloatingball.gesture.TouchConflictResolver
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 增强的WebView触摸处理器
 * 解决WebView与ViewPager2的滑动冲突问题
 */
class EnhancedWebViewTouchHandler(
    private val context: Context,
    private val webView: WebView,
    private val viewPager: ViewPager2? = null,
    private val onTouchCoordinatesUpdated: ((Float, Float) -> Unit)? = null
) {
    
    companion object {
        private const val TAG = "EnhancedWebViewTouchHandler"
        
        // 缩放检测阈值
        private const val ZOOM_THRESHOLD = 50f
        private const val MULTI_TOUCH_TIMEOUT = 300L
    }
    
    private val touchConflictResolver = TouchConflictResolver(context)
    private var isZooming = false
    private var pointerCount = 0
    private var initialDistance = 0f
    private var lastTouchTime = 0L
    
    /**
     * 设置WebView的触摸处理
     */
    fun setupWebViewTouchHandling() {
        webView.setOnTouchListener { view, event ->
            handleWebViewTouch(view, event)
        }
    }
    
    /**
     * 处理WebView的触摸事件
     */
    private fun handleWebViewTouch(view: android.view.View, event: MotionEvent): Boolean {
        val currentTime = System.currentTimeMillis()
        
        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                return handleActionDown(view, event, currentTime)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                return handlePointerDown(view, event)
            }
            MotionEvent.ACTION_MOVE -> {
                return handleActionMove(view, event)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                return handlePointerUp(view, event)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                return handleActionUp(view, event)
            }
        }
        
        return false
    }
    
    private fun handleActionDown(view: android.view.View, event: MotionEvent, currentTime: Long): Boolean {
        pointerCount = 1
        isZooming = false
        lastTouchTime = currentTime

        // 通知触摸坐标更新
        onTouchCoordinatesUpdated?.invoke(event.x, event.y)

        // 重置冲突解决器
        touchConflictResolver.reset()

        Log.d(TAG, "单指按下: x=${event.x}, y=${event.y}")

        // 分析触摸事件
        val result = touchConflictResolver.analyzeTouchEvent(event)

        // 对于单指按下，我们不拦截事件，让WebView正常处理
        // 这样可以确保长按事件能够正常触发
        view.parent?.requestDisallowInterceptTouchEvent(false)

        // 单指按下时不消费事件，让WebView处理长按等事件
        return false
    }
    
    private fun handlePointerDown(view: android.view.View, event: MotionEvent): Boolean {
        pointerCount = event.pointerCount
        
        if (pointerCount == 2) {
            // 双指按下，准备缩放
            isZooming = true
            initialDistance = getDistance(event)
            Log.d(TAG, "双指按下，开始缩放模式: distance=$initialDistance")
            
            // 禁用ViewPager的触摸事件，避免与缩放冲突
            viewPager?.isUserInputEnabled = false
            view.parent?.requestDisallowInterceptTouchEvent(true)
            
            return true // 消费事件，专注处理缩放
        }
        
        return false
    }
    
    private fun handleActionMove(view: android.view.View, event: MotionEvent): Boolean {
        if (pointerCount >= 2 && isZooming) {
            // 双指移动，处理缩放
            val currentDistance = getDistance(event)
            val deltaDistance = abs(currentDistance - initialDistance)
            
            if (deltaDistance > ZOOM_THRESHOLD) {
                // 确认是缩放操作，继续禁用ViewPager
                viewPager?.isUserInputEnabled = false
                view.parent?.requestDisallowInterceptTouchEvent(true)
                Log.d(TAG, "双指缩放中，距离变化: $deltaDistance")
                return false // 让WebView处理缩放
            }
        } else if (pointerCount == 1 && !isZooming) {
            // 单指移动，分析滑动方向
            val result = touchConflictResolver.analyzeTouchEvent(event)
            
            // 额外的保护机制：检查滑动距离和方向的一致性
            val deltaX = event.x - (touchConflictResolver.getInitialX() ?: event.x)
            val deltaY = event.y - (touchConflictResolver.getInitialY() ?: event.y)
            val absX = abs(deltaX)
            val absY = abs(deltaY)
            
            when (result.direction) {
                TouchConflictResolver.SwipeDirection.TWO_FINGER_HORIZONTAL -> {
                    // 两指横滑，允许WebView切换
                    if (viewPager?.scrollState == ViewPager2.SCROLL_STATE_IDLE) {
                        viewPager?.isUserInputEnabled = true
                        view.parent?.requestDisallowInterceptTouchEvent(false)
                        Log.d(TAG, "确认两指横滑，允许ViewPager处理: deltaX=$deltaX, deltaY=$deltaY")
                    } else {
                        Log.d(TAG, "ViewPager正在滚动中，保持当前状态")
                    }
                    return false // 不消费，让ViewPager处理
                }
                TouchConflictResolver.SwipeDirection.HORIZONTAL -> {
                    // 单指水平滑动被禁用，按垂直滑动处理
                    viewPager?.isUserInputEnabled = false
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    Log.d(TAG, "单指水平滑动被禁用，按垂直滑动处理: deltaX=$deltaX, deltaY=$deltaY")
                    return false
                }
                TouchConflictResolver.SwipeDirection.VERTICAL -> {
                    // 垂直滑动，WebView页面滚动
                    viewPager?.isUserInputEnabled = false
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    Log.d(TAG, "检测到垂直滑动，让WebView处理: deltaX=$deltaX, deltaY=$deltaY")
                    return false // 不消费，让WebView处理
                }
                TouchConflictResolver.SwipeDirection.DIAGONAL -> {
                    // 对角线滑动，优先让WebView处理
                    viewPager?.isUserInputEnabled = false
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    Log.d(TAG, "检测到对角线滑动，优先让WebView处理: deltaX=$deltaX, deltaY=$deltaY")
                    return false
                }
                else -> {
                    // 未确定方向，优先让WebView处理
                    viewPager?.isUserInputEnabled = false
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    Log.d(TAG, "未确定方向，优先让WebView处理: deltaX=$deltaX, deltaY=$deltaY")
                    return false
                }
            }
        }
        
        return false
    }
    
    private fun handlePointerUp(view: android.view.View, event: MotionEvent): Boolean {
        pointerCount = event.pointerCount - 1
        
        if (pointerCount < 2) {
            // 不再是双指操作，重新允许ViewPager
            isZooming = false
            viewPager?.isUserInputEnabled = true
            view.parent?.requestDisallowInterceptTouchEvent(false)
            Log.d(TAG, "结束缩放模式")
        }
        
        return false
    }
    
    private fun handleActionUp(view: android.view.View, event: MotionEvent): Boolean {
        pointerCount = 0
        isZooming = false
        
        // 恢复ViewPager的正常状态
        viewPager?.isUserInputEnabled = true
        view.parent?.requestDisallowInterceptTouchEvent(false)
        
        // 分析最终的触摸结果
        val result = touchConflictResolver.analyzeTouchEvent(event)
        Log.d(TAG, "触摸结束: direction=${result.direction}, shouldIntercept=${result.shouldIntercept}")
        
        return false
    }
    
    /**
     * 计算两个触摸点之间的距离
     */
    private fun getDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        
        val x = event.getX(0) - event.getX(1)
        val y = event.getY(0) - event.getY(1)
        return sqrt(x * x + y * y)
    }
    
    /**
     * 检查是否是快速双击
     */
    private fun isDoubleTap(currentTime: Long): Boolean {
        val timeDiff = currentTime - lastTouchTime
        return timeDiff < MULTI_TOUCH_TIMEOUT
    }
    
    /**
     * 强制重置所有状态
     */
    fun reset() {
        pointerCount = 0
        isZooming = false
        touchConflictResolver.reset()
        
        // 恢复ViewPager状态
        viewPager?.isUserInputEnabled = true
        webView.parent?.requestDisallowInterceptTouchEvent(false)
        
        Log.d(TAG, "WebView触摸处理器已重置")
    }
    
    /**
     * 获取当前触摸状态信息
     */
    fun getTouchState(): String {
        return "pointerCount=$pointerCount, isZooming=$isZooming"
    }
}
