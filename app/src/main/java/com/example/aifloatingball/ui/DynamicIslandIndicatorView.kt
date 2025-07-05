package com.example.aifloatingball.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager
import kotlin.math.abs

class DynamicIslandIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var settingsManager: SettingsManager = SettingsManager.getInstance(context)
    
    // Paint objects for different elements
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // 小横条的基础尺寸
    private val baseWidth = 72f  // dp
    private val baseHeight = 6f  // dp
    private var cornerRadius = 0f
    private var bounds = RectF()
    
    // Colors
    private var backgroundColor = Color.BLACK
    private var glowColor = Color.WHITE
    private var shadowColor = Color.BLACK
    
    // Animation properties
    private var currentScale = 1f
    private var currentGlowAlpha = 0f
    private var currentIconAlpha = 0f
    private var currentWidth = 1f
    private var currentHeight = 1f
    private var breatheScale = 1f
    private var pulseAlpha = 1f
    private var rippleScale = 0f
    private var rippleAlpha = 0f
    
    // Icon properties
    private var currentIcon: Drawable? = null
    private var showIcon = false
    
    // Touch and drag properties
    private var isPressed = false
    private var isDragging = false
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var startTouchX = 0f
    private var startTouchY = 0f
    private var touchDownTime = 0L
    private val longPressThreshold = 500L
    private val dragThreshold = 20f
    
    // Handlers and animators
    private val handler = Handler(Looper.getMainLooper())
    private var hideIconRunnable: Runnable? = null
    private var longPressRunnable: Runnable? = null
    private var breatheAnimator: ValueAnimator? = null
    private var currentAnimator: AnimatorSet? = null
    private var pulseAnimator: ValueAnimator? = null
    private var rippleAnimator: ValueAnimator? = null
    
    // Listeners
    private var onLongClickListener: OnLongClickListener? = null
    private var onDragListener: OnDragListener? = null
    
    interface OnDragListener {
        fun onDragStart()
        fun onDragMove(deltaX: Float, deltaY: Float)
        fun onDragEnd()
    }
    
    init {
        setupView()
        updateTheme()
        startIdleAnimation()
    }
    
    private fun setupView() {
        // Calculate dimensions based on density
        val density = resources.displayMetrics.density
        cornerRadius = baseHeight * density / 2f  // 完全圆角的小横条
        
        // Setup paint objects
        backgroundPaint.style = Paint.Style.FILL
        glowPaint.style = Paint.Style.FILL
        shadowPaint.style = Paint.Style.FILL
        iconPaint.style = Paint.Style.FILL
        
        // Set layer type for shadow effects
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        
        // Apply transparency settings
        updateAlpha()
    }
    
    private fun updateTheme() {
        val isDarkMode = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        
        // Update colors based on theme
        backgroundColor = if (isDarkMode) {
            Color.parseColor("#FFFFFF")  // 深色模式下用白色
        } else {
            Color.parseColor("#1C1C1E")  // 浅色模式下用深色
        }
        
        glowColor = if (isDarkMode) {
            Color.parseColor("#FFFFFF")  // 白色发光
        } else {
            Color.parseColor("#007AFF")  // iOS蓝色发光
        }
        
        shadowColor = Color.parseColor("#40000000")  // 半透明黑色阴影
        
        backgroundPaint.color = backgroundColor
        glowPaint.color = glowColor
        shadowPaint.color = shadowColor
        
        invalidate()
    }
    
    private fun updateAlpha() {
        val opacity = settingsManager.getBallAlpha()
        pulseAlpha = opacity / 100f
        alpha = pulseAlpha
    }
    
    private fun startIdleAnimation() {
        // 微妙的呼吸动画
        breatheAnimator?.cancel()
        breatheAnimator = ValueAnimator.ofFloat(1f, 1.05f, 1f).apply {
            duration = 3000  // 3秒一个循环
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                breatheScale = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        val centerX = width / 2f
        val centerY = height / 2f
        val density = resources.displayMetrics.density
        
        // 应用所有缩放效果
        val totalScale = currentScale * breatheScale
        val rectWidth = baseWidth * density * currentWidth * totalScale
        val rectHeight = baseHeight * density * currentHeight * totalScale
        
        // 计算绘制区域
        val left = centerX - rectWidth / 2f
        val top = centerY - rectHeight / 2f
        val right = centerX + rectWidth / 2f
        val bottom = centerY + rectHeight / 2f
        
        bounds.set(left, top, right, bottom)
        
        // 绘制波纹效果
        if (rippleScale > 0 && rippleAlpha > 0) {
            val rippleRadius = rectWidth * rippleScale
            glowPaint.alpha = (rippleAlpha * 60).toInt()
            canvas.drawCircle(centerX, centerY, rippleRadius, glowPaint)
        }
        
        // 绘制阴影
        if (!isPressed) {
            shadowPaint.setShadowLayer(4f * density, 0f, 2f * density, shadowColor)
            canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, shadowPaint)
            shadowPaint.clearShadowLayer()
        }
        
        // 绘制发光效果
        if (currentGlowAlpha > 0) {
            glowPaint.alpha = (currentGlowAlpha * 100).toInt()
            
            // 创建发光效果
            for (i in 1..2) {
                val glowOffset = 8f * density * i / 2f
                val glowAlpha = (currentGlowAlpha * (3 - i) / 2f * 80).toInt()
                glowPaint.alpha = glowAlpha
                
                val glowBounds = RectF(
                    left - glowOffset,
                    top - glowOffset,
                    right + glowOffset,
                    bottom + glowOffset
                )
                
                canvas.drawRoundRect(glowBounds, cornerRadius + glowOffset, cornerRadius + glowOffset, glowPaint)
            }
        }
        
        // 绘制主背景
        backgroundPaint.alpha = (255 * pulseAlpha).toInt()
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, backgroundPaint)
        
        // 绘制图标
        if (showIcon && currentIcon != null && currentIconAlpha > 0) {
            val iconSize = (rectHeight * 1.5f).toInt()
            val iconLeft = (centerX - iconSize / 2).toInt()
            val iconTop = (centerY - iconSize / 2).toInt()
            
            currentIcon?.setBounds(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
            currentIcon?.alpha = (255 * currentIconAlpha).toInt()
            currentIcon?.draw(canvas)
        }
    }
    
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 设置固定大小，包含触摸区域
        val density = resources.displayMetrics.density
        val width = ((baseWidth + 24) * density).toInt()  // 增加触摸区域
        val height = ((baseHeight + 24) * density).toInt()
        setMeasuredDimension(width, height)
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isPressed = true
                isDragging = false
                lastTouchX = event.x
                lastTouchY = event.y
                startTouchX = event.x
                startTouchY = event.y
                touchDownTime = System.currentTimeMillis()
                
                startPressAnimation()
                
                // 启动长按检测
                longPressRunnable = Runnable {
                    if (isPressed && !isDragging && visibility == View.VISIBLE) {
                        performLongClick()
                        startLongPressAnimation()
                    }
                }
                handler.postDelayed(longPressRunnable!!, longPressThreshold)
                
                return true
            }
            
            MotionEvent.ACTION_MOVE -> {
                if (isPressed) {
                    val deltaX = event.x - startTouchX
                    val deltaY = event.y - startTouchY
                    val distance = kotlin.math.sqrt(deltaX * deltaX + deltaY * deltaY)
                    
                    if (distance > dragThreshold && !isDragging) {
                        // 开始拖拽
                        isDragging = true
                        longPressRunnable?.let { handler.removeCallbacks(it) }
                        startDragAnimation()
                        onDragListener?.onDragStart()
                    }
                    
                    if (isDragging) {
                        val moveX = event.x - lastTouchX
                        val moveY = event.y - lastTouchY
                        onDragListener?.onDragMove(moveX, moveY)
                    }
                    
                    lastTouchX = event.x
                    lastTouchY = event.y
                }
                return true
            }
            
            MotionEvent.ACTION_UP -> {
                longPressRunnable?.let { handler.removeCallbacks(it) }
                
                if (isDragging) {
                    onDragListener?.onDragEnd()
                    startDragEndAnimation()
                } else {
                    val touchDuration = System.currentTimeMillis() - touchDownTime
                    if (touchDuration < longPressThreshold) {
                        performClick()
                        startClickAnimation()
                    }
                }
                
                startReleaseAnimation()
                isPressed = false
                isDragging = false
                return true
            }
            
            MotionEvent.ACTION_CANCEL -> {
                longPressRunnable?.let { handler.removeCallbacks(it) }
                if (isDragging) {
                    onDragListener?.onDragEnd()
                }
                startReleaseAnimation()
                isPressed = false
                isDragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
    
    private fun startPressAnimation() {
        currentAnimator?.cancel()
        
        // 按下效果：轻微缩小 + 发光
        val scaleAnimator = ValueAnimator.ofFloat(currentScale, 0.95f).apply {
            duration = 100
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                currentScale = animator.animatedValue as Float
                invalidate()
            }
        }
        
        val glowAnimator = ValueAnimator.ofFloat(currentGlowAlpha, 0.6f).apply {
            duration = 100
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                currentGlowAlpha = animator.animatedValue as Float
                invalidate()
            }
        }
        
        currentAnimator = AnimatorSet().apply {
            playTogether(scaleAnimator, glowAnimator)
            start()
        }
    }
    
    private fun startReleaseAnimation() {
        currentAnimator?.cancel()
        
        // 释放效果：回弹 + 发光消失
        val scaleAnimator = ValueAnimator.ofFloat(currentScale, 1f).apply {
            duration = 200
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { animator ->
                currentScale = animator.animatedValue as Float
                invalidate()
            }
        }
        
        val glowAnimator = ValueAnimator.ofFloat(currentGlowAlpha, 0f).apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                currentGlowAlpha = animator.animatedValue as Float
                invalidate()
            }
        }
        
        currentAnimator = AnimatorSet().apply {
            playTogether(scaleAnimator, glowAnimator)
            start()
        }
    }
    
    private fun startClickAnimation() {
        // 点击波纹效果
        rippleAnimator?.cancel()
        rippleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 400
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                val progress = animator.animatedValue as Float
                rippleScale = progress * 2f
                rippleAlpha = (1f - progress) * 0.8f
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    rippleScale = 0f
                    rippleAlpha = 0f
                    invalidate()
                }
            })
            start()
        }
    }
    
    private fun startLongPressAnimation() {
        // 长按效果：脉冲 + 扩展，确保不会影响View的可见性
        currentAnimator?.cancel()
        
        val pulseAnimator = ValueAnimator.ofFloat(currentScale, 1.15f, 1f).apply {
            duration = 400
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                currentScale = animator.animatedValue as Float
                invalidate()
            }
        }
        
        val glowAnimator = ValueAnimator.ofFloat(currentGlowAlpha, 1f, 0.7f).apply {
            duration = 400
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                currentGlowAlpha = animator.animatedValue as Float
                invalidate()
            }
        }
        
        currentAnimator = AnimatorSet().apply {
            playTogether(pulseAnimator, glowAnimator)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // 确保动画结束后状态正确
                    if (!isDragging && !isPressed) {
                        currentScale = 1f
                        currentGlowAlpha = 0f
                        invalidate()
                    }
                }
            })
            start()
        }
    }
    
    private fun startDragAnimation() {
        // 拖拽开始效果：扩展 + 强发光
        val scaleAnimator = ValueAnimator.ofFloat(currentScale, 1.1f).apply {
            duration = 150
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                currentScale = animator.animatedValue as Float
                invalidate()
            }
        }
        
        val glowAnimator = ValueAnimator.ofFloat(currentGlowAlpha, 1f).apply {
            duration = 150
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                currentGlowAlpha = animator.animatedValue as Float
                invalidate()
            }
        }
        
        currentAnimator = AnimatorSet().apply {
            playTogether(scaleAnimator, glowAnimator)
            start()
        }
    }
    
    private fun startDragEndAnimation() {
        // 拖拽结束效果：回弹
        val scaleAnimator = ValueAnimator.ofFloat(currentScale, 1f).apply {
            duration = 300
            interpolator = OvershootInterpolator(1.5f)
            addUpdateListener { animator ->
                currentScale = animator.animatedValue as Float
                invalidate()
            }
        }
        
        currentAnimator = AnimatorSet().apply {
            play(scaleAnimator)
            start()
        }
    }
    
    fun showAppIcon(iconResId: Int) {
        currentIcon = ContextCompat.getDrawable(context, iconResId)
        showIcon = true
        
        // 取消任何待定的隐藏
        hideIconRunnable?.let { handler.removeCallbacks(it) }
        
        // 展开并显示图标的动画
        currentAnimator?.cancel()
        
        val expandAnimator = ValueAnimator.ofFloat(currentHeight, 2f).apply {
            duration = 300
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { animator ->
                currentHeight = animator.animatedValue as Float
                invalidate()
            }
        }
        
        val iconFadeInAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200
            startDelay = 100
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                currentIconAlpha = animator.animatedValue as Float
                invalidate()
            }
        }
        
        val glowAnimator = ValueAnimator.ofFloat(0f, 0.8f, 0f).apply {
            duration = 600
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                currentGlowAlpha = animator.animatedValue as Float
                invalidate()
            }
        }
        
        currentAnimator = AnimatorSet().apply {
            playTogether(expandAnimator, iconFadeInAnimator, glowAnimator)
            start()
        }
        
        // 定时隐藏图标
        hideIconRunnable = Runnable {
            hideAppIcon()
        }
        handler.postDelayed(hideIconRunnable!!, 2000)
    }
    
    private fun hideAppIcon() {
        currentAnimator?.cancel()
        
        val collapseAnimator = ValueAnimator.ofFloat(currentHeight, 1f).apply {
            duration = 300
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                currentHeight = animator.animatedValue as Float
                invalidate()
            }
        }
        
        val iconFadeOutAnimator = ValueAnimator.ofFloat(currentIconAlpha, 0f).apply {
            duration = 200
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                currentIconAlpha = animator.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    showIcon = false
                    currentIcon = null
                }
            })
        }
        
        currentAnimator = AnimatorSet().apply {
            playTogether(collapseAnimator, iconFadeOutAnimator)
            start()
        }
    }
    
    fun startPulseAnimation() {
        // 脉冲动画，用于吸引注意力
        pulseAnimator?.cancel()
        pulseAnimator = ValueAnimator.ofFloat(pulseAlpha, pulseAlpha * 0.3f, pulseAlpha).apply {
            duration = 800
            repeatCount = 2
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animator ->
                alpha = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
    
    fun updateSettings() {
        updateAlpha()
        updateTheme()
    }
    
    override fun setOnLongClickListener(l: OnLongClickListener?) {
        onLongClickListener = l
    }
    
    fun setOnDragListener(listener: OnDragListener?) {
        onDragListener = listener
    }
    
    override fun performLongClick(): Boolean {
        return onLongClickListener?.onLongClick(this) ?: super.performLongClick()
    }
    
    override fun onConfigurationChanged(newConfig: Configuration?) {
        super.onConfigurationChanged(newConfig)
        updateTheme()
    }
    
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        hideIconRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable?.let { handler.removeCallbacks(it) }
        breatheAnimator?.cancel()
        currentAnimator?.cancel()
        pulseAnimator?.cancel()
        rippleAnimator?.cancel()
    }
} 