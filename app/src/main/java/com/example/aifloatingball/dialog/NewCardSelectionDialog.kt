package com.example.aifloatingball.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.aifloatingball.R
import com.example.aifloatingball.adapter.HistoryEntryAdapter
import com.example.aifloatingball.adapter.BookmarkEntryAdapter
import com.example.aifloatingball.model.BookmarkEntry
import com.example.aifloatingball.model.HistoryEntry
import com.google.android.material.button.MaterialButton
import com.google.android.material.tabs.TabLayout

/**
 * 新建卡片选择弹窗
 */
class NewCardSelectionDialog(
    context: Context,
    private val onHistoryItemSelected: (HistoryEntry) -> Unit = {},
    private val onBookmarkItemSelected: (BookmarkEntry) -> Unit = {},
    private val onCreateBlankCard: () -> Unit = {},
    private val onDismiss: () -> Unit = {}
) : Dialog(context) {

    private lateinit var tabLayout: TabLayout
    private lateinit var contentContainer: LinearLayout
    private lateinit var btnClose: ImageButton
    private lateinit var btnCancel: MaterialButton
    private lateinit var btnCreateBlank: MaterialButton
    private lateinit var etUrlInput: com.google.android.material.textfield.TextInputEditText
    private lateinit var btnClearInput: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            android.util.Log.d("NewCardSelectionDialog", "开始创建Dialog")
            
            val view = LayoutInflater.from(context).inflate(R.layout.dialog_new_card_selection, null)
            setContentView(view)
            
            android.util.Log.d("NewCardSelectionDialog", "布局加载成功")
            
            initViews(view)
            android.util.Log.d("NewCardSelectionDialog", "视图初始化成功")
            
            setupViewPager()
            android.util.Log.d("NewCardSelectionDialog", "ViewPager设置成功")
            
            setupClickListeners()
            android.util.Log.d("NewCardSelectionDialog", "点击监听器设置成功")
            
            // 设置Dialog属性
            window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            
            android.util.Log.d("NewCardSelectionDialog", "Dialog创建完成")
            
        } catch (e: Exception) {
            android.util.Log.e("NewCardSelectionDialog", "Dialog创建失败", e)
            // 不抛出异常，而是显示一个简单的错误对话框
            showErrorDialog(e.message ?: "未知错误")
        }
    }
    
    /**
     * 显示错误对话框
     */
    private fun showErrorDialog(message: String) {
        try {
            val errorDialog = android.app.AlertDialog.Builder(context)
                .setTitle("错误")
                .setMessage("新建卡片弹窗加载失败: $message")
                .setPositiveButton("确定") { _, _ -> dismiss() }
                .create()
            errorDialog.show()
        } catch (e: Exception) {
            android.util.Log.e("NewCardSelectionDialog", "显示错误对话框失败", e)
        }
    }

    private fun initViews(view: View) {
        try {
            android.util.Log.d("NewCardSelectionDialog", "开始初始化视图")
            
            tabLayout = view.findViewById(R.id.tab_layout)
            if (tabLayout == null) {
                android.util.Log.e("NewCardSelectionDialog", "tabLayout未找到")
                throw IllegalStateException("tabLayout未找到")
            }
            
            contentContainer = view.findViewById(R.id.view_pager)
            if (contentContainer == null) {
                android.util.Log.e("NewCardSelectionDialog", "contentContainer未找到")
                throw IllegalStateException("contentContainer未找到")
            }
            
            btnClose = view.findViewById(R.id.btn_close_dialog)
            if (btnClose == null) {
                android.util.Log.e("NewCardSelectionDialog", "btnClose未找到")
                throw IllegalStateException("btnClose未找到")
            }
            
            btnCancel = view.findViewById(R.id.btn_cancel)
            if (btnCancel == null) {
                android.util.Log.e("NewCardSelectionDialog", "btnCancel未找到")
                throw IllegalStateException("btnCancel未找到")
            }
            
            btnCreateBlank = view.findViewById(R.id.btn_create_blank)
            if (btnCreateBlank == null) {
                android.util.Log.e("NewCardSelectionDialog", "btnCreateBlank未找到")
                throw IllegalStateException("btnCreateBlank未找到")
            }
            
            etUrlInput = view.findViewById(R.id.et_url_input)
            if (etUrlInput == null) {
                android.util.Log.e("NewCardSelectionDialog", "etUrlInput未找到")
                throw IllegalStateException("etUrlInput未找到")
            }
            
            btnClearInput = view.findViewById(R.id.btn_clear_input)
            if (btnClearInput == null) {
                android.util.Log.e("NewCardSelectionDialog", "btnClearInput未找到")
                throw IllegalStateException("btnClearInput未找到")
            }
            
            android.util.Log.d("NewCardSelectionDialog", "视图初始化成功")
        } catch (e: Exception) {
            android.util.Log.e("NewCardSelectionDialog", "视图初始化失败", e)
            throw e
        }
    }

    private fun setupViewPager() {
        // 设置选项卡点击事件
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    0 -> showHistoryPage()
                    1 -> showBookmarksPage()
                }
            }
            
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
        
        // 默认显示历史页面
        showHistoryPage()
    }
    
    
    private fun showHistoryPage() {
        try {
            android.util.Log.d("NewCardSelectionDialog", "显示历史页面")
            
            // 清空容器
            contentContainer.removeAllViews()
            
            // 创建历史记录列表
            val historyView = try {
                LayoutInflater.from(context).inflate(R.layout.fragment_history_page, contentContainer, false)
            } catch (e: Exception) {
                android.util.Log.e("NewCardSelectionDialog", "加载历史页面布局失败", e)
                // 创建简单的错误布局
                val errorView = createSimpleHistoryLayout()
                contentContainer.addView(errorView)
                return
            }
            
            val recyclerView = historyView.findViewById<RecyclerView>(R.id.rv_history)
            val emptyLayout = historyView.findViewById<LinearLayout>(R.id.layout_empty_history)
        
        // 模拟历史数据
        val mockHistory = listOf(
            HistoryEntry(
                id = "1",
                title = "GitHub - 代码托管平台",
                url = "https://github.com",
                visitTime = java.util.Date(System.currentTimeMillis() - 2 * 60 * 60 * 1000)
            ),
            HistoryEntry(
                id = "2",
                title = "Stack Overflow - 程序员问答社区",
                url = "https://stackoverflow.com",
                visitTime = java.util.Date(System.currentTimeMillis() - 4 * 60 * 60 * 1000)
            ),
            HistoryEntry(
                id = "3",
                title = "Android 开发者官网",
                url = "https://developer.android.com",
                visitTime = java.util.Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000)
            ),
            HistoryEntry(
                id = "4",
                title = "Material Design 指南",
                url = "https://material.io",
                visitTime = java.util.Date(System.currentTimeMillis() - 3 * 24 * 60 * 60 * 1000)
            )
        )
        
        val adapter = HistoryEntryAdapter(
            entries = mockHistory,
            onItemClick = { entry ->
                onHistoryItemSelected(entry)
                dismiss()
            },
            onMoreClick = { entry ->
                showHistoryMoreMenu(entry)
            }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        
        // 更新空状态
        if (adapter.itemCount == 0) {
            recyclerView.visibility = View.GONE
            emptyLayout.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyLayout.visibility = View.GONE
        }
        
        contentContainer.addView(historyView)
        
        } catch (e: Exception) {
            android.util.Log.e("NewCardSelectionDialog", "显示历史页面失败", e)
            // 显示错误信息
            val errorView = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, contentContainer, false)
            val textView = errorView.findViewById<TextView>(android.R.id.text1)
            textView.text = "加载历史记录失败: ${e.message}"
            contentContainer.addView(errorView)
        }
    }
    
    private fun showBookmarksPage() {
        try {
            android.util.Log.d("NewCardSelectionDialog", "显示收藏页面")
            
            // 清空容器
            contentContainer.removeAllViews()
            
            // 创建收藏列表
            val bookmarksView = try {
                LayoutInflater.from(context).inflate(R.layout.fragment_bookmarks_page, contentContainer, false)
            } catch (e: Exception) {
                android.util.Log.e("NewCardSelectionDialog", "加载收藏页面布局失败", e)
                // 创建简单的错误布局
                val errorView = createSimpleBookmarksLayout()
                contentContainer.addView(errorView)
                return
            }
            
            val recyclerView = bookmarksView.findViewById<RecyclerView>(R.id.rv_bookmarks)
            val emptyLayout = bookmarksView.findViewById<LinearLayout>(R.id.layout_empty_bookmarks)
        
        // 模拟收藏数据
        val mockBookmarks = listOf(
            BookmarkEntry(
                id = "1",
                title = "Android 开发最佳实践",
                url = "https://developer.android.com/guide",
                folder = "开发资源",
                createTime = java.util.Date(System.currentTimeMillis() - 2 * 24 * 60 * 60 * 1000)
            ),
            BookmarkEntry(
                id = "2",
                title = "Kotlin 官方文档",
                url = "https://kotlinlang.org/docs",
                folder = "开发资源",
                createTime = java.util.Date(System.currentTimeMillis() - 5 * 24 * 60 * 60 * 1000)
            ),
            BookmarkEntry(
                id = "3",
                title = "Material Design 组件库",
                url = "https://material.io/components",
                folder = "UI设计",
                createTime = java.util.Date(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000)
            ),
            BookmarkEntry(
                id = "4",
                title = "GitHub 趋势项目",
                url = "https://github.com/trending",
                folder = "技术资讯",
                createTime = java.util.Date(System.currentTimeMillis() - 10 * 24 * 60 * 60 * 1000)
            )
        )
        
        val adapter = BookmarkEntryAdapter(
            entries = mockBookmarks,
            onItemClick = { entry ->
                onBookmarkItemSelected(entry)
                dismiss()
            },
            onMoreClick = { entry ->
                showBookmarkMoreMenu(entry)
            }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        
        // 更新空状态
        if (adapter.itemCount == 0) {
            recyclerView.visibility = View.GONE
            emptyLayout.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyLayout.visibility = View.GONE
        }
        
        contentContainer.addView(bookmarksView)
        
        } catch (e: Exception) {
            android.util.Log.e("NewCardSelectionDialog", "显示收藏页面失败", e)
            // 显示错误信息
            val errorView = LayoutInflater.from(context).inflate(android.R.layout.simple_list_item_1, contentContainer, false)
            val textView = errorView.findViewById<TextView>(android.R.id.text1)
            textView.text = "加载收藏失败: ${e.message}"
            contentContainer.addView(errorView)
        }
    }

    private fun setupClickListeners() {
        btnClose.setOnClickListener {
            dismiss()
        }
        
        btnCancel.setOnClickListener {
            dismiss()
        }
        
        btnCreateBlank.setOnClickListener {
            onCreateBlankCard()
            dismiss()
        }

        btnClearInput.setOnClickListener {
            etUrlInput.setText("")
            etUrlInput.clearFocus()
        }

        // 输入框回车事件
        etUrlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                val inputText = etUrlInput.text.toString().trim()
                if (inputText.isNotEmpty()) {
                    // 创建新卡片
                    createCardFromInput(inputText)
                }
                true
            } else {
                false
            }
        }
    }

    /**
     * 从输入框创建卡片
     */
    private fun createCardFromInput(input: String) {
        try {
            // 判断输入的是URL还是搜索内容
            val url = if (input.startsWith("http://") || input.startsWith("https://")) {
                input
            } else if (input.contains(".") && !input.contains(" ")) {
                // 可能是域名，添加https://
                "https://$input"
            } else {
                // 搜索内容，使用Google搜索
                "https://www.google.com/search?q=${java.net.URLEncoder.encode(input, "UTF-8")}"
            }

            // 创建历史记录条目
            val historyEntry = HistoryEntry(
                id = System.currentTimeMillis().toString(),
                title = if (input.startsWith("http")) input else "搜索: $input",
                url = url,
                visitTime = java.util.Date()
            )

            // 调用历史记录选择回调
            onHistoryItemSelected(historyEntry)
            dismiss()

        } catch (e: Exception) {
            android.util.Log.e("NewCardSelectionDialog", "从输入创建卡片失败", e)
            android.widget.Toast.makeText(context, "创建卡片失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun showHistoryMoreMenu(@Suppress("UNUSED_PARAMETER") entry: HistoryEntry) {
        // TODO: 实现历史记录更多操作菜单
        // 可以包括：删除、添加到收藏、复制链接等
    }

    private fun showBookmarkMoreMenu(@Suppress("UNUSED_PARAMETER") entry: BookmarkEntry) {
        // TODO: 实现收藏更多操作菜单
        // 可以包括：删除、编辑、移动文件夹等
    }

    /**
     * 创建简单的历史布局（当XML布局加载失败时使用）
     */
    private fun createSimpleHistoryLayout(): View {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(context).apply {
            text = "历史记录"
            textSize = 18f
            setTextColor(android.graphics.Color.BLACK)
            setPadding(0, 0, 0, 16)
        }
        layout.addView(title)

        val message = TextView(context).apply {
            text = "历史记录功能暂时不可用，请稍后再试"
            textSize = 14f
            setTextColor(android.graphics.Color.GRAY)
        }
        layout.addView(message)

        return layout
    }

    /**
     * 创建简单的收藏布局（当XML布局加载失败时使用）
     */
    private fun createSimpleBookmarksLayout(): View {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }

        val title = TextView(context).apply {
            text = "收藏页面"
            textSize = 18f
            setTextColor(android.graphics.Color.BLACK)
            setPadding(0, 0, 0, 16)
        }
        layout.addView(title)

        val message = TextView(context).apply {
            text = "收藏页面功能暂时不可用，请稍后再试"
            textSize = 14f
            setTextColor(android.graphics.Color.GRAY)
        }
        layout.addView(message)

        return layout
    }

    override fun dismiss() {
        super.dismiss()
        onDismiss()
    }

    companion object {
        fun show(
            context: Context,
            @Suppress("UNUSED_PARAMETER") fragmentManager: FragmentManager,
            onHistoryItemSelected: (HistoryEntry) -> Unit = {},
            onBookmarkItemSelected: (BookmarkEntry) -> Unit = {},
            onCreateBlankCard: () -> Unit = {},
            onDismiss: () -> Unit = {}
        ) {
            val dialog = NewCardSelectionDialog(
                context,
                onHistoryItemSelected,
                onBookmarkItemSelected,
                onCreateBlankCard,
                onDismiss
            )
            dialog.show()
        }
    }
}
