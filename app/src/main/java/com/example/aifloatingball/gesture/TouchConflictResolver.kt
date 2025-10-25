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
        
        // 滑动方向判断阈值 - 优化参数，让水平滑动更容易被识别
        private const val MIN_DISTANCE = 60f // 降低最小滑动距离
        private const val ANGLE_THRESHOLD = 20f // 增加角度阈值，让水平滑动更容易被识别
        private const val VELOCITY_THRESHOLD = 800f // 降低速度阈值
        private const val HORIZONTAL_RATIO_THRESHOLD = 2.0f // 降低水平滑动比例阈值
        private const val MIN_HORIZONTAL_DISTANCE = 80f // 降低水平滑动最小距离
        private const val VERTICAL_BIAS = 1.1f // 降低垂直滑动偏向系数
        
        // 两指横滑检测参数
        private const val TWO_FINGER_MIN_DISTANCE = 60f // 两指横滑最小距离
        private const val TWO_FINGER_ANGLE_THRESHOLD = 20f // 两指横滑角度阈值
        private const val TWO_FINGER_HORIZONTAL_RATIO = 2.0f // 两指横滑水平比例阈值
        
        // 触摸状态
        private const val TOUCH_STATE_IDLE = 0
        private const val TOUCH_STATE_HORIZONTAL = 1
        private const val TOUCH_STATE_VERTICAL = 2
        private const val TOUCH_STATE_UNDETERMINED = 3
        private const val TOUCH_STATE_TWO_FINGER_HORIZONTAL = 4
    }
    
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var touchState = TOUCH_STATE_IDLE
    private var initialX = 0f
    private var initialY = 0f
    private var lastX = 0f
    private var lastY = 0f
    
    // 两指横滑检测变量
    private var initialX1 = 0f
    private var initialY1 = 0f
    private var initialX2 = 0f
    private var initialY2 = 0f
    private var lastX1 = 0f
    private var lastY1 = 0f
    private var lastX2 = 0f
    private var lastY2 = 0f
    private var isTwoFingerMode = false
    
    /**
     * 滑动方向枚举
     */
    enum class SwipeDirection {
        HORIZONTAL,
        VERTICAL,
        DIAGONAL,
        TWO_FINGER_HORIZONTAL, // 两指横滑
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
            MotionEvent.ACTION_POINTER_DOWN -> {
                return handlePointerDown(event)
            }
            MotionEvent.ACTION_MOVE -> {
                return handleActionMove(event)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                return handlePointerUp(event)
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
        isTwoFingerMode = false
        
        Log.d(TAG, "ACTION_DOWN: x=${event.x}, y=${event.y}")
        
        return TouchResult(false, SwipeDirection.NONE, true)
    }
    
    private fun handlePointerDown(event: MotionEvent): TouchResult {
        if (event.pointerCount == 2) {
            // 两指按下，记录初始位置
            initialX1 = event.getX(0)
            initialY1 = event.getY(0)
            initialX2 = event.getX(1)
            initialY2 = event.getY(1)
            lastX1 = initialX1
            lastY1 = initialY1
            lastX2 = initialX2
            lastY2 = initialY2
            isTwoFingerMode = true
            touchState = TOUCH_STATE_UNDETERMINED
            
            Log.d(TAG, "ACTION_POINTER_DOWN: 两指按下 - 指1:($initialX1, $initialY1), 指2:($initialX2, $initialY2)")
        }
        
        return TouchResult(false, SwipeDirection.NONE, true)
    }
    
    private fun handlePointerUp(event: MotionEvent): TouchResult {
        if (event.pointerCount < 2) {
            // 不再是两指操作
            isTwoFingerMode = false
            touchState = TOUCH_STATE_IDLE
            Log.d(TAG, "ACTION_POINTER_UP: 结束两指模式")
        }
        
        return TouchResult(false, SwipeDirection.NONE, true)
    }
    
    private fun handleActionMove(event: MotionEvent): TouchResult {
        // 如果是两指模式，检测两指横滑
        if (isTwoFingerMode && event.pointerCount == 2) {
            return handleTwoFingerMove(event)
        }
        
        // 单指模式，优化横滑检测，允许明显的水平滑动
        val deltaX = event.x - initialX
        val deltaY = event.y - initialY
        val distance = sqrt(deltaX * deltaX + deltaY * deltaY)
        
        // 距离太小，继续等待
        if (distance < MIN_DISTANCE) {
            return TouchResult(false, SwipeDirection.NONE, true)
        }
        
        // 单指模式优化：允许明显的水平滑动，但优先垂直滑动
        if (touchState == TOUCH_STATE_IDLE || touchState == TOUCH_STATE_UNDETERMINED) {
            val direction = determineSwipeDirection(deltaX, deltaY)
            
            when (direction) {
                SwipeDirection.HORIZONTAL -> {
                    // 允许水平滑动，但需要更严格的条件
                    touchState = TOUCH_STATE_HORIZONTAL
                    Log.d(TAG, "检测到水平滑动，允许ViewPager2处理")
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
                else -> {
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
    
    /**
     * 处理两指移动事件
     */
    private fun handleTwoFingerMove(event: MotionEvent): TouchResult {
        if (event.pointerCount < 2) {
            return TouchResult(false, SwipeDirection.NONE, true)
        }
        
        val currentX1 = event.getX(0)
        val currentY1 = event.getY(0)
        val currentX2 = event.getX(1)
        val currentY2 = event.getY(1)
        
        // 计算两指的平均移动距离
        val deltaX1 = currentX1 - initialX1
        val deltaY1 = currentY1 - initialY1
        val deltaX2 = currentX2 - initialX2
        val deltaY2 = currentY2 - initialY2
        
        val avgDeltaX = (deltaX1 + deltaX2) / 2
        val avgDeltaY = (deltaY1 + deltaY2) / 2
        val distance = sqrt(avgDeltaX * avgDeltaX + avgDeltaY * avgDeltaY)
        
        // 距离太小，继续等待
        if (distance < TWO_FINGER_MIN_DISTANCE) {
            return TouchResult(false, SwipeDirection.NONE, true)
        }
        
        // 如果还没有确定方向，进行判断
        if (touchState == TOUCH_STATE_UNDETERMINED) {
            val direction = determineTwoFingerSwipeDirection(avgDeltaX, avgDeltaY)
            
            when (direction) {
                SwipeDirection.TWO_FINGER_HORIZONTAL -> {
                    touchState = TOUCH_STATE_TWO_FINGER_HORIZONTAL
                    Log.d(TAG, "检测到两指横滑，允许WebView切换")
                    return TouchResult(true, SwipeDirection.TWO_FINGER_HORIZONTAL, false)
                }
                SwipeDirection.VERTICAL -> {
                    touchState = TOUCH_STATE_VERTICAL
                    Log.d(TAG, "检测到两指垂直滑动，允许WebView处理")
                    return TouchResult(false, SwipeDirection.VERTICAL, true)
                }
                else -> {
                    Log.d(TAG, "两指滑动方向未确定，继续等待")
                    return TouchResult(false, SwipeDirection.NONE, true)
                }
            }
        }
        
        // 已经确定方向，保持当前状态
        return when (touchState) {
            TOUCH_STATE_TWO_FINGER_HORIZONTAL -> TouchResult(true, SwipeDirection.TWO_FINGER_HORIZONTAL, false)
            TOUCH_STATE_VERTICAL -> TouchResult(false, SwipeDirection.VERTICAL, true)
            else -> TouchResult(false, SwipeDirection.NONE, true)
        }
    }
    
    private fun handleActionUp(): TouchResult {
        val result = when (touchState) {
            TOUCH_STATE_HORIZONTAL -> TouchResult(true, SwipeDirection.HORIZONTAL, false)
            TOUCH_STATE_VERTICAL -> TouchResult(false, SwipeDirection.VERTICAL, true)
            TOUCH_STATE_TWO_FINGER_HORIZONTAL -> TouchResult(true, SwipeDirection.TWO_FINGER_HORIZONTAL, false)
            else -> TouchResult(false, SwipeDirection.NONE, true)
        }
        
        // 重置状态
        touchState = TOUCH_STATE_IDLE
        isTwoFingerMode = false
        Log.d(TAG, "ACTION_UP: 重置触摸状态")
        
        return result
    }
    
    /**
     * 判断滑动方向 - 进一步优化算法，严格防止垂直滑动误触发
     */
    private fun determineSwipeDirection(deltaX: Float, deltaY: Float): SwipeDirection {
        val absX = abs(deltaX)
        val absY = abs(deltaY)
        
        // 计算角度
        val angle = Math.toDegrees(atan2(absY.toDouble(), absX.toDouble())).toFloat()
        
        // 计算水平/垂直比例
        val horizontalRatio = if (absY > 0) absX / absY else Float.MAX_VALUE
        val verticalRatio = if (absX > 0) absY / absX else Float.MAX_VALUE
        
        Log.d(TAG, "手势分析: angle=$angle, deltaX=$deltaX, deltaY=$deltaY, hRatio=$horizontalRatio, vRatio=$verticalRatio")
        
        return when {
            // 优化条件：让水平滑动更容易被识别
            // 1. 角度小于20度 且 2. 水平比例大于2倍 且 3. 水平距离大于80px
            angle < ANGLE_THRESHOLD && 
            horizontalRatio > HORIZONTAL_RATIO_THRESHOLD && 
            absX > MIN_HORIZONTAL_DISTANCE -> {
                Log.d(TAG, "确认为水平滑动: angle=$angle, hRatio=$horizontalRatio, absX=$absX")
                SwipeDirection.HORIZONTAL
            }
            // 垂直滑动：更宽松的条件，优先识别垂直滑动
            // 1. 角度大于70度 或 2. 垂直距离明显大于水平距离
            angle > (90 - ANGLE_THRESHOLD) || absY > absX * VERTICAL_BIAS -> {
                Log.d(TAG, "确认为垂直滑动: angle=$angle, vRatio=$verticalRatio, absY=$absY")
                SwipeDirection.VERTICAL
            }
            // 对角线滑动：中等角度，优先让WebView处理
            else -> {
                Log.d(TAG, "对角线滑动，优先WebView: angle=$angle")
                SwipeDirection.DIAGONAL
            }
        }
    }
    
    /**
     * 判断两指滑动方向
     */
    private fun determineTwoFingerSwipeDirection(deltaX: Float, deltaY: Float): SwipeDirection {
        val absX = abs(deltaX)
        val absY = abs(deltaY)
        
        // 计算角度
        val angle = Math.toDegrees(atan2(absY.toDouble(), absX.toDouble())).toFloat()
        
        // 计算水平/垂直比例
        val horizontalRatio = if (absY > 0) absX / absY else Float.MAX_VALUE
        val verticalRatio = if (absX > 0) absY / absX else Float.MAX_VALUE
        
        Log.d(TAG, "两指手势分析: angle=$angle, deltaX=$deltaX, deltaY=$deltaY, hRatio=$horizontalRatio, vRatio=$verticalRatio")
        
        return when {
            // 两指横滑：相对宽松的条件
            // 1. 角度小于20度 且 2. 水平比例大于2倍 且 3. 水平距离大于60px
            angle < TWO_FINGER_ANGLE_THRESHOLD && 
            horizontalRatio > TWO_FINGER_HORIZONTAL_RATIO && 
            absX > TWO_FINGER_MIN_DISTANCE -> {
                Log.d(TAG, "确认为两指横滑: angle=$angle, hRatio=$horizontalRatio, absX=$absX")
                SwipeDirection.TWO_FINGER_HORIZONTAL
            }
            // 两指垂直滑动：更宽松的条件
            // 1. 角度大于70度 或 2. 垂直距离明显大于水平距离
            angle > (90 - TWO_FINGER_ANGLE_THRESHOLD) || absY > absX * 1.5f -> {
                Log.d(TAG, "确认为两指垂直滑动: angle=$angle, vRatio=$verticalRatio, absY=$absY")
                SwipeDirection.VERTICAL
            }
            // 两指对角线滑动：中等角度，优先让WebView处理
            else -> {
                Log.d(TAG, "两指对角线滑动，优先WebView: angle=$angle")
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
     * 获取初始X坐标
     */
    fun getInitialX(): Float = initialX
    
    /**
     * 获取初始Y坐标
     */
    fun getInitialY(): Float = initialY
    
    /**
     * 重置触摸状态
     */
    fun reset() {
        touchState = TOUCH_STATE_IDLE
        isTwoFingerMode = false
        Log.d(TAG, "触摸状态已重置")
    }
}
