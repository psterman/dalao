package com.example.aifloatingball.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.MotionEventCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.R
import com.example.aifloatingball.model.AppSearchConfig
import com.example.aifloatingball.model.AppSearchSettings
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.Collections

class AppSearchSettingsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var appSearchAdapter: AppSearchAdapter
    private lateinit var appSearchSettings: AppSearchSettings
    private lateinit var itemTouchHelper: ItemTouchHelper
    private var appConfigs: MutableList<AppSearchConfig> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            android.util.Log.d("AppSearchSettings", "开始创建应用搜索设置Activity")

            setContentView(R.layout.activity_app_search_settings)
            android.util.Log.d("AppSearchSettings", "成功设置内容视图")

            val toolbar: androidx.appcompat.widget.Toolbar? = findViewById(R.id.toolbar)
            if (toolbar == null) {
                android.util.Log.e("AppSearchSettings", "找不到toolbar控件")
                android.widget.Toast.makeText(this, "界面加载失败：找不到标题栏", android.widget.Toast.LENGTH_LONG).show()
                finish()
                return
            }

            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "应用内搜索管理"
            android.util.Log.d("AppSearchSettings", "成功设置工具栏")

            appSearchSettings = AppSearchSettings.getInstance(this)
            android.util.Log.d("AppSearchSettings", "成功初始化AppSearchSettings")

            val recyclerViewView = findViewById<RecyclerView>(R.id.app_search_recycler_view)
            if (recyclerViewView == null) {
                android.util.Log.e("AppSearchSettings", "找不到RecyclerView控件")
                android.widget.Toast.makeText(this, "界面加载失败：找不到列表控件", android.widget.Toast.LENGTH_LONG).show()
                finish()
                return
            }

            recyclerView = recyclerViewView
            android.util.Log.d("AppSearchSettings", "成功初始化RecyclerView")

            loadAppConfigs()
            android.util.Log.d("AppSearchSettings", "应用搜索设置Activity创建完成")
        } catch (e: Exception) {
            android.util.Log.e("AppSearchSettings", "创建应用搜索设置Activity失败", e)
            android.widget.Toast.makeText(this, "加载应用搜索设置失败: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            finish()
        }

        appSearchAdapter = AppSearchAdapter(this, appConfigs, { config, isEnabled ->
            config.isEnabled = isEnabled
            // No need to save here, will save on drag release
        }, object : OnStartDragListener {
            override fun onStartDrag(viewHolder: RecyclerView.ViewHolder) {
                itemTouchHelper.startDrag(viewHolder)
            }
        })
        recyclerView.adapter = appSearchAdapter

        val callback = AppSearchItemTouchHelperCallback(appSearchAdapter)
        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }
    
    override fun onPause() {
        super.onPause()
        // Save the final order when the activity is paused or closed
        appSearchSettings.saveAppConfigs(appConfigs)
    }
    
    private fun loadAppConfigs() {
        // Load, sort by order, and then update the list
        appConfigs.clear()
        appConfigs.addAll(appSearchSettings.getAppConfigs().sortedBy { it.order })
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    interface OnStartDragListener {
        fun onStartDrag(viewHolder: RecyclerView.ViewHolder)
    }

    class AppSearchAdapter(
        private val context: Context,
        private val appList: MutableList<AppSearchConfig>,
        private val onSwitchChanged: (AppSearchConfig, Boolean) -> Unit,
        private val dragStartListener: OnStartDragListener
    ) : RecyclerView.Adapter<AppSearchAdapter.ViewHolder>(), AppSearchItemTouchHelperCallback.ItemTouchHelperAdapter {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.list_item_app_search, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val config = appList[position]
            holder.appName.text = config.appName
            
            try {
                val icon = context.packageManager.getApplicationIcon(config.packageName)
                holder.appIcon.setImageDrawable(icon)
            } catch (e: PackageManager.NameNotFoundException) {
                holder.appIcon.setImageResource(config.iconResId) // Fallback
            }

            holder.enabledSwitch.setOnCheckedChangeListener(null)
            holder.enabledSwitch.isChecked = config.isEnabled
            holder.enabledSwitch.setOnCheckedChangeListener { _, isChecked ->
                onSwitchChanged(config, isChecked)
            }

            holder.dragHandle.setOnTouchListener { _, event ->
                if (MotionEventCompat.getActionMasked(event) == MotionEvent.ACTION_DOWN) {
                    dragStartListener.onStartDrag(holder)
                }
                false
            }
        }

        override fun getItemCount(): Int = appList.size

        override fun onItemMove(fromPosition: Int, toPosition: Int) {
            Collections.swap(appList, fromPosition, toPosition)
            notifyItemMoved(fromPosition, toPosition)
        }

        override fun onItemDismiss(position: Int) {
            // Not used
        }
        
        override fun onDragFinished() {
            // Update the order property based on the new position
            for (i in appList.indices) {
                appList[i].order = i
            }
        }


        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val appIcon: ImageView = view.findViewById(R.id.app_icon)
            val appName: TextView = view.findViewById(R.id.app_name)
            val enabledSwitch: SwitchMaterial = view.findViewById(R.id.app_enabled_switch)
            val dragHandle: ImageView = view.findViewById(R.id.drag_handle)
        }
    }
}

class AppSearchItemTouchHelperCallback(private val adapter: ItemTouchHelperAdapter) : ItemTouchHelper.Callback() {

    interface ItemTouchHelperAdapter {
        fun onItemMove(fromPosition: Int, toPosition: Int)
        fun onItemDismiss(position: Int)
        fun onDragFinished()
    }

    override fun isLongPressDragEnabled(): Boolean = false
    override fun isItemViewSwipeEnabled(): Boolean = false

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
        return makeMovementFlags(dragFlags, 0)
    }

    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
        adapter.onItemMove(viewHolder.adapterPosition, target.adapterPosition)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // Not used
    }
    
    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        adapter.onDragFinished()
    }
}
