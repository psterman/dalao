package com.example.aifloatingball.adapter

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.ui.cardview.CardViewModeManager
import com.example.aifloatingball.ui.webview.CustomWebView

/**
 * 搜索结果卡片适配器
 * 用于RecyclerView显示搜索结果卡片
 */
class SearchResultCardAdapter(
    private val cards: MutableList<CardViewModeManager.SearchResultCardData>,
    private val onCardClick: (CardViewModeManager.SearchResultCardData) -> Unit,
    private val onCardLongClick: (CardViewModeManager.SearchResultCardData) -> Unit,
    private val cardViewModeManager: CardViewModeManager? = null
) : RecyclerView.Adapter<SearchResultCardAdapter.CardViewHolder>() {

    companion object {
        private const val TAG = "SearchResultCardAdapter"
    }

    /**
     * 卡片ViewHolder
     */
    class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleView: TextView = itemView.findViewById(R.id.card_title)
        val engineView: TextView = itemView.findViewById(R.id.card_engine)
        val webViewContainer: FrameLayout = itemView.findViewById(R.id.card_webview_container)
        val appIconView: ImageView = itemView.findViewById(R.id.card_app_icon)
        val appIconFloating: ImageView = itemView.findViewById(R.id.card_app_icon_floating)
        val floatingButtonsContainer: LinearLayout = itemView.findViewById(R.id.card_floating_buttons)
        val downloadButton: Button = itemView.findViewById(R.id.card_download_button)
        val openButton: Button = itemView.findViewById(R.id.card_open_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_result_card, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        if (position >= cards.size) return
        
        val cardData = cards[position]
        val context = holder.itemView.context
        
        // 检测暗色模式
        val isDarkMode = (context.resources.configuration.uiMode and 
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) == 
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        
        // 设置标题和引擎名称，支持暗色/亮色模式
        holder.titleView.text = cardData.title.ifEmpty { cardData.searchQuery }
        holder.titleView.setTextColor(
            if (isDarkMode) 0xFFFFFFFF.toInt() else 0xFF212121.toInt()
        )
        
        // 设置标题栏背景为圆角卡片样式
        val headerBackground = android.graphics.drawable.GradientDrawable().apply {
            cornerRadius = dpToPx(context, 8).toFloat()
            setColor(if (isDarkMode) 0xFF2C2C2C.toInt() else 0xFFF5F5F5.toInt())
        }
        holder.titleView.parent?.let { parent ->
            if (parent is ViewGroup) {
                parent.background = headerBackground
            }
        }
        
        holder.engineView.text = cardData.engineName
        
        // 检查是否有对应的app
        val hasApp = this.cardViewModeManager?.isAppInstalledForEngine(cardData.engineKey) == true
        val packageName = this.cardViewModeManager?.getPackageNameForEngine(cardData.engineKey)
        val hasPackageName = packageName != null
        
        // 设置浮动按钮的显示逻辑
        if (hasPackageName) {
            if (hasApp) {
                // app已安装：显示app图标和"打开"按钮
                holder.floatingButtonsContainer.visibility = View.VISIBLE
                holder.appIconView.visibility = View.GONE  // 隐藏头部的app图标
                holder.appIconFloating.visibility = View.VISIBLE  // 显示浮动区域的app图标
                holder.downloadButton.visibility = View.GONE
                holder.openButton.visibility = View.VISIBLE
                
                try {
                    // 获取app图标
                    val appIcon = context.packageManager.getApplicationIcon(packageName as String)
                    holder.appIconFloating.setImageDrawable(appIcon)
                } catch (e: PackageManager.NameNotFoundException) {
                    // 如果获取图标失败，隐藏图标
                    holder.appIconFloating.visibility = View.GONE
                }
                
                // 设置app图标点击事件：跳转到app搜索
                holder.appIconFloating.setOnClickListener {
                    this.cardViewModeManager?.tryJumpToApp(cardData.engineKey, cardData.searchQuery)
                }
                
                // 设置打开按钮点击事件：在卡片内打开
                holder.openButton.setOnClickListener {
                    onCardClick(cardData)
                }
                
                android.util.Log.d(TAG, "显示浮动按钮 - app已安装: $packageName，显示app图标和打开按钮")
            } else {
                // app未安装：显示"下载"和"打开"两个按钮
                holder.floatingButtonsContainer.visibility = View.VISIBLE
                holder.appIconView.visibility = View.GONE
                holder.appIconFloating.visibility = View.GONE
                holder.downloadButton.visibility = View.VISIBLE
                holder.openButton.visibility = View.VISIBLE
                
                // 设置下载按钮点击事件：打开应用商店
                holder.downloadButton.setOnClickListener {
                    this.cardViewModeManager?.openAppStore(packageName!!)
                }
                
                // 设置打开按钮点击事件：在卡片内打开
                holder.openButton.setOnClickListener {
                    onCardClick(cardData)
                }
                
                android.util.Log.d(TAG, "显示浮动按钮 - app未安装: $packageName，显示下载和打开按钮")
            }
        } else {
            // 无对应app：只显示"打开"按钮
            holder.appIconView.visibility = View.GONE
            holder.appIconFloating.visibility = View.GONE
            holder.floatingButtonsContainer.visibility = View.VISIBLE
            holder.downloadButton.visibility = View.GONE
            holder.openButton.visibility = View.VISIBLE
            
            // 设置打开按钮点击事件：在卡片内打开
            holder.openButton.setOnClickListener {
                onCardClick(cardData)
            }
            
            android.util.Log.d(TAG, "显示浮动按钮 - 无对应app，只显示打开按钮")
        }
        
        // 设置WebView
        val webView = cardData.webView
        val parent = webView.parent as? ViewGroup
        parent?.removeView(webView)
        
        // 先移除WebView（如果已存在），但保留浮动按钮容器
        val existingWebView = holder.webViewContainer.findViewWithTag<View>("webview")
        existingWebView?.let {
            holder.webViewContainer.removeView(it)
        }
        
        // 如果容器中没有WebView，添加它
        if (holder.webViewContainer.indexOfChild(webView) == -1) {
            webView.tag = "webview"
            holder.webViewContainer.addView(webView, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        }
        
        // 确保浮动按钮在WebView之上（通过bringToFront）
        holder.floatingButtonsContainer.bringToFront()
        holder.webViewContainer.invalidate()
        
        // 再次确保按钮容器可见（防止被其他操作隐藏）
        if (holder.floatingButtonsContainer.visibility != View.VISIBLE) {
            holder.floatingButtonsContainer.visibility = View.VISIBLE
            android.util.Log.d(TAG, "强制显示浮动按钮容器")
        }
        
        // 确保按钮可点击和可聚焦
        holder.downloadButton.isClickable = true
        holder.downloadButton.isFocusable = true
        holder.openButton.isClickable = true
        holder.openButton.isFocusable = true
        holder.floatingButtonsContainer.isClickable = true
        
        // 设置WebView不拦截按钮区域的触摸事件
        webView.setOnTouchListener { view, event ->
            val buttonsContainer = holder.floatingButtonsContainer
            if (buttonsContainer.visibility == View.VISIBLE) {
                val location = IntArray(2)
                buttonsContainer.getLocationOnScreen(location)
                val x = event.rawX.toInt()
                val y = event.rawY.toInt()
                
                // 检查触摸事件是否在按钮区域内
                if (x >= location[0] && x <= location[0] + buttonsContainer.width &&
                    y >= location[1] && y <= location[1] + buttonsContainer.height) {
                    // 触摸在按钮区域，不拦截，让按钮处理
                    return@setOnTouchListener false
                }
            }
            // 其他区域正常处理
            false
        }
        
        // 设置标题栏点击事件：进入卡片内搜索（全屏模式）
        holder.titleView.setOnClickListener {
            onCardClick(cardData)
        }
        
        // 设置卡片点击事件（点击卡片其他区域也进入全屏）
        holder.itemView.setOnClickListener {
            onCardClick(cardData)
        }
        
        holder.itemView.setOnLongClickListener {
            onCardLongClick(cardData)
            true
        }
        
        // 注意：卡片标题栏的滑动返回功能在全屏模式下实现（FullScreenCardViewer中）
        // 这里不需要添加滑动功能，因为卡片列表中的标题栏点击是打开全屏
        
        // 根据展开状态调整高度
        val layoutParams = holder.itemView.layoutParams as RecyclerView.LayoutParams
        if (cardData.isExpanded) {
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
        } else {
            layoutParams.height = dpToPx(holder.itemView.context, 300) // 默认高度300dp
        }
        holder.itemView.layoutParams = layoutParams
    }

    override fun getItemCount(): Int = cards.size

    private fun dpToPx(context: android.content.Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
    
}

