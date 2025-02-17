package com.example.aifloatingball

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SearchEngineAdapter(private var engines: List<SearchEngine>) :
    RecyclerView.Adapter<SearchEngineAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.engine_icon)
        val name: TextView = view.findViewById(R.id.engine_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_search_engine, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val engine = engines[position]
        holder.icon.setImageResource(engine.iconResId)
        holder.name.text = engine.name

        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                putExtra("ENGINE_NAME", engine.name)
                putExtra("ENGINE_URL", engine.url)
                putExtra("ENGINE_ICON", engine.iconResId)
            }
            context.startService(intent)
            if (context is SearchActivity) {
                context.finish()
            }
        }
    }

    override fun getItemCount() = engines.size

    fun updateEngines(newEngines: List<SearchEngine>) {
        engines = newEngines
        notifyDataSetChanged()
    }
} 