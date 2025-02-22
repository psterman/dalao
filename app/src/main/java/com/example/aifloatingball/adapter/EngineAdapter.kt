package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.SearchEngine

class EngineAdapter(
    private val engines: List<SearchEngine>,
    private val onEngineClick: (SearchEngine) -> Unit
) : RecyclerView.Adapter<EngineAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val engineIcon: ImageView = view.findViewById(R.id.engine_icon)
        val engineName: TextView = view.findViewById(R.id.engine_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_engine, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val engine = engines[position]
        holder.engineIcon.setImageResource(engine.iconResId)
        holder.engineName.text = engine.name
        holder.itemView.setOnClickListener { onEngineClick(engine) }
    }

    override fun getItemCount() = engines.size
} 