package com.example.aifloatingball

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.example.aifloatingball.ui.chat.DeepSeekChatActivity

class AIApiSettingsActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.settings_activity)
        
        // 设置标题栏
        supportActionBar?.apply {
            title = "AI API设置"
            setDisplayHomeAsUpEnabled(true)
        }

        // 加载设置Fragment
        if (savedInstanceState == null) {
            supportFragmentManager
                .beginTransaction()
                .replace(R.id.settings, AIApiSettingsFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
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

            // DeepSeek 对话入口
            findPreference<Preference>("deepseek_chat")?.setOnPreferenceClickListener {
                val intent = Intent(requireContext(), DeepSeekChatActivity::class.java)
                startActivity(intent)
                true
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