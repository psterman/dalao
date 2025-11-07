package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.TabGroup

/**
 * 标签页组列表适配器
 * 支持编辑、删除、置顶、拖动排序
 */
class TabGroupAdapter(
    private var groups: MutableList<TabGroup>,
    private val onGroupClick: (TabGroup) -> Unit,
    private val onGroupEdit: (TabGroup) -> Unit,
    private val onGroupDelete: (TabGroup) -> Unit,
    private val onGroupPin: (TabGroup) -> Unit,
    private val onGroupDrag: (fromPosition: Int, toPosition: Int) -> Unit
) : RecyclerView.Adapter<TabGroupAdapter.GroupViewHolder>() {
    
    class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val groupName: TextView = itemView.findViewById(R.id.group_name)
        val editButton: ImageButton = itemView.findViewById(R.id.btn_edit_group)
        val deleteButton: ImageButton = itemView.findViewById(R.id.btn_delete_group)
        val pinButton: ImageButton = itemView.findViewById(R.id.btn_pin_group)
        val dragHandle: View = itemView.findViewById(R.id.drag_handle)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tab_group, parent, false)
        return GroupViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = groups[position]
        
        holder.groupName.text = group.name
        
        // 置顶按钮图标
        val pinIcon = if (group.isPinned) {
            R.drawable.ic_pin_filled
        } else {
            R.drawable.ic_pin_outline
        }
        holder.pinButton.setImageResource(pinIcon)
        
        // 点击组名进入组
        holder.groupName.setOnClickListener {
            onGroupClick(group)
        }
        
        // 编辑按钮
        holder.editButton.setOnClickListener {
            onGroupEdit(group)
        }
        
        // 删除按钮
        holder.deleteButton.setOnClickListener {
            onGroupDelete(group)
        }
        
        // 置顶按钮
        holder.pinButton.setOnClickListener {
            onGroupPin(group)
        }
    }
    
    override fun getItemCount(): Int = groups.size
    
    /**
     * 更新组列表
     */
    fun updateGroups(newGroups: List<TabGroup>) {
        groups.clear()
        groups.addAll(newGroups)
        notifyDataSetChanged()
    }
    
    /**
     * 移动组位置（用于拖动排序）
     */
    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                groups[i] = groups[i + 1].also { groups[i + 1] = groups[i] }
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                groups[i] = groups[i - 1].also { groups[i - 1] = groups[i] }
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }
}

/**
 * 组拖动排序辅助类
 */
class GroupItemTouchHelperCallback(
    private val adapter: TabGroupAdapter
) : ItemTouchHelper.SimpleCallback(
    ItemTouchHelper.UP or ItemTouchHelper.DOWN,
    0
) {
    
    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val fromPosition = viewHolder.adapterPosition
        val toPosition = target.adapterPosition
        adapter.moveItem(fromPosition, toPosition)
        return true
    }
    
    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // 不支持滑动删除
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
}

