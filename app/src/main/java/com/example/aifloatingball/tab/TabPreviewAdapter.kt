package com.example.aifloatingball.tab

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R

class TabPreviewAdapter(
    private val onTabSelected: (Int) -> Unit
) : RecyclerView.Adapter<TabPreviewAdapter.TabPreviewViewHolder>() {
    
    private var tabs: List<WebViewTab> = emptyList()
    private var currentPosition: Int = 0

    fun updateTabs(newTabs: List<WebViewTab>, currentPos: Int) {
        tabs = newTabs
        currentPosition = currentPos
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabPreviewViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tab_preview, parent, false)
        return TabPreviewViewHolder(view)
    }

    override fun onBindViewHolder(holder: TabPreviewViewHolder, position: Int) {
        val tab = tabs[position]
        holder.bind(tab, position == currentPosition)
        
        holder.itemView.setOnClickListener {
            onTabSelected(position)
        }
        
        holder.closeButton.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) {
                removeTab(pos)
            }
        }
    }

    override fun getItemCount(): Int = tabs.size

    private fun removeTab(position: Int) {
        if (position < 0 || position >= tabs.size) return
        
        val newTabs = tabs.toMutableList()
        newTabs.removeAt(position)
        updateTabs(newTabs, if (position == currentPosition) position - 1 else currentPosition)
    }

    class TabPreviewViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.tab_title)
        private val favicon: ImageView = itemView.findViewById(R.id.tab_favicon)
        val closeButton: ImageView = itemView.findViewById(R.id.tab_close)

        fun bind(tab: WebViewTab, isSelected: Boolean) {
            titleText.text = tab.title
            // TODO: 加载favicon
            
            itemView.isSelected = isSelected
            itemView.alpha = if (isSelected) 1.0f else 0.7f
        }
    }
} 