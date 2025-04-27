package com.example.aifloatingball.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.example.aifloatingball.R
import kotlin.math.roundToInt

class TextSelectionHandleView(
    context: Context,
    private val isLeft: Boolean,
    private val windowManager: WindowManager,
    private val onHandleMoved: (Float, Float) -> Unit,
    private val onHandleReleased: () -> Unit
) : View(context) {

    companion object {
        private const val TAG = "TextSelectionHandleView"
    }

    private val handleDrawable: Drawable = context.getDrawable(
        if (isLeft) R.drawable.text_select_handle_left
        else R.drawable.text_select_handle_right
    )!!

    // 添加可见的边框和突出的颜色
    private val borderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    
    private val fillPaint = Paint().apply {
        color = Color.BLUE
        alpha = 180
        style = Paint.Style.FILL
    }

    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f
    private var isDragging: Boolean = false
    
    // 增大触碰区域
    private val touchAreaExpansion = 20

    init {
        handleDrawable.callback = this
        
        // 设置更明显的颜色
        handleDrawable.colorFilter = PorterDuffColorFilter(
            Color.parseColor("#2196F3"),  // 使用蓝色
            PorterDuff.Mode.SRC_IN
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 增大可见尺寸
        val minWidth = handleDrawable.intrinsicWidth.coerceAtLeast(50)
        val minHeight = handleDrawable.intrinsicHeight.coerceAtLeast(50)
        setMeasuredDimension(minWidth, minHeight)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        handleDrawable.setBounds(0, 0, width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // 绘制填充背景
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), fillPaint)
        
        // 绘制图标
        handleDrawable.draw(canvas)
        
        // 绘制边框，增强可见性
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), borderPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 扩大触摸区域
        val expandedBounds = computeExpandedBounds(event)
        val isInExpandedBounds = expandedBounds.contains(event.x.toInt(), event.y.toInt())
        
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isInExpandedBounds) {
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    isDragging = true
                    // 提高选择柄的层级
                    bringToFront()
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val deltaX = event.rawX - lastTouchX
                    val deltaY = event.rawY - lastTouchY
                    
                    // 通知移动，更新文本选择
                    onHandleMoved(deltaX, deltaY)
                    
                    lastTouchX = event.rawX
                    lastTouchY = event.rawY
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    onHandleReleased()
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }
    
    // 计算扩大的触摸区域
    private fun computeExpandedBounds(event: MotionEvent): android.graphics.Rect {
        return android.graphics.Rect(
            -touchAreaExpansion,
            -touchAreaExpansion,
            width + touchAreaExpansion,
            height + touchAreaExpansion
        )
    }

    fun updatePosition(x: Int, y: Int) {
        val params = layoutParams as WindowManager.LayoutParams
        params.x = x
        params.y = y
        try {
            windowManager.updateViewLayout(this, params)
            Log.d(TAG, "选择柄位置已更新: ($x, $y) - ${if(isLeft) "左" else "右"}")
        } catch (e: Exception) {
            Log.e(TAG, "更新选择柄位置失败", e)
        }
    }

    fun getHandlePosition(): Point {
        val params = layoutParams as WindowManager.LayoutParams
        return Point(params.x, params.y)
    }
} 