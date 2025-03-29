package com.example.aifloatingball

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.model.SearchEngine
import java.util.Collections

class SearchEngineAdapter(
    private val engines: MutableList<SearchEngine>,
    private val enabledEngines: MutableSet<String>,
    private val onEngineStateChanged: (SearchEngine, Boolean) -> Unit
) : RecyclerView.Adapter<SearchEngineAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.engineIcon)
        val name: TextView = view.findViewById(R.id.engineName)
        val description: TextView = view.findViewById(R.id.engineDescription)
        val enableSwitch: CheckBox = view.findViewById(R.id.engineEnable)
        val dragHandle: ImageView = view.findViewById(R.id.dragHandle)
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
        holder.description.text = engine.description
        holder.enableSwitch.isChecked = engine.name in enabledEngines

        holder.enableSwitch.setOnCheckedChangeListener { _, isChecked ->
            onEngineStateChanged(engine, isChecked)
        }
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

    fun updateEnabledEngines(newEnabledEngines: Set<String>) {
        enabledEngines.clear()
        enabledEngines.addAll(newEnabledEngines)
        notifyDataSetChanged()
    }
} 