package com.example.aifloatingball.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.AppSelectionHistoryManager
import com.example.aifloatingball.R

/**
 * 应用选择对话框适配器
 */
class AppSelectionDialogAdapter(
    private val context: Context,
    private val apps: List<AppSelectionHistoryManager.AppSelectionItem>,
    private val onAppClick: (AppSelectionHistoryManager.AppSelectionItem) -> Unit
) : RecyclerView.Adapter<AppSelectionDialogAdapter.AppViewHolder>() {

    class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val appIcon: ImageView = itemView.findViewById(R.id.app_icon)
        val appName: TextView = itemView.findViewById(R.id.app_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_app_selection_dialog, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = apps[position]
        
        // 设置应用名称
        holder.appName.text = app.appName
        
        // 获取并设置应用图标
        try {
            val drawable = if (isAppInstalled(app.packageName)) {
                context.packageManager.getApplicationIcon(app.packageName)
            } else {
                // 如果应用未安装，使用默认图标
                androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_search)
            }
            holder.appIcon.setImageDrawable(drawable)
        } catch (e: Exception) {
            // 如果获取图标失败，使用默认图标
            holder.appIcon.setImageResource(R.drawable.ic_search)
        }
        
        // 设置点击事件
        holder.itemView.setOnClickListener {
            onAppClick(app)
        }
        
        // 添加点击动画效果
        holder.itemView.setOnTouchListener { view, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    view.animate().scaleX(0.95f).scaleY(0.95f).setDuration(100).start()
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()
                }
            }
            false
        }
    }

    override fun getItemCount(): Int = apps.size

    /**
     * 检查应用是否已安装
     */
    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: Exception) {
            false
        }
    }
}
