package com.example.aifloatingball.views

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.aifloatingball.R
import com.google.android.material.card.MaterialCardView

/**
 * Material Design风格的搜索引擎选择器
 * 修复点击问题，提供更好的视觉反馈
 */
class MaterialSearchEngineSelector @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    // 搜索引擎数据
    data class SearchEngine(
        val name: String,
        val displayName: String,
        val url: String,
        val iconRes: Int
    ) {
        fun getSearchUrl(query: String): String {
            return when (name) {
                "Google" -> "https://www.google.com/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
                "Bing" -> "https://www.bing.com/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
                "百度" -> "https://www.baidu.com/s?wd=${java.net.URLEncoder.encode(query, "UTF-8")}"
                "必应" -> "https://cn.bing.com/search?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
                "知乎" -> "https://www.zhihu.com/search?type=content&q=${java.net.URLEncoder.encode(query, "UTF-8")}"
                "微博" -> "https://s.weibo.com/weibo?q=${java.net.URLEncoder.encode(query, "UTF-8")}"
                "DeepSeek" -> "https://chat.deepseek.com"
                "ChatGPT" -> "https://chat.openai.com"
                "文心一言" -> "https://yiyan.baidu.com"
                else -> url.replace("{query}", java.net.URLEncoder.encode(query, "UTF-8"))
            }
        }
    }

    // 默认搜索引擎列表 - 已移除所有搜索引擎图标
    private val searchEngines = emptyList<SearchEngine>()

    // 点击监听器
    private var onEngineClickListener: OnEngineClickListener? = null

    /**
     * 搜索引擎点击监听器
     */
    interface OnEngineClickListener {
        fun onEngineClick(engine: SearchEngine)
    }

    init {
        setupView()
        createEngineButtons()
    }

    /**
     * 设置视图
     */
    private fun setupView() {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        
        // 设置内边距
        val padding = (12 * resources.displayMetrics.density).toInt()
        setPadding(padding, padding, padding, padding)
        
        // 设置背景
        background = GradientDrawable().apply {
            setColor(ContextCompat.getColor(context, R.color.material_grey_100))
            cornerRadius = (12 * resources.displayMetrics.density)
        }
    }

    /**
     * 创建搜索引擎按钮
     */
    private fun createEngineButtons() {
        searchEngines.forEach { engine ->
            val engineButton = createEngineButton(engine)
            addView(engineButton)
        }
    }

    /**
     * 创建单个搜索引擎按钮
     */
    private fun createEngineButton(engine: SearchEngine): MaterialCardView {
        return MaterialCardView(context).apply {
            // 设置卡片样式
            cardElevation = (2 * resources.displayMetrics.density)
            radius = (8 * resources.displayMetrics.density)
            setCardBackgroundColor(ContextCompat.getColor(context, android.R.color.white))
            
            // 设置大小和边距
            val size = (48 * resources.displayMetrics.density).toInt()
            val margin = (4 * resources.displayMetrics.density).toInt()
            layoutParams = LayoutParams(size, size).apply {
                setMargins(margin, 0, margin, 0)
            }
            
            // 设置点击效果
            isClickable = true
            isFocusable = true
            foreground = ContextCompat.getDrawable(context, android.R.drawable.list_selector_background)
            
            // 创建内容布局
            val contentLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                
                // 图标
                val icon = ImageView(context).apply {
                    setImageResource(engine.iconRes)
                    setColorFilter(ContextCompat.getColor(context, R.color.material_blue_500))
                    val iconSize = (20 * resources.displayMetrics.density).toInt()
                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                }
                addView(icon)
                
                // 文字
                val text = TextView(context).apply {
                    text = engine.displayName
                    textSize = 10f
                    setTextColor(ContextCompat.getColor(context, R.color.material_grey_800))
                    gravity = Gravity.CENTER
                    maxLines = 1
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = (2 * resources.displayMetrics.density).toInt()
                    }
                }
                addView(text)
            }
            
            addView(contentLayout)
            
            // 设置点击监听器 - 关键修复点
            setOnClickListener { view ->
                // 添加点击动画
                view.animate()
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(100)
                    .withEndAction {
                        view.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(100)
                            .start()
                    }
                    .start()
                
                // 触发点击事件
                onEngineClickListener?.onEngineClick(engine)
            }
            
            // 添加长按提示
            setOnLongClickListener {
                // 可以添加长按显示引擎信息的功能
                true
            }
        }
    }

    /**
     * 设置搜索引擎点击监听器
     */
    fun setOnEngineClickListener(listener: OnEngineClickListener) {
        this.onEngineClickListener = listener
    }

    /**
     * 获取搜索引擎列表
     */
    fun getSearchEngines(): List<SearchEngine> {
        return searchEngines.toList()
    }

    /**
     * 根据名称获取搜索引擎
     */
    fun getSearchEngine(name: String): SearchEngine? {
        return searchEngines.find { it.name == name }
    }

    /**
     * 高亮显示选中的搜索引擎
     */
    fun highlightEngine(engineName: String) {
        for (i in 0 until childCount) {
            val child = getChildAt(i) as? MaterialCardView
            val engine = searchEngines.getOrNull(i)
            
            if (engine?.name == engineName) {
                // 高亮选中的引擎
                child?.setCardBackgroundColor(ContextCompat.getColor(context, R.color.material_blue_100))
                child?.cardElevation = (4 * resources.displayMetrics.density)
            } else {
                // 恢复其他引擎的默认样式
                child?.setCardBackgroundColor(ContextCompat.getColor(context, android.R.color.white))
                child?.cardElevation = (2 * resources.displayMetrics.density)
            }
        }
    }

    /**
     * 清除所有高亮
     */
    fun clearHighlight() {
        for (i in 0 until childCount) {
            val child = getChildAt(i) as? MaterialCardView
            child?.setCardBackgroundColor(ContextCompat.getColor(context, android.R.color.white))
            child?.cardElevation = (2 * resources.displayMetrics.density)
        }
    }
}
