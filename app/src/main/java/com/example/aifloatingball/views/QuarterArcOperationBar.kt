package com.example.aifloatingball.views

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager
import kotlin.math.*

/**
 * 四分之一圆弧操作栏
 * 支持在右下角或左下角显示，包含四个功能按钮：刷新、切换标签、后退、撤回关闭
 */
class QuarterArcOperationBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "QuarterArcOperationBar"
        private const val DEFAULT_ARC_RADIUS = 120f // dp
        private const val BUTTON_SIZE = 36f // dp - 减小按钮尺寸
        private const val ARC_STROKE_WIDTH = 4f // dp
        private const val ANIMATION_DURATION = 300L
    }

    // 操作监听器接口
    interface OnOperationListener {
        fun onRefresh()
        fun onNextTab()
        fun onBack()
        fun onUndoClose()
    }

    // 属性
    private var arcRadius = DEFAULT_ARC_RADIUS * resources.displayMetrics.density
    private var buttonSize = BUTTON_SIZE * resources.displayMetrics.density
    private var arcStrokeWidth = ARC_STROKE_WIDTH * resources.displayMetrics.density
    private var buttonRadiusOffset = 0f // 按钮相对于圆心的额外偏移距离
    private var isLeftHanded = false
    private var isExpanded = false
    private var animationProgress = 0f

    // 激活按钮相关
    private var activatorButtonSize = 40f * resources.displayMetrics.density
    private var activatorButtonX = 0f
    private var activatorButtonY = 0f
    private var isActivatorVisible = true
    private var animator: ValueAnimator? = null
    private var expandAnimator: ValueAnimator? = null

    // 缩放手势相关
    private var scaleGestureDetector: ScaleGestureDetector? = null
    private var minArcRadius = 80f * resources.displayMetrics.density
    private var maxArcRadius = 200f * resources.displayMetrics.density
    private var isScaling = false

    // 缩放提示相关
    private var showScaleHint = false
    private var scaleHintAlpha = 0f
    private var scaleHintAnimator: ValueAnimator? = null

    // 按钮提示相关
    private var showButtonHint = false
    private var buttonHintText = ""
    private var buttonHintAlpha = 0f
    private var buttonHintAnimator: ValueAnimator? = null
    private var longPressHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null

    // 双击检测
    private var lastClickTime = 0L
    private val doubleClickThreshold = 300L

    // 配置相关
    private var configListener: OnConfigListener? = null

    // 位置调整相关
    private var isDragging = false
    private var dragStartX = 0f
    private var dragStartY = 0f
    private var initialX = 0f
    private var initialY = 0f
    private var positionAdjustmentMode = false

    // 显示状态控制
    private var isMinimized = false
    private var minimizeAnimator: ValueAnimator? = null
    private var minimizedAlpha = 0.3f

    interface OnConfigListener {
        fun onShowConfig()
    }

    interface OnPositionChangeListener {
        fun onPositionChanged(x: Float, y: Float)
    }

    interface OnVisibilityChangeListener {
        fun onVisibilityChanged(isMinimized: Boolean)
    }

    private var positionChangeListener: OnPositionChangeListener? = null
    private var visibilityChangeListener: OnVisibilityChangeListener? = null

    // 绘制相关
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val activatorButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hintTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    
    // 按钮数据
    private data class ButtonData(
        val icon: Int,
        val action: () -> Unit,
        val description: String,
        var centerX: Float = 0f,
        var centerY: Float = 0f,
        var isPressed: Boolean = false
    )

    private val buttons = mutableListOf<ButtonData>()
    private var operationListener: OnOperationListener? = null

    init {
        setupPaints()
        setupButtons()
        setupScaleGestureDetector()

        // 设置点击监听
        setOnClickListener { toggleExpansion() }

        // 设置初始大小
        val size = (arcRadius * 2).toInt()
        layoutParams = layoutParams ?: android.widget.FrameLayout.LayoutParams(size, size)
    }

    private fun setupPaints() {
        // 使用白色主题，支持暗色模式
        val buttonColor = getThemeAwareColor()
        val iconColor = getThemeAwareIconColor()
        val primaryColor = getSystemColor(android.R.attr.colorPrimary, ContextCompat.getColor(context, R.color.material_blue_600))

        // 圆弧画笔 - 使用白色
        arcPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = arcStrokeWidth
            color = buttonColor
            strokeCap = Paint.Cap.ROUND
        }

        // 按钮背景画笔 - 使用白色，Material Design风格
        buttonPaint.apply {
            style = Paint.Style.FILL
            color = buttonColor
            isAntiAlias = true
            // 添加阴影效果
            setShadowLayer(
                6f * resources.displayMetrics.density, // 阴影半径
                0f, // X偏移
                3f * resources.displayMetrics.density, // Y偏移
                Color.argb(40, 0, 0, 0) // 阴影颜色
            )
        }

        // 图标画笔 - 使用黑色图标
        iconPaint.apply {
            style = Paint.Style.FILL
            color = iconColor
            textAlign = Paint.Align.CENTER
            textSize = buttonSize * 0.4f
        }

        // 激活按钮画笔 - 使用白色，半透明边框
        activatorButtonPaint.apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f * resources.displayMetrics.density
            color = buttonColor
            alpha = 180 // 半透明
            isAntiAlias = true
        }

        // 提示文本画笔 - 使用主色调
        hintTextPaint.apply {
            style = Paint.Style.FILL
            color = primaryColor
            textSize = 12f * resources.displayMetrics.scaledDensity
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
    }

    /**
     * 获取系统颜色，支持暗色模式
     */
    private fun getSystemColor(attr: Int, defaultColor: Int): Int {
        val typedValue = android.util.TypedValue()
        val theme = context.theme
        return if (theme.resolveAttribute(attr, typedValue, true)) {
            if (typedValue.type >= android.util.TypedValue.TYPE_FIRST_COLOR_INT &&
                typedValue.type <= android.util.TypedValue.TYPE_LAST_COLOR_INT) {
                typedValue.data
            } else {
                ContextCompat.getColor(context, typedValue.resourceId)
            }
        } else {
            defaultColor
        }
    }

    /**
     * 检查是否为暗色模式
     */
    private fun isDarkMode(): Boolean {
        val nightModeFlags = context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    /**
     * 获取适合当前主题的颜色
     */
    private fun getThemeAwareColor(): Int {
        return if (isDarkMode()) {
            Color.WHITE
        } else {
            Color.WHITE
        }
    }

    /**
     * 获取适合当前主题的图标颜色
     */
    private fun getThemeAwareIconColor(): Int {
        return if (isDarkMode()) {
            Color.BLACK
        } else {
            Color.BLACK
        }
    }

    private fun setupScaleGestureDetector() {
        scaleGestureDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (!isExpanded) return false // 只有在展开状态下才允许缩放

                val scaleFactor = detector.scaleFactor
                val newRadius = arcRadius * scaleFactor

                // 限制缩放范围
                if (newRadius in minArcRadius..maxArcRadius) {
                    val scaleRatio = newRadius / arcRadius
                    arcRadius = newRadius
                    buttonSize = (BUTTON_SIZE * resources.displayMetrics.density) * (arcRadius / (DEFAULT_ARC_RADIUS * resources.displayMetrics.density))
                    activatorButtonSize = (40f * resources.displayMetrics.density) * (arcRadius / (DEFAULT_ARC_RADIUS * resources.displayMetrics.density))

                    // 按比例调整按钮半径偏移
                    buttonRadiusOffset *= scaleRatio

                    calculateButtonPositions()
                    calculateActivatorButtonPosition()
                    invalidate()

                    Log.d(TAG, "圆弧半径调整为: $arcRadius, 按钮偏移: $buttonRadiusOffset")
                }
                return true
            }

            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                isScaling = true
                showScaleHint = true
                showScaleHintAnimation()
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                isScaling = false
                hideScaleHintAnimation()
            }
        })
    }

    private fun setupButtons() {
        // 设置默认按钮配置
        val defaultConfigs = listOf(
            ButtonConfig(
                icon = R.drawable.ic_refresh,
                action = {
                    operationListener?.onRefresh()
                    Log.d(TAG, "刷新按钮被点击")
                },
                description = "刷新"
            ),
            ButtonConfig(
                icon = R.drawable.ic_tab_switch,
                action = {
                    operationListener?.onNextTab()
                    Log.d(TAG, "切换标签按钮被点击")
                },
                description = "下一个标签"
            ),
            ButtonConfig(
                icon = R.drawable.ic_arrow_back,
                action = {
                    operationListener?.onBack()
                    Log.d(TAG, "后退按钮被点击")
                },
                description = "后退"
            ),
            ButtonConfig(
                icon = R.drawable.ic_undo,
                action = {
                    operationListener?.onUndoClose()
                    Log.d(TAG, "撤回关闭按钮被点击")
                },
                description = "撤回关闭"
            )
        )

        setButtonConfigs(defaultConfigs)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 计算所需的最小尺寸，确保不被截断
        val maxButtonDistance = arcRadius + kotlin.math.abs(buttonRadiusOffset) + buttonSize
        // 四分之一圆弧需要的最小尺寸，添加足够的边距
        val minRequiredSize = (maxButtonDistance + 60f * resources.displayMetrics.density).toInt()

        // 使用父布局给定的尺寸和计算出的最小尺寸中的较大值
        val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
        val parentHeight = MeasureSpec.getSize(heightMeasureSpec)

        val finalWidth = kotlin.math.max(minRequiredSize, parentWidth)
        val finalHeight = kotlin.math.max(minRequiredSize, parentHeight)

        setMeasuredDimension(finalWidth, finalHeight)
        Log.d(TAG, "View尺寸设置为: ${finalWidth}x${finalHeight}, 最小需求: $minRequiredSize, 圆弧半径: $arcRadius")
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        calculateButtonPositions()
        calculateActivatorButtonPosition()
    }

    private fun calculateButtonPositions() {
        if (buttons.isEmpty()) return

        // 圆弧中心点位置 - 留出足够边距确保按钮不被截断
        val margin = 30f * resources.displayMetrics.density
        val centerX = if (isLeftHanded) margin else width - margin
        val centerY = height - margin

        // 按钮在圆弧上的半径 - 可调整的距离
        val baseRadius = arcRadius - buttonSize / 2 - 12f * resources.displayMetrics.density
        val buttonRadius = baseRadius + buttonRadiusOffset

        // 根据左手模式调整角度范围
        // 右手模式：从180度到270度（左上到正下）
        // 左手模式：从270度到360度（正下到右上）
        val startAngle = if (isLeftHanded) 270f else 180f

        // 均匀分布按钮：在90度范围内等间距分布
        val totalAngleRange = 90f
        val angleStep = if (buttons.size > 1) {
            totalAngleRange / (buttons.size - 1) // 使用 size-1 来让按钮分布在起始和结束位置
        } else {
            0f // 只有一个按钮时放在中间
        }

        buttons.forEachIndexed { index, button ->
            val angle = if (buttons.size == 1) {
                startAngle + 45f // 单个按钮放在中间
            } else {
                startAngle + angleStep * index
            }
            val radian = Math.toRadians(angle.toDouble())

            button.centerX = centerX + (buttonRadius * cos(radian)).toFloat()
            button.centerY = centerY + (buttonRadius * sin(radian)).toFloat()
        }

        Log.d(TAG, "按钮位置计算完成 - 左手模式: $isLeftHanded, 按钮数量: ${buttons.size}")
    }

    private fun calculateActivatorButtonPosition() {
        // 激活按钮位置在圆弧的中心点，与按钮位置计算保持一致
        val margin = 30f * resources.displayMetrics.density

        if (isLeftHanded) {
            // 左手模式：左下角
            activatorButtonX = margin
            activatorButtonY = height - margin
        } else {
            // 右手模式：右下角
            activatorButtonX = width - margin
            activatorButtonY = height - margin
        }

        Log.d(TAG, "激活按钮位置更新 - 左手模式: $isLeftHanded, 位置: ($activatorButtonX, $activatorButtonY)")
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 如果是最小化状态，只绘制半透明的激活按钮
        if (isMinimized) {
            canvas.save()
            canvas.scale(0.7f, 0.7f, activatorButtonX, activatorButtonY)
            val originalAlpha = activatorButtonPaint.alpha
            activatorButtonPaint.alpha = (originalAlpha * minimizedAlpha).toInt()
            drawActivatorButton(canvas)
            activatorButtonPaint.alpha = originalAlpha
            canvas.restore()
            return
        }

        // 正常状态下的绘制
        // 始终绘制激活按钮
        if (isActivatorVisible) {
            drawActivatorButton(canvas)
        }

        // 只有在展开状态下才绘制功能按钮
        if (isExpanded && animationProgress > 0.3f) {
            drawButtons(canvas)
        }

        // 绘制缩放提示
        if (showScaleHint && scaleHintAlpha > 0f) {
            drawScaleHint(canvas)
        }

        // 绘制按钮功能提示
        if (showButtonHint && buttonHintAlpha > 0f) {
            drawButtonHint(canvas)
        }
    }

    private fun drawArc(canvas: Canvas) {
        // 圆弧中心点 - 与按钮位置计算保持一致
        val margin = 30f * resources.displayMetrics.density
        val centerX = if (isLeftHanded) margin else width - margin
        val centerY = height - margin

        val rect = RectF(
            centerX - arcRadius,
            centerY - arcRadius,
            centerX + arcRadius,
            centerY + arcRadius
        )

        // 根据左手模式调整起始角度
        // 右手模式：从180度开始绘制90度（左上到正下）
        // 左手模式：从270度开始绘制90度（正下到右上）
        val startAngle = if (isLeftHanded) 270f else 180f
        val sweepAngle = 90f * animationProgress

        canvas.drawArc(rect, startAngle, sweepAngle, false, arcPaint)
    }

    private fun drawActivatorButton(canvas: Canvas) {
        // 绘制透明的激活按钮
        val radius = activatorButtonSize / 2

        // 绘制圆形边框
        canvas.drawCircle(
            activatorButtonX,
            activatorButtonY,
            radius,
            activatorButtonPaint
        )

        // 绘制中心的加号或减号图标
        val iconPaint = Paint().apply {
            color = ContextCompat.getColor(context, R.color.material_blue_600)
            strokeWidth = 3f * resources.displayMetrics.density
            strokeCap = Paint.Cap.ROUND
            isAntiAlias = true
            alpha = 120
        }

        val iconSize = radius * 0.4f
        if (isExpanded) {
            // 绘制减号（收起状态）
            canvas.drawLine(
                activatorButtonX - iconSize,
                activatorButtonY,
                activatorButtonX + iconSize,
                activatorButtonY,
                iconPaint
            )
        } else {
            // 绘制加号（展开状态）
            canvas.drawLine(
                activatorButtonX - iconSize,
                activatorButtonY,
                activatorButtonX + iconSize,
                activatorButtonY,
                iconPaint
            )
            canvas.drawLine(
                activatorButtonX,
                activatorButtonY - iconSize,
                activatorButtonX,
                activatorButtonY + iconSize,
                iconPaint
            )
        }
    }

    private fun drawScaleHint(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f

        // 设置提示文本透明度
        hintTextPaint.alpha = (255 * scaleHintAlpha).toInt()

        // 绘制缩放提示文本
        val hintText = "双指缩放调整大小"
        val textY = centerY + hintTextPaint.textSize / 2
        canvas.drawText(hintText, centerX, textY, hintTextPaint)

        // 绘制缩放图标指示
        val iconSize = 16f * resources.displayMetrics.density
        val iconPaint = Paint(hintTextPaint).apply {
            strokeWidth = 2f * resources.displayMetrics.density
            style = Paint.Style.STROKE
        }

        // 绘制两个圆圈表示双指
        val circle1X = centerX - iconSize
        val circle1Y = centerY - iconSize * 1.5f
        val circle2X = centerX + iconSize
        val circle2Y = centerY - iconSize * 1.5f

        canvas.drawCircle(circle1X, circle1Y, iconSize * 0.3f, iconPaint)
        canvas.drawCircle(circle2X, circle2Y, iconSize * 0.3f, iconPaint)

        // 绘制箭头表示缩放方向
        canvas.drawLine(circle1X + iconSize * 0.3f, circle1Y, circle2X - iconSize * 0.3f, circle2Y, iconPaint)
    }

    private fun showScaleHintAnimation() {
        scaleHintAnimator?.cancel()
        scaleHintAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200
            addUpdateListener { animator ->
                scaleHintAlpha = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun hideScaleHintAnimation() {
        scaleHintAnimator?.cancel()
        scaleHintAnimator = ValueAnimator.ofFloat(scaleHintAlpha, 0f).apply {
            duration = 300
            addUpdateListener { animator ->
                scaleHintAlpha = animator.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    showScaleHint = false
                }
            })
            start()
        }
    }

    private fun drawButtonHint(canvas: Canvas) {
        if (buttonHintText.isEmpty()) return

        val centerX = width / 2f
        val centerY = height / 2f

        // 设置提示文本透明度
        val hintPaint = Paint(hintTextPaint).apply {
            alpha = (255 * buttonHintAlpha).toInt()
            textSize = 14f * resources.displayMetrics.scaledDensity
        }

        // 绘制背景
        val textBounds = android.graphics.Rect()
        hintPaint.getTextBounds(buttonHintText, 0, buttonHintText.length, textBounds)

        val padding = 8f * resources.displayMetrics.density
        val backgroundPaint = Paint().apply {
            color = Color.BLACK
            alpha = (180 * buttonHintAlpha).toInt()
            isAntiAlias = true
        }

        val backgroundRect = RectF(
            centerX - textBounds.width() / 2 - padding,
            centerY - textBounds.height() / 2 - padding,
            centerX + textBounds.width() / 2 + padding,
            centerY + textBounds.height() / 2 + padding
        )

        canvas.drawRoundRect(backgroundRect, 8f * resources.displayMetrics.density, 8f * resources.displayMetrics.density, backgroundPaint)

        // 绘制提示文本
        hintPaint.color = Color.WHITE
        canvas.drawText(buttonHintText, centerX, centerY + textBounds.height() / 2, hintPaint)
    }

    private fun startLongPressDetection(button: ButtonData) {
        cancelLongPressDetection()
        longPressRunnable = Runnable {
            showButtonHint(button.description)
        }
        longPressHandler.postDelayed(longPressRunnable!!, 500) // 500ms长按
    }

    private fun cancelLongPressDetection() {
        longPressRunnable?.let {
            longPressHandler.removeCallbacks(it)
            longPressRunnable = null
        }
    }

    private fun showButtonHint(text: String) {
        buttonHintText = text
        showButtonHint = true

        buttonHintAnimator?.cancel()
        buttonHintAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200
            addUpdateListener { animator ->
                buttonHintAlpha = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun hideButtonHint() {
        if (!showButtonHint) return

        buttonHintAnimator?.cancel()
        buttonHintAnimator = ValueAnimator.ofFloat(buttonHintAlpha, 0f).apply {
            duration = 200
            addUpdateListener { animator ->
                buttonHintAlpha = animator.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    showButtonHint = false
                    buttonHintText = ""
                }
            })
            start()
        }
    }

    private fun startActivatorLongPressDetection() {
        cancelActivatorLongPressDetection()

        // 简化长按逻辑：800ms显示配置，2000ms进入拖动模式
        longPressRunnable = Runnable {
            showButtonHint("长按显示配置")
            configListener?.onShowConfig()
        }
        longPressHandler.postDelayed(longPressRunnable!!, 800)

        // 设置超长按进入拖动模式
        val dragModeRunnable = Runnable {
            enterPositionAdjustmentMode()
        }
        longPressHandler.postDelayed(dragModeRunnable, 2000)
    }

    private fun cancelActivatorLongPressDetection() {
        longPressHandler.removeCallbacksAndMessages(null) // 移除所有回调
        longPressRunnable = null
    }

    private fun enterPositionAdjustmentMode() {
        positionAdjustmentMode = true
        showButtonHint("拖动调整位置，点击确认")
        Log.d(TAG, "进入位置调整模式")
    }

    private fun exitPositionAdjustmentMode() {
        positionAdjustmentMode = false
        isDragging = false
        hideButtonHint()
        Log.d(TAG, "退出位置调整模式")
    }

    private fun handlePositionAdjustment(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragStartX = event.rawX
                dragStartY = event.rawY
                initialX = x
                initialY = y
                isDragging = false // 先不设为true，等移动时再设置
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = event.rawX - dragStartX
                val deltaY = event.rawY - dragStartY

                // 如果移动距离超过阈值，开始拖拽
                if (!isDragging && (kotlin.math.abs(deltaX) > 10 || kotlin.math.abs(deltaY) > 10)) {
                    isDragging = true
                    showButtonHint("拖动调整位置")
                }

                if (isDragging) {
                    val newX = initialX + deltaX
                    val newY = initialY + deltaY

                    // 限制在屏幕边界内
                    val parent = parent as? ViewGroup
                    if (parent != null) {
                        val maxX = parent.width - width
                        val maxY = parent.height - height

                        x = newX.coerceIn(0f, maxX.toFloat())
                        y = newY.coerceIn(0f, maxY.toFloat())
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    // 通知位置变化
                    positionChangeListener?.onPositionChanged(x, y)
                }
                exitPositionAdjustmentMode()
                return true
            }
        }
        return false
    }

    private fun handleMinimizedTouch(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        // 检查是否在激活按钮区域内
        val activatorDistance = sqrt((x - activatorButtonX).pow(2) + (y - activatorButtonY).pow(2))
        val isInActivatorArea = activatorDistance <= activatorButtonSize / 2

        if (!isInActivatorArea) {
            // 不在激活按钮区域内，不处理触摸事件，让底层处理
            return false
        }

        // 在激活按钮区域内，处理触摸事件
        when (event.action) {
            MotionEvent.ACTION_UP -> {
                // 恢复到正常状态
                toggleMinimized()
                return true
            }
        }
        return true
    }

    private fun drawButtons(canvas: Canvas) {
        buttons.forEach { button ->
            // 计算按钮透明度（从0.3开始显示，到1.0完全显示）
            val buttonProgress = ((animationProgress - 0.3f) / 0.7f).coerceIn(0f, 1f)
            val buttonAlpha = (255 * buttonProgress).toInt()

            if (buttonAlpha > 0) {
                // 计算按钮缩放效果（Material Design动画）
                val scale = 0.8f + 0.2f * buttonProgress
                val scaledRadius = (buttonSize / 2) * scale

                // 绘制按钮背景
                val currentButtonPaint = Paint(buttonPaint).apply {
                    alpha = buttonAlpha
                    // 如果按钮被按下，改变颜色为主色调
                    if (button.isPressed) {
                        color = getSystemColor(android.R.attr.colorPrimary, ContextCompat.getColor(context, R.color.material_blue_600))
                    }
                }

                // 绘制按钮圆形
                if (button.isPressed) {
                    // 按下时稍微放大
                    val pressedRadius = scaledRadius * 1.1f
                    canvas.drawCircle(
                        button.centerX,
                        button.centerY,
                        pressedRadius,
                        currentButtonPaint
                    )
                } else {
                    canvas.drawCircle(
                        button.centerX,
                        button.centerY,
                        scaledRadius,
                        currentButtonPaint
                    )
                }

                // 绘制图标
                drawIcon(canvas, button, buttonAlpha, scale)
            }
        }
    }

    private fun drawIcon(canvas: Canvas, button: ButtonData, alpha: Int, scale: Float = 1f) {
        val drawable = ContextCompat.getDrawable(context, button.icon)
        drawable?.let {
            val iconSize = (buttonSize * 0.5f * scale).toInt()
            val left = (button.centerX - iconSize / 2).toInt()
            val top = (button.centerY - iconSize / 2).toInt()
            val right = left + iconSize
            val bottom = top + iconSize

            it.setBounds(left, top, right, bottom)
            it.setTint(getThemeAwareIconColor())
            it.alpha = alpha
            it.draw(canvas)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 如果是最小化状态，只处理激活按钮区域的触摸
        if (isMinimized) {
            return handleMinimizedTouch(event)
        }

        // 首先处理缩放手势
        scaleGestureDetector?.onTouchEvent(event)

        // 如果正在缩放，不处理其他触摸事件
        if (isScaling) {
            return true
        }

        // 处理位置调整模式
        if (positionAdjustmentMode) {
            return handlePositionAdjustment(event)
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.x
                val y = event.y

                // 首先检查是否点击了激活按钮
                val activatorDistance = sqrt((x - activatorButtonX).pow(2) + (y - activatorButtonY).pow(2))
                if (activatorDistance <= activatorButtonSize / 2) {
                    // 开始长按检测（用于显示配置）
                    startActivatorLongPressDetection()
                    return true
                }

                // 如果展开了，检查是否点击了功能按钮
                if (isExpanded) {
                    val touchedButton = findTouchedButton(x, y)
                    touchedButton?.let { button ->
                        button.isPressed = true

                        // 开始长按检测
                        startLongPressDetection(button)

                        invalidate()
                        return true
                    }
                }
                return super.onTouchEvent(event)
            }

            MotionEvent.ACTION_UP -> {
                val x = event.x
                val y = event.y

                // 取消长按检测
                cancelLongPressDetection()
                cancelActivatorLongPressDetection()

                // 检查是否在激活按钮上释放
                val activatorDistance = sqrt((x - activatorButtonX).pow(2) + (y - activatorButtonY).pow(2))
                if (activatorDistance <= activatorButtonSize / 2) {
                    if (!showButtonHint) { // 如果没有显示提示，执行操作
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastClickTime < doubleClickThreshold) {
                            // 双击：切换最小化状态
                            toggleMinimized()
                        } else {
                            // 单击：切换展开状态
                            if (!isMinimized) {
                                toggleExpansion()
                            } else {
                                // 如果是最小化状态，单击恢复
                                toggleMinimized()
                            }
                        }
                        lastClickTime = currentTime
                    }
                    hideButtonHint()
                    return true
                }

                // 检查是否在功能按钮上释放
                if (isExpanded) {
                    val touchedButton = findTouchedButton(x, y)
                    touchedButton?.let {
                        it.isPressed = false
                        if (!showButtonHint) { // 如果没有显示提示，执行操作
                            it.action.invoke()
                            // 执行操作后收起按钮
                            collapse()
                        }
                        hideButtonHint()
                        invalidate()
                        return true
                    }
                }
                return super.onTouchEvent(event)
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelLongPressDetection()
                cancelActivatorLongPressDetection()
                hideButtonHint()
                buttons.forEach { it.isPressed = false }
                invalidate()
                return true
            }
        }

        return super.onTouchEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // 如果是最小化状态，只有激活按钮区域才分发触摸事件
        if (isMinimized) {
            val x = event.x
            val y = event.y
            val activatorDistance = sqrt((x - activatorButtonX).pow(2) + (y - activatorButtonY).pow(2))
            val isInActivatorArea = activatorDistance <= activatorButtonSize / 2

            if (isInActivatorArea) {
                // 在激活按钮区域内，正常分发事件
                return super.dispatchTouchEvent(event)
            } else {
                // 不在激活按钮区域内，不分发事件
                return false
            }
        }

        // 正常状态下，正常分发事件
        return super.dispatchTouchEvent(event)
    }

    private fun findTouchedButton(x: Float, y: Float): ButtonData? {
        return buttons.find { button ->
            val distance = sqrt((x - button.centerX).pow(2) + (y - button.centerY).pow(2))
            distance <= buttonSize / 2
        }
    }

    private fun toggleExpansion() {
        if (expandAnimator?.isRunning == true) return
        
        isExpanded = !isExpanded
        val targetProgress = if (isExpanded) 1f else 0f
        
        expandAnimator = ValueAnimator.ofFloat(animationProgress, targetProgress).apply {
            duration = ANIMATION_DURATION
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                animationProgress = animator.animatedValue as Float
                invalidate()
            }
            start()
        }
        
        Log.d(TAG, "切换展开状态: $isExpanded")
    }

    // 公共方法
    fun setLeftHandedMode(leftHanded: Boolean) {
        if (isLeftHanded != leftHanded) {
            isLeftHanded = leftHanded

            // 重新计算所有位置
            calculateButtonPositions()
            calculateActivatorButtonPosition()

            // 通知父布局需要更新位置
            requestLayout()

            // 如果当前是展开状态，重新播放动画以显示新位置
            if (isExpanded) {
                animationProgress = 0f
                toggleExpansion()
            } else {
                invalidate()
            }

            Log.d(TAG, "设置左手模式: $leftHanded")
        }
    }

    fun setOnOperationListener(listener: OnOperationListener) {
        this.operationListener = listener
    }

    fun setOnConfigListener(listener: OnConfigListener) {
        this.configListener = listener
    }

    fun setOnPositionChangeListener(listener: OnPositionChangeListener) {
        this.positionChangeListener = listener
    }

    fun setOnVisibilityChangeListener(listener: OnVisibilityChangeListener) {
        this.visibilityChangeListener = listener
    }

    /**
     * 切换最小化状态
     */
    private fun toggleMinimized() {
        isMinimized = !isMinimized

        // 如果最小化，先收起展开状态
        if (isMinimized && isExpanded) {
            collapse()
        }

        // 播放最小化动画
        minimizeAnimator?.cancel()
        minimizeAnimator = ValueAnimator.ofFloat(
            if (isMinimized) 1f else minimizedAlpha,
            if (isMinimized) minimizedAlpha else 1f
        ).apply {
            duration = ANIMATION_DURATION
            addUpdateListener { animator ->
                val alpha = animator.animatedValue as Float
                if (!isMinimized) {
                    // 恢复时更新透明度
                    minimizedAlpha = alpha
                }
                invalidate()
            }
            start()
        }

        // 更新触摸区域
        updateTouchableRegion()

        // 通知状态变化
        visibilityChangeListener?.onVisibilityChanged(isMinimized)

        val statusText = if (isMinimized) "已最小化" else "已恢复"
        showButtonHint(statusText)

        Log.d(TAG, "切换最小化状态: $isMinimized")
    }

    /**
     * 更新可触摸区域
     */
    private fun updateTouchableRegion() {
        if (isMinimized) {
            // 最小化状态下，只有激活按钮区域可触摸
            val touchableSize = (activatorButtonSize * 1.2f).toInt() // 稍微放大触摸区域
            val left = (activatorButtonX - touchableSize / 2).toInt()
            val top = (activatorButtonY - touchableSize / 2).toInt()
            val right = left + touchableSize
            val bottom = top + touchableSize

            // 设置触摸代理，限制触摸区域
            val touchDelegate = android.view.TouchDelegate(
                android.graphics.Rect(left, top, right, bottom),
                this
            )
            (parent as? android.view.ViewGroup)?.touchDelegate = touchDelegate
        } else {
            // 正常状态下，移除触摸代理
            (parent as? android.view.ViewGroup)?.touchDelegate = null
        }
    }

    /**
     * 获取是否最小化
     */
    fun isMinimized(): Boolean {
        return isMinimized
    }

    /**
     * 设置最小化状态
     */
    fun setMinimized(minimized: Boolean) {
        if (isMinimized != minimized) {
            toggleMinimized()
        }
    }

    fun expand() {
        if (!isExpanded) {
            toggleExpansion()
        }
    }

    fun collapse() {
        if (isExpanded) {
            toggleExpansion()
        }
    }

    fun setArcRadius(radius: Float) {
        val newRadius = radius * resources.displayMetrics.density
        if (newRadius in minArcRadius..maxArcRadius) {
            val scaleRatio = newRadius / arcRadius
            arcRadius = newRadius
            // 按比例调整按钮大小
            buttonSize = (BUTTON_SIZE * resources.displayMetrics.density) * (arcRadius / (DEFAULT_ARC_RADIUS * resources.displayMetrics.density))
            activatorButtonSize = (40f * resources.displayMetrics.density) * (arcRadius / (DEFAULT_ARC_RADIUS * resources.displayMetrics.density))

            // 按比例调整按钮半径偏移
            buttonRadiusOffset *= scaleRatio

            calculateButtonPositions()
            calculateActivatorButtonPosition()
            invalidate()
            Log.d(TAG, "设置圆弧半径: $arcRadius, 按钮偏移: $buttonRadiusOffset")
        }
    }

    fun getArcRadius(): Float {
        return arcRadius / resources.displayMetrics.density
    }

    /**
     * 设置按钮相对于圆心的半径偏移
     * @param offset 偏移距离（dp），正值向外，负值向内
     */
    fun setButtonRadiusOffset(offset: Float) {
        buttonRadiusOffset = offset * resources.displayMetrics.density
        calculateButtonPositions()
        invalidate()
        Log.d(TAG, "设置按钮半径偏移: ${offset}dp")
    }

    /**
     * 获取按钮相对于圆心的半径偏移
     */
    fun getButtonRadiusOffset(): Float {
        return buttonRadiusOffset / resources.displayMetrics.density
    }

    /**
     * 自定义按钮配置
     */
    data class ButtonConfig(
        val icon: Int,
        val action: () -> Unit,
        val description: String,
        val isEnabled: Boolean = true
    )

    /**
     * 设置自定义按钮配置
     */
    fun setButtonConfigs(configs: List<ButtonConfig>) {
        buttons.clear()
        configs.filter { it.isEnabled }.forEach { config ->
            buttons.add(ButtonData(
                icon = config.icon,
                action = config.action,
                description = config.description
            ))
        }

        // 自动适应按钮数量
        autoAdaptToButtonCount(buttons.size)

        calculateButtonPositions()
        invalidate()
        Log.d(TAG, "设置自定义按钮配置，按钮数量: ${buttons.size}")
    }

    /**
     * 根据按钮数量自动调整布局
     */
    private fun autoAdaptToButtonCount(buttonCount: Int) {
        when (buttonCount) {
            1, 2 -> {
                // 1-2个按钮：紧凑模式
                setArcRadius(80f)
                setButtonRadiusOffset(-20f)
            }
            3, 4 -> {
                // 3-4个按钮：标准模式
                setArcRadius(100f)
                setButtonRadiusOffset(-10f)
            }
            5, 6 -> {
                // 5-6个按钮：宽松模式
                setArcRadius(120f)
                setButtonRadiusOffset(0f)
            }
            else -> {
                // 更多按钮：超宽松模式
                setArcRadius(140f)
                setButtonRadiusOffset(10f)
            }
        }
    }

    /**
     * 添加按钮
     */
    fun addButton(config: ButtonConfig) {
        if (config.isEnabled) {
            buttons.add(ButtonData(
                icon = config.icon,
                action = config.action,
                description = config.description
            ))
            calculateButtonPositions()
            invalidate()
            Log.d(TAG, "添加按钮: ${config.description}")
        }
    }

    /**
     * 移除按钮
     */
    fun removeButton(description: String) {
        val removed = buttons.removeAll { it.description == description }
        if (removed) {
            calculateButtonPositions()
            invalidate()
            Log.d(TAG, "移除按钮: $description")
        }
    }

    /**
     * 获取当前按钮配置
     */
    fun getButtonConfigs(): List<ButtonConfig> {
        return buttons.map { button ->
            ButtonConfig(
                icon = button.icon,
                action = button.action,
                description = button.description,
                isEnabled = true
            )
        }
    }

    /**
     * 显示操作栏（带动画）
     */
    fun show() {
        if (visibility != View.VISIBLE) {
            visibility = View.VISIBLE
            alpha = 0f
            animate()
                .alpha(1f)
                .setDuration(200)
                .start()
        }
    }

    /**
     * 隐藏操作栏（带动画）
     */
    fun hide() {
        if (visibility == View.VISIBLE) {
            animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction {
                    visibility = View.GONE
                }
                .start()
        }
    }

    /**
     * 显示配置对话框
     */
    fun showConfigDialog(
        fragmentManager: androidx.fragment.app.FragmentManager,
        settingsManager: SettingsManager
    ) {
        val dialog = QuarterArcConfigDialog.newInstance(this, settingsManager)
        dialog.setOnConfigChangedListener(object : QuarterArcConfigDialog.OnConfigChangedListener {
            override fun onConfigChanged() {
                Log.d(TAG, "配置已更新")
            }
        })
        dialog.show(fragmentManager, "QuarterArcConfig")
    }
}
