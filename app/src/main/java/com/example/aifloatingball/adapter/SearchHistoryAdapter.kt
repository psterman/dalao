package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import java.text.SimpleDateFormat
import java.util.*

class SearchHistoryAdapter(
    private val onDelete: (Map<String, Any>) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val items = mutableListOf<Any>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ITEM = 1
    }

    // --- ViewHolder for Header ---
    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val headerTitle: TextView = view.findViewById(R.id.header_title)
    }

    // --- ViewHolder for History Item ---
    class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val keyword: TextView = view.findViewById(R.id.search_keyword)
        val details: TextView = view.findViewById(R.id.search_details)
        val source: TextView = view.findViewById(R.id.search_source)
        val deleteButton: TextView = view.findViewById(R.id.delete_button)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is String -> TYPE_HEADER
            is Map<*, *> -> TYPE_ITEM
            else -> throw IllegalArgumentException("Invalid type of data at position $position")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item_search_history_header, parent, false)
            HeaderViewHolder(view)
            } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item_search_history, parent, false)
            ItemViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is HeaderViewHolder -> {
                holder.headerTitle.text = items[position] as String
            }
            is ItemViewHolder -> {
                @Suppress("UNCHECKED_CAST")
                val item = items[position] as Map<String, Any>
                holder.keyword.text = item["keyword"] as? String ?: "N/A"

                val timestamp = (item["timestamp"] as? Number)?.toLong() ?: 0L
                val duration = (item["duration"] as? Number)?.toLong() ?: 0L
                val source = item["source"] as? String ?: "未知来源"

                val date = if (timestamp > 0) dateFormat.format(Date(timestamp)) else "时间未知"
                holder.details.text = "时间: $date | 时长: ${duration}s"
                holder.source.text = "来源: $source"

                holder.deleteButton.setOnClickListener {
                    onDelete(item)
                }
            }
        }
    }

    override fun getItemCount() = items.size

    fun updateData(groupedHistory: Map<String, List<Map<String, Any>>>) {
        items.clear()
        // Sort groups by a predefined order or alphabetically
        val sortedGroups = groupedHistory.keys.sorted() 
        sortedGroups.forEach { source ->
            items.add(source) // Add header
            val historyItems = groupedHistory[source]
            if (historyItems != null) {
                items.addAll(historyItems) // Add items for this group
            }
        }
        notifyDataSetChanged()
    }
} 