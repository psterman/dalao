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
        
        // 按分类排序：AI搜索引擎 -> 普通搜索引擎 -> 自定义搜索引擎
        val aiEngines = newEngines.filter { it.isAI }.sortedBy { it.displayName }
        val normalEngines = newEngines.filter { !it.isAI && !it.isCustom }.sortedBy { it.displayName }
        val customEngines = newEngines.filter { it.isCustom }.sortedBy { it.displayName }
        
        // 按顺序添加
        engines.addAll(aiEngines)
        engines.addAll(normalEngines)
        engines.addAll(customEngines)
        
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
            
            // 根据搜索引擎类型设置描述和样式
            when {
                engine.isAI -> {
                    engineDescription.text = "🤖 ${engine.description}"
                    engineDescription.setTextColor(engineDescription.context.getColor(R.color.ai_engine_color))
                }
                engine.isCustom -> {
                    engineDescription.text = "⭐ ${engine.description}"
                    engineDescription.setTextColor(engineDescription.context.getColor(R.color.custom_engine_color))
                }
                else -> {
                    engineDescription.text = engine.description
                    engineDescription.setTextColor(engineDescription.context.getColor(R.color.simple_mode_text_secondary_light))
                }
            }

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
