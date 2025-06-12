package com.example.aifloatingball.service

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R

class SearchEngineAdapter(
    private val searchEngines: List<DynamicIslandService.SearchEngine>,
    private val onItemClick: (DynamicIslandService.SearchEngine) -> Unit
) : RecyclerView.Adapter<SearchEngineAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.search_engine_icon)
        val name: TextView = view.findViewById(R.id.search_engine_name)
        val description: TextView = view.findViewById(R.id.search_engine_description)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.search_engine_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val engine = searchEngines[position]
        holder.icon.setImageResource(engine.iconResId)
        holder.name.text = engine.name
        holder.description.text = engine.description
        holder.itemView.setOnClickListener {
            onItemClick(engine)
        }
    }

    override fun getItemCount() = searchEngines.size
} 