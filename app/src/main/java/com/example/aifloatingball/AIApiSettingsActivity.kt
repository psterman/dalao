package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.google.android.material.appbar.MaterialToolbar

class AIApiSettingsActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_api_settings)
        
        // 设置标题栏
        val toolbar: MaterialToolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = "AI API 设置"
            setDisplayHomeAsUpEnabled(true)
        }

        // 加载设置Fragment
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings_container, AIApiSettingsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    class AIApiSettingsFragment : PreferenceFragmentCompat() {
        private lateinit var settingsManager: SettingsManager

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.ai_api_preferences, rootKey)
            settingsManager = SettingsManager.getInstance(requireContext())

            // DeepSeek API设置
            findPreference<EditTextPreference>("deepseek_api_key")?.apply {
                text = settingsManager.getDeepSeekApiKey()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setDeepSeekApiKey(newValue.toString())
                    Toast.makeText(context, "DeepSeek API密钥已保存", Toast.LENGTH_SHORT).show()
                    true
                }
            }

            // ChatGPT API设置
            findPreference<EditTextPreference>("chatgpt_api_key")?.apply {
                text = settingsManager.getChatGPTApiKey()
                setOnPreferenceChangeListener { _, newValue ->
                    settingsManager.setChatGPTApiKey(newValue.toString())
                    Toast.makeText(context, "ChatGPT API密钥已保存", Toast.LENGTH_SHORT).show()
                    true
                }
            }
        }
    }
} 