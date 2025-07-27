package com.example.aifloatingball.views

import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.aifloatingball.R
import com.example.aifloatingball.webview.MultiPageWebViewManager

/**
 * WebView标签栏组件
 * 显示所有打开的页面标签，支持切换和关闭页面
 */
class WebViewTabBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    private val tabContainer: LinearLayout
    private val tabs = mutableListOf<TabView>()
    private var currentTabIndex = 0
    
    // 标签点击监听器
    private var onTabClickListener: OnTabClickListener? = null
    
    /**
     * 标签点击监听器接口
     */
    interface OnTabClickListener {
        fun onTabSelected(position: Int)
        fun onTabClosed(position: Int)
        fun onNewTabRequested()
    }
    
    /**
     * 单个标签视图
     */
    private inner class TabView(
        val container: View,
        val favicon: ImageView,
        val title: TextView,
        val closeButton: ImageView,
        var page: MultiPageWebViewManager.WebViewPage
    )

    init {
        // 创建标签容器
        tabContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.MATCH_PARENT
            )
        }
        addView(tabContainer)
        
        // 设置滚动视图属性
        isHorizontalScrollBarEnabled = false
        isFillViewport = false
    }

    /**
     * 添加标签
     */
    fun addTab(page: MultiPageWebViewManager.WebViewPage, position: Int) {
        try {
            Log.d("WebViewTabBar", "开始添加标签: position=$position, currentTabsSize=${tabs.size}, pageId=${page.id}")

            val tabView = createTabView(page, position)

            // 确保位置在有效范围内
            val safePosition = position.coerceIn(0, tabs.size)

            if (safePosition != position) {
                Log.w("WebViewTabBar", "位置调整: 原始=$position, 安全=$safePosition, 当前大小=${tabs.size}")
            }

            tabs.add(safePosition, tabView)
            tabContainer.addView(tabView.container, safePosition)

            // 更新所有标签的位置索引
            updateTabPositions()

            // 滚动到新标签
            post {
                val tabWidth = tabView.container.width
                val scrollX = safePosition * tabWidth - width / 2 + tabWidth / 2
                smoothScrollTo(scrollX.coerceAtLeast(0), 0)
            }

            Log.d("WebViewTabBar", "成功添加标签: safePosition=$safePosition, 新的tabsSize=${tabs.size}")

        } catch (e: Exception) {
            Log.e("WebViewTabBar", "添加标签失败: position=$position, tabsSize=${tabs.size}", e)
            // 如果添加失败，尝试添加到末尾
            try {
                val tabView = createTabView(page, tabs.size)
                tabs.add(tabView)
                tabContainer.addView(tabView.container)
                updateTabPositions()
                Log.d("WebViewTabBar", "回退添加标签成功")
            } catch (fallbackException: Exception) {
                Log.e("WebViewTabBar", "回退添加标签也失败", fallbackException)
            }
        }
    }

    /**
     * 移除标签
     */
    fun removeTab(position: Int) {
        if (position >= 0 && position < tabs.size) {
            val tabView = tabs[position]
            tabContainer.removeView(tabView.container)
            tabs.removeAt(position)
            
            // 更新当前标签索引
            if (currentTabIndex >= position && currentTabIndex > 0) {
                currentTabIndex--
            }
            if (currentTabIndex >= tabs.size) {
                currentTabIndex = tabs.size - 1
            }
            
            // 更新所有标签的位置索引和选中状态
            updateTabPositions()
            updateTabSelection()
        }
    }

    /**
     * 更新标签信息
     */
    fun updateTab(position: Int, page: MultiPageWebViewManager.WebViewPage) {
        if (position >= 0 && position < tabs.size) {
            val tabView = tabs[position]
            tabView.page = page
            
            // 更新标题
            tabView.title.text = page.title.take(15).let { 
                if (page.title.length > 15) "$it..." else it 
            }
            
            // 更新图标
            if (page.favicon != null) {
                tabView.favicon.setImageBitmap(page.favicon)
            } else {
                tabView.favicon.setImageResource(R.drawable.ic_web)
            }
        }
    }

    /**
     * 设置当前选中的标签
     */
    fun setCurrentTab(position: Int) {
        if (position >= 0 && position < tabs.size) {
            currentTabIndex = position
            updateTabSelection()
            
            // 滚动到当前标签
            post {
                val tabView = tabs[position]
                val tabWidth = tabView.container.width
                val scrollX = position * tabWidth - width / 2 + tabWidth / 2
                smoothScrollTo(scrollX.coerceAtLeast(0), 0)
            }
        }
    }

    /**
     * 创建标签视图
     */
    private fun createTabView(page: MultiPageWebViewManager.WebViewPage, position: Int): TabView {
        val inflater = LayoutInflater.from(context)
        val tabView = inflater.inflate(R.layout.webview_tab_item, tabContainer, false)
        
        val favicon = tabView.findViewById<ImageView>(R.id.tab_favicon)
        val title = tabView.findViewById<TextView>(R.id.tab_title)
        val closeButton = tabView.findViewById<ImageView>(R.id.tab_close)
        
        // 设置标题
        title.text = page.title.take(15).let { 
            if (page.title.length > 15) "$it..." else it 
        }
        
        // 设置图标
        if (page.favicon != null) {
            favicon.setImageBitmap(page.favicon)
        } else {
            favicon.setImageResource(R.drawable.ic_web)
        }
        
        // 设置点击监听器
        tabView.setOnClickListener {
            onTabClickListener?.onTabSelected(position)
        }
        
        closeButton.setOnClickListener {
            onTabClickListener?.onTabClosed(position)
        }
        
        return TabView(tabView, favicon, title, closeButton, page)
    }

    /**
     * 更新标签位置索引
     */
    private fun updateTabPositions() {
        tabs.forEachIndexed { index, tabView ->
            tabView.container.setOnClickListener {
                onTabClickListener?.onTabSelected(index)
            }
            tabView.closeButton.setOnClickListener {
                onTabClickListener?.onTabClosed(index)
            }
        }
    }

    /**
     * 更新标签选中状态
     */
    private fun updateTabSelection() {
        tabs.forEachIndexed { index, tabView ->
            val isSelected = index == currentTabIndex
            tabView.container.isSelected = isSelected
            
            // 更新背景色
            val backgroundColor = if (isSelected) {
                ContextCompat.getColor(context, R.color.webview_tab_selected)
            } else {
                ContextCompat.getColor(context, R.color.webview_tab_normal)
            }
            tabView.container.setBackgroundColor(backgroundColor)
        }
    }

    /**
     * 设置标签点击监听器
     */
    fun setOnTabClickListener(listener: OnTabClickListener) {
        this.onTabClickListener = listener
    }

    /**
     * 获取标签数量
     */
    fun getTabCount(): Int = tabs.size

    /**
     * 清空所有标签
     */
    fun clearTabs() {
        tabs.clear()
        tabContainer.removeAllViews()
        currentTabIndex = 0
    }
}
