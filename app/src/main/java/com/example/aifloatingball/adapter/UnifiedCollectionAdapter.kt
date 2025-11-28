package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * 统一收藏项适配器
 * 支持三种视图模式：列表、网格、时间线
 * 支持批量选择
 */
class UnifiedCollectionAdapter(
    private var collections: List<UnifiedCollectionItem> = emptyList(),
    private var viewMode: ViewMode = ViewMode.LIST,
    private val onItemClick: (UnifiedCollectionItem) -> Unit = {},
    private val onItemLongClick: (UnifiedCollectionItem) -> Unit = {},
    private val onItemSelected: (List<String>) -> Unit = {} // 选中项ID列表回调
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    
    // 视图模式枚举
    enum class ViewMode {
        LIST,      // 列表模式
        GRID,      // 网格模式
        TIMELINE   // 时间线模式
    }
    
    // 批量选择模式
    private var isSelectionMode = false
    private val selectedIds = mutableSetOf<String>()
    
    // 时间线模式需要的数据
    private val timelineGroups = mutableListOf<TimelineGroup>()
    
    init {
        if (viewMode == ViewMode.TIMELINE) {
            updateTimelineGroups()
        }
    }
    
    /**
     * 时间线分组数据
     */
    data class TimelineGroup(
        val date: String,
        val collections: List<UnifiedCollectionItem>
    )
    
    override fun getItemViewType(position: Int): Int {
        return when (viewMode) {
            ViewMode.LIST -> VIEW_TYPE_LIST
            ViewMode.GRID -> VIEW_TYPE_GRID
            ViewMode.TIMELINE -> {
                // 时间线模式：日期标题或收藏项
                if (isTimelineHeader(position)) VIEW_TYPE_TIMELINE_HEADER else VIEW_TYPE_TIMELINE_ITEM
            }
        }
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        
        return when (viewType) {
            VIEW_TYPE_LIST -> {
                val view = inflater.inflate(R.layout.item_unified_collection_list, parent, false)
                ListViewHolder(view)
            }
            VIEW_TYPE_GRID -> {
                val view = inflater.inflate(R.layout.item_unified_collection_grid, parent, false)
                GridViewHolder(view)
            }
            VIEW_TYPE_TIMELINE_HEADER -> {
                val view = inflater.inflate(R.layout.item_timeline_header, parent, false)
                TimelineHeaderViewHolder(view)
            }
            VIEW_TYPE_TIMELINE_ITEM -> {
                val view = inflater.inflate(R.layout.item_unified_collection_timeline, parent, false)
                TimelineItemViewHolder(view)
            }
            else -> throw IllegalArgumentException("Unknown view type: $viewType")
        }
    }
    
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ListViewHolder -> {
                holder.bind(collections[position])
            }
            is GridViewHolder -> {
                holder.bind(collections[position])
            }
            is TimelineHeaderViewHolder -> {
                val groupIndex = getTimelineGroupIndex(position)
                if (groupIndex >= 0 && groupIndex < timelineGroups.size) {
                    holder.bind(timelineGroups[groupIndex].date)
                }
            }
            is TimelineItemViewHolder -> {
                val item = getTimelineItem(position)
                if (item != null) {
                    holder.bind(item)
                }
            }
        }
    }
    
    override fun getItemCount(): Int {
        return when (viewMode) {
            ViewMode.LIST, ViewMode.GRID -> collections.size
            ViewMode.TIMELINE -> {
                // 时间线模式：日期标题数量 + 收藏项数量
                timelineGroups.sumOf { it.collections.size + 1 }
            }
        }
    }
    
    /**
     * 更新数据
     */
    fun updateData(newCollections: List<UnifiedCollectionItem>) {
        collections = newCollections
        if (viewMode == ViewMode.TIMELINE) {
            updateTimelineGroups()
        }
        notifyDataSetChanged()
    }
    
    /**
     * 设置视图模式
     */
    fun setViewMode(mode: ViewMode) {
        if (viewMode != mode) {
            viewMode = mode
            if (mode == ViewMode.TIMELINE) {
                updateTimelineGroups()
            }
            notifyDataSetChanged()
        }
    }
    
    /**
     * 设置选择模式
     */
    fun setSelectionMode(enabled: Boolean) {
        if (isSelectionMode != enabled) {
            isSelectionMode = enabled
            if (!enabled) {
                selectedIds.clear()
            }
            notifyDataSetChanged()
            onItemSelected(selectedIds.toList())
        }
    }
    
    /**
     * 获取选中的项ID列表
     */
    fun getSelectedIds(): List<String> {
        return selectedIds.toList()
    }
    
    /**
     * 清除选择
     */
    fun clearSelection() {
        selectedIds.clear()
        notifyDataSetChanged()
        onItemSelected(emptyList())
    }
    
    /**
     * 全选/取消全选
     */
    fun toggleSelectAll() {
        if (selectedIds.size == collections.size) {
            selectedIds.clear()
        } else {
            selectedIds.addAll(collections.map { it.id })
        }
        notifyDataSetChanged()
        onItemSelected(selectedIds.toList())
    }
    
    /**
     * 更新时间线分组
     */
    private fun updateTimelineGroups() {
        timelineGroups.clear()
        
        // 按日期分组
        val grouped = collections.groupBy { item ->
            val date = Date(item.collectedTime)
            SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()).format(date)
        }
        
        // 按日期排序（降序）
        grouped.toList().sortedByDescending { it.first }.forEach { (date, items) ->
            timelineGroups.add(TimelineGroup(date, items.sortedByDescending { it.collectedTime }))
        }
    }
    
    /**
     * 判断是否为时间线标题
     */
    private fun isTimelineHeader(position: Int): Boolean {
        var currentPos = 0
        timelineGroups.forEach { group ->
            if (currentPos == position) return true
            currentPos++
            currentPos += group.collections.size
            if (currentPos > position) return false
        }
        return false
    }
    
    /**
     * 获取时间线组索引
     */
    private fun getTimelineGroupIndex(position: Int): Int {
        var currentPos = 0
        timelineGroups.forEachIndexed { index, group ->
            if (currentPos == position) return index
            currentPos++
            currentPos += group.collections.size
            if (currentPos > position) return -1
        }
        return -1
    }
    
    /**
     * 获取时间线项
     */
    private fun getTimelineItem(position: Int): UnifiedCollectionItem? {
        var currentPos = 0
        timelineGroups.forEach { group ->
            currentPos++ // 跳过标题
            val itemIndex = position - currentPos
            if (itemIndex >= 0 && itemIndex < group.collections.size) {
                return group.collections[itemIndex]
            }
            currentPos += group.collections.size
        }
        return null
    }
    
    /**
     * 列表视图Holder
     */
    inner class ListViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: TextView = itemView.findViewById(R.id.collection_icon)
        private val titleText: TextView = itemView.findViewById(R.id.collection_title)
        private val sourceText: TextView = itemView.findViewById(R.id.collection_source)
        private val previewText: TextView = itemView.findViewById(R.id.collection_preview)
        private val tagsContainer: LinearLayout = itemView.findViewById(R.id.collection_tags_container)
        private val bottomInfoText: TextView = itemView.findViewById(R.id.collection_bottom_info)
        private val priorityBadge: TextView = itemView.findViewById(R.id.collection_priority)
        private val checkBox: CheckBox = itemView.findViewById(R.id.collection_checkbox)
        private val cardView: View = itemView.findViewById(R.id.collection_card)
        
        fun bind(item: UnifiedCollectionItem) {
            // 设置图标（使用emoji或图标资源）
            iconView.text = item.collectionType.icon
            
            // 设置标题
            titleText.text = item.title
            
            // 设置来源
            sourceText.text = item.getSourceDisplayText() + " · " + item.getFormattedCollectedTime()
            
            // 设置预览（对于视频收藏，显示下载状态）
            val previewContent = if (item.collectionType == com.example.aifloatingball.model.CollectionType.VIDEO_COLLECTION) {
                buildString {
                    // 获取下载状态
                    val downloadStatus = item.extraData?.get("downloadStatus") as? String
                    val saveLocation = item.extraData?.get("saveLocation") as? String
                    val videoPath = item.extraData?.get("videoPath") as? String
                    
                    // 显示下载状态（优先检查downloadStatus，然后检查文件路径）
                    when {
                        downloadStatus == "completed" || (!videoPath.isNullOrBlank() && saveLocation == "已下载到本地") -> {
                            append("✅ 已下载到本地")
                            val fileName = item.extraData?.get("originalFileName") as? String
                            if (!fileName.isNullOrBlank()) {
                                append(" · $fileName")
                            } else if (!videoPath.isNullOrBlank()) {
                                // 如果没有文件名，尝试从路径提取
                                val pathName = videoPath.substringAfterLast("/").substringBefore("?")
                                if (pathName.isNotEmpty() && pathName.length < 50) {
                                    append(" · $pathName")
                                }
                            }
                        }
                        downloadStatus == "downloading" -> {
                            append("⏳ 正在下载中...")
                        }
                        downloadStatus == "failed" -> {
                            val error = item.extraData?.get("downloadError") as? String
                            append("❌ 下载失败")
                            if (!error.isNullOrBlank()) {
                                append(" · $error")
                            }
                        }
                        else -> {
                            // 显示原始预览或视频链接信息
                            val originalPreview = item.preview ?: "视频链接"
                            append(originalPreview)
                            append(" · 未下载")
                        }
                    }
                    
                    // 添加格式信息
                    val format = item.extraData?.get("videoFormat") as? String
                    if (format != null && format != "UNKNOWN") {
                        append("\n格式: $format")
                    }
                }
            } else {
                item.preview ?: item.content.take(200)
            }
            previewText.text = previewContent
            previewText.visibility = if (previewText.text.isNotEmpty()) View.VISIBLE else View.GONE
            
            // 设置优先级徽章
            priorityBadge.text = when (item.priority) {
                Priority.HIGH -> "高"
                Priority.NORMAL -> "中"
                Priority.LOW -> "低"
            }
            priorityBadge.setBackgroundColor(item.collectionType.color)
            
            // 设置标签
            setupTags(tagsContainer, item)
            
            // 设置底部信息
            val bottomInfo = buildString {
                // 喜欢程度
                repeat(item.likeLevel) { append("⭐") }
                if (item.likeLevel < 5) {
                    repeat(5 - item.likeLevel) { append("☆") }
                }
                append("  ")
                
                // 加密状态
                if (item.isEncrypted) append("🔒 ")
                
                // 提醒状态
                if (item.reminderTime != null) append("⏰ ")
            }
            bottomInfoText.text = bottomInfo
            
            // 设置选择模式
            checkBox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            checkBox.isChecked = selectedIds.contains(item.id)
            
            // 设置点击事件
            cardView.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(item.id)
                } else {
                    onItemClick(item)
                }
            }
            
            cardView.setOnLongClickListener {
                if (!isSelectionMode) {
                    setSelectionMode(true)
                    toggleSelection(item.id)
                }
                onItemLongClick(item)
                true
            }
        }
        
        private fun setupTags(container: LinearLayout, item: UnifiedCollectionItem) {
            container.removeAllViews()
            
            // 添加自定义标签
            item.customTags.take(3).forEach { tag ->
                val tagView = createTagView(container.context, tag, false)
                container.addView(tagView)
            }
            
            // 添加情感标签
            if (item.emotionTag != EmotionTag.NEUTRAL) {
                val tagView = createTagView(container.context, item.emotionTag.displayName, true)
                container.addView(tagView)
            }
            
            // 添加完成状态
            if (item.completionStatus != CompletionStatus.NOT_STARTED) {
                val tagView = createTagView(container.context, item.completionStatus.displayName, true)
                container.addView(tagView)
            }
        }
        
        private fun createTagView(context: android.content.Context, text: String, isSpecial: Boolean): TextView {
            return TextView(context).apply {
                this.text = text
                textSize = 10f
                setPadding(8, 4, 8, 4)
                setBackgroundColor(if (isSpecial) 0xFFE0E0E0.toInt() else 0xFFF5F5F5.toInt())
                setTextColor(if (isSpecial) 0xFF666666.toInt() else 0xFF333333.toInt())
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 4
                }
                layoutParams = params
            }
        }
        
        private fun toggleSelection(id: String) {
            if (selectedIds.contains(id)) {
                selectedIds.remove(id)
            } else {
                selectedIds.add(id)
            }
            notifyItemChanged(adapterPosition)
            onItemSelected(selectedIds.toList())
        }
    }
    
    /**
     * 网格视图Holder
     */
    inner class GridViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: TextView = itemView.findViewById(R.id.collection_icon)
        private val titleText: TextView = itemView.findViewById(R.id.collection_title)
        private val sourceText: TextView = itemView.findViewById(R.id.collection_source)
        private val checkBox: CheckBox = itemView.findViewById(R.id.collection_checkbox)
        private val cardView: View = itemView.findViewById(R.id.collection_card)
        
        fun bind(item: UnifiedCollectionItem) {
            iconView.text = item.collectionType.icon
            titleText.text = item.title
            sourceText.text = item.getSourceDisplayText()
            
            checkBox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            checkBox.isChecked = selectedIds.contains(item.id)
            
            cardView.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(item.id)
                } else {
                    onItemClick(item)
                }
            }
        }
        
        private fun toggleSelection(id: String) {
            if (selectedIds.contains(id)) {
                selectedIds.remove(id)
            } else {
                selectedIds.add(id)
            }
            notifyItemChanged(adapterPosition)
            onItemSelected(selectedIds.toList())
        }
    }
    
    /**
     * 时间线标题Holder
     */
    inner class TimelineHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateText: TextView = itemView.findViewById(R.id.timeline_date)
        
        fun bind(date: String) {
            dateText.text = date
        }
    }
    
    /**
     * 时间线项Holder
     */
    inner class TimelineItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: TextView = itemView.findViewById(R.id.collection_icon)
        private val titleText: TextView = itemView.findViewById(R.id.collection_title)
        private val sourceText: TextView = itemView.findViewById(R.id.collection_source)
        private val timeLine: View = itemView.findViewById(R.id.timeline_line)
        private val timeDot: View = itemView.findViewById(R.id.timeline_dot)
        private val checkBox: CheckBox = itemView.findViewById(R.id.collection_checkbox)
        private val cardView: View = itemView.findViewById(R.id.collection_card)
        
        fun bind(item: UnifiedCollectionItem) {
            iconView.text = item.collectionType.icon
            titleText.text = item.title
            sourceText.text = item.getSourceDisplayText() + " · " + item.getFormattedCollectedTime()
            
            checkBox.visibility = if (isSelectionMode) View.VISIBLE else View.GONE
            checkBox.isChecked = selectedIds.contains(item.id)
            
            cardView.setOnClickListener {
                if (isSelectionMode) {
                    toggleSelection(item.id)
                } else {
                    onItemClick(item)
                }
            }
        }
        
        private fun toggleSelection(id: String) {
            if (selectedIds.contains(id)) {
                selectedIds.remove(id)
            } else {
                selectedIds.add(id)
            }
            notifyItemChanged(adapterPosition)
            onItemSelected(selectedIds.toList())
        }
    }
    
    companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_GRID = 1
        private const val VIEW_TYPE_TIMELINE_HEADER = 2
        private const val VIEW_TYPE_TIMELINE_ITEM = 3
    }
}

