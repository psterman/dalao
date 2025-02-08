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
            callback.onSingleTap()
            return true
        }
        
        override fun onDoubleTap(e: MotionEvent): Boolean {
            callback.onDoubleTap()
            return true
        }
        
        override fun onLongPress(e: MotionEvent) {
            isLongPressed = true
            callback.onLongPress()
        }
        
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (e1 == null) return false
            
            val distanceX = e2.x - e1.x
            val distanceY = e2.y - e1.y
            
            if (abs(distanceX) > abs(distanceY)) {
                if (distanceX > 0) callback.onSwipeRight()
                else callback.onSwipeLeft()
            } else {
                if (distanceY > 0) callback.onSwipeDown()
                else callback.onSwipeUp()
            }
            return true
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
                    view.x = initialX + deltaX
                    view.y = initialY + deltaY
                    callback.onDrag(view.x, view.y)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    callback.onDragEnd(view.x, view.y)
                }
                isDragging = false
                isLongPressed = false
            }
        }
        
        return if (!isDragging) {
            gestureDetector.onTouchEvent(event)
        } else true
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