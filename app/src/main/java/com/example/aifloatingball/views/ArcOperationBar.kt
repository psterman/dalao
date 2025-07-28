package com.example.aifloatingball.views

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.AnimationDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.example.aifloatingball.R
import com.example.aifloatingball.data.ArcOperationBarSettings
import kotlin.math.*

/**
 * 圆弧布局操作栏
 * 按钮以拇指运动圆弧排列，支持展开/收拢动画
 */
class ArcOperationBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "ArcOperationBar"
        private const val DEFAULT_RADIUS = 120f // dp
        private const val ANIMATION_DURATION = 300L
        private const val BUTTON_SIZE = 56 // dp - Material Design FAB size
    }

    // 操作按钮
    private lateinit var toggleButton: FloatingActionButton
    private val operationButtons = mutableListOf<FloatingActionButton>()
    
    // 配置参数
    private var arcRadius = DEFAULT_RADIUS
    private var isLeftHanded = false
    private var isExpanded = false

    // 设置管理器
    private val settings = ArcOperationBarSettings(context)

    // 按钮配置
    private var buttonConfigs = mutableListOf<ButtonConfig>()
    
    // 操作监听器
    private var operationListener: OnOperationListener? = null
    
    /**
     * 按钮配置数据类
     */
    data class ButtonConfig(
        val id: String,
        val iconRes: Int,
        val contentDescription: String,
        val colorRes: Int,
        var isVisible: Boolean = true,
        var position: Int = 0
    )
    
    /**
     * 操作监听器接口
     */
    interface OnOperationListener {
        fun onBack()
        fun onRefresh()
        fun onHome()
        fun onNew()
    }

    init {
        setupToggleButton()
        setupOperationButtons()
        applyConfiguration()
    }

    /**
     * 设置切换按钮
     */
    private fun setupToggleButton() {
        toggleButton = FloatingActionButton(context).apply {
            setImageResource(R.drawable.ic_more_vert)
            backgroundTintList = ContextCompat.getColorStateList(context, R.color.material_blue_grey_600)
            imageTintList = ContextCompat.getColorStateList(context, android.R.color.white)
            
            setOnClickListener {
                toggleExpansion()
            }
            
            // 设置位置
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = if (isLeftHanded) Gravity.BOTTOM or Gravity.START else Gravity.BOTTOM or Gravity.END
                marginStart = if (isLeftHanded) 16.dp else 0
                marginEnd = if (isLeftHanded) 0 else 16.dp
                bottomMargin = 16.dp
            }
        }
        
        addView(toggleButton)
    }

    /**
     * 设置操作按钮
     */
    private fun setupOperationButtons() {
        buttonConfigs.forEachIndexed { index, config ->
            if (config.isVisible) {
                val button = createOperationButton(config, index)
                operationButtons.add(button)
                addView(button)
            }
        }
    }

    /**
     * 创建操作按钮
     */
    private fun createOperationButton(config: ButtonConfig, index: Int): FloatingActionButton {
        return FloatingActionButton(context).apply {
            setImageResource(config.iconRes)
            backgroundTintList = ContextCompat.getColorStateList(context, config.colorRes)
            imageTintList = ContextCompat.getColorStateList(context, android.R.color.white)
            contentDescription = config.contentDescription
            
            // 初始状态：隐藏在切换按钮位置
            alpha = 0f
            scaleX = 0f
            scaleY = 0f
            
            // 设置点击监听器
            setOnClickListener {
                handleButtonClick(config.id)
            }
            
            // 设置布局参数
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = if (isLeftHanded) Gravity.BOTTOM or Gravity.START else Gravity.BOTTOM or Gravity.END
                marginStart = if (isLeftHanded) 16.dp else 0
                marginEnd = if (isLeftHanded) 0 else 16.dp
                bottomMargin = 16.dp
            }
        }
    }

    /**
     * 处理按钮点击
     */
    private fun handleButtonClick(buttonId: String) {
        // 添加点击动画
        val clickedButton = operationButtons.find { it.contentDescription == buttonConfigs.find { config -> config.id == buttonId }?.contentDescription }
        clickedButton?.let { button ->
            // 点击缩放动画
            val scaleDown = ObjectAnimator.ofFloat(button, "scaleX", 1f, 0.9f)
            val scaleUp = ObjectAnimator.ofFloat(button, "scaleX", 0.9f, 1f)
            val scaleDownY = ObjectAnimator.ofFloat(button, "scaleY", 1f, 0.9f)
            val scaleUpY = ObjectAnimator.ofFloat(button, "scaleY", 0.9f, 1f)
            
            AnimatorSet().apply {
                playTogether(scaleDown, scaleDownY)
                duration = 100
                start()
            }
            
            AnimatorSet().apply {
                playTogether(scaleUp, scaleUpY)
                duration = 100
                startDelay = 100
                start()
            }
        }
        
        // 执行对应功能
        when (buttonId) {
            "back" -> {
                operationListener?.onBack()
            }
            "refresh" -> {
                showRefreshAnimation(clickedButton)
                operationListener?.onRefresh()
            }
            "home" -> {
                operationListener?.onHome()
            }
            "new" -> {
                operationListener?.onNew()
            }
        }
    }

    /**
     * 显示刷新动画
     */
    private fun showRefreshAnimation(button: FloatingActionButton?) {
        button?.let {
            // 旋转动画
            val rotateAnimator = ObjectAnimator.ofFloat(it, "rotation", 0f, 360f).apply {
                duration = 1000
                interpolator = AccelerateDecelerateInterpolator()
                repeatCount = 1
            }
            rotateAnimator.start()
        }
    }

    /**
     * 切换展开/收拢状态
     */
    private fun toggleExpansion() {
        if (isExpanded) {
            collapse()
        } else {
            expand()
        }
    }

    /**
     * 展开按钮
     */
    private fun expand() {
        isExpanded = true
        
        // 切换按钮图标
        toggleButton.setImageResource(R.drawable.ic_close)
        
        // 计算按钮位置并执行展开动画
        operationButtons.forEachIndexed { index, button ->
            val angle = calculateButtonAngle(index)
            val (x, y) = calculateButtonPosition(angle)
            
            // 移动和显示动画
            val moveX = ObjectAnimator.ofFloat(button, "translationX", 0f, x)
            val moveY = ObjectAnimator.ofFloat(button, "translationY", 0f, y)
            val fadeIn = ObjectAnimator.ofFloat(button, "alpha", 0f, 1f)
            val scaleX = ObjectAnimator.ofFloat(button, "scaleX", 0f, 1f)
            val scaleY = ObjectAnimator.ofFloat(button, "scaleY", 0f, 1f)
            
            AnimatorSet().apply {
                playTogether(moveX, moveY, fadeIn, scaleX, scaleY)
                duration = ANIMATION_DURATION
                interpolator = AccelerateDecelerateInterpolator()
                startDelay = index * 50L // 错开动画时间
                start()
            }
        }
    }

    /**
     * 收拢按钮
     */
    private fun collapse() {
        isExpanded = false
        
        // 切换按钮图标
        toggleButton.setImageResource(R.drawable.ic_more_vert)
        
        // 执行收拢动画
        operationButtons.forEachIndexed { index, button ->
            val moveX = ObjectAnimator.ofFloat(button, "translationX", button.translationX, 0f)
            val moveY = ObjectAnimator.ofFloat(button, "translationY", button.translationY, 0f)
            val fadeOut = ObjectAnimator.ofFloat(button, "alpha", 1f, 0f)
            val scaleX = ObjectAnimator.ofFloat(button, "scaleX", 1f, 0f)
            val scaleY = ObjectAnimator.ofFloat(button, "scaleY", 1f, 0f)
            
            AnimatorSet().apply {
                playTogether(moveX, moveY, fadeOut, scaleX, scaleY)
                duration = ANIMATION_DURATION
                interpolator = AccelerateDecelerateInterpolator()
                startDelay = (operationButtons.size - index - 1) * 50L // 反向错开动画时间
                start()
            }
        }
    }

    /**
     * 计算按钮角度
     */
    private fun calculateButtonAngle(index: Int): Float {
        val totalButtons = operationButtons.size
        val angleStep = 90f / (totalButtons + 1) // 90度四分之一圆
        return angleStep * (index + 1)
    }

    /**
     * 计算按钮位置
     */
    private fun calculateButtonPosition(angle: Float): Pair<Float, Float> {
        val radiusPixels = arcRadius * resources.displayMetrics.density
        val radian = Math.toRadians(angle.toDouble())
        
        val x = if (isLeftHanded) {
            radiusPixels * cos(radian).toFloat()
        } else {
            -radiusPixels * cos(radian).toFloat()
        }
        
        val y = -radiusPixels * sin(radian).toFloat()
        
        return Pair(x, y)
    }

    /**
     * 应用配置
     */
    private fun applyConfiguration() {
        // 从设置中读取配置
        isLeftHanded = settings.isLeftHanded
        arcRadius = settings.arcRadius

        // 加载按钮配置
        loadButtonConfigs()

        // 重新设置按钮
        setupOperationButtons()
    }

    /**
     * 加载按钮配置
     */
    private fun loadButtonConfigs() {
        buttonConfigs.clear()

        val configs = settings.getAllButtonConfigs()
        configs.forEach { config ->
            if (config.isVisible) {
                buttonConfigs.add(ButtonConfig(
                    config.id,
                    when(config.id) {
                        "back" -> R.drawable.ic_arrow_back
                        "refresh" -> R.drawable.ic_refresh
                        "home" -> R.drawable.ic_home
                        "new" -> R.drawable.ic_add
                        else -> R.drawable.ic_more_vert
                    },
                    config.name,
                    R.color.material_blue_grey_600,
                    true,
                    config.position
                ))
            }
        }

        // 按位置排序
        buttonConfigs.sortBy { it.position }
    }

    /**
     * 设置操作监听器
     */
    fun setOnOperationListener(listener: OnOperationListener) {
        this.operationListener = listener
    }

    /**
     * 设置左手模式
     */
    fun setLeftHanded(leftHanded: Boolean) {
        this.isLeftHanded = leftHanded
        // 重新布局
        setupToggleButton()
        setupOperationButtons()
    }

    /**
     * 设置圆弧半径
     */
    fun setArcRadius(radius: Float) {
        this.arcRadius = radius
    }

    /**
     * dp转px扩展属性
     */
    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}
