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

    companion object {
        // 测试标签，方便logcat过滤
        private const val TAG = "StackedCardPreview"
    }

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
    
    // 手势优化相关
    private var swipeStartY = 0f // 滑动起始Y坐标
    private var isSwipeCloseInProgress = false // 是否正在进行上滑关闭
    private var swipeCloseProgress = 0f // 上滑关闭进度（0-1）
    private var minSwipeDistance = 80f // 最小滑动距离
    private var maxSwipeDistance = 400f // 最大滑动距离
    private var velocityTracker: VelocityTracker? = null // 速度跟踪器

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
    private var onCardCloseListener: ((String) -> Unit)? = null  // 改为传递URL
    private var onCardRefreshListener: ((Int) -> Unit)? = null
    private var onNewCardRequestedListener: (() -> Unit)? = null
    private var onCardFavoriteListener: ((Int, String) -> Unit)? = null // 收藏监听器：传递索引和URL
    private var onCardCopyUrlListener: ((Int, String) -> Unit)? = null // 复制网址监听器
    private var onCardMuteListener: ((Int) -> Unit)? = null // 静音监听器
    private var onCardAddToDesktopListener: ((Int, String, String) -> Unit)? = null // 添加到桌面监听器：传递索引、URL和标题
    private var onAllCardsRemovedListener: (() -> Unit)? = null
    
    // 底部导航栏高度获取回调
    private var bottomNavHeightProvider: (() -> Int)? = null

    data class WebViewCardData(
        val title: String,
        val url: String,
        val favicon: Bitmap? = null,
        val screenshot: Bitmap? = null
    )

    init {
        // 初始状态设置为不可交互，激活时会重新设置
        isClickable = false
        isFocusable = false
        isFocusableInTouchMode = false
        isEnabled = false

        // 不设置OnTouchListener，让StackedCardPreview自己处理触摸事件
        // 移除阻止触摸的监听器，避免触摸穿透问题
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
        val handled = handleStackedModeTouch(event)
        
        // 如果处理了事件，阻止穿透；否则让事件继续传递
        return handled
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // 直接处理触摸事件，不调用父类的dispatchTouchEvent
        val handled = handleStackedModeTouch(event)
        
        if (handled) {
            // 如果处理了事件，阻止穿透
            return true
        } else {
            // 如果没有处理，让父类处理
            return super.dispatchTouchEvent(event)
        }
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
    fun setWebViewCards(cards: List<WebViewCardData>, selectedIndex: Int = 0) {
        Log.d(
            TAG,
            "setWebViewCards: 设置 ${cards.size} 张卡片，当前模式: 平行 (请求索引=$selectedIndex)"
        )

        cardAnimator?.cancel()
        webViewCards = cards

        if (cards.isEmpty()) {
            Log.d(TAG, "setWebViewCards: 空数据，重置预览状态")
            currentCardIndex = 0
            scrollOffset = 0f
            resetActivationState()
            initializeCardProperties()
            invalidate()
            return
        }

        val normalizedIndex = selectedIndex.coerceIn(0, cards.size - 1)
        if (normalizedIndex != selectedIndex) {
            Log.d(
                TAG,
                "setWebViewCards: 归一化索引 $selectedIndex -> $normalizedIndex，避免越界"
            )
        }
        currentCardIndex = normalizedIndex

        // 重置激活状态，避免遗留的触摸状态影响点击
        resetActivationState()

        initializeCardProperties()
        invalidate()

        Log.d(
            TAG,
            "setWebViewCards: 完成，卡片数据已更新，当前卡片索引=$currentCardIndex，滚动偏移=$scrollOffset"
        )
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

        currentCardIndex = currentCardIndex.coerceIn(0, webViewCards.size - 1)
        scrollOffset = currentCardIndex * cardSpacing

        Log.d(
            TAG,
            "initializeParallelModeProperties: 对齐初始索引=$currentCardIndex, scrollOffset=$scrollOffset"
        )
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
                
                // 初始化速度跟踪器
                velocityTracker = VelocityTracker.obtain()
                velocityTracker?.addMovement(event)
                
                // 记录滑动起始位置
                swipeStartY = event.y
                swipeCloseProgress = 0f

                Log.d(TAG, "开始长按检测，当前激活状态: $isLongPressActivated")
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (isTracking) {
                    val deltaX = event.x - slideStartX
                    val deltaY = event.y - slideStartY
                    val distance = sqrt(deltaX * deltaX + deltaY * deltaY)
                    val currentTime = System.currentTimeMillis()
                    
                    // 更新速度跟踪器
                    velocityTracker?.addMovement(event)

                    // 检查是否超过点击阈值
                    if (isClick && distance > clickThreshold) {
                        isClick = false // 不再是点击操作
                        Log.d("StackedCardPreview", "移动距离超过阈值，不是点击操作")
                    }

                    // 直接处理滑动
                    // 检测是否开始滑动 - 进一步降低阈值提高响应性
                    if (!isLongPressSliding && !isVerticalDragging && distance > 5f) {
                        // 判断是水平还是垂直滑动 - 优化方向判断，让水平滑动更容易触发
                        if (abs(deltaX) > abs(deltaY) * 1.1f) {
                            // 水平滑动更容易触发，降低比例要求
                            isLongPressSliding = true
                            Log.d("StackedCardPreview", "开始水平滑动")
                        } else if (abs(deltaY) > abs(deltaX) * 1.2f) {
                            // 垂直滑动需要更明显的垂直移动
                            isVerticalDragging = true
                            Log.d("StackedCardPreview", "开始垂直拖拽（关闭卡片）")
                            
                            // 垂直滑动开始时提供触觉反馈
                            vibrate(VibrationType.LIGHT)
                        }
                    }

                    if (isLongPressSliding) {
                        // 水平滑动控制卡片
                        handleLongPressSlide(deltaX)
                        // 水平滑动时完全阻止事件穿透，防止文本选择
                        return true
                    } else if (isVerticalDragging) {
                        // 垂直滑动关闭中心卡片
                        handleVerticalDrag(deltaY)
                        // 垂直滑动时也完全阻止事件穿透
                        return true
                    }

                    // 即使没有开始滑动，也要阻止事件穿透，防止意外的文本选择
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

                        if (abs(slideVelocity) > 500f) {
                            // 速度足够快，启动惯性滚动 - 降低阈值提高响应性
                            startInertiaScrollWithoutOpen()
                        } else {
                            // 速度较慢，直接对齐到最近的卡片
                            snapToNearestCard()
                        }
                    } else if (isVerticalDragging) {
                        // 垂直滑动结束，检查是否需要关闭卡片
                        handleVerticalDragEnd()
                    } else {
                        // 如果是点击操作，检查是否点击了按钮
                        if (isClick) {
                            when {
                                isNewCardButtonClicked(event.x, event.y) -> {
                                    Log.d("StackedCardPreview", "检测到新建卡片按钮点击")
                                    onNewCardRequestedListener?.invoke()
                                    vibrate(VibrationType.IMPORTANT) // 重要操作震动
                                }
                                isCloseButtonClicked(event.x, event.y) -> {
                                    Log.d(TAG, "🔴 检测到关闭按钮点击")
                                    closeCurrentCard()
                                    vibrate(VibrationType.HEAVY) // 重要操作震动
                                }
                                isNewCardButtonOnCardClicked(event.x, event.y) -> {
                                    Log.d(TAG, "🟢 检测到卡片上的新建按钮点击")
                                    onNewCardRequestedListener?.invoke()
                                    vibrate(VibrationType.IMPORTANT) // 重要操作震动
                                }
                                isBottomButtonClicked(event.x, event.y, true, false, false, false, false, false) -> {
                                    // 底部新建按钮
                                    Log.d(TAG, "🟢 检测到底部新建按钮点击")
                                    onNewCardRequestedListener?.invoke()
                                    vibrate(VibrationType.IMPORTANT)
                                }
                                isBottomButtonClicked(event.x, event.y, false, true, false, false, false, false) -> {
                                    // 底部收藏按钮
                                    if (currentCardIndex >= 0 && currentCardIndex < webViewCards.size) {
                                        val card = webViewCards[currentCardIndex]
                                        Log.d(TAG, "⭐ 检测到底部收藏按钮点击: ${card.title}")
                                        onCardFavoriteListener?.invoke(currentCardIndex, card.url)
                                        vibrate(VibrationType.IMPORTANT)
                                    }
                                }
                                isBottomButtonClicked(event.x, event.y, false, false, false, false, false, true) -> {
                                    // 底部关闭按钮
                                    Log.d(TAG, "🔴 检测到底部关闭按钮点击")
                                    closeCurrentCard()
                                    vibrate(VibrationType.HEAVY)
                                }
                                isBottomButtonClicked(event.x, event.y, false, false, true, false, false, false) -> {
                                    // 复制网址按钮
                                    if (currentCardIndex >= 0 && currentCardIndex < webViewCards.size) {
                                        val card = webViewCards[currentCardIndex]
                                        Log.d(TAG, "📋 检测到底部复制网址按钮点击: ${card.url}")
                                        onCardCopyUrlListener?.invoke(currentCardIndex, card.url)
                                        vibrate(VibrationType.IMPORTANT)
                                    }
                                }
                                isBottomButtonClicked(event.x, event.y, false, false, false, true, false, false) -> {
                                    // 静音按钮
                                    if (currentCardIndex >= 0 && currentCardIndex < webViewCards.size) {
                                        val card = webViewCards[currentCardIndex]
                                        Log.d(TAG, "🔇 检测到底部静音按钮点击: ${card.title}")
                                        onCardMuteListener?.invoke(currentCardIndex)
                                        vibrate(VibrationType.BASIC)
                                    }
                                }
                                isBottomButtonClicked(event.x, event.y, false, false, false, false, true, false) -> {
                                    // 添加到桌面按钮
                                    if (currentCardIndex >= 0 && currentCardIndex < webViewCards.size) {
                                        val card = webViewCards[currentCardIndex]
                                        Log.d(TAG, "🏠 检测到底部添加到桌面按钮点击: ${card.url}")
                                        onCardAddToDesktopListener?.invoke(currentCardIndex, card.url, card.title)
                                        vibrate(VibrationType.IMPORTANT)
                                    }
                                }
                                else -> {
                                    Log.d("StackedCardPreview", "检测到点击操作，纠正索引后打开当前中心卡片")
                                    // 点击时根据当前位置重新计算最近的中心索引，避免轻微偏移导致错选相邻卡片
                                    if (cardSpacing > 0f && webViewCards.isNotEmpty()) {
                                        val correctedIndex = ((scrollOffset / cardSpacing) + 0.5f).toInt()
                                            .coerceIn(0, webViewCards.size - 1)
                                        if (correctedIndex != currentCardIndex) {
                                            Log.d("StackedCardPreview", "点击校正索引: $currentCardIndex -> $correctedIndex (scrollOffset=$scrollOffset, spacing=$cardSpacing)")
                                            currentCardIndex = correctedIndex
                                        }
                                    }
                                    selectCurrentCardWithFadeIn()
                                    vibrate(VibrationType.BASIC) // 基本操作震动
                                }
                            }
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
     * 处理长按滑动（控制悬浮卡片左右滑动）- 添加阻尼效果
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

        // 添加阻尼效果：应用阻尼系数，让滑动更有阻力感
        val dampingFactor = 0.4f // 增加阻尼系数到40%，让滑动更有阻力感
        val dampedDeltaX = deltaX * (1f - dampingFactor)
        
        // 更新滚动偏移，应用阻尼后的移动距离
        val sensitivity = when {
            abs(slideVelocity) > 3000f -> 2.0f // 极快滑动时降低灵敏度
            abs(slideVelocity) > 2000f -> 1.6f // 快速滑动时适度降低灵敏度
            abs(slideVelocity) > 1000f -> 1.3f // 中等速度时适度降低灵敏度
            else -> 1.1f // 慢速滑动时轻微降低灵敏度
        }
        scrollOffset -= dampedDeltaX * sensitivity

        // 限制滚动范围
        val maxOffset = (webViewCards.size - 1) * cardSpacing
        scrollOffset = scrollOffset.coerceIn(0f, maxOffset)

        // 计算当前中心卡片
        val newCardIndex = (scrollOffset / cardSpacing + 0.5f).toInt()
        if (newCardIndex != currentCardIndex && newCardIndex >= 0 && newCardIndex < webViewCards.size) {
            currentCardIndex = newCardIndex
            Log.d("StackedCardPreview", "滑动切换到卡片: $currentCardIndex (${webViewCards[currentCardIndex].title}) 速度: ${slideVelocity.toInt()}px/s, 灵敏度: $sensitivity")

            // 提供浏览操作的震动反馈
            vibrate(VibrationType.BROWSING)
        }

        // 重新绘制
        invalidate()

        // 更新滑动起点，使滑动更连续
        slideStartX = slideStartX + deltaX * 0.1f // 进一步减少起点更新幅度，保持滑动连续性
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
            // 向上拖拽：关闭卡片 - 优化手势识别
            val swipeDistance = abs(deltaY)
            
            // 计算关闭进度（0-1），基于滑动距离和速度
            swipeCloseProgress = when {
                swipeDistance < minSwipeDistance -> 0f
                swipeDistance > maxSwipeDistance -> 1f
                else -> (swipeDistance - minSwipeDistance) / (maxSwipeDistance - minSwipeDistance)
            }
            
            // 根据进度计算卡片偏移，提供更自然的跟随效果
            centerCardOffsetY = -minSwipeDistance - (swipeDistance - minSwipeDistance) * 0.8f
            
            // 根据滑动速度调整关闭阈值
            val velocity = velocityTracker?.let { 
                it.computeCurrentVelocity(1000)
                abs(it.yVelocity)
            } ?: 0f
            
            // 快速滑动时降低关闭阈值
            val dynamicCloseThreshold = if (velocity > 2000f) {
                closeThreshold * 0.6f // 快速滑动时阈值降低40%
            } else if (velocity > 1000f) {
                closeThreshold * 0.8f // 中等速度时阈值降低20%
            } else {
                closeThreshold
            }
            
            Log.d(TAG, "⬆️ 上滑关闭进度: ${(swipeCloseProgress * 100).toInt()}%, 速度: ${velocity.toInt()}px/s, 动态阈值: $dynamicCloseThreshold")
            
        } else if (deltaY > 0) {
            // 向下拖拽：关闭页面（类似向上拖拽关闭，但方向相反）
            val swipeDistance = abs(deltaY)
            
            // 计算关闭进度（0-1），基于滑动距离和速度
            swipeCloseProgress = when {
                swipeDistance < minSwipeDistance -> 0f
                swipeDistance > maxSwipeDistance -> 1f
                else -> (swipeDistance - minSwipeDistance) / (maxSwipeDistance - minSwipeDistance)
            }
            
            // 根据进度计算卡片偏移，提供更自然的跟随效果
            centerCardOffsetY = minSwipeDistance + (swipeDistance - minSwipeDistance) * 0.8f
            
            // 根据滑动速度调整关闭阈值
            val velocity = velocityTracker?.let { 
                it.computeCurrentVelocity(1000)
                abs(it.yVelocity)
            } ?: 0f
            
            // 快速滑动时降低关闭阈值
            val dynamicCloseThreshold = if (velocity > 2000f) {
                closeThreshold * 0.6f // 快速滑动时阈值降低40%
            } else if (velocity > 1000f) {
                closeThreshold * 0.8f // 中等速度时阈值降低20%
            } else {
                closeThreshold
            }
            
            Log.d(TAG, "⬇️ 下滑关闭进度: ${(swipeCloseProgress * 100).toInt()}%, 速度: ${velocity.toInt()}px/s, 动态阈值: $dynamicCloseThreshold")
        } else {
            centerCardOffsetY = 0f
            swipeCloseProgress = 0f
        }

        // 重新绘制
        invalidate()
    }

    /**
     * 处理垂直拖拽结束
     */
    private fun handleVerticalDragEnd() {
        val maxRefreshOffset = closeThreshold * 0.8f
        
        // 获取滑动速度用于智能判断
        val velocity = velocityTracker?.let { 
            it.computeCurrentVelocity(1000)
            abs(it.yVelocity)
        } ?: 0f
        
        // 动态关闭阈值：考虑滑动速度
        val dynamicCloseThreshold = if (velocity > 2000f) {
            closeThreshold * 0.6f // 快速滑动时阈值降低40%
        } else if (velocity > 1000f) {
            closeThreshold * 0.8f // 中等速度时阈值降低20%
        } else {
            closeThreshold
        }
        
        // 判断是向上还是向下滑动
        val isSwipeDown = centerCardOffsetY > 0
        val isSwipeUp = centerCardOffsetY < 0
        
        // 智能关闭判断：考虑滑动距离、速度和进度（支持向上和向下）
        val shouldClose = when {
            // 向上滑动关闭
            isSwipeUp && centerCardOffsetY < -dynamicCloseThreshold -> true
            isSwipeUp && swipeCloseProgress > 0.7f -> true
            isSwipeUp && velocity > 1500f && centerCardOffsetY < -minSwipeDistance -> true
            // 向下滑动关闭
            isSwipeDown && centerCardOffsetY > dynamicCloseThreshold -> true
            isSwipeDown && swipeCloseProgress > 0.7f -> true
            isSwipeDown && velocity > 1500f && centerCardOffsetY > minSwipeDistance -> true
            else -> false
        }
        
        if (shouldClose) {
            // 超过关闭阈值（向上或向下），关闭中心卡片
            val direction = if (isSwipeDown) "下滑" else "上滑"
            Log.d(TAG, "🗑️ ${direction}关闭中心卡片: $currentCardIndex, 速度: ${velocity.toInt()}px/s, 进度: ${(swipeCloseProgress * 100).toInt()}%")
            closeCurrentCard()
        } else if (centerCardOffsetY > maxRefreshOffset && !isSwipeDown) {
            // 向下超过刷新阈值（且不是关闭操作），刷新当前卡片
            Log.d("StackedCardPreview", "刷新中心卡片: $currentCardIndex")
            refreshCurrentCard()
        } else {
            // 没有超过任何阈值，回弹到原位置
            animateCenterCardReturn()
        }
        
        // 清理速度跟踪器
        velocityTracker?.recycle()
        velocityTracker = null
        swipeCloseProgress = 0f
    }



    /**
     * 关闭当前中心卡片 - 增强版本
     */
    private fun closeCurrentCard() {
        if (currentCardIndex < 0 || currentCardIndex >= webViewCards.size) {
            Log.w("StackedCardPreview", "❌ 无法关闭卡片：无效的卡片索引 $currentCardIndex，总卡片数：${webViewCards.size}")
            return
        }

        val cardToClose = webViewCards[currentCardIndex]
        Log.d(TAG, "🔥 开始关闭卡片：${cardToClose.title} (${cardToClose.url})")

        // 提供强烈的触觉反馈
        vibrate(VibrationType.HEAVY)

        // 播放关闭动画
        animateCardClose()
    }

    /**
     * 卡片关闭动画 - 修复版本（支持向上和向下关闭）
     */
    private fun animateCardClose() {
        // 关键修复：在动画开始前就获取要关闭的卡片URL并通知外部系统销毁WebView
        val cardToClose = webViewCards[currentCardIndex]
        val isSwipeDown = centerCardOffsetY > 0
        val closeDirection = if (isSwipeDown) "向下" else "向上"
        Log.d("StackedCardPreview", "开始${closeDirection}关闭动画，准备销毁WebView: ${cardToClose.url}")

        // 创建更流畅的关闭动画（根据滑动方向）
        val startOffset = centerCardOffsetY
        val endOffset = if (isSwipeDown) {
            height.toFloat() // 向下滑动关闭，卡片向下移出屏幕
        } else {
            -height.toFloat() // 向上滑动关闭，卡片向上移出屏幕
        }

        ValueAnimator.ofFloat(startOffset, endOffset).apply {
            duration = 300 // 缩短动画时间，减少WebView处于不稳定状态的时间
            interpolator = android.view.animation.AccelerateInterpolator() // 加速动画，快速完成关闭

            addUpdateListener { animator ->
                centerCardOffsetY = animator.animatedValue as Float

                // 计算动画进度，用于视觉反馈（支持向上和向下）
                val offsetDiff = abs(endOffset - startOffset)
                val currentDiff = abs(centerCardOffsetY - startOffset)
                val progress = if (offsetDiff > 0) (currentDiff / offsetDiff).coerceIn(0f, 1f) else 0f

                // 根据进度调整卡片透明度，提供更自然的消失效果
                val alpha = (1f - progress * 0.8f).coerceAtLeast(0.2f)
                cardPaint.alpha = (alpha * 255).toInt()

                invalidate()
            }

            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: android.animation.Animator) {
                    // 动画开始时提供触觉反馈
                    vibrate(VibrationType.HEAVY)

                    // 关键修复：动画开始时立即通知外部系统销毁WebView
                    // 这样可以确保WebView在动画过程中就开始停止加载和清理资源
                    Log.d(TAG, "🎬 关闭动画开始，立即通知外部销毁WebView: ${cardToClose.title} (${cardToClose.url})")
                    onCardCloseListener?.invoke(cardToClose.url)
                }

                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // 关键修复：动画结束后立即更新本地数据，不再延迟等待
                    // 因为WebView销毁已经在动画开始时触发

                    // 从本地数据中移除卡片
                    removeCard(currentCardIndex)

                    // 重置卡片透明度
                    cardPaint.alpha = 255

                    // 检查是否还有卡片
                    if (webViewCards.isEmpty()) {
                        // 没有卡片了，通知外部
                        onAllCardsRemovedListener?.invoke()

                        // 隐藏预览
                        visibility = View.GONE
                    } else {
                        // 调整当前卡片索引
                        if (currentCardIndex >= webViewCards.size) {
                            currentCardIndex = webViewCards.size - 1
                        }

                        // 重置卡片偏移
                        centerCardOffsetY = 0f
                        invalidate()
                    }

                    Log.d(TAG, "✅ 卡片关闭动画完成，本地数据已更新：${cardToClose.title}")

                    // 延迟再次通知外部保存状态，确保数据一致性
                    post {
                        onCardCloseListener?.invoke(cardToClose.url)
                        Log.d(TAG, "🔄 延迟再次通知外部保存状态")
                    }
                }
            })
            start()
        }
    }

    /**
     * 中心卡片回弹动画 - 优化版本
     */
    private fun animateCenterCardReturn() {
        if (centerCardOffsetY == 0f) return

        // 根据滑动距离和速度调整回弹动画
        val swipeDistance = abs(centerCardOffsetY)
        val velocity = velocityTracker?.let { 
            it.computeCurrentVelocity(1000)
            abs(it.yVelocity)
        } ?: 0f
        
        // 动态调整动画时长：滑动距离越大，速度越快，动画时间越短
        val baseDuration = 300L
        val distanceFactor = (swipeDistance / closeThreshold).coerceIn(0.1f, 2.0f)
        val velocityFactor = (velocity / 1000f).coerceIn(0.5f, 2.0f)
        val dynamicDuration = (baseDuration / distanceFactor / velocityFactor).toLong().coerceIn(150L, 500L)
        
        val startOffset = centerCardOffsetY
        
        ValueAnimator.ofFloat(centerCardOffsetY, 0f).apply {
            duration = dynamicDuration
            interpolator = android.view.animation.OvershootInterpolator(0.8f) // 轻微过冲，更自然
            
            addUpdateListener { animator ->
                centerCardOffsetY = animator.animatedValue as Float
                
                // 根据进度恢复卡片透明度
                val progress = (centerCardOffsetY - startOffset) / (0f - startOffset)
                val alpha = 0.2f + progress * 0.8f // 从20%透明度恢复到100%
                cardPaint.alpha = (alpha * 255).toInt()
                
                invalidate()
            }
            
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // 动画结束时确保透明度完全恢复
                    cardPaint.alpha = 255
                    invalidate()
                    
                    // 提供轻微触觉反馈
                    if (swipeDistance > minSwipeDistance) {
                        vibrate(VibrationType.LIGHT)
                    }
                }
            })
            
            start()
        }
        
        Log.d("StackedCardPreview", "回弹动画: 距离${swipeDistance.toInt()}px, 速度${velocity.toInt()}px/s, 时长${dynamicDuration}ms")
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
            // 没有卡片了，隐藏预览并通知外部
            visibility = View.GONE
            onAllCardsRemovedListener?.invoke()
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

        Log.d(TAG, "🗂️ 移除卡片后，剩余 ${webViewCards.size} 张卡片")
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
        
        // 绘制滑动关闭进度指示器（如果有向上或向下滑动操作）
        if (isVerticalDragging && centerCardOffsetY != 0f) {
            drawSwipeCloseIndicator(canvas, centerX, centerY)
        }
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
    
    /**
     * 绘制上滑关闭进度指示器
     */
    private fun drawSwipeCloseIndicator(canvas: Canvas, centerX: Float, centerY: Float) {
        val indicatorWidth = 200f
        val indicatorHeight = 8f
        val indicatorY = centerY + baseCardHeight / 2f + 100f // 在卡片下方显示
        
        // 背景轨道
        val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = 80
            style = Paint.Style.FILL
        }
        
        // 进度条
        val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2196F3") // 蓝色进度条
            alpha = 200
            style = Paint.Style.FILL
        }
        
        // 绘制背景轨道
        canvas.drawRoundRect(
            centerX - indicatorWidth / 2f,
            indicatorY,
            centerX + indicatorWidth / 2f,
            indicatorY + indicatorHeight,
            indicatorHeight / 2f,
            indicatorHeight / 2f,
            trackPaint
        )
        
        // 绘制进度条
        val progressWidth = indicatorWidth * swipeCloseProgress
        canvas.drawRoundRect(
            centerX - indicatorWidth / 2f,
            indicatorY,
            centerX - indicatorWidth / 2f + progressWidth,
            indicatorY + indicatorHeight,
            indicatorHeight / 2f,
            indicatorHeight / 2f,
            progressPaint
        )
        
        // 绘制关闭提示文字
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 28f
            textAlign = Paint.Align.CENTER
            alpha = (255 * swipeCloseProgress).toInt()
        }
        
        // 判断是向上还是向下滑动
        val isSwipeDown = centerCardOffsetY > 0
        val closeText = when {
            swipeCloseProgress > 0.7f -> "松手关闭"
            swipeCloseProgress > 0.3f -> if (isSwipeDown) "继续下滑关闭" else "继续上滑关闭"
            else -> if (isSwipeDown) "下滑关闭" else "上滑关闭"
        }
        
        canvas.drawText(
            closeText,
            centerX,
            indicatorY - 20f,
            textPaint
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
        } ?: run {
            // 如果没有截图，绘制占位符内容
            drawPlaceholderContent(canvas, cardData, left, top, width, height, scale, alpha)
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

        // 绘制右上角红色关闭按钮（保留）
        drawCloseButton(canvas, left, top, width, scale, alpha)

        // 绘制卡片下方的按钮：新建、收藏、关闭
        drawBottomButtons(canvas, left, top, width, height, scale, alpha)
    }

    /**
     * 绘制占位符内容（当没有截图时）
     */
    private fun drawPlaceholderContent(
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
        val contentLeft = left + padding
        val contentTop = top + padding
        val contentWidth = width - padding * 2
        val contentHeight = height - padding * 3 - 40f * scale // 为标题留空间

        // 绘制背景色
        val placeholderPaint = Paint().apply {
            color = Color.parseColor("#F5F5F5")
            this.alpha = (255 * alpha).toInt()
        }
        canvas.drawRoundRect(
            contentLeft,
            contentTop,
            contentLeft + contentWidth,
            contentTop + contentHeight,
            8f * scale,
            8f * scale,
            placeholderPaint
        )

        // 绘制URL信息
        val urlText = cardData.url.takeIf { it.isNotEmpty() } ?: "无URL"
        val displayUrl = if (urlText.length > 30) {
            urlText.substring(0, 30) + "..."
        } else {
            urlText
        }

        val urlPaint = Paint(textPaint).apply {
            textSize = 24f * scale
            color = Color.parseColor("#666666")
            this.alpha = (255 * alpha).toInt()
        }

        // 计算文本位置（居中）
        val textBounds = android.graphics.Rect()
        urlPaint.getTextBounds(displayUrl, 0, displayUrl.length, textBounds)
        val textX = contentLeft + (contentWidth - textBounds.width()) / 2f
        val textY = contentTop + contentHeight / 2f + textBounds.height() / 2f

        canvas.drawText(displayUrl, textX, textY, urlPaint)

        // 绘制一个简单的网页图标
        val iconSize = 32f * scale
        val iconX = contentLeft + (contentWidth - iconSize) / 2f
        val iconY = textY - textBounds.height() - 20f * scale

        val iconPaint = Paint().apply {
            color = Color.parseColor("#4CAF50")
            this.alpha = (255 * alpha).toInt()
            style = Paint.Style.FILL
        }

        // 绘制简单的网页图标（矩形）
        canvas.drawRoundRect(
            iconX,
            iconY,
            iconX + iconSize,
            iconY + iconSize,
            4f * scale,
            4f * scale,
            iconPaint
        )
    }

    /**
     * 绘制卡片右上角的红色关闭按钮
     */
    private fun drawCloseButton(
        canvas: Canvas,
        cardLeft: Float,
        cardTop: Float,
        cardWidth: Float,
        scale: Float,
        alpha: Float
    ) {
        // 增大按钮尺寸，提高点击便利性
        val buttonSize = 60f * scale // 从40f增加到60f
        val buttonMargin = 8f * scale // 减少边距，让按钮更靠近边缘
        val buttonX = cardLeft + cardWidth - buttonMargin - buttonSize / 2f
        val buttonY = cardTop + buttonMargin + buttonSize / 2f

        // 绘制按钮阴影
        val shadowPaint = Paint().apply {
            color = Color.parseColor("#40000000")
            isAntiAlias = true
            this.alpha = (255 * alpha * 0.6f).toInt()
        }
        canvas.drawCircle(buttonX + 2f, buttonY + 2f, buttonSize / 2f, shadowPaint)

        // 绘制按钮背景（红色背景）
        val buttonBackgroundPaint = Paint().apply {
            color = Color.parseColor("#F44336") // 红色背景
            isAntiAlias = true
            this.alpha = (255 * alpha).toInt()
        }
        canvas.drawCircle(buttonX, buttonY, buttonSize / 2f, buttonBackgroundPaint)

        // 绘制按钮边框
        val buttonBorderPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f * scale
            isAntiAlias = true
            this.alpha = (255 * alpha).toInt()
        }
        canvas.drawCircle(buttonX, buttonY, buttonSize / 2f - 1f, buttonBorderPaint)

        // 绘制X图标
        val xPaint = Paint().apply {
            color = Color.WHITE
            strokeWidth = 4f * scale // 增加X符号粗细
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            this.alpha = (255 * alpha).toInt()
        }

        val xSize = buttonSize * 0.35f // 稍微增大X符号
        // 左上到右下的线
        canvas.drawLine(
            buttonX - xSize / 2f,
            buttonY - xSize / 2f,
            buttonX + xSize / 2f,
            buttonY + xSize / 2f,
            xPaint
        )
        // 右上到左下的线
        canvas.drawLine(
            buttonX + xSize / 2f,
            buttonY - xSize / 2f,
            buttonX - xSize / 2f,
            buttonY + xSize / 2f,
            xPaint
        )
    }

    /**
     * 绘制卡片下方的按钮：新建、收藏、关闭，以及复制网址、静音、添加到桌面
     */
    private fun drawBottomButtons(
        canvas: Canvas,
        cardLeft: Float,
        cardTop: Float,
        cardWidth: Float,
        cardHeight: Float,
        scale: Float,
        alpha: Float
    ) {
        // 增大按钮尺寸
        val buttonSize = 70f * scale // 从50f增加到70f
        val buttonSpacing = 15f * scale
        val buttonMargin = 25f * scale
        val textSpacing = 12f * scale // 按钮和文字之间的间距
        
        // 第一行按钮：新建、收藏、关闭
        val firstRowButtons = 3
        val firstRowTotalWidth = buttonSize * firstRowButtons + buttonSpacing * (firstRowButtons - 1)
        val firstRowStartX = cardLeft + (cardWidth - firstRowTotalWidth) / 2f
        val firstRowButtonY = cardTop + cardHeight + buttonMargin + buttonSize / 2f
        
        // 绘制第一行按钮
        drawBottomButtonWithLabel(canvas, firstRowStartX, firstRowButtonY, buttonSize, scale, alpha, Color.parseColor("#4CAF50"), "新建", true, false, false, false, false)
        
        val favoriteX = firstRowStartX + buttonSize + buttonSpacing
        drawBottomButtonWithLabel(canvas, favoriteX, firstRowButtonY, buttonSize, scale, alpha, Color.parseColor("#FF9800"), "收藏", false, true, false, false, false)
        
        val closeX = favoriteX + buttonSize + buttonSpacing
        drawBottomButtonWithLabel(canvas, closeX, firstRowButtonY, buttonSize, scale, alpha, Color.parseColor("#F44336"), "关闭", false, false, false, false, true)
        
        // 第二行按钮：复制网址、静音、添加到桌面
        val secondRowButtons = 3
        val secondRowTotalWidth = buttonSize * secondRowButtons + buttonSpacing * (secondRowButtons - 1)
        val secondRowStartX = cardLeft + (cardWidth - secondRowTotalWidth) / 2f
        val textHeight = 24f * scale // 文字高度
        val secondRowButtonY = firstRowButtonY + buttonSize / 2f + textSpacing + textHeight + buttonMargin // 第一行按钮下方
        
        drawBottomButtonWithLabel(canvas, secondRowStartX, secondRowButtonY, buttonSize, scale, alpha, Color.parseColor("#2196F3"), "复制网址", false, false, true, false, false)
        
        val muteX = secondRowStartX + buttonSize + buttonSpacing
        drawBottomButtonWithLabel(canvas, muteX, secondRowButtonY, buttonSize, scale, alpha, Color.parseColor("#9C27B0"), "静音", false, false, false, true, false)
        
        val desktopX = muteX + buttonSize + buttonSpacing
        drawBottomButtonWithLabel(canvas, desktopX, secondRowButtonY, buttonSize, scale, alpha, Color.parseColor("#607D8B"), "添加到桌面", false, false, false, false, false)
    }
    
    /**
     * 绘制底部单个按钮（带标签文字）
     */
    private fun drawBottomButtonWithLabel(
        canvas: Canvas,
        x: Float,
        y: Float,
        size: Float,
        scale: Float,
        alpha: Float,
        bgColor: Int,
        label: String,
        isNew: Boolean,
        isFavorite: Boolean,
        isCopy: Boolean,
        isMute: Boolean,
        isClose: Boolean
    ) {
        // 绘制阴影
        val shadowPaint = Paint().apply {
            color = Color.parseColor("#40000000")
            isAntiAlias = true
            this.alpha = (255 * alpha * 0.6f).toInt()
        }
        canvas.drawCircle(x + 2f, y + 2f, size / 2f, shadowPaint)
        
        // 绘制背景
        val bgPaint = Paint().apply {
            color = bgColor
            isAntiAlias = true
            this.alpha = (255 * alpha).toInt()
        }
        canvas.drawCircle(x, y, size / 2f, bgPaint)
        
        // 绘制边框
        val borderPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f * scale
            isAntiAlias = true
            this.alpha = (255 * alpha).toInt()
        }
        canvas.drawCircle(x, y, size / 2f - 1f, borderPaint)
        
        // 绘制图标
        val iconPaint = Paint().apply {
            color = Color.WHITE
            strokeWidth = 4f * scale
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
            this.alpha = (255 * alpha).toInt()
        }
        
        if (isNew) {
            // 绘制加号
            val plusSize = size * 0.35f
            canvas.drawLine(x - plusSize / 2f, y, x + plusSize / 2f, y, iconPaint)
            canvas.drawLine(x, y - plusSize / 2f, x, y + plusSize / 2f, iconPaint)
        } else if (isFavorite) {
            // 绘制星星（简化版）
            val starSize = size * 0.3f
            val path = android.graphics.Path()
            path.moveTo(x, y - starSize)
            path.lineTo(x + starSize * 0.3f, y - starSize * 0.3f)
            path.lineTo(x + starSize, y - starSize * 0.3f)
            path.lineTo(x + starSize * 0.4f, y + starSize * 0.2f)
            path.lineTo(x + starSize * 0.6f, y + starSize)
            path.lineTo(x, y + starSize * 0.4f)
            path.lineTo(x - starSize * 0.6f, y + starSize)
            path.lineTo(x - starSize * 0.4f, y + starSize * 0.2f)
            path.lineTo(x - starSize, y - starSize * 0.3f)
            path.lineTo(x - starSize * 0.3f, y - starSize * 0.3f)
            path.close()
            iconPaint.style = Paint.Style.FILL
            canvas.drawPath(path, iconPaint)
        } else if (isCopy) {
            // 绘制复制图标（两个重叠的矩形）
            val rectSize = size * 0.3f
            val rectPaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.STROKE
                strokeWidth = 3f * scale
                isAntiAlias = true
                this.alpha = (255 * alpha).toInt()
            }
            // 第一个矩形
            canvas.drawRect(x - rectSize / 2f, y - rectSize / 2f, x + rectSize / 2f, y + rectSize / 2f, rectPaint)
            // 第二个矩形（偏移）
            canvas.drawRect(x - rectSize / 2f + rectSize * 0.2f, y - rectSize / 2f + rectSize * 0.2f, 
                x + rectSize / 2f + rectSize * 0.2f, y + rectSize / 2f + rectSize * 0.2f, rectPaint)
        } else if (isMute) {
            // 绘制静音图标（扬声器带斜线）
            val speakerSize = size * 0.3f
            // 扬声器主体（三角形）
            val path = android.graphics.Path()
            path.moveTo(x - speakerSize / 2f, y - speakerSize / 3f)
            path.lineTo(x - speakerSize / 4f, y)
            path.lineTo(x - speakerSize / 2f, y + speakerSize / 3f)
            path.close()
            iconPaint.style = Paint.Style.FILL
            canvas.drawPath(path, iconPaint)
            // 声波（两个半圆）
            iconPaint.style = Paint.Style.STROKE
            canvas.drawArc(x - speakerSize / 4f, y - speakerSize / 2f, x + speakerSize / 2f, y + speakerSize / 2f, -90f, 180f, false, iconPaint)
            canvas.drawArc(x - speakerSize / 8f, y - speakerSize, x + speakerSize, y + speakerSize, -90f, 180f, false, iconPaint)
            // 静音斜线
            val linePaint = Paint().apply {
                color = Color.WHITE
                strokeWidth = 4f * scale
                isAntiAlias = true
                strokeCap = Paint.Cap.ROUND
                this.alpha = (255 * alpha).toInt()
            }
            canvas.drawLine(x + speakerSize / 2f, y - speakerSize / 2f, x + speakerSize, y + speakerSize / 2f, linePaint)
        } else if (isClose) {
            // 绘制X（关闭）
            val xSize = size * 0.35f
            canvas.drawLine(x - xSize / 2f, y - xSize / 2f, x + xSize / 2f, y + xSize / 2f, iconPaint)
            canvas.drawLine(x + xSize / 2f, y - xSize / 2f, x - xSize / 2f, y + xSize / 2f, iconPaint)
        } else {
            // 绘制添加到桌面图标（房子）
            val houseSize = size * 0.35f
            val path = android.graphics.Path()
            path.moveTo(x, y - houseSize / 2f)
            path.lineTo(x - houseSize / 2f, y)
            path.lineTo(x - houseSize / 4f, y)
            path.lineTo(x - houseSize / 4f, y + houseSize / 2f)
            path.lineTo(x + houseSize / 4f, y + houseSize / 2f)
            path.lineTo(x + houseSize / 4f, y)
            path.lineTo(x + houseSize / 2f, y)
            path.close()
            iconPaint.style = Paint.Style.FILL
            canvas.drawPath(path, iconPaint)
        }
        
        // 绘制文字标签
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 20f * scale
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            this.alpha = (255 * alpha).toInt()
            setShadowLayer(2f, 0f, 1f, Color.parseColor("#80000000"))
        }
        val textSpacing = 12f * scale // 按钮和文字之间的间距
        val textY = y + size / 2f + textSpacing
        canvas.drawText(label, x, textY, textPaint)
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
    fun setOnCardCloseListener(listener: (String) -> Unit) {
        onCardCloseListener = listener
    }

    /**
     * 设置卡片刷新监听器
     */
    fun setOnCardRefreshListener(listener: (Int) -> Unit) {
        onCardRefreshListener = listener
    }

    /**
     * 设置所有卡片移除监听器
     */
    fun setOnAllCardsRemovedListener(listener: () -> Unit) {
        onAllCardsRemovedListener = listener
    }

    /**
     * 设置新建卡片请求监听器
     */
    fun setOnNewCardRequestedListener(listener: () -> Unit) {
        onNewCardRequestedListener = listener
    }
    
    /**
     * 设置卡片收藏监听器
     */
    fun setOnCardFavoriteListener(listener: (Int, String) -> Unit) {
        onCardFavoriteListener = listener
    }
    
    /**
     * 设置复制网址监听器
     */
    fun setOnCardCopyUrlListener(listener: (Int, String) -> Unit) {
        onCardCopyUrlListener = listener
    }
    
    /**
     * 设置静音监听器
     */
    fun setOnCardMuteListener(listener: (Int) -> Unit) {
        onCardMuteListener = listener
    }
    
    /**
     * 设置添加到桌面监听器
     */
    fun setOnCardAddToDesktopListener(listener: (Int, String, String) -> Unit) {
        onCardAddToDesktopListener = listener
    }
    
    /**
     * 检查是否点击了底部按钮
     */
    private fun isBottomButtonClicked(
        x: Float, 
        y: Float, 
        isNew: Boolean, 
        isFavorite: Boolean, 
        isCopy: Boolean,
        isMute: Boolean,
        isDesktop: Boolean,
        isClose: Boolean
    ): Boolean {
        if (webViewCards.isEmpty() || currentCardIndex < 0 || currentCardIndex >= webViewCards.size) {
            return false
        }
        
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val centerX = viewWidth / 2f
        val centerY = viewHeight / 2f
        
        // 计算当前中心卡片的位置
        val cardWidth = baseCardWidth
        val cardHeight = baseCardHeight
        val cardLeft = centerX - cardWidth / 2f
        val cardTop = centerY - cardHeight / 2f + centerCardOffsetY
        
        // 计算按钮位置（与绘制时保持一致）
        val buttonSize = 70f
        val buttonSpacing = 15f
        val buttonMargin = 25f
        val textSpacing = 12f
        val textHeight = 24f
        
        // 第一行按钮
        val firstRowButtons = 3
        val firstRowTotalWidth = buttonSize * firstRowButtons + buttonSpacing * (firstRowButtons - 1)
        val firstRowStartX = cardLeft + (cardWidth - firstRowTotalWidth) / 2f
        val firstRowButtonY = cardTop + cardHeight + buttonMargin + buttonSize / 2f
        
        // 第二行按钮
        val secondRowButtons = 3
        val secondRowTotalWidth = buttonSize * secondRowButtons + buttonSpacing * (secondRowButtons - 1)
        val secondRowStartX = cardLeft + (cardWidth - secondRowTotalWidth) / 2f
        val secondRowButtonY = firstRowButtonY + buttonSize / 2f + textSpacing + textHeight + buttonMargin
        
        val (buttonX, buttonY) = when {
            isNew -> Pair(firstRowStartX, firstRowButtonY)
            isFavorite -> Pair(firstRowStartX + buttonSize + buttonSpacing, firstRowButtonY)
            isClose -> Pair(firstRowStartX + (buttonSize + buttonSpacing) * 2, firstRowButtonY)
            isCopy -> Pair(secondRowStartX, secondRowButtonY)
            isMute -> Pair(secondRowStartX + buttonSize + buttonSpacing, secondRowButtonY)
            isDesktop -> Pair(secondRowStartX + (buttonSize + buttonSpacing) * 2, secondRowButtonY)
            else -> return false
        }
        
        val distance = sqrt((x - buttonX) * (x - buttonX) + (y - buttonY) * (y - buttonY))
        return distance <= buttonSize / 2f
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

        // 绘制新建卡片按钮（在指示器右侧）
        drawNewCardButton(canvas, viewWidth, viewHeight, indicatorY)
    }

    /**
     * 检查是否点击了新建卡片按钮（在指示器区域）
     */
    private fun isNewCardButtonClicked(x: Float, y: Float): Boolean {
        val buttonSize = 70f
        val buttonMargin = 50f
        val buttonX = width - buttonMargin - buttonSize / 2f
        val buttonY = height / 2f - baseCardHeight / 2f - 60f // 与指示器位置一致

        val distance = sqrt((x - buttonX) * (x - buttonX) + (y - buttonY) * (y - buttonY))
        return distance <= buttonSize / 2f
    }

    /**
     * 检查是否点击了当前中心卡片的关闭按钮
     */
    private fun isCloseButtonClicked(x: Float, y: Float): Boolean {
        if (webViewCards.isEmpty() || currentCardIndex < 0 || currentCardIndex >= webViewCards.size) {
            return false
        }

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val centerX = viewWidth / 2f
        val centerY = viewHeight / 2f

        // 计算当前中心卡片的位置
        val cardWidth = baseCardWidth
        val cardHeight = baseCardHeight
        val cardLeft = centerX - cardWidth / 2f
        val cardTop = centerY - cardHeight / 2f + centerCardOffsetY

        // 计算关闭按钮的位置（与绘制时保持一致）
        val buttonSize = 60f // 更新为新的按钮尺寸
        val buttonMargin = 8f // 更新为新的边距
        val buttonX = cardLeft + cardWidth - buttonMargin - buttonSize / 2f
        val buttonY = cardTop + buttonMargin + buttonSize / 2f

        val distance = sqrt((x - buttonX) * (x - buttonX) + (y - buttonY) * (y - buttonY))
        return distance <= buttonSize / 2f
    }

    /**
     * 检查是否点击了当前中心卡片的新建按钮
     */
    private fun isNewCardButtonOnCardClicked(x: Float, y: Float): Boolean {
        if (webViewCards.isEmpty() || currentCardIndex < 0 || currentCardIndex >= webViewCards.size) {
            return false
        }

        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val centerX = viewWidth / 2f
        val centerY = viewHeight / 2f

        // 计算当前中心卡片的位置
        val cardWidth = baseCardWidth
        val cardHeight = baseCardHeight
        val cardLeft = centerX - cardWidth / 2f
        val cardTop = centerY - cardHeight / 2f + centerCardOffsetY

        // 计算新建按钮的位置（与绘制时保持一致）
        val buttonSize = 60f // 更新为新的按钮尺寸
        val buttonMargin = 8f // 更新为新的边距
        val buttonX = cardLeft + buttonMargin + buttonSize / 2f
        val buttonY = cardTop + buttonMargin + buttonSize / 2f

        val distance = sqrt((x - buttonX) * (x - buttonX) + (y - buttonY) * (y - buttonY))
        return distance <= buttonSize / 2f
    }

    /**
     * 绘制新建卡片按钮
     */
    private fun drawNewCardButton(canvas: Canvas, viewWidth: Float, viewHeight: Float, indicatorY: Float) {
        val buttonSize = 70f
        val buttonMargin = 50f
        val buttonX = viewWidth - buttonMargin - buttonSize / 2f
        val buttonY = indicatorY

        // 绘制按钮阴影
        val shadowPaint = Paint().apply {
            color = Color.parseColor("#40000000")
            isAntiAlias = true
        }
        canvas.drawCircle(buttonX + 3f, buttonY + 3f, buttonSize / 2f, shadowPaint)

        // 绘制按钮背景（绿色背景）
        val buttonBackgroundPaint = Paint().apply {
            color = Color.parseColor("#4CAF50") // 绿色背景
            isAntiAlias = true
        }

        canvas.drawCircle(buttonX, buttonY, buttonSize / 2f, buttonBackgroundPaint)

        // 绘制按钮边框（更粗的边框）
        val buttonBorderPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }

        canvas.drawCircle(buttonX, buttonY, buttonSize / 2f - 3f, buttonBorderPaint)

        // 绘制加号图标（更粗更明显）
        val plusPaint = Paint().apply {
            color = Color.WHITE
            strokeWidth = 5f
            isAntiAlias = true
            strokeCap = Paint.Cap.ROUND
        }

        val plusSize = buttonSize * 0.35f
        // 水平线
        canvas.drawLine(
            buttonX - plusSize / 2f,
            buttonY,
            buttonX + plusSize / 2f,
            buttonY,
            plusPaint
        )
        // 垂直线
        canvas.drawLine(
            buttonX,
            buttonY - plusSize / 2f,
            buttonX,
            buttonY + plusSize / 2f,
            plusPaint
        )

        // 绘制按钮文字（更大更清晰）
        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = 32f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(3f, 0f, 2f, Color.parseColor("#80000000"))
        }

        val textY = buttonY + buttonSize / 2f + 40f
        canvas.drawText("新建", buttonX, textY, textPaint)

        // 绘制提示文字（在按钮下方）
        val hintPaint = Paint().apply {
            color = Color.parseColor("#CCFFFFFF")
            textSize = 24f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            setShadowLayer(2f, 0f, 1f, Color.parseColor("#80000000"))
        }

        val hintY = textY + 30f
        canvas.drawText("点击添加", buttonX, hintY, hintPaint)
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
        Log.d(TAG, "启用平行预览模式交互")

        // 设置为可交互
        isClickable = true
        isFocusable = true
        isEnabled = true

        // 重置触摸状态
        resetActivationState()

        Log.d(TAG, "平行预览模式交互已启用")
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        cardAnimator?.cancel()
    }

    /**
     * 测试方法：打印当前状态信息，方便调试
     */
    fun printDebugInfo() {
        Log.d(TAG, "=== StackedCardPreview 调试信息 ===")
        Log.d(TAG, "卡片总数: ${webViewCards.size}")
        Log.d(TAG, "当前卡片索引: $currentCardIndex")
        Log.d(TAG, "可见性: ${if (visibility == View.VISIBLE) "VISIBLE" else "GONE/INVISIBLE"}")
        Log.d(TAG, "是否可点击: $isClickable")
        Log.d(TAG, "是否可聚焦: $isFocusable")
        Log.d(TAG, "中心卡片偏移Y: $centerCardOffsetY")
        Log.d(TAG, "滚动偏移: $scrollOffset")

        webViewCards.forEachIndexed { index, card ->
            Log.d(TAG, "卡片 $index: ${card.title} (${card.url})")
        }
        Log.d(TAG, "=== 调试信息结束 ===")
    }

    /**
     * 测试方法：模拟关闭当前卡片
     */
    fun testCloseCurrentCard() {
        Log.d(TAG, "🧪 测试：模拟关闭当前卡片")
        printDebugInfo()
        closeCurrentCard()
    }

    /**
     * 测试方法：检查SharedPreferences中的保存状态
     */
    fun checkSavedState() {
        try {
            val sharedPreferences = context.getSharedPreferences("gesture_cards_state", Context.MODE_PRIVATE)
            val savedUrls = sharedPreferences.getStringSet("floating_card_urls", emptySet()) ?: emptySet()

            Log.d(TAG, "=== SharedPreferences 状态检查 ===")
            Log.d(TAG, "保存的URL数量: ${savedUrls.size}")
            savedUrls.forEachIndexed { index, url ->
                Log.d(TAG, "保存的URL $index: $url")
            }
            Log.d(TAG, "=== 状态检查结束 ===")
        } catch (e: Exception) {
            Log.e(TAG, "检查保存状态失败", e)
        }
    }
}
