package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.MenuItem
import com.example.aifloatingball.model.MenuCategory

class MenuSettingsAdapter(
    private val items: MutableList<MenuItem>
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private companion object {
        const val TYPE_HEADER = 0
        const val TYPE_ITEM = 1
    }

    private val sections = mutableListOf<Any>()

    init {
        // 初始化时对项目进行分类
        organizeSections()
    }

    private fun organizeSections() {
        sections.clear()
        
        // AI搜索引擎分类
        val aiItems = items.filter { it.category == MenuCategory.AI_SEARCH }
        if (aiItems.isNotEmpty()) {
            sections.add("AI 搜索引擎")
            sections.addAll(aiItems)
        }

        // 普通搜索引擎分类
        val normalItems = items.filter { it.category == MenuCategory.NORMAL_SEARCH }
        if (normalItems.isNotEmpty()) {
            sections.add("普通搜索引擎")
            sections.addAll(normalItems)
        }

        // 功能分类
        val functionItems = items.filter { it.category == MenuCategory.FUNCTION }
        if (functionItems.isNotEmpty()) {
            sections.add("功能")
            sections.addAll(functionItems)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (sections[position] is String) TYPE_HEADER else TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_category_header, parent, false)
                HeaderViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_menu_setting, parent, false)
                ItemViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> {
                holder.bind(sections[position] as String)
            }
            is ItemViewHolder -> {
                holder.bind(sections[position] as MenuItem)
            }
        }
    }

    override fun getItemCount() = sections.size

    fun moveItem(fromPosition: Int, toPosition: Int) {
        // 确保不能跨分类移动
        if (getItemViewType(fromPosition) == TYPE_ITEM && 
            getItemViewType(toPosition) == TYPE_ITEM) {
            val fromItem = sections[fromPosition] as MenuItem
            val toItem = sections[toPosition] as MenuItem
            
            // 只允许在同一分类内移动
            if (fromItem.category == toItem.category) {
                sections.removeAt(fromPosition)
                sections.add(toPosition, fromItem)
                notifyItemMoved(fromPosition, toPosition)
            }
        }
    }

    fun getSelectedItems(): List<MenuItem> {
        return sections.filterIsInstance<MenuItem>()
            .filter { it.isEnabled }
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val textView = view as TextView

        fun bind(title: String) {
            textView.text = title
        }
    }

    class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val checkbox: CheckBox = view.findViewById(R.id.checkbox)
        private val icon: ImageView = view.findViewById(R.id.icon)
        private val name: TextView = view.findViewById(R.id.name)
        private val dragHandle: ImageView = view.findViewById(R.id.drag_handle)

        fun bind(item: MenuItem) {
            checkbox.isChecked = item.isEnabled
            icon.setImageResource(item.iconRes)
            name.text = item.name
            
            checkbox.setOnCheckedChangeListener { _, isChecked ->
                item.copy(isEnabled = isChecked)
            }
        }
    }
} 