package com.example.aifloatingball.reader

import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RelativeLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.example.aifloatingball.R

/**
 * 小说阅读模式UI
 */
class NovelReaderUI(private val context: Context, private val container: ViewGroup) : NovelReaderManager.ReaderModeListener {

    private var readerView: View? = null
    private var titleView: TextView? = null
    private var contentView: TextView? = null
    private var scrollView: ScrollView? = null
    private var topBar: RelativeLayout? = null
    private var bottomBar: LinearLayout? = null
    private var loadingView: ProgressBar? = null
    
    private var isMenuVisible = false
    private val manager = NovelReaderManager.getInstance(context)

    init {
        manager.setListener(this)
    }

    /**
     * 显示阅读器
     */
    fun show() {
        if (readerView == null) {
            initView()
        }
        readerView?.visibility = View.VISIBLE
        readerView?.bringToFront()
    }

    /**
     * 隐藏阅读器
     */
    fun hide() {
        readerView?.visibility = View.GONE
    }

    private var contentContainer: LinearLayout? = null

    private fun initView() {
        readerView = LayoutInflater.from(context).inflate(R.layout.layout_novel_reader, container, false)
        container.addView(readerView)

        scrollView = readerView?.findViewById(R.id.reader_scroll_view)
        // 获取ScrollView内部的LinearLayout
        contentContainer = scrollView?.getChildAt(0) as? LinearLayout
        
        // 初始的标题和内容View
        titleView = readerView?.findViewById(R.id.reader_title)
        contentView = readerView?.findViewById(R.id.reader_content)
        
        topBar = readerView?.findViewById(R.id.reader_top_bar)
        bottomBar = readerView?.findViewById(R.id.reader_bottom_bar)
        loadingView = readerView?.findViewById(R.id.reader_loading)

        // 点击中间区域切换菜单显示
        // 需要给整个容器设置点击事件，或者给新添加的View设置
        contentContainer?.setOnClickListener {
            toggleMenu()
        }
        contentView?.setOnClickListener { toggleMenu() }
        
        // 退出按钮
        readerView?.findViewById<ImageButton>(R.id.btn_exit_reader)?.setOnClickListener {
            manager.exitReaderMode()
        }
        
        // 上一章/下一章按钮
        readerView?.findViewById<Button>(R.id.btn_prev_chapter)?.setOnClickListener {
            Toast.makeText(context, "上一章功能开发中", Toast.LENGTH_SHORT).show()
        }
        
        readerView?.findViewById<Button>(R.id.btn_next_chapter)?.setOnClickListener {
            manager.loadNextChapter()
        }
        
        // 夜间模式切换
        readerView?.findViewById<TextView>(R.id.btn_night_mode)?.setOnClickListener {
            toggleNightMode()
        }
        
        // 监听滚动，实现自动加载下一章
        scrollView?.viewTreeObserver?.addOnScrollChangedListener {
            val view = scrollView ?: return@addOnScrollChangedListener
            // 检查是否滚动到底部
            val diff = (view.getChildAt(0).bottom - (view.height + view.scrollY))
            
            // 如果距离底部小于 500px，且没有在加载，则加载下一章
            if (diff < 500) {
                manager.loadNextChapter()
            }
        }
    }

    private fun toggleMenu() {
        isMenuVisible = !isMenuVisible
        val visibility = if (isMenuVisible) View.VISIBLE else View.GONE
        topBar?.visibility = visibility
        bottomBar?.visibility = visibility
    }
    
    private var isNightMode = false
    private fun toggleNightMode() {
        isNightMode = !isNightMode
        val bgColor = if (isNightMode) Color.parseColor("#1a1a1a") else Color.parseColor("#F5F5DC")
        val textColor = if (isNightMode) Color.parseColor("#a0a0a0") else Color.parseColor("#333333")
        
        readerView?.setBackgroundColor(bgColor)
        titleView?.setTextColor(textColor)
        contentView?.setTextColor(textColor)
        
        readerView?.findViewById<TextView>(R.id.btn_night_mode)?.text = if (isNightMode) "日间" else "夜间"
    }

    override fun onReaderModeStateChanged(isActive: Boolean) {
        if (isActive) {
            show()
        } else {
            hide()
        }
    }

    override fun onChapterLoaded(title: String, content: String, hasNext: Boolean, hasPrev: Boolean, isAppend: Boolean) {
        // 在主线程更新UI
        readerView?.post {
            loadingView?.visibility = View.GONE
            
            if (!isAppend) {
                // 如果不是追加，重置内容
                // 清除除了初始View之外的所有View? 
                // 或者简单点，重置初始View的文本，移除后面添加的View
                
                // 简单实现：重置初始View
                titleView?.text = title
                contentView?.text = content
                
                // 移除contentContainer中index > 2的View (假设0是title, 1是content, 2是buttons, 3是placeholder)
                // 实际上我们需要更健壮的方式。
                // 让我们重新查找初始View，并移除之后添加的章节View
                
                // 更好的方式：
                // 初始状态：
                // titleView (id: reader_title)
                // contentView (id: reader_content)
                // buttonsLayout
                // placeholder
                
                // 我们保留这些，移除动态添加的View
                // 动态添加的View应该有特殊的Tag或ID
                
                val childCount = contentContainer?.childCount ?: 0
                if (childCount > 4) {
                    contentContainer?.removeViews(4, childCount - 4)
                }
                
                scrollView?.scrollTo(0, 0)
            } else {
                // 追加新章节
                addChapterView(title, content)
                Toast.makeText(context, "已加载下一章: $title", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun addChapterView(title: String, content: String) {
        val context = readerView?.context ?: return
        
        // 分割线
        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2 // height
            ).apply {
                setMargins(0, 48, 0, 48)
            }
            setBackgroundColor(if (isNightMode) Color.DKGRAY else Color.LTGRAY)
        }
        
        // 标题
        val newTitleView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 32 // px approx 16dp
            }
            textSize = 24f // sp
            // setTypeface(null, Typeface.BOLD)
            setTextColor(if (isNightMode) Color.parseColor("#a0a0a0") else Color.parseColor("#333333"))
            text = title
        }
        
        // 内容
        val newContentView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            textSize = 18f // sp
            setLineSpacing(0f, 1.5f)
            setTextColor(if (isNightMode) Color.parseColor("#a0a0a0") else Color.parseColor("#333333"))
            text = content
            setOnClickListener { toggleMenu() }
        }
        
        // 添加到容器，在buttonsLayout之前
        // buttonsLayout应该是倒数第二个 (倒数第一个是placeholder)
        val count = contentContainer?.childCount ?: 0
        val insertIndex = if (count >= 2) count - 2 else count
        
        contentContainer?.addView(divider, insertIndex)
        contentContainer?.addView(newTitleView, insertIndex + 1)
        contentContainer?.addView(newContentView, insertIndex + 2)
    }

    override fun onChapterLoadFailed(error: String) {
        readerView?.post {
            loadingView?.visibility = View.GONE
            Toast.makeText(context, error, Toast.LENGTH_SHORT).show()
        }
    }
}
