package com.example.aifloatingball.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import com.example.aifloatingball.R
import com.example.aifloatingball.manager.PlatformJumpManager
import com.example.aifloatingball.utils.PlatformIconLoader

/**
 * 平台图标组件
 * 显示相关平台的图标，支持点击跳转和水平滚动
 */
class PlatformIconsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {
    
    private val platformJumpManager = PlatformJumpManager(context)
    private val iconSize = context.resources.getDimensionPixelSize(R.dimen.platform_icon_size)
    private val iconMargin = context.resources.getDimensionPixelSize(R.dimen.platform_icon_margin)
    private val iconContainer: LinearLayout
    
    // 回调接口，用于处理➕号点击事件
    var onAddPlatformClickListener: (() -> Unit)? = null
    
    init {
        // 创建水平滚动的LinearLayout容器
        iconContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, 0)
        }
        
        // 设置滚动视图
        addView(iconContainer)
        isHorizontalScrollBarEnabled = false
        isVerticalScrollBarEnabled = false
        
        // 预加载所有平台图标
        PlatformIconLoader.preloadAllPlatformIcons(context)
    }
    
    /**
     * 设置平台图标
     */
    fun setPlatforms(platforms: List<PlatformJumpManager.PlatformInfo>, query: String) {
        iconContainer.removeAllViews()
        
        platforms.forEach { platform ->
            val iconView = createPlatformIcon(platform, query)
            iconContainer.addView(iconView)
        }
        
        // 添加➕号按钮
        addPlusButton()
    }
    
    /**
     * 添加➕号按钮
     */
    private fun addPlusButton() {
        val plusButton = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                marginStart = iconMargin
            }
            
            // 设置➕号图标
            setImageResource(R.drawable.ic_add_platform)
            
            // 设置点击效果
            background = ContextCompat.getDrawable(context, R.drawable.platform_icon_background)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(
                context.resources.getDimensionPixelSize(R.dimen.platform_icon_padding),
                context.resources.getDimensionPixelSize(R.dimen.platform_icon_padding),
                context.resources.getDimensionPixelSize(R.dimen.platform_icon_padding),
                context.resources.getDimensionPixelSize(R.dimen.platform_icon_padding)
            )
            
            // 设置点击事件
            setOnClickListener {
                onAddPlatformClickListener?.invoke()
            }
            
            // 设置内容描述
            contentDescription = "添加或移除平台图标"
        }
        
        iconContainer.addView(plusButton)
    }
    
    /**
     * 创建平台图标
     */
    private fun createPlatformIcon(platform: PlatformJumpManager.PlatformInfo, query: String): ImageView {
        val iconView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                marginEnd = iconMargin
            }
            
            // 使用PlatformIconLoader加载图标
            PlatformIconLoader.loadPlatformIcon(this, platform.name, context)
            
            // 设置点击效果
            background = ContextCompat.getDrawable(context, R.drawable.platform_icon_background)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            setPadding(
                context.resources.getDimensionPixelSize(R.dimen.platform_icon_padding),
                context.resources.getDimensionPixelSize(R.dimen.platform_icon_padding),
                context.resources.getDimensionPixelSize(R.dimen.platform_icon_padding),
                context.resources.getDimensionPixelSize(R.dimen.platform_icon_padding)
            )
            
            // 设置点击事件，传递用户查询
            setOnClickListener {
                platformJumpManager.jumpToPlatform(platform.name, query)
            }
            
            // 设置内容描述
            contentDescription = "在${platform.name}搜索相关内容"
        }
        
        return iconView
    }
    
    /**
     * 根据查询内容智能显示平台图标
     * 现在默认显示所有平台，确保八个图标完整显示
     */
    fun showRelevantPlatforms(query: String) {
        val relevantPlatforms = platformJumpManager.getRelevantPlatforms(query)
        setPlatforms(relevantPlatforms, query)
    }
    
    /**
     * 显示所有平台图标
     */
    fun showAllPlatforms(query: String) {
        val allPlatforms = platformJumpManager.getAllPlatforms()
        setPlatforms(allPlatforms, query)
    }
}
