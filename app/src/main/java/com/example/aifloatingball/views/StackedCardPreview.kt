package com.example.aifloatingball.views

import android.animation.ValueAnimator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.VelocityTracker
import android.view.animation.DecelerateInterpolator
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

    // 进度指示器画笔
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        color = Color.parseColor("#2196F3")
    }

    // 震动服务
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

    private var touchX = 0f
    private var touchY = 0f
    private var isTracking = false
    private var isParallelMode = true // 使用平行显示模式

    // 是否显示进度指示器
    private var isShowingProgress = false
    private var progressAngle = 0f

    // 卡片数据
    private var webViewCards = listOf<WebViewCardData>()
    private var currentCardIndex = 0 // 当前中心显示的卡片索引


    // 长按滑动相关
    private var isLongPressSliding = false // 是否在长按滑动状态
    private var slideStartX = 0f
    private var slideStartY = 0f
    private var scrollOffset = 0f // 滑动偏移量
    private var cardSpacing = 0f // 卡片间距
    private var cardWidth = 0f // 单个卡片宽度

    // 滑动增强功能
    private var lastSlideTime = 0L // 上次滑动时间
    private var slideVelocity = 0f // 滑动速度
    private var isInertiaScrolling = false // 是否在惯性滚动

    // 垂直拖拽相关（用于关闭卡片）
    private var isVerticalDragging = false
    private var centerCardOffsetY = 0f // 中心卡片的垂直偏移
    private var closeThreshold = 0f // 关闭阈值（旁边卡片高度的一半）
    private var refreshAnimationProgress = 0f // 刷新动画进度

    // 点击检测相关
    private var isClick = false // 是否是点击操作
    private var clickThreshold = 20f // 点击阈值（像素）

    private var longPressStartTime = 0L
    private var isLongPressActivated = false

    // 动画参数
    private var cardAnimator: ValueAnimator? = null
    private val cardOffsets = mutableListOf<PointF>() // 改为PointF支持x,y偏移
    private val cardRotations = mutableListOf<Float>()
    private val cardScales = mutableListOf<Float>()

    // 卡片参数 - 调整为屏幕的1/2大小
    private var baseCardWidth = 0f // 将在onSizeChanged中计算
    private var baseCardHeight = 0f
    private val cornerRadius = 16f
    private val stackSpacing = 60f // 层叠间距
    private val maxRotation = 8f // 减少旋转角度
    private val baseScale = 0.9f // 增加基础缩放
    private val hoverScale = 1.0f

    // 回调接口
    private var onCardSelectedListener: ((Int) -> Unit)? = null
    private var onCardCloseListener: ((Int) -> Unit)? = null
    private var onCardRefreshListener: ((Int) -> Unit)? = null
    
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // 计算卡片大小为屏幕的1/2
        baseCardWidth = w * 0.5f
        baseCardHeight = h * 0.5f

        // 重新初始化卡片属性
        if (webViewCards.isNotEmpty()) {
            initializeCardProperties()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 平行模式下处理触摸事件
        return handleStackedModeTouch(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // 平行模式下拦截事件，用于滑动交互
        return super.dispatchTouchEvent(event)
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
        Log.d("StackedCardPreview", "setWebViewCards: 设置 ${cards.size} 张卡片，当前模式: 平行")

        cardAnimator?.cancel()

        webViewCards = cards
        currentCardIndex = 0

        // 重置激活状态
        resetActivationState()

        initializeCardProperties()
        invalidate()

        Log.d("StackedCardPreview", "setWebViewCards: 完成，卡片数据已更新")
    }

    /**
     * 初始化卡片属性
     */
    private fun initializeCardProperties() {
        cardOffsets.clear()
        cardRotations.clear()
        cardScales.clear()

        // 使用平行显示模式
        initializeParallelModeProperties()
    }

    /**
     * 初始化平行模式属性
     */
    private fun initializeParallelModeProperties() {
        if (webViewCards.isEmpty()) return

        // 计算卡片尺寸和间距
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        // 卡片宽度为屏幕宽度的70%，高度为屏幕高度的60%
        cardWidth = viewWidth * 0.7f
        val cardHeight = viewHeight * 0.6f

        // 卡片间距为卡片宽度的80%，这样会有20%的重叠
        cardSpacing = cardWidth * 0.8f

        // 更新基础卡片尺寸
        baseCardWidth = cardWidth
        baseCardHeight = cardHeight

        // 计算关闭阈值（卡片高度的一半）
        closeThreshold = cardHeight * 0.3f // 降低关闭阈值，提升用户体验

        Log.d("StackedCardPreview", "平行模式初始化: 卡片宽度=$cardWidth, 间距=$cardSpacing, 关闭阈值=$closeThreshold, 卡片数=${webViewCards.size}")

        for (i in webViewCards.indices) {
            // 平行排列：每张卡片按间距水平排列
            val xOffset = i * cardSpacing
            cardOffsets.add(PointF(xOffset, 0f))

            // 无旋转
            cardRotations.add(0f)

            // 统一缩放
            cardScales.add(1.0f)
        }

        // 初始化滚动偏移，让第一张卡片居中
        scrollOffset = 0f
        currentCardIndex = 0
    }





    /**
     * 外部调用来更新手指位置（平行模式下不需要特殊处理）
     */
    fun updateFingerPosition(x: Float, y: Float) {
        touchX = x
        touchY = y
        isTracking = true

        // 平行模式下不需要悬停检测，直接更新显示
        invalidate()
    }

    /**
     * 停止预览效果
     */
    fun stopWave() {
        isTracking = false

        // 平行模式下不需要特殊处理，保持显示状态
        Log.d("StackedCardPreview", "停止预览效果，保持平行显示")
    }

    /**
     * 处理平行模式的触摸事件
     */
    private fun handleStackedModeTouch(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                Log.d("StackedCardPreview", "触摸按下: (${event.x}, ${event.y})")
                isTracking = true
                isShowingProgress = true // 开始时显示进度
                progressAngle = 0f // 重置进度
                isLongPressSliding = false
                isVerticalDragging = false
                isClick = true // 初始假设是点击
                slideStartX = event.x
                slideStartY = event.y
                touchX = event.x
                touchY = event.y
                centerCardOffsetY = 0f
                longPressStartTime = System.currentTimeMillis()

                Log.d("StackedCardPreview", "开始长按检测，当前激活状态: $isLongPressActivated")
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isTracking) {
                    val deltaX = event.x - slideStartX
                    val deltaY = event.y - slideStartY
                    val distance = sqrt(deltaX * deltaX + deltaY * deltaY)
                    val currentTime = System.currentTimeMillis()

                    // 检查是否超过点击阈值
                    if (isClick && distance > clickThreshold) {
                        isClick = false // 不再是点击操作
                        Log.d("StackedCardPreview", "移动距离超过阈值，不是点击操作")
                    }

                    // 直接处理滑动
                    // 检测是否开始滑动
                    if (!isLongPressSliding && !isVerticalDragging && distance > 30f) {
                        // 判断是水平还是垂直滑动
                        if (abs(deltaX) > abs(deltaY)) {
                            isLongPressSliding = true
                            Log.d("StackedCardPreview", "开始水平滑动")
                        } else {
                            isVerticalDragging = true
                            Log.d("StackedCardPreview", "开始垂直拖拽（关闭卡片）")
                        }
                    }

                    if (isLongPressSliding) {
                        // 水平滑动控制卡片
                        handleLongPressSlide(deltaX)
                    } else if (isVerticalDragging) {
                        // 垂直滑动关闭中心卡片
                        handleVerticalDrag(deltaY)
                    }

                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                Log.d("StackedCardPreview", "手指脱离屏幕，滑动状态: $isLongPressSliding")

                if (isTracking) {
                    isTracking = false

                    if (isLongPressSliding) {
                        // 水平滑动结束，检查是否需要惯性滚动
                        Log.d("StackedCardPreview", "水平滑动结束，速度: ${slideVelocity.toInt()}px/s")

                        if (abs(slideVelocity) > 1000f) {
                            // 速度足够快，启动惯性滚动
                            startInertiaScrollWithoutOpen()
                        } else {
                            // 速度较慢，直接对齐到最近的卡片
                            snapToNearestCard()
                        }
                    } else if (isVerticalDragging) {
                        // 垂直滑动结束，检查是否需要关闭卡片
                        handleVerticalDragEnd()
                    } else {
                        // 如果是点击操作，则立即打开卡片
                        if (isClick) {
                            Log.d("StackedCardPreview", "检测到点击操作，立即打开当前中心卡片")
                            selectCurrentCardWithFadeIn()
                            vibrate(VibrationType.BASIC) // 基本操作震动
                        }
                    }

                    // 重置所有状态
                    isLongPressSliding = false
                    isVerticalDragging = false
                    isClick = false
                    isShowingProgress = false // 停止显示进度
                    invalidate()
                    return true
                }
            }
        }
        return false
    }

    /**
     * 根据震动类型触发不同的震动效果
     */
    private fun vibrate(type: VibrationType) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = when (type) {
                VibrationType.BASIC -> VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE) // 基本操作
                VibrationType.IMPORTANT -> VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE) // 重要操作
                VibrationType.BROWSING -> VibrationEffect.createWaveform(longArrayOf(0, 20, 20, 20), -1) // 浏览操作
                VibrationType.LIGHT -> VibrationEffect.createOneShot(30, 50) // 轻震动，使用固定的低强度值
                VibrationType.HEAVY -> VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE) // 重震动
            }
            vibrator.vibrate(effect)
        }
    }

    // 震动类型枚举
    enum class VibrationType {
        BASIC, IMPORTANT, BROWSING, LIGHT, HEAVY
    }

    /**
     * 重置激活状态
     */
    fun resetActivationState() {
        isLongPressActivated = false
        longPressStartTime = 0L
        // Add any other state resets needed
    }

    /**
     * 重置视图状态
     */
    fun reset() {
        visibility = View.GONE
        resetActivationState()
        
        // 重置交互状态，防止触摸失效
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
        isEnabled = false
        
        webViewCards = emptyList()
        currentCardIndex = 0
        scrollOffset = 0f
        invalidate()
    }

    /**
     * 处理长按滑动（控制悬浮卡片左右滑动）
     */
    private fun handleLongPressSlide(deltaX: Float) {
        val currentTime = System.currentTimeMillis()

        // 计算滑动速度
        if (lastSlideTime > 0) {
            val timeDelta = currentTime - lastSlideTime
            if (timeDelta > 0) {
                slideVelocity = deltaX / timeDelta * 1000f // 像素/秒
            }
        }
        lastSlideTime = currentTime

        // 更新滚动偏移，增加灵敏度让滑动更流畅
        val sensitivity = if (abs(slideVelocity) > 2000f) 1.2f else 0.8f // 快速滑动时增加灵敏度
        scrollOffset -= deltaX * sensitivity

        // 限制滚动范围
        val maxOffset = (webViewCards.size - 1) * cardSpacing
        scrollOffset = scrollOffset.coerceIn(0f, maxOffset)

        // 计算当前中心卡片
        val newCardIndex = (scrollOffset / cardSpacing + 0.5f).toInt()
        if (newCardIndex != currentCardIndex && newCardIndex >= 0 && newCardIndex < webViewCards.size) {
            currentCardIndex = newCardIndex
            Log.d("StackedCardPreview", "滑动切换到卡片: $currentCardIndex (${webViewCards[currentCardIndex].title}) 速度: ${slideVelocity.toInt()}px/s")

            // 提供浏览操作的震动反馈
            vibrate(VibrationType.BROWSING)
        }

        // 重新绘制
        invalidate()

        // 更新滑动起点，使滑动更连续
        slideStartX = slideStartX + deltaX * 0.3f // 部分更新起点，保持滑动连续性
    }

    /**
     * 处理垂直拖拽（关闭卡片或刷新页面）
     */
    private fun handleVerticalDrag(deltaY: Float) {
        // 如果是第一次开始拖拽，触发轻震动
        if (centerCardOffsetY == 0f && deltaY != 0f) {
            vibrate(VibrationType.LIGHT)
        }
        
        if (deltaY < 0) {
            // 向上拖拽：关闭卡片
            centerCardOffsetY = deltaY
            Log.d("StackedCardPreview", "中心卡片向上偏移: $centerCardOffsetY, 关闭阈值: $closeThreshold")
        } else if (deltaY > 0) {
            // 向下拖拽：刷新页面（限制最大偏移量）
            val maxRefreshOffset = closeThreshold * 0.8f // 刷新阈值为关闭阈值的80%
            centerCardOffsetY = minOf(deltaY, maxRefreshOffset)
            Log.d("StackedCardPreview", "中心卡片向下偏移: $centerCardOffsetY, 刷新阈值: $maxRefreshOffset")
        } else {
            centerCardOffsetY = 0f
        }

        // 重新绘制
        invalidate()
    }

    /**
     * 处理垂直拖拽结束
     */
    private fun handleVerticalDragEnd() {
        val maxRefreshOffset = closeThreshold * 0.8f
        
        if (centerCardOffsetY < -closeThreshold) {
            // 向上超过关闭阈值，关闭中心卡片
            Log.d("StackedCardPreview", "关闭中心卡片: $currentCardIndex")
            closeCurrentCard()
        } else if (centerCardOffsetY > maxRefreshOffset) {
            // 向下超过刷新阈值，刷新当前卡片
            Log.d("StackedCardPreview", "刷新中心卡片: $currentCardIndex")
            refreshCurrentCard()
        } else {
            // 没有超过任何阈值，回弹到原位置
            animateCenterCardReturn()
        }
    }



    /**
     * 关闭当前中心卡片
     */
    private fun closeCurrentCard() {
        if (currentCardIndex < 0 || currentCardIndex >= webViewCards.size) return

        // 播放关闭动画
        animateCardClose()
    }

    /**
     * 卡片关闭动画
     */
    private fun animateCardClose() {
        ValueAnimator.ofFloat(centerCardOffsetY, -height.toFloat()).apply {
            duration = 300
            addUpdateListener { animator ->
                centerCardOffsetY = animator.animatedValue as Float
                invalidate()
            }

            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // 触发重震动
                    vibrate(VibrationType.HEAVY)
                    // 通知关闭卡片
                    onCardCloseListener?.invoke(currentCardIndex)
                    // 移除卡片
                    removeCard(currentCardIndex)
                }
            })
            start()
        }
    }

    /**
     * 中心卡片回弹动画
     */
    private fun animateCenterCardReturn() {
        if (centerCardOffsetY == 0f) return

        ValueAnimator.ofFloat(centerCardOffsetY, 0f).apply {
            duration = 250
            addUpdateListener { animator ->
                centerCardOffsetY = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /**
     * 移除卡片
     */
    private fun removeCard(cardIndex: Int) {
        if (cardIndex < 0 || cardIndex >= webViewCards.size) return

        val mutableCards = webViewCards.toMutableList()
        mutableCards.removeAt(cardIndex)
        webViewCards = mutableCards

        // 重置中心卡片偏移
        centerCardOffsetY = 0f

        // 调整当前卡片索引
        if (webViewCards.isEmpty()) {
            // 没有卡片了，隐藏预览
            visibility = View.GONE
        } else {
            if (currentCardIndex >= webViewCards.size) {
                currentCardIndex = webViewCards.size - 1
            }

            // 重新初始化卡片属性
            initializeCardProperties()

            // 对齐到当前卡片
            scrollOffset = currentCardIndex * cardSpacing

            invalidate()
        }

        Log.d("StackedCardPreview", "移除卡片后，剩余 ${webViewCards.size} 张卡片")
    }

    /**
     * 对齐到最近的卡片并打开
     */
    private fun snapToNearestCardAndOpen() {
        val targetOffset = currentCardIndex * cardSpacing

        if (abs(scrollOffset - targetOffset) > 5f) {
            // 使用动画平滑对齐，然后打开卡片
            ValueAnimator.ofFloat(scrollOffset, targetOffset).apply {
                duration = 200
                addUpdateListener { animator ->
                    scrollOffset = animator.animatedValue as Float
                    invalidate()
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        // 对齐完成后，打开中间卡片
                        Log.d("StackedCardPreview", "对齐完成，打开中间卡片: $currentCardIndex")
                        selectCurrentCard()
                    }
                })
                start()
            }
        } else {
            // 已经对齐，直接打开卡片
            Log.d("StackedCardPreview", "已对齐，直接打开中间卡片: $currentCardIndex")
            selectCurrentCard()
        }

        Log.d("StackedCardPreview", "对齐到卡片 $currentCardIndex，目标偏移: $targetOffset")
    }

    /**
     * 对齐到最近的卡片（不自动打开）
     */
    private fun snapToNearestCard() {
        val targetOffset = currentCardIndex * cardSpacing

        if (abs(scrollOffset - targetOffset) > 5f) {
            // 使用动画平滑对齐
            ValueAnimator.ofFloat(scrollOffset, targetOffset).apply {
                duration = 200
                addUpdateListener { animator ->
                    scrollOffset = animator.animatedValue as Float
                    invalidate()
                }
                start()
            }
        }

        Log.d("StackedCardPreview", "对齐到卡片 $currentCardIndex，目标偏移: $targetOffset，等待用户点击")
    }

    /**
     * 选择当前中心卡片
     */
    private fun selectCurrentCard() {
        if (currentCardIndex >= 0 && currentCardIndex < webViewCards.size) {
            Log.d("StackedCardPreview", "选择卡片: $currentCardIndex")

            // 通知选择了卡片
            onCardSelectedListener?.invoke(currentCardIndex)

            // 重置激活状态
            resetActivationState()

            // 隐藏预览
            visibility = View.GONE
        }
    }

    /**
     * 选择当前中心卡片并使用淡入动画
     */
    private fun selectCurrentCardWithFadeIn() {
        if (currentCardIndex >= 0 && currentCardIndex < webViewCards.size) {
            Log.d("StackedCardPreview", "点击选择卡片: $currentCardIndex，使用淡入动画")

            // 通知选择了卡片
            onCardSelectedListener?.invoke(currentCardIndex)

            // 重置激活状态
            resetActivationState()

            // 使用淡出动画隐藏预览
            animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    visibility = View.GONE
                    alpha = 1f // 重置透明度
                }
                .start()
        }
    }

    /**
     * 启动惯性滚动
     */
    private fun startInertiaScroll() {
        if (isInertiaScrolling) return

        isInertiaScrolling = true
        val initialVelocity = slideVelocity
        val deceleration = 2000f // 减速度 px/s²

        Log.d("StackedCardPreview", "启动惯性滚动，初始速度: ${initialVelocity.toInt()}px/s")

        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = (abs(initialVelocity) / deceleration * 1000f).toLong().coerceAtMost(800L)

        var lastTime = System.currentTimeMillis()
        var currentVelocity = initialVelocity

        animator.addUpdateListener { animation ->
            val currentTime = System.currentTimeMillis()
            val deltaTime = (currentTime - lastTime) / 1000f
            lastTime = currentTime

            // 计算当前速度（减速）
            val velocitySign = if (currentVelocity > 0) 1f else -1f
            currentVelocity -= velocitySign * deceleration * deltaTime

            // 如果速度变号或接近0，停止滚动
            if (abs(currentVelocity) < 100f || (velocitySign > 0 && currentVelocity < 0) || (velocitySign < 0 && currentVelocity > 0)) {
                animation.cancel()
                return@addUpdateListener
            }

            // 更新滚动偏移
            val deltaOffset = -currentVelocity * deltaTime
            scrollOffset += deltaOffset

            // 限制滚动范围
            val maxOffset = (webViewCards.size - 1) * cardSpacing
            scrollOffset = scrollOffset.coerceIn(0f, maxOffset)

            // 更新当前卡片索引
            val newCardIndex = (scrollOffset / cardSpacing + 0.5f).toInt()
            if (newCardIndex != currentCardIndex && newCardIndex >= 0 && newCardIndex < webViewCards.size) {
                currentCardIndex = newCardIndex
                Log.d("StackedCardPreview", "惯性滚动切换到卡片: $currentCardIndex")
            }

            invalidate()
        }

        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                isInertiaScrolling = false
                // 惯性滚动结束后，对齐到最近的卡片
                snapToNearestCardAndOpen()
            }

            override fun onAnimationCancel(animation: android.animation.Animator) {
                isInertiaScrolling = false
                snapToNearestCardAndOpen()
            }
        })

        animator.start()
    }

    /**
     * 启动惯性滚动（不自动打开卡片）
     */
    private fun startInertiaScrollWithoutOpen() {
        if (isInertiaScrolling) return

        isInertiaScrolling = true
        val initialVelocity = slideVelocity
        val velocitySign = if (initialVelocity > 0) 1 else -1

        Log.d("StackedCardPreview", "启动惯性滚动（不自动打开），初始速度: ${initialVelocity.toInt()}px/s")

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000 // 最大持续时间
            interpolator = DecelerateInterpolator(2f)
        }

        var lastTime = System.currentTimeMillis()

        animator.addUpdateListener { animation ->
            val currentTime = System.currentTimeMillis()
            val deltaTime = (currentTime - lastTime) / 1000f
            lastTime = currentTime

            // 计算当前速度（逐渐减速）
            val progress = animation.animatedValue as Float
            val currentVelocity = initialVelocity * (1f - progress)

            // 如果速度变号或接近0，停止滚动
            if (abs(currentVelocity) < 100f || (velocitySign > 0 && currentVelocity < 0) || (velocitySign < 0 && currentVelocity > 0)) {
                animation.cancel()
                return@addUpdateListener
            }

            // 更新滚动偏移
            val deltaOffset = -currentVelocity * deltaTime
            scrollOffset += deltaOffset

            // 限制滚动范围
            val maxOffset = (webViewCards.size - 1) * cardSpacing
            scrollOffset = scrollOffset.coerceIn(0f, maxOffset)

            // 更新当前卡片索引
            val newCardIndex = (scrollOffset / cardSpacing + 0.5f).toInt()
            if (newCardIndex != currentCardIndex && newCardIndex >= 0 && newCardIndex < webViewCards.size) {
                currentCardIndex = newCardIndex
                Log.d("StackedCardPreview", "惯性滚动切换到卡片: $currentCardIndex")
            }

            invalidate()
        }

        animator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                isInertiaScrolling = false
                // 惯性滚动结束后，只对齐不自动打开
                snapToNearestCard()
            }

            override fun onAnimationCancel(animation: android.animation.Animator) {
                isInertiaScrolling = false
                snapToNearestCard()
            }
        })

        animator.start()
    }





















    /**
     * 重置激活状态
     */


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (webViewCards.isEmpty()) {
            Log.d("StackedCardPreview", "onDraw: 没有卡片数据")
            return
        }

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        // 计算底部导航栏高度，确保不覆盖
        val bottomNavHeight = bottomNavHeightProvider?.invoke()?.toFloat() 
            ?: (64 * resources.displayMetrics.density) // fallback to layout defined 64dp
        val maskHeight = viewHeight - bottomNavHeight

        // 绘制半透明蒙版背景
        canvas.drawRect(0f, 0f, viewWidth, maskHeight, maskPaint)

        // 如果正在显示进度，绘制圆形进度条
        if (isShowingProgress) {
            val progressRect = RectF(touchX - 40, touchY - 40, touchX + 40, touchY + 40)
            canvas.drawArc(progressRect, -90f, progressAngle, false, progressPaint)
            progressAngle += 10 // 更新进度角度
            invalidate() // 持续重绘
        }

        Log.d("StackedCardPreview", "onDraw: 平行模式, 卡片数=${webViewCards.size}, 当前中心卡片=$currentCardIndex")

        // 平行模式：绘制所有卡片的平行排列
        drawParallelCards(canvas, viewWidth, viewHeight)
    }

    /**
     * 绘制平行排列的卡片
     */
    private fun drawParallelCards(canvas: Canvas, viewWidth: Float, viewHeight: Float) {
        Log.d("StackedCardPreview", "drawParallelCards: 绘制 ${webViewCards.size} 张平行卡片")

        val centerX = viewWidth / 2f
        val centerY = viewHeight / 2f

        // 计算起始X位置，让当前卡片居中
        val startX = centerX - scrollOffset

        // 性能优化：只绘制可见区域的卡片，避免遍历所有卡片
        // 计算可见卡片的索引范围，增加一些余量
        val firstVisibleIndex = ((scrollOffset - viewWidth / 2 - cardWidth) / cardSpacing).toInt().coerceAtLeast(0)
        val lastVisibleIndex = ((scrollOffset + viewWidth / 2 + cardWidth) / cardSpacing).toInt().coerceAtMost(webViewCards.size - 1)

        // 只绘制可见范围内的卡片
        for (i in firstVisibleIndex..lastVisibleIndex) {
            val cardData = webViewCards[i]

            // 计算卡片位置
            val cardCenterX = startX + i * cardSpacing
            val cardLeft = cardCenterX - cardWidth / 2f

            // 计算卡片垂直位置（中心卡片可能有垂直偏移）
            val cardTopBase = centerY - baseCardHeight / 2f
            val cardTop = if (i == currentCardIndex) {
                cardTopBase + centerCardOffsetY // 中心卡片应用垂直偏移
            } else {
                cardTopBase // 其他卡片保持原位置
            }

            // 计算卡片与屏幕中心的距离，用于缩放和透明度
            val distanceFromCenter = abs(cardCenterX - centerX)
            val maxDistance = cardSpacing * 2f // 最大影响距离
            val normalizedDistance = (distanceFromCenter / maxDistance).coerceIn(0f, 1f)

            // 中心卡片最大，边缘卡片较小
            val scale = 1.0f - normalizedDistance * 0.3f // 0.7 到 1.0
            val alpha = 1.0f - normalizedDistance * 0.5f // 0.5 到 1.0

            // 只绘制在屏幕范围内的卡片
            if (cardLeft < viewWidth && cardLeft + cardWidth > 0) {
                // 保存画布状态
                canvas.save()

                // 应用缩放
                canvas.scale(scale, scale, cardCenterX, centerY)

                // 绘制阴影
                val shadowPaint = Paint(cardShadowPaint).apply {
                    this.alpha = (255 * alpha * 0.3f).toInt()
                }
                canvas.drawRoundRect(
                    cardLeft + 8f,
                    cardTop + 8f,
                    cardLeft + cardWidth + 8f,
                    cardTop + baseCardHeight + 8f,
                    cornerRadius,
                    cornerRadius,
                    shadowPaint
                )

                // 绘制卡片背景
                val backgroundPaint = Paint(cardPaint).apply {
                    this.alpha = (255 * alpha).toInt()
                }
                canvas.drawRoundRect(
                    cardLeft,
                    cardTop,
                    cardLeft + cardWidth,
                    cardTop + baseCardHeight,
                    cornerRadius,
                    cornerRadius,
                    backgroundPaint
                )

                // 绘制卡片边框
                val borderPaint = Paint(cardBorderPaint).apply {
                    this.alpha = (255 * alpha).toInt()
                }
                canvas.drawRoundRect(
                    cardLeft,
                    cardTop,
                    cardLeft + cardWidth,
                    cardTop + baseCardHeight,
                    cornerRadius,
                    cornerRadius,
                    borderPaint
                )

                // 绘制卡片内容
                drawCardContent(canvas, cardData, cardLeft, cardTop, cardWidth, baseCardHeight, scale, alpha)

                // 恢复画布状态
                canvas.restore()

                Log.d("StackedCardPreview", "绘制卡片 $i: 位置=(${cardLeft.toInt()}, ${cardTop.toInt()}), 缩放=${"%.2f".format(scale)}, 透明度=${"%.2f".format(alpha)}")
            }
        }

        // 绘制中心指示器
        drawCenterIndicator(canvas, centerX, centerY)

        // 绘制卡片位置指示器
        drawCardPositionIndicator(canvas, viewWidth, viewHeight)
    }

    /**
     * 绘制中心指示器
     */
    private fun drawCenterIndicator(canvas: Canvas, centerX: Float, centerY: Float) {
        // 绘制一个小的中心指示线，帮助用户理解哪张卡片在中心
        val indicatorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 150
            strokeWidth = 4f
        }

        val indicatorHeight = 60f
        val indicatorY = centerY + baseCardHeight / 2f + 30f

        canvas.drawLine(
            centerX,
            indicatorY,
            centerX,
            indicatorY + indicatorHeight,
            indicatorPaint
        )
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

    /**
     * 设置卡片关闭监听器
     */
    fun setOnCardCloseListener(listener: (Int) -> Unit) {
        onCardCloseListener = listener
    }

    /**
     * 设置卡片刷新监听器
     */
    fun setOnCardRefreshListener(listener: (Int) -> Unit) {
        onCardRefreshListener = listener
    }

    /**
     * 刷新当前中心卡片
     */
    private fun refreshCurrentCard() {
        if (currentCardIndex < 0 || currentCardIndex >= webViewCards.size) return

        // 播放刷新动画
        animateCardRefresh()
    }

    /**
     * 卡片刷新动画
     */
    private fun animateCardRefresh() {
        // 第一阶段：向下拉伸并缩放
        val pullDownAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                centerCardOffsetY = centerCardOffsetY * (1f - progress * 0.3f)
                refreshAnimationProgress = progress
                invalidate()
            }
        }
        
        // 第二阶段：回弹并完成刷新
        val bounceBackAnimator = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 300
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                centerCardOffsetY = centerCardOffsetY * progress
                refreshAnimationProgress = 1f - progress
                invalidate()
            }
            
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // 触发重震动
                    vibrate(VibrationType.HEAVY)
                    // 通知刷新卡片
                    onCardRefreshListener?.invoke(currentCardIndex)
                    centerCardOffsetY = 0f
                    refreshAnimationProgress = 0f
                    invalidate()
                }
            })
        }
        
        // 顺序播放动画
        pullDownAnimator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                bounceBackAnimator.start()
            }
        })
        
        pullDownAnimator.start()
    }



    /**
     * 绘制卡片位置指示器
     */
    private fun drawCardPositionIndicator(canvas: Canvas, viewWidth: Float, viewHeight: Float) {
        if (webViewCards.isEmpty()) return

        // 计算卡片区域的顶部位置
        val centerY = viewHeight / 2f
        val cardTop = centerY - baseCardHeight / 2f + centerCardOffsetY

        // 将指示器放在卡片上方，距离卡片顶部60px
        val indicatorY = cardTop - 60f
        val indicatorCenterX = viewWidth / 2f
        val dotRadius = 8f
        val dotSpacing = 24f
        val totalWidth = (webViewCards.size - 1) * dotSpacing
        val startX = indicatorCenterX - totalWidth / 2f

        // 绘制指示器背景
        val backgroundPaint = Paint().apply {
            color = Color.parseColor("#80000000") // 半透明黑色
            isAntiAlias = true
        }

        val backgroundRect = RectF(
            startX - dotRadius - 12f,
            indicatorY - dotRadius - 8f,
            startX + totalWidth + dotRadius + 12f,
            indicatorY + dotRadius + 8f
        )
        canvas.drawRoundRect(backgroundRect, 16f, 16f, backgroundPaint)

        // 绘制指示点
        for (i in webViewCards.indices) {
            val dotX = startX + i * dotSpacing
            val dotPaint = Paint().apply {
                color = if (i == currentCardIndex) {
                    Color.parseColor("#FF4081") // 当前卡片用粉色
                } else {
                    Color.parseColor("#FFFFFF") // 其他卡片用白色
                }
                isAntiAlias = true
            }

            val radius = if (i == currentCardIndex) dotRadius * 1.2f else dotRadius * 0.8f
            canvas.drawCircle(dotX, indicatorY, radius, dotPaint)
        }

        // 绘制卡片标题（在指示器上方）
        if (currentCardIndex >= 0 && currentCardIndex < webViewCards.size) {
            val title = webViewCards[currentCardIndex].title
            val titlePaint = Paint().apply {
                color = Color.WHITE
                textSize = 36f
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                // 添加阴影效果，提高可读性
                setShadowLayer(4f, 0f, 2f, Color.parseColor("#80000000"))
            }

            // 将标题放在指示器上方40px
            val titleY = indicatorY - 40f
            canvas.drawText(title, indicatorCenterX, titleY, titlePaint)
        }
    }

    /**
     * 重置为平行模式
     */
    fun resetToStackedMode() {
        Log.d("StackedCardPreview", "重置为平行模式")

        // 重置所有状态
        isParallelMode = true
        currentCardIndex = 0
        scrollOffset = 0f

        // 设置为可交互（平行模式下需要处理触摸事件）
        isClickable = true
        isFocusable = true
        isEnabled = true

        // 重置动画状态
        scaleX = 1f
        scaleY = 1f
        alpha = 1f
        translationY = 0f

        Log.d("StackedCardPreview", "平行模式重置完成")
    }

    /**
     * 启用平行预览模式的交互
     */
    fun enableStackedInteraction() {
        Log.d("StackedCardPreview", "启用平行预览模式交互")

        // 设置为可交互
        isClickable = true
        isFocusable = true
        isEnabled = true

        // 重置触摸状态
        resetActivationState()

        Log.d("StackedCardPreview", "平行预览模式交互已启用")
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cardAnimator?.cancel()
    }
}
