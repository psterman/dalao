package com.example.aifloatingball.service

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R

class SearchCategoryAdapter(
    private val categories: List<DynamicIslandService.SearchCategory>,
    private val onEngineClick: (DynamicIslandService.SearchEngine) -> Unit
) : RecyclerView.Adapter<SearchCategoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.category_title)
        val recyclerView: RecyclerView = view.findViewById(R.id.category_recycler_view)
        var lastAnimatedPosition = -1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.search_engine_category_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val category = categories[position]
        holder.title.text = category.title

        // Setup nested RecyclerView
        holder.recyclerView.setHasFixedSize(true)
        val engineAdapter = SearchEngineAdapter(category.engines, onEngineClick)
        holder.recyclerView.adapter = engineAdapter

        // Staggered animation for the row
        if (holder.absoluteAdapterPosition > holder.lastAnimatedPosition) {
            val animation = AnimationUtils.loadAnimation(holder.itemView.context, R.anim.fade_in_up)
            holder.itemView.startAnimation(animation)
            holder.lastAnimatedPosition = holder.absoluteAdapterPosition
        }
    }

    override fun getItemCount() = categories.size
} 