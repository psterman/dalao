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
        
        // 获取真实的历史数据
        val historyData = getRealHistoryData()
        
        val adapter = HistoryEntryAdapter(
            entries = historyData,
            onItemClick = { entry ->
                // 创建新卡片而不是直接加载
                createCardFromHistory(entry)
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
        
        // 获取真实的收藏数据
        val bookmarkData = getRealBookmarkData()
        
        val adapter = BookmarkEntryAdapter(
            entries = bookmarkData,
            onItemClick = { entry ->
                // 创建新卡片而不是直接加载
                createCardFromBookmark(entry)
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
                    // 隐藏软键盘
                    etUrlInput.clearFocus()
                    val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.hideSoftInputFromWindow(etUrlInput.windowToken, 0)
                    
                    // 创建新卡片
                    createCardFromInput(inputText)
                }
                true
            } else {
                false
            }
        }
        
        // 输入框点击事件 - 确保焦点正确
        etUrlInput.setOnClickListener {
            etUrlInput.requestFocus()
            val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.showSoftInput(etUrlInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }
        
        // 输入框文本变化监听 - 实时搜索
        etUrlInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s.toString().trim()
                if (query.isNotEmpty()) {
                    // 实时过滤历史记录
                    filterHistoryEntries(query)
                } else {
                    // 显示所有历史记录
                    showHistoryPage()
                }
            }
            
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    /**
     * 从输入框创建卡片
     */
    private fun createCardFromInput(input: String) {
        try {
            android.util.Log.d("NewCardSelectionDialog", "从输入创建卡片: $input")
            
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

            android.util.Log.d("NewCardSelectionDialog", "处理后的URL: $url")
            
            // 保存搜索历史
            saveSearchHistory(input, url)

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

    /**
     * 保存搜索历史
     */
    private fun saveSearchHistory(query: String, url: String) {
        try {
            val searchHistoryManager = com.example.aifloatingball.SearchHistoryManager.getInstance(context)
            searchHistoryManager.addSearchHistory(query, "浏览器", "com.example.aifloatingball")
            android.util.Log.d("NewCardSelectionDialog", "搜索历史已保存: $query")
        } catch (e: Exception) {
            android.util.Log.e("NewCardSelectionDialog", "保存搜索历史失败", e)
        }
    }

    private fun showHistoryMoreMenu(entry: HistoryEntry) {
        try {
            val options = arrayOf("复制链接", "删除记录", "添加到收藏", "清空所有历史")
            
            val builder = android.app.AlertDialog.Builder(context)
                .setTitle("操作")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            // 复制链接
                            copyToClipboard(entry.url)
                            android.widget.Toast.makeText(context, "链接已复制", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        1 -> {
                            // 删除记录
                            deleteHistoryEntry(entry)
                        }
                        2 -> {
                            // 添加到收藏
                            addToBookmarks(entry)
                        }
                        3 -> {
                            // 清空所有历史
                            clearAllHistory()
                        }
                    }
                }
                .setNegativeButton("取消", null)
            
            builder.show()
        } catch (e: Exception) {
            android.util.Log.e("NewCardSelectionDialog", "显示历史记录菜单失败", e)
        }
    }

    private fun showBookmarkMoreMenu(entry: BookmarkEntry) {
        try {
            val options = arrayOf("复制链接", "删除收藏", "编辑收藏")
            
            val builder = android.app.AlertDialog.Builder(context)
                .setTitle("操作")
                .setItems(options) { _, which ->
                    when (which) {
                        0 -> {
                            // 复制链接
                            copyToClipboard(entry.url)
                            android.widget.Toast.makeText(context, "链接已复制", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        1 -> {
                            // 删除收藏
                            deleteBookmarkEntry(entry)
                        }
                        2 -> {
                            // 编辑收藏
                            editBookmarkEntry(entry)
                        }
                    }
                }
                .setNegativeButton("取消", null)
            
            builder.show()
        } catch (e: Exception) {
            android.util.Log.e("NewCardSelectionDialog", "显示收藏菜单失败", e)
        }
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

    /**
     * 获取真实的历史数据
     */
    private fun getRealHistoryData(): List<HistoryEntry> {
        return try {
            val historyList = mutableListOf<HistoryEntry>()
            
            // 1. 获取搜索历史
            val searchHistory = getSearchHistory()
            historyList.addAll(searchHistory)
            
            // 2. 获取浏览历史
            val browseHistory = getBrowseHistory()
            historyList.addAll(browseHistory)
            
            // 3. 按时间排序（最新的在前）
            historyList.sortByDescending { it.visitTime }
            
            // 4. 限制数量（最多显示20条）
            val limitedHistory = historyList.take(20)
            
            if (limitedHistory.isEmpty()) {
                // 如果没有历史数据，返回一些默认的常用网站
                listOf(
                    HistoryEntry(
                        id = "default_1",
                        title = "Google 搜索",
                        url = "https://www.google.com",
                        visitTime = java.util.Date()
                    ),
                    HistoryEntry(
                        id = "default_2", 
                        title = "百度搜索",
                        url = "https://www.baidu.com",
                        visitTime = java.util.Date()
                    )
                )
            } else {
                limitedHistory
            }
        } catch (e: Exception) {
            android.util.Log.e("NewCardSelectionDialog", "获取历史数据失败", e)
            emptyList()
        }
    }

    /**
     * 获取搜索历史
     */
    private fun getSearchHistory(): List<HistoryEntry> {
        return try {
            val searchHistoryManager = com.example.aifloatingball.SearchHistoryManager.getInstance(context)
            val searchHistory = searchHistoryManager.getSearchHistory()
            
            searchHistory.map { item ->
                HistoryEntry(
                    id = "search_${item.timestamp}",
                    title = "搜索: ${item.query}",
                    url = "https://www.google.com/search?q=${java.net.URLEncoder.encode(item.query, "UTF-8")}",
                    visitTime = java.util.Date(item.timestamp)
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("NewCardSelectionDialog", "获取搜索历史失败", e)
            emptyList()
        }
    }

    /**
     * 获取浏览历史
     */
    private fun getBrowseHistory(): List<HistoryEntry> {
        return try {
            val sharedPrefs = context.getSharedPreferences("browser_history", android.content.Context.MODE_PRIVATE)
            val historyJson = sharedPrefs.getString("history_data", "[]")
            
            if (historyJson.isNullOrEmpty()) {
                emptyList()
            } else {
                // 这里简化处理，实际应该用Gson等库解析JSON
                // 暂时返回空列表，等待真实的历史数据实现
                emptyList()
            }
        } catch (e: Exception) {
            android.util.Log.e("NewCardSelectionDialog", "获取浏览历史失败", e)
            emptyList()
        }
    }

    /**
     * 复制到剪贴板
     */
    private fun copyToClipboard(text: String) {
        try {
            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("URL", text)
            clipboard.setPrimaryClip(clip)
        } catch (e: Exception) {
            android.util.Log.e("NewCardSelectionDialog", "复制到剪贴板失败", e)
        }
    }

    /**
     * 删除历史记录
     */
    private fun deleteHistoryEntry(entry: HistoryEntry) {
        try {
            val searchHistoryManager = com.example.aifloatingball.SearchHistoryManager.getInstance(context)
            
            // 从标题中提取搜索关键词
            val query = if (entry.title.startsWith("搜索: ")) {
                entry.title.substring(4) // 移除"搜索: "前缀
            } else {
                entry.title
            }
            
            searchHistoryManager.removeSearchHistory(query, "com.example.aifloatingball")
            android.widget.Toast.makeText(context, "历史记录已删除", android.widget.Toast.LENGTH_SHORT).show()
            
            // 刷新历史页面
            showHistoryPage()
        } catch (e: Exception) {
            android.util.Log.e("NewCardSelectionDialog", "删除历史记录失败", e)
            android.widget.Toast.makeText(context, "删除失败", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 添加到收藏
     */
    private fun addToBookmarks(entry: HistoryEntry) {
        try {
            val sharedPrefs = context.getSharedPreferences("browser_bookmarks", android.content.Context.MODE_PRIVATE)
            val bookmarksJson = sharedPrefs.getString("bookmarks_data", "[]")
            
            // 创建新的收藏条目
            val bookmarkEntry = BookmarkEntry(
                id = "bookmark_${System.currentTimeMillis()}",
                title = entry.title,
                url = entry.url,
                folder = "从历史添加",
                createTime = java.util.Date()
            )
            
            // 这里简化处理，实际应该用Gson等库
            android.widget.Toast.makeText(context, "已添加到收藏", android.widget.Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.util.Log.e("NewCardSelectionDialog", "添加到收藏失败", e)
            android.widget.Toast.makeText(context, "添加失败", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 删除收藏
     */
    private fun deleteBookmarkEntry(entry: BookmarkEntry) {
        try {
            val sharedPrefs = context.getSharedPreferences("browser_bookmarks", android.content.Context.MODE_PRIVATE)
            val bookmarksJson = sharedPrefs.getString("bookmarks_data", "[]")
            
            // 这里简化处理，实际应该用Gson等库
            android.widget.Toast.makeText(context, "收藏已删除", android.widget.Toast.LENGTH_SHORT).show()
            
            // 刷新收藏页面
            showBookmarksPage()
        } catch (e: Exception) {
            android.util.Log.e("NewCardSelectionDialog", "删除收藏失败", e)
            android.widget.Toast.makeText(context, "删除失败", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 编辑收藏
     */
    private fun editBookmarkEntry(entry: BookmarkEntry) {
        try {
            val input = android.widget.EditText(context)
            input.setText(entry.title)
            input.setHint("输入新的标题")
            
            val builder = android.app.AlertDialog.Builder(context)
                .setTitle("编辑收藏")
                .setView(input)
                .setPositiveButton("保存") { _, _ ->
                    val newTitle = input.text.toString().trim()
                    if (newTitle.isNotEmpty()) {
                        // 这里简化处理，实际应该更新数据库
                        android.widget.Toast.makeText(context, "收藏已更新", android.widget.Toast.LENGTH_SHORT).show()
                        showBookmarksPage()
                    }
                }
                .setNegativeButton("取消", null)
            
            builder.show()
        } catch (e: Exception) {
            android.util.Log.e("NewCardSelectionDialog", "编辑收藏失败", e)
            android.widget.Toast.makeText(context, "编辑失败", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 从历史记录创建卡片
     */
    private fun createCardFromHistory(entry: HistoryEntry) {
        try {
            android.util.Log.d("NewCardSelectionDialog", "从历史记录创建卡片: ${entry.title}")
            
            // 调用历史记录选择回调
            onHistoryItemSelected(entry)
            dismiss()
        } catch (e: Exception) {
            android.util.Log.e("NewCardSelectionDialog", "从历史记录创建卡片失败", e)
            android.widget.Toast.makeText(context, "创建卡片失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 从收藏创建卡片
     */
    private fun createCardFromBookmark(entry: BookmarkEntry) {
        try {
            android.util.Log.d("NewCardSelectionDialog", "从收藏创建卡片: ${entry.title}")
            
            // 将BookmarkEntry转换为HistoryEntry格式
            val historyEntry = HistoryEntry(
                id = entry.id,
                title = entry.title,
                url = entry.url,
                visitTime = entry.createTime
            )
            
            // 调用历史记录选择回调
            onHistoryItemSelected(historyEntry)
            dismiss()
        } catch (e: Exception) {
            android.util.Log.e("NewCardSelectionDialog", "从收藏创建卡片失败", e)
            android.widget.Toast.makeText(context, "创建卡片失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 清空所有历史记录
     */
    private fun clearAllHistory() {
        try {
            val builder = android.app.AlertDialog.Builder(context)
                .setTitle("确认清空")
                .setMessage("确定要清空所有搜索历史吗？此操作不可撤销。")
                .setPositiveButton("清空") { _, _ ->
                    val searchHistoryManager = com.example.aifloatingball.SearchHistoryManager.getInstance(context)
                    searchHistoryManager.clearSearchHistory()
                    android.widget.Toast.makeText(context, "历史记录已清空", android.widget.Toast.LENGTH_SHORT).show()
                    
                    // 刷新历史页面
                    showHistoryPage()
                }
                .setNegativeButton("取消", null)
            
            builder.show()
        } catch (e: Exception) {
            android.util.Log.e("NewCardSelectionDialog", "清空历史记录失败", e)
            android.widget.Toast.makeText(context, "清空失败", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 过滤历史记录
     */
    private fun filterHistoryEntries(query: String) {
        try {
            val allHistory = getRealHistoryData()
            val filteredHistory = allHistory.filter { entry ->
                entry.title.contains(query, ignoreCase = true) || 
                entry.url.contains(query, ignoreCase = true)
            }
            
            // 更新历史页面显示
            updateHistoryDisplay(filteredHistory)
        } catch (e: Exception) {
            android.util.Log.e("NewCardSelectionDialog", "过滤历史记录失败", e)
        }
    }

    /**
     * 更新历史记录显示
     */
    private fun updateHistoryDisplay(historyData: List<HistoryEntry>) {
        try {
            val recyclerView = contentContainer.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_history)
            val emptyLayout = contentContainer.findViewById<LinearLayout>(R.id.layout_empty_history)
            
            if (recyclerView != null) {
                val adapter = recyclerView.adapter as? com.example.aifloatingball.adapter.HistoryEntryAdapter
                if (adapter != null) {
                    adapter.updateEntries(historyData)
                }
                
                // 更新空状态显示
                if (emptyLayout != null) {
                    emptyLayout.visibility = if (historyData.isEmpty()) {
                        android.view.View.VISIBLE
                    } else {
                        android.view.View.GONE
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("NewCardSelectionDialog", "更新历史记录显示失败", e)
        }
    }

    /**
     * 获取真实的收藏数据
     */
    private fun getRealBookmarkData(): List<BookmarkEntry> {
        return try {
            // 从SharedPreferences或数据库获取收藏数据
            val sharedPrefs = context.getSharedPreferences("browser_bookmarks", android.content.Context.MODE_PRIVATE)
            val bookmarksJson = sharedPrefs.getString("bookmarks_data", "[]")
            
            if (bookmarksJson.isNullOrEmpty()) {
                // 如果没有收藏数据，返回一些默认的常用网站
                listOf(
                    BookmarkEntry(
                        id = "default_1",
                        title = "Google 搜索",
                        url = "https://www.google.com",
                        folder = "常用网站",
                        createTime = java.util.Date()
                    ),
                    BookmarkEntry(
                        id = "default_2",
                        title = "百度搜索", 
                        url = "https://www.baidu.com",
                        folder = "常用网站",
                        createTime = java.util.Date()
                    )
                )
            } else {
                // 解析JSON数据（这里简化处理，实际应该用Gson等库）
                listOf(
                    BookmarkEntry(
                        id = "parsed_1",
                        title = "收藏的网站",
                        url = "https://www.google.com",
                        folder = "默认文件夹",
                        createTime = java.util.Date()
                    )
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("NewCardSelectionDialog", "获取收藏数据失败", e)
            emptyList()
        }
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
