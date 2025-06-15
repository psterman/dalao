package com.example.aifloatingball

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.model.AppSearchConfig
import com.example.aifloatingball.model.AppSearchSettings
import java.util.*

class AppSearchSettingsActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppSearchAdapter
    private lateinit var appSearchSettings: AppSearchSettings
    private lateinit var itemTouchHelper: ItemTouchHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_search_settings)

        // 初始化设置管理器
        appSearchSettings = AppSearchSettings.getInstance(this)

        // 设置标题
            title = "应用搜索设置"

        // 初始化RecyclerView
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        // 创建适配器
        adapter = AppSearchAdapter(appSearchSettings.getAppConfigs().toMutableList(),
            onToggleEnabled = { config, enabled ->
            // 切换应用启用状态
            appSearchSettings.toggleAppEnabled(config.appId, enabled)
            // 发送广播通知服务更新
            sendBroadcast(android.content.Intent("com.example.aifloatingball.ACTION_UPDATE_APP_SEARCH"))
            },
            onStartDrag = { viewHolder ->
                itemTouchHelper.startDrag(viewHolder)
        },
        getDomainForApp = { appId -> getDomainForApp(appId) }
        )
        recyclerView.adapter = adapter

        // 设置拖拽排序
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                
                // 更新列表顺序
                if (fromPos != RecyclerView.NO_POSITION && toPos != RecyclerView.NO_POSITION) {
                Collections.swap(adapter.apps, fromPos, toPos)
                adapter.notifyItemMoved(fromPos, toPos)
                }
                
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // 不实现滑动删除
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                // 拖拽结束后，更新所有应用的顺序
                adapter.apps.forEachIndexed { index, config ->
                    appSearchSettings.reorderApp(config.appId, index + 1)
                }
                // 发送广播通知服务更新
                sendBroadcast(android.content.Intent("com.example.aifloatingball.ACTION_UPDATE_APP_SEARCH"))
            }
        }
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun getDomainForApp(appId: String): String {
        return when (appId) {
            "wechat" -> "weixin.qq.com"
            "taobao" -> "taobao.com"
            "pdd" -> "pinduoduo.com"
            "douyin" -> "douyin.com"
            "xiaohongshu" -> "xiaohongshu.com"
            else -> ""
        }
    }
}

@SuppressLint("ClickableViewAccessibility")
class AppSearchAdapter(
    val apps: MutableList<AppSearchConfig>,
    private val onToggleEnabled: (AppSearchConfig, Boolean) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val getDomainForApp: (String) -> String
    ) : RecyclerView.Adapter<AppSearchAdapter.ViewHolder>() {

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val iconView: ImageView = view.findViewById(R.id.app_icon)
        val nameView: TextView = view.findViewById(R.id.app_name)
        val switchView: Switch = view.findViewById(R.id.app_switch)
        val dragHandle: ImageView = view.findViewById(R.id.drag_handle)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_search_setting, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        
        holder.iconView.setImageResource(app.iconResId)
        holder.nameView.text = app.appName
        
        val domain = getDomainForApp(app.appId)
        
        if (domain.isNotEmpty()) {
            com.example.aifloatingball.utils.FaviconLoader.loadIcon(holder.iconView, "https://$domain", app.iconResId)
        }
        
        holder.switchView.setOnCheckedChangeListener(null)
        holder.switchView.isChecked = app.isEnabled
        holder.switchView.setOnCheckedChangeListener { _, isChecked ->
            onToggleEnabled(app, isChecked)
        }
        
        holder.dragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                onStartDrag(holder)
            }
            true // Consume the touch event
        }
    }

    override fun getItemCount() = apps.size
} 