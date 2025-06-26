package com.example.aifloatingball.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.AppInfo

class AppSearchAdapter(
    private var apps: List<AppInfo>,
    private val isHorizontal: Boolean = false,
    private val clickListener: (AppInfo) -> Unit
) : RecyclerView.Adapter<AppSearchAdapter.ViewHolder>() {

    fun updateData(newApps: List<AppInfo>) {
        this.apps = newApps
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutId = if (isHorizontal) {
            R.layout.item_app_search_horizontal
        } else {
            R.layout.item_app_search
        }
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return ViewHolder(view, isHorizontal)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.bind(app, clickListener)
    }

    override fun getItemCount(): Int = apps.size

    class ViewHolder(itemView: View, private val isHorizontal: Boolean) : RecyclerView.ViewHolder(itemView) {
        private val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
        private val appName: TextView? = if (!isHorizontal) itemView.findViewById(R.id.app_name) else null

        fun bind(app: AppInfo, clickListener: (AppInfo) -> Unit) {
            appIcon.setImageDrawable(app.icon)
            if (!isHorizontal) {
                appName?.text = app.label
            }
            itemView.setOnClickListener { clickListener(app) }
        }
    }
} 