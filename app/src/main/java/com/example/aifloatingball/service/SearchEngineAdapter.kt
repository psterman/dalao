package com.example.aifloatingball.service

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.databinding.SearchEngineItemBinding

class SearchEngineAdapter(
    private val searchEngines: List<DynamicIslandService.SearchEngine>,
    private val onItemClick: (DynamicIslandService.SearchEngine) -> Unit
) : RecyclerView.Adapter<SearchEngineAdapter.ViewHolder>() {

    class ViewHolder(val binding: SearchEngineItemBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = SearchEngineItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val engine = searchEngines[position]
        // 使用FaviconLoader加载图标
        com.example.aifloatingball.utils.FaviconLoader.loadIcon(
            holder.binding.searchEngineIcon,
            engine.searchUrl,
            engine.iconResId
        )
        holder.binding.searchEngineName.text = engine.name
        holder.binding.searchEngineDescription.text = engine.description
        holder.itemView.setOnClickListener {
            onItemClick(engine)
        }
    }

    override fun getItemCount() = searchEngines.size
} 