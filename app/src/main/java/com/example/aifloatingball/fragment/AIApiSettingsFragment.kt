package com.example.aifloatingball.fragment

import android.content.Context
import android.os.Bundle
import android.view.ContextThemeWrapper
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
    
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        // 应用主题以支持暗色模式
        val contextThemeWrapper = ContextThemeWrapper(requireContext(), R.style.PreferenceTheme)
        val themedInflater = inflater.cloneInContext(contextThemeWrapper)
        val view = super.onCreateView(themedInflater, container, savedInstanceState)
        
        // 设置背景色
        view?.setBackgroundColor(requireContext().getColor(R.color.ai_assistant_center_background_light))
        
        return view
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 在view创建后设置文字颜色
        view.post {
            applyDarkModeTextColors()
        }
    }
    
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.ai_api_preferences, rootKey)
        settingsManager = SettingsManager.getInstance(requireContext())
        
        // 设置API密钥的监听器
        setupApiKeyListeners()
    }
    
    /**
     * 应用暗色模式文字颜色
     */
    private fun applyDarkModeTextColors() {
        val textPrimaryColor = requireContext().getColor(R.color.ai_assistant_text_primary)
        val textSecondaryColor = requireContext().getColor(R.color.ai_assistant_text_secondary)
        
        // 获取RecyclerView（PreferenceFragment使用RecyclerView）
        val recyclerView = view?.findViewById<androidx.recyclerview.widget.RecyclerView>(androidx.preference.R.id.recycler_view)
        if (recyclerView != null) {
            // 遍历所有可见的item
            for (i in 0 until recyclerView.childCount) {
                val itemView = recyclerView.getChildAt(i)
                applyTextColorsToView(itemView, textPrimaryColor, textSecondaryColor)
            }
            
            // 监听RecyclerView的滚动，为动态加载的item设置颜色
            recyclerView.addOnScrollListener(object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    for (i in 0 until recyclerView.childCount) {
                        val itemView = recyclerView.getChildAt(i)
                        applyTextColorsToView(itemView, textPrimaryColor, textSecondaryColor)
                    }
                }
            })
            
            // 监听Adapter数据变化
            recyclerView.adapter?.registerAdapterDataObserver(object : androidx.recyclerview.widget.RecyclerView.AdapterDataObserver() {
                override fun onChanged() {
                    super.onChanged()
                    for (i in 0 until recyclerView.childCount) {
                        val itemView = recyclerView.getChildAt(i)
                        applyTextColorsToView(itemView, textPrimaryColor, textSecondaryColor)
                    }
                }
                
                override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                    super.onItemRangeInserted(positionStart, itemCount)
                    for (i in 0 until recyclerView.childCount) {
                        val itemView = recyclerView.getChildAt(i)
                        applyTextColorsToView(itemView, textPrimaryColor, textSecondaryColor)
                    }
                }
            })
        }
    }
    
    /**
     * 递归查找并设置View中的TextView文字颜色
     */
    private fun applyTextColorsToView(view: View, primaryColor: Int, secondaryColor: Int) {
        if (view is android.widget.TextView) {
            // 根据TextView的资源ID判断类型
            val resourceName = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                try {
                    view.context.resources.getResourceEntryName(view.id)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
            
            // 判断是否是标题或摘要
            when {
                resourceName?.contains("title", ignoreCase = true) == true -> {
                    view.setTextColor(primaryColor)
                }
                resourceName?.contains("summary", ignoreCase = true) == true -> {
                    view.setTextColor(secondaryColor)
                }
                else -> {
                    // 根据文字大小判断
                    val textSize = view.textSize / view.context.resources.displayMetrics.scaledDensity
                    val isBold = view.typeface?.isBold ?: false
                    if (textSize >= 16 || isBold) {
                        view.setTextColor(primaryColor)
                    } else if (textSize >= 14) {
                        view.setTextColor(secondaryColor)
                    }
                }
            }
        } else if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyTextColorsToView(view.getChildAt(i), primaryColor, secondaryColor)
            }
        }
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
