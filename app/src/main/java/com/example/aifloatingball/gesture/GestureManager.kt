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
            
            val distanceX = e2.x - e1.x
            val distanceY = e2.y - e1.y
            val minSwipeDistance = 100 // 最小滑动距离
            
            if (abs(distanceX) > abs(distanceY) && abs(distanceX) > minSwipeDistance) {
                if (distanceX > 0) {
                    callback.onSwipeRight()
                } else {
                    callback.onSwipeLeft()
                }
                return true
            } else if (abs(distanceY) > abs(distanceX) && abs(distanceY) > minSwipeDistance) {
                if (distanceY > 0) {
                    callback.onSwipeDown()
                } else {
                    callback.onSwipeUp()
                }
                return true
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
        view.setOnTouchListener { v, event -> onTouch(v, event) }
    }
    
    interface GestureCallback {
        fun onSingleTap()
        fun onDoubleTap()
        fun onLongPress()
        fun onSwipeLeft()
        fun onSwipeRight()
        fun onSwipeUp()
        fun onSwipeDown()
        fun onDrag(x: Float, y: Float)
        fun onDragEnd(x: Float, y: Float)
    }
} 