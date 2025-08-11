package com.example.aifloatingball.ui

import android.content.Context
import android.content.res.Configuration
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager

/**
 * 自适应对话界面布局管理器
 * 支持左右手模式和暗色/亮色模式的动态布局调整
 */
class AdaptiveChatLayoutManager(private val context: Context) {
    
    private val settingsManager = SettingsManager.getInstance(context)
    
    /**
     * 布局模式枚举
     */
    enum class LayoutMode {
        RIGHT_HANDED,  // 右手模式
        LEFT_HANDED    // 左手模式
    }
    
    /**
     * 主题模式枚举
     */
    enum class ThemeMode {
        LIGHT,  // 亮色模式
        DARK    // 暗色模式
    }
    
    /**
     * 获取当前布局模式
     */
    fun getCurrentLayoutMode(): LayoutMode {
        return if (settingsManager.isLeftHandedModeEnabled()) {
            LayoutMode.LEFT_HANDED
        } else {
            LayoutMode.RIGHT_HANDED
        }
    }
    
    /**
     * 获取当前主题模式
     */
    fun getCurrentThemeMode(): ThemeMode {
        val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return when (nightModeFlags) {
            Configuration.UI_MODE_NIGHT_YES -> ThemeMode.DARK
            else -> ThemeMode.LIGHT
        }
    }
    
    /**
     * 应用消息气泡布局
     */
    fun applyMessageBubbleLayout(
        messageContainer: ViewGroup,
        isUserMessage: Boolean,
        layoutMode: LayoutMode = getCurrentLayoutMode()
    ) {
        val layoutParams = messageContainer.layoutParams as? LinearLayout.LayoutParams
        layoutParams?.let { params ->
            when (layoutMode) {
                LayoutMode.RIGHT_HANDED -> {
                    // 右手模式：用户消息在右，AI消息在左
                    if (isUserMessage) {
                        params.gravity = android.view.Gravity.END
                        params.marginStart = dpToPx(48)
                        params.marginEnd = dpToPx(16)
                    } else {
                        params.gravity = android.view.Gravity.START
                        params.marginStart = dpToPx(16)
                        params.marginEnd = dpToPx(48)
                    }
                }
                LayoutMode.LEFT_HANDED -> {
                    // 左手模式：用户消息在左，AI消息在右
                    if (isUserMessage) {
                        params.gravity = android.view.Gravity.START
                        params.marginStart = dpToPx(16)
                        params.marginEnd = dpToPx(48)
                    } else {
                        params.gravity = android.view.Gravity.END
                        params.marginStart = dpToPx(48)
                        params.marginEnd = dpToPx(16)
                    }
                }
            }
            messageContainer.layoutParams = params
        }
    }
    
    /**
     * 应用输入区域布局
     */
    fun applyInputAreaLayout(
        inputContainer: LinearLayout,
        layoutMode: LayoutMode = getCurrentLayoutMode()
    ) {
        // 获取子视图
        val functionButtons = inputContainer.findViewById<View>(R.id.btn_toggle_functions)
        val inputArea = inputContainer.findViewById<View>(R.id.input_area_container)
        val sendButton = inputContainer.findViewById<View>(R.id.btn_send)
        
        // 清空容器
        inputContainer.removeAllViews()
        
        when (layoutMode) {
            LayoutMode.RIGHT_HANDED -> {
                // 右手模式：功能按钮 - 输入框 - 发送按钮
                functionButtons?.let { inputContainer.addView(it) }
                inputArea?.let { inputContainer.addView(it) }
                sendButton?.let { inputContainer.addView(it) }
            }
            LayoutMode.LEFT_HANDED -> {
                // 左手模式：发送按钮 - 输入框 - 功能按钮
                sendButton?.let { inputContainer.addView(it) }
                inputArea?.let { inputContainer.addView(it) }
                functionButtons?.let { inputContainer.addView(it) }
            }
        }
    }
    
    /**
     * 应用主题颜色
     */
    fun applyThemeColors(view: View, themeMode: ThemeMode = getCurrentThemeMode()) {
        when (themeMode) {
            ThemeMode.LIGHT -> applyLightThemeColors(view)
            ThemeMode.DARK -> applyDarkThemeColors(view)
        }
    }
    
    /**
     * 应用亮色主题颜色
     */
    private fun applyLightThemeColors(view: View) {
        // 根据view的类型应用颜色，而不是依赖特定的ID
        when (view::class.java.simpleName) {
            "RecyclerView" -> {
                view.setBackgroundColor(ContextCompat.getColor(context, R.color.material_chat_background_light))
            }
            "MaterialCardView" -> {
                // 检查是否是消息气泡
                if (view.tag == "user_bubble") {
                    view.setBackgroundColor(ContextCompat.getColor(context, R.color.material_chat_user_bubble_light))
                } else if (view.tag == "ai_bubble") {
                    view.setBackgroundColor(ContextCompat.getColor(context, R.color.material_chat_ai_bubble_light))
                }
            }
            "LinearLayout" -> {
                // 输入区域背景
                view.setBackgroundColor(ContextCompat.getColor(context, R.color.material_chat_input_background_light))
            }
        }
    }
    
    /**
     * 应用暗色主题颜色
     */
    private fun applyDarkThemeColors(view: View) {
        // 根据view的类型应用颜色，而不是依赖特定的ID
        when (view::class.java.simpleName) {
            "RecyclerView" -> {
                view.setBackgroundColor(ContextCompat.getColor(context, R.color.material_chat_background))
            }
            "MaterialCardView" -> {
                // 检查是否是消息气泡
                if (view.tag == "user_bubble") {
                    view.setBackgroundColor(ContextCompat.getColor(context, R.color.material_chat_user_bubble))
                } else if (view.tag == "ai_bubble") {
                    view.setBackgroundColor(ContextCompat.getColor(context, R.color.material_chat_ai_bubble))
                }
            }
            "LinearLayout" -> {
                // 输入区域背景
                view.setBackgroundColor(ContextCompat.getColor(context, R.color.material_chat_input_background))
            }
        }
    }
    
    /**
     * 获取消息气泡圆角配置
     */
    fun getMessageBubbleCorners(
        isUserMessage: Boolean,
        layoutMode: LayoutMode = getCurrentLayoutMode()
    ): FloatArray {
        val cornerRadius = dpToPx(18).toFloat()
        val smallCorner = dpToPx(4).toFloat()
        
        return when (layoutMode) {
            LayoutMode.RIGHT_HANDED -> {
                if (isUserMessage) {
                    // 右手模式用户消息：右下角小圆角
                    floatArrayOf(cornerRadius, cornerRadius, smallCorner, smallCorner, cornerRadius, cornerRadius, cornerRadius, cornerRadius)
                } else {
                    // 右手模式AI消息：左下角小圆角
                    floatArrayOf(smallCorner, smallCorner, cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius)
                }
            }
            LayoutMode.LEFT_HANDED -> {
                if (isUserMessage) {
                    // 左手模式用户消息：左下角小圆角
                    floatArrayOf(smallCorner, smallCorner, cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius, cornerRadius)
                } else {
                    // 左手模式AI消息：右下角小圆角
                    floatArrayOf(cornerRadius, cornerRadius, smallCorner, smallCorner, cornerRadius, cornerRadius, cornerRadius, cornerRadius)
                }
            }
        }
    }
    
    /**
     * dp转px
     */
    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
    
    /**
     * 应用输入区域自适应布局
     */
    fun applyInputAreaAdaptiveLayout(
        inputRoot: android.view.View,
        layoutMode: LayoutMode = getCurrentLayoutMode()
    ) {
        val leftContainer = inputRoot.findViewById<android.widget.FrameLayout>(R.id.left_button_container)
        val rightContainer = inputRoot.findViewById<android.widget.FrameLayout>(R.id.right_button_container)

        val toggleButton = inputRoot.findViewById<android.widget.ImageButton>(R.id.btn_toggle_functions)
        val toggleButtonRight = inputRoot.findViewById<android.widget.ImageButton>(R.id.btn_toggle_functions_right)
        val sendButtonLeft = inputRoot.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.btn_send_left)
        val sendButtonRight = inputRoot.findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.btn_send_right)

        when (layoutMode) {
            LayoutMode.RIGHT_HANDED -> {
                // 右手模式：左侧功能按钮，右侧发送按钮
                toggleButton?.visibility = android.view.View.VISIBLE
                toggleButtonRight?.visibility = android.view.View.GONE
                sendButtonLeft?.visibility = android.view.View.GONE
                sendButtonRight?.visibility = android.view.View.VISIBLE
            }
            LayoutMode.LEFT_HANDED -> {
                // 左手模式：左侧发送按钮，右侧功能按钮
                toggleButton?.visibility = android.view.View.GONE
                toggleButtonRight?.visibility = android.view.View.VISIBLE
                sendButtonLeft?.visibility = android.view.View.VISIBLE
                sendButtonRight?.visibility = android.view.View.GONE
            }
        }
    }

    /**
     * 切换功能按钮区域显示状态
     */
    fun toggleFunctionButtonsVisibility(
        inputRoot: android.view.View,
        isVisible: Boolean
    ) {
        val functionScroll = inputRoot.findViewById<android.widget.HorizontalScrollView>(R.id.function_buttons_scroll)
        val functionDivider = inputRoot.findViewById<android.view.View>(R.id.function_divider)

        functionScroll?.visibility = if (isVisible) android.view.View.VISIBLE else android.view.View.GONE
        functionDivider?.visibility = if (isVisible) android.view.View.VISIBLE else android.view.View.GONE

        // 更新切换按钮图标
        val toggleButton = inputRoot.findViewById<android.widget.ImageButton>(R.id.btn_toggle_functions)
        val toggleButtonRight = inputRoot.findViewById<android.widget.ImageButton>(R.id.btn_toggle_functions_right)

        val iconRes = if (isVisible) R.drawable.ic_close else R.drawable.ic_add
        toggleButton?.setImageResource(iconRes)
        toggleButtonRight?.setImageResource(iconRes)
    }

    /**
     * 监听设置变化
     */
    fun onSettingsChanged(callback: (LayoutMode, ThemeMode) -> Unit) {
        // 这里可以添加设置变化监听逻辑
        callback(getCurrentLayoutMode(), getCurrentThemeMode())
    }
}
