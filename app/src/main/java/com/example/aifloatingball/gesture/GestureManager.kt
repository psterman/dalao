package com.example.aifloatingball.gesture

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs

class GestureManager(context: Context, private val callback: GestureCallback) {
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var initialX = 0
    private var initialY = 0
    private var isDragging = false
    private var isLongPressed = false
    
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
        fun onDragEnd(x: Float, y: Float)
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
    
    fun onTouch(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.rawX
                lastTouchY = event.rawY
                initialX = view.x.toInt()
                initialY = view.y.toInt()
                isDragging = false
                isLongPressed = false
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - lastTouchX
                val deltaY = event.rawY - lastTouchY
                
                if (!isDragging && !isLongPressed && (abs(deltaX) > 10 || abs(deltaY) > 10)) {
                    isDragging = true
                }
                
                if (isDragging) {
                    callback.onDrag(initialX + deltaX, initialY + deltaY)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    callback.onDragEnd(event.rawX, event.rawY)
                }
                isDragging = false
                isLongPressed = false
            }
            MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                isLongPressed = false
            }
        }
        
        return gestureDetector.onTouchEvent(event)
    }
    
    fun attachToView(view: View) {
        view.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
        }
    }
    
    companion object {
        private const val SWIPE_THRESHOLD = 100
        private const val SWIPE_VELOCITY_THRESHOLD = 100
    }
} 