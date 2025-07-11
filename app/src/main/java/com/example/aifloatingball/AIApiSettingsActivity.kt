package com.example.aifloatingball

import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
            setContentView(R.layout.activity_preference_container)
            
            // 设置标题栏
            val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
            setSupportActionBar(toolbar)
            supportActionBar?.apply {
                title = getString(R.string.ai_api_settings)
                setDisplayHomeAsUpEnabled(true)
            }

            // 加载设置Fragment
            if (savedInstanceState == null) {
                supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.settings_container, AIApiSettingsFragment())
                    .commit()
            }
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

    class AIApiSettingsFragment : PreferenceFragmentCompat() {
        private lateinit var settingsManager: SettingsManager
        
        companion object {
            private const val TAG = "AIApiSettingsFragment"
        }

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            try {
                setPreferencesFromResource(R.xml.ai_api_preferences, rootKey)
                settingsManager = SettingsManager.getInstance(requireContext())
                
                // 设置API密钥的摘要
                setupApiKeySummaries()
            } catch (e: Exception) {
                Log.e(TAG, "创建AI API设置Fragment失败", e)
                Toast.makeText(context, "加载设置项失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
        
        private fun setupApiKeySummaries() {
            try {
                // DeepSeek API密钥
                findPreference<EditTextPreference>("deepseek_api_key")?.let { pref ->
                    val apiKey = settingsManager.getString("deepseek_api_key", "")
                    pref.summary = if (apiKey.isNullOrEmpty()) {
                        "未设置"
                    } else {
                        "已设置 (${apiKey.take(4)}...${apiKey.takeLast(4)})"
                    }
                    
                    pref.setOnPreferenceChangeListener { _, newValue ->
                        val newApiKey = newValue as String
                        pref.summary = if (newApiKey.isEmpty()) {
                            "未设置"
                        } else {
                            "已设置 (${newApiKey.take(4)}...${newApiKey.takeLast(4)})"
                        }
                        settingsManager.putString("deepseek_api_key", newApiKey)
                        true
                    }
                }
                
                // ChatGPT API密钥
                findPreference<EditTextPreference>("chatgpt_api_key")?.let { pref ->
                    val apiKey = settingsManager.getString("chatgpt_api_key", "")
                    pref.summary = if (apiKey.isNullOrEmpty()) {
                        "未设置"
                    } else {
                        "已设置 (${apiKey.take(4)}...${apiKey.takeLast(4)})"
                    }
                    
                    pref.setOnPreferenceChangeListener { _, newValue ->
                        val newApiKey = newValue as String
                        pref.summary = if (newApiKey.isEmpty()) {
                            "未设置"
                        } else {
                            "已设置 (${newApiKey.take(4)}...${newApiKey.takeLast(4)})"
                        }
                        settingsManager.putString("chatgpt_api_key", newApiKey)
                        true
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "设置API密钥摘要失败", e)
                // 即使设置摘要失败，也不要崩溃整个Fragment
            }
        }
    }
}