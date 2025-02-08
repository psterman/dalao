package com.example.aifloatingball.adapter

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class DragCallback(private val adapter: EngineAdapter) : ItemTouchHelper.Callback() {
    
    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        return makeMovementFlags(dragFlags, 0)
    }
    
    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val fromPosition = viewHolder.bindingAdapterPosition
        val toPosition = target.bindingAdapterPosition
        if (fromPosition != RecyclerView.NO_POSITION && toPosition != RecyclerView.NO_POSITION) {
            adapter.moveItem(fromPosition, toPosition)
            return true
        }
        return false
    }
    
    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // 不支持滑动删除
    }
    
    override fun isLongPressDragEnabled(): Boolean {
        return true
    }
} 