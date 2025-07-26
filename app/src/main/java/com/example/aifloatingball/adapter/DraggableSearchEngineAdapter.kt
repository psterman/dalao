package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.SearchEngine
import com.example.aifloatingball.utils.FaviconLoader
import java.util.Collections

class DraggableSearchEngineAdapter(
    private var engines: MutableList<SearchEngine>,
    private val onEngineClick: (SearchEngine) -> Unit,
    private val onEngineReorder: (List<SearchEngine>) -> Unit
) : RecyclerView.Adapter<DraggableSearchEngineAdapter.ViewHolder>() {

    private var itemTouchHelper: ItemTouchHelper? = null

    fun attachToRecyclerView(recyclerView: RecyclerView) {
        val callback = ItemTouchHelperCallback()
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper?.attachToRecyclerView(recyclerView)
    }

    fun updateEngines(newEngines: List<SearchEngine>) {
        engines.clear()
        engines.addAll(newEngines)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_draggable_search_engine, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(engines[position])
    }

    override fun getItemCount(): Int = engines.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val engineIcon: ImageView = itemView.findViewById(R.id.engine_icon)
        private val engineName: TextView = itemView.findViewById(R.id.engine_name)
        private val engineDescription: TextView = itemView.findViewById(R.id.engine_description)
        private val dragHandle: ImageView = itemView.findViewById(R.id.drag_handle)

        fun bind(engine: SearchEngine) {
            engineName.text = engine.displayName
            engineDescription.text = engine.description

            // 使用FaviconLoader加载搜索引擎图标
            FaviconLoader.loadIcon(
                engineIcon,
                engine.url,
                engine.iconResId
            )

            // 设置点击事件
            itemView.setOnClickListener {
                onEngineClick(engine)
            }

            // 设置拖动手柄
            dragHandle.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    itemTouchHelper?.startDrag(this)
                }
                false
            }
        }
    }

    private inner class ItemTouchHelperCallback : ItemTouchHelper.Callback() {
        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
            return makeMovementFlags(dragFlags, 0)
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val fromPosition = viewHolder.adapterPosition
            val toPosition = target.adapterPosition

            Collections.swap(engines, fromPosition, toPosition)
            notifyItemMoved(fromPosition, toPosition)
            
            // 通知外部排序变化
            onEngineReorder(engines.toList())
            
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            // 不支持滑动删除
        }

        override fun isLongPressDragEnabled(): Boolean = false
        override fun isItemViewSwipeEnabled(): Boolean = false
    }
}
