package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.SearchHistory
import java.text.SimpleDateFormat
import java.util.*

class SearchHistoryAdapter(
    private val onDelete: (SearchHistory) -> Unit
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
            is SearchHistory -> TYPE_ITEM
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
                val item = items[position] as SearchHistory
                holder.keyword.text = item.query

                val durationInSeconds = item.durationInMillis / 1000
                val date = dateFormat.format(Date(item.timestamp))

                holder.details.text = "时间: $date | 时长: ${durationInSeconds}s"
                holder.source.text = "来源: ${item.source}"

                holder.deleteButton.setOnClickListener {
                    onDelete(item)
                }
            }
        }
    }

    override fun getItemCount() = items.size

    fun updateData(groupedHistory: Map<String, List<SearchHistory>>) {
        items.clear()
        val sortedGroups = groupedHistory.keys.sorted() 
        sortedGroups.forEach { source ->
            items.add(source) // Add header
            val historyItems = groupedHistory[source]
            if (historyItems != null) {
                items.addAll(historyItems)
            }
        }
        notifyDataSetChanged()
    }
} 