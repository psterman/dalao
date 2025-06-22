package com.example.aifloatingball.settings

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R

class AppAdapter(
    private val appList: List<AppItem>,
    private val onItemClick: (AppItem) -> Unit
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.app_list_item, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val appItem = appList[position]
        holder.bind(appItem)
        holder.itemView.setOnClickListener {
            onItemClick(appItem)
        }
    }

    override fun getItemCount(): Int = appList.size

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
        private val appName: TextView = itemView.findViewById(R.id.app_name)
        private val appCheckbox: CheckBox = itemView.findViewById(R.id.app_checkbox)

        fun bind(appItem: AppItem) {
            appIcon.setImageDrawable(appItem.icon)
            appName.text = appItem.name
            appCheckbox.isChecked = appItem.isSelected
        }
    }
} 