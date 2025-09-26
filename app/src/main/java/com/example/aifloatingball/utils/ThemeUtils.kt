package com.example.aifloatingball.utils

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.view.View
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputLayout
import com.example.aifloatingball.R

/**
 * 主题工具类 - 用于处理暗色模式
 */
object ThemeUtils {
    
    /**
     * 检查当前是否为暗色模式
     */
    fun isDarkMode(context: Context): Boolean {
        val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }
    
    /**
     * 应用AI助手中心的主题
     */
    fun applyAIAssistantTheme(view: View, context: Context) {
        val isDarkMode = isDarkMode(context)
        
        if (isDarkMode) {
            // 暗色模式
            view.setBackgroundColor(context.getColor(R.color.ai_assistant_center_background_light))
            
            // 递归应用主题到所有子视图
            applyThemeToChildren(view, isDarkMode, context)
        }
    }
    
    /**
     * 递归应用主题到子视图
     */
    private fun applyThemeToChildren(view: View, isDarkMode: Boolean, context: Context) {
        if (view is View) {
            when (view) {
                is TextView -> {
                    // 设置文本颜色
                    when {
                        view.textSize > 16f -> {
                            // 标题文本
                            view.setTextColor(context.getColor(R.color.ai_assistant_text_primary))
                        }
                        view.textSize > 12f -> {
                            // 普通文本
                            view.setTextColor(context.getColor(R.color.ai_assistant_text_secondary))
                        }
                        else -> {
                            // 提示文本
                            view.setTextColor(context.getColor(R.color.ai_assistant_text_hint))
                        }
                    }
                }
                is MaterialCardView -> {
                    // 设置卡片背景
                    view.setCardBackgroundColor(context.getColor(R.color.ai_assistant_card_background))
                }
                is TextInputLayout -> {
                    // 设置输入框主题
                    view.setBoxBackgroundColor(context.getColor(R.color.ai_assistant_input_background))
                    view.setHintTextColor(android.content.res.ColorStateList.valueOf(context.getColor(R.color.ai_assistant_text_hint)))
                }
            }
        }
        
        // 递归处理子视图
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                applyThemeToChildren(view.getChildAt(i), isDarkMode, context)
            }
        }
    }
    
    /**
     * 获取主题相关的颜色
     */
    fun getThemeColor(context: Context, lightColorRes: Int, darkColorRes: Int): Int {
        return if (isDarkMode(context)) {
            context.getColor(darkColorRes)
        } else {
            context.getColor(lightColorRes)
        }
    }
    
    /**
     * 获取AI助手中心的背景颜色
     */
    fun getAIAssistantBackgroundColor(context: Context): Int {
        return if (isDarkMode(context)) {
            context.getColor(R.color.ai_assistant_center_background_light)
        } else {
            context.getColor(R.color.ai_assistant_center_background_light)
        }
    }
    
    /**
     * 获取AI助手中心的卡片背景颜色
     */
    fun getAIAssistantCardColor(context: Context): Int {
        return if (isDarkMode(context)) {
            context.getColor(R.color.ai_assistant_card_background)
        } else {
            context.getColor(R.color.ai_assistant_card_background)
        }
    }
    
    /**
     * 获取AI助手中心的文本颜色
     */
    fun getAIAssistantTextColor(context: Context, isPrimary: Boolean = true): Int {
        return if (isDarkMode(context)) {
            if (isPrimary) context.getColor(R.color.ai_assistant_text_primary) else context.getColor(R.color.ai_assistant_text_secondary)
        } else {
            if (isPrimary) context.getColor(R.color.ai_assistant_text_primary) else context.getColor(R.color.ai_assistant_text_secondary)
        }
    }
}
