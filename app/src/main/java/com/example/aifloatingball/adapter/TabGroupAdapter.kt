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
    private val onGroupDrag: (fromPosition: Int, toPosition: Int) -> Unit,
    private val onGroupSecurity: (TabGroup) -> Unit = {}, // 密码设置回调
    private val onGroupVisibility: (TabGroup) -> Unit = {}, // 隐藏/显示回调
    private val isUnlockMode: Boolean = false, // 是否处于解锁模式（显示隐藏组）
    private val unlockedGroupIds: Set<String> = emptySet() // 已解锁的组ID集合
) : RecyclerView.Adapter<TabGroupAdapter.GroupViewHolder>() {
    
    class GroupViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val groupName: TextView = itemView.findViewById(R.id.group_name)
        val editButton: ImageButton = itemView.findViewById(R.id.btn_edit_group)
        val deleteButton: ImageButton = itemView.findViewById(R.id.btn_delete_group)
        val pinButton: ImageButton = itemView.findViewById(R.id.btn_pin_group)
        val securityButton: ImageButton = itemView.findViewById(R.id.btn_security_group)
        val visibilityButton: ImageButton = itemView.findViewById(R.id.btn_visibility_group)
        val dragHandle: View = itemView.findViewById(R.id.drag_handle)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tab_group, parent, false)
        return GroupViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = groups[position]
        
        // 🔧 修复：处理组名显示逻辑
        // 1. 未加密的组：正常显示名称
        // 2. 加密的组（有密码）：未解锁时显示"***"，解锁后显示真实名称
        val isGroupUnlocked = unlockedGroupIds.contains(group.id)
        val hasPassword = group.passwordHash != null
        
        if (hasPassword && !isGroupUnlocked) {
            // 有密码但未解锁的组，显示"***"
            holder.groupName.text = "***"
            // 使用次要文本颜色
            val typedValue = android.util.TypedValue()
            holder.groupName.context.theme.resolveAttribute(android.R.attr.textColorSecondary, typedValue, true)
            holder.groupName.setTextColor(typedValue.data)
        } else {
            // 正常显示组名（无密码的组，或已解锁的加密组）
            holder.groupName.text = group.name
            // 使用主要文本颜色
            val typedValue = android.util.TypedValue()
            holder.groupName.context.theme.resolveAttribute(android.R.attr.textColorPrimary, typedValue, true)
            holder.groupName.setTextColor(typedValue.data)
        }
        
        // 置顶按钮图标
        val pinIcon = if (group.isPinned) {
            R.drawable.ic_pin_filled
        } else {
            R.drawable.ic_pin_outline
        }
        holder.pinButton.setImageResource(pinIcon)
        
        // 隐藏/显示按钮图标（眼睛图标）
        val visibilityIcon = if (group.isHidden) {
            R.drawable.ic_visibility_off // 闭眼 = 隐藏
        } else {
            R.drawable.ic_visibility // 睁眼 = 显示
        }
        holder.visibilityButton.setImageResource(visibilityIcon)
        
        // 密码按钮图标（如果有密码显示锁定图标）
        holder.securityButton.setImageResource(R.drawable.ic_security)
        
        // 如果组未解锁，禁用编辑、删除、置顶按钮
        val isActionEnabled = !isUnlockMode || isGroupUnlocked || (!group.isHidden && group.passwordHash == null)
        holder.editButton.isEnabled = isActionEnabled
        holder.deleteButton.isEnabled = isActionEnabled
        holder.pinButton.isEnabled = isActionEnabled
        holder.editButton.alpha = if (isActionEnabled) 1.0f else 0.5f
        holder.deleteButton.alpha = if (isActionEnabled) 1.0f else 0.5f
        holder.pinButton.alpha = if (isActionEnabled) 1.0f else 0.5f
        
        // 点击组名：如果未解锁，触发解锁流程；如果已解锁或非解锁模式，切换到该组
        holder.groupName.setOnClickListener {
            onGroupClick(group)
        }
        
        // 隐藏/显示按钮
        holder.visibilityButton.setOnClickListener {
            onGroupVisibility(group)
        }
        
        // 密码设置按钮
        holder.securityButton.setOnClickListener {
            if (isUnlockMode && (group.isHidden || group.passwordHash != null) && !isGroupUnlocked) {
                // 未解锁的组，需要先解锁才能设置密码
                onGroupClick(group) // 触发解锁流程
            } else {
                onGroupSecurity(group)
            }
        }
        
        // 编辑按钮
        holder.editButton.setOnClickListener {
            if (isActionEnabled) {
                onGroupEdit(group)
            }
        }
        
        // 删除按钮
        holder.deleteButton.setOnClickListener {
            if (isActionEnabled) {
                onGroupDelete(group)
            }
        }
        
        // 置顶按钮
        holder.pinButton.setOnClickListener {
            if (isActionEnabled) {
                onGroupPin(group)
            }
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

