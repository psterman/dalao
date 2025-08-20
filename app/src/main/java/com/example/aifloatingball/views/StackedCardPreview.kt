package com.example.aifloatingball.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

/**
 * 层叠卡片预览视图 - 参考手机launcher的后台任务样式
 * 显示webview卡片的层叠预览，手指位置的卡片会突出显示
 */
class StackedCardPreview @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 蒙版背景画笔
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
        alpha = 140 // 半透明黑色蒙版
    }

    // 卡片绘制画笔
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val cardShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
        alpha = 60
    }

    private val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.parseColor("#E0E0E0")
    }

    // 文字画笔
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 24f
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }

    private var touchX = 0f
    private var touchY = 0f
    private var isTracking = false

    // 卡片数据
    private var webViewCards = listOf<WebViewCardData>()
    private var hoveredCardIndex = -1

    // 动画参数
    private var cardAnimator: ValueAnimator? = null
    private val cardOffsets = mutableListOf<Float>()
    private val cardRotations = mutableListOf<Float>()
    private val cardScales = mutableListOf<Float>()

    // 卡片参数
    private val baseCardWidth = 280f
    private val baseCardHeight = 420f // 3:2比例
    private val cornerRadius = 16f
    private val maxStackOffset = 40f // 最大层叠偏移
    private val maxRotation = 15f // 最大旋转角度
    private val baseScale = 0.85f
    private val hoverScale = 1.0f

    // 回调接口
    private var onCardSelectedListener: ((Int) -> Unit)? = null

    data class WebViewCardData(
        val title: String,
        val url: String,
        val favicon: Bitmap? = null,
        val screenshot: Bitmap? = null
    )

    init {
        // 设置为完全不可交互，确保触摸事件穿透
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
        isEnabled = false

        // 设置触摸监听器，确保不消费任何触摸事件
        setOnTouchListener { _, _ -> false }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 完全不处理触摸事件，让事件穿透到底层
        return false
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // 确保触摸事件不被拦截，直接穿透到底层
        return false
    }

    /**
     * 设置webview卡片数据
     */
    fun setWebViewCards(cards: List<WebViewCardData>) {
        cardAnimator?.cancel()

        webViewCards = cards
        initializeCardProperties()
        invalidate()
    }

    /**
     * 初始化卡片属性
     */
    private fun initializeCardProperties() {
        cardOffsets.clear()
        cardRotations.clear()
        cardScales.clear()

        for (i in webViewCards.indices) {
            // 层叠偏移：后面的卡片向右下偏移
            cardOffsets.add(i * 8f)
            // 轻微旋转：创造层叠效果
            cardRotations.add((i - webViewCards.size / 2f) * 2f)
            // 基础缩放
            cardScales.add(baseScale)
        }
    }

    /**
     * 外部调用来更新手指位置
     */
    fun updateFingerPosition(x: Float, y: Float) {
        touchX = x
        touchY = y
        isTracking = true

        // 计算悬停的卡片
        val newHoveredIndex = calculateHoveredCard(x, y)
        if (newHoveredIndex != hoveredCardIndex) {
            hoveredCardIndex = newHoveredIndex
            animateCardProperties()
        }

        invalidate()
    }

    /**
     * 停止预览效果
     */
    fun stopWave() {
        // 如果手指在某个卡片上，切换到该卡片
        if (hoveredCardIndex >= 0 && hoveredCardIndex < webViewCards.size) {
            onCardSelectedListener?.invoke(hoveredCardIndex)
        }

        isTracking = false
        hoveredCardIndex = -1
        animateCardProperties()
    }

    /**
     * 计算悬停的卡片
     */
    private fun calculateHoveredCard(x: Float, y: Float): Int {
        if (webViewCards.isEmpty()) return -1

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val centerX = viewWidth / 2f
        val centerY = viewHeight / 2f

        // 从最上层的卡片开始检测（倒序）
        for (i in webViewCards.indices.reversed()) {
            val offset = cardOffsets.getOrElse(i) { 0f }
            val rotation = cardRotations.getOrElse(i) { 0f }
            val scale = cardScales.getOrElse(i) { baseScale }

            val cardLeft = centerX - (baseCardWidth * scale) / 2f + offset
            val cardTop = centerY - (baseCardHeight * scale) / 2f + offset / 2f
            val cardRight = cardLeft + baseCardWidth * scale
            val cardBottom = cardTop + baseCardHeight * scale

            // 简单的矩形碰撞检测（忽略旋转）
            if (x >= cardLeft && x <= cardRight && y >= cardTop && y <= cardBottom) {
                return i
            }
        }

        return -1
    }

    /**
     * 动画卡片属性变化
     */
    private fun animateCardProperties() {
        cardAnimator?.cancel()

        val targetScales = mutableListOf<Float>()
        val targetOffsets = mutableListOf<Float>()
        val targetRotations = mutableListOf<Float>()

        for (i in webViewCards.indices) {
            if (i == hoveredCardIndex) {
                // 悬停的卡片：放大、减少偏移、减少旋转
                targetScales.add(hoverScale)
                targetOffsets.add(i * 4f) // 减少偏移
                targetRotations.add(0f) // 无旋转
            } else {
                // 其他卡片：正常状态
                targetScales.add(baseScale)
                targetOffsets.add(i * 8f)
                targetRotations.add((i - webViewCards.size / 2f) * 2f)
            }
        }

        cardAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 250
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                
                for (i in webViewCards.indices) {
                    if (i < cardScales.size && i < targetScales.size) {
                        val startScale = cardScales[i]
                        val targetScale = targetScales[i]
                        cardScales[i] = startScale + (targetScale - startScale) * progress
                    }
                    
                    if (i < cardOffsets.size && i < targetOffsets.size) {
                        val startOffset = cardOffsets[i]
                        val targetOffset = targetOffsets[i]
                        cardOffsets[i] = startOffset + (targetOffset - startOffset) * progress
                    }
                    
                    if (i < cardRotations.size && i < targetRotations.size) {
                        val startRotation = cardRotations[i]
                        val targetRotation = targetRotations[i]
                        cardRotations[i] = startRotation + (targetRotation - startRotation) * progress
                    }
                }
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (webViewCards.isEmpty()) return

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        // 计算底部导航栏高度，确保不覆盖
        val bottomNavHeight = (120 * resources.displayMetrics.density)
        val maskHeight = viewHeight - bottomNavHeight

        // 绘制半透明蒙版背景
        canvas.drawRect(0f, 0f, viewWidth, maskHeight, maskPaint)

        // 绘制层叠卡片（从底层到顶层）
        drawStackedCards(canvas, viewWidth, viewHeight)
    }

    private fun drawStackedCards(canvas: Canvas, viewWidth: Float, viewHeight: Float) {
        val centerX = viewWidth / 2f
        val centerY = viewHeight / 2f

        // 从底层开始绘制（正序）
        for (i in webViewCards.indices) {
            val offset = cardOffsets.getOrElse(i) { 0f }
            val rotation = cardRotations.getOrElse(i) { 0f }
            val scale = cardScales.getOrElse(i) { baseScale }

            val scaledWidth = baseCardWidth * scale
            val scaledHeight = baseCardHeight * scale

            // 计算卡片位置
            val cardLeft = centerX - scaledWidth / 2f + offset
            val cardTop = centerY - scaledHeight / 2f + offset / 2f

            // 保存画布状态
            canvas.save()

            // 应用旋转变换
            canvas.rotate(rotation, centerX + offset, centerY + offset / 2f)

            // 计算透明度（被悬停的卡片更明亮）
            val alpha = if (i == hoveredCardIndex) 1.0f else 0.8f

            // 设置透明度
            val originalShadowAlpha = cardShadowPaint.alpha
            val originalCardAlpha = cardPaint.alpha
            val originalBorderAlpha = cardBorderPaint.alpha
            
            cardShadowPaint.alpha = (originalShadowAlpha * alpha).toInt()
            cardPaint.alpha = (originalCardAlpha * alpha).toInt()
            cardBorderPaint.alpha = (originalBorderAlpha * alpha).toInt()

            // 绘制阴影
            canvas.drawRoundRect(
                cardLeft + 4f,
                cardTop + 4f,
                cardLeft + scaledWidth + 4f,
                cardTop + scaledHeight + 4f,
                cornerRadius * scale,
                cornerRadius * scale,
                cardShadowPaint
            )

            // 绘制卡片背景
            canvas.drawRoundRect(
                cardLeft,
                cardTop,
                cardLeft + scaledWidth,
                cardTop + scaledHeight,
                cornerRadius * scale,
                cornerRadius * scale,
                cardPaint
            )

            // 绘制卡片边框
            canvas.drawRoundRect(
                cardLeft,
                cardTop,
                cardLeft + scaledWidth,
                cardTop + scaledHeight,
                cornerRadius * scale,
                cornerRadius * scale,
                cardBorderPaint
            )

            // 绘制卡片内容
            drawCardContent(canvas, webViewCards[i], cardLeft, cardTop, scaledWidth, scaledHeight, scale, alpha)

            // 恢复透明度
            cardShadowPaint.alpha = originalShadowAlpha
            cardPaint.alpha = originalCardAlpha
            cardBorderPaint.alpha = originalBorderAlpha

            // 恢复画布状态
            canvas.restore()
        }
    }

    private fun drawCardContent(
        canvas: Canvas,
        cardData: WebViewCardData,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
        scale: Float,
        alpha: Float
    ) {
        val padding = 16f * scale

        // 绘制截图（如果有）
        cardData.screenshot?.let { screenshot ->
            val scaledBitmap = Bitmap.createScaledBitmap(
                screenshot,
                (width - padding * 2).toInt(),
                (height - padding * 3 - 40f * scale).toInt(), // 为标题留空间
                true
            )
            val bitmapPaint = Paint().apply {
                this.alpha = (255 * alpha).toInt()
            }
            canvas.drawBitmap(scaledBitmap, left + padding, top + padding, bitmapPaint)
        }

        // 绘制标题（在卡片底部）
        val title = if (cardData.title.length > 12) {
            cardData.title.substring(0, 12) + "..."
        } else {
            cardData.title
        }

        val titlePaint = Paint(textPaint).apply {
            textSize = 28f * scale
            this.alpha = (255 * alpha).toInt()
        }

        canvas.drawText(
            title,
            left + width / 2f,
            top + height - 20f * scale,
            titlePaint
        )
    }

    /**
     * 设置卡片选择监听器
     */
    fun setOnCardSelectedListener(listener: (Int) -> Unit) {
        onCardSelectedListener = listener
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cardAnimator?.cancel()
    }
}
