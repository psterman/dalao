package com.example.aifloatingball.video

import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R

/**
 * 播放列表适配器（支持拖拽排序和批量管理）
 * 
 * 功能：
 * - 拖拽排序
 * - 批量选择
 * - 批量删除
 * - 批量移动
 * 
 * @author AI Floating Ball
 */
class PlaylistAdapter(
    private var playlist: MutableList<VideoPlaylistManager.VideoPlayItem>,
    private val onItemClick: (VideoPlaylistManager.VideoPlayItem) -> Unit,
    private val onItemDelete: (VideoPlaylistManager.VideoPlayItem) -> Unit
) : RecyclerView.Adapter<PlaylistAdapter.ViewHolder>() {
    
    // 批量管理模式
    private var isSelectionMode = false
    private val selectedItems = mutableSetOf<Int>()
    
    // 拖拽回调
    private var onItemMoved: ((Int, Int) -> Unit)? = null
    
    /**
     * ViewHolder
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleText: TextView = view.findViewById(R.id.video_title)
        val durationText: TextView = view.findViewById(R.id.video_duration)
        val deleteButton: ImageButton = view.findViewById(R.id.delete_button)
        val checkbox: CheckBox = view.findViewById(R.id.selection_checkbox)
        val dragHandle: View = view.findViewById(R.id.drag_handle)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_playlist_video, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = playlist[position]
        
        holder.titleText.text = item.title
        holder.durationText.text = formatDuration(item.duration)
        
        // 批量管理模式
        if (isSelectionMode) {
            holder.checkbox.visibility = View.VISIBLE
            holder.checkbox.isChecked = selectedItems.contains(position)
            holder.deleteButton.visibility = View.GONE
            holder.dragHandle.visibility = View.GONE
        } else {
            holder.checkbox.visibility = View.GONE
            holder.deleteButton.visibility = View.VISIBLE
            holder.dragHandle.visibility = View.VISIBLE
        }
        
        // 点击事件
        holder.itemView.setOnClickListener {
            if (isSelectionMode) {
                toggleSelection(position)
                holder.checkbox.isChecked = selectedItems.contains(position)
            } else {
                onItemClick(item)
            }
        }
        
        // 长按进入批量管理模式
        holder.itemView.setOnLongClickListener {
            if (!isSelectionMode) {
                enterSelectionMode()
                toggleSelection(position)
                notifyDataSetChanged()
            }
            true
        }
        
        // 删除按钮
        holder.deleteButton.setOnClickListener {
            onItemDelete(item)
        }
        
        // 复选框
        holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectedItems.add(position)
            } else {
                selectedItems.remove(position)
            }
        }
    }
    
    override fun getItemCount(): Int = playlist.size
    
    /**
     * 切换选择状态
     */
    private fun toggleSelection(position: Int) {
        if (selectedItems.contains(position)) {
            selectedItems.remove(position)
        } else {
            selectedItems.add(position)
        }
    }
    
    /**
     * 进入批量管理模式
     */
    fun enterSelectionMode() {
        isSelectionMode = true
        selectedItems.clear()
        notifyDataSetChanged()
    }
    
    /**
     * 退出批量管理模式
     */
    fun exitSelectionMode() {
        isSelectionMode = false
        selectedItems.clear()
        notifyDataSetChanged()
    }
    
    /**
     * 是否在批量管理模式
     */
    fun isInSelectionMode(): Boolean = isSelectionMode
    
    /**
     * 获取选中的项
     */
    fun getSelectedItems(): List<VideoPlaylistManager.VideoPlayItem> {
        return selectedItems.map { playlist[it] }
    }
    
    /**
     * 全选
     */
    fun selectAll() {
        selectedItems.clear()
        selectedItems.addAll(playlist.indices)
        notifyDataSetChanged()
    }
    
    /**
     * 取消全选
     */
    fun deselectAll() {
        selectedItems.clear()
        notifyDataSetChanged()
    }
    
    /**
     * 删除选中的项
     */
    fun deleteSelected() {
        val itemsToDelete = getSelectedItems()
        playlist.removeAll(itemsToDelete)
        exitSelectionMode()
        notifyDataSetChanged()
    }
    
    /**
     * 更新播放列表
     */
    fun updatePlaylist(newPlaylist: List<VideoPlaylistManager.VideoPlayItem>) {
        playlist.clear()
        playlist.addAll(newPlaylist)
        notifyDataSetChanged()
    }
    
    /**
     * 移动项
     */
    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                playlist[i] = playlist[i + 1].also { playlist[i + 1] = playlist[i] }
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                playlist[i] = playlist[i - 1].also { playlist[i - 1] = playlist[i] }
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        onItemMoved?.invoke(fromPosition, toPosition)
    }
    
    /**
     * 设置项移动回调
     */
    fun setOnItemMovedListener(listener: (Int, Int) -> Unit) {
        onItemMoved = listener
    }
    
    /**
     * 格式化时长
     */
    private fun formatDuration(durationMs: Long): String {
        val seconds = durationMs / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        
        return when {
            hours > 0 -> String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60)
            else -> String.format("%d:%02d", minutes, seconds % 60)
        }
    }
}

/**
 * ItemTouchHelper 回调（拖拽排序）
 */
class PlaylistItemTouchHelperCallback(
    private val adapter: PlaylistAdapter
) : ItemTouchHelper.Callback() {
    
    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        val swipeFlags = 0 // 不支持滑动删除
        return makeMovementFlags(dragFlags, swipeFlags)
    }
    
    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        adapter.moveItem(viewHolder.adapterPosition, target.adapterPosition)
        return true
    }
    
    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // 不支持滑动删除
    }
    
    override fun isLongPressDragEnabled(): Boolean {
        return !adapter.isInSelectionMode()
    }
    
    override fun isItemViewSwipeEnabled(): Boolean {
        return false
    }
    
    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            viewHolder?.itemView?.alpha = 0.5f
        }
    }
    
    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        viewHolder.itemView.alpha = 1.0f
    }
    
    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
            viewHolder.itemView.translationY = dY
        }
    }
}
