package com.example.aifloatingball.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.HorizontalScrollView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CustomHorizontalScrollbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scrollbarRect = RectF()
    private var scrollbarWidth = 0f
    private var scrollbarX = 0f
    private var isDragging = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var scrollableWidth = 0
    private var viewportWidth = 0
    private var targetScrollView: HorizontalScrollView? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var hasMovedBeyondSlop = false
    
    init {
        paint.color = 0x80808080.toInt() // 半透明灰色
    }

    fun attachToScrollView(scrollView: HorizontalScrollView) {
        targetScrollView = scrollView
        scrollView.viewTreeObserver.addOnScrollChangedListener {
            if (!isDragging) {
                updateScrollbarPosition()
            }
        }
        scrollView.viewTreeObserver.addOnGlobalLayoutListener {
            updateScrollMetrics()
        }
    }

    private fun updateScrollMetrics() {
        targetScrollView?.let { scrollView ->
            val content = scrollView.getChildAt(0)
            scrollableWidth = content?.width ?: 0
            viewportWidth = scrollView.width
            scrollbarWidth = (viewportWidth.toFloat() / scrollableWidth.toFloat()) * width
            invalidate()
        }
    }

    private fun updateScrollbarPosition() {
        targetScrollView?.let { scrollView ->
            val scrollX = scrollView.scrollX
            val maxScroll = scrollableWidth - viewportWidth
            if (maxScroll > 0) {
                scrollbarX = (scrollX.toFloat() / maxScroll) * (width - scrollbarWidth)
                invalidate()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // 绘制背景轨道
        paint.color = 0x20808080
        canvas.drawRoundRect(
            0f, 
            height * 0.25f,
            width.toFloat(),
            height * 0.75f,
            height * 0.25f,
            height * 0.25f,
            paint
        )
        
        // 绘制滚动条
        paint.color = 0x80808080.toInt()
        scrollbarRect.set(
            scrollbarX,
            0f,
            scrollbarX + scrollbarWidth,
            height.toFloat()
        )
        canvas.drawRoundRect(
            scrollbarRect,
            height * 0.25f,
            height * 0.25f,
            paint
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.x
                initialTouchY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                hasMovedBeyondSlop = false

                // 检查是否点击在滚动条上或附近（增加触摸区域）
                val expandedRect = RectF(
                    scrollbarRect.left - 20f,
                    scrollbarRect.top - 20f,
                    scrollbarRect.right + 20f,
                    scrollbarRect.bottom + 20f
                )
                if (expandedRect.contains(event.x, event.y)) {
                    isDragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (!hasMovedBeyondSlop) {
                    val deltaX = abs(event.x - initialTouchX)
                    val deltaY = abs(event.y - initialTouchY)
                    if (deltaX > touchSlop || deltaY > touchSlop) {
                        hasMovedBeyondSlop = true
                        // 如果水平移动大于垂直移动，且正在拖动滚动条，则拦截事件
                        if (deltaX > deltaY && isDragging) {
                            parent?.requestDisallowInterceptTouchEvent(true)
                        } else {
                            isDragging = false
                            parent?.requestDisallowInterceptTouchEvent(false)
                            return false
                        }
                    }
                }

                if (isDragging) {
                    val deltaX = event.x - lastTouchX
                    lastTouchX = event.x
                    
                    // 更新滚动条位置
                    val newScrollbarX = max(0f, min(width - scrollbarWidth, scrollbarX + deltaX))
                    if (newScrollbarX != scrollbarX) {
                        scrollbarX = newScrollbarX
                        
                        // 计算并设置目标ScrollView的滚动位置
                        targetScrollView?.let { scrollView ->
                            val scrollFraction = scrollbarX / (width - scrollbarWidth)
                            val maxScroll = scrollableWidth - viewportWidth
                            val targetScroll = (maxScroll * scrollFraction).toInt()
                            scrollView.scrollTo(targetScroll, 0)
                        }
                        
                        invalidate()
                    }
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    hasMovedBeyondSlop = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    // 点击滚动条轨道时直接跳转到对应位置
    override fun performClick(): Boolean {
        if (!isDragging) {
            val clickX = lastTouchX
            val targetX = max(0f, min(width - scrollbarWidth, clickX - scrollbarWidth / 2))
            scrollbarX = targetX
            
            targetScrollView?.let { scrollView ->
                val scrollFraction = scrollbarX / (width - scrollbarWidth)
                val maxScroll = scrollableWidth - viewportWidth
                val targetScroll = (maxScroll * scrollFraction).toInt()
                scrollView.smoothScrollTo(targetScroll, 0)
            }
            
            invalidate()
        }
        return super.performClick()
    }
} 