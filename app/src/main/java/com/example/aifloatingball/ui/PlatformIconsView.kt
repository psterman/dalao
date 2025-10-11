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
        PlatformIconLoader.preloadAllPlatformIcons()
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
    }
    
    /**
     * 创建平台图标
     * 使用与软件tab相同的图标样式
     */
    private fun createPlatformIcon(platform: PlatformJumpManager.PlatformInfo, query: String): ImageView {
        val iconView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                marginEnd = iconMargin
            }
            
            // 使用PlatformIconLoader加载图标
            PlatformIconLoader.loadPlatformIcon(this, platform.name, context)
            
            // 设置与软件tab相同的图标样式
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = null // 清除背景，让IconProcessor处理
            
            // 设置点击效果
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
