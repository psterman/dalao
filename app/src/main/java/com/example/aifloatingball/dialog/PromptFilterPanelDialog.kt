package com.example.aifloatingball.dialog

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.adapter.PromptCommunityAdapter
import com.example.aifloatingball.data.PromptCommunityData
import com.example.aifloatingball.model.PromptCommunityItem
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/**
 * Prompt筛选面板对话框
 */
class PromptFilterPanelDialog(
    context: Context,
    private val filterType: FilterPanelType,
    private val onPromptClick: (PromptCommunityItem) -> Unit
) {
    
    enum class FilterPanelType {
        HOT,           // 热门
        LATEST,        // 新发布
        MY_COLLECTION  // 我的收藏
    }
    
    private val dialog: Dialog
    private lateinit var titleText: TextView
    private lateinit var closeButton: ImageButton
    private lateinit var sortChipsContainer: LinearLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView
    
    private lateinit var adapter: PromptCommunityAdapter
    private var currentData: List<PromptCommunityItem> = emptyList()
    
    init {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_prompt_filter_panel, null)
        
        dialog = Dialog(context, R.style.FullScreenDialog)
        dialog.setContentView(view)
        dialog.window?.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT
        )
        
        initViews(view)
        setupRecyclerView()
        setupSortOptions(context)
        loadData()
        
        titleText.text = when (filterType) {
            FilterPanelType.HOT -> "热门Prompt"
            FilterPanelType.LATEST -> "新发布Prompt"
            FilterPanelType.MY_COLLECTION -> "我的收藏"
        }
    }
    
    private fun initViews(view: View) {
        titleText = view.findViewById(R.id.filter_panel_title)
        closeButton = view.findViewById(R.id.close_filter_panel)
        sortChipsContainer = view.findViewById(R.id.sort_chips_container)
        recyclerView = view.findViewById(R.id.filter_panel_recycler_view)
        emptyText = view.findViewById(R.id.filter_panel_empty_text)
        
        closeButton.setOnClickListener { dismiss() }
    }
    
    private fun setupRecyclerView() {
        adapter = PromptCommunityAdapter(
            emptyList(),
            onItemClick = { prompt ->
                onPromptClick(prompt)
                dismiss()
            },
            onLikeClick = { prompt ->
                android.util.Log.d("FilterPanel", "点赞: ${prompt.title}")
            },
            onCollectClick = { prompt ->
                android.util.Log.d("FilterPanel", "收藏: ${prompt.title}")
            },
            onCommentClick = { prompt ->
                android.util.Log.d("FilterPanel", "评论: ${prompt.title}")
            },
            onShareClick = { prompt ->
                android.util.Log.d("FilterPanel", "分享: ${prompt.title}")
            }
        )
        
        recyclerView.layoutManager = LinearLayoutManager(recyclerView.context)
        recyclerView.adapter = adapter
    }
    
    private fun setupSortOptions(context: Context) {
        val sortOptions = when (filterType) {
            FilterPanelType.HOT -> listOf(
                "综合热度" to { list: List<PromptCommunityItem> ->
                    list.sortedByDescending { it.likeCount + it.collectCount + it.viewCount }
                },
                "点赞最多" to { list: List<PromptCommunityItem> ->
                    list.sortedByDescending { it.likeCount }
                },
                "收藏最多" to { list: List<PromptCommunityItem> ->
                    list.sortedByDescending { it.collectCount }
                },
                "浏览最多" to { list: List<PromptCommunityItem> ->
                    list.sortedByDescending { it.viewCount }
                }
            )
            
            FilterPanelType.LATEST -> listOf(
                "最新发布" to { list: List<PromptCommunityItem> ->
                    list.sortedByDescending { it.publishTime }
                },
                "热度上升" to { list: List<PromptCommunityItem> ->
                    list.sortedByDescending { 
                        // 热度上升速度 = (点赞数 + 收藏数) / 时间差
                        val timeDiff = System.currentTimeMillis() - it.publishTime
                        val hours = timeDiff.toDouble() / (1000 * 60 * 60)
                        if (hours > 0) {
                            (it.likeCount + it.collectCount).toDouble() / hours
                        } else {
                            0.0
                        }
                    }
                },
                "评论最多" to { list: List<PromptCommunityItem> ->
                    list.sortedByDescending { it.commentCount }
                }
            )
            
            FilterPanelType.MY_COLLECTION -> listOf(
                "最近收藏" to { list: List<PromptCommunityItem> ->
                    list.sortedByDescending { it.publishTime } // 假设使用publishTime作为收藏时间
                },
                "使用最久" to { list: List<PromptCommunityItem> ->
                    list.sortedBy { it.likeCount } // 反转逻辑，使用次数多的排在前面
                },
                "点赞最多" to { list: List<PromptCommunityItem> ->
                    list.sortedByDescending { it.likeCount }
                },
                "我的标签" to { list: List<PromptCommunityItem> ->
                    list // 保持原顺序
                }
            )
        }
        
        var selectedChip: Chip? = null
        var isFirstChip = true
        
        sortOptions.forEach { (label, sortFunction) ->
            val chip = Chip(context).apply {
                text = label
                isClickable = true
                isCheckable = true
                isChecked = isFirstChip // 第一个默认选中
                setChipBackgroundColorResource(R.color.ai_assistant_primary_container_light)
                setTextColor(ContextCompat.getColorStateList(context, R.color.ai_assistant_primary))
                setOnClickListener {
                    // 取消其他chip的选中状态
                    selectedChip?.isChecked = false
                    selectedChip = this
                    this.isChecked = true
                    
                    // 应用排序
                    val sorted = sortFunction(currentData)
                    currentData = sorted
                    adapter.updateData(sorted)
                }
            }
            
            sortChipsContainer.addView(chip)
            if (selectedChip == null) {
                selectedChip = chip
            }
            isFirstChip = false
        }
    }
    
    private fun loadData() {
        currentData = when (filterType) {
            FilterPanelType.HOT -> {
                PromptCommunityData.getPromptsByFilter(
                    com.example.aifloatingball.model.FilterType.HOT
                )
            }
            FilterPanelType.LATEST -> {
                PromptCommunityData.getPromptsByFilter(
                    com.example.aifloatingball.model.FilterType.LATEST
                )
            }
            FilterPanelType.MY_COLLECTION -> {
                PromptCommunityData.getPromptsByFilter(
                    com.example.aifloatingball.model.FilterType.MY_COLLECTION
                )
            }
        }
        
        if (currentData.isEmpty()) {
            recyclerView.visibility = android.view.View.GONE
            emptyText.visibility = android.view.View.VISIBLE
        } else {
            recyclerView.visibility = android.view.View.VISIBLE
            emptyText.visibility = android.view.View.GONE
            adapter.updateData(currentData)
        }
    }
    
    fun show() {
        dialog.show()
    }
    
    fun dismiss() {
        dialog.dismiss()
    }
    
    fun isShowing(): Boolean {
        return dialog.isShowing
    }
}

