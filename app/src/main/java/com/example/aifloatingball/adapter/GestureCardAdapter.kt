package com.example.aifloatingball.adapter

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ImageButton
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.webview.GestureCardWebViewManager

/**
 * 手势卡片适配器
 * 用于ViewPager2显示全屏WebView卡片
 */
class GestureCardAdapter(
    private val cards: MutableList<GestureCardWebViewManager.WebViewCardData>,
    private val onWebViewSetup: (WebView, GestureCardWebViewManager.WebViewCardData) -> Unit,
    private val onCardClose: (String) -> Unit  // 添加关闭回调
) : RecyclerView.Adapter<GestureCardAdapter.CardViewHolder>() {

    /**
     * 卡片ViewHolder
     */
    class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val container: FrameLayout = itemView as FrameLayout
        var closeButton: ImageButton? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        // 创建全屏容器
        val container = FrameLayout(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        
        return CardViewHolder(container)
    }

    /**
     * 创建红色关闭按钮
     */
    private fun createCloseButton(context: Context): ImageButton {
        return ImageButton(context).apply {
            // 设置按钮ID，方便在ViewHolder中查找
            id = android.R.id.button1
            
            // 设置按钮大小
            val buttonSize = (48 * context.resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(buttonSize, buttonSize).apply {
                // 位置在右上角
                gravity = android.view.Gravity.TOP or android.view.Gravity.END
                topMargin = (16 * context.resources.displayMetrics.density).toInt()
                rightMargin = (16 * context.resources.displayMetrics.density).toInt()
            }
            
            // 设置按钮背景为红色圆形
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FF4444")) // 红色背景
                setStroke((2 * context.resources.displayMetrics.density).toInt(), Color.WHITE) // 白色边框
            }
            
            // 设置关闭图标（使用系统图标）
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            
            // 设置按钮内容描述
            contentDescription = "关闭网页"
            
            // 设置按钮样式
            scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
            val padding = (8 * context.resources.displayMetrics.density).toInt()
            setPadding(padding, padding, padding, padding)
            
            // 设置点击效果
            setOnClickListener {
                // 点击效果：稍微缩小
                animate().scaleX(0.9f).scaleY(0.9f).setDuration(100)
                    .withEndAction {
                        animate().scaleX(1.0f).scaleY(1.0f).setDuration(100)
                    }
            }
        }
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val cardData = cards[position]
        val webView = cardData.webView
        
        // 移除WebView的旧父容器
        (webView.parent as? ViewGroup)?.removeView(webView)
        
        // 清空容器
        holder.container.removeAllViews()
        
        // 添加WebView到容器
        holder.container.addView(webView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))
        
        // 创建并添加关闭按钮（确保在最上层）
        val closeButton = createCloseButton(holder.container.context)
        holder.container.addView(closeButton)
        holder.closeButton = closeButton
        
        // 设置关闭按钮点击监听器
        closeButton.setOnClickListener {
            onCardClose(cardData.url)
        }
        
        // 设置WebView回调（如果需要）
        onWebViewSetup(webView, cardData)
    }

    override fun getItemCount(): Int = cards.size

    override fun onViewRecycled(holder: CardViewHolder) {
        super.onViewRecycled(holder)
        // 清理容器，避免内存泄漏
        holder.container.removeAllViews()
    }
}
