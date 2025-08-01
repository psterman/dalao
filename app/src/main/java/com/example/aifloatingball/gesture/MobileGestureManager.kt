package com.example.aifloatingball.gesture

import android.content.Context
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import androidx.core.view.GestureDetectorCompat
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 手机全面屏手势管理器
 * 基于现代手机的手势操作习惯设计
 */
class MobileGestureManager(
    private val context: Context,
    private val targetView: View
) {
    
    companion object {
        private const val TAG = "MobileGestureManager"
        
        // 手势阈值
        private const val SWIPE_THRESHOLD = 100
        private const val SWIPE_VELOCITY_THRESHOLD = 100
        private const val EDGE_SWIPE_THRESHOLD = 50 // 边缘滑动阈值
        private const val PINCH_THRESHOLD = 0.3f // 捏合手势阈值
    }
    
    private val gestureDetector: GestureDetectorCompat
    private val viewConfiguration: ViewConfiguration
    private var velocityTracker: VelocityTracker? = null
    
    // 多点触控相关
    private var initialDistance = 0f
    private var isScaling = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    
    // 边缘检测
    private val screenWidth = context.resources.displayMetrics.widthPixels
    private val screenHeight = context.resources.displayMetrics.heightPixels
    
    // 手势监听器
    private var gestureListener: MobileGestureListener? = null
    
    /**
     * 手势监听器接口
     */
    interface MobileGestureListener {
        // 基础手势
        fun onSingleTap(x: Float, y: Float)
        fun onDoubleTap(x: Float, y: Float)
        fun onLongPress(x: Float, y: Float)
        
        // 滑动手势
        fun onSwipeLeft()
        fun onSwipeRight() 
        fun onSwipeUp()
        fun onSwipeDown()
        
        // 边缘手势（类似手机系统手势）
        fun onEdgeSwipeLeft() // 返回手势
        fun onEdgeSwipeRight() // 前进手势
        fun onEdgeSwipeUp() // 显示概览
        fun onEdgeSwipeDown() // 下拉刷新
        
        // 多点触控手势
        fun onPinchIn(scaleFactor: Float) // 捏合缩小
        fun onPinchOut(scaleFactor: Float) // 捏合放大
        fun onTwoFingerSwipeUp() // 双指上滑
        fun onTwoFingerSwipeDown() // 双指下滑
        
        // 复合手势
        fun onThreeFingerSwipeUp() // 三指上滑（多任务）
        fun onThreeFingerSwipeDown() // 三指下滑（关闭）
    }
    
    init {
        viewConfiguration = ViewConfiguration.get(context)
        
        gestureDetector = GestureDetectorCompat(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                gestureListener?.onSingleTap(e.x, e.y)
                return true
            }
            
            override fun onDoubleTap(e: MotionEvent): Boolean {
                gestureListener?.onDoubleTap(e.x, e.y)
                return true
            }
            
            override fun onLongPress(e: MotionEvent) {
                gestureListener?.onLongPress(e.x, e.y)
            }
            
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (e1 == null) return false
                
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                
                return if (abs(diffX) > abs(diffY)) {
                    // 水平滑动
                    if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            // 检查是否是边缘手势
                            if (e1.x < EDGE_SWIPE_THRESHOLD) {
                                gestureListener?.onEdgeSwipeRight()
                            } else {
                                gestureListener?.onSwipeRight()
                            }
                        } else {
                            if (e1.x > screenWidth - EDGE_SWIPE_THRESHOLD) {
                                gestureListener?.onEdgeSwipeLeft()
                            } else {
                                gestureListener?.onSwipeLeft()
                            }
                        }
                        true
                    } else false
                } else {
                    // 垂直滑动
                    if (abs(diffY) > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffY > 0) {
                            if (e1.y < EDGE_SWIPE_THRESHOLD) {
                                gestureListener?.onEdgeSwipeDown()
                            } else {
                                gestureListener?.onSwipeDown()
                            }
                        } else {
                            if (e1.y > screenHeight - EDGE_SWIPE_THRESHOLD) {
                                gestureListener?.onEdgeSwipeUp()
                            } else {
                                gestureListener?.onSwipeUp()
                            }
                        }
                        true
                    } else false
                }
            }
        })
        
        // 设置触摸监听器
        targetView.setOnTouchListener { _, event ->
            handleTouchEvent(event)
        }
    }
    
    /**
     * 处理触摸事件
     */
    private fun handleTouchEvent(event: MotionEvent): Boolean {
        // 处理速度追踪
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(event)
        
        // 处理多点触控
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isScaling = false
            }
            
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (event.pointerCount == 2) {
                    initialDistance = getDistance(event)
                    isScaling = true
                } else if (event.pointerCount == 3) {
                    // 三指手势检测
                    handleThreeFingerGesture(event)
                }
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (isScaling && event.pointerCount == 2) {
                    val currentDistance = getDistance(event)
                    val scaleFactor = currentDistance / initialDistance
                    
                    if (scaleFactor > 1 + PINCH_THRESHOLD) {
                        gestureListener?.onPinchOut(scaleFactor)
                    } else if (scaleFactor < 1 - PINCH_THRESHOLD) {
                        gestureListener?.onPinchIn(scaleFactor)
                    }
                } else if (event.pointerCount == 2) {
                    // 双指滑动检测
                    handleTwoFingerSwipe(event)
                }
            }
            
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (event.pointerCount <= 1) {
                    isScaling = false
                    velocityTracker?.recycle()
                    velocityTracker = null
                }
            }
        }
        
        // 传递给手势检测器
        return gestureDetector.onTouchEvent(event)
    }
    
    /**
     * 计算两点间距离
     */
    private fun getDistance(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return sqrt(dx * dx + dy * dy)
    }
    
    /**
     * 处理双指滑动
     */
    private fun handleTwoFingerSwipe(event: MotionEvent) {
        if (event.pointerCount != 2) return
        
        val centerY = (event.getY(0) + event.getY(1)) / 2
        val diffY = centerY - lastTouchY
        
        if (abs(diffY) > SWIPE_THRESHOLD) {
            if (diffY > 0) {
                gestureListener?.onTwoFingerSwipeDown()
            } else {
                gestureListener?.onTwoFingerSwipeUp()
            }
        }
    }
    
    /**
     * 处理三指手势
     */
    private fun handleThreeFingerGesture(event: MotionEvent) {
        if (event.pointerCount != 3) return
        
        val centerY = (event.getY(0) + event.getY(1) + event.getY(2)) / 3
        val diffY = centerY - lastTouchY
        
        if (abs(diffY) > SWIPE_THRESHOLD) {
            if (diffY > 0) {
                gestureListener?.onThreeFingerSwipeDown()
            } else {
                gestureListener?.onThreeFingerSwipeUp()
            }
        }
    }
    
    /**
     * 设置手势监听器
     */
    fun setGestureListener(listener: MobileGestureListener) {
        this.gestureListener = listener
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        velocityTracker?.recycle()
        velocityTracker = null
    }
}
