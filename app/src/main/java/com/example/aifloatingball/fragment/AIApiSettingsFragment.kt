package com.example.aifloatingball.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.example.aifloatingball.R
import com.example.aifloatingball.SettingsManager

/**
 * AI API设置Fragment - 集成到简易模式AI助手中心
 */
class AIApiSettingsFragment : PreferenceFragmentCompat() {
    
    private lateinit var settingsManager: SettingsManager
    
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.ai_api_preferences, rootKey)
        settingsManager = SettingsManager.getInstance(requireContext())
        
        // 设置API密钥的监听器
        setupApiKeyListeners()
    }
    
    private fun setupApiKeyListeners() {
        // ChatGPT API设置
        findPreference<EditTextPreference>("chatgpt_api_key")?.setOnPreferenceChangeListener { _, newValue ->
            settingsManager.setChatGPTApiKey(newValue as String)
            showToast("ChatGPT API密钥已保存")
            true
        }
        
        findPreference<EditTextPreference>("chatgpt_api_url")?.setOnPreferenceChangeListener { _, newValue ->
            settingsManager.setChatGPTApiUrl(newValue as String)
            showToast("ChatGPT API地址已保存")
            true
        }
        
        // Claude API设置
        findPreference<EditTextPreference>("claude_api_key")?.setOnPreferenceChangeListener { _, newValue ->
            settingsManager.setClaudeApiKey(newValue as String)
            showToast("Claude API密钥已保存")
            true
        }
        
        findPreference<EditTextPreference>("claude_api_url")?.setOnPreferenceChangeListener { _, newValue ->
            settingsManager.setClaudeApiUrl(newValue as String)
            showToast("Claude API地址已保存")
            true
        }
        
        // Gemini API设置
        findPreference<EditTextPreference>("gemini_api_key")?.setOnPreferenceChangeListener { _, newValue ->
            settingsManager.setGeminiApiKey(newValue as String)
            showToast("Gemini API密钥已保存")
            true
        }
        
        findPreference<EditTextPreference>("gemini_api_url")?.setOnPreferenceChangeListener { _, newValue ->
            settingsManager.setGeminiApiUrl(newValue as String)
            showToast("Gemini API地址已保存")
            true
        }
        
        // 文心一言 API设置
        findPreference<EditTextPreference>("wenxin_api_key")?.setOnPreferenceChangeListener { _, newValue ->
            settingsManager.setWenxinApiKey(newValue as String)
            showToast("文心一言 API密钥已保存")
            true
        }
        
        findPreference<EditTextPreference>("wenxin_secret_key")?.setOnPreferenceChangeListener { _, newValue ->
            // 使用SharedPreferences直接保存
            val prefs = android.content.Context.MODE_PRIVATE
            requireContext().getSharedPreferences("app_preferences", prefs).edit()
                .putString("wenxin_secret_key", newValue as String).apply()
            showToast("文心一言 Secret密钥已保存")
            true
        }
        
        findPreference<EditTextPreference>("wenxin_api_url")?.setOnPreferenceChangeListener { _, newValue ->
            settingsManager.setWenxinApiUrl(newValue as String)
            showToast("文心一言 API地址已保存")
            true
        }
        
        // DeepSeek API设置
        findPreference<EditTextPreference>("deepseek_api_key")?.setOnPreferenceChangeListener { _, newValue ->
            settingsManager.setDeepSeekApiKey(newValue as String)
            showToast("DeepSeek API密钥已保存")
            true
        }
        
        findPreference<EditTextPreference>("deepseek_api_url")?.setOnPreferenceChangeListener { _, newValue ->
            settingsManager.setDeepSeekApiUrl(newValue as String)
            showToast("DeepSeek API地址已保存")
            true
        }
        
        // 通义千问 API设置
        findPreference<EditTextPreference>("qianwen_api_key")?.setOnPreferenceChangeListener { _, newValue ->
            settingsManager.setQianwenApiKey(newValue as String)
            showToast("通义千问 API密钥已保存")
            true
        }
        
        findPreference<EditTextPreference>("qianwen_api_url")?.setOnPreferenceChangeListener { _, newValue ->
            settingsManager.setQianwenApiUrl(newValue as String)
            showToast("通义千问 API地址已保存")
            true
        }
        
        // 讯飞星火 API设置
        findPreference<EditTextPreference>("xinghuo_api_key")?.setOnPreferenceChangeListener { _, newValue ->
            // 使用SharedPreferences直接保存
            val prefs = android.content.Context.MODE_PRIVATE
            requireContext().getSharedPreferences("app_preferences", prefs).edit()
                .putString("xinghuo_api_key", newValue as String).apply()
            showToast("讯飞星火 API密钥已保存")
            true
        }
        
        findPreference<EditTextPreference>("xinghuo_api_url")?.setOnPreferenceChangeListener { _, newValue ->
            // 使用SharedPreferences直接保存
            val prefs = android.content.Context.MODE_PRIVATE
            requireContext().getSharedPreferences("app_preferences", prefs).edit()
                .putString("xinghuo_api_url", newValue as String).apply()
            showToast("讯飞星火 API地址已保存")
            true
        }
        
        // Kimi API设置
        findPreference<EditTextPreference>("kimi_api_key")?.setOnPreferenceChangeListener { _, newValue ->
            settingsManager.setKimiApiKey(newValue as String)
            showToast("Kimi API密钥已保存")
            true
        }
        
        findPreference<EditTextPreference>("kimi_api_url")?.setOnPreferenceChangeListener { _, newValue ->
            settingsManager.setKimiApiUrl(newValue as String)
            showToast("Kimi API地址已保存")
            true
        }
        
        // 智谱AI API设置
        findPreference<EditTextPreference>("zhipu_ai_api_key")?.setOnPreferenceChangeListener { _, newValue ->
            settingsManager.setZhipuAiApiKey(newValue as String)
            showToast("智谱AI API密钥已保存")
            true
        }
        
        findPreference<EditTextPreference>("zhipu_ai_api_url")?.setOnPreferenceChangeListener { _, newValue ->
            settingsManager.setZhipuAiApiUrl(newValue as String)
            showToast("智谱AI API地址已保存")
            true
        }
    }
    
    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
