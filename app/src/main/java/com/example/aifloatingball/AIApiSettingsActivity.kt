package com.example.aifloatingball

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

                Log.d(TAG, "AI API设置Fragment创建完成")
            } catch (e: Exception) {
                Log.e(TAG, "创建AI API设置Fragment失败", e)
                Toast.makeText(context, "加载设置项失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        private fun setupApiKeySummaries() {
            try {
                Log.d(TAG, "开始设置API密钥摘要")

                // DeepSeek API密钥
                val deepSeekPref = findPreference<EditTextPreference>("deepseek_api_key")
                if (deepSeekPref == null) {
                    Log.w(TAG, "找不到deepseek_api_key偏好设置")
                } else {
                    Log.d(TAG, "找到deepseek_api_key偏好设置")
                    try {
                        val apiKey = settingsManager.getString("deepseek_api_key", "")
                        deepSeekPref.summary = if (apiKey.isNullOrEmpty()) {
                            "未设置"
                        } else {
                            "已设置 (${apiKey.take(4)}...${apiKey.takeLast(4)})"
                        }

                        deepSeekPref.setOnPreferenceChangeListener { _, newValue ->
                            try {
                                val newApiKey = newValue as String
                                deepSeekPref.summary = if (newApiKey.isEmpty()) {
                                    "未设置"
                                } else {
                                    "已设置 (${newApiKey.take(4)}...${newApiKey.takeLast(4)})"
                                }
                                settingsManager.putString("deepseek_api_key", newApiKey)
                                true
                            } catch (e: Exception) {
                                Log.e(TAG, "更新DeepSeek API密钥失败", e)
                                false
                            }
                        }
                        Log.d(TAG, "成功设置DeepSeek API密钥偏好")
                    } catch (e: Exception) {
                        Log.e(TAG, "设置DeepSeek API密钥偏好失败", e)
                    }
                }

                // ChatGPT API密钥
                val chatGptPref = findPreference<EditTextPreference>("chatgpt_api_key")
                if (chatGptPref == null) {
                    Log.w(TAG, "找不到chatgpt_api_key偏好设置")
                } else {
                    Log.d(TAG, "找到chatgpt_api_key偏好设置")
                    try {
                        val apiKey = settingsManager.getString("chatgpt_api_key", "")
                        chatGptPref.summary = if (apiKey.isNullOrEmpty()) {
                            "未设置"
                        } else {
                            "已设置 (${apiKey.take(4)}...${apiKey.takeLast(4)})"
                        }

                        chatGptPref.setOnPreferenceChangeListener { _, newValue ->
                            try {
                                val newApiKey = newValue as String
                                chatGptPref.summary = if (newApiKey.isEmpty()) {
                                    "未设置"
                                } else {
                                    "已设置 (${newApiKey.take(4)}...${newApiKey.takeLast(4)})"
                                }
                                settingsManager.putString("chatgpt_api_key", newApiKey)
                                true
                            } catch (e: Exception) {
                                Log.e(TAG, "更新ChatGPT API密钥失败", e)
                                false
                            }
                        }
                        Log.d(TAG, "成功设置ChatGPT API密钥偏好")
                    } catch (e: Exception) {
                        Log.e(TAG, "设置ChatGPT API密钥偏好失败", e)
                    }
                }

                Log.d(TAG, "API密钥摘要设置完成")
            } catch (e: Exception) {
                Log.e(TAG, "设置API密钥摘要失败", e)
                // 即使设置摘要失败，也不要崩溃整个Fragment
                Toast.makeText(context, "设置API密钥显示失败，但不影响功能使用", Toast.LENGTH_SHORT).show()
            }
        }
    }
}