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

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var settingsManager: SettingsManager
    private lateinit var textTheme: TextView
    private var currentTheme = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    
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

        // 搜索引擎设置
        findViewById<LinearLayout>(R.id.searchEngineSettings).setOnClickListener {
            startActivity(Intent(this, MenuSettingsActivity::class.java))
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