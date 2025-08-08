package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.MaterialToolbar

class AIApiSettingsActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "AIApiSettings"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            Log.d(TAG, "开始创建AI API设置页面")

            // 尝试设置内容视图
            try {
                setContentView(R.layout.activity_preference_container)
                Log.d(TAG, "成功设置内容视图")
            } catch (e: Exception) {
                Log.e(TAG, "设置主布局失败，尝试使用备用布局", e)
                createFallbackLayout()
            }

            // 查找并设置标题栏
            val toolbar: MaterialToolbar? = findViewById(R.id.toolbar)
            if (toolbar == null) {
                Log.e(TAG, "无法找到toolbar控件")
                Toast.makeText(this, "界面加载失败：找不到标题栏", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            Log.d(TAG, "成功找到toolbar控件")
            setSupportActionBar(toolbar)

            supportActionBar?.apply {
                try {
                    title = getString(R.string.ai_api_settings)
                    setDisplayHomeAsUpEnabled(true)
                    Log.d(TAG, "成功设置ActionBar")
                } catch (e: Exception) {
                    Log.e(TAG, "设置ActionBar失败", e)
                    title = "AI API 设置" // 使用硬编码标题作为备用
                    setDisplayHomeAsUpEnabled(true)
                }
            }

            // 检查设置容器
            val settingsContainer = findViewById<FrameLayout>(R.id.settings_container)
            if (settingsContainer == null) {
                Log.e(TAG, "无法找到settings_container")
                Toast.makeText(this, "界面加载失败：找不到设置容器", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            Log.d(TAG, "成功找到settings_container")

            // 加载设置Fragment
            if (savedInstanceState == null) {
                try {
                    supportFragmentManager
                        .beginTransaction()
                        .replace(R.id.settings_container, AIApiSettingsFragment())
                        .commit()
                    Log.d(TAG, "成功加载AIApiSettingsFragment")
                } catch (e: Exception) {
                    Log.e(TAG, "加载Fragment失败", e)
                    Toast.makeText(this, "加载设置项失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    finish()
                    return
                }
            }

            Log.d(TAG, "AI API设置页面创建完成")
        } catch (e: Exception) {
            Log.e(TAG, "创建AI API设置页面失败", e)
            Toast.makeText(this, "加载设置页面失败: ${e.message}", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /**
     * 创建备用布局，当主布局加载失败时使用
     */
    private fun createFallbackLayout() {
        try {
            Log.d(TAG, "创建备用布局")

            // 创建根布局
            val rootLayout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(ContextCompat.getColor(this@AIApiSettingsActivity, R.color.elderly_background))
            }

            // 创建标题栏
            val toolbar = com.google.android.material.appbar.MaterialToolbar(this).apply {
                id = R.id.toolbar
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    resources.getDimensionPixelSize(androidx.appcompat.R.dimen.abc_action_bar_default_height_material)
                )
                setBackgroundColor(ContextCompat.getColor(this@AIApiSettingsActivity, R.color.elderly_primary))
                title = "AI API 设置"
                setTitleTextColor(ContextCompat.getColor(this@AIApiSettingsActivity, R.color.elderly_button_text_primary))
                setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material)
                setNavigationOnClickListener { finish() }
            }

            // 创建设置容器
            val settingsContainer = FrameLayout(this).apply {
                id = R.id.settings_container
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            }

            rootLayout.addView(toolbar)
            rootLayout.addView(settingsContainer)

            setContentView(rootLayout)
            setSupportActionBar(toolbar)

            Log.d(TAG, "备用布局创建成功")
        } catch (e: Exception) {
            Log.e(TAG, "创建备用布局失败", e)
            // 如果连备用布局都创建失败，显示错误信息并关闭Activity
            Toast.makeText(this, "界面创建失败，请重试", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    class AIApiSettingsFragment : PreferenceFragmentCompat() {
        private lateinit var settingsManager: SettingsManager
        
        companion object {
            private const val TAG = "AIApiSettingsFragment"
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            try {
                Log.d(TAG, "开始创建AI API设置Fragment")

                // 检查XML资源是否存在
                try {
                    setPreferencesFromResource(R.xml.ai_api_preferences, rootKey)
                    Log.d(TAG, "成功加载ai_api_preferences.xml")
                } catch (e: Exception) {
                    Log.e(TAG, "加载ai_api_preferences.xml失败", e)
                    Toast.makeText(context, "加载设置配置失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    return
                }

                // 初始化SettingsManager
                try {
                    settingsManager = SettingsManager.getInstance(requireContext())
                    Log.d(TAG, "成功初始化SettingsManager")
                } catch (e: Exception) {
                    Log.e(TAG, "初始化SettingsManager失败", e)
                    Toast.makeText(context, "初始化设置管理器失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    return
                }

                // 设置API密钥的摘要
                setupApiKeySummaries()
                
                // 添加DeepSeek诊断功能
                setupDeepSeekDiagnostic()

                Log.d(TAG, "AI API设置Fragment创建完成")
            } catch (e: Exception) {
                Log.e(TAG, "创建AI API设置Fragment失败", e)
                Toast.makeText(context, "加载设置项失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        private fun setupApiKeySummaries() {
            try {
                Log.d(TAG, "开始设置API密钥摘要")

                // 设置所有API密钥的摘要
                setupApiKeySummary("chatgpt_api_key", "ChatGPT")
                setupApiKeySummary("claude_api_key", "Claude")
                setupApiKeySummary("gemini_api_key", "Gemini")
                setupApiKeySummary("wenxin_api_key", "文心一言")
                setupApiKeySummary("deepseek_api_key", "DeepSeek")
                setupApiKeySummary("qianwen_api_key", "通义千问")
                setupApiKeySummary("xinghuo_api_key", "讯飞星火")
                setupApiKeySummary("kimi_api_key", "Kimi")

                Log.d(TAG, "API密钥摘要设置完成")
            } catch (e: Exception) {
                Log.e(TAG, "设置API密钥摘要失败", e)
                Toast.makeText(context, "设置API密钥显示失败，但不影响功能使用", Toast.LENGTH_SHORT).show()
            }
        }
        
        private fun setupApiKeySummary(key: String, serviceName: String) {
            val pref = findPreference<EditTextPreference>(key)
            if (pref == null) {
                Log.w(TAG, "找不到${key}偏好设置")
                return
            }
            
            try {
                val apiKey = settingsManager.getString(key, "")
                pref.summary = if (apiKey.isNullOrEmpty()) {
                    "未设置"
                } else {
                    "已设置 (${apiKey.take(4)}...${apiKey.takeLast(4)})"
                }

                pref.setOnPreferenceChangeListener { _, newValue ->
                    try {
                        val newApiKey = newValue as String
                        
                        // 验证API密钥格式
                        if (newApiKey.isNotEmpty()) {
                            when (serviceName) {
                                "DeepSeek" -> {
                                    if (!newApiKey.startsWith("sk-")) {
                                        Toast.makeText(context, "DeepSeek API密钥应该以'sk-'开头", Toast.LENGTH_LONG).show()
                                        return@setOnPreferenceChangeListener false
                                    }
                                }
                                "ChatGPT" -> {
                                    if (!newApiKey.startsWith("sk-")) {
                                        Toast.makeText(context, "ChatGPT API密钥应该以'sk-'开头", Toast.LENGTH_LONG).show()
                                        return@setOnPreferenceChangeListener false
                                    }
                                }
                                "Claude" -> {
                                    if (!newApiKey.startsWith("sk-ant-")) {
                                        Toast.makeText(context, "Claude API密钥应该以'sk-ant-'开头", Toast.LENGTH_LONG).show()
                                        return@setOnPreferenceChangeListener false
                                    }
                                }
                            }
                            
                            if (newApiKey.length < 20) {
                                Toast.makeText(context, "API密钥长度不足，请检查是否正确", Toast.LENGTH_LONG).show()
                                return@setOnPreferenceChangeListener false
                            }
                        }
                        
                        // 保存API密钥
                        settingsManager.putString(key, newApiKey)
                        
                        // 更新显示
                        pref.summary = if (newApiKey.isEmpty()) {
                            "未设置"
                        } else {
                            "已设置 (${newApiKey.take(4)}...${newApiKey.takeLast(4)})"
                        }
                        
                        // 显示成功消息
                        Toast.makeText(context, "${serviceName} API密钥保存成功", Toast.LENGTH_SHORT).show()
                        
                        true
                    } catch (e: Exception) {
                        Log.e(TAG, "更新${serviceName} API密钥失败", e)
                        Toast.makeText(context, "保存失败: ${e.message}", Toast.LENGTH_LONG).show()
                        false
                    }
                }
                Log.d(TAG, "成功设置${serviceName} API密钥偏好")
            } catch (e: Exception) {
                Log.e(TAG, "设置${serviceName} API密钥偏好失败", e)
            }
        }
        
        /**
         * 设置DeepSeek诊断功能
         */
        private fun setupDeepSeekDiagnostic() {
            try {
                // 查找DeepSeek API密钥偏好设置
                val deepSeekApiKeyPref = findPreference<EditTextPreference>("deepseek_api_key")
                if (deepSeekApiKeyPref == null) {
                    Log.w(TAG, "找不到deepseek_api_key偏好设置")
                    return
                }
                
                // 为DeepSeek API密钥添加诊断功能
                deepSeekApiKeyPref.setOnPreferenceClickListener { preference ->
                    showDeepSeekDiagnosticDialog()
                    true
                }
                
                Log.d(TAG, "DeepSeek诊断功能设置完成")
            } catch (e: Exception) {
                Log.e(TAG, "设置DeepSeek诊断功能失败", e)
            }
        }
        
        /**
         * 显示DeepSeek诊断对话框
         */
        private fun showDeepSeekDiagnosticDialog() {
            try {
                val currentApiKey = settingsManager.getString("deepseek_api_key", "") ?: ""
                
                val dialogBuilder = androidx.appcompat.app.AlertDialog.Builder(requireContext())
                dialogBuilder.setTitle("DeepSeek API诊断")
                
                if (currentApiKey.isEmpty()) {
                    dialogBuilder.setMessage("您还没有设置DeepSeek API密钥。请先设置API密钥，然后使用诊断工具检查连接问题。")
                    dialogBuilder.setPositiveButton("设置API密钥") { _, _ ->
                        // 用户点击设置API密钥，对话框会自动关闭，用户可以编辑API密钥
                    }
                    dialogBuilder.setNegativeButton("取消", null)
                } else {
                    dialogBuilder.setMessage("检测到您已设置DeepSeek API密钥。如果遇到401认证错误或其他连接问题，可以使用诊断工具进行详细检查。")
                    dialogBuilder.setPositiveButton("开始诊断") { _, _ ->
                        startDeepSeekDiagnostic()
                    }
                    dialogBuilder.setNegativeButton("取消", null)
                    dialogBuilder.setNeutralButton("设置API密钥") { _, _ ->
                        // 用户点击设置API密钥，对话框会自动关闭，用户可以编辑API密钥
                    }
                }
                
                dialogBuilder.show()
            } catch (e: Exception) {
                Log.e(TAG, "显示DeepSeek诊断对话框失败", e)
                Toast.makeText(context, "显示诊断对话框失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        /**
         * 启动DeepSeek诊断
         */
        private fun startDeepSeekDiagnostic() {
            try {
                // 启动DeepSeek诊断活动
                val intent = Intent(requireContext(), DeepSeekDiagnosticActivity::class.java)
                startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "启动DeepSeek诊断失败", e)
                Toast.makeText(context, "启动诊断工具失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}