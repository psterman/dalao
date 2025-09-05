package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.AppInfo

class RecentAppAdapter(
    private var apps: List<AppInfo>,
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppRemove: (AppInfo) -> Unit
) : RecyclerView.Adapter<RecentAppAdapter.RecentAppViewHolder>() {

    class RecentAppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
        val appName: TextView = itemView.findViewById(R.id.app_name)
        val removeButton: ImageView = itemView.findViewById(R.id.remove_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentAppViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_recent_app, parent, false)
        return RecentAppViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecentAppViewHolder, position: Int) {
        val app = apps[position]
        
        // 设置图标，如果图标为null则使用默认图标
        if (app.icon != null) {
            holder.appIcon.setImageDrawable(app.icon)
        } else {
            // 使用默认应用图标
            holder.appIcon.setImageResource(android.R.drawable.sym_def_app_icon)
        }
        
        holder.appName.text = app.label
        
        holder.itemView.setOnClickListener {
            onAppClick(app)
        }
        
        holder.removeButton.setOnClickListener {
            onAppRemove(app)
        }
    }

    override fun getItemCount(): Int = apps.size

    fun updateApps(newApps: List<AppInfo>) {
        apps = newApps
        notifyDataSetChanged()
    }
}
