package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.SearchEngine

class SearchEngineAdapter(
    private val engines: List<SearchEngine>,
    private val onEngineSelected: (SearchEngine) -> Unit
) : RecyclerView.Adapter<SearchEngineAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.engine_icon)
        val name: TextView = view.findViewById(R.id.engine_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.layout_search_engine_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val engine = engines[position]
        holder.icon.setImageResource(engine.iconResId)
        holder.name.text = engine.name
        holder.itemView.setOnClickListener { onEngineSelected(engine) }
    }

    override fun getItemCount() = engines.size
} 