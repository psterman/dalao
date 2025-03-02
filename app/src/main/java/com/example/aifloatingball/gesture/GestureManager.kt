package com.example.aifloatingball.gesture

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.VelocityTracker
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

class GestureManager(context: Context, private val callback: GestureCallback) {
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var initialX = 0
    private var initialY = 0
    private var isDragging = false
    private var isLongPressed = false
    private var velocityTracker: VelocityTracker? = null
    private var lastUpdateTime = 0L
    
    interface GestureCallback {
        fun onGestureDetected(gesture: Gesture)
        fun onDoubleTap()
        fun onSingleTap()
        fun onLongPress()
        fun onSwipeLeft()
        fun onSwipeRight()
        fun onSwipeUp()
        fun onSwipeDown()
        fun onDrag(x: Float, y: Float)
        fun onDragEnd(x: Float, y: Float, velocityX: Float = 0f, velocityY: Float = 0f)
    }
    
    enum class Gesture {
        SWIPE_UP,
        SWIPE_DOWN,
        LONG_PRESS
    }
    
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (!isDragging) {
                callback.onSingleTap()
                return true
            }
            return false
        }
        
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (!isDragging) {
                callback.onDoubleTap()
                return true
            }
            return false
        }
        
        override fun onLongPress(e: MotionEvent) {
            if (!isDragging) {
                isLongPressed = true
                callback.onLongPress()
            }
        }
        
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (e1 == null || isDragging || isLongPressed) return false
            
            val diffY = e2.y - e1.y
            val diffX = e2.x - e1.x
            
            if (Math.abs(diffX) < Math.abs(diffY)) {
                if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffY > 0) {
                        callback.onGestureDetected(Gesture.SWIPE_DOWN)
                    } else {
                        callback.onGestureDetected(Gesture.SWIPE_UP)
                    }
                    return true
                }
            }
            return false
        }
    })
    
    companion object {
        private const val SWIPE_THRESHOLD = 100
        private const val SWIPE_VELOCITY_THRESHOLD = 100
        private const val DRAG_THRESHOLD = 8f // dp
        private const val DRAG_UPDATE_INTERVAL = 16L // ms, 约60fps
    }
    
    private val dragThresholdPx = DRAG_THRESHOLD * context.resources.displayMetrics.density
    
    fun onTouch(view: View, event: MotionEvent): Boolean {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(event)
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.rawX
                lastTouchY = event.rawY
                initialX = view.x.toInt()
                initialY = view.y.toInt()
                isDragging = false
                isLongPressed = false
                lastUpdateTime = System.currentTimeMillis()
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - lastTouchX
                val deltaY = event.rawY - lastTouchY
                
                if (!isDragging && !isLongPressed) {
                    val distance = sqrt(deltaX * deltaX + deltaY * deltaY)
                    if (distance > dragThresholdPx) {
                        isDragging = true
                    }
                }
                
                if (isDragging) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastUpdateTime >= DRAG_UPDATE_INTERVAL) {
                        velocityTracker?.computeCurrentVelocity(1000)
                        val velocityX = velocityTracker?.xVelocity ?: 0f
                        val velocityY = velocityTracker?.yVelocity ?: 0f
                        
                        val dampingFactor = 1.0f - min(
                            sqrt(velocityX * velocityX + velocityY * velocityY) / 3000f,
                            0.5f
                        )
                        
                        val newX = initialX + deltaX * dampingFactor
                        val newY = initialY + deltaY * dampingFactor
                        
                        callback.onDrag(newX, newY)
                        lastUpdateTime = currentTime
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    velocityTracker?.computeCurrentVelocity(1000)
                    val velocityX = velocityTracker?.xVelocity ?: 0f
                    val velocityY = velocityTracker?.yVelocity ?: 0f
                    callback.onDragEnd(event.rawX, event.rawY, velocityX, velocityY)
                }
                isDragging = false
                isLongPressed = false
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }
        
        return gestureDetector.onTouchEvent(event)
    }
    
    fun attachToView(view: View) {
        view.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
        }
    }
} 