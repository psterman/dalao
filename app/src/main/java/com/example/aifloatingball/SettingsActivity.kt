package com.example.aifloatingball

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Switch
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.*
import com.example.aifloatingball.model.AISearchEngine
import com.example.aifloatingball.model.SearchEngine
import android.widget.SeekBar
import android.content.pm.PackageManager
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var settingsManager: SettingsManager
    private lateinit var textTheme: TextView
    private var currentTheme = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    private lateinit var searchEngineAdapter: SearchEngineAdapter
    private lateinit var recyclerView: RecyclerView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        settingsManager = SettingsManager.getInstance(this)
        
        // 设置标题栏
        supportActionBar?.apply {
            title = "设置"
            setDisplayHomeAsUpEnabled(true)
        }

        // 初始化所有设置项
        setupSettings()
        // 加载保存的主题设置
        loadThemeSetting()
        // 初始化搜索引擎列表
        setupSearchEngineList()
    }

    private fun setupSearchEngineList() {
        recyclerView = findViewById(R.id.recyclerViewSearchEngines)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        // 获取搜索引擎列表
        val engines = if (settingsManager.isDefaultAIMode()) {
            AISearchEngine.DEFAULT_AI_ENGINES
        } else {
            SearchEngine.NORMAL_SEARCH_ENGINES
        }
        val enabledEngines = settingsManager.getEnabledEngines()
        
        // 初始化适配器
        searchEngineAdapter = SearchEngineAdapter(engines.toMutableList(), enabledEngines.toMutableSet()) { engine, isEnabled ->
            // 处理引擎启用/禁用状态变化
            if (isEnabled) {
                settingsManager.saveEnabledEngines(settingsManager.getEnabledEngines() + engine.name)
            } else {
                settingsManager.saveEnabledEngines(settingsManager.getEnabledEngines() - engine.name)
            }
        }
        
        recyclerView.adapter = searchEngineAdapter

        // 设置拖拽排序
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
            
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, 
                              target: RecyclerView.ViewHolder): Boolean {
                val fromPos = viewHolder.adapterPosition
                val toPos = target.adapterPosition
                searchEngineAdapter.moveItem(fromPos, toPos)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // 不实现滑动删除
            }

            override fun onMoved(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, 
                               fromPos: Int, target: RecyclerView.ViewHolder, toPos: Int, x: Int, y: Int) {
                super.onMoved(recyclerView, viewHolder, fromPos, target, toPos, x, y)
                // 保存新的排序
                settingsManager.saveEngineOrder(searchEngineAdapter.getEngines())
            }
        })
        
        itemTouchHelper.attachToRecyclerView(recyclerView)
        
        // 显示RecyclerView
        recyclerView.visibility = View.VISIBLE
    }

    private fun setupSettings() {
        // 初始化主题文本视图
        textTheme = findViewById(R.id.textTheme)
        
        // 左手模式设置
        val handModeIcon = findViewById<ImageView>(R.id.handModeIcon)
        val textHandMode = findViewById<TextView>(R.id.textHandMode)
        findViewById<Switch>(R.id.switchLeftHandedMode).apply {
            isChecked = settingsManager.isLeftHandedMode
            // 初始化图标和文本
            handModeIcon.setImageResource(if (isChecked) R.drawable.ic_hand_left else R.drawable.ic_hand_right)
            textHandMode.text = if (isChecked) "左手模式" else "右手模式"
            
            setOnCheckedChangeListener { _, isChecked ->
            settingsManager.isLeftHandedMode = isChecked
                // 更新图标和文本
                handModeIcon.setImageResource(if (isChecked) R.drawable.ic_hand_left else R.drawable.ic_hand_right)
                textHandMode.text = if (isChecked) "左手模式" else "右手模式"
            sendBroadcast(Intent(ACTION_SETTINGS_CHANGED).apply {
                putExtra(EXTRA_LEFT_HANDED_MODE_CHANGED, true)
            })
        }
        }

        // 悬浮球大小设置
        findViewById<SeekBar>(R.id.seekBarBallSize).apply {
            progress = settingsManager.getFloatingBallSize()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        settingsManager.setFloatingBallSize(progress)
                        val intent = Intent(this@SettingsActivity, FloatingWindowService::class.java).apply {
                            action = "UPDATE_BALL_SIZE"
                            putExtra("size", progress)
                        }
                        startService(intent)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }

        // 默认搜索模式设置
        val searchModeIcon = findViewById<ImageView>(R.id.searchModeIcon)
        val searchModeText = findViewById<TextView>(R.id.searchModeText)
        findViewById<Switch>(R.id.switchDefaultSearchMode).apply {
            isChecked = settingsManager.isDefaultAIMode()
            // 初始化图标和文本
            searchModeIcon.setImageResource(if (isChecked) R.drawable.ic_ai_search else R.drawable.ic_search_mode)
            searchModeText.text = if (isChecked) "AI搜索引擎" else "普通搜索引擎"
            
            setOnCheckedChangeListener { _, isChecked ->
                settingsManager.setDefaultAIMode(isChecked)
                // 更新图标和文本
                searchModeIcon.setImageResource(if (isChecked) R.drawable.ic_ai_search else R.drawable.ic_search_mode)
                searchModeText.text = if (isChecked) "AI搜索引擎" else "普通搜索引擎"
                Toast.makeText(
                    this@SettingsActivity,
                    if (isChecked) "已切换到AI搜索模式" else "已切换到普通搜索模式",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        // 隐私模式设置
        findViewById<Switch>(R.id.switchPrivacyMode).apply {
                isChecked = settingsManager.getPrivacyMode()
            setOnCheckedChangeListener { _, isChecked ->
                settingsManager.setPrivacyMode(isChecked)
            }
        }

        // 自启动设置
        findViewById<Switch>(R.id.switchAutoStart).apply {
                isChecked = settingsManager.getAutoStart()
            setOnCheckedChangeListener { _, isChecked ->
                settingsManager.setAutoStart(isChecked)
            }
        }

        // 搜索引擎管理入口
        findViewById<LinearLayout>(R.id.searchEngineSettings).apply {
            setOnClickListener {
                showSearchEngineFilterDialog()
            }
        }

        // 检查更新
        findViewById<View>(R.id.update_container).apply {
            findViewById<TextView>(R.id.textVersion).text = try {
                "当前版本：${packageManager.getPackageInfo(packageName, 0).versionName}"
            } catch (e: PackageManager.NameNotFoundException) {
                "版本获取失败"
            }
            
            setOnClickListener {
                Toast.makeText(this@SettingsActivity, "正在检查更新...", Toast.LENGTH_SHORT).show()
                // TODO: 实现检查更新逻辑
            }
        }

        // 设置主题容器的点击事件
        findViewById<View>(R.id.theme_container).setOnClickListener {
            showThemeDialog()
        }

        // 在搜索设置部分添加布局选项
        findViewById<LinearLayout>(R.id.menuLayoutSettings).apply {
            val layoutText = findViewById<TextView>(R.id.textMenuLayout)
            // 设置当前布局文本
            layoutText.text = when(settingsManager.getMenuLayout()) {
                "alphabetical" -> "字母索引排列"
                else -> "混合排列"
            }
            
            setOnClickListener {
                showLayoutDialog()
            }
        }
    }

    private fun loadThemeSetting() {
        try {
            // 使用新的键名存储整数类型的主题设置
            currentTheme = getSharedPreferences("settings", MODE_PRIVATE)
                .getInt("theme_mode_int", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        } catch (e: ClassCastException) {
            // 如果发生类型转换错误，说明是旧版本的字符串设置
            val oldThemeMode = getSharedPreferences("settings", MODE_PRIVATE)
                .getString("theme_mode", "system") ?: "system"
            
            // 转换旧的字符串设置为新的整数设置
            currentTheme = when (oldThemeMode) {
                "light" -> AppCompatDelegate.MODE_NIGHT_NO
                "dark" -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            
            // 保存新的设置并删除旧的设置
            getSharedPreferences("settings", MODE_PRIVATE)
                .edit()
                .remove("theme_mode")
                .putInt("theme_mode_int", currentTheme)
                .apply()
        }
        updateThemeText()
    }
    
    private fun showThemeDialog() {
        val themes = arrayOf("跟随系统", "浅色模式", "深色模式")
        val currentSelection = when (currentTheme) {
            AppCompatDelegate.MODE_NIGHT_NO -> 1
            AppCompatDelegate.MODE_NIGHT_YES -> 2
            else -> 0
        }
        
        AlertDialog.Builder(this)
            .setTitle("选择主题")
            .setSingleChoiceItems(themes, currentSelection) { dialog, which ->
                val newTheme = when (which) {
                    1 -> AppCompatDelegate.MODE_NIGHT_NO
                    2 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
                if (newTheme != currentTheme) {
                    currentTheme = newTheme
                    // 使用新的键名保存设置
                    getSharedPreferences("settings", MODE_PRIVATE)
                        .edit()
                        .putInt("theme_mode_int", currentTheme)
                        .apply()
                    // 应用主题
                    AppCompatDelegate.setDefaultNightMode(currentTheme)
                    // 更新显示文本
                    updateThemeText()
                }
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun updateThemeText() {
        val themeText = when (currentTheme) {
            AppCompatDelegate.MODE_NIGHT_NO -> "浅色模式"
            AppCompatDelegate.MODE_NIGHT_YES -> "深色模式"
            else -> "跟随系统"
        }
        textTheme.text = themeText
    }

    private fun showLayoutDialog() {
        val layouts = arrayOf("混合排列", "字母索引排列")
        val currentLayout = when(settingsManager.getMenuLayout()) {
            "alphabetical" -> 1
            else -> 0
        }

        AlertDialog.Builder(this)
            .setTitle("选择悬浮菜单布局")
            .setSingleChoiceItems(layouts, currentLayout) { dialog, which ->
                val newLayout = when(which) {
                    1 -> "alphabetical"
                    else -> "mixed"
                }
                settingsManager.setMenuLayout(newLayout)
                
                // 更新显示的文本
                findViewById<TextView>(R.id.textMenuLayout).text = layouts[which]
                
                // 发送广播通知悬浮窗服务更新布局
                val intent = Intent(this, FloatingWindowService::class.java).apply {
                    action = "UPDATE_MENU_LAYOUT"
                    putExtra("layout", newLayout)
                }
                startService(intent)
                
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showSearchEngineFilterDialog() {
        val engines = searchEngineAdapter.getEngines()
        val enabledEngines = settingsManager.getEnabledEngines()
        val checkedItems = engines.map { it.name in enabledEngines }.toBooleanArray()
        val engineNames = engines.map { it.name }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("选择搜索引擎")
            .setMultiChoiceItems(engineNames, checkedItems) { _, which, isChecked ->
                val engineName = engineNames[which]
                if (isChecked) {
                    settingsManager.saveEnabledEngines(settingsManager.getEnabledEngines() + engineName)
                } else {
                    settingsManager.saveEnabledEngines(settingsManager.getEnabledEngines() - engineName)
                }
                searchEngineAdapter.updateEnabledEngines(settingsManager.getEnabledEngines())
            }
            .setPositiveButton("确定", null)
            .show()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        const val ACTION_SETTINGS_CHANGED = "com.example.aifloatingball.SETTINGS_CHANGED"
        const val EXTRA_LEFT_HANDED_MODE_CHANGED = "left_handed_mode_changed"
        const val PREF_LEFT_HANDED_MODE = "left_handed_mode"
        const val PREF_DEFAULT_SEARCH_ENGINE = "default_search_engine"
        const val PREF_DEFAULT_SEARCH_MODE = "default_search_mode"
        const val KEY_DEFAULT_SEARCH_MODE = "default_search_mode"
    }
} 