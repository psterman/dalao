package com.example.aifloatingball.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.example.aifloatingball.R

/**
 * iOS风格的SegmentedControl组件
 * 类似iOS的UISegmentedControl，支持多个选项的切换
 */
class IOSSegmentedControl @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /**
     * 选项数据类
     */
    data class Segment(
        val title: String,
        val value: Any
    )

    /**
     * 选择监听器
     */
    interface OnSegmentSelectedListener {
        fun onSegmentSelected(index: Int, segment: Segment)
    }

    private var segments: List<Segment> = emptyList()
    private var selectedIndex: Int = 0
    private var listener: OnSegmentSelectedListener? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var segmentWidth: Float = 0f
    private var cornerRadius: Float = 8f.dp
    private var strokeWidth: Float = 1f.dp

    // 颜色
    private var backgroundColor: Int = 0
    private var selectedBackgroundColor: Int = 0
    private var textColor: Int = 0
    private var selectedTextColor: Int = 0
    private var borderColor: Int = 0

    init {
        // 初始化颜色
        backgroundColor = ContextCompat.getColor(context, R.color.simple_mode_card_background_light)
        selectedBackgroundColor = ContextCompat.getColor(context, R.color.simple_mode_accent_light)
        textColor = ContextCompat.getColor(context, R.color.simple_mode_text_secondary_light)
        selectedTextColor = ContextCompat.getColor(context, android.R.color.white)
        borderColor = ContextCompat.getColor(context, R.color.simple_mode_divider_light)

        // 设置文本画笔
        textPaint.textSize = 14f.sp
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.color = textColor

        selectedTextPaint.textSize = 14f.sp
        selectedTextPaint.textAlign = Paint.Align.CENTER
        selectedTextPaint.color = selectedTextColor
        selectedTextPaint.typeface = android.graphics.Typeface.DEFAULT_BOLD

        // 设置背景画笔
        paint.style = Paint.Style.FILL
        paint.color = backgroundColor

        // 设置最小高度
        minimumHeight = 36.dp
    }

    /**
     * 设置选项
     */
    fun setSegments(segments: List<Segment>) {
        this.segments = segments
        if (selectedIndex >= segments.size) {
            selectedIndex = 0
        }
        requestLayout()
        invalidate()
    }

    /**
     * 设置选中的索引
     */
    fun setSelectedIndex(index: Int) {
        if (index in segments.indices && index != selectedIndex) {
            selectedIndex = index
            invalidate()
            listener?.onSegmentSelected(index, segments[index])
        }
    }

    /**
     * 获取选中的索引
     */
    fun getSelectedIndex(): Int = selectedIndex

    /**
     * 获取选中的值
     */
    fun getSelectedValue(): Any? = segments.getOrNull(selectedIndex)?.value

    /**
     * 设置选择监听器
     */
    fun setOnSegmentSelectedListener(listener: OnSegmentSelectedListener) {
        this.listener = listener
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val desiredWidth = widthSize
        val desiredHeight = 36.dp.coerceAtLeast(
            if (heightMode == MeasureSpec.EXACTLY) heightSize else 36.dp
        )

        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (segments.isNotEmpty()) {
            segmentWidth = (w - paddingStart - paddingEnd) / segments.size.toFloat()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (segments.isEmpty()) return

        val left = paddingStart.toFloat()
        val top = paddingTop.toFloat()
        val right = width - paddingEnd.toFloat()
        val bottom = height - paddingBottom.toFloat()

        // 绘制背景（整体圆角矩形）
        val backgroundRect = RectF(left, top, right, bottom)
        paint.color = backgroundColor
        canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, paint)

        // 绘制边框
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = strokeWidth
        paint.color = borderColor
        canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, paint)

        // 绘制选中项的背景
        if (selectedIndex in segments.indices) {
            val selectedLeft = left + selectedIndex * segmentWidth
            val selectedRight = selectedLeft + segmentWidth

            // 根据位置决定圆角
            val selectedRect = RectF(selectedLeft, top, selectedRight, bottom)
            val leftRadius = if (selectedIndex == 0) cornerRadius else 0f
            val rightRadius = if (selectedIndex == segments.size - 1) cornerRadius else 0f

            paint.style = Paint.Style.FILL
            paint.color = selectedBackgroundColor
            canvas.drawRoundRect(selectedRect, leftRadius, rightRadius, paint)

            // 绘制分隔线（除了选中项）
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokeWidth
            paint.color = borderColor

            // 左侧分隔线
            if (selectedIndex > 0) {
                canvas.drawLine(selectedLeft, top, selectedLeft, bottom, paint)
            }
            // 右侧分隔线
            if (selectedIndex < segments.size - 1) {
                canvas.drawLine(selectedRight, top, selectedRight, bottom, paint)
            }
        }

        // 绘制文本
        segments.forEachIndexed { index, segment ->
            val segmentLeft = left + index * segmentWidth
            val segmentCenterX = segmentLeft + segmentWidth / 2
            val textY = (height / 2) + (textPaint.textSize / 3)

            if (index == selectedIndex) {
                canvas.drawText(segment.title, segmentCenterX, textY, selectedTextPaint)
            } else {
                canvas.drawText(segment.title, segmentCenterX, textY, textPaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val x = event.x
                val segmentIndex = ((x - paddingStart) / segmentWidth).toInt().coerceIn(0, segments.size - 1)
                setSelectedIndex(segmentIndex)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    // 扩展函数：dp转px
    private val Float.dp: Float
        get() = this * resources.displayMetrics.density

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    // 扩展函数：sp转px
    private val Float.sp: Float
        get() = this * resources.displayMetrics.scaledDensity
}



