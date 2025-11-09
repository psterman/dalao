package com.example.aifloatingball.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.ItemTouchHelper
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
import com.example.aifloatingball.SettingsManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlin.math.abs

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
        
        val settingsManager = SettingsManager.getInstance(context)
        val isLeftHanded = settingsManager.isLeftHandedModeEnabled()
        
        val adapter = HistoryEntryAdapter(
            entries = historyData,
            onItemClick = { entry ->
                // 创建新卡片而不是直接加载
                createCardFromHistory(entry)
            },
            onMoreClick = { entry ->
                showHistoryMoreMenu(entry)
            },
            onSwipeFavorite = { entry ->
                // 收藏操作
                addToBookmarks(entry)
            },
            onSwipeDelete = { entry ->
                // 删除操作
                deleteHistoryEntryFromSharedPreferences(entry)
            },
            isLeftHandedMode = isLeftHanded
        )
        
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        
        // 设置滑动功能
        setupHistorySwipe(recyclerView, adapter)
        
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
        
        val settingsManager = SettingsManager.getInstance(context)
        val isLeftHanded = settingsManager.isLeftHandedModeEnabled()
        
        val adapter = BookmarkEntryAdapter(
            entries = bookmarkData,
            onItemClick = { entry ->
                // 创建新卡片而不是直接加载
                createCardFromBookmark(entry)
            },
            onMoreClick = { entry ->
                showBookmarkMoreMenu(entry)
            },
            onSwipeEdit = { entry ->
                // 编辑操作
                editBookmarkEntry(entry)
            },
            onSwipeDelete = { entry ->
                // 删除操作
                deleteBookmarkEntry(entry)
            },
            isLeftHandedMode = isLeftHanded
        )
        
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
        
        // 设置滑动功能
        setupBookmarkSwipe(recyclerView, adapter)
        
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

            // 调用历史记录选择回调，在后台创建卡片
            onHistoryItemSelected(historyEntry)
            
            // 延迟关闭对话框，让用户看到卡片创建过程
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                dismiss()
            }, 500)

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
     * 获取浏览历史（自动过滤隐藏组的历史记录）
     */
    private fun getBrowseHistory(): List<HistoryEntry> {
        return try {
            val sharedPrefs = context.getSharedPreferences("browser_history", android.content.Context.MODE_PRIVATE)
            val historyJson = sharedPrefs.getString("history_data", "[]")
            
            if (historyJson.isNullOrEmpty()) {
                emptyList()
            } else {
                // 使用Gson解析JSON历史记录
                val gson = com.google.gson.Gson()
                val type = object : com.google.gson.reflect.TypeToken<List<HistoryEntry>>() {}.type
                val allHistory = gson.fromJson<List<HistoryEntry>>(historyJson, type) ?: emptyList()
                
                // 过滤隐藏组的历史记录
                val groupManager = com.example.aifloatingball.manager.TabGroupManager.getInstance(context)
                val hiddenGroupIds = groupManager.getAllGroupsIncludingHidden()
                    .filter { it.isHidden }
                    .map { it.id }
                    .toSet()
                
                // 只返回可见组的历史记录（groupId为null的记录也显示，兼容旧数据）
                allHistory.filter { entry ->
                    entry.groupId == null || !hiddenGroupIds.contains(entry.groupId)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("NewCardSelectionDialog", "获取浏览历史失败", e)
            emptyList()
        }
    }

    /**
     * 设置历史访问页面滑动功能
     */
    private fun setupHistorySwipe(recyclerView: RecyclerView, adapter: HistoryEntryAdapter) {
        var swipeHistoryLastDx: Float = 0f
        var swipeHistoryLastTime: Long = 0L
        val settingsManager = SettingsManager.getInstance(context)
        val isLeftHanded = settingsManager.isLeftHandedModeEnabled()
        
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, if (isLeftHanded) ItemTouchHelper.RIGHT else ItemTouchHelper.LEFT
        ) {
            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val swipeFlags = if (isLeftHanded) ItemTouchHelper.RIGHT else ItemTouchHelper.LEFT
                return makeMovementFlags(0, swipeFlags)
            }

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false
            }
            
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // 不删除，防止触发删除动画
            }
            
            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
                return 0.5f
            }
            
            override fun getSwipeEscapeVelocity(defaultValue: Float): Float {
                return defaultValue * 2f
            }
            
            /**
             * 在开始滑动之前检查是否应该允许滑动
             */
            override fun isItemViewSwipeEnabled(): Boolean {
                return true
            }
            
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                if (viewHolder is HistoryEntryAdapter.ViewHolder) {
                    val velocityX = if (swipeHistoryLastTime > 0 && abs(swipeHistoryLastDx) > 0) {
                        val timeDiff = System.currentTimeMillis() - swipeHistoryLastTime
                        if (timeDiff > 0) abs(swipeHistoryLastDx) / timeDiff * 1000 else 0f
                    } else 0f
                    
                    adapter.handleSwipeEnd(viewHolder, swipeHistoryLastDx, velocityX)
                    swipeHistoryLastDx = 0f
                    swipeHistoryLastTime = 0L
                }
            }
            
            override fun onChildDraw(
                c: android.graphics.Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && viewHolder is HistoryEntryAdapter.ViewHolder) {
                    swipeHistoryLastDx = dX
                    swipeHistoryLastTime = System.currentTimeMillis()
                    
                    val maxSwipe = 120f * recyclerView.context.resources.displayMetrics.density
                    // 限制滑动方向：普通模式只允许左滑（负数），左撇子模式只允许右滑（正数）
                    val limitedDx = if (isLeftHanded) {
                        dX.coerceIn(0f, maxSwipe) // 左撇子模式：只允许右滑
                    } else {
                        dX.coerceIn(-maxSwipe, 0f) // 普通模式：只允许左滑
                    }
                    adapter.handleSwipe(viewHolder, limitedDx, isCurrentlyActive)
                    
                    // 使用0作为dX传给super，因为我们已经手动处理了translationX
                    super.onChildDraw(c, recyclerView, viewHolder, 0f, dY, actionState, isCurrentlyActive)
                    return
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        })
        
        // 在 RecyclerView 上添加触摸监听器，检测按钮区域的触摸事件
        recyclerView.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 检测触摸点是否在按钮区域内
                    val x = event.x
                    val y = event.y
                    
                    // 遍历所有可见的子视图，检查触摸点是否在已滑动项的按钮区域
                    for (i in 0 until recyclerView.childCount) {
                        val child = recyclerView.getChildAt(i)
                        val viewHolder = recyclerView.getChildViewHolder(child)
                        if (viewHolder is HistoryEntryAdapter.ViewHolder) {
                            val translationX = viewHolder.cardContent.translationX
                            if (abs(translationX) >= 10f) {
                                // 卡片已经滑动，检查触摸点是否在按钮区域
                                val buttonAreaWidth = 120f * view.context.resources.displayMetrics.density
                                val itemLeft = child.left
                                val itemRight = child.right
                                val itemTop = child.top
                                val itemBottom = child.bottom
                                
                                val isOnButton = if (isLeftHanded) {
                                    val buttonLeft = itemLeft.toFloat()
                                    val buttonRight = itemLeft + buttonAreaWidth
                                    x >= buttonLeft && x <= buttonRight && y >= itemTop && y <= itemBottom
                                } else {
                                    val buttonLeft = itemRight - buttonAreaWidth
                                    val buttonRight = itemRight.toFloat()
                                    x >= buttonLeft && x <= buttonRight && y >= itemTop && y <= itemBottom
                                }
                                
                                if (isOnButton) {
                                    // 触摸点在按钮区域，禁止 RecyclerView 和 ItemTouchHelper 拦截事件
                                    view.parent?.requestDisallowInterceptTouchEvent(true)
                                    recyclerView.requestDisallowInterceptTouchEvent(true)
                                    // 暂时分离 ItemTouchHelper，让按钮可以接收事件
                                    itemTouchHelper.attachToRecyclerView(null)
                                    android.util.Log.d("NewCardSelectionDialog", "检测到按钮区域触摸，禁用滑动")
                                    return@setOnTouchListener false // 让子视图处理事件
                                }
                            }
                        }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 恢复事件拦截和 ItemTouchHelper
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    recyclerView.requestDisallowInterceptTouchEvent(false)
                    // 延迟重新附加 ItemTouchHelper，确保按钮点击事件完成
                    recyclerView.post {
                        itemTouchHelper.attachToRecyclerView(recyclerView)
                    }
                }
            }
            false // 不消费事件，让 RecyclerView 正常处理
        }
        
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    /**
     * 设置收藏页面滑动功能
     */
    private fun setupBookmarkSwipe(recyclerView: RecyclerView, adapter: BookmarkEntryAdapter) {
        var swipeBookmarkLastDx: Float = 0f
        var swipeBookmarkLastTime: Long = 0L
        val settingsManager = SettingsManager.getInstance(context)
        val isLeftHanded = settingsManager.isLeftHandedModeEnabled()
        
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            0, if (isLeftHanded) ItemTouchHelper.RIGHT else ItemTouchHelper.LEFT
        ) {
            override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val swipeFlags = if (isLeftHanded) ItemTouchHelper.RIGHT else ItemTouchHelper.LEFT
                return makeMovementFlags(0, swipeFlags)
            }

            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
                return false
            }
            
            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // 不删除，防止触发删除动画
            }
            
            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float {
                return 0.5f
            }
            
            override fun getSwipeEscapeVelocity(defaultValue: Float): Float {
                return defaultValue * 2f
            }
            
            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                if (viewHolder is BookmarkEntryAdapter.ViewHolder) {
                    val velocityX = if (swipeBookmarkLastTime > 0 && abs(swipeBookmarkLastDx) > 0) {
                        val timeDiff = System.currentTimeMillis() - swipeBookmarkLastTime
                        if (timeDiff > 0) abs(swipeBookmarkLastDx) / timeDiff * 1000 else 0f
                    } else 0f
                    
                    adapter.handleSwipeEnd(viewHolder, swipeBookmarkLastDx, velocityX)
                    swipeBookmarkLastDx = 0f
                    swipeBookmarkLastTime = 0L
                }
            }
            
            override fun onChildDraw(
                c: android.graphics.Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && viewHolder is BookmarkEntryAdapter.ViewHolder) {
                    swipeBookmarkLastDx = dX
                    swipeBookmarkLastTime = System.currentTimeMillis()
                    
                    val maxSwipe = 120f * recyclerView.context.resources.displayMetrics.density
                    // 限制滑动方向：普通模式只允许左滑（负数），左撇子模式只允许右滑（正数）
                    val limitedDx = if (isLeftHanded) {
                        dX.coerceIn(0f, maxSwipe) // 左撇子模式：只允许右滑
                    } else {
                        dX.coerceIn(-maxSwipe, 0f) // 普通模式：只允许左滑
                    }
                    adapter.handleSwipe(viewHolder, limitedDx, isCurrentlyActive)
                    
                    // 使用0作为dX传给super，因为我们已经手动处理了translationX
                    super.onChildDraw(c, recyclerView, viewHolder, 0f, dY, actionState, isCurrentlyActive)
                    return
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        })
        
        itemTouchHelper.attachToRecyclerView(recyclerView)
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
     * 删除历史记录（从SharedPreferences）
     */
    private fun deleteHistoryEntryFromSharedPreferences(entry: HistoryEntry) {
        try {
            // 如果是“搜索历史”项，优先从 SearchHistoryManager 中删除
            run {
                val isSearchItem = try {
                    entry.id.startsWith("search_") || entry.title.startsWith("搜索:") || entry.url.contains("?q=") || entry.url.contains("&q=")
                } catch (_: Exception) { false }
                if (isSearchItem) {
                    var query: String? = null
                    if (entry.title.startsWith("搜索:")) {
                        query = entry.title.removePrefix("搜索:").trim()
                    }
                    if (query.isNullOrEmpty()) {
                        try {
                            val uri = android.net.Uri.parse(entry.url)
                            query = uri.getQueryParameter("q")
                        } catch (_: Exception) {}
                    }
                    if (!query.isNullOrEmpty()) {
                        com.example.aifloatingball.SearchHistoryManager.getInstance(context)
                            .removeSearchHistoryByQuery(query!!)
                        android.widget.Toast.makeText(context, "搜索记录已删除", android.widget.Toast.LENGTH_SHORT).show()
                        refreshCurrentPage()
                        return
                    }
                }
            }
            val sharedPrefs = context.getSharedPreferences("browser_history", android.content.Context.MODE_PRIVATE)
            val historyJson = sharedPrefs.getString("history_data", "[]")
            
            // 使用Gson解析历史记录
            val gson = Gson()
            val type = object : TypeToken<MutableList<HistoryEntry>>() {}.type
            val historyList = if (historyJson != null && historyJson.isNotEmpty()) {
                try {
                    gson.fromJson<MutableList<HistoryEntry>>(historyJson, type) ?: mutableListOf()
                } catch (e: Exception) {
                    mutableListOf()
                }
            } else {
                mutableListOf()
            }
            
            // 删除匹配的记录（优先匹配ID，如果ID为空则匹配URL和标题）
            val initialSize = historyList.size
            android.util.Log.d("NewCardSelectionDialog", "删除历史记录前: 总数=${initialSize}, 要删除的条目: id=${entry.id}, url=${entry.url}, title=${entry.title}")
            
            // 先尝试通过ID删除
            var removed = false
            if (entry.id.isNotEmpty()) {
                val wasRemoved = historyList.removeAll { it.id == entry.id }
                if (wasRemoved) {
                    removed = true
                    android.util.Log.d("NewCardSelectionDialog", "通过ID删除历史记录成功")
                }
            }
            
            // 如果通过ID没有删除成功，尝试通过URL和标题匹配
            if (!removed) {
                val wasRemoved = historyList.removeAll { 
                    (it.url == entry.url || it.url.equals(entry.url, ignoreCase = true)) && 
                    (it.title == entry.title || it.title.equals(entry.title, ignoreCase = true))
                }
                if (wasRemoved) {
                    removed = true
                    android.util.Log.d("NewCardSelectionDialog", "通过URL和标题删除历史记录成功")
                }
            }
            
            if (removed && historyList.size < initialSize) {
                // 保存更新后的历史记录
                val updatedJson = gson.toJson(historyList)
                sharedPrefs.edit().putString("history_data", updatedJson).apply()
                
                android.util.Log.d("NewCardSelectionDialog", "删除历史记录后: 总数=${historyList.size}")
                android.widget.Toast.makeText(context, "历史记录已删除", android.widget.Toast.LENGTH_SHORT).show()
                
                // 刷新当前页面显示
                refreshCurrentPage()
            } else {
                android.util.Log.w("NewCardSelectionDialog", "未找到要删除的历史记录: id=${entry.id}, url=${entry.url}, title=${entry.title}")
                android.widget.Toast.makeText(context, "未找到该记录", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("NewCardSelectionDialog", "删除历史记录失败", e)
            android.widget.Toast.makeText(context, "删除失败", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * 删除历史记录（旧方法，保留兼容性）
     */
    private fun deleteHistoryEntry(entry: HistoryEntry) {
        deleteHistoryEntryFromSharedPreferences(entry)
    }

    /**
     * 添加到收藏
     */
    private fun addToBookmarks(entry: HistoryEntry) {
        try {
            // 使用BookmarkManager统一管理
            val bookmarkManager = com.example.aifloatingball.manager.BookmarkManager.getInstance(context)
            
            // 检查是否已存在相同URL的收藏（忽略大小写）
            if (bookmarkManager.isBookmarkExist(entry.url)) {
                android.widget.Toast.makeText(context, "该网址已在收藏中", android.widget.Toast.LENGTH_SHORT).show()
                android.util.Log.d("NewCardSelectionDialog", "收藏已存在: ${entry.url}")
                return
            }
            
            android.util.Log.d("NewCardSelectionDialog", "添加收藏: title=${entry.title}, url=${entry.url}")
            
            // 创建新的书签
            val bookmark = com.example.aifloatingball.model.Bookmark(
                title = entry.title,
                url = entry.url,
                folder = "从历史添加",
                addTime = System.currentTimeMillis()
            )
            
            // 添加到收藏
            bookmarkManager.addBookmark(bookmark, null)
            
            android.widget.Toast.makeText(context, "已添加到收藏", android.widget.Toast.LENGTH_SHORT).show()
            
            // 刷新当前页面显示（如果是历史页面，需要更新历史记录；如果是收藏页面，需要更新收藏列表）
            refreshCurrentPage()
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
            
            // 使用Gson解析收藏
            val gson = Gson()
            val type = object : TypeToken<MutableList<BookmarkEntry>>() {}.type
            val bookmarksList = if (bookmarksJson != null && bookmarksJson.isNotEmpty()) {
                try {
                    gson.fromJson<MutableList<BookmarkEntry>>(bookmarksJson, type) ?: mutableListOf()
                } catch (e: Exception) {
                    mutableListOf()
                }
            } else {
                mutableListOf()
            }
            
            // 删除匹配的记录（优先匹配ID，如果ID为空则匹配URL和标题）
            val initialSize = bookmarksList.size
            android.util.Log.d("NewCardSelectionDialog", "删除收藏前: 总数=${initialSize}, 要删除的条目: id=${entry.id}, url=${entry.url}, title=${entry.title}")
            
            // 先尝试通过ID删除
            var removed = false
            if (entry.id.isNotEmpty()) {
                val wasRemoved = bookmarksList.removeAll { it.id == entry.id }
                if (wasRemoved) {
                    removed = true
                    android.util.Log.d("NewCardSelectionDialog", "通过ID删除成功")
                }
            }
            
            // 如果通过ID没有删除成功，尝试通过URL和标题匹配
            if (!removed) {
                val wasRemoved = bookmarksList.removeAll { 
                    (it.url == entry.url || it.url.equals(entry.url, ignoreCase = true)) && 
                    (it.title == entry.title || it.title.equals(entry.title, ignoreCase = true))
                }
                if (wasRemoved) {
                    removed = true
                    android.util.Log.d("NewCardSelectionDialog", "通过URL和标题删除成功")
                }
            }
            
            if (removed && bookmarksList.size < initialSize) {
                // 保存更新后的收藏
                val updatedJson = gson.toJson(bookmarksList)
                sharedPrefs.edit().putString("bookmarks_data", updatedJson).apply()
                
                android.util.Log.d("NewCardSelectionDialog", "删除收藏后: 总数=${bookmarksList.size}")
                android.widget.Toast.makeText(context, "收藏已删除", android.widget.Toast.LENGTH_SHORT).show()
                
                // 刷新当前页面显示
                refreshCurrentPage()
            } else {
                android.util.Log.w("NewCardSelectionDialog", "未找到要删除的收藏: id=${entry.id}, url=${entry.url}, title=${entry.title}")
                android.widget.Toast.makeText(context, "未找到该收藏", android.widget.Toast.LENGTH_SHORT).show()
            }
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
            input.setSingleLine(true)
            
            val builder = android.app.AlertDialog.Builder(context)
                .setTitle("编辑收藏")
                .setView(input)
                .setPositiveButton("保存") { _, _ ->
                    val newTitle = input.text.toString().trim()
                    if (newTitle.isNotEmpty() && newTitle != entry.title) {
                        updateBookmarkTitle(entry, newTitle)
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
     * 更新收藏标题
     */
    private fun updateBookmarkTitle(entry: BookmarkEntry, newTitle: String) {
        try {
            val sharedPrefs = context.getSharedPreferences("browser_bookmarks", android.content.Context.MODE_PRIVATE)
            val bookmarksJson = sharedPrefs.getString("bookmarks_data", "[]")
            
            // 使用Gson解析收藏
            val gson = Gson()
            val type = object : TypeToken<MutableList<BookmarkEntry>>() {}.type
            val bookmarksList = if (bookmarksJson != null && bookmarksJson.isNotEmpty()) {
                try {
                    gson.fromJson<MutableList<BookmarkEntry>>(bookmarksJson, type) ?: mutableListOf()
                } catch (e: Exception) {
                    mutableListOf()
                }
            } else {
                mutableListOf()
            }
            
            // 更新匹配的记录
            val index = bookmarksList.indexOfFirst { it.id == entry.id || (it.url == entry.url && it.title == entry.title) }
            if (index >= 0) {
                val updatedEntry = bookmarksList[index].copy(title = newTitle)
                bookmarksList[index] = updatedEntry
                
                // 保存更新后的收藏
                val updatedJson = gson.toJson(bookmarksList)
                sharedPrefs.edit().putString("bookmarks_data", updatedJson).apply()
                
                android.widget.Toast.makeText(context, "收藏已更新", android.widget.Toast.LENGTH_SHORT).show()
                
                // 刷新当前页面显示
                refreshCurrentPage()
            } else {
                android.widget.Toast.makeText(context, "未找到该收藏", android.widget.Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("NewCardSelectionDialog", "更新收藏失败", e)
            android.widget.Toast.makeText(context, "更新失败", android.widget.Toast.LENGTH_SHORT).show()
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
     * 刷新当前页面显示
     */
    private fun refreshCurrentPage() {
        try {
            // 根据当前选中的Tab刷新对应的页面
            val selectedTab = tabLayout.selectedTabPosition
            
            if (selectedTab == 0) {
                // 历史页面
                val recyclerView = contentContainer.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_history)
                val emptyLayout = contentContainer.findViewById<LinearLayout>(R.id.layout_empty_history)
                
                if (recyclerView != null) {
                    val adapter = recyclerView.adapter as? com.example.aifloatingball.adapter.HistoryEntryAdapter
                    if (adapter != null) {
                        val historyData = getRealHistoryData()
                        adapter.updateEntries(historyData)
                        
                        // 更新空状态显示
                        if (emptyLayout != null) {
                            emptyLayout.visibility = if (historyData.isEmpty()) {
                                android.view.View.VISIBLE
                            } else {
                                android.view.View.GONE
                            }
                            recyclerView.visibility = if (historyData.isEmpty()) {
                                android.view.View.GONE
                            } else {
                                android.view.View.VISIBLE
                            }
                        }
                    }
                }
            } else {
                // 收藏页面
                val recyclerView = contentContainer.findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_bookmarks)
                val emptyLayout = contentContainer.findViewById<LinearLayout>(R.id.layout_empty_bookmarks)
                
                if (recyclerView != null) {
                    val adapter = recyclerView.adapter as? com.example.aifloatingball.adapter.BookmarkEntryAdapter
                    if (adapter != null) {
                        val bookmarkData = getRealBookmarkData()
                        adapter.updateEntries(bookmarkData)
                        
                        // 更新空状态显示
                        if (emptyLayout != null) {
                            emptyLayout.visibility = if (bookmarkData.isEmpty()) {
                                android.view.View.VISIBLE
                            } else {
                                android.view.View.GONE
                            }
                            recyclerView.visibility = if (bookmarkData.isEmpty()) {
                                android.view.View.GONE
                            } else {
                                android.view.View.VISIBLE
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("NewCardSelectionDialog", "刷新当前页面失败", e)
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
     * 获取真实的收藏数据（兼容旧接口，返回BookmarkEntry列表）
     * 注意：此方法用于向后兼容，新代码应直接使用BookmarkManager
     */
    private fun getRealBookmarkData(): List<BookmarkEntry> {
        return try {
            // 使用BookmarkManager获取书签
            val bookmarkManager = com.example.aifloatingball.manager.BookmarkManager.getInstance(context)
            val bookmarks = bookmarkManager.getAllBookmarks()
            
            // 转换为BookmarkEntry（向后兼容）
            bookmarks.map { bookmark ->
                BookmarkEntry(
                    id = bookmark.id,
                    title = bookmark.title,
                    url = bookmark.url,
                    favicon = bookmark.faviconPath,
                    folder = bookmark.folder,
                    createTime = java.util.Date(bookmark.addTime),
                    tags = bookmark.tags
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
