package com.example.aifloatingball.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.aifloatingball.R
import com.example.aifloatingball.model.PromptProfile
import com.example.aifloatingball.viewmodel.SettingsViewModel
import com.example.aifloatingball.utils.ThemeUtils
import com.google.android.material.switchmaterial.SwitchMaterial

class AiParamsFragment : Fragment() {
    private val viewModel: SettingsViewModel by activityViewModels()

    private lateinit var inferenceModeDropdown: AutoCompleteTextView
    private lateinit var switchReasoning: SwitchMaterial
    private lateinit var switchExamples: SwitchMaterial
    private lateinit var codeStyleDropdown: AutoCompleteTextView
    private lateinit var seekTemperature: SeekBar
    private lateinit var labelTemperature: TextView
    private lateinit var seekTopP: SeekBar
    private lateinit var labelTopP: TextView
    private lateinit var maxTokensDropdown: AutoCompleteTextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return try {
            val view = inflater.inflate(R.layout.fragment_ai_params, container, false)
            // 应用当前主题
            applyTheme(view)
            view
        } catch (e: Exception) {
            android.util.Log.e("AiParamsFragment", "Error in onCreateView", e)
            // 返回一个简单的错误视图
            val errorView = TextView(requireContext())
            errorView.text = "AI参数配置加载失败，请重试"
            errorView.gravity = android.view.Gravity.CENTER
            errorView
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 确保在观察LiveData之前先初始化所有视图
        try {
            setupViews(view)
            setupDropdowns()
            setupSeekBarListeners()

            // 现在安全地观察LiveData
            viewModel.selectedProfile.observe(viewLifecycleOwner) { profile ->
                if (::seekTemperature.isInitialized && ::labelTemperature.isInitialized) {
                    displayProfile(profile)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AiParamsFragment", "Error in onViewCreated", e)
        }
    }
    
    private fun applyTheme(view: View) {
        // 使用主题工具类应用AI助手中心主题
        ThemeUtils.applyAIAssistantTheme(view, requireContext())
    }

    private fun setupViews(view: View) {
        try {
            inferenceModeDropdown = view.findViewById(R.id.inference_mode_dropdown) ?: throw IllegalStateException("inference_mode_dropdown not found")
            switchReasoning = view.findViewById(R.id.switch_reasoning) ?: throw IllegalStateException("switch_reasoning not found")
            switchExamples = view.findViewById(R.id.switch_examples) ?: throw IllegalStateException("switch_examples not found")
            codeStyleDropdown = view.findViewById(R.id.code_style_dropdown) ?: throw IllegalStateException("code_style_dropdown not found")
            seekTemperature = view.findViewById(R.id.seek_temperature) ?: throw IllegalStateException("seek_temperature not found")
            labelTemperature = view.findViewById(R.id.label_temperature) ?: throw IllegalStateException("label_temperature not found")
            seekTopP = view.findViewById(R.id.seek_top_p) ?: throw IllegalStateException("seek_top_p not found")
            labelTopP = view.findViewById(R.id.label_top_p) ?: throw IllegalStateException("label_top_p not found")
            maxTokensDropdown = view.findViewById(R.id.max_tokens_dropdown) ?: throw IllegalStateException("max_tokens_dropdown not found")
        } catch (e: Exception) {
            android.util.Log.e("AiParamsFragment", "Error setting up views", e)
            throw e
        }
    }
    
    private fun setupDropdowns() {
        context?.let { ctx ->
            // 设置推理模式下拉菜单
            val inferenceModeAdapter = ArrayAdapter(
                ctx,
                android.R.layout.simple_dropdown_item_1line,
                resources.getStringArray(R.array.inference_mode_entries)
            )
            inferenceModeDropdown.setAdapter(inferenceModeAdapter)
            
            // 设置代码风格下拉菜单
            val codeStyleAdapter = ArrayAdapter(
                ctx,
                android.R.layout.simple_dropdown_item_1line,
                resources.getStringArray(R.array.code_style_entries)
            )
            codeStyleDropdown.setAdapter(codeStyleAdapter)
            
            // 设置最大令牌数下拉菜单
            val maxTokensAdapter = ArrayAdapter(
                ctx,
                android.R.layout.simple_dropdown_item_1line,
                resources.getStringArray(R.array.max_tokens_entries)
            )
            maxTokensDropdown.setAdapter(maxTokensAdapter)
        }
    }

    private fun setupSeekBarListeners() {
        seekTemperature.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress / 100.0f
                labelTemperature.text = String.format("%.2f", value)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        seekTopP.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress / 100.0f
                labelTopP.text = String.format("%.2f", value)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun displayProfile(profile: PromptProfile?) {
        profile ?: return

        // 检查所有必需的视图是否已初始化
        if (!::inferenceModeDropdown.isInitialized ||
            !::switchReasoning.isInitialized ||
            !::switchExamples.isInitialized ||
            !::codeStyleDropdown.isInitialized ||
            !::seekTemperature.isInitialized ||
            !::labelTemperature.isInitialized ||
            !::seekTopP.isInitialized ||
            !::labelTopP.isInitialized ||
            !::maxTokensDropdown.isInitialized) {
            android.util.Log.w("AiParamsFragment", "Views not initialized yet, skipping displayProfile")
            return
        }

        try {
            // 根据保存的值设置下拉菜单的选定项
            val inferenceModeEntries = resources.getStringArray(R.array.inference_mode_entries)
            val inferenceModeValues = resources.getStringArray(R.array.inference_mode_values)
            val inferenceModeIndex = inferenceModeValues.indexOf(profile.inferenceMode)
            if (inferenceModeIndex >= 0) {
                inferenceModeDropdown.setText(inferenceModeEntries[inferenceModeIndex], false)
            } else {
                inferenceModeDropdown.setText("", false)
            }

            switchReasoning.isChecked = profile.reasoning
            switchExamples.isChecked = profile.examples

            val codeStyleEntries = resources.getStringArray(R.array.code_style_entries)
            val codeStyleValues = resources.getStringArray(R.array.code_style_values)
            val codeStyleIndex = codeStyleValues.indexOf(profile.codeStyle)
            if (codeStyleIndex >= 0) {
                codeStyleDropdown.setText(codeStyleEntries[codeStyleIndex], false)
            } else {
                codeStyleDropdown.setText("", false)
            }

            seekTemperature.progress = (profile.temperature * 100).toInt()
            labelTemperature.text = String.format("%.2f", profile.temperature)
            seekTopP.progress = (profile.topP * 100).toInt()
            labelTopP.text = String.format("%.2f", profile.topP)

            val maxTokensEntries = resources.getStringArray(R.array.max_tokens_entries)
            val maxTokensValues = resources.getStringArray(R.array.max_tokens_values)
            val maxTokensIndex = maxTokensValues.indexOf(profile.maxTokens.toString())
            if (maxTokensIndex >= 0) {
                maxTokensDropdown.setText(maxTokensEntries[maxTokensIndex], false)
            } else {
                maxTokensDropdown.setText("", false)
            }
        } catch (e: Exception) {
            android.util.Log.e("AiParamsFragment", "Error displaying profile", e)
        }
    }

    fun collectProfileData(profile: PromptProfile): PromptProfile {
        // 检查所有必需的视图是否已初始化
        if (!::inferenceModeDropdown.isInitialized ||
            !::switchReasoning.isInitialized ||
            !::switchExamples.isInitialized ||
            !::codeStyleDropdown.isInitialized ||
            !::seekTemperature.isInitialized ||
            !::labelTemperature.isInitialized ||
            !::seekTopP.isInitialized ||
            !::labelTopP.isInitialized ||
            !::maxTokensDropdown.isInitialized) {
            android.util.Log.w("AiParamsFragment", "Views not initialized yet, returning original profile")
            return profile
        }

        try {
            // 获取选定值的索引并转换为对应的值
            val inferenceModeEntries = resources.getStringArray(R.array.inference_mode_entries)
            val inferenceModeValues = resources.getStringArray(R.array.inference_mode_values)
            val selectedInferenceMode = inferenceModeDropdown.text.toString()
            val inferenceModeIndex = inferenceModeEntries.indexOf(selectedInferenceMode)
            val inferenceModeValue = if (inferenceModeIndex >= 0) inferenceModeValues[inferenceModeIndex] else ""
            
            val codeStyleEntries = resources.getStringArray(R.array.code_style_entries)
            val codeStyleValues = resources.getStringArray(R.array.code_style_values)
            val selectedCodeStyle = codeStyleDropdown.text.toString()
            val codeStyleIndex = codeStyleEntries.indexOf(selectedCodeStyle)
            val codeStyleValue = if (codeStyleIndex >= 0) codeStyleValues[codeStyleIndex] else ""
            
            val maxTokensEntries = resources.getStringArray(R.array.max_tokens_entries)
            val maxTokensValues = resources.getStringArray(R.array.max_tokens_values)
            val selectedMaxTokens = maxTokensDropdown.text.toString()
            val maxTokensIndex = maxTokensEntries.indexOf(selectedMaxTokens)
            val maxTokensValue = if (maxTokensIndex >= 0) maxTokensValues[maxTokensIndex].toIntOrNull() ?: 2048 else 2048
            
            return profile.copy(
                inferenceMode = inferenceModeValue,
                reasoning = switchReasoning.isChecked,
                examples = switchExamples.isChecked,
                codeStyle = codeStyleValue,
                temperature = seekTemperature.progress / 100.0f,
                topP = seekTopP.progress / 100.0f,
                maxTokens = maxTokensValue
            )
        } catch (e: Exception) {
            android.util.Log.e("AiParamsFragment", "Error collecting profile data", e)
            return profile
        }
    }
} 