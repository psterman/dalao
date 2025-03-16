package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.aifloatingball.adapter.MenuSettingsAdapter
import com.example.aifloatingball.model.MenuItem
import com.example.aifloatingball.model.MenuCategory

class MenuSettingsActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MenuSettingsAdapter
    private lateinit var settingsManager: SettingsManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu_settings)

        // 设置标题栏
        supportActionBar?.apply {
            title = "悬浮菜单设置"
            setDisplayHomeAsUpEnabled(true)
        }

        settingsManager = SettingsManager.getInstance(this)
        
        // 初始化RecyclerView
        recyclerView = findViewById(R.id.menu_items_recycler)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        // 获取已保存的菜单项配置并转换为 MutableList
        val savedMenuItems = settingsManager.getMenuItems().toMutableList()
        
        // 如果没有保存的配置，使用默认配置
        val menuItems = if (savedMenuItems.isEmpty()) {
            getDefaultMenuItems().toMutableList()
        } else {
            savedMenuItems
        }

        adapter = MenuSettingsAdapter(menuItems)
        recyclerView.adapter = adapter

        // 设置拖拽排序
        setupDragAndDrop()

        // 保存按钮点击事件
        findViewById<Button>(R.id.btn_save).setOnClickListener {
            saveSettings()
        }
    }

    private fun setupDragAndDrop() {
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                adapter.moveItem(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    private fun saveSettings() {
        val selectedItems = adapter.getSelectedItems()
        settingsManager.saveMenuItems(selectedItems)
        
        // 发送广播通知服务更新菜单
        sendBroadcast(Intent("com.example.aifloatingball.ACTION_UPDATE_MENU"))
        
        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    private fun getDefaultMenuItems(): List<MenuItem> {
        return listOf(
            // AI 搜索引擎
            MenuItem("ChatGPT", R.drawable.ic_chatgpt, "https://chat.openai.com", MenuCategory.AI_SEARCH, true),
            MenuItem("Claude", R.drawable.ic_claude, "https://claude.ai", MenuCategory.AI_SEARCH, true),
            MenuItem("文心一言", R.drawable.ic_wenxin, "https://yiyan.baidu.com", MenuCategory.AI_SEARCH, true),
            MenuItem("通义千问", R.drawable.ic_qianwen, "https://qianwen.aliyun.com", MenuCategory.AI_SEARCH, true),
            MenuItem("讯飞星火", R.drawable.ic_xinghuo, "https://xinghuo.xfyun.cn", MenuCategory.AI_SEARCH, true),
            MenuItem("Gemini", R.drawable.ic_gemini, "https://gemini.google.com", MenuCategory.AI_SEARCH, true),
            
            // 普通搜索引擎
            MenuItem("百度", R.drawable.ic_baidu, "https://www.baidu.com", MenuCategory.NORMAL_SEARCH, true),
            MenuItem("Google", R.drawable.ic_google, "https://www.google.com", MenuCategory.NORMAL_SEARCH, true),
            MenuItem("必应", R.drawable.ic_bing, "https://www.bing.com", MenuCategory.NORMAL_SEARCH, true),
            MenuItem("搜狗", R.drawable.ic_sogou, "https://www.sogou.com", MenuCategory.NORMAL_SEARCH, true),
            MenuItem("360搜索", R.drawable.ic_360, "https://www.so.com", MenuCategory.NORMAL_SEARCH, true),
            
            // 功能
            MenuItem("返回", R.drawable.ic_back, "back://last_app", MenuCategory.FUNCTION, true),
            MenuItem("截图", R.drawable.ic_screenshot, "action://screenshot", MenuCategory.FUNCTION, true),
            MenuItem("设置", R.drawable.ic_settings, "action://settings", MenuCategory.FUNCTION, true),
            MenuItem("分享", R.drawable.ic_share, "action://share", MenuCategory.FUNCTION, true)
        )
    }
} 