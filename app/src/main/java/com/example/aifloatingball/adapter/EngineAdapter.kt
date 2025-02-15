package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.AIEngine
import com.example.aifloatingball.R
import java.util.Collections

class EngineAdapter(private val engines: MutableList<AIEngine>) : 
    RecyclerView.Adapter<EngineAdapter.ViewHolder>() {
    
    interface OnItemLongClickListener {
        fun onItemLongClick(position: Int)
    }
    
    private var onItemLongClickListener: OnItemLongClickListener? = null
    
    fun setOnItemLongClickListener(listener: OnItemLongClickListener) {
        onItemLongClickListener = listener
    }
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.engine_icon)
        val name: TextView = view.findViewById(R.id.engine_name)
        val dragHandle: ImageView = view.findViewById(R.id.drag_handle)
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_engine, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val engine = engines[position]
        holder.icon.setImageResource(engine.iconResId)
        holder.name.text = engine.name
        
        holder.itemView.setOnLongClickListener {
            onItemLongClickListener?.onItemLongClick(position)
            true
        }
    }
    
    override fun getItemCount() = engines.size
    
    fun onItemMove(fromPosition: Int, toPosition: Int) {
        Collections.swap(engines, fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
    }
    
    fun moveToTop(position: Int) {
        if (position > 0) {
            val newEngines = engines.toMutableList()
            val engine = newEngines.removeAt(position)
            newEngines.add(0, engine)
            
            engines.clear()
            engines.addAll(newEngines)
            
            notifyDataSetChanged()
        }
    }
    
    fun moveToBottom(position: Int) {
        if (position < engines.size - 1) {
            val newEngines = engines.toMutableList()
            val engine = newEngines.removeAt(position)
            newEngines.add(engine)
            
            engines.clear()
            engines.addAll(newEngines)
            
            notifyDataSetChanged()
        }
    }
    
    fun removeEngine(position: Int) {
        if (engines.size > 1) {
            engines.removeAt(position)
            notifyItemRemoved(position)
            
            notifyDataSetChanged()
        }
    }
    
    fun getEngines(): List<AIEngine> = engines.toList()
} 