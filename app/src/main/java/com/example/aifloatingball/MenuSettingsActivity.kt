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
import com.example.aifloatingball.utils.IconLoader

class MenuSettingsActivity : AppCompatActivity() {
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: MenuSettingsAdapter
    private lateinit var settingsManager: SettingsManager
    private lateinit var iconLoader: IconLoader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu_settings)

        // 设置标题栏
        supportActionBar?.apply {
            title = "悬浮菜单设置"
            setDisplayHomeAsUpEnabled(true)
        }

        settingsManager = SettingsManager.getInstance(this)
        iconLoader = IconLoader(this)
        
        // 初始化RecyclerView
        setupRecyclerView()

        // 保存按钮点击事件
        findViewById<Button>(R.id.btn_save).setOnClickListener {
            saveSettings()
        }
    }

    private fun setupRecyclerView() {
        recyclerView = findViewById(R.id.menu_items_recycler)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // 从 SharedPreferences 加载菜单项
        val menuItems = loadMenuItems().toMutableList()
        adapter = MenuSettingsAdapter(menuItems, iconLoader)
        recyclerView.adapter = adapter

        // 设置拖拽排序
        setupDragAndDrop()
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

    private fun loadMenuItems(): List<MenuItem> {
        // 获取已保存的菜单项配置并转换为 MutableList
        val savedMenuItems = settingsManager.getMenuItems().toMutableList()
        
        // 如果没有保存的配置，使用默认配置
        return if (savedMenuItems.isEmpty()) {
            getDefaultMenuItems().toMutableList()
        } else {
            savedMenuItems
        }
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
            MenuItem("Bard", R.drawable.ic_floating_ball, "https://bard.google.com", MenuCategory.AI_SEARCH, true),
            MenuItem("Copilot", R.drawable.ic_floating_ball, "https://copilot.microsoft.com", MenuCategory.AI_SEARCH, true),
            MenuItem("Poe", R.drawable.ic_floating_ball, "https://poe.com", MenuCategory.AI_SEARCH, true),
            MenuItem("Anthropic", R.drawable.ic_floating_ball, "https://anthropic.com", MenuCategory.AI_SEARCH, true),
            MenuItem("Character", R.drawable.ic_floating_ball, "https://character.ai", MenuCategory.AI_SEARCH, true),
            MenuItem("Pi", R.drawable.ic_floating_ball, "https://pi.ai", MenuCategory.AI_SEARCH, true),
            
            // 普通搜索引擎
            MenuItem("百度", R.drawable.ic_baidu, "https://www.baidu.com", MenuCategory.NORMAL_SEARCH, true),
            MenuItem("Google", R.drawable.ic_google, "https://www.google.com", MenuCategory.NORMAL_SEARCH, true),
            MenuItem("必应", R.drawable.ic_bing, "https://www.bing.com", MenuCategory.NORMAL_SEARCH, true),
            MenuItem("搜狗", R.drawable.ic_sogou, "https://www.sogou.com", MenuCategory.NORMAL_SEARCH, true),
            MenuItem("360搜索", R.drawable.ic_360, "https://www.so.com", MenuCategory.NORMAL_SEARCH, true),
            MenuItem("头条搜索", R.drawable.ic_floating_ball, "https://so.toutiao.com", MenuCategory.NORMAL_SEARCH, true),
            MenuItem("夸克搜索", R.drawable.ic_floating_ball, "https://quark.sm.cn", MenuCategory.NORMAL_SEARCH, true),
            MenuItem("神马搜索", R.drawable.ic_floating_ball, "https://m.sm.cn", MenuCategory.NORMAL_SEARCH, true),
            MenuItem("Yandex", R.drawable.ic_floating_ball, "https://yandex.com", MenuCategory.NORMAL_SEARCH, true),
            MenuItem("DuckDuckGo", R.drawable.ic_floating_ball, "https://duckduckgo.com", MenuCategory.NORMAL_SEARCH, true),
            MenuItem("Yahoo", R.drawable.ic_floating_ball, "https://search.yahoo.com", MenuCategory.NORMAL_SEARCH, true),
            MenuItem("Ecosia", R.drawable.ic_floating_ball, "https://www.ecosia.org", MenuCategory.NORMAL_SEARCH, true),
            
            // 功能
            MenuItem("返回", R.drawable.ic_back, "back://last_app", MenuCategory.FUNCTION, true),
            MenuItem("截图", R.drawable.ic_screenshot, "action://screenshot", MenuCategory.FUNCTION, true),
            MenuItem("设置", R.drawable.ic_settings, "action://settings", MenuCategory.FUNCTION, true),
            MenuItem("分享", R.drawable.ic_share, "action://share", MenuCategory.FUNCTION, true)
        )
    }
} 