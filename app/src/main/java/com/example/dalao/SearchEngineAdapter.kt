package com.example.dalao

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.BaseSearchEngine
import com.example.aifloatingball.model.AISearchEngine

class SearchEngineAdapter(
    private val context: Context,
    private val engines: List<BaseSearchEngine>
) : RecyclerView.Adapter<SearchEngineAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.engine_icon)
        val name: TextView = view.findViewById(R.id.engine_name)
        val description: TextView = view.findViewById(R.id.engine_description)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context)
            .inflate(R.layout.item_search_engine, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val engine = engines[position]
        
        holder.icon.setImageResource(engine.iconResId)
        holder.name.text = engine.name
        
        holder.description.text = engine.description
        holder.description.visibility = if (engine.description.isNotEmpty()) View.VISIBLE else View.GONE

        // 为AI搜索引擎添加标记
        if (engine is AISearchEngine) {
            holder.name.text = "${engine.name} (AI)"
        }
    }

    override fun getItemCount(): Int = engines.size
} 