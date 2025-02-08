package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import java.util.Collections

class EngineAdapter(private val engines: MutableList<SearchEngine>) : 
    RecyclerView.Adapter<EngineAdapter.ViewHolder>() {
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val engineName: TextView = view.findViewById(R.id.engine_name)
        val dragHandle: ImageView = view.findViewById(R.id.drag_handle)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.engine_item, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val engine = engines[position]
        holder.engineName.text = engine.name
    }
    
    override fun getItemCount() = engines.size
    
    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(engines, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(engines, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }
    
    fun getEngines(): List<SearchEngine> = engines.toList()
    
    data class SearchEngine(
        val name: String,
        val url: String
    )
} 