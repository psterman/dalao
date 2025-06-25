package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.SearchableSetting

class SettingsSearchResultAdapter(
    private var results: List<SearchableSetting>,
    private val onItemClick: (SearchableSetting) -> Unit
) : RecyclerView.Adapter<SettingsSearchResultAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.setting_title)
        val summary: TextView = view.findViewById(R.id.setting_summary)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_settings_search_result, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val setting = results[position]
        holder.title.text = setting.title
        holder.summary.text = setting.summary
        holder.itemView.setOnClickListener {
            onItemClick(setting)
        }
    }

    override fun getItemCount() = results.size

    fun updateData(newResults: List<SearchableSetting>) {
        results = newResults
        notifyDataSetChanged()
    }
} 