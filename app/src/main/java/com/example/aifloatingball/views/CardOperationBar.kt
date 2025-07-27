package com.example.aifloatingball.views

import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.aifloatingball.R
import com.google.android.material.button.MaterialButton

/**
 * Material Design风格的卡片操作栏
 * 提供新建、关闭、刷新、后退、预览等操作
 */
class CardOperationBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    // 操作按钮
    private lateinit var btnBack: MaterialButton
    private lateinit var btnRefresh: MaterialButton
    private lateinit var btnHome: MaterialButton
    private lateinit var btnNewCard: MaterialButton
    private lateinit var btnCloseCard: MaterialButton
    private lateinit var btnPreview: MaterialButton
    
    // 信息显示
    private lateinit var tvCardInfo: TextView
    
    // 操作监听器
    private var operationListener: OnOperationListener? = null
    
    // 是否显示状态
    private var isVisible = true

    /**
     * 操作监听器接口
     */
    interface OnOperationListener {
        fun onNewCard()
        fun onCloseCard()
        fun onRefresh()
        fun onGoBack()
        fun onGoHome()
        fun onPreviewToggle()
    }

    init {
        setupView()
        setupButtons()
        applyMaterialDesign()
    }

    /**
     * 设置视图
     */
    private fun setupView() {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        
        // 设置内边距
        val padding = (16 * resources.displayMetrics.density).toInt()
        setPadding(padding, padding / 2, padding, padding / 2)
        
        // 设置高度
        layoutParams = LayoutParams(
            LayoutParams.MATCH_PARENT,
            (56 * resources.displayMetrics.density).toInt()
        )
    }

    /**
     * 设置按钮
     */
    private fun setupButtons() {
        // 后退按钮
        btnBack = createMaterialButton("←", "后退").apply {
            setOnClickListener { operationListener?.onGoBack() }
        }
        addView(btnBack)
        
        // 刷新按钮
        btnRefresh = createMaterialButton("↻", "刷新").apply {
            setOnClickListener { operationListener?.onRefresh() }
        }
        addView(btnRefresh)

        // 首页按钮
        btnHome = createMaterialButton("⌂", "首页").apply {
            setOnClickListener { operationListener?.onGoHome() }
        }
        addView(btnHome)

        // 卡片信息（居中显示）
        tvCardInfo = TextView(context).apply {
            text = "1 / 1"
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, android.R.color.white))
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        addView(tvCardInfo)
        
        // 新建卡片按钮
        btnNewCard = createMaterialButton("+", "新建").apply {
            setOnClickListener { operationListener?.onNewCard() }
        }
        addView(btnNewCard)
        
        // 关闭卡片按钮
        btnCloseCard = createMaterialButton("×", "关闭").apply {
            setOnClickListener { operationListener?.onCloseCard() }
        }
        addView(btnCloseCard)
        
        // 预览按钮
        btnPreview = createMaterialButton("⊞", "预览").apply {
            setOnClickListener { operationListener?.onPreviewToggle() }
        }
        addView(btnPreview)
    }

    /**
     * 创建Material按钮
     */
    private fun createMaterialButton(text: String, contentDescription: String): MaterialButton {
        return MaterialButton(context).apply {
            this.contentDescription = contentDescription
            textSize = 14f

            // 设置按钮样式
            val size = (48 * resources.displayMetrics.density).toInt()
            layoutParams = LayoutParams(size, size).apply {
                marginStart = (6 * resources.displayMetrics.density).toInt()
                marginEnd = (6 * resources.displayMetrics.density).toInt()
            }

            // 设置圆形背景
            cornerRadius = size / 2

            // 设置Material Design 3.0样式和图标
            when (contentDescription) {
                "后退" -> {
                    setBackgroundColor(ContextCompat.getColor(context, R.color.material_blue_600))
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_arrow_back)
                }
                "刷新" -> {
                    setBackgroundColor(ContextCompat.getColor(context, R.color.material_green_600))
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_refresh)
                }
                "首页" -> {
                    setBackgroundColor(ContextCompat.getColor(context, R.color.material_indigo_600))
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_home)
                }
                "新建" -> {
                    setBackgroundColor(ContextCompat.getColor(context, R.color.material_purple_600))
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_add)
                }
                "关闭" -> {
                    setBackgroundColor(ContextCompat.getColor(context, R.color.material_red_600))
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_close)
                }
                "预览" -> {
                    setBackgroundColor(ContextCompat.getColor(context, R.color.material_orange_600))
                    icon = ContextCompat.getDrawable(context, R.drawable.ic_view_module)
                }
                else -> {
                    setBackgroundColor(ContextCompat.getColor(context, R.color.material_blue_500))
                    this.text = text
                }
            }

            // 设置图标属性
            if (icon != null) {
                iconTint = ContextCompat.getColorStateList(context, android.R.color.white)
                iconSize = (20 * resources.displayMetrics.density).toInt()
                iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
                this.text = "" // 隐藏文字，只显示图标
            }

            // 设置文字颜色
            setTextColor(ContextCompat.getColor(context, android.R.color.white))

            // 设置点击效果和阴影
            elevation = (4 * resources.displayMetrics.density)
            isClickable = true
            isFocusable = true

            // 设置Material Design涟漪效果
            setRippleColorResource(android.R.color.white)

            // 添加点击动画
            setOnTouchListener { view, event ->
                when (event.action) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.95f).apply {
                            duration = 100
                            start()
                        }
                        ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.95f).apply {
                            duration = 100
                            start()
                        }
                    }
                    android.view.MotionEvent.ACTION_UP,
                    android.view.MotionEvent.ACTION_CANCEL -> {
                        ObjectAnimator.ofFloat(view, "scaleX", 0.95f, 1f).apply {
                            duration = 100
                            start()
                        }
                        ObjectAnimator.ofFloat(view, "scaleY", 0.95f, 1f).apply {
                            duration = 100
                            start()
                        }
                    }
                }
                false
            }
        }
    }

    /**
     * 应用Material Design样式
     */
    private fun applyMaterialDesign() {
        // 设置背景
        background = GradientDrawable().apply {
            // 半透明黑色背景
            setColor(0x88000000.toInt())
            
            // 圆角
            cornerRadius = (8 * resources.displayMetrics.density)
            
            // 阴影效果
            setStroke(
                (1 * resources.displayMetrics.density).toInt(),
                ContextCompat.getColor(context, R.color.material_blue_200)
            )
        }
        
        // 设置阴影
        elevation = (8 * resources.displayMetrics.density)
    }

    /**
     * 更新卡片信息显示
     */
    fun updateCardInfo(title: String, currentIndex: Int, totalCount: Int) {
        val displayTitle = if (title.length > 10) {
            title.substring(0, 10) + "..."
        } else {
            title
        }
        tvCardInfo.text = "$displayTitle ($currentIndex/$totalCount)"
    }

    /**
     * 设置操作监听器
     */
    fun setOnOperationListener(listener: OnOperationListener) {
        this.operationListener = listener
    }

    /**
     * 显示操作栏
     */
    fun show() {
        if (!isVisible) {
            isVisible = true
            visibility = View.VISIBLE
            
            // 从底部滑入动画
            translationY = height.toFloat()
            animate()
                .translationY(0f)
                .setDuration(300)
                .start()
        }
    }

    /**
     * 隐藏操作栏
     */
    fun hide() {
        if (isVisible) {
            isVisible = false
            
            // 滑出到底部动画
            animate()
                .translationY(height.toFloat())
                .setDuration(300)
                .withEndAction {
                    visibility = View.GONE
                }
                .start()
        }
    }

    /**
     * 切换显示状态
     */
    fun toggle() {
        if (isVisible) {
            hide()
        } else {
            show()
        }
    }

    /**
     * 更新按钮状态
     */
    fun updateButtonStates(canGoBack: Boolean, isLoading: Boolean, cardCount: Int) {
        btnBack.isEnabled = canGoBack
        btnBack.alpha = if (canGoBack) 1f else 0.5f
        
        btnRefresh.text = if (isLoading) "⏹" else "↻"
        btnRefresh.contentDescription = if (isLoading) "停止" else "刷新"
        
        btnCloseCard.isEnabled = cardCount > 1
        btnCloseCard.alpha = if (cardCount > 1) 1f else 0.5f
    }
}
