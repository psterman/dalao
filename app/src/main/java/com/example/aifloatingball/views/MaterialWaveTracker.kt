package com.example.aifloatingball.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.roundToInt
import android.view.animation.DecelerateInterpolator
import kotlin.math.*

/**
 * webOS风格的卡片预览波浪效果View
 * 显示所有webview卡片的横向悬浮预览，手指位置的卡片最高
 */
class MaterialWaveTracker @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 蒙版背景画笔
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
        alpha = 120 // 半透明黑色蒙版
    }

    // 卡片绘制画笔
    private val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val cardShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
        alpha = 80
    }

    private val cardBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.parseColor("#E0E0E0")
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 28f
        color = Color.WHITE
        textAlign = Paint.Align.LEFT
        typeface = Typeface.DEFAULT_BOLD
        setShadowLayer(2f, 1f, 1f, Color.BLACK)
    }

    private var touchX = 0f
    private var isTracking = false

    // 卡片数据
    private var webViewCards = listOf<WebViewCardData>()
    private var hoveredCardIndex = -1

    // 动画参数
    private var cardScaleAnimator: ValueAnimator? = null
    private val cardScales = mutableListOf<Float>()
    private val baseScale = 1.0f
    private val maxScale = 1.3f

    // 卡片参数（动态计算）
    private var baseCardWidth = 120f
    private var baseCardHeight = 180f // 竖屏比例，高度大于宽度
    private var maxCardWidth = 150f
    private var maxCardHeight = 225f
    private var cardSpacing = 20f
    private val cornerRadius = 12f

    // 卡片尺寸限制（竖屏比例 2:3）
    private val maxCardWidthLimit = 160f
    private val minCardWidthLimit = 80f
    private val cardAspectRatio = 1.5f // 高度是宽度的1.5倍

    // 滑动相关参数
    private var currentCardOffset = 0f
    private var targetCardOffset = 0f
    private var cardOffsetAnimator: ValueAnimator? = null
    private var lastTouchX = 0f
    private var isDragging = false

    // 指示器参数
    private val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        alpha = 180
        textSize = 32f
        typeface = Typeface.DEFAULT_BOLD
    }

    private val indicatorBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        alpha = 100
        style = Paint.Style.FILL
    }

    // 回调接口
    private var onCardSelectedListener: ((Int) -> Unit)? = null
    
    // 底部导航栏高度获取回调
    private var bottomNavHeightProvider: (() -> Int)? = null

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
        // 所有的交互都通过外部调用updateFingerPosition等方法来处理
        return false
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // 确保触摸事件不被拦截，直接穿透到底层
        return false
    }

    /**
     * 设置底部导航栏高度提供者
     */
    fun setBottomNavHeightProvider(provider: () -> Int) {
        bottomNavHeightProvider = provider
    }

    /**
     * 设置webview卡片数据
     */
    fun setWebViewCards(cards: List<WebViewCardData>) {
        // 先取消所有动画
        cardScaleAnimator?.cancel()
        cardOffsetAnimator?.cancel()

        webViewCards = cards
        calculateCardDimensions()

        // 重新初始化缩放列表
        cardScales.clear()
        repeat(cards.size) {
            cardScales.add(baseScale)
        }

        currentCardOffset = 0f
        targetCardOffset = 0f
        hoveredCardIndex = -1
        invalidate()
    }

    /**
     * 根据卡片数量动态计算卡片尺寸
     */
    private fun calculateCardDimensions() {
        if (webViewCards.isEmpty()) return

        val screenWidth = resources.displayMetrics.widthPixels.toFloat()
        val cardCount = webViewCards.size

        // 显示5个卡片撑满屏幕，支持滑动查看更多
        val visibleCardCount = minOf(cardCount, 5)
        val availableWidth = screenWidth * 0.9f // 使用屏幕宽度的90%

        // 根据可见卡片数量计算合适的宽度
        val totalSpacing = (visibleCardCount - 1) * cardSpacing
        val calculatedWidth = (availableWidth - totalSpacing) / visibleCardCount

        // 限制卡片宽度在合理范围内
        baseCardWidth = calculatedWidth.coerceIn(minCardWidthLimit, maxCardWidthLimit)

        // 根据竖屏比例计算高度
        baseCardHeight = baseCardWidth * cardAspectRatio

        // 计算最大尺寸
        maxCardWidth = baseCardWidth * maxScale
        maxCardHeight = baseCardHeight * maxScale

        // 调整间距，让卡片更紧凑
        cardSpacing = when {
            cardCount > 5 -> 8f
            cardCount > 3 -> 12f
            else -> 16f
        }
    }

    /**
     * 外部调用来更新手指位置
     */
    fun updateFingerPosition(x: Float, y: Float) {
        touchX = x
        isTracking = true

        // 处理水平滑动
        handleHorizontalSwipe(x)

        // 计算悬停的卡片
        val newHoveredIndex = calculateHoveredCard(x)
        if (newHoveredIndex != hoveredCardIndex) {
            hoveredCardIndex = newHoveredIndex
            animateCardScales()
        }

        invalidate()
    }

    /**
     * 停止波浪效果
     */
    fun stopWave() {
        // 完成滑动对齐
        finishSwipe()

        // 如果手指在某个卡片上，切换到该卡片
        if (hoveredCardIndex >= 0 && hoveredCardIndex < webViewCards.size) {
            onCardSelectedListener?.invoke(hoveredCardIndex)
        }

        isTracking = false
        hoveredCardIndex = -1
        animateCardScales()
    }

    /**
     * 处理水平滑动
     */
    private fun handleHorizontalSwipe(x: Float) {
        if (webViewCards.size <= 5) return // 少于等于5个卡片不需要滑动

        if (!isDragging) {
            lastTouchX = x
            isDragging = true
            return
        }

        val deltaX = x - lastTouchX
        val maxOffset = (webViewCards.size - 5) * (baseCardWidth + cardSpacing)

        // 检查是否在边缘区域，如果是则增加滑动敏感度
        val screenWidth = width.toFloat()
        val edgeThreshold = screenWidth * 0.2f // 20%的边缘区域
        val sensitivity = if (x < edgeThreshold || x > screenWidth - edgeThreshold) {
            1.2f // 边缘区域更敏感
        } else {
            0.8f // 中间区域正常敏感度
        }

        // 实时跟随手指移动
        currentCardOffset = (currentCardOffset + deltaX * sensitivity).coerceIn(-maxOffset, 0f)
        lastTouchX = x

        invalidate()
    }

    /**
     * 完成滑动，自动对齐到最近的卡片
     */
    private fun finishSwipe() {
        if (!isDragging) return
        isDragging = false

        if (webViewCards.size <= 5) return

        val cardStep = baseCardWidth + cardSpacing
        val maxOffset = (webViewCards.size - 5) * cardStep

        // 计算最近的对齐位置
        val nearestIndex = (-currentCardOffset / cardStep).roundToInt()
        targetCardOffset = (-nearestIndex * cardStep).coerceIn(-maxOffset, 0f)

        animateCardOffset()
    }

    /**
     * 计算手指悬停的卡片索引
     */
    private fun calculateHoveredCard(x: Float): Int {
        if (webViewCards.isEmpty()) return -1

        val visibleCardCount = minOf(webViewCards.size, 5)
        val totalWidth = visibleCardCount * baseCardWidth + (visibleCardCount - 1) * cardSpacing
        val startX = (width - totalWidth) / 2f + currentCardOffset
        var currentX = startX

        // 检查可见的卡片
        val startIndex = maxOf(0, (-currentCardOffset / (baseCardWidth + cardSpacing)).toInt())
        val endIndex = minOf(webViewCards.size - 1, startIndex + 4) // 显示5个卡片

        for (i in startIndex..endIndex) {
            val cardLeft = currentX
            val cardRight = currentX + baseCardWidth

            // 扩大边缘卡片的点击区域
            val expandedLeft = if (i == startIndex) cardLeft - cardSpacing / 2f else cardLeft
            val expandedRight = if (i == endIndex) cardRight + cardSpacing / 2f else cardRight

            if (x >= expandedLeft && x <= expandedRight) {
                return i
            }

            currentX += baseCardWidth + cardSpacing
        }

        return -1
    }

    /**
     * 动画卡片缩放变化
     */
    private fun animateCardScales() {
        cardScaleAnimator?.cancel()

        val targetScales = mutableListOf<Float>()
        for (i in webViewCards.indices) {
            targetScales.add(
                if (i == hoveredCardIndex) maxScale else baseScale
            )
        }

        cardScaleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                // 确保索引不越界
                val maxIndex = minOf(cardScales.size, targetScales.size, webViewCards.size)
                for (i in 0 until maxIndex) {
                    if (i < cardScales.size && i < targetScales.size) {
                        val startScale = cardScales[i]
                        val targetScale = targetScales[i]
                        cardScales[i] = startScale + (targetScale - startScale) * progress
                    }
                }
                invalidate()
            }
            start()
        }
    }

    /**
     * 动画卡片偏移变化
     */
    private fun animateCardOffset() {
        cardOffsetAnimator?.cancel()

        cardOffsetAnimator = ValueAnimator.ofFloat(currentCardOffset, targetCardOffset).apply {
            duration = 400
            interpolator = android.view.animation.DecelerateInterpolator(2.0f)
            addUpdateListener { animator ->
                currentCardOffset = animator.animatedValue as Float
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
        val bottomNavHeight = bottomNavHeightProvider?.invoke()?.toFloat() 
            ?: (120 * resources.displayMetrics.density) // fallback to default
        val maskHeight = viewHeight - bottomNavHeight

        // 绘制半透明蒙版背景，完全不覆盖底部导航栏
        canvas.drawRect(0f, 0f, viewWidth, maskHeight, maskPaint)

        // 绘制卡片预览（在屏幕中间）
        drawCardPreviews(canvas, viewWidth, viewHeight)

        // 绘制指示器
        drawIndicators(canvas, viewWidth, viewHeight)
    }

    private fun drawCardPreviews(canvas: Canvas, viewWidth: Float, viewHeight: Float) {
        if (webViewCards.isEmpty()) return

        val visibleCardCount = minOf(webViewCards.size, 5)
        val totalWidth = visibleCardCount * baseCardWidth + (visibleCardCount - 1) * cardSpacing
        val startX = (viewWidth - totalWidth) / 2f + currentCardOffset
        var currentX = startX

        // 计算垂直居中位置
        val centerY = viewHeight / 2f

        // 绘制所有卡片，包括部分可见的
        val startIndex = maxOf(0, (-currentCardOffset / (baseCardWidth + cardSpacing)).toInt() - 1)
        val endIndex = minOf(webViewCards.size - 1, startIndex + 6) // 多绘制一些以显示边缘

        for (i in startIndex..endIndex) {
            // 确保索引在有效范围内
            if (i >= webViewCards.size || i < 0) continue

            val scale = cardScales.getOrElse(i) { baseScale }
            val scaledWidth = baseCardWidth * scale
            val scaledHeight = baseCardHeight * scale

            // 计算卡片位置（居中）
            val cardLeft = currentX - (scaledWidth - baseCardWidth) / 2f
            val cardTop = centerY - scaledHeight / 2f

            // 计算透明度（边缘卡片半透明）
            val alpha = calculateCardAlpha(cardLeft, scaledWidth, viewWidth)

            // 设置透明度
            val originalShadowAlpha = cardShadowPaint.alpha
            val originalCardAlpha = cardPaint.alpha
            cardShadowPaint.alpha = (originalShadowAlpha * alpha).toInt()
            cardPaint.alpha = (originalCardAlpha * alpha).toInt()

            // 绘制阴影
            canvas.drawRoundRect(
                cardLeft + 3f,
                cardTop + 3f,
                cardLeft + scaledWidth + 3f,
                cardTop + scaledHeight + 3f,
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
            val originalBorderAlpha = cardBorderPaint.alpha
            cardBorderPaint.alpha = (originalBorderAlpha * alpha).toInt()
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

            currentX += baseCardWidth + cardSpacing
        }
    }

    /**
     * 计算卡片透明度（边缘卡片半透明）
     */
    private fun calculateCardAlpha(cardLeft: Float, cardWidth: Float, screenWidth: Float): Float {
        val cardCenter = cardLeft + cardWidth / 2f
        val screenCenter = screenWidth / 2f
        val maxDistance = screenWidth * 0.35f // 35%屏幕宽度作为渐变范围

        val distance = abs(cardCenter - screenCenter)
        return if (distance <= maxDistance) {
            1.0f - (distance / maxDistance) * 0.3f // 最多减少30%透明度
        } else {
            0.7f // 最小透明度70%，保持较好的可见性
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
        val centerX = left + width / 2f
        val centerY = top + height / 2f
        val padding = 8f * scale

        // 绘制截图（如果有）
        cardData.screenshot?.let { screenshot ->
            val scaledBitmap = Bitmap.createScaledBitmap(
                screenshot,
                (width - padding * 2).toInt(),
                (height - padding * 3).toInt(),
                true
            )
            val bitmapPaint = Paint().apply {
                this.alpha = (255 * alpha).toInt()
            }
            canvas.drawBitmap(scaledBitmap, left + padding, top + padding, bitmapPaint)
        }

        // 绘制标题（在卡片左上角）
        val title = if (cardData.title.length > 8) {
            cardData.title.substring(0, 8) + "..."
        } else {
            cardData.title
        }

        val originalTextAlpha = textPaint.alpha
        textPaint.alpha = (originalTextAlpha * alpha).toInt()
        textPaint.textSize = 14f * scale

        // 绘制文字背景
        val textWidth = textPaint.measureText(title)
        val textHeight = textPaint.textSize
        val textBackgroundPaint = Paint().apply {
            color = Color.BLACK
        }
        textBackgroundPaint.alpha = (150 * alpha).toInt()

        canvas.drawRoundRect(
            left + padding - 4f,
            top + padding - 2f,
            left + padding + textWidth + 4f,
            top + padding + textHeight + 2f,
            4f, 4f,
            textBackgroundPaint
        )

        canvas.drawText(
            title,
            left + padding,
            top + padding + textHeight - 2f,
            textPaint
        )

        // 恢复文本透明度
        textPaint.alpha = originalTextAlpha
    }

    private fun getTotalWidth(): Float {
        if (webViewCards.isEmpty()) return 0f
        val visibleCardCount = minOf(webViewCards.size, 5)
        return visibleCardCount * baseCardWidth + (visibleCardCount - 1) * cardSpacing
    }

    /**
     * 绘制左右指示器
     */
    private fun drawIndicators(canvas: Canvas, viewWidth: Float, viewHeight: Float) {
        if (webViewCards.size <= 5) return // 少于等于5个卡片不需要指示器

        val centerY = viewHeight / 2f
        val indicatorY = centerY - baseCardHeight / 2f - 40f

        // 计算当前显示的卡片范围
        val startIndex = maxOf(0, (-currentCardOffset / (baseCardWidth + cardSpacing)).toInt())
        val canScrollLeft = startIndex > 0
        val canScrollRight = startIndex + 5 < webViewCards.size

        // 左侧指示器
        if (canScrollLeft) {
            val leftX = 30f
            canvas.drawCircle(leftX, indicatorY, 20f, indicatorBackgroundPaint)
            canvas.drawText("‹", leftX - 8f, indicatorY + 8f, indicatorPaint)
        }

        // 右侧指示器
        if (canScrollRight) {
            val rightX = viewWidth - 30f
            canvas.drawCircle(rightX, indicatorY, 20f, indicatorBackgroundPaint)
            canvas.drawText("›", rightX - 8f, indicatorY + 8f, indicatorPaint)
        }

        // 页面指示器（显示当前位置）
        val totalPages = if (webViewCards.size <= 5) 1 else kotlin.math.ceil((webViewCards.size - 4).toFloat() / 1f).toInt()
        val currentPage = startIndex + 1
        val pageText = "$currentPage/$totalPages"
        val pageTextWidth = indicatorPaint.measureText(pageText)

        canvas.drawRoundRect(
            viewWidth / 2f - pageTextWidth / 2f - 10f,
            indicatorY - 15f,
            viewWidth / 2f + pageTextWidth / 2f + 10f,
            indicatorY + 15f,
            15f, 15f,
            indicatorBackgroundPaint
        )
        canvas.drawText(
            pageText,
            viewWidth / 2f - pageTextWidth / 2f,
            indicatorY + 8f,
            indicatorPaint
        )
    }

    /**
     * 更新webview卡片数据
     */
    fun updateWebViewCards(cards: List<WebViewCardData>) {
        // 先取消所有动画
        cardScaleAnimator?.cancel()
        cardOffsetAnimator?.cancel()

        webViewCards = cards
        calculateCardDimensions()

        // 重新初始化缩放列表
        cardScales.clear()
        repeat(cards.size) {
            cardScales.add(baseScale)
        }

        currentCardOffset = 0f
        targetCardOffset = 0f
        hoveredCardIndex = -1

        invalidate()
    }

    /**
     * 设置卡片选择监听器
     */
    fun setOnCardSelectedListener(listener: (Int) -> Unit) {
        onCardSelectedListener = listener
    }

    /**
     * 设置卡片颜色
     */
    fun setCardColor(color: Int) {
        cardPaint.color = color
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cardScaleAnimator?.cancel()
        cardOffsetAnimator?.cancel()
    }
}
