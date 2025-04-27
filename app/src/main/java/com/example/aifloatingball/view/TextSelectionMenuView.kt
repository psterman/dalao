package com.example.aifloatingball.view

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.aifloatingball.R

/**
 * 文本选择菜单视图
 * 展示复制、分享、搜索等文本操作选项
 */
class TextSelectionMenuView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val copyButton: View
    private val shareButton: View
    private val searchButton: View
    
    init {
        // 设置基本属性
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(16, 8, 16, 8)
        
        // 创建背景
        val background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = 16f
            setStroke(1, Color.LTGRAY)
        }
        setBackground(background)
        
        // 设置阴影
        elevation = 8f
        
        // 添加按钮
        copyButton = createButton("复制", R.drawable.ic_content_copy)
        shareButton = createButton("分享", R.drawable.ic_share)
        searchButton = createButton("搜索", R.drawable.ic_search)
        
        // 添加到布局
        addView(copyButton)
        addView(createDivider())
        addView(shareButton)
        addView(createDivider())
        addView(searchButton)
    }
    
    /**
     * 创建菜单按钮
     */
    private fun createButton(text: String, iconRes: Int): View {
        val button = LayoutInflater.from(context).inflate(R.layout.view_menu_item, this, false)
        
        // 设置图标
        val iconView = button.findViewById<ImageButton>(R.id.item_icon)
        iconView.setImageResource(iconRes)
        
        // 设置文本
        val textView = button.findViewById<TextView>(R.id.item_text)
        textView.text = text
        
        return button
    }
    
    /**
     * 创建分隔线
     */
    private fun createDivider(): View {
        return View(context).apply {
            layoutParams = LayoutParams(1, LayoutParams.MATCH_PARENT).apply {
                setMargins(8, 0, 8, 0)
            }
            setBackgroundColor(Color.LTGRAY)
        }
    }
    
    /**
     * 设置复制按钮点击监听器
     */
    fun setOnCopyClickListener(listener: OnClickListener) {
        copyButton.setOnClickListener(listener)
    }
    
    /**
     * 设置分享按钮点击监听器
     */
    fun setOnShareClickListener(listener: OnClickListener) {
        shareButton.setOnClickListener(listener)
    }
    
    /**
     * 设置搜索按钮点击监听器
     */
    fun setOnSearchClickListener(listener: OnClickListener) {
        searchButton.setOnClickListener(listener)
    }
} 