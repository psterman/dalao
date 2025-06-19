package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.SearchHistory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class SearchHistoryAdapter : ListAdapter<SearchHistory, SearchHistoryAdapter.HistoryViewHolder>(HistoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_history, parent, false)
        return HistoryViewHolder(view)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val historyItem = getItem(position)
        holder.bind(historyItem)
    }

    class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val queryTextView: TextView = itemView.findViewById(R.id.query_text)
        private val timestampTextView: TextView = itemView.findViewById(R.id.timestamp_text)
        private val sourceTextView: TextView = itemView.findViewById(R.id.source_text)
        private val durationTextView: TextView = itemView.findViewById(R.id.duration_text)
        private val enginesTextView: TextView = itemView.findViewById(R.id.engines_text)

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        fun bind(history: SearchHistory) {
            queryTextView.text = history.query
            timestampTextView.text = dateFormat.format(Date(history.timestamp))
            sourceTextView.text = "来源: ${history.source}"
            durationTextView.text = "时长: ${formatDuration(history.durationInMillis)}"
            enginesTextView.text = "引擎: ${history.engines}"
        }

        private fun formatDuration(millis: Long): String {
            val seconds = TimeUnit.MILLISECONDS.toSeconds(millis)
            return if (seconds < 60) {
                "${seconds}s"
            } else {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
                "${minutes}m ${seconds % 60}s"
            }
        }
    }

    class HistoryDiffCallback : DiffUtil.ItemCallback<SearchHistory>() {
        override fun areItemsTheSame(oldItem: SearchHistory, newItem: SearchHistory): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: SearchHistory, newItem: SearchHistory): Boolean {
            return oldItem == newItem
        }
    }
} 